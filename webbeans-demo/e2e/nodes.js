// Browser end-to-end check of the explorer (outline view, property sheet, context menu, factory restore). Start the
// server with --admin-password secret123 --port 8089 and a fresh --datadir, then: npm i playwright && node nodes.js
const { chromium } = require('playwright');
const base = process.env.BASE || 'http://localhost:8089';
const step = s => console.log('* ' + s);
(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 800 } });
  const page = await ctx.newPage();
  const errors = [];
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  const settle = () => page.waitForTimeout(450);
  const rows = () => page.evaluate(() => Array.from(document.querySelectorAll('[data-test=planets] .wb-nv-row')).map(r => r.querySelector('.wb-nv-label').textContent + (r.classList.contains('wb-nv-selected') ? '*' : '')));
  const row = name => page.locator('[data-test=planets] .wb-nv-row', { has: page.locator('.wb-nv-label', { hasText: new RegExp('^' + name + '$') }) });
  const sheetRows = (sel) => page.evaluate(s => Array.from(document.querySelectorAll(s + ' .wb-ps-row')).map(r => r.dataset.prop + '=' + (r.querySelector('input,select') ? (r.querySelector('input[type=checkbox]') ? r.querySelector('input').checked : r.querySelector('input,select').value) : r.querySelector('.wb-ps-value').textContent)), sel);
  const tab = id => page.locator('.wb-dk-tab[data-tc="' + id + '"]');

  step('login');
  await page.goto(base + '/');
  await page.fill('input[name=username]', 'admin'); await page.fill('input[name=password]', 'secret123'); await page.click('button[type=submit]');
  await page.waitForSelector('[data-test=planets] .wb-nv-row'); await page.waitForTimeout(1200);
  console.log('  rows: ' + (await rows()).join(', '));

  step('select → global context → property sheet');
  await row('Mars').click(); await settle();
  console.log('  ' + (await rows()).filter(r => r.endsWith('*')).join(',') + ' status=' + await page.textContent('.wb-status-text'));
  console.log('  sheet: ' + (await sheetRows('[data-test=sheet]')).join(' | '));

  step('edit in the property sheet: gravity, type, invalid value');
  const gravity = page.locator('[data-test=sheet] .wb-ps-row[data-prop=gravity] input');
  await gravity.fill('3.9'); await gravity.press('Enter'); await settle();
  console.log('  mars row: ' + await row('Mars').textContent());
  await page.selectOption('[data-test=sheet] .wb-ps-row[data-prop=type] select', { label: 'Dwarf planet' }); await settle();
  console.log('  mars row after type: ' + await row('Mars').textContent());
  await gravity.fill('-1'); await gravity.press('Enter'); await settle();
  console.log('  error: ' + (await page.textContent('[data-test=sheet] .wb-ps-row[data-prop=gravity] .wb-ps-err')).trim());
  await gravity.fill('3.71'); await gravity.press('Enter'); await settle();

  step('edit an outline cell in place');
  const moonsCell = row('Mars').locator('.wb-ov-cell[data-col=moons]');
  await moonsCell.dblclick(); await page.keyboard.type('3'); await page.keyboard.press('Enter'); await settle();
  console.log('  mars moons cell: ' + await moonsCell.textContent() + ' sheet moons: ' + (await sheetRows('[data-test=sheet]')).find(x => x.startsWith('moons')));

  step('rename with F2');
  await row('Pluto').click(); await page.keyboard.press('F2'); await page.keyboard.type('Pluto IX'); await page.keyboard.press('Enter'); await settle();
  console.log('  rows: ' + (await rows()).join(', '));

  step('multi-select + context menu (common actions) + keyboard navigation');
  await row('Mercury').click(); await row('Earth').click({ modifiers: ['Shift'] }); await settle();
  console.log('  selected: ' + (await rows()).filter(r => r.endsWith('*')).join(',') + ' sheet title: ' + await page.textContent('[data-test=sheet] .wb-ps-title'));
  await row('Earth').click({ button: 'right' }); await settle();
  console.log('  menu: ' + (await page.locator('.wb-dropdown-open .wb-menuitem .wb-text').allTextContents()).join(' / '));
  await page.keyboard.press('Escape'); await settle();
  await row('Venus').click(); await page.keyboard.press('ArrowDown'); await settle();
  console.log('  after ArrowDown: ' + (await rows()).filter(r => r.endsWith('*')).join(','));

  step('double-click opens a multi-instance window with the node in its lookup');
  await page.evaluate(() => { window.__msgs = []; wb.on('message', m => window.__msgs.push(m)); });
  await row('Jupiter').locator('.wb-nv-label').dblclick(); await settle();
  console.log('  msgs: ' + JSON.stringify(await page.evaluate(() => window.__msgs.map(m => m.ops.map(o => o.op + ':' + o.id + (o.m ? '.' + o.m : '') + (o.type ? '(' + o.type + ')' : ''))))).slice(0, 1500));
  console.log('  editor tabs: ' + await page.evaluate(() => Array.from(document.querySelectorAll('.wb-mode[data-mode=editor] .wb-dk-tab')).map(t => t.dataset.tc).join(',')) + ' active=' + await page.textContent('[data-test=activated]'));
  console.log('  properties follow the window: ' + (await sheetRows('[data-test=sheet]')).slice(0, 2).join(' | '));
  const sheet2 = page.locator('.wb-tc:not(.wb-tc-hidden) [data-test=planet-sheet] .wb-ps-row[data-prop=moons] input');
  console.log('  planet sheets: ' + await page.locator('[data-test=planet-sheet]').count() + ' rows ' + await page.locator('[data-test=planet-sheet] .wb-ps-row').count() + ' html=' + (await page.locator('[data-test=planet-sheet]').innerHTML()).slice(0, 160));
  await sheet2.fill('96'); await sheet2.press('Enter'); await settle();
  console.log('  jupiter moons in outline: ' + await row('Jupiter').locator('.wb-ov-cell[data-col=moons]').textContent());

  step('context-menu action: Open, and Delete via key');
  await row('Saturn').click({ button: 'right' }); await settle();
  await page.locator('.wb-dropdown-open .wb-menuitem', { has: page.locator('.wb-text', { hasText: /^Open$/ }) }).click(); await settle();
  console.log('  editor tabs: ' + await page.evaluate(() => Array.from(document.querySelectorAll('.wb-mode[data-mode=editor] .wb-dk-tab')).map(t => t.dataset.tc).join(',')));
  await row('Neptune').click(); await page.keyboard.press('Delete'); await settle();
  await page.locator('[data-test=dialog-yes]').click(); await settle(); // the DialogDisplayer confirmation (step 5)
  console.log('  rows: ' + (await rows()).join(', '));
  const log = await page.inputValue('[data-test=log] textarea');
  console.log('  events: ' + log.trim().split('\n').filter(l => /Renamed|Deleted|opened/.test(l)).join(' ; '));

  step('add a planet (model change → factory refresh → tree)');
  await page.click('[data-test=add-planet]'); await settle();
  console.log('  rows: ' + (await rows()).length + ' last=' + (await rows()).pop());

  step('sort by gravity (client side)');
  await page.click('.wb-ov-head[data-col=gravity]'); await settle();
  console.log('  ' + (await rows()).slice(1, 4).join(', '));
  await page.click('.wb-ov-head[data-col=gravity]'); await settle();
  console.log('  ' + (await rows()).slice(1, 4).join(', '));
  await page.click('.wb-ov-head[data-col=gravity]'); await settle();

  step('collapse / expand root; quick search');
  await page.locator('[data-test=planets] .wb-nv-row').first().locator('.wb-nv-toggle').click(); await settle();
  console.log('  collapsed rows: ' + (await rows()).length);
  await page.locator('[data-test=planets] .wb-nv-row').first().locator('.wb-nv-toggle').click(); await settle();
  await page.locator('[data-test=planets] .wb-nv-list').focus(); await page.keyboard.type('ur'); await settle();
  console.log('  quick search "ur": ' + (await rows()).filter(r => r.endsWith('*')).join(','));

  step('persistence: reload restores the Jupiter/Saturn windows through the Factory');
  await page.waitForTimeout(2500);
  await page.reload(); await page.waitForSelector('[data-test=planets] .wb-nv-row'); await page.waitForTimeout(1200);
  console.log('  editor tabs: ' + await page.evaluate(() => Array.from(document.querySelectorAll('.wb-mode[data-mode=editor] .wb-dk-tab')).map(t => t.dataset.tc + ':' + t.textContent.trim()).join(',')));
  console.log('  rows after reload: ' + (await rows()).length + ' (session model keeps Pluto IX/no Neptune: ' + (await rows()).includes('Pluto IX') + ')');

  step('reconnect resyncs the tree (init replay of call-delivered state)');
  await page.evaluate(() => { wb.seq = 0; }); // force a full init on the next hello
  await page.evaluate(ms => wb.transport.simulateOutage(ms), 1500); // drops the WebSocket (or fails the polls) for a while
  await page.waitForFunction(() => document.documentElement.dataset.wbState === 'reconnecting', null, { timeout: 40000 });
  await page.waitForFunction(() => document.documentElement.dataset.wbState === 'connected', null, { timeout: 40000 });
  await page.waitForTimeout(800);
  console.log('  rows after resync: ' + (await rows()).length);

  await page.screenshot({ path: 'nodes2.png' });
  console.log('console errors: ' + JSON.stringify(errors.filter(e => !/ERR_FAILED|410/.test(e))));
  await browser.close();
})().catch(e => { console.error('FAILED', e); process.exit(1); });
