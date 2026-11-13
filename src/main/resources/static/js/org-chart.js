/*
 * Organizational chart (templates/organization-chart.html).
 *
 *  - Automatic tree layout: each team sits in one row under its supervisor, joined by a line
 *    from the middle of the supervisor's box.
 *  - Boxes can be dragged; connector lines follow. "Move with branch" moves everyone below.
 *    Admins' arrangement is saved for everyone; other users' dragging is temporary.
 *  - Drag empty space to pan, Ctrl/⌘ + scroll (or pinch) to zoom, Fit / zoom buttons.
 *  - Each department gets its own colour; click a department in the legend to highlight it.
 *  - Search, collapsible branches, staff profile dialog, add/edit dialog for admins.
 *
 * Re-initialises after sidebar navigation (app:navigated) and cleans up its listeners.
 */
(function () {
  'use strict';

  var CARD_W = 236, CARD_H = 104, GAP_X = 28, GAP_Y = 72, STACK_SIDE = 22, STACK_GAP = 16;
  // Every team is laid out in one row under its supervisor. To show big teams as a two-column
  // list instead, set this to the team size where the list should start (e.g. 4).
  var STACK_MIN = Infinity;
  var BUS = 34;                      // distance from a parent's bottom to the shared horizontal line
  var MIN_K = 0.25, MAX_K = 1.75;
  // Ordered so the first departments get the most different colours (no red: that's the CBM
  // accent and the error colour). Departments are assigned in alphabetical order.
  var PALETTE = ['#2563eb', '#ea580c', '#0d9488', '#9333ea', '#db2777', '#65a30d',
                 '#0891b2', '#a16207', '#4f46e5', '#be123c', '#15803d', '#78350f'];
  var TOP_COLOR = '#101d31', NONE_COLOR = '#6b7a8f';

  var active = null;   // current chart instance

  function init() {
    if (active) { active.destroy(); active = null; }
    var root = document.getElementById('orgChart');
    if (root) active = createChart(root);
  }

  function createChart(root) {
    var ac = new AbortController();
    var on = function (target, type, fn, opts) {
      target.addEventListener(type, fn, Object.assign({ signal: ac.signal }, opts || {}));
    };
    var $ = function (id) { return document.getElementById(id); };

    var admin = root.getAttribute('data-admin') === 'true';
    var viewport = $('ocViewport'), stage = $('ocStage'), nodesEl = $('ocNodes'), svg = $('ocLines');
    var saveState = $('ocSaveState'), zoomLabel = $('ocZoomLevel');

    var data = parseJson('ocData', []);
    var accounts = parseJson('ocAccounts', []);
    var byId = new Map();
    data.forEach(function (n) { n.children = []; n.dx = +n.dx || 0; n.dy = +n.dy || 0; byId.set(n.id, n); });
    var roots = [];
    data.forEach(function (n) {
      var p = n.parentId != null ? byId.get(n.parentId) : null;
      if (p) p.children.push(n); else { n.parentId = null; roots.push(n); }
    });

    var collapsed = loadCollapsed();
    var view = { x: 0, y: 0, k: 1 };
    var selectedId = null, highlightDept = null, searchHits = [], searchIndex = -1, profileId = null;
    var cards = new Map();           // id -> element
    var colors = assignColors();

    // An empty chart still needs its buttons (e.g. "Add the first position"), so don't stop here.
    var empty = !data.length;

    // ------------------------------------------------------------------ colours
    function deptKey(n) { return (n.department || '').trim().toLowerCase(); }

    function assignColors() {
      var names = {};
      data.forEach(function (n) { var k = deptKey(n); if (k && !names[k]) names[k] = (n.department || '').trim(); });
      var map = {};
      Object.keys(names).sort().forEach(function (k, i) { map[k] = { name: names[k], color: PALETTE[i % PALETTE.length] }; });
      return map;
    }

    function colorOf(n) {
      var k = deptKey(n);
      if (k && colors[k]) return colors[k].color;
      return n.parentId == null ? TOP_COLOR : NONE_COLOR;
    }

    // ------------------------------------------------------------------ layout
    function isLeaf(n) { return !n.children.length || collapsed.has(n.id); }
    function stacked(n) {
      return !collapsed.has(n.id) && n.children.length >= STACK_MIN && n.children.every(function (c) { return isLeaf(c); });
    }

    // Returns subtree width; sets n._w
    function measure(n) {
      if (collapsed.has(n.id) || !n.children.length) { n._w = CARD_W; return n._w; }
      if (stacked(n)) { n._w = 2 * CARD_W + 2 * STACK_SIDE; return n._w; }
      var sum = 0;
      n.children.forEach(function (c, i) { sum += measure(c) + (i ? GAP_X : 0); });
      n._w = Math.max(CARD_W, sum);
      return n._w;
    }

    function place(n, left, top) {
      n.ax = left + (n._w - CARD_W) / 2;
      n.ay = top;
      n._stackChild = false;
      if (collapsed.has(n.id) || !n.children.length) return;
      if (stacked(n)) {
        // Two columns on either side of a line that drops from the middle of the supervisor.
        var cx = left + n._w / 2, y0 = top + CARD_H + GAP_Y * 0.6;
        n.children.forEach(function (c, i) {
          var row = Math.floor(i / 2), rightSide = i % 2 === 1;
          c.ax = rightSide ? cx + STACK_SIDE : cx - STACK_SIDE - CARD_W;
          c.ay = y0 + row * (CARD_H + STACK_GAP);
          c._stackChild = true; c._w = CARD_W;
        });
        return;
      }
      var x = left;
      n.children.forEach(function (c) { place(c, x, top + CARD_H + GAP_Y); x += c._w + GAP_X; });
    }

    function layout() {
      var x = 0;
      roots.forEach(function (r) { measure(r); place(r, x, 0); x += r._w + GAP_X * 2; });
    }

    function visibleNodes() {
      var out = [];
      (function walk(list) {
        list.forEach(function (n) { out.push(n); if (!collapsed.has(n.id)) walk(n.children); });
      })(roots);
      return out;
    }

    function px(n) { return n.ax + n.dx; }
    function py(n) { return n.ay + n.dy; }

    // ------------------------------------------------------------------ rendering
    function initials(name) {
      // Skip titles such as "Engr.", "Arch.", "Atty.", middle initials and suffixes such as "Jr.".
      var parts = (name || '').trim().split(/\s+/).filter(function (w) {
        return w && !/^(engr|arch|atty|dr|mr|mrs|ms|mx|sir|jr|sr|ii|iii|iv)\.?,?$/i.test(w) && !/^[A-Z]\.$/.test(w);
      });
      if (!parts.length) return '';
      return (parts[0][0] + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
    }

    function avatar(el, n) {
      el.replaceChildren();
      el.style.setProperty('--dept', colorOf(n));
      if (n.photo) {
        var img = new Image();
        img.alt = '';
        img.loading = 'lazy';
        img.src = n.photo;
        img.onerror = function () { img.remove(); el.textContent = initials(n.name) || '?'; };
        el.appendChild(img);
      } else if (n.name) {
        el.textContent = initials(n.name);
      } else {
        var i = document.createElement('i'); i.className = 'bi bi-person'; el.appendChild(i);
      }
    }

    function text(tag, cls, value) { var e = document.createElement(tag); e.className = cls; e.textContent = value; return e; }

    function buildCard(n) {
      var card = document.createElement('div');
      card.className = 'oc-card' + (n.published ? '' : ' is-draft') + (n.name ? '' : ' is-vacant');
      card.setAttribute('tabindex', '0');
      card.setAttribute('role', 'button');
      card.setAttribute('aria-label', (n.name || 'Vacant') + ', ' + n.title);
      card.dataset.id = n.id;
      card.style.setProperty('--dept', colorOf(n));

      var av = document.createElement('div'); av.className = 'oc-avatar'; avatar(av, n);
      var body = document.createElement('div'); body.className = 'oc-card-body';
      body.appendChild(text('div', 'oc-name', n.name || 'Vacant'));
      body.appendChild(text('div', 'oc-title', n.title));
      var meta = [n.department, n.site].filter(Boolean).join(' · ');
      if (meta) body.appendChild(text('div', 'oc-meta', meta));
      card.appendChild(av); card.appendChild(body);

      if (!n.published) card.appendChild(text('span', 'oc-draft', 'Draft'));
      if (n.licenseExpired) {
        var warn = document.createElement('span'); warn.className = 'oc-flag'; warn.title = 'Licence expired';
        warn.innerHTML = '<i class="bi bi-exclamation-triangle-fill"></i>'; card.appendChild(warn);
      }
      if (n.children.length) {
        var t = document.createElement('button');
        t.type = 'button'; t.className = 'oc-toggle'; t.dataset.toggle = n.id;
        t.title = collapsed.has(n.id) ? 'Show ' + countAll(n) + ' below' : 'Hide the people below';
        t.innerHTML = collapsed.has(n.id) ? '<i class="bi bi-plus"></i>' + countAll(n) : '<i class="bi bi-dash"></i>';
        card.appendChild(t);
      }
      return card;
    }

    function countAll(n) { var c = 0; n.children.forEach(function (k) { c += 1 + countAll(k); }); return c; }

    function render() {
      layout();
      nodesEl.replaceChildren();
      cards.clear();
      var frag = document.createDocumentFragment();
      visibleNodes().forEach(function (n) {
        var el = buildCard(n);
        cards.set(n.id, el);
        frag.appendChild(el);
      });
      nodesEl.appendChild(frag);
      positionAll();
      applyHighlight();
      renderLegend();
      if (selectedId && cards.get(selectedId)) cards.get(selectedId).classList.add('is-selected');
    }

    function positionAll() {
      cards.forEach(function (el, id) { var n = byId.get(id); el.style.transform = 'translate(' + px(n) + 'px,' + py(n) + 'px)'; });
      drawLines();
    }

    // ------------------------------------------------------------------ connector lines
    var linesFrame = 0;
    function scheduleLines() { if (!linesFrame) linesFrame = requestAnimationFrame(function () { linesFrame = 0; drawLines(); }); }

    function drawLines() {
      var vis = visibleNodes();
      if (!vis.length) { svg.innerHTML = ''; return; }
      var minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
      vis.forEach(function (n) {
        minX = Math.min(minX, px(n)); minY = Math.min(minY, py(n));
        maxX = Math.max(maxX, px(n) + CARD_W); maxY = Math.max(maxY, py(n) + CARD_H);
      });
      var pad = 400;
      var ox = minX - pad, oy = minY - pad;
      svg.setAttribute('width', maxX - minX + pad * 2);
      svg.setAttribute('height', maxY - minY + pad * 2);
      svg.style.transform = 'translate(' + ox + 'px,' + oy + 'px)';

      var out = [];
      vis.forEach(function (n) {
        if (n.parentId == null) return;
        var p = byId.get(n.parentId);
        if (!cards.has(p.id)) return;
        var cls = 'oc-line' + (dragging && dragging.ids.has(n.id) ? ' is-active' : '') + lineDim(p, n);
        out.push('<path class="' + cls + '" d="' + connector(p, n, ox, oy) + '"/>');
      });
      vis.forEach(function (n) {
        (n.also || []).forEach(function (sid) {
          var s = byId.get(sid);
          if (!s || !cards.has(s.id)) return;
          out.push('<path class="oc-line oc-dotted' + lineDim(s, n) + '" d="' + curve(s, n, ox, oy) + '"/>');
        });
      });
      svg.innerHTML = out.join('');
    }

    function lineDim(a, b) {
      if (highlightDept == null && !searchHits.length) return '';
      return cards.get(a.id).classList.contains('is-dim') || cards.get(b.id).classList.contains('is-dim') ? ' is-dim' : '';
    }

    function r(v) { return Math.round(v * 10) / 10; }

    function connector(p, c, ox, oy) {
      var pl = px(p) - ox, pt = py(p) - oy, cl = px(c) - ox, ct = py(c) - oy;
      var pcx = pl + CARD_W / 2, pb = pt + CARD_H, ccx = cl + CARD_W / 2;

      // Two-column list: line drops from the middle of the supervisor, then turns to the box.
      if (c._stackChild && ct > pb + 8) {
        var tx = pcx, cy = ct + CARD_H / 2, cr = cl + CARD_W;
        if (cl >= tx + 6 || cr <= tx - 6) {
          var right = cl >= tx, edge = right ? cl : cr, s = right ? 1 : -1, rr = Math.min(8, Math.abs(edge - tx) / 2);
          return 'M' + r(tx) + ' ' + r(pb) + 'V' + r(cy - rr) + 'Q' + r(tx) + ' ' + r(cy) + ' ' + r(tx + s * rr) + ' ' + r(cy) + 'H' + r(edge);
        }
      }
      // Child clearly below: right-angled line through a shared horizontal bus.
      if (ct >= pb + 20) {
        var midY = pb + Math.min(BUS, (ct - pb) / 2);
        var dx = ccx - pcx, rad = Math.min(8, Math.abs(dx) / 2, (ct - midY) / 2, (midY - pb) / 2);
        if (Math.abs(dx) < 1) return 'M' + r(pcx) + ' ' + r(pb) + 'V' + r(ct);
        var sx = dx > 0 ? 1 : -1;
        return 'M' + r(pcx) + ' ' + r(pb) + 'V' + r(midY - rad) +
          'Q' + r(pcx) + ' ' + r(midY) + ' ' + r(pcx + sx * rad) + ' ' + r(midY) +
          'H' + r(ccx - sx * rad) +
          'Q' + r(ccx) + ' ' + r(midY) + ' ' + r(ccx) + ' ' + r(midY + rad) + 'V' + r(ct);
      }
      // Beside or above the supervisor: smooth curve between the facing edges.
      return curve(p, c, ox, oy);
    }

    function curve(a, b, ox, oy) {
      var al = px(a) - ox, at = py(a) - oy, bl = px(b) - ox, bt = py(b) - oy;
      var acx = al + CARD_W / 2, acy = at + CARD_H / 2, bcx = bl + CARD_W / 2, bcy = bt + CARD_H / 2;
      var dx = bcx - acx, dy = bcy - acy, x1, y1, x2, y2, c1x, c1y, c2x, c2y;
      if (Math.abs(dy) * (CARD_W / CARD_H) > Math.abs(dx)) {          // mostly vertical
        var down = dy > 0;
        x1 = acx; y1 = down ? at + CARD_H : at; x2 = bcx; y2 = down ? bt : bt + CARD_H;
        var k = Math.max(30, Math.abs(y2 - y1) / 2) * (down ? 1 : -1);
        c1x = x1; c1y = y1 + k; c2x = x2; c2y = y2 - k;
      } else {                                                          // mostly horizontal
        var right = dx > 0;
        x1 = right ? al + CARD_W : al; y1 = acy; x2 = right ? bl : bl + CARD_W; y2 = bcy;
        var h = Math.max(30, Math.abs(x2 - x1) / 2) * (right ? 1 : -1);
        c1x = x1 + h; c1y = y1; c2x = x2 - h; c2y = y2;
      }
      return 'M' + r(x1) + ' ' + r(y1) + 'C' + r(c1x) + ' ' + r(c1y) + ' ' + r(c2x) + ' ' + r(c2y) + ' ' + r(x2) + ' ' + r(y2);
    }

    // ------------------------------------------------------------------ pan & zoom
    function applyView() {
      stage.style.transform = 'translate(' + view.x + 'px,' + view.y + 'px) scale(' + view.k + ')';
      zoomLabel.textContent = Math.round(view.k * 100) + '%';
      viewport.style.setProperty('--grid', (24 * view.k) + 'px');
      viewport.style.backgroundPosition = view.x + 'px ' + view.y + 'px';
    }

    function bounds(list) {
      var b = { x1: Infinity, y1: Infinity, x2: -Infinity, y2: -Infinity };
      list.forEach(function (n) {
        b.x1 = Math.min(b.x1, px(n)); b.y1 = Math.min(b.y1, py(n));
        b.x2 = Math.max(b.x2, px(n) + CARD_W); b.y2 = Math.max(b.y2, py(n) + CARD_H);
      });
      return b;
    }

    function fit(maxK) {
      if (!visibleNodes().length) return;
      var b = bounds(visibleNodes()), vw = viewport.clientWidth, vh = viewport.clientHeight, pad = 40;
      var k = Math.min((vw - pad * 2) / (b.x2 - b.x1), (vh - pad * 2) / (b.y2 - b.y1), maxK || 1);
      view.k = clamp(k, MIN_K, MAX_K);
      view.x = (vw - (b.x2 - b.x1) * view.k) / 2 - b.x1 * view.k;
      view.y = Math.max(pad, (vh - (b.y2 - b.y1) * view.k) / 2) - b.y1 * view.k;
      applyView();
    }

    // First view: fit, but keep boxes readable on big charts (start at the top, centred on the top box).
    function initialView() {
      var b = bounds(visibleNodes()), vw = viewport.clientWidth, vh = viewport.clientHeight;
      var fitK = Math.min((vw - 80) / (b.x2 - b.x1), (vh - 80) / (b.y2 - b.y1), 1);
      if (fitK >= 0.6) { fit(1); return; }
      view.k = 0.6;
      var top = roots[0];
      view.x = vw / 2 - (px(top) + CARD_W / 2) * view.k;
      view.y = 32 - b.y1 * view.k;
      applyView();
    }

    function zoomAt(k, cx, cy) {
      k = clamp(k, MIN_K, MAX_K);
      var wx = (cx - view.x) / view.k, wy = (cy - view.y) / view.k;
      view.k = k;
      view.x = cx - wx * k;
      view.y = cy - wy * k;
      applyView();
    }

    function zoomBy(f) { zoomAt(view.k * f, viewport.clientWidth / 2, viewport.clientHeight / 2); }

    function centerOn(n, smooth) {
      var tx = viewport.clientWidth / 2 - (px(n) + CARD_W / 2) * view.k;
      var ty = viewport.clientHeight / 2 - (py(n) + CARD_H / 2) * view.k;
      if (!smooth || window.matchMedia('(prefers-reduced-motion: reduce)').matches) { view.x = tx; view.y = ty; applyView(); return; }
      var sx = view.x, sy = view.y, t0 = performance.now();
      (function step(t) {
        var p = Math.min(1, (t - t0) / 320), e = 1 - Math.pow(1 - p, 3);
        view.x = sx + (tx - sx) * e; view.y = sy + (ty - sy) * e; applyView();
        if (p < 1 && !ac.signal.aborted) requestAnimationFrame(step);
      })(t0);
    }

    function clamp(v, a, b) { return Math.max(a, Math.min(b, v)); }

    // Wheel: pan; with Ctrl/⌘ (and trackpad pinch, which sends ctrlKey) zoom at the pointer.
    on(viewport, 'wheel', function (e) {
      e.preventDefault();
      var rect = viewport.getBoundingClientRect();
      if (e.ctrlKey || e.metaKey) {
        zoomAt(view.k * Math.exp(-e.deltaY * 0.0022), e.clientX - rect.left, e.clientY - rect.top);
      } else {
        var dx = e.shiftKey && !e.deltaX ? e.deltaY : e.deltaX, dy = e.shiftKey && !e.deltaX ? 0 : e.deltaY;
        view.x -= dx; view.y -= dy; applyView();
      }
    }, { passive: false });

    // ------------------------------------------------------------------ pointer: pan, drag, pinch
    var pointers = new Map();
    var panning = null, dragging = null, pinch = null, suppressClick = false;

    on(viewport, 'pointerdown', function (e) {
      if (e.button !== 0 && e.pointerType === 'mouse') return;
      if (e.target.closest('.oc-toggle, button, a, input, .oc-hint')) return;
      pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });

      if (pointers.size === 2) {                       // pinch zoom (touch)
        if (dragging) endDrag(false);
        panning = null;
        var pts = Array.from(pointers.values());
        pinch = { d: dist(pts[0], pts[1]), k: view.k };
        return;
      }
      var card = e.target.closest('.oc-card');
      if (card) {
        var n = byId.get(+card.dataset.id);
        dragging = { id: n.id, startX: e.clientX, startY: e.clientY, moved: false, ids: null, origin: null, pointerId: e.pointerId };
      } else {
        panning = { x: e.clientX, y: e.clientY, vx: view.x, vy: view.y, pointerId: e.pointerId };
        viewport.classList.add('is-panning');
      }
      viewport.setPointerCapture(e.pointerId);
    });

    on(viewport, 'pointermove', function (e) {
      if (!pointers.has(e.pointerId)) return;
      pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });

      if (pinch && pointers.size >= 2) {
        var pts = Array.from(pointers.values()), rect = viewport.getBoundingClientRect();
        zoomAt(pinch.k * dist(pts[0], pts[1]) / pinch.d, (pts[0].x + pts[1].x) / 2 - rect.left, (pts[0].y + pts[1].y) / 2 - rect.top);
        return;
      }
      if (panning && e.pointerId === panning.pointerId) {
        view.x = panning.vx + e.clientX - panning.x;
        view.y = panning.vy + e.clientY - panning.y;
        applyView();
        return;
      }
      if (dragging && e.pointerId === dragging.pointerId) {
        var dx = e.clientX - dragging.startX, dy = e.clientY - dragging.startY;
        if (!dragging.moved) {
          if (Math.hypot(dx, dy) < 5) return;
          beginDrag();
        }
        var wdx = dx / view.k, wdy = dy / view.k;
        dragging.ids.forEach(function (id) {
          var n = byId.get(id), o = dragging.origin.get(id);
          n.dx = o.dx + wdx; n.dy = o.dy + wdy;
          var el = cards.get(id);
          if (el) el.style.transform = 'translate(' + px(n) + 'px,' + py(n) + 'px)';
        });
        scheduleLines();
      }
    });

    function beginDrag() {
      dragging.moved = true;
      var n = byId.get(dragging.id);
      var withBranch = !$('ocMoveBranch') || $('ocMoveBranch').checked;
      dragging.ids = new Set([n.id]);
      if (withBranch) (function add(x) { if (collapsed.has(x.id) && x !== n) return; x.children.forEach(function (c) { dragging.ids.add(c.id); add(c); }); })(n);
      // Hidden (collapsed) descendants move along too so the branch stays together when expanded.
      if (withBranch && collapsed.has(n.id)) (function addAll(x) { x.children.forEach(function (c) { dragging.ids.add(c.id); addAll(c); }); })(n);
      dragging.origin = new Map();
      dragging.ids.forEach(function (id) { var m = byId.get(id); dragging.origin.set(id, { dx: m.dx, dy: m.dy }); });
      dragging.ids.forEach(function (id) { var el = cards.get(id); if (el) el.classList.add('is-dragging'); });
      viewport.classList.add('is-dragging');
      select(n.id);
    }

    function endDrag(commit) {
      if (!dragging) return;
      var d = dragging;
      dragging = null;
      viewport.classList.remove('is-dragging');
      cards.forEach(function (el) { el.classList.remove('is-dragging'); });
      drawLines();
      if (d.moved && commit) persist(Array.from(d.ids));
    }

    function release(e) {
      pointers.delete(e.pointerId);
      if (pinch) { if (pointers.size < 2) pinch = null; return; }
      if (panning && e.pointerId === panning.pointerId) {
        panning = null; viewport.classList.remove('is-panning');
        return;
      }
      if (dragging && e.pointerId === dragging.pointerId) {
        var wasClick = !dragging.moved, id = dragging.id;
        endDrag(e.type === 'pointerup');
        if (wasClick && e.type === 'pointerup') { select(id); openProfile(id); }
        suppressClick = true;
      }
    }
    on(viewport, 'pointerup', release);
    on(viewport, 'pointercancel', release);

    function dist(a, b) { return Math.hypot(a.x - b.x, a.y - b.y) || 1; }

    // ------------------------------------------------------------------ saving the arrangement
    var pending = new Set(), saveTimer = null;

    function persist(ids) {
      if (!admin) { status('Arrangement not saved (view only)', 'muted'); return; }
      ids.forEach(function (id) { pending.add(id); });
      status('Saving…', 'muted');
      clearTimeout(saveTimer);
      saveTimer = setTimeout(flush, 500);
    }

    function flush() {
      if (!pending.size) return;
      var body = Array.from(pending).map(function (id) { var n = byId.get(id); return { id: id, dx: n.dx, dy: n.dy }; });
      pending.clear();
      post('/organization-chart/layout', body).then(function () {
        status('Layout saved', 'ok');
      }).catch(function (err) {
        body.forEach(function (b) { pending.add(b.id); });
        status(err.message || 'Couldn’t save the layout', 'err');
      });
    }

    function post(url, body) {
      var headers = { 'Content-Type': 'application/json', 'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest' };
      var csrf = window.CBM && window.CBM.csrf && window.CBM.csrf();
      if (csrf) headers[csrf.header] = csrf.token;
      return fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body || {}), credentials: 'same-origin' })
        .then(function (res) {
          if (res.redirected || !res.ok) throw new Error(res.status === 403 ? 'Not allowed to save the layout' : 'Couldn’t save the layout (' + (res.redirected ? 'session expired' : res.status) + ')');
          return res.json();
        });
    }

    var statusTimer = null;
    function status(msg, tone) {
      if (!saveState) return;
      saveState.textContent = msg;
      saveState.className = 'oc-save-state is-' + tone;
      clearTimeout(statusTimer);
      if (tone === 'ok' || tone === 'muted') statusTimer = setTimeout(function () { saveState.textContent = ''; }, tone === 'ok' ? 2500 : 4000);
      if (tone === 'ok') saveState.innerHTML = '<i class="bi bi-check2"></i>' + msg;
    }

    // Save anything still pending before leaving (sidebar navigation or closing the tab).
    on(window, 'beforeunload', function () {
      if (!pending.size || !admin) return;
      var body = Array.from(pending).map(function (id) { var n = byId.get(id); return { id: id, dx: n.dx, dy: n.dy }; });
      var csrf = window.CBM && window.CBM.csrf && window.CBM.csrf();
      try {
        fetch('/organization-chart/layout', { method: 'POST', keepalive: true, credentials: 'same-origin',
          headers: Object.assign({ 'Content-Type': 'application/json' }, csrf ? (function () { var h = {}; h[csrf.header] = csrf.token; return h; })() : {}),
          body: JSON.stringify(body) });
      } catch (ignore) {}
    });

    // ------------------------------------------------------------------ selection & keyboard
    function select(id) {
      if (selectedId && cards.get(selectedId)) cards.get(selectedId).classList.remove('is-selected');
      selectedId = id;
      var el = cards.get(id);
      if (el) { el.classList.add('is-selected'); el.focus({ preventScroll: true }); }
    }

    on(document, 'keydown', function (e) {
      if (document.querySelector('dialog[open]')) return;
      var inChart = viewport.contains(document.activeElement) || document.activeElement === viewport;
      if (!inChart) return;
      var card = document.activeElement.closest && document.activeElement.closest('.oc-card');
      var arrows = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] };

      if (card && (e.key === 'Enter' || e.key === ' ')) { e.preventDefault(); openProfile(+card.dataset.id); return; }
      if (card && e.altKey && arrows[e.key]) {
        e.preventDefault();
        var n = byId.get(+card.dataset.id), step = e.shiftKey ? 2 : 12, d = arrows[e.key];
        var ids = [n.id];
        if (!$('ocMoveBranch') || $('ocMoveBranch').checked) (function add(x) { x.children.forEach(function (c) { ids.push(c.id); add(c); }); })(n);
        ids.forEach(function (id) { var m = byId.get(id); m.dx += d[0] * step; m.dy += d[1] * step; });
        positionAll();
        persist(ids);
        return;
      }
      if (!e.altKey && arrows[e.key] && !card) {        // pan with arrow keys when the canvas is focused
        e.preventDefault();
        view.x -= arrows[e.key][0] * 60; view.y -= arrows[e.key][1] * 60; applyView();
      }
      if ((e.key === '+' || e.key === '=') && !e.ctrlKey) { e.preventDefault(); zoomBy(1.2); }
      if (e.key === '-' && !e.ctrlKey) { e.preventDefault(); zoomBy(1 / 1.2); }
    });

    // ------------------------------------------------------------------ collapse branches
    function loadCollapsed() {
      try { return new Set(JSON.parse(localStorage.getItem('cbm.orgCollapsed') || '[]').filter(function (id) { return byId.has(id); })); }
      catch (e) { return new Set(); }
    }
    function saveCollapsed() { try { localStorage.setItem('cbm.orgCollapsed', JSON.stringify(Array.from(collapsed))); } catch (e) {} }

    on(nodesEl, 'click', function (e) {
      var t = e.target.closest('.oc-toggle');
      if (!t) return;
      e.stopPropagation();
      var id = +t.dataset.toggle, n = byId.get(id);
      var before = { x: px(n), y: py(n) };
      if (collapsed.has(id)) collapsed.delete(id); else collapsed.add(id);
      saveCollapsed();
      render();
      // Keep the toggled box where it was on screen.
      view.x -= (px(n) - before.x) * view.k; view.y -= (py(n) - before.y) * view.k; applyView();
    });

    // Expand every collapsed ancestor of a node (used by search / "show on chart").
    function reveal(n) {
      var changed = false, p = n.parentId != null ? byId.get(n.parentId) : null;
      while (p) { if (collapsed.delete(p.id)) changed = true; p = p.parentId != null ? byId.get(p.parentId) : null; }
      if (changed) { saveCollapsed(); render(); }
    }

    // ------------------------------------------------------------------ legend & highlight
    function renderLegend() {
      var legend = $('ocLegend');
      var counts = {}, none = 0, top = 0;
      data.forEach(function (n) {
        var k = deptKey(n);
        if (k) counts[k] = (counts[k] || 0) + 1; else if (n.parentId == null) top++; else none++;
      });
      var items = Object.keys(counts).sort().map(function (k) { return { key: k, name: colors[k].name, color: colors[k].color, count: counts[k] }; });
      if (none) items.push({ key: '__none', name: 'No department', color: NONE_COLOR, count: none });
      legend.replaceChildren();
      if (!items.length) { legend.hidden = true; return; }
      legend.hidden = false;
      items.forEach(function (it) {
        var b = document.createElement('button');
        b.type = 'button';
        b.className = 'oc-chip' + (highlightDept === it.key ? ' is-on' : '') + (highlightDept && highlightDept !== it.key ? ' is-off' : '');
        b.dataset.dept = it.key;
        b.style.setProperty('--dept', it.color);
        b.setAttribute('aria-pressed', highlightDept === it.key ? 'true' : 'false');
        b.innerHTML = '<span class="dot"></span>';
        b.appendChild(document.createTextNode(it.name));
        var c = document.createElement('span'); c.className = 'count'; c.textContent = it.count; b.appendChild(c);
        legend.appendChild(b);
      });
      if (highlightDept) {
        var all = document.createElement('button');
        all.type = 'button'; all.className = 'oc-chip oc-chip-reset'; all.dataset.dept = '';
        all.innerHTML = '<i class="bi bi-x"></i>Show all';
        legend.appendChild(all);
      }
    }

    on($('ocLegend'), 'click', function (e) {
      var b = e.target.closest('.oc-chip');
      if (!b) return;
      var k = b.dataset.dept;
      highlightDept = !k || highlightDept === k ? null : k;
      clearSearch(true);
      renderLegend();
      applyHighlight();
    });

    function applyHighlight() {
      var hits = new Set(searchHits);
      cards.forEach(function (el, id) {
        var n = byId.get(id), dim = false;
        if (searchHits.length) dim = !hits.has(id);
        else if (highlightDept) dim = (highlightDept === '__none' ? !(deptKey(n) === '' && n.parentId != null) : deptKey(n) !== highlightDept);
        el.classList.toggle('is-dim', dim);
        el.classList.toggle('is-match', searchHits.length > 0 && hits.has(id));
      });
      drawLines();
    }

    // ------------------------------------------------------------------ search
    var searchInput = $('ocSearch'), searchCount = $('ocSearchCount');
    function runSearch() {
      var q = searchInput.value.trim().toLowerCase();
      if (!q) { clearSearch(); return; }
      highlightDept = null; renderLegend();
      var found = data.filter(function (n) {
        return [n.name, n.title, n.department, n.site, n.profession].some(function (v) { return v && v.toLowerCase().indexOf(q) !== -1; });
      });
      found.forEach(reveal);
      searchHits = found.map(function (n) { return n.id; });
      searchIndex = -1;
      searchCount.textContent = found.length ? found.length + (found.length === 1 ? ' match' : ' matches') + (found.length > 1 ? ' · Enter for next' : '') : 'No matches';
      applyHighlight();
      if (found.length) nextHit();
    }
    function nextHit() {
      if (!searchHits.length) return;
      searchIndex = (searchIndex + 1) % searchHits.length;
      var n = byId.get(searchHits[searchIndex]);
      centerOn(n, true);
      cards.forEach(function (el) { el.classList.remove('is-current'); });
      var el = cards.get(n.id); if (el) el.classList.add('is-current');
    }
    function clearSearch(keepInput) {
      if (!keepInput && searchInput) searchInput.value = '';
      if (keepInput && searchInput) searchInput.value = '';
      searchHits = []; searchIndex = -1;
      if (searchCount) searchCount.textContent = '';
      cards.forEach(function (el) { el.classList.remove('is-current'); });
      applyHighlight();
    }
    var searchTimer = null;
    on(searchInput, 'input', function () { clearTimeout(searchTimer); searchTimer = setTimeout(runSearch, 180); });
    on(searchInput, 'keydown', function (e) {
      if (e.key === 'Enter') { e.preventDefault(); if (searchHits.length) nextHit(); else runSearch(); }
      if (e.key === 'Escape') clearSearch();
    });

    // ------------------------------------------------------------------ profile dialog
    function openProfile(id) {
      var n = byId.get(id), dialog = $('ocProfileDialog');
      if (!n || !dialog) return;
      profileId = id;
      avatar($('ocProfileAvatar'), n);
      $('ocProfileName').textContent = n.name || 'Vacant position';
      $('ocProfileTitle').textContent = n.title + (n.published ? '' : ' · Draft');
      var dept = $('ocProfileDept');
      dept.replaceChildren();
      if (n.department) { var chip = text('span', 'oc-dept-chip', n.department); chip.style.setProperty('--dept', colorOf(n)); dept.appendChild(chip); }

      var dl = $('ocProfileDetails');
      dl.replaceChildren();
      function row(label, value, href, cls) {
        if (!value) return;
        dl.appendChild(text('dt', '', label));
        var dd = document.createElement('dd');
        if (href) { var a = document.createElement('a'); a.href = href; a.textContent = value; dd.appendChild(a); }
        else dd.textContent = value;
        if (cls) dd.className = cls;
        dl.appendChild(dd);
      }
      row('Profession / trade', n.profession);
      row('Licence no.', n.license ? n.license + (n.licenseExpiryLabel ? (n.licenseExpired ? ' — expired ' : ' — valid until ') + n.licenseExpiryLabel : '') : null, null, n.licenseExpired ? 'text-danger fw-semibold' : '');
      row('Office / site', n.site);
      row('Email', n.email, n.email ? 'mailto:' + n.email : null);
      row('Phone', n.phone, n.phone ? 'tel:' + n.phone.replace(/[^\d+]/g, '') : null);
      row('Date hired', n.dateHiredLabel);
      if (admin) { row('Employee ID', n.employeeId); row('Login account', n.accountLabel); }
      if (!dl.children.length) dl.appendChild(text('dd', 'text-muted', 'No details recorded yet.'));

      var people = $('ocProfilePeople');
      people.replaceChildren();
      function group(label, list) {
        if (!list.length) return;
        people.appendChild(text('div', 'summary-label mt-3', label));
        var wrap = document.createElement('div'); wrap.className = 'oc-people-list';
        list.forEach(function (p) {
          var b = document.createElement('button');
          b.type = 'button'; b.className = 'oc-person'; b.dataset.person = p.id;
          b.style.setProperty('--dept', colorOf(p));
          var av = document.createElement('span'); av.className = 'oc-avatar oc-avatar-sm'; avatar(av, p);
          b.appendChild(av);
          var t = document.createElement('span');
          t.appendChild(text('strong', '', p.name || 'Vacant'));
          t.appendChild(text('small', '', p.title));
          b.appendChild(t);
          wrap.appendChild(b);
        });
        people.appendChild(wrap);
      }
      group('Reports to', n.parentId != null ? [byId.get(n.parentId)] : []);
      group('Also reports to', (n.also || []).map(function (i) { return byId.get(i); }).filter(Boolean));
      group('Direct reports (' + n.children.length + ')', n.children);
      var dotted = data.filter(function (m) { return (m.also || []).indexOf(n.id) !== -1; });
      group('Dotted-line reports', dotted);

      renderAccount(n);
      if (!dialog.open) dialog.showModal();
    }

    // Login account box in the profile (admins only).
    function renderAccount(n) {
      var box = $('ocProfileAccount');
      if (!box) return;
      box.replaceChildren();
      if (!n.name) { box.hidden = true; return; }
      box.hidden = false;
      var row = document.createElement('div'); row.className = 'oc-account-row';
      var state = document.createElement('div'); state.className = 'oc-account-state';
      var actions = document.createElement('div'); actions.className = 'oc-account-actions';
      var icon = document.createElement('i'); icon.className = 'bi';
      var textBox = document.createElement('div');
      var title = document.createElement('strong'), small = document.createElement('small');
      textBox.appendChild(title); textBox.appendChild(small);
      state.appendChild(icon); state.appendChild(textBox);

      function button(label, iconCls, cls, onClick) {
        var b = document.createElement('button');
        b.type = 'button'; b.className = 'btn btn-sm ' + cls;
        b.innerHTML = '<i class="bi ' + iconCls + '"></i>';
        b.appendChild(document.createTextNode(label));
        b.addEventListener('click', onClick);
        actions.appendChild(b);
      }
      function link(label, iconCls, href) {
        var a = document.createElement('a');
        a.className = 'btn btn-sm btn-outline'; a.href = href;
        a.innerHTML = '<i class="bi ' + iconCls + '"></i>';
        a.appendChild(document.createTextNode(label));
        actions.appendChild(a);
      }

      var st = n.accountState || 'none';
      if (st === 'none') {
        icon.classList.add('bi-person-x', 'text-muted');
        title.textContent = 'No login account';
        if (n.email) {
          small.textContent = 'Create one and an activation link is emailed to ' + n.email + '.';
          button('Create login account', 'bi-person-plus', 'btn-dark', function () { invite(n, false); });
        } else {
          small.textContent = 'Add their email address (Edit) to create an account.';
        }
      } else if (st === 'invited') {
        icon.classList.add('bi-hourglass-split', 'text-warning');
        title.textContent = 'Invited — waiting for activation';
        small.textContent = 'Username @' + n.accountUsername + '. They haven\u2019t set a password yet.';
        button('Resend link', 'bi-envelope', 'btn-outline', function () { invite(n, true); });
      } else if (st === 'active') {
        icon.classList.add('bi-person-check-fill', 'text-success');
        title.textContent = '@' + n.accountUsername;
        if (n.accountAdmin) { var b = document.createElement('span'); b.className = 'oc-badge-admin'; b.textContent = 'Admin'; title.appendChild(b); }
        small.textContent = 'Access: ' + (n.accountAccess || '—');
        link('Manage access', 'bi-shield-lock', '/users?edit=' + n.accountUserId);
      } else {
        icon.classList.add('bi-person-slash', 'text-danger');
        title.textContent = '@' + n.accountUsername + ' (disabled)';
        small.textContent = 'This account can\u2019t sign in.';
        link('Open in Users', 'bi-people', '/users?edit=' + n.accountUserId);
      }
      row.appendChild(state); row.appendChild(actions);
      box.appendChild(row);
    }

    function invite(n, resend) {
      var form = $('ocInviteForm');
      if (!form) return;
      $('ocProfileDialog').close();
      window.CBM.confirm({
        title: resend ? 'Resend activation link' : 'Create login account',
        message: resend
          ? 'Send a new activation link to ' + n.email + '? The previous link will stop working.'
          : 'Create a login account for ' + n.name + ' and email the activation link to ' + n.email + '? Their access will follow the ' + (n.department || 'department') + ' settings.',
        ok: resend ? 'Send link' : 'Create account',
        tone: 'dark'
      }).then(function (yes) {
        if (!yes) return;
        form.action = form.action.replace(/\/organization-chart\/\d+\/invite$/, '/organization-chart/' + n.id + '/invite');
        if (form.requestSubmit) form.requestSubmit(); else form.submit();
      });
    }

    on($('ocProfileDialog'), 'click', function (e) {
      var p = e.target.closest('.oc-person');
      if (p) openProfile(+p.dataset.person);
    });

    // ------------------------------------------------------------------ edit dialog (admins)
    var form = $('ocEditForm');
    var depts = [], sites = [], professions = [];
    data.forEach(function (n) {
      if (n.department && depts.indexOf(n.department) === -1) depts.push(n.department);
      if (n.site && sites.indexOf(n.site) === -1) sites.push(n.site);
      if (n.profession && professions.indexOf(n.profession) === -1) professions.push(n.profession);
    });

    function fillList(id, values) {
      var dl = $(id); if (!dl) return;
      dl.replaceChildren();
      values.sort().forEach(function (v) { var o = document.createElement('option'); o.value = v; dl.appendChild(o); });
    }

    function descendants(n, set) { n.children.forEach(function (c) { set.add(c.id); descendants(c, set); }); return set; }

    function label(n) { return (n.name ? n.name + ' — ' : '') + n.title; }

    function openEditor(n, parentId) {
      if (!form) return;
      var editing = !!n;
      $('ocEditTitle').textContent = editing ? 'Edit position' : 'Add position';
      form.reset();
      $('ocId').value = editing ? n.id : '';
      var blocked = editing ? descendants(n, new Set([n.id])) : new Set();

      var parent = $('ocParent');
      parent.replaceChildren(new Option('— Top of the chart (no supervisor) —', ''));
      data.slice().sort(function (a, b) { return label(a).localeCompare(label(b)); }).forEach(function (m) {
        if (!blocked.has(m.id)) parent.appendChild(new Option(label(m), m.id));
      });
      parent.value = editing ? (n.parentId == null ? '' : n.parentId) : (parentId == null ? '' : parentId);

      var also = $('ocAlsoList');
      also.replaceChildren();
      var selectedAlso = editing ? (n.also || []) : [];
      data.slice().sort(function (a, b) { return label(a).localeCompare(label(b)); }).forEach(function (m) {
        if (blocked.has(m.id)) return;
        var l = document.createElement('label'); l.className = 'oc-check';
        var cb = document.createElement('input'); cb.type = 'checkbox'; cb.name = 'alsoReportsTo'; cb.value = m.id;
        cb.checked = selectedAlso.indexOf(m.id) !== -1;
        l.appendChild(cb); l.appendChild(document.createTextNode(label(m)));
        also.appendChild(l);
      });
      updateAlsoCount();
      also.closest('details').open = selectedAlso.length > 0;

      var acc = $('ocAccount');
      acc.replaceChildren(new Option('— Not linked —', ''));
      accounts.forEach(function (a) { acc.appendChild(new Option(a.label, a.id)); });

      fillList('ocDepartments', depts); fillList('ocSites', sites); fillList('ocProfessions', professions);

      var set = function (id, v) { $(id).value = v == null ? '' : v; };
      if (editing) {
        set('ocTitle', n.title); set('ocDepartment', n.department); set('ocSite', n.site); set('ocName', n.name);
        set('ocEmployeeId', n.employeeId); set('ocEmail', n.email); set('ocPhone', n.phone); set('ocHired', n.dateHired);
        set('ocAccount', n.accountUserId); set('ocProfession', n.profession); set('ocLicense', n.license);
        set('ocLicenseExpiry', n.licenseExpiry); set('ocSort', n.sortOrder || 0);
        $('ocPublished').checked = !!n.published;
      } else {
        var p = parentId != null ? byId.get(parentId) : null;
        if (p) { set('ocDepartment', p.department); set('ocSite', p.site); }
        $('ocPublished').checked = true;
        set('ocSort', 0);
      }
      $('ocResigned').checked = false;
      $('ocResignedDate').hidden = true;
      $('ocRemovePhoto').checked = false;
      $('ocRemovePhotoWrap').hidden = !(editing && n.ownPhoto);
      previewPhoto(editing ? n : { name: '' }, null);
      $('ocDeleteButton').hidden = !editing;

      var profile = $('ocProfileDialog');
      if (profile && profile.open) profile.close();
      $('ocEditDialog').showModal();
      setTimeout(function () { $('ocTitle').focus(); }, 40);
    }

    function updateAlsoCount() {
      var c = form ? form.querySelectorAll('input[name=alsoReportsTo]:checked').length : 0;
      if ($('ocAlsoCount')) $('ocAlsoCount').textContent = c ? '(' + c + ' selected)' : '';
    }

    function previewPhoto(n, file) {
      var el = $('ocPhotoPreview');
      if (file) {
        var reader = new FileReader();
        reader.onload = function (ev) { el.replaceChildren(); var img = new Image(); img.alt = ''; img.src = ev.target.result; el.appendChild(img); };
        reader.readAsDataURL(file);
      } else {
        avatar(el, n);
      }
    }

    if (form) {
      on(form, 'change', function (e) {
        if (e.target.name === 'alsoReportsTo') updateAlsoCount();
        if (e.target.id === 'ocResigned') {
          $('ocResignedDate').hidden = !e.target.checked;
          if (e.target.checked && !$('ocResignedDate').value) $('ocResignedDate').value = new Date().toISOString().slice(0, 10);
        }
        if (e.target.id === 'ocPhoto') {
          var f = e.target.files && e.target.files[0];
          if (f && f.size > 3 * 1024 * 1024) { alertBox('The photo is larger than 3 MB.'); e.target.value = ''; return; }
          if (f) { $('ocRemovePhoto').checked = false; previewPhoto(null, f); }
        }
        if (e.target.id === 'ocRemovePhoto' && e.target.checked) {
          $('ocPhoto').value = '';
          previewPhoto({ name: $('ocName').value }, null);
        }
      });
      on(form, 'submit', function (e) {
        if ($('ocResigned').checked && !window.confirm('Mark this person as resigned? They will be removed from the chart (you can restore them later).')) {
          e.preventDefault();
        }
      });
    }

    function alertBox(msg) { if (window.CBM && window.CBM.confirm) window.CBM.confirm({ title: 'Photo', message: msg, ok: 'OK' }); else window.alert(msg); }

    // ------------------------------------------------------------------ toolbar & buttons
    on(root.closest('main') || document, 'click', function (e) {
      var b = e.target.closest('[data-oc-action]');
      if (!b) return;
      var action = b.getAttribute('data-oc-action');
      switch (action) {
        case 'zoom-in': zoomBy(1.2); break;
        case 'zoom-out': zoomBy(1 / 1.2); break;
        case 'zoom-reset': zoomAt(1, viewport.clientWidth / 2, viewport.clientHeight / 2); break;
        case 'fit': fit(1); break;
        case 'print': printChart(); break;
        case 'reset-layout': resetLayout(); break;
        case 'add': openEditor(null, null); break;
        case 'add-under': openEditor(null, profileId); break;
        case 'edit': openEditor(byId.get(profileId)); break;
        case 'locate': {
          var n = byId.get(profileId);
          $('ocProfileDialog').close();
          reveal(n); select(n.id); centerOn(n, true);
          break;
        }
        case 'delete': {
          var current = byId.get(+$('ocId').value);
          $('ocEditDialog').close();
          if (current) deletePosition(current.id, (current.name ? current.name + ' (' + current.title + ')' : current.title));
          break;
        }
      }
    });

    function deletePosition(id, label) {
      var del = $('ocDeleteForm');
      if (!del) return;
      del.action = del.action.replace(/\/delete\/\d+$/, '/delete/' + id);
      del.setAttribute('data-confirm', 'Delete ' + label + ' from the chart? This cannot be undone. ' +
        'If the person has only left the company, mark them as Resigned in Edit instead so their record is kept.');
      delete del.dataset.confirmed;
      if (del.requestSubmit) del.requestSubmit(); else del.submit();
    }

    function resetLayout() {
      if (empty) return;
      var go = function () {
        data.forEach(function (n) { n.dx = 0; n.dy = 0; });
        render(); fit(1);
        if (!admin) { status('Back to the automatic layout', 'muted'); return; }
        status('Resetting…', 'muted');
        pending.clear();
        post('/organization-chart/layout/reset').then(function () { status('Layout reset for everyone', 'ok'); })
          .catch(function (err) { status(err.message, 'err'); });
      };
      if (!admin) { go(); return; }
      window.CBM.confirm({ title: 'Reset layout', message: 'Put every box back in its automatic position? This changes the chart for everyone.', ok: 'Reset layout', tone: 'danger' })
        .then(function (yes) { if (yes) go(); });
    }

    function printChart() {
      var saved = { x: view.x, y: view.y, k: view.k };
      root.classList.add('is-printing');
      fit(1);
      var restore = function () {
        root.classList.remove('is-printing');
        view.x = saved.x; view.y = saved.y; view.k = saved.k; applyView();
        window.removeEventListener('afterprint', restore);
      };
      window.addEventListener('afterprint', restore);
      setTimeout(function () { window.print(); }, 60);
    }

    on(window, 'resize', function () { scheduleLines(); });

    // ------------------------------------------------------------------ start
    if (empty) { $('ocLegend').hidden = true; applyView(); }
    else { render(); initialView(); }

    // Open a specific profile from #person-ID in the URL (shareable link).
    var m = /#person-(\d+)/.exec(window.location.hash);
    if (m && byId.has(+m[1])) { var target = byId.get(+m[1]); reveal(target); centerOn(target); select(target.id); }

    return {
      destroy: function () {
        if (pending.size) flush();
        ac.abort();
        if (linesFrame) cancelAnimationFrame(linesFrame);
      }
    };
  }

  function parseJson(id, fallback) {
    var el = document.getElementById(id);
    if (!el) return fallback;
    try { return JSON.parse(el.textContent || 'null') || fallback; } catch (e) { return fallback; }
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
  document.addEventListener('app:navigated', init);
})();
