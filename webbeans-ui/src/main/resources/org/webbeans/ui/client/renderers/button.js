wb.register('wb.Button', {
  create: function (ctx) {
    var el = wb.h('button', 'wb-button'); el.type = 'button';
    el.ico = wb.h('span', 'wb-icon'); el.txt = wb.h('span', 'wb-text'); el.append(el.ico, el.txt);
    el.addEventListener('click', function (e) { ctx.emit('click', { button: e.button, ctrl: e.ctrlKey, shift: e.shiftKey, alt: e.altKey, meta: e.metaKey, count: e.detail || 1 }); });
    el.addEventListener('focus', function () { ctx.emit('focus', {}); }); el.addEventListener('blur', function () { ctx.emit('blur', {}); });
    return el;
  },
  update: function (el, props, changed) {
    if (changed.has('text')) { el.txt.textContent = props.text || ''; el.classList.toggle('wb-icon-only', !props.text); }
    if (changed.has('icon')) { el.ico.innerHTML = ''; if (props.icon) { var img = wb.h('img'); img.src = wb.res(props.icon); img.alt = ''; el.ico.appendChild(img); } el.ico.classList.toggle('wb-hidden', !props.icon); }
    if (changed.has('kind')) el.dataset.kind = (props.kind || 'DEFAULT').toLowerCase();
    if (changed.has('defaultButton')) el.classList.toggle('wb-default', !!props.defaultButton);
    if (changed.has('busy')) { el.classList.toggle('wb-busy', !!props.busy); el.disabled = !!props.busy || props.enabled === false; }
  },
  call: function (el, m) { if (m === 'click') el.click(); else if (m === 'focus') el.focus(); },
  insertChild: function () {}, removeChild: function () {}
});
