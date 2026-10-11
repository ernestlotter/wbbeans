// Browser end-to-end check of the window system (dock, persistence, reconnect). Start the server with
//   --admin-password secret123 --port 8089 and a fresh --datadir, then: npm i playwright && node windows.js
const { chromium } = require('playwright');
const base = process.env.BASE || 'http://localhost:8089';
const step = s => console.log('* ' + s);
const modes = page => page.evaluate(() => Array.from(document.querySelectorAll('.wb-dock-area .wb-mode')).map(m => m.dataset.mode + ':' + Array.from(m.querySelectorAll('.wb-dk-tab')).map(t => t.dataset.tc + (t.classList.contains('wb-selected') ? '*' : '')).join('|')));
const layout = page => page.evaluate(() => document.querySelector('.wb-dock').layout);
(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 800 } });
  const page = await ctx.newPage();
  const errors = [];
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  const tab = id => page.locator('.wb-dk-tab[data-tc="' + id + '"]');
  const modeEl = name => page.locator('.wb-dock-area .wb-mode[data-mode="' + name + '"]');
  const settle = () => page.waitForTimeout(400);

  step('login + default layout');
  await page.goto(base + '/');
  await page.fill('input[name=username]', 'admin'); await page.fill('input[name=password]', 'secret123'); await page.click('button[type=submit]');
  await page.waitForSelector('.wb-dock .wb-mode'); await settle();
  console.log('  ' + (await modes(page)).join('  '));
  console.log('  properties: ' + await page.textContent('[data-test=activated]'));

  step('activate by clicking a tab');
  await tab('AboutTC').click(); await settle();
  console.log('  ' + await page.textContent('[data-test=activated]') + '; editor=' + (await modes(page))[1]);
  console.log('  about visible: ' + await page.isVisible('.demo-about'));

  step('close + reopen');
  await tab('AboutTC').locator('.wb-dk-tab-close').click(); await settle();
  console.log('  after close: ' + (await modes(page))[1] + '; active=' + await page.textContent('[data-test=activated]'));
  await page.click('[data-test=open-about]'); await settle();
  console.log('  after reopen: ' + (await modes(page))[1] + '; active=' + await page.textContent('[data-test=activated]'));

  step('multi-instance windows from the explorer');
  const prow = name => page.locator('[data-test=planets] .wb-nv-row', { has: page.locator('.wb-nv-label', { hasText: new RegExp('^' + name + '$') }) }).locator('.wb-nv-label');
  await page.waitForSelector('[data-test=planets] .wb-nv-row'); await page.waitForTimeout(800);
  await prow('Mars').dblclick(); await settle();
  await prow('Jupiter').dblclick(); await settle();
  await prow('Mars').dblclick(); await settle();
  console.log('  editor=' + (await modes(page))[1] + '; sheet title=' + await page.textContent('[data-test=sheet] .wb-ps-title'));
  await page.locator('[data-test=mark]:visible').click(); await settle();
  console.log('  modified tab html: ' + await tab('PlanetTC#1').locator('.wb-dk-tab-label').innerHTML() + ' marker=' + await tab('PlanetTC#1').locator('.wb-dk-tab-modified').count());

  step('drag Form tab into the output mode');
  await tab('FormTC').dragTo(modeEl('output').locator('.wb-mode-body'), { targetPosition: { x: 300, y: 80 } }); await settle();
  console.log('  ' + (await modes(page)).join('  '));

  step('drag Events tab to the right edge of the editor (split)');
  const ed = modeEl('editor'); const box = await ed.boundingBox();
  await tab('EventsTC').dragTo(ed, { targetPosition: { x: box.width - 20, y: box.height / 2 } }); await settle();
  const L1 = await layout(page);
  console.log('  ' + (await modes(page)).join('  ') + '  anonymous modes: ' + Object.keys(await page.evaluate(() => document.querySelector('.wb-dock').modes)).filter(n => n.startsWith('anonymous')).join(','));

  step('resize a gutter');
  const g = page.locator('.wb-dock-area .wb-split-row > .wb-gutter').first(); const gb = await g.boundingBox();
  const before = (await layout(page)).root.children[0].sizes.slice();
  await page.mouse.move(gb.x + 2, gb.y + 100); await page.mouse.down(); await page.mouse.move(gb.x + 80, gb.y + 100, { steps: 5 }); await page.mouse.up(); await settle();
  const after = (await layout(page)).root.children[0].sizes.slice();
  console.log('  sizes ' + JSON.stringify(before) + ' -> ' + JSON.stringify(after));

  step('maximize by double-click, restore by button');
  await tab('AboutTC').dblclick(); await settle();
  console.log('  maximized=' + (await layout(page)).maximized + ' modes visible: ' + (await modes(page)).length);
  await modeEl('editor').locator('.wb-dk-tab-tool').last().click(); await settle();
  console.log('  maximized=' + (await layout(page)).maximized + ' modes visible: ' + (await modes(page)).length);
  await page.click('[data-test=maximize]'); await settle();
  console.log('  via API: maximized=' + (await layout(page)).maximized);
  await page.click('[data-test=maximize]'); await settle();

  step('minimize explorer to the side bar and slide it in');
  await modeEl('explorer').locator('.wb-dk-tab-tool').first().click(); await settle();
  console.log('  minimised=' + JSON.stringify(Object.keys((await layout(page)).minimised)) + ' sidebar buttons=' + await page.locator('.wb-sidebar-btn').count());
  await page.click('.wb-sidebar-btn'); await settle();
  console.log('  slide visible: ' + await page.isVisible('.wb-slide .wb-mode') + ' planets visible: ' + await page.isVisible('.wb-slide [data-test=planets]'));
  await page.locator('.wb-slide .wb-dk-tab-tool').first().click(); await settle();
  console.log('  docked back: ' + (await modes(page))[0]);

  step('float via the tab context menu, then dock');
  await tab('PropertiesTC').click({ button: 'right' }); await settle();
  const items = await page.locator('.wb-dk-tabmenu-item').allTextContents(); console.log('  menu: ' + items.join(' / '));
  await page.locator('.wb-dk-tabmenu-item', { hasText: 'Float' }).click(); await settle();
  console.log('  floating=' + (await layout(page)).floating.map(f => f.name + ':' + f.tcs.join('+')).join(','));
  await page.locator('.wb-floating .wb-dk-tab-tool').first().click(); await settle();
  console.log('  floating=' + (await layout(page)).floating.length + ' ' + (await modes(page)).join('  '));

  step('keyboard: Ctrl+Shift+Enter toggles maximize of the active window');
  await tab('FormTC').click(); await settle();
  await page.keyboard.press('Control+Shift+Enter'); await settle();
  console.log('  maximized=' + (await layout(page)).maximized);
  await page.keyboard.press('Control+Shift+Enter'); await settle();

  step('persistence: reload the page');
  await page.waitForTimeout(2500); // save debounce
  await page.reload(); await page.waitForSelector('.wb-dock .wb-mode'); await settle();
  console.log('  ' + (await modes(page)).join('  '));
  console.log('  active=' + await page.textContent('[data-test=activated]'));

  step('second tab: independent layout, same session');
  const page2 = await ctx.newPage(); await page2.goto(base + '/'); await page2.waitForSelector('.wb-dock .wb-mode'); await page2.waitForTimeout(600);
  console.log('  tab2: ' + (await modes(page2)).join('  '));
  await page2.close();

  step('reset layout');
  await page.click('[data-test=reset-layout]'); await settle();
  console.log('  ' + (await modes(page)).join('  '));

  await page.screenshot({ path: 'win2.png' });
  step('form window: validation, events, server timer');
  await tab('FormTC').click(); await settle();
  await page.fill('[data-test=name] input', 'Ada 42'); await page.locator('[data-test=name] input').blur(); await settle();
  console.log('  error=' + (await page.textContent('[data-test=name] .wb-field-error')).trim() + ' greet disabled=' + await page.locator('[data-test=greet]').isDisabled());
  await page.fill('[data-test=name] input', 'Ada Lovelace'); await page.locator('[data-test=name] input').blur();
  await page.waitForFunction(() => !document.querySelector('[data-test=greet]').disabled);
  await page.selectOption('[data-test=planet] select', { label: 'Mars' });
  await page.click('[data-test=greet]'); await page.waitForFunction(() => document.querySelector('[data-test=greeting]').textContent.includes('Hello'));
  console.log('  ' + await page.textContent('[data-test=greeting]'));
  await page.click('[data-test=work]');
  await page.waitForFunction(() => document.querySelector('[data-test=progress]').textContent.includes('Done'), null, { timeout: 5000 });
  console.log('  progress=' + (await page.textContent('[data-test=progress]')).trim() + ' busy spinner gone=' + (await tab('FormTC').locator('.wb-dk-tab-busy').count() === 0));
  const log = await page.inputValue('[data-test=log] textarea');
  console.log('  events window lines=' + log.trim().split('\n').length + ' last=' + log.trim().split('\n').pop());

  step('locale switch relabels the dock tooltips and status bar');
  await page.click('[data-test=lang-de]'); await page.waitForFunction(() => document.querySelector('[data-test=welcome]').textContent.startsWith('Angemeldet'));
  console.log('  ' + await page.textContent('[data-test=welcome]') + ' / close tooltip=' + await tab('FormTC').locator('.wb-dk-tab-close').getAttribute('title'));
  await page.click('[data-test=lang-en]'); await page.waitForFunction(() => document.querySelector('[data-test=welcome]').textContent.startsWith('Signed'));

  step('reconnect keeps the layout and the windows');
  await page.evaluate(ms => wb.transport.simulateOutage(ms), 2000); // drops the WebSocket (or fails the polls) for a while
  await page.waitForFunction(() => document.documentElement.dataset.wbState === 'reconnecting', null, { timeout: 40000 });
  await page.waitForFunction(() => document.documentElement.dataset.wbState === 'connected', null, { timeout: 40000 });
  await tab('AboutTC').click(); await settle();
  console.log('  after reconnect active=' + await page.textContent('[data-test=activated]') + ' modes=' + (await modes(page)).length);

  step('logout');
  await page.click('[data-test=logout]'); await page.waitForURL(/\/login/);
  console.log('  ' + page.url());

  console.log('console errors: ' + JSON.stringify(errors));
  await browser.close();
})().catch(e => { console.error('FAILED', e); process.exit(1); });
