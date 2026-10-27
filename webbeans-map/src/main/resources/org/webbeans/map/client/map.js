/*
 * wb.MapView — a slippy map without dependencies. Web Mercator tiles (256px, z/x/y) from the server's tile
 * endpoint (or a direct URL template, or none: a graticule), overlays from "map" calls (id → description, null
 * removes; "reset" replaces all; "view" moves the camera; "layers" are toggleable groups): markers (HTML,
 * draggable, rotatable, labelled), lines, polygons with holes, circles in metres, rectangles, images, GeoJSON
 * (styled per feature), popups. Pan by drag / arrows, wheel zoom at the pointer, double-click zoom, +/-,
 * pinch on touch screens; a zoom control, scale bar, attribution and layer control; draw modes (marker, line,
 * polygon, rectangle, circle). Reports view changes (lazy), clicks, double-clicks, hovers, context menus,
 * marker drags, finished drawings, layer toggles and popup closes.
 */
(function () {
  var NS = 'http://www.w3.org/2000/svg';
  var TILE = 256, MAX_LAT = 85.05112878;

  function svg(tag, attrs, parent) { var e = document.createElementNS(NS, tag); if (attrs) { for (var k in attrs) { if (attrs[k] != null) e.setAttribute(k, attrs[k]); } } if (parent) parent.appendChild(e); return e; }
  function num(v, d) { return typeof v === 'number' && isFinite(v) ? v : d; }
  function clamp(v, a, b) { return v < a ? a : v > b ? b : v; }

  /* ---------------- projection ---------------- */
  function project(lat, lng, z) {
    var s = TILE * Math.pow(2, z); lat = clamp(lat, -MAX_LAT, MAX_LAT);
    var sin = Math.sin(lat * Math.PI / 180);
    return { x: (lng + 180) / 360 * s, y: (0.5 - Math.log((1 + sin) / (1 - sin)) / (4 * Math.PI)) * s };
  }
  function unproject(x, y, z) {
    var s = TILE * Math.pow(2, z); var n = Math.PI - 2 * Math.PI * y / s;
    return { lat: 180 / Math.PI * Math.atan(0.5 * (Math.exp(n) - Math.exp(-n))), lng: x / s * 360 - 180 };
  }
  function metresPerPixel(lat, z) { return 40075016.686 * Math.cos(lat * Math.PI / 180) / (TILE * Math.pow(2, z)); }
  function distance(a, b) {
    var R = 6371008.8, la1 = a.lat * Math.PI / 180, la2 = b.lat * Math.PI / 180, dLat = la2 - la1, dLng = (b.lng - a.lng) * Math.PI / 180;
    var h = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(la1) * Math.cos(la2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
    return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)));
  }
  var PIN = 'M12 31S2 18.5 2 11a10 10 0 0 1 20 0c0 7.5-10 20-10 20z';

  /* ---------------- the renderer ---------------- */
  wb.register('wb.MapView', {
    create: function (ctx) {
      var el = wb.h('div', 'wb-map'); el.tabIndex = 0; el.ctx = ctx; el.props = {};
      el.view = { lat: 0, lng: 0, z: 2 }; el.overlays = {}; el.layers = {}; el.els = {}; el.tiles = {}; el.tileLevel = null; el.draw = null; el.hoverId = null;
      el.tilePane = wb.h('div', 'wb-map-tiles'); el.graticule = svg('svg', { 'class': 'wb-map-graticule' }); el.vector = svg('svg', { 'class': 'wb-map-vector' });
      el.imagePane = wb.h('div', 'wb-map-images'); el.markerPane = wb.h('div', 'wb-map-markers'); el.popupPane = wb.h('div', 'wb-map-popups'); el.drawPane = svg('svg', { 'class': 'wb-map-drawpane' });
      el.append(el.tilePane, el.graticule, el.imagePane, el.vector, el.markerPane, el.popupPane, el.drawPane);
      el.defs = svg('defs', null, el.vector); el.markers = {};
      // controls
      el.zoomCtl = wb.h('div', 'wb-map-zoomctl'); ['+', '−'].forEach(function (t, i) { var b = wb.h('button', 'wb-map-zbtn', t); b.type = 'button'; b.title = i ? 'Zoom out' : 'Zoom in'; b.addEventListener('click', function () { self.zoomBy(el, i ? -1 : 1, null, true); }); el.zoomCtl.appendChild(b); });
      el.scale = wb.h('div', 'wb-map-scale'); el.attr = wb.h('div', 'wb-map-attr'); el.layerCtl = wb.h('div', 'wb-map-layerctl wb-hidden'); el.hint = wb.h('div', 'wb-map-hint wb-hidden');
      el.append(el.zoomCtl, el.scale, el.attr, el.layerCtl, el.hint);
      var self = this;
      el.toPoint = function (lat, lng) { var c = project(el.view.lat, el.view.lng, el.view.z); var p = project(lat, lng, el.view.z); return { x: p.x - c.x + el.clientWidth / 2, y: p.y - c.y + el.clientHeight / 2 }; };
      el.toLatLng = function (px, py) { var c = project(el.view.lat, el.view.lng, el.view.z); return unproject(c.x + px - el.clientWidth / 2, c.y + py - el.clientHeight / 2, el.view.z); };
      el.eventLatLng = function (e) { var r = el.getBoundingClientRect(); return el.toLatLng(e.clientX - r.left, e.clientY - r.top); };
      el.addEventListener('wheel', function (e) { if (el.props.interactive === false) return; e.preventDefault(); var r = el.getBoundingClientRect(); self.zoomBy(el, -e.deltaY / 300, { x: e.clientX - r.left, y: e.clientY - r.top }, false); }, { passive: false });
      el.addEventListener('mousedown', function (e) { self.onDown(el, e); });
      el.addEventListener('touchstart', function (e) { self.onTouch(el, e); }, { passive: false });
      el.addEventListener('dblclick', function (e) { self.onDouble(el, e); });
      el.addEventListener('contextmenu', function (e) { self.onMenu(el, e); });
      el.addEventListener('mousemove', function (e) { self.onMove(el, e); });
      el.addEventListener('mouseleave', function () { if (el.hoverId !== null) { el.hoverId = null; ctx.emit('hover', {}); } });
      el.addEventListener('keydown', function (e) { self.onKey(el, e); });
      if (typeof ResizeObserver !== 'undefined') { el.ro = new ResizeObserver(function () { if (el.pendingFit && el.clientWidth) { self.applyFit(el); } self.render(el); }); el.ro.observe(el); }
      return el;
    },
    update: function (el, props, changed) {
      el.props = props; wb.size(el, props, changed);
      if (changed.has('tiles')) { el.tileSource = props.tiles || { kind: 'blank' }; this.clearTiles(el); el.attr.textContent = el.tileSource.attr || ''; el.tilePane.style.background = el.tileSource.bg || ''; }
      el.zoomCtl.classList.toggle('wb-hidden', props.zoomControl === false); el.scale.classList.toggle('wb-hidden', props.scaleBar === false); el.attr.classList.toggle('wb-hidden', props.attribution === false || !el.attr.textContent);
      el.classList.toggle('wb-map-static', props.interactive === false);
      if (changed.has('drawMode')) { this.cancelDraw(el); el.classList.toggle('wb-map-drawing', !!props.drawMode && props.drawMode !== 'NONE'); this.hint(el); }
      this.render(el);
    },
    call: function (el, m, args) {
      if (m !== 'map') return; var d = args[0] || {}; var self = this;
      if (d.reset) { el.overlays = {}; el.layers = {}; Object.keys(el.els).forEach(function (id) { el.els[id].remove(); }); el.els = {}; }
      if (d.layers) Object.keys(d.layers).forEach(function (id) { if (d.layers[id] == null) delete el.layers[id]; else el.layers[id] = d.layers[id]; });
      if (d.overlays) Object.keys(d.overlays).forEach(function (id) { var spec = d.overlays[id]; if (spec == null) { delete el.overlays[id]; if (el.els[id]) { el.els[id].remove(); delete el.els[id]; } return; } if (el.els[id] && el.overlays[id] && el.overlays[id].k !== spec.k) { el.els[id].remove(); delete el.els[id]; } el.overlays[id] = spec; delete spec.__built; });
      if (d.view) this.setView(el, d.view);
      this.paintLayerControl(el);
      this.render(el);
    },
    insertChild: function () {}, removeChild: function () {},
    destroy: function (el) { if (el.ro) el.ro.disconnect(); if (el.raf) cancelAnimationFrame(el.raf); if (el.anim) cancelAnimationFrame(el.anim); },

    /* ---------------- view ---------------- */
    setView: function (el, v) {
      if (v.fit) { el.pendingFit = { b: v.fit, pad: num(v.pad, 20) }; if (el.clientWidth) this.applyFit(el); return; }
      var target = { lat: num(v.c && v.c[0], el.view.lat), lng: num(v.c && v.c[1], el.view.lng), z: this.clampZoom(el, num(v.z, el.view.z)) };
      if (v.anim && el.clientWidth && el.props.animation !== false) this.animateTo(el, target); else { el.view = target; this.render(el); }
    },
    applyFit: function (el) {
      var f = el.pendingFit; el.pendingFit = null; var b = f.b; var pad = f.pad;
      var sw = project(b[0], b[1], 0), ne = project(b[2], b[3], 0); var w = Math.max(1e-9, Math.abs(ne.x - sw.x)), h = Math.max(1e-9, Math.abs(ne.y - sw.y));
      var z = Math.min(Math.log(Math.max(1, el.clientWidth - 2 * pad) / w) / Math.log(2), Math.log(Math.max(1, el.clientHeight - 2 * pad) / h) / Math.log(2));
      el.view = { lat: (b[0] + b[2]) / 2, lng: (b[1] + b[3]) / 2, z: this.clampZoom(el, Math.floor(z * 4) / 4) }; this.render(el); this.reportView(el);
    },
    clampZoom: function (el, z) { var min = Math.max(num(el.props.minZoom, 0), num(el.tileSource && el.tileSource.min, 0)); var max = Math.min(num(el.props.maxZoom, 19), num(el.tileSource && el.tileSource.max, 19)); return clamp(z, min, max); },
    animateTo: function (el, target) {
      var self = this; var from = { lat: el.view.lat, lng: el.view.lng, z: el.view.z }; var t0 = performance.now(); if (el.anim) cancelAnimationFrame(el.anim);
      var step = function (now) { var t = Math.min(1, (now - t0) / 350); var e = 1 - Math.pow(1 - t, 3); el.view = { lat: from.lat + (target.lat - from.lat) * e, lng: from.lng + (target.lng - from.lng) * e, z: from.z + (target.z - from.z) * e }; self.render(el); if (t < 1) el.anim = requestAnimationFrame(step); else { el.anim = null; self.reportView(el); } };
      el.anim = requestAnimationFrame(step);
    },
    zoomBy: function (el, dz, at, animated) {
      var z = this.clampZoom(el, el.view.z + dz); if (z === el.view.z) return;
      if (at) { var before = el.toLatLng(at.x, at.y); el.view.z = z; var after = el.toPoint(before.lat, before.lng); var c = el.toLatLng(el.clientWidth / 2 + (after.x - at.x), el.clientHeight / 2 + (after.y - at.y)); el.view.lat = c.lat; el.view.lng = c.lng; this.render(el); this.reportView(el); }
      else if (animated) this.animateTo(el, { lat: el.view.lat, lng: el.view.lng, z: Math.round(z) });
      else { el.view.z = z; this.render(el); this.reportView(el); }
    },
    panBy: function (el, dx, dy) { var c = el.toLatLng(el.clientWidth / 2 + dx, el.clientHeight / 2 + dy); el.view.lat = clamp(c.lat, -MAX_LAT, MAX_LAT); el.view.lng = c.lng; this.constrain(el); this.render(el); },
    constrain: function (el) { var mb = el.props.maxBounds; if (!mb) return; el.view.lat = clamp(el.view.lat, mb[0], mb[2]); el.view.lng = clamp(el.view.lng, mb[1], mb[3]); },
    bounds: function (el) { var sw = el.toLatLng(0, el.clientHeight), ne = el.toLatLng(el.clientWidth, 0); return [sw.lat, sw.lng, ne.lat, ne.lng]; },
    reportView: function (el) { if (!el.clientWidth) return; el.ctx.emit('view', { c: [el.view.lat, el.view.lng], z: Math.round(el.view.z * 1000) / 1000, b: this.bounds(el) }); },

    /* ---------------- rendering ---------------- */
    render: function (el) {
      if (!el.clientWidth) return;
      this.renderTiles(el); this.renderGraticule(el);
      var self = this; var ids = Object.keys(el.overlays).sort(function (a, b) { return num(el.overlays[a].z, 0) - num(el.overlays[b].z, 0); });
      ids.forEach(function (id) { self.renderOverlay(el, id); });
      el.vector.setAttribute('viewBox', '0 0 ' + el.clientWidth + ' ' + el.clientHeight); el.drawPane.setAttribute('viewBox', '0 0 ' + el.clientWidth + ' ' + el.clientHeight);
      this.renderDraw(el); this.renderScale(el);
    },
    visible: function (el, spec) { return spec.vis !== false && (!spec.ly || !el.layers[spec.ly] || el.layers[spec.ly].vis !== false); },
    renderTiles: function (el) {
      var src = el.tileSource || { kind: 'blank' }; if (src.kind === 'blank' || !src.url) { this.clearTiles(el); el.tilePane.style.display = 'none'; return; }
      el.tilePane.style.display = '';
      var zi = clamp(Math.round(el.view.z), num(src.min, 0), num(src.max, 19)); var scale = Math.pow(2, el.view.z - zi);
      if (el.tileLevel !== zi) { this.clearTiles(el); el.tileLevel = zi; }
      var c = project(el.view.lat, el.view.lng, zi); var W = el.clientWidth, H = el.clientHeight; var n = Math.pow(2, zi);
      var left = c.x - W / 2 / scale, top = c.y - H / 2 / scale; var x0 = Math.floor(left / TILE), y0 = Math.floor(top / TILE), x1 = Math.floor((left + W / scale) / TILE), y1 = Math.floor((top + H / scale) / TILE);
      var keep = {};
      for (var ty = Math.max(0, y0); ty <= Math.min(n - 1, y1); ty++) {
        for (var tx = x0; tx <= x1; tx++) {
          var wx = ((tx % n) + n) % n; var key = zi + '/' + tx + '/' + ty; keep[key] = true;
          var img = el.tiles[key];
          if (!img) { img = document.createElement('img'); img.className = 'wb-map-tile'; img.draggable = false; img.alt = ''; img.onerror = function () { this.classList.add('wb-map-tile-missing'); }; img.src = this.tileUrl(src, zi, wx, ty); el.tiles[key] = img; el.tilePane.appendChild(img); }
          var px = (tx * TILE - left) * scale, py = (ty * TILE - top) * scale; img.style.transform = 'translate(' + px + 'px,' + py + 'px) scale(' + scale + ')';
        }
      }
      Object.keys(el.tiles).forEach(function (k) { if (!keep[k]) { el.tiles[k].remove(); delete el.tiles[k]; } });
    },
    tileUrl: function (src, z, x, y) {
      var u = src.url.replace('{z}', z).replace('{x}', x).replace('{y}', y);
      if (u.indexOf('{s}') >= 0) { var subs = src.sub || ''; u = u.replace('{s}', subs ? subs.charAt(Math.abs(x + y) % subs.length) : ''); }
      return src.kind === 'direct' ? u : wb.base + u;
    },
    clearTiles: function (el) { Object.keys(el.tiles).forEach(function (k) { el.tiles[k].remove(); }); el.tiles = {}; el.tileLevel = null; },
    renderGraticule: function (el) {
      var g = el.graticule; while (g.lastChild) g.removeChild(g.lastChild);
      var blank = !el.tileSource || el.tileSource.kind === 'blank' || !el.tileSource.url; g.style.display = blank ? '' : 'none'; if (!blank) return;
      g.setAttribute('viewBox', '0 0 ' + el.clientWidth + ' ' + el.clientHeight); var b = this.bounds(el); var step = el.view.z < 3 ? 30 : el.view.z < 5 ? 10 : el.view.z < 8 ? 2 : el.view.z < 11 ? 0.5 : 0.1;
      for (var lng = Math.floor(b[1] / step) * step; lng <= b[3] + step; lng += step) { var p = el.toPoint(0, lng); svg('line', { 'class': 'wb-map-gratline', x1: p.x, y1: 0, x2: p.x, y2: el.clientHeight }, g); var t = svg('text', { 'class': 'wb-map-grattext', x: p.x + 3, y: 12 }, g); t.textContent = (Math.round(lng * 100) / 100) + '°'; }
      for (var lat = Math.max(-80, Math.floor(b[0] / step) * step); lat <= Math.min(80, b[2] + step); lat += step) { var q = el.toPoint(lat, 0); svg('line', { 'class': 'wb-map-gratline', x1: 0, y1: q.y, x2: el.clientWidth, y2: q.y }, g); var t2 = svg('text', { 'class': 'wb-map-grattext', x: 4, y: q.y - 3 }, g); t2.textContent = (Math.round(lat * 100) / 100) + '°'; }
      var eq = el.toPoint(0, 0); svg('line', { 'class': 'wb-map-gratline wb-map-gratmajor', x1: 0, y1: eq.y, x2: el.clientWidth, y2: eq.y }, g); svg('line', { 'class': 'wb-map-gratline wb-map-gratmajor', x1: eq.x, y1: 0, x2: eq.x, y2: el.clientHeight }, g);
    },
    renderScale: function (el) {
      if (el.props.scaleBar === false) return; var mpp = metresPerPixel(el.view.lat, el.view.z); var target = mpp * 100; var nice = [1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 50000, 100000, 200000, 500000, 1000000, 2000000, 5000000];
      var m = nice[0]; for (var i = 0; i < nice.length; i++) { if (nice[i] <= target) m = nice[i]; } var px = m / mpp; el.scale.style.width = Math.round(px) + 'px'; el.scale.textContent = m >= 1000 ? (m / 1000) + ' km' : m + ' m';
    },
    styleCommon: function (node, spec, base) {
      var cls = base + (spec.st ? ' ' + spec.st : '') + (spec.ia === false ? ' wb-map-passive' : ''); if (node.classList.contains('wb-selected')) cls += ' wb-selected'; node.setAttribute('class', cls);
      node.style.opacity = spec.op != null ? spec.op : '';
      (node.__attrs || []).forEach(function (k) { node.removeAttribute('data-' + k); node.style.removeProperty('--wb-m-' + k); }); node.__attrs = [];
      if (spec.a) Object.keys(spec.a).forEach(function (k) { node.setAttribute('data-' + k, spec.a[k]); node.style.setProperty('--wb-m-' + k, spec.a[k]); node.__attrs.push(k); });
      node.dataset.id = spec.__id;
    },
    renderOverlay: function (el, id) {
      var spec = el.overlays[id]; spec.__id = id; var vis = this.visible(el, spec);
      switch (spec.k) {
        case 'marker': this.renderMarker(el, id, spec, vis); break;
        case 'popup': this.renderPopup(el, id, spec, vis); break;
        case 'image': this.renderImage(el, id, spec, vis); break;
        default: this.renderVector(el, id, spec, vis);
      }
    },
    renderMarker: function (el, id, spec, vis) {
      var d = el.els[id];
      if (!d || !spec.__built) {
        if (!d) { d = wb.h('div', 'wb-map-marker'); el.els[id] = d; el.markerPane.appendChild(d); }
        while (d.lastChild) d.removeChild(d.lastChild);
        var w = num(spec.iw, 24), h = num(spec.ih, 32); var ax = spec.ax != null ? spec.ax : w / 2, ay = spec.ay != null ? spec.ay : h;
        var icon;
        if (spec.i) { icon = document.createElement('img'); icon.src = wb.res(spec.i); icon.alt = ''; icon.draggable = false; }
        else { icon = svg('svg', { viewBox: '0 0 24 32', 'class': 'wb-map-pin' }); svg('path', { d: PIN, 'class': 'wb-map-pinpath' }, icon); svg('circle', { cx: 12, cy: 11, r: 4, 'class': 'wb-map-pindot' }, icon); if (spec.c) icon.style.color = spec.c; }
        icon.classList.add('wb-map-markericon'); icon.style.width = w + 'px'; icon.style.height = h + 'px'; icon.style.marginLeft = -ax + 'px'; icon.style.marginTop = -ay + 'px';
        if (spec.rot) icon.style.transform = 'rotate(' + spec.rot + 'deg)';
        d.appendChild(icon);
        if (spec.l) { var lb = wb.h('div', 'wb-map-markerlabel', spec.l); lb.style.left = (w - ax + 4) + 'px'; lb.style.top = (-ay + h / 2 - 9) + 'px'; d.appendChild(lb); }
        d.title = spec.tt || ''; d.classList.toggle('wb-map-draggable', !!spec.drag); spec.__built = true;
      }
      this.styleCommon(d, spec, 'wb-map-marker' + (spec.drag ? ' wb-map-draggable' : ''));
      d.style.display = vis ? '' : 'none'; if (!vis) return;
      var p = el.toPoint(spec.p[0], spec.p[1]); d.style.transform = 'translate(' + Math.round(p.x) + 'px,' + Math.round(p.y) + 'px)'; d.style.zIndex = 100 + num(spec.z, 0);
    },
    renderPopup: function (el, id, spec, vis) {
      var d = el.els[id]; var self = this;
      if (!d || !spec.__built) {
        if (!d) { d = wb.h('div', 'wb-map-popup'); el.els[id] = d; el.popupPane.appendChild(d); }
        while (d.lastChild) d.removeChild(d.lastChild);
        var box = wb.h('div', 'wb-map-popupbox'); box.style.maxWidth = num(spec.mw, 300) + 'px'; var body = wb.h('div', 'wb-map-popupbody'); body.innerHTML = spec.html || ''; box.appendChild(body);
        if (spec.close !== false) { var x = wb.h('button', 'wb-map-popupclose', '×'); x.type = 'button'; x.title = 'Close'; x.addEventListener('click', function (e) { e.stopPropagation(); spec.open = false; d.style.display = 'none'; el.ctx.emit('popupClose', { popup: id }); }); box.appendChild(x); }
        d.appendChild(box); d.appendChild(wb.h('div', 'wb-map-popuptip')); d.addEventListener('mousedown', function (e) { e.stopPropagation(); }); spec.__built = true;
      }
      this.styleCommon(d, spec, 'wb-map-popup');
      var show = vis && spec.open !== false; d.style.display = show ? '' : 'none'; if (!show) return;
      var p = el.toPoint(spec.p[0], spec.p[1]); d.style.transform = 'translate(' + Math.round(p.x) + 'px,' + Math.round(p.y) + 'px)';
    },
    renderImage: function (el, id, spec, vis) {
      var img = el.els[id];
      if (!img) { img = document.createElement('img'); img.className = 'wb-map-image'; img.draggable = false; img.alt = ''; el.els[id] = img; el.imagePane.appendChild(img); }
      var src = spec.i ? wb.res(spec.i) : spec.url ? wb.base + spec.url : ''; if (img.getAttribute('src') !== src) img.src = src;
      this.styleCommon(img, spec, 'wb-map-image'); img.style.display = vis ? '' : 'none'; if (!vis) return;
      var sw = el.toPoint(spec.b[0], spec.b[1]), ne = el.toPoint(spec.b[2], spec.b[3]);
      img.style.transform = 'translate(' + ne.y.toFixed(1) + 'px,0)'; img.style.left = sw.x + 'px'; img.style.top = ne.y + 'px'; img.style.width = Math.max(1, ne.x - sw.x) + 'px'; img.style.height = Math.max(1, sw.y - ne.y) + 'px'; img.style.transform = '';
    },
    pathStyle: function (el, node, spec, closed) {
      node.style.stroke = spec.c || ''; node.style.strokeWidth = num(spec.w, 2) + 'px'; node.style.strokeDasharray = spec.dash || '';
      if (closed && spec.fill !== false) { node.style.fill = spec.fc || spec.c || ''; node.style.fillOpacity = num(spec.fo, 0.25); node.classList.add('wb-map-filled'); } else { node.style.fill = 'none'; }
    },
    renderVector: function (el, id, spec, vis) {
      var g = el.els[id]; var self = this;
      if (!g) { g = svg('g', { 'class': 'wb-map-shape' }, el.vector); el.els[id] = g; }
      this.styleCommon(g, spec, 'wb-map-shape wb-map-' + spec.k); g.style.display = vis ? '' : 'none'; if (!vis) return;
      while (g.lastChild) g.removeChild(g.lastChild);
      if (spec.tt) { var t = svg('title', null, g); t.textContent = spec.tt; }
      var ring = function (pts) { return pts.map(function (p, i) { var q = el.toPoint(p[0], p[1]); return (i ? 'L' : 'M') + q.x.toFixed(1) + ',' + q.y.toFixed(1); }).join(' '); };
      if (spec.k === 'line') {
        var p = svg('path', { d: ring(spec.pts), 'class': 'wb-map-path' }, g); this.pathStyle(el, p, spec, false);
        if (spec.arrow && spec.pts.length > 1) { p.setAttribute('marker-end', 'url(#' + this.arrowMarker(el, spec.c) + ')'); }
        svg('path', { d: ring(spec.pts), 'class': 'wb-map-hit' }, g);
      } else if (spec.k === 'polygon') {
        var d = spec.rings.map(function (r) { return ring(r) + ' Z'; }).join(' '); var pg = svg('path', { d: d, 'class': 'wb-map-path', 'fill-rule': 'evenodd' }, g); this.pathStyle(el, pg, spec, true);
      } else if (spec.k === 'rect') {
        var sw = el.toPoint(spec.b[0], spec.b[1]), ne = el.toPoint(spec.b[2], spec.b[3]); var r = svg('rect', { x: Math.min(sw.x, ne.x), y: Math.min(sw.y, ne.y), width: Math.abs(ne.x - sw.x), height: Math.abs(sw.y - ne.y), 'class': 'wb-map-path' }, g); this.pathStyle(el, r, spec, true);
      } else if (spec.k === 'circle') {
        var c = el.toPoint(spec.p[0], spec.p[1]); var rpx = num(spec.r, 0) / metresPerPixel(spec.p[0], el.view.z); var ci = svg('circle', { cx: c.x, cy: c.y, r: rpx, 'class': 'wb-map-path' }, g); this.pathStyle(el, ci, spec, true);
      } else if (spec.k === 'geojson') {
        this.renderGeoJson(el, g, spec, ring);
      }
    },
    arrowMarker: function (el, color) {
      var key = 'arrow|' + (color || ''); if (el.markers[key]) return el.markers[key]; var id = 'wb-m-' + el.ctx.id + '-' + Object.keys(el.markers).length;
      var m = svg('marker', { id: id, viewBox: '0 0 12 12', refX: 10, refY: 6, markerWidth: 10, markerHeight: 10, markerUnits: 'userSpaceOnUse', orient: 'auto' }, el.defs); var p = svg('path', { d: 'M1,1 L11,6 L1,11 Z', 'class': 'wb-map-arrow' }, m); if (color) { p.style.fill = color; p.style.stroke = color; }
      return el.markers[key] = id;
    },
    renderGeoJson: function (el, g, spec, ring) {
      var self = this; var features = []; var doc = spec.geo || {};
      if (doc.type === 'FeatureCollection') features = doc.features || []; else if (doc.type === 'Feature') features = [doc]; else if (doc.type) features = [{ type: 'Feature', geometry: doc, properties: {} }];
      features.forEach(function (f, idx) {
        var geom = f.geometry; if (!geom) return; var props = f.properties || {}; var st = Object.assign({}, spec, props.style || {}); var fid = f.id != null ? f.id : props.id;
        var fg = svg('g', { 'class': 'wb-map-feature', 'data-feature': idx }, g); if (fid != null) fg.setAttribute('data-fid', fid);
        var tip = st.tooltip || (spec.tp && props[spec.tp]); if (tip) { var t = svg('title', null, fg); t.textContent = tip; }
        var draw = function (type, coords) {
          var sw = function (c) { return [c[1], c[0]]; };
          if (type === 'Point') {
            var p = el.toPoint(coords[1], coords[0]);
            if (spec.circles || st.circles) { var c = svg('circle', { cx: p.x, cy: p.y, r: 6, 'class': 'wb-map-path wb-map-gjpoint' }, fg); self.pathStyle(el, c, st, true); c.style.fillOpacity = 0.9; }
            else { var pin = svg('path', { d: PIN, 'class': 'wb-map-gjpin', transform: 'translate(' + (p.x - 12) + ',' + (p.y - 32) + ')' }, fg); pin.style.fill = st.c || ''; }
            var label = st.label || (spec.lp && props[spec.lp]); if (label) { var tx = svg('text', { 'class': 'wb-map-gjlabel', x: p.x + 9, y: p.y + 4 }, fg); tx.textContent = label; }
          } else if (type === 'LineString') { var l = svg('path', { d: ring(coords.map(sw)), 'class': 'wb-map-path' }, fg); self.pathStyle(el, l, st, false); svg('path', { d: ring(coords.map(sw)), 'class': 'wb-map-hit' }, fg); }
          else if (type === 'Polygon') { var pg = svg('path', { d: coords.map(function (r) { return ring(r.map(sw)) + ' Z'; }).join(' '), 'class': 'wb-map-path', 'fill-rule': 'evenodd' }, fg); self.pathStyle(el, pg, st, true); }
          else if (type === 'MultiPoint') coords.forEach(function (c) { draw('Point', c); });
          else if (type === 'MultiLineString') coords.forEach(function (c) { draw('LineString', c); });
          else if (type === 'MultiPolygon') coords.forEach(function (c) { draw('Polygon', c); });
          else if (type === 'GeometryCollection') (geom.geometries || []).forEach(function (gg) { draw(gg.type, gg.coordinates); });
        };
        draw(geom.type, geom.coordinates);
      });
    },
    paintLayerControl: function (el) {
      var ids = Object.keys(el.layers); var ctl = el.layerCtl; while (ctl.lastChild) ctl.removeChild(ctl.lastChild);
      ctl.classList.toggle('wb-hidden', !ids.length || el.props.layerControl === false); if (!ids.length) return;
      var self = this;
      ids.forEach(function (id) { var l = el.layers[id]; var row = wb.h('label', 'wb-map-layerrow'); var cb = wb.h('input'); cb.type = 'checkbox'; cb.checked = l.vis !== false; cb.addEventListener('change', function () { l.vis = cb.checked; el.ctx.emit('layer', { layer: id, vis: cb.checked }); self.render(el); }); row.appendChild(cb); row.appendChild(wb.h('span', 'wb-text', l.name || id)); ctl.appendChild(row); });
    },

    /* ---------------- interaction ---------------- */
    track: function (move, up) { var mu = function (e) { document.removeEventListener('mousemove', move); document.removeEventListener('mouseup', mu); up(e); }; document.addEventListener('mousemove', move); document.addEventListener('mouseup', mu); },
    overlayAt: function (e) {
      var m = e.target.closest('.wb-map-marker'); if (m) return { id: m.dataset.id, el: m };
      var f = e.target.closest('.wb-map-feature'); var s = e.target.closest('.wb-map-shape'); if (s) return { id: s.dataset.id, el: s, feature: f ? Number(f.dataset.feature) : -1, fid: f ? f.dataset.fid : undefined };
      var im = e.target.closest('.wb-map-image'); if (im) return { id: im.dataset.id, el: im }; return null;
    },
    eventData: function (el, e, hit) { var ll = el.eventLatLng(e); var d = { p: [ll.lat, ll.lng], x: e.clientX, y: e.clientY }; if (hit) { d.id = hit.id; if (hit.feature != null && hit.feature >= 0) { d.f = hit.feature; if (hit.fid != null) d.fid = hit.fid; } } return d; },
    onDown: function (el, e) {
      if (e.button !== 0) return; el.focus(); if (el.props.interactive === false) return;
      if (e.target.closest('.wb-map-zoomctl, .wb-map-layerctl, .wb-map-popup')) return;
      var hit = this.overlayAt(e); var spec = hit && el.overlays[hit.id];
      if (spec && spec.k === 'marker' && spec.drag) { this.dragMarker(el, e, hit.id); return; }
      var mode = el.props.drawMode; if (mode && mode !== 'NONE' && (mode === 'RECTANGLE' || mode === 'CIRCLE')) { this.dragDraw(el, e, mode); return; }
      this.pan(el, e, hit);
    },
    pan: function (el, e, hit) {
      e.preventDefault(); var self = this; var sx = e.clientX, sy = e.clientY; var start = { lat: el.view.lat, lng: el.view.lng }; var moved = false; var c0 = project(start.lat, start.lng, el.view.z);
      this.track(function (m) { var dx = m.clientX - sx, dy = m.clientY - sy; if (!moved && Math.abs(dx) + Math.abs(dy) < 3) return; if (!moved) { moved = true; el.classList.add('wb-map-panning'); } var c = unproject(c0.x - dx, c0.y - dy, el.view.z); el.view.lat = clamp(c.lat, -MAX_LAT, MAX_LAT); el.view.lng = c.lng; self.constrain(el); self.render(el); },
        function (m) { el.classList.remove('wb-map-panning'); if (moved) { self.reportView(el); return; } self.click(el, m, hit); });
    },
    click: function (el, e, hit) {
      var mode = el.props.drawMode;
      if (mode && mode !== 'NONE' && !hit) { this.drawClick(el, e, mode); return; }
      if (hit && hit.el.classList.contains('wb-map-passive')) hit = null;
      el.ctx.emit('mapClick', this.eventData(el, e, hit));
    },
    dragMarker: function (el, e, id) {
      e.preventDefault(); e.stopPropagation(); var self = this; var spec = el.overlays[id]; var d = el.els[id]; var r = el.getBoundingClientRect(); var sx = e.clientX, sy = e.clientY; var moved = false;
      var p0 = el.toPoint(spec.p[0], spec.p[1]);
      this.track(function (m) { if (!moved && Math.abs(m.clientX - sx) + Math.abs(m.clientY - sy) < 3) return; moved = true; d.classList.add('wb-map-dragging'); var ll = el.toLatLng(p0.x + m.clientX - sx, p0.y + m.clientY - sy); spec.p = [ll.lat, ll.lng]; self.render(el); },
        function (m) { d.classList.remove('wb-map-dragging'); if (!moved) { self.click(el, m, { id: id, el: d }); return; } el.ctx.emit('drag', { id: id, p: spec.p, dragged: true }); });
    },
    onDouble: function (el, e) {
      if (el.props.interactive === false) return; if (e.target.closest('.wb-map-zoomctl, .wb-map-layerctl, .wb-map-popup')) return;
      var mode = el.props.drawMode;
      if (mode === 'LINE' || mode === 'POLYGON') { e.preventDefault(); this.finishDraw(el); return; }
      var hit = this.overlayAt(e); if (hit) { el.ctx.emit('dblclick', this.eventData(el, e, hit)); return; }
      if (el.ctx.listens('dblclick')) el.ctx.emit('dblclick', this.eventData(el, e, null));
      var r = el.getBoundingClientRect(); this.zoomBy(el, 1, { x: e.clientX - r.left, y: e.clientY - r.top }, false);
    },
    onMenu: function (el, e) { if (e.target.closest('.wb-map-popup')) return; e.preventDefault(); el.ctx.emit('menu', this.eventData(el, e, this.overlayAt(e))); },
    onMove: function (el, e) {
      if (el.draw && el.draw.pts.length) { var ll = el.eventLatLng(e); el.draw.cursor = [ll.lat, ll.lng]; this.renderDraw(el); }
      if (!el.ctx.listens('hover')) return; var hit = this.overlayAt(e); var id = hit ? hit.id + (hit.feature >= 0 ? '#' + hit.feature : '') : null; if (id === el.hoverId) return; el.hoverId = id;
      el.ctx.emit('hover', hit ? this.eventData(el, e, hit) : {});
    },
    onKey: function (el, e) {
      if (el.props.interactive === false) return; var step = e.shiftKey ? 200 : 50;
      switch (e.key) {
        case 'ArrowLeft': this.panBy(el, -step, 0); break; case 'ArrowRight': this.panBy(el, step, 0); break; case 'ArrowUp': this.panBy(el, 0, -step); break; case 'ArrowDown': this.panBy(el, 0, step); break;
        case '+': case '=': this.zoomBy(el, 1, null, true); break; case '-': this.zoomBy(el, -1, null, true); break;
        case 'Escape': this.cancelDraw(el); return; case 'Enter': this.finishDraw(el); return;
        default: return;
      }
      e.preventDefault(); if (/^Arrow/.test(e.key)) this.reportView(el);
    },
    onTouch: function (el, e) {
      if (el.props.interactive === false || e.target.closest('.wb-map-zoomctl, .wb-map-layerctl, .wb-map-popup')) return; var self = this;
      var t = e.touches; if (!t.length) return; e.preventDefault();
      var start = { lat: el.view.lat, lng: el.view.lng, z: el.view.z }; var c0 = project(start.lat, start.lng, start.z); var p0 = [t[0].clientX, t[0].clientY]; var d0 = t.length > 1 ? Math.hypot(t[1].clientX - t[0].clientX, t[1].clientY - t[0].clientY) : 0;
      var move = function (m) { m.preventDefault(); var mt = m.touches; if (mt.length > 1 && d0) { var d1 = Math.hypot(mt[1].clientX - mt[0].clientX, mt[1].clientY - mt[0].clientY); el.view.z = self.clampZoom(el, start.z + Math.log(d1 / d0) / Math.log(2)); }
        var c = unproject(c0.x - (mt[0].clientX - p0[0]) * Math.pow(2, el.view.z - start.z), c0.y - (mt[0].clientY - p0[1]) * Math.pow(2, el.view.z - start.z), el.view.z); el.view.lat = clamp(c.lat, -MAX_LAT, MAX_LAT); el.view.lng = c.lng; self.render(el); };
      var end = function () { document.removeEventListener('touchmove', move); document.removeEventListener('touchend', end); self.reportView(el); };
      document.addEventListener('touchmove', move, { passive: false }); document.addEventListener('touchend', end);
    },

    /* ---------------- drawing ---------------- */
    hint: function (el) { var m = el.props.drawMode; var text = { MARKER: 'Click to place a marker', LINE: 'Click to add points, double-click or Enter to finish, Esc to cancel', POLYGON: 'Click to add corners, double-click or Enter to finish, Esc to cancel', RECTANGLE: 'Drag a box', CIRCLE: 'Drag from the centre' }[m]; el.hint.textContent = text || ''; el.hint.classList.toggle('wb-hidden', !text); },
    drawClick: function (el, e, mode) {
      var ll = el.eventLatLng(e); var p = [ll.lat, ll.lng];
      if (mode === 'MARKER') { el.ctx.emit('draw', { mode: 'marker', pts: [p] }); return; }
      if (!el.draw) el.draw = { mode: mode, pts: [] }; el.draw.pts.push(p); el.draw.cursor = null; this.renderDraw(el);
    },
    dragDraw: function (el, e, mode) {
      e.preventDefault(); var self = this; var ll = el.eventLatLng(e); var a = [ll.lat, ll.lng]; el.draw = { mode: mode, pts: [a], cursor: a }; var moved = false;
      this.track(function (m) { var r = el.getBoundingClientRect(); var q = el.toLatLng(m.clientX - r.left, m.clientY - r.top); el.draw.cursor = [q.lat, q.lng]; moved = true; self.renderDraw(el); },
        function () { var pts = moved ? [a, el.draw.cursor] : null; el.draw = null; self.renderDraw(el); if (pts) el.ctx.emit('draw', { mode: mode.toLowerCase(), pts: pts }); });
    },
    finishDraw: function (el) { var d = el.draw; if (!d) return; var need = d.mode === 'POLYGON' ? 3 : 2; el.draw = null; this.renderDraw(el); if (d.pts.length >= need) el.ctx.emit('draw', { mode: d.mode.toLowerCase(), pts: d.pts }); },
    cancelDraw: function (el) { el.draw = null; this.renderDraw(el); },
    renderDraw: function (el) {
      var g = el.drawPane; while (g.lastChild) g.removeChild(g.lastChild); var d = el.draw; if (!d || !d.pts.length) return;
      var pts = d.pts.concat(d.cursor ? [d.cursor] : []); var toP = function (p) { return el.toPoint(p[0], p[1]); };
      if (d.mode === 'RECTANGLE' && pts.length > 1) { var a = toP(pts[0]), b = toP(pts[1]); svg('rect', { x: Math.min(a.x, b.x), y: Math.min(a.y, b.y), width: Math.abs(b.x - a.x), height: Math.abs(b.y - a.y), 'class': 'wb-map-drawshape' }, g); return; }
      if (d.mode === 'CIRCLE' && pts.length > 1) { var c = toP(pts[0]); var r = distance({ lat: pts[0][0], lng: pts[0][1] }, { lat: pts[1][0], lng: pts[1][1] }) / metresPerPixel(pts[0][0], el.view.z); svg('circle', { cx: c.x, cy: c.y, r: r, 'class': 'wb-map-drawshape' }, g); return; }
      var path = pts.map(function (p, i) { var q = toP(p); return (i ? 'L' : 'M') + q.x + ',' + q.y; }).join(' ') + (d.mode === 'POLYGON' && pts.length > 2 ? ' Z' : '');
      svg('path', { d: path, 'class': 'wb-map-drawshape' + (d.mode === 'POLYGON' ? '' : ' wb-map-drawline') }, g);
      d.pts.forEach(function (p) { var q = toP(p); svg('circle', { cx: q.x, cy: q.y, r: 4, 'class': 'wb-map-drawvertex' }, g); });
    }
  });
})();
