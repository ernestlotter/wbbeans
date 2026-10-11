/* wb.SplitPane — two panes and a draggable divider; the first pane's size (px) is client-owned. */
wb.register('wb.SplitPane', {
  create: function (ctx) {
    var el = wb.h('div', 'wb-split'); el.first = wb.h('div', 'wb-split-pane'); el.div = wb.h('div', 'wb-split-divider'); el.second = wb.h('div', 'wb-split-pane');
    el.div.setAttribute('role', 'separator'); el.div.tabIndex = 0; el.append(el.first, el.div, el.second); el.ctx = ctx; el.props = {};
    var self = this;
    el.div.addEventListener('mousedown', function (e) {
      if (e.button !== 0) return; e.preventDefault();
      var vert = el.props.orientation === 'VERTICAL'; var start = vert ? e.clientY : e.clientX; var startPos = self.position(el);
      var move = function (ev) { self.apply(el, startPos + ((vert ? ev.clientY : ev.clientX) - start), true); };
      var up = function () { document.removeEventListener('mousemove', move); document.removeEventListener('mouseup', up); el.classList.remove('wb-dragging'); self.report(el); };
      document.addEventListener('mousemove', move); document.addEventListener('mouseup', up); el.classList.add('wb-dragging');
    });
    el.div.addEventListener('dblclick', function () { if (el.props.collapsible) { el.collapsed = !el.collapsed; self.layout(el); self.report(el); } });
    el.div.addEventListener('keydown', function (e) {
      var vert = el.props.orientation === 'VERTICAL'; var d = 0;
      if (e.key === (vert ? 'ArrowUp' : 'ArrowLeft')) d = -16; else if (e.key === (vert ? 'ArrowDown' : 'ArrowRight')) d = 16; else return;
      e.preventDefault(); self.apply(el, self.position(el) + d, true); self.report(el);
    });
    return el;
  },
  position: function (el) { var vert = el.props.orientation === 'VERTICAL'; return vert ? el.first.offsetHeight : el.first.offsetWidth; },
  apply: function (el, px, user) {
    var vert = el.props.orientation === 'VERTICAL'; var total = vert ? el.offsetHeight : el.offsetWidth; var divider = vert ? el.div.offsetHeight : el.div.offsetWidth;
    var minF = el.props.minFirst == null ? 40 : el.props.minFirst, minS = el.props.minSecond == null ? 40 : el.props.minSecond;
    if (total > 0) px = Math.max(minF, Math.min(total - divider - minS, px));
    el.pos = Math.max(0, Math.round(px)); if (user) el.collapsed = false; this.layout(el);
  },
  layout: function (el) {
    var vert = el.props.orientation === 'VERTICAL';
    el.classList.toggle('wb-vertical', vert); el.classList.toggle('wb-collapsed', !!el.collapsed);
    var size = el.collapsed ? '0px' : (el.pos == null ? '50%' : el.pos + 'px');
    el.first.style.flex = '0 0 ' + size; el.first.style[vert ? 'height' : 'width'] = size; el.first.style[vert ? 'width' : 'height'] = '';
  },
  report: function (el) { el.ctx.emit('change', { position: el.collapsed ? 0 : this.position(el), collapsed: !!el.collapsed }); },
  update: function (el, props, changed) {
    el.props = props;
    if (changed.has('position')) el.pos = props.position == null ? null : props.position;
    if (changed.has('collapsed')) el.collapsed = !!props.collapsed;
    if (changed.has('collapsible')) el.div.classList.toggle('wb-collapsible', !!props.collapsible);
    this.layout(el);
  },
  insertChild: function (el, child, i, ctx, node) { (ctx.node.children.indexOf(node) === 0 ? el.first : el.second).appendChild(child); },
  removeChild: function (el, child) { child.remove(); }
});
