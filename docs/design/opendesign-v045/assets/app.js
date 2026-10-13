/* 足迹涂色 · 设计原型交互层
   —— 本文件为设计原型：底图为自绘示意矢量图，非真实地图 SDK，也不做真实定位。 */
(function () {
  "use strict";

  var D = window.WP_DATA;
  var TODAY = D.meta.updated;
  var $ = function (s, r) { return (r || document).querySelector(s); };
  var $$ = function (s, r) { return Array.prototype.slice.call((r || document).querySelectorAll(s)); };

  /* ---------------- icons ---------------- */
  function svg(p, w) { return '<svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="' + (w || 1.7) + '" stroke-linecap="round" stroke-linejoin="round">' + p + '</svg>'; }
  var IC = {
    chevL: '<path d="M15 5l-7 7 7 7"/>', chevR: '<path d="M9 5l7 7-7 7"/>', arrowR: '<path d="M5 12h13M13 6l6 6-6 6"/>',
    check: '<path d="M5 12.6l4.4 4.4L19 7.6"/>',
    location: '<circle cx="12" cy="12" r="3.2"/><path d="M12 2.6v3.2M12 18.2v3.2M2.6 12h3.2M18.2 12h3.2"/>',
    route: '<path d="M6 20c-1.7 0-3-1.3-3-3s1.3-3 3-3h4"/><circle cx="18" cy="5" r="2.6"/><path d="M18 7.6V13c0 1.7-1.3 3-3 3h-3"/>',
    shield: '<path d="M12 3l7 3v6c0 4.2-2.9 7.6-7 9-4.1-1.4-7-4.8-7-9V6z"/>',
    activity: '<path d="M3 12.5h3.6l2.3-6 4.2 12 2.3-6H21"/>',
    refresh: '<path d="M19.6 11a7.6 7.6 0 10-2.2 5.4"/><path d="M19.6 5.2v5.8h-5.8"/>',
    x: '<path d="M6 6l12 12M18 6L6 18"/>',
    alert: '<path d="M12 4.6l8.4 14.8H3.6z"/><path d="M12 10v4.2M12 17h.01"/>',
    cloudOff: '<path d="M7 18h9.4a3.5 3.5 0 001-6.9"/><path d="M6.5 7.6A4.5 4.5 0 003.6 12c0 1.8 1.1 3.4 2.8 4"/><path d="M4 4l16 16"/>',
    lock: '<rect x="5" y="10.5" width="14" height="9.5" rx="2.2"/><path d="M8.5 10.5V8a3.5 3.5 0 017 0v2.5"/>',
    download: '<path d="M12 4v11M7.6 10.6L12 15l4.4-4.4"/><path d="M5 19h14"/>',
    trash: '<path d="M5 7h14M9.5 7V5h5v2M7.5 7l.9 12h7.2l.9-12"/>',
    calendar: '<rect x="4" y="5.5" width="16" height="14.5" rx="2.5"/><path d="M4 10.2h16M9 4v3M15 4v3"/>',
    info: '<circle cx="12" cy="12" r="7.8"/><path d="M12 11.2v4.6M12 8.4h.01"/>',
    flag: '<path d="M6 20V4.5h11l-2.2 4.2L17 13H6"/>'
  };
  function icon(n, w) { return svg(IC[n] || "", w); }

  /* ---------------- state ---------------- */
  var STORE = "walkpaint.prototype.v1";
  var state = {
    page: "map", theme: "system", themeResolved: "light",
    perm: "granted", gps: "ok", mapStatus: "loading",
    autoRecord: true, bgRecord: true, precise: true,
    showLabels: true, trackColor: "coral",
    monthKey: "2026-10", selectedDay: null, consentSeen: true,
    view: null, forceEmpty: false, noisy: false
  };
  var TRACK = {
    coral: { name: "珊瑚", light: "oklch(0.655 0.150 40)", dark: "oklch(0.720 0.140 42)" },
    teal: { name: "青绿", light: "oklch(0.590 0.100 176)", dark: "oklch(0.720 0.100 176)" },
    amber: { name: "琥珀", light: "oklch(0.690 0.140 78)", dark: "oklch(0.780 0.125 78)" },
    indigo: { name: "靛蓝", light: "oklch(0.560 0.120 268)", dark: "oklch(0.680 0.120 268)" },
    rose: { name: "绛红", light: "oklch(0.590 0.150 12)", dark: "oklch(0.715 0.140 12)" }
  };

  function save() {
    try {
      localStorage.setItem(STORE, JSON.stringify({
        theme: state.theme, perm: state.perm, gps: state.gps, autoRecord: state.autoRecord,
        bgRecord: state.bgRecord, precise: state.precise, noisy: state.noisy, showLabels: state.showLabels,
        trackColor: state.trackColor, monthKey: state.monthKey, consentSeen: state.consentSeen, view: state.view
      }));
    } catch (e) {}
  }
  function load() {
    try {
      var raw = localStorage.getItem(STORE); if (!raw) return;
      var o = JSON.parse(raw); for (var k in o) if (k in state && o[k] !== null && o[k] !== undefined) state[k] = o[k];
      if (!TRACK[state.trackColor]) state.trackColor = "coral";
    } catch (e) {}
  }

  /* ---------------- theme ---------------- */
  function resolveTheme() {
    if (state.theme === "system") return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
    return state.theme;
  }
  function applyTrack() {
    var t = TRACK[state.trackColor] || TRACK.coral;
    document.documentElement.style.setProperty("--track", state.themeResolved === "dark" ? t.dark : t.light);
  }
  function applyTheme() {
    state.themeResolved = resolveTheme();
    document.documentElement.setAttribute("data-theme", state.themeResolved);
    applyTrack();
    if (mapMain) mapMain.refreshTheme();
    if (mapDay) mapDay.refreshTheme();
  }
  window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", function () { if (state.theme === "system") applyTheme(); });

  /* ---------------- time & stats ---------------- */
  function pad2(n) { return (n < 10 ? "0" : "") + n; }
  function parseDay(s) { var p = s.split("-"); return new Date(+p[0], +p[1] - 1, +p[2]); }
  function dow(s) { return "日一二三四五六"[parseDay(s).getDay()]; }
  var SPEED = 1.32; // m/s 步行速度，用于由里程推算时长（演示数据，非实测）

  function segLen(a, b) { return Math.hypot(b[0] - a[0], b[1] - a[1]); }
  function stats(rec) {
    var meters = 0;
    rec.segs.forEach(function (s) { for (var i = 0; i < s.length - 1; i++) meters += segLen(s[i], s[i + 1]); });
    var secs = meters / (rec.speed || SPEED);
    var st = rec.start.split(":"), startMin = (+st[0]) * 60 + (+st[1]);
    var endMin = startMin + Math.round(secs / 60);
    return {
      meters: meters, km: meters / 1000, secs: secs,
      start: rec.start,
      end: pad2(Math.floor(endMin / 60) % 24) + ":" + pad2(endMin % 60),
      paceSecPerKm: secs / (meters / 1000),
      points: rec.segs.reduce(function (n, s) { return n + s.length; }, 0)
    };
  }
  function fmtKm(km) { return km.toFixed(2); }
  function fmtDur(sec) {
    var m = Math.round(sec / 60);
    if (m < 60) return m + " 分钟";
    return Math.floor(m / 60) + " 小时 " + pad2(m % 60) + " 分";
  }
  function fmtPace(spk) { var m = Math.floor(spk / 60); return m + "'" + pad2(Math.round(spk - m * 60)) + '"'; }

  function days() { return state.forceEmpty ? [] : D.days; }
  function recOf(ds) { var r = null; days().forEach(function (d) { if (d.date === ds) r = d; }); return r; }
  function monthRecs(key) { var p = key.split("-"), y = +p[0], m = +p[1]; return days().filter(function (d) { var q = d.date.split("-"); return +q[0] === y && +q[1] === m; }).sort(function (a, b) { return a.date < b.date ? 1 : -1; }); }

  var _explored = null;
  function explored() {
    if (_explored) return _explored;
    var step = 25, cols = Math.ceil(D.world.w / step), rows = Math.ceil(D.world.h / step), R = 34;
    var grid = new Uint8Array(cols * rows);
    function mark(x, y) {
      var c0 = Math.max(0, Math.floor((x - R) / step)), c1 = Math.min(cols - 1, Math.floor((x + R) / step));
      var r0 = Math.max(0, Math.floor((y - R) / step)), r1 = Math.min(rows - 1, Math.floor((y + R) / step));
      for (var r = r0; r <= r1; r++) for (var c = c0; c <= c1; c++) {
        var cx = (c + 0.5) * step, cy = (r + 0.5) * step;
        if ((cx - x) * (cx - x) + (cy - y) * (cy - y) <= R * R) grid[r * cols + c] = 1;
      }
    }
    days().forEach(function (d) {
      d.segs.forEach(function (seg) {
        for (var i = 0; i < seg.length - 1; i++) {
          var a = seg[i], b = seg[i + 1], len = segLen(a, b), n = Math.max(1, Math.ceil(len / 8));
          for (var k = 0; k <= n; k++) { var t = k / n; mark(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t); }
        }
      });
    });
    var on = 0; for (var i = 0; i < grid.length; i++) on += grid[i];
    _explored = { ratio: grid.length ? on / grid.length : 0, area: on * step * step / 1e6 };
    return _explored;
  }

  function allPoints() {
    var out = [];
    days().forEach(function (d) { d.segs.forEach(function (s) { s.forEach(function (p) { out.push(p); }); }); });
    return out;
  }
  function nearestRoad(p) {
    var best = null, bd = 16;
    D.vRoads.forEach(function (r) { var d = Math.abs(p[0] - r.x); if (d < bd && p[1] > D.YL[0] - 20 && p[1] < D.YL[D.YL.length - 1] + 20) { bd = d; best = r.name; } });
    D.hRoads.forEach(function (r) { var d = Math.abs(p[1] - r.y); if (d < bd && p[0] > D.XL[0] - 20 && p[0] < D.XL[D.XL.length - 1] + 20) { bd = d; best = r.name; } });
    return best;
  }
  function waypoints(rec) {
    var out = [];
    rec.segs.forEach(function (s) {
      s.forEach(function (p) { var n = nearestRoad(p); if (n && out[out.length - 1] !== n) out.push(n); });
    });
    return out.slice(0, 7);
  }

  /* ---------------- toast / sheet ---------------- */
  var toastT = 0;
  function toast(msg) {
    var el = $("#toast"); el.textContent = msg; el.classList.add("show");
    clearTimeout(toastT); toastT = setTimeout(function () { el.classList.remove("show"); }, 1900);
  }
  var sheetLastFocus = null;
  function openSheet(html) {
    var sheet = $("#sheet"); sheetLastFocus = document.activeElement;
    sheet.innerHTML = '<div class="grab"></div>' + html;
    sheet.classList.add("show"); $("#scrim").classList.add("show");
    setTimeout(function () { var f = sheet.querySelector("button,a"); if (f) f.focus(); }, 60);
  }
  function closeSheet() {
    $("#sheet").classList.remove("show"); $("#scrim").classList.remove("show");
    if (sheetLastFocus && sheetLastFocus.focus) sheetLastFocus.focus();
  }
  $("#scrim").addEventListener("click", closeSheet);
  document.addEventListener("keydown", function (e) { if (e.key === "Escape") closeSheet(); });

  var CONSENT = '<h3>开始记录前</h3>' +
    '<p class="lead">足迹涂色把你的行走轨迹画成一张慢慢被点亮的地图。开启前，先说明它如何使用你的位置。</p>' +
    '<div class="pts">' +
    '<div class="pt">' + icon("location") + '<div><div class="b">仅在你走路时定位</div><div class="s">只有开始记录后才会读取位置，随时可以关闭自动记录。</div></div></div>' +
    '<div class="pt">' + icon("lock") + '<div><div class="b">轨迹只保存在本机</div><div class="s">原始轨迹不会上传，也不与账号或社交关系关联。</div></div></div>' +
    '<div class="pt">' + icon("shield") + '<div><div class="b">可以随时导出或清除</div><div class="s">设置里能导出轨迹，也能一键清空全部记录。</div></div></div>' +
    '</div><div class="actions">' +
    '<button class="btn ghost" data-act="consent-deny">暂不开启</button>' +
    '<button class="btn primary" data-act="consent-allow">' + icon("check") + '允许并继续</button>' +
    '</div><p class="note" style="margin-top:12px">这是设计原型的示意流程，不会调用系统定位权限。</p>';

  function showConsent() {
    state.consentSeen = true;
    openSheet(CONSENT);
  }

  /* ---------------- map ---------------- */
  var mapMain = null, mapDay = null, mainOpts = null, dayOpts = null;

  function method() { return state.perm === "granted" && state.gps === "ok" && state.mapStatus === "ready"; }

  function syncMap() {
    if (!mapMain) return;
    mapMain.setTracks(trackPolys(null));
    mapMain.setLocation(method() ? { x: D.home.x, y: D.home.y } : null);
    mapMain.invalidate();
  }
  function trackPolys(rec) {
    var out = [];
    var list = rec ? [rec] : days();
    list.forEach(function (d) { d.segs.forEach(function (s) { out.push(s); }); });
    return out;
  }

  function initMap() {
    var cv = $("#map-canvas");
    mainOpts = { mode: "explore", labels: state.showLabels, onViewChange: function (v) { state.view = v; } };
    mapMain = window.WPMap.create(cv, mainOpts);
    mapMain.setTracks(trackPolys(null));
    mapMain.setLocation({ x: D.home.x, y: D.home.y });
    var v = state.view;
    if (v && isFinite(v.s)) mapMain.setView(v);
    else { mapMain.fitBounds(allPoints().concat([[D.home.x, D.home.y]]), 0.1); }
    mapMain.start();

    // ---- pointer gestures: drag pan / two-finger pinch (anchored at midpoint)
    var ptrs = new Map(), drag = null, pinch = null;
    function rectRel(e) { var r = cv.getBoundingClientRect(); return { x: e.clientX - r.left, y: e.clientY - r.top }; }
    function twoPts() { var a = []; ptrs.forEach(function (p) { a.push(p); }); return a; }
    cv.addEventListener("pointerdown", function (e) {
      try { cv.setPointerCapture(e.pointerId); } catch (x) {}
      ptrs.set(e.pointerId, { x: e.clientX, y: e.clientY });
      if (ptrs.size === 1) { var g = mapMain.getView(); drag = { x: e.clientX, y: e.clientY, vx: g.x, vy: g.y }; }
      else { drag = null; var t = twoPts(); pinch = { d: Math.hypot(t[0].x - t[1].x, t[0].y - t[1].y) }; }
    });
    cv.addEventListener("pointermove", function (e) {
      if (!ptrs.has(e.pointerId)) return;
      ptrs.set(e.pointerId, { x: e.clientX, y: e.clientY });
      if (ptrs.size === 1 && drag) {
        var g = mapMain.getView();
        mapMain.setView({ s: g.s, x: drag.vx + (e.clientX - drag.x), y: drag.vy + (e.clientY - drag.y) });
      } else if (ptrs.size >= 2) {
        var t = twoPts(), d = Math.hypot(t[0].x - t[1].x, t[0].y - t[1].y);
        var r = cv.getBoundingClientRect();
        var mid = { x: (t[0].x + t[1].x) / 2 - r.left, y: (t[0].y + t[1].y) / 2 - r.top };
        if (pinch && pinch.d > 0 && d > 0) mapMain.zoomAt(mid.x, mid.y, d / pinch.d);
        pinch = { d: d };
      }
    });
    function endPtr(e) {
      ptrs.delete(e.pointerId);
      if (ptrs.size === 0) { drag = null; pinch = null; save(); }
      else if (ptrs.size === 1) { var t = twoPts()[0]; var g = mapMain.getView(); drag = { x: t.x, y: t.y, vx: g.x, vy: g.y }; pinch = null; }
    }
    cv.addEventListener("pointerup", endPtr);
    cv.addEventListener("pointercancel", endPtr);
    cv.addEventListener("wheel", function (e) {
      e.preventDefault(); var r = cv.getBoundingClientRect();
      mapMain.zoomAt(e.clientX - r.left, e.clientY - r.top, e.deltaY < 0 ? 1.12 : 1 / 1.12);
    }, { passive: false });

    // ---- zoom buttons anchored at the current position (so repeat taps don't drift)
    function anchor() {
      var sz = mapMain.size();
      if (method()) {
        var s = mapMain.project(D.home.x, D.home.y);
        if (s[0] > 28 && s[0] < sz.w - 28 && s[1] > 86 && s[1] < sz.h - 150) return s;
      }
      return [sz.w / 2, sz.h / 2];
    }
    $("#zoom-in").addEventListener("click", function () { var a = anchor(); mapMain.zoomAt(a[0], a[1], 1.45); save(); });
    $("#zoom-out").addEventListener("click", function () { var a = anchor(); mapMain.zoomAt(a[0], a[1], 1 / 1.45); save(); });
    $("#locate").addEventListener("click", function () {
      if (state.perm !== "granted") { showConsent(); return; }
      if (state.gps !== "ok") { toast("当前无法定位，请稍后重试"); return; }
      mapMain.centerOn([D.home.x, D.home.y], Math.max(mapMain.getView().s, 0.85));
      save(); toast("已回到当前位置");
    });
    window.addEventListener("resize", function () { mapMain.resize(); });
  }

  /* ---------------- map page chrome ---------------- */
  function renderOverview() {
    var mrecs = monthRecs(state.monthKey);
    var mkm = mrecs.reduce(function (n, d) { return n + stats(d).km; }, 0);
    var e = explored();
    var todayRec = recOf(TODAY);
    var todayTxt = todayRec ? ("今天 " + fmtKm(stats(todayRec).km) + " km · " + todayRec.start + " 出发") : "今天还没有记录";
    $("#overview").innerHTML =
      '<div class="ov-head">' +
      '<div class="ov-metric"><span class="k">已涂色</span><span class="v"><b>' + (e.ratio * 100).toFixed(1) + '</b><span>%</span></span></div>' +
      '<div class="ov-metric right"><span class="k">本月里程</span><span class="v"><b>' + fmtKm(mkm) + '</b><span>km</span></span></div>' +
      '</div>' +
      '<div class="ov-bar"><i style="width:' + Math.min(100, e.ratio * 100).toFixed(1) + '%"></i></div>' +
      '<div class="ov-foot"><span class="stat">' + todayTxt + '</span>' +
      '<button class="link" data-nav="tracks">足迹记录 ' + icon("arrowR") + '</button></div>';
  }

  function renderMapState() {
    var host = $("#map-state"), slots = $("#map-slots");
    if (state.mapStatus === "loading") {
      host.innerHTML = '<div class="overlay card"><div class="spinner"></div><div class="t">正在加载底图…</div><div class="d">示意底图为本地绘制，首次进入需要一点时间。</div></div>';
    } else if (state.mapStatus === "error") {
      host.innerHTML = '<div class="overlay card"><div class="badge-ico warn">' + icon("alert") + '</div>' +
        '<div class="t">底图加载失败</div><div class="d">示意底图数据没有载入成功。可以重新加载，或先查看已记录的足迹。</div>' +
        '<button class="btn primary" id="map-retry" style="max-width:200px">' + icon("refresh") + '重新加载</button></div>';
    } else { host.innerHTML = ""; }

    var s = "";
    if (state.mapStatus === "ready" && state.perm === "denied") {
      s = '<div class="banner">' + icon("alert") +
        '<span class="txt">定位权限未开启，暂时无法记录新的足迹。</span>' +
        '<button class="act" id="banner-open">去开启</button>' +
        '<button class="x" id="banner-close" aria-label="关闭">' + icon("x") + '</button></div>';
    } else if (state.mapStatus === "ready") {
      var ok = state.gps === "ok";
      s = '<div class="loc-chip"><span class="dot ' + (ok ? "ok" : "bad") + '"></span>' +
        (ok ? "已定位 · " + D.meta.place : "定位不可用 · 等待信号") + '</div>';
    }
    slots.innerHTML = s;
  }

  /* ---------------- calendar ---------------- */
  function calCell(dateStr, km) {
    var isToday = dateStr === TODAY, sel = dateStr === state.selectedDay;
    var w = km ? Math.min(20, 6 + km * 4) : 0;
    return '<button class="cal-cell' + (km ? " has-rec" : "") + (isToday ? " today" : "") + (sel ? " sel" : "") +
      '" data-day="' + dateStr + '"><span class="n">' + (+dateStr.split("-")[2]) + '</span>' +
      '<span class="bar" style="width:' + w + 'px"></span></button>';
  }

  function renderCalendar() {
    var p = state.monthKey.split("-"), y = +p[0], m = +p[1];
    var first = new Date(y, m - 1, 1), startDow = first.getDay();
    var inMonth = new Date(y, m, 0).getDate();
    var byDate = {};
    monthRecs(state.monthKey).forEach(function (d) { byDate[d.date] = stats(d).km; });
    var html = '<div class="cal"><div class="cal-dow">' + "日一二三四五六".split("").map(function (c) { return "<span>" + c + "</span>"; }).join("") + '</div><div class="cal-grid">';
    for (var i = 0; i < startDow; i++) html += '<span class="cal-cell blank"></span>';
    for (var d = 1; d <= inMonth; d++) {
      var ds = y + "-" + pad2(m) + "-" + pad2(d);
      html += calCell(ds, byDate[ds]);
    }
    html += '</div></div>';
    return html;
  }

  /* ---------------- tracks page ---------------- */
  function renderTracks() {
    var p = state.monthKey.split("-"), y = +p[0], m = +p[1];
    var recs = monthRecs(state.monthKey);
    var km = recs.reduce(function (n, d) { return n + stats(d).km; }, 0);
    var longest = recs.reduce(function (n, d) { return Math.max(n, stats(d).km); }, 0);
    var dur = recs.reduce(function (n, d) { return n + stats(d).secs; }, 0);
    var dowTxt = ["一月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "十一月", "十二月"];

    var h = '<div class="pagehead"><h1>足迹</h1><p>按月回看走过的地面</p></div>';
    h += '<div class="monthnav"><button class="iconbtn" id="m-prev" aria-label="上个月">' + icon("chevL") + '</button>' +
      '<div class="m">' + y + "年" + m + "月<em>" + (recs.length ? "记录 " + recs.length + " 天" : "暂无记录") + '</em></div>' +
      '<button class="iconbtn" id="m-next" aria-label="下个月">' + icon("chevR") + '</button></div>';

    if (!recs.length) {
      h += '<div class="card"><div class="empty">' + icon("route", 1.5) +
        '<div class="t">这个月还没有足迹</div><div class="d">去探索页走走，走过的地面会在这里留下记录。</div>' +
        '<button class="btn primary" id="empty-go" style="max-width:190px">' + icon("location") + '回到探索</button></div></div>';
      h += renderCalendar();
      h += '<p class="note" style="margin-top:12px">提示：切到 2026 年 10 月或 9 月可以看到有记录的状态。</p>';
      $("#tracks-body").innerHTML = h;
      wireTracks();
      return;
    }

    h += '<div class="statstrip">' +
      '<div class="s"><div class="k">本月里程</div><div class="v"><b>' + fmtKm(km) + '</b><span>km</span></div></div>' +
      '<div class="s"><div class="k">出行天数</div><div class="v"><b>' + recs.length + '</b><span>天</span></div></div>' +
      '<div class="s"><div class="k">最长单日</div><div class="v"><b>' + fmtKm(longest) + '</b><span>km</span></div><div class="sub">' + fmtDur(dur) + ' 合计</div></div>' +
      '</div>';
    h += renderCalendar();
    h += '<div class="calnote"><span class="sw"></span><span>短横线长度表示当天的里程；点某一天查看当天路线。</span></div>';
    h += '<div class="sect"><div class="sect-h">本月的日子</div><div class="card">';
    recs.forEach(function (d) {
      var s = stats(d);
      h += '<button class="row" data-day="' + d.date + '"><span class="ic-w">' + icon("route") + '</span>' +
        '<span class="tx"><span class="t">' + d.date.slice(5).replace("-", " 月 ") + ' 日 · 周' + dow(d.date) + '</span>' +
        '<span class="d">' + s.start + " – " + s.end + " · " + fmtDur(s.secs) + '</span></span>' +
        '<span class="end"><span class="v">' + fmtKm(s.km) + ' km</span>' + icon("chevR") + '</span></button>';
    });
    h += '</div></div>';
    $("#tracks-body").innerHTML = h;
    wireTracks();
  }

  function wireTracks() {
    var b = $("#tracks-body");
    var prev = $("#m-prev", b), next = $("#m-next", b);
    if (prev) prev.addEventListener("click", function () { shiftMonth(-1); });
    if (next) next.addEventListener("click", function () { shiftMonth(1); });
    var eg = $("#empty-go", b); if (eg) eg.addEventListener("click", function () { go("map"); });
  }
  function shiftMonth(delta) {
    var p = state.monthKey.split("-"), y = +p[0], m = +p[1] + delta;
    if (m < 1) { m = 12; y--; } if (m > 12) { m = 1; y++; }
    if (y < 2025 || y > 2027) return;
    state.monthKey = y + "-" + pad2(m);
    save(); renderTracks();
  }

  /* ---------------- day detail ---------------- */
  function renderDay() {
    var ds = state.selectedDay; if (!ds) { go("tracks"); return; }
    $("#day-title").textContent = ds.slice(0, 4) + " 年 " + ds.slice(5).replace("-", " 月 ") + " 日 · 周" + dow(ds);
    var rec = recOf(ds);
    var body = $("#day-body");

    if (!rec) {
      body.innerHTML = '<div class="card"><div class="empty">' + icon("calendar", 1.5) +
        '<div class="t">这一天没有记录</div><div class="d">' + ds.slice(5).replace("-", " 月 ") + ' 日没有产生足迹，可能没有开始记录或没有出行。</div>' +
        '<button class="btn ghost" id="day-exp" style="max-width:190px">' + icon("location") + '回到探索页</button></div></div>';
      var b = $("#day-exp"); if (b) b.addEventListener("click", function () { go("map"); });
      return;
    }

    var s = stats(rec);
    var wps = waypoints(rec);
    body.innerHTML =
      '<div class="daymap" id="day-mapwrap"><canvas id="day-canvas"></canvas>' +
      '<div class="tag">' + icon("route") + '当天路线</div>' +
      '<div class="scale"><i></i><span>' + (s.km > 3 ? "500 m" : "200 m") + '</span></div></div>' +
      '<div class="sect"><div class="sect-h">当天数据</div><div class="kv">' +
      '<div class="c"><div class="k">距离</div><div class="v">' + fmtKm(s.km) + '<span>km</span></div></div>' +
      '<div class="c"><div class="k">用时</div><div class="v">' + Math.round(s.secs / 60) + '<span>分钟</span></div></div>' +
      '<div class="c"><div class="k">平均配速</div><div class="v">' + fmtPace(s.paceSecPerKm) + '<span>/km</span></div></div>' +
      '<div class="c"><div class="k">出发 – 返回</div><div class="v" style="font-size:16px">' + s.start + '<span>–</span>' + s.end + '</div></div>' +
      '</div></div>' +
      '<div class="sect"><div class="sect-h">途经</div><div class="card">' +
      wps.map(function (n) { return '<div class="row" style="cursor:default"><span class="ic-w">' + icon("flag") + '</span><span class="tx"><span class="t">' + n + '</span></span></div>'; }).join("") +
      '</div></div>' +
      '<p class="note" style="margin-top:16px">当天路线为原创匿名演示数据，底图由本地绘制的示意矢量图生成，不代表真实地理信息。</p>';

    // day map instance
    if (mapDay) { mapDay.stop(); mapDay = null; }
    var cv = $("#day-canvas");
    dayOpts = { mode: "day", labels: state.showLabels, blocks: true };
    mapDay = window.WPMap.create(cv, dayOpts);
    mapDay.setTracks(trackPolys(rec));
    var pts = []; rec.segs.forEach(function (g) { g.forEach(function (p) { pts.push(p); }); });
    mapDay.resize();
    mapDay.fitBounds(pts, 0.16);
    if (ds === TODAY && method()) mapDay.setLocation({ x: D.home.x, y: D.home.y });
    mapDay.start();
  }

  /* ---------------- mine page ---------------- */
  function switchRow(id, label, desc, checked) {
    return '<button class="row" data-toggle="' + id + '" role="switch" aria-checked="' + (checked ? "true" : "false") + '">' +
      '<span class="tx"><span class="t">' + label + '</span>' + (desc ? '<span class="d">' + desc + '</span>' : '') + '</span>' +
      '<span class="end"><span class="switch" aria-checked="' + (checked ? "true" : "false") + '"></span></span></button>';
  }
  function navRow(id, ic, label, desc, value) {
    return '<button class="row" data-goto="' + id + '"><span class="ic-w">' + icon(ic) + '</span>' +
      '<span class="tx"><span class="t">' + label + '</span>' + (desc ? '<span class="d">' + desc + '</span>' : '') + '</span>' +
      '<span class="end"><span class="v">' + (value || "") + '</span>' + icon("chevR") + '</span></button>';
  }
  function permLabel() { return state.perm === "granted" ? "已允许" : state.perm === "denied" ? "已拒绝" : "未询问"; }

  function renderMine() {
    var e = explored();
    var totalKm = days().reduce(function (n, d) { return n + stats(d).km; }, 0);
    var h = '<div class="pagehead"><h1>我的</h1><p>记录方式、外观与数据</p></div>';
    h += '<div class="statstrip">' +
      '<div class="s"><div class="k">总里程</div><div class="v"><b>' + totalKm.toFixed(1) + '</b><span>km</span></div></div>' +
      '<div class="s"><div class="k">记录天数</div><div class="v"><b>' + days().length + '</b><span>天</span></div></div>' +
      '<div class="s"><div class="k">已涂色</div><div class="v"><b>' + (e.ratio * 100).toFixed(1) + '</b><span>%</span></div></div>' +
      '</div>';

    h += '<div class="sect"><div class="sect-h">记录</div><div class="card">' +
      switchRow("autoRecord", "自动记录", "检测到步行时自动开始记录", state.autoRecord) +
      switchRow("bgRecord", "后台记录", "切到后台仍继续记录轨迹", state.bgRecord) +
      '<div class="row" style="cursor:default"><span class="tx"><span class="t">记录精度</span><span class="d">高精度更费电</span></span>' +
      '<span class="end"><span class="seg" data-seg="precise">' +
      '<button data-v="high" aria-pressed="' + (state.precise ? "true" : "false") + '">高精度</button>' +
      '<button data-v="normal" aria-pressed="' + (!state.precise ? "true" : "false") + '">省电</button>' +
      '</span></span></div>' +
      '</div></div>';

    h += '<div class="sect"><div class="sect-h">外观</div><div class="card">' +
      '<div class="row" style="cursor:default;flex-wrap:wrap"><span class="tx"><span class="t">轨迹颜色</span><span class="d">当前 ' + TRACK[state.trackColor].name + '</span></span>' +
      '<span class="end"><span class="swatches" data-swatches>' +
      Object.keys(TRACK).map(function (k) {
        var t = TRACK[k], c = state.themeResolved === "dark" ? t.dark : t.light;
        return '<button class="swatch" data-color="' + k + '" aria-pressed="' + (k === state.trackColor ? "true" : "false") + '" aria-label="' + t.name + '" style="background:' + c + '"></button>';
      }).join("") + '</span></span></div>' +
      '<div class="row" style="cursor:default"><span class="tx"><span class="t">主题</span></span><span class="end"><span class="seg" data-seg="theme">' +
      ["system", "light", "dark"].map(function (k) {
        var n = { system: "跟随系统", light: "浅色", dark: "深色" }[k];
        return '<button data-v="' + k + '" aria-pressed="' + (state.theme === k ? "true" : "false") + '">' + n + '</button>';
      }).join("") + '</span></span></div>' +
      switchRow("showLabels", "地图注记", "在地图上显示路名与地名", state.showLabels) +
      '</div></div>';

    h += '<div class="sect"><div class="sect-h">隐私与数据</div><div class="card">' +
      navRow("privacy", "lock", "隐私与权限", "定位权限、数据留存", permLabel()) +
      '<div class="row" style="cursor:default"><span class="ic-w">' + icon("download") + '</span><span class="tx"><span class="t">轨迹数据</span><span class="d">仅保存在本机，未上传</span></span><span class="end"><span class="v">' + days().length + ' 条</span></span></div>' +
      '<button class="row danger" data-act="clear"><span class="ic-w">' + icon("trash") + '</span><span class="tx"><span class="t">清除本地记录</span><span class="d">删除全部足迹与地图涂色</span></span><span class="end">' + icon("chevR") + '</span></button>' +
      '</div></div>';

    h += '<div class="sect"><div class="sect-h">关于</div><div class="card">' +
      '<button class="row" data-act="update"><span class="ic-w">' + icon("refresh") + '</span><span class="tx"><span class="t">检查更新</span></span><span class="end"><span class="v">v' + D.meta.version + '</span>' + icon("chevR") + '</span></button>' +
      navRow("diag", "activity", "诊断与更新", "运行状态与原型状态预览") +
      '<div class="row" style="cursor:default"><span class="ic-w">' + icon("info") + '</span><span class="tx"><span class="t">关于足迹涂色</span><span class="d">匿名演示版本 · ' + D.meta.place + '</span></span></div>' +
      '</div></div>';
    h += '<p class="note" style="margin-top:18px">这是界面设计原型：底图为本地绘制的示意矢量图，轨迹为原创匿名演示数据，未接入真实地图 SDK，也不进行真实定位。</p>';
    $("#mine-body").innerHTML = h;
  }

  /* ---------------- privacy ---------------- */
  function renderPrivacy() {
    var h = '<div class="pagehead"><h1>隐私与权限</h1><p>位置如何使用、数据存在哪里</p></div>';
    h += '<div class="card"><div class="row" style="cursor:default"><span class="ic-w">' + icon("location") + '</span>' +
      '<span class="tx"><span class="t">位置权限</span><span class="d">当前：' + permLabel() + '</span></span>' +
      '<span class="end"><span class="seg" data-perm>' +
      ["granted", "whileUsing", "denied"].map(function (k) {
        var n = { granted: "始终", whileUsing: "使用期间", denied: "拒绝" }[k];
        var on = (k === "granted" || k === "whileUsing") ? state.perm === "granted" : state.perm === "denied";
        return '<button data-v="' + k + '" aria-pressed="' + (on ? "true" : "false") + '">' + n + '</button>';
      }).join("") + '</span></span></div>' +
      switchRow("bgRecord", "后台持续记录", "锁屏或切到后台时继续记录轨迹", state.bgRecord) +
      '</div>';

    h += '<div class="sect"><div class="sect-h">数据</div><div class="card">' +
      '<div class="row" style="cursor:default"><span class="ic-w">' + icon("lock") + '</span><span class="tx"><span class="t">轨迹存储</span><span class="d">原始轨迹仅保存在本机，不上传服务器</span></span><span class="end"><span class="pill ok">仅本机</span></span></div>' +
      switchRow("noisy", "匿名使用统计", "只上报崩溃与性能数据，不含轨迹", state.noisy) +
      '<button class="row" data-act="export"><span class="ic-w">' + icon("download") + '</span><span class="tx"><span class="t">导出轨迹</span><span class="d">导出为 GPX 文件（原型不带出真实文件）</span></span><span class="end">' + icon("chevR") + '</span></button>' +
      '<button class="row danger" data-act="clear"><span class="ic-w">' + icon("trash") + '</span><span class="tx"><span class="t">清除本地记录</span><span class="d">删除全部足迹与地图涂色</span></span><span class="end">' + icon("chevR") + '</span></button>' +
      '</div></div>';
    h += '<p class="note" style="margin-top:18px">原型说明：本页面只演示权限与数据留存的界面与状态切换，不会申请系统权限，也不会写入或导出任何真实文件。</p>';
    $("#privacy-body").innerHTML = h;
  }

  /* ---------------- diagnostics ---------------- */
  function renderDiag() {
    var pill = state.mapStatus === "ready" ? '<span class="pill ok">正常</span>' :
      state.mapStatus === "loading" ? '<span class="pill warn">加载中</span>' : '<span class="pill warn">失败</span>';
    var locPill = state.perm === "denied" ? '<span class="pill warn">未授权</span>' :
      state.gps === "ok" ? '<span class="pill ok">已定位</span>' : '<span class="pill warn">无信号</span>';
    var pts = allPoints().length;

    var h = '<div class="pagehead"><h1>诊断与更新</h1><p>运行状态与原型状态预览</p></div>';
    h += '<div class="sect"><div class="sect-h">运行状态</div><div class="card">' +
      '<div class="row" style="cursor:default"><span class="tx"><span class="t">示意底图</span><span class="d">本地绘制的矢量示意底图</span></span><span class="end">' + pill + '</span></div>' +
      '<div class="row" style="cursor:default"><span class="tx"><span class="t">定位服务</span><span class="d">权限 ' + permLabel() + ' · 信号 ' + (state.gps === "ok" ? "正常" : "不可用") + '</span></span><span class="end">' + locPill + '</span></div>' +
      '<div class="row" style="cursor:default"><span class="tx"><span class="t">本地数据</span><span class="d">' + days().length + ' 天记录 · ' + pts + ' 个轨迹点</span></span><span class="end"><span class="v">' + explored().area.toFixed(2) + ' km²</span></span></div>' +
      '<div class="row" style="cursor:default"><span class="tx"><span class="t">版本</span></span><span class="end"><span class="v">v' + D.meta.version + '</span></span></div>' +
      '</div></div>';

    h += '<div class="sect"><div class="sect-h">原型状态预览</div><div class="card">' +
      '<button class="row" data-state="loading"><span class="ic-w">' + icon("refresh") + '</span><span class="tx"><span class="t">加载状态</span><span class="d">底图正在载入</span></span><span class="end">' + icon("chevR") + '</span></button>' +
      '<button class="row" data-state="nolocation"><span class="ic-w">' + icon("cloudOff") + '</span><span class="tx"><span class="t">无定位</span><span class="d">权限已给但暂无卫星信号</span></span><span class="end">' + icon("chevR") + '</span></button>' +
      '<button class="row" data-state="empty"><span class="ic-w">' + icon("route") + '</span><span class="tx"><span class="t">无记录</span><span class="d">空月份与空日期</span></span><span class="end">' + icon("chevR") + '</span></button>' +
      '<button class="row" data-state="denied"><span class="ic-w">' + icon("alert") + '</span><span class="tx"><span class="t">定位权限缺失</span><span class="d">地图顶部的提示条</span></span><span class="end">' + icon("chevR") + '</span></button>' +
      '<button class="row" data-state="error"><span class="ic-w">' + icon("alert") + '</span><span class="tx"><span class="t">地图错误</span><span class="d">底图加载失败与重试</span></span><span class="end">' + icon("chevR") + '</span></button>' +
      '<button class="row" data-state="consent"><span class="ic-w">' + icon("shield") + '</span><span class="tx"><span class="t">首次授权引导</span><span class="d">开始记录前的说明</span></span><span class="end">' + icon("chevR") + '</span></button>' +
      '<button class="row" data-state="reset"><span class="ic-w">' + icon("check") + '</span><span class="tx"><span class="t">恢复默认状态</span><span class="d">回到正常的可记录状态</span></span><span class="end">' + icon("chevR") + '</span></button>' +
      '</div></div>';

    h += '<div class="sect"><div class="sect-h">最近日志</div><div class="card" style="padding:12px 14px">' +
      '<div class="mono" style="color:var(--muted);line-height:1.9">' +
      '2026-10-13 18:20  开始记录 · ' + D.meta.place + '<br>' +
      '2026-10-13 18:52  记录结束 · 2.04 km<br>' +
      '2026-10-13 18:52  涂色已更新<br>' +
      '2026-10-12 06:38  记录结束 · 2.46 km</div></div></div>';

    h += '<p class="note" style="margin-top:18px">诊断页中的状态预览用于演示加载、无定位、无记录、权限缺失与地图错误等界面状态；这是设计原型，不代表真实 SDK 的验证结果。</p>';
    $("#diag-body").innerHTML = h;
  }

  /* ---------------- routing ---------------- */
  var backTarget = { day: "tracks", privacy: "mine", diag: "mine" };
  function go(page) {
    state.page = page;
    $$(".screen").forEach(function (s) { s.classList.toggle("active", s.id === "page-" + page); });
    $("#nav").style.display = (page === "map" || page === "tracks" || page === "mine") ? "" : "none";
    $$("#nav [data-nav]").forEach(function (b) { b.setAttribute("aria-selected", b.getAttribute("data-nav") === page ? "true" : "false"); });

    if (page !== "day" && mapDay) { mapDay.stop(); mapDay = null; }
    if (page === "tracks") renderTracks();
    else if (page === "mine") renderMine();
    else if (page === "privacy") renderPrivacy();
    else if (page === "diag") renderDiag();
    else if (page === "day") renderDay();
    else if (page === "map") { renderOverview(); syncMap(); if (mapMain) mapMain.resize(); }

    if (page !== "day") { var pb = document.querySelector("#page-" + page + " .pagebody"); if (pb) pb.scrollTop = 0; }
    save();
  }
  function refreshMine() { var el = $("#mine-body"), y = el.scrollTop; renderMine(); el.scrollTop = y; }
  function refreshPrivacy() { var el = $("#privacy-body"), y = el.scrollTop; renderPrivacy(); el.scrollTop = y; }

  /* ---------------- global wiring ---------------- */
  function applyState(name) {
    if (name === "loading") { state.mapStatus = "loading"; state.gps = "ok"; state.perm = "granted"; }
    if (name === "nolocation") { state.mapStatus = "ready"; state.perm = "granted"; state.gps = "unavailable"; }
    if (name === "denied") { state.mapStatus = "ready"; state.perm = "denied"; state.gps = "ok"; }
    if (name === "error") { state.mapStatus = "error"; }
    if (name === "empty") { state.forceEmpty = true; _explored = null; state.monthKey = "2026-07"; state.mapStatus = "ready"; state.perm = "granted"; state.gps = "ok"; }
    if (name === "consent") { state.mapStatus = "ready"; state.perm = "denied"; showConsent(); }
    if (name === "reset") { state.forceEmpty = false; _explored = null; state.mapStatus = "ready"; state.perm = "granted"; state.gps = "ok"; state.monthKey = "2026-10"; }
    save(); renderMapState(); renderOverview(); syncMap();
    go(name === "empty" ? "tracks" : "map");
    if (name === "loading") setTimeout(function () { if (state.mapStatus === "loading") { state.mapStatus = "ready"; renderMapState(); syncMap(); renderOverview(); } }, 1000);
  }

  function init() {
    load();
    applyTheme();
    initMap();
    renderOverview();
    renderMapState();
    go(state.page || "map");

    $$("[data-back]").forEach(function (b) {
      b.addEventListener("click", function () {
        var cur = document.querySelector(".screen.active");
        go(backTarget[cur.id.replace("page-", "")] || "mine");
      });
    });
    $("#day-back").addEventListener("click", function () { go("tracks"); });
    document.addEventListener("click", delegate);

    if (!state.consentSeen) setTimeout(showConsent, 420);

    setTimeout(function () {
      if (state.mapStatus === "loading") { state.mapStatus = "ready"; renderMapState(); syncMap(); renderOverview(); }
    }, 950);
  }

  function delegate(e) {
    var t = e.target; if (!t || !t.closest) return;

    var nav = t.closest("[data-nav]");
    if (nav) { var g = nav.getAttribute("data-nav"); if (g === "tracks") state.selectedDay = null; go(g); return; }

    var day = t.closest("[data-day]");
    if (day) { state.selectedDay = day.getAttribute("data-day"); save(); go("day"); return; }

    var st = t.closest("[data-state]");
    if (st) { applyState(st.getAttribute("data-state")); return; }

    var act = t.closest("[data-act]");
    if (act) {
      var a = act.getAttribute("data-act");
      if (a === "consent-allow") { state.perm = "granted"; state.consentSeen = true; closeSheet(); save(); syncMap(); renderMapState(); toast("定位权限已开启"); }
      else if (a === "consent-deny") { state.perm = "denied"; state.consentSeen = true; closeSheet(); save(); syncMap(); renderMapState(); toast("已跳过，可稍后在设置中开启"); }
      else if (a === "update") { toast("正在检查更新…"); setTimeout(function () { toast("已是最新版本 v" + D.meta.version); }, 900); }
      else if (a === "export") { toast("原型不导出真实文件"); }
      else if (a === "clear") {
        openSheet('<h3>清除全部足迹？</h3><p class="lead">将删除本机保存的全部轨迹与地图涂色，且无法恢复。这是设计原型，不会真正删除任何数据。</p>' +
          '<div class="actions"><button class="btn ghost" data-act="clear-cancel">取消</button>' +
          '<button class="btn primary" data-act="clear-ok" style="background:var(--danger)">清除</button></div>');
      }
      else if (a === "clear-cancel") { closeSheet(); }
      else if (a === "clear-ok") { closeSheet(); toast("原型演示：未真正删除"); }
      return;
    }

    var tg = t.closest("[data-toggle]");
    if (tg) {
      var k = tg.getAttribute("data-toggle");
      state[k] = !state[k];
      save();
      if (k === "showLabels") {
        if (mainOpts) mainOpts.labels = state.showLabels;
        if (dayOpts) dayOpts.labels = state.showLabels;
        if (mapMain) mapMain.invalidate(); if (mapDay) mapDay.invalidate();
      }
      if (state.page === "mine") refreshMine(); else if (state.page === "privacy") refreshPrivacy();
      toast(tg.querySelector(".t").textContent + (state[k] ? " 已开启" : " 已关闭"));
      return;
    }

    var swatch = t.closest("[data-color]");
    if (swatch) {
      state.trackColor = swatch.getAttribute("data-color");
      applyTrack();
      if (mapMain) mapMain.invalidate(); if (mapDay) mapDay.invalidate();
      save(); refreshMine();
      toast("轨迹颜色：" + TRACK[state.trackColor].name);
      return;
    }

    var themeBtn = t.closest('[data-seg="theme"] button[data-v]');
    if (themeBtn) { state.theme = themeBtn.getAttribute("data-v"); applyTheme(); save(); refreshMine(); return; }

    var precBtn = t.closest('[data-seg="precise"] button[data-v]');
    if (precBtn) { state.precise = precBtn.getAttribute("data-v") === "high"; save(); refreshMine(); toast(state.precise ? "记录精度：高精度" : "记录精度：省电"); return; }

    var permBtn = t.closest("[data-perm] button[data-v]");
    if (permBtn) {
      var pv = permBtn.getAttribute("data-v");
      state.perm = pv === "denied" ? "denied" : "granted";
      state.consentSeen = true;
      save(); syncMap(); renderMapState();
      toast(pv === "denied" ? "已拒绝定位权限" : "定位权限已允许");
      setTimeout(refreshPrivacy, 0);
      return;
    }

    var goto = t.closest("[data-goto]");
    if (goto) { go(goto.getAttribute("data-goto")); return; }

    if (t.closest("#banner-open")) { go("privacy"); return; }
    if (t.closest("#banner-close")) { var bn = $(".banner"); if (bn) bn.remove(); return; }
    if (t.closest("#map-retry")) {
      state.mapStatus = "loading"; renderMapState();
      setTimeout(function () { state.mapStatus = "ready"; renderMapState(); syncMap(); renderOverview(); toast("底图已重新加载"); }, 900);
      return;
    }
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
  else init();
})();
