/* wb.MarkdownEditor — a toolbar over a textarea with a preview pane. The preview HTML comes from the server
 * (same Markdown renderer as the help pages), so the client only wraps selections in Markdown syntax. */
(function () {
  var tools = [
    ['h', 'H', 'Heading', function (t) { return t.line('## '); }],
    ['b', 'B', 'Bold (Ctrl+B)', function (t) { return t.wrap('**', '**', 'bold'); }],
    ['i', 'I', 'Italic (Ctrl+I)', function (t) { return t.wrap('_', '_', 'italic'); }],
    ['code', '<>', 'Code', function (t) { return t.sel().indexOf('\n') >= 0 ? t.wrap('```\n', '\n```', 'code') : t.wrap('`', '`', 'code'); }],
    ['link', '\u{1F517}', 'Link', function (t) { return t.wrap('[', '](https://)', 'text'); }],
    ['ul', '•', 'Bullet list', function (t) { return t.line('- '); }],
    ['ol', '1.', 'Numbered list', function (t) { return t.line('1. '); }],
    ['quote', '“', 'Quote', function (t) { return t.line('> '); }]
  ];

  wb.register('wb.MarkdownEditor', {
    create: function (ctx) {
      var el = wb.h('div', 'wb-md'); el.ctx = ctx; el.props = {};
      el.toolbar = wb.h('div', 'wb-md-toolbar'); el.body = wb.h('div', 'wb-md-body');
      el.ta = wb.h('textarea', 'wb-md-input'); el.ta.spellcheck = true;
      el.preview = wb.h('div', 'wb-html wb-md-preview');
      el.body.append(el.ta, el.preview); el.append(el.toolbar, el.body);
      var self = this; var committed = '';
      var ops = {
        sel: function () { return el.ta.value.slice(el.ta.selectionStart, el.ta.selectionEnd); },
        wrap: function (pre, post, placeholder) {
          var ta = el.ta; var s = ta.selectionStart, e = ta.selectionEnd; var txt = ta.value.slice(s, e) || placeholder;
          ta.setRangeText(pre + txt + post, s, e, 'end'); ta.setSelectionRange(s + pre.length, s + pre.length + txt.length);
        },
        line: function (prefix) { // toggle a prefix on every selected line
          var ta = el.ta; var s = ta.selectionStart, e = ta.selectionEnd; var ls = ta.value.lastIndexOf('\n', s - 1) + 1; var le = ta.value.indexOf('\n', e); if (le < 0) le = ta.value.length;
          var lines = ta.value.slice(ls, le).split('\n'); var all = lines.every(function (l) { return l.indexOf(prefix) === 0; });
          var out = lines.map(function (l) { return all ? l.slice(prefix.length) : prefix + l; }).join('\n');
          ta.setRangeText(out, ls, le, 'select');
        }
      };
      tools.forEach(function (t) {
        var b = wb.h('button', 'wb-md-tool wb-md-tool-' + t[0]); b.type = 'button'; b.textContent = t[1]; b.title = t[2]; b.tabIndex = -1;
        b.addEventListener('mousedown', function (e) { e.preventDefault(); });
        b.addEventListener('click', function () { if (el.ta.disabled || el.ta.readOnly) return; el.ta.focus(); t[3](ops); self.changed(el); });
        el.toolbar.appendChild(b);
      });
      var sep = wb.h('span', 'wb-md-spacer'); el.toolbar.appendChild(sep);
      el.previewBtn = wb.h('button', 'wb-md-tool wb-md-tool-preview'); el.previewBtn.type = 'button'; el.previewBtn.title = 'Preview'; el.previewBtn.textContent = '◧'; el.previewBtn.tabIndex = -1;
      el.previewBtn.addEventListener('click', function () { var m = el.mode === 'OFF' ? 'SPLIT' : el.mode === 'SPLIT' ? 'ONLY' : 'OFF'; self.mode(el, m); });
      el.toolbar.appendChild(el.previewBtn);
      el.ta.addEventListener('input', function () { ctx.emit('input', { value: el.ta.value }); });
      el.ta.addEventListener('change', function () { if (el.ta.value !== committed) { committed = el.ta.value; ctx.emit('change', { value: el.ta.value }); } });
      el.ta.addEventListener('blur', function () { if (el.ta.value !== committed) { committed = el.ta.value; ctx.emit('change', { value: el.ta.value }); } ctx.emit('blur', {}); });
      el.ta.addEventListener('focus', function () { ctx.emit('focus', {}); });
      el.ta.addEventListener('keydown', function (e) {
        if ((e.ctrlKey || e.metaKey) && !e.altKey && (e.key === 'b' || e.key === 'i')) { e.preventDefault(); (e.key === 'b' ? tools[1] : tools[2])[3](ops); self.changed(el); }
        else if (e.key === 'Enter' && !e.ctrlKey && !e.metaKey) { // continue lists
          var ta = el.ta; var st = ta.selectionStart; var ls = ta.value.lastIndexOf('\n', st - 1) + 1; var line = ta.value.slice(ls, st); var m = /^(\s*)([-*+]|\d+\.)\s+(.*)$/.exec(line);
          if (m) { e.preventDefault(); if (!m[3]) { ta.setRangeText('', ls, st, 'end'); } else { var marker = /\d/.test(m[2]) ? (parseInt(m[2], 10) + 1) + '.' : m[2]; ta.setRangeText('\n' + m[1] + marker + ' ', st, ta.selectionEnd, 'end'); } self.changed(el); }
        } else if (e.key === 'Escape') { ta.blur(); }
      });
      el.preview.addEventListener('click', function (e) { var a = e.target.closest('a'); if (a) { a.rel = 'noopener'; a.target = '_blank'; } });
      el.setCommitted = function (v) { committed = v; };
      el.mode = 'SPLIT';
      return el;
    },
    changed: function (el) { el.ctx.emit('input', { value: el.ta.value }); },
    mode: function (el, m) {
      el.mode = m; el.classList.toggle('wb-md-only', m === 'ONLY'); el.classList.toggle('wb-md-off', m === 'OFF'); el.previewBtn.classList.toggle('wb-active', m !== 'OFF');
    },
    update: function (el, props, changed) {
      el.props = props;
      if (changed.has('value') && el.ta.value !== (props.value || '')) { el.ta.value = props.value || ''; el.setCommitted(el.ta.value); }
      if (changed.has('previewHtml')) el.preview.innerHTML = props.previewHtml || '';
      if (changed.has('preview')) this.mode(el, props.preview || 'SPLIT');
      if (changed.has('toolbar')) el.toolbar.classList.toggle('wb-hidden', props.toolbar === false);
      if (changed.has('readOnly')) el.ta.readOnly = !!props.readOnly;
      if (changed.has('enabled')) el.ta.disabled = props.enabled === false;
      if (changed.has('placeholder')) el.ta.placeholder = props.placeholder || '';
      if (changed.has('rows')) { el.ta.rows = props.rows || 8; el.classList.toggle('wb-md-fixed', !!props.rows); }
      if (changed.has('error')) el.classList.toggle('wb-invalid', !!props.error);
    },
    call: function (el, m, args) {
      if (m === 'focus') el.ta.focus();
      else if (m === 'insert') { el.ta.setRangeText(args[0], el.ta.selectionStart, el.ta.selectionEnd, 'end'); this.changed(el); }
    },
    insertChild: function () {}, removeChild: function () {}
  });
})();
