/* wb.ScrollPane — a viewport around one child; reports the scroll offsets (debounced server-side). */
wb.register('wb.ScrollPane', {
  create: function (ctx) {
    var el = wb.h('div', 'wb-scrollpane'); el.ctx = ctx;
    el.addEventListener('scroll', function () { ctx.emit('scroll', { x: el.scrollLeft, y: el.scrollTop, atBottom: el.scrollTop + el.clientHeight >= el.scrollHeight - 2 }); });
    return el;
  },
  update: function (el, props, changed) {
    var pol = function (p) { return p === 'ALWAYS' ? 'scroll' : p === 'NEVER' ? 'hidden' : 'auto'; };
    if (changed.has('horizontal')) el.style.overflowX = pol(props.horizontal);
    if (changed.has('vertical')) el.style.overflowY = pol(props.vertical);
    if (changed.has('scrollX') || changed.has('scrollY')) { el.scrollLeft = props.scrollX || 0; el.scrollTop = props.scrollY || 0; }
  },
  call: function (el, m, args) {
    if (m === 'scrollTo') { el.scrollLeft = args[0]; el.scrollTop = args[1]; }
    else if (m === 'scrollToBottom') el.scrollTop = el.scrollHeight;
    else if (m === 'scrollIntoView') { var n = wb.nodes.get(args[0]); if (n && n.el) n.el.scrollIntoView({ block: 'nearest' }); }
  },
  insertChild: function (el, child) { el.appendChild(child); },
  removeChild: function (el, child) { child.remove(); }
});
