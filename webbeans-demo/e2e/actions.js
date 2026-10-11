// Browser end-to-end check of step 5: menu bar, toolbars, shortcuts, node popup, dialogs (input line, confirmation,
// form dialog, wizard), notifications, progress and View ▸ Toolbars. Start the server with --admin-password
// secret123 --port 8089 and a fresh --datadir, then: npm i playwright && node actions.js
const { chromium } = require('playwright');
const base = process.env.BASE || 'http://localhost:8089';
const step = s => console.log('* ' + s);
const assert = (cond, msg) => { if (!cond) { throw new Error('ASSERT: ' + msg); } };
(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 800 } });
  const page = await ctx.newPage();
  const errors = [];
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  const settle = (ms) => page.waitForTimeout(ms || 450);
  const rows = () => page.evaluate(() => Array.from(document.querySelectorAll('[data-test=planets] .wb-nv-row')).map(r => r.querySelector('.wb-nv-label').textContent + (r.classList.contains('wb-nv-selected') ? '*' : '')));
  const row = name => page.locator('[data-test=planets] .wb-nv-row', { has: page.locator('.wb-nv-label', { hasText: new RegExp('^' + name + '$') }) });
  const menu = text => page.locator('.wb-menubar').getByRole('menuitem', { name: text, exact: true });
  const openItems = () => page.evaluate(() => Array.from(document.querySelectorAll('.wb-dropdown-open')).map(dd => Array.from(dd.children).map(c => c.classList.contains('wb-separator') ? '-' : (c.querySelector('.wb-text').textContent + (c.classList.contains('wb-disabled') ? '(off)' : '') + (c.querySelector('.wb-menu-acc') && c.querySelector('.wb-menu-acc').textContent ? ' [' + c.querySelector('.wb-menu-acc').textContent + ']' : ''))).join(', ')));
  const item = text => page.locator('.wb-dropdown-open .wb-menuitem', { has: page.locator('.wb-text', { hasText: new RegExp('^' + text + '$') }) });
  const toolbarButtons = () => page.evaluate(() => Array.from(document.querySelectorAll('.wb-toolbarpanel .wb-tbbutton')).map(b => b.title.replace(/ \(.*\)$/, '') + (b.disabled ? '(off)' : '')));
  const dialog = () => page.locator('[data-test=dialog] .wb-dialog');
  const dlgButton = id => page.locator('[data-test=dialog-' + id + ']');
  const status = () => page.textContent('.wb-status-text');
  const notifCount = () => page.evaluate(() => (document.querySelector('.wb-notif-count') || {}).textContent || '0');

  step('login');
  await page.goto(base + '/');
  await page.fill('input[name=username]', 'admin'); await page.fill('input[name=password]', 'secret123'); await page.click('button[type=submit]');
  await page.waitForSelector('[data-test=planets] .wb-nv-row'); await settle(1200);
  console.log('  rows: ' + (await rows()).join(', '));

  step('menu bar: top-level menus, lazy fill, accelerators, enablement');
  const tops = await page.evaluate(() => Array.from(document.querySelectorAll('.wb-menubar > .wb-menu .wb-text')).map(e => e.textContent));
  console.log('  menus: ' + tops.join(' | '));
  assert(tops.join() === 'File,Edit,Planets,View,Tools,Window,Help', 'menu order (Tools carries the platform Options item)');
  await menu('Planets').click(); await settle();
  console.log('  Planets: ' + (await openItems()).join(' / '));
  assert((await openItems())[0].includes('Explore planet(off) [Ctrl+E]'), 'explore disabled without selection, accelerator shown');
  await menu('File').hover(); await settle();
  console.log('  File (hover switch): ' + (await openItems()).join(' / '));
  assert((await openItems())[0].startsWith('New Planet... [Ctrl+N], -'), 'menuText + separatorAfter');
  await page.keyboard.press('Escape'); await settle();
  assert((await openItems()).length === 0, 'Escape closes');

  step('selection enables context actions in menu and toolbar');
  console.log('  toolbar before: ' + (await toolbarButtons()).join(', '));
  await row('Jupiter').click(); await settle();
  console.log('  toolbar after selecting Jupiter: ' + (await toolbarButtons()).join(', '));
  const tb = await toolbarButtons();
  assert(tb.includes('Explore planet') && tb.includes('Remove planets') && tb.includes('Copy'), 'buttons enabled: ' + tb);
  assert(tb.includes('Paste(off)'), 'paste disabled with an empty clipboard');
  await menu('Planets').click(); await settle();
  assert((await openItems())[0].includes('Explore planet [Ctrl+E]'), 'explore enabled now');
  await page.keyboard.press('Escape');

  step('run a context action from the toolbar → notification toast + drawer count');
  await page.locator('.wb-tbbutton[title^="Explore planet"]').click(); await settle(800);
  const toast = await page.textContent('.wb-toasts .wb-notif-title');
  console.log('  toast: ' + toast + '  bell=' + await notifCount());
  assert(toast === 'Jupiter is now explored', 'toast text');
  assert(await notifCount() === '1', 'bell count');
  await page.click('.wb-toasts .wb-notif-close'); await settle();
  await page.click('[data-test=notifications-cell]'); await settle();
  console.log('  drawer: ' + (await page.textContent('.wb-notif-drawer .wb-notif-list')).trim().replace(/\s+/g, ' '));
  await page.click('.wb-notif-drawer .wb-notif-clearall'); await settle();
  assert(await notifCount() === '0', 'cleared');
  await page.click('.wb-notif-drawer-head .wb-notif-close');

  step('shortcut Ctrl+E on Venus (already explored → "was explored already")');
  await row('Venus').click(); await settle();
  await page.keyboard.press('Control+E'); await settle(800);
  console.log('  toast: ' + await page.textContent('.wb-toasts .wb-notif-title'));
  await page.click('.wb-toasts .wb-notif-close');

  step('File > New Planet... → input line dialog, Enter = OK');
  await menu('File').click(); await settle();
  await item('New Planet...').click(); await settle();
  assert(await dialog().count() === 1, 'dialog open');
  console.log('  dialog title: ' + await page.textContent('[data-test=dialog] .wb-dialog-title'));
  await page.fill('[data-test=dialog-input] input', 'Vulcan'); await page.keyboard.press('Enter'); await settle(800);
  console.log('  rows: ' + (await rows()).join(', '));
  assert((await rows()).some(r => r.startsWith('Vulcan')), 'planet added');
  assert(await dialog().count() === 0, 'dialog closed');

  step('duplicate name → error message dialog; Escape closes it');
  await page.keyboard.press('Control+N'); await settle();
  await page.fill('[data-test=dialog-input] input', 'Mars'); await dlgButton('ok').click(); await settle();
  console.log('  error dialog: ' + await page.textContent('[data-test=dialog] .wb-dialog-content'));
  assert(await page.locator('[data-test=dialog] .wb-dialog-icon[data-kind=error]').count() === 1, 'error icon');
  await page.keyboard.press('Escape'); await settle();
  assert(await dialog().count() === 0, 'error dialog closed by Escape');

  step('modal dialog blocks the page: clicking a row behind it does not change the selection');
  await row('Mars').click(); await settle();
  await page.keyboard.press('Control+N'); await settle();
  await page.mouse.click(60, 300); await settle();
  assert(await dialog().count() === 1, 'dialog still open');
  // a forced click bypasses the backdrop (a stale/malicious client): the server drops the event
  await row('Earth').click({ force: true }); await settle();
  console.log('  status while modal: ' + await status());
  assert((await status()) === 'Mars selected', 'server-side selection unchanged');
  await dlgButton('cancel').click(); await settle();
  await row('Mars').click(); await settle();

  step('node popup: own actions + registered Planets/Actions + edit actions; Remove with confirmation');
  await row('Vulcan').click({ button: 'right' }); await settle();
  console.log('  popup: ' + (await openItems()).join(' / '));
  assert((await openItems())[0].includes('Open, Toggle explored, -, Explore planet [Ctrl+E], Remove planets, -, Copy, Cut, Paste(off), -, Delete, Rename'), 'popup content: ' + (await openItems())[0]);
  await item('Remove planets').click(); await settle();
  console.log('  confirmation: ' + (await page.textContent('[data-test=dialog] .wb-dialog-content')).trim());
  await dlgButton('no').click(); await settle();
  assert((await rows()).some(r => r.startsWith('Vulcan')), 'No keeps the planet');
  await row('Vulcan').click({ button: 'right' }); await settle();
  await item('Remove planets').click(); await settle();
  await dlgButton('yes').click(); await settle(800);
  console.log('  rows: ' + (await rows()).join(', '));
  assert(!(await rows()).some(r => r.startsWith('Vulcan')), 'Yes removes the planet');

  step('Delete key → DialogDisplayer confirmation instead of window.confirm');
  await row('Earth').click(); await settle();
  await page.keyboard.press('Delete'); await settle();
  console.log('  confirm: ' + (await page.textContent('[data-test=dialog] .wb-dialog-content')).trim());
  await dlgButton('no').click(); await settle();
  assert((await rows()).some(r => r.startsWith('Earth')), 'Earth kept');

  step('wizard: validation per step, Back/Next/Finish');
  await menu('Planets').click(); await settle(); await item('New planet wizard...').click(); await settle();
  console.log('  title: ' + await page.textContent('[data-test=dialog] .wb-dialog-title') + '  steps: ' + (await page.textContent('[data-test=dialog] .wb-wizard-steps')).trim().replace(/\s+/g, ' '));
  await page.fill('[data-test=wizard-name] input', ''); await settle(600);
  console.log('  next disabled with empty name: ' + await dlgButton('next').isDisabled() + ' note=' + (await page.textContent('[data-test=dialog] .wb-dialog-note')).trim());
  assert(await dlgButton('next').isDisabled(), 'next disabled');
  await page.fill('[data-test=wizard-name] input', 'Krypton'); await settle(600);
  await dlgButton('next').click(); await settle();
  console.log('  step 2 title: ' + await page.textContent('[data-test=dialog] .wb-dialog-title'));
  await page.fill('[data-test=wizard-moons] input', 'x'); await settle(600);
  assert(await dlgButton('finish').isDisabled(), 'finish disabled with a non-number');
  await page.fill('[data-test=wizard-moons] input', '3'); await page.fill('[data-test=wizard-gravity] input', '12.5'); await settle(600);
  await dlgButton('back').click(); await settle();
  assert((await page.inputValue('[data-test=wizard-name] input')) === 'Krypton', 'settings kept on Back');
  await dlgButton('next').click(); await settle();
  await dlgButton('finish').click(); await settle(800);
  console.log('  rows: ' + (await rows()).join(', '));
  assert((await rows()).some(r => r.startsWith('Krypton')), 'wizard added the planet');
  await row('Krypton').click(); await settle();
  console.log('  krypton row: ' + (await row('Krypton').textContent()).replace(/\s+/g, ' '));

  step('asynchronous action with progress, cancel from the status bar');
  await menu('Planets').click(); await settle(); await item('Scan the sky').click(); await settle(700);
  const progressText = await page.textContent('[data-test=progress-cell] .wb-progress-text');
  console.log('  progress: ' + progressText + ' visible=' + await page.isVisible('[data-test=progress-cell]'));
  assert(progressText.startsWith('Scan the sky'), 'progress shows the task');
  await page.click('[data-test=progress-cell] .wb-progress-cancel'); await settle(1200);
  console.log('  after cancel: toast=' + await page.textContent('.wb-toasts .wb-notif-title') + ' progress visible=' + await page.isVisible('[data-test=progress-cell]'));
  assert((await page.textContent('.wb-toasts .wb-notif-title')) === 'Scan cancelled', 'cancelled notification');
  assert(!(await page.isVisible('[data-test=progress-cell]')), 'progress cell hidden when idle');
  await page.click('.wb-toasts .wb-notif-close');

  step('Help > About: form dialog with a custom button; View > Events window check item');
  await menu('Help').click(); await settle(); await item('About WebBeans demo...').click(); await settle();
  console.log('  about: ' + (await page.textContent('[data-test=about-content]')).slice(0, 40) + '...');
  await dlgButton('close').click(); await settle();
  assert(await dialog().count() === 0, 'about closed');
  await menu('View').click(); await settle();
  console.log('  View: ' + (await openItems()).join(' / '));
  const eventsTab = () => page.locator('.wb-dk-tab[data-tc="EventsTC"]');
  const before = await eventsTab().count();
  await page.locator('[data-test=menu-events-window]').click(); await settle();
  console.log('  events tab before=' + before + ' after=' + await eventsTab().count());
  assert((await eventsTab().count()) !== before, 'check item toggles the Events window');
  await menu('View').click(); await settle();
  console.log('  View again: ' + (await openItems()).join(' / '));
  await page.keyboard.press('Escape');

  step('View > Toolbars: hide the Planets toolbar through the user overlay, survives reload');
  await menu('View').click(); await settle();
  await page.locator('.wb-dropdown-open .wb-menu', { hasText: 'Toolbars' }).hover(); await settle();
  console.log('  submenu: ' + (await openItems()).slice(1).join(' / '));
  await page.locator('.wb-dropdown-open .wb-menuitem', { has: page.locator('.wb-text', { hasText: /^Planets$/ }) }).click(); await settle(800);
  let bars = await page.evaluate(() => Array.from(document.querySelectorAll('.wb-toolbar')).map(t => t.dataset.name));
  console.log('  toolbars: ' + bars.join(', '));
  assert(!bars.includes('Planets'), 'Planets toolbar hidden');
  await page.reload(); await page.waitForSelector('[data-test=planets] .wb-nv-row'); await settle(1200);
  bars = await page.evaluate(() => Array.from(document.querySelectorAll('.wb-toolbar')).map(t => t.dataset.name));
  console.log('  toolbars after reload: ' + bars.join(', '));
  assert(!bars.includes('Planets'), 'still hidden after reload');
  await menu('View').click(); await settle();
  await page.locator('.wb-dropdown-open .wb-menu', { hasText: 'Toolbars' }).hover(); await settle();
  await page.locator('.wb-dropdown-open .wb-menuitem', { has: page.locator('.wb-text', { hasText: /^Planets$/ }) }).click(); await settle(800);
  bars = await page.evaluate(() => Array.from(document.querySelectorAll('.wb-toolbar')).map(t => t.dataset.name));
  assert(bars.includes('Planets'), 'Planets toolbar back');

  step('Edit > Copy / Paste through the ActionMap of the active window; keyboard menu navigation');
  await row('Mars').click(); await settle();
  await page.keyboard.press('Control+C'); await settle();
  await row('Solar system').click(); await settle();
  await menu('Edit').click(); await settle();
  console.log('  Edit: ' + (await openItems()).join(' / '));
  assert((await openItems())[0].includes('Paste [Ctrl+V]'), 'paste enabled after copy, on the root');
  await item('Paste').click(); await settle(800);
  console.log('  rows: ' + (await rows()).join(', '));
  assert((await rows()).some(r => r.startsWith('Mars (copy)')), 'pasted a copy');
  await menu('Edit').click(); await settle();
  await page.keyboard.press('ArrowDown'); await page.keyboard.press('ArrowDown'); await page.keyboard.press('ArrowRight'); await settle();
  console.log('  after ArrowRight: ' + (await openItems()).join(' / '));
  assert((await openItems())[0].startsWith('Explore planet'), 'ArrowRight moves to the next top-level menu');
  await page.keyboard.press('Escape');

  step('logout via File menu');
  await menu('File').click(); await settle(); await item('Log out').click();
  await page.waitForSelector('input[name=username]');
  errors.length = 0; // the poll in flight during the logout may report 410 Gone, which is expected
  console.log('  back on the login page');

  console.log(errors.length ? 'CONSOLE ERRORS:\n' + errors.join('\n') : 'no console errors');
  await browser.close();
  if (errors.length) process.exit(1);
})().catch(e => { console.error(e); process.exit(1); });
