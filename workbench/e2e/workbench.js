// Browser end-to-end check of the Workbench sample application (step 8), against the application jar:
// branding, the five modules, the editor round trip, picking in 3D with tree sync, options (theme + language),
// role filtering for bob, and layout persistence across a reload.
// Start: java -jar workbench.jar --branding workbench --title Workbench --datadir <fresh> --port 8090; then: node workbench.js
const { chromium } = require('playwright');
const fs = require('fs');
const base = process.env.BASE || 'http://localhost:8090';
const dataDir = process.env.DATADIR || '';
const step = s => console.log('* ' + s);
const assert = (cond, msg) => { if (!cond) { throw new Error('ASSERT: ' + msg); } };
(async () => {
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const ctx = await browser.newContext({ viewport: { width: 1400, height: 900 }, acceptDownloads: true });
  const page = await ctx.newPage();
  const errors = [];
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  const settle = ms => page.waitForTimeout(ms || 450);
  const menu = text => page.locator('.wb-menubar').getByRole('menuitem', { name: text, exact: true });
  const item = text => page.locator('.wb-dropdown-open .wb-menuitem', { has: page.locator('.wb-text', { hasText: new RegExp('^' + text + '$') }) });
  const menus = () => page.evaluate(() => Array.from(document.querySelectorAll('.wb-menubar [role=menuitem]')).map(m => m.textContent.trim()));
  const openItems = () => page.evaluate(() => Array.from(document.querySelectorAll('.wb-dropdown-open .wb-menuitem')).map(m => m.textContent.trim()));
  const tabs = () => page.evaluate(() => Array.from(document.querySelectorAll('.wb-dk-tab')).map(t => t.dataset.tc));
  const status = () => page.textContent('.wb-status-text');
  const rows = sel => page.evaluate(s => Array.from(document.querySelectorAll(s + ' .wb-nv-row')).map(r => r.querySelector('.wb-nv-label').textContent.trim() + (r.classList.contains('wb-nv-selected') ? '*' : '')), sel);
  const row = (sel, name) => page.locator(sel + ' .wb-nv-row', { has: page.locator('.wb-nv-label', { hasText: new RegExp('^' + name + '$') }) });
  const login = async (user) => {
    await page.goto(base + '/');
    await page.fill('input[name=username]', user); await page.fill('input[name=password]', 'secret'); await page.click('button[type=submit]');
    await page.waitForSelector('.wb-menubar', { timeout: 30000 }); await settle(2500);
  };
  const logout = async () => { await menu(await isGerman() ? 'Datei' : 'File').click(); await settle(); await item(await isGerman() ? 'Abmelden' : 'Log out').click(); await page.waitForSelector('input[name=username]'); errors.length = 0; };
  const isGerman = async () => (await menus()).includes('Datei');

  step('login as alice: branded help menu, five windows, status cells');
  await login('alice');
  console.log('  menus: ' + (await menus()).join(' | '));
  assert((await menus()).includes('Workbench Help'), 'branding overlay renamed the Help menu');
  console.log('  tabs: ' + (await tabs()).join(', '));
  for (const id of ['FilesTC', 'PartsTC', 'DashboardTC', 'ViewerTC', 'OutputTC', 'ServerStatusTC']) assert((await tabs()).includes(id), id + ' open at startup (admin layout)');
  assert((await page.textContent('[data-test=status-user]')).includes('Alice Admin'), 'status cell');
  await menu('Workbench Help').click(); await settle(); await item('About Workbench').click(); await settle(600);
  assert(await page.locator('[data-test=about]').count() === 1, 'about dialog');
  await page.click('[data-test=dialog-ok]'); await settle();

  step('files: the seeded workdir, expand src, open README by double-click, edit, Ctrl+S → file on disk');
  console.log('  files: ' + (await rows('[data-test=files]')).join(', '));
  assert((await rows('[data-test=files]')).includes('README.md'), 'seeded files listed');
  await row('[data-test=files]', 'src').locator('.wb-nv-toggle').click(); await settle(800);
  assert((await rows('[data-test=files]')).includes('Hello.java'), 'async folder children');
  await row('[data-test=files]', 'README.md').locator('.wb-nv-label').dblclick(); await settle(900);
  assert((await tabs()).some(t => t.startsWith('EditorTC')), 'editor opened');
  const editor = page.locator('.wb-tc:not(.wb-tc-hidden) [data-test=editor] textarea');
  await editor.click(); await page.keyboard.press('Control+End'); await page.keyboard.type('\nEdited in the browser.'); await settle(600);
  console.log('  info: ' + await page.textContent('.wb-tc:not(.wb-tc-hidden) [data-test=editor-info]'));
  await page.keyboard.press('Control+S'); await settle(900);
  console.log('  status: ' + await status());
  assert((await status()) === 'Saved README.md', 'save through the Ctrl+S callback → Savable in the context');
  if (dataDir) {
    const text = fs.readFileSync(dataDir + '/users/alice/org.webbeans.workbench.files/workdir/README.md', 'utf8');
    assert(text.endsWith('Edited in the browser.'), 'the file on disk changed: ' + JSON.stringify(text.slice(-40)));
    console.log('  on disk: ...' + text.slice(-30).replace(/\n/g, '\\n'));
  }
  const output = await page.inputValue('[data-test=output] textarea');
  assert(output.includes('[files] Saved README.md') && output.includes('[files] Opened README.md'), 'output window got the log lines: ' + output);

  step('new file via Ctrl+N and the input dialog; it opens in an editor and shows in the tree');
  await page.locator('.wb-dk-tab[data-tc="FilesTC"]').click(); await settle(300);
  await page.keyboard.press('Control+N'); await settle(600);
  await page.fill('[data-test=dialog-input] input', 'todo.txt'); await page.keyboard.press('Enter'); await settle(1200);
  assert((await rows('[data-test=files]')).includes('todo.txt'), 'tree refreshed with the new file');
  assert((await tabs()).filter(t => t.startsWith('EditorTC')).length === 2, 'second editor');

  step('3D viewer: pick a blade → status, Parts tree selection; More blades → scene and tree grow');
  await page.locator('.wb-dk-tab[data-tc="ViewerTC"]').click(); await settle(500);
  await page.waitForFunction(() => { const el = document.querySelector('[data-test=viewer]'); return el && el.ready && el.s.nodes.size > 10; }, null, { timeout: 30000 }); await settle(1500);
  const st = await page.evaluate(() => { const s = document.querySelector('[data-test=viewer]').s; const t = {}; s.nodes.forEach(r => { t[r.t] = (t[r.t] || 0) + 1; }); return { types: t, anims: s.anims.size, labels: document.querySelectorAll('[data-test=viewer] .wb-scene3d-label').length }; });
  console.log('  scene: ' + JSON.stringify(st));
  assert(st.types.mesh >= 12 && st.anims >= 1 && st.labels === 3, 'turbine rendered with rotor animation and sensor labels');
  const bladeAt = await page.evaluate(() => { const s = document.querySelector('[data-test=viewer]').s; let rec; s.nodes.forEach(r => { if (r.obj.name === 'Blade 2' && r.t === 'mesh') rec = r; });
    const p = rec.obj.localToWorld(new THREE.Vector3(0, 1.8, 0)).project(s.camera); const b = s.renderer.domElement.getBoundingClientRect(); return [b.left + (p.x + 1) / 2 * b.width, b.top + (1 - p.y) / 2 * b.height]; });
  await page.evaluate(() => { document.querySelector('[data-test=viewer]').s.anims.forEach(a => { a.paused = true; }); });  // hold the rotor for a precise click
  await settle(300);
  const bladeNow = await page.evaluate(() => { const s = document.querySelector('[data-test=viewer]').s; let rec; s.nodes.forEach(r => { if (r.obj.name === 'Blade 2' && r.t === 'mesh') rec = r; });
    const p = rec.obj.localToWorld(new THREE.Vector3(0, 1.8, 0)).project(s.camera); const b = s.renderer.domElement.getBoundingClientRect(); return [b.left + (p.x + 1) / 2 * b.width, b.top + (1 - p.y) / 2 * b.height]; });
  await page.mouse.click(bladeNow[0], bladeNow[1]); await settle(900);
  console.log('  status: ' + await status() + '; parts: ' + (await rows('[data-test=parts]')).filter(r => r.endsWith('*')).join(','));
  assert((await status()).startsWith('Picked Blade'), 'pick resolved to a blade');
  assert((await rows('[data-test=parts]')).some(r => /^Blade \d\*$/.test(r)), 'the Parts tree selected the picked blade');
  await page.locator('.wb-toolbar .wb-tbbutton[title^="More blades"]').click(); await settle(1200);
  console.log('  parts after: ' + (await rows('[data-test=parts]')).length + ' rows');
  assert((await rows('[data-test=parts]')).filter(r => r.startsWith('Blade')).length === 7, 'seven blades in the tree');
  const meshes = await page.evaluate(() => { const s = document.querySelector('[data-test=viewer]').s; let n = 0; s.nodes.forEach(r => { if (r.t === 'mesh' && /^Blade/.test(r.obj.name)) n++; }); return n; });
  assert(meshes === 7, 'seven blade meshes in the scene');
  const temp = await page.evaluate(() => Array.from(document.querySelectorAll('[data-test=viewer] .wb-scene3d-label')).map(l => l.textContent));
  console.log('  sensor labels: ' + temp.join(' | '));
  assert(temp.some(t => /Sensor A: \d+\.\d °C/.test(t) && !t.includes(': 20.0 ')), 'server push updated the sensor labels');

  step('dashboard: live charts; Tools ▸ Options → German + dark theme relabels the UI without a reload');
  await page.locator('.wb-dk-tab[data-tc="DashboardTC"]').click(); await settle(1500);
  const pts = await page.evaluate(() => document.querySelector('[data-test=chart-cpu]').series[0].points.length);
  console.log('  cpu points: ' + pts);
  assert(pts >= 2, 'cpu series is live');
  await menu('Tools').click(); await settle(); await item('Options').click(); await settle(800);
  assert(await page.locator('[data-test=options-dialog]').count() === 1, 'options dialog');
  await page.selectOption('[data-test=opt-theme] select', 'dark'); await settle(200);
  await page.selectOption('[data-test=opt-language] select', 'de'); await settle(200);
  await page.click('[data-test=dialog-ok]'); await settle(1500);
  console.log('  menus now: ' + (await menus()).join(' | ') + '; theme=' + await page.evaluate(() => document.documentElement.dataset.theme));
  assert((await menus()).includes('Datei') && (await menus()).includes('Workbench-Hilfe'), 'menus relabelled in German (with the branded help menu)');
  assert((await page.evaluate(() => document.documentElement.dataset.theme)) === 'dark', 'dark theme applied');
  assert((await page.textContent('[data-test=status-user]')).startsWith('Angemeldet als'), 'status cell relabelled');
  const tabTexts = await page.evaluate(() => Array.from(document.querySelectorAll('.wb-dk-tab')).map(t => t.textContent.trim()));
  console.log('  tabs: ' + tabTexts.join(', '));
  assert(tabTexts.includes('Dateien') && tabTexts.includes('Ausgabe'), 'windows relabelled');

  step('reload: layout, editors and the options survive; switch back to English');
  await page.reload(); await page.waitForSelector('.wb-menubar', { timeout: 30000 }); await settle(3000);
  console.log('  tabs after reload: ' + (await tabs()).join(', '));
  assert((await tabs()).filter(t => t.startsWith('EditorTC')).length === 2, 'both editors restored from their factories');
  assert((await menus()).includes('Datei'), 'saved language applied at login');
  assert((await page.evaluate(() => document.documentElement.dataset.theme)) === 'dark', 'saved theme applied at login');
  await menu('Extras').click(); await settle(); await item('Einstellungen').click(); await settle(800);
  await page.selectOption('[data-test=opt-language] select', 'en'); await page.selectOption('[data-test=opt-theme] select', 'system'); await settle(200);
  await page.click('[data-test=dialog-ok]'); await settle(1200);
  assert((await menus()).includes('File'), 'back to English');
  await settle(800); // the relabelled menu bar may still be settling

  step('admin: the shipped admin layout opened Server Status for alice; log out; bob sees neither the window nor the menu entry');
  await menu('Window').click(); await settle();
  const windowItems = await openItems();
  console.log('  window menu: ' + windowItems.join(' | '));
  assert(windowItems.some(i => i.startsWith('Server Status')), 'admin sees Window ▸ Server Status');
  await page.keyboard.press('Escape'); await settle();
  assert((await tabs()).includes('ServerStatusTC'), 'status window open from the shipped admin layout');
  await page.locator('.wb-dk-tab[data-tc="ServerStatusTC"]').click(); await settle(1500);
  console.log('  sessions: ' + (await rows('[data-test=sessions]')).join(', ') + ' / ' + await page.textContent('[data-test=sessions-summary]'));
  assert((await rows('[data-test=sessions]')).some(r => r.includes('alice')), 'alice listed');
  await logout();
  await login('bob');
  await menu('Window').click(); await settle();
  const bobItems = await openItems();
  console.log('  bob window menu: ' + bobItems.join(' | '));
  assert(!bobItems.some(i => i.startsWith('Server Status')), 'bob has no Server Status entry');
  await page.keyboard.press('Escape');
  assert(!(await tabs()).includes('ServerStatusTC'), 'no admin window for bob');
  assert((await tabs()).includes('FilesTC') && (await rows('[data-test=files]')).includes('README.md'), 'bob has his own seeded workdir');
  await logout();

  const real = errors.filter(e => !/ERR_FAILED|net::|410/.test(e));
  console.log(real.length ? 'CONSOLE ERRORS:\n' + real.join('\n') : 'no console errors');
  await browser.close();
  if (real.length) process.exit(1);
})().catch(e => { console.error(e); process.exit(1); });
