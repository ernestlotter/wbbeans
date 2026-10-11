/* wb.ListBox — a list of items with single/multi selection (keys), keyboard navigation and activation. */
wb.register('wb.ListBox', {
  create: function (ctx) {
    var el = wb.h('div', 'wb-listbox'); el.setAttribute('role', 'listbox'); el.tabIndex = 0; el.ctx = ctx; el.sel = []; el.anchor = null; el.props = {};
    var self = this;
    el.addEventListener('keydown', function (e) {
      var rows = el.rows || []; if (!rows.length) return;
      var cur = el.focused == null ? -1 : el.focused; var next = cur;
      if (e.key === 'ArrowDown') next = Math.min(rows.length - 1, cur + 1); else if (e.key === 'ArrowUp') next = Math.max(0, cur - 1);
      else if (e.key === 'Home') next = 0; else if (e.key === 'End') next = rows.length - 1;
      else if (e.key === 'Enter') { if (cur >= 0) ctx.emit('activate', { key: rows[cur].key }); return; }
      else if (e.key === ' ') { if (cur >= 0) { e.preventDefault(); self.toggle(el, rows[cur].key, true); } return; }
      else if (e.key === 'a' && (e.ctrlKey || e.metaKey) && el.props.multi) { e.preventDefault(); el.sel = rows.map(function (r) { return r.key; }); self.paint(el); self.report(el); return; }
      else return;
      e.preventDefault(); if (next === cur) return;
      el.focused = next; var row = rows[next];
      if (e.shiftKey && el.props.multi && el.anchor != null) self.range(el, el.anchor, row.key); else { el.sel = [row.key]; el.anchor = row.key; }
      self.paint(el); row.el.scrollIntoView({ block: 'nearest' }); self.report(el);
    });
    return el;
  },
  render: function (el, props) {
    el.innerHTML = ''; el.rows = []; var self = this; el.focused = null;
    var items = props.items || [];
    if (!items.length) { if (props.emptyText) el.appendChild(wb.h('div', 'wb-list-empty', props.emptyText)); return; }
    items.forEach(function (it, i) {
      var row = wb.h('div', 'wb-list-row'); row.setAttribute('role', 'option'); row.dataset.key = it.v; if (it.e === false) row.classList.add('wb-disabled');
      if (props.checkboxes) { var cb = wb.h('input', 'wb-list-check'); cb.type = 'checkbox'; cb.tabIndex = -1; row.appendChild(cb); row.cb = cb; }
      if (it.i) { var img = wb.h('img', 'wb-icon'); img.src = wb.res(it.i); img.alt = ''; row.appendChild(img); }
      var txt = wb.h('div', 'wb-list-text'); txt.appendChild(wb.h('div', 'wb-text', it.l)); if (it.d) txt.appendChild(wb.h('div', 'wb-list-desc', it.d)); row.appendChild(txt);
      row.addEventListener('click', function (e) {
        if (it.e === false) return; el.focused = i; el.focus();
        if (props.checkboxes) { self.toggle(el, it.v, true); return; }
        if (props.multi && (e.ctrlKey || e.metaKey)) self.toggle(el, it.v, false);
        else if (props.multi && e.shiftKey && el.anchor != null) { self.range(el, el.anchor, it.v); self.paint(el); self.report(el); }
        else { el.sel = [it.v]; el.anchor = it.v; self.paint(el); self.report(el); }
      });
      row.addEventListener('dblclick', function () { if (it.e !== false) el.ctx.emit('activate', { key: it.v }); });
      el.appendChild(row); el.rows.push({ key: it.v, el: row });
    });
    this.paint(el);
  },
  toggle: function (el, key, keepAnchor) {
    var i = el.sel.indexOf(key); if (i >= 0) el.sel.splice(i, 1); else { if (!el.props.multi) el.sel = []; el.sel.push(key); }
    if (!keepAnchor || el.anchor == null) el.anchor = key; this.paint(el); this.report(el);
  },
  range: function (el, a, b) {
    var keys = el.rows.map(function (r) { return r.key; }); var i = keys.indexOf(a), j = keys.indexOf(b); if (i < 0 || j < 0) return;
    el.sel = keys.slice(Math.min(i, j), Math.max(i, j) + 1);
  },
  paint: function (el) {
    (el.rows || []).forEach(function (r, i) { var on = el.sel.indexOf(r.key) >= 0; r.el.classList.toggle('wb-selected', on); r.el.setAttribute('aria-selected', on ? 'true' : 'false'); if (r.el.cb) r.el.cb.checked = on; r.el.classList.toggle('wb-focused', el.focused === i); });
  },
  report: function (el) { el.ctx.emit('select', { keys: el.sel.slice() }); },
  update: function (el, props, changed) {
    el.props = props;
    if (changed.has('selected')) el.sel = (props.selected || []).slice();
    if (changed.has('items') || changed.has('checkboxes') || changed.has('emptyText')) this.render(el, props); else if (changed.has('selected')) this.paint(el);
    if (changed.has('multi')) el.setAttribute('aria-multiselectable', props.multi ? 'true' : 'false');
    if (changed.has('visibleRows')) el.style.height = props.visibleRows ? (props.visibleRows * 26 + 2) + 'px' : '';
    if (changed.has('enabled')) el.classList.toggle('wb-disabled', props.enabled === false);
  },
  call: function (el, m, args) { if (m === 'scrollTo') { var r = (el.rows || []).filter(function (x) { return x.key === args[0]; })[0]; if (r) r.el.scrollIntoView({ block: 'nearest' }); } },
  insertChild: function () {}, removeChild: function () {}
});
