/* The small standard widgets: ToggleButton, ButtonGroup, SplitButton, ColorPicker, TimePicker, PasswordField, TagInput,
 * Badge, Chip, Avatar, Card, Accordion, Breadcrumb, Stepper, EmptyState, Popover. One file, one stylesheet. */
(function () {
  var icon = function (res, cls) { var img = wb.h('img'); img.src = wb.res(res); img.alt = ''; if (cls === '') return img; var sp = wb.h('span', cls || 'wb-icon'); sp.appendChild(img); return sp; };
  var noChildren = { insertChild: function () {}, removeChild: function () {} };
  var mix = function (impl) { return Object.assign({}, noChildren, impl); };
  var closeAllPopups = function (except) { document.querySelectorAll('.wb-popup-open').forEach(function (p) { if (p !== except) { p.classList.remove('wb-popup-open'); if (p.onClosePopup) p.onClosePopup(); } }); };
  document.addEventListener('mousedown', function (e) { document.querySelectorAll('.wb-popup-open:not(.wb-popup-sticky)').forEach(function (p) { if (!p.contains(e.target) && !(p.anchorEl && p.anchorEl.contains(e.target))) { p.classList.remove('wb-popup-open'); if (p.onClosePopup) p.onClosePopup(); } }); }, true);
  document.addEventListener('keydown', function (e) { if (e.key === 'Escape') closeAllPopups(); }, true);
  // positions a fixed popup next to an anchor rectangle
  function place(pop, anchor, placement) {
    var r = anchor.getBoundingClientRect(); pop.style.visibility = 'hidden'; pop.classList.add('wb-popup-open'); var pr = pop.getBoundingClientRect(); var vw = window.innerWidth, vh = window.innerHeight; var top, left; var p = placement || 'BOTTOM';
    if (p === 'BOTTOM' && r.bottom + pr.height + 8 > vh && r.top - pr.height - 8 > 0) p = 'TOP'; else if (p === 'TOP' && r.top - pr.height - 8 < 0) p = 'BOTTOM';
    if (p === 'BOTTOM') { top = r.bottom + 6; left = r.left; } else if (p === 'TOP') { top = r.top - pr.height - 6; left = r.left; } else if (p === 'RIGHT') { top = r.top; left = r.right + 6; } else { top = r.top; left = r.left - pr.width - 6; }
    left = Math.max(4, Math.min(left, vw - pr.width - 4)); top = Math.max(4, Math.min(top, vh - pr.height - 4));
    pop.style.left = left + 'px'; pop.style.top = top + 'px'; pop.dataset.placement = p.toLowerCase(); pop.style.visibility = '';
  }
  wb.placePopup = place;

  /* ---------- ToggleButton ---------- */
  wb.register('wb.ToggleButton', mix({
    create: function (ctx) {
      var el = wb.h('button', 'wb-button wb-toggle'); el.type = 'button'; el.ico = wb.h('span', 'wb-icon'); el.txt = wb.h('span', 'wb-text'); el.append(el.ico, el.txt); el.on = false;
      el.addEventListener('click', function () { el.on = !el.on; el.classList.toggle('wb-on', el.on); el.setAttribute('aria-pressed', el.on ? 'true' : 'false'); ctx.emit('change', { value: el.on }); ctx.emit('click', { button: 0 }); });
      el.addEventListener('focus', function () { ctx.emit('focus', {}); }); el.addEventListener('blur', function () { ctx.emit('blur', {}); });
      return el;
    },
    update: function (el, props, changed) {
      if (changed.has('value')) { el.on = !!props.value; el.classList.toggle('wb-on', el.on); el.setAttribute('aria-pressed', el.on ? 'true' : 'false'); }
      if (changed.has('text')) { el.txt.textContent = props.text || ''; el.classList.toggle('wb-icon-only', !props.text); }
      if (changed.has('icon') || changed.has('selectedIcon') || changed.has('value')) { var r = el.on && props.selectedIcon ? props.selectedIcon : props.icon; el.ico.innerHTML = ''; if (r) el.ico.appendChild(icon(r, '')); el.ico.classList.toggle('wb-hidden', !r); }
      if (changed.has('enabled') || changed.has('readOnly')) el.disabled = props.enabled === false || !!props.readOnly;
      if (changed.has('error')) el.classList.toggle('wb-invalid', !!props.error);
    },
    call: function (el, m) { if (m === 'focus') el.focus(); }
  }));

  /* ---------- ButtonGroup (segmented) ---------- */
  wb.register('wb.ButtonGroup', mix({
    create: function (ctx) { var el = wb.h('div', 'wb-field wb-btngroup'); el.setAttribute('role', 'radiogroup'); el.bar = wb.h('div', 'wb-btngroup-bar'); el.err = wb.h('div', 'wb-field-error'); el.append(el.bar, el.err); el.ctx = ctx; el.value = null; return el; },
    render: function (el, props) {
      el.bar.innerHTML = '';
      (props.items || []).forEach(function (it) {
        var b = wb.h('button', 'wb-button wb-seg' + (props.value != null && String(props.value) === it.v ? ' wb-on' : '')); b.type = 'button'; b.dataset.key = it.v; b.setAttribute('role', 'radio'); b.disabled = props.enabled === false || !!props.readOnly || it.e === false;
        if (it.i) b.appendChild(icon(it.i)); if (!props.iconsOnly || !it.i) b.appendChild(wb.h('span', 'wb-text', it.l)); if (props.iconsOnly || it.d) b.title = it.d || it.l;
        b.addEventListener('click', function () { var v = it.v; if (props.deselectable && el.value === v) v = null; el.value = v; el.bar.querySelectorAll('.wb-seg').forEach(function (x) { x.classList.toggle('wb-on', x.dataset.key === v); x.setAttribute('aria-checked', x.dataset.key === v ? 'true' : 'false'); }); el.ctx.emit('change', { value: v }); });
        el.bar.appendChild(b);
      });
    },
    update: function (el, props, changed) {
      if (changed.has('value')) el.value = props.value == null ? null : String(props.value);
      if (changed.has('items') || changed.has('enabled') || changed.has('readOnly') || changed.has('iconsOnly') || changed.has('deselectable')) this.render(el, props);
      else if (changed.has('value')) el.bar.querySelectorAll('.wb-seg').forEach(function (x) { x.classList.toggle('wb-on', x.dataset.key === el.value); });
      if (changed.has('error')) { el.err.textContent = props.error || ''; el.classList.toggle('wb-invalid', !!props.error); }
    },
    call: function (el, m) { if (m === 'focus') { var b = el.bar.querySelector('.wb-on') || el.bar.querySelector('button'); if (b) b.focus(); } }
  }));

  /* ---------- SplitButton ---------- */
  wb.register('wb.SplitButton', mix({
    create: function (ctx) {
      var el = wb.h('div', 'wb-splitbutton'); el.ctx = ctx; el.props = {};
      el.main = wb.h('button', 'wb-button wb-split-main'); el.main.type = 'button'; el.ico = wb.h('span', 'wb-icon'); el.txt = wb.h('span', 'wb-text'); el.main.append(el.ico, el.txt);
      el.arrow = wb.h('button', 'wb-button wb-split-arrow', '▾'); el.arrow.type = 'button'; el.arrow.setAttribute('aria-haspopup', 'menu');
      el.menu = wb.h('div', 'wb-popup wb-split-menu'); el.menu.setAttribute('role', 'menu'); el.menu.anchorEl = el; document.body.appendChild(el.menu);
      el.append(el.main, el.arrow); var self = this;
      el.main.addEventListener('click', function (e) { if (el.props.menuOnly) { self.open(el); return; } ctx.emit('click', { button: e.button, ctrl: e.ctrlKey, shift: e.shiftKey, alt: e.altKey, meta: e.metaKey, count: e.detail || 1 }); });
      el.arrow.addEventListener('click', function () { if (el.menu.classList.contains('wb-popup-open')) closeAllPopups(); else self.open(el); });
      el.menu.addEventListener('click', function (e) { var it = e.target.closest('.wb-split-item'); if (it && !it.classList.contains('wb-disabled')) { closeAllPopups(); ctx.emit('select', { key: it.dataset.key }); } });
      el.menu.addEventListener('keydown', function (e) { var items = Array.from(el.menu.querySelectorAll('.wb-split-item:not(.wb-disabled)')); var i = items.indexOf(document.activeElement); if (e.key === 'ArrowDown') { e.preventDefault(); (items[i + 1] || items[0]).focus(); } else if (e.key === 'ArrowUp') { e.preventDefault(); (items[i - 1] || items[items.length - 1]).focus(); } else if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); document.activeElement.click(); } });
      return el;
    },
    open: function (el) { closeAllPopups(el.menu); place(el.menu, el, 'BOTTOM'); var first = el.menu.querySelector('.wb-split-item:not(.wb-disabled)'); if (first) first.focus(); },
    update: function (el, props, changed) {
      el.props = props;
      if (changed.has('text')) { el.txt.textContent = props.text || ''; el.main.classList.toggle('wb-icon-only', !props.text); }
      if (changed.has('icon')) { el.ico.innerHTML = ''; if (props.icon) el.ico.appendChild(icon(props.icon, '')); el.ico.classList.toggle('wb-hidden', !props.icon); }
      if (changed.has('kind')) { el.main.dataset.kind = (props.kind || 'DEFAULT').toLowerCase(); el.arrow.dataset.kind = el.main.dataset.kind; }
      if (changed.has('enabled')) { el.main.disabled = props.enabled === false; el.arrow.disabled = props.enabled === false; }
      if (changed.has('menuOnly')) el.classList.toggle('wb-split-menuonly', !!props.menuOnly);
      if (changed.has('items')) { el.menu.innerHTML = ''; (props.items || []).forEach(function (it) { if (it.v === '-') { el.menu.appendChild(wb.h('div', 'wb-split-sep')); return; } var d = wb.h('div', 'wb-split-item' + (it.e === false ? ' wb-disabled' : '')); d.dataset.key = it.v; d.tabIndex = -1; d.setAttribute('role', 'menuitem'); if (it.i) d.appendChild(icon(it.i)); else d.appendChild(wb.h('span', 'wb-icon')); d.appendChild(wb.h('span', 'wb-text', it.l)); el.menu.appendChild(d); }); }
    },
    call: function (el, m) { if (m === 'focus') el.main.focus(); },
    destroy: function (el) { el.menu.remove(); }
  }));

  /* ---------- ColorPicker ---------- */
  var PALETTE = ['#c62828', '#ef6c00', '#f9a825', '#2e7d32', '#00838f', '#1565c0', '#4527a0', '#ad1457', '#e57373', '#ffb74d', '#fff176', '#81c784', '#4dd0e1', '#64b5f6', '#9575cd', '#f06292', '#000000', '#424242', '#757575', '#bdbdbd', '#eeeeee', '#ffffff'];   // the server's default is not sent
  wb.register('wb.ColorPicker', mix({
    create: function (ctx) {
      var el = wb.h('div', 'wb-field wb-colorpicker'); el.ctx = ctx; el.props = {}; el.value = null;
      el.btn = wb.h('button', 'wb-input wb-color-btn'); el.btn.type = 'button'; el.swatch = wb.h('span', 'wb-color-swatch'); el.txt = wb.h('span', 'wb-text'); el.btn.append(el.swatch, el.txt);
      el.pop = wb.h('div', 'wb-popup wb-color-pop'); el.pop.anchorEl = el.btn; document.body.appendChild(el.pop); el.err = wb.h('div', 'wb-field-error'); el.append(el.btn, el.err);
      var self = this;
      el.btn.addEventListener('click', function () { if (el.btn.disabled) return; if (el.pop.classList.contains('wb-popup-open')) closeAllPopups(); else { closeAllPopups(el.pop); self.renderPop(el); place(el.pop, el.btn, 'BOTTOM'); } });
      return el;
    },
    set: function (el, v) { el.value = v; this.paint(el); el.ctx.emit('change', { value: v }); },
    paint: function (el) { el.swatch.style.background = el.value || 'transparent'; el.swatch.classList.toggle('wb-color-none', !el.value); el.txt.textContent = el.props.showText === false ? '' : (el.value || ''); el.pop.querySelectorAll('.wb-color-cell').forEach(function (c) { c.classList.toggle('wb-on', c.dataset.c === el.value); }); },
    renderPop: function (el) {
      var self = this; el.pop.innerHTML = ''; var grid = wb.h('div', 'wb-color-grid');
      (el.props.palette || PALETTE).forEach(function (c) { var cell = wb.h('button', 'wb-color-cell' + (c === el.value ? ' wb-on' : '')); cell.type = 'button'; cell.dataset.c = c; cell.style.background = c; cell.title = c; cell.addEventListener('click', function () { self.set(el, c); closeAllPopups(); }); grid.appendChild(cell); });
      el.pop.appendChild(grid); var foot = wb.h('div', 'wb-color-foot');
      if (el.props.allowCustom !== false) { var inp = wb.h('input', 'wb-color-native'); inp.type = 'color'; inp.value = el.value || '#000000'; inp.addEventListener('input', function () { self.set(el, inp.value.toLowerCase()); }); var lab = wb.h('label', 'wb-color-custom'); lab.append(inp, wb.h('span', '', '…')); foot.appendChild(lab); var hex = wb.h('input', 'wb-input wb-color-hex'); hex.placeholder = '#rrggbb'; hex.value = el.value || ''; hex.addEventListener('change', function () { var v = hex.value.trim().toLowerCase(); if (/^#[0-9a-f]{6}$/.test(v)) { self.set(el, v); closeAllPopups(); } }); foot.appendChild(hex); }
      if (el.props.clearable) { var clr = wb.h('button', 'wb-button', '×'); clr.type = 'button'; clr.addEventListener('click', function () { self.set(el, null); closeAllPopups(); }); foot.appendChild(clr); }
      el.pop.appendChild(foot);
    },
    update: function (el, props, changed) {
      el.props = props; if (changed.has('value')) el.value = props.value || null;
      if (changed.has('enabled') || changed.has('readOnly')) el.btn.disabled = props.enabled === false || !!props.readOnly;
      if (changed.has('error')) { el.err.textContent = props.error || ''; el.classList.toggle('wb-invalid', !!props.error); }
      this.paint(el);
    },
    call: function (el, m) { if (m === 'focus') el.btn.focus(); },
    destroy: function (el) { el.pop.remove(); }
  }));

  /* ---------- TimePicker ---------- */
  wb.register('wb.TimePicker', mix({
    create: function (ctx) {
      var wrap = wb.h('div', 'wb-field wb-timepicker'); var el = wb.h('input', 'wb-input'); el.type = 'time'; wrap.input = el; wrap.err = wb.h('div', 'wb-field-error'); wrap.append(el, wrap.err); var committed = null;
      var commit = function () { if (el.value !== committed || wrap.classList.contains('wb-invalid')) { committed = el.value; ctx.emit('change', { value: el.value }); } };
      el.addEventListener('change', commit); el.addEventListener('blur', function () { commit(); ctx.emit('blur', {}); }); el.addEventListener('focus', function () { ctx.emit('focus', {}); });
      wrap.setCommitted = function (v) { committed = v; }; return wrap;
    },
    update: function (wrap, props, changed) {
      var el = wrap.input; var v = props.value || '';
      if (changed.has('step')) { var s = props.step == null ? 1 : props.step; el.step = s === 0 ? 1 : s * 60; }
      if (changed.has('value') && el.value !== v) { if (v.length > 5 && el.step % 60 === 0 && el.step !== 1) v = v.slice(0, 5); el.value = v; wrap.setCommitted(el.value); }
      if (changed.has('min')) el.min = props.min || ''; if (changed.has('max')) el.max = props.max || '';
      if (changed.has('readOnly')) el.readOnly = !!props.readOnly; if (changed.has('enabled')) el.disabled = props.enabled === false; if (changed.has('required')) el.required = !!props.required;
      if (changed.has('error')) { wrap.err.textContent = props.error || ''; wrap.classList.toggle('wb-invalid', !!props.error); }
    },
    call: function (wrap, m) { if (m === 'focus') wrap.input.focus(); }
  }));

  /* ---------- PasswordField ---------- */
  var strength = function (pw) {
    if (!pw) return 0; var score = 0; if (pw.length >= 8) score++; if (pw.length >= 12) score++;
    var classes = (/[a-z]/.test(pw) ? 1 : 0) + (/[A-Z]/.test(pw) ? 1 : 0) + (/[0-9]/.test(pw) ? 1 : 0) + (/[^A-Za-z0-9]/.test(pw) ? 1 : 0);
    score += classes >= 2 ? 1 : 0; score += classes >= 4 ? 1 : 0; if (pw.length < 6) score = 0; return score <= 1 ? 0 : score === 2 ? 1 : score === 3 ? 2 : 3;
  };
  var base = wb.textInput('input');
  wb.register('wb.PasswordField', Object.assign({}, base, {
    create: function (ctx) {
      var wrap = base.create.call(this, ctx); wrap.classList.add('wb-password'); wrap.input.type = 'password'; wrap.input.autocomplete = 'current-password';
      var row = wb.h('div', 'wb-password-row'); wrap.insertBefore(row, wrap.input); row.appendChild(wrap.input);
      wrap.eye = wb.h('button', 'wb-password-eye', '\u{1F441}'); wrap.eye.type = 'button'; wrap.eye.tabIndex = -1; wrap.eye.title = 'Show'; wrap.eye.addEventListener('mousedown', function (e) { e.preventDefault(); }); wrap.eye.addEventListener('click', function () { var show = wrap.input.type === 'password'; wrap.input.type = show ? 'text' : 'password'; wrap.eye.classList.toggle('wb-on', show); }); row.appendChild(wrap.eye);
      wrap.meter = wb.h('div', 'wb-password-meter wb-hidden'); for (var i = 0; i < 4; i++) wrap.meter.appendChild(wb.h('span')); wrap.meter.label = wb.h('span', 'wb-password-label'); wrap.meter.appendChild(wrap.meter.label); wrap.insertBefore(wrap.meter, wrap.err);
      var self = this; wrap.input.addEventListener('input', function () { self.meterUpdate(wrap); });
      return wrap;
    },
    meterUpdate: function (wrap) { var s = strength(wrap.input.value); var labels = wrap.labels || ['Weak', 'Fair', 'Good', 'Strong']; wrap.meter.dataset.level = wrap.input.value ? s : ''; wrap.meter.label.textContent = wrap.input.value ? labels[s] : ''; },
    update: function (wrap, props, changed) {
      base.update.call(this, wrap, props, changed); if (changed.has('password')) wrap.input.type = 'password';
      if (changed.has('revealButton')) wrap.eye.classList.toggle('wb-hidden', props.revealButton === false);
      if (changed.has('strengthMeter')) wrap.meter.classList.toggle('wb-hidden', !props.strengthMeter);
      if (changed.has('strengthLabels')) wrap.labels = props.strengthLabels;
      if (changed.has('value') || changed.has('strengthMeter')) this.meterUpdate(wrap);
    }
  }));

  /* ---------- TagInput ---------- */
  wb.register('wb.TagInput', mix({
    create: function (ctx) {
      var el = wb.h('div', 'wb-field wb-taginput'); el.ctx = ctx; el.props = {}; el.tags = [];
      el.box = wb.h('div', 'wb-input wb-tag-box'); el.input = wb.h('input', 'wb-tag-edit'); el.input.autocomplete = 'off'; el.list = wb.h('datalist'); el.list.id = 'wb-tags-' + ctx.id; el.input.setAttribute('list', el.list.id);
      el.box.append(el.input, el.list); el.err = wb.h('div', 'wb-field-error'); el.append(el.box, el.err); var self = this;
      el.box.addEventListener('click', function (e) { if (e.target === el.box) el.input.focus(); });
      el.input.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' || e.key === ',' || e.key === 'Tab' && el.input.value.trim()) { if (el.input.value.trim()) { e.preventDefault(); self.add(el, el.input.value); } else if (e.key === 'Enter') e.preventDefault(); }
        else if (e.key === 'Backspace' && !el.input.value && el.tags.length) { el.tags.pop(); self.commit(el); }
        else if (e.key === 'Escape') el.input.blur();
      });
      el.input.addEventListener('change', function () { if (el.input.value.trim() && el.props.suggestions && el.props.suggestions.indexOf(el.input.value.trim()) >= 0) self.add(el, el.input.value); });
      el.input.addEventListener('blur', function () { if (el.input.value.trim()) self.add(el, el.input.value); ctx.emit('blur', {}); }); el.input.addEventListener('focus', function () { ctx.emit('focus', {}); });
      return el;
    },
    add: function (el, text) {
      var parts = text.split(',').map(function (s) { return s.trim(); }).filter(Boolean); var changed = false;
      parts.forEach(function (t) { if (el.props.lowerCase) t = t.toLowerCase(); if (el.props.restrict && (el.props.suggestions || []).indexOf(t) < 0) return; if (el.tags.indexOf(t) < 0 && (el.props.maxTags == null || el.tags.length < el.props.maxTags)) { el.tags.push(t); changed = true; } });
      el.input.value = ''; if (changed) this.commit(el); else this.render(el);
    },
    commit: function (el) { this.render(el); el.ctx.emit('change', { value: el.tags.slice() }); },
    render: function (el) {
      var self = this; el.box.querySelectorAll('.wb-tag').forEach(function (t) { t.remove(); });
      el.tags.forEach(function (t, i) { var chip = wb.h('span', 'wb-tag'); chip.appendChild(wb.h('span', 'wb-text', t)); var x = wb.h('button', 'wb-tag-x', '×'); x.type = 'button'; x.tabIndex = -1; x.title = 'Remove'; x.addEventListener('click', function () { el.tags.splice(i, 1); self.commit(el); el.input.focus(); }); chip.appendChild(x); el.box.insertBefore(chip, el.input); });
      el.input.placeholder = el.tags.length ? '' : (el.props.placeholder || ''); el.input.disabled = el.props.enabled === false || !!el.props.readOnly || (el.props.maxTags != null && el.tags.length >= el.props.maxTags);
      el.box.querySelectorAll('.wb-tag-x').forEach(function (x) { x.classList.toggle('wb-hidden', el.props.enabled === false || !!el.props.readOnly); });
    },
    update: function (el, props, changed) {
      el.props = props;
      if (changed.has('value')) el.tags = (props.value || []).slice();
      if (changed.has('suggestions')) { el.list.innerHTML = ''; (props.suggestions || []).forEach(function (s) { var o = wb.h('option'); o.value = s; el.list.appendChild(o); }); }
      if (changed.has('error')) { el.err.textContent = props.error || ''; el.classList.toggle('wb-invalid', !!props.error); }
      this.render(el);
    },
    call: function (el, m) { if (m === 'focus') el.input.focus(); }
  }));

  /* ---------- Badge ---------- */
  wb.register('wb.Badge', mix({
    create: function () { return wb.h('span', 'wb-badge'); },
    update: function (el, props, changed) {
      if (changed.has('text')) el.textContent = props.text || '';
      if (changed.has('kind')) el.dataset.kind = (props.kind || 'DEFAULT').toLowerCase();
      if (changed.has('pill')) el.classList.toggle('wb-badge-square', props.pill === false);
      if (changed.has('dot')) el.classList.toggle('wb-badge-dot', !!props.dot);
    }
  }));

  /* ---------- Chip ---------- */
  wb.register('wb.Chip', mix({
    create: function (ctx) {
      var el = wb.h('span', 'wb-chip'); el.ico = wb.h('span', 'wb-icon wb-hidden'); el.txt = wb.h('span', 'wb-text'); el.x = wb.h('button', 'wb-chip-x wb-hidden', '×'); el.x.type = 'button'; el.x.title = 'Remove'; el.append(el.ico, el.txt, el.x); el.props = {}; el.on = false;
      el.x.addEventListener('click', function (e) { e.stopPropagation(); ctx.emit('close', {}); });
      el.addEventListener('click', function (e) { if (el.props.selectable) { el.on = !el.on; el.classList.toggle('wb-on', el.on); ctx.emit('select', { value: el.on }); } ctx.emit('click', { button: e.button, ctrl: e.ctrlKey, shift: e.shiftKey, alt: e.altKey, meta: e.metaKey, count: e.detail || 1 }); });
      el.addEventListener('keydown', function (e) { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); el.click(); } else if (e.key === 'Delete' || e.key === 'Backspace') { if (el.props.closable) ctx.emit('close', {}); } });
      return el;
    },
    update: function (el, props, changed) {
      el.props = props;
      if (changed.has('text')) el.txt.textContent = props.text || '';
      if (changed.has('icon')) { el.ico.innerHTML = ''; if (props.icon) el.ico.appendChild(icon(props.icon, '')); el.ico.classList.toggle('wb-hidden', !props.icon); }
      if (changed.has('closable')) el.x.classList.toggle('wb-hidden', !props.closable);
      if (changed.has('selectable') || changed.has('closable')) { var act = !!props.selectable || !!props.closable; el.classList.toggle('wb-chip-active', act); el.tabIndex = 0; }
      if (changed.has('selected')) { el.on = !!props.selected; el.classList.toggle('wb-on', el.on); }
      if (changed.has('color')) { if (props.color) el.style.setProperty('--wb-chip-color', props.color); else el.style.removeProperty('--wb-chip-color'); }
      if (changed.has('enabled')) el.classList.toggle('wb-disabled', props.enabled === false);
    }
  }));

  /* ---------- Avatar ---------- */
  var hue = function (s) { var h = 0; for (var i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) % 360; return h; };
  var initials = function (name) { var p = (name || '').trim().split(/\s+/).filter(Boolean); if (!p.length) return '?'; var s = p[0][0]; if (p.length > 1) s += p[p.length - 1][0]; return s.toUpperCase(); };
  wb.register('wb.Avatar', mix({
    create: function () { var el = wb.h('span', 'wb-avatar'); el.body = wb.h('span', 'wb-avatar-body'); el.dot = wb.h('span', 'wb-avatar-status wb-hidden'); el.append(el.body, el.dot); return el; },
    update: function (el, props, changed) {
      if (changed.has('size')) { el.style.setProperty('--wb-avatar-size', (props.size || 32) + 'px'); }
      if (changed.has('name') || changed.has('image') || changed.has('color')) {
        el.body.innerHTML = ''; var name = props.name || '';
        if (props.image) { var img = wb.h('img'); img.src = wb.res(props.image); img.alt = name; el.body.appendChild(img); el.body.style.background = ''; }
        else { el.body.textContent = initials(name); el.body.style.background = props.color || ('hsl(' + hue(name) + ', 45%, 45%)'); }
        el.setAttribute('aria-label', name);
      }
      if (changed.has('status')) { var s = (props.status || 'NONE').toLowerCase(); el.dot.classList.toggle('wb-hidden', s === 'none'); el.dot.dataset.status = s; }
      if (changed.has('square')) el.classList.toggle('wb-avatar-square', !!props.square);
    }
  }));

  /* ---------- Card (a Panel with a header) ---------- */
  var cardImpl = {
    create: function (ctx) {
      if (!cardImpl.__linked) { Object.setPrototypeOf(cardImpl, wb.renderers['wb.Panel']); cardImpl.__linked = true; }
      var el = Object.getPrototypeOf(cardImpl).create.call(this, ctx); el.classList.add('wb-card'); el.ctx = ctx;
      el.head = wb.h('div', 'wb-card-head wb-panel-keep wb-panel-head wb-hidden'); el.banner = wb.h('img', 'wb-card-image wb-hidden'); el.banner.alt = '';
      el.head.ico = wb.h('span', 'wb-icon wb-hidden'); el.head.titles = wb.h('div', 'wb-card-titles'); el.head.titleEl = wb.h('div', 'wb-card-title'); el.head.sub = wb.h('div', 'wb-card-subtitle'); el.head.titles.append(el.head.titleEl, el.head.sub); el.head.append(el.banner, el.head.ico, el.head.titles);
      el.prepend(el.head);
      el.addEventListener('click', function (e) { if (el.classList.contains('wb-card-clickable') && !e.target.closest('button, input, select, textarea, a')) ctx.emit('click', { button: e.button, ctrl: e.ctrlKey, shift: e.shiftKey, alt: e.altKey, meta: e.metaKey, count: e.detail || 1 }); });
      el.addEventListener('keydown', function (e) { if (el.classList.contains('wb-card-clickable') && (e.key === 'Enter' || e.key === ' ') && e.target === el) { e.preventDefault(); ctx.emit('click', { button: 0, count: 1 }); } });
      return el;
    },
    update: function (el, props, changed, ctx) {
      Object.getPrototypeOf(cardImpl).update.call(this, el, props, changed, ctx);
      if (changed.has('cardTitle') || changed.has('subtitle') || changed.has('icon') || changed.has('image')) {
        el.head.titleEl.textContent = props.cardTitle || ''; el.head.sub.textContent = props.subtitle || ''; el.head.sub.classList.toggle('wb-hidden', !props.subtitle);
        el.head.ico.innerHTML = ''; if (props.icon) el.head.ico.appendChild(icon(props.icon, '')); el.head.ico.classList.toggle('wb-hidden', !props.icon);
        if (props.image) { el.banner.src = wb.res(props.image); } el.banner.classList.toggle('wb-hidden', !props.image);
        el.head.classList.toggle('wb-hidden', !props.cardTitle && !props.subtitle && !props.icon && !props.image);
      }
      if (changed.has('clickable')) { el.classList.toggle('wb-card-clickable', !!props.clickable); el.tabIndex = props.clickable ? 0 : -1; }
      if (changed.has('cardSelected')) el.classList.toggle('wb-card-selected', !!props.cardSelected);
      if (changed.has('elevated')) el.classList.toggle('wb-card-flat', props.elevated === false);
    }
  };
  wb.register('wb.Card', cardImpl);

  /* ---------- Accordion ---------- */
  wb.register('wb.Accordion', {
    create: function (ctx) { var el = wb.h('div', 'wb-accordion'); el.ctx = ctx; el.props = {}; el.open = new Set(); return el; },
    rebuild: function (el, node) {
      var self = this; el.innerHTML = '';
      node.children.forEach(function (k, i) {
        var c = k.constraint && typeof k.constraint === 'object' ? k.constraint : { title: String(k.constraint || '') };
        var sec = wb.h('div', 'wb-acc-section' + (el.open.has(i) ? ' wb-acc-open' : '')); var h = wb.h('button', 'wb-acc-head'); h.type = 'button'; h.setAttribute('aria-expanded', el.open.has(i) ? 'true' : 'false');
        h.appendChild(wb.h('span', 'wb-acc-arrow')); if (c.icon) h.appendChild(icon(c.icon)); h.appendChild(wb.h('span', 'wb-text', c.title || '')); if (c.badge) h.appendChild(wb.h('span', 'wb-badge', c.badge));
        h.addEventListener('click', function () { self.toggle(el, i); });
        var body = wb.h('div', 'wb-acc-body'); body.appendChild(k.el); sec.append(h, body); el.appendChild(sec);
      });
    },
    toggle: function (el, i) {
      if (el.open.has(i)) el.open.delete(i); else { if (!el.props.multiple) el.open.clear(); el.open.add(i); }
      this.paint(el); el.ctx.emit('toggle', { expanded: Array.from(el.open).sort() });
    },
    paint: function (el) { Array.from(el.children).forEach(function (sec, i) { var on = el.open.has(i); sec.classList.toggle('wb-acc-open', on); sec.firstChild.setAttribute('aria-expanded', on ? 'true' : 'false'); }); },
    update: function (el, props, changed, ctx) {
      el.props = props; if (changed.has('expanded')) { el.open = new Set(props.expanded || []); if (!props.multiple && el.open.size > 1) el.open = new Set([Array.from(el.open)[0]]); }
      this.rebuild(el, ctx.node);
    },
    insertChild: function (el, child, i, ctx) { this.rebuild(el, ctx.node); },
    removeChild: function (el, child, ctx) { child.remove(); this.rebuild(el, ctx.node); }
  });

  /* ---------- Breadcrumb ---------- */
  wb.register('wb.Breadcrumb', mix({
    create: function (ctx) { var el = wb.h('nav', 'wb-breadcrumb'); el.ctx = ctx; el.setAttribute('aria-label', 'Breadcrumb'); return el; },
    update: function (el, props, changed) {
      if (!(changed.has('items') || changed.has('separator') || changed.has('maxItems'))) return;
      el.innerHTML = ''; var items = props.items || []; var max = props.maxItems; var self = this;
      var show = items.map(function (it, i) { return { it: it, i: i }; });
      if (max && items.length > max && max >= 2) { var head = show.slice(0, 1); var tail = show.slice(items.length - (max - 1)); show = head.concat([{ ellipsis: true }], tail); }
      show.forEach(function (s, n) {
        if (n > 0) el.appendChild(wb.h('span', 'wb-crumb-sep', props.separator || '›'));
        if (s.ellipsis) { el.appendChild(wb.h('span', 'wb-crumb wb-crumb-ellipsis', '…')); return; }
        var last = s.i === items.length - 1; var c = wb.h(last ? 'span' : 'a', 'wb-crumb' + (last ? ' wb-crumb-current' : '')); if (!last) { c.href = '#'; c.addEventListener('click', function (e) { e.preventDefault(); el.ctx.emit('select', { key: s.it.v, index: s.i }); }); } else c.setAttribute('aria-current', 'page');
        if (s.it.i) c.appendChild(icon(s.it.i)); c.appendChild(wb.h('span', 'wb-text', s.it.l)); el.appendChild(c);
      });
    }
  }));

  /* ---------- Stepper ---------- */
  wb.register('wb.Stepper', mix({
    create: function (ctx) { var el = wb.h('ol', 'wb-stepper'); el.ctx = ctx; return el; },
    update: function (el, props, changed) {
      el.classList.toggle('wb-stepper-v', props.orientation === 'VERTICAL'); el.innerHTML = ''; var cur = props.current || 0; var err = props.errorStep;
      (props.steps || []).forEach(function (s, i) {
        var state = err === i ? 'error' : i < cur ? 'done' : i === cur ? 'current' : 'pending';
        var li = wb.h('li', 'wb-step wb-step-' + state); li.dataset.state = state; var click = props.clickable && (props.free || i < cur);
        var num = wb.h(click ? 'button' : 'span', 'wb-step-num', state === 'done' ? '✓' : state === 'error' ? '!' : String(i + 1)); if (click) { num.type = 'button'; num.addEventListener('click', function () { el.ctx.emit('select', { index: i }); }); li.classList.add('wb-step-click'); }
        var txt = wb.h('div', 'wb-step-text'); txt.appendChild(wb.h('div', 'wb-step-title', s.title)); if (s.desc) txt.appendChild(wb.h('div', 'wb-step-desc', s.desc));
        li.append(num, txt); if (click) li.addEventListener('click', function (e) { if (e.target !== num) num.click(); }); el.appendChild(li);
      });
    }
  }));

  /* ---------- EmptyState ---------- */
  wb.register('wb.EmptyState', mix({
    create: function (ctx) {
      var el = wb.h('div', 'wb-empty'); el.ico = wb.h('div', 'wb-empty-icon wb-hidden'); el.titleEl = wb.h('div', 'wb-empty-title'); el.txt = wb.h('div', 'wb-empty-text wb-hidden'); el.btn = wb.h('button', 'wb-button wb-hidden'); el.btn.type = 'button'; el.btn.dataset.kind = 'primary';
      el.btn.addEventListener('click', function (e) { ctx.emit('click', { button: e.button, count: 1 }); }); el.append(el.ico, el.titleEl, el.txt, el.btn); return el;
    },
    update: function (el, props, changed) {
      if (changed.has('icon')) { el.ico.innerHTML = ''; if (props.icon) el.ico.appendChild(icon(props.icon, '')); el.ico.classList.toggle('wb-hidden', !props.icon); }
      if (changed.has('title')) el.titleEl.textContent = props.title || '';
      if (changed.has('text')) { el.txt.textContent = props.text || ''; el.txt.classList.toggle('wb-hidden', !props.text); }
      if (changed.has('actionText')) { el.btn.textContent = props.actionText || ''; el.btn.classList.toggle('wb-hidden', !props.actionText); }
    }
  }));

  /* ---------- Popover ---------- */
  wb.register('wb.Popover', {
    create: function (ctx) {
      var el = wb.h('div', 'wb-popover-slot'); el.ctx = ctx; el.props = {};
      el.pop = wb.h('div', 'wb-popup wb-popover'); el.pop.setAttribute('role', 'dialog'); el.arrow = wb.h('div', 'wb-popover-arrow'); el.body = wb.h('div', 'wb-popover-body'); el.pop.append(el.arrow, el.body); document.body.appendChild(el.pop);
      el.pop.onClosePopup = function () { if (el.isOpen) { el.isOpen = false; ctx.emit('close', {}); } };
      el.pop.addEventListener('mousedown', function (e) { e.stopPropagation(); });
      el.reposition = function () { if (el.isOpen && el.anchor) place(el.pop, el.anchor, el.props.placement); };
      window.addEventListener('resize', el.reposition); window.addEventListener('scroll', el.reposition, true);
      return el;
    },
    update: function (el, props, changed) {
      el.props = props;
      if (changed.has('anchor')) { var n = wb.ref(props.anchor); el.anchor = n ? n.el : null; el.pop.anchorEl = el.anchor; }
      if (changed.has('padding')) el.body.style.padding = (props.padding == null ? 10 : props.padding) + 'px';
      if (changed.has('arrow')) el.arrow.classList.toggle('wb-hidden', props.arrow === false);
      if (changed.has('closeOnOutside')) el.pop.classList.toggle('wb-popup-sticky', props.closeOnOutside === false);
      if (changed.has('open') || changed.has('anchor') || changed.has('placement')) {
        if (props.open && el.anchor) { el.isOpen = true; closeAllPopups(el.pop); place(el.pop, el.anchor, props.placement); var f = el.body.querySelector('input, select, textarea, button'); if (f) f.focus(); }
        else { el.isOpen = false; el.pop.classList.remove('wb-popup-open'); }
      }
    },
    insertChild: function (el, child) { el.body.appendChild(child); },
    removeChild: function (el, child) { child.remove(); },
    destroy: function (el) { el.pop.remove(); window.removeEventListener('resize', el.reposition); window.removeEventListener('scroll', el.reposition, true); }
  });
})();
