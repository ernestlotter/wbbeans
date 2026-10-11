// Browser end-to-end check of the help system (step 9) against the Workbench application jar: Help ▸ Help Contents
// (table of contents), help: links and Back, F1 context help, search, the Help button of the Options and About
// dialogs (F1 presses it), the stand-alone /wb/help/<id> page, German page variants, and role filtering for bob.
// Start: java -jar workbench.jar --branding workbench --title Workbench --datadir <fresh> --port 8090; then: node help.js
const { chromium } = require('playwright');
const base = process.env.BASE || 'http://localhost:8090';
const assert = (c, m) => { if (!c) throw new Error('ASSERT: ' + m); };
(async () => {
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const ctx = await browser.newContext({ viewport: { width: 1400, height: 900 } });
  const page = await ctx.newPage();
  const errors = [];
  page.on('console', m => { if (m.type() === 'error' && !m.text().includes('410')) errors.push(m.text()); });   // a 410 is the previous tab's poll after Log out
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  const settle = ms => page.waitForTimeout(ms || 450);
  const menu = text => page.locator('.wb-menubar').getByRole('menuitem', { name: text, exact: true });
  const item = text => page.locator('.wb-dropdown-open .wb-menuitem', { has: page.locator('.wb-text', { hasText: new RegExp('^' + text + '$') }) });
  const openItems = () => page.evaluate(() => Array.from(document.querySelectorAll('.wb-dropdown-open .wb-menuitem')).map(m => m.textContent.trim()));
  await page.goto(base + '/');
  await page.fill('input[name=username]', 'alice'); await page.fill('input[name=password]', 'secret'); await page.click('button[type=submit]');
  await page.waitForSelector('.wb-menubar', { timeout: 30000 }); await settle(2500);
  // Help menu: Help Contents first, then About
  await menu('Workbench Help').click(); await settle();
  const items = await openItems();
  console.log('help menu: ' + items.join(' | '));
  assert(items[0] === 'Help Contents', 'Help Contents first');
  await item('Help Contents').click(); await settle(900);
  // the Help window opened on the table of contents; the home page is its first entry
  await page.waitForSelector('[data-test=help-toc]');
  const toc0 = await page.evaluate(() => Array.from(document.querySelectorAll('[data-test=help-toc] .wb-nv-row .wb-nv-label')).map(e => e.textContent.trim()));
  console.log('toc: ' + toc0.join(', '));
  assert(toc0[0] === 'Welcome to the Workbench', 'home first in the toc');
  await page.locator('[data-test=help-toc] .wb-nv-row', { has: page.locator('.wb-nv-label', { hasText: /^Welcome to the Workbench$/ }) }).click(); await settle(700);
  let crumbs = await page.textContent('[data-test=help-crumbs]');
  console.log('crumbs: ' + crumbs);
  assert(crumbs === 'Welcome to the Workbench', 'home page');
  const h1 = await page.textContent('[data-test=help-page] h1');
  assert(h1 === 'Welcome to the Workbench', 'rendered markdown h1: ' + h1);
  // a help: link inside the page → Files page
  await page.locator('[data-test=help-page] a', { hasText: 'Files and the editor' }).click(); await settle(600);
  crumbs = await page.textContent('[data-test=help-crumbs]');
  console.log('crumbs: ' + crumbs);
  assert(crumbs === 'Welcome to the Workbench › Files', 'link to files page');
  // Back
  await page.click('[data-test=help-back]'); await settle(500);
  assert((await page.textContent('[data-test=help-crumbs]')) === 'Welcome to the Workbench', 'back');
  // Contents: the TOC tree
  await page.click('[data-test=help-contents]'); await settle(500);
  const toc = await page.evaluate(() => Array.from(document.querySelectorAll('[data-test=help-toc] .wb-nv-row .wb-nv-label')).map(e => e.textContent.trim()));
  console.log('toc: ' + toc.join(', '));
  assert(toc.includes('Welcome to the Workbench') && toc.includes('Files') && toc.includes('The editor') && toc.includes('Server Status') && toc.includes('Getting around'), 'toc');
  // F1 in the 3D viewer → the viewer's page
  await page.locator('.wb-dk-tab[data-tc=ViewerTC]').click(); await settle(500);
  await page.keyboard.press('F1'); await settle(800);
  crumbs = await page.textContent('[data-test=help-crumbs]');
  console.log('F1 crumbs: ' + crumbs);
  assert(crumbs === 'Welcome to the Workbench › 3D Viewer', 'F1 shows the viewer page');
  // search
  await page.fill('[data-test=help-search] input, input[data-test=help-search]', 'notification'); await page.keyboard.press('Enter'); await settle(800);
  const results = await page.evaluate(() => Array.from(document.querySelectorAll('[data-test=help-toc] .wb-nv-row .wb-nv-label')).map(e => e.textContent.trim()));
  console.log('search "notification": ' + results.join(', '));
  assert(results[0] === 'Dashboard' && results.includes('Welcome to the Workbench'), 'search results: keyword hit first, then text hits');
  // the Options dialog has a Help button that follows the category
  const openOptions = async (toolsLabel, optionsRe) => { await page.keyboard.press('Escape'); await settle(200); await menu(toolsLabel).click(); await page.waitForSelector('.wb-dropdown-open'); await page.locator('.wb-dropdown-open .wb-menuitem', { hasText: optionsRe }).click(); await page.waitForSelector('[data-test=options-dialog]'); await settle(500); };
  await openOptions('Tools', /^Options/);
  assert(await page.$('[data-test=dialog-help]'), 'help button in the options dialog');
  await page.click('[data-test=options-cat-DashboardOptions]'); await settle(400);
  await page.click('[data-test=dialog-help]'); await settle(800);
  crumbs = await page.textContent('[data-test=help-crumbs]');
  console.log('options help: ' + crumbs);
  assert(crumbs.endsWith('Dashboard options'), 'dialog help button shows the category page');
  assert(await page.$('[data-test=dialog]'), 'dialog still open');
  await page.click('[data-test=dialog-cancel]'); await settle(300);
  // About has a Help button too
  await menu('Workbench Help').click(); await settle(); await item('About Workbench').click(); await settle(600);
  assert(await page.$('[data-test=dialog-help]'), 'about dialog has help');
  await page.keyboard.press('F1'); await settle(600);
  assert((await page.textContent('[data-test=help-crumbs]')) === 'Welcome to the Workbench', 'F1 in a dialog presses its Help button');
  await page.click('[data-test=dialog-ok]'); await settle(300);
  // open in new tab: the stand-alone page
  const href = await page.getAttribute('[data-test=help-open-tab]', 'href');
  console.log('open in tab: ' + href);
  const res = await page.request.get(base + '/' + href);
  const html = await res.text();
  assert(res.status() === 200 && html.includes('<nav class="toc">') && html.includes('<title>Welcome to the Workbench</title>'), 'standalone page: ' + res.status());
  assert(html.includes('href="/wb/help/files"'), 'toc links in the standalone page');
  // German: the help window relabels and the home page switches to the German variant
  await openOptions('Tools', /^Options/);
  await page.click('[data-test=options-cat-WorkbenchOptions]'); await settle(300);
  await page.selectOption('[data-test=opt-language] select, select[data-test=opt-language]', 'de'); await settle(300);
  await page.click('[data-test=dialog-ok]'); await settle(1200);
  const deCrumbs = await page.textContent('[data-test=help-crumbs]');
  console.log('de crumbs: ' + deCrumbs);
  assert(deCrumbs === 'Willkommen in der Workbench', 'German title and page');
  const deH1 = await page.textContent('[data-test=help-page] h1');
  assert(deH1 === 'Willkommen in der Workbench', 'German markdown variant: ' + deH1);
  await page.screenshot({ path: process.env.SHOT || 'help.png' });
  // back to English
  await openOptions('Extras', /^Einstellungen/);
  await page.click('[data-test=options-cat-WorkbenchOptions]'); await settle(300);
  await page.selectOption('[data-test=opt-language] select, select[data-test=opt-language]', 'en'); await settle(300);
  await page.click('[data-test=dialog-ok]'); await settle(800);
  // bob does not see the admin page
  await menu('File').click(); await settle(); await item('Log out').click(); await settle(1500);
  await page.fill('input[name=username]', 'bob'); await page.fill('input[name=password]', 'secret'); await page.click('button[type=submit]');
  await page.waitForSelector('.wb-menubar', { timeout: 30000 }); await settle(2500);
  await page.keyboard.press('F1'); await settle(1000);
  await page.click('[data-test=help-contents]'); await settle(500);
  const bobToc = await page.evaluate(() => Array.from(document.querySelectorAll('[data-test=help-toc] .wb-nv-row .wb-nv-label')).map(e => e.textContent.trim()));
  console.log('bob toc: ' + bobToc.join(', '));
  assert(!bobToc.includes('Server Status') && bobToc.includes('Files'), 'role-filtered toc');
  const forbidden = await page.request.get(base + '/wb/help/admin');
  assert(forbidden.status() === 404, 'standalone admin page is 404 for bob: ' + forbidden.status());
  console.log('console errors: ' + JSON.stringify(errors));
  assert(errors.length === 0, 'no console errors');
  await browser.close();
  console.log('HELP E2E PASSED');
})().catch(e => { console.error(e); process.exit(1); });
