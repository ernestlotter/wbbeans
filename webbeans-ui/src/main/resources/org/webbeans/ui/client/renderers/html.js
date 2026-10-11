wb.register('wb.Html', {
  create: function (ctx) { var el = wb.h('div', 'wb-html'); el.addEventListener('click', function (e) { var a = e.target.closest('a'); if (a) { a.rel = 'noopener'; a.target = '_blank'; } ctx.emit('click', { href: a ? a.getAttribute('href') : null }); }); return el; },
  update: function (el, props, changed) { if (changed.has('html')) el.innerHTML = props.html || ''; },
  insertChild: function () {}, removeChild: function () {}
});
