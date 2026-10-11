/* wb.NumberField — a text input that formats/parses numbers with the server-compiled locale spec; optional spinner. */
wb.register('wb.NumberField', {
  create: function (ctx) {
    var wrap = wb.h('div', 'wb-field wb-numberfield'); var box = wb.h('div', 'wb-number-box');
    var el = wb.h('input', 'wb-input'); el.type = 'text'; el.inputMode = 'decimal'; el.id = 'wb-in-' + ctx.id; el.spellcheck = false; el.autocomplete = 'off';
    wrap.input = el; wrap.pre = wb.h('span', 'wb-number-affix wb-hidden'); wrap.suf = wb.h('span', 'wb-number-affix wb-hidden');
    wrap.spin = wb.h('span', 'wb-spinner wb-hidden'); var up = wb.h('button', 'wb-spin', '▴'), down = wb.h('button', 'wb-spin', '▾'); up.type = down.type = 'button'; up.tabIndex = down.tabIndex = -1;
    wrap.spin.append(up, down); box.append(wrap.pre, el, wrap.suf, wrap.spin); wrap.appendChild(box);
    var err = wb.h('div', 'wb-field-error'); wrap.err = err; wrap.appendChild(err);
    wrap.fmt = { decimal: '.', grouping: ',', maxFrac: 2, minFrac: 0 }; wrap.value = null; wrap.props = {};
    var parse = function (s) {
      s = String(s).trim(); if (!s) return null;
      if (wrap.fmt.grouping) s = s.split(wrap.fmt.grouping).join('');
      if (wrap.fmt.decimal !== '.') s = s.split(wrap.fmt.decimal).join('.');
      s = s.replace(/\s/g, '');
      var n = Number(s); return isNaN(n) ? NaN : n;
    };
    var format = function (n) {
      if (n == null || isNaN(n)) return '';
      var f = wrap.fmt; var fixed = Math.abs(n).toFixed(f.maxFrac); var parts = fixed.split('.'); var int = parts[0], frac = parts[1] || '';
      while (frac.length > f.minFrac && frac.charAt(frac.length - 1) === '0') frac = frac.slice(0, -1);
      if (f.grouping) int = int.replace(/\B(?=(\d{3})+(?!\d))/g, f.grouping);
      return (n < 0 ? '-' : '') + int + (frac ? f.decimal + frac : '');
    };
    wrap.format = format;
    var commit = function () {
      var raw = el.value; var n = parse(raw);
      if (isNaN(n)) { ctx.emit('change', { value: raw }); return; }   // the server answers with an error and the old value
      if (n !== wrap.value || wrap.props.error) { wrap.value = n; el.value = format(n); ctx.emit('change', { value: n }); } else el.value = format(n);  // with an error showing, re-committing the same value clears it
    };
    var step = function (dir) {
      if (el.disabled || el.readOnly) return;
      var p = wrap.props; var cur = parse(el.value); if (isNaN(cur) || cur == null) cur = p.min != null ? p.min : 0;
      var st = p.step || 1; var n = Math.round((cur + dir * st) / st) * st; n = Number(n.toFixed(wrap.fmt.maxFrac));
      if (p.min != null) n = Math.max(p.min, n); if (p.max != null) n = Math.min(p.max, n);
      el.value = format(n); commit();
    };
    up.addEventListener('click', function () { step(1); el.focus(); }); down.addEventListener('click', function () { step(-1); el.focus(); });
    el.addEventListener('change', commit); el.addEventListener('blur', function () { commit(); ctx.emit('blur', {}); });
    el.addEventListener('focus', function () { ctx.emit('focus', {}); });
    el.addEventListener('keydown', function (e) {
      if (e.key === 'ArrowUp') { e.preventDefault(); step(1); } else if (e.key === 'ArrowDown') { e.preventDefault(); step(-1); }
      else if (e.key === 'Enter') { commit(); var form = el.closest('.wb-panel'); var def = form && form.querySelector('.wb-button.wb-default'); if (def) def.click(); }
    });
    el.addEventListener('wheel', function (e) { if (document.activeElement === el && wrap.props.spinner) { e.preventDefault(); step(e.deltaY < 0 ? 1 : -1); } }, { passive: false });
    return wrap;
  },
  update: function (wrap, props, changed) {
    var el = wrap.input; wrap.props = props;
    if (changed.has('format') && props.format) wrap.fmt = Object.assign({ decimal: '.', grouping: ',', maxFrac: 2, minFrac: 0 }, props.format);
    if (changed.has('value') || changed.has('format')) { wrap.value = props.value == null ? null : Number(props.value); el.value = wrap.format(wrap.value); }
    if (changed.has('placeholder')) el.placeholder = props.placeholder || '';
    if (changed.has('readOnly')) el.readOnly = !!props.readOnly;
    if (changed.has('required')) el.required = !!props.required;
    if (changed.has('enabled')) { el.disabled = props.enabled === false; wrap.classList.toggle('wb-disabled', props.enabled === false); }
    if (changed.has('spinner')) wrap.spin.classList.toggle('wb-hidden', !props.spinner);
    if (changed.has('prefix')) { wrap.pre.textContent = props.prefix || ''; wrap.pre.classList.toggle('wb-hidden', !props.prefix); }
    if (changed.has('suffix')) { wrap.suf.textContent = props.suffix || ''; wrap.suf.classList.toggle('wb-hidden', !props.suffix); }
    if (changed.has('error')) { wrap.err.textContent = props.error || ''; wrap.classList.toggle('wb-invalid', !!props.error); el.setAttribute('aria-invalid', props.error ? 'true' : 'false'); }
  },
  call: function (wrap, m) { if (m === 'focus') wrap.input.focus(); else if (m === 'select') wrap.input.select(); },
  insertChild: function () {}, removeChild: function () {}
});
