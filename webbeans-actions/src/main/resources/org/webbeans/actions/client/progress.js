/* wb.ProgressCell — the status bar progress: bar + text of the newest task, count badge, cancel button, task list on hover. */
wb.register('wb.ProgressCell', {
  create: function (ctx) {
    var el = wb.h('div', 'wb-progress-cell wb-hidden');
    el.txt = wb.h('span', 'wb-progress-text'); el.bar = wb.h('span', 'wb-progress-bar'); el.fill = wb.h('span', 'wb-progress-fill'); el.bar.appendChild(el.fill);
    el.cnt = wb.h('span', 'wb-progress-count'); el.cancel = wb.h('button', 'wb-progress-cancel', '×'); el.cancel.type = 'button'; el.cancel.title = 'Cancel';
    el.append(el.txt, el.bar, el.cnt, el.cancel);
    el.cancel.addEventListener('click', function (e) { e.stopPropagation(); ctx.emit('cancel', { index: -1 }); });
    el.list = wb.h('div', 'wb-progress-list wb-hidden'); el.appendChild(el.list);
    el.addEventListener('click', function () { el.list.classList.toggle('wb-hidden'); });
    el.ctx = ctx;
    return el;
  },
  update: function (el, props, changed) {
    if (changed.has('count')) { el.classList.toggle('wb-hidden', !props.count); el.cnt.textContent = props.count > 1 ? '+' + (props.count - 1) : ''; }
    if (changed.has('text')) el.txt.textContent = props.text || '';
    if (changed.has('value')) { var v = props.value; el.bar.classList.toggle('wb-progress-indeterminate', v == null || v < 0); el.fill.style.width = (v == null || v < 0 ? 100 : Math.round(v * 100)) + '%'; }
    if (changed.has('cancellable')) el.cancel.classList.toggle('wb-hidden', !props.cancellable);
    if (changed.has('tasks')) {
      el.list.innerHTML = '';
      (props.tasks || []).forEach(function (t, i) {
        var row = wb.h('div', 'wb-progress-row'); row.appendChild(wb.h('span', 'wb-progress-row-name', t.name + (t.message ? ': ' + t.message : '')));
        var b = wb.h('span', 'wb-progress-bar'); var f = wb.h('span', 'wb-progress-fill'); b.classList.toggle('wb-progress-indeterminate', t.value == null || t.value < 0); f.style.width = (t.value == null || t.value < 0 ? 100 : Math.round(t.value * 100)) + '%'; b.appendChild(f); row.appendChild(b);
        if (t.cancellable) { var c = wb.h('button', 'wb-progress-cancel', '×'); c.type = 'button'; c.addEventListener('click', function (e) { e.stopPropagation(); el.ctx.emit('cancel', { index: i }); }); row.appendChild(c); }
        el.list.appendChild(row);
      });
      if (!(props.tasks || []).length) el.list.classList.add('wb-hidden');
    }
  },
  insertChild: function () {}, removeChild: function () {}
});
