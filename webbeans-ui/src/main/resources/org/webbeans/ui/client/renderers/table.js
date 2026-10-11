/* wb.Table — a virtualised grid: the server sends pages of pre-rendered rows on request (`page {offset, limit}`),
 * the client keeps a row cache keyed by index, draws only the visible window, and owns selection, sort clicks,
 * column widths, the filter box and inline editing. Calls: rows({total, offset, rows, reset}), update([rows]),
 * editError(key, col, message), scrollTo(key), scrollToIndex(i). */
wb.register('wb.Table', {
  create: function (ctx) {
    var el = wb.h('div', 'wb-table'); el.setAttribute('role', 'grid'); el.ctx = ctx; el.props = {}; el.cache = {}; el.total = -1; el.inflight = {};
    el.sel = []; el.anchor = null; el.focusIdx = -1; el.widths = {}; el.editErrors = {};
    el.filterBox = wb.h('div', 'wb-table-filter wb-hidden'); var fi = wb.h('input', 'wb-input'); fi.type = 'search'; el.filterInput = fi; el.filterBox.appendChild(fi);
    fi.addEventListener('input', function () { ctx.emit('filter', { text: fi.value }); });
    el.head = wb.h('div', 'wb-table-head'); el.head.setAttribute('role', 'row');
    el.body = wb.h('div', 'wb-table-body'); el.body.tabIndex = 0; el.spacer = wb.h('div', 'wb-table-spacer'); el.rowsEl = wb.h('div', 'wb-table-rows'); el.body.append(el.spacer, el.rowsEl);
    el.empty = wb.h('div', 'wb-table-empty wb-hidden');
    el.append(el.filterBox, el.head, el.body, el.empty);
    var self = this;
    el.body.addEventListener('scroll', function () { el.head.scrollLeft = el.body.scrollLeft; self.draw(el); });
    el.body.addEventListener('keydown', function (e) { self.key(el, e); });
    el.body.addEventListener('contextmenu', function (e) {
      var row = e.target.closest('.wb-table-row'); if (!row) return; e.preventDefault();
      var idx = Number(row.dataset.idx), k = row.dataset.key;
      if (el.sel.indexOf(k) < 0) { el.sel = [k]; el.anchor = idx; el.focusIdx = idx; self.paint(el); self.report(el); }
      ctx.emit('rowMenu', { key: k, x: e.clientX, y: e.clientY });
    });
    if (typeof ResizeObserver !== 'undefined') { el.ro = new ResizeObserver(function () { self.draw(el); }); el.ro.observe(el.body); }
    return el;
  },
  /* ----- layout ----- */
  visibleColumns: function (el) { return (el.props.columns || []).filter(function (c) { return !c.hidden; }); },
  template: function (el) {
    return this.visibleColumns(el).map(function (c) { var w = el.widths[c.id] || c.width; return w ? w + 'px' : 'minmax(' + (c.minWidth || 80) + 'px, 1fr)'; }).join(' ');
  },
  renderHead: function (el) {
    var self = this; var props = el.props; el.head.innerHTML = ''; var tpl = this.template(el); el.head.style.gridTemplateColumns = tpl; el.tpl = tpl;
    var sort = props.sort || {};
    this.visibleColumns(el).forEach(function (c) {
      var th = wb.h('div', 'wb-th' + (c.align ? ' wb-align-' + c.align : '')); th.setAttribute('role', 'columnheader'); th.dataset.col = c.id;
      th.appendChild(wb.h('span', 'wb-th-title', c.title || ''));
      if (c.sortable !== false) {
        th.classList.add('wb-sortable'); var ind = wb.h('span', 'wb-th-sort', sort.col === c.id ? (sort.asc === false ? '▼' : '▲') : ''); th.appendChild(ind);
        th.addEventListener('click', function (e) { if (e.target.classList.contains('wb-th-resize')) return; var asc = sort.col === c.id ? sort.asc === false : true; if (sort.col === c.id && sort.asc === false) el.ctx.emit('sort', { col: '', asc: true }); else el.ctx.emit('sort', { col: c.id, asc: asc }); });
      }
      if (c.resizable !== false) {
        var grip = wb.h('span', 'wb-th-resize'); th.appendChild(grip);
        grip.addEventListener('mousedown', function (e) {
          e.preventDefault(); e.stopPropagation(); var startX = e.clientX, startW = th.offsetWidth;
          var move = function (ev) { el.widths[c.id] = Math.max(c.minWidth || 40, startW + ev.clientX - startX); var t = self.template(el); el.head.style.gridTemplateColumns = t; el.tpl = t; el.rowsEl.querySelectorAll('.wb-table-row').forEach(function (r) { r.style.gridTemplateColumns = t; }); };
          var up = function () { document.removeEventListener('mousemove', move); document.removeEventListener('mouseup', up); };
          document.addEventListener('mousemove', move); document.addEventListener('mouseup', up);
        });
      }
      el.head.appendChild(th);
    });
  },
  /* ----- data ----- */
  reset: function (el) { el.cache = {}; el.inflight = {}; el.total = -1; el.rowsEl.innerHTML = ''; el.rendered = {}; this.draw(el); },
  request: function (el, from, to) {
    var size = el.props.pageSize || 100; var first = Math.floor(from / size) * size;
    for (var off = first; off <= to; off += size) {
      if (el.total >= 0 && off >= el.total) break;
      if (el.cache[off] !== undefined || el.inflight[off]) continue;
      var need = false; for (var i = off; i < off + size; i++) { if (el.cache[i] === undefined) { need = true; break; } }
      if (!need) continue;
      el.inflight[off] = true; el.ctx.emit('page', { offset: off, limit: size });
    }
  },
  draw: function (el) {
    var rh = el.props.rowHeight || 28; var h = el.body.clientHeight || 300;
    var total = el.total < 0 ? 0 : el.total;
    el.spacer.style.height = (total * rh) + 'px';
    el.empty.classList.toggle('wb-hidden', !(el.total === 0 && el.props.emptyText)); el.empty.textContent = el.props.emptyText || '';
    var first = Math.max(0, Math.floor(el.body.scrollTop / rh) - 5), last = Math.min(Math.max(total - 1, 0), Math.ceil((el.body.scrollTop + h) / rh) + 5);
    if (el.total < 0) { this.request(el, 0, 0); return; }
    this.request(el, first, Math.max(last, first));
    var keep = {}; var self = this; el.rendered = el.rendered || {};
    for (var i = first; i <= last && i < total; i++) {
      var r = el.cache[i]; var node = el.rendered[i];
      if (!node) { node = this.rowEl(el, i, r); el.rendered[i] = node; el.rowsEl.appendChild(node); }
      else if (node.row !== r) { var fresh = this.rowEl(el, i, r); el.rowsEl.replaceChild(fresh, node); el.rendered[i] = fresh; }
      keep[i] = true;
    }
    Object.keys(el.rendered).forEach(function (k) { if (!keep[k]) { el.rendered[k].remove(); delete el.rendered[k]; } });
    this.paint(el);
  },
  rowEl: function (el, idx, r) {
    var rh = el.props.rowHeight || 28; var self = this;
    var row = wb.h('div', 'wb-table-row' + (el.props.striped !== false && idx % 2 ? ' wb-odd' : '') + (r && r.s ? ' ' + r.s : '')); row.setAttribute('role', 'row');
    row.style.top = (idx * rh) + 'px'; row.style.height = rh + 'px'; row.style.gridTemplateColumns = el.tpl || this.template(el); row.dataset.idx = idx; row.row = r;
    if (!r) { row.classList.add('wb-loading'); return row; }
    row.dataset.key = r.k;
    var cols = el.props.columns || []; var vi = 0;
    cols.forEach(function (c, ci) {
      if (c.hidden) return; var v = r.c[ci]; var cell = wb.h('div', 'wb-td' + (c.align ? ' wb-align-' + c.align : '') + (c.style ? ' ' + c.style : '') + (c.editable ? ' wb-editable' : '')); cell.setAttribute('role', 'gridcell'); cell.dataset.col = c.id;
      self.fill(cell, v); row.appendChild(cell); vi++;
      var ek = r.k + '|' + c.id; if (el.editErrors[ek] != null) { cell.classList.add('wb-cell-error'); cell.title = el.editErrors[ek]; }
      if (c.editable) cell.addEventListener('dblclick', function (e) { e.stopPropagation(); self.edit(el, row, cell, c, v); });
    });
    row.addEventListener('mousedown', function (e) { if (e.button !== 0 || el.props.selectionMode === 'NONE') return; el.body.focus(); self.select(el, idx, r.k, e); });
    row.addEventListener('dblclick', function (e) { if (e.target.closest('.wb-editable')) return; el.ctx.emit('activate', { key: r.k }); });
    return row;
  },
  fill: function (cell, v) {
    cell.innerHTML = '';
    if (v && typeof v === 'object') {
      if (v.i) { var img = wb.h('img', 'wb-icon'); img.src = wb.res(v.i); img.alt = ''; cell.appendChild(img); }
      if (v.h) { var sp = wb.h('span', 'wb-text wb-html'); sp.innerHTML = v.h; cell.appendChild(sp); } else cell.appendChild(wb.h('span', 'wb-text', v.t || ''));
      if (v.s) v.s.split(' ').forEach(function (s) { if (s) cell.classList.add(s); }); if (v.tt) cell.title = v.tt;
    } else cell.appendChild(wb.h('span', 'wb-text', v == null ? '' : String(v)));
  },
  /* ----- selection ----- */
  select: function (el, idx, key, e) {
    var multi = el.props.selectionMode === 'MULTI';
    if (multi && (e.ctrlKey || e.metaKey)) { var i = el.sel.indexOf(key); if (i >= 0) el.sel.splice(i, 1); else el.sel.push(key); el.anchor = idx; }
    else if (multi && e.shiftKey && el.anchor != null) { el.sel = []; for (var j = Math.min(el.anchor, idx); j <= Math.max(el.anchor, idx); j++) { var r = el.cache[j]; if (r) el.sel.push(r.k); } }
    else { el.sel = [key]; el.anchor = idx; }
    el.focusIdx = idx; this.paint(el); this.report(el);
  },
  paint: function (el) {
    var rendered = el.rendered || {};
    Object.keys(rendered).forEach(function (i) { var row = rendered[i]; var on = row.dataset.key != null && el.sel.indexOf(row.dataset.key) >= 0; row.classList.toggle('wb-selected', on); row.setAttribute('aria-selected', on ? 'true' : 'false'); row.classList.toggle('wb-focused', Number(i) === el.focusIdx); });
  },
  report: function (el) { el.ctx.emit('select', { keys: el.sel.slice() }); },
  key: function (el, e) {
    if (el.editing) return;
    var total = Math.max(0, el.total); if (!total) return; var cur = el.focusIdx < 0 ? -1 : el.focusIdx; var next = cur;
    var page = Math.max(1, Math.floor((el.body.clientHeight || 300) / (el.props.rowHeight || 28)) - 1);
    if (e.key === 'ArrowDown') next = Math.min(total - 1, cur + 1); else if (e.key === 'ArrowUp') next = Math.max(0, cur - 1);
    else if (e.key === 'PageDown') next = Math.min(total - 1, cur + page); else if (e.key === 'PageUp') next = Math.max(0, cur - page);
    else if (e.key === 'Home') next = 0; else if (e.key === 'End') next = total - 1;
    else if (e.key === 'Enter') { if (cur >= 0 && el.cache[cur]) el.ctx.emit('activate', { key: el.cache[cur].k }); return; }
    else if (e.key === 'F2') { var row = el.rendered && el.rendered[cur]; var cell = row && row.querySelector('.wb-editable'); if (cell) { var c = (el.props.columns || []).filter(function (x) { return x.id === cell.dataset.col; })[0]; this.edit(el, row, cell, c, row.row.c[(el.props.columns || []).indexOf(c)]); } return; }
    else if (e.key === 'a' && (e.ctrlKey || e.metaKey) && el.props.selectionMode === 'MULTI') { e.preventDefault(); el.sel = Object.keys(el.cache).map(function (i) { return el.cache[i].k; }); this.paint(el); this.report(el); return; }
    else return;
    e.preventDefault(); if (next === cur && cur >= 0) return;
    this.scrollToIndex(el, next); var r = el.cache[next];
    if (r && el.props.selectionMode !== 'NONE') { if (e.shiftKey && el.props.selectionMode === 'MULTI' && el.anchor != null) { el.sel = []; for (var j = Math.min(el.anchor, next); j <= Math.max(el.anchor, next); j++) { var rr = el.cache[j]; if (rr) el.sel.push(rr.k); } } else { el.sel = [r.k]; el.anchor = next; } }
    el.focusIdx = next; this.draw(el); if (r) this.report(el);
  },
  scrollToIndex: function (el, idx) {
    var rh = el.props.rowHeight || 28; var top = idx * rh, bottom = top + rh; var h = el.body.clientHeight;
    if (top < el.body.scrollTop) el.body.scrollTop = top; else if (bottom > el.body.scrollTop + h) el.body.scrollTop = bottom - h;
  },
  /* ----- editing ----- */
  edit: function (el, row, cell, c, v) {
    if (el.editing || !c) return; el.editing = true; var self = this;
    var text = v && typeof v === 'object' ? (v.t || '') : (v == null ? '' : String(v));
    var input = wb.h('input', 'wb-table-editor'); input.value = text; cell.innerHTML = ''; cell.appendChild(input); input.focus(); input.select();
    var done = false;
    var finish = function (commit) {
      if (done) return; done = true; el.editing = false;
      if (commit && input.value !== text) el.ctx.emit('edit', { key: row.dataset.key, col: c.id, value: input.value });
      else self.fill(cell, v);
      el.body.focus();
    };
    input.addEventListener('keydown', function (e) { if (e.key === 'Enter') { e.preventDefault(); finish(true); } else if (e.key === 'Escape') { e.preventDefault(); finish(false); } e.stopPropagation(); });
    input.addEventListener('blur', function () { finish(true); });
  },
  /* ----- protocol ----- */
  update: function (el, props, changed) {
    el.props = props;
    if (changed.has('columns') || changed.has('sort')) { this.renderHead(el); if (changed.has('columns')) { el.rendered && Object.keys(el.rendered).forEach(function (k) { el.rendered[k].remove(); }); el.rendered = {}; } }
    if (changed.has('showHeader')) el.head.classList.toggle('wb-hidden', props.showHeader === false);
    if (changed.has('showFilter')) el.filterBox.classList.toggle('wb-hidden', !props.showFilter);
    if (changed.has('filterPlaceholder')) el.filterInput.placeholder = props.filterPlaceholder || '';
    if (changed.has('filter') && el.filterInput.value !== (props.filter || '')) el.filterInput.value = props.filter || '';
    if (changed.has('selected')) { el.sel = (props.selected || []).slice(); }
    if (changed.has('selectionMode')) el.setAttribute('aria-multiselectable', props.selectionMode === 'MULTI' ? 'true' : 'false');
    if (changed.has('emptyText') || changed.has('rowHeight') || changed.has('striped')) { el.rendered && Object.keys(el.rendered).forEach(function (k) { el.rendered[k].remove(); }); el.rendered = {}; }
    this.draw(el);
  },
  call: function (el, m, args) {
    if (m === 'rows') {
      var p = args[0] || {};
      if (p.reset) { this.reset(el); return; }
      el.total = p.total; delete el.inflight[p.offset];
      (p.rows || []).forEach(function (r, i) { el.cache[p.offset + i] = r; });
      // nothing beyond total
      Object.keys(el.cache).forEach(function (i) { if (Number(i) >= el.total) delete el.cache[i]; });
      this.draw(el);
    } else if (m === 'update') {
      var byKey = {}; (args[0] || []).forEach(function (r) { byKey[r.k] = r; });
      Object.keys(el.cache).forEach(function (i) { var r = el.cache[i]; if (byKey[r.k]) el.cache[i] = byKey[r.k]; });
      this.draw(el);
    } else if (m === 'editError') {
      // remembered briefly so the re-render that follows (the row is re-sent) keeps the mark
      var ek = args[0] + '|' + args[1]; el.editErrors[ek] = args[2] || ''; var self = this;
      setTimeout(function () { delete el.editErrors[ek]; var r2 = el.rowsEl.querySelector('.wb-table-row[data-key="' + CSS.escape(args[0]) + '"] .wb-td[data-col="' + CSS.escape(args[1]) + '"]'); if (r2) { r2.classList.remove('wb-cell-error'); r2.removeAttribute('title'); } }, 3000);
      var row = el.rowsEl.querySelector('.wb-table-row[data-key="' + CSS.escape(args[0]) + '"]'); var cell = row && row.querySelector('.wb-td[data-col="' + CSS.escape(args[1]) + '"]');
      if (cell) { cell.classList.add('wb-cell-error'); cell.title = args[2] || ''; }
    } else if (m === 'scrollTo') {
      var idx = Object.keys(el.cache).filter(function (i) { return el.cache[i].k === args[0]; })[0]; if (idx != null) { this.scrollToIndex(el, Number(idx)); el.focusIdx = Number(idx); this.draw(el); }
    } else if (m === 'scrollToIndex') { this.scrollToIndex(el, args[0]); el.focusIdx = args[0]; this.draw(el); }
  },
  destroy: function (el) { if (el.ro) el.ro.disconnect(); },
  insertChild: function () {}, removeChild: function () {}
});
