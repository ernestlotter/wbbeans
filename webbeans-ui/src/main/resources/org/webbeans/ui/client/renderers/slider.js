/* wb.Slider — a range input; `input` while dragging (throttled by the server's lazy spec), `change` on release. */
wb.register('wb.Slider', {
  create: function (ctx) {
    var wrap = wb.h('div', 'wb-field wb-slider'); var row = wb.h('div', 'wb-slider-row');
    var el = wb.h('input', 'wb-range'); el.type = 'range'; el.id = 'wb-in-' + ctx.id; wrap.input = el;
    wrap.val = wb.h('span', 'wb-slider-value wb-hidden'); wrap.ticks = wb.h('datalist'); wrap.ticks.id = 'wb-ticks-' + ctx.id;
    row.append(el, wrap.val); wrap.append(row, wrap.ticks);
    var err = wb.h('div', 'wb-field-error'); wrap.err = err; wrap.appendChild(err);
    el.addEventListener('input', function () { wrap.val.textContent = el.value; ctx.emit('input', { value: Number(el.value) }); });
    el.addEventListener('change', function () { ctx.emit('change', { value: Number(el.value) }); });
    el.addEventListener('focus', function () { ctx.emit('focus', {}); }); el.addEventListener('blur', function () { ctx.emit('blur', {}); });
    return wrap;
  },
  update: function (wrap, props, changed) {
    var el = wrap.input;
    if (changed.has('min')) el.min = props.min == null ? 0 : props.min;
    if (changed.has('max')) el.max = props.max == null ? 100 : props.max;
    if (changed.has('step')) el.step = props.step || 1;
    if (changed.has('value') || changed.has('min') || changed.has('max')) { el.value = props.value == null ? el.min : props.value; wrap.val.textContent = el.value; }
    if (changed.has('showValue')) wrap.val.classList.toggle('wb-hidden', !props.showValue);
    if (changed.has('orientation')) { var v = props.orientation === 'VERTICAL'; wrap.classList.toggle('wb-vertical', v); if (v) el.setAttribute('orient', 'vertical'); else el.removeAttribute('orient'); }
    if (changed.has('ticks') || changed.has('min') || changed.has('max')) {
      wrap.ticks.innerHTML = '';
      if (props.ticks) { var min = Number(el.min), max = Number(el.max); for (var t = min; t <= max + 1e-9; t += props.ticks) { var o = wb.h('option'); o.value = Math.round(t * 1e6) / 1e6; wrap.ticks.appendChild(o); } el.setAttribute('list', wrap.ticks.id); }
      else el.removeAttribute('list');
    }
    if (changed.has('enabled') || changed.has('readOnly')) el.disabled = props.enabled === false || !!props.readOnly;
    if (changed.has('error')) { wrap.err.textContent = props.error || ''; wrap.classList.toggle('wb-invalid', !!props.error); }
  },
  call: function (wrap, m) { if (m === 'focus') wrap.input.focus(); },
  insertChild: function () {}, removeChild: function () {}
});
