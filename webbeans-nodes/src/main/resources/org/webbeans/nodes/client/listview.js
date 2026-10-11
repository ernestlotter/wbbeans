/* wb.ListView — the children of the explored context as a flat list; double-click traverses into folders. */
(function () {
  var engine = function () { return wb.nodeView; };
  wb.register('wb.ListView', {
    create: function (ctx) {
      var el = wb.h('div', 'wb-nv wb-listview'); el.props = {}; el.context = null;
      el.bar = wb.h('div', 'wb-lv-bar'); el.appendChild(el.bar);
      engine().init(el, ctx, {
        hideRoot: true,
        activate: function (el, row) { if (!row.leaf && el.props.traversalAllowed !== false) ctx.emit('traverse', { n: row.n }); else ctx.emit('activate', { n: row.n }); },
        context: function (el, c) { el.context = c; wb.renderers['wb.ListView'].renderBar(el); },
        afterRender: function (el) { el.list.querySelectorAll('.wb-nv-toggle').forEach(function (t) { t.remove(); }); if (el.props.iconSize) el.list.style.setProperty('--wb-nv-icon', el.props.iconSize + 'px'); }
      });
      el.list.setAttribute('role', 'listbox');
      return el;
    },
    renderBar: function (el) {
      var b = el.bar; b.innerHTML = ''; var c = el.context; if (!c) return;
      if (c.hasParent && el.props.showParentNode !== false && el.props.traversalAllowed !== false) { var up = wb.h('button', 'wb-lv-up', '..'); up.type = 'button'; up.title = c.dn || ''; up.addEventListener('click', function () { el.ctx.emit('traverse', { up: true }); }); b.appendChild(up); }
      b.appendChild(wb.h('span', 'wb-lv-title', c.dn || ''));
    },
    update: function (el, props, changed) { engine().update(el, props, changed); if (changed.has('showParentNode') || changed.has('traversalAllowed')) this.renderBar(el); if (changed.has('iconSize')) engine().render(el); },
    call: function (el, m, args) { engine().call(el, m, args); },
    insertChild: function () {}, removeChild: function () {}
  });
})();
