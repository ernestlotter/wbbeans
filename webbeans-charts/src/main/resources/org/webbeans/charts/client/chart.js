/*
 * wb.Chart — the SVG chart renderer. The server owns the data (series + points arrive through "data" calls:
 * set/points/append/trim/update/remove per series, null removes one); appearance comes as properties. Draws
 * line/step/area (stacked), bar (grouped/stacked, horizontal), scatter/bubble, pie/donut with axes (linear,
 * log, time, category), grid, markers/bands, legend, tooltip, crosshair, wheel zoom, drag pan, shift-drag brush,
 * LTTB downsampling, resize observation, and SVG/PNG export. No dependencies.
 */
(function () {
  var NS = 'http://www.w3.org/2000/svg';
  var DEFAULT_PALETTE = ['#0f7f86', '#d9822b', '#5b6fd6', '#c84f6a', '#3f9c5a', '#8a63c9', '#c9a227', '#4a9fd4', '#b05f3c', '#5e9e8c', '#9c5ba3', '#7a8a2f'];
  var MARGIN = { top: 12, right: 16, bottom: 8, left: 8 };
  var TYPES = { line: 1, step: 1, area: 1, bar: 1, scatter: 1, pie: 1, donut: 1 };

  function svg(tag, attrs, parent) { var e = document.createElementNS(NS, tag); if (attrs) for (var k in attrs) if (attrs[k] != null) e.setAttribute(k, attrs[k]); if (parent) parent.appendChild(e); return e; }
  function clamp(v, a, b) { return v < a ? a : v > b ? b : v; }
  function num(v, d) { return typeof v === 'number' && isFinite(v) ? v : d; }

  /* ---------------- formatting ---------------- */
  function fmtNumber(v, spec) {
    if (!isFinite(v)) return '';
    if (!spec || spec.kind !== 'number') return fmtAuto(v);
    var n = v * (spec.multiplier || 1);
    var neg = n < 0; n = Math.abs(n);
    var maxF = spec.maxFrac == null ? 3 : spec.maxFrac, minF = spec.minFrac || 0;
    var s = n.toFixed(maxF);
    if (maxF > minF) { s = s.replace(/(\.\d*?[1-9])0+$/, '$1').replace(/\.0+$/, ''); if (minF > 0) { var parts = s.split('.'); var fr = parts[1] || ''; while (fr.length < minF) fr += '0'; s = parts[0] + '.' + fr; } }
    var ip = s.split('.')[0], fp = s.split('.')[1];
    if (spec.grouping) { var g = spec.groupSize || 3; var re = new RegExp('\\B(?=(\\d{' + g + '})+(?!\\d))', 'g'); ip = ip.replace(re, spec.group || ','); }
    s = ip + (fp != null && fp !== '' ? (spec.decimal || '.') + fp : '');
    return (neg ? '-' : '') + (spec.prefix || '') + s + (spec.suffix || '');
  }
  function fmtAuto(v) {
    if (!isFinite(v)) return '';
    var a = Math.abs(v);
    if (a >= 1e9) return (v / 1e9).toFixed(a >= 1e10 ? 0 : 1) + 'G';
    if (a >= 1e6) return (v / 1e6).toFixed(a >= 1e7 ? 0 : 1) + 'M';
    if (a >= 1e4) return (v / 1e3).toFixed(0) + 'k';
    if (a >= 100 || a === 0) return String(Math.round(v * 100) / 100);
    if (a >= 1) return String(Math.round(v * 1000) / 1000);
    return v.toPrecision(3).replace(/\.?0+$/, '');
  }
  var MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'], DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
  function pad(n, w) { var s = String(n); while (s.length < w) s = '0' + s; return s; }
  function fmtTime(ms, pattern) {
    var d = new Date(ms);
    if (!pattern) return d.toLocaleString();
    return pattern.replace(/yyyy|yy|MMM|MM|dd|HH|mm|ss|SSS|EEE|a|'[^']*'/g, function (t) {
      switch (t) {
        case 'yyyy': return String(d.getFullYear()); case 'yy': return pad(d.getFullYear() % 100, 2);
        case 'MMM': return MONTHS[d.getMonth()]; case 'MM': return pad(d.getMonth() + 1, 2); case 'dd': return pad(d.getDate(), 2);
        case 'HH': return pad(d.getHours(), 2); case 'mm': return pad(d.getMinutes(), 2); case 'ss': return pad(d.getSeconds(), 2);
        case 'SSS': return pad(d.getMilliseconds(), 3); case 'EEE': return DAYS[d.getDay()]; case 'a': return d.getHours() < 12 ? 'AM' : 'PM';
        default: return t.slice(1, -1);
      }
    });
  }
  function fmtTimeAuto(ms, span) {
    var d = new Date(ms);
    if (span < 2000) return fmtTime(ms, 'HH:mm:ss.SSS');
    if (span < 86400000) return fmtTime(ms, 'HH:mm:ss');
    if (span < 86400000 * 60) return fmtTime(ms, 'MMM dd HH:mm');
    if (span < 86400000 * 800) return fmtTime(ms, 'yyyy-MM-dd');
    return String(d.getFullYear());
  }

  /* ---------------- scales ---------------- */
  function niceStep(span, count) {
    var raw = span / Math.max(1, count); var mag = Math.pow(10, Math.floor(Math.log10(raw))); var r = raw / mag;
    return (r >= 5 ? 10 : r >= 2 ? 5 : r >= 1 ? 2 : 1) * mag;
  }
  var TIME_STEPS = [1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 15000, 30000, 60000, 120000, 300000, 600000, 900000, 1800000, 3600000, 7200000, 10800000, 21600000, 43200000, 86400000, 172800000, 604800000, 1209600000, 2592000000, 7776000000, 15552000000, 31536000000];
  function ticksFor(axis, count) {
    var lo = axis.d0, hi = axis.d1, out = [];
    if (axis.type === 'category') { var step = Math.max(1, Math.ceil((hi - lo) / Math.max(1, count))); for (var i = Math.ceil(lo); i <= hi; i += step) if (i >= 0 && i < (axis.categories || []).length) out.push(i); return out; }
    if (axis.type === 'log') { var e0 = Math.ceil(Math.log10(Math.max(lo, 1e-300))), e1 = Math.floor(Math.log10(hi)); for (var e = e0; e <= e1; e++) out.push(Math.pow(10, e)); if (out.length < 2) { out = []; var s = niceStep(hi - lo, count); for (var v = Math.ceil(lo / s) * s; v <= hi; v += s) out.push(v); } return out; }
    if (axis.type === 'time') { var span = hi - lo; var st = TIME_STEPS[TIME_STEPS.length - 1]; for (var k = 0; k < TIME_STEPS.length; k++) if (span / TIME_STEPS[k] <= count) { st = TIME_STEPS[k]; break; } var off = new Date(lo).getTimezoneOffset() * 60000; var first = Math.ceil((lo - off) / st) * st + off; for (var t = first; t <= hi; t += st) out.push(t); return out; }
    var ns = niceStep(hi - lo, count); for (var x = Math.ceil(lo / ns - 1e-9) * ns; x <= hi + 1e-9; x += ns) out.push(Math.round(x / ns) * ns);
    return out;
  }
  function makeScale(axis, r0, r1) {
    var lo = axis.d0, hi = axis.d1;
    if (axis.type === 'log') { var l0 = Math.log10(Math.max(lo, 1e-300)), l1 = Math.log10(Math.max(hi, 1e-299)); var kk = (r1 - r0) / ((l1 - l0) || 1); return { map: function (v) { return r0 + (Math.log10(Math.max(v, 1e-300)) - l0) * kk; }, invert: function (p) { return Math.pow(10, l0 + (p - r0) / kk); } }; }
    var k = (r1 - r0) / ((hi - lo) || 1);
    return { map: function (v) { return r0 + (v - lo) * k; }, invert: function (p) { return lo + (p - r0) / k; } };
  }

  /* ---------------- LTTB downsampling ---------------- */
  function lttb(pts, threshold) {
    var n = pts.length; if (threshold >= n || threshold < 3) return pts;
    var out = [pts[0]], every = (n - 2) / (threshold - 2), a = 0;
    for (var i = 0; i < threshold - 2; i++) {
      var rs = Math.floor((i + 1) * every) + 1, re = Math.min(Math.floor((i + 2) * every) + 1, n); var ax = 0, ay = 0, cnt = re - rs;
      for (var j = rs; j < re; j++) { ax += pts[j].x; ay += pts[j].y; } ax /= cnt; ay /= cnt;
      var s = Math.floor(i * every) + 1, e = Math.floor((i + 1) * every) + 1, best = -1, bi = s;
      for (var m = s; m < e; m++) { var area = Math.abs((pts[a].x - ax) * (pts[m].y - pts[a].y) - (pts[a].x - pts[m].x) * (ay - pts[a].y)); if (area > best) { best = area; bi = m; } }
      out.push(pts[bi]); a = bi;
    }
    out.push(pts[n - 1]); return out;
  }

  /* ---------------- the renderer ---------------- */
  wb.register('wb.Chart', {
    create: function (ctx) {
      var el = wb.h('div', 'wb-chart'); el.tabIndex = 0;
      el.svg = svg('svg', { 'class': 'wb-chart-svg' }, el);
      el.tip = wb.h('div', 'wb-chart-tip wb-hidden'); el.appendChild(el.tip);
      el.ctx = ctx; el.props = {}; el.series = []; el.byId = {}; el.categories = []; el.range = {}; el.hiddenSeries = {}; el.highlightKey = null; el.sel = {};
      el.ro = new ResizeObserver(function () { schedule(el); }); el.ro.observe(el);
      el.addEventListener('mousemove', function (e) { onMove(el, e); });
      el.addEventListener('mouseleave', function () { hideTip(el); });
      el.addEventListener('click', function (e) { onClick(el, e); });
      el.addEventListener('wheel', function (e) { onWheel(el, e); }, { passive: false });
      el.addEventListener('mousedown', function (e) { onDown(el, e); });
      el.addEventListener('dblclick', function () { if (el.props.zoom && el.props.zoom !== 'NONE') { resetRange(el, true); } });
      el.addEventListener('keydown', function (e) { onKey(el, e); });
      return el;
    },
    update: function (el, props, changed) { el.props = props; if (changed.has('xAxis') || changed.has('yAxis') || changed.has('y2Axis')) { el.range = {}; } schedule(el); },
    call: function (el, m, args) {
      switch (m) {
        case 'data': applyData(el, args[0] || {}); break;
        case 'resetZoom': resetRange(el, false); break;
        case 'highlight': el.highlightSeries = args[0]; el.highlightKey = args[1]; schedule(el); break;
        case 'setRange': if (args[0] === 'x') { if (args[1] == null || args[2] == null) delete el.range.x; else el.range.x = [args[1], args[2]]; } schedule(el); break;
        case 'export': doExport(el, args[0], args[1], args[2], args[3]); break;
        case 'download': download(el, args[0], args[1]); break;
      }
    },
    insertChild: function () {}, removeChild: function () {},
    destroy: function (el) { if (el.ro) el.ro.disconnect(); if (el.raf) cancelAnimationFrame(el.raf); }
  });

  /* ---------------- data ---------------- */
  function toPoint(p) { if (Array.isArray(p)) return { x: p[0], y: p[1], s: p[2] }; return { x: p.x, y: p.y, s: p.s, k: p.k, l: p.l, c: p.c, m: p.m }; }
  function applyData(el, d) {
    if (d.reset) { el.series = []; el.byId = {}; }
    if (d.categories) el.categories = d.categories;
    var sm = d.series || {};
    Object.keys(sm).forEach(function (id) {
      var sd = sm[id];
      if (sd === null) { var i = el.series.indexOf(el.byId[id]); if (i >= 0) el.series.splice(i, 1); delete el.byId[id]; return; }
      var s = el.byId[id]; if (!s) { s = { id: id, points: [] }; el.byId[id] = s; el.series.push(s); }
      if (sd.set) Object.assign(s, sd.set);
      if (sd.points) s.points = sd.points.map(toPoint);
      if (sd.append) { s.points = s.points.concat(sd.append.map(toPoint)); if (sd.trim && s.points.length > sd.trim) s.points.splice(0, s.points.length - sd.trim); }
      if (sd.update) Object.keys(sd.update).forEach(function (k) { var np = toPoint(sd.update[k]); np.k = k; var j = indexOfKey(s, k); if (j < 0) s.points.push(np); else s.points[j] = np; });
      if (sd.remove) sd.remove.forEach(function (k) { var j = indexOfKey(s, k); if (j >= 0) s.points.splice(j, 1); });
      if (sd.set && 'visible' in sd.set) { if (sd.set.visible === false) el.hiddenSeries[id] = true; else delete el.hiddenSeries[id]; }
      s.sorted = null;
    });
    schedule(el);
  }
  function indexOfKey(s, k) { for (var i = 0; i < s.points.length; i++) if (s.points[i].k === k) return i; return -1; }
  function seriesType(el, s) { return (s.type || (el.props.type || 'line')).toLowerCase(); }
  function seriesColor(el, s, i) { return s.color || (el.props.palette && el.props.palette[i % el.props.palette.length]) || DEFAULT_PALETTE[i % DEFAULT_PALETTE.length]; }
  function visibleSeries(el) { return el.series.filter(function (s) { return !el.hiddenSeries[s.id]; }); }

  /* ---------------- layout + draw ---------------- */
  function schedule(el) { if (el.raf) return; el.raf = requestAnimationFrame(function () { el.raf = null; try { draw(el); } catch (e) { console.error('wb.Chart draw failed', e); } }); }

  function draw(el) {
    var W = el.clientWidth, H = el.clientHeight; if (W < 10 || H < 10) return;
    var root = el.svg; while (root.firstChild) root.removeChild(root.firstChild);
    root.setAttribute('width', W); root.setAttribute('height', H); root.setAttribute('viewBox', '0 0 ' + W + ' ' + H);
    var p = el.props;
    var top = MARGIN.top;
    if (p.title) { svg('text', { x: W / 2, y: top + 14, 'class': 'wb-chart-title', 'text-anchor': 'middle' }, root).textContent = p.title; top += 24; }
    if (p.subtitle) { svg('text', { x: W / 2, y: top + 10, 'class': 'wb-chart-subtitle', 'text-anchor': 'middle' }, root).textContent = p.subtitle; top += 18; }
    var all = el.series, vis = visibleSeries(el);
    var pie = all.length && all.every(function (s) { var t = seriesType(el, s); return t === 'pie' || t === 'donut'; });
    var legend = p.legend || { position: 'bottom', visible: true, interactive: true };
    var box = { x: MARGIN.left, y: top, w: W - MARGIN.left - MARGIN.right, h: H - top - MARGIN.bottom };
    if (legend.visible !== false && all.length) box = drawLegend(el, root, box, legend, pie);
    el.plot = null;
    if (!all.length || !vis.length) { svg('text', { x: W / 2, y: box.y + box.h / 2, 'class': 'wb-chart-empty', 'text-anchor': 'middle' }, root).textContent = all.length ? '' : ''; return; }
    if (pie) drawPie(el, root, box, vis); else drawCartesian(el, root, box, vis);
  }

  function drawLegend(el, root, box, legend, pie) {
    var items = [];
    if (pie) { var s0 = el.series[0]; s0.points.forEach(function (pt, i) { items.push({ label: pt.l || (el.categories[pt.x] != null ? el.categories[pt.x] : String(pt.x)), color: pt.c || seriesColor(el, { }, i), id: s0.id, slice: i }); }); }
    else el.series.forEach(function (s, i) { items.push({ label: s.name || s.id, color: seriesColor(el, s, i), id: s.id, hidden: !!el.hiddenSeries[s.id] }); });
    var g = svg('g', { 'class': 'wb-chart-legend' }, root);
    var vertical = legend.position === 'left' || legend.position === 'right';
    var x = 0, y = 0, rowH = 18, maxW = 0, lineW = 0;
    var measured = items.map(function (it) { var t = svg('text', { 'class': 'wb-chart-legend-text' }, g); t.textContent = it.label; var w = t.getComputedTextLength() + 22; g.removeChild(t); return w; });
    var rows = 1;
    items.forEach(function (it, i) {
      var w = measured[i];
      if (!vertical && lineW + w > box.w && lineW > 0) { lineW = 0; rows++; }
      var ix = vertical ? 0 : lineW, iy = vertical ? i * rowH : (rows - 1) * rowH;
      var ig = svg('g', { 'class': 'wb-chart-legend-item' + (it.hidden ? ' wb-chart-legend-hidden' : ''), transform: 'translate(' + ix + ',' + iy + ')' }, g);
      svg('rect', { x: 0, y: 4, width: 12, height: 12, rx: 2, fill: it.color }, ig);
      svg('text', { x: 16, y: 14, 'class': 'wb-chart-legend-text' }, ig).textContent = it.label;
      svg('rect', { x: -2, y: 0, width: w, height: rowH, fill: 'transparent' }, ig);
      if (legend.interactive !== false && !pie) ig.addEventListener('click', function (e) { e.stopPropagation(); toggleSeries(el, it.id); });
      lineW += w; maxW = Math.max(maxW, w); x = Math.max(x, lineW);
    });
    var lw = vertical ? maxW : Math.min(box.w, x), lh = vertical ? items.length * rowH : rows * rowH;
    var gx, gy;
    if (legend.position === 'top') { gx = box.x + (box.w - lw) / 2; gy = box.y; box.y += lh + 6; box.h -= lh + 6; }
    else if (legend.position === 'left') { gx = box.x; gy = box.y + (box.h - lh) / 2; box.x += lw + 10; box.w -= lw + 10; }
    else if (legend.position === 'right') { gx = box.x + box.w - lw; gy = box.y + (box.h - lh) / 2; box.w -= lw + 10; }
    else { gx = box.x + (box.w - lw) / 2; gy = box.y + box.h - lh; box.h -= lh + 6; }
    g.setAttribute('transform', 'translate(' + gx + ',' + gy + ')');
    return box;
  }
  function toggleSeries(el, id) { if (el.hiddenSeries[id]) delete el.hiddenSeries[id]; else el.hiddenSeries[id] = true; el.ctx.emit('legendToggle', { series: id, visible: !el.hiddenSeries[id] }); schedule(el); }

  function axisSpec(el, name) { return el.props[name] || { type: 'linear', grid: true, visible: true }; }
  function fmtAxis(axis, v) { if (axis.type === 'category') return axis.categories[Math.round(v)] != null ? axis.categories[Math.round(v)] : ''; if (axis.type === 'time') return axis.format && axis.format.kind === 'time' ? fmtTime(v, axis.format.pattern) : fmtTimeAuto(v, axis.d1 - axis.d0); return fmtNumber(v, axis.format); }

  function drawCartesian(el, root, box, vis) {
    var p = el.props, horizontal = !!p.horizontal, stackedAll = !!p.stacked;
    var xs = Object.assign({}, axisSpec(el, 'xAxis')), ys = Object.assign({}, axisSpec(el, 'yAxis')), y2s = p.y2Axis ? Object.assign({}, p.y2Axis) : null;
    xs.categories = el.categories; if (xs.type === 'linear' && el.categories.length && vis.every(function (s) { return s.points.every(function (q) { return Number.isInteger(q.x); }); }) && vis.some(function (s) { return seriesType(el, s) === 'bar'; })) xs.type = 'category';
    // stacking: per stack group, cumulative y per x
    var stacks = {};
    vis.forEach(function (s) { var t = seriesType(el, s); s.stackKey = (s.stack || (stackedAll && (t === 'bar' || t === 'area') ? '*' + t : null)); if (s.stackKey) { var st = stacks[s.stackKey] = stacks[s.stackKey] || {}; s.stacked = s.points.map(function (q) { var base = st[q.x] || 0; st[q.x] = base + q.y; return { x: q.x, y0: base, y1: base + q.y, p: q }; }); } else s.stacked = null; });
    // extents
    var xmin = Infinity, xmax = -Infinity, ymin = [Infinity, Infinity], ymax = [-Infinity, -Infinity], hasBar = false;
    vis.forEach(function (s) { var ai = s.yAxis === 1 && y2s ? 1 : 0; if (seriesType(el, s) === 'bar') hasBar = true; s.points.forEach(function (q, i) { if (!isFinite(q.x) || !isFinite(q.y)) return; xmin = Math.min(xmin, q.x); xmax = Math.max(xmax, q.x); var y0 = s.stacked ? s.stacked[i].y0 : q.y, y1 = s.stacked ? s.stacked[i].y1 : q.y; ymin[ai] = Math.min(ymin[ai], y0, y1); ymax[ai] = Math.max(ymax[ai], y0, y1); }); });
    if (!isFinite(xmin)) { xmin = 0; xmax = 1; }
    var hasScatter = vis.some(function (s) { return seriesType(el, s) === 'scatter'; });
    if (xs.type === 'category') { xmin = -0.5; xmax = Math.max(0, el.categories.length - 1) + 0.5; } else if (xmin === xmax) { xmin -= 1; xmax += 1; } else if (hasBar) { var pad = (xmax - xmin) / Math.max(1, countDistinctX(vis) - 1) / 2; xmin -= pad; xmax += pad; } else if (hasScatter) { var padS = (xmax - xmin) * 0.08; xmin -= padS; xmax += padS; }
    [ys, y2s].forEach(function (a, i) { if (!a) return; var lo = isFinite(ymin[i]) ? ymin[i] : 0, hi = isFinite(ymax[i]) ? ymax[i] : 1; if (hasBar || vis.some(function (s) { return seriesType(el, s) === 'area'; })) { if (lo > 0) lo = 0; if (hi < 0) hi = 0; } if (lo === hi) { lo -= 1; hi += 1; } if (a.type !== 'log') { var padY = (hi - lo) * (p.labels ? 0.12 : 0.05); if (lo < 0 || !hasBar) lo -= padY; hi += padY; } else { lo = lo > 0 ? lo / 1.5 : lo; hi = hi * 1.5; } a.d0 = a.min != null ? a.min : (a.type === 'log' ? Math.max(lo, 1e-9) : lo); a.d1 = a.max != null ? a.max : hi; if (a.type === 'log' && a.d0 <= 0) a.d0 = 1e-9; });
    xs.d0 = xs.min != null ? xs.min : xmin; xs.d1 = xs.max != null ? xs.max : xmax;
    // zoom ranges
    if (el.range.x) { xs.d0 = el.range.x[0]; xs.d1 = el.range.x[1]; }
    if (el.range.y) { ys.d0 = el.range.y[0]; ys.d1 = el.range.y[1]; }
    // measure labels for margins
    var hAxis = horizontal ? ys : xs, vAxis = horizontal ? xs : ys, v2Axis = horizontal ? null : y2s;
    var measureG = svg('g', {}, root);
    function textW(txt, cls) { var t = svg('text', { 'class': cls }, measureG); t.textContent = txt; var w = t.getComputedTextLength(); return w; }
    var vTicks = ticksFor(vAxis, Math.max(2, Math.floor(box.h / 40)));
    var leftW = vAxis.visible === false ? 4 : Math.max.apply(null, [10].concat(vTicks.map(function (v) { return textW(fmtAxis(vAxis, v), 'wb-chart-tick'); }))) + 10 + (vAxis.title ? 16 : 0);
    var rightW = v2Axis && v2Axis.visible !== false ? 10 : 6;
    if (v2Axis) { var t2 = ticksFor(v2Axis, Math.max(2, Math.floor(box.h / 40))); rightW = Math.max.apply(null, [10].concat(t2.map(function (v) { return textW(fmtAxis(v2Axis, v), 'wb-chart-tick'); }))) + 10 + (v2Axis.title ? 16 : 0); }
    var bottomH = hAxis.visible === false ? 6 : 22 + (hAxis.title ? 16 : 0);
    root.removeChild(measureG);
    var plot = { x: box.x + leftW, y: box.y + 4, w: Math.max(10, box.w - leftW - rightW), h: Math.max(10, box.h - bottomH - 4) };
    el.plot = plot; el.xs = xs; el.ys = ys; el.y2s = y2s; el.horizontal = horizontal;
    var sx = horizontal ? makeScale(ys, plot.y + plot.h, plot.y) : makeScale(xs, plot.x, plot.x + plot.w);   // data x → screen (vertical position when horizontal)
    var sy = horizontal ? makeScale(xs, plot.x, plot.x + plot.w) : makeScale(ys, plot.y + plot.h, plot.y);
    // for horizontal: category (x) runs down the vertical axis, values along the horizontal axis
    var X = horizontal ? function (x) { return sy.map(x); } : function (x) { return sx.map(x); };            // x data → horizontal coordinate (only meaningful when !horizontal)
    var scaleX = horizontal ? makeScale(xs, plot.y, plot.y + plot.h) : makeScale(xs, plot.x, plot.x + plot.w);
    var scaleY = horizontal ? makeScale(ys, plot.x, plot.x + plot.w) : makeScale(ys, plot.y + plot.h, plot.y);
    var scaleY2 = y2s ? (horizontal ? makeScale(y2s, plot.x, plot.x + plot.w) : makeScale(y2s, plot.y + plot.h, plot.y)) : null;
    el.scaleX = scaleX; el.scaleY = scaleY; el.scaleY2 = scaleY2;
    var pos = function (x, y, ai) { var sYY = ai === 1 && scaleY2 ? scaleY2 : scaleY; return horizontal ? [sYY.map(y), scaleX.map(x)] : [scaleX.map(x), sYY.map(y)]; };
    var defs = svg('defs', {}, root); var clipId = 'wbc' + el.ctx.id;
    var clip = svg('clipPath', { id: clipId }, defs); svg('rect', { x: plot.x, y: plot.y, width: plot.w, height: plot.h }, clip);
    // bands + grid + axes
    var gBands = svg('g', { 'class': 'wb-chart-bands', 'clip-path': 'url(#' + clipId + ')' }, root);
    var gGrid = svg('g', { 'class': 'wb-chart-grid' }, root);
    var xTicks = ticksFor(xs, Math.max(2, Math.floor((horizontal ? plot.h : plot.w) / (xs.type === 'time' ? 90 : xs.type === 'category' ? (horizontal ? 16 : 56) : 60))));
    var yTicks = ticksFor(ys, Math.max(2, Math.floor((horizontal ? plot.w : plot.h) / 40)));
    (xs.markers || []).forEach(function (m) { drawMarker(el, gBands, root, m, scaleX, true, plot); });
    (ys.markers || []).forEach(function (m) { drawMarker(el, gBands, root, m, scaleY, false, plot); });
    if (xs.grid !== false && xs.type !== 'category') xTicks.forEach(function (v) { var c = scaleX.map(v); if (horizontal) svg('line', { x1: plot.x, x2: plot.x + plot.w, y1: c, y2: c }, gGrid); else svg('line', { x1: c, x2: c, y1: plot.y, y2: plot.y + plot.h }, gGrid); });
    if (ys.grid !== false) yTicks.forEach(function (v) { var c = scaleY.map(v); if (horizontal) svg('line', { x1: c, x2: c, y1: plot.y, y2: plot.y + plot.h }, gGrid); else svg('line', { x1: plot.x, x2: plot.x + plot.w, y1: c, y2: c }, gGrid); });
    var gAxes = svg('g', { 'class': 'wb-chart-axes' }, root);
    svg('line', { x1: plot.x, x2: plot.x + plot.w, y1: plot.y + plot.h, y2: plot.y + plot.h, 'class': 'wb-chart-axis-line' }, gAxes);
    svg('line', { x1: plot.x, x2: plot.x, y1: plot.y, y2: plot.y + plot.h, 'class': 'wb-chart-axis-line' }, gAxes);
    // bottom axis labels
    var bottomAxis = horizontal ? ys : xs, bottomTicks = horizontal ? yTicks : xTicks, bottomScale = horizontal ? scaleY : scaleX;
    if (bottomAxis.visible !== false) { bottomTicks.forEach(function (v) { var c = bottomScale.map(v); if (c < plot.x - 1 || c > plot.x + plot.w + 1) return; svg('text', { x: c, y: plot.y + plot.h + 15, 'class': 'wb-chart-tick', 'text-anchor': 'middle' }, gAxes).textContent = fmtAxis(bottomAxis, v); }); if (bottomAxis.title) svg('text', { x: plot.x + plot.w / 2, y: plot.y + plot.h + 32, 'class': 'wb-chart-axis-title', 'text-anchor': 'middle' }, gAxes).textContent = bottomAxis.title; }
    var leftAxis = horizontal ? xs : ys, leftTicks = horizontal ? xTicks : yTicks, leftScale = horizontal ? scaleX : scaleY;
    if (leftAxis.visible !== false) { leftTicks.forEach(function (v) { var c = leftScale.map(v); if (c < plot.y - 1 || c > plot.y + plot.h + 1) return; svg('text', { x: plot.x - 6, y: c + 4, 'class': 'wb-chart-tick', 'text-anchor': 'end' }, gAxes).textContent = fmtAxis(leftAxis, v); }); if (leftAxis.title) { var t = svg('text', { 'class': 'wb-chart-axis-title', 'text-anchor': 'middle', transform: 'translate(' + (box.x + 12) + ',' + (plot.y + plot.h / 2) + ') rotate(-90)' }, gAxes); t.textContent = leftAxis.title; } }
    if (y2s && !horizontal && y2s.visible !== false) { svg('line', { x1: plot.x + plot.w, x2: plot.x + plot.w, y1: plot.y, y2: plot.y + plot.h, 'class': 'wb-chart-axis-line' }, gAxes); ticksFor(y2s, Math.max(2, Math.floor(plot.h / 40))).forEach(function (v) { var c = scaleY2.map(v); svg('text', { x: plot.x + plot.w + 6, y: c + 4, 'class': 'wb-chart-tick' }, gAxes).textContent = fmtAxis(y2s, v); }); if (y2s.title) { var t2 = svg('text', { 'class': 'wb-chart-axis-title', 'text-anchor': 'middle', transform: 'translate(' + (plot.x + plot.w + rightW - 6) + ',' + (plot.y + plot.h / 2) + ') rotate(90)' }, gAxes); t2.textContent = y2s.title; } }
    // series
    var gSeries = svg('g', { 'class': 'wb-chart-series', 'clip-path': 'url(#' + clipId + ')' }, root);
    var bars = vis.filter(function (s) { return seriesType(el, s) === 'bar'; });
    var barGroups = {}; var nGroups = 0; bars.forEach(function (s) { var key = s.stackKey || s.id; if (!(key in barGroups)) { barGroups[key] = nGroups++; } });
    var slotW = xs.type === 'category' ? Math.abs(scaleX.map(1) - scaleX.map(0)) : (countDistinctX(bars) > 1 ? Math.abs(scaleX.map(xs.d0 + (xs.d1 - xs.d0) / Math.max(1, countDistinctX(bars) + 1)) - scaleX.map(xs.d0)) : Math.abs(horizontal ? plot.h : plot.w) * 0.5);
    var groupW = slotW * 0.7, barW = Math.max(1, groupW / Math.max(1, nGroups));
    el.hit = [];
    vis.forEach(function (s, si) {
      var idx = el.series.indexOf(s), color = seriesColor(el, s, idx), t = seriesType(el, s), ai = s.yAxis === 1 && scaleY2 ? 1 : 0;
      var g = svg('g', { 'class': 'wb-chart-s wb-chart-s-' + t, 'data-series': s.id }, gSeries);
      var pts = s.points.filter(function (q) { return isFinite(q.x) && isFinite(q.y); });
      if (t === 'bar') {
        var gi = barGroups[s.stackKey || s.id];
        pts.forEach(function (q, i) {
          var y0 = s.stacked ? s.stacked[s.points.indexOf(q)].y0 : 0, y1 = s.stacked ? s.stacked[s.points.indexOf(q)].y1 : q.y;
          var c = scaleX.map(q.x) - groupW / 2 + gi * barW; var a = pos(q.x, y0, ai), b = pos(q.x, y1, ai); var r;
          if (horizontal) r = svg('rect', { x: Math.min(a[0], b[0]), y: c, width: Math.abs(b[0] - a[0]), height: Math.max(1, barW - 1), fill: q.c || color, 'class': 'wb-chart-bar' }, g);
          else r = svg('rect', { x: c, y: Math.min(a[1], b[1]), width: Math.max(1, barW - 1), height: Math.abs(b[1] - a[1]), fill: q.c || color, 'class': 'wb-chart-bar' }, g);
          if (isSelected(el, s.id, q, i)) r.classList.add('wb-chart-selected');
          var bx = +r.getAttribute('x'), by = +r.getAttribute('y'), bw = +r.getAttribute('width'), bh = +r.getAttribute('height');
          el.hit.push({ s: s, q: q, i: i, cx: horizontal ? b[0] : c + barW / 2, cy: horizontal ? c + barW / 2 : b[1], el: r, box: [bx, by, bw, bh] });
          if (p.labels) svg('text', { x: horizontal ? b[0] + 4 : c + barW / 2, y: horizontal ? c + barW / 2 + 4 : b[1] - 4, 'class': 'wb-chart-label', 'text-anchor': horizontal ? 'start' : 'middle' }, g).textContent = fmtNumber(q.y, (ai === 1 ? y2s : ys).format);
        });
        return;
      }
      if (t === 'scatter') {
        var sizes = pts.map(function (q) { return q.s == null ? 1 : q.s; }); var smax = Math.max.apply(null, sizes.concat([1]));
        pts.forEach(function (q, i) { var xy = pos(q.x, q.y, ai); var rr = q.s == null ? 4 : 3 + 12 * Math.sqrt(q.s / smax); var c = svg('circle', { cx: xy[0], cy: xy[1], r: rr, fill: q.c || color, 'class': 'wb-chart-dot' }, g); if (isSelected(el, s.id, q, i)) c.classList.add('wb-chart-selected'); el.hit.push({ s: s, q: q, i: i, cx: xy[0], cy: xy[1], el: c }); if (p.labels || q.l) svg('text', { x: xy[0], y: xy[1] - rr - 3, 'class': 'wb-chart-label', 'text-anchor': 'middle' }, g).textContent = q.l || fmtNumber(q.y, ys.format); });
        return;
      }
      // line / step / area
      var sorted = pts.slice().sort(function (a, b) { return a.x - b.x; });
      var maxPts = Math.max(50, Math.ceil((horizontal ? plot.h : plot.w) * 2));
      var drawn = sorted.length > maxPts && !s.stacked ? lttb(sorted, maxPts) : sorted;
      var path = '', area = '';
      var stackIndex = {}; if (s.stacked) s.stacked.forEach(function (st) { stackIndex[st.x] = st; });
      var coords = drawn.map(function (q) { var y1v = s.stacked ? stackIndex[q.x].y1 : q.y; return pos(q.x, y1v, ai); });
      if (s.smooth && t !== 'step' && coords.length > 2) path = monotone(coords); else coords.forEach(function (c, i) { if (i === 0) path += 'M' + c[0] + ' ' + c[1]; else if (t === 'step') { var prev = coords[i - 1]; path += horizontal ? 'L' + prev[0] + ' ' + c[1] + 'L' + c[0] + ' ' + c[1] : 'L' + c[0] + ' ' + prev[1] + 'L' + c[0] + ' ' + c[1]; } else path += 'L' + c[0] + ' ' + c[1]; });
      if (t === 'area' && coords.length) {
        var axY = ai === 1 && y2s ? y2s : ys;
        var base = drawn.map(function (q) { var y0v = s.stacked ? stackIndex[q.x].y0 : (axY.type === 'log' ? axY.d0 : 0); return pos(q.x, clamp(y0v, axY.d0, axY.d1), ai); }).reverse();
        area = path; base.forEach(function (c, i) { if (t === 'step' || i === 0) area += 'L' + c[0] + ' ' + c[1]; else area += 'L' + c[0] + ' ' + c[1]; }); area += 'Z';
        svg('path', { d: area, fill: color, 'class': 'wb-chart-area' }, g);
      }
      var dash = s.lineStyle === 'dashed' ? '6 4' : s.lineStyle === 'dotted' ? '2 3' : null;
      svg('path', { d: path, fill: 'none', stroke: color, 'stroke-dasharray': dash, 'class': 'wb-chart-line' }, g);
      var showMarkers = s.marker && s.marker !== 'none' && drawn.length <= 500;
      drawn.forEach(function (q, i) { var c = coords[i]; var idxInSeries = s.points.indexOf(q); var m = null; if (showMarkers) m = drawMarkerShape(g, s.marker, c[0], c[1], q.c || color); else if (drawn.length <= 60) m = svg('circle', { cx: c[0], cy: c[1], r: 2.5, fill: q.c || color, 'class': 'wb-chart-dot wb-chart-dot-small' }, g); if (m && isSelected(el, s.id, q, idxInSeries)) m.classList.add('wb-chart-selected'); el.hit.push({ s: s, q: q, i: idxInSeries, cx: c[0], cy: c[1], el: m }); if (p.labels && drawn.length <= 60) svg('text', { x: c[0], y: c[1] - 7, 'class': 'wb-chart-label', 'text-anchor': 'middle' }, g).textContent = fmtNumber(q.y, ys.format); });
    });
    // highlight
    if (el.highlightSeries && el.highlightKey != null) { var hk = el.highlightKey; el.hit.forEach(function (h) { if (h.s.id === el.highlightSeries && h.el && (h.q.k === hk || h.q.x === hk)) h.el.classList.add('wb-chart-highlight'); }); }
    // overlay
    el.overlay = svg('g', { 'class': 'wb-chart-overlay' }, root);
  }
  function countDistinctX(series) { var set = {}; series.forEach(function (s) { s.points.forEach(function (q) { set[q.x] = 1; }); }); return Object.keys(set).length; }
  function isSelected(el, id, q, i) { var k = id + ':' + (q.k != null ? 'k' + q.k : 'i' + i); return !!el.sel[k] || (el.props.selection === 'SERIES' && el.sel['s:' + id]); }
  function monotone(c) {
    var n = c.length, d = '', m = [], dx = [], dy = [];
    for (var i = 0; i < n - 1; i++) { dx.push(c[i + 1][0] - c[i][0]); dy.push(c[i + 1][1] - c[i][1]); m.push(dx[i] === 0 ? 0 : dy[i] / dx[i]); }
    var t = [m[0]]; for (var k = 1; k < n - 1; k++) t.push(m[k - 1] * m[k] <= 0 ? 0 : (m[k - 1] + m[k]) / 2); t.push(m[n - 2]);
    d = 'M' + c[0][0] + ' ' + c[0][1];
    for (var j = 0; j < n - 1; j++) { var h = dx[j] / 3; d += 'C' + (c[j][0] + h) + ' ' + (c[j][1] + t[j] * h) + ' ' + (c[j + 1][0] - h) + ' ' + (c[j + 1][1] - t[j + 1] * h) + ' ' + c[j + 1][0] + ' ' + c[j + 1][1]; }
    return d;
  }
  function drawMarkerShape(g, kind, x, y, color) {
    switch (kind) {
      case 'square': return svg('rect', { x: x - 3.5, y: y - 3.5, width: 7, height: 7, fill: color, 'class': 'wb-chart-dot' }, g);
      case 'diamond': return svg('path', { d: 'M' + x + ' ' + (y - 4.5) + 'L' + (x + 4.5) + ' ' + y + 'L' + x + ' ' + (y + 4.5) + 'L' + (x - 4.5) + ' ' + y + 'Z', fill: color, 'class': 'wb-chart-dot' }, g);
      case 'triangle': return svg('path', { d: 'M' + x + ' ' + (y - 4.5) + 'L' + (x + 4.5) + ' ' + (y + 3.5) + 'L' + (x - 4.5) + ' ' + (y + 3.5) + 'Z', fill: color, 'class': 'wb-chart-dot' }, g);
      default: return svg('circle', { cx: x, cy: y, r: 3.5, fill: color, 'class': 'wb-chart-dot' }, g);
    }
  }
  function drawMarker(el, gBands, root, m, scale, isX, plot) {
    var horizontal = el.horizontal; var alongX = isX ? !horizontal : horizontal;   // marker line perpendicular to the axis it belongs to
    var a = scale.map(m.from);
    if (m.to != null) { var b = scale.map(m.to); if (alongX) svg('rect', { x: Math.min(a, b), y: plot.y, width: Math.abs(b - a), height: plot.h, fill: m.color || 'var(--wb-accent)', 'class': 'wb-chart-band' }, gBands); else svg('rect', { x: plot.x, y: Math.min(a, b), width: plot.w, height: Math.abs(b - a), fill: m.color || 'var(--wb-accent)', 'class': 'wb-chart-band' }, gBands); return; }
    var g = svg('g', { 'class': 'wb-chart-marker' }, gBands);
    if (alongX) { svg('line', { x1: a, x2: a, y1: plot.y, y2: plot.y + plot.h, stroke: m.color || 'var(--wb-danger)' }, g); if (m.label) svg('text', { x: a + 4, y: plot.y + 12, 'class': 'wb-chart-marker-label', fill: m.color || 'var(--wb-danger)' }, g).textContent = m.label; }
    else { svg('line', { x1: plot.x, x2: plot.x + plot.w, y1: a, y2: a, stroke: m.color || 'var(--wb-danger)' }, g); if (m.label) svg('text', { x: plot.x + plot.w - 4, y: a - 4, 'class': 'wb-chart-marker-label', 'text-anchor': 'end', fill: m.color || 'var(--wb-danger)' }, g).textContent = m.label; }
  }

  /* ---------------- pie ---------------- */
  function drawPie(el, root, box, vis) {
    var s = vis[0], donut = seriesType(el, s) === 'donut', p = el.props;
    var labelPad = p.labels !== false ? 80 : 6;
    var cx = box.x + box.w / 2, cy = box.y + box.h / 2, R = Math.max(10, Math.min(box.w - 2 * labelPad, box.h - 2 * (p.labels !== false ? 22 : 6)) / 2), r0 = donut ? R * 0.55 : 0;
    var pts = s.points.filter(function (q) { return isFinite(q.y) && q.y > 0; }); var total = pts.reduce(function (a, q) { return a + q.y; }, 0) || 1;
    var g = svg('g', { 'class': 'wb-chart-s wb-chart-s-pie', 'data-series': s.id }, root); el.hit = []; el.plot = null;
    var a0 = -Math.PI / 2;
    pts.forEach(function (q, i) {
      var a1 = a0 + q.y / total * Math.PI * 2; var color = q.c || seriesColor(el, {}, i);
      var big = a1 - a0 > Math.PI ? 1 : 0;
      var x0 = cx + R * Math.cos(a0), y0 = cy + R * Math.sin(a0), x1 = cx + R * Math.cos(a1), y1 = cy + R * Math.sin(a1);
      var d = 'M' + x0 + ' ' + y0 + 'A' + R + ' ' + R + ' 0 ' + big + ' 1 ' + x1 + ' ' + y1;
      if (r0 > 0) { var xi1 = cx + r0 * Math.cos(a1), yi1 = cy + r0 * Math.sin(a1), xi0 = cx + r0 * Math.cos(a0), yi0 = cy + r0 * Math.sin(a0); d += 'L' + xi1 + ' ' + yi1 + 'A' + r0 + ' ' + r0 + ' 0 ' + big + ' 0 ' + xi0 + ' ' + yi0 + 'Z'; } else d += 'L' + cx + ' ' + cy + 'Z';
      var path = svg('path', { d: d, fill: color, 'class': 'wb-chart-slice' }, g);
      var idx = s.points.indexOf(q); if (isSelected(el, s.id, q, idx)) path.classList.add('wb-chart-selected');
      var am = (a0 + a1) / 2; var lx = cx + (R + 14) * Math.cos(am), ly = cy + (R + 14) * Math.sin(am);
      if (p.labels !== false && (a1 - a0) > 0.12) { var label = (q.l || el.categories[q.x] || '') + ' ' + Math.round(q.y / total * 100) + '%'; svg('text', { x: lx, y: ly + 4, 'class': 'wb-chart-label', 'text-anchor': Math.cos(am) < -0.1 ? 'end' : Math.cos(am) > 0.1 ? 'middle' : 'middle' }, g).textContent = label.trim(); }
      el.hit.push({ s: s, q: q, i: idx, cx: cx + (R + r0) / 2 * Math.cos(am), cy: cy + (R + r0) / 2 * Math.sin(am), el: path, slice: true, a0: a0, a1: a1, cxp: cx, cyp: cy, R: R, r0: r0 });
      a0 = a1;
    });
    if (p.title && donut) svg('text', { x: cx, y: cy + 4, 'class': 'wb-chart-subtitle', 'text-anchor': 'middle' }, g).textContent = '';
    el.overlay = svg('g', { 'class': 'wb-chart-overlay' }, root);
  }

  /* ---------------- interaction ---------------- */
  function localXY(el, e) { var r = el.svg.getBoundingClientRect(); return [e.clientX - r.left, e.clientY - r.top]; }
  function nearest(el, x, y) {
    var best = null, bd = Infinity;
    if (!el.hit) return null;
    el.hit.forEach(function (h) {
      var d;
      if (h.slice) { var dx = x - h.cxp, dy = y - h.cyp, rr = Math.sqrt(dx * dx + dy * dy); if (rr > h.R || rr < h.r0) return; var a = Math.atan2(dy, dx); while (a < -Math.PI / 2) a += Math.PI * 2; while (a >= Math.PI * 1.5) a -= Math.PI * 2; d = a >= h.a0 && a < h.a1 ? 0 : Infinity; }
      else if (h.box && x >= h.box[0] && x <= h.box[0] + h.box[2] && y >= h.box[1] && y <= h.box[1] + h.box[3]) d = 0;
      else if (el.props.tooltip === 'SHARED' || el.props.tooltip === 'AXIS') d = el.horizontal ? Math.abs(y - h.cy) : Math.abs(x - h.cx);
      else { var ddx = x - h.cx, ddy = y - h.cy; d = Math.sqrt(ddx * ddx + ddy * ddy); }
      if (d < bd) { bd = d; best = h; }
    });
    return best && bd < 40 ? best : null;
  }
  function onMove(el, e) {
    if (el.drag) { dragMove(el, e); return; }
    var mode = el.props.tooltip || 'SHARED'; if (mode === 'NONE') { hideTip(el); return; }
    var xy = localXY(el, e); var h = nearest(el, xy[0], xy[1]);
    if (!h) { hideTip(el); return; }
    showTip(el, h, xy, mode);
  }
  function hideTip(el) { el.tip.classList.add('wb-hidden'); if (el.overlay) while (el.overlay.firstChild) el.overlay.removeChild(el.overlay.firstChild); }
  function tipRow(color, name, value) { return '<div class="wb-chart-tip-row"><span class="wb-chart-tip-sw" style="background:' + wb.esc(color) + '"></span><span class="wb-chart-tip-name">' + wb.esc(name) + '</span><span class="wb-chart-tip-val">' + wb.esc(value) + '</span></div>'; }
  function showTip(el, h, xy, mode) {
    var xs = el.xs, ys = el.ys, html = '';
    if (h.slice) { var total = h.s.points.reduce(function (a, q) { return a + (q.y > 0 ? q.y : 0); }, 0) || 1; html = '<div class="wb-chart-tip-head">' + wb.esc(h.q.l || el.categories[h.q.x] || String(h.q.x)) + '</div>' + tipRow(h.el.getAttribute('fill'), h.s.name || h.s.id, fmtNumber(h.q.y, ys && ys.format) + ' (' + Math.round(h.q.y / total * 100) + '%)'); }
    else {
      var head = xs.type === 'category' ? (el.categories[Math.round(h.q.x)] || '') : fmtAxis(xs, h.q.x);
      html = '<div class="wb-chart-tip-head">' + wb.esc(h.q.l || head) + '</div>';
      var rows = mode === 'SHARED' ? el.hit.filter(function (o) { return o.q.x === h.q.x; }) : [h];
      if (mode !== 'AXIS') rows.forEach(function (o) { var ai = o.s.yAxis === 1 && el.y2s ? el.y2s : ys; var idx = el.series.indexOf(o.s); html += tipRow(o.q.c || seriesColor(el, o.s, idx), o.s.name || o.s.id, fmtNumber(o.q.y, ai.format) + (o.q.s != null ? ' · ' + fmtAuto(o.q.s) : '')); });
      // crosshair
      if (el.overlay && el.plot) { while (el.overlay.firstChild) el.overlay.removeChild(el.overlay.firstChild); if (el.horizontal) svg('line', { x1: el.plot.x, x2: el.plot.x + el.plot.w, y1: h.cy, y2: h.cy, 'class': 'wb-chart-crosshair' }, el.overlay); else svg('line', { x1: h.cx, x2: h.cx, y1: el.plot.y, y2: el.plot.y + el.plot.h, 'class': 'wb-chart-crosshair' }, el.overlay); }
    }
    el.tip.innerHTML = html; el.tip.classList.remove('wb-hidden');
    var tw = el.tip.offsetWidth, th = el.tip.offsetHeight; var left = xy[0] + 14, top = xy[1] - th / 2;
    if (left + tw > el.clientWidth) left = xy[0] - tw - 14; top = clamp(top, 0, Math.max(0, el.clientHeight - th));
    el.tip.style.left = left + 'px'; el.tip.style.top = top + 'px';
  }
  function onClick(el, e) {
    if (el.suppressClick) { el.suppressClick = false; return; }
    if (e.target.closest('.wb-chart-legend')) return;
    var xy = localXY(el, e); var h = nearest(el, xy[0], xy[1]); if (!h) return;
    var mode = el.props.selection || 'NONE';
    if (mode !== 'NONE') { var key = h.s.id + ':' + (h.q.k != null ? 'k' + h.q.k : 'i' + h.i); if (mode === 'SERIES') { el.sel = {}; el.sel['s:' + h.s.id] = true; } else if (mode === 'MULTI_POINT' && (e.ctrlKey || e.metaKey)) { if (el.sel[key]) delete el.sel[key]; else el.sel[key] = true; } else { el.sel = {}; el.sel[key] = true; } schedule(el); }
    el.ctx.emit('pointClick', { series: h.s.id, index: h.i, k: h.q.k == null ? null : h.q.k, x: h.q.x, y: h.q.y, ctrl: e.ctrlKey || e.metaKey, shift: e.shiftKey, alt: e.altKey });
  }
  function zoomAxes(el) { var z = el.props.zoom || 'NONE'; return { x: z === 'X' || z === 'XY', y: z === 'Y' || z === 'XY' }; }
  function emitRange(el, reset) {
    var d = { reset: !!reset };
    d.xFrom = el.range.x ? el.range.x[0] : null; d.xTo = el.range.x ? el.range.x[1] : null; d.yFrom = el.range.y ? el.range.y[0] : null; d.yTo = el.range.y ? el.range.y[1] : null;
    el.ctx.emit('rangeSelect', d);
  }
  function resetRange(el, emit) { el.range = {}; schedule(el); if (emit) emitRange(el, true); }
  function onWheel(el, e) {
    var za = zoomAxes(el); if (!za.x && !za.y || !el.plot || !el.xs) return;
    e.preventDefault(); var xy = localXY(el, e); var f = e.deltaY < 0 ? 0.8 : 1.25;
    if (za.x) { var sc = el.horizontal ? el.scaleX : el.scaleX; var c = el.horizontal ? sc.invert(xy[1]) : sc.invert(xy[0]); var lo = el.xs.d0, hi = el.xs.d1; el.range.x = [c - (c - lo) * f, c + (hi - c) * f]; }
    if (za.y) { var c2 = el.horizontal ? el.scaleY.invert(xy[0]) : el.scaleY.invert(xy[1]); el.range.y = [c2 - (c2 - el.ys.d0) * f, c2 + (el.ys.d1 - c2) * f]; }
    schedule(el); emitRange(el, false);
  }
  function onDown(el, e) {
    var za = zoomAxes(el); if (!za.x && !za.y || !el.plot || e.button !== 0 || e.target.closest('.wb-chart-legend')) return;
    var xy = localXY(el, e); if (xy[0] < el.plot.x || xy[0] > el.plot.x + el.plot.w || xy[1] < el.plot.y || xy[1] > el.plot.y + el.plot.h) return;
    el.drag = { x0: xy[0], y0: xy[1], brush: e.shiftKey, xr: el.range.x ? el.range.x.slice() : [el.xs.d0, el.xs.d1], yr: el.range.y ? el.range.y.slice() : [el.ys.d0, el.ys.d1], moved: false };
    e.preventDefault();
    var up = function (ev) { document.removeEventListener('mouseup', up); dragEnd(el, ev); };
    document.addEventListener('mouseup', up);
  }
  function dragMove(el, e) {
    var d = el.drag, xy = localXY(el, e), za = zoomAxes(el); var dx = xy[0] - d.x0, dy = xy[1] - d.y0;
    if (Math.abs(dx) > 2 || Math.abs(dy) > 2) d.moved = true;
    if (d.brush) { if (!el.overlay) return; while (el.overlay.firstChild) el.overlay.removeChild(el.overlay.firstChild); var bx = za.x ? Math.min(d.x0, xy[0]) : el.plot.x, bw = za.x ? Math.abs(dx) : el.plot.w, by = za.y ? Math.min(d.y0, xy[1]) : el.plot.y, bh = za.y ? Math.abs(dy) : el.plot.h; svg('rect', { x: bx, y: by, width: bw, height: bh, 'class': 'wb-chart-brush' }, el.overlay); return; }
    // pan
    if (za.x) { var pxPerUnit = el.horizontal ? (el.plot.h / (d.xr[1] - d.xr[0])) : (el.plot.w / (d.xr[1] - d.xr[0])); var shift = (el.horizontal ? dy : dx) / pxPerUnit; el.range.x = [d.xr[0] - shift, d.xr[1] - shift]; }
    if (za.y) { var pyPerUnit = el.horizontal ? (el.plot.w / (d.yr[1] - d.yr[0])) : (el.plot.h / (d.yr[1] - d.yr[0])); var shiftY = (el.horizontal ? -dx : dy) / pyPerUnit; el.range.y = [d.yr[0] + shiftY, d.yr[1] + shiftY]; }
    schedule(el);
  }
  function dragEnd(el, e) {
    var d = el.drag; el.drag = null; if (!d) return;
    var xy = localXY(el, e), za = zoomAxes(el);
    if (d.brush) {
      if (el.overlay) while (el.overlay.firstChild) el.overlay.removeChild(el.overlay.firstChild);
      if (d.moved) {
        if (za.x) { var a = el.scaleX.invert(el.horizontal ? d.y0 : d.x0), b = el.scaleX.invert(el.horizontal ? xy[1] : xy[0]); el.range.x = [Math.min(a, b), Math.max(a, b)]; }
        if (za.y) { var c = el.scaleY.invert(el.horizontal ? d.x0 : d.y0), e2 = el.scaleY.invert(el.horizontal ? xy[0] : xy[1]); el.range.y = [Math.min(c, e2), Math.max(c, e2)]; }
        schedule(el); emitRange(el, false);
      }
    } else if (d.moved) emitRange(el, false);
    if (d.moved) el.suppressClick = true;
  }
  function onKey(el, e) {
    if (!el.hit || !el.hit.length) return;
    if (e.key === 'ArrowRight' || e.key === 'ArrowLeft') { e.preventDefault(); var i = el.focusIdx == null ? -1 : el.focusIdx; i = (i + (e.key === 'ArrowRight' ? 1 : -1) + el.hit.length) % el.hit.length; el.focusIdx = i; var h = el.hit[i]; showTip(el, h, [h.cx, h.cy], el.props.tooltip === 'NONE' ? 'POINT' : (el.props.tooltip || 'SHARED')); }
    else if (e.key === 'Enter' && el.focusIdx != null) { var hh = el.hit[el.focusIdx]; el.ctx.emit('pointClick', { series: hh.s.id, index: hh.i, k: hh.q.k == null ? null : hh.q.k, x: hh.q.x, y: hh.q.y }); }
    else if (e.key === 'Escape') { hideTip(el); if (el.props.zoom && el.props.zoom !== 'NONE') resetRange(el, true); }
  }

  /* ---------------- export ---------------- */
  function inlineSvg(el) {
    var clone = el.svg.cloneNode(true); var src = el.svg.querySelectorAll('*'), dst = clone.querySelectorAll('*');
    var PROPS = ['fill', 'stroke', 'stroke-width', 'stroke-dasharray', 'font-size', 'font-family', 'font-weight', 'opacity', 'fill-opacity', 'stroke-opacity'];
    var rootStyle = getComputedStyle(el.svg);
    clone.style.backgroundColor = getComputedStyle(el).backgroundColor; clone.setAttribute('xmlns', NS); clone.style.fontFamily = rootStyle.fontFamily;
    for (var i = 0; i < src.length; i++) { var cs = getComputedStyle(src[i]); PROPS.forEach(function (p) { var v = cs.getPropertyValue(p); if (v && v !== 'none' || p === 'fill') dst[i].style.setProperty(p, v); }); if (dst[i].classList.contains('wb-chart-crosshair') || dst[i].classList.contains('wb-chart-brush')) dst[i].remove(); }
    return new XMLSerializer().serializeToString(clone);
  }
  function rasterize(el, w, h, cb) {
    var s = inlineSvg(el); var W = w || el.clientWidth, H = h || el.clientHeight;
    var img = new Image(); var url = URL.createObjectURL(new Blob([s], { type: 'image/svg+xml;charset=utf-8' }));
    img.onload = function () { var c = document.createElement('canvas'); c.width = W * 2; c.height = H * 2; var g = c.getContext('2d'); g.fillStyle = getComputedStyle(el).backgroundColor || '#fff'; g.fillRect(0, 0, c.width, c.height); g.drawImage(img, 0, 0, c.width, c.height); URL.revokeObjectURL(url); cb(null, c.toDataURL('image/png')); };
    img.onerror = function () { URL.revokeObjectURL(url); cb('rasterisation failed'); };
    img.src = url;
  }
  function doExport(el, id, format, w, h) {
    if (format === 'svg') { el.ctx.emit('export', { id: id, format: 'svg', data: inlineSvg(el) }); return; }
    rasterize(el, w, h, function (err, data) { el.ctx.emit('export', err ? { id: id, format: 'png', error: err } : { id: id, format: 'png', data: data }); });
  }
  function download(el, format, name) {
    var save = function (href) { var a = document.createElement('a'); a.href = href; a.download = name || ('chart.' + format); document.body.appendChild(a); a.click(); a.remove(); };
    if (format === 'svg') { var url = URL.createObjectURL(new Blob([inlineSvg(el)], { type: 'image/svg+xml' })); save(url); setTimeout(function () { URL.revokeObjectURL(url); }, 1000); }
    else rasterize(el, 0, 0, function (err, data) { if (!err) save(data); });
  }
  wb.chartFormat = { number: fmtNumber, time: fmtTime, lttb: lttb, ticks: ticksFor };
})();
