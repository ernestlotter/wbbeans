wb.register('wb.Image', {
  create: function (ctx) { var el = wb.h('img', 'wb-image'); el.addEventListener('click', function (e) { ctx.emit('click', { button: e.button }); }); return el; },
  update: function (el, props, changed) { if (changed.has('source')) el.src = wb.res(props.source) || ''; if (changed.has('alt')) el.alt = props.alt || ''; if (changed.has('fit')) el.style.objectFit = (props.fit || 'contain').toLowerCase(); },
  insertChild: function () {}, removeChild: function () {}
});
