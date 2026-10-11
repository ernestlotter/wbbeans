/* wb.OutlineView — the tree engine plus property columns: header with sorting and resizable columns, editable cells. */
(function () {
  var engine = function () { return wb.nodeView; };
  function cmp(a, b) { var na = parseFloat(a), nb = parseFloat(b); if (!isNaN(na) && !isNaN(nb) && /^\s*-?[\d.,]+/.test(a) && /^\s*-?[\d.,]+/.test(b)) return na - nb; return String(a).localeCompare(String(b), undefined, { numeric: true, sensitivity: 'base' }); }
  function cellText(row, col) { if (col === '__tree') return row.dn || row.name || ''; var c = row.cols && row.cols[col]; return c ? (c.text || '') : ''; }
  function startEdit(el, row, col, cell) {
    var c = row.cols && row.cols[col]; if (!c || !c.edit || el.editing) return;
    var input;
    if (c.edit === 'CHOICE' && c.choices) { input = wb.h('select', 'wb-nv-cell-editor'); c.choices.forEach(function (ch) { var o = wb.h('option', '', ch.label); o.value = ch.value; if (ch.value === c.text) o.selected = true; input.appendChild(o); }); }
    else if (c.edit === 'BOOLEAN') { input = wb.h('select', 'wb-nv-cell-editor'); ['true', 'false'].forEach(function (v) { var o = wb.h('option', '', v); o.value = v; if (v === String(c.text)) o.selected = true; input.appendChild(o); }); }
    else { input = wb.h('input', 'wb-nv-cell-editor'); input.type = c.edit === 'NUMBER' ? 'text' : 'text'; input.value = c.text || ''; }
    cell.innerHTML = ''; cell.appendChild(input); input.focus(); if (input.select) input.select(); el.editing = true;
    var done = function (commit) { if (!el.editing) return; el.editing = false; var v = input.value; engine().render(el); if (commit && v !== c.text) el.ctx.emit('editCell', { n: row.n, prop: col, text: v }); el.list.focus({ preventScroll: true }); };
    input.addEventListener('keydown', function (e) { e.stopPropagation(); if (e.key === 'Enter') done(true); else if (e.key === 'Escape') done(false); });
    input.addEventListener('blur', function () { done(true); });
    input.addEventListener('click', function (e) { e.stopPropagation(); });
    if (input.tagName === 'SELECT') input.addEventListener('change', function () { done(true); });
  }
  wb.register('wb.OutlineView', {
    create: function (ctx) {
      var el = wb.h('div', 'wb-nv wb-outlineview'); el.props = {}; el.sort = null; el.widths = {};
      el.header = wb.h('div', 'wb-ov-header'); el.appendChild(el.header);
      engine().init(el, ctx, {
        hideRoot: false,
        cells: function (el, row, r) {
          (el.props.columns || []).forEach(function (col) {
            var cell = wb.h('div', 'wb-ov-cell'); cell.dataset.col = col.name; cell.style.width = (el.widths[col.name] || col.width || 90) + 'px';
            var c = row.cols && row.cols[col.name]; cell.textContent = '';
            if (c && c.cell) { var d = c.cell; if (d.i) { var img = wb.h('img', 'wb-icon'); img.src = wb.res(d.i); img.alt = ''; cell.appendChild(img); }
              if (d.h) { var sp = wb.h('span', 'wb-text wb-html'); sp.innerHTML = d.h; cell.appendChild(sp); } else cell.appendChild(wb.h('span', 'wb-text', d.t != null ? d.t : (c.text || '')));
              if (d.s) d.s.split(' ').forEach(function (k) { if (k) cell.classList.add(k); }); if (d.tt) cell.title = d.tt; }
            else cell.textContent = c ? (c.text || '') : '';
            if (c && c.edit) { cell.classList.add('wb-ov-editable'); cell.addEventListener('dblclick', function (e) { e.stopPropagation(); startEdit(el, row, col.name, cell); }); }
            r.appendChild(cell);
          });
        },
        sortChildren: function (el, ids) {
          if (!el.sort) return ids;
          var s = el.sort; return ids.slice().sort(function (a, b) { var ra = el.rows.get(a), rb = el.rows.get(b); var d = cmp(cellText(ra, s.col), cellText(rb, s.col)); return s.dir === 'desc' ? -d : d; });
        },
        editError: function (el, err) { var r = el.list.querySelector('.wb-nv-row[data-n="' + err.n + '"] .wb-ov-cell[data-col="' + CSS.escape(err.prop) + '"]'); if (r) { r.classList.add('wb-ov-error'); r.title = err.message; setTimeout(function () { r.classList.remove('wb-ov-error'); r.title = ''; }, 2500); } }
      });
      return el;
    },
    update: function (el, props, changed) {
      engine().update(el, props, changed);
      if (changed.has('columns') || changed.has('treeColumnLabel') || changed.has('showHeader') || changed.has('sortable')) this.renderHeader(el);
    },
    renderHeader: function (el) {
      var h = el.header; h.innerHTML = ''; h.classList.toggle('wb-hidden', el.props.showHeader === false);
      var mk = function (name, label, width) {
        var c = wb.h('div', 'wb-ov-head'); c.dataset.col = name; c.textContent = label || ''; if (width) c.style.width = width + 'px';
        if (el.props.sortable !== false) c.addEventListener('click', function () { el.sort = el.sort && el.sort.col === name ? (el.sort.dir === 'asc' ? { col: name, dir: 'desc' } : null) : { col: name, dir: 'asc' }; wb.nodeView.render(el); wb.renderers['wb.OutlineView'].renderHeader(el); });
        if (el.sort && el.sort.col === name) c.classList.add(el.sort.dir === 'asc' ? 'wb-ov-asc' : 'wb-ov-desc');
        if (name !== '__tree') { var g = wb.h('div', 'wb-ov-resize'); g.addEventListener('mousedown', function (e) { e.preventDefault(); e.stopPropagation(); var sx = e.clientX, w0 = c.offsetWidth;
          var mv = function (ev) { var w = Math.max(40, w0 + ev.clientX - sx); el.widths[name] = w; c.style.width = w + 'px'; el.list.querySelectorAll('.wb-ov-cell[data-col="' + CSS.escape(name) + '"]').forEach(function (x) { x.style.width = w + 'px'; }); };
          var up = function () { document.removeEventListener('mousemove', mv); document.removeEventListener('mouseup', up); }; document.addEventListener('mousemove', mv); document.addEventListener('mouseup', up); }); c.appendChild(g); }
        return c;
      };
      var tree = mk('__tree', el.props.treeColumnLabel, null); tree.classList.add('wb-ov-head-tree'); h.appendChild(tree);
      (el.props.columns || []).forEach(function (col) { h.appendChild(mk(col.name, col.label, el.widths[col.name] || col.width || 140)); });
    },
    call: function (el, m, args) { engine().call(el, m, args); },
    insertChild: function () {}, removeChild: function () {}
  });
})();
