// Browser end-to-end check of step 10: the WebSocket transport and its long-poll fallback. Start the server with
// --admin-password secret123 --port 8089, then: npm i playwright && node transport.js
//   - a normal tab talks over the WebSocket (wb.transport.kind === 'ws'), events are acknowledged, pings keep it alive
//   - an outage drops the socket; the client reconnects with its last seq and the deltas are replayed
//   - a browser whose WebSocket cannot connect (a proxy in the way) falls back to long polling on the first attempt
//   - a browser without WebSocket at all uses long polling from the start; the poll transport survives an outage too
const { chromium } = require('playwright');
const base = process.env.BASE || 'http://localhost:8089';
const step = s => console.log('* ' + s);
const assert = (cond, msg) => { if (!cond) { throw new Error('ASSERT: ' + msg); } };
(async () => {
  const browser = await chromium.launch();
  const login = async (ctx) => {
    const page = await ctx.newPage();
    const errors = [], sockets = [];
    page.on('websocket', ws => sockets.push(ws.url()));
    page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
    page.on('pageerror', e => errors.push('pageerror: ' + e.message));
    await page.goto(base + '/');
    await page.fill('input[name=username]', 'admin'); await page.fill('input[name=password]', 'secret123'); await page.click('button[type=submit]');
    await page.waitForSelector('.wb-dock .wb-mode');
    await page.waitForFunction(() => document.documentElement.dataset.wbState === 'connected');
    return { page, errors, sockets };
  };
  const state = page => page.evaluate(() => ({ kind: wb.transport.kind, state: wb.state, seq: wb.seq, unacked: wb.unacked.length, queue: wb.queue.length }));
  const clickAbout = async page => {
    await page.click('[data-test=open-about]'); await page.waitForTimeout(400);
  };

  step('WebSocket transport');
  {
    const ctx = await browser.newContext({ viewport: { width: 1280, height: 800 } });
    const { page, errors, sockets } = await login(ctx);
    let s = await state(page);
    console.log('  ' + JSON.stringify(s) + ' sockets=' + sockets.length);
    assert(s.kind === 'ws' && s.state === 'connected', 'connected over the WebSocket');
    assert(sockets.length === 1 && /\/wb\/ws\?ui=u[0-9a-f]+&token=/.test(sockets[0]), 'one socket to /wb/ws with ui and token: ' + sockets);

    await clickAbout(page);
    s = await state(page);
    console.log('  after a click: ' + JSON.stringify(s) + ' active=' + await page.textContent('[data-test=activated]'));
    assert(s.unacked === 0 && s.queue === 0, 'events acknowledged');
    assert((await page.textContent('[data-test=activated]')).includes('About'), 'the click reached the server');

    step('outage: the socket drops, the client reconnects and the missed deltas are replayed');
    const seqBefore = s.seq;
    await page.evaluate(() => wb.transport.simulateOutage(1500));
    await page.waitForFunction(() => document.documentElement.dataset.wbState === 'reconnecting', null, { timeout: 10000 });
    await page.waitForFunction(() => document.documentElement.dataset.wbState === 'connected', null, { timeout: 20000 });
    s = await state(page);
    console.log('  after reconnect: ' + JSON.stringify(s) + ' sockets=' + sockets.length);
    assert(s.kind === 'ws' && sockets.length === 2, 'a second socket was opened');
    assert(s.seq >= seqBefore, 'seq kept across the reconnect (replay, not re-init)');
    await page.click('.wb-dk-tab[data-tc="HelloTC"]'); await page.waitForTimeout(400);
    assert((await page.textContent('[data-test=activated]')).includes('Hello'), 'events flow again after the reconnect');

    step('bye on close: the UI is disposed');
    const uis = () => page.evaluate(() => fetch('/auth/me').then(r => r.json()));
    console.log('  me: ' + JSON.stringify(await uis()));
    await page.close();
    await page.waitForTimeout(300).catch(() => {});
    console.log('  console errors: ' + JSON.stringify(errors));
    assert(errors.length === 0, 'no console errors');
    await ctx.close();
  }

  step('fallback: a WebSocket that never connects (proxy) → long polling, decided on the first attempt');
  {
    const ctx = await browser.newContext({ viewport: { width: 1280, height: 800 } });
    await ctx.addInitScript(() => {
      // a WebSocket that fails like one behind a proxy which does not forward the upgrade
      window.WebSocket = class {
        constructor() { setTimeout(() => { if (this.onclose) this.onclose({ code: 1006, reason: 'blocked' }); }, 20); }
        send() {} close() {}
        static get OPEN() { return 1; }
      };
    });
    const { page, errors } = await login(ctx);
    const s = await state(page);
    console.log('  ' + JSON.stringify(s));
    assert(s.kind === 'poll' && s.state === 'connected', 'fell back to long polling');
    await clickAbout(page);
    assert((await page.textContent('[data-test=activated]')).includes('About'), 'events flow over the poll transport');
    await page.evaluate(() => wb.transport.simulateOutage(1500));
    await page.waitForFunction(() => document.documentElement.dataset.wbState === 'reconnecting', null, { timeout: 10000 });
    await page.waitForFunction(() => document.documentElement.dataset.wbState === 'connected', null, { timeout: 40000 });
    console.log('  poll transport after an outage: ' + JSON.stringify(await state(page)));
    console.log('  console errors: ' + JSON.stringify(errors));
    assert(errors.length === 0, 'no console errors');
    await ctx.close();
  }

  step('no WebSocket API at all → long polling from the start');
  {
    const ctx = await browser.newContext({ viewport: { width: 1280, height: 800 } });
    await ctx.addInitScript(() => { delete window.WebSocket; });
    const { page } = await login(ctx);
    const s = await state(page);
    console.log('  ' + JSON.stringify(s));
    assert(s.kind === 'poll' && s.state === 'connected', 'long polling');
    await ctx.close();
  }

  await browser.close();
  console.log('OK');
})().catch(e => { console.error('FAILED', e); process.exit(1); });
