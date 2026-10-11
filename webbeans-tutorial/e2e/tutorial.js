// Browser check of the tutorial module (docs/user-guide.md) running next to the demo: Hello greeting, the Tasks window
// (Ctrl+Shift+T → input dialog → node, popup → Mark as Done), the Tasks toolbar, the New contact window and the
// Options category. Start the demo server with --admin-password secret123 --port 8089 and a fresh --datadir, then:
// npm i playwright && node tutorial.js
const { chromium } = require('playwright');
const base = process.env.BASE || 'http://localhost:8089';
const assert = (c, m) => { if (!c) throw new Error('ASSERT: ' + m); };
(async () => {
  const browser = await chromium.launch();
  const page = await (await browser.newContext({ viewport: { width: 1280, height: 800 } })).newPage();
  const errors = [];
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  await page.goto(base + '/');
  await page.fill('input[name=username]', 'admin'); await page.fill('input[name=password]', 'secret123'); await page.click('button[type=submit]');
  await page.waitForSelector('.wb-menubar'); await page.waitForTimeout(1500);
  await page.locator('.wb-menubar').getByRole('menuitem', { name: 'Window', exact: true }).click(); await page.waitForTimeout(300);
  await page.locator('.wb-dropdown-open .wb-menuitem', { has: page.locator('.wb-text', { hasText: /^Hello$/ }) }).click(); await page.waitForTimeout(800);
  await page.waitForSelector('[data-test=hello-name]');
  // §1 hello
  await page.fill('[data-test=hello-name] input, input[data-test=hello-name]', 'Ada'); await page.keyboard.press('Enter'); await page.waitForTimeout(500);
  const greeting = await page.textContent('[data-test=hello-greeting]');
  console.log('greeting: ' + greeting);
  assert(greeting === 'Hello, Ada!', 'greeting');
  // §7 tasks window: activate it (the explorer mode shows the demo's Planets tab first), rows
  await page.locator('.wb-menubar').getByRole('menuitem', { name: 'Window', exact: true }).click(); await page.waitForTimeout(300);
  await page.locator('.wb-dropdown-open .wb-menuitem', { has: page.locator('.wb-text', { hasText: /^Tasks$/ }) }).click(); await page.waitForTimeout(800);
  const rows = await page.evaluate(() => Array.from(document.querySelectorAll('[data-test=tasks] .wb-nv-row .wb-nv-label')).map(e => e.textContent));
  console.log('tasks: ' + rows.join(', '));
  assert(rows.length === 2, 'two tasks');
  // §8 Ctrl+Shift+T → input dialog → new task
  await page.keyboard.press('Control+Shift+T'); await page.waitForSelector('[data-test=dialog-input]');
  await page.fill('[data-test=dialog-input] input, input[data-test=dialog-input]', 'Ship it'); await page.click('[data-test=dialog-ok]'); await page.waitForTimeout(600);
  const rows2 = await page.evaluate(() => Array.from(document.querySelectorAll('[data-test=tasks] .wb-nv-row .wb-nv-label')).map(e => e.textContent));
  console.log('tasks: ' + rows2.join(', '));
  assert(rows2.length === 3 && rows2[2] === 'Ship it', 'task added');
  // popup on the new task → Mark as Done
  const row = page.locator('[data-test=tasks] .wb-nv-row', { has: page.locator('.wb-nv-label', { hasText: /^Ship it$/ }) });
  await row.click({ button: 'right' }); await page.waitForTimeout(400);
  const items = await page.evaluate(() => Array.from(document.querySelectorAll('.wb-popup .wb-menuitem .wb-text, .wb-dropdown-open .wb-menuitem .wb-text')).map(e => e.textContent));
  console.log('popup: ' + items.join(', '));
  await page.locator('.wb-menuitem', { has: page.locator('.wb-text', { hasText: /^Mark as Done$/ }) }).first().click(); await page.waitForTimeout(600);
  console.log('status: ' + await page.textContent('.wb-status-text'));
  const html = await row.locator('.wb-nv-label').innerHTML();
  console.log('label html: ' + html);
  assert(html.includes('<s>'), 'strikethrough');
  // toolbar + Tools menu entries
  const tb = await page.evaluate(() => Array.from(document.querySelectorAll('.wb-toolbarpanel .wb-tbbutton')).map(b => b.title.replace(/ \(.*\)$/, '')));
  console.log('toolbar: ' + tb.join(', '));
  assert(tb.includes('Add Task…'), 'toolbar button');
  // §2 contact window via Window menu
  await page.locator('.wb-menubar').getByRole('menuitem', { name: 'Window', exact: true }).click(); await page.waitForTimeout(300);
  await page.locator('.wb-dropdown-open .wb-menuitem', { has: page.locator('.wb-text', { hasText: /^New contact$/ }) }).click(); await page.waitForTimeout(800);
  const tabs = await page.evaluate(() => Array.from(document.querySelectorAll('.wb-dk-tab')).map(t => t.textContent.trim()));
  console.log('tabs: ' + tabs.join(' | '));
  assert(tabs.some(t => t.includes('New contact')), 'contact tab');
  // §9 options page present
  await page.locator('.wb-menubar').getByRole('menuitem', { name: 'Tools', exact: true }).click(); await page.waitForTimeout(300);
  await page.locator('.wb-dropdown-open .wb-menuitem', { has: page.locator('.wb-text', { hasText: /^Options/ }) }).click(); await page.waitForTimeout(800);
  const cats = await page.evaluate(() => Array.from(document.querySelectorAll('.wb-options-cat')).map(e => e.textContent.trim()));
  console.log('options categories: ' + cats.join(', '));
  assert(cats.includes('Tutorial'), 'tutorial options category');
  await page.click('[data-test=dialog-cancel]');
  await page.screenshot({ path: process.env.SHOT || 'tutorial.png' });
  console.log('console errors: ' + JSON.stringify(errors));
  assert(errors.length === 0, 'no console errors');
  await browser.close();
  console.log('TUTORIAL E2E PASSED');
})().catch(e => { console.error(e); process.exit(1); });
