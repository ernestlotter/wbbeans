/* wb.PropertySheet — property sets as collapsible groups with in-place editors (text, number, boolean, choice). */
wb.register('wb.PropertySheet', {
  create: function (ctx) { var el = wb.h('div', 'wb-ps'); el.ctx = ctx; el.collapsed = new Set(); el.sheet = null; el.props = {}; return el; },
  update: function (el, props, changed) { el.props = props; if (changed.has('labels')) this.render(el); },
  call: function (el, m, args) {
    if (m === 'sheet') { el.sheet = args[0]; this.render(el); }
    else if (m === 'editError') { var err = args[0]; var row = el.querySelector('.wb-ps-row[data-prop="' + CSS.escape(err.prop) + '"]'); if (row) { row.classList.add('wb-ps-error'); var e = row.querySelector('.wb-ps-err'); if (e) e.textContent = err.message; } }
  },
  render: function (el) {
    var self = this; var s = el.sheet; var labels = el.props.labels || {}; el.innerHTML = '';
    if (!s || !s.count) { el.appendChild(wb.h('div', 'wb-ps-empty', labels.noSelection || '')); return; }
    var head = wb.h('div', 'wb-ps-title'); if (s.icon) { var im = wb.h('img'); im.src = wb.res(s.icon); im.alt = ''; head.appendChild(im); } head.appendChild(wb.h('span', '', s.title || (s.count + ' ×'))); el.appendChild(head);
    if (!s.sets.length) { el.appendChild(wb.h('div', 'wb-ps-empty', labels.noProperties || '')); return; }
    s.sets.forEach(function (set) {
      var g = wb.h('div', 'wb-ps-set' + (el.collapsed.has(set.name) ? ' wb-ps-collapsed' : '')); var h = wb.h('div', 'wb-ps-set-title', set.label);
      h.addEventListener('click', function () { if (el.collapsed.has(set.name)) el.collapsed.delete(set.name); else el.collapsed.add(set.name); self.render(el); });
      g.appendChild(h); var body = wb.h('div', 'wb-ps-rows');
      set.props.forEach(function (p) { body.appendChild(self.row(el, p)); });
      g.appendChild(body); el.appendChild(g);
    });
  },
  row: function (el, p) {
    var r = wb.h('div', 'wb-ps-row'); r.dataset.prop = p.name; var lab = wb.h('div', 'wb-ps-label', p.label); if (p.desc) lab.title = p.desc; r.appendChild(lab);
    var val = wb.h('div', 'wb-ps-value'); var commit = function (text) { el.ctx.emit('edit', { prop: p.name, text: text }); };
    var deco = function (target) { var d = p.cell; if (!d) return false; if (d.i) { var img = wb.h('img', 'wb-icon'); img.src = wb.res(d.i); img.alt = ''; target.appendChild(img); }
      if (d.h) { var sp = wb.h('span', 'wb-text wb-html'); sp.innerHTML = d.h; target.appendChild(sp); } else target.appendChild(wb.h('span', 'wb-text', d.t != null ? d.t : p.text));
      if (d.s) d.s.split(' ').forEach(function (k) { if (k) target.classList.add(k); }); if (d.tt) target.title = d.tt; return true; };
    if (!p.canWrite) { if (p.mixed || !deco(val)) val.textContent = p.mixed ? '…' : p.text; val.classList.add('wb-ps-readonly'); }
    else if (p.kind === 'BOOLEAN') { var cb = wb.h('input'); cb.type = 'checkbox'; cb.checked = p.text === 'true'; cb.indeterminate = !!p.mixed; cb.addEventListener('change', function () { commit(cb.checked ? 'true' : 'false'); }); val.appendChild(cb); }
    else if (p.kind === 'CHOICE' && p.choices) { var sel = wb.h('select', 'wb-input'); if (p.mixed) { var o0 = wb.h('option', '', '…'); o0.value = ''; sel.appendChild(o0); } p.choices.forEach(function (c) { var o = wb.h('option', '', c.label); o.value = c.value; if (!p.mixed && c.value === p.text) o.selected = true; sel.appendChild(o); }); sel.addEventListener('change', function () { commit(sel.value); });
      var cur = p.choices.filter(function (c) { return c.value === p.text; })[0]; if (cur && cur.icon && !p.mixed) { var ci = wb.h('img', 'wb-icon wb-ps-choice-icon'); ci.src = wb.res(cur.icon); ci.alt = ''; val.appendChild(ci); val.classList.add('wb-ps-with-icon'); }
      val.appendChild(sel); }
    else { var inp = wb.h('input', 'wb-input'); inp.type = 'text'; inp.value = p.mixed ? '' : p.text; if (p.mixed) inp.placeholder = '…'; if (p.kind === 'NUMBER') inp.inputMode = 'decimal'; if (p.config && p.config.placeholder) inp.placeholder = p.config.placeholder;
      var orig = inp.value; inp.addEventListener('keydown', function (e) { if (e.key === 'Enter') { inp.blur(); } else if (e.key === 'Escape') { inp.value = orig; inp.blur(); } });
      inp.addEventListener('blur', function () { if (inp.value !== orig) { orig = inp.value; commit(inp.value); } }); val.appendChild(inp); }
    r.appendChild(val); r.appendChild(wb.h('div', 'wb-ps-err'));
    return r;
  },
  insertChild: function () {}, removeChild: function () {}
});
