wb.register('wb.CheckBox', {
  create: function (ctx) {
    var el = wb.h('label', 'wb-checkbox'); var input = wb.h('input'); input.type = 'checkbox'; input.id = 'wb-cb-' + ctx.id; el.input = input;
    var txt = wb.h('span', 'wb-text'); el.txt = txt; el.append(input, txt);
    input.addEventListener('change', function () { ctx.emit('change', { value: input.checked }); });
    return el;
  },
  update: function (el, props, changed) {
    if (changed.has('value')) el.input.checked = !!props.value;
    if (changed.has('text')) el.txt.textContent = props.text || '';
    if (changed.has('indeterminate')) el.input.indeterminate = !!props.indeterminate;
    if (changed.has('enabled') || changed.has('readOnly')) el.input.disabled = props.enabled === false || !!props.readOnly;
  },
  insertChild: function () {}, removeChild: function () {}
});
