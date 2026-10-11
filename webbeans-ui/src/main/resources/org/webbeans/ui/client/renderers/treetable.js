/* wb.TreeTable — the Table renderer plus indentation and expanders in the first visible column. Rows carry
 * d (depth), x (expandable), e (expanded), p (parent key); toggling asks the server, which re-flattens. */
(function () {
  var impl = {
    create: function (ctx) {
      if (!impl.__linked) { Object.setPrototypeOf(impl, wb.renderers['wb.Table']); impl.__linked = true; }   // the bundle order is not fixed
      var el = Object.getPrototypeOf(impl).create.call(this, ctx); el.classList.add('wb-treetable'); return el;
    },
    rowEl: function (el, idx, r) {
      var row = Object.getPrototypeOf(impl).rowEl.call(this, el, idx, r);
      if (!r) return row;
      var first = row.querySelector('.wb-td'); if (!first) return row;
      var ind = wb.h('span', 'wb-tt-indent'); ind.style.width = ((r.d || 0) * 18) + 'px';
      var tg = wb.h('span', 'wb-tt-toggle' + (r.x ? (r.e ? ' wb-tt-open' : ' wb-tt-closed') : ' wb-tt-leaf')); tg.setAttribute('aria-hidden', 'true');
      if (r.x) tg.addEventListener('mousedown', function (e) { e.stopPropagation(); e.preventDefault(); el.ctx.emit('toggle', { key: r.k }); });
      first.insertBefore(tg, first.firstChild); first.insertBefore(ind, first.firstChild);
      row.setAttribute('aria-level', (r.d || 0) + 1); if (r.x) row.setAttribute('aria-expanded', r.e ? 'true' : 'false');
      if (r.x) row.addEventListener('dblclick', function (e) { if (e.target.closest('.wb-editable')) return; el.ctx.emit('toggle', { key: r.k }); });
      return row;
    },
    key: function (el, e) {
      var cur = el.focusIdx; var r = cur >= 0 ? el.cache[cur] : null;
      if (r && e.key === 'ArrowRight') { e.preventDefault(); if (r.x && !r.e) el.ctx.emit('toggle', { key: r.k }); else if (r.x && r.e) { Object.getPrototypeOf(impl).key.call(this, el, { key: 'ArrowDown', preventDefault: function () {} }); } return; }
      if (r && e.key === 'ArrowLeft') {
        e.preventDefault();
        if (r.x && r.e) el.ctx.emit('toggle', { key: r.k });
        else if (r.p != null) { var pi = Object.keys(el.cache).filter(function (i) { return el.cache[i].k === r.p; })[0]; if (pi != null) { this.scrollToIndex(el, Number(pi)); el.sel = [r.p]; el.focusIdx = Number(pi); el.anchor = el.focusIdx; this.paint(el); this.report(el); } }
        return;
      }
      return Object.getPrototypeOf(impl).key.call(this, el, e);
    }
  };
  wb.register('wb.TreeTable', impl);
})();
