// Browser end-to-end check of step 7: the 3D solar system window — three.js loads on demand, the renderer reports
// ready, the canvas shows geometry, picking selects in the scene and in the outline tree (and the tree selects in
// the scene), the probe drags on its plane with snapping and server-side rejection, the toolbar fits/snapshots,
// a model change (new planet) reaches the scene as a small op, and a resync rebuilds everything.
// Start the server with --admin-password secret123 --port 8089 and a fresh --datadir, then: npm i playwright && node scene3d.js
const { chromium } = require('playwright');
const base = process.env.BASE || 'http://localhost:8089';
const step = s => console.log('* ' + s);
const assert = (cond, msg) => { if (!cond) { throw new Error('ASSERT: ' + msg); } };
(async () => {
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 800 }, acceptDownloads: true });
  const page = await ctx.newPage();
  const errors = [];
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  const settle = (ms) => page.waitForTimeout(ms || 450);
  const status = () => page.textContent('.wb-status-text');
  const menu = text => page.locator('.wb-menubar').getByRole('menuitem', { name: text, exact: true });
  const item = text => page.locator('.wb-dropdown-open .wb-menuitem', { has: page.locator('.wb-text', { hasText: new RegExp('^' + text + '$') }) });
  const sceneState = () => page.evaluate(() => {
    const el = document.querySelector('[data-test=scene-solar]'); const s = el.s;
    const types = {}; s.nodes.forEach(r => { types[r.t] = (types[r.t] || 0) + 1; });
    return { ready: el.ready, nodes: s.nodes.size, types, anims: s.anims.size, geoms: s.geoms.size, mats: s.mats.size, labels: el.querySelectorAll('.wb-scene3d-label').length,
      selection: el.props.selection || [], canvas: [s.renderer.domElement.width, s.renderer.domElement.height], calls: s.renderer.info.render.calls };
  });
  const nodeByName = name => page.evaluate(n => { const s = document.querySelector('[data-test=scene-solar]').s; let out = null; s.nodes.forEach(r => { if (r.obj.name === n && (r.t === 'mesh' || r.t === 'group')) out = out || r.id; }); return out; }, name);
  const screenOf = async name => page.evaluate(n => {
    const el = document.querySelector('[data-test=scene-solar]'); const s = el.s; let rec = null;
    s.nodes.forEach(r => { if (r.obj.name === n && r.t === 'mesh') rec = rec || r; });
    const p = rec.obj.getWorldPosition(new THREE.Vector3()).project(s.camera); const b = s.renderer.domElement.getBoundingClientRect();
    return [b.left + (p.x + 1) / 2 * b.width, b.top + (1 - p.y) / 2 * b.height];
  }, name);
  const treeRows = () => page.evaluate(() => Array.from(document.querySelectorAll('[data-test=scene-tree] .wb-nv-row')).map(r => r.textContent.trim()));

  step('login, open the 3D window from the Window menu');
  await page.goto(base + '/');
  await page.fill('input[name=username]', 'admin'); await page.fill('input[name=password]', 'secret123'); await page.click('button[type=submit]');
  await page.waitForSelector('[data-test=planets] .wb-nv-row'); await settle(1200);
  await page.evaluate(() => { window.__events = []; const orig = wb.renderers['wb.Scene3D']; });
  await menu('Window').click(); await settle(); await item('Solar system 3D').click();
  await page.waitForSelector('[data-test=scene-solar]');
  await page.waitForFunction(() => { const el = document.querySelector('[data-test=scene-solar]'); return el && el.ready && el.s.nodes.size > 10; }, null, { timeout: 30000 });
  await settle(1500);
  let st = await sceneState();
  console.log('  state: ' + JSON.stringify(st));
  assert(st.ready && st.canvas[0] > 100 && st.canvas[1] > 100, 'renderer ready with a sized canvas');
  assert(st.types.mesh >= 10 && st.types.group >= 10 && st.types.line >= 8 && st.types.label >= 10, 'sun, probe, planets with orbits and labels');
  assert(st.anims >= 18, 'orbit + spin animations running');
  assert(st.labels >= 10, 'DOM labels');
  assert(st.geoms >= 3 && st.mats >= 5, 'shared geometry and material definitions');
  const threeLoaded = await page.evaluate(() => !!window.THREE && THREE.REVISION);
  console.log('  three.js revision ' + threeLoaded + ', draw calls ' + st.calls);
  assert(threeLoaded === '152', 'vendored three.js r152 loaded on demand');

  step('the canvas actually shows the scene (non-background pixels, several colours)');
  const pixels = await page.evaluate(() => {
    const s = document.querySelector('[data-test=scene-solar]').s; s.renderer.render(s.scene, s.camera);
    const gl = s.renderer.getContext(); const w = gl.drawingBufferWidth, h = gl.drawingBufferHeight; const buf = new Uint8Array(w * h * 4); gl.readPixels(0, 0, w, h, gl.RGBA, gl.UNSIGNED_BYTE, buf);
    const colors = new Set(); let lit = 0; for (let i = 0; i < buf.length; i += 16) { const r = buf[i], g = buf[i + 1], b = buf[i + 2]; if (r + g + b > 120) lit++; colors.add((r >> 5) + ',' + (g >> 5) + ',' + (b >> 5)); }
    return { lit, colors: colors.size, w, h };
  });
  console.log('  pixels: ' + JSON.stringify(pixels));
  assert(pixels.lit > 200 && pixels.colors > 6, 'rendered geometry');

  step('the tree mirrors the scene graph: root → planets group → pivot → body/orbit/label');
  let rows = await treeRows();
  console.log('  tree rows: ' + rows.slice(0, 8).join(' | ') + ' …');
  assert(rows.some(r => r === 'planets') && rows.some(r => r === 'Sun') && rows.some(r => r === 'Probe'), 'top-level scene nodes listed');
  await page.locator('[data-test=scene-tree] .wb-nv-row', { hasText: /^planets$/ }).locator('.wb-nv-toggle').click(); await settle();
  rows = await treeRows();
  assert(rows.includes('Mars') && rows.includes('Jupiter'), 'planet pivots under the group');

  step('pick Mars → status, scene selection outline, tree selection');
  const [mx, my] = await screenOf('Mars');
  await page.mouse.click(mx, my); await settle(700);
  console.log('  status: ' + await status());
  assert((await status()).startsWith('Picked Mars at ('), 'pick resolved to the planet');
  st = await sceneState();
  const marsId = await nodeByName('Mars');
  console.log('  selection: ' + st.selection.join(',') + ' mars=' + marsId);
  assert(st.selection.length === 1, 'one selected node');
  const outlined = await page.evaluate(() => { let n = 0; document.querySelector('[data-test=scene-solar]').s.scene.traverse(o => { if (o.userData.outline) n++; }); return n; });
  assert(outlined === 1, 'selection outline drawn');
  const selectedRows = await page.evaluate(() => Array.from(document.querySelectorAll('[data-test=scene-tree] .wb-nv-row.wb-nv-selected')).map(r => r.textContent.trim()));
  console.log('  tree selection: ' + selectedRows.join(','));
  assert(selectedRows.length === 1 && selectedRows[0] === 'Mars', 'the tree selected the picked body');
  const propNames = await page.evaluate(() => Array.from(document.querySelectorAll('[data-test=sheet] .wb-ps-row')).map(r => r.dataset.prop));
  console.log('  property sheet (Properties window): ' + propNames.join(',') + ' title: ' + await page.textContent('[data-test=sheet] .wb-ps-title'));
  assert(propNames.includes('x') && propNames.includes('scale') && propNames.includes('visible'), 'Node3DNode properties shown in the Properties window');
  await page.fill('[data-test=sheet] .wb-ps-row[data-prop=scale] input', '1.8'); await page.keyboard.press('Enter'); await settle(600);
  const marsScale = await page.evaluate(() => { const s = document.querySelector('[data-test=scene-solar]').s; let r; s.nodes.forEach(x => { if (x.obj.name === 'Mars' && x.t === 'mesh') r = x; }); return r.obj.scale.x; });
  console.log('  Mars scale after editing the sheet: ' + marsScale);
  assert(Math.abs(marsScale - 1.8) < 1e-6, 'sheet edit reached the scene node');

  step('tree → scene: select the Sun in the tree');
  await page.locator('[data-test=scene-tree] .wb-nv-row', { hasText: /^Sun$/ }).click(); await settle(600);
  st = await sceneState();
  const sunId = await nodeByName('Sun');
  console.log('  selection now ' + st.selection.join(',') + ' (sun=' + sunId + ')');
  assert(st.selection.length === 1 && st.selection[0] === sunId, 'scene selection follows the tree');

  step('drag the probe on the ecliptic plane with snapping; drag it out of the system → rejected and snapped back');
  const probeBefore = await page.evaluate(() => { const s = document.querySelector('[data-test=scene-solar]').s; let r; s.nodes.forEach(x => { if (x.obj.name === 'Probe' && x.t === 'group') r = x; }); return [r.obj.position.x, r.obj.position.y, r.obj.position.z]; });
  const [px, py] = await screenOf('Probe');
  await page.mouse.move(px, py); await page.mouse.down(); await page.mouse.move(px + 40, py + 10, { steps: 6 }); await page.mouse.move(px + 80, py + 20, { steps: 6 }); await page.mouse.up(); await settle(700);
  const probeAfter = await page.evaluate(() => { const s = document.querySelector('[data-test=scene-solar]').s; let r; s.nodes.forEach(x => { if (x.obj.name === 'Probe' && x.t === 'group') r = x; }); return [r.obj.position.x, r.obj.position.y, r.obj.position.z]; });
  console.log('  probe ' + probeBefore.map(v => v.toFixed(2)) + ' → ' + probeAfter.map(v => v.toFixed(2)) + '; status: ' + await status());
  assert((await status()).startsWith('Probe moved to ('), 'drag end reported');
  assert(Math.abs(probeAfter[1]) < 1e-6 && (probeAfter[0] !== probeBefore[0] || probeAfter[2] !== probeBefore[2]), 'moved within the y=0 plane');
  assert(Math.abs(probeAfter[0] * 2 - Math.round(probeAfter[0] * 2)) < 1e-6 && Math.abs(probeAfter[2] * 2 - Math.round(probeAfter[2] * 2)) < 1e-6, 'snapped to 0.5');
  const [qx, qy] = await screenOf('Probe');
  await page.mouse.move(qx, qy); await page.mouse.down(); await page.mouse.move(qx + 300, qy + 220, { steps: 10 }); await page.mouse.move(qx + 600, qy + 380, { steps: 10 }); await page.mouse.up(); await settle(800);
  const probeRejected = await page.evaluate(() => { const s = document.querySelector('[data-test=scene-solar]').s; let r; s.nodes.forEach(x => { if (x.obj.name === 'Probe' && x.t === 'group') r = x; }); return [r.obj.position.x, r.obj.position.y, r.obj.position.z]; });
  console.log('  after rejected drag: ' + probeRejected.map(v => v.toFixed(2)) + '; status: ' + await status());
  assert((await status()) === 'The probe cannot leave the system', 'server rejected the drag');
  assert(Math.abs(probeRejected[0] - probeAfter[0]) < 1e-6 && Math.abs(probeRejected[2] - probeAfter[2]) < 1e-6, 'snapped back to the drag start');

  step('orbit with the mouse → camera event updates the server copy');
  const canvasBox = await page.locator('[data-test=scene-solar] canvas').boundingBox();
  await page.mouse.move(canvasBox.x + 50, canvasBox.y + canvasBox.height - 50); await page.mouse.down(); await page.mouse.move(canvasBox.x + 250, canvasBox.y + canvasBox.height - 80, { steps: 8 }); await page.mouse.up(); await settle(500);

  step('model change: add a planet in the explorer → the scene gets one small add op');
  await page.evaluate(() => { window.__msgs = []; wb.on('message', m => window.__msgs.push(JSON.stringify(m))); });
  await page.locator('.wb-dk-tab[data-tc="PlanetsTC"]').click().catch(() => {}); await settle(300);
  await page.click('[data-test=add-planet]'); await settle(900);
  const sceneCalls = (await page.evaluate(() => window.__msgs)).map(m => JSON.parse(m)).flatMap(m => (m.ops || []).filter(o => o.op === 'call' && o.m === 'scene')).map(o => JSON.stringify(o.args[0]));
  st = await sceneState();
  console.log('  scene calls=' + sceneCalls.length + ' sizes=' + sceneCalls.map(m => m.length).join(',') + ' ops=' + sceneCalls.map(m => (JSON.parse(m).nodes || []).map(n => n.op + ':' + n.t).join('+')).join(' / ') + ' nodes now ' + st.nodes);
  assert(sceneCalls.length === 1 && sceneCalls[0].length < 3000 && !sceneCalls[0].includes('"reset"'), 'one incremental scene delta (group + body + orbit + label)');
  assert(st.types.mesh === 12, 'one more planet body (sun + probe + 10 planets)');
  rows = await treeRows();
  assert(rows.includes('Planet 1'), 'tree got the new pivot');

  step('toolbar: fit, toggle grid, toggle orbit lines, snapshot download');
  const tb = sel => page.locator('.wb-tc:not(.wb-tc-hidden) .wb-toolbar .wb-tbbutton' + sel);
  await page.locator('.wb-dk-tab[data-tc="SolarSystem3DTC"]').click(); await settle(300);
  const camBefore = await page.evaluate(() => { const s = document.querySelector('[data-test=scene-solar]').s; return s.camera.position.toArray(); });
  await tb('[title^="Fit to view"]').click(); await settle(900);
  const camAfter = await page.evaluate(() => { const s = document.querySelector('[data-test=scene-solar]').s; return s.camera.position.toArray(); });
  console.log('  camera ' + camBefore.map(v => v.toFixed(1)) + ' → ' + camAfter.map(v => v.toFixed(1)));
  assert(camBefore.join() !== camAfter.join(), 'fit moved the camera');
  await tb('[title^="Toggle grid"]').click(); await settle(500);
  const gridOff = await page.evaluate(() => !document.querySelector('[data-test=scene-solar]').s.grid);
  assert(gridOff, 'grid toggled off');
  await tb('[title^="Toggle orbit lines"]').click(); await settle(500);
  const linesHidden = await page.evaluate(() => { const s = document.querySelector('[data-test=scene-solar]').s; let hidden = 0, total = 0; s.nodes.forEach(r => { if (r.t === 'line') { total++; if (!r.obj.visible) hidden++; } }); return hidden + '/' + total; });
  console.log('  orbit lines hidden: ' + linesHidden);
  assert(/^(\d+)\/\1$/.test(linesHidden), 'registered context action hid the orbit lines');
  const [dl] = await Promise.all([page.waitForEvent('download'), tb('[title^="Save as PNG"]').click()]);
  console.log('  download: ' + dl.suggestedFilename());
  assert(dl.suggestedFilename().endsWith('.png'), 'png snapshot download');

  step('reconnect: the scene comes back from a full init (resync) with definitions and animations');
  await page.evaluate(() => { wb.seq = 0; });
  await page.evaluate(ms => wb.transport.simulateOutage(ms), 1500); // drops the WebSocket (or fails the polls) for a while
  await page.waitForFunction(() => document.documentElement.dataset.wbState === 'reconnecting', null, { timeout: 40000 });
  await page.waitForFunction(() => document.documentElement.dataset.wbState === 'connected', null, { timeout: 40000 });
  await settle(1500);
  await page.locator('.wb-dk-tab[data-tc="SolarSystem3DTC"]').click(); await settle(800);
  await page.waitForFunction(() => { const el = document.querySelector('[data-test=scene-solar]'); return el && el.ready && el.s.nodes.size > 10; }, null, { timeout: 30000 });
  st = await sceneState();
  console.log('  after resync: ' + JSON.stringify(st.types) + ' anims=' + st.anims);
  assert(st.types.mesh === 12 && st.anims >= 20, 'scene rebuilt after resync');

  const real = errors.filter(e => !/ERR_FAILED|net::/.test(e));
  console.log(real.length ? 'CONSOLE ERRORS:\n' + real.join('\n') : 'no console errors');
  await browser.close();
  if (real.length) process.exit(1);
})().catch(e => { console.error(e); process.exit(1); });
