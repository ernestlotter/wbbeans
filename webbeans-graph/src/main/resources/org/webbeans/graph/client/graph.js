/*
 * wb.GraphScene — the SVG renderer of org.webbeans.graph.GraphScene. The server owns the model: node, edge and
 * note descriptions arrive through "graph" calls (id → description, null removes; "reset" replaces all) and the
 * browser draws them: shapes (rect, rounded, ellipse, diamond, hexagon, parallelogram, cylinder, document, icon,
 * custom SVG), labels, icons, accent bands, pins (rows inside the node or sockets on a side), edges with anchors
 * (boundary, centre, circular, fixed side, side-facing), routers (direct, orthogonal with rounded corners,
 * curved, free through control points), end shapes (arrow, triangles, diamonds, circles, bar), labels along
 * the path, notes with a folded corner and a dashed connector, a dotted grid, a minimap. Interaction: pan
 * (drag the background, middle button), wheel zoom at the pointer, drag nodes/notes (multi-selection moves
 * together, snap to grid), click / Ctrl-click / Shift-drag marquee selection, connect by dragging from a
 * node's handle or a pin, double-click, right-click, F2 / double-click in-place editing, Delete, arrow nudges.
 * Node sizes are measured from the content and reported back ("measure") so server-side layouts are exact.
 */
