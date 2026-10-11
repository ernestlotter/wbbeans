wb.register('wb.Link', {
  create: function (ctx) { var el = wb.h('a', 'wb-link'); el.addEventListener('click', function (e) { if (!el.getAttribute('href')) { e.preventDefault(); ctx.emit('click', { button: e.button }); } }); return el; },
  update: function (el, props, changed) {
    if (changed.has('text')) el.textContent = props.text || '';
    if (changed.has('href') || changed.has('newTab')) { if (props.href) { el.href = props.href; el.target = props.newTab === false ? '' : '_blank'; el.rel = 'noopener'; } else { el.removeAttribute('href'); el.href = '#'; el.setAttribute('role', 'button'); } }
  },
  insertChild: function () {}, removeChild: function () {}
});
