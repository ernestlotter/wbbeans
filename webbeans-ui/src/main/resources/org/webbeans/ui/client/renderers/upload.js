/* wb.Upload — a file button + drop zone; each file is POSTed raw to /wb/upload with the session token. */
wb.register('wb.Upload', {
  create: function (ctx) {
    var el = wb.h('div', 'wb-upload'); el.ctx = ctx; el.props = {};
    var input = wb.h('input'); input.type = 'file'; input.className = 'wb-upload-input'; input.id = 'wb-up-' + ctx.id; el.input = input;
    var btn = wb.h('button', 'wb-button'); btn.type = 'button'; el.btn = btn; btn.addEventListener('click', function () { input.click(); });
    el.hint = wb.h('span', 'wb-upload-hint'); el.zone = wb.h('div', 'wb-upload-zone'); el.zone.append(btn, el.hint, input);
    el.list = wb.h('div', 'wb-upload-list'); el.append(el.zone, el.list);
    var self = this;
    input.addEventListener('change', function () { self.send(el, Array.prototype.slice.call(input.files)); input.value = ''; });
    ['dragenter', 'dragover'].forEach(function (t) { el.zone.addEventListener(t, function (e) { if (el.props.dropZone === false || el.props.enabled === false) return; e.preventDefault(); el.zone.classList.add('wb-dragover'); }); });
    ['dragleave', 'drop'].forEach(function (t) { el.zone.addEventListener(t, function (e) { el.zone.classList.remove('wb-dragover'); }); });
    el.zone.addEventListener('drop', function (e) { if (el.props.dropZone === false || el.props.enabled === false) return; e.preventDefault(); var files = Array.prototype.slice.call(e.dataTransfer.files); self.send(el, el.props.multiple ? files : files.slice(0, 1)); });
    return el;
  },
  send: function (el, files) {
    if (!files.length) return; var ctx = el.ctx; var self = this; var pending = files.length;
    var finishOne = function () { if (--pending === 0) ctx.emit('finished', {}); };
    files.forEach(function (f) {
      var row = self.row(el, f.name, f.size);
      if (el.props.maxSize && f.size > el.props.maxSize) { row.fail('too-large'); ctx.emit('failed', { name: f.name, reason: 'too-large' }); finishOne(); return; }
      var xhr = new XMLHttpRequest();
      var url = (wb.cfg.base || '') + '/wb/upload?ui=' + encodeURIComponent(wb.cfg.uiId) + '&c=' + ctx.node.id + '&name=' + encodeURIComponent(f.name) + '&size=' + f.size;
      xhr.open('POST', url, true); xhr.setRequestHeader('X-WB-Token', wb.cfg.token || ''); xhr.setRequestHeader('Content-Type', f.type || 'application/octet-stream');
      xhr.upload.addEventListener('progress', function (e) { if (e.lengthComputable) { row.progress(e.loaded, e.total); ctx.emit('progress', { name: f.name, loaded: e.loaded, total: e.total }); } });
      xhr.addEventListener('load', function () { if (xhr.status === 200) row.done(); else { var reason = xhr.status === 413 ? 'too-large' : xhr.status === 410 || xhr.status === 401 ? 'session' : String(xhr.status); row.fail(reason); ctx.emit('failed', { name: f.name, reason: reason }); } finishOne(); });
      xhr.addEventListener('error', function () { row.fail('network'); ctx.emit('failed', { name: f.name, reason: 'network' }); finishOne(); });
      xhr.send(f);
    });
  },
  row: function (el, name, size) {
    var row = wb.h('div', 'wb-upload-item'); var label = wb.h('span', 'wb-text', name + ' (' + this.size(size) + ')'); var bar = wb.h('div', 'wb-progress-track'); var fill = wb.h('div', 'wb-progress-bar'); bar.appendChild(fill);
    var state = wb.h('span', 'wb-upload-state'); row.append(label, bar, state); el.list.appendChild(row);
    row.progress = function (l, t) { fill.style.width = Math.round(l * 100 / t) + '%'; };
    row.done = function () { fill.style.width = '100%'; row.classList.add('wb-done'); state.textContent = '✓'; };
    row.fail = function (r) { row.classList.add('wb-failed'); state.textContent = r; };
    if (el.props.showList === false) row.classList.add('wb-hidden');
    return row;
  },
  size: function (n) { return n < 1024 ? n + ' B' : n < 1048576 ? (n / 1024).toFixed(1) + ' KB' : (n / 1048576).toFixed(1) + ' MB'; },
  update: function (el, props, changed) {
    el.props = props;
    if (changed.has('accept')) { if (props.accept) el.input.accept = props.accept; else el.input.removeAttribute('accept'); }
    if (changed.has('multiple')) el.input.multiple = !!props.multiple;
    if (changed.has('text')) el.btn.textContent = props.text || '';
    if (changed.has('hint') || changed.has('dropZone')) { var dz = props.dropZone !== false; el.hint.textContent = dz && props.hint ? props.hint : ''; el.zone.classList.toggle('wb-upload-dropzone', dz); }
    if (changed.has('enabled')) { el.btn.disabled = props.enabled === false; el.classList.toggle('wb-disabled', props.enabled === false); }
    if (changed.has('showList')) el.list.classList.toggle('wb-hidden', props.showList === false);
  },
  call: function (el, m) { if (m === 'open') el.input.click(); else if (m === 'clear') el.list.innerHTML = ''; },
  insertChild: function () {}, removeChild: function () {}
});
