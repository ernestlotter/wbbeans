/*
 * wb.Dock — the docking area. The server owns the layout model; this renderer draws it (splits with gutters,
 * tab groups per mode, floating windows, minimised side bars, the maximized mode) and reports semantic events:
 * select, activate, close, move, resize, maximize, float, dock, minimize. TopComponent elements (the children)
 * are reparented on every layout change, never recreated, so their content keeps scroll position and focus.
 */
(function () {
  var ICON = {
    close: '<svg viewBox="0 0 12 12" width="10" height="10"><path d="M2 2l8 8M10 2l-8 8" stroke="currentColor" stroke-width="1.6" fill="none"/></svg>',
    max: '<svg viewBox="0 0 12 12" width="11" height="11"><rect x="1.5" y="1.5" width="9" height="9" stroke="currentColor" stroke-width="1.4" fill="none"/></svg>',
    restore: '<svg viewBox="0 0 12 12" width="11" height="11"><path d="M4 4V1.5h6.5V8H8" stroke="currentColor" stroke-width="1.4" fill="none"/><rect x="1.5" y="4" width="6.5" height="6.5" stroke="currentColor" stroke-width="1.4" fill="none"/></svg>',
    min: '<svg viewBox="0 0 12 12" width="11" height="11"><path d="M2 9.5h8" stroke="currentColor" stroke-width="1.6"/></svg>',
    float: '<svg viewBox="0 0 12 12" width="11" height="11"><path d="M5 2H2v8h8V7M7 2h3v3M10 2L5.5 6.5" stroke="currentColor" stroke-width="1.4" fill="none"/></svg>',
    dock: '<svg viewBox="0 0 12 12" width="11" height="11"><path d="M7 10h3V2H2v3M2 10l4.5-4.5M2 7v3h3" stroke="currentColor" stroke-width="1.4" fill="none"/></svg>',
    menu: '<svg viewBox="0 0 12 12" width="11" height="11"><path d="M2 4l4 4 4-4" stroke="currentColor" stroke-width="1.6" fill="none"/></svg>'
  };
  var ALLOWED = { B: 1, I: 1, EM: 1, STRONG: 1, U: 1, S: 1, SPAN: 1, FONT: 1, SMALL: 1 };
  function safeHtml(html) {
    var t = document.createElement('template'); t.innerHTML = html;
    var walk = function (n) {
      Array.from(n.children).forEach(function (c) {
        if (!ALLOWED[c.tagName]) { c.replaceWith(document.createTextNode(c.textContent)); return; }
        Array.from(c.attributes).forEach(function (a) { if (a.name !== 'color' && a.name !== 'style') c.removeAttribute(a.name); });
        if (c.getAttribute('style') && !/^\s*color\s*:[^;]+;?\s*$/i.test(c.getAttribute('style'))) c.removeAttribute('style');
        walk(c);
      });
    };
    walk(t.content);
    return t.content;
  }

  wb.register('wb.Dock', {
    create: function (ctx) {
      var el = wb.h('div', 'wb-dock');
      el.ctx = ctx; el.tcs = new Map(); el.layout = null; el.modes = {}; el.labels = {}; el.active = null;
      el.park = wb.h('div', 'wb-dock-park'); el.park.hidden = true; el.appendChild(el.park);
      el.area = wb.h('div', 'wb-dock-area'); el.appendChild(el.area);
      el.refreshTab = function (tcEl) { var tab = el.tabOf(tcEl.dataset.tcId); if (tab) R.fillTab(el, tab, tcEl); };
      el.tabOf = function (id) { return el.area.querySelector('.wb-dk-tab[data-tc="' + CSS.escape(id) + '"]'); };
      el.addEventListener('mousedown', function (e) {
        var tcEl = e.target.closest('.wb-tc'); if (!tcEl || !el.tcs.has(tcEl.dataset.tcId)) return;
        if (tcEl.dataset.tcId !== el.active) ctx.emit('activate', { tc: tcEl.dataset.tcId });
      }, true);
      document.addEventListener('keydown', function (e) { if (e.key === 'Escape') R.closePopups(el); });
      return el;
    },
    update: function (el, props, changed) {
      if (changed.has('modes')) el.modes = props.modes || {};
      if (changed.has('labels')) el.labels = props.labels || {};
      if (changed.has('active')) { el.active = props.active; R.markActive(el); }
      if (changed.has('layout')) { el.layout = props.layout; R.render(el); }
      else if (changed.has('modes') || changed.has('labels')) R.render(el);
    },
    insertChild: function (el, child, i, ctx, node) {
      var id = node.constraint || (node.props && node.props.tcId); child.dock = el; child.dataset.tcId = id;
      el.tcs.set(id, child); el.park.appendChild(child);
      if (el.layout) R.render(el);
    },
    removeChild: function (el, child) { el.tcs.delete(child.dataset.tcId); child.remove(); if (el.layout) R.render(el); },
    destroy: function (el) { R.closePopups(el); }
  });

  var R = {
    /* ---------- full (re)build; TC elements are moved, not recreated ---------- */
    render: function (el) {
      var L = el.layout; if (!L) return;
      R.closePopups(el);
      el.tcs.forEach(function (tcEl) { el.park.appendChild(tcEl); tcEl.classList.add('wb-tc-hidden'); });
      el.area.innerHTML = '';
      var mins = L.minimised || {};
      var bars = { left: [], right: [], top: [], bottom: [] };
      Object.keys(mins).forEach(function (name) { var m = mins[name]; (bars[m.side] || bars.left).push(m); });
      var center = wb.h('div', 'wb-dock-center');
      var maxNode = L.maximized ? R.findMode(L.root, L.maximized) : null;
      center.appendChild(maxNode ? R.modeEl(el, maxNode, null, 'max') : R.build(el, L.root, []));
      if (bars.top.length) el.area.appendChild(R.sidebar(el, 'top', bars.top));
      var mid = wb.h('div', 'wb-dock-mid');
      if (bars.left.length) mid.appendChild(R.sidebar(el, 'left', bars.left));
      mid.appendChild(center);
      if (bars.right.length) mid.appendChild(R.sidebar(el, 'right', bars.right));
      el.area.appendChild(mid);
      if (bars.bottom.length) el.area.appendChild(R.sidebar(el, 'bottom', bars.bottom));
      (L.floating || []).forEach(function (f) { el.area.appendChild(R.floatingEl(el, f)); });
      R.markActive(el);
    },
    findMode: function (n, name) { if (!n) return null; if (n.type === 'mode') return n.name === name ? n : null; for (var i = 0; i < n.children.length; i++) { var r = R.findMode(n.children[i], name); if (r) return r; } return null; },
    build: function (el, node, path) {
      if (node.type === 'mode') return R.modeEl(el, node, path, 'docked');
      var s = wb.h('div', 'wb-split wb-split-' + node.dir); s.path = path; s.node = node;
      node.children.forEach(function (c, i) {
        if (i > 0) s.appendChild(R.gutter(el, s, node, i));
        var pane = wb.h('div', 'wb-pane'); R.applySize(pane, node.sizes[i]); pane.appendChild(R.build(el, c, path.concat(i))); s.appendChild(pane);
      });
      return s;
    },
    applySize: function (pane, size) { if (size === '*' || size == null) { pane.style.flex = '1 1 0'; pane.dataset.star = '1'; } else { pane.style.flex = '0 0 ' + size + 'px'; delete pane.dataset.star; } },

    /* ---------- one mode: tab strip + body ---------- */
    modeEl: function (el, m, path, kind) {
      var L = el.layout, info = el.modes[m.name] || {}, labels = el.labels;
      var wrap = wb.h('div', 'wb-mode' + (m.tcs.length ? '' : ' wb-mode-empty')); wrap.dataset.mode = m.name; wrap.mode = m;
      if (info.side === 'center') wrap.classList.add('wb-mode-center');
      var strip = wb.h('div', 'wb-dk-tabstrip'); var tabs = wb.h('div', 'wb-dk-tabs'); strip.appendChild(tabs);
      var body = wb.h('div', 'wb-mode-body'); wrap.append(strip, body);
      m.tcs.forEach(function (id) {
        var tcEl = el.tcs.get(id); if (!tcEl) return;
        var tab = wb.h('div', 'wb-dk-tab' + (m.selected === id ? ' wb-selected' : '')); tab.dataset.tc = id; tab.draggable = true; tab.setAttribute('role', 'tab');
        R.fillTab(el, tab, tcEl);
        tab.addEventListener('mousedown', function (e) { if (e.button === 0 && !e.target.closest('.wb-dk-tab-close')) el.ctx.emit('select', { mode: m.name, tc: id }); });
        tab.addEventListener('dblclick', function (e) { if (!e.target.closest('.wb-dk-tab-close') && info.maximizable !== false) el.ctx.emit('maximize', { mode: m.name }); });
        tab.addEventListener('contextmenu', function (e) { e.preventDefault(); R.tabMenu(el, m, id, tcEl, e.clientX, e.clientY); });
        tab.addEventListener('dragstart', function (e) { e.dataTransfer.setData('text/wb-tc', id); e.dataTransfer.effectAllowed = 'move'; tab.classList.add('wb-dragging'); el.dragging = { tc: id, mode: m.name }; });
        tab.addEventListener('dragend', function () { tab.classList.remove('wb-dragging'); el.dragging = null; R.clearDrop(el); });
        tabs.appendChild(tab);
        if (m.selected === id) { tcEl.classList.remove('wb-tc-hidden'); body.appendChild(tcEl); }
      });
      if (!m.tcs.length) body.appendChild(wb.h('div', 'wb-mode-empty-text', labels.empty || ''));
      // tools
      var tools = wb.h('div', 'wb-dk-tab-tools');
      var btn = function (icon, title, fn) { var b = wb.h('button', 'wb-dk-tab-tool'); b.type = 'button'; b.innerHTML = icon; b.title = title || ''; b.addEventListener('click', function (e) { e.stopPropagation(); fn(); }); return b; };
      if (kind === 'floating') tools.appendChild(btn(ICON.dock, labels.dock, function () { el.ctx.emit('dock', { mode: m.name }); }));
      else if (kind === 'slide') tools.appendChild(btn(ICON.dock, labels.dock, function () { el.ctx.emit('dock', { mode: m.name }); }));
      else {
        if (kind === 'docked' && info.side !== 'center' && m.tcs.length) tools.appendChild(btn(ICON.min, labels.minimize, function () { el.ctx.emit('minimize', { mode: m.name, side: R.sideFor(el, m, info) }); }));
        if (info.maximizable !== false && m.tcs.length) tools.appendChild(btn(L.maximized === m.name ? ICON.restore : ICON.max, L.maximized === m.name ? labels.restore : labels.maximize, function () { el.ctx.emit('maximize', { mode: m.name }); }));
      }
      strip.appendChild(tools);
      // drop target
      R.dropTarget(el, wrap, m, kind);
      // scroll the tab strip horizontally with the wheel
      tabs.addEventListener('wheel', function (e) { if (e.deltaY && tabs.scrollWidth > tabs.clientWidth) { tabs.scrollLeft += e.deltaY; e.preventDefault(); } }, { passive: false });
      return wrap;
    },
    sideFor: function (el, m, info) {
      var s = info.side; if (s === 'left' || s === 'right' || s === 'top' || s === 'bottom') return s;
      // anonymous/centre modes: by position in the dock
      var r = el.area.getBoundingClientRect(); var w = el.area.querySelector('.wb-mode[data-mode="' + CSS.escape(m.name) + '"]'); if (!w) return 'left';
      var b = w.getBoundingClientRect(); var cx = (b.left + b.right) / 2 - r.left, cy = (b.top + b.bottom) / 2 - r.top;
      if (cy > r.height * 0.66) return 'bottom'; return cx < r.width / 2 ? 'left' : 'right';
    },
    fillTab: function (el, tab, tcEl) {
      var p = (tcEl.__wb && tcEl.__wb.props) || {}; var labels = el.labels;
      tab.innerHTML = '';
      if (p.icon) { var im = wb.h('img', 'wb-dk-tab-icon'); im.src = wb.res(p.icon); im.alt = ''; tab.appendChild(im); }
      if (p.busy) tab.appendChild(wb.h('span', 'wb-dk-tab-busy'));
      var label = wb.h('span', 'wb-dk-tab-label');
      if (p.htmlDisplayName) label.appendChild(safeHtml(p.htmlDisplayName)); else label.textContent = p.displayName || tcEl.dataset.tcId;
      tab.appendChild(label);
      if (p.modified) { var mod = wb.h('span', 'wb-dk-tab-modified', '•'); mod.title = labels.modified || ''; tab.appendChild(mod); }
      tab.title = p.tooltip || p.displayName || '';
      if (p.closable !== false) {
        var c = wb.h('button', 'wb-dk-tab-close'); c.type = 'button'; c.innerHTML = ICON.close; c.title = labels.close || 'Close'; c.tabIndex = -1;
        c.addEventListener('click', function (e) { e.stopPropagation(); el.ctx.emit('close', { tc: tcEl.dataset.tcId }); });
        tab.appendChild(c);
      }
    },
    markActive: function (el) {
      el.area.querySelectorAll('.wb-mode').forEach(function (w) { w.classList.toggle('wb-mode-active', !!(el.active && w.mode && w.mode.tcs.indexOf(el.active) >= 0)); });
    },

    /* ---------- gutters ---------- */
    gutter: function (el, splitEl, node, i) {
      var g = wb.h('div', 'wb-gutter'); var row = node.dir === 'row';
      g.addEventListener('mousedown', function (e) {
        e.preventDefault(); g.classList.add('wb-gutter-active'); document.body.classList.add('wb-resizing-' + (row ? 'col' : 'row'));
        var panes = Array.from(splitEl.children).filter(function (c) { return c.classList.contains('wb-pane'); });
        var a = panes[i - 1], b = panes[i]; var start = row ? e.clientX : e.clientY;
        var sa = row ? a.offsetWidth : a.offsetHeight, sb = row ? b.offsetWidth : b.offsetHeight; var na = sa, nb = sb;
        var move = function (ev) {
          var d = (row ? ev.clientX : ev.clientY) - start; na = Math.max(40, Math.min(sa + sb - 40, sa + d)); nb = sa + sb - na;
          if (a.dataset.star && b.dataset.star) { a.style.flex = '0 0 ' + na + 'px'; }
          else { if (!a.dataset.star) a.style.flex = '0 0 ' + na + 'px'; if (!b.dataset.star) b.style.flex = '0 0 ' + nb + 'px'; }
        };
        var up = function () {
          document.removeEventListener('mousemove', move); document.removeEventListener('mouseup', up);
          g.classList.remove('wb-gutter-active'); document.body.classList.remove('wb-resizing-col', 'wb-resizing-row');
          if (na === sa) return;
          var sizes = node.sizes.slice();
          if (a.dataset.star && b.dataset.star) sizes[i - 1] = Math.round(na);
          else { if (!a.dataset.star) sizes[i - 1] = Math.round(na); if (!b.dataset.star) sizes[i] = Math.round(nb); }
          el.ctx.emit('resize', { path: splitEl.path, sizes: sizes });
        };
        document.addEventListener('mousemove', move); document.addEventListener('mouseup', up);
      });
      return g;
    },

    /* ---------- drag & drop ---------- */
    dropTarget: function (el, wrap, m, kind) {
      var zone = null;
      var compute = function (e) {
        var tabs = wrap.querySelector('.wb-dk-tabstrip');
        if (tabs.contains(e.target)) return { kind: 'tabs' };
        if (kind !== 'docked' && kind !== 'max') return { kind: 'center' };
        var r = wrap.getBoundingClientRect(); var x = (e.clientX - r.left) / r.width, y = (e.clientY - r.top) / r.height; var edge = 0.22;
        if (x < edge) return { kind: 'left' }; if (x > 1 - edge) return { kind: 'right' }; if (y < edge) return { kind: 'top' }; if (y > 1 - edge) return { kind: 'bottom' };
        return { kind: 'center' };
      };
      wrap.addEventListener('dragover', function (e) {
        if (!el.dragging) return; e.preventDefault(); e.dataTransfer.dropEffect = 'move';
        var z = compute(e); if (zone && zone.kind === z.kind) return; zone = z; R.showDrop(el, wrap, z.kind);
      });
      wrap.addEventListener('dragleave', function (e) { if (!wrap.contains(e.relatedTarget)) { zone = null; R.clearDrop(el); } });
      wrap.addEventListener('drop', function (e) {
        e.preventDefault(); var z = compute(e); zone = null; R.clearDrop(el);
        var id = e.dataTransfer.getData('text/wb-tc') || (el.dragging && el.dragging.tc); if (!id) return;
        if (z.kind === 'center' || z.kind === 'tabs') {
          var index = null;
          if (z.kind === 'tabs') { var t = e.target.closest('.wb-dk-tab'); var all = Array.from(wrap.querySelectorAll('.wb-dk-tabs > .wb-dk-tab')); if (t) { var r = t.getBoundingClientRect(); index = all.indexOf(t) + (e.clientX > r.left + r.width / 2 ? 1 : 0); } else index = all.length; }
          if (el.dragging && el.dragging.mode === m.name && index === null) return;
          el.ctx.emit('move', { tc: id, mode: m.name, index: index });
        } else {
          if (m.tcs.length === 1 && m.tcs[0] === id) return;
          el.ctx.emit('move', { tc: id, beside: m.name, side: z.kind });
        }
      });
    },
    showDrop: function (el, wrap, kind) {
      R.clearDrop(el);
      var d = wb.h('div', 'wb-dropzone wb-drop-' + kind); wrap.appendChild(d); el.dropEl = d;
    },
    clearDrop: function (el) { if (el.dropEl) { el.dropEl.remove(); el.dropEl = null; } },

    /* ---------- floating windows ---------- */
    floatingEl: function (el, m) {
      var win = wb.h('div', 'wb-floating'); win.style.left = (m.x || 100) + 'px'; win.style.top = (m.y || 100) + 'px'; win.style.width = (m.w || 600) + 'px'; win.style.height = (m.h || 400) + 'px';
      var title = wb.h('div', 'wb-floating-title'); var mode = R.modeEl(el, m, null, 'floating');
      var sel = m.selected ? el.tcs.get(m.selected) : null; var p = sel && sel.__wb ? sel.__wb.props : {};
      title.appendChild(wb.h('span', 'wb-floating-name', p.displayName || m.name));
      var handle = wb.h('div', 'wb-floating-resize'); win.append(title, mode, handle);
      var report = function () { el.ctx.emit('float', { mode: m.name, x: Math.round(win.offsetLeft), y: Math.round(win.offsetTop), w: Math.round(win.offsetWidth), h: Math.round(win.offsetHeight) }); };
      R.dragMove(title, win, function (dx, dy, s) { win.style.left = Math.max(0, s.left + dx) + 'px'; win.style.top = Math.max(0, s.top + dy) + 'px'; }, report);
      R.dragMove(handle, win, function (dx, dy, s) { win.style.width = Math.max(200, s.width + dx) + 'px'; win.style.height = Math.max(120, s.height + dy) + 'px'; }, report);
      win.addEventListener('mousedown', function () { el.area.querySelectorAll('.wb-floating').forEach(function (w) { w.classList.remove('wb-floating-top'); }); win.classList.add('wb-floating-top'); });
      return win;
    },
    dragMove: function (grip, win, apply, done) {
      grip.addEventListener('mousedown', function (e) {
        if (e.button !== 0 || e.target.closest('button')) return; e.preventDefault();
        var sx = e.clientX, sy = e.clientY, s = { left: win.offsetLeft, top: win.offsetTop, width: win.offsetWidth, height: win.offsetHeight }; var moved = false;
        var move = function (ev) { moved = true; apply(ev.clientX - sx, ev.clientY - sy, s); };
        var up = function () { document.removeEventListener('mousemove', move); document.removeEventListener('mouseup', up); if (moved) done(); };
        document.addEventListener('mousemove', move); document.addEventListener('mouseup', up);
      });
    },

    /* ---------- minimised side bars + slide-in popups ---------- */
    sidebar: function (el, side, modes) {
      var bar = wb.h('div', 'wb-sidebar wb-sidebar-' + side);
      modes.forEach(function (m) {
        var sel = m.selected ? el.tcs.get(m.selected) : null; var p = sel && sel.__wb ? sel.__wb.props : {};
        var b = wb.h('button', 'wb-sidebar-btn'); b.type = 'button';
        if (p.icon) { var im = wb.h('img'); im.src = wb.res(p.icon); im.alt = ''; b.appendChild(im); }
        b.appendChild(wb.h('span', '', p.displayName || (el.modes[m.name] && el.modes[m.name].displayName) || m.name));
        if (m.tcs.length > 1) b.appendChild(wb.h('span', 'wb-sidebar-count', String(m.tcs.length)));
        b.addEventListener('click', function () { R.slideIn(el, side, m, b); });
        b.addEventListener('dblclick', function () { el.ctx.emit('dock', { mode: m.name }); });
        bar.appendChild(b);
      });
      return bar;
    },
    slideIn: function (el, side, m, btn) {
      if (el.slide && el.slide.mode === m.name) { R.closePopups(el); return; }
      R.closePopups(el);
      var pop = wb.h('div', 'wb-slide wb-slide-' + side); pop.mode = m.name;
      var mode = R.modeEl(el, m, null, 'slide'); pop.appendChild(mode); el.area.appendChild(pop); el.slide = pop;
      var sel = m.selected ? el.tcs.get(m.selected) : null; if (sel) { sel.classList.remove('wb-tc-hidden'); }
      var away = function (e) { if (!pop.contains(e.target) && e.target !== btn && !btn.contains(e.target)) R.closePopups(el); };
      setTimeout(function () { document.addEventListener('mousedown', away, true); }, 0); pop.away = away;
    },
    closePopups: function (el) {
      if (el.slide) { var m = el.slide.mode; var L = el.layout; var mins = (L && L.minimised) || {}; var node = mins[m];
        if (node && node.selected) { var tcEl = el.tcs.get(node.selected); if (tcEl) { tcEl.classList.add('wb-tc-hidden'); el.park.appendChild(tcEl); } }
        document.removeEventListener('mousedown', el.slide.away, true); el.slide.remove(); el.slide = null; }
      if (el.menu) { el.menu.remove(); el.menu = null; }
    },

    /* ---------- tab context menu (local; the actions module adds real actions in step 5) ---------- */
    tabMenu: function (el, m, id, tcEl, x, y) {
      R.closePopups(el);
      var L = el.layout, info = el.modes[m.name] || {}, labels = el.labels; var p = tcEl.__wb ? tcEl.__wb.props : {};
      var menu = wb.h('div', 'wb-dk-tabmenu'); menu.setAttribute('role', 'menu');
      var item = function (label, fn, disabled) { var it = wb.h('div', 'wb-dk-tabmenu-item' + (disabled ? ' wb-disabled' : ''), label); it.setAttribute('role', 'menuitem'); if (!disabled) it.addEventListener('click', function () { R.closePopups(el); fn(); }); menu.appendChild(it); };
      item(labels.close, function () { el.ctx.emit('close', { tc: id }); }, p.closable === false);
      item(labels.closeOthers, function () { m.tcs.forEach(function (o) { if (o !== id) { var oe = el.tcs.get(o); if (!oe || !oe.__wb || oe.__wb.props.closable !== false) el.ctx.emit('close', { tc: o }); } }); }, m.tcs.length < 2);
      menu.appendChild(wb.h('div', 'wb-dk-tabmenu-sep'));
      var isFloating = (L.floating || []).some(function (f) { return f.name === m.name; }); var isMin = !!(L.minimised && L.minimised[m.name]);
      if (!isFloating && !isMin) item(L.maximized === m.name ? labels.restore : labels.maximize, function () { el.ctx.emit('maximize', { mode: m.name }); }, info.maximizable === false);
      if (isFloating || isMin) item(labels.dock, function () { el.ctx.emit('dock', { mode: m.name }); });
      else {
        item(labels.float, function () { var r = tcEl.getBoundingClientRect(), a = el.area.getBoundingClientRect(); el.ctx.emit('move', { tc: id, floating: { x: Math.max(0, Math.round(r.left - a.left + 40)), y: Math.max(0, Math.round(r.top - a.top + 40)), w: Math.max(240, Math.round(r.width * 0.8)), h: Math.max(160, Math.round(r.height * 0.8)) } }); }, info.floatable === false);
        if (info.side !== 'center') item(labels.minimize, function () { el.ctx.emit('minimize', { mode: m.name, side: R.sideFor(el, m, info) }); });
      }
      document.body.appendChild(menu); el.menu = menu;
      var mw = menu.offsetWidth, mh = menu.offsetHeight;
      menu.style.left = Math.min(x, window.innerWidth - mw - 4) + 'px'; menu.style.top = Math.min(y, window.innerHeight - mh - 4) + 'px';
      setTimeout(function () { var away = function (e) { if (!menu.contains(e.target)) { document.removeEventListener('mousedown', away, true); R.closePopups(el); } }; document.addEventListener('mousedown', away, true); }, 0);
    }
  };
})();
