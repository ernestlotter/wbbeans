/* wb.StatusBar — message on the left, cells (children) on the right. */
wb.register('wb.StatusBar', {
  create: function () { var el = wb.h('div', 'wb-statusbar'); el.msg = wb.h('div', 'wb-status-text'); el.cells = wb.h('div', 'wb-status-cells'); el.append(el.msg, el.cells); el.setAttribute('role', 'status'); return el; },
  update: function (el, props, changed) { if (changed.has('text')) el.msg.textContent = props.text || ''; },
  insertChild: function (el, child, i) { var ref = el.cells.children[i]; if (ref) el.cells.insertBefore(child, ref); else el.cells.appendChild(child); },
  removeChild: function (el, child) { child.remove(); }
});
