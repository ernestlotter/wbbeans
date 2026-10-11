/* wb.HelpViewer — a scrollable help page. help:<id> and #anchor links become "navigate" events; other links open
   in a new tab. scrollTo(id) scrolls a heading into view. */
wb.register('wb.HelpViewer', {
  create: function (ctx) {
    var el = wb.h('div', 'wb-help');
    el.addEventListener('click', function (e) {
      var a = e.target.closest('a');
      if (!a) return;
      var href = a.getAttribute('href') || '';
      if (href.indexOf('help:') === 0 || href.indexOf('#') === 0) { e.preventDefault(); ctx.emit('navigate', { href: href }); }
      else { a.rel = 'noopener'; a.target = '_blank'; }
    });
    return el;
  },
  update: function (el, props, changed) { if (changed.has('html')) { el.innerHTML = props.html || ''; el.scrollTop = 0; } },
  call: function (el, m, args) {
    if (m === 'scrollTo') {
      var id = args && args[0];
      if (!id) { el.scrollTop = 0; return; }
      var t = el.querySelector('#' + CSS.escape(id)) || el.querySelector('[name="' + id + '"]');
      if (t) t.scrollIntoView({ block: 'start' });
    }
  },
  insertChild: function () {}, removeChild: function () {}
});
