/* wb.ImageViewer — zoom/pan/rotate one image. wb.PdfViewer — the browser's PDF viewer in an iframe. */
(function () {
  wb.register('wb.ImageViewer', {
    create: function (ctx) {
      var el = wb.h('div', 'wb-imgview'); el.ctx = ctx; el.props = {}; el.zoom = null; el.rot = 0; el.pan = { x: 0, y: 0 }; el.tabIndex = 0;
      el.toolbar = wb.h('div', 'wb-imgview-toolbar'); el.stage = wb.h('div', 'wb-imgview-stage'); el.img = wb.h('img', 'wb-imgview-img'); el.img.draggable = false; el.stage.appendChild(el.img); el.info = wb.h('span', 'wb-imgview-info');
      var self = this; var btn = function (t, title, fn) { var b = wb.h('button', 'wb-imgview-btn', t); b.type = 'button'; b.title = title; b.addEventListener('click', fn); el.toolbar.appendChild(b); return b; };
      btn('−', 'Zoom out', function () { self.zoomBy(el, 1 / 1.25); }); btn('+', 'Zoom in', function () { self.zoomBy(el, 1.25); }); btn('▣', 'Fit', function () { self.fit(el); }); btn('1:1', 'Actual size', function () { self.setZoom(el, 1); });
      btn('↻', 'Rotate', function () { el.rot = (el.rot + 90) % 360; self.apply(el); }); el.toolbar.appendChild(el.info);
      el.append(el.toolbar, el.stage);
      el.stage.addEventListener('wheel', function (e) { e.preventDefault(); self.zoomBy(el, e.deltaY < 0 ? 1.1 : 1 / 1.1); }, { passive: false });
      el.stage.addEventListener('mousedown', function (e) { if (e.button !== 0) return; e.preventDefault(); var sx = e.clientX - el.pan.x, sy = e.clientY - el.pan.y; el.stage.classList.add('wb-dragging'); var mv = function (m) { el.pan = { x: m.clientX - sx, y: m.clientY - sy }; self.apply(el); }; var up = function () { document.removeEventListener('mousemove', mv); document.removeEventListener('mouseup', up); el.stage.classList.remove('wb-dragging'); }; document.addEventListener('mousemove', mv); document.addEventListener('mouseup', up); });
      el.stage.addEventListener('dblclick', function () { if (el.zoom == null) self.setZoom(el, 1); else self.fit(el); });
      el.addEventListener('keydown', function (e) { if (e.key === '+' || e.key === '=') self.zoomBy(el, 1.25); else if (e.key === '-') self.zoomBy(el, 1 / 1.25); else if (e.key === '0') self.fit(el); else if (e.key === '1') self.setZoom(el, 1); else if (e.key === 'r') { el.rot = (el.rot + 90) % 360; self.apply(el); } else return; e.preventDefault(); });
      el.img.addEventListener('load', function () { self.apply(el); });
      if (typeof ResizeObserver !== 'undefined') { el.ro = new ResizeObserver(function () { if (el.zoom == null) self.apply(el); }); el.ro.observe(el.stage); }
      return el;
    },
    fitScale: function (el) { var iw = el.img.naturalWidth || 1, ih = el.img.naturalHeight || 1; var rot = el.rot % 180 !== 0; var w = rot ? ih : iw, h = rot ? iw : ih; return Math.min(1, (el.stage.clientWidth - 16) / w, (el.stage.clientHeight - 16) / h) || 1; },
    scale: function (el) { return el.zoom == null ? this.fitScale(el) : el.zoom; },
    apply: function (el) { var s = this.scale(el); el.img.style.transform = 'translate(' + el.pan.x + 'px,' + el.pan.y + 'px) scale(' + s + ') rotate(' + el.rot + 'deg)'; el.info.textContent = el.img.naturalWidth ? el.img.naturalWidth + '×' + el.img.naturalHeight + ' · ' + Math.round(s * 100) + '%' : ''; },
    setZoom: function (el, z) { el.zoom = z == null ? null : Math.max(0.05, Math.min(16, z)); if (z == null) el.pan = { x: 0, y: 0 }; this.apply(el); el.ctx.emit('zoom', { value: el.zoom }); },
    zoomBy: function (el, f) { this.setZoom(el, this.scale(el) * f); },
    fit: function (el) { el.pan = { x: 0, y: 0 }; this.setZoom(el, null); },
    update: function (el, props, changed) {
      el.props = props;
      if (changed.has('source')) { el.img.src = props.source ? wb.res(props.source) : ''; el.pan = { x: 0, y: 0 }; }
      if (changed.has('alt')) el.img.alt = props.alt || '';
      if (changed.has('toolbar')) el.toolbar.classList.toggle('wb-hidden', props.toolbar === false);
      if (changed.has('checkerboard')) el.stage.classList.toggle('wb-imgview-checker', !!props.checkerboard);
      if (changed.has('rotation')) el.rot = props.rotation || 0;
      if (changed.has('zoom')) el.zoom = props.zoom == null ? null : props.zoom;
      this.apply(el);
    },
    call: function (el, m) { if (m === 'fit') this.fit(el); },
    destroy: function (el) { if (el.ro) el.ro.disconnect(); },
    insertChild: function () {}, removeChild: function () {}
  });

  wb.register('wb.PdfViewer', {
    create: function (ctx) {
      var el = wb.h('div', 'wb-pdfview'); el.props = {};
      el.toolbar = wb.h('div', 'wb-pdfview-toolbar'); el.name = wb.h('span', 'wb-pdfview-name'); el.dl = wb.h('a', 'wb-button wb-pdfview-dl', '⤓'); el.dl.title = 'Download'; el.open = wb.h('a', 'wb-button wb-pdfview-open', '↗'); el.open.title = 'Open in a new tab'; el.open.target = '_blank'; el.open.rel = 'noopener';
      el.toolbar.append(el.name, el.dl, el.open); el.frame = wb.h('iframe', 'wb-pdfview-frame'); el.frame.title = 'PDF'; el.empty = wb.h('div', 'wb-pdfview-empty wb-hidden', '—');
      el.append(el.toolbar, el.frame, el.empty); return el;
    },
    update: function (el, props, changed) {
      el.props = props;
      if (changed.has('url') || changed.has('page')) { var u = props.url || ''; var src = u ? u + (props.page ? '#page=' + props.page : '') : 'about:blank'; if (el.frame.src !== src) el.frame.src = src; el.dl.href = u || '#'; el.dl.download = props.fileName || 'document.pdf'; el.open.href = u || '#'; el.empty.classList.toggle('wb-hidden', !!u); el.frame.classList.toggle('wb-hidden', !u); }
      if (changed.has('fileName')) el.name.textContent = props.fileName || '';
      if (changed.has('toolbar')) el.toolbar.classList.toggle('wb-hidden', props.toolbar === false);
    },
    insertChild: function () {}, removeChild: function () {}
  });
})();
