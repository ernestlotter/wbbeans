/*
 * wb.Dialog — a window in the overlay: backdrop (modal), title bar (draggable, close button), side slot,
 * content, notification line, button bar. Escape emits "close"; Enter (outside textareas) presses the default
 * button; focus is trapped inside modal dialogs.
 */
wb.register('wb.Dialog', {
  create: function (ctx) {
    var wrap = wb.h('div', 'wb-dialog-wrap'); wrap.ctx = ctx;
    wrap.back = wb.h('div', 'wb-dialog-backdrop'); wrap.appendChild(wrap.back);
    var dlg = wb.h('div', 'wb-dialog'); dlg.setAttribute('role', 'dialog'); dlg.setAttribute('aria-modal', 'true'); dlg.tabIndex = -1; wrap.dlg = dlg; wrap.appendChild(dlg);
    wrap.head = wb.h('div', 'wb-dialog-head'); wrap.titleEl = wb.h('div', 'wb-dialog-title'); wrap.closeBtn = wb.h('button', 'wb-dialog-close', '×'); wrap.closeBtn.type = 'button'; wrap.closeBtn.title = 'Close';
    wrap.head.append(wrap.titleEl, wrap.closeBtn); dlg.appendChild(wrap.head);
    wrap.main = wb.h('div', 'wb-dialog-main'); wrap.side = wb.h('div', 'wb-dialog-side wb-hidden'); wrap.body = wb.h('div', 'wb-dialog-body');
    wrap.iconEl = wb.h('div', 'wb-dialog-icon wb-hidden'); wrap.content = wb.h('div', 'wb-dialog-content');
    wrap.body.append(wrap.iconEl, wrap.content); wrap.main.append(wrap.side, wrap.body); dlg.appendChild(wrap.main);
    wrap.note = wb.h('div', 'wb-dialog-note wb-hidden'); dlg.appendChild(wrap.note);
    wrap.buttons = wb.h('div', 'wb-dialog-buttons'); dlg.appendChild(wrap.buttons);
    wrap.closeBtn.addEventListener('click', function () { ctx.emit('close', {}); });
    wrap.back.addEventListener('mousedown', function (e) { e.preventDefault(); dlg.classList.add('wb-dialog-shake'); setTimeout(function () { dlg.classList.remove('wb-dialog-shake'); }, 300); });
    dlg.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); if (wrap.props.closable !== false) ctx.emit('close', {}); }
      else if (e.key === 'F1') { var hb = wrap.buttons.querySelector('.wb-dialog-help'); if (hb) { e.preventDefault(); e.stopPropagation(); hb.click(); } }
      else if (e.key === 'Enter' && !/TEXTAREA|BUTTON/.test(e.target.tagName) && !e.target.closest('.wb-dropdown')) { var def = wrap.buttons.querySelector('.wb-button.wb-default:not([disabled])'); if (def) { e.preventDefault(); e.stopPropagation(); def.click(); } }
      else if (e.key === 'Tab' && wrap.props.modal !== false) {
        var f = Array.from(dlg.querySelectorAll('button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])')).filter(function (x) { return x.offsetParent !== null; });
        if (!f.length) return; var first = f[0], last = f[f.length - 1];
        if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); } else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
      }
    });
    // drag by the title bar
    wrap.head.addEventListener('mousedown', function (e) {
      if (e.target === wrap.closeBtn || e.button !== 0) return;
      var r = dlg.getBoundingClientRect(); var sx = e.clientX - r.left, sy = e.clientY - r.top; e.preventDefault();
      dlg.style.position = 'fixed'; dlg.style.margin = '0';
      var move = function (ev) { dlg.style.left = Math.max(0, Math.min(window.innerWidth - 60, ev.clientX - sx)) + 'px'; dlg.style.top = Math.max(0, Math.min(window.innerHeight - 40, ev.clientY - sy)) + 'px'; };
      var up = function () { document.removeEventListener('mousemove', move); document.removeEventListener('mouseup', up); };
      document.addEventListener('mousemove', move); document.addEventListener('mouseup', up);
    });
    wrap.props = {};
    return wrap;
  },
  update: function (wrap, props, changed) {
    wrap.props = props;
    if (changed.has('title')) wrap.titleEl.textContent = props.title || '';
    if (changed.has('modal')) { wrap.classList.toggle('wb-dialog-modal', props.modal !== false); wrap.dlg.setAttribute('aria-modal', props.modal !== false ? 'true' : 'false'); }
    if (changed.has('closable')) wrap.closeBtn.classList.toggle('wb-hidden', props.closable === false);
    if (changed.has('resizable')) wrap.dlg.classList.toggle('wb-dialog-resizable', props.resizable !== false);
    if (changed.has('messageType')) { wrap.iconEl.classList.toggle('wb-hidden', !props.messageType); wrap.iconEl.dataset.kind = props.messageType || ''; wrap.iconEl.textContent = { error: '✕', warning: '!', info: 'i', question: '?' }[props.messageType] || ''; }
    if (changed.has('notification') || changed.has('notificationKind')) { wrap.note.textContent = props.notification || ''; wrap.note.classList.toggle('wb-hidden', !props.notification); wrap.note.dataset.kind = props.notificationKind || 'info'; }
    if (changed.has('width')) wrap.dlg.style.width = props.width === 'fill' ? '90vw' : (props.width || '');
    if (changed.has('height')) wrap.dlg.style.height = props.height === 'fill' ? '90vh' : (props.height || '');
  },
  call: function (wrap, m) {
    if (m === 'open') {
      wrap.prevFocus = document.activeElement;
      setTimeout(function () { var f = wrap.content.querySelector('input,select,textarea,button,[tabindex]'); (f || wrap.buttons.querySelector('.wb-default') || wrap.dlg).focus(); }, 0);
    }
  },
  insertChild: function (wrap, child, i, ctx, node) {
    if (node.constraint === 'button') { child.classList.add('wb-dialog-button'); wrap.buttons.appendChild(child); }
    else if (node.constraint === 'help') { child.classList.add('wb-dialog-button', 'wb-dialog-help'); wrap.buttons.insertBefore(child, wrap.buttons.firstChild); }
    else if (node.constraint === 'side') { wrap.side.appendChild(child); wrap.side.classList.remove('wb-hidden'); }
    else { wrap.content.appendChild(child); }
  },
  removeChild: function (wrap, child) { child.remove(); if (!wrap.side.children.length) wrap.side.classList.add('wb-hidden'); },
  destroy: function (wrap) { if (wrap.prevFocus && wrap.prevFocus.focus && document.contains(wrap.prevFocus)) { try { wrap.prevFocus.focus(); } catch (e) { /* ignore */ } } }
});
