/* wb.MainWindow — menu bar, toolbars, dock and status bar stacked; slots by child constraint. */
wb.register('wb.MainWindow', {
  create: function () {
    var el = wb.h('div', 'wb-mainwindow');
    el.slots = {};
    ['menubar', 'toolbar', 'dock', 'status'].forEach(function (s) { var d = wb.h('div', 'wb-mw-' + s); d.classList.add('wb-mw-empty'); el.slots[s] = d; el.appendChild(d); });
    return el;
  },
  update: function () {},
  insertChild: function (el, child, i, ctx, node) {
    var slot = el.slots[node.constraint] || el.slots.dock;
    slot.appendChild(child); slot.classList.remove('wb-mw-empty');
  },
  removeChild: function (el, child) { var slot = child.parentElement; child.remove(); if (slot && !slot.children.length) slot.classList.add('wb-mw-empty'); }
});
