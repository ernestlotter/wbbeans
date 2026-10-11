/* wb.Calendar — month and week views over keyed entries. Navigation is client-side (reported as `nav` and
 * `range`); entries are a property; moving an entry by drag and drop is only reported (`move`). */
(function () {
  var pad = function (n) { return (n < 10 ? '0' : '') + n; };
  var iso = function (d) { return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()); };
  var parse = function (s) { var p = s.split('-'); return new Date(Number(p[0]), Number(p[1]) - 1, Number(p[2])); };
  var addDays = function (d, n) { var x = new Date(d.getFullYear(), d.getMonth(), d.getDate() + n); return x; };
  var fmt = function (opts) { try { return new Intl.DateTimeFormat(wb.locale || undefined, opts); } catch (e) { return new Intl.DateTimeFormat(undefined, opts); } };
  var weekNo = function (d) { var t = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate())); var day = t.getUTCDay() || 7; t.setUTCDate(t.getUTCDate() + 4 - day); var y0 = new Date(Date.UTC(t.getUTCFullYear(), 0, 1)); return Math.ceil(((t - y0) / 864e5 + 1) / 7); };
  var mins = function (t) { var p = t.split(':'); return Number(p[0]) * 60 + Number(p[1] || 0); };

  wb.register('wb.Calendar', {
    create: function (ctx) {
      var el = wb.h('div', 'wb-cal'); el.ctx = ctx; el.props = {}; el.tabIndex = 0; el.setAttribute('role', 'grid');
      el.toolbar = wb.h('div', 'wb-cal-toolbar'); el.body = wb.h('div', 'wb-cal-body'); el.append(el.toolbar, el.body);
      var self = this; var today = new Date(); el.date = new Date(today.getFullYear(), today.getMonth(), today.getDate()); el.view = 'MONTH'; el.selected = null;
      var btn = function (cls, text, title, fn) { var b = wb.h('button', 'wb-btn wb-cal-btn ' + cls, text); b.type = 'button'; b.title = title; b.addEventListener('click', fn); return b; };
      el.titleEl = wb.h('div', 'wb-cal-title');
      el.toolbar.append(btn('wb-cal-prev', '‹', 'Previous', function () { self.go(el, -1); }), btn('wb-cal-today', '●', 'Today', function () { var t = new Date(); el.date = new Date(t.getFullYear(), t.getMonth(), t.getDate()); self.nav(el); }),
        btn('wb-cal-next', '›', 'Next', function () { self.go(el, 1); }), el.titleEl, wb.h('span', 'wb-cal-spacer'),
        btn('wb-cal-view-month', 'M', 'Month', function () { el.view = 'MONTH'; self.nav(el); }), btn('wb-cal-view-week', 'W', 'Week', function () { el.view = 'WEEK'; self.nav(el); }));
      el.addEventListener('keydown', function (e) {
        var sel = el.selected || el.date; var n = null;
        if (e.key === 'ArrowLeft') n = addDays(sel, -1); else if (e.key === 'ArrowRight') n = addDays(sel, 1); else if (e.key === 'ArrowUp') n = addDays(sel, -7); else if (e.key === 'ArrowDown') n = addDays(sel, 7);
        else if (e.key === 'PageUp') { self.go(el, -1); e.preventDefault(); return; } else if (e.key === 'PageDown') { self.go(el, 1); e.preventDefault(); return; }
        else if (e.key === 'Enter' && el.selected) { ctx.emit('dayActivate', { date: iso(el.selected) }); return; } else return;
        e.preventDefault(); el.selected = n; if (!self.visible(el, n)) { el.date = n; self.nav(el); } else { self.render(el); ctx.emit('nav', self.state(el)); }
      });
      el.body.addEventListener('click', function (e) {
        var chip = e.target.closest('.wb-cal-entry'); var day = e.target.closest('[data-date]'); if (!day) return;
        el.selected = parse(day.dataset.date); self.paintSelection(el); ctx.emit('nav', self.state(el));
        if (chip) ctx.emit('entry', { key: chip.dataset.key, date: day.dataset.date }); else ctx.emit('day', { date: day.dataset.date });
      });
      el.body.addEventListener('dblclick', function (e) {
        var chip = e.target.closest('.wb-cal-entry'); var day = e.target.closest('[data-date]'); if (!day) return;
        if (chip) ctx.emit('entryActivate', { key: chip.dataset.key, date: day.dataset.date }); else ctx.emit('dayActivate', { date: day.dataset.date });
      });
      el.body.addEventListener('dragstart', function (e) { var chip = e.target.closest('.wb-cal-entry'); if (!chip || chip.dataset.ro) { e.preventDefault(); return; } e.dataTransfer.setData('text/plain', chip.dataset.key); e.dataTransfer.effectAllowed = 'move'; el.dragKey = chip.dataset.key; chip.classList.add('wb-cal-dragging'); });
      el.body.addEventListener('dragend', function () { el.dragKey = null; el.body.querySelectorAll('.wb-cal-dragging, .wb-cal-over').forEach(function (n) { n.classList.remove('wb-cal-dragging', 'wb-cal-over'); }); });
      el.body.addEventListener('dragover', function (e) { var day = e.target.closest('[data-date]'); if (day && el.dragKey) { e.preventDefault(); e.dataTransfer.dropEffect = 'move'; el.body.querySelectorAll('.wb-cal-over').forEach(function (n) { n.classList.remove('wb-cal-over'); }); day.classList.add('wb-cal-over'); } });
      el.body.addEventListener('drop', function (e) { var day = e.target.closest('[data-date]'); if (day && el.dragKey) { e.preventDefault(); ctx.emit('move', { key: el.dragKey, date: day.dataset.date }); } el.dragKey = null; });
      return el;
    },
    state: function (el) { return { date: iso(el.date), view: el.view, selected: el.selected ? iso(el.selected) : null }; },
    range: function (el) {
      var first = Number(el.props.firstDay || 1) % 7; // 1=Mon..7=Sun → JS 0=Sun
      if (el.view === 'WEEK') { var d = el.date; var off = (d.getDay() - first + 7) % 7; var s = addDays(d, -off); return { from: s, to: addDays(s, 6), rows: 1 }; }
      var m0 = new Date(el.date.getFullYear(), el.date.getMonth(), 1); var off0 = (m0.getDay() - first + 7) % 7; var from = addDays(m0, -off0);
      var m1 = new Date(el.date.getFullYear(), el.date.getMonth() + 1, 0); var off1 = (first + 6 - m1.getDay() + 7) % 7; var to = addDays(m1, off1);
      return { from: from, to: to, rows: Math.round((to - from) / 864e5 + 1) / 7 };
    },
    visible: function (el, d) { var r = this.range(el); return d >= r.from && d <= r.to; },
    go: function (el, n) { if (el.view === 'WEEK') el.date = addDays(el.date, 7 * n); else el.date = new Date(el.date.getFullYear(), el.date.getMonth() + n, 1); this.nav(el); },
    nav: function (el) { this.render(el); el.ctx.emit('nav', this.state(el)); if (el.ctx.listens('range')) { var r = this.range(el); el.ctx.emit('range', { from: iso(r.from), to: iso(r.to) }); } },
    entriesOn: function (el, dayIso) {
      return (el.props.entries || []).filter(function (e) { return e.s <= dayIso && (e.e || e.s) >= dayIso; })
        .sort(function (a, b) { return (a.st ? 1 : 0) - (b.st ? 1 : 0) || (a.st || '').localeCompare(b.st || '') || a.t.localeCompare(b.t); });
    },
    chip: function (el, e, dayIso, timed) {
      var c = wb.h('div', 'wb-cal-entry' + (e.st ? ' wb-cal-timed' : ' wb-cal-allday') + (e.s < dayIso ? ' wb-cal-cont' : '') + ((e.e || e.s) > dayIso ? ' wb-cal-more' : ''));
      c.dataset.key = e.k; if (e.ro) c.dataset.ro = '1'; c.draggable = !e.ro && el.props.draggable !== false; if (e.c) c.style.setProperty('--wb-cal-color', e.c);
      if (e.i) { var img = wb.h('img', 'wb-icon'); img.src = wb.res(e.i); img.alt = ''; c.appendChild(img); }
      if (e.st && !timed) c.appendChild(wb.h('span', 'wb-cal-time', e.st.slice(0, 5)));
      c.appendChild(wb.h('span', 'wb-text', e.t)); c.title = e.tt || e.t;
      return c;
    },
    render: function (el) {
      var self = this; var r = this.range(el); var todayIso = iso(new Date()); var days = fmt({ weekday: 'short' });
      el.titleEl.textContent = el.view === 'WEEK' ? fmt({ month: 'long', year: 'numeric' }).format(r.from) + (r.from.getMonth() !== r.to.getMonth() ? ' / ' + fmt({ month: 'long', year: 'numeric' }).format(r.to) : '') + (el.props.weekNumbers ? ' — W' + weekNo(r.from) : '')
        : fmt({ month: 'long', year: 'numeric' }).format(el.date);
      el.toolbar.classList.toggle('wb-hidden', el.props.toolbar === false);
      el.querySelector('.wb-cal-view-month').classList.toggle('wb-active', el.view === 'MONTH'); el.querySelector('.wb-cal-view-week').classList.toggle('wb-active', el.view === 'WEEK');
      el.classList.toggle('wb-cal-weekview', el.view === 'WEEK'); el.body.innerHTML = '';
      var head = wb.h('div', 'wb-cal-head' + (el.props.weekNumbers ? ' wb-cal-wn' : '')); if (el.props.weekNumbers) head.appendChild(wb.h('div', 'wb-cal-hd wb-cal-wncell', ''));
      for (var i = 0; i < 7; i++) { var d = addDays(r.from, i); head.appendChild(wb.h('div', 'wb-cal-hd', el.view === 'WEEK' ? fmt({ weekday: 'short', day: 'numeric' }).format(d) : days.format(d))); }
      el.body.appendChild(head);
      if (el.view === 'WEEK') { this.renderWeek(el, r); return; }
      var month = el.date.getMonth();
      for (var w = 0; w < r.rows; w++) {
        var row = wb.h('div', 'wb-cal-week' + (el.props.weekNumbers ? ' wb-cal-wn' : '')); if (el.props.weekNumbers) row.appendChild(wb.h('div', 'wb-cal-wncell', String(weekNo(addDays(r.from, w * 7)))));
        for (var k = 0; k < 7; k++) {
          var day = addDays(r.from, w * 7 + k); var di = iso(day);
          var cell = wb.h('div', 'wb-cal-day' + (day.getMonth() !== month ? ' wb-cal-other' : '') + (di === todayIso ? ' wb-cal-today-cell' : '') + (day.getDay() === 0 || day.getDay() === 6 ? ' wb-cal-weekend' : '')); cell.dataset.date = di; cell.setAttribute('role', 'gridcell');
          cell.appendChild(wb.h('div', 'wb-cal-num', String(day.getDate())));
          var list = self.entriesOn(el, di); var box = wb.h('div', 'wb-cal-entries'); list.forEach(function (e) { box.appendChild(self.chip(el, e, di, false)); }); cell.appendChild(box);
          row.appendChild(cell);
        }
        el.body.appendChild(row);
      }
      this.paintSelection(el);
    },
    renderWeek: function (el, r) {
      var self = this; var h0 = el.props.dayStart == null ? 7 : el.props.dayStart; var h1 = el.props.dayEnd == null ? 20 : el.props.dayEnd; var todayIso = iso(new Date());
      var allday = wb.h('div', 'wb-cal-allday-row' + (el.props.weekNumbers ? ' wb-cal-wn' : '')); if (el.props.weekNumbers) allday.appendChild(wb.h('div', 'wb-cal-wncell', ''));
      var grid = wb.h('div', 'wb-cal-grid' + (el.props.weekNumbers ? ' wb-cal-wn' : '')); var hours = wb.h('div', 'wb-cal-hours'); for (var h = h0; h < h1; h++) hours.appendChild(wb.h('div', 'wb-cal-hour', pad(h) + ':00')); grid.appendChild(hours);
      var ph = 100 / Math.max(1, h1 - h0);
      for (var i = 0; i < 7; i++) {
        var day = addDays(r.from, i); var di = iso(day); var list = self.entriesOn(el, di);
        var ad = wb.h('div', 'wb-cal-day wb-cal-allday-cell' + (di === todayIso ? ' wb-cal-today-cell' : '')); ad.dataset.date = di;
        var col = wb.h('div', 'wb-cal-col' + (di === todayIso ? ' wb-cal-today-cell' : '') + (day.getDay() === 0 || day.getDay() === 6 ? ' wb-cal-weekend' : '')); col.dataset.date = di;
        for (var h = h0; h < h1; h++) col.appendChild(wb.h('div', 'wb-cal-slot'));
        list.forEach(function (e) {
          if (!e.st) { ad.appendChild(self.chip(el, e, di, false)); return; }
          var top = (mins(e.st) - h0 * 60) / 60 * ph; var end = e.et ? mins(e.et) : mins(e.st) + 60; var height = Math.max(ph / 4, (end - mins(e.st)) / 60 * ph);
          var c = self.chip(el, e, di, true); c.style.top = Math.max(0, top) + '%'; c.style.height = Math.min(100 - Math.max(0, top), height) + '%'; c.insertBefore(wb.h('span', 'wb-cal-time', e.st.slice(0, 5) + (e.et ? '–' + e.et.slice(0, 5) : '')), c.firstChild); col.appendChild(c);
        });
        allday.appendChild(ad); grid.appendChild(col);
      }
      el.body.append(allday, grid); this.paintSelection(el);
    },
    paintSelection: function (el) { var s = el.selected ? iso(el.selected) : null; el.body.querySelectorAll('[data-date]').forEach(function (n) { n.classList.toggle('wb-cal-selected', n.dataset.date === s); }); },
    update: function (el, props, changed) {
      el.props = props;
      if (changed.has('date') && props.date) el.date = parse(props.date);
      if (changed.has('view') && props.view) el.view = props.view;
      if (changed.has('selected')) el.selected = props.selected ? parse(props.selected) : null;
      this.render(el);
    },
    insertChild: function () {}, removeChild: function () {}
  });
})();
