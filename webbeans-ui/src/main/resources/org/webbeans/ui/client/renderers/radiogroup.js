/* wb.RadioGroup — one radio button per item; the value is the selected item's key. */
wb.register('wb.RadioGroup', {
  create: function (ctx) {
    var el = wb.h('div', 'wb-field wb-radiogroup'); el.setAttribute('role', 'radiogroup'); el.list = wb.h('div', 'wb-radio-list'); el.appendChild(el.list);
    var err = wb.h('div', 'wb-field-error'); el.err = err; el.appendChild(err); el.name = 'wb-rg-' + ctx.id; el.ctx = ctx;
    return el;
  },
  render: function (el, props) {
    el.list.innerHTML = '';
    (props.items || []).forEach(function (it) {
      var lab = wb.h('label', 'wb-radio'); var input = wb.h('input'); input.type = 'radio'; input.name = el.name; input.value = it.v;
      input.checked = props.value != null && String(props.value) === it.v; input.disabled = props.enabled === false || !!props.readOnly || it.e === false;
      var txt = wb.h('span', 'wb-radio-text'); if (it.i) { var img = wb.h('img', 'wb-icon'); img.src = wb.res(it.i); img.alt = ''; txt.appendChild(img); }
      txt.appendChild(wb.h('span', 'wb-text', it.l)); if (it.d) txt.appendChild(wb.h('div', 'wb-radio-desc', it.d));
      lab.append(input, txt); el.list.appendChild(lab);
      input.addEventListener('change', function () { if (input.checked) el.ctx.emit('change', { value: it.v }); });
    });
  },
  update: function (el, props, changed) {
    if (changed.has('items') || changed.has('enabled') || changed.has('readOnly')) this.render(el, props);
    else if (changed.has('value')) el.list.querySelectorAll('input').forEach(function (i) { i.checked = props.value != null && String(props.value) === i.value; });
    if (changed.has('orientation')) el.list.classList.toggle('wb-horizontal', props.orientation === 'HORIZONTAL');
    if (changed.has('error')) { el.err.textContent = props.error || ''; el.classList.toggle('wb-invalid', !!props.error); }
  },
  call: function (el, m) { if (m === 'focus') { var i = el.list.querySelector('input:checked') || el.list.querySelector('input'); if (i) i.focus(); } },
  insertChild: function () {}, removeChild: function () {}
});
