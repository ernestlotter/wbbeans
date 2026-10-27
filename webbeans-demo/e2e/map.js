// Browser check of the MapView (Sites map window in the demo): tiles endpoint, overlays, layers, pan/zoom/view
// events, click → popup, marker drag, hover, drawing, GeoJSON features, basemap switch, graticule.
const { chromium } = require('playwright');
const base = process.env.BASE || 'http://localhost:8089';
const step = s => console.log('* ' + s);
const assert = (c, m) => { if (!c) throw new Error('ASSERT: ' + m); };
(async () => {
  const browser = await chromium.launch();
  const page = await (await browser.newContext({ viewport: { width: 1400, height: 900 } })).newPage();
  const errors = []; const tileRequests = [];
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  page.on('request', r => { if (r.url().includes('/wb/tiles/')) tileRequests.push(r.url()); });
  const settle = ms => page.waitForTimeout(ms || 500);
  const map = '[data-test=sites-map]';
  const markers = () => page.evaluate(s => Array.from(document.querySelectorAll(s + ' .wb-map-marker')).filter(m => m.style.display !== 'none').map(m => ({ id: m.dataset.id, label: (m.querySelector('.wb-map-markerlabel') || {}).textContent, t: m.style.transform })), map);
  const shapes = () => page.evaluate(s => Array.from(document.querySelectorAll(s + ' .wb-map-shape')).filter(m => m.style.display !== 'none').map(m => m.dataset.id + ':' + m.getAttribute('class').split(' ')[1]), map);
  const status = () => page.textContent('.wb-status-text');
  const center = async (id) => { const b = await page.locator(map + ' .wb-map-marker[data-id="' + id + '"] .wb-map-markericon').boundingBox(); return { x: b.x + b.width / 2, y: b.y + b.height - 4 }; };

  step('login and open the Sites map');
  await page.goto(base + '/');
  await page.fill('input[name=username]', 'admin'); await page.fill('input[name=password]', 'secret123'); await page.click('button[type=submit]');
  await page.waitForSelector('.wb-menubar'); await settle(1500);
  await page.locator('.wb-menubar').getByRole('menuitem', { name: 'Window', exact: true }).click(); await settle();
  await page.locator('.wb-dropdown-open .wb-menuitem', { has: page.locator('.wb-text', { hasText: /^Sites map$/ }) }).click();
  await page.waitForSelector(map + ' .wb-map-markericon'); await settle(1500);
  console.log('  markers: ' + (await markers()).map(m => m.id + '=' + m.label).join(', '));
  assert((await markers()).length === 5, '5 markers');
  console.log('  shapes: ' + (await shapes()).join(' '));
  assert((await shapes()).length === 4, 'line, circle, polygon, geojson');
  console.log('  tile requests through the proxy: ' + tileRequests.length + ' e.g. ' + (tileRequests[0] || '').replace(base, ''));
  assert(tileRequests.length > 0 && /\/wb\/tiles\/osm\/\d+\/\d+\/\d+$/.test(tileRequests[0]), 'tiles come from the server endpoint');
  const features = await page.locator(map + ' .wb-map-feature').count(); console.log('  geojson features: ' + features); assert(features === 2, 'two features');
  console.log('  layer control: ' + (await page.locator(map + ' .wb-map-layerrow').allTextContents()).join(', '));
  console.log('  scale: ' + await page.textContent(map + ' .wb-map-scale') + ' attribution: ' + await page.textContent(map + ' .wb-map-attr'));
  console.log('  status: ' + await status());

  step('switch to the blank basemap (toolbar context action) → graticule');
  await page.locator('.wb-toolbar [title="Toggle base map (OpenStreetMap / none)"]').click(); await settle();
  const grat = await page.locator(map + ' .wb-map-gratline').count(); console.log('  graticule lines: ' + grat); assert(grat > 4, 'graticule drawn');
  await page.screenshot({ path: 'map-1.png' });

  step('click a marker → popup from the server; click the map → popup; close it');
  const s1 = await center('s1'); await page.mouse.click(s1.x, s1.y); await settle();
  const popup = await page.locator(map + ' .wb-map-popupbody').first().innerHTML(); console.log('  popup: ' + popup.slice(0, 80));
  assert(/Mount Wellington/.test(popup) && /m from HQ/.test(popup), 'marker popup');
  const box = await page.locator(map).boundingBox(); const at = (fx, fy) => ({ x: box.x + box.width * fx, y: box.y + box.height * fy });
  const log = () => page.inputValue('[data-test=log] textarea');
  const empty = at(0.1, 0.15); await page.mouse.click(empty.x, empty.y); await settle(700);
  console.log('  log: ' + (await log()).trim().split('\n').slice(-1)[0]); assert(/Clicked -4\d\.\d+,14\d\.\d+/.test(await log()), 'map click reports a position');
  await page.locator(map + ' .wb-map-popupclose').first().click(); await settle();
  assert(await page.locator(map + ' .wb-map-popup:not([style*="display: none"])').count() === 0, 'popup closed');

  step('drag a marker → DRAG event applied on the server');
  const s3 = await center('s3');
  await page.mouse.move(s3.x, s3.y); await page.mouse.down(); await page.mouse.move(s3.x + 40, s3.y + 30); await page.mouse.move(s3.x + 80, s3.y + 60); await page.mouse.up(); await settle();
  console.log('  log: ' + (await log()).trim().split('\n').slice(-1)[0]); assert(/Richmond moved to/.test(await log()), 'drag reported');

  step('pan and wheel zoom → VIEW events; zoom control');
  const p0 = at(0.5, 0.5); await page.mouse.move(p0.x, p0.y); await page.mouse.down(); await page.mouse.move(p0.x + 50, p0.y + 20); await page.mouse.move(p0.x + 100, p0.y + 60); await page.mouse.up(); await settle(600);
  console.log('  status: ' + await status()); assert(/Map at .* zoom 10/.test(await status()), 'view after pan');
  await page.mouse.wheel(0, -600); await settle(600);
  console.log('  status: ' + await status()); assert(/zoom 1[12]/.test(await status()), 'zoomed in with the wheel');
  await page.locator(map + ' .wb-map-zbtn').nth(1).click(); await settle(800);
  console.log('  status: ' + await status());

  step('hover over the coverage circle is passive; hovering the route reports it');
  const route = await page.locator(map + ' .wb-map-shape[data-id=route] .wb-map-hit').boundingBox();
  await page.mouse.move(route.x + route.width / 2, route.y + route.height / 2); await settle(400);
  console.log('  status: ' + await status());

  step('layer toggle hides the zones');
  await page.locator(map + ' .wb-map-layerrow', { hasText: 'Zones' }).locator('input').click(); await settle();
  console.log('  shapes now: ' + (await shapes()).join(' ')); assert((await shapes()).length === 1, 'only the route left');
  await page.locator(map + ' .wb-map-layerrow', { hasText: 'Zones' }).locator('input').click(); await settle();

  step('draw a polygon with the toolbar tool, then a rectangle by drag');
  await page.locator('.wb-toolbar [title="Draw an area"]').click(); await settle();
  assert(!(await page.locator(map + ' .wb-map-hint').evaluate(e => e.classList.contains('wb-hidden'))), 'hint shown');
  const a = at(0.3, 0.3), b = at(0.4, 0.3), c = at(0.4, 0.45), d = at(0.3, 0.45);
  await page.mouse.click(a.x, a.y); await page.mouse.click(b.x, b.y); await page.mouse.click(c.x, c.y); await page.mouse.dblclick(d.x, d.y); await settle();
  console.log('  log: ' + (await log()).trim().split('\n').slice(-1)[0]); assert(/Drew a polygon/.test(await log()), 'polygon drawn');
  await page.locator('.wb-toolbar [title="Draw a box"]').click(); await settle();
  const r0 = at(0.65, 0.3); await page.mouse.move(r0.x, r0.y); await page.mouse.down(); await page.mouse.move(r0.x + 50, r0.y + 50); await page.mouse.move(r0.x + 100, r0.y + 100); await page.mouse.up(); await settle();
  assert(/Drew a rectangle/.test(await log()), 'rectangle drawn');
  await page.locator('.wb-toolbar [title="Draw a box"]').click(); await settle();   // off again
  console.log('  shapes: ' + (await shapes()).join(' '));
  assert((await shapes()).some(s => s.startsWith('drawn1:')) && (await shapes()).some(s => s.startsWith('drawn2:')), 'drawn overlays added by the listener');

  step('fit everything from the toolbar');
  await page.locator('.wb-toolbar [title="Show everything"]').click(); await settle(800);
  console.log('  status: ' + await status());
  await page.screenshot({ path: 'map-2.png' });
  console.log(errors.length ? 'ERRORS ' + errors.join('; ') : 'no page errors');
  await browser.close();
})().catch(e => { console.error('FAILED', e); process.exit(1); });
