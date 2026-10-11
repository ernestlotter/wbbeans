/* wb.DiffView — renders a server-computed edit script side by side or inline. Folded unchanged runs ({op:'~'})
 * expand on click. Highlighting reuses the CodeEditor tokenizers when that renderer is loaded. */
(function () {
  var esc = function (s) { return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); };
  var hl = function (t, lang) { return wb.highlightCode && lang && lang !== 'PLAIN' ? wb.highlightCode(t, lang.toLowerCase()) : esc(t); };
  var cls = { '=': 'wb-diff-eq', '-': 'wb-diff-del', '+': 'wb-diff-add' };

  wb.register('wb.DiffView', {
    create: function (ctx) {
      var el = wb.h('div', 'wb-diff'); el.ctx = ctx; el.props = {};
      el.head = wb.h('div', 'wb-diff-head'); el.body = wb.h('div', 'wb-diff-body'); el.append(el.head, el.body);
      var self = this;
      el.body.addEventListener('click', function (e) {
        var fold = e.target.closest('.wb-diff-fold'); if (fold) { self.expand(el, fold); return; }
        var cell = e.target.closest('.wb-diff-line'); if (cell && ctx.listens('line')) ctx.emit('line', { side: cell.dataset.side, line: Number(cell.dataset.line), op: cell.dataset.op });
      });
      return el;
    },
    cell: function (el, side, n, op, text, lang) {
      return '<div class="wb-diff-line ' + cls[op] + '" data-side="' + side + '" data-op="' + op + '"' + (n ? ' data-line="' + n + '"' : '') + '>'
        + '<span class="wb-diff-n">' + (n || '') + '</span><span class="wb-diff-m">' + (op === '=' ? ' ' : op) + '</span><span class="wb-diff-t">' + (text === '' ? '&nbsp;' : hl(text, lang)) + '</span></div>';
    },
    render: function (el) {
      var props = el.props; var ops = props.ops || []; var lang = props.language; var side = props.layout !== 'INLINE'; var self = this;
      el.classList.toggle('wb-diff-inline', !side);
      el.head.innerHTML = props.leftTitle || props.rightTitle ? '<div class="wb-diff-title">' + esc(props.leftTitle || '') + '</div>' + (side ? '<div class="wb-diff-title">' + esc(props.rightTitle || '') + '</div>' : '') : '';
      el.head.classList.toggle('wb-hidden', !el.head.innerHTML);
      var L = '', R = '', I = '';
      var fold = function (f, idx) { var n = f.lines.length; return '<div class="wb-diff-fold" data-idx="' + idx + '">… ' + n + ' unchanged line' + (n === 1 ? '' : 's') + '</div>'; };
      // side by side: pair deletions with the insertions that follow them
      var i = 0;
      while (i < ops.length) {
        var op = ops[i];
        if (op.op === '~') { var f = fold(op, i); L += f; R += f; I += f; i++; continue; }
        if (op.op === '=') { L += self.cell(el, 'left', op.a, '=', op.t, lang); R += self.cell(el, 'right', op.b, '=', op.t, lang); I += self.cell(el, 'both', op.a, '=', op.t, lang); i++; continue; }
        var dels = [], adds = [];
        while (i < ops.length && ops[i].op === '-') dels.push(ops[i++]);
        while (i < ops.length && ops[i].op === '+') adds.push(ops[i++]);
        dels.forEach(function (d) { I += self.cell(el, 'left', d.a, '-', d.t, lang); });
        adds.forEach(function (a) { I += self.cell(el, 'right', a.b, '+', a.t, lang); });
        var n = Math.max(dels.length, adds.length);
        for (var k = 0; k < n; k++) {
          L += k < dels.length ? self.cell(el, 'left', dels[k].a, '-', dels[k].t, lang) : '<div class="wb-diff-line wb-diff-pad"></div>';
          R += k < adds.length ? self.cell(el, 'right', adds[k].b, '+', adds[k].t, lang) : '<div class="wb-diff-line wb-diff-pad"></div>';
        }
      }
      el.body.innerHTML = side ? '<div class="wb-diff-col">' + L + '</div><div class="wb-diff-col">' + R + '</div>' : '<div class="wb-diff-col wb-diff-single">' + I + '</div>';
      if (!ops.length) el.body.innerHTML = '<div class="wb-diff-empty">No differences</div>';
    },
    expand: function (el, foldEl) {
      var idx = Number(foldEl.dataset.idx); var f = el.props.ops[idx]; var lang = el.props.language; var self = this;
      el.body.querySelectorAll('.wb-diff-fold[data-idx="' + idx + '"]').forEach(function (node) {
        var col = node.parentNode; var side = col.classList.contains('wb-diff-single') ? 'both' : col === el.body.firstElementChild ? 'left' : 'right';
        var html = f.lines.map(function (l) { return self.cell(el, side, side === 'right' ? l.b : l.a, '=', l.t, lang); }).join('');
        var tmp = document.createElement('div'); tmp.innerHTML = html; while (tmp.firstChild) col.insertBefore(tmp.firstChild, node); col.removeChild(node);
      });
    },
    update: function (el, props, changed) {
      el.props = props;
      if (changed.has('wrap')) el.classList.toggle('wb-diff-wrap', !!props.wrap);
      if (changed.has('lineNumbers')) el.classList.toggle('wb-diff-nonum', props.lineNumbers === false);
      if (changed.has('ops') || changed.has('layout') || changed.has('language') || changed.has('leftTitle') || changed.has('rightTitle')) this.render(el);
    },
    insertChild: function () {}, removeChild: function () {}
  });
})();
