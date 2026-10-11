/*
 * wb.TreeView — the explorer tree. The server sends incremental "nodes" payloads (add / remove / update / reorder /
 * children) keyed by view-local node ids; the client keeps the model, expansion and selection and emits semantic
 * events: expand, collapse, select, activate, contextMenu, rename, delete, editCell, drop. The context menu itself is
 * a server-side wb.Popup (actions module).
 * wb.nodeView is the shared engine that OutlineView and ListView build on.
 */
(function () {
  var ALLOWED = { B: 1, I: 1, EM: 1, STRONG: 1, U: 1, S: 1, SPAN: 1, FONT: 1, SMALL: 1 };
  function safeHtml(html) {
    var t = document.createElement('template'); t.innerHTML = html;
    (function walk(n) { Array.from(n.children).forEach(function (c) {
      if (!ALLOWED[c.tagName]) { c.replaceWith(document.createTextNode(c.textContent)); return; }
      Array.from(c.attributes).forEach(function (a) { if (a.name !== 'color' && a.name !== 'style') c.removeAttribute(a.name); });
      if (c.getAttribute('style') && !/^\s*color\s*:[^;]+;?\s*$/i.test(c.getAttribute('style'))) c.removeAttribute('style');
      walk(c); }); })(t.content);
    return t.content;
  }
  var ICON_TOGGLE = '<svg viewBox="0 0 10 10" width="10" height="10"><path d="M3 1.5l4 3.5-4 3.5" stroke="currentColor" stroke-width="1.6" fill="none"/></svg>';

  var engine = {
    /* ---------- model ---------- */
    init: function (el, ctx, opts) {
      el.ctx = ctx; el.opts = opts || {}; el.rows = new Map(); el.rootIds = []; el.expanded = new Set(); el.selected = []; el.anchor = null; el.loading = new Set();
      el.list = wb.h('div', 'wb-nv-list'); el.list.tabIndex = 0; el.list.setAttribute('role', 'tree');
      el.appendChild(el.list);
      el.list.addEventListener('keydown', function (e) { engine.key(el, e); });
      el.list.addEventListener('click', function (e) { var r = e.target.closest('.wb-nv-row'); if (!r) { engine.select(el, [], null); return; } engine.rowClick(el, r, e); });
      el.list.addEventListener('dblclick', function (e) { var r = e.target.closest('.wb-nv-row'); if (r && !e.target.closest('.wb-nv-toggle') && !e.target.closest('input,select')) engine.activate(el, +r.dataset.n); });
      el.list.addEventListener('contextmenu', function (e) {
        var r = e.target.closest('.wb-nv-row'); e.preventDefault();
        if (r) { var n = +r.dataset.n; if (el.selected.indexOf(n) < 0) engine.select(el, [n], n); }
        if (el.selected.length) ctx.emit('contextMenu', { nodes: el.selected.slice(), x: e.clientX, y: e.clientY });
      });
      el.list.addEventListener('dragover', function (e) { if (el.props.dropTarget && el.dragging) { e.preventDefault(); var r = e.target.closest('.wb-nv-row'); el.list.querySelectorAll('.wb-nv-drop').forEach(function (x) { if (x !== r) x.classList.remove('wb-nv-drop'); }); if (r) r.classList.add('wb-nv-drop'); } });
      el.list.addEventListener('dragleave', function (e) { var r = e.target.closest('.wb-nv-row'); if (r && !r.contains(e.relatedTarget)) r.classList.remove('wb-nv-drop'); });
      el.list.addEventListener('drop', function (e) {
        e.preventDefault(); el.list.querySelectorAll('.wb-nv-drop').forEach(function (x) { x.classList.remove('wb-nv-drop'); });
        var r = e.target.closest('.wb-nv-row'); if (!r) return;
        var nodes = el.dragging ? el.dragging.slice() : []; var text = e.dataTransfer.getData('text/plain');
        if (nodes.indexOf(+r.dataset.n) >= 0) return;
        ctx.emit('drop', { target: +r.dataset.n, index: -1, action: e.ctrlKey ? 'copy' : 'move', nodes: nodes, text: nodes.length ? null : (text || null) });
      });
      // quick search: typing while the list is focused jumps to the next matching visible row
      el.quick = ''; el.quickTimer = null;
    },
    reset: function (el) { el.rows.clear(); el.rootIds = []; el.expanded.clear(); el.selected = []; el.anchor = null; el.loading.clear(); engine.render(el); },
    apply: function (el, p) {
      (p.remove || []).forEach(function (id) { engine.removeRow(el, id); });
      (p.add || []).forEach(function (r) {
        var row = el.rows.get(r.n) || { n: r.n, children: [] };
        Object.assign(row, r); row.children = row.children || [];
        el.rows.set(r.n, row);
        if (r.p == null) { if (el.rootIds.indexOf(r.n) < 0) el.rootIds.splice(r.i == null ? el.rootIds.length : r.i, 0, r.n); }
        else { var parent = el.rows.get(r.p); if (parent) { if (parent.children.indexOf(r.n) < 0) parent.children.splice(r.i == null ? parent.children.length : Math.min(r.i, parent.children.length), 0, r.n); parent.leaf = false; } }
      });
      (p.update || []).forEach(function (u) { var row = el.rows.get(u.n); if (row) { Object.assign(row, u); } });
      (p.reorder || []).forEach(function (o) { var parent = el.rows.get(o.p); if (parent) parent.children = o.order.filter(function (id) { return el.rows.has(id); }); });
      (p.children || []).forEach(function (c) { var parent = el.rows.get(c.p); if (parent) { parent.loaded = true; el.loading.delete(c.p); } });
      engine.render(el);
    },
    removeRow: function (el, id) {
      var row = el.rows.get(id); if (!row) return;
      (row.children || []).slice().forEach(function (c) { engine.removeRow(el, c); });
      el.rows.delete(id); el.expanded.delete(id); el.loading.delete(id);
      var i = el.rootIds.indexOf(id); if (i >= 0) el.rootIds.splice(i, 1);
      if (row.p != null) { var parent = el.rows.get(row.p); if (parent) { var j = parent.children.indexOf(id); if (j >= 0) parent.children.splice(j, 1); } }
      var k = el.selected.indexOf(id); if (k >= 0) el.selected.splice(k, 1);
    },

    /* ---------- rendering (flat list of visible rows) ---------- */
    visible: function (el) {
      var out = [];
      var walk = function (id, depth) {
        var row = el.rows.get(id); if (!row) return;
        out.push({ row: row, depth: depth });
        if (el.expanded.has(id)) engine.childrenOf(el, row).forEach(function (c) { walk(c, depth + 1); });
      };
      var roots = el.rootIds;
      if (el.opts.hideRoot && roots.length === 1) { var root = el.rows.get(roots[0]); if (root) { el.expanded.add(root.n); engine.childrenOf(el, root).forEach(function (c) { walk(c, 0); }); return out; } }
      roots.forEach(function (r) { walk(r, 0); });
      return out;
    },
    childrenOf: function (el, row) { return el.opts.sortChildren ? el.opts.sortChildren(el, row.children) : row.children; },
    render: function (el) {
      var vis = engine.visible(el); var focusId = el.focusId;
      var frag = document.createDocumentFragment();
      vis.forEach(function (v) { frag.appendChild(engine.rowEl(el, v.row, v.depth)); });
      el.list.innerHTML = ''; el.list.appendChild(frag);
      if (!vis.length && el.opts.emptyText) el.list.appendChild(wb.h('div', 'wb-nv-empty', el.opts.emptyText(el)));
      engine.applySelection(el);
      if (focusId != null) { var f = el.list.querySelector('.wb-nv-row[data-n="' + focusId + '"]'); if (f) f.classList.add('wb-nv-focus'); }
      if (el.opts.afterRender) el.opts.afterRender(el);
    },
    rowEl: function (el, row, depth) {
      var r = wb.h('div', 'wb-nv-row'); r.dataset.n = row.n; r.setAttribute('role', 'treeitem'); r.style.setProperty('--wb-nv-depth', depth);
      if (row.tip) r.title = row.tip;
      var main = wb.h('div', 'wb-nv-main');
      var toggle = wb.h('span', 'wb-nv-toggle'); if (!row.leaf) { toggle.innerHTML = ICON_TOGGLE; toggle.classList.add('wb-nv-has-children'); if (el.expanded.has(row.n)) toggle.classList.add('wb-nv-open'); if (el.loading.has(row.n)) toggle.classList.add('wb-nv-loading'); }
      toggle.addEventListener('click', function (e) { e.stopPropagation(); if (!row.leaf) engine.toggle(el, row.n); });
      main.appendChild(toggle);
      var iconRes = el.expanded.has(row.n) && row.oicon ? row.oicon : row.icon;
      if (iconRes) { var im = wb.h('img', 'wb-nv-icon'); im.src = wb.res(iconRes); im.alt = ''; im.draggable = false; main.appendChild(im); }
      var label = wb.h('span', 'wb-nv-label'); if (row.html) label.appendChild(safeHtml(row.html)); else label.textContent = row.dn == null ? row.name : row.dn;
      main.appendChild(label); r.appendChild(main);
      if (el.opts.cells) el.opts.cells(el, row, r);
      if (el.props.dragSource !== false && (row.can && (row.can.cut || row.can.copy))) {
        r.draggable = true;
        r.addEventListener('dragstart', function (e) { if (el.selected.indexOf(row.n) < 0) engine.select(el, [row.n], row.n); el.dragging = el.selected.slice(); e.dataTransfer.effectAllowed = 'copyMove'; e.dataTransfer.setData('text/plain', row.dn || row.name || ''); r.classList.add('wb-nv-dragging'); });
        r.addEventListener('dragend', function () { el.dragging = null; r.classList.remove('wb-nv-dragging'); });
      }
      return r;
    },
    applySelection: function (el) {
      el.list.querySelectorAll('.wb-nv-row').forEach(function (r) { r.classList.toggle('wb-nv-selected', el.selected.indexOf(+r.dataset.n) >= 0); r.setAttribute('aria-selected', el.selected.indexOf(+r.dataset.n) >= 0); });
    },

    /* ---------- interaction ---------- */
    toggle: function (el, id) { if (el.expanded.has(id)) engine.collapse(el, id); else engine.expand(el, id); },
    expand: function (el, id) {
      var row = el.rows.get(id); if (!row || row.leaf) return;
      el.expanded.add(id);
      if (!row.loaded && !el.loading.has(id)) { el.loading.add(id); el.ctx.emit('expand', { n: id }); }
      engine.render(el);
    },
    collapse: function (el, id) { if (el.expanded.delete(id)) { el.ctx.emit('collapse', { n: id }); engine.render(el); } },
    select: function (el, ids, anchor, silent) {
      if (el.props.selectionMode === 'SINGLE' && ids.length > 1) ids = ids.slice(-1);
      var same = ids.length === el.selected.length && ids.every(function (x, i) { return x === el.selected[i]; });
      el.selected = ids.slice(); if (anchor != null) el.anchor = anchor; el.focusId = ids.length ? ids[ids.length - 1] : el.focusId;
      engine.applySelection(el);
      el.list.querySelectorAll('.wb-nv-focus').forEach(function (x) { x.classList.remove('wb-nv-focus'); });
      if (el.focusId != null) { var f = el.list.querySelector('.wb-nv-row[data-n="' + el.focusId + '"]'); if (f) { f.classList.add('wb-nv-focus'); if (f.scrollIntoViewIfNeeded) f.scrollIntoViewIfNeeded(false); else f.scrollIntoView({ block: 'nearest' }); } }
      if (!same && !silent) el.ctx.emit('select', { nodes: el.selected.slice() });
    },
    rowClick: function (el, r, e) {
      if (e.target.closest('.wb-nv-toggle') || e.target.closest('input,select,button')) return;
      var id = +r.dataset.n; var vis = engine.visible(el).map(function (v) { return v.row.n; });
      if (e.shiftKey && el.anchor != null && el.props.selectionMode !== 'SINGLE') {
        var a = vis.indexOf(el.anchor), b = vis.indexOf(id); if (a < 0) a = b;
        engine.select(el, vis.slice(Math.min(a, b), Math.max(a, b) + 1), el.anchor);
      } else if ((e.ctrlKey || e.metaKey) && el.props.selectionMode !== 'SINGLE') {
        var s = el.selected.slice(); var i = s.indexOf(id); if (i >= 0) s.splice(i, 1); else s.push(id); engine.select(el, s, id);
      } else engine.select(el, [id], id);
      el.list.focus({ preventScroll: true });
    },
    activate: function (el, id) { var row = el.rows.get(id); if (!row) return; if (el.opts.activate) { el.opts.activate(el, row); return; } el.ctx.emit('activate', { n: id }); },
    key: function (el, e) {
      if (el.editing) return;
      var vis = engine.visible(el).map(function (v) { return v.row.n; }); if (!vis.length) return;
      var cur = el.focusId != null && vis.indexOf(el.focusId) >= 0 ? el.focusId : (el.selected[0] != null ? el.selected[0] : vis[0]);
      var i = vis.indexOf(cur); var row = el.rows.get(cur);
      var go = function (j) { j = Math.max(0, Math.min(vis.length - 1, j)); var id = vis[j];
        if (e.shiftKey && el.props.selectionMode !== 'SINGLE') { var a = vis.indexOf(el.anchor != null ? el.anchor : cur); engine.select(el, vis.slice(Math.min(a, j), Math.max(a, j) + 1), el.anchor != null ? el.anchor : cur); }
        else engine.select(el, [id], id); };
      switch (e.key) {
        case 'ArrowDown': e.preventDefault(); go(i + 1); break;
        case 'ArrowUp': e.preventDefault(); go(i - 1); break;
        case 'Home': e.preventDefault(); go(0); break;
        case 'End': e.preventDefault(); go(vis.length - 1); break;
        case 'PageDown': e.preventDefault(); go(i + 20); break;
        case 'PageUp': e.preventDefault(); go(i - 20); break;
        case 'ArrowRight': e.preventDefault(); if (row && !row.leaf) { if (!el.expanded.has(cur)) engine.expand(el, cur); else go(i + 1); } break;
        case 'ArrowLeft': e.preventDefault(); if (row && el.expanded.has(cur)) engine.collapse(el, cur); else if (row && row.p != null && vis.indexOf(row.p) >= 0) go(vis.indexOf(row.p)); break;
        case 'Enter': e.preventDefault(); if (row) engine.activate(el, cur); break;
        case ' ': e.preventDefault(); if (row && !row.leaf) engine.toggle(el, cur); break;
        case 'F2': e.preventDefault(); if (row && row.can && row.can.rename) engine.startRename(el, cur); break;
        case 'Delete': e.preventDefault(); engine.deleteSelected(el); break;
        default:
          if (e.key.length === 1 && !e.ctrlKey && !e.metaKey && !e.altKey && el.props.quickSearch !== false) { engine.quickSearch(el, e.key, vis, i); e.preventDefault(); }
      }
    },
    quickSearch: function (el, ch, vis, from) {
      clearTimeout(el.quickTimer); el.quick += ch.toLowerCase(); el.quickTimer = setTimeout(function () { el.quick = ''; }, 900);
      var q = el.quick; var start = el.quick.length === 1 ? from + 1 : from;
      for (var k = 0; k < vis.length; k++) { var id = vis[(start + k) % vis.length]; var r = el.rows.get(id); var t = (r.dn || r.name || '').toLowerCase(); if (t.indexOf(q) === 0) { engine.select(el, [id], id); return; } }
    },
    deleteSelected: function (el) {
      var ids = el.selected.filter(function (id) { var r = el.rows.get(id); return r && r.can && r.can.destroy; }); if (!ids.length) return;
      el.ctx.emit('delete', { nodes: ids }); // the server asks for confirmation (DialogDisplayer)
    },
    startRename: function (el, id) {
      var r = el.list.querySelector('.wb-nv-row[data-n="' + id + '"]'); var row = el.rows.get(id); if (!r || !row) return;
      var label = r.querySelector('.wb-nv-label'); var input = wb.h('input', 'wb-nv-rename'); input.value = row.name || row.dn || ''; input.type = 'text';
      label.replaceWith(input); input.focus(); input.select(); el.editing = true;
      var done = function (commit) { if (!el.editing) return; el.editing = false; var v = input.value; engine.render(el); if (commit && v.trim() && v !== row.name) el.ctx.emit('rename', { n: id, name: v.trim() }); el.list.focus({ preventScroll: true }); };
      input.addEventListener('keydown', function (e) { e.stopPropagation(); if (e.key === 'Enter') done(true); else if (e.key === 'Escape') done(false); });
      input.addEventListener('blur', function () { done(true); });
      input.addEventListener('click', function (e) { e.stopPropagation(); });
    },

    /* ---------- server calls ---------- */
    call: function (el, m, args) {
      switch (m) {
        case 'nodes': engine.apply(el, args[0] || {}); break;
        case 'reset': engine.reset(el); break;
        case 'select': engine.select(el, (args[0] || []).filter(function (id) { return el.rows.has(id); }), null, true); break;
        case 'expand': engine.expand(el, args[0]); break;
        case 'collapse': engine.collapse(el, args[0]); break;
        case 'startRename': engine.startRename(el, args[0]); break;
        case 'editError': if (el.opts.editError) el.opts.editError(el, args[0]); break;
        case 'context': if (el.opts.context) el.opts.context(el, args[0]); break;
      }
    },
    update: function (el, props, changed) { el.props = props; if (changed.has('selectionMode') || changed.has('rootVisible')) { el.opts.hideRoot = props.rootVisible === false; engine.render(el); } }
  };
  wb.nodeView = engine;

  wb.register('wb.TreeView', {
    create: function (ctx) { var el = wb.h('div', 'wb-nv wb-treeview'); el.props = {}; engine.init(el, ctx, { hideRoot: false }); return el; },
    update: function (el, props, changed) { engine.update(el, props, changed); },
    call: function (el, m, args) { engine.call(el, m, args); },
    insertChild: function () {}, removeChild: function () {}
  });
})();
