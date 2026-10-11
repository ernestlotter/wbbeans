/*
 * wb.Notifications — toasts (bottom right) and the drawer (list of all notifications); wb.NotificationsCell is
 * the bell in the status bar. Items arrive by call: init(list), add(item, toast), remove(id), clearAll().
 */
(function () {
  function itemEl(host, it, ctx, inToast) {
    var el = wb.h('div', 'wb-notif'); el.dataset.id = it.id; el.dataset.category = it.category || 'info'; el.setAttribute('role', 'status');
    if (it.clickable) el.classList.add('wb-notif-clickable');
    var head = wb.h('div', 'wb-notif-head');
    if (it.icon) { var im = wb.h('img', 'wb-notif-icon'); im.src = wb.res(it.icon); im.alt = ''; head.appendChild(im); }
    head.appendChild(wb.h('span', 'wb-notif-title', it.title || ''));
    var x = wb.h('button', 'wb-notif-close', '×'); x.type = 'button'; x.title = 'Dismiss'; head.appendChild(x);
    el.appendChild(head);
    if (it.details) el.appendChild(wb.h('div', 'wb-notif-details', it.details));
    if (!inToast && it.time) { var d = new Date(it.time); el.appendChild(wb.h('div', 'wb-notif-time', isNaN(d) ? '' : d.toLocaleTimeString())); }
    x.addEventListener('click', function (e) { e.stopPropagation(); if (inToast) { host.hideToast(it.id); } else ctx.emit('dismiss', { id: it.id }); });
    el.addEventListener('click', function () { if (it.clickable) ctx.emit('clickItem', { id: it.id }); if (inToast) host.hideToast(it.id); });
    return el;
  }
  wb.register('wb.Notifications', {
    create: function (ctx) {
      var host = wb.h('div', 'wb-notifications'); host.items = [];
      host.toasts = wb.h('div', 'wb-toasts'); host.drawer = wb.h('div', 'wb-notif-drawer wb-hidden'); host.append(host.toasts, host.drawer);
      var dh = wb.h('div', 'wb-notif-drawer-head'); dh.appendChild(wb.h('span', '', 'Notifications'));
      var clear = wb.h('button', 'wb-notif-clearall', 'Clear all'); clear.type = 'button'; clear.addEventListener('click', function () { ctx.emit('dismissAll', {}); }); dh.appendChild(clear);
      var close = wb.h('button', 'wb-notif-close', '×'); close.type = 'button'; close.addEventListener('click', function () { host.drawer.classList.add('wb-hidden'); }); dh.appendChild(close);
      host.drawer.appendChild(dh); host.list = wb.h('div', 'wb-notif-list'); host.drawer.appendChild(host.list);
      host.timers = {};
      host.hideToast = function (id) { var t = host.toasts.querySelector('.wb-notif[data-id="' + id + '"]'); if (t) { t.classList.add('wb-notif-out'); setTimeout(function () { t.remove(); }, 250); } clearTimeout(host.timers[id]); delete host.timers[id]; };
      host.render = function () { host.list.innerHTML = ''; if (!host.items.length) host.list.appendChild(wb.h('div', 'wb-notif-empty', 'No notifications')); host.items.forEach(function (it) { host.list.appendChild(itemEl(host, it, ctx, false)); }); };
      host.ctx = ctx;
      return host;
    },
    update: function () {},
    call: function (host, m, args) {
      var ctx = host.ctx;
      switch (m) {
        case 'init': host.items = (args[0] || []).slice(); host.render(); break;
        case 'add': {
          var it = args[0]; host.items = host.items.filter(function (x) { return x.id !== it.id; }); host.items.unshift(it); host.render();
          if (args[1] && it.priority !== 'silent') {
            var t = itemEl(host, it, ctx, true); host.toasts.appendChild(t);
            if (it.priority !== 'high') host.timers[it.id] = setTimeout(function () { host.hideToast(it.id); }, it.priority === 'low' ? 3000 : 6000);
          }
          break;
        }
        case 'remove': host.items = host.items.filter(function (x) { return x.id !== args[0]; }); host.render(); host.hideToast(args[0]); break;
        case 'clearAll': host.items = []; host.render(); host.toasts.innerHTML = ''; break;
        case 'toggleDrawer': host.drawer.classList.toggle('wb-hidden'); if (!host.drawer.classList.contains('wb-hidden')) host.toasts.innerHTML = ''; break;
      }
    },
    insertChild: function () {}, removeChild: function () {}
  });
  wb.register('wb.NotificationsCell', {
    create: function (ctx) {
      var el = wb.h('button', 'wb-notif-cell'); el.type = 'button'; el.title = 'Notifications';
      el.bell = wb.h('span', 'wb-notif-bell'); el.bell.innerHTML = '<svg viewBox="0 0 16 16" width="14" height="14"><path d="M8 1.5a4 4 0 0 0-4 4v3L2.5 11v1h11v-1L12 8.5v-3a4 4 0 0 0-4-4zM6.5 13.5a1.5 1.5 0 0 0 3 0z" fill="currentColor"/></svg>';
      el.count = wb.h('span', 'wb-notif-count'); el.append(el.bell, el.count);
      el.addEventListener('click', function () { ctx.emit('click', { button: 0 }); });
      return el;
    },
    update: function (el, props, changed) { if (changed.has('count')) { el.count.textContent = props.count ? String(props.count) : ''; el.count.classList.toggle('wb-hidden', !props.count); } },
    insertChild: function () {}, removeChild: function () {}
  });
})();
