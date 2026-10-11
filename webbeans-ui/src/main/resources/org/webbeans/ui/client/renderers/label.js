wb.register('wb.Label', {
  create: function (ctx) { var el = wb.h('div', 'wb-label'); el.ico = wb.h('span', 'wb-icon'); el.txt = wb.h('span', 'wb-text'); el.append(el.ico, el.txt); el.addEventListener('click', function (e) { ctx.emit('click', { button: e.button }); }); return el; },
  update: function (el, props, changed) {
    if (changed.has('html') || changed.has('text')) { if (props.html) el.txt.innerHTML = props.html; else el.txt.textContent = props.text || ''; }
    if (changed.has('icon')) { el.ico.innerHTML = ''; if (props.icon) { var img = wb.h('img'); img.src = wb.res(props.icon); img.alt = ''; el.ico.appendChild(img); } el.ico.classList.toggle('wb-hidden', !props.icon); }
    if (changed.has('wrap')) el.classList.toggle('wb-nowrap', props.wrap === false);
  },
  insertChild: function () {}, removeChild: function () {}
});
