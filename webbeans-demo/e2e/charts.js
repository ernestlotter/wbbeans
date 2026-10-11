// Browser end-to-end check of step 6: the dashboard's four charts (live appends, bars with click, donut, zoomable
// scatter), tooltips, legend toggle, wheel zoom + rangeSelect, the chart window's toolbar and export round trip.
// Start the server with --admin-password secret123 --port 8089 and a fresh --datadir, then: npm i playwright && node charts.js
const { chromium } = require('playwright');
const base = process.env.BASE || 'http://localhost:8089';
const step = s => console.log('* ' + s);
const assert = (cond, msg) => { if (!cond) { throw new Error('ASSERT: ' + msg); } };
(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 800 }, acceptDownloads: true });
  const page = await ctx.newPage();
  const errors = [];
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  const settle = (ms) => page.waitForTimeout(ms || 450);
  const chart = id => page.locator('[data-test=' + id + ']');
  const count = (id, sel) => page.evaluate(([i, s]) => document.querySelector('[data-test=' + i + ']').querySelectorAll(s).length, [id, sel]);
  const points = id => page.evaluate(i => { const c = document.querySelector('[data-test=' + i + ']'); return c.series.map(s => s.id + '=' + s.points.length); }, id);
  const status = () => page.textContent('.wb-status-text');
  const menu = text => page.locator('.wb-menubar').getByRole('menuitem', { name: text, exact: true });
  const item = text => page.locator('.wb-dropdown-open .wb-menuitem', { has: page.locator('.wb-text', { hasText: new RegExp('^' + text + '$') }) });
  const centerOf = async (id, sel) => { const b = await chart(id).locator(sel).first().boundingBox(); return [b.x + b.width / 2, b.y + b.height / 2]; };

  step('login, open the dashboard');
  await page.goto(base + '/');
  await page.fill('input[name=username]', 'admin'); await page.fill('input[name=password]', 'secret123'); await page.click('button[type=submit]');
  await page.waitForSelector('[data-test=planets] .wb-nv-row'); await settle(1500);
  await page.locator('.wb-dk-tab[data-tc="DashboardTC"]').click(); await settle(800);
  const charts = await page.evaluate(() => Array.from(document.querySelectorAll('.wb-chart')).map(c => c.dataset.test + ':' + c.clientWidth + 'x' + c.clientHeight));
  console.log('  charts: ' + charts.join(', '));
  assert(charts.length === 4 && charts.every(c => !c.endsWith('0x0')), 'four sized charts');
  console.log('  wind lines=' + await count('chart-wind', 'path.wb-chart-line') + ' areas=' + await count('chart-wind', 'path.wb-chart-area') + ' ticks=' + await count('chart-wind', 'text.wb-chart-tick') + ' band=' + await count('chart-wind', '.wb-chart-band') + ' marker=' + await count('chart-wind', '.wb-chart-marker'));
  assert(await count('chart-wind', 'path.wb-chart-line') === 2 && await count('chart-wind', 'path.wb-chart-area') === 1, 'line + area series');
  assert(await count('chart-wind', '.wb-chart-band') === 1 && await count('chart-wind', '.wb-chart-marker') === 1, 'band and marker drawn');
  console.log('  moons bars=' + await count('chart-moons', 'rect.wb-chart-bar') + ' labels=' + await count('chart-moons', 'text.wb-chart-label') + '  types slices=' + await count('chart-types', 'path.wb-chart-slice') + '  gravity dots=' + await count('chart-gravity', 'circle.wb-chart-dot'));
  assert(await count('chart-moons', 'rect.wb-chart-bar') === 9, 'nine bars');
  assert(await count('chart-types', 'path.wb-chart-slice') === 4, 'four slices');
  assert(await count('chart-gravity', 'circle.wb-chart-dot') === 9, 'nine bubbles');
  const ticks = await page.evaluate(() => Array.from(document.querySelector('[data-test=chart-wind]').querySelectorAll('text.wb-chart-tick')).map(t => t.textContent));
  console.log('  wind ticks: ' + ticks.join(' | '));
  assert(ticks.some(t => /^\d\d:\d\d:\d\d$/.test(t)) && ticks.some(t => /^\d{3}$/.test(t)), 'HH:mm:ss and #,##0 formats');

  step('live appends: one small data delta per second, sliding window of 60');
  const before = await points('chart-wind');
  let deltaBytes = 0;
  await page.evaluate(() => { window.__msgs = []; wb.on('message', m => window.__msgs.push(JSON.stringify(m))); });
  await settle(2600);
  const after = await points('chart-wind');
  const msgs = await page.evaluate(() => window.__msgs);
  const dataMsgs = msgs.filter(m => m.includes('"m":"data"'));
  console.log('  before ' + before.join(',') + ' after ' + after.join(',') + '; data messages=' + dataMsgs.length + ' sizes=' + dataMsgs.map(m => m.length).join(','));
  assert(dataMsgs.length >= 2 && dataMsgs.every(m => m.length < 400), 'small append deltas');
  assert(dataMsgs.every(m => m.includes('"append"') && !m.includes('"points"')), 'appends, no re-send');

  step('tooltip on hover (shared mode: both series), crosshair');
  const [lx, ly] = await centerOf('chart-wind', 'circle.wb-chart-dot');
  await page.mouse.move(lx, ly); await settle(300);
  const tip = await chart('chart-wind').locator('.wb-chart-tip').textContent();
  console.log('  tooltip: ' + tip.replace(/\s+/g, ' ').trim());
  assert(tip.includes('Speed') && tip.includes('Density'), 'shared tooltip lists both series');
  assert(await count('chart-wind', '.wb-chart-crosshair') === 1, 'crosshair');
  await page.mouse.move(5, 400); await settle(200);

  step('legend toggle hides a series and tells the server');
  await chart('chart-wind').locator('.wb-chart-legend-item').nth(1).click(); await settle();
  console.log('  areas after hiding Density: ' + await count('chart-wind', 'path.wb-chart-area') + ' legend hidden=' + await count('chart-wind', '.wb-chart-legend-hidden'));
  assert(await count('chart-wind', 'path.wb-chart-area') === 0, 'area hidden');
  await chart('chart-wind').locator('.wb-chart-legend-item').nth(1).click(); await settle();
  assert(await count('chart-wind', 'path.wb-chart-area') === 1, 'area back');

  step('bar click → pointClick with the category and value');
  const bars = await page.evaluate(() => Array.from(document.querySelector('[data-test=chart-moons]').querySelectorAll('rect.wb-chart-bar')).map(r => r.getBoundingClientRect()).map(b => [b.x + b.width / 2, b.y + b.height / 2, b.height]));
  const tallest = bars.reduce((a, b) => b[2] > a[2] ? b : a);
  await page.mouse.click(tallest[0], tallest[1]); await settle();
  console.log('  status: ' + await status());
  assert((await status()) === 'Saturn: 146 moons', 'bar click resolved the keyed point');

  step('model change → chart update (add a planet through the explorer)');
  await page.locator('.wb-dk-tab[data-tc="DashboardTC"]').click();
  await page.click('[data-test=add-planet]'); await settle(800);
  console.log('  bars now: ' + await count('chart-moons', 'rect.wb-chart-bar') + ' categories: ' + await page.evaluate(() => document.querySelector('[data-test=chart-moons]').categories.join(',')));
  assert(await count('chart-moons', 'rect.wb-chart-bar') === 10, 'ten bars after adding a planet');

  step('wheel zoom on the scatter → rangeSelect (lazy) → status; double-click resets');
  const gb = await chart('chart-gravity').boundingBox();
  await page.mouse.move(gb.x + gb.width / 2, gb.y + gb.height / 2);
  await page.mouse.wheel(0, -300); await settle(600);
  console.log('  status after zoom: ' + await status());
  assert((await status()).startsWith('Zoomed to gravity'), 'range event');
  await page.mouse.dblclick(gb.x + gb.width / 2, gb.y + gb.height / 2); await settle(600);
  console.log('  status after reset: ' + await status());
  assert((await status()) === 'Zoom reset', 'reset event');

  step('brush selection (shift-drag) on the scatter');
  await page.keyboard.down('Shift'); await page.mouse.move(gb.x + gb.width * 0.3, gb.y + gb.height * 0.3); await page.mouse.down(); await page.mouse.move(gb.x + gb.width * 0.7, gb.y + gb.height * 0.7, { steps: 8 }); await page.mouse.up(); await page.keyboard.up('Shift'); await settle(600);
  console.log('  status after brush: ' + await status());
  assert((await status()).startsWith('Zoomed to gravity'), 'brush range event');

  step('chart window: toolbar (reset zoom, save SVG/PNG), horizontal bars, export round trip via a registered action');
  await menu('Window').click(); await settle(); await item('Moons chart').click(); await settle(900);
  console.log('  horizontal bars: ' + await count('chart-moons-h', 'rect.wb-chart-bar') + ' toolbar buttons: ' + await page.locator('.wb-tc:not(.wb-tc-hidden) .wb-toolbar .wb-tbbutton').count());
  assert(await count('chart-moons-h', 'rect.wb-chart-bar') === 10, 'horizontal bars');
  const [dl] = await Promise.all([page.waitForEvent('download'), page.locator('.wb-tc:not(.wb-tc-hidden) .wb-tbbutton[title^="Save as SVG"]').click()]);
  console.log('  download: ' + dl.suggestedFilename());
  assert(dl.suggestedFilename().endsWith('.svg'), 'svg download');
  const [dl2] = await Promise.all([page.waitForEvent('download'), page.locator('.wb-tc:not(.wb-tc-hidden) .wb-tbbutton[title^="Save as PNG"]').click()]);
  console.log('  download: ' + dl2.suggestedFilename());
  await page.locator('.wb-tc:not(.wb-tc-hidden) .wb-tbbutton[title^="Measure PNG export"]').click(); await settle(1500);
  const toast = await page.textContent('.wb-toasts .wb-notif-title');
  console.log('  export toast: ' + toast);
  assert(/PNG export: [\d,.]{3,} bytes, [\d,.]{3,} characters of SVG/.test(toast), 'export futures completed with content');
  await page.click('.wb-toasts .wb-notif-close');

  step('reconnect: the charts come back from a full init (resync)');
  await page.evaluate(() => { wb.seq = 0; }); // force a full init on the next hello
  await page.evaluate(ms => wb.transport.simulateOutage(ms), 1500); // drops the WebSocket (or fails the polls) for a while
  await page.waitForFunction(() => document.documentElement.dataset.wbState === 'reconnecting', null, { timeout: 40000 });
  await page.waitForFunction(() => document.documentElement.dataset.wbState === 'connected', null, { timeout: 40000 });
  await settle(1500);
  await page.locator('.wb-dk-tab[data-tc="DashboardTC"]').click(); await settle(800);
  console.log('  after resync: wind ' + (await points('chart-wind')).join(',') + ' bars=' + await count('chart-moons', 'rect.wb-chart-bar'));
  assert(await count('chart-moons', 'rect.wb-chart-bar') === 10, 'bars after resync');

  const real = errors.filter(e => !/ERR_FAILED|net::/.test(e)); // aborted polls during the forced reconnect
  console.log(real.length ? 'CONSOLE ERRORS:\n' + real.join('\n') : 'no console errors');
  await browser.close();
  if (real.length) process.exit(1);
})().catch(e => { console.error(e); process.exit(1); });
