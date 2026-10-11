// Browser end-to-end check of step 11: the tier-1 components. Start the server with --admin-password secret123
// --port 8089 and a fresh --datadir, then: npm i playwright && node components.js
//   Components window: NumberField (locale formatting, spinner, range error), Slider, RadioGroup, DatePicker,
//   DateTimePicker, Form validity → button enablement, Tree lazy children → ListBox in a ScrollPane inside a
//   SplitPane (drag the divider), Upload (drop a file → server reads it), DownloadService (offer + link).
//   Orders window: a 10 000-row Table — virtual scrolling, sort, filter, selection, keyboard, inline edit with
//   a rejected value, row context menu → popup action, keyed updates, add/delete.
const { chromium } = require('playwright');
const base = process.env.BASE || 'http://localhost:8089';
const step = s => console.log('* ' + s);
const assert = (cond, msg) => { if (!cond) { throw new Error('ASSERT: ' + msg); } };
(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1400, height: 900 }, acceptDownloads: true });
  const page = await ctx.newPage();
  const errors = [];
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  const settle = (ms) => page.waitForTimeout(ms || 400);
  const menu = text => page.locator('.wb-menubar').getByRole('menuitem', { name: text, exact: true });
  const item = text => page.locator('.wb-dropdown-open .wb-menuitem', { has: page.locator('.wb-text', { hasText: new RegExp('^' + text + '$') }) });
  const T = id => page.locator('[data-test=' + id + ']');

  step('login, open the Components window');
  await page.goto(base + '/');
  await page.fill('input[name=username]', 'admin'); await page.fill('input[name=password]', 'secret123'); await page.click('button[type=submit]');
  await page.waitForSelector('.wb-dock .wb-mode'); await settle();
  await menu('Window').click(); await settle(); await item('Components').click(); await settle(800);
  assert(await page.isVisible('[data-test=qty]'), 'gallery open');

  step('NumberField: en-US formatting, spinner, range validation');
  const qty = T('qty').locator('input');
  console.log('  qty=' + await qty.inputValue() + ' price=' + await T('price').locator('input').inputValue());
  assert(await T('price').locator('input').inputValue() === '19.99', 'price formatted with 2 decimals');
  await T('qty').locator('.wb-spin').first().click(); await settle(300);
  assert(await qty.inputValue() === '2', 'spinner up → 2, got ' + await qty.inputValue());
  await qty.fill('1500'); await qty.press('Tab'); await settle(500);
  const err = await T('qty').locator('.wb-field-error').textContent();
  console.log('  range error: ' + err);
  assert(/at most 999/.test(err), 'range error from the server');
  assert(!(await T('place').isEnabled()), 'Place order disabled while the form is invalid');
  await qty.fill('12'); await qty.press('Tab'); await settle(500);
  assert((await T('qty').locator('.wb-field-error').textContent()) === '', 'error cleared');
  assert(await T('place').isEnabled(), 'Place order enabled again');
  await T('price').locator('input').fill('1234.5'); await T('price').locator('input').press('Tab'); await settle(400);
  console.log('  price reformatted: ' + await T('price').locator('input').inputValue());
  assert((await T('price').locator('input').inputValue()) === '1,234.5', 'grouping separator applied on commit');
  await qty.fill('abc'); await qty.press('Tab'); await settle(500);
  console.log('  not a number → ' + await T('qty').locator('.wb-field-error').textContent() + ', value restored to ' + await qty.inputValue());
  assert((await qty.inputValue()) === '12', 'rejected text replaced by the last valid value');
  assert(!(await T('place').isEnabled()), 'form invalid while the error shows');
  await qty.fill('12'); await qty.press('Tab'); await settle(500);
  assert((await T('qty').locator('.wb-field-error').textContent()) === '' && await T('place').isEnabled(), 're-committing the valid value clears the error');

  step('Slider, RadioGroup, DatePicker, DateTimePicker, then Place order');
  await T('discount').locator('input[type=range]').fill('25'); await settle(300);
  assert((await T('discount').locator('.wb-slider-value').textContent()) === '25', 'slider value label');
  await T('shipping').locator('input[value=express]').check(); await settle(300);
  await T('delivery').locator('input').fill('2030-05-06'); await T('delivery').locator('input').press('Tab'); await settle(300);
  await T('pickup').locator('input').fill('2030-05-06T14:30'); await T('pickup').locator('input').press('Tab'); await settle(300);
  await T('reference').locator('input').fill('ORD-42'); await T('reference').locator('input').press('Tab'); await settle(300);
  await T('place').click(); await settle(600);
  const summary = await T('summary').textContent();
  console.log('  ' + summary);
  assert(/12 × 1,234.5, 25% off, express, delivery May 6, 2030, pickup May 6, 2030, 2:30\sPM/.test(summary), 'summary reflects every field, formatted server-side');

  step('DownloadService: offer() triggers a save; a link produces content on click');
  const dl1 = page.waitForEvent('download');
  await T('export').click();
  const d1 = await dl1;
  const body1 = require('fs').readFileSync(await d1.path(), 'utf8');
  console.log('  ' + d1.suggestedFilename() + ': ' + body1.split('\n')[1]);
  assert(d1.suggestedFilename() === 'order.csv' && body1.startsWith('quantity,price') && body1.includes('12,1234.5,25.0,express,2030-05-06'), 'csv download');
  const dl2 = page.waitForEvent('download');
  await T('ref-link').click();
  const d2 = await dl2;
  assert(d2.suggestedFilename() === 'reference.txt' && require('fs').readFileSync(await d2.path(), 'utf8') === 'reference=ORD-42\n', 'link download produced on click');

  step('Upload: drop a file, the server reads it');
  const dt = await page.evaluateHandle(() => { const dt = new DataTransfer(); dt.items.add(new File(['first line\nsecond line\n'], 'notes.txt', { type: 'text/plain' })); return dt; });
  await T('upload').locator('.wb-upload-zone').dispatchEvent('drop', { dataTransfer: dt });
  await page.waitForFunction(() => (document.querySelector('[data-test=uploaded] .wb-text') || {}).textContent.includes('notes.txt'), null, { timeout: 10000 });
  console.log('  ' + await T('uploaded').textContent());
  assert(/Received notes.txt \(23 bytes, text\/plain\): first line/.test(await T('uploaded').textContent()), 'upload event with the file content');
  assert((await T('upload').locator('.wb-upload-item.wb-done').count()) === 1, 'progress row marked done');

  step('Tree (lazy children) → ListBox (multi) in a ScrollPane; SplitPane divider');
  const tree = T('tree');
  assert((await tree.locator('.wb-tree-row').count()) === 3, 'three roots');
  await tree.locator('.wb-tree-row', { hasText: 'Europe' }).locator('.wb-tree-toggle').click(); await settle(500);
  assert((await tree.locator('.wb-tree-row').count()) === 6, 'Europe expanded with 3 children, got ' + await tree.locator('.wb-tree-row').count());
  await tree.locator('.wb-tree-row', { has: page.locator('.wb-text', { hasText: /^Germany$/ }) }).click(); await settle(500);
  const cities = T('cities');
  console.log('  cities: ' + (await cities.locator('.wb-list-row .wb-text').allTextContents()).join(','));
  assert((await cities.locator('.wb-list-row').count()) === 3, 'list filled from the selected node');
  await cities.locator('.wb-list-row', { hasText: 'Berlin' }).click();
  await cities.locator('.wb-list-row', { hasText: 'Hamburg' }).click({ modifiers: ['Shift'] }); await settle(500);
  console.log('  ' + await T('tree-summary').textContent());
  assert(/\[Germany\] selected in the tree, \[Berlin, Munich, Hamburg\] in the list/.test(await T('tree-summary').textContent()), 'shift-click range selection reported to the server');
  await cities.press('ArrowUp'); await settle(300);
  assert(/\[Munich\]/.test(await T('tree-summary').textContent()), 'keyboard moves the selection');
  const divider = T('split').locator('.wb-split-divider').first();
  const box = await divider.boundingBox();
  await page.mouse.move(box.x + 2, box.y + 100); await page.mouse.down(); await page.mouse.move(box.x + 120, box.y + 100, { steps: 5 }); await page.mouse.up(); await settle(500);
  const firstW = await T('split').locator('.wb-split-pane').first().evaluate(e => e.offsetWidth);
  console.log('  divider dragged, first pane ' + firstW + 'px; status: ' + await page.textContent('.wb-statusbar'));
  assert(firstW > 230 && (await page.textContent('.wb-statusbar')).includes('Divider at ' + firstW + ' px'), 'split position reported');

  step('Orders: 10 000 rows, virtual scrolling');
  await menu('Window').click(); await settle(); await item('Orders').click(); await settle(1000);
  const grid = T('orders');
  const bodySel = '[data-test=orders] .wb-table-body';
  await page.waitForFunction(s => document.querySelector(s + ' .wb-table-row[data-key]'), bodySel, { timeout: 10000 });
  const rendered = await grid.locator('.wb-table-row[data-key]').count();
  const spacerH = await grid.locator('.wb-table-spacer').evaluate(e => e.offsetHeight);
  console.log('  rendered rows=' + rendered + ' spacer=' + spacerH + 'px status=' + await T('orders-status').textContent());
  assert(rendered < 80 && spacerH === 10000 * 28, 'only the window is rendered; the spacer spans every row');
  assert(/0 selected, 10,000 rows|0 selected, 10000 rows/.test(await T('orders-status').textContent()), 'status shows the row count');
  await page.evaluate(s => { document.querySelector(s).scrollTop = 28 * 5000; }, bodySel); await settle(800);
  const midKeys = await grid.locator('.wb-table-row[data-key]').evaluateAll(rows => rows.map(r => Number(r.dataset.key)).sort((a, b) => a - b));
  console.log('  after scrolling to the middle: first key ' + midKeys[0] + ' … ' + midKeys[midKeys.length - 1]);
  assert(midKeys[0] > 4900 && midKeys[0] < 5100, 'rows around 5000 were fetched on demand');

  step('sort by Total desc, then filter');
  await grid.locator('.wb-th', { hasText: 'Total' }).click(); await settle(800);
  await grid.locator('.wb-th', { hasText: 'Total' }).click(); await settle(800);
  await page.evaluate(s => { document.querySelector(s).scrollTop = 0; }, bodySel); await settle(600);
  const totals = await grid.locator('.wb-table-row[data-key] .wb-td:nth-child(5) .wb-text').allTextContents();
  const nums = totals.slice(0, 10).map(t => Number(t.replace(/,/g, '')));
  console.log('  top totals: ' + nums.slice(0, 5).join(' > '));
  assert(nums.every((n, i) => i === 0 || n <= nums[i - 1]) && nums[0] > 2000, 'sorted descending by total');
  await grid.locator('.wb-table-filter input').fill('Wonka'); await settle(1200);
  const filtered = await T('orders-status').textContent();
  const firstCustomer = await grid.locator('.wb-table-row[data-key] .wb-td:nth-child(2) .wb-text').first().textContent();
  const filteredTotal = await grid.locator('.wb-table-spacer').evaluate(e => e.offsetHeight / 28);
  console.log('  filter "Wonka": ' + filteredTotal + ' rows, first customer ' + firstCustomer);
  assert(filteredTotal > 100 && filteredTotal < 1500 && /Wonka/.test(firstCustomer), 'server-side filter');
  await grid.locator('.wb-table-filter input').fill(''); await settle(1200);

  step('selection: click, shift-click, keyboard; row menu → Mark paid updates in place');
  const row = i => grid.locator('.wb-table-row[data-key]').nth(i);
  await row(0).click(); await settle(300);
  await row(3).click({ modifiers: ['Shift'] }); await settle(400);
  console.log('  ' + await T('orders-status').textContent());
  assert(/^4 selected/.test(await T('orders-status').textContent()), 'four rows selected');
  await page.keyboard.press('ArrowDown'); await settle(400);
  assert(/^1 selected/.test(await T('orders-status').textContent()), 'arrow key moves to a single row');
  const key = await row(4).getAttribute('data-key');
  await row(4).click({ button: 'right' }); await settle(400);
  console.log('  popup items: ' + (await page.locator('.wb-dropdown-open .wb-menuitem .wb-text').allTextContents()).join(', '));
  assert((await page.locator('.wb-dropdown-open .wb-menuitem').count()) === 2, 'row context menu popup with two actions');
  await item('Mark paid').click(); await settle(600);
  const statusCell = await grid.locator('.wb-table-row[data-key="' + key + '"] .wb-td:nth-child(6) .wb-text').textContent();
  console.log('  order ' + key + ' status now: ' + statusCell);
  assert(statusCell === 'Paid', 'keyed update re-rendered the row without a reset');

  step('inline edit: accepted and rejected values');
  const cust = grid.locator('.wb-table-row[data-key="' + key + '"] .wb-td:nth-child(2)');
  await cust.dblclick(); await settle(200);
  await grid.locator('.wb-table-editor').fill('Edited Co'); await grid.locator('.wb-table-editor').press('Enter'); await settle(600);
  assert((await cust.locator('.wb-text').textContent()) === 'Edited Co', 'edit applied and re-rendered');
  await cust.dblclick(); await settle(200);
  await grid.locator('.wb-table-editor').fill('   '); await grid.locator('.wb-table-editor').press('Enter'); await settle(600);
  console.log('  rejected edit: text=' + await cust.locator('.wb-text').textContent() + ' error=' + await cust.getAttribute('class'));
  assert((await cust.locator('.wb-text').textContent()) === 'Edited Co' && /wb-cell-error/.test(await cust.getAttribute('class')), 'rejected edit keeps the old text and flags the cell');

  step('add and delete rows (model resets, selection follows)');
  await grid.locator('.wb-th', { hasText: 'Id' }).click(); await settle(600);
  await grid.locator('.wb-th', { hasText: 'Id' }).click(); await settle(600);     // id desc: the new (highest) id comes first
  await T('add-order').click(); await settle(800);
  console.log('  ' + await T('orders-status').textContent() + '; first row: ' + await row(0).locator('.wb-td:nth-child(2) .wb-text').textContent());
  assert(/10,001 rows|10001 rows/.test(await T('orders-status').textContent()) && (await row(0).locator('.wb-td:nth-child(2) .wb-text').textContent()) === 'New Customer', 'new row at the top and selected');
  await T('delete-orders').click(); await settle(800);
  assert(/10,000 rows|10000 rows/.test(await T('orders-status').textContent()), 'deleted again');

  step('reconnect keeps the table (pages re-requested after the init)');
  await page.evaluate(() => { wb.seq = 0; });
  await page.evaluate(ms => wb.transport.simulateOutage(ms), 1500);
  await page.waitForFunction(() => document.documentElement.dataset.wbState === 'reconnecting', null, { timeout: 10000 });
  await page.waitForFunction(() => document.documentElement.dataset.wbState === 'connected', null, { timeout: 20000 });
  await settle(1500);
  await page.waitForFunction(s => document.querySelector(s + ' .wb-table-row[data-key]'), bodySel, { timeout: 10000 });
  console.log('  rows after resync: ' + await grid.locator('.wb-table-row[data-key]').count());
  assert((await grid.locator('.wb-table-row[data-key]').count()) > 10, 'rows back after a full init');

  console.log('console errors: ' + JSON.stringify(errors));
  assert(errors.length === 0, 'no console errors');
  await browser.close();
  console.log('COMPONENTS E2E PASSED');
})().catch(e => { console.error('FAILED', e); process.exit(1); });
