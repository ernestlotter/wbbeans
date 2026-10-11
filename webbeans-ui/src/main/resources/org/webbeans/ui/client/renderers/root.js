/* wb.Root — the page: content area plus an overlay layer for dialogs/toasts; applies title, theme and keymap. */
wb.register('wb.Root', {
  create: function (ctx) {
    var el = wb.h('div', 'wb-page');
    el.content = wb.h('div', 'wb-content'); el.overlay = wb.h('div', 'wb-overlay');
    el.append(el.content, el.overlay);
    wb.rootNode = ctx.node;
    return el;
  },
  update: function (el, props, changed) {
    if (changed.has('title')) document.title = props.title || '';
    if (changed.has('theme')) { var t = props.theme; if (!t || t === 'system') document.documentElement.removeAttribute('data-theme'); else document.documentElement.setAttribute('data-theme', t); }
    if (changed.has('keymap')) wb.keymap = props.keymap || {};
  },
  call: function (el, m, args) {
    if (m === 'navigate') { wb.transport.stopped = true; location.href = args[0]; }
    else if (m === 'reload') { wb.transport.stopped = true; location.reload(); }
    else if (m === 'download') {
      var url = args[0]; if (!/^(https?:)?\//.test(url)) url = (wb.cfg.base || '') + '/' + url;
      var a = wb.h('a'); a.href = url; a.download = ''; a.style.display = 'none'; document.body.appendChild(a); a.click(); setTimeout(function () { a.remove(); }, 1000);
    }
    else if (m === 'logout') {
      wb.transport.stopped = true;
      fetch((wb.cfg.base || '') + '/logout', { method: 'POST', credentials: 'same-origin', headers: { 'X-WB-Token': wb.cfg.token || '', 'Accept': 'application/json' } })
        .then(function () { location.href = (wb.cfg.base || '') + '/login'; }, function () { location.href = (wb.cfg.base || '') + '/login'; });
    }
  },
  insertChild: function (el, child, i, ctx, node) { (node.constraint === 'overlay' ? el.overlay : el.content).appendChild(child); },
  removeChild: function (el, child) { child.remove(); }
});
