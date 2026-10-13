/* 足迹涂色 — 自绘风格化矢量底图 + 战争迷雾揭图引擎 (全部离线 canvas, 无远程资源) */
(function () {
  "use strict";

  var D = window.WP_DATA;

  /* ---------- oklch → sRGB (单一色彩来源: CSS 变量) ---------- */
  function parseOklch(str) {
    var m = String(str).match(/oklch\(\s*([\d.]+%?)\s+([\d.]+%?)\s+([\d.]+)\s*(?:\/\s*([\d.]+%?))?\s*\)/i);
    if (!m) return null;
    function num(s, scale) { if (s == null) return scale; if (s.indexOf("%") >= 0) return parseFloat(s) / 100 * scale; return parseFloat(s); }
    return { L: num(m[1], 1), C: num(m[2], 0.4), h: parseFloat(m[3]), a: m[4] == null ? 1 : num(m[4], 1) };
  }
  function oklchToRgba(L, C, h, a) {
    var hr = h * Math.PI / 180, ca = C * Math.cos(hr), cb = C * Math.sin(hr);
    var l_ = L + 0.3963377774 * ca + 0.2158037573 * cb;
    var m_ = L - 0.1055613458 * ca - 0.0638541728 * cb;
    var s_ = L - 0.0894841775 * ca - 1.2914855480 * cb;
    var l = l_ * l_ * l_, m = m_ * m_ * m_, s = s_ * s_ * s_;
    var r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s;
    var g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s;
    var b = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s;
    function f(x) { x = x <= 0.0031308 ? 12.92 * x : 1.055 * Math.pow(x, 1 / 2.4) - 0.055; return Math.max(0, Math.min(255, Math.round(x * 255))); }
    return { r: f(r), g: f(g), b: f(b), a: Math.max(0, Math.min(1, a)) };
  }
  function color(str) {
    var o = parseOklch(str);
    if (!o) return { css: str, rgba: null };
    var c = oklchToRgba(o.L, o.C, o.h, o.a);
    return { css: "rgba(" + c.r + "," + c.g + "," + c.b + "," + c.a + ")", rgba: c };
  }

  var VARS = ["--map-land", "--map-land-2", "--map-park", "--map-green", "--map-water", "--map-water-2",
    "--map-road", "--map-road-casing", "--map-road-2", "--map-road-2-casing", "--map-contour",
    "--map-block", "--map-block-line", "--map-label", "--map-label-strong", "--map-label-halo", "--fog",
    "--track", "--track-case", "--loc", "--loc-halo"];

  function readPalette() {
    var cs = getComputedStyle(document.documentElement);
    var p = {};
    for (var i = 0; i < VARS.length; i++) {
      var raw = cs.getPropertyValue(VARS[i]).trim() || "#888";
      p[VARS[i]] = color(raw);
    }
    return p;
  }

  /* ---------- geometry helpers ---------- */
  function segDist(px, py, ax, ay, bx, by) {
    var dx = bx - ax, dy = by - ay, l2 = dx * dx + dy * dy;
    var t = l2 ? ((px - ax) * dx + (py - ay) * dy) / l2 : 0;
    t = Math.max(0, Math.min(1, t));
    var x = ax + t * dx - px, y = ay + t * dy - py;
    return Math.hypot(x, y);
  }
  function distToPolyline(px, py, pts) {
    var d = Infinity;
    for (var i = 0; i < pts.length - 1; i++) d = Math.min(d, segDist(px, py, pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1]));
    return d;
  }
  function pointInPoly(px, py, poly) {
    var inside = false;
    for (var i = 0, j = poly.length - 1; i < poly.length; j = i++) {
      var xi = poly[i][0], yi = poly[i][1], xj = poly[j][0], yj = poly[j][1];
      if (((yi > py) !== (yj > py)) && (px < (xj - xi) * (py - yi) / (yj - yi) + xi)) inside = !inside;
    }
    return inside;
  }
  function offsetPolyline(pts, dist) {
    var out = [];
    for (var i = 0; i < pts.length; i++) {
      var a = pts[Math.max(0, i - 1)], b = pts[Math.min(pts.length - 1, i + 1)];
      var dx = b[0] - a[0], dy = b[1] - a[1], l = Math.hypot(dx, dy) || 1;
      out.push([pts[i][0] - dy / l * dist, pts[i][1] + dx / l * dist]);
    }
    return out;
  }
  function polyBounds(list) {
    var minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
    list.forEach(function (p) { minX = Math.min(minX, p[0]); minY = Math.min(minY, p[1]); maxX = Math.max(maxX, p[0]); maxY = Math.max(maxY, p[1]); });
    return { minX: minX, minY: minY, maxX: maxX, maxY: maxY };
  }

  /* ---------- block / parcel generation (deterministic, original) ---------- */
  var rng = (function (seed) { return function () { seed = (seed * 1664525 + 1013904223) % 4294967296; return seed / 4294967296; }; })(20261013);

  var BLOCKS = null;
  function buildBlocks() {
    if (BLOCKS) return BLOCKS;
    var blocks = [];
    var parkBB = polyBounds(D.park);
    for (var i = 0; i < D.XL.length - 1; i++) {
      for (var j = 0; j < D.YL.length - 1; j++) {
        var x0 = D.XL[i] + 13, x1 = D.XL[i + 1] - 13;
        var y0 = D.YL[j] + 13, y1 = D.YL[j + 1] - 13;
        var cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
        if (cx > parkBB.minX - 30 && cx < parkBB.maxX + 30 && cy > parkBB.minY - 30 && cy < parkBB.maxY + 30) continue;
        if (pointInPoly(cx, cy, D.green2)) continue;
        if (distToPolyline(cx, cy, D.river) < D.riverHW + 46) continue;
        if (Math.hypot(cx - D.lake.cx, cy - D.lake.cy) < 210) continue;
        // subdivide a few cells for texture
        var r = rng();
        if (r < 0.32) {
          var mid = x0 + (x1 - x0) * (0.38 + rng() * 0.24);
          blocks.push({ x: x0, y: y0, w: mid - x0, h: y1 - y0 });
          blocks.push({ x: mid + 8, y: y0, w: x1 - mid - 8, h: y1 - y0 });
        } else if (r < 0.5) {
          var midy = y0 + (y1 - y0) * (0.4 + rng() * 0.2);
          blocks.push({ x: x0, y: y0, w: x1 - x0, h: midy - y0 });
          blocks.push({ x: x0, y: midy + 8, w: x1 - x0, h: y1 - midy - 8 });
        } else {
          blocks.push({ x: x0, y: y0, w: x1 - x0, h: y1 - y0 });
        }
      }
    }
    BLOCKS = blocks;
    return blocks;
  }

  /* ---------- engine ---------- */
  function create(canvas, opts) {
    opts = opts || {};
    var ctx = canvas.getContext("2d");
    var fog = document.createElement("canvas");
    var fctx = fog.getContext("2d");
    var pal = readPalette();
    var blocks = opts.blocks === false ? null : buildBlocks();
    var tracks = [];
    var loc = null;
    var view = { s: 0.42, x: 0, y: 0 };
    var cssW = 320, cssH = 480, dpr = 1;
    var raf = 0, dirty = true, running = false;
    var fogOpacity = opts.mode === "day" ? 0.93 : 0.94;
    var baseR = opts.mode === "day" ? 40 : 34;
    var world = { w: D.world.w, h: D.world.h };

    function resize() {
      var r = canvas.getBoundingClientRect();
      cssW = Math.max(1, Math.round(r.width));
      cssH = Math.max(1, Math.round(r.height));
      dpr = Math.min(2.5, window.devicePixelRatio || 1);
      canvas.width = Math.round(cssW * dpr);
      canvas.height = Math.round(cssH * dpr);
      fog.width = canvas.width; fog.height = canvas.height;
      dirty = true;
    }

    function project(p) { return [p[0] * view.s + view.x, p[1] * view.s + view.y]; }
    function unproject(sx, sy) { return [(sx - view.x) / view.s, (sy - view.y) / view.s]; }
    function worldToScreen(x, y) { return [x * view.s + view.x, y * view.s + view.y]; }

    function clampView() {
      var w = world.w * view.s, h = world.h * view.s;
      var m = Math.min(cssW, cssH) * 0.35;
      view.x = Math.min(cssW - m, Math.max(m - w, view.x));
      view.y = Math.min(cssH - m, Math.max(m - h, view.y));
    }

    function setScale(s) { view.s = Math.max(0.22, Math.min(6, s)); }
    function zoomAt(sx, sy, factor) {
      var before = unproject(sx, sy);
      setScale(view.s * factor);
      view.x = sx - before[0] * view.s;
      view.y = sy - before[1] * view.s;
      clampView(); dirty = true; emitView();
    }
    function panBy(dx, dy) { view.x += dx; view.y += dy; clampView(); dirty = true; emitView(); }
    function centerOn(p, s) {
      if (s != null) setScale(s);
      view.x = cssW / 2 - p[0] * view.s;
      view.y = cssH / 2 - p[1] * view.s;
      clampView(); dirty = true; emitView();
    }
    function fitBounds(list, padPct) {
      if (!list.length) return;
      var bb = polyBounds(list);
      var pad = padPct == null ? 0.14 : padPct;
      var s = Math.min((cssW * (1 - pad * 2)) / Math.max(1, bb.maxX - bb.minX), (cssH * (1 - pad * 2)) / Math.max(1, bb.maxY - bb.minY));
      setScale(s);
      view.x = cssW / 2 - (bb.minX + bb.maxX) / 2 * view.s;
      view.y = cssH / 2 - (bb.minY + bb.maxY) / 2 * view.s;
      clampView(); dirty = true; emitView();
    }
    function emitView() { if (opts.onViewChange) opts.onViewChange({ s: view.s, x: view.x, y: view.y }); }

    /* ---------- basemap ---------- */
    function applyWorld(c) { c.setTransform(dpr, 0, 0, dpr, 0, 0); c.translate(view.x, view.y); c.scale(view.s, view.s); }

    function drawLand(c) {
      c.fillStyle = pal["--map-land"].css; c.fillRect(0, 0, world.w, world.h);
      // faint tonal patches for terrain feel
      c.fillStyle = pal["--map-land-2"].css;
      [[0, 0, 900, 420], [1000, 1100, 700, 900], [0, 1600, 700, 500]].forEach(function (r) {
        c.fillRect(r[0], r[1], r[2], r[3]);
      });
    }
    function drawContours(c) {
      c.strokeStyle = pal["--map-contour"].css; c.lineWidth = 1 / view.s; c.setLineDash([7 / view.s, 6 / view.s]);
      D.contours.forEach(function (line) { strokeLine(c, line); });
      c.setLineDash([]);
    }
    function strokeLine(c, pts) {
      c.beginPath(); c.moveTo(pts[0][0], pts[0][1]);
      for (var i = 1; i < pts.length; i++) c.lineTo(pts[i][0], pts[i][1]);
      c.stroke();
    }
    function drawBlocks(c) {
      c.fillStyle = pal["--map-block"].css; c.strokeStyle = pal["--map-block-line"].css;
      c.lineWidth = 0.8 / view.s;
      blocks.forEach(function (b) {
        roundedRect(c, b.x, b.y, b.w, b.h, 5);
        c.fill(); c.stroke();
      });
    }
    function roundedRect(c, x, y, w, h, r) {
      r = Math.min(r, w / 2, h / 2);
      c.beginPath();
      c.moveTo(x + r, y); c.arcTo(x + w, y, x + w, y + h, r); c.arcTo(x + w, y + h, x, y + h, r);
      c.arcTo(x, y + h, x, y, r); c.arcTo(x, y, x + w, y, r); c.closePath();
    }
    function drawRoads(c) {
      var order = [["local", 5, "--map-road-2", "--map-road-2-casing"], ["minor", 9, "--map-road-2", "--map-road-2-casing"], ["arterial", 15, "--map-road", "--map-road-casing"]];
      order.forEach(function (o) {
        var wid = o[1], fill = pal[o[2]].css, casing = pal[o[3]].css;
        c.lineCap = "round"; c.lineJoin = "round";
        c.strokeStyle = casing; c.lineWidth = wid + 2.6;
        roadList(o[0]).forEach(function (r) { strokeLine(c, r); });
        c.strokeStyle = fill; c.lineWidth = wid;
        roadList(o[0]).forEach(function (r) { strokeLine(c, r); });
      });
    }
    function roadList(cls) {
      var out = [];
      D.vRoads.concat(D.hRoads).forEach(function (r) {
        if (r.cls !== cls) return;
        if (r.x != null) out.push([[r.x, D.YL[0]], [r.x, D.YL[D.YL.length - 1]]]);
        else out.push([[D.XL[0], r.y], [D.XL[D.XL.length - 1], r.y]]);
      });
      return out;
    }
    function drawParks(c) {
      c.fillStyle = pal["--map-park"].css; fillPoly(c, D.park);
      c.fillStyle = pal["--map-green"].css; fillPoly(c, D.green2);
    }
    function fillPoly(c, poly) {
      c.beginPath(); c.moveTo(poly[0][0], poly[0][1]);
      for (var i = 1; i < poly.length; i++) c.lineTo(poly[i][0], poly[i][1]);
      c.closePath(); c.fill();
    }
    function drawWater(c) {
      c.strokeStyle = pal["--map-water"].css; c.lineWidth = D.riverHW * 2; c.lineCap = "round"; c.lineJoin = "round";
      strokeLine(c, D.river);
      c.strokeStyle = pal["--map-water-2"].css; c.lineWidth = D.riverHW * 2 * 0.42;
      strokeLine(c, D.river);
      var L = D.lake;
      c.save(); c.translate(L.cx, L.cy); c.rotate(L.rot);
      c.fillStyle = pal["--map-water"].css;
      c.beginPath(); c.ellipse(0, 0, L.rx, L.ry, 0, 0, Math.PI * 2); c.fill();
      c.fillStyle = pal["--map-water-2"].css;
      c.beginPath(); c.ellipse(0, 0, L.rx * 0.5, L.ry * 0.5, 0, 0, Math.PI * 2); c.fill();
      c.restore();
    }
    function drawBridges(c) {
      c.strokeStyle = pal["--map-road-casing"].css; c.lineWidth = 17; c.lineCap = "round";
      D.bridges.forEach(function (b) { strokeLine(c, b); });
      c.strokeStyle = pal["--map-road"].css; c.lineWidth = 14;
      D.bridges.forEach(function (b) { strokeLine(c, b); });
    }
    function drawPaths(c) {
      c.lineCap = "round";
      c.setLineDash([9, 7]);
      c.strokeStyle = pal["--map-road-casing"].css; c.lineWidth = 3.4;
      strokeLine(c, offsetPolyline(D.river, D.riverHW + 15));
      c.setLineDash([]);
    }

    /* ---------- fog of war ---------- */
    function drawFog() {
      var fogCol = pal["--fog"].css;
      fctx.setTransform(1, 0, 0, 1, 0, 0);
      fctx.globalCompositeOperation = "source-over";
      fctx.clearRect(0, 0, fog.width, fog.height);
      fctx.fillStyle = fogCol;
      fctx.fillRect(0, 0, fog.width, fog.height);
      fctx.globalCompositeOperation = "destination-out";
      fctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      var R = Math.max(12, Math.min(240, baseR * view.s));
      var grad = fctx.createRadialGradient(0, 0, 0, 0, 0, R);
      grad.addColorStop(0, "rgba(0,0,0,1)");
      grad.addColorStop(0.62, "rgba(0,0,0,0.96)");
      grad.addColorStop(1, "rgba(0,0,0,0)");
      fctx.fillStyle = grad;
      function stamp(sx, sy) {
        fctx.beginPath(); fctx.arc(sx, sy, R, 0, Math.PI * 2); fctx.fill();
      }
      var step = Math.max(7, R * 0.42);
      tracks.forEach(function (seg) {
        for (var i = 0; i < seg.length - 1; i++) {
          var a = worldToScreen(seg[i][0], seg[i][1]);
          var b = worldToScreen(seg[i + 1][0], seg[i + 1][1]);
          var len = Math.hypot(b[0] - a[0], b[1] - a[1]);
          var n = Math.max(1, Math.ceil(len / step));
          for (var k = 0; k <= n; k++) {
            var t = k / n;
            stamp(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t);
          }
        }
      });
      if (loc) {
        var sp = worldToScreen(loc.x, loc.y);
        var R2 = R * 1.1;
        var g2 = fctx.createRadialGradient(0, 0, 0, 0, 0, R2);
        g2.addColorStop(0, "rgba(0,0,0,1)"); g2.addColorStop(0.55, "rgba(0,0,0,0.9)"); g2.addColorStop(1, "rgba(0,0,0,0)");
        fctx.fillStyle = g2;
        fctx.beginPath(); fctx.arc(sp[0], sp[1], R2, 0, Math.PI * 2); fctx.fill();
      }
      fctx.globalCompositeOperation = "source-over";
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.globalAlpha = fogOpacity;
      ctx.drawImage(fog, 0, 0);
      ctx.globalAlpha = 1;
    }

    /* ---------- tracks ---------- */
    function drawTracks(c) {
      if (!tracks.length) return;
      c.lineCap = "round"; c.lineJoin = "round";
      c.strokeStyle = pal["--track-case"].css;
      c.lineWidth = Math.max(5.4, 5.4);
      tracks.forEach(function (seg) { strokeLine(c, seg); });
      c.strokeStyle = pal["--track"].css;
      c.lineWidth = Math.max(3, 3.2);
      tracks.forEach(function (seg) { strokeLine(c, seg); });
      // start / end
      tracks.forEach(function (seg) {
        marker(c, seg[0], pal["--track"].css, pal["--map-land"].css, 5);
        marker(c, seg[seg.length - 1], pal["--track"].css, pal["--map-land"].css, 5);
      });
    }
    function marker(c, p, ring, fill, r) {
      c.beginPath(); c.arc(p[0], p[1], r, 0, Math.PI * 2); c.fillStyle = fill; c.fill();
      c.lineWidth = 2.6; c.strokeStyle = ring; c.stroke();
    }

    /* ---------- location ---------- */
    function drawLocation(c) {
      if (!loc) return;
      var halo = pal["--loc-halo"].css, dot = pal["--loc"].css;
      c.beginPath(); c.arc(loc.x, loc.y, 9, 0, Math.PI * 2); c.fillStyle = halo; c.fill();
      c.beginPath(); c.arc(loc.x, loc.y, 4.6, 0, Math.PI * 2); c.fillStyle = dot; c.fill();
      c.lineWidth = 2.2; c.strokeStyle = pal["--loc"].css; c.stroke();
    }

    /* ---------- labels (screen space) ---------- */
    function drawLabels() {
      if (opts.labels === false) return;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      var showRoads = view.s > 0.55;
      D.labels.forEach(function (l) {
        if (l.kind === "road" && !showRoads) return;
        var p = worldToScreen(l.x, l.y);
        if (p[0] < -80 || p[0] > cssW + 80 || p[1] < -40 || p[1] > cssH + 40) return;
        var size = l.kind === "district" ? 13 : l.kind === "water" ? 12.5 : l.kind === "park" ? 12.5 : l.kind === "poi" ? 11.5 : 10.5;
        if (l.kind === "district" && view.s < 0.35) return;
        var strong = l.kind === "district" || l.kind === "water" || l.kind === "park" || l.kind === "home";
        ctx.font = (strong ? "600 " : "500 ") + size + "px " + FONT_UI;
        ctx.textAlign = "center"; ctx.textBaseline = "middle";
        ctx.lineWidth = 3.4; ctx.strokeStyle = pal["--map-label-halo"] ? pal["--map-label-halo"].css : "rgba(255,255,255,0.85)";
        ctx.lineJoin = "round";
        ctx.save();
        if (l.rot) { ctx.translate(p[0], p[1]); ctx.rotate(l.rot); }
        else ctx.translate(p[0], p[1]);
        ctx.strokeText(l.text, 0, 0);
        ctx.fillStyle = (l.kind === "road" || l.kind === "poi") ? pal["--map-label"].css : pal["--map-label-strong"].css;
        ctx.fillText(l.text, 0, 0);
        ctx.restore();
      });
    }
    var FONT_UI = '"Noto Sans CJK SC","Source Han Sans SC","PingFang SC","HarmonyOS Sans SC","Microsoft YaHei",system-ui,sans-serif';

    /* ---------- frame ---------- */
    function render() {
      dirty = false;
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      applyWorld(ctx);
      drawLand(ctx);
      drawContours(ctx);
      if (blocks) drawBlocks(ctx);
      drawRoads(ctx);
      drawParks(ctx);
      drawWater(ctx);
      drawBridges(ctx);
      drawPaths(ctx);
      drawTracks(ctx);
      drawLocation(ctx);
      drawFog();
      drawLabels();
    }
    function loop() { if (!running) return; if (dirty) render(); raf = requestAnimationFrame(loop); }

    var ro = null;
    if (window.ResizeObserver) { ro = new ResizeObserver(function () { resize(); }); ro.observe(canvas); }
    window.addEventListener("resize", resize);

    resize();
    return {
      setTracks: function (t) { tracks = t || []; dirty = true; },
      setLocation: function (p) { loc = p || null; dirty = true; },
      setBlocks: function (b) { blocks = b; dirty = true; },
      refreshTheme: function () { pal = readPalette(); dirty = true; },
      resize: resize,
      start: function () { if (running) return; running = true; loop(); },
      stop: function () { running = false; cancelAnimationFrame(raf); },
      invalidate: function () { dirty = true; },
      render: render,
      project: function (x, y) { return worldToScreen(x, y); },
      unproject: unproject,
      getView: function () { return { s: view.s, x: view.x, y: view.y }; },
      setView: function (v) { if (!v) return; view.s = v.s; view.x = v.x; view.y = v.y; clampView(); dirty = true; },
      zoomAt: zoomAt,
      panBy: panBy,
      centerOn: centerOn,
      fitBounds: fitBounds,
      size: function () { return { w: cssW, h: cssH }; }
    };
  }

  window.WPMap = { create: create, buildBlocks: buildBlocks };
})();
