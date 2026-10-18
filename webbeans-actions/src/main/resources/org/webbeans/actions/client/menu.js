/*
 * wb.Menu / wb.MenuItem / wb.Separator / wb.Popup — menus. A menu's children live in a dropdown panel that is
 * moved into document.body while open (so it floats above everything); the server fills lazy menus when it
 * receives the "open" event. One menu tree is open at a time (wb.menus.openRoot); keyboard: arrows, Enter,
 * Escape; a click outside or on an item closes everything.
 */
(function () {
  var menus = {
    openRoot: null,   // the top-level element (menubar item or popup) whose tree is open
    stack: [],        // open dropdown elements, outermost first
    away: null,
    closeAll: function () {
      menus.stack.slice().reverse().forEach(menus.closeDropdown);
      menus.stack = [];
      if (menus.openRoot) { menus.openRoot.classList.remove('wb-menu-open'); if (menus.openRoot.__popup) menus.openRoot.__popup.ctx.emit('close', {}); menus.openRoot = null; }
      if (menus.away) { document.removeEventListener('mousedown', menus.away, true); document.removeEventListener('keydown', menus.key, true); menus.away = null; }
    },
    arm: function () {
      if (menus.away) return;
      menus.away = function (e) { if (!e.target.closest('.wb-dropdown, .wb-menubar, .wb-popup')) menus.closeAll(); };
      document.addEventListener('mousedown', menus.away, true);
      document.addEventListener('keydown', menus.key, true);
    },
    openDropdown: function (dd, anchor, place) {
      if (menus.stack.indexOf(dd) >= 0) return;
      // close siblings deeper than the anchor's own dropdown
      var parentDd = anchor.closest('.wb-dropdown');
      var depth = parentDd ? menus.stack.indexOf(parentDd) + 1 : 0;
      menus.stack.slice(depth).reverse().forEach(menus.closeDropdown);
      menus.stack = menus.stack.slice(0, depth);
      document.body.appendChild(dd); dd.classList.add('wb-dropdown-open'); menus.stack.push(dd);
      anchor.classList.add('wb-menu-open');
      menus.position(dd, anchor, place);
      menus.clearFocus(dd);   // a stale focus from the last time the menu was open would survive otherwise
      menus.arm();
    },
    closeDropdown: function (dd) {
      dd.classList.remove('wb-dropdown-open');
      if (dd.__anchor) dd.__anchor.classList.remove('wb-menu-open');
      if (dd.__home) dd.__home.appendChild(dd); else dd.remove();
      var i = menus.stack.indexOf(dd); if (i >= 0) menus.stack.splice(i, 1);
    },
    position: function (dd, anchor, place) {
      var r = anchor.getBoundingClientRect(); var W = window.innerWidth, H = window.innerHeight;
      dd.style.left = '0px'; dd.style.top = '0px';
      var w = dd.offsetWidth, h = dd.offsetHeight, x, y;
      if (place === 'right') { x = r.right - 2; y = r.top - 4; if (x + w > W) x = Math.max(0, r.left - w + 2); }
      else if (place === 'at') { x = r.left; y = r.top; }
      else { x = r.left; y = r.bottom; }
      if (x + w > W) x = Math.max(0, W - w - 4);
      if (y + h > H) y = Math.max(0, H - h - 4);
      dd.style.left = x + 'px'; dd.style.top = y + 'px';
    },
    items: function (dd) { return Array.from(dd.querySelectorAll(':scope > .wb-menuitem:not(.wb-disabled), :scope > .wb-menu-sub:not(.wb-disabled)')); },
    focusItem: function (dd, idx) { var its = menus.items(dd); if (!its.length) return; idx = (idx + its.length) % its.length; its.forEach(function (i, k) { i.classList.toggle('wb-menu-focus', k === idx); }); dd.__focus = idx; },
    /* no keyboard focus: nothing highlighted (focusItem(-1) would wrap to the last item) */
    clearFocus: function (dd) { menus.items(dd).forEach(function (i) { i.classList.remove('wb-menu-focus'); }); dd.__focus = null; },
    key: function (e) {
      if (!menus.stack.length) return;
      var dd = menus.stack[menus.stack.length - 1]; var its = menus.items(dd); var cur = dd.__focus == null ? -1 : dd.__focus;
      switch (e.key) {
        case 'Escape': e.preventDefault(); e.stopPropagation(); if (menus.stack.length > 1) menus.closeDropdown(dd); else menus.closeAll(); break;
        case 'ArrowDown': e.preventDefault(); e.stopPropagation(); menus.focusItem(dd, cur + 1); break;
        case 'ArrowUp': e.preventDefault(); e.stopPropagation(); menus.focusItem(dd, cur - 1); break;
        case 'ArrowRight': e.preventDefault(); e.stopPropagation(); if (cur >= 0 && its[cur].classList.contains('wb-menu-sub')) { its[cur].__open(); } else if (menus.openRoot && menus.openRoot.__bar) menus.openRoot.__bar.step(1); break;
        case 'ArrowLeft': e.preventDefault(); e.stopPropagation(); if (menus.stack.length > 1) menus.closeDropdown(dd); else if (menus.openRoot && menus.openRoot.__bar) menus.openRoot.__bar.step(-1); break;
        case 'Enter': case ' ': e.preventDefault(); e.stopPropagation(); if (cur >= 0) its[cur].click(); break;
      }
    }
  };
  wb.menus = menus;

  function makeDropdown(ctx) {
    var dd = wb.h('div', 'wb-dropdown'); dd.setAttribute('role', 'menu'); dd.__ctx = ctx;
    dd.addEventListener('mouseover', function (e) {
      var it = e.target.closest('.wb-menu-sub'); if (it && it.parentElement === dd && it.__open) it.__open();
      var plain = e.target.closest('.wb-menuitem'); if (plain && plain.parentElement === dd) { var deeper = menus.stack.indexOf(dd) + 1; menus.stack.slice(deeper).reverse().forEach(menus.closeDropdown); }
      /* the mouse and the keyboard share one highlight: hovering moves the focus, so Enter runs the hovered item */
      var hovered = e.target.closest('.wb-menuitem, .wb-menu-sub'); var k = hovered && hovered.parentElement === dd ? menus.items(dd).indexOf(hovered) : -1;
      if (k >= 0) menus.focusItem(dd, k); else if (hovered && hovered.parentElement === dd) menus.clearFocus(dd);
    });
    dd.addEventListener('mouseleave', function () { if (dd.__focus != null && !dd.querySelector(':scope > .wb-menu-sub.wb-menu-open')) menus.clearFocus(dd); });
    return dd;
  }

  /* ---- Menu: a bar entry or a submenu entry plus its dropdown ---- */
  wb.register('wb.Menu', {
    create: function (ctx) {
      var el = wb.h('div', 'wb-menu'); el.setAttribute('role', 'menuitem'); el.setAttribute('aria-haspopup', 'true');
      el.ico = wb.h('span', 'wb-icon'); el.txt = wb.h('span', 'wb-text'); el.arrow = wb.h('span', 'wb-menu-arrow'); el.append(el.ico, el.txt, el.arrow);
      el.dd = makeDropdown(ctx); el.dd.__anchor = el; el.dd.__home = el; el.appendChild(el.dd);
      el.__open = function () {
        if (el.classList.contains('wb-disabled')) return;
        var inBar = el.parentElement && el.parentElement.classList.contains('wb-menubar');
        var alreadyOpen = menus.stack.indexOf(el.dd) >= 0;
        if (alreadyOpen) return;
        if (inBar) { if (menus.openRoot && menus.openRoot !== el) menus.closeAll(); menus.openRoot = el; el.__bar = el.parentElement.__bar; }
        ctx.emit('open', {});
        menus.openDropdown(el.dd, el, inBar ? 'below' : 'right');
      };
      el.addEventListener('click', function (e) {
        e.stopPropagation();
        var inBar = el.parentElement && el.parentElement.classList.contains('wb-menubar');
        if (inBar && menus.openRoot === el) { menus.closeAll(); return; }
        el.__open();
      });
      el.addEventListener('mouseenter', function () {
        var inBar = el.parentElement && el.parentElement.classList.contains('wb-menubar');
        if (inBar && menus.openRoot && menus.openRoot !== el) el.__open();
      });
      return el;
    },
    update: function (el, props, changed) {
      if (changed.has('text')) el.txt.textContent = props.text || '';
      if (changed.has('icon')) { el.ico.innerHTML = ''; if (props.icon) { var im = wb.h('img'); im.src = wb.res(props.icon); im.alt = ''; el.ico.appendChild(im); } el.ico.classList.toggle('wb-hidden', !props.icon); }
    },
    afterUpdate: function (el) { el.classList.toggle('wb-menu-sub', !(el.parentElement && el.parentElement.classList.contains('wb-menubar'))); if (menus.stack.indexOf(el.dd) >= 0 && el.dd.__anchor) menus.position(el.dd, el, el.parentElement && el.parentElement.classList.contains('wb-menubar') ? 'below' : 'right'); },
    insertChild: function (el, child, i) { var ref = el.dd.children[i]; if (ref) el.dd.insertBefore(child, ref); else el.dd.appendChild(child); if (menus.stack.indexOf(el.dd) >= 0) menus.position(el.dd, el, el.parentElement && el.parentElement.classList.contains('wb-menubar') ? 'below' : 'right'); },
    removeChild: function (el, child) { child.remove(); },
    destroy: function (el) { if (menus.stack.indexOf(el.dd) >= 0) menus.closeDropdown(el.dd); if (menus.openRoot === el) menus.closeAll(); }
  });

  /* ---- MenuItem ---- */
  wb.register('wb.MenuItem', {
    create: function (ctx) {
      var el = wb.h('div', 'wb-menuitem'); el.setAttribute('role', 'menuitem');
      el.chk = wb.h('span', 'wb-menu-check'); el.ico = wb.h('span', 'wb-icon'); el.txt = wb.h('span', 'wb-text'); el.acc = wb.h('span', 'wb-menu-acc');
      el.append(el.chk, el.ico, el.txt, el.acc);
      el.addEventListener('click', function (e) { e.stopPropagation(); if (el.classList.contains('wb-disabled')) return; ctx.emit('action', {}); menus.closeAll(); });
      return el;
    },
    update: function (el, props, changed) {
      if (changed.has('text')) el.txt.textContent = props.text || '';
      if (changed.has('icon')) { el.ico.innerHTML = ''; if (props.icon) { var im = wb.h('img'); im.src = wb.res(props.icon); im.alt = ''; el.ico.appendChild(im); } }
      if (changed.has('accelerator')) el.acc.textContent = props.accelerator || '';
      if (changed.has('kind') || changed.has('selected')) {
        var k = (props.kind || 'ITEM').toLowerCase(); el.dataset.kind = k;
        el.chk.textContent = props.selected === true ? (k === 'radio' ? '●' : '✓') : '';
        if (k !== 'item') el.setAttribute('aria-checked', props.selected === true ? 'true' : 'false'); else el.removeAttribute('aria-checked');
      }
    },
    call: function (el, m) { if (m === 'click') el.click(); },
    insertChild: function () {}, removeChild: function () {}
  });

  wb.register('wb.Separator', {
    create: function () { var el = wb.h('div', 'wb-separator'); el.setAttribute('role', 'separator'); return el; },
    update: function () {}, insertChild: function () {}, removeChild: function () {}
  });

  /* ---- Popup: a dropdown opened at page coordinates by call("show", x, y) ---- */
  wb.register('wb.Popup', {
    create: function (ctx) {
      var el = wb.h('div', 'wb-popup'); el.dd = makeDropdown(ctx); el.dd.__anchor = el; el.dd.__home = el; el.appendChild(el.dd); el.ctx = ctx; el.__popup = el;
      return el;
    },
    update: function () {},
    call: function (el, m, args) {
      if (m === 'show') {
        menus.closeAll();
        menus.openRoot = el;
        var anchor = { getBoundingClientRect: function () { return { left: args[0], top: args[1], right: args[0], bottom: args[1] }; } };
        document.body.appendChild(el.dd); el.dd.classList.add('wb-dropdown-open'); menus.stack.push(el.dd);
        menus.position(el.dd, anchor, 'at'); menus.arm();
        menus.clearFocus(el.dd);
      }
    },
    insertChild: function (el, child, i) { var ref = el.dd.children[i]; if (ref) el.dd.insertBefore(child, ref); else el.dd.appendChild(child); },
    removeChild: function (el, child) { child.remove(); },
    destroy: function (el) { var i = menus.stack.indexOf(el.dd); if (i >= 0) { el.dd.remove(); menus.stack.splice(i, 1); } if (menus.openRoot === el) { menus.openRoot = null; if (!menus.stack.length && menus.away) { document.removeEventListener('mousedown', menus.away, true); document.removeEventListener('keydown', menus.key, true); menus.away = null; } } }
  });
})();
