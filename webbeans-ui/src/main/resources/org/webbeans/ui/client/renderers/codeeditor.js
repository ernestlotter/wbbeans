/* wb.CodeEditor — a highlighted textarea: a <pre> layer with tokens behind a transparent textarea, a gutter with
 * line numbers and annotations, current-line highlight, tab/indent handling. No dependencies; the tokenizers are
 * small regex scanners that are good enough for reading code, not compilers. */
(function () {
  var langs = {};
  var esc = function (s) { return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); };
  var kw = function (words) { return new RegExp('^(?:' + words.split(' ').join('|') + ')\\b'); };
  // a language = ordered list of [class, regex] tried at each position; the first match wins
  var common = function (keywords, extra) {
    return [
      ['com', /^\/\/[^\n]*/], ['com', /^\/\*[\s\S]*?(\*\/|$)/],
      ['str', /^"(?:[^"\\\n]|\\.)*"?/], ['str', /^'(?:[^'\\\n]|\\.)*'?/], ['str', /^`(?:[^`\\]|\\.)*`?/],
      ['num', /^(?:0x[0-9a-fA-F_]+|\d[\d_]*\.?\d*(?:[eE][+-]?\d+)?[fFdDlL]?)/],
      ['kw', kw(keywords)]
    ].concat(extra || [], [['typ', /^[A-Z][A-Za-z0-9_]*/], ['id', /^[A-Za-z_$][A-Za-z0-9_$]*/], ['op', /^[-+*\/%=<>!&|^~?:;,.(){}\[\]]/]]);
  };
  langs.java = common('abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for goto if implements import instanceof int interface long native new package private protected public return short static strictfp super switch synchronized this throw throws transient try void volatile while var record yield sealed permits true false null', [['ann', /^@[A-Za-z_][A-Za-z0-9_.]*/]]);
  langs.javascript = common('async await break case catch class const continue debugger default delete do else export extends finally for function if import in instanceof let new of return static super switch this throw try typeof var void while with yield true false null undefined', [['re', /^\/(?:[^\/\\\n]|\\.)+\/[gimsuy]*/]]);
  langs.python = [['com', /^#[^\n]*/], ['str', /^(?:"""[\s\S]*?"""|'''[\s\S]*?'''|"(?:[^"\\\n]|\\.)*"|'(?:[^'\\\n]|\\.)*')/], ['num', /^\d[\d_]*\.?\d*(?:[eE][+-]?\d+)?j?/], ['ann', /^@[A-Za-z_][\w.]*/],
    ['kw', kw('and as assert async await break class continue def del elif else except finally for from global if import in is lambda nonlocal not or pass raise return try while with yield True False None self')], ['typ', /^[A-Z][A-Za-z0-9_]*/], ['id', /^[A-Za-z_][\w]*/], ['op', /^[-+*\/%=<>!&|^~?:;,.(){}\[\]@]/]];
  langs.json = [['key', /^"(?:[^"\\]|\\.)*"(?=\s*:)/], ['str', /^"(?:[^"\\]|\\.)*"/], ['num', /^-?\d+\.?\d*(?:[eE][+-]?\d+)?/], ['kw', /^(?:true|false|null)\b/], ['op', /^[{}\[\]:,]/]];
  langs.css = [['com', /^\/\*[\s\S]*?(\*\/|$)/], ['str', /^"(?:[^"\\]|\\.)*"|^'(?:[^'\\]|\\.)*'/], ['sel', /^[.#]?[A-Za-z_-][\w-]*(?=[^;{}]*\{)/], ['key', /^[a-z-]+(?=\s*:)/], ['num', /^-?\d+\.?\d*(?:px|em|rem|%|vh|vw|s|ms|deg)?/], ['str', /^#[0-9a-fA-F]{3,8}\b/], ['id', /^[A-Za-z_-][\w-]*/], ['op', /^[{}:;,()>+~*]/]];
  var markup = [['com', /^<!--[\s\S]*?(-->|$)/], ['com', /^<!\[CDATA\[[\s\S]*?(\]\]>|$)/], ['tag', /^<\/?[A-Za-z][\w:.-]*|^\/?>|^<\?[\s\S]*?\?>|^<![^>]*>/], ['attr', /^[A-Za-z_:][\w:.-]*(?=\s*=)/], ['str', /^"[^"]*"?|^'[^']*'?/], ['op', /^=/], ['ent', /^&[#\w]+;/]];
  langs.xml = markup; langs.html = markup;
  langs.properties = [['com', /^[#!][^\n]*/], ['key', /^[^=:\s][^=:]*?(?=\s*[=:])/], ['op', /^[=:]/], ['str', /^[^\n]+/]];
  langs.yaml = [['com', /^#[^\n]*/], ['key', /^[A-Za-z_][\w .-]*(?=\s*:)/], ['str', /^"(?:[^"\\]|\\.)*"|^'[^']*'/], ['num', /^-?\d+\.?\d*\b/], ['kw', /^(?:true|false|null|yes|no|~)\b/], ['op', /^[-:\[\]{},|>&*!]/], ['id', /^[^\s:#]+/]];
  langs.sql = [['com', /^--[^\n]*/], ['com', /^\/\*[\s\S]*?(\*\/|$)/], ['str', /^'(?:[^']|'')*'?/], ['num', /^\d+\.?\d*/],
    ['kw', new RegExp('^(?:' + 'select from where and or not in is null insert into values update set delete create table alter drop index view join left right inner outer on group by order having limit offset as distinct union all exists between like case when then else end primary key foreign references default unique check constraint begin commit rollback with'.split(' ').join('|') + ')\\b', 'i')], ['id', /^[A-Za-z_][\w$]*/], ['op', /^[-+*\/%=<>!,.;()]/]];
  langs.shell = [['com', /^#[^\n]*/], ['str', /^"(?:[^"\\]|\\.)*"?|^'[^']*'?/], ['var', /^\$(?:\{[^}]*\}|\w+|[@#?$!*])/], ['kw', kw('if then else elif fi for in do done while until case esac function return exit export local set unset echo cd ls rm cp mv mkdir cat grep sed awk')], ['num', /^\d+\b/], ['id', /^[A-Za-z_][\w-]*/], ['op', /^[-|&;<>()=!{}\[\]*]/]];
  langs.markdown = [['hd', /^#{1,6}[^\n]*/], ['com', /^```[\s\S]*?(```|$)/], ['str', /^`[^`\n]*`/], ['kw', /^(?:\*\*|__)[^*_\n]+(?:\*\*|__)/], ['typ', /^(?:\*|_)[^*_\n]+(?:\*|_)/], ['attr', /^\[[^\]\n]*\]\([^)\n]*\)/], ['num', /^(?:[-*+]|\d+\.)(?=\s)/], ['op', /^>/]];

  function highlight(text, lang) {
    var rules = langs[lang]; if (!rules) return esc(text);
    var out = ''; var i = 0; var n = text.length;
    while (i < n) {
      var rest = text.slice(i, i + 4000); var m = null, cls = null;
      for (var r = 0; r < rules.length; r++) { var mm = rules[r][1].exec(rest); if (mm && mm.index === 0 && mm[0].length) { m = mm[0]; cls = rules[r][0]; break; } }
      if (!m) { var ws = /^\s+|^./.exec(rest); out += esc(ws[0]); i += ws[0].length; continue; }
      out += '<span class="tok-' + cls + '">' + esc(m) + '</span>'; i += m.length;
    }
    return out;
  }

  wb.register('wb.CodeEditor', {
    create: function (ctx) {
      var el = wb.h('div', 'wb-code'); el.ctx = ctx; el.props = {}; el.anns = {};
      el.gutter = wb.h('div', 'wb-code-gutter'); el.scroller = wb.h('div', 'wb-code-scroller');
      el.pre = wb.h('pre', 'wb-code-layer'); el.code = wb.h('code'); el.pre.appendChild(el.code);
      el.ta = wb.h('textarea', 'wb-code-input'); el.ta.spellcheck = false; el.ta.autocapitalize = 'off'; el.ta.setAttribute('autocomplete', 'off'); el.ta.wrap = 'off';
      el.scroller.append(el.pre, el.ta); el.append(el.gutter, el.scroller);
      var self = this; var committed = '';
      el.ta.addEventListener('input', function () { self.render(el); ctx.emit('input', { value: el.ta.value }); });
      el.ta.addEventListener('change', function () { if (el.ta.value !== committed) { committed = el.ta.value; ctx.emit('change', { value: el.ta.value }); } });
      el.ta.addEventListener('blur', function () { if (el.ta.value !== committed) { committed = el.ta.value; ctx.emit('change', { value: el.ta.value }); } ctx.emit('blur', {}); });
      el.ta.addEventListener('focus', function () { ctx.emit('focus', {}); });
      el.ta.addEventListener('scroll', function () { el.pre.style.transform = 'translate(' + (-el.ta.scrollLeft) + 'px,' + (-el.ta.scrollTop) + 'px)'; el.gutter.style.transform = 'translateY(' + (-el.ta.scrollTop) + 'px)'; });
      var caret = function () { self.caret(el); if (ctx.listens('cursor')) { var p = self.pos(el); ctx.emit('cursor', { line: p.line, col: p.col }); } };
      ['keyup', 'click', 'select'].forEach(function (t) { el.ta.addEventListener(t, caret); });
      el.ta.addEventListener('keydown', function (e) {
        var ta = el.ta; var tab = ' '.repeat(el.props.tabSize || 4);
        if (e.key === 'Tab' && !e.ctrlKey && !e.altKey) {
          e.preventDefault(); var s = ta.selectionStart, en = ta.selectionEnd;
          if (s !== en && ta.value.slice(s, en).indexOf('\n') >= 0) { // indent / outdent the selected lines
            var ls = ta.value.lastIndexOf('\n', s - 1) + 1; var block = ta.value.slice(ls, en); var lines = block.split('\n');
            var changed = lines.map(function (l) { return e.shiftKey ? l.replace(new RegExp('^ {1,' + tab.length + '}'), '') : tab + l; }).join('\n');
            ta.setRangeText(changed, ls, en, 'select');
          } else if (e.shiftKey) { var ls2 = ta.value.lastIndexOf('\n', s - 1) + 1; var m = new RegExp('^ {1,' + tab.length + '}').exec(ta.value.slice(ls2, s)); if (m) ta.setRangeText('', ls2, ls2 + m[0].length, 'end'); ta.selectionStart = ta.selectionEnd = Math.max(ls2, s - (m ? m[0].length : 0)); }
          else ta.setRangeText(tab, s, en, 'end');
          self.render(el); ctx.emit('input', { value: ta.value });
        } else if (e.key === 'Enter' && !e.ctrlKey && !e.metaKey) {
          e.preventDefault(); var st = ta.selectionStart; var lineStart = ta.value.lastIndexOf('\n', st - 1) + 1; var indent = /^[ \t]*/.exec(ta.value.slice(lineStart, st))[0];
          var prev = ta.value.slice(lineStart, st); if (/[{(\[:]\s*$/.test(prev)) indent += tab;
          ta.setRangeText('\n' + indent, st, ta.selectionEnd, 'end'); self.render(el); ctx.emit('input', { value: ta.value });
        } else if (e.key === 'Escape') { ta.blur(); }
        else if (ctx.listens('key')) ctx.emit('key', { key: e.key, ctrl: e.ctrlKey, shift: e.shiftKey, alt: e.altKey });
        // other keys bubble: the global router keeps plain keys local to the textarea and dispatches chords like Ctrl+S
      });
      el.gutter.addEventListener('click', function (e) { var ln = e.target.closest('.wb-code-ln'); if (ln && ctx.listens('gutter')) ctx.emit('gutter', { line: Number(ln.dataset.line) }); });
      el.setCommitted = function (v) { committed = v; };
      return el;
    },
    pos: function (el) { var v = el.ta.value.slice(0, el.ta.selectionStart); var line = v.split('\n').length; var col = v.length - v.lastIndexOf('\n'); return { line: line, col: col }; },
    render: function (el) {
      var text = el.ta.value; var lang = (el.props.language || 'PLAIN').toLowerCase();
      el.code.innerHTML = highlight(text + '\n', lang);
      var lines = text.split('\n').length; var g = ''; var anns = el.anns;
      for (var i = 1; i <= lines; i++) { var a = anns[i]; g += '<div class="wb-code-ln' + (a ? ' wb-ann-' + a.kind : '') + '" data-line="' + i + '"' + (a && a.message ? ' title="' + esc(a.message) + '"' : '') + '>' + (el.props.lineNumbers === false ? '' : i) + '</div>'; }
      el.gutter.innerHTML = g; el.gutter.classList.toggle('wb-hidden', el.props.lineNumbers === false && !Object.keys(anns).length);
      this.caret(el);
    },
    caret: function (el) {
      if (el.props.highlightLine === false) { el.scroller.style.removeProperty('--wb-code-line'); return; }
      var p = this.pos(el); el.scroller.style.setProperty('--wb-code-line', p.line); el.gutter.querySelectorAll('.wb-code-ln.wb-current').forEach(function (n) { n.classList.remove('wb-current'); });
      var cur = el.gutter.querySelector('.wb-code-ln[data-line="' + p.line + '"]'); if (cur) cur.classList.add('wb-current');
    },
    update: function (el, props, changed) {
      el.props = props;
      if (changed.has('value') && el.ta.value !== (props.value || '')) { el.ta.value = props.value || ''; el.setCommitted(el.ta.value); }
      if (changed.has('annotations')) { el.anns = {}; (props.annotations || []).forEach(function (a) { el.anns[a.line] = a; }); }
      if (changed.has('readOnly')) el.ta.readOnly = !!props.readOnly;
      if (changed.has('enabled')) el.ta.disabled = props.enabled === false;
      if (changed.has('placeholder')) el.ta.placeholder = props.placeholder || '';
      if (changed.has('wrap')) { el.classList.toggle('wb-code-wrap', !!props.wrap); el.ta.wrap = props.wrap ? 'soft' : 'off'; }
      if (changed.has('tabSize')) { el.ta.style.tabSize = props.tabSize || 4; el.pre.style.tabSize = props.tabSize || 4; }
      if (changed.has('error')) el.classList.toggle('wb-invalid', !!props.error);
      this.render(el);
    },
    call: function (el, m, args) {
      var ta = el.ta;
      if (m === 'focus') ta.focus();
      else if (m === 'goToLine') {
        var lines = ta.value.split('\n'); var ln = Math.max(1, Math.min(lines.length, args[0])); var pos = 0; for (var i = 0; i < ln - 1; i++) pos += lines[i].length + 1;
        ta.focus(); ta.setSelectionRange(pos, pos + lines[ln - 1].length); var lh = parseFloat(getComputedStyle(ta).lineHeight) || 18; ta.scrollTop = Math.max(0, (ln - 1) * lh - ta.clientHeight / 2); this.caret(el);
      } else if (m === 'insert') { ta.setRangeText(args[0], ta.selectionStart, ta.selectionEnd, 'end'); this.render(el); el.ctx.emit('input', { value: ta.value }); }
      else if (m === 'select') { ta.focus(); ta.setSelectionRange(args[0], args[1]); this.caret(el); }
    },
    insertChild: function () {}, removeChild: function () {}
  });
  wb.highlightCode = highlight;   // reused by the Markdown editor's preview and the output view
})();
