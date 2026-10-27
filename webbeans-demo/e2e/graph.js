// Browser check of the GraphScene (Deployment window in the demo): rendering, measure, selection → Properties,
// drag, connect, rename, popup, delete, layout animation, export. Start the server with --admin-password secret123
// --port 8089 and a fresh --datadir, then: npm i playwright && node graph.js
const { chromium } = require('playwright');
const base = process.env.BASE || 'http://localhost:8089';
const step = s => console.log('* ' + s);
const assert = (c, m) => { if (!c) throw new Error('ASSERT: ' + m); };
(async () => {
  const browser = await chromium.launch();
  const page = await (await browser.newContext({ viewport: { width: 1400, height: 900 } })).newPage();
  const errors = [];
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  const settle = ms => page.waitForTimeout(ms || 500);
  const scene = '[data-test=deployment]';
  const nodes = () => page.evaluate(s => Array.from(document.querySelectorAll(s + ' .wb-graph-node')).map(g => ({ id: g.dataset.id, label: (g.querySelector('.wb-graph-label') || {}).textContent, t: g.getAttribute('transform'), sel: g.classList.contains('wb-selected'), kind: g.dataset.kind, pins: g.querySelectorAll('.wb-graph-pin').length })), scene);
  const edges = () => page.evaluate(s => Array.from(document.querySelectorAll(s + ' .wb-graph-edge')).map(g => ({ id: g.dataset.id, d: g.querySelector('.wb-graph-edge-line').getAttribute('d').slice(0, 40), me: g.querySelector('.wb-graph-edge-line').getAttribute('marker-end'), label: (g.querySelector('.wb-graph-edgelabel-text') || {}).textContent, dash: g.querySelector('.wb-graph-edge-line').getAttribute('class') })), scene);
  const notes = () => page.evaluate(s => Array.from(document.querySelectorAll(s + ' .wb-graph-note')).map(g => ({ id: g.dataset.id, lines: g.querySelectorAll('.wb-graph-notetext').length })), scene);
  const center = async (id) => { const b = await page.locator(scene + ' [data-id="' + id + '"] > .wb-graph-shape').boundingBox(); return { x: b.x + b.width / 2, y: b.y + b.height / 2, b }; };
  const nodeByLabel = async (l) => (await nodes()).find(n => n.label === l);

  step('login and open the Deployment window');
  await page.goto(base + '/');
  await page.fill('input[name=username]', 'admin'); await page.fill('input[name=password]', 'secret123'); await page.click('button[type=submit]');
  await page.waitForSelector('.wb-menubar'); await settle(1500);
  await page.locator('.wb-menubar').getByRole('menuitem', { name: 'Window', exact: true }).click(); await settle();
  await page.locator('.wb-dropdown-open .wb-menuitem', { has: page.locator('.wb-text', { hasText: /^Deployment graph$/ }) }).click();
  await page.waitForSelector(scene + ' .wb-graph-node'); await settle(1200);
  const ns = await nodes(); console.log('  nodes: ' + ns.map(n => n.label + '[' + n.kind + ',pins=' + n.pins + ']').join(' '));
  assert(ns.length === 6, '6 nodes');
  const es = await edges(); console.log('  edges: ' + es.map(e => e.label + ':' + e.me + ':' + e.dash.replace('wb-graph-edge-line ', '')).join(' | '));
  assert(es.length === 6 && es.every(e => e.d.startsWith('M')), '6 edges with paths');
  assert(es.some(e => /dashed/.test(e.dash)) && es.some(e => /dotted/.test(e.dash)), 'line styles');
  const nts = await notes(); console.log('  notes: ' + JSON.stringify(nts)); assert(nts.length === 2 && nts[0].lines >= 2, 'notes wrapped');
  assert(await page.locator(scene + ' .wb-graph-notelink').count() === 1, 'note connector');
  assert(await page.locator(scene + ' .wb-graph-minimap .wb-graph-mininode').count() === 6, 'minimap');
  await page.screenshot({ path: 'graph-1.png' });

  step('click selects a node; double-click on an edge activates it');
  const web = await center((await nodeByLabel('web')).id);
  await page.mouse.click(web.x, web.y); await settle();
  assert((await nodeByLabel('web')).sel, 'web selected');
  const lbl = await page.locator(scene + ' .wb-graph-edge .wb-graph-edgelabel-text', { hasText: /^SQL$/ }).boundingBox();
  await page.mouse.dblclick(lbl.x + lbl.width / 2, lbl.y + lbl.height / 2); await settle();
  console.log('  status: ' + await page.textContent('.wb-status-text'));
  assert(/Opened api→orders-db/.test(await page.textContent('.wb-status-text')), 'activate event with the edge object');

  step('drag api 120px right → move event → server location');
  const api = await center((await nodeByLabel('api')).id); const apiBefore = (await nodeByLabel('api')).t;
  await page.mouse.move(api.x, api.y); await page.mouse.down(); await page.mouse.move(api.x + 60, api.y + 5); await page.mouse.move(api.x + 120, api.y + 10); await page.mouse.up(); await settle();
  const apiAfter = (await nodeByLabel('api')).t; console.log('  api: ' + apiBefore + ' → ' + apiAfter); assert(apiBefore !== apiAfter, 'moved');
  const esAfter = await edges(); assert(esAfter.some((e, i) => e.d !== es[i].d), 'edges re-routed');

  step('connect: drag from the web node handle onto the queue node');
  const webN = await nodeByLabel('web'); const handle = await page.locator(scene + ' [data-id="' + webN.id + '"] .wb-graph-connect-handle').boundingBox();
  const queue = await center((await nodeByLabel('events')).id);
  await page.mouse.move(web.x, web.y); await settle(200);
  await page.mouse.move(handle.x + handle.width / 2, handle.y + handle.height / 2); await page.mouse.down(); await page.mouse.move(queue.x - 30, queue.y - 30); await page.mouse.move(queue.x, queue.y); await page.mouse.up(); await settle();
  console.log('  edges now: ' + (await edges()).length + ' status: ' + await page.textContent('.wb-status-text'));
  assert((await edges()).length === 7, 'edge added by the ConnectProvider');

  step('rename in place (F2) and via double-click on an editable node');
  await page.mouse.click(web.x, web.y); await page.keyboard.press('F2'); await settle(300);
  await page.keyboard.type('web-tier'); await page.keyboard.press('Enter'); await settle();
  assert(!!(await nodeByLabel('web-tier')), 'renamed');

  step('right-click popup from the PopupProvider: toggle state');
  const wt = await center((await nodeByLabel('web-tier')).id);
  await page.mouse.click(wt.x, wt.y, { button: 'right' }); await settle();
  const items = await page.locator('.wb-dropdown-open .wb-menuitem .wb-text').allTextContents(); console.log('  popup: ' + items.join(' / '));
  assert(items.includes('Toggle up/down') && items.includes('Add note'), 'popup items');
  await page.locator('.wb-dropdown-open .wb-menuitem', { has: page.locator('.wb-text', { hasText: /^Toggle up\/down$/ }) }).click(); await settle();
  const accentAfter = await page.evaluate((s) => Array.from(document.querySelectorAll(s + ' .wb-graph-node')).map(g => (g.querySelector('.wb-graph-accent') || { style: {} }).style.fill), scene);
  console.log('  accents: ' + accentAfter.join(',')); assert(accentAfter[1] === 'rgb(180, 59, 59)', 'web-tier toggled to down');

  step('marquee select two nodes and Delete them');
  const n1 = await center((await nodeByLabel('orders-db')).id), n2 = await center((await nodeByLabel('orders-replica')).id);
  const x0 = Math.min(n1.b.x, n2.b.x) - 30, y0 = Math.min(n1.b.y, n2.b.y) - 30, x1 = Math.max(n1.b.x + n1.b.width, n2.b.x + n2.b.width) + 30, y1 = Math.max(n1.b.y + n1.b.height, n2.b.y + n2.b.height) + 30;
  await page.keyboard.down('Shift'); await page.mouse.move(x0, y0); await page.mouse.down(); await page.mouse.move(x1, y1, { steps: 4 }); await page.mouse.up(); await page.keyboard.up('Shift'); await settle();
  console.log('  selected: ' + (await nodes()).filter(n => n.sel).map(n => n.label).join(','));
  assert((await nodes()).filter(n => n.sel).length === 2, 'two selected by marquee');
  await page.keyboard.press('Delete'); await settle();
  console.log('  nodes after delete: ' + (await nodes()).length + ' edges: ' + (await edges()).length + ' notelinks: ' + await page.locator(scene + ' .wb-graph-notelink').count());
  assert((await nodes()).length === 4, 'nodes removed'); assert(await page.locator(scene + ' .wb-graph-notelink').count() === 0, 'note detached');

  step('Arrange (tree layout, animated) and Fit from the toolbar; wheel zoom');
  const before = (await nodes()).map(n => n.t).join(';');
  await page.locator('.wb-toolbar [title="Arrange"]').click(); await settle(800);
  const after = (await nodes()).map(n => n.t).join(';'); assert(before !== after, 'layout moved nodes');
  await page.locator('.wb-toolbar [title="Fit to view"]').click(); await settle();
  const view = () => page.evaluate(s => document.querySelector(s + ' .wb-graph-world').getAttribute('transform'), scene);
  const v1 = await view(); await page.mouse.move(web.x, web.y); await page.mouse.wheel(0, -300); await settle(); const v2 = await view();
  console.log('  view: ' + v1 + ' → ' + v2); assert(v1 !== v2, 'zoomed');
  await page.screenshot({ path: 'graph-2.png' });

  step('export SVG');
  const svg = await page.evaluate(s => { const el = document.querySelector(s); return wb.renderers['wb.GraphScene'].inlineSvg(el).length; }, scene);
  console.log('  svg length: ' + svg); assert(svg > 2000, 'svg export');
  console.log(errors.length ? 'ERRORS ' + errors.join('; ') : 'no page errors');
  await browser.close();
})().catch(e => { console.error('FAILED', e); process.exit(1); });
