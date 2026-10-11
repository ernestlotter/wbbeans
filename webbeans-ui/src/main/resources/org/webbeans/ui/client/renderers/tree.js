/* wb.Tree — a lazily loaded tree over keyed nodes: roots as a property, children pushed by `children(key, nodes)`. */
wb.register('wb.Tree', {
  create: function (ctx) {
    var el = wb.h('div', 'wb-tree'); el.setAttribute('role', 'tree'); el.tabIndex = 0; el.ctx = ctx; el.props = {};
    el.kids = {}; el.expanded = new Set(); el.sel = []; el.anchor = null; el.focusKey = null;
    var self = this;
    el.addEventListener('keydown', function (e) {
      var vis = self.visible(el); if (!vis.length) return;
      var idx = vis.findIndex(function (v) { return v.k === el.focusKey; }); var cur = idx < 0 ? 0 : idx; var n = vis[cur];
      if (e.key === 'ArrowDown') cur = Math.min(vis.length - 1, cur + 1);
      else if (e.key === 'ArrowUp') cur = Math.max(0, cur - 1);
      else if (e.key === 'ArrowRight') { if (!n.leaf && !el.expanded.has(n.k)) { self.setExpanded(el, n.k, true); return; } cur = Math.min(vis.length - 1, cur + 1); }
      else if (e.key === 'ArrowLeft') { if (el.expanded.has(n.k)) { self.setExpanded(el, n.k, false); return; } if (n.parent != null) cur = vis.findIndex(function (v) { return v.k === n.parent; }); }
      else if (e.key === 'Enter') { ctx.emit('activate', { key: n.k }); return; }
      else if (e.key === ' ') { e.preventDefault(); self.select(el, n.k, e); return; }
      else return;
      e.preventDefault(); el.focusKey = vis[cur].k;
      if (!e.shiftKey || !el.props.multi) { el.sel = [el.focusKey]; el.anchor = el.focusKey; } else self.range(el, el.anchor, el.focusKey);
      self.paint(el); self.report(el); var row = el.querySelector('[data-k="' + CSS.escape(el.focusKey) + '"]'); if (row) row.scrollIntoView({ block: 'nearest' });
    });
    return el;
  },
  /* the visible nodes in order, with parent keys */
  visible: function (el) {
    var out = []; var self = this;
    var walk = function (nodes, parent, depth) { (nodes || []).forEach(function (n) { out.push({ k: n.k, leaf: n.leaf, parent: parent, depth: depth }); if (el.expanded.has(n.k)) walk(el.kids[n.k], n.k, depth + 1); }); };
    walk(el.props.roots, null, 0); return out;
  },
  render: function (el) {
    el.innerHTML = ''; var self = this; var roots = el.props.roots || [];
    if (!roots.length) { if (el.props.emptyText) el.appendChild(wb.h('div', 'wb-list-empty', el.props.emptyText)); return; }
    var build = function (nodes, depth, container) {
      (nodes || []).forEach(function (n) {
        var row = wb.h('div', 'wb-tree-row'); row.dataset.k = n.k; row.setAttribute('role', 'treeitem'); row.style.paddingLeft = (8 + depth * 18) + 'px';
        var tog = wb.h('span', 'wb-tree-toggle' + (n.leaf ? ' wb-leaf' : ''), n.leaf ? '' : (el.expanded.has(n.k) ? '▾' : '▸'));
        tog.addEventListener('click', function (e) { e.stopPropagation(); if (!n.leaf) self.setExpanded(el, n.k, !el.expanded.has(n.k)); });
        row.appendChild(tog);
        if (n.i) { var img = wb.h('img', 'wb-icon'); img.src = wb.res(n.i); img.alt = ''; row.appendChild(img); }
        row.appendChild(wb.h('span', 'wb-text', n.l));
        row.addEventListener('click', function (e) { el.focusKey = n.k; el.focus(); self.select(el, n.k, e); });
        row.addEventListener('dblclick', function () { if (!n.leaf) self.setExpanded(el, n.k, !el.expanded.has(n.k)); el.ctx.emit('activate', { key: n.k }); });
        container.appendChild(row);
        if (!n.leaf && el.expanded.has(n.k)) {
          var group = wb.h('div', 'wb-tree-children'); group.setAttribute('role', 'group');
          if (el.kids[n.k]) build(el.kids[n.k], depth + 1, group); else group.appendChild(wb.h('div', 'wb-tree-loading', '…'));
          container.appendChild(group);
        }
      });
    };
    build(roots, 0, el); this.paint(el);
  },
  setExpanded: function (el, k, on) {
    if (on) el.expanded.add(k); else el.expanded.delete(k);
    el.ctx.emit('expand', { key: k, expanded: on }); this.render(el);
  },
  select: function (el, k, e) {
    if (el.props.multi && (e.ctrlKey || e.metaKey)) { var i = el.sel.indexOf(k); if (i >= 0) el.sel.splice(i, 1); else el.sel.push(k); el.anchor = k; }
    else if (el.props.multi && e.shiftKey && el.anchor != null) this.range(el, el.anchor, k);
    else { el.sel = [k]; el.anchor = k; }
    this.paint(el); this.report(el);
  },
  range: function (el, a, b) { var keys = this.visible(el).map(function (v) { return v.k; }); var i = keys.indexOf(a), j = keys.indexOf(b); if (i < 0 || j < 0) return; el.sel = keys.slice(Math.min(i, j), Math.max(i, j) + 1); },
  paint: function (el) { el.querySelectorAll('.wb-tree-row').forEach(function (r) { var on = el.sel.indexOf(r.dataset.k) >= 0; r.classList.toggle('wb-selected', on); r.setAttribute('aria-selected', on ? 'true' : 'false'); r.classList.toggle('wb-focused', r.dataset.k === el.focusKey); }); },
  report: function (el) { el.ctx.emit('select', { keys: el.sel.slice() }); },
  update: function (el, props, changed) {
    el.props = props;
    if (changed.has('expanded')) el.expanded = new Set(props.expanded || []);
    if (changed.has('selected')) el.sel = (props.selected || []).slice();
    if (changed.has('roots')) el.kids = {};
    if (changed.has('roots') || changed.has('expanded') || changed.has('emptyText')) this.render(el); else if (changed.has('selected')) this.paint(el);
    if (changed.has('multi')) el.setAttribute('aria-multiselectable', props.multi ? 'true' : 'false');
  },
  call: function (el, m, args) { if (m === 'children') { el.kids[args[0]] = args[1] || []; this.render(el); } },
  insertChild: function () {}, removeChild: function () {}
});