(function () {
  var NS = 'http://www.w3.org/2000/svg';
  var MIME = 'text/x-wb-palette';
  var PAD_X = 12, PAD_Y = 8, ICON = 18, PIN_H = 20, PIN_DOT = 4, MIN_W = 48, MIN_H = 30, ACCENT_H = 5;

  function svg(tag, attrs, parent) {
    var e = document.createElementNS(NS, tag);
    if (attrs) { for (var k in attrs) { if (attrs[k] != null) e.setAttribute(k, attrs[k]); } }
    if (parent) parent.appendChild(e);
    return e;
  }
  function num(v, d) { return typeof v === 'number' && isFinite(v) ? v : d; }
  function dist(a, b) { return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y)); }
  function hash(s) { var h = 0; for (var i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0; return (h >>> 0).toString(36); }

  /* ---------------- shapes ---------------- */
  function shapePath(shape, w, h) {
    var r = Math.min(10, w / 4, h / 4);
    switch (shape) {
      case 'rectangle': return 'M0,0 H' + w + ' V' + h + ' H0 Z';
      case 'ellipse': return 'M' + (w / 2) + ',0 A' + (w / 2) + ',' + (h / 2) + ' 0 1 1 ' + (w / 2) + ',' + h + ' A' + (w / 2) + ',' + (h / 2) + ' 0 1 1 ' + (w / 2) + ',0 Z';
      case 'diamond': return 'M' + (w / 2) + ',0 L' + w + ',' + (h / 2) + ' L' + (w / 2) + ',' + h + ' L0,' + (h / 2) + ' Z';
      case 'hexagon': { var c = Math.min(h / 2, w / 4); return 'M' + c + ',0 H' + (w - c) + ' L' + w + ',' + (h / 2) + ' L' + (w - c) + ',' + h + ' H' + c + ' L0,' + (h / 2) + ' Z'; }
      case 'parallelogram': { var s = Math.min(h / 2, w / 4); return 'M' + s + ',0 H' + w + ' L' + (w - s) + ',' + h + ' H0 Z'; }
      case 'cylinder': { var ry = Math.min(8, h / 5); return 'M0,' + ry + ' A' + (w / 2) + ',' + ry + ' 0 0 1 ' + w + ',' + ry + ' V' + (h - ry) + ' A' + (w / 2) + ',' + ry + ' 0 0 1 0,' + (h - ry) + ' Z M0,' + ry + ' A' + (w / 2) + ',' + ry + ' 0 0 0 ' + w + ',' + ry; }
      case 'document': { var f = Math.min(10, h / 4); return 'M0,0 H' + w + ' V' + (h - f) + ' Q' + (w * 0.75) + ',' + (h - 2 * f) + ' ' + (w / 2) + ',' + (h - f) + ' T0,' + h + ' Z'; }
      default: return 'M' + r + ',0 H' + (w - r) + ' Q' + w + ',0 ' + w + ',' + r + ' V' + (h - r) + ' Q' + w + ',' + h + ' ' + (w - r) + ',' + h + ' H' + r + ' Q0,' + h + ' 0,' + (h - r) + ' V' + r + ' Q0,0 ' + r + ',0 Z';
    }
  }
  /* the point where a ray from the node centre towards (tx,ty) crosses the outline */
  function border(n, tx, ty) {
    var b = bounds(n); var cx = b.x + b.w / 2, cy = b.y + b.h / 2; var dx = tx - cx, dy = ty - cy;
    if (!dx && !dy) return { x: cx, y: cy };
    if (n.sh === 'ellipse') { var a = Math.atan2(dy / (b.h / 2), dx / (b.w / 2)); return { x: cx + b.w / 2 * Math.cos(a), y: cy + b.h / 2 * Math.sin(a) }; }
    if (n.sh === 'diamond') { var s = Math.abs(dx) / (b.w / 2) + Math.abs(dy) / (b.h / 2); return { x: cx + dx / s, y: cy + dy / s }; }
    var k = Math.max(Math.abs(dx) / (b.w / 2), Math.abs(dy) / (b.h / 2)); return { x: cx + dx / k, y: cy + dy / k };
  }

  /* ---------------- geometry of the model ---------------- */
  function sizeOf(el, id) { var n = el.nodes[id]; if (!n) return { w: MIN_W, h: MIN_H }; if (n.w != null && n.h != null) return { w: n.w, h: n.h }; var m = el.sizes[id]; return m || { w: MIN_W, h: MIN_H }; }
  function bounds(n) { var s = n.__size || { w: MIN_W, h: MIN_H }; return { x: n.x - s.w / 2, y: n.y - s.h / 2, w: s.w, h: s.h }; }
  function noteBounds(el, id) { var n = el.notes[id]; var h = (el.noteHeights[id] || 40); return { x: n.x, y: n.y, w: n.w || 160, h: h }; }
  function widgetBounds(el, id) {
    if (el.nodes[id]) return bounds(el.nodes[id]);
    if (el.notes[id]) return noteBounds(el, id);
    var slash = id.indexOf('/');
    if (slash > 0 && el.nodes[id.slice(0, slash)]) { var p = pinPoint(el, el.nodes[id.slice(0, slash)], id.slice(slash + 1), null); return p ? { x: p.x - 4, y: p.y - 4, w: 8, h: 8 } : null; }
    var e = el.edges[id]; if (e && e.__mid) return { x: e.__mid.x - 2, y: e.__mid.y - 2, w: 4, h: 4 };
    return null;
  }
  /* where a pin sits on its node and which way it faces; `towards` picks left/right for AUTO pins */
  function pinPoint(el, n, pinId, towards) {
    var b = bounds(n); var pins = n.pins || []; var idx = -1, pin = null;
    for (var i = 0; i < pins.length; i++) if (pins[i].id === pinId) { idx = i; pin = pins[i]; }
    if (!pin) return null;
    var side = pin.side || 'auto';
    if (side === 'auto') {
      var y = b.y + (n.__headerH || MIN_H) + idx * PIN_H + PIN_H / 2 - (n.__autoPins ? 0 : 0);
      var right = towards ? towards.x > n.x : false;
      return { x: right ? b.x + b.w : b.x, y: y, dir: right ? 'r' : 'l' };
    }
    var same = pins.filter(function (p) { return (p.side || 'auto') === side; }); var k = same.indexOf(pin); var count = same.length;
    var t = (k + 1) / (count + 1);
    if (side === 'left') return { x: b.x, y: b.y + b.h * t, dir: 'l' };
    if (side === 'right') return { x: b.x + b.w, y: b.y + b.h * t, dir: 'r' };
    if (side === 'top') return { x: b.x + b.w * t, y: b.y, dir: 't' };
    return { x: b.x + b.w * t, y: b.y + b.h, dir: 'b' };
  }
  /* the anchor point of an edge end */
  function anchorPoint(el, end, kind, other) {
    if (!end) return null;
    var n = el.nodes[end.n]; if (!n) return null;
    if (end.p) { var pp = pinPoint(el, n, end.p, other); if (pp) return pp; }
    var b = bounds(n); var cx = b.x + b.w / 2, cy = b.y + b.h / 2; other = other || { x: cx + 1, y: cy };
    switch (kind || 'boundary') {
      case 'center': return { x: cx, y: cy, dir: null };
      case 'circular': { var r = Math.min(b.w, b.h) / 2; var a = Math.atan2(other.y - cy, other.x - cx); return { x: cx + r * Math.cos(a), y: cy + r * Math.sin(a), dir: null }; }
      case 'left': return { x: b.x, y: cy, dir: 'l' };
      case 'right': return { x: b.x + b.w, y: cy, dir: 'r' };
      case 'top': return { x: cx, y: b.y, dir: 't' };
      case 'bottom': return { x: cx, y: b.y + b.h, dir: 'b' };
      case 'side': return other.x >= cx ? { x: b.x + b.w, y: cy, dir: 'r' } : { x: b.x, y: cy, dir: 'l' };
      default: { var p = border(n, other.x, other.y); var dx = p.x - cx, dy = p.y - cy; p.dir = Math.abs(dx) / b.w > Math.abs(dy) / b.h ? (dx > 0 ? 'r' : 'l') : (dy > 0 ? 'b' : 't'); return p; }
    }
  }

  /* ---------------- routers ---------------- */
  function dirVec(d) { return d === 'l' ? { x: -1, y: 0 } : d === 'r' ? { x: 1, y: 0 } : d === 't' ? { x: 0, y: -1 } : d === 'b' ? { x: 0, y: 1 } : null; }
  function orthoPoints(a, b) {
    var da = a.dir || (Math.abs(b.x - a.x) >= Math.abs(b.y - a.y) ? (b.x > a.x ? 'r' : 'l') : (b.y > a.y ? 'b' : 't'));
    var db = b.dir || (Math.abs(b.x - a.x) >= Math.abs(b.y - a.y) ? (b.x > a.x ? 'l' : 'r') : (b.y > a.y ? 't' : 'b'));
    var ha = da === 'l' || da === 'r', hb = db === 'l' || db === 'r'; var pts = [a];
    var stub = 16;
    if (ha && hb) {
      var ax = a.x + (da === 'r' ? stub : -stub), bx = b.x + (db === 'r' ? stub : -stub);
      var ok = (da === 'r' ? bx >= ax : bx <= ax);
      if (ok) { var mx = (ax + bx) / 2; pts.push({ x: mx, y: a.y }, { x: mx, y: b.y }); }
      else { var my = (a.y + b.y) / 2; pts.push({ x: ax, y: a.y }, { x: ax, y: my }, { x: bx, y: my }, { x: bx, y: b.y }); }
    } else if (!ha && !hb) {
      var ay = a.y + (da === 'b' ? stub : -stub), by = b.y + (db === 'b' ? stub : -stub);
      var oky = (da === 'b' ? by >= ay : by <= ay);
      if (oky) { var my2 = (ay + by) / 2; pts.push({ x: a.x, y: my2 }, { x: b.x, y: my2 }); }
      else { var mx2 = (a.x + b.x) / 2; pts.push({ x: a.x, y: ay }, { x: mx2, y: ay }, { x: mx2, y: by }, { x: b.x, y: by }); }
    } else if (ha) { pts.push({ x: b.x, y: a.y }); } else { pts.push({ x: a.x, y: b.y }); }
    pts.push(b);
    // drop collinear duplicates
    var out = [pts[0]];
    for (var i = 1; i < pts.length; i++) { var p = pts[i], q = out[out.length - 1]; if (Math.abs(p.x - q.x) > 0.01 || Math.abs(p.y - q.y) > 0.01) out.push(p); }
    return out;
  }
  function polyPath(pts, radius) {
    if (pts.length < 2) return '';
    var d = 'M' + pts[0].x + ',' + pts[0].y;
    for (var i = 1; i < pts.length - 1; i++) {
      var p = pts[i], prev = pts[i - 1], next = pts[i + 1];
      var r = Math.min(radius || 0, dist(p, prev) / 2, dist(p, next) / 2);
      if (r < 1) { d += ' L' + p.x + ',' + p.y; continue; }
      var v1 = { x: (prev.x - p.x) / dist(p, prev), y: (prev.y - p.y) / dist(p, prev) }, v2 = { x: (next.x - p.x) / dist(p, next), y: (next.y - p.y) / dist(p, next) };
      d += ' L' + (p.x + v1.x * r) + ',' + (p.y + v1.y * r) + ' Q' + p.x + ',' + p.y + ' ' + (p.x + v2.x * r) + ',' + (p.y + v2.y * r);
    }
    d += ' L' + pts[pts.length - 1].x + ',' + pts[pts.length - 1].y;
    return d;
  }
  function curvedPath(a, b) {
    var d = Math.max(40, dist(a, b) / 2); var va = dirVec(a.dir), vb = dirVec(b.dir);
    if (!va) va = Math.abs(b.x - a.x) >= Math.abs(b.y - a.y) ? { x: b.x > a.x ? 1 : -1, y: 0 } : { x: 0, y: b.y > a.y ? 1 : -1 };
    if (!vb) vb = { x: -va.x, y: -va.y };
    return 'M' + a.x + ',' + a.y + ' C' + (a.x + va.x * d) + ',' + (a.y + va.y * d) + ' ' + (b.x + vb.x * d) + ',' + (b.y + vb.y * d) + ' ' + b.x + ',' + b.y;
  }

  /* ---------------- markers ---------------- */
  function markerId(el, shape, color, start) {
    var key = shape + '|' + (color || '') + '|' + (start ? 's' : 'e'); if (el.markers[key]) return el.markers[key];
    var id = 'wb-g-' + el.ctx.id + '-' + hash(key); var m = svg('marker', { id: id, viewBox: '0 0 12 12', refX: shape === 'bar' ? 6 : 11, refY: 6, markerWidth: 10, markerHeight: 10, markerUnits: 'userSpaceOnUse', orient: start ? 'auto-start-reverse' : 'auto' }, el.defs);
    var hollow = /hollow/.test(shape); var cls = 'wb-graph-marker' + (hollow ? ' wb-graph-marker-hollow' : '');
    var p;
    switch (shape.replace('_hollow', '')) {
      case 'arrow': p = svg('path', { d: 'M1,1 L11,6 L1,11', 'class': 'wb-graph-marker wb-graph-marker-open' }, m); if (color) p.style.stroke = color; return el.markers[key] = id;
      case 'triangle': p = svg('path', { d: 'M1,1 L11,6 L1,11 Z', 'class': cls }, m); break;
      case 'diamond': p = svg('path', { d: 'M1,6 L6,1 L11,6 L6,11 Z', 'class': cls }, m); break;
      case 'circle': p = svg('circle', { cx: 6, cy: 6, r: 4.5, 'class': cls }, m); break;
      case 'bar': p = svg('path', { d: 'M6,1 V11', 'class': 'wb-graph-marker wb-graph-marker-open' }, m); break;
      default: p = svg('path', { d: 'M1,1 L11,6 L1,11', 'class': 'wb-graph-marker wb-graph-marker-open' }, m);
    }
    if (color) { p.style.stroke = color; if (!hollow) p.style.fill = color; }
    return el.markers[key] = id;
  }

  /* ---------------- text measuring ---------------- */
  function textWidth(el, text, cls) {
    var key = cls + '|' + text; if (el.textCache[key] != null) return el.textCache[key];
    var t = svg('text', { 'class': cls, x: -9999, y: -9999 }, el.measureG); t.textContent = text; var w = t.getComputedTextLength(); t.remove();
    return el.textCache[key] = w;
  }
  function wrap(el, text, width, cls) {
    var lines = []; String(text == null ? '' : text).split('\n').forEach(function (para) {
      var words = para.split(/\s+/), line = '';
      words.forEach(function (w) { var trial = line ? line + ' ' + w : w; if (textWidth(el, trial, cls) > width && line) { lines.push(line); line = w; } else line = trial; });
      lines.push(line);
    });
    return lines;
  }

  /* ---------------- the renderer ---------------- */
  wb.register('wb.GraphScene', {
    create: function (ctx) {
      var el = wb.h('div', 'wb-graph'); el.tabIndex = 0; el.ctx = ctx; el.props = {};
      el.nodes = {}; el.edges = {}; el.notes = {}; el.sizes = {}; el.noteHeights = {}; el.sel = new Set(); el.view = { x: 20, y: 20, k: 1 }; el.markers = {}; el.textCache = {}; el.g = {}; el.links = {}; el.anims = {}; el.reported = {};
      el.svg = svg('svg', { 'class': 'wb-graph-svg' }, el); el.defs = svg('defs', null, el.svg);
      el.gridPat = svg('pattern', { id: 'wb-g-grid-' + ctx.id, width: 20, height: 20, patternUnits: 'userSpaceOnUse' }, el.defs); el.gridDot = svg('circle', { cx: 1, cy: 1, r: 1, 'class': 'wb-graph-griddot' }, el.gridPat);
      el.bg = svg('rect', { 'class': 'wb-graph-bg', x: -1e5, y: -1e5, width: 2e5, height: 2e5 }, el.svg);
      el.world = svg('g', { 'class': 'wb-graph-world' }, el.svg);
      el.gridRect = svg('rect', { 'class': 'wb-graph-grid', x: -1e5, y: -1e5, width: 2e5, height: 2e5, fill: 'url(#wb-g-grid-' + ctx.id + ')' }, el.world);
      el.linksG = svg('g', { 'class': 'wb-graph-notelinks' }, el.world); el.edgesG = svg('g', { 'class': 'wb-graph-edges' }, el.world); el.nodesG = svg('g', { 'class': 'wb-graph-nodes' }, el.world); el.notesG = svg('g', { 'class': 'wb-graph-notes' }, el.world); el.overlayG = svg('g', { 'class': 'wb-graph-overlay' }, el.world);
      el.measureG = svg('g', { 'class': 'wb-graph-measure', visibility: 'hidden' }, el.svg);
      el.minimap = wb.h('div', 'wb-graph-minimap wb-hidden'); el.appendChild(el.minimap); el.miniSvg = svg('svg', { 'class': 'wb-graph-minisvg' }, el.minimap);
      el.toScene = function (cx, cy) { var r = el.svg.getBoundingClientRect(); return { x: (cx - r.left - el.view.x) / el.view.k, y: (cy - r.top - el.view.y) / el.view.k }; };
      var self = this;
      el.svg.addEventListener('wheel', function (e) { e.preventDefault(); var r = el.svg.getBoundingClientRect(); self.zoomAt(el, e.deltaY < 0 ? 1.1 : 1 / 1.1, e.clientX - r.left, e.clientY - r.top); }, { passive: false });
      el.svg.addEventListener('mousedown', function (e) { self.onDown(el, e); });
      el.svg.addEventListener('dblclick', function (e) { self.onDouble(el, e); });
      el.svg.addEventListener('contextmenu', function (e) { self.onMenu(el, e); });
      el.svg.addEventListener('mouseover', function (e) { self.onHover(el, e); });
      el.svg.addEventListener('mouseleave', function () { if (el.hoverId !== null) { el.hoverId = null; ctx.emit('hover', {}); } });
      el.addEventListener('keydown', function (e) { self.onKey(el, e); });
      el.addEventListener('dragover', function (e) { if (el.props.dropTarget && Array.prototype.indexOf.call(e.dataTransfer.types, MIME) >= 0) { e.preventDefault(); e.dataTransfer.dropEffect = 'copy'; el.classList.add('wb-graph-over'); } });
      el.addEventListener('dragleave', function () { el.classList.remove('wb-graph-over'); });
      el.addEventListener('drop', function (e) { el.classList.remove('wb-graph-over'); var item = e.dataTransfer.getData(MIME); if (!item) return; e.preventDefault(); var p = self.snap(el, el.toScene(e.clientX, e.clientY)); ctx.emit('drop', { item: item, sx: p.x, sy: p.y, x: e.clientX, y: e.clientY }); });
      if (typeof ResizeObserver !== 'undefined') { el.ro = new ResizeObserver(function () { if (el.pendingFit && el.clientWidth > 0) { el.pendingFit = false; self.fit(el); } self.paintMinimap(el); }); el.ro.observe(el); }
      el.minimap.addEventListener('mousedown', function (e) { e.preventDefault(); var mv = function (m) { self.minimapPan(el, m); }; mv(e); var up = function () { document.removeEventListener('mousemove', mv); document.removeEventListener('mouseup', up); }; document.addEventListener('mousemove', mv); document.addEventListener('mouseup', up); });
      return el;
    },
    update: function (el, props, changed) {
      el.props = props; wb.size(el, props, changed);
      var g = num(props.grid, 10); el.gridPat.setAttribute('width', g || 20); el.gridPat.setAttribute('height', g || 20); el.gridRect.style.display = props.showGrid === false || !g ? 'none' : '';
      el.minimap.classList.toggle('wb-hidden', !props.minimap);
      el.classList.toggle('wb-graph-connectable', !!props.connectable);
      if (changed.has('router')) this.schedule(el);
    },
    call: function (el, m, args) {
      switch (m) {
        case 'graph': this.applyGraph(el, args[0] || {}); break;
        case 'select': el.sel = new Set(args[0] || []); this.paintSel(el); break;
        case 'view': this.viewCall(el, args); break;
        case 'export': this.doExport(el, args[0], args[1], args[2], args[3]); break;
        case 'download': this.download(el, args[0], args[1]); break;
      }
    },
    insertChild: function () {}, removeChild: function () {},
    destroy: function (el) { if (el.ro) el.ro.disconnect(); if (el.raf) cancelAnimationFrame(el.raf); if (el.animRaf) cancelAnimationFrame(el.animRaf); },

    /* ---------------- model ---------------- */
    applyGraph: function (el, d) {
      var self = this;
      if (d.reset) { el.nodes = {}; el.edges = {}; el.notes = {}; el.anims = {}; el.sel = new Set(); el.sizes = {}; el.reported = {}; Object.keys(el.g).forEach(function (id) { el.g[id].remove(); }); el.g = {}; Object.keys(el.links).forEach(function (id) { el.links[id].remove(); }); el.links = {}; }
      var first = !el.everDrawn;
      ['nodes', 'edges', 'notes'].forEach(function (kind) {
        var m = d[kind]; if (!m) return;
        Object.keys(m).forEach(function (id) {
          var spec = m[id];
          if (spec == null) { delete el[kind][id]; delete el.anims[id]; if (el.g[id]) { el.g[id].remove(); delete el.g[id]; } if (el.links[id]) { el.links[id].remove(); delete el.links[id]; } el.sel.delete(id); return; }
          var old = el[kind][id];
          if (kind === 'nodes' && old && spec.anim && el.props.animation !== false && (old.x !== spec.x || old.y !== spec.y)) { el.anims[id] = { fx: old.x, fy: old.y, tx: spec.x, ty: spec.y, t0: performance.now() }; spec.__ax = old.x; spec.__ay = old.y; }
          if (kind === 'nodes' && old) { spec.__size = old.__size; spec.__headerH = old.__headerH; }
          el[kind][id] = spec;
        });
      });
      if (first && Object.keys(el.nodes).length) { el.pendingFit = true; }
      el.everDrawn = true;
      this.schedule(el);
      if (Object.keys(el.anims).length) this.animate(el);
    },
    animate: function (el) {
      var self = this; if (el.animRaf) return;
      var step = function (now) {
        el.animRaf = null; var live = false;
        Object.keys(el.anims).forEach(function (id) {
          var a = el.anims[id], n = el.nodes[id]; if (!n) { delete el.anims[id]; return; }
          var t = Math.min(1, (now - a.t0) / 320); var e = 1 - Math.pow(1 - t, 3);
          n.__ax = a.fx + (a.tx - a.fx) * e; n.__ay = a.fy + (a.ty - a.fy) * e;
          if (t >= 1) { delete el.anims[id]; delete n.__ax; delete n.__ay; } else live = true;
        });
        self.render(el);
        if (live) el.animRaf = requestAnimationFrame(step);
      };
      el.animRaf = requestAnimationFrame(step);
    },
    schedule: function (el) { var self = this; if (el.raf) return; el.raf = requestAnimationFrame(function () { el.raf = null; self.render(el); }); },

    /* ---------------- rendering ---------------- */
    render: function (el) {
      var self = this; var measures = {};
      if (!el.isConnected) { setTimeout(function () { self.schedule(el); }, 50); return; }   // text can only be measured in the document
      Object.keys(el.nodes).forEach(function (id) { self.renderNode(el, id, measures); });
      Object.keys(el.g).forEach(function (id) { if (!el.nodes[id] && !el.edges[id] && !el.notes[id]) { el.g[id].remove(); delete el.g[id]; } });
      Object.keys(el.notes).forEach(function (id) { self.renderNote(el, id); });
      Object.keys(el.edges).forEach(function (id) { self.renderEdge(el, id); });
      Object.keys(el.notes).forEach(function (id) { self.renderNoteLink(el, id); });
      Object.keys(el.links).forEach(function (id) { if (!el.notes[id]) { el.links[id].remove(); delete el.links[id]; } });
      this.paintSel(el); if (el.pendingFit && el.clientWidth) { el.pendingFit = false; this.fit(el); } this.applyView(el);
      if (Object.keys(measures).length) el.ctx.emit('measure', { sizes: measures });
      this.paintMinimap(el);
    },
    styleWidget: function (g, spec) {
      var cls = g.dataset.baseClass + (spec.st ? ' ' + spec.st : '') + (spec.vis === false ? ' wb-hidden' : '') + (spec.mv === false ? ' wb-graph-locked' : '') + (spec.sel === false ? ' wb-graph-unselectable' : '');
      if (g.classList.contains('wb-selected')) cls += ' wb-selected'; if (g.classList.contains('wb-graph-target')) cls += ' wb-graph-target';
      g.setAttribute('class', cls);
      g.style.opacity = spec.op != null ? spec.op : '';
      (g.__attrs || []).forEach(function (k) { g.removeAttribute('data-' + k); g.style.removeProperty('--wb-g-' + k); }); g.__attrs = [];
      if (spec.a) Object.keys(spec.a).forEach(function (k) { g.setAttribute('data-' + k, spec.a[k]); g.style.setProperty('--wb-g-' + k, spec.a[k]); g.__attrs.push(k); });
      var tt = g.querySelector(':scope > title'); if (spec.tt) { if (!tt) { tt = svg('title'); g.insertBefore(tt, g.firstChild); } tt.textContent = spec.tt; } else if (tt) tt.remove();
    },
    renderNode: function (el, id, measures) {
      var n = el.nodes[id]; var g = el.g[id];
      if (!g) { g = svg('g', { 'class': 'wb-graph-node', 'data-id': id }, el.nodesG); g.dataset.baseClass = 'wb-graph-node'; el.g[id] = g; }
      var sig = JSON.stringify([n.l, n.l2, n.i, n.ip, n.sh, n.pins, n.w, n.h, n.ac, n.svg, n.bd]);
      var x = n.__ax != null ? n.__ax : n.x, y = n.__ay != null ? n.__ay : n.y;
      if (g.dataset.sig !== sig) {
        g.dataset.sig = sig; while (g.lastChild) g.removeChild(g.lastChild);
        var pins = n.pins || []; var autoPins = pins.filter(function (p) { return !p.side || p.side === 'auto'; });
        var lw = n.l ? textWidth(el, n.l, 'wb-graph-label') : 0, l2w = n.l2 ? textWidth(el, n.l2, 'wb-graph-label2') : 0;
        var iconLeft = n.i && n.ip !== 'top', iconTop = n.i && n.ip === 'top';
        var headerW = Math.max(lw, l2w) + (iconLeft ? ICON + 6 : 0) + 2 * PAD_X;
        var headerH = (n.l ? 16 : 0) + (n.l2 ? 14 : 0) + (iconTop ? ICON + 4 : 0) + 2 * PAD_Y; if (iconLeft) headerH = Math.max(headerH, ICON + 2 * PAD_Y);
        var pinW = 0; autoPins.forEach(function (p) { pinW = Math.max(pinW, textWidth(el, p.l || p.id, 'wb-graph-pinlabel') + 2 * PAD_X + 2 * PIN_DOT + 8 + (p.i ? ICON : 0)); });
        var w = Math.max(MIN_W, headerW, pinW), h = Math.max(MIN_H, headerH + autoPins.length * PIN_H + (autoPins.length ? 4 : 0));
        if (n.sh === 'diamond' || n.sh === 'hexagon' || n.sh === 'parallelogram') { w += w * 0.4; }
        if (n.sh === 'ellipse') { w += 16; h += 10; }
        if (n.sh === 'icon') { w = Math.max(ICON * 2, lw) + 8; h = ICON * 2 + (n.l ? 18 : 0); }
        if (n.w != null && n.h != null) { w = n.w; h = n.h; }
        if (n.ac) headerH += ACCENT_H;
        n.__size = { w: w, h: h }; n.__headerH = headerH; n.__autoPins = autoPins.length;
        if (n.w == null || n.h == null) { var prev = el.reported[id]; if (!prev || prev.w !== w || prev.h !== h) { el.reported[id] = { w: w, h: h }; measures[id] = [w, h]; } }
        // outline
        var shape;
        if (n.sh === 'custom' && n.svg) { shape = svg('g', { 'class': 'wb-graph-shape wb-graph-custom' }); shape.innerHTML = String(n.svg).replace(/\{w\}/g, w).replace(/\{h\}/g, h); }
        else if (n.sh === 'icon') { shape = svg('rect', { 'class': 'wb-graph-shape wb-graph-shape-icon', width: w, height: h, rx: 4 }); }
        else { shape = svg('path', { 'class': 'wb-graph-shape wb-graph-shape-' + (n.sh || 'rounded'), d: shapePath(n.sh || 'rounded', w, h) }); }
        if (n.bg) shape.style.fill = n.bg; if (n.fg) shape.style.stroke = n.fg;
        shape.classList.add('wb-graph-border-' + (n.bd || 'line'));
        g.appendChild(shape);
        if (n.ac && n.sh !== 'icon') {
          // the band is the top slice of the outline itself, so it follows hexagons, ellipses and cylinders
          var clipId = 'wb-g-clip-' + el.ctx.id + '-' + hash(id); var old = el.defs.querySelector('#' + clipId); if (old) old.remove();
          var clip = svg('clipPath', { id: clipId }, el.defs); var cp = shape.cloneNode(true); cp.removeAttribute('class'); cp.removeAttribute('style'); clip.appendChild(cp);
          var band = svg('rect', { 'class': 'wb-graph-accent', x: 0, y: 0, width: w, height: ACCENT_H + 1, 'clip-path': 'url(#' + clipId + ')' }, g); band.style.fill = n.ac;
        }
        // content
        var cx = w / 2; var ty = (n.ac ? ACCENT_H : 0) + PAD_Y;
        if (n.sh === 'icon') {
          if (n.i) { var ic = svg('image', { x: cx - ICON, y: 2, width: ICON * 2, height: ICON * 2 }, g); ic.setAttribute('href', wb.res(n.i)); }
          if (n.l) { var lt = svg('text', { 'class': 'wb-graph-label', x: cx, y: ICON * 2 + 12, 'text-anchor': 'middle' }, g); lt.textContent = n.l; if (n.fg) lt.style.fill = n.fg; }
        } else {
          var textX = cx;
          if (iconLeft) { var leftX = cx - headerW / 2 + PAD_X; var img = svg('image', { x: leftX, y: ty + ((n.l ? 16 : 0) + (n.l2 ? 14 : 0)) / 2 - ICON / 2, width: ICON, height: ICON }, g); img.setAttribute('href', wb.res(n.i)); textX = leftX + ICON + 6 + Math.max(lw, l2w) / 2; }
          if (iconTop) { var img2 = svg('image', { x: cx - ICON / 2, y: ty, width: ICON, height: ICON }, g); img2.setAttribute('href', wb.res(n.i)); ty += ICON + 4; }
          if (n.l) { var t1 = svg('text', { 'class': 'wb-graph-label', x: textX, y: ty + 12, 'text-anchor': 'middle' }, g); t1.textContent = n.l; if (n.fg) t1.style.fill = n.fg; ty += 16; }
          if (n.l2) { var t2 = svg('text', { 'class': 'wb-graph-label2', x: textX, y: ty + 10, 'text-anchor': 'middle' }, g); t2.textContent = n.l2; if (n.fg) t2.style.fill = n.fg; }
          if (autoPins.length) { svg('path', { 'class': 'wb-graph-pinsep', d: 'M1,' + headerH + ' H' + (w - 1) }, g); }
          autoPins.forEach(function (p, i) {
            var py = headerH + i * PIN_H + PIN_H / 2; var pg = svg('g', { 'class': 'wb-graph-pin' + (p.st ? ' ' + p.st : ''), 'data-id': id + '/' + p.id }, g);
            svg('rect', { 'class': 'wb-graph-pinrow', x: 1, y: py - PIN_H / 2, width: w - 2, height: PIN_H }, pg);
            svg('circle', { 'class': 'wb-graph-pindot', cx: 0, cy: py, r: PIN_DOT }, pg); svg('circle', { 'class': 'wb-graph-pindot', cx: w, cy: py, r: PIN_DOT }, pg);
            var px = PAD_X; if (p.i) { var pi = svg('image', { x: px, y: py - 7, width: 14, height: 14 }, pg); pi.setAttribute('href', wb.res(p.i)); px += ICON; }
            var pt = svg('text', { 'class': 'wb-graph-pinlabel', x: px, y: py + 4 }, pg); pt.textContent = p.l || p.id;
            if (p.tt) { var ptt = svg('title', null, pg); ptt.textContent = p.tt; }
            if (p.a) Object.keys(p.a).forEach(function (k) { pg.setAttribute('data-' + k, p.a[k]); });
            if (p.cs) pg.classList.add('wb-graph-pin-source');
          });
          pins.filter(function (p) { return p.side && p.side !== 'auto'; }).forEach(function (p) {
            var pp = pinPoint(el, { x: w / 2, y: h / 2, pins: pins, __size: { w: w, h: h } }, p.id, null);
            var pg2 = svg('g', { 'class': 'wb-graph-pin wb-graph-pin-side' + (p.st ? ' ' + p.st : ''), 'data-id': id + '/' + p.id }, g);
            svg('circle', { 'class': 'wb-graph-pindot', cx: pp.x, cy: pp.y, r: PIN_DOT + 1 }, pg2);
            var tt = svg('title', null, pg2); tt.textContent = p.tt || p.l || p.id;
            if (p.cs) pg2.classList.add('wb-graph-pin-source');
          });
        }
        if (n.cs) { var handle = svg('circle', { 'class': 'wb-graph-connect-handle', cx: w - 2, cy: n.ac ? ACCENT_H + 2 : 2, r: 5 }, g); var ht = svg('title', null, handle); ht.textContent = 'Drag to connect'; }
      }
      this.styleWidget(g, n);
      g.setAttribute('transform', 'translate(' + (x - n.__size.w / 2) + ',' + (y - n.__size.h / 2) + ')');
    },
    renderEdge: function (el, id) {
      var e = el.edges[id]; var g = el.g[id];
      if (!g) { g = svg('g', { 'class': 'wb-graph-edge', 'data-id': id }, el.edgesG); g.dataset.baseClass = 'wb-graph-edge'; el.g[id] = g; svg('path', { 'class': 'wb-graph-edge-hit' }, g); svg('path', { 'class': 'wb-graph-edge-line' }, g); }
      var hit = g.children[g.firstChild && g.firstChild.tagName === 'title' ? 1 : 0], line = hit.nextSibling;
      var sn = e.s && el.nodes[e.s.n], tn = e.t && el.nodes[e.t.n];
      if (!sn || !tn) { g.style.display = 'none'; return; }
      g.style.display = '';
      var sc = { x: sn.__ax != null ? sn.__ax : sn.x, y: sn.__ay != null ? sn.__ay : sn.y }, tc = { x: tn.__ax != null ? tn.__ax : tn.x, y: tn.__ay != null ? tn.__ay : tn.y };
      var snA = Object.assign({}, sn, { x: sc.x, y: sc.y }), tnA = Object.assign({}, tn, { x: tc.x, y: tc.y }); var tmp = { nodes: Object.assign({}, el.nodes) }; tmp.nodes[e.s.n] = snA; tmp.nodes[e.t.n] = tnA;
      var router = e.r || el.props.router || 'direct';
      var cps = (e.cp || []).map(function (p) { return { x: p[0], y: p[1] }; });
      var towardsS = cps.length ? cps[0] : tc, towardsT = cps.length ? cps[cps.length - 1] : sc;
      var a = anchorPoint(tmp, e.s, e.sa, towardsS), b = anchorPoint(tmp, e.t, e.ta, towardsT);
      if (!a || !b) { g.style.display = 'none'; return; }
      var d;
      if (router === 'orthogonal') d = polyPath(orthoPoints(a, b), e.cr == null ? 8 : e.cr);
      else if (router === 'curved') d = curvedPath(a, b);
      else if (router === 'free' && cps.length) d = polyPath([a].concat(cps, [b]), e.cr == null ? 8 : e.cr);
      else d = 'M' + a.x + ',' + a.y + ' L' + b.x + ',' + b.y;
      line.setAttribute('d', d); hit.setAttribute('d', d);
      line.setAttribute('class', 'wb-graph-edge-line wb-graph-ls-' + (e.ls || 'solid')); line.style.strokeWidth = (e.sw || 1.5) + 'px'; line.style.stroke = e.fg || '';
      line.setAttribute('marker-end', e.ts ? 'url(#' + markerId(el, e.ts, e.fg, false) + ')' : ''); line.setAttribute('marker-start', e.ss ? 'url(#' + markerId(el, e.ss, e.fg, true) + ')' : '');
      // labels
      Array.from(g.querySelectorAll('.wb-graph-edgelabel')).forEach(function (x) { x.remove(); });
      var len = line.getTotalLength ? line.getTotalLength() : 0;
      e.__mid = len ? line.getPointAtLength(len / 2) : { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 };
      (e.lb || []).forEach(function (lb) {
        var at = Math.max(0.02, Math.min(0.98, num(lb.at, 0.5))); var p = len ? line.getPointAtLength(len * at) : e.__mid; var p2 = len ? line.getPointAtLength(Math.min(len, len * at + 1)) : b;
        var ang = Math.atan2(p2.y - p.y, p2.x - p.x); var nx = -Math.sin(ang), ny = Math.cos(ang); var off = lb.below ? 12 : -8;
        var tw = textWidth(el, lb.t, 'wb-graph-edgelabel-text'); var lg = svg('g', { 'class': 'wb-graph-edgelabel' }, g);
        svg('rect', { 'class': 'wb-graph-edgelabel-bg', x: p.x + nx * off - tw / 2 - 3, y: p.y + ny * off - 8, width: tw + 6, height: 14, rx: 3 }, lg);
        var t = svg('text', { 'class': 'wb-graph-edgelabel-text', x: p.x + nx * off, y: p.y + ny * off + 3, 'text-anchor': 'middle' }, lg); t.textContent = lb.t; if (e.fg) t.style.fill = e.fg;
      });
      this.styleWidget(g, e);
    },
    renderNote: function (el, id) {
      var n = el.notes[id]; var g = el.g[id];
      if (!g) { g = svg('g', { 'class': 'wb-graph-note', 'data-id': id }, el.notesG); g.dataset.baseClass = 'wb-graph-note'; el.g[id] = g; }
      var sig = JSON.stringify([n.tx, n.w, n.bg, n.fg, n.fs]);
      if (g.dataset.sig !== sig) {
        g.dataset.sig = sig; while (g.lastChild) g.removeChild(g.lastChild);
        var w = n.w || 160; var fs = n.fs || 12; var lh = fs * 1.35; var lines = wrap(el, n.tx, w - 2 * PAD_X, 'wb-graph-notetext'); var h = Math.max(30, lines.length * lh + 2 * PAD_Y + 4);
        el.noteHeights[id] = h; var f = 12;
        var paper = svg('path', { 'class': 'wb-graph-paper', d: 'M0,0 H' + (w - f) + ' L' + w + ',' + f + ' V' + h + ' H0 Z' }, g); if (n.bg) paper.style.fill = n.bg; if (n.fg) paper.style.stroke = n.fg;
        var fold = svg('path', { 'class': 'wb-graph-fold', d: 'M' + (w - f) + ',0 V' + f + ' H' + w }, g); if (n.bg) fold.style.fill = n.bg;
        lines.forEach(function (ln, i) { var t = svg('text', { 'class': 'wb-graph-notetext', x: PAD_X, y: PAD_Y + fs + i * lh }, g); t.style.fontSize = fs + 'px'; t.textContent = ln; if (n.fg) t.style.fill = n.fg; });
      }
      this.styleWidget(g, n);
      g.setAttribute('transform', 'translate(' + n.x + ',' + n.y + ')');
    },
    renderNoteLink: function (el, id) {
      var n = el.notes[id]; var p = el.links[id];
      if (!n || !n.to || !widgetBounds(el, n.to) || n.vis === false) { if (p) { p.remove(); delete el.links[id]; } return; }
      if (!p) { p = svg('path', { 'class': 'wb-graph-notelink' }, el.linksG); el.links[id] = p; }
      var nb = noteBounds(el, id), tb = widgetBounds(el, n.to); var from = { x: nb.x + nb.w / 2, y: nb.y + nb.h / 2 }, to = { x: tb.x + tb.w / 2, y: tb.y + tb.h / 2 };
      var a = border({ x: from.x, y: from.y, __size: { w: nb.w, h: nb.h }, sh: 'rectangle' }, to.x, to.y);
      var b = el.nodes[n.to] ? border(el.nodes[n.to], from.x, from.y) : to;
      p.setAttribute('d', 'M' + a.x + ',' + a.y + ' L' + b.x + ',' + b.y);
    },
    paintSel: function (el) { Object.keys(el.g).forEach(function (id) { if (el.g[id].classList) el.g[id].classList.toggle('wb-selected', el.sel.has(id)); }); },
    applyView: function (el) { el.world.setAttribute('transform', 'translate(' + el.view.x + ',' + el.view.y + ') scale(' + el.view.k + ')'); this.paintMinimap(el); },
    contentBounds: function (el) {
      var b = null; var add = function (r) { if (!r) return; if (!b) b = { x: r.x, y: r.y, x2: r.x + r.w, y2: r.y + r.h }; else { b.x = Math.min(b.x, r.x); b.y = Math.min(b.y, r.y); b.x2 = Math.max(b.x2, r.x + r.w); b.y2 = Math.max(b.y2, r.y + r.h); } };
      Object.keys(el.nodes).forEach(function (id) { if (el.nodes[id].vis !== false) add(bounds(el.nodes[id])); });
      Object.keys(el.notes).forEach(function (id) { if (el.notes[id].vis !== false) add(noteBounds(el, id)); });
      return b ? { x: b.x, y: b.y, w: b.x2 - b.x, h: b.y2 - b.y } : null;
    },
    fit: function (el) {
      var b = this.contentBounds(el); if (!b || !el.clientWidth) return;
      var k = Math.min(1.5, (el.clientWidth - 40) / Math.max(1, b.w), (el.clientHeight - 40) / Math.max(1, b.h)); k = Math.max(0.1, k);
      el.view = { k: k, x: (el.clientWidth - b.w * k) / 2 - b.x * k, y: (el.clientHeight - b.h * k) / 2 - b.y * k }; this.applyView(el);
    },
    zoomAt: function (el, f, px, py) { var k = Math.max(0.1, Math.min(6, el.view.k * f)); var r = k / el.view.k; el.view = { k: k, x: px - (px - el.view.x) * r, y: py - (py - el.view.y) * r }; this.applyView(el); },
    viewCall: function (el, args) {
      if (args[0] === 'fit') { if (el.clientWidth) this.fit(el); else el.pendingFit = true; }
      else if (args[0] === 'zoom') { var f = num(args[1], 1) / el.view.k; this.zoomAt(el, f, el.clientWidth / 2, el.clientHeight / 2); }
      else if (args[0] === 'zoomBy') this.zoomAt(el, num(args[1], 1), el.clientWidth / 2, el.clientHeight / 2);
      else if (args[0] === 'center') { var b = widgetBounds(el, args[1]); if (b) { el.view.x = el.clientWidth / 2 - (b.x + b.w / 2) * el.view.k; el.view.y = el.clientHeight / 2 - (b.y + b.h / 2) * el.view.k; this.applyView(el); } }
    },
    paintMinimap: function (el) {
      if (!el.props.minimap || el.minimap.classList.contains('wb-hidden')) return;
      var b = this.contentBounds(el); var ms = el.miniSvg; while (ms.lastChild) ms.removeChild(ms.lastChild); if (!b) return;
      var W = 160, H = 100; var k = Math.min(W / (b.w + 40), H / (b.h + 40)); el.miniK = k; el.miniB = b;
      var g = svg('g', { transform: 'translate(' + ((W - b.w * k) / 2 - b.x * k) + ',' + ((H - b.h * k) / 2 - b.y * k) + ') scale(' + k + ')' }, ms); el.miniG = g;
      Object.keys(el.nodes).forEach(function (id) { var r = bounds(el.nodes[id]); svg('rect', { 'class': 'wb-graph-mininode', x: r.x, y: r.y, width: r.w, height: r.h }, g); });
      var vx = -el.view.x / el.view.k, vy = -el.view.y / el.view.k; svg('rect', { 'class': 'wb-graph-miniview', x: vx, y: vy, width: el.clientWidth / el.view.k, height: el.clientHeight / el.view.k }, g);
    },
    minimapPan: function (el, e) {
      if (!el.miniB) return; var r = el.miniSvg.getBoundingClientRect(); var W = 160, H = 100, k = el.miniK, b = el.miniB;
      var sx = (e.clientX - r.left - ((W - b.w * k) / 2 - b.x * k)) / k, sy = (e.clientY - r.top - ((H - b.h * k) / 2 - b.y * k)) / k;
      el.view.x = el.clientWidth / 2 - sx * el.view.k; el.view.y = el.clientHeight / 2 - sy * el.view.k; this.applyView(el);
    },
    snap: function (el, p) { var g = num(el.props.grid, 10); if (!g) return p; return { x: Math.round(p.x / g) * g, y: Math.round(p.y / g) * g }; },

    /* ---------------- interaction ---------------- */
    track: function (move, up) { var mu = function (e) { document.removeEventListener('mousemove', move); document.removeEventListener('mouseup', mu); up(e); }; document.addEventListener('mousemove', move); document.addEventListener('mouseup', mu); },
    widgetAt: function (e) { var pin = e.target.closest('.wb-graph-pin'); if (pin) return { id: pin.dataset.id, kind: 'pin', el: pin }; var g = e.target.closest('.wb-graph-node, .wb-graph-edge, .wb-graph-note'); return g ? { id: g.dataset.id, kind: g.classList.contains('wb-graph-node') ? 'node' : g.classList.contains('wb-graph-edge') ? 'edge' : 'note', el: g } : null; },
    reportSel: function (el) { el.ctx.emit('select', { ids: Array.from(el.sel) }); },
    select: function (el, id, toggle) {
      var mode = el.props.selectionMode || 'MULTI'; if (mode === 'NONE') return;
      var spec = el.nodes[id] || el.edges[id] || el.notes[id]; if (spec && spec.sel === false) return;
      var before = Array.from(el.sel).join(',');
      if (toggle && mode === 'MULTI') { if (el.sel.has(id)) el.sel.delete(id); else el.sel.add(id); }
      else if (!el.sel.has(id) || el.sel.size > 1) { el.sel = new Set([id]); }
      if (Array.from(el.sel).join(',') !== before) { this.paintSel(el); this.reportSel(el); }
    },
    clearSel: function (el) { if (el.sel.size) { el.sel = new Set(); this.paintSel(el); this.reportSel(el); } },
    onDown: function (el, e) {
      if (e.button !== 0 && e.button !== 1) return; el.focus();
      if (el.editor) return;
      var w = this.widgetAt(e);
      if (e.target.closest('.wb-graph-connect-handle') && el.props.connectable) { this.startConnect(el, e, w.id); return; }
      if (w && w.kind === 'pin' && el.props.connectable && w.el.classList.contains('wb-graph-pin-source')) { this.startConnect(el, e, w.id); return; }
      if (w && w.kind === 'pin') { w = { id: w.id.slice(0, w.id.indexOf('/')), kind: 'node', el: w.el.closest('.wb-graph-node') }; }
      if (w && e.button === 0) {
        this.select(el, w.id, e.ctrlKey || e.metaKey);
        if ((w.kind === 'node' || w.kind === 'note') && el.sel.has(w.id)) this.startDrag(el, e);
        return;
      }
      if (e.shiftKey && e.button === 0) this.startBand(el, e); else this.startPan(el, e);
    },
    startPan: function (el, e) { e.preventDefault(); var sx = e.clientX, sy = e.clientY, vx = el.view.x, vy = el.view.y; var moved = false; el.classList.add('wb-graph-panning'); var self = this; this.track(function (m) { if (Math.abs(m.clientX - sx) + Math.abs(m.clientY - sy) > 2) moved = true; el.view.x = vx + m.clientX - sx; el.view.y = vy + m.clientY - sy; self.applyView(el); }, function () { el.classList.remove('wb-graph-panning'); if (!moved) self.clearSel(el); }); },
    startBand: function (el, e) {
      e.preventDefault(); var self = this; var s = el.toScene(e.clientX, e.clientY); var rect = svg('rect', { 'class': 'wb-graph-band' }, el.overlayG);
      this.track(function (m) { var p = el.toScene(m.clientX, m.clientY); var x = Math.min(s.x, p.x), y = Math.min(s.y, p.y), w = Math.abs(p.x - s.x), h = Math.abs(p.y - s.y); rect.setAttribute('x', x); rect.setAttribute('y', y); rect.setAttribute('width', w); rect.setAttribute('height', h); },
        function (m) { rect.remove(); var p = el.toScene(m.clientX, m.clientY); var x = Math.min(s.x, p.x), y = Math.min(s.y, p.y), x2 = Math.max(s.x, p.x), y2 = Math.max(s.y, p.y); if (x2 - x < 3 && y2 - y < 3) return;
          var ids = []; Object.keys(el.nodes).forEach(function (id) { var b = bounds(el.nodes[id]); if (b.x >= x && b.y >= y && b.x + b.w <= x2 && b.y + b.h <= y2 && el.nodes[id].sel !== false) ids.push(id); });
          Object.keys(el.notes).forEach(function (id) { var b = noteBounds(el, id); if (b.x >= x && b.y >= y && b.x + b.w <= x2 && b.y + b.h <= y2) ids.push(id); });
          if (el.props.selectionMode === 'SINGLE') ids = ids.slice(0, 1);
          el.sel = new Set(ids); self.paintSel(el); self.reportSel(el); });
    },
    startDrag: function (el, e) {
      e.preventDefault(); var self = this; var ids = Array.from(el.sel).filter(function (id) { var s = el.nodes[id] || el.notes[id]; return s && s.mv !== false && !(id in el.anims); });
      if (!ids.length) return;
      var start = { x: e.clientX, y: e.clientY }; var orig = {}; ids.forEach(function (id) { var s = el.nodes[id] || el.notes[id]; orig[id] = { x: s.x, y: s.y }; }); var moved = false;
      el.classList.add('wb-graph-dragging');
      this.track(function (m) {
        var dx = (m.clientX - start.x) / el.view.k, dy = (m.clientY - start.y) / el.view.k; if (!moved && Math.abs(dx) + Math.abs(dy) < 2) return; moved = true;
        ids.forEach(function (id) { var s = el.nodes[id] || el.notes[id]; var p = self.snap(el, { x: orig[id].x + dx, y: orig[id].y + dy }); s.x = p.x; s.y = p.y; });
        self.render(el);
      }, function () {
        el.classList.remove('wb-graph-dragging'); if (!moved) return;
        var moves = {}; ids.forEach(function (id) { var s = el.nodes[id] || el.notes[id]; moves[id] = [s.x, s.y]; }); el.ctx.emit('move', { moves: moves });
      });
    },
    startConnect: function (el, e, sourceId) {
      e.preventDefault(); e.stopPropagation(); var self = this; var from = widgetBounds(el, sourceId); var a = { x: from.x + from.w / 2, y: from.y + from.h / 2 };
      var line = svg('path', { 'class': 'wb-graph-connectline' }, el.overlayG); var target = null;
      el.classList.add('wb-graph-connecting');
      this.track(function (m) {
        var p = el.toScene(m.clientX, m.clientY); line.setAttribute('d', 'M' + a.x + ',' + a.y + ' L' + p.x + ',' + p.y);
        var w = self.widgetAt(m); var tid = w && (w.kind === 'node' || w.kind === 'pin') ? w.id : null; if (tid === sourceId) tid = null;
        if (tid !== target) { if (target && el.g[target.split('/')[0]]) el.g[target.split('/')[0]].classList.remove('wb-graph-target'); target = tid; if (target && el.g[target.split('/')[0]]) el.g[target.split('/')[0]].classList.add('wb-graph-target'); }
      }, function () {
        line.remove(); el.classList.remove('wb-graph-connecting'); if (target && el.g[target.split('/')[0]]) el.g[target.split('/')[0]].classList.remove('wb-graph-target');
        if (target) el.ctx.emit('connect', { source: sourceId, target: target });
      });
    },
    onDouble: function (el, e) {
      var w = this.widgetAt(e); if (!w) return; if (w.kind === 'pin') w = { id: w.id.slice(0, w.id.indexOf('/')), kind: 'node' };
      var spec = el.nodes[w.id] || el.notes[w.id];
      if (spec && spec.ed) { this.edit(el, w.id); return; }
      var p = el.toScene(e.clientX, e.clientY); el.ctx.emit('activate', { id: w.id, x: e.clientX, y: e.clientY, sx: p.x, sy: p.y });
    },
    onMenu: function (el, e) {
      e.preventDefault(); var w = this.widgetAt(e); if (w && w.kind === 'pin') w = { id: w.id.slice(0, w.id.indexOf('/')) };
      if (w && !el.sel.has(w.id)) { this.select(el, w.id, false); }
      var p = el.toScene(e.clientX, e.clientY); el.ctx.emit('menu', { id: w ? w.id : null, x: e.clientX, y: e.clientY, sx: p.x, sy: p.y });
    },
    onHover: function (el, e) {
      if (!el.ctx.listens('hover')) return; var w = this.widgetAt(e); var id = w ? w.id : null; if (id === el.hoverId) return; el.hoverId = id;
      var p = el.toScene(e.clientX, e.clientY); el.ctx.emit('hover', id ? { id: id, x: e.clientX, y: e.clientY, sx: p.x, sy: p.y } : {});
    },
    onKey: function (el, e) {
      if (el.editor) return; var self = this;
      if ((e.key === 'Delete' || e.key === 'Backspace') && el.sel.size) { e.preventDefault(); el.ctx.emit('delete', { ids: Array.from(el.sel) }); }
      else if (e.key === 'a' && (e.ctrlKey || e.metaKey)) { e.preventDefault(); if ((el.props.selectionMode || 'MULTI') === 'MULTI') { el.sel = new Set(Object.keys(el.nodes).concat(Object.keys(el.edges)).filter(function (id) { var s = el.nodes[id] || el.edges[id]; return s.sel !== false; })); this.paintSel(el); this.reportSel(el); } }
      else if (e.key === 'Escape') this.clearSel(el);
      else if (e.key === 'F2' && el.sel.size === 1) { var id = Array.from(el.sel)[0]; var s = el.nodes[id] || el.notes[id]; if (s && s.ed) { e.preventDefault(); this.edit(el, id); } }
      else if (/^Arrow/.test(e.key) && el.sel.size) {
        e.preventDefault(); var d = e.shiftKey ? 10 : 1; var dx = e.key === 'ArrowLeft' ? -d : e.key === 'ArrowRight' ? d : 0, dy = e.key === 'ArrowUp' ? -d : e.key === 'ArrowDown' ? d : 0; var moves = {};
        el.sel.forEach(function (id) { var s = el.nodes[id] || el.notes[id]; if (s && s.mv !== false) { s.x += dx; s.y += dy; moves[id] = [s.x, s.y]; } });
        if (Object.keys(moves).length) { this.render(el); el.ctx.emit('move', { moves: moves }); }
      }
      else if (e.key === '+' || e.key === '=') this.zoomAt(el, 1.2, el.clientWidth / 2, el.clientHeight / 2);
      else if (e.key === '-') this.zoomAt(el, 1 / 1.2, el.clientWidth / 2, el.clientHeight / 2);
      else if (e.key === '0' && (e.ctrlKey || e.metaKey)) { e.preventDefault(); this.fit(el); }
    },
    edit: function (el, id) {
      var self = this; var node = el.nodes[id], note = el.notes[id]; var b = node ? bounds(node) : noteBounds(el, id); var r = el.svg.getBoundingClientRect(), hr = el.getBoundingClientRect();
      var box = wb.h(note ? 'textarea' : 'input', 'wb-input wb-graph-editor'); box.value = node ? (node.l || '') : (note.tx || '');
      box.style.left = (r.left - hr.left + el.view.x + b.x * el.view.k) + 'px'; box.style.top = (r.top - hr.top + el.view.y + b.y * el.view.k) + 'px'; box.style.width = Math.max(80, b.w * el.view.k) + 'px'; box.style.height = (note ? Math.max(40, b.h * el.view.k) : Math.max(22, Math.min(b.h, 30) * el.view.k)) + 'px';
      el.appendChild(box); el.editor = box; box.focus(); box.select();
      var done = false; var finish = function (commit) { if (done) return; done = true; var v = box.value; box.remove(); el.editor = null; el.focus(); if (commit && v !== (node ? node.l || '' : note.tx || '')) el.ctx.emit('rename', { id: id, text: v }); };
      box.addEventListener('keydown', function (k) { k.stopPropagation(); if (k.key === 'Enter' && (!note || k.ctrlKey)) { k.preventDefault(); finish(true); } else if (k.key === 'Escape') finish(false); });
      box.addEventListener('blur', function () { finish(true); });
    },

    /* ---------------- export ---------------- */
    inlineSvg: function (el) {
      var clone = el.svg.cloneNode(true); var src = el.svg.querySelectorAll('*'), dst = clone.querySelectorAll('*');
      var PROPS = ['fill', 'stroke', 'stroke-width', 'stroke-dasharray', 'stroke-linecap', 'stroke-linejoin', 'font-size', 'font-family', 'font-weight', 'opacity', 'fill-opacity', 'stroke-opacity', 'visibility'];
      clone.setAttribute('xmlns', NS); clone.style.backgroundColor = getComputedStyle(el).backgroundColor; clone.style.fontFamily = getComputedStyle(el.svg).fontFamily;
      for (var i = 0; i < src.length; i++) { var cs = getComputedStyle(src[i]); for (var k = 0; k < PROPS.length; k++) { var v = cs.getPropertyValue(PROPS[k]); if (v) dst[i].style.setProperty(PROPS[k], v); } }
      var m = clone.querySelector('.wb-graph-measure'); if (m) m.remove(); var ov = clone.querySelector('.wb-graph-overlay'); if (ov) ov.remove();
      var b = this.contentBounds(el); if (b) { clone.setAttribute('viewBox', (b.x - 20) + ' ' + (b.y - 20) + ' ' + (b.w + 40) + ' ' + (b.h + 40)); clone.setAttribute('width', b.w + 40); clone.setAttribute('height', b.h + 40); var w = clone.querySelector('.wb-graph-world'); if (w) w.removeAttribute('transform'); }
      return new XMLSerializer().serializeToString(clone);
    },
    rasterize: function (el, w, h, cb) {
      var s = this.inlineSvg(el); var b = this.contentBounds(el) || { w: el.clientWidth, h: el.clientHeight }; var W = w || b.w + 40, H = h || b.h + 40;
      var img = new Image(); var url = URL.createObjectURL(new Blob([s], { type: 'image/svg+xml;charset=utf-8' }));
      img.onload = function () { var c = document.createElement('canvas'); c.width = W * 2; c.height = H * 2; var g = c.getContext('2d'); g.fillStyle = getComputedStyle(el).backgroundColor || '#fff'; g.fillRect(0, 0, c.width, c.height); g.drawImage(img, 0, 0, c.width, c.height); URL.revokeObjectURL(url); cb(null, c.toDataURL('image/png')); };
      img.onerror = function () { URL.revokeObjectURL(url); cb('rasterisation failed'); };
      img.src = url;
    },
    doExport: function (el, id, format, w, h) {
      if (format === 'svg') { el.ctx.emit('export', { id: id, format: 'svg', data: this.inlineSvg(el) }); return; }
      this.rasterize(el, w, h, function (err, data) { el.ctx.emit('export', err ? { id: id, format: 'png', error: err } : { id: id, format: 'png', data: data }); });
    },
    download: function (el, format, name) {
      var save = function (href) { var a = document.createElement('a'); a.href = href; a.download = name || ('graph.' + format); document.body.appendChild(a); a.click(); a.remove(); };
      if (format === 'svg') { var url = URL.createObjectURL(new Blob([this.inlineSvg(el)], { type: 'image/svg+xml' })); save(url); setTimeout(function () { URL.revokeObjectURL(url); }, 1000); }
      else this.rasterize(el, 0, 0, function (err, data) { if (!err) save(data); });
    }
  });
})();
