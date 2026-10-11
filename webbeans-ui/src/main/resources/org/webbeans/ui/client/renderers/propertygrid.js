/* wb.PropertyGrid — name/value rows grouped by category with typed in-place editors. `properties` describes the
 * rows, `values` (id → {t,i,h,s,tt,v,k}) their current rendering; edits go to the server as `edit`. */
wb.register('wb.PropertyGrid', {
  create: function (ctx) {
    var el = wb.h('div', 'wb-pg'); el.ctx = ctx; el.props = {}; el.collapsed = new Set(); el.errors = {};
    el.rows = wb.h('div', 'wb-pg-rows'); el.desc = wb.h('div', 'wb-pg-desc wb-hidden'); el.append(el.rows, el.desc);
    return el;
  },
  update: function (el, props, changed) {
    el.props = props;
    if (changed.has('showDescription')) el.desc.classList.toggle('wb-hidden', !props.showDescription);
    if (changed.has('properties') || changed.has('showCategories') || changed.has('emptyText')) this.render(el);
    else if (changed.has('values')) this.refresh(el);
  },
  call: function (el, m, args) {
    if (m === 'editError') { el.errors[args[0]] = args[1] || ''; var r = el.rows.querySelector('.wb-pg-row[data-id="' + CSS.escape(args[0]) + '"]'); if (r) { r.classList.add('wb-pg-error'); r.title = args[1] || ''; } var self = this; setTimeout(function () { delete el.errors[args[0]]; if (r) { r.classList.remove('wb-pg-error'); r.removeAttribute('title'); } }, 4000); }
  },
  render: function (el) {
    var self = this; var list = el.props.properties || []; el.rows.innerHTML = '';
    if (!list.length) { el.rows.appendChild(wb.h('div', 'wb-pg-empty', el.props.emptyText || '')); return; }
    var groups = []; var byCat = {};
    list.forEach(function (p) { var c = el.props.showCategories === false ? '' : (p.cat || ''); if (!byCat[c]) { byCat[c] = []; groups.push(c); } byCat[c].push(p); });
    groups.forEach(function (c) {
      var g = wb.h('div', 'wb-pg-group' + (el.collapsed.has(c) ? ' wb-pg-collapsed' : ''));
      if (c) { var h = wb.h('div', 'wb-pg-cat', c); h.addEventListener('click', function () { if (el.collapsed.has(c)) el.collapsed.delete(c); else el.collapsed.add(c); g.classList.toggle('wb-pg-collapsed'); }); g.appendChild(h); }
      byCat[c].forEach(function (p) { g.appendChild(self.row(el, p)); });
      el.rows.appendChild(g);
    });
    this.refresh(el);
  },
  refresh: function (el) {
    var self = this; var values = el.props.values || {};
    el.rows.querySelectorAll('.wb-pg-row').forEach(function (r) { var p = r.prop; var v = values[p.id] || {}; self.fill(el, r, p, v); });
  },
  fill: function (el, r, p, v) {
    var val = r.querySelector('.wb-pg-value'); val.innerHTML = ''; val.className = 'wb-pg-value';
    var deco = function (target, d) { if (d.i) { var img = wb.h('img', 'wb-icon'); img.src = wb.res(d.i); img.alt = ''; target.appendChild(img); }
      if (d.h) { var sp = wb.h('span', 'wb-text wb-html'); sp.innerHTML = d.h; target.appendChild(sp); } else target.appendChild(wb.h('span', 'wb-text', d.t || ''));
      if (d.s) d.s.split(' ').forEach(function (k) { if (k) target.classList.add(k); }); if (d.tt) target.title = d.tt; };
    var commit = function (value) { el.ctx.emit('edit', { id: p.id, value: value }); };
    if (p.ro) { deco(val, v); val.classList.add('wb-pg-readonly'); return; }
    if (p.kind === 'bool') { var cb = wb.h('input'); cb.type = 'checkbox'; cb.checked = v.v === true; cb.addEventListener('change', function () { commit(cb.checked); }); val.appendChild(cb); if (v.t && v.t !== 'true' && v.t !== 'false') val.appendChild(wb.h('span', 'wb-text', v.t)); }
    else if (p.kind === 'choice') {
      var sel = wb.h('select', 'wb-input'); var o0 = wb.h('option', '', ''); o0.value = ''; sel.appendChild(o0);
      (p.choices || []).forEach(function (c) { var o = wb.h('option', '', c.l); o.value = c.k; sel.appendChild(o); });
      sel.value = v.k == null ? '' : v.k; sel.addEventListener('change', function () { commit(sel.value === '' ? null : sel.value); });
      if (v.i) { var ci = wb.h('img', 'wb-icon wb-pg-choice-icon'); ci.src = wb.res(v.i); ci.alt = ''; val.appendChild(ci); val.classList.add('wb-pg-with-icon'); }
      val.appendChild(sel);
    } else {
      var inp = wb.h('input', 'wb-input'); inp.type = p.kind === 'date' ? 'date' : 'text'; if (p.kind === 'number') inp.inputMode = 'decimal';
      inp.value = v.v == null ? '' : String(v.v); var orig = inp.value;
      inp.addEventListener('keydown', function (e) { if (e.key === 'Enter') inp.blur(); else if (e.key === 'Escape') { inp.value = orig; inp.blur(); } });
      inp.addEventListener('blur', function () { if (inp.value !== orig) { orig = inp.value; commit(inp.value); } });
      if (v.i) { var ii = wb.h('img', 'wb-icon wb-pg-choice-icon'); ii.src = wb.res(v.i); ii.alt = ''; val.appendChild(ii); val.classList.add('wb-pg-with-icon'); }
      val.appendChild(inp);
    }
    if (v.s) v.s.split(' ').forEach(function (k) { if (k) val.classList.add(k); }); if (v.tt) val.title = v.tt;
    if (el.errors[p.id] != null) { r.classList.add('wb-pg-error'); r.title = el.errors[p.id]; }
  },
  row: function (el, p) {
    var r = wb.h('div', 'wb-pg-row'); r.dataset.id = p.id; r.prop = p;
    var lab = wb.h('div', 'wb-pg-label', p.label); if (p.desc) lab.title = p.desc; r.appendChild(lab); r.appendChild(wb.h('div', 'wb-pg-value'));
    r.addEventListener('focusin', function () { el.desc.innerHTML = ''; el.desc.appendChild(wb.h('b', '', p.label)); if (p.desc) el.desc.appendChild(wb.h('div', '', p.desc)); el.rows.querySelectorAll('.wb-pg-row.wb-focused').forEach(function (x) { x.classList.remove('wb-focused'); }); r.classList.add('wb-focused'); });
    r.addEventListener('click', function () { var i = r.querySelector('input, select'); if (i && document.activeElement !== i) i.focus(); });
    return r;
  },
  insertChild: function () {}, removeChild: function () {}
});
