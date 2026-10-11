/* wb.Diagram — nodes and edges on an SVG canvas with pan/zoom, node dragging, rubber-band and click selection,
 * connect-by-drag and palette drops. wb.Palette — categories of draggable items (text/x-wb-palette). */
(function () {
  var NS = 'http://www.w3.org/2000/svg';
  var svg = function (tag, attrs) { var e = document.createElementNS(NS, tag); if (attrs) Object.keys(attrs).forEach(function (k) { e.setAttribute(k, attrs[k]); }); return e; };
  var MIME = 'text/x-wb-palette';

  function shapePath(shape, w, h) {
    if (shape === 'ellipse') return svg('ellipse', { cx: w / 2, cy: h / 2, rx: w / 2, ry: h / 2 });
    if (shape === 'circle') { var r = Math.min(w, h) / 2; return svg('circle', { cx: w / 2, cy: h / 2, r: r }); }
    if (shape === 'diamond') return svg('polygon', { points: (w / 2) + ',0 ' + w + ',' + (h / 2) + ' ' + (w / 2) + ',' + h + ' 0,' + (h / 2) });
    return svg('rect', { width: w, height: h, rx: shape === 'rounded' ? 8 : 0 });
  }
  // the point on a node's border towards another point
  function border(n, tx, ty) {
    var cx = n.x + n.w / 2, cy = n.y + n.h / 2; var dx = tx - cx, dy = ty - cy; if (!dx && !dy) return { x: cx, y: cy };
    if (n.shape === 'ellipse' || n.shape === 'circle') { var rx = n.shape === 'circle' ? Math.min(n.w, n.h) / 2 : n.w / 2, ry = n.shape === 'circle' ? rx : n.h / 2; var a = Math.atan2(dy / ry, dx / rx); return { x: cx + rx * Math.cos(a), y: cy + ry * Math.sin(a) }; }
    if (n.shape === 'diamond') { var s = Math.abs(dx) / (n.w / 2) + Math.abs(dy) / (n.h / 2); return { x: cx + dx / s, y: cy + dy / s }; }
    var sx = Math.abs(dx) / (n.w / 2), sy = Math.abs(dy) / (n.h / 2); var k = Math.max(sx, sy); return { x: cx + dx / k, y: cy + dy / k };
  }

  wb.register('wb.Diagram', {
    create: function (ctx) {
      var el = wb.h('div', 'wb-diagram'); el.ctx = ctx; el.props = {}; el.tabIndex = 0; el.view = { x: 0, y: 0, k: 1 }; el.sel = new Set(); el.selEdges = new Set(); el.byKey = {};
      el.svg = svg('svg', { 'class': 'wb-diagram-svg' }); el.defs = svg('defs');
      var marker = svg('marker', { id: 'wb-arrow-' + ctx.id, viewBox: '0 0 10 10', refX: 9, refY: 5, markerWidth: 8, markerHeight: 8, orient: 'auto-start-reverse' }); marker.appendChild(svg('path', { d: 'M0,0 L10,5 L0,10 z', 'class': 'wb-diagram-arrowhead' })); el.defs.appendChild(marker);
      var pat = svg('pattern', { id: 'wb-grid-' + ctx.id, width: 20, height: 20, patternUnits: 'userSpaceOnUse' }); pat.appendChild(svg('path', { d: 'M20,0 L0,0 L0,20', 'class': 'wb-diagram-gridline' })); el.defs.appendChild(pat);
      el.bg = svg('rect', { 'class': 'wb-diagram-bg', x: -1e5, y: -1e5, width: 2e5, height: 2e5, fill: 'url(#wb-grid-' + ctx.id + ')' });
      el.world = svg('g', { 'class': 'wb-diagram-world' }); el.edgesG = svg('g', { 'class': 'wb-diagram-edges' }); el.nodesG = svg('g', { 'class': 'wb-diagram-nodes' }); el.tempG = svg('g');
      el.world.append(el.bg, el.edgesG, el.nodesG, el.tempG); el.svg.append(el.defs, el.world); el.appendChild(el.svg);
      el.zoomBox = wb.h('div', 'wb-diagram-zoom'); ['−', '▣', '+'].forEach(function (t, i) { var b = wb.h('button', 'wb-diagram-zbtn', t); b.type = 'button'; b.title = i === 1 ? 'Fit' : i ? 'Zoom in' : 'Zoom out'; b.addEventListener('click', function () { if (i === 1) self.fit(el); else self.zoomAt(el, i ? 1.2 : 1 / 1.2, el.clientWidth / 2, el.clientHeight / 2); }); el.zoomBox.appendChild(b); }); el.appendChild(el.zoomBox);
      var self = this;
      el.toWorld = function (cx, cy) { var r = el.svg.getBoundingClientRect(); return { x: (cx - r.left - el.view.x) / el.view.k, y: (cy - r.top - el.view.y) / el.view.k }; };
      el.svg.addEventListener('wheel', function (e) { e.preventDefault(); var r = el.svg.getBoundingClientRect(); self.zoomAt(el, e.deltaY < 0 ? 1.1 : 1 / 1.1, e.clientX - r.left, e.clientY - r.top); }, { passive: false });
      el.svg.addEventListener('mousedown', function (e) {
        if (e.button !== 0 && e.button !== 1) return; el.focus();
        var g = e.target.closest('.wb-diagram-node'); var edge = e.target.closest('.wb-diagram-edge'); var port = e.target.closest('.wb-diagram-port');
        if (port && el.props.connectable) { self.startConnect(el, e, port.closest('.wb-diagram-node').dataset.key); return; }
        if (g) { var k = g.dataset.key; if (!el.sel.has(k)) { if (!(e.ctrlKey || e.metaKey || e.shiftKey)) { el.sel.clear(); el.selEdges.clear(); } el.sel.add(k); self.paintSel(el); self.reportSel(el); } else if (e.ctrlKey || e.metaKey) { el.sel.delete(k); self.paintSel(el); self.reportSel(el); return; } self.startDrag(el, e); return; }
        if (edge) { var ek = edge.dataset.key; if (!(e.ctrlKey || e.metaKey)) { el.sel.clear(); el.selEdges.clear(); } if (el.selEdges.has(ek)) el.selEdges.delete(ek); else el.selEdges.add(ek); self.paintSel(el); self.reportSel(el); return; }
        if (e.shiftKey || e.button === 1 || e.altKey) self.startPan(el, e); else self.startBand(el, e);
      });
      el.svg.addEventListener('dblclick', function (e) { var g = e.target.closest('.wb-diagram-node'); var edge = e.target.closest('.wb-diagram-edge'); if (g) ctx.emit('nodeActivate', { key: g.dataset.key }); else if (edge) ctx.emit('edgeActivate', { key: edge.dataset.key }); });
      el.svg.addEventListener('contextmenu', function (e) { var g = e.target.closest('.wb-diagram-node'); if (!g) return; e.preventDefault(); if (!el.sel.has(g.dataset.key)) { el.sel.clear(); el.sel.add(g.dataset.key); self.paintSel(el); self.reportSel(el); } ctx.emit('nodeMenu', { key: g.dataset.key, x: e.clientX, y: e.clientY }); });
      el.addEventListener('keydown', function (e) {
        if ((e.key === 'Delete' || e.key === 'Backspace') && (el.sel.size || el.selEdges.size)) { e.preventDefault(); ctx.emit('delete', { nodes: Array.from(el.sel), edges: Array.from(el.selEdges) }); }
        else if (e.key === 'a' && (e.ctrlKey || e.metaKey)) { e.preventDefault(); el.sel = new Set(Object.keys(el.byKey)); self.paintSel(el); self.reportSel(el); }
        else if (e.key === 'Escape') { el.sel.clear(); el.selEdges.clear(); self.paintSel(el); self.reportSel(el); }
        else if (/^Arrow/.test(e.key) && el.sel.size) { e.preventDefault(); var d = e.shiftKey ? 10 : 1; var dx = e.key === 'ArrowLeft' ? -d : e.key === 'ArrowRight' ? d : 0, dy = e.key === 'ArrowUp' ? -d : e.key === 'ArrowDown' ? d : 0; var moved = []; el.sel.forEach(function (k) { var n = el.byKey[k]; if (n && !n.locked) { n.x += dx; n.y += dy; moved.push({ key: k, x: n.x, y: n.y }); } }); self.render(el); ctx.emit('move', { nodes: moved }); }
      });
      if (typeof ResizeObserver !== 'undefined') { el.ro = new ResizeObserver(function () { if (el.pendingFit && el.clientWidth > 0) { el.pendingFit = false; self.fit(el); } }); el.ro.observe(el); }
      el.addEventListener('dragover', function (e) { if (el.props.dropTarget !== false && Array.prototype.indexOf.call(e.dataTransfer.types, MIME) >= 0) { e.preventDefault(); e.dataTransfer.dropEffect = 'copy'; el.classList.add('wb-diagram-over'); } });
      el.addEventListener('dragleave', function () { el.classList.remove('wb-diagram-over'); });
      el.addEventListener('drop', function (e) { el.classList.remove('wb-diagram-over'); var item = e.dataTransfer.getData(MIME); if (!item) return; e.preventDefault(); var p = el.toWorld(e.clientX, e.clientY); var g = el.props.grid == null ? 10 : el.props.grid; if (g) { p.x = Math.round(p.x / g) * g; p.y = Math.round(p.y / g) * g; } ctx.emit('drop', { item: item, x: p.x, y: p.y }); });
      return el;
    },
    /* ----- interaction ----- */
    track: function (move, up) { var mu = function (e) { document.removeEventListener('mousemove', move); document.removeEventListener('mouseup', mu); up(e); }; document.addEventListener('mousemove', move); document.addEventListener('mouseup', mu); },
    startPan: function (el, e) { e.preventDefault(); var sx = e.clientX, sy = e.clientY, vx = el.view.x, vy = el.view.y; el.classList.add('wb-diagram-panning'); var self = this; this.track(function (m) { el.view.x = vx + m.clientX - sx; el.view.y = vy + m.clientY - sy; self.applyView(el); }, function () { el.classList.remove('wb-diagram-panning'); }); },
    startDrag: function (el, e) {
      e.preventDefault(); var self = this; var start = el.toWorld(e.clientX, e.clientY); var orig = {}; el.sel.forEach(function (k) { var n = el.byKey[k]; if (n && !n.locked) orig[k] = { x: n.x, y: n.y }; }); if (!Object.keys(orig).length) return; var moved = false; var g = el.props.grid == null ? 10 : el.props.grid;
      this.track(function (m) { var p = el.toWorld(m.clientX, m.clientY); var dx = p.x - start.x, dy = p.y - start.y; if (!moved && Math.abs(dx) < 2 && Math.abs(dy) < 2) return; moved = true; Object.keys(orig).forEach(function (k) { var n = el.byKey[k]; var nx = orig[k].x + dx, ny = orig[k].y + dy; if (g) { nx = Math.round(nx / g) * g; ny = Math.round(ny / g) * g; } n.x = nx; n.y = ny; }); self.render(el); },
        function () { if (moved) el.ctx.emit('move', { nodes: Object.keys(orig).map(function (k) { return { key: k, x: el.byKey[k].x, y: el.byKey[k].y }; }) }); });
    },
    startBand: function (el, e) {
      e.preventDefault(); var self = this; var s = el.toWorld(e.clientX, e.clientY); var band = svg('rect', { 'class': 'wb-diagram-band' }); el.tempG.appendChild(band); var add = e.ctrlKey || e.metaKey || e.shiftKey; var any = false;
      this.track(function (m) { var p = el.toWorld(m.clientX, m.clientY); any = true; var x = Math.min(s.x, p.x), y = Math.min(s.y, p.y), w = Math.abs(p.x - s.x), h = Math.abs(p.y - s.y); band.setAttribute('x', x); band.setAttribute('y', y); band.setAttribute('width', w); band.setAttribute('height', h); if (!add) el.sel.clear(); Object.keys(el.byKey).forEach(function (k) { var n = el.byKey[k]; if (n.x < x + w && n.x + n.w > x && n.y < y + h && n.y + n.h > y) el.sel.add(k); }); self.paintSel(el); },
        function () { band.remove(); if (!any && !add) { el.sel.clear(); el.selEdges.clear(); self.paintSel(el); } self.reportSel(el); });
    },
    startConnect: function (el, e, fromKey) {
      e.preventDefault(); e.stopPropagation(); var self = this; var from = el.byKey[fromKey]; var line = svg('path', { 'class': 'wb-diagram-edge-line wb-diagram-connecting', 'marker-end': 'url(#wb-arrow-' + el.ctx.id + ')' }); el.tempG.appendChild(line); var target = null;
      this.track(function (m) { var p = el.toWorld(m.clientX, m.clientY); var b = border(from, p.x, p.y); line.setAttribute('d', 'M' + b.x + ',' + b.y + ' L' + p.x + ',' + p.y); var g = document.elementFromPoint(m.clientX, m.clientY); var node = g && g.closest ? g.closest('.wb-diagram-node') : null; var nk = node ? node.dataset.key : null; if (nk !== target) { el.nodesG.querySelectorAll('.wb-diagram-target').forEach(function (n) { n.classList.remove('wb-diagram-target'); }); target = nk && nk !== fromKey ? nk : null; if (target) node.classList.add('wb-diagram-target'); } },
        function () { line.remove(); el.nodesG.querySelectorAll('.wb-diagram-target').forEach(function (n) { n.classList.remove('wb-diagram-target'); }); if (target) el.ctx.emit('connect', { from: fromKey, to: target }); });
    },
    zoomAt: function (el, f, px, py) { var k = Math.max(0.2, Math.min(4, el.view.k * f)); f = k / el.view.k; el.view.x = px - (px - el.view.x) * f; el.view.y = py - (py - el.view.y) * f; el.view.k = k; this.applyView(el); },
    applyView: function (el) { el.world.setAttribute('transform', 'translate(' + el.view.x + ',' + el.view.y + ') scale(' + el.view.k + ')'); },
    fit: function (el) {
      var ks = Object.keys(el.byKey); if (!ks.length) { el.view = { x: 0, y: 0, k: 1 }; this.applyView(el); return; }
      var x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity; ks.forEach(function (k) { var n = el.byKey[k]; x0 = Math.min(x0, n.x); y0 = Math.min(y0, n.y); x1 = Math.max(x1, n.x + n.w); y1 = Math.max(y1, n.y + n.h); });
      var W = el.clientWidth || 400, H = el.clientHeight || 300; var k = Math.min(4, Math.max(0.2, Math.min((W - 40) / (x1 - x0 || 1), (H - 40) / (y1 - y0 || 1), 1.5)));
      el.view = { k: k, x: (W - (x1 - x0) * k) / 2 - x0 * k, y: (H - (y1 - y0) * k) / 2 - y0 * k }; this.applyView(el);
    },
    /* ----- rendering ----- */
    render: function (el) {
      var self = this; var props = el.props; el.byKey = {}; (props.nodes || []).forEach(function (n) { el.byKey[n.k] = n; });
      el.nodesG.innerHTML = ''; el.edgesG.innerHTML = '';
      (props.edges || []).forEach(function (e) {
        var a = el.byKey[e.from], b = el.byKey[e.to]; if (!a || !b) return;
        var g = svg('g', { 'class': 'wb-diagram-edge' + (e.s ? ' ' + e.s : '') + (el.selEdges.has(e.k) ? ' wb-selected' : ''), 'data-key': e.k });
        var p1 = border(a, b.x + b.w / 2, b.y + b.h / 2), p2 = border(b, a.x + a.w / 2, a.y + a.h / 2); var d = 'M' + p1.x + ',' + p1.y + ' L' + p2.x + ',' + p2.y;
        var hit = svg('path', { d: d, 'class': 'wb-diagram-edge-hit' }); var line = svg('path', { d: d, 'class': 'wb-diagram-edge-line' + (e.ls ? ' wb-diagram-' + e.ls : '') }); if (e.arrow !== false) line.setAttribute('marker-end', 'url(#wb-arrow-' + el.ctx.id + ')'); if (e.c) line.style.stroke = e.c;
        g.append(hit, line);
        if (e.t) { var t = svg('text', { x: (p1.x + p2.x) / 2, y: (p1.y + p2.y) / 2 - 4, 'class': 'wb-diagram-edge-label', 'text-anchor': 'middle' }); t.textContent = e.t; g.appendChild(t); }
        el.edgesG.appendChild(g);
      });
      (props.nodes || []).forEach(function (n) {
        var g = svg('g', { 'class': 'wb-diagram-node wb-shape-' + (n.shape || 'rounded') + (n.s ? ' ' + n.s : '') + (el.sel.has(n.k) ? ' wb-selected' : '') + (n.locked ? ' wb-diagram-locked' : ''), 'data-key': n.k, transform: 'translate(' + n.x + ',' + n.y + ')' });
        var shape = shapePath(n.shape || 'rounded', n.w, n.h); shape.setAttribute('class', 'wb-diagram-node-shape'); if (n.c) shape.style.fill = n.c; g.appendChild(shape);
        var tx = n.w / 2; if (n.i) { var img = svg('image', { x: 8, y: n.h / 2 - 10, width: 20, height: 20 }); img.setAttributeNS('http://www.w3.org/1999/xlink', 'href', wb.res(n.i)); img.setAttribute('href', wb.res(n.i)); g.appendChild(img); tx = (n.w + 24) / 2; }
        var t = svg('text', { x: tx, y: n.h / 2, 'class': 'wb-diagram-node-label', 'text-anchor': 'middle', 'dominant-baseline': 'middle' }); t.textContent = n.t || ''; g.appendChild(t);
        if (n.tt) { var title = svg('title'); title.textContent = n.tt; g.appendChild(title); }
        if (props.connectable) { var port = svg('circle', { 'class': 'wb-diagram-port', cx: n.w, cy: n.h / 2, r: 5 }); port.appendChild(svg('title')).textContent = 'Drag to connect'; g.appendChild(port); }
        el.nodesG.appendChild(g);
      });
    },
    paintSel: function (el) { el.nodesG.querySelectorAll('.wb-diagram-node').forEach(function (g) { g.classList.toggle('wb-selected', el.sel.has(g.dataset.key)); }); el.edgesG.querySelectorAll('.wb-diagram-edge').forEach(function (g) { g.classList.toggle('wb-selected', el.selEdges.has(g.dataset.key)); }); },
    reportSel: function (el) { el.ctx.emit('select', { nodes: Array.from(el.sel), edges: Array.from(el.selEdges) }); },
    update: function (el, props, changed) {
      el.props = props; var first = !el.rendered;
      if (changed.has('selected')) { el.sel = new Set(props.selected || []); }
      if (changed.has('showGrid')) el.bg.classList.toggle('wb-hidden', props.showGrid === false);
      if (changed.has('connectable')) el.classList.toggle('wb-diagram-connectable', !!props.connectable);
      if (changed.has('nodes') || changed.has('edges') || changed.has('connectable') || changed.has('selected')) { this.render(el); }
      if (first && (props.nodes || []).length) { el.rendered = true; el.pendingFit = true; var self = this; requestAnimationFrame(function () { if (el.clientWidth > 0) { el.pendingFit = false; self.fit(el); } }); }
    },
    call: function (el, m, args) {
      if (m === 'fit') this.fit(el); else if (m === 'zoom') { el.view.k = Math.max(0.2, Math.min(4, args[0])); this.applyView(el); }
      else if (m === 'scrollTo') { var n = el.byKey[args[0]]; if (n) { el.view.x = (el.clientWidth || 400) / 2 - (n.x + n.w / 2) * el.view.k; el.view.y = (el.clientHeight || 300) / 2 - (n.y + n.h / 2) * el.view.k; this.applyView(el); } }
    },
    destroy: function (el) { if (el.ro) el.ro.disconnect(); },
    insertChild: function () {}, removeChild: function () {}
  });

  wb.register('wb.Palette', {
    create: function (ctx) {
      var el = wb.h('div', 'wb-palette'); el.ctx = ctx; el.props = {}; el.collapsed = new Set();
      el.filterBox = wb.h('div', 'wb-palette-filter wb-hidden'); el.filter = wb.h('input', 'wb-input'); el.filter.type = 'search'; el.filter.placeholder = '⌕'; el.filterBox.appendChild(el.filter); el.body = wb.h('div', 'wb-palette-body'); el.append(el.filterBox, el.body);
      var self = this; el.filter.addEventListener('input', function () { self.render(el); ctx.emit('filter', { text: el.filter.value }); });
      el.body.addEventListener('click', function (e) { var h = e.target.closest('.wb-palette-cat'); if (h) { var n = h.dataset.name; if (el.collapsed.has(n)) el.collapsed.delete(n); else el.collapsed.add(n); self.render(el); return; } var it = e.target.closest('.wb-palette-item'); if (it) { el.body.querySelectorAll('.wb-palette-item.wb-selected').forEach(function (x) { x.classList.remove('wb-selected'); }); it.classList.add('wb-selected'); ctx.emit('select', { key: it.dataset.key }); } });
      el.body.addEventListener('dragstart', function (e) { var it = e.target.closest('.wb-palette-item'); if (!it) return; e.dataTransfer.setData(MIME, it.dataset.key); e.dataTransfer.setData('text/plain', it.dataset.key); e.dataTransfer.effectAllowed = 'copy'; });
      return el;
    },
    render: function (el) {
      var props = el.props; el.body.innerHTML = ''; var q = (el.filter.value || '').toLowerCase();
      (props.categories || []).forEach(function (c) {
        var items = (c.items || []).filter(function (it) { return !q || it.l.toLowerCase().indexOf(q) >= 0; }); if (q && !items.length) return;
        var sec = wb.h('div', 'wb-palette-section' + (el.collapsed.has(c.name) && !q ? ' wb-collapsed' : '')); var h = wb.h('div', 'wb-palette-cat', c.name); h.dataset.name = c.name; sec.appendChild(h);
        var grid = wb.h('div', 'wb-palette-items' + (props.showLabels === false ? ' wb-palette-icons' : ''));
        items.forEach(function (it) { var d = wb.h('div', 'wb-palette-item'); d.draggable = true; d.dataset.key = it.v; d.title = it.d || it.l; if (it.i) { var ic = wb.h('span', 'wb-icon'); var img = wb.h('img'); img.src = wb.res(it.i); img.alt = ''; ic.appendChild(img); d.appendChild(ic); } if (props.showLabels !== false) d.appendChild(wb.h('span', 'wb-text', it.l)); grid.appendChild(d); });
        sec.appendChild(grid); el.body.appendChild(sec);
      });
    },
    update: function (el, props, changed) { el.props = props; if (changed.has('showFilter')) el.filterBox.classList.toggle('wb-hidden', !props.showFilter); if (changed.has('filter') && el.filter.value !== (props.filter || '')) el.filter.value = props.filter || ''; this.render(el); },
    insertChild: function () {}, removeChild: function () {}
  });
})();
