wb.register('wb.ComboBox', {
  create: function (ctx) {
    var wrap = wb.h('div', 'wb-field'); var el = wb.h('select', 'wb-input wb-select'); el.id = 'wb-sel-' + ctx.id; wrap.input = el; wrap.appendChild(el);
    var err = wb.h('div', 'wb-field-error'); wrap.err = err; wrap.appendChild(err);
    el.addEventListener('change', function () { ctx.emit('change', { value: el.value === '' ? null : el.value }); });
    return wrap;
  },
  update: function (wrap, props, changed) {
    var el = wrap.input;
    if (changed.has('items') || changed.has('placeholder') || changed.has('allowEmpty')) {
      el.innerHTML = '';
      if (props.allowEmpty !== false || !props.items || !props.items.length) { var o = wb.h('option'); o.value = ''; o.textContent = props.placeholder || ''; el.appendChild(o); }
      (props.items || []).forEach(function (it) { var o = wb.h('option'); o.value = it.v; o.textContent = it.l; if (it.e === false) o.disabled = true; if (it.d) o.title = it.d; el.appendChild(o); });
      el.value = props.value == null ? '' : props.value;
    }
    if (changed.has('value')) el.value = props.value == null ? '' : props.value;
    if (changed.has('enabled') || changed.has('readOnly')) el.disabled = props.enabled === false || !!props.readOnly;
    if (changed.has('error')) { wrap.err.textContent = props.error || ''; wrap.classList.toggle('wb-invalid', !!props.error); }
  },
  call: function (wrap, m) { if (m === 'focus') wrap.input.focus(); },
  insertChild: function () {}, removeChild: function () {}
});
