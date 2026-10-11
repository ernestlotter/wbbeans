/* wb.ToolbarPanel / wb.Toolbar / wb.ToolbarButton */
wb.register('wb.ToolbarPanel', {
  create: function () { return wb.h('div', 'wb-toolbarpanel'); },
  update: function () {},
  insertChild: function (el, child, i) { var ref = el.children[i]; if (ref) el.insertBefore(child, ref); else el.appendChild(child); },
  removeChild: function (el, child) { child.remove(); }
});
wb.register('wb.Toolbar', {
  create: function () { var el = wb.h('div', 'wb-toolbar'); el.setAttribute('role', 'toolbar'); el.grip = wb.h('span', 'wb-toolbar-grip'); el.appendChild(el.grip); return el; },
  update: function (el, props, changed) { if (changed.has('name')) { el.dataset.name = props.name || ''; el.setAttribute('aria-label', props.name || ''); } },
  insertChild: function (el, child, i) { var ref = el.children[i + 1]; if (ref) el.insertBefore(child, ref); else el.appendChild(child); },
  removeChild: function (el, child) { child.remove(); }
});
wb.register('wb.ToolbarButton', {
  create: function (ctx) {
    var el = wb.h('button', 'wb-tbbutton'); el.type = 'button';
    el.ico = wb.h('span', 'wb-icon'); el.txt = wb.h('span', 'wb-text'); el.append(el.ico, el.txt);
    el.addEventListener('click', function () { if (!el.disabled) ctx.emit('action', {}); });
    return el;
  },
  update: function (el, props, changed) {
    if (changed.has('text')) { el.txt.textContent = props.text || ''; el.classList.toggle('wb-icon-only', !props.text); }
    if (changed.has('icon')) { el.ico.innerHTML = ''; if (props.icon) { var im = wb.h('img'); im.src = wb.res(props.icon); im.alt = ''; el.ico.appendChild(im); } el.ico.classList.toggle('wb-hidden', !props.icon); }
    if (changed.has('selected')) { el.classList.toggle('wb-selected', props.selected === true); if (props.selected == null) el.removeAttribute('aria-pressed'); else el.setAttribute('aria-pressed', props.selected ? 'true' : 'false'); }
  },
  call: function (el, m) { if (m === 'click') el.click(); },
  insertChild: function () {}, removeChild: function () {}
});
