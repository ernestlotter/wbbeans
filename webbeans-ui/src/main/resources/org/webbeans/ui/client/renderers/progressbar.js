wb.register('wb.ProgressBar', {
  create: function () { var el = wb.h('div', 'wb-progress'); el.track = wb.h('div', 'wb-progress-track'); el.bar = wb.h('div', 'wb-progress-bar'); el.txt = wb.h('div', 'wb-progress-text'); el.track.appendChild(el.bar); el.append(el.track, el.txt); el.setAttribute('role', 'progressbar'); return el; },
  update: function (el, props, changed) {
    if (changed.has('value') || changed.has('indeterminate')) { el.classList.toggle('wb-indeterminate', !!props.indeterminate); el.bar.style.width = props.indeterminate ? '' : (Math.round((props.value || 0) * 100) + '%'); el.setAttribute('aria-valuenow', Math.round((props.value || 0) * 100)); }
    if (changed.has('text')) el.txt.textContent = props.text || '';
  },
  insertChild: function () {}, removeChild: function () {}
});
