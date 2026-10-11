/* wb.Kanban — columns of draggable cards. A drop is reported as `drop` {key, column, index}; the server applies it
 * and re-sends the cards (the client moves the card optimistically meanwhile). */
wb.register('wb.Kanban', {
  create: function (ctx) {
    var el = wb.h('div', 'wb-kanban'); el.ctx = ctx; el.props = {}; el.selected = null; el.collapsed = new Set(); el.tabIndex = 0; var self = this;
    el.addEventListener('click', function (e) {
      var add = e.target.closest('.wb-kb-add'); if (add) { ctx.emit('add', { column: add.closest('.wb-kb-col').dataset.col }); return; }
      var head = e.target.closest('.wb-kb-head'); if (head) { var col = head.closest('.wb-kb-col'); if (el.collapsed.has(col.dataset.col)) el.collapsed.delete(col.dataset.col); else el.collapsed.add(col.dataset.col); col.classList.toggle('wb-kb-collapsed'); return; }
      var card = e.target.closest('.wb-kb-card'); if (!card) return;
      self.select(el, card.dataset.key); ctx.emit('card', { key: card.dataset.key });
    });
    el.addEventListener('dblclick', function (e) { var card = e.target.closest('.wb-kb-card'); if (card) ctx.emit('cardActivate', { key: card.dataset.key }); });
    el.addEventListener('contextmenu', function (e) { var card = e.target.closest('.wb-kb-card'); if (!card) return; e.preventDefault(); self.select(el, card.dataset.key); ctx.emit('cardMenu', { key: card.dataset.key, x: e.clientX, y: e.clientY }); });
    el.addEventListener('keydown', function (e) {
      var cur = el.selected ? el.querySelector('.wb-kb-card[data-key="' + CSS.escape(el.selected) + '"]') : null; var next = null;
      if (e.key === 'ArrowDown') next = cur ? cur.nextElementSibling : el.querySelector('.wb-kb-card'); else if (e.key === 'ArrowUp') next = cur ? cur.previousElementSibling : null;
      else if (e.key === 'ArrowRight' || e.key === 'ArrowLeft') { var col = cur ? cur.closest('.wb-kb-col') : null; var sib = col ? (e.key === 'ArrowRight' ? col.nextElementSibling : col.previousElementSibling) : null; next = sib ? sib.querySelector('.wb-kb-card') : null; }
      else if (e.key === 'Enter' && cur) { ctx.emit('cardActivate', { key: el.selected }); return; } else return;
      e.preventDefault(); if (next && next.classList.contains('wb-kb-card')) { self.select(el, next.dataset.key); next.scrollIntoView({ block: 'nearest' }); ctx.emit('card', { key: next.dataset.key }); }
    });
    el.addEventListener('dragstart', function (e) { var card = e.target.closest('.wb-kb-card'); if (!card || card.dataset.ro || el.props.draggable === false) { e.preventDefault(); return; } el.dragKey = card.dataset.key; e.dataTransfer.effectAllowed = 'move'; e.dataTransfer.setData('text/plain', card.dataset.key); setTimeout(function () { card.classList.add('wb-kb-dragging'); }, 0); });
    el.addEventListener('dragend', function () { el.dragKey = null; el.querySelectorAll('.wb-kb-dragging').forEach(function (n) { n.classList.remove('wb-kb-dragging'); }); el.querySelectorAll('.wb-kb-over').forEach(function (n) { n.classList.remove('wb-kb-over'); }); });
    el.addEventListener('dragover', function (e) {
      var col = e.target.closest('.wb-kb-col'); if (!col || !el.dragKey) return; e.preventDefault(); e.dataTransfer.dropEffect = 'move';
      var body = col.querySelector('.wb-kb-cards'); var dragging = el.querySelector('.wb-kb-card[data-key="' + CSS.escape(el.dragKey) + '"]'); if (!dragging) return;
      el.querySelectorAll('.wb-kb-over').forEach(function (n) { n.classList.remove('wb-kb-over'); }); col.classList.add('wb-kb-over');
      var after = null; Array.prototype.forEach.call(body.querySelectorAll('.wb-kb-card:not(.wb-kb-dragging)'), function (c) { var r = c.getBoundingClientRect(); if (e.clientY < r.top + r.height / 2 && !after) after = c; });
      if (after) body.insertBefore(dragging, after); else body.appendChild(dragging);
    });
    el.addEventListener('drop', function (e) {
      var col = e.target.closest('.wb-kb-col'); if (!col || !el.dragKey) return; e.preventDefault();
      var cards = Array.prototype.slice.call(col.querySelectorAll('.wb-kb-card')); var idx = cards.findIndex(function (c) { return c.dataset.key === el.dragKey; });
      ctx.emit('drop', { key: el.dragKey, column: col.dataset.col, index: idx < 0 ? cards.length : idx }); el.dragKey = null; self.counts(el);
    });
    return el;
  },
  select: function (el, key) { el.selected = key; el.querySelectorAll('.wb-kb-card').forEach(function (c) { c.classList.toggle('wb-selected', c.dataset.key === key); }); el.ctx.emit('select', { key: key }); },
  counts: function (el) { el.querySelectorAll('.wb-kb-col').forEach(function (col) { var n = col.querySelectorAll('.wb-kb-card').length; var c = col.querySelector('.wb-kb-count'); var lim = col.dataset.limit; c.textContent = lim ? n + ' / ' + lim : String(n); col.classList.toggle('wb-kb-over-limit', !!lim && n > Number(lim)); }); },
  card: function (el, c) {
    var d = wb.h('div', 'wb-kb-card' + (c.k === el.selected ? ' wb-selected' : '')); d.dataset.key = c.k; if (c.ro) d.dataset.ro = '1'; d.draggable = !c.ro && el.props.draggable !== false; if (c.c) d.style.setProperty('--wb-kb-color', c.c); if (c.tt) d.title = c.tt;
    var t = wb.h('div', 'wb-kb-title'); if (c.i) { var img = wb.h('img', 'wb-icon'); img.src = wb.res(c.i); img.alt = ''; t.appendChild(img); } t.appendChild(wb.h('span', 'wb-text', c.t)); d.appendChild(t);
    if (c.s) d.appendChild(wb.h('div', 'wb-kb-sub', c.s));
    if (c.tags && c.tags.length) { var tags = wb.h('div', 'wb-kb-tags'); c.tags.forEach(function (x) { tags.appendChild(wb.h('span', 'wb-kb-tag', x)); }); d.appendChild(tags); }
    return d;
  },
  render: function (el) {
    var self = this; el.innerHTML = ''; var cards = el.props.cards || [];
    (el.props.columns || []).forEach(function (c) {
      var col = wb.h('div', 'wb-kb-col' + ((c.collapsed && !el.collapsed.has('!' + c.id)) || el.collapsed.has(c.id) ? ' wb-kb-collapsed' : '')); col.dataset.col = c.id; if (c.limit != null) col.dataset.limit = c.limit; if (c.c) col.style.setProperty('--wb-kb-color', c.c);
      var head = wb.h('div', 'wb-kb-head'); head.append(wb.h('span', 'wb-kb-coltitle', c.t), wb.h('span', 'wb-kb-count', '')); col.appendChild(head);
      var body = wb.h('div', 'wb-kb-cards'); cards.filter(function (x) { return x.col === c.id; }).forEach(function (x) { body.appendChild(self.card(el, x)); }); col.appendChild(body);
      if (c.add) { var add = wb.h('button', 'wb-kb-add', '+'); add.type = 'button'; add.title = 'Add'; col.appendChild(add); }
      el.appendChild(col);
    });
    this.counts(el);
  },
  update: function (el, props, changed) {
    el.props = props;
    if (changed.has('selected')) el.selected = props.selected || null;
    if (changed.has('columns') || changed.has('cards') || changed.has('draggable')) this.render(el);
    else if (changed.has('selected')) el.querySelectorAll('.wb-kb-card').forEach(function (c) { c.classList.toggle('wb-selected', c.dataset.key === el.selected); });
  },
  insertChild: function () {}, removeChild: function () {}
});
