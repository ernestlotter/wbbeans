/* wb.Panel — layouts: fill, border, flow, grid, form, stack. Child constraints arrive with the child node. */
wb.register('wb.Panel', {
  create: function () { return wb.h('div', 'wb-panel'); },
  update: function (el, props, changed, ctx) {
    if (changed.has('layout')) { el.dataset.layout = (props.layout && props.layout.t) || 'fill'; this.relayout(el, ctx.node); }
    if (changed.has('padding')) el.style.padding = (props.padding || 0) + 'px';
    if (changed.has('border') || changed.has('title')) {
      el.classList.toggle('wb-border-line', props.border === 'LINE');
      el.classList.toggle('wb-border-titled', props.border === 'TITLED');
      var t = el.querySelector(':scope > .wb-panel-title');
      if (props.border === 'TITLED') { if (!t) { t = wb.h('div', 'wb-panel-title'); el.prepend(t); } t.textContent = props.title || ''; } else if (t) t.remove();
    }
    if (changed.has('scrollable')) el.classList.toggle('wb-scroll', !!props.scrollable);
    if (changed.has('busy') || changed.has('busyText')) {
      var ov = el.querySelector(':scope > .wb-busy-overlay');
      if (props.busy) { if (!ov) { ov = wb.h('div', 'wb-busy-overlay wb-panel-keep'); ov.appendChild(wb.h('div', 'wb-busy-spinner')); ov.appendChild(wb.h('div', 'wb-busy-text')); el.appendChild(ov); } ov.querySelector('.wb-busy-text').textContent = props.busyText || ''; el.classList.add('wb-is-busy'); }
      else if (ov) { ov.remove(); el.classList.remove('wb-is-busy'); }
    }
    if (changed.has('selected')) this.selectStack(el, ctx.node, props.selected);
  },
  insertChild: function (el, child, i, ctx, node) { this.relayout(el, ctx.node); },
  removeChild: function (el, child, ctx, node) { var w = child.parentElement; child.remove(); if (w && w !== el && w.classList.contains('wb-cell')) w.remove(); this.relayout(el, ctx.node); },
  relayout: function (el, node) {
    var L = node.props.layout || { t: 'fill' };
    var kids = node.children;
    // detach children (keep DOM nodes), rebuild wrappers
    var keep = Array.from(el.children).filter(function (c) { return c.classList.contains('wb-panel-keep'); });   // header / overlay elements owned by the renderer
    Array.from(el.children).forEach(function (c) { if (!c.classList.contains('wb-panel-title') && !c.classList.contains('wb-panel-keep')) c.remove(); });
    kids.forEach(function (k) { k.el.classList.remove('wb-fill'); });
    if (L.t === 'border') {
      var slots = { north: null, south: null, east: null, west: null, center: null };
      kids.forEach(function (k) { var c = typeof k.constraint === 'string' ? k.constraint : 'center'; if (!(c in slots)) c = 'center'; if (slots[c]) slots[c] = [].concat(slots[c], k); else slots[c] = k; });
      var mk = function (name) { var w = wb.h('div', 'wb-cell wb-' + name); [].concat(slots[name] || []).forEach(function (k) { w.appendChild(k.el); }); return w; };
      var mid = wb.h('div', 'wb-cell wb-middle'); mid.append(mk('west'), mk('center'), mk('east'));
      el.append(mk('north'), mid, mk('south'));
    } else if (L.t === 'flow') {
      el.classList.toggle('wb-flow-v', L.o === 'v'); el.style.gap = (L.gap || 0) + 'px'; el.dataset.align = L.align || 'start'; el.classList.toggle('wb-wrap', !!L.wrap);
      kids.forEach(function (k) { el.appendChild(k.el); });
    } else if (L.t === 'grid') {
      el.style.gridTemplateColumns = L.widths ? L.widths.map(function (w) { return w === 'fill' ? 'minmax(0, 1fr)' : w; }).join(' ') : 'repeat(' + (L.cols || 1) + ', minmax(0, 1fr))';
      el.style.gridTemplateRows = L.heights ? L.heights.map(function (h) { return h === 'fill' ? 'minmax(0, 1fr)' : h; }).join(' ') : '';
      el.style.alignContent = L.heights ? 'stretch' : '';
      el.style.gap = (L.vgap || 0) + 'px ' + (L.hgap || 0) + 'px';
      kids.forEach(function (k) { var c = k.constraint; if (c && typeof c === 'object' && c.row != null) { k.el.style.gridRow = (c.row + 1) + ' / span ' + (c.rs || 1); k.el.style.gridColumn = (c.col + 1) + ' / span ' + (c.cs || 1); } el.appendChild(k.el); });
    } else if (L.t === 'form') {
      el.style.setProperty('--wb-label-w', L.labelWidth || '160px');
      kids.forEach(function (k) {
        var c = k.constraint || {};
        if (c.section != null) { var s = wb.h('div', 'wb-form-section', c.section); el.appendChild(s); el.appendChild(k.el); k.el.classList.add('wb-form-full'); return; }
        var row = wb.h('div', 'wb-cell wb-form-row'); var lab = wb.h('label', 'wb-form-label', c.label || ''); var fld = wb.h('div', 'wb-form-field');
        fld.appendChild(k.el); if (c.hint) fld.appendChild(wb.h('div', 'wb-form-hint', c.hint)); row.append(lab, fld); el.appendChild(row);
        var input = k.el.matches('input,select,textarea') ? k.el : k.el.querySelector('input,select,textarea'); if (input) { if (!input.id) input.id = 'wb-f-' + k.id; lab.htmlFor = input.id; }
      });
    } else if (L.t === 'stack') {
      kids.forEach(function (k) { el.appendChild(k.el); });
      this.selectStack(el, node, node.props.selected);
    } else {
      kids.forEach(function (k) { k.el.classList.add('wb-fill'); el.appendChild(k.el); });
    }
    keep.forEach(function (c) { if (c.classList.contains('wb-panel-head')) el.prepend(c); else el.appendChild(c); });
  },
  selectStack: function (el, node, selected) {
    var sel = wb.ref(selected) || node.children[0];
    node.children.forEach(function (k) { k.el.classList.toggle('wb-stack-hidden', k !== sel); });
  }
});
