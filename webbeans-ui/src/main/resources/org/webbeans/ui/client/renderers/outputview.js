/* wb.OutputView — an append-only console. Lines arrive through `append` calls in batches; the DOM keeps at most
 * maxLines of them. URLs become links; ANSI SGR codes become spans; follow-tail is switched off when the user
 * scrolls up and on again at the bottom (reported to the server as `follow`). */
(function () {
  var esc = function (s) { return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); };
  var URL = /(https?:\/\/[^\s<>"']+[^\s<>"'.,;:)\]])/g;
  var ANSI_COLORS = ['black', 'red', 'green', 'yellow', 'blue', 'magenta', 'cyan', 'white'];

  function ansi(text) { // SGR only; everything else is dropped
    var out = ''; var open = 0; var parts = text.split(/\x1b\[([0-9;]*)m/);
    for (var i = 0; i < parts.length; i++) {
      if (i % 2 === 0) { out += linkify(parts[i]); continue; }
      var codes = parts[i] === '' ? ['0'] : parts[i].split(';'); var cls = [];
      codes.forEach(function (c) {
        var n = parseInt(c, 10);
        if (n === 0) { while (open > 0) { out += '</span>'; open--; } }
        else if (n === 1) cls.push('wb-ansi-b'); else if (n === 3) cls.push('wb-ansi-i'); else if (n === 4) cls.push('wb-ansi-u');
        else if (n >= 30 && n <= 37) cls.push('wb-ansi-' + ANSI_COLORS[n - 30]); else if (n >= 90 && n <= 97) cls.push('wb-ansi-' + ANSI_COLORS[n - 90] + ' wb-ansi-bright');
        else if (n >= 40 && n <= 47) cls.push('wb-ansi-bg-' + ANSI_COLORS[n - 40]);
      });
      if (cls.length) { out += '<span class="' + cls.join(' ') + '">'; open++; }
    }
    while (open > 0) { out += '</span>'; open--; }
    return out;
  }
  function linkify(text) { return esc(text).replace(URL, '<a href="$1" target="_blank" rel="noopener">$1</a>'); }
  function stamp(ts) { var d = new Date(ts); var p = function (n) { return (n < 10 ? '0' : '') + n; }; return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds()); }

  wb.register('wb.OutputView', {
    create: function (ctx) {
      var el = wb.h('div', 'wb-output'); el.ctx = ctx; el.props = {}; el.count = 0;
      el.lines = wb.h('div', 'wb-output-lines'); el.appendChild(el.lines);
      el.follow = true; var self = this;
      el.addEventListener('scroll', function () {
        var atEnd = el.scrollTop + el.clientHeight >= el.scrollHeight - 4;
        if (atEnd !== el.follow) { el.follow = atEnd; el.classList.toggle('wb-output-paused', !atEnd); ctx.emit('follow', { value: atEnd }); }
      });
      el.lines.addEventListener('click', function (e) {
        var ln = e.target.closest('.wb-output-line'); if (!ln) return;
        var a = e.target.closest('a');
        if (a) { if (ctx.listens('link')) { e.preventDefault(); ctx.emit('link', { link: a.getAttribute('href'), url: true, line: Number(ln.dataset.n) }); } return; }
        if (ln.dataset.link !== undefined) ctx.emit('link', { link: ln.dataset.link, line: Number(ln.dataset.n) });
      });
      var btn = wb.h('button', 'wb-output-resume'); btn.type = 'button'; btn.textContent = '↓'; btn.title = 'Follow output';
      btn.addEventListener('click', function () { self.scrollToEnd(el); });
      el.appendChild(btn);
      return el;
    },
    line: function (el, l) {
      var d = wb.h('div', 'wb-output-line' + (l.s ? ' wb-output-' + l.s : '') + (l.link !== undefined ? ' wb-output-link' : ''));
      d.dataset.n = ++el.count; if (l.link !== undefined) d.dataset.link = l.link;
      var html = el.props.ansi === false ? linkify(l.t) : ansi(l.t);
      d.innerHTML = (l.ts ? '<span class="wb-output-ts">' + stamp(l.ts) + '</span>' : '') + '<span class="wb-output-text">' + (html || '&nbsp;') + '</span>';
      return d;
    },
    append: function (el, list) {
      var frag = document.createDocumentFragment(); var self = this;
      list.forEach(function (l) { frag.appendChild(self.line(el, l)); });
      el.lines.appendChild(frag);
      var max = el.props.maxLines || 10000;
      while (el.lines.childElementCount > max) el.lines.removeChild(el.lines.firstElementChild);
      if (el.follow) el.scrollTop = el.scrollHeight;
    },
    scrollToEnd: function (el) { el.scrollTop = el.scrollHeight; if (!el.follow) { el.follow = true; el.classList.remove('wb-output-paused'); el.ctx.emit('follow', { value: true }); } },
    update: function (el, props, changed) {
      el.props = props;
      if (changed.has('wrap')) el.classList.toggle('wb-output-nowrap', props.wrap === false);
      if (changed.has('timestamps')) el.classList.toggle('wb-output-stamped', !!props.timestamps);
      if (changed.has('follow') && props.follow !== undefined && props.follow !== el.follow) { el.follow = !!props.follow; el.classList.toggle('wb-output-paused', !el.follow); if (el.follow) el.scrollTop = el.scrollHeight; }
    },
    call: function (el, m, args) {
      if (m === 'append') this.append(el, args[0] || []);
      else if (m === 'set') { el.lines.innerHTML = ''; el.count = 0; this.append(el, args[0] || []); }
      else if (m === 'clear') { el.lines.innerHTML = ''; el.count = 0; }
      else if (m === 'scrollToEnd') this.scrollToEnd(el);
    },
    insertChild: function () {}, removeChild: function () {}
  });
})();
