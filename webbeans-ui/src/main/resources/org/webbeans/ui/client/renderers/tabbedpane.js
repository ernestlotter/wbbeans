wb.register('wb.TabbedPane', {
  create: function (ctx) { var el = wb.h('div', 'wb-tabs'); el.strip = wb.h('div', 'wb-tabstrip'); el.strip.setAttribute('role', 'tablist'); el.body = wb.h('div', 'wb-tabbody'); el.append(el.strip, el.body); return el; },
  update: function (el, props, changed, ctx) { if (changed.has('selected')) this.render(el, ctx.node); },
  insertChild: function (el, child, i, ctx, node) { el.body.appendChild(child); this.render(el, ctx.node); },
  removeChild: function (el, child, ctx, node) { child.remove(); this.render(el, ctx.node); },
  render: function (el, node) {
    var sel = Math.min(node.props.selected || 0, node.children.length - 1); var self = this;
    el.strip.innerHTML = '';
    node.children.forEach(function (k, i) {
      var c = k.constraint || {}; var tab = wb.h('div', 'wb-tab' + (i === sel ? ' wb-selected' : '')); tab.setAttribute('role', 'tab'); tab.tabIndex = 0;
      if (c.icon) { var img = wb.h('img'); img.src = wb.res(c.icon); img.alt = ''; tab.appendChild(img); }
      tab.appendChild(wb.h('span', 'wb-text', c.title || ''));
      if (c.closable) { var x = wb.h('button', 'wb-tab-close', '×'); x.type = 'button'; x.title = 'Close'; x.addEventListener('click', function (e) { e.stopPropagation(); node.ctx.emit('tabClose', { index: i }); }); tab.appendChild(x); }
      var pick = function () { node.props.selected = i; self.render(el, node); node.ctx.emit('select', { index: i }); };
      tab.addEventListener('click', pick); tab.addEventListener('keydown', function (e) { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); pick(); } });
      el.strip.appendChild(tab);
      k.el.classList.toggle('wb-stack-hidden', i !== sel);
    });
  }
});
