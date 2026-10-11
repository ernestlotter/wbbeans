/* wb.QuickSearch — a search box over the main window; results come from the server per keystroke. */
wb.register('wb.QuickSearch', {
  create: function (ctx) {
    var el = wb.h('div', 'wb-qs-wrap wb-hidden'); el.ctx = ctx; el.props = {}; el.cur = -1;
    el.box = wb.h('div', 'wb-qs'); el.box.setAttribute('role', 'dialog'); el.input = wb.h('input', 'wb-input wb-qs-input'); el.input.type = 'search'; el.input.autocomplete = 'off'; el.input.spellcheck = false;
    el.list = wb.h('div', 'wb-qs-list'); el.list.setAttribute('role', 'listbox'); el.box.append(el.input, el.list); el.appendChild(el.box);
    var self = this;
    el.addEventListener('mousedown', function (e) { if (e.target === el) { e.preventDefault(); self.close(el); } });
    el.input.addEventListener('input', function () { ctx.emit('query', { text: el.input.value }); });
    el.input.addEventListener('keydown', function (e) {
      var items = el.list.querySelectorAll('.wb-qs-item');
      if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); self.close(el); }
      else if (e.key === 'ArrowDown') { e.preventDefault(); self.move(el, items, 1); } else if (e.key === 'ArrowUp') { e.preventDefault(); self.move(el, items, -1); }
      else if (e.key === 'Enter') { e.preventDefault(); var it = items[el.cur] || items[0]; if (it) self.run(el, it); }
      else if (e.key === 'Tab') { e.preventDefault(); self.move(el, items, e.shiftKey ? -1 : 1); }
      e.stopPropagation();
    });
    el.list.addEventListener('mousedown', function (e) { e.preventDefault(); var it = e.target.closest('.wb-qs-item'); if (it) self.run(el, it); });
    el.list.addEventListener('mousemove', function (e) { var it = e.target.closest('.wb-qs-item'); if (it) { var items = Array.from(el.list.querySelectorAll('.wb-qs-item')); self.hilite(el, items, items.indexOf(it)); } });
    return el;
  },
  move: function (el, items, d) { if (!items.length) return; this.hilite(el, items, (el.cur + d + items.length) % items.length); },
  hilite: function (el, items, i) { el.cur = i; items.forEach(function (it, k) { it.classList.toggle('wb-qs-cur', k === i); it.setAttribute('aria-selected', k === i ? 'true' : 'false'); }); if (items[i]) items[i].scrollIntoView({ block: 'nearest' }); },
  run: function (el, it) { el.ctx.emit('run', { index: Number(it.dataset.i) }); this.hide(el); },
  close: function (el) { this.hide(el); el.ctx.emit('close', {}); },
  hide: function (el) { el.classList.add('wb-hidden'); el.input.value = ''; el.list.innerHTML = ''; el.cur = -1; el.input.blur(); },
  show: function (el) { el.classList.remove('wb-hidden'); el.input.value = ''; el.list.innerHTML = ''; el.cur = -1; setTimeout(function () { el.input.focus(); }, 0); },
  render: function (el) {
    var self = this; el.list.innerHTML = ''; var groups = el.props.results || []; var n = 0;
    groups.forEach(function (g) {
      el.list.appendChild(wb.h('div', 'wb-qs-cat', g.cat));
      (g.items || []).forEach(function (r) {
        var it = wb.h('div', 'wb-qs-item'); it.dataset.i = r.i; it.setAttribute('role', 'option');
        var ic = wb.h('span', 'wb-icon'); if (r.icon) { var img = wb.h('img'); img.src = wb.res(r.icon); img.alt = ''; ic.appendChild(img); } it.appendChild(ic);
        var t = wb.h('div', 'wb-qs-text'); t.appendChild(wb.h('div', 'wb-qs-title', r.t)); if (r.d) t.appendChild(wb.h('div', 'wb-qs-detail', r.d)); it.appendChild(t); el.list.appendChild(it); n++;
      });
    });
    if (!n && el.input.value.trim()) el.list.appendChild(wb.h('div', 'wb-qs-empty', el.props.noResults || ''));
    el.list.classList.toggle('wb-hidden', !el.list.childElementCount); el.cur = -1; var items = el.list.querySelectorAll('.wb-qs-item'); if (items.length) self.hilite(el, items, 0);
  },
  update: function (el, props, changed) {
    el.props = props;
    if (changed.has('placeholder')) el.input.placeholder = props.placeholder || '';
    if (changed.has('results')) this.render(el);
    if (changed.has('open')) { if (props.open && el.classList.contains('wb-hidden')) this.show(el); else if (!props.open) el.classList.add('wb-hidden'); }
  },
  call: function (el, m) { if (m === 'open') this.show(el); },
  insertChild: function () {}, removeChild: function () {}
});
