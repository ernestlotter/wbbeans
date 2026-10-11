/* wb.MenuBar — the horizontal bar of top-level wb.Menu children; Alt focuses it, arrows move between menus. */
wb.register('wb.MenuBar', {
  create: function (ctx) {
    var el = wb.h('div', 'wb-menubar'); el.setAttribute('role', 'menubar');
    el.__bar = {
      step: function (dir) {
        var tops = Array.from(el.children).filter(function (c) { return c.classList.contains('wb-menu') && !c.classList.contains('wb-disabled'); });
        var cur = tops.indexOf(wb.menus.openRoot); if (cur < 0 || !tops.length) return;
        wb.menus.closeAll(); tops[(cur + dir + tops.length) % tops.length].__open();
      }
    };
    return el;
  },
  update: function () {},
  insertChild: function (el, child, i) { var ref = el.children[i]; if (ref) el.insertBefore(child, ref); else el.appendChild(child); child.classList.remove('wb-menu-sub'); },
  removeChild: function (el, child) { child.remove(); }
});
