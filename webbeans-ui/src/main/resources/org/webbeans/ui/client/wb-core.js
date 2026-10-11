/*
 * wb-core.js — the WebBeans client runtime.
 * Mirrors the server-side component tree, applies deltas, hosts renderers and sends semantic events back.
 * Transport: a WebSocket to /wb/ws, or long polling over fetch (/wb/poll) when no WebSocket can be opened.
 * Plain ES2017, no dependencies. The global `wb` is the only symbol exported.
 */
(function () {
  'use strict';
  var cfg = window.WB || {};
  var base = cfg.base || '';

  var wb = {
    cfg: cfg,
    base: base,
    renderers: {},
    nodes: new Map(),
    rootEl: null,
    seq: 0,            // last applied downstream seq
    evSeq: 0,          // client event sequence
    pending: new Map(),// out-of-order downstream messages by seq
    unacked: [],       // events not yet acknowledged by a server response
    queue: [],         // events waiting for the next flush
    flushTimer: null,
    state: 'init',
    keymap: {},
    listeners: {},
    register: function (id, impl) { wb.renderers[id] = impl; },
    on: function (ev, fn) { (wb.listeners[ev] = wb.listeners[ev] || []).push(fn); },
    fire: function (ev, data) { (wb.listeners[ev] || []).forEach(function (f) { try { f(data); } catch (e) { console.error(e); } }); },
    res: function (v) {
      if (v && typeof v === 'object' && v.$res) return base + '/wb/res/' + v.$res;
      return v;
    },
    ref: function (v) { return v && typeof v === 'object' && v.$ref != null ? wb.nodes.get(v.$ref) : null; },
    h: function (tag, cls, text) { var e = document.createElement(tag); if (cls) e.className = cls; if (text != null) e.textContent = text; return e; },
    esc: function (s) { return String(s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); },
    size: function (el, props, changed) {
      if (changed.has('width')) el.style.width = props.width === 'fill' ? '100%' : (props.width || '');
      if (changed.has('height')) el.style.height = props.height === 'fill' ? '100%' : (props.height || '');
    },
    common: function (el, props, changed) {
      if (changed.has('visible')) el.classList.toggle('wb-hidden', props.visible === false);
      if (changed.has('enabled')) { el.classList.toggle('wb-disabled', props.enabled === false); if ('disabled' in el) el.disabled = props.enabled === false; }
      if (changed.has('tooltip')) { if (props.tooltip) el.title = props.tooltip; else el.removeAttribute('title'); }
      if (changed.has('styleName')) { (el.__wbStyle || '').split(' ').forEach(function (c) { if (c) el.classList.remove(c); }); el.__wbStyle = props.styleName || ''; el.__wbStyle.split(' ').forEach(function (c) { if (c) el.classList.add(c); }); }
      if (changed.has('testId')) { if (props.testId) el.dataset.test = props.testId; else delete el.dataset.test; }
      wb.size(el, props, changed);
    }
  };
  window.wb = wb;

  /* ---------------- tree + apply ---------------- */
  function ctxFor(node) {
    return {
      id: node.id, node: node,
      emit: function (type, data) { if (!node.listens.has(type)) return; sendEvent(node.id, type, data || {}, node.lazy[type]); },
      listens: function (type) { return node.listens.has(type); }
    };
  }
  function listenSpec(node, spec) {
    if (typeof spec === 'string') { node.listens.add(spec); }
    else if (spec && spec.type) { node.listens.add(spec.type); if (spec.lazy) node.lazy[spec.type] = spec.lazy; }
  }
  function apply(op, touched) {
    var node, parent;
    switch (op.op) {
      case 'create': {
        var r = wb.renderers[op.type];
        if (!r) { console.error('WebBeans: no renderer for ' + op.type); r = wb.renderers['wb.Missing']; }
        node = { id: op.id, type: op.type, props: op.props || {}, listens: new Set(), lazy: {}, renderer: r, parent: null, children: [], constraint: op.c == null ? null : op.c };
        (op.events || []).forEach(function (e) { listenSpec(node, e); });
        node.ctx = ctxFor(node);
        node.el = r.create(node.ctx);
        node.el.__wb = node;
        wb.nodes.set(op.id, node);
        var all = new Set(Object.keys(node.props)); ['visible', 'enabled', 'tooltip', 'styleName', 'testId', 'width', 'height'].forEach(function (k) { if (k in node.props) all.add(k); });
        wb.common(node.el, node.props, all);
        r.update(node.el, node.props, all, node.ctx);
        parent = op.parent != null ? wb.nodes.get(op.parent) : null;
        if (parent) {
          var idx = Math.min(op.index == null ? parent.children.length : op.index, parent.children.length);
          parent.children.splice(idx, 0, node); node.parent = parent;
          parent.renderer.insertChild(parent.el, node.el, idx, parent.ctx, node);
          touched.add(parent);
        } else { wb.rootEl.appendChild(node.el); }
        touched.add(node);
        break;
      }
      case 'set':
        node = wb.nodes.get(op.id); if (!node) return;
        Object.assign(node.props, op.props);
        var ch = new Set(Object.keys(op.props));
        wb.common(node.el, node.props, ch);
        node.renderer.update(node.el, node.props, ch, node.ctx);
        touched.add(node);
        break;
      case 'remove':
        node = wb.nodes.get(op.id); if (!node) return;
        if (node.parent) { node.parent.children = node.parent.children.filter(function (c) { return c !== node; }); node.parent.renderer.removeChild(node.parent.el, node.el, node.parent.ctx, node); touched.add(node.parent); }
        else node.el.remove();
        destroy(node);
        break;
      case 'call':
        node = wb.nodes.get(op.id);
        if (node && node.renderer.call) { node.renderer.call(node.el, op.m, op.args || [], node.ctx); touched.add(node); }
        break;
      case 'listen':
        node = wb.nodes.get(op.id);
        if (node) (op.events || []).forEach(function (e) { listenSpec(node, e); });
        break;
    }
  }
  function destroy(node) {
    node.children.forEach(destroy);
    if (node.renderer.destroy) { try { node.renderer.destroy(node.el, node.ctx); } catch (e) { console.error(e); } }
    wb.nodes.delete(node.id);
  }
  function applyMessage(msg) {
    if (msg.t === 'init') {
      wb.nodes.forEach(destroy); wb.nodes.clear(); wb.rootEl.innerHTML = '';
      if (msg.locale) { wb.locale = msg.locale; document.documentElement.lang = msg.locale; }   // renderers format dates/numbers with it
    }
    wb.seq = msg.seq;
    var touched = new Set();
    (msg.ops || []).forEach(function (op) { try { apply(op, touched); } catch (e) { console.error('WebBeans: op failed', op, e); } });
    touched.forEach(function (n) { if (n.renderer.afterUpdate) n.renderer.afterUpdate(n.el, n.props, n.ctx); });
    wb.fire('message', msg);
  }
  function receive(messages) {
    messages.forEach(function (msg) {
      if (msg.t === 'nav') { if (msg.reload) location.reload(); else location.href = msg.url; return; }
      if (msg.t === 'err') { showError(msg.message, msg.fatal); return; }
      if (msg.seq == null) return;
      if (msg.t === 'init' || msg.seq === wb.seq + 1) {
        applyMessage(msg);
        while (wb.pending.has(wb.seq + 1)) { var next = wb.pending.get(wb.seq + 1); wb.pending.delete(wb.seq + 1); applyMessage(next); }
      } else if (msg.seq > wb.seq + 1) {
        wb.pending.set(msg.seq, msg);
        scheduleGapCheck();
      } // older: already applied (duplicate after reconnect)
    });
  }
  var gapTimer = null;
  function scheduleGapCheck() {
    if (gapTimer) return;
    gapTimer = setTimeout(function () { gapTimer = null; if (wb.pending.size) { wb.pending.clear(); transport.resync(); } }, 2000);
  }

  /* ---------------- events upstream ---------------- */
  var lazyTimers = {};
  function sendEvent(id, type, data, lazyMs) {
    var key = id + ':' + type;
    if (lazyMs) {
      clearTimeout(lazyTimers[key]);
      lazyTimers[key] = setTimeout(function () { delete lazyTimers[key]; enqueue(id, type, data); }, lazyMs);
      return;
    }
    enqueue(id, type, data);
  }
  function enqueue(id, type, data) {
    wb.queue.push({ s: ++wb.evSeq, id: id, type: type, data: data });
    if (!wb.flushTimer) wb.flushTimer = setTimeout(flushEvents, 0);
  }
  function flushEvents() {
    wb.flushTimer = null;
    // one event request in flight at a time: the server deduplicates by client sequence and assumes it sees
    // events in order, which concurrent POSTs would not guarantee; later events are batched into the next request
    if (!wb.queue.length || transport.sending || transport.stopped) return;
    var events = wb.queue; wb.queue = [];
    wb.unacked = wb.unacked.concat(events);
    transport.sendEvents(events);
  }
  wb.sendEvent = sendEvent;

  /* ---------------- transports ---------------- */
  // Two transports speak the same message protocol: a WebSocket (`/wb/ws`, the normal case) and long polling over
  // fetch (`/wb/poll`, the fallback when no WebSocket can be opened — a proxy that strips the upgrade, an old
  // browser, or the server started without WebSocket). `transport` is the facade the rest of the runtime uses.
  function gone() {
    transport.stopped = true; setState('gone');
    setTimeout(function () { location.href = base + '/login?return=' + encodeURIComponent(location.pathname + location.search); }, 300);
  }
  function connected() {
    transport.failures = 0; transport.backoff = 500;
    setState('connected');
    if (wb.unacked.length) { wb.queue = wb.unacked.concat(wb.queue); wb.unacked = []; }
    flushEvents();
  }
  function acked(upTo) { wb.unacked = wb.unacked.filter(function (e) { return e.s > upTo; }); }
  function retryDelay() {
    return Math.min(30000, transport.backoff * Math.pow(1.6, Math.min(10, transport.failures - 1)));
  }

  var transport = {
    kind: null, stopped: false, failures: 0, backoff: 500, sending: false, offlineUntil: 0,
    hello: function () { transport.impl.hello(); },
    resync: function () { transport.impl.resync(); },
    sendEvents: function (events) { if (!transport.stopped) transport.impl.sendEvents(events); },
    bye: function () { transport.impl.bye(); },
    /** Schedules the next connection attempt with exponential backoff. */
    fail: function () {
      if (transport.stopped) return;
      transport.failures++;
      setState('reconnecting');
      setTimeout(function () { if (!transport.stopped) transport.impl.reconnect(); }, Math.max(retryDelay(), transport.offlineUntil - Date.now()));
    },
    /** Drops the connection and refuses to reconnect for `ms` (tests: exercises the reconnect path). */
    simulateOutage: function (ms) { transport.offlineUntil = Date.now() + (ms || 1000); transport.impl.drop(); },
    /** Switches to long polling for good. */
    usePoll: function () { transport.kind = 'poll'; transport.impl = poll; transport.sending = false; }
  };

  /* --- WebSocket --- */
  var ws = {
    socket: null, everConnected: false, lastReceived: 0, timer: null,
    url: function () {
      return (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + base + cfg.ws
        + '?ui=' + encodeURIComponent(cfg.uiId) + '&token=' + encodeURIComponent(cfg.token || '');
    },
    hello: function () {
      if (transport.stopped) return;
      if (ws.socket && ws.socket.readyState === WebSocket.OPEN) { ws.send({ t: 'hello', lastSeq: wb.seq || -1 }); return; }
      if (ws.socket) return; // connecting: hello goes out on open
      if (Date.now() < transport.offlineUntil) { transport.fail(); return; }
      setState(wb.seq ? 'reconnecting' : 'connecting');
      var sock;
      try { sock = new WebSocket(ws.url()); } catch (e) { ws.unavailable(); return; }
      ws.socket = sock;
      var opened = false;
      sock.onopen = function () { opened = true; ws.lastReceived = Date.now(); ws.send({ t: 'hello', lastSeq: wb.seq || -1 }); };
      sock.onmessage = function (ev) {
        ws.lastReceived = Date.now();
        var msg; try { msg = JSON.parse(ev.data); } catch (e) { console.error('WebBeans: bad message', ev.data); return; }
        if (msg.t === 'pong') return;
        if (msg.t === 'ack') { acked(msg.ev); return; }
        if (msg.t === 'gone') { gone(); return; }
        if (!ws.everConnected || wb.state !== 'connected') { ws.everConnected = true; connected(); }
        receive([msg]);
      };
      sock.onerror = function () { /* onclose follows */ };
      sock.onclose = function (ev) {
        if (ws.socket !== sock) return;
        ws.socket = null;
        if (transport.stopped) return;
        if (ev.code === 4410 || ev.code === 4401 || ev.code === 4403) { gone(); return; }
        if (!opened && !ws.everConnected && cfg.poll) { ws.unavailable(); return; } // never got through: a proxy in the way
        transport.fail();
      };
    },
    reconnect: function () { ws.hello(); },
    resync: function () { ws.hello(); },
    send: function (obj) { ws.socket.send(JSON.stringify(obj)); },
    sendEvents: function (events) {
      // a single socket keeps events in order, so they go out at once; the server's ack prunes `unacked`
      if (ws.socket && ws.socket.readyState === WebSocket.OPEN) ws.send({ t: 'ev', events: events });
      // not open: they stay in `unacked` and are re-sent after the next hello
    },
    bye: function () {
      if (ws.socket && ws.socket.readyState === WebSocket.OPEN) { try { ws.send({ t: 'bye' }); ws.socket.close(1000, 'bye'); } catch (e) { /* closing anyway */ } }
      else poll.bye();
    },
    drop: function () { var s = ws.socket; if (s) { ws.socket = null; try { s.close(); } catch (e) { /* ignore */ } transport.fail(); } },
    unavailable: function () { console.info('WebBeans: WebSocket unavailable, using long polling'); ws.stop(); transport.usePoll(); poll.hello(); },
    stop: function () { clearInterval(ws.timer); ws.timer = null; },
    keepAlive: function () {
      if (!ws.timer) ws.timer = setInterval(function () {
        var s = ws.socket;
        if (!s || s.readyState !== WebSocket.OPEN) return;
        if (Date.now() - ws.lastReceived > 90000) { ws.drop(); return; }  // half-open connection: the pongs stopped
        try { ws.send({ t: 'ping' }); } catch (e) { ws.drop(); }
      }, 30000);
    }
  };

  /* --- long polling --- */
  var poll = {
    polling: false, needHello: false,
    post: function (body) {
      if (Date.now() < transport.offlineUntil) return Promise.reject(new Error('offline'));
      return fetch(base + (cfg.poll || '/wb/poll'), {
        method: 'POST', credentials: 'same-origin', cache: 'no-store',
        headers: { 'Content-Type': 'application/json', 'X-WB-Token': cfg.token || '' },
        body: JSON.stringify(Object.assign({ uiId: cfg.uiId }, body))
      }).then(function (r) {
        if (r.status === 410 || r.status === 401) { gone(); throw new Error('session gone'); }
        if (!r.ok) throw new Error('HTTP ' + r.status);
        return r.json();
      });
    },
    hello: function () {
      if (transport.stopped) return;
      setState(wb.seq ? 'reconnecting' : 'connecting');
      poll.post({ t: 'hello', lastSeq: wb.seq || -1, lastEventSeq: wb.evSeq }).then(function (res) {
        transport.sending = false;
        connected();
        receive(res.messages || []);
        poll.loop();
      }).catch(transport.fail);
    },
    // a new hello replaces the server-side transport, so wait for an in-flight poll to come back first
    reconnect: function () { if (poll.polling) poll.needHello = true; else poll.hello(); },
    resync: function () { poll.reconnect(); },
    loop: function () {
      if (poll.polling || transport.stopped) return;
      poll.polling = true;
      poll.post({ t: 'poll', ack: wb.seq }).then(function (res) {
        poll.polling = false; transport.failures = 0;
        receive(res.messages || []);
        if (poll.needHello) { poll.needHello = false; poll.hello(); } else poll.loop();
      }).catch(function () { poll.polling = false; transport.fail(); });
    },
    sendEvents: function (events) {
      // one event request in flight at a time: concurrent POSTs would not keep the events in order
      transport.sending = true;
      poll.post({ t: 'ev', ack: wb.seq, events: events }).then(function (res) {
        transport.sending = false;
        acked(Math.max.apply(null, events.map(function (e) { return e.s; })));
        receive(res.messages || []);
        flushEvents();
      }).catch(function () { transport.sending = false; transport.fail(); });
    },
    bye: function () { if (navigator.sendBeacon) navigator.sendBeacon(base + (cfg.poll || '/wb/poll') + '?token=' + encodeURIComponent(cfg.token || ''), JSON.stringify({ t: 'bye', uiId: cfg.uiId })); },
    drop: function () { transport.fail(); }
  };

  if (cfg.ws && typeof WebSocket !== 'undefined') { transport.kind = 'ws'; transport.impl = ws; ws.keepAlive(); }
  else transport.usePoll();
  wb.transport = transport;

  function setState(s) { wb.state = s; document.documentElement.dataset.wbState = s; var el = document.getElementById('wb-conn'); if (el) { el.textContent = s === 'connected' ? '' : s === 'gone' ? 'Session ended' : 'Reconnecting…'; el.classList.toggle('wb-hidden', s === 'connected'); } wb.fire('state', s); }
  function showError(message, fatal) {
    var el = document.getElementById('wb-error') || document.body.appendChild(wb.h('div', 'wb-error'));
    el.id = 'wb-error'; el.textContent = message || 'Error';
    if (fatal) transport.stopped = true;
  }

  /* ---------------- keyboard router ---------------- */
  function keyName(e) {
    var parts = [];
    if (e.ctrlKey || e.metaKey) parts.push('Ctrl');
    if (e.altKey) parts.push('Alt');
    if (e.shiftKey) parts.push('Shift');
    var k = e.key.length === 1 ? e.key.toUpperCase() : e.key;
    parts.push(k);
    return parts.join('+');
  }
  document.addEventListener('keydown', function (e) {
    var name = keyName(e);
    var action = wb.keymap[name];
    if (action && wb.rootNode && wb.rootNode.listens.has('shortcut')) {
      var tag = e.target && e.target.tagName;
      var editable = /INPUT|TEXTAREA|SELECT/.test(tag) || (e.target && e.target.isContentEditable);
      // inside a text field plain keys (Delete, F2, letters...) and the editing chords stay local
      var local = editable && (!(e.ctrlKey || e.metaKey || e.altKey) || /^Ctrl\+(C|V|X|A|Z|Y)$/.test(name));
      // a modal dialog takes every key; the server would drop the event anyway
      var modal = document.querySelector('.wb-dialog-wrap.wb-dialog-modal');
      if (!local && !modal) { e.preventDefault(); sendEvent(wb.rootNode.id, 'shortcut', { key: name, action: action }); }
    }
  });

  /* ---------------- shared renderer factory for text inputs ---------------- */
  wb.textInput = function (tag) {
    return {
      create: function (ctx) {
        var wrap = wb.h('div', 'wb-field'); var el = wb.h(tag, 'wb-input'); wrap.input = el; wrap.appendChild(el);
        var err = wb.h('div', 'wb-field-error'); wrap.err = err; wrap.appendChild(err);
        el.spellcheck = false; el.id = 'wb-in-' + ctx.id;
        var committed = null;
        var commit = function () { if (el.value !== committed || wrap.classList.contains('wb-invalid')) { committed = el.value; ctx.emit('change', { value: el.value }); } };
        el.addEventListener('input', function () { ctx.emit('input', { value: el.value }); });
        el.addEventListener('change', commit);
        el.addEventListener('blur', function () { commit(); ctx.emit('blur', {}); });
        el.addEventListener('focus', function () { ctx.emit('focus', {}); });
        el.addEventListener('keydown', function (e) {
          if (e.key === 'Enter' && tag === 'input') { commit(); var form = el.closest('.wb-panel'); var def = form && form.querySelector('.wb-button.wb-default'); if (def) def.click(); }
          if (ctx.listens('key')) ctx.emit('key', { key: e.key, ctrl: e.ctrlKey, shift: e.shiftKey, alt: e.altKey });
        });
        wrap.setCommitted = function (v) { committed = v; };
        return wrap;
      },
      update: function (wrap, props, changed) {
        var el = wrap.input;
        if (changed.has('value') && el.value !== (props.value || '')) { el.value = props.value || ''; wrap.setCommitted(el.value); }
        if (changed.has('placeholder')) el.placeholder = props.placeholder || '';
        if (changed.has('maxLength')) { if (props.maxLength) el.maxLength = props.maxLength; else el.removeAttribute('maxlength'); }
        if (changed.has('password') && tag === 'input') el.type = props.password ? 'password' : 'text';
        if (changed.has('readOnly')) el.readOnly = !!props.readOnly;
        if (changed.has('required')) el.required = !!props.required;
        if (changed.has('enabled')) el.disabled = props.enabled === false;
        if (changed.has('error')) { wrap.err.textContent = props.error || ''; wrap.classList.toggle('wb-invalid', !!props.error); el.setAttribute('aria-invalid', props.error ? 'true' : 'false'); }
        if (changed.has('rows')) el.rows = props.rows || 4;
        if (changed.has('monospace')) el.classList.toggle('wb-mono', !!props.monospace);
        if (changed.has('wrap')) el.wrap = props.wrap === false ? 'off' : 'soft';
        if (changed.has('autoGrow')) el.classList.toggle('wb-autogrow', !!props.autoGrow);
      },
      call: function (wrap, m, args) { if (m === 'focus') wrap.input.focus(); else if (m === 'select') wrap.input.select(); else if (m === 'scrollToLine') { var lh = parseFloat(getComputedStyle(wrap.input).lineHeight) || 18; wrap.input.scrollTop = Math.max(0, (args[0] - 1) * lh); } },
      insertChild: function () {}, removeChild: function () {}
    };
  };

  /* ---------------- bootstrap ---------------- */
  wb.register('wb.Missing', {
    create: function (ctx) { return wb.h('div', 'wb-missing', 'missing renderer: ' + ctx.node.type); },
    update: function () {}, insertChild: function (el, c) { el.appendChild(c); }, removeChild: function (el, c) { c.remove(); }
  });
  wb.start = function () {
    if (wb.started) return;
    wb.started = true;
    wb.rootEl = document.getElementById('wb-root') || document.body.appendChild(wb.h('div'));
    wb.rootEl.id = 'wb-root';
    window.addEventListener('pagehide', transport.bye);
    transport.hello();
  };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', wb.start); else wb.start();
})();
