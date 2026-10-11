/* wb.DatePicker / wb.DateTimePicker — the browser's native date inputs; ISO strings on the wire. */
(function () {
  var picker = function (type) {
    return {
      create: function (ctx) {
        var wrap = wb.h('div', 'wb-field wb-datepicker'); var box = wb.h('div', 'wb-date-box');
        var el = wb.h('input', 'wb-input'); el.type = type; el.id = 'wb-in-' + ctx.id; wrap.input = el;
        var clear = wb.h('button', 'wb-clear wb-hidden', '×'); clear.type = 'button'; clear.tabIndex = -1; clear.title = 'Clear'; wrap.clear = clear;
        box.append(el, clear); wrap.appendChild(box);
        var err = wb.h('div', 'wb-field-error'); wrap.err = err; wrap.appendChild(err);
        var committed = '';
        var commit = function () { if (el.value !== committed || wrap.classList.contains('wb-invalid')) { committed = el.value; ctx.emit('change', { value: el.value || null }); } };
        el.addEventListener('change', commit); el.addEventListener('blur', function () { commit(); ctx.emit('blur', {}); });
        el.addEventListener('focus', function () { ctx.emit('focus', {}); });
        clear.addEventListener('click', function () { el.value = ''; commit(); el.focus(); });
        wrap.setCommitted = function (v) { committed = v; };
        return wrap;
      },
      update: function (wrap, props, changed) {
        var el = wrap.input;
        if (changed.has('value')) { el.value = props.value || ''; wrap.setCommitted(el.value); }
        if (changed.has('min')) { if (props.min) el.min = props.min; else el.removeAttribute('min'); }
        if (changed.has('max')) { if (props.max) el.max = props.max; else el.removeAttribute('max'); }
        if (changed.has('seconds')) el.step = props.seconds ? 1 : 60;
        if (changed.has('clearButton')) wrap.clear.classList.toggle('wb-hidden', !props.clearButton);
        if (changed.has('readOnly')) el.readOnly = !!props.readOnly;
        if (changed.has('required')) el.required = !!props.required;
        if (changed.has('enabled')) el.disabled = props.enabled === false;
        if (changed.has('error')) { wrap.err.textContent = props.error || ''; wrap.classList.toggle('wb-invalid', !!props.error); el.setAttribute('aria-invalid', props.error ? 'true' : 'false'); }
      },
      call: function (wrap, m) { if (m === 'focus') wrap.input.focus(); },
      insertChild: function () {}, removeChild: function () {}
    };
  };
  wb.register('wb.DatePicker', picker('date'));
  wb.register('wb.DateTimePicker', picker('datetime-local'));
})();
