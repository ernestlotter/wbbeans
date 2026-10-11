/*
 * wb.Scene3D — the three.js viewport renderer. The server owns the scene graph and sends it as "scene" calls:
 * shared definitions (geoms/mats/texs, large ones as blobs from /wb/blob/), node ops (add/set/remove), camera,
 * controls, animation ops and one-shot commands (fit/flyTo/snapshot/download). This file builds the three.js
 * objects, renders on demand (continuously only while something animates), implements orbit/map/first-person
 * controls, picking, hover effects, constrained dragging, selection outlines, DOM labels and the tween engine.
 * three.js (r152, MIT, vendored next to this file) is loaded on first use, so sessions without a 3D window
 * never download it.
 */
(function () {
  var THREE_RES = 'org/webbeans/scene3d/client/three.min.js';
  var loadingThree = null;
  function loadThree() {
    if (window.THREE) return Promise.resolve();
    if (loadingThree) return loadingThree;
    loadingThree = new Promise(function (resolve, reject) {
      var s = document.createElement('script');
      s.src = wb.base + '/wb/res/' + THREE_RES;
      s.async = true;
      s.onload = function () { window.THREE ? resolve() : reject(new Error('three.js did not define THREE')); };
      s.onerror = function () { reject(new Error('Could not load three.js')); };
      document.head.appendChild(s);
    });
    return loadingThree;
  }
  var blobs = new Map();   // id → Promise<ArrayBuffer>, shared by every scene of the page
  function fetchBlob(id) {
    if (!blobs.has(id)) {
      blobs.set(id, fetch(wb.base + '/wb/blob/' + id, { credentials: 'same-origin' }).then(function (r) {
        if (!r.ok) throw new Error('blob ' + id + ': HTTP ' + r.status);
        return r.arrayBuffer();
      }));
    }
    return blobs.get(id);
  }
  function now() { return performance.now(); }
  function v3(a) { return new THREE.Vector3(a[0], a[1], a[2]); }
  function clamp(v, a, b) { return v < a ? a : v > b ? b : v; }
  var EASE = {
    'linear': function (t) { return t; },
    'ease-in': function (t) { return t * t; },
    'ease-out': function (t) { return t * (2 - t); },
    'ease-in-out': function (t) { return t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t; }
  };

  wb.register('wb.Scene3D', {
    create: function (ctx) {
      var el = wb.h('div', 'wb-scene3d'); el.tabIndex = 0;
      el.ctx = ctx; el.props = {}; el.queue = []; el.ready = false;
      el.labels = wb.h('div', 'wb-scene3d-labels'); el.appendChild(el.labels);
      el.overlay = wb.h('div', 'wb-scene3d-overlay', 'Loading 3D…'); el.appendChild(el.overlay);
      el.stats = wb.h('div', 'wb-scene3d-stats wb-hidden'); el.appendChild(el.stats);
      loadThree().then(function () { init(el); }).catch(function (err) {
        el.overlay.textContent = String(err && err.message || err); el.overlay.classList.add('wb-scene3d-error');
        el.ctx.emit('ready', { error: String(err && err.message || err) });
      });
      return el;
    },
    update: function (el, props, changed) {
      el.props = props; wb.common(el, props, changed);
      if (el.ready) applyProps(el, changed);
    },
    call: function (el, m, args) {
      if (m === 'scene') { if (el.ready) applyScene(el, args[0] || {}); else el.queue.push(args[0] || {}); }
    },
    insertChild: function () {}, removeChild: function () {},
    destroy: function (el) { dispose(el); }
  });

  /* ---------------- setup ---------------- */
  function init(el) {
    var T = THREE;
    var renderer;
    try {
      renderer = new T.WebGLRenderer({ antialias: el.props.antialias !== false, alpha: true, powerPreference: 'high-performance' });
    } catch (e) {
      el.overlay.textContent = 'WebGL is not available: ' + e.message; el.overlay.classList.add('wb-scene3d-error');
      el.ctx.emit('ready', { error: 'no webgl' }); return;
    }
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, el.props.pixelRatioCap || 2));
    renderer.shadowMap.enabled = !!el.props.shadows; renderer.shadowMap.type = T.PCFSoftShadowMap;
    renderer.domElement.className = 'wb-scene3d-canvas';
    el.insertBefore(renderer.domElement, el.labels);
    var s = {
      renderer: renderer, scene: new T.Scene(), camera: new T.PerspectiveCamera(50, 1, 0.1, 2000), target: new T.Vector3(),
      nodes: new Map(), geoms: new Map(), mats: new Map(), texs: new Map(), anims: new Map(), loads: [],
      controls: { mode: 'orbit', minDist: 0.1, maxDist: Infinity, minPolar: 0, maxPolar: Math.PI, pan: true, zoom: true, rotate: true, damping: true },
      needsRender: true, loop: null, hover: null, selection: [], fly: null, lastFrame: now(), frames: 0, fpsAt: now(), fps: 0,
      pickables: [], contextLost: false, keys: {}
    };
    s.camera.position.set(0, 5, 10); s.camera.lookAt(s.target);
    el.s = s; el.ready = true; el.overlay.classList.add('wb-hidden');
    renderer.domElement.addEventListener('webglcontextlost', function (e) { e.preventDefault(); s.contextLost = true; el.overlay.textContent = 'Graphics context lost'; el.overlay.classList.remove('wb-hidden'); });
    renderer.domElement.addEventListener('webglcontextrestored', function () { s.contextLost = false; el.overlay.classList.add('wb-hidden'); requestRender(el); });
    el.ro = new ResizeObserver(function () { resize(el); }); el.ro.observe(el);
    bindInput(el);
    resize(el);
    applyProps(el, new Set(['background', 'fog', 'grid', 'gridSize', 'axes', 'shadows', 'hoverEffect', 'selection', 'selectionColor', 'stats']));
    var q = el.queue; el.queue = [];
    q.forEach(function (p) { applyScene(el, p); });
    var gl = renderer.getContext();
    var dbg = gl.getExtension('WEBGL_debug_renderer_info');
    el.ctx.emit('ready', {
      webgl2: typeof WebGL2RenderingContext !== 'undefined' && gl instanceof WebGL2RenderingContext,
      renderer: dbg ? gl.getParameter(dbg.UNMASKED_RENDERER_WEBGL) : 'unknown',
      maxTextureSize: gl.getParameter(gl.MAX_TEXTURE_SIZE), pixelRatio: renderer.getPixelRatio()
    });
    requestRender(el);
  }
  function dispose(el) {
    if (el.ro) el.ro.disconnect();
    var s = el.s; if (!s) return;
    if (s.loop) cancelAnimationFrame(s.loop);
    s.nodes.forEach(function (rec) { if (rec.label) rec.label.remove(); });
    s.geoms.forEach(function (g) { g.dispose(); });
    s.mats.forEach(function (m) { m.dispose(); });
    s.texs.forEach(function (t) { t.dispose(); });
    s.renderer.dispose(); el.s = null; el.ready = false;
  }
  function resize(el) {
    var s = el.s; if (!s) return;
    var w = el.clientWidth, h = el.clientHeight; if (!w || !h) return;
    s.renderer.setSize(w, h, false);
    updateProjection(el);
    requestRender(el);
  }
  function updateProjection(el) {
    var s = el.s, w = el.clientWidth || 1, h = el.clientHeight || 1, c = s.camera;
    if (c.isPerspectiveCamera) c.aspect = w / h;
    else { var half = (c.userData.size || 10) / 2; c.left = -half * w / h; c.right = half * w / h; c.top = half; c.bottom = -half; }
    c.updateProjectionMatrix();
  }

  /* ---------------- properties ---------------- */
  function applyProps(el, changed) {
    var s = el.s, p = el.props, T = THREE;
    if (changed.has('background')) { s.scene.background = p.background == null ? null : new T.Color(p.background); }
    if (changed.has('fog')) {
      var f = p.fog; s.scene.fog = !f ? null : f.density ? new T.FogExp2(f.color, f.density) : new T.Fog(f.color, f.range[0], f.range[1]);
    }
    if (changed.has('grid') || changed.has('gridSize')) {
      if (s.grid) { s.scene.remove(s.grid); s.grid.geometry.dispose(); s.grid = null; }
      if (p.grid) { var gs = p.gridSize || [20, 20]; s.grid = new T.GridHelper(gs[0], gs[1], 0x888888, 0x444444); s.grid.material.transparent = true; s.grid.material.opacity = 0.5; s.grid.userData.helper = true; s.scene.add(s.grid); }
    }
    if (changed.has('axes')) {
      if (s.axes) { s.scene.remove(s.axes); s.axes = null; }
      if (p.axes) { s.axes = new T.AxesHelper(5); s.axes.userData.helper = true; s.scene.add(s.axes); }
    }
    if (changed.has('shadows')) { s.renderer.shadowMap.enabled = !!p.shadows; s.scene.traverse(function (o) { if (o.material) o.material.needsUpdate = true; }); }
    if (changed.has('pixelRatioCap')) s.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, p.pixelRatioCap || 2));
    if (changed.has('selection') || changed.has('selectionColor')) applySelection(el);
    if (changed.has('stats')) el.stats.classList.toggle('wb-hidden', !p.stats);
    requestRender(el);
  }

  /* ---------------- scene payloads ---------------- */
  function applyScene(el, p) {
    var s = el.s;
    if (p.reset) reset(el);
    if (p.texs) Object.keys(p.texs).forEach(function (id) { defineTexture(el, id, p.texs[id]); });
    if (p.mats) Object.keys(p.mats).forEach(function (id) { defineMaterial(el, id, p.mats[id]); });
    if (p.geoms) Object.keys(p.geoms).forEach(function (id) { defineGeometry(el, id, p.geoms[id]); });
    (p.nodes || []).forEach(function (op) {
      if (op.op === 'add') addNode(el, op);
      else if (op.op === 'set') setNode(el, op);
      else if (op.op === 'remove') removeNode(el, op.id);
    });
    if (p.camera) applyCamera(el, p.camera);
    if (p.controls) applyControls(el, p.controls);
    (p.anim || []).forEach(function (op) { animOp(el, op); });
    (p.cmd || []).forEach(function (c) { command(el, c); });
    rebuildPickables(el);
    applySelection(el);
    requestRender(el);
  }
  function reset(el) {
    var s = el.s;
    s.nodes.forEach(function (rec) { if (rec.label) rec.label.remove(); });
    s.nodes.clear(); s.anims.clear(); s.pickables = []; s.hover = null;
    s.scene.children.slice().forEach(function (o) { if (!o.userData.helper) s.scene.remove(o); });
    s.geoms.forEach(function (g) { g.dispose(); }); s.geoms.clear();
    s.mats.forEach(function (m) { m.dispose(); }); s.mats.clear();
    s.texs.forEach(function (t) { t.dispose(); }); s.texs.clear();
  }

  /* ---- definitions ---- */
  function defineTexture(el, id, d) {
    var s = el.s, T = THREE;
    var url = d.url.charAt(0) === '/' ? wb.base + d.url : d.url;
    var tex = new T.TextureLoader().load(url, function () { requestRender(el); });
    var wrap = d.wrap === 'repeat' ? T.RepeatWrapping : d.wrap === 'mirror' ? T.MirroredRepeatWrapping : T.ClampToEdgeWrapping;
    tex.wrapS = tex.wrapT = wrap;
    if (d.repeat) tex.repeat.set(d.repeat[0], d.repeat[1]);
    if (d.filter === 'nearest') { tex.magFilter = T.NearestFilter; tex.minFilter = T.NearestMipmapNearestFilter; }
    if (d.flipY === false) tex.flipY = false;
    if (d.srgb !== false) tex.colorSpace = T.SRGBColorSpace;
    tex.anisotropy = Math.min(4, s.renderer.capabilities.getMaxAnisotropy());
    var old = s.texs.get(id); if (old) old.dispose();
    s.texs.set(id, tex);
  }
  function buildMaterial(el, d) {
    var s = el.s, T = THREE, m;
    if (d.kind === 'line') { m = d.dash ? new T.LineDashedMaterial({ dashSize: d.dash[0], gapSize: d.dash[1] }) : new T.LineBasicMaterial(); m.linewidth = d.width || 1; }
    else if (d.kind === 'points') { m = new T.PointsMaterial({ size: d.size || 1, sizeAttenuation: d.sizeAttenuation !== false }); }
    else if (d.type === 'basic') m = new T.MeshBasicMaterial();
    else if (d.type === 'lambert') m = new T.MeshLambertMaterial();
    else if (d.type === 'phong') m = new T.MeshPhongMaterial();
    else if (d.type === 'physical') m = new T.MeshPhysicalMaterial();
    else m = new T.MeshStandardMaterial();
    fillMaterial(el, m, d); m.userData.def = d;
    return m;
  }
  function fillMaterial(el, m, d) {
    var s = el.s, T = THREE;
    m.color.set(d.color == null ? 0xffffff : d.color);
    m.opacity = d.opacity == null ? 1 : d.opacity; m.transparent = !!d.transparent || m.opacity < 1;
    if ('metalness' in m) { m.metalness = d.metalness == null ? 0.1 : d.metalness; m.roughness = d.roughness == null ? 0.6 : d.roughness; }
    if ('shininess' in m && d.shininess != null) m.shininess = d.shininess;
    if (m.emissive) { m.emissive.set(d.emissive || 0); m.emissiveIntensity = d.emissiveIntensity == null ? 1 : d.emissiveIntensity; }
    if ('map' in m) m.map = d.map ? s.texs.get(d.map) || null : null;
    if ('normalMap' in m) m.normalMap = d.normalMap ? s.texs.get(d.normalMap) || null : null;
    if ('emissiveMap' in m) m.emissiveMap = d.emissiveMap ? s.texs.get(d.emissiveMap) || null : null;
    m.side = d.side === 'double' ? T.DoubleSide : d.side === 'back' ? T.BackSide : T.FrontSide;
    if ('flatShading' in m) m.flatShading = !!d.flatShading;
    m.depthTest = d.depthTest !== false;
    if ('wireframe' in m) m.wireframe = !!d.wireframe;
    if (d.kind === 'line') { m.linewidth = d.width || 1; if (m.isLineDashedMaterial && d.dash) { m.dashSize = d.dash[0]; m.gapSize = d.dash[1]; } }
    if (d.kind === 'points') { m.size = d.size || 1; m.sizeAttenuation = d.sizeAttenuation !== false; }
    m.needsUpdate = true;
  }
  function sameClass(d1, d2) { return d1 && d2 && d1.kind === d2.kind && d1.type === d2.type && !!d1.dash === !!d2.dash; }
  function defineMaterial(el, id, d) {
    var s = el.s, old = s.mats.get(id);
    if (old && sameClass(old.userData.def, d)) { fillMaterial(el, old, d); old.userData.def = d; invalidateHover(el, id); return; }
    var m = buildMaterial(el, d);
    s.mats.set(id, m);
    if (old) {
      s.nodes.forEach(function (rec) { if (rec.matId === id && rec.obj.material) { rec.obj.material = m; } });
      old.dispose(); invalidateHover(el, id);
    }
  }
  function invalidateHover(el, matId) { el.s.nodes.forEach(function (rec) { if (rec.matId === matId && rec.hoverMat) { if (rec.hovering) rec.obj.material = el.s.mats.get(matId); rec.hoverMat.dispose(); rec.hoverMat = null; } }); }
  function defineGeometry(el, id, d) {
    var s = el.s, T = THREE, g, a = d.args || [];
    switch (d.prim) {
      case 'box': g = new T.BoxGeometry(a[0], a[1], a[2]); break;
      case 'sphere': g = new T.SphereGeometry(a[0], a[1] || 32, a[2] || 16); break;
      case 'plane': g = new T.PlaneGeometry(a[0], a[1]); break;
      case 'cylinder': g = new T.CylinderGeometry(a[0], a[1], a[2], a[3] || 32); break;
      case 'cone': g = new T.ConeGeometry(a[0], a[1], a[2] || 32); break;
      case 'torus': g = new T.TorusGeometry(a[0], a[1], a[2] || 16, a[3] || 64); break;
      case 'circle': g = new T.CircleGeometry(a[0], a[1] || 32); break;
      case 'ring': g = new T.RingGeometry(a[0], a[1], a[2] || 32); break;
      default:
        g = new T.BufferGeometry();
        if (d.blob) {
          var pr = fetchBlob(d.blob).then(function (buf) { decodeGeometry(g, buf); s.nodes.forEach(function (rec) { if (rec.geomId === id && rec.outline) refreshOutline(el, rec); }); requestRender(el); })
            .catch(function (e) { console.error('scene3d geometry ' + id, e); });
          s.loads.push(pr); pr.then(function () { var i = s.loads.indexOf(pr); if (i >= 0) s.loads.splice(i, 1); }, function () {});
        }
    }
    g.userData.def = d;
    var old = s.geoms.get(id); s.geoms.set(id, g);
    if (old) { s.nodes.forEach(function (rec) { if (rec.geomId === id && rec.obj.geometry) rec.obj.geometry = g; }); old.dispose(); }
  }
  function decodeGeometry(g, buf) {
    var T = THREE, dv = new DataView(buf), hlen = dv.getUint32(0, true);
    var header = JSON.parse(new TextDecoder().decode(new Uint8Array(buf, 4, hlen)));
    var base = 4 + hlen, attrs = header.attrs || {};
    Object.keys(attrs).forEach(function (name) {
      var a = attrs[name], arr = new Float32Array(buf.slice(base + a.offset, base + a.offset + a.count * a.size * 4));
      g.setAttribute(name, new T.BufferAttribute(arr, a.size));
    });
    if (header.index) { var ix = header.index; g.setIndex(new T.BufferAttribute(new Uint32Array(buf.slice(base + ix.offset, base + ix.offset + ix.count * 4)), 1)); }
    if (header.computeNormals || !attrs.normal) g.computeVertexNormals();
    g.computeBoundingSphere(); g.computeBoundingBox();
  }

  /* ---- nodes ---- */
  function addNode(el, op) {
    var s = el.s, T = THREE, obj, rec = { id: op.id, t: op.t, def: op };
    switch (op.t) {
      case 'group': obj = new T.Group(); break;
      case 'mesh': obj = new T.Mesh(geomOf(el, op.g), matOf(el, op.m)); rec.geomId = op.g; rec.matId = op.m; break;
      case 'imesh':
        obj = new T.InstancedMesh(geomOf(el, op.g), matOf(el, op.m), (op.inst && op.inst.count) || 0); rec.geomId = op.g; rec.matId = op.m;
        applyInstances(el, rec, obj, op.inst); break;
      case 'line': obj = op.closed ? new T.LineLoop(lineGeometry(op.pts), matOf(el, op.m)) : new T.Line(lineGeometry(op.pts), matOf(el, op.m)); rec.matId = op.m; obj.computeLineDistances(); break;
      case 'points': obj = new T.Points(pointsGeometry(op.pts, op.col), matOf(el, op.m)); rec.matId = op.m; if (op.col) obj.material = vertexColored(el, op.m); break;
      case 'label':
        obj = new T.Object3D(); rec.label = wb.h('div', 'wb-scene3d-label' + (op.css ? ' ' + op.css : ''), op.text || ''); rec.occ = !!op.occ; rec.off = op.off || [0, 0];
        el.labels.appendChild(rec.label); break;
      case 'light.ambient': obj = new T.AmbientLight(op.color, op.intensity); break;
      case 'light.hemisphere': obj = new T.HemisphereLight(op.color, op.ground, op.intensity); break;
      case 'light.directional': obj = new T.DirectionalLight(op.color, op.intensity); lightTarget(el, obj, op.target); obj.shadow.mapSize.set(2048, 2048); shadowBox(obj, op.shadowSize || 20); break;
      case 'light.point': obj = new T.PointLight(op.color, op.intensity, op.range ? op.range[0] : 0, op.range ? op.range[1] : 2); break;
      case 'light.spot': obj = new T.SpotLight(op.color, op.intensity, op.distance || 0, op.cone ? op.cone[0] : Math.PI / 6, op.cone ? op.cone[1] : 0.2); lightTarget(el, obj, op.target); break;
      default: obj = new T.Group();
    }
    obj.name = op.name || ''; obj.userData.id = op.id; rec.obj = obj;
    applyTransform(obj, op);
    obj.visible = op.vis !== false; rec.pick = op.pick !== false && op.t !== 'label' && op.t.indexOf('light') !== 0;
    setShadow(obj, op.shadow); rec.drag = op.drag || null;
    var parent = op.p ? s.nodes.get(op.p) : null;
    (parent ? parent.obj : s.scene).add(obj);
    s.nodes.set(op.id, rec);
  }
  function geomOf(el, id) { return (id && el.s.geoms.get(id)) || new THREE.BufferGeometry(); }
  function matOf(el, id) { return (id && el.s.mats.get(id)) || new THREE.MeshStandardMaterial({ color: 0xff00ff }); }
  function vertexColored(el, id) { var m = matOf(el, id).clone(); m.vertexColors = true; m.userData.derived = true; return m; }
  function lineGeometry(pts) { var g = new THREE.BufferGeometry(); g.setAttribute('position', new THREE.BufferAttribute(new Float32Array(pts || []), 3)); return g; }
  function pointsGeometry(pts, col) {
    var g = lineGeometry(pts);
    if (col) { var c = new Float32Array(col.length * 3); for (var i = 0; i < col.length; i++) { c[i * 3] = ((col[i] >> 16) & 255) / 255; c[i * 3 + 1] = ((col[i] >> 8) & 255) / 255; c[i * 3 + 2] = (col[i] & 255) / 255; } g.setAttribute('color', new THREE.BufferAttribute(c, 3)); }
    return g;
  }
  function lightTarget(el, light, t) { light.target.position.set(t ? t[0] : 0, t ? t[1] : 0, t ? t[2] : 0); light.userData.targetAttached = true; el.s.scene.add(light.target); }
  function shadowBox(light, size) { var c = light.shadow.camera; c.left = -size; c.right = size; c.top = size; c.bottom = -size; c.near = 0.5; c.far = size * 10; c.updateProjectionMatrix(); }
  function applyTransform(obj, op) {
    if (op.pos) obj.position.set(op.pos[0], op.pos[1], op.pos[2]);
    if (op.rot) obj.quaternion.set(op.rot[0], op.rot[1], op.rot[2], op.rot[3]);
    if (op.scl) obj.scale.set(op.scl[0], op.scl[1], op.scl[2]);
  }
  function setShadow(obj, sh) { if (!sh) { obj.castShadow = false; obj.receiveShadow = false; return; } obj.castShadow = !!sh[0]; obj.receiveShadow = !!sh[1]; }
  function applyInstances(el, rec, obj, inst) {
    var s = el.s, T = THREE; if (!inst) return;
    if (inst.count != null && inst.count !== obj.count) { obj.count = inst.count; }
    var n = obj.count;
    if (inst.blob) {
      var pr = fetchBlob(inst.blob).then(function (buf) {
        var xf = new Float32Array(buf, 0, n * 16); obj.instanceMatrix = new T.InstancedBufferAttribute(new Float32Array(xf), 16); obj.instanceMatrix.setUsage(T.DynamicDrawUsage);
        if (inst.colors) { obj.instanceColor = new T.InstancedBufferAttribute(new Float32Array(new Float32Array(buf, n * 16 * 4, n * 3)), 3); }
        obj.instanceMatrix.needsUpdate = true; obj.computeBoundingSphere(); requestRender(el);
      }).catch(function (e) { console.error('scene3d instances', e); });
      s.loads.push(pr); pr.then(function () { var i = s.loads.indexOf(pr); if (i >= 0) s.loads.splice(i, 1); }, function () {});
      return;
    }
    if (inst.xf) {
      if (obj.instanceMatrix.count < n) { obj.instanceMatrix = new T.InstancedBufferAttribute(new Float32Array(n * 16), 16); }
      obj.instanceMatrix.array.set(inst.xf); obj.instanceMatrix.needsUpdate = true;
      if (inst.col) { if (!obj.instanceColor || obj.instanceColor.count < n) obj.instanceColor = new T.InstancedBufferAttribute(new Float32Array(n * 3), 3); obj.instanceColor.array.set(inst.col); obj.instanceColor.needsUpdate = true; }
    }
    if (inst.upd) {
      inst.upd.forEach(function (u) {
        obj.instanceMatrix.array.set(u.xf, u.i * 16);
        if (u.col) { if (!obj.instanceColor) { obj.instanceColor = new T.InstancedBufferAttribute(new Float32Array(n * 3).fill(1), 3); } obj.instanceColor.array.set(u.col, u.i * 3); obj.instanceColor.needsUpdate = true; }
      });
      obj.instanceMatrix.needsUpdate = true;
    }
    obj.computeBoundingSphere();
  }
  function setNode(el, op) {
    var s = el.s, T = THREE, rec = s.nodes.get(op.id); if (!rec) return;
    var o = rec.obj;
    Object.keys(op).forEach(function (k) {
      var v = op[k];
      switch (k) {
        case 'name': o.name = v || ''; break;
        case 'pos': if (!(rec.dragging)) o.position.set(v[0], v[1], v[2]); break;
        case 'rot': o.quaternion.set(v[0], v[1], v[2], v[3]); break;
        case 'scl': o.scale.set(v[0], v[1], v[2]); break;
        case 'vis': o.visible = v !== false; if (rec.label) rec.label.classList.toggle('wb-hidden', v === false); break;
        case 'pick': rec.pick = v !== false; break;
        case 'shadow': setShadow(o, v); break;
        case 'drag': rec.drag = v || null; break;
        case 'g': rec.geomId = v; if (o.geometry) o.geometry = geomOf(el, v); if (rec.outline) refreshOutline(el, rec); break;
        case 'm': rec.matId = v; if (rec.hoverMat) { rec.hoverMat.dispose(); rec.hoverMat = null; } if (o.material) o.material = rec.t === 'points' && rec.def.col ? vertexColored(el, v) : matOf(el, v); break;
        case 'inst': applyInstances(el, rec, o, v); break;
        case 'pts': if (rec.t === 'line') { o.geometry.dispose(); o.geometry = lineGeometry(v); o.computeLineDistances(); } else if (rec.t === 'points') { o.geometry.dispose(); o.geometry = pointsGeometry(v, rec.def.col); } rec.def.pts = v; break;
        case 'closed': rec.def.closed = v; break;
        case 'col': rec.def.col = v; if (rec.t === 'points') { o.geometry.dispose(); o.geometry = pointsGeometry(rec.def.pts, v); o.material = v ? vertexColored(el, rec.matId) : matOf(el, rec.matId); } break;
        case 'text': if (rec.label) rec.label.textContent = v || ''; break;
        case 'css': if (rec.label) rec.label.className = 'wb-scene3d-label' + (v ? ' ' + v : ''); break;
        case 'occ': rec.occ = !!v; break;
        case 'off': rec.off = v || [0, 0]; break;
        case 'color': if (o.color) o.color.set(v); break;
        case 'intensity': if ('intensity' in o) o.intensity = v; break;
        case 'ground': if (o.groundColor) o.groundColor.set(v); break;
        case 'target': if (o.target) o.target.position.set(v[0], v[1], v[2]); break;
        case 'shadowSize': if (o.isDirectionalLight) shadowBox(o, v); break;
        case 'range': if (o.isPointLight) { o.distance = v[0]; o.decay = v[1]; } break;
        case 'cone': if (o.isSpotLight) { o.angle = v[0]; o.penumbra = v[1]; } break;
        case 'distance': if (o.isSpotLight) o.distance = v; break;
      }
    });
  }
  function removeNode(el, id) {
    var s = el.s, rec = s.nodes.get(id); if (!rec) return;
    rec.obj.traverse(function (o) {
      var r = o.userData.id != null ? s.nodes.get(o.userData.id) : null;
      if (r) { if (r.label) r.label.remove(); if (r.hoverMat) r.hoverMat.dispose(); s.nodes.delete(r.id); s.anims.forEach(function (a, aid) { if (a.rec === r) s.anims.delete(aid); }); if (s.hover === r) s.hover = null; }
      if (o.geometry && o.geometry.userData.def == null && o.geometry.dispose) o.geometry.dispose();
      if (o.userData.targetAttached && o.target) s.scene.remove(o.target);
    });
    if (rec.obj.parent) rec.obj.parent.remove(rec.obj);
    if (s.drag && s.drag.rec === rec) s.drag = null;
  }
  function rebuildPickables(el) {
    var s = el.s, list = [];
    s.nodes.forEach(function (rec) { if (rec.pick && rec.obj.visible && (rec.t === 'mesh' || rec.t === 'imesh' || rec.t === 'points' || rec.t === 'line')) list.push(rec.obj); });
    s.pickables = list;
  }

  /* ---- camera & controls ---- */
  function applyCamera(el, c) {
    var s = el.s, T = THREE, cam = s.camera;
    if ((c.proj === 'ortho') !== !!cam.isOrthographicCamera) {
      cam = c.proj === 'ortho' ? new T.OrthographicCamera(-1, 1, 1, -1, c.near, c.far) : new T.PerspectiveCamera(c.fov || 50, 1, c.near, c.far);
      s.camera = cam;
    }
    if (c.fov != null && cam.isPerspectiveCamera) cam.fov = c.fov;
    if (c.size != null) cam.userData.size = c.size;
    cam.near = c.near || 0.1; cam.far = c.far || 2000;
    if (c.up) cam.up.set(c.up[0], c.up[1], c.up[2]);
    if (c.pos) cam.position.set(c.pos[0], c.pos[1], c.pos[2]);
    if (c.target) s.target.set(c.target[0], c.target[1], c.target[2]);
    cam.lookAt(s.target); s.fly = null;
    updateProjection(el);
  }
  function applyControls(el, c) { el.s.controls = c; }

  /* ---- animations ---- */
  function animOp(el, op) {
    var s = el.s;
    if (op.op === 'play') {
      var rec = s.nodes.get(op.node); if (!rec) return;
      var a = { id: op.id, rec: rec, kind: op.kind, def: op, t0: now() + (op.delay || 0), paused: !!op.paused, pausedAt: 0, elapsed: 0 };
      if (op.kind === 'move_to') { a.from = rec.obj.position.clone(); a.to = v3(op.to); }
      if (op.kind === 'scale_to') { a.from = rec.obj.scale.clone(); a.to = v3(op.to); }
      if (op.kind === 'path') { var pts = []; for (var i = 0; i < op.path.length; i += 3) pts.push(new THREE.Vector3(op.path[i], op.path[i + 1], op.path[i + 2])); a.curve = new THREE.CatmullRomCurve3(pts); }
      if (op.kind === 'orbit') { a.center = v3(op.center); a.axis = v3(op.axis).normalize(); a.radial = rec.obj.position.clone().sub(a.center); a.angle = 0; }
      if (op.kind === 'rotate') { a.axis = v3(op.axis).normalize(); }
      a.ease = EASE[op.easing] || EASE.linear;
      s.anims.set(op.id, a);
    } else {
      var an = s.anims.get(op.id); if (!an) return;
      if (op.op === 'pause' && !an.paused) { an.paused = true; an.pausedAt = now(); }
      else if (op.op === 'resume' && an.paused) { an.paused = false; an.t0 += now() - an.pausedAt; }
      else if (op.op === 'stop') s.anims.delete(op.id);
      else if (op.op === 'seek') { an.t0 = now() - (op.t || 0); an.lastT = null; }
    }
    requestRender(el);
  }
  function stepAnims(el, t) {
    var s = el.s, done = [];
    s.anims.forEach(function (a) {
      if (a.paused) return;
      var o = a.rec.obj, dt = a.lastT == null ? 0 : (t - a.lastT) / 1000; a.lastT = t;
      if (t < a.t0) return;
      if (a.kind === 'rotate') { o.rotateOnAxis(a.axis, a.def.rate * dt); return; }
      if (a.kind === 'orbit') { a.angle += a.def.rate * dt; var r = a.radial.clone().applyAxisAngle(a.axis, a.angle); o.position.copy(a.center).add(r); return; }
      var dur = a.def.duration || 1, raw = (t - a.t0) / dur, finished = false;
      if (a.def.loop) { raw = a.def.yoyo ? raw % 2 : raw % 1; if (a.def.yoyo && raw > 1) raw = 2 - raw; }
      else if (a.def.yoyo) { if (raw >= 2) { raw = 0; finished = true; } else if (raw > 1) raw = 2 - raw; }
      else if (raw >= 1) { raw = 1; finished = true; }
      var k = a.ease(clamp(raw, 0, 1));
      if (a.kind === 'move_to') o.position.lerpVectors(a.from, a.to, k);
      else if (a.kind === 'scale_to') o.scale.lerpVectors(a.from, a.to, k);
      else if (a.kind === 'path') o.position.copy(a.curve.getPoint(k));
      if (finished) done.push(a);
    });
    done.forEach(function (a) {
      s.anims.delete(a.id); var o = a.rec.obj;
      el.ctx.emit('animDone', { id: a.id, node: a.rec.id, pos: [o.position.x, o.position.y, o.position.z], rot: [o.quaternion.x, o.quaternion.y, o.quaternion.z, o.quaternion.w], scl: [o.scale.x, o.scale.y, o.scale.z] });
    });
  }

  /* ---- commands ---- */
  function command(el, c) {
    var s = el.s;
    switch (c.c) {
      case 'fit': whenLoaded(el, function () { fit(el, c.node ? s.nodes.get(c.node) : null); }); break;
      case 'flyTo': flyTo(el, v3(c.pos), v3(c.target), c.up ? v3(c.up) : null, c.duration || 0); break;
      case 'snapshot': whenLoaded(el, function () { try { el.ctx.emit('snapshot', { id: c.id, data: snapshot(el, c.w, c.h) }); } catch (e) { el.ctx.emit('snapshot', { id: c.id, error: String(e.message || e) }); } }); break;
      case 'download': whenLoaded(el, function () { var a = document.createElement('a'); a.href = snapshot(el, c.w, c.h); a.download = c.name || 'scene.png'; document.body.appendChild(a); a.click(); a.remove(); }); break;
    }
  }
  function whenLoaded(el, fn) { var s = el.s; if (!s.loads.length) { fn(); return; } Promise.all(s.loads.slice()).then(fn, fn); }
  function fit(el, rec) {
    var s = el.s, T = THREE, box = new T.Box3();
    if (rec) box.setFromObject(rec.obj);
    else s.scene.children.forEach(function (o) { if (!o.userData.helper && !o.isLight && o.visible) box.expandByObject(o); });
    if (box.isEmpty()) return;
    var center = box.getCenter(new T.Vector3()), size = box.getSize(new T.Vector3()), radius = Math.max(size.x, size.y, size.z) * 0.5 || 1;
    var cam = s.camera, dir = cam.position.clone().sub(s.target); if (dir.lengthSq() < 1e-9) dir.set(0, 0.5, 1); dir.normalize();
    var dist = cam.isPerspectiveCamera ? radius / Math.sin(Math.min(1.2, (cam.fov * Math.PI / 180) / 2)) * 1.1 : radius * 2;
    if (cam.isOrthographicCamera) { cam.userData.size = radius * 2.2; updateProjection(el); }
    flyTo(el, center.clone().add(dir.multiplyScalar(dist)), center, null, 500);
  }
  function flyTo(el, pos, target, up, ms) {
    var s = el.s;
    if (!ms) { s.camera.position.copy(pos); s.target.copy(target); if (up) s.camera.up.copy(up); s.camera.lookAt(s.target); s.fly = null; requestRender(el); return; }
    s.fly = { p0: s.camera.position.clone(), t0: s.target.clone(), p1: pos, t1: target, start: now(), ms: ms };
    requestRender(el);
  }
  function stepFly(el, t) {
    var s = el.s, f = s.fly; if (!f) return;
    var k = EASE['ease-in-out'](clamp((t - f.start) / f.ms, 0, 1));
    s.camera.position.lerpVectors(f.p0, f.p1, k); s.target.lerpVectors(f.t0, f.t1, k); s.camera.lookAt(s.target);
    if (k >= 1) { s.fly = null; emitCamera(el); }
  }
  function snapshot(el, w, h) {
    var s = el.s, r = s.renderer, cw = el.clientWidth, ch = el.clientHeight, pr = r.getPixelRatio();
    if (w && h && (w !== cw || h !== ch)) {
      r.setPixelRatio(1); r.setSize(w, h, false); var cam = s.camera;
      if (cam.isPerspectiveCamera) { cam.aspect = w / h; } else { var half = (cam.userData.size || 10) / 2; cam.left = -half * w / h; cam.right = half * w / h; }
      cam.updateProjectionMatrix();
      try { render(el); return r.domElement.toDataURL('image/png'); } finally { r.setPixelRatio(pr); r.setSize(cw, ch, false); updateProjection(el); requestRender(el); }
    }
    render(el); return r.domElement.toDataURL('image/png');
  }

  /* ---------------- rendering ---------------- */
  function requestRender(el) {
    var s = el.s; if (!s) return;
    s.needsRender = true;
    if (!s.loop) s.loop = requestAnimationFrame(function () { s.loop = null; frame(el); });
  }
  function active(s) { return s.anims.size > 0 || s.fly || (s.controls.autoRotate && !s.controls.locked) || s.drag || Object.keys(s.keys).length; }
  function frame(el) {
    var s = el.s; if (!s) return;
    var t = now();
    stepAnims(el, t); stepFly(el, t); stepKeys(el, (t - s.lastFrame) / 1000);
    if (s.controls.autoRotate && !s.controls.locked && !s.drag && !s.pointer) { orbitBy(el, -s.controls.autoRotate * (t - s.lastFrame) / 1000, 0); }
    s.lastFrame = t;
    if (!s.contextLost) render(el);
    s.needsRender = false;
    if (active(s)) s.loop = requestAnimationFrame(function () { s.loop = null; frame(el); });
  }
  function render(el) {
    var s = el.s;
    s.renderer.render(s.scene, s.camera);
    updateLabels(el);
    s.frames++;
    var t = now();
    if (t - s.fpsAt > 1000) { s.fps = Math.round(s.frames * 1000 / (t - s.fpsAt)); s.frames = 0; s.fpsAt = t; if (el.props.stats) el.stats.textContent = s.fps + ' fps · ' + s.renderer.info.render.calls + ' calls · ' + s.renderer.info.render.triangles + ' tris'; }
  }
  var _p = null, _ray = null;
  function updateLabels(el) {
    var s = el.s, w = el.clientWidth, h = el.clientHeight; if (!_p) { _p = new THREE.Vector3(); _ray = new THREE.Raycaster(); }
    s.nodes.forEach(function (rec) {
      if (!rec.label) return;
      var vis = rec.obj.visible; for (var o = rec.obj.parent; vis && o; o = o.parent) vis = o.visible;
      if (!vis) { rec.label.style.display = 'none'; return; }
      rec.obj.getWorldPosition(_p);
      if (rec.occ && s.pickables.length) {
        var dir = _p.clone().sub(s.camera.position), dist = dir.length(); _ray.set(s.camera.position, dir.normalize()); _ray.far = dist - 0.01;
        var hits = _ray.intersectObjects(s.pickables, false);
        if (hits.length && hits[0].distance < dist - 0.01 && hits[0].object !== rec.obj.parent) { rec.label.style.display = 'none'; return; }
      }
      _p.project(s.camera);
      if (_p.z > 1 || _p.z < -1) { rec.label.style.display = 'none'; return; }
      rec.label.style.display = '';
      rec.label.style.transform = 'translate(-50%, -50%) translate(' + ((_p.x + 1) / 2 * w + rec.off[0]).toFixed(1) + 'px,' + ((1 - _p.y) / 2 * h + rec.off[1]).toFixed(1) + 'px)';
    });
  }

  /* ---------------- selection & hover ---------------- */
  function applySelection(el) {
    var s = el.s, ids = el.props.selection || [], set = new Set(ids);
    s.nodes.forEach(function (rec) {
      var want = set.has(rec.id);
      if (want && !rec.outline) addOutline(el, rec);
      else if (!want && rec.outline) removeOutline(rec);
      else if (want && rec.outline) rec.outline.forEach(function (o) { o.material.color.set(el.props.selectionColor == null ? 0xffb000 : el.props.selectionColor); });
    });
  }
  function addOutline(el, rec) {
    var T = THREE, color = el.props.selectionColor == null ? 0xffb000 : el.props.selectionColor, list = [];
    rec.obj.traverse(function (o) {
      if (o.isMesh && o.geometry && o.geometry.attributes.position) {
        var edges = new T.LineSegments(new T.EdgesGeometry(o.geometry, 30), new T.LineBasicMaterial({ color: color, depthTest: false, transparent: true, opacity: 0.95 }));
        edges.userData.outline = true; edges.renderOrder = 999; edges.scale.setScalar(o.isInstancedMesh ? 0 : 1.002);
        o.add(edges); list.push(edges);
      }
    });
    rec.outline = list;
  }
  function removeOutline(rec) { (rec.outline || []).forEach(function (e) { if (e.parent) e.parent.remove(e); e.geometry.dispose(); e.material.dispose(); }); rec.outline = null; }
  function refreshOutline(el, rec) { removeOutline(rec); addOutline(el, rec); }
  function setHover(el, rec, inst) {
    var s = el.s, mode = el.props.hoverEffect || 'CURSOR';
    if (s.hover === rec) return;
    if (s.hover) { var old = s.hover; old.hovering = false; if (old.hoverMat && old.obj.material === old.hoverMat) old.obj.material = s.mats.get(old.matId) || old.obj.material; if (old.hoverOutline) { removeOutline({ outline: old.hoverOutline }); old.hoverOutline = null; } }
    s.hover = rec;
    el.classList.toggle('wb-scene3d-hover', !!rec && mode !== 'NONE');
    if (rec && mode === 'HIGHLIGHT' && rec.obj.material && !rec.obj.material.userData.derived) {
      if (!rec.hoverMat) { var m = rec.obj.material.clone(); if (m.emissive) { m.emissive.add(new THREE.Color(0x2a2a2a)); } else if (m.color) { m.color.offsetHSL(0, 0, 0.12); } rec.hoverMat = m; }
      rec.hovering = true; rec.obj.material = rec.hoverMat;
    } else if (rec && mode === 'OUTLINE' && !rec.outline) {
      var tmp = { obj: rec.obj }; addOutline(el, tmp); rec.hoverOutline = tmp.outline; rec.hoverOutline.forEach(function (e) { e.material.color.set(0x60a5fa); });
    }
    requestRender(el);
  }

  /* ---------------- input ---------------- */
  function bindInput(el) {
    var s = el.s, c = s.renderer.domElement;
    c.style.touchAction = 'none';
    c.addEventListener('pointerdown', function (e) { onDown(el, e); });
    c.addEventListener('pointermove', function (e) { onMove(el, e); });
    c.addEventListener('pointerup', function (e) { onUp(el, e); });
    c.addEventListener('pointercancel', function (e) { onUp(el, e); });
    c.addEventListener('pointerleave', function () { if (!s.pointer) { setHover(el, null); if (el.ctx.listens('hover') && s.hoverSent) { s.hoverSent = false; el.ctx.emit('hover', {}); } } });
    c.addEventListener('wheel', function (e) { onWheel(el, e); }, { passive: false });
    c.addEventListener('contextmenu', function (e) { e.preventDefault(); });
    c.addEventListener('dblclick', function (e) { var hit = pick(el, e); el.ctx.emit('pick', pickData(el, hit, e, true)); });
    el.addEventListener('keydown', function (e) { if (s.controls.mode === 'first_person') { s.keys[e.key.toLowerCase()] = true; requestRender(el); e.preventDefault(); } });
    el.addEventListener('keyup', function (e) { delete s.keys[e.key.toLowerCase()]; });
  }
  function ndc(el, e) { var r = el.s.renderer.domElement.getBoundingClientRect(); return new THREE.Vector2(((e.clientX - r.left) / r.width) * 2 - 1, -((e.clientY - r.top) / r.height) * 2 + 1); }
  function local(el, e) { var r = el.s.renderer.domElement.getBoundingClientRect(); return [Math.round(e.clientX - r.left), Math.round(e.clientY - r.top)]; }
  var _rc = null;
  function pick(el, e) {
    var s = el.s; if (!_rc) _rc = new THREE.Raycaster(); _rc.params.Points = { threshold: 0.15 }; _rc.params.Line = { threshold: 0.1 };
    _rc.setFromCamera(ndc(el, e), s.camera);
    var hits = _rc.intersectObjects(s.pickables, false);
    for (var i = 0; i < hits.length; i++) { var h = hits[i]; if (h.object.userData.outline) continue; var rec = s.nodes.get(h.object.userData.id); if (rec) return { rec: rec, hit: h }; }
    return null;
  }
  function pickData(el, p, e, dbl) {
    var d = { button: e.button || 0, ctrl: e.ctrlKey || e.metaKey, shift: e.shiftKey, alt: e.altKey, x: local(el, e)[0], y: local(el, e)[1] };
    if (dbl) d.dbl = true;
    if (p) {
      d.node = p.rec.id; d.point = [p.hit.point.x, p.hit.point.y, p.hit.point.z]; d.dist = p.hit.distance;
      if (p.hit.face) { var n = p.hit.face.normal.clone().transformDirection(p.hit.object.matrixWorld); d.normal = [n.x, n.y, n.z]; }
      if (p.hit.instanceId != null) d.inst = p.hit.instanceId;
    }
    return d;
  }
  function onDown(el, e) {
    var s = el.s; el.focus({ preventScroll: true });
    s.renderer.domElement.setPointerCapture(e.pointerId);
    var pointers = s.pointers || (s.pointers = new Map()); pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
    if (pointers.size > 1) { s.pinch = pinchState(pointers); s.pointer = null; return; }
    var p = (e.button === 0 && !s.controls.locked) ? pick(el, e) : null;
    var dr = p ? dragTargetOf(el, p.rec) : null;
    if (dr) { startDrag(el, { rec: dr, hit: p.hit }, e); return; }
    s.pointer = { x: e.clientX, y: e.clientY, sx: e.clientX, sy: e.clientY, button: e.button, moved: false, shift: e.shiftKey, hit: p || pick(el, e) };
  }
  function onMove(el, e) {
    var s = el.s;
    if (s.pointers && s.pointers.has(e.pointerId)) s.pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
    if (s.drag) { moveDrag(el, e); return; }
    if (s.pinch && s.pointers.size > 1) { var ps = pinchState(s.pointers); var k = ps.dist / (s.pinch.dist || 1); dollyBy(el, 1 / k); panBy(el, ps.cx - s.pinch.cx, ps.cy - s.pinch.cy); s.pinch = ps; return; }
    if (s.pointer) {
      var dx = e.clientX - s.pointer.x, dy = e.clientY - s.pointer.y; s.pointer.x = e.clientX; s.pointer.y = e.clientY;
      if (Math.abs(e.clientX - s.pointer.sx) + Math.abs(e.clientY - s.pointer.sy) > 3) s.pointer.moved = true;
      if (s.controls.locked || s.controls.mode === 'none') return;
      var pan = s.pointer.button === 2 || s.pointer.button === 1 || (s.pointer.button === 0 && s.pointer.shift);
      if (s.controls.mode === 'first_person') { lookBy(el, dx, dy); }
      else if (pan) { if (s.controls.pan !== false) panBy(el, dx, dy); }
      else if (s.controls.rotate !== false) orbitBy(el, -dx * 2 * Math.PI / el.clientHeight, -dy * 2 * Math.PI / el.clientHeight);
      return;
    }
    // hover (throttled)
    var t = now(); if (s.hoverAt && t - s.hoverAt < 60) { s.hoverPending = e; if (!s.hoverTimer) s.hoverTimer = setTimeout(function () { s.hoverTimer = null; if (s.hoverPending) onMove(el, s.hoverPending); }, 60); return; }
    s.hoverAt = t; s.hoverPending = null;
    var wants = el.ctx.listens('hover') || (el.props.hoverEffect || 'CURSOR') !== 'NONE';
    if (!wants || !s.pickables.length) return;
    var p = pick(el, e);
    setHover(el, p ? p.rec : null);
    if (el.ctx.listens('hover')) {
      var id = p ? p.rec.id : null, inst = p && p.hit.instanceId != null ? p.hit.instanceId : -1;
      if (id !== s.hoverSentId || inst !== s.hoverSentInst) { s.hoverSentId = id; s.hoverSentInst = inst; s.hoverSent = !!id; var d = pickData(el, p, e); delete d.button; el.ctx.emit('hover', d); }
    }
  }
  function onUp(el, e) {
    var s = el.s;
    if (s.pointers) s.pointers.delete(e.pointerId);
    if (s.drag) { endDrag(el, e); return; }
    if (s.pinch) { if (!s.pointers.size) { s.pinch = null; emitCamera(el); } return; }
    var p = s.pointer; s.pointer = null; if (!p) return;
    if (p.moved) { emitCamera(el); return; }
    if (e.type === 'pointercancel') return;
    var hit = pick(el, e);
    el.ctx.emit('pick', pickData(el, hit, e, false));
  }
  function pinchState(pointers) { var a = []; pointers.forEach(function (p) { a.push(p); }); var dx = a[1].x - a[0].x, dy = a[1].y - a[0].y; return { dist: Math.sqrt(dx * dx + dy * dy), cx: (a[0].x + a[1].x) / 2, cy: (a[0].y + a[1].y) / 2 }; }
  function onWheel(el, e) {
    var s = el.s; if (s.controls.locked || s.controls.zoom === false || s.controls.mode === 'none') return;
    e.preventDefault();
    dollyBy(el, Math.exp(clamp(e.deltaY, -300, 300) * 0.0015));
    clearTimeout(s.wheelTimer); s.wheelTimer = setTimeout(function () { emitCamera(el); }, 200);
  }
  var _q = null, _qi = null, _sph = null, _off = null;
  function orbitBy(el, dTheta, dPhi) {
    var s = el.s, T = THREE, cam = s.camera;
    if (!_q) { _q = new T.Quaternion(); _qi = new T.Quaternion(); _sph = new T.Spherical(); _off = new T.Vector3(); }
    _q.setFromUnitVectors(cam.up, new T.Vector3(0, 1, 0)); _qi.copy(_q).invert();
    _off.copy(cam.position).sub(s.target).applyQuaternion(_q);
    _sph.setFromVector3(_off);
    _sph.theta += dTheta; _sph.phi += dPhi;
    _sph.phi = clamp(_sph.phi, Math.max(1e-4, s.controls.minPolar || 0), Math.min(Math.PI - 1e-4, s.controls.maxPolar == null ? Math.PI : s.controls.maxPolar));
    _sph.makeSafe();
    _off.setFromSpherical(_sph).applyQuaternion(_qi);
    cam.position.copy(s.target).add(_off); cam.lookAt(s.target);
    requestRender(el);
  }
  function dollyBy(el, scale) {
    var s = el.s, cam = s.camera;
    if (cam.isOrthographicCamera) { cam.userData.size = clamp((cam.userData.size || 10) * scale, 0.01, 1e6); updateProjection(el); requestRender(el); return; }
    var off = cam.position.clone().sub(s.target), d = clamp(off.length() * scale, s.controls.minDist || 0.1, s.controls.maxDist == null ? Infinity : s.controls.maxDist);
    cam.position.copy(s.target).add(off.normalize().multiplyScalar(d));
    requestRender(el);
  }
  function panBy(el, dx, dy) {
    var s = el.s, cam = s.camera, h = el.clientHeight || 1;
    var dist = cam.isPerspectiveCamera ? cam.position.distanceTo(s.target) * 2 * Math.tan(cam.fov * Math.PI / 360) : (cam.userData.size || 10);
    var right = new THREE.Vector3().setFromMatrixColumn(cam.matrix, 0), up = new THREE.Vector3().setFromMatrixColumn(cam.matrix, 1);
    var move = right.multiplyScalar(-dx * dist / h).add(up.multiplyScalar(dy * dist / h));
    cam.position.add(move); s.target.add(move);
    requestRender(el);
  }
  function lookBy(el, dx, dy) {
    var s = el.s, cam = s.camera, dir = s.target.clone().sub(cam.position);
    var sph = new THREE.Spherical().setFromVector3(dir); sph.theta -= dx * 0.004; sph.phi = clamp(sph.phi + dy * 0.004, 0.05, Math.PI - 0.05);
    dir.setFromSpherical(sph); s.target.copy(cam.position).add(dir); cam.lookAt(s.target); requestRender(el);
  }
  function stepKeys(el, dt) {
    var s = el.s, k = s.keys, cam = s.camera; if (s.controls.mode !== 'first_person' || s.controls.locked) return false;
    var sp = (s.controls.speed || 5) * dt, fwd = s.target.clone().sub(cam.position).normalize(), right = new THREE.Vector3().crossVectors(fwd, cam.up).normalize(), mv = new THREE.Vector3(), any = false;
    if (k.w || k.arrowup) { mv.add(fwd); any = true; } if (k.s || k.arrowdown) { mv.sub(fwd); any = true; }
    if (k.d || k.arrowright) { mv.add(right); any = true; } if (k.a || k.arrowleft) { mv.sub(right); any = true; }
    if (k.q) { mv.y -= 1; any = true; } if (k.e) { mv.y += 1; any = true; }
    if (any) { mv.normalize().multiplyScalar(sp); cam.position.add(mv); s.target.add(mv); return true; }
    return false;
  }
  function emitCamera(el) {
    var s = el.s, c = s.camera;
    el.ctx.emit('camera', { pos: [c.position.x, c.position.y, c.position.z], target: [s.target.x, s.target.y, s.target.z], up: [c.up.x, c.up.y, c.up.z] });
  }

  /* ---------------- dragging ---------------- */
  function dragTargetOf(el, rec) {
    var s = el.s;
    for (var o = rec.obj; o; o = o.parent) { var r = o.userData.id != null ? s.nodes.get(o.userData.id) : null; if (r && r.drag) return r; }
    return null;
  }
  function startDrag(el, p, e) {
    var s = el.s, T = THREE, rec = p.rec, obj = rec.obj, c = rec.drag;
    var worldPos = obj.getWorldPosition(new T.Vector3());
    var d = { rec: rec, start: obj.position.clone(), startWorld: worldPos, grab: p.hit.point.clone().sub(worldPos), lastSent: 0 };
    if (c.plane) { d.plane = new T.Plane(new T.Vector3(c.plane[0], c.plane[1], c.plane[2]), -c.plane[3]); }
    else if (c.axis) { d.axis = new T.Vector3(c.axis[0], c.axis[1], c.axis[2]).normalize(); }
    else { var n = s.camera.getWorldDirection(new T.Vector3()).negate(); d.plane = new T.Plane().setFromNormalAndCoplanarPoint(n, worldPos); }
    d.snap = c.snap || 0; s.drag = d; rec.dragging = true; s.pointer = null;
    el.classList.add('wb-scene3d-dragging');
    el.ctx.emit('drag', { node: rec.id, phase: 'start', pos: [obj.position.x, obj.position.y, obj.position.z] });
    requestRender(el);
  }
  function moveDrag(el, e) {
    var s = el.s, T = THREE, d = s.drag, obj = d.rec.obj;
    if (!_rc) _rc = new T.Raycaster(); _rc.setFromCamera(ndc(el, e), s.camera);
    var target = new T.Vector3();
    if (d.plane) { if (!_rc.ray.intersectPlane(d.plane, target)) return; target.sub(d.grab); }
    else {
      // closest point on the axis line to the pointer ray
      var p0 = d.startWorld, u = d.axis, r0 = _rc.ray.origin, v = _rc.ray.direction, w0 = p0.clone().sub(r0);
      var a = u.dot(u), b = u.dot(v), cc = v.dot(v), dd = u.dot(w0), ee = v.dot(w0), den = a * cc - b * b;
      var sc = Math.abs(den) < 1e-9 ? 0 : (b * ee - cc * dd) / den;
      target.copy(p0).add(u.clone().multiplyScalar(sc)).sub(d.grab.clone().projectOnVector(u));
    }
    if (obj.parent) obj.parent.worldToLocal(target);
    if (d.snap > 0) { target.x = Math.round(target.x / d.snap) * d.snap; target.y = Math.round(target.y / d.snap) * d.snap; target.z = Math.round(target.z / d.snap) * d.snap; }
    if (d.plane && d.rec.drag.plane) { var ax = d.rec.drag.plane; if (ax[0]) target.x = ax[3]; if (ax[1]) target.y = ax[3]; if (ax[2]) target.z = ax[3]; }
    if (d.axis) { var ux = d.rec.drag.axis; if (!ux[0]) target.x = d.start.x; if (!ux[1]) target.y = d.start.y; if (!ux[2]) target.z = d.start.z; }
    obj.position.copy(target); requestRender(el);
    var t = now(); if (t - d.lastSent > 50) { d.lastSent = t; el.ctx.emit('drag', { node: d.rec.id, phase: 'move', pos: [target.x, target.y, target.z] }); }
  }
  function endDrag(el, e) {
    var s = el.s, d = s.drag, obj = d.rec.obj; s.drag = null; d.rec.dragging = false;
    el.classList.remove('wb-scene3d-dragging');
    el.ctx.emit('drag', { node: d.rec.id, phase: 'end', pos: [obj.position.x, obj.position.y, obj.position.z] });
    requestRender(el);
  }
})();
