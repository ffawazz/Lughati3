/* =====================================================================
   المحرك: أدوات، حفظ التقدّم، النطق (جسر أندرويد + المتصفح)،
   لوحة الكتابة بالقلم، وفاحص الخط المتسامح
   ===================================================================== */
'use strict';

/* ---------- أدوات عامة ---------- */
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));
const AR_DIGITS = n => String(n).replace(/\d/g, d => '٠١٢٣٤٥٦٧٨٩'[d]);
const MARKS_RE = /[ً-ٰٟـۖ-ۭ]/g;
const strip = s => String(s).replace(MARKS_RE, '');
const esc = s => String(s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const rnd = n => Math.floor(Math.random() * n);
const pick = a => a[rnd(a.length)];
function shuffle(a) { a = a.slice(); for (let i = a.length - 1; i > 0; i--) { const j = rnd(i + 1); [a[i], a[j]] = [a[j], a[i]]; } return a; }
function uniq(a) { return Array.from(new Set(a)); }
const sleep = ms => new Promise(r => setTimeout(r, ms));
// {{كلمة}} ← كلمة بالأحمر
const redify = s => esc(s).replace(/\{\{(.+?)\}\}/g, '<span class="red">$1</span>');
const plainSpeak = s => String(s).replace(/\{\{|\}\}/g, '').replace(/_{2,}/g, ' ... ').replace(/ﷺ/g, 'صلى الله عليه وسلم').replace(/←/g, ' ').replace(/[◌]/g, ' ');

/* ---------- حفظ التقدّم محلياً ---------- */
const Store = {
  key: 'lughati3.v1',
  data: null,
  fresh() {
    return { stars: 0, cur: 0, focus: 0, scope: 'lesson', lessons: {}, set: { finger: false, rate: 0.85, who: 'n', penSeen: false, sound: true } };
  },
  load() {
    try { const j = localStorage.getItem(this.key); this.data = j ? JSON.parse(j) : this.fresh(); } catch (e) { this.data = this.fresh(); }
    const f = this.fresh();
    this.data = Object.assign(f, this.data); this.data.set = Object.assign(f.set, this.data.set || {});
    return this.data;
  },
  save() { try { localStorage.setItem(this.key, JSON.stringify(this.data)); } catch (e) { /* قد يكون التخزين غير متاح */ } },
  lesson(id) { return (this.data.lessons[id] = this.data.lessons[id] || { stars: 0, writing: 0, reading: 0, review: 0, manual: false }); },
  isDone(id) { const l = this.data.lessons[id]; return !!l && (l.manual || (l.writing > 0 && l.reading > 0 && l.review > 0)); },
  addStars(id, n) { if (n > 0) { this.data.stars += n; this.lesson(id).stars += n; this.save(); } },
  reset() { this.data = this.fresh(); this.save(); },
};

/* ---------- المديح والتحية ---------- */
const Praise = {
  ok: ['أَحْسَنْتَ !', 'مُمْتَازٌ !', 'مَا شَاءَ اللَّهُ ، رَائِعٌ !', 'عَمَلٌ جَمِيلٌ !', 'بَارَكَ اللَّهُ فِيكَ !', 'إِجَابَةٌ صَحِيحَةٌ !', 'أَنْتَ نَجْمٌ لَامِعٌ !'],
  okG: { b: ['أَحْسَنْتَ يَا بَطَلُ !', 'مُمْتَازٌ يَا بَطَلُ !'], g: ['أَحْسَنْتِ يَا بَطَلَةُ !', 'مُمْتَازَةٌ يَا نَجْمَةُ !'] },
  try: ['حَاوِلْ مَرَّةً أُخْرَى ، أَنْتَ تَسْتَطِيعُ !', 'قَرِيبٌ جِدًّا ! جَرِّبْ مَرَّةً أُخْرَى', 'لَا بَأْسَ ، الْمُحَاوَلَةُ تَصْنَعُ الْمُتَفَوِّقِينَ !', 'تَعَلَّمْنَا شَيْئًا جَدِيدًا ، هَيَّا نُكْمِلْ !'],
  end: [[5, 'مَا شَاءَ اللَّهُ ! جَوْلَةٌ كَامِلَةٌ بِلَا أَخْطَاءٍ !'], [4, 'رَائِعٌ جِدًّا ! كِدْتَ تَصِلُ إِلَى الْكَمَالِ !'], [3, 'أَحْسَنْتَ ! أَنْتَ تَتَقَدَّمُ يَوْمًا بَعْدَ يَوْمٍ !'], [0, 'لَا بَأْسَ ! كُلُّ مُحَاوَلَةٍ تُقَرِّبُكَ مِنَ النَّجَاحِ ، جَرِّبْ مَرَّةً أُخْرَى !']],
  okMsg() { const w = Store.data.set.who; const pool = this.ok.concat(w === 'b' ? this.okG.b : w === 'g' ? this.okG.g : []); return pick(pool); },
  tryMsg() { return pick(this.try); },
  endMsg(stars, total) { const sc = total ? Math.round(stars * 5 / total) : 0; for (const [t, m] of this.end) if (sc >= t) return m; return this.end[3][1]; },
  greet() {
    const h = new Date().getHours(); const w = Store.data.set.who;
    const hi = h < 12 ? 'صَبَاحُ الْخَيْرِ' : h < 18 ? 'مَرْحَبًا' : 'مَسَاءُ الْخَيْرِ';
    const t = w === 'b' ? 'يَا بَطَلُ' : w === 'g' ? 'يَا بَطَلَةُ' : 'يَا صَدِيقِي الذَّكِيَّ';
    return `${hi} ${t} ! أَنْتَ قَادِرٌ عَلَى كُلِّ شَيْءٍ ، هَيَّا نَتَعَلَّمْ وَنَسْتَمْتِعْ !`;
  },
};

/* ---------- النطق: جسر أندرويد ثم المتصفح ---------- */
const TTS = {
  seq: 0, waits: {}, voice: null,
  get native() { return typeof window.AndroidTTS !== 'undefined' && window.AndroidTTS && typeof window.AndroidTTS.speak === 'function'; },
  status() {
    if (this.native) { try { return JSON.parse(window.AndroidTTS.status()); } catch (e) { return { engine: 'android', ready: false, arabic: false }; } }
    const ok = 'speechSynthesis' in window;
    let ar = false; try { ar = ok && speechSynthesis.getVoices().some(v => /^ar/i.test(v.lang)); } catch (e) { /* لا شيء */ }
    return { engine: ok ? 'browser' : 'none', ready: ok, arabic: ar };
  },
  stop() {
    this.seq++; // يُبطل الانتظارات الجارية
    Object.keys(this.waits).forEach(k => { const w = this.waits[k]; delete this.waits[k]; clearTimeout(w.t); w.res(false); });
    try { if (this.native) window.AndroidTTS.stop(); else if ('speechSynthesis' in window) speechSynthesis.cancel(); } catch (e) { /* لا شيء */ }
  },
  // يعيد وعداً يُحلّ عند انتهاء الكلام (أو بعد مهلة تقديرية حتى لا يتوقف البرنامج)
  speak(text, o = {}) {
    const S = Store.data.set; if (!S.sound && !o.force) return Promise.resolve(false);
    const t = plainSpeak(text).trim(); if (!t) return Promise.resolve(false);
    if (!o.keep) this.stop();
    const rate = o.rate || S.rate || 0.85; const id = 'u' + (++this.seq);
    return new Promise(res => {
      const est = 900 + t.length * 105 / rate;
      const w = { res, t: setTimeout(() => { if (this.waits[id]) { delete this.waits[id]; res(true); } }, est + 2500) };
      this.waits[id] = w;
      try {
        if (this.native) { window.AndroidTTS.speak(t, rate, id); return; }
        if (!('speechSynthesis' in window)) { setTimeout(() => this.__end(id), est); return; }
        const u = new SpeechSynthesisUtterance(t); u.lang = 'ar-SA'; u.rate = rate;
        const vs = speechSynthesis.getVoices(); const v = vs.find(x => /^ar[-_]SA/i.test(x.lang)) || vs.find(x => /^ar/i.test(x.lang)); if (v) u.voice = v;
        u.onend = () => this.__end(id); u.onerror = () => this.__end(id);
        speechSynthesis.speak(u);
      } catch (e) { setTimeout(() => this.__end(id), est); }
    });
  },
  __end(id) { const w = this.waits[id]; if (w) { delete this.waits[id]; clearTimeout(w.t); w.res(true); } },
  settings() { try { if (this.native && window.AndroidTTS.openSettings) window.AndroidTTS.openSettings(); } catch (e) { /* لا شيء */ } },
};
// يستدعيها الجسر الأصلي (Java) عند انتهاء النطق
window.__ttsEvent = function (type, id) { if (type === 'end' || type === 'error') TTS.__end(id); };

/* =====================================================================
   لوحة الكتابة بالقلم
   - ضغط القلم يغيّر سماكة الخط
   - بعد استخدام القلم تُتجاهل لمسات اليد (رفض راحة اليد)
   - طرف الممحاة في القلم يمحو
   ===================================================================== */
class InkCanvas {
  constructor(wrap, opt = {}) {
    this.wrap = wrap; this.opt = opt;
    this.under = document.createElement('canvas'); this.under.className = 'cv-under';
    this.ink = document.createElement('canvas'); this.ink.className = 'cv-ink';
    wrap.appendChild(this.under); wrap.appendChild(this.ink);
    this.strokes = []; this.history = []; this.tool = 'pen'; this.active = null; this.guide = null;
    this.color = '#1b2a6b'; this.onchange = opt.onchange || (() => {});
    this.stats = { pen: 0, touch: 0, mouse: 0, ignoredTouch: 0, erased: 0 };
    this._bind(); this.resize();
    if (typeof ResizeObserver !== 'undefined') { this.ro = new ResizeObserver(() => this.resize()); this.ro.observe(wrap); }
  }
  destroy() { if (this.ro) this.ro.disconnect(); }
  static widthFor(p) { return 1.6 + Math.max(0, Math.min(1, p)) * 8; }
  resize() {
    const r = this.wrap.getBoundingClientRect(); const dpr = Math.max(1, window.devicePixelRatio || 1);
    this.w = Math.max(50, Math.round(r.width)); this.h = Math.max(50, Math.round(r.height)); this.dpr = dpr;
    [this.under, this.ink].forEach(c => { c.width = this.w * dpr; c.height = this.h * dpr; c.style.width = this.w + 'px'; c.style.height = this.h + 'px'; });
    this.redrawUnder(); this.redraw();
  }
  setGuide(fn) { this.guide = fn; this.redrawUnder(); }
  redrawUnder() {
    const c = this.under.getContext('2d'); c.setTransform(this.dpr, 0, 0, this.dpr, 0, 0); c.clearRect(0, 0, this.w, this.h);
    // سطور الكتابة
    c.strokeStyle = '#c9d6ff'; c.lineWidth = 2; c.setLineDash([]);
    const n = this.opt.lines || 3;
    for (let i = 1; i <= n; i++) { const y = (this.h * i) / (n + 1); c.beginPath(); c.moveTo(14, y); c.lineTo(this.w - 14, y); c.stroke(); }
    if (this.guide) this.guide(c, this.w, this.h);
  }
  _bind() {
    const cv = this.ink;
    cv.style.touchAction = 'none';
    ['contextmenu', 'selectstart', 'dragstart'].forEach(ev => cv.addEventListener(ev, e => e.preventDefault()));
    cv.addEventListener('pointerdown', e => this._down(e));
    cv.addEventListener('pointermove', e => this._move(e));
    cv.addEventListener('pointerup', e => this._up(e));
    cv.addEventListener('pointercancel', e => this._up(e));
  }
  _pos(e) { const r = this.ink.getBoundingClientRect(); return [e.clientX - r.left, e.clientY - r.top]; }
  _pressure(e) { let p = e.pressure; if (!(p > 0)) p = 0.5; if (e.pointerType === 'mouse') p = 0.5; return p; }
  _isEraser(e) { return this.tool === 'eraser' || (e.pointerType === 'pen' && ((e.buttons & 32) === 32 || e.button === 5)); }
  _down(e) {
    const S = Store.data.set;
    if (e.pointerType === 'pen') { this.stats.pen++; if (!S.penSeen) { S.penSeen = true; Store.save(); } }
    else if (e.pointerType === 'touch') {
      // رفض راحة اليد: بعد استخدام القلم لا نقبل اللمس ما لم يسمح الوالدان بالإصبع
      if (S.penSeen && !S.finger) { this.stats.ignoredTouch++; return; }
      this.stats.touch++;
    } else this.stats.mouse++;
    if (this.active) return; // مؤشر واحد فقط (يتجاهل راحة اليد أثناء الكتابة)
    e.preventDefault();
    try { this.ink.setPointerCapture(e.pointerId); } catch (er) { /* لا شيء */ }
    const [x, y] = this._pos(e);
    const eraser = this._isEraser(e);
    this.active = { id: e.pointerId, eraser, type: e.pointerType, erased: [], stroke: null };
    if (eraser) { this._erase(x, y); this.wrap.classList.add('erasing'); }
    else { const st = { pts: [[x, y, this._pressure(e)]], color: this.color }; this.active.stroke = st; this.strokes.push(st); this._dot(st); }
  }
  _move(e) {
    const a = this.active; if (!a || a.id !== e.pointerId) return;
    e.preventDefault();
    const evs = (e.getCoalescedEvents && e.getCoalescedEvents().length) ? e.getCoalescedEvents() : [e];
    for (const ev of evs) {
      const [x, y] = this._pos(ev);
      // الممحاة قد تُضغط في منتصف الحركة
      if (a.eraser) this._erase(x, y);
      else {
        const pts = a.stroke.pts; const last = pts[pts.length - 1];
        if (Math.hypot(x - last[0], y - last[1]) < 0.8) continue;
        pts.push([x, y, this._pressure(ev)]); this._seg(a.stroke, pts.length - 1);
      }
    }
  }
  _up(e) {
    const a = this.active; if (!a || a.id !== e.pointerId) return;
    this.active = null; this.wrap.classList.remove('erasing');
    if (a.eraser) { if (a.erased.length) this.history.push({ t: 'erase', s: a.erased }); }
    else { this.history.push({ t: 'add', s: a.stroke }); }
    this.onchange(this);
  }
  _erase(x, y) {
    const R = 18; const a = this.active;
    for (let i = this.strokes.length - 1; i >= 0; i--) {
      const st = this.strokes[i];
      if (st.pts.some(p => Math.hypot(p[0] - x, p[1] - y) <= R)) { this.strokes.splice(i, 1); a.erased.push({ st, i }); this.stats.erased++; }
    }
    if (a.erased.length) this.redraw();
  }
  _ctx() { const c = this.ink.getContext('2d'); c.setTransform(this.dpr, 0, 0, this.dpr, 0, 0); c.lineCap = 'round'; c.lineJoin = 'round'; return c; }
  _dot(st) { const c = this._ctx(); const p = st.pts[0]; c.fillStyle = st.color; c.beginPath(); c.arc(p[0], p[1], InkCanvas.widthFor(p[2]) / 2, 0, 7); c.fill(); }
  _seg(st, i) { const c = this._ctx(); const a = st.pts[i - 1], b = st.pts[i]; c.strokeStyle = st.color; c.lineWidth = InkCanvas.widthFor((a[2] + b[2]) / 2); c.beginPath(); c.moveTo(a[0], a[1]); c.lineTo(b[0], b[1]); c.stroke(); }
  redraw() {
    const c = this.ink.getContext('2d'); c.setTransform(this.dpr, 0, 0, this.dpr, 0, 0); c.clearRect(0, 0, this.w, this.h);
    for (const st of this.strokes) { if (st.pts.length === 1) this._dot(st); else for (let i = 1; i < st.pts.length; i++) this._seg(st, i); }
  }
  undo() {
    const h = this.history.pop(); if (!h) return;
    if (h.t === 'add') { const i = this.strokes.indexOf(h.s); if (i >= 0) this.strokes.splice(i, 1); }
    else { h.s.slice().sort((a, b) => a.i - b.i).forEach(o => this.strokes.splice(Math.min(o.i, this.strokes.length), 0, o.st)); }
    this.redraw(); this.onchange(this);
  }
  clear() { this.strokes = []; this.history = []; this.redraw(); this.onchange(this); }
  setTool(t) { this.tool = t; this.wrap.dataset.tool = t; }
  hasInk() { return this.strokes.length > 0; }
}

/* =====================================================================
   فاحص الخط المتسامح
   الفكرة: نحوّل ما كتبه الطفل وشكل الكلمة المطلوبة إلى شبكة صغيرة بعد تطبيعهما
   (الموضع والحجم لا يهمّان)، ثم نقيس:
     cov  = كم من الكلمة المطلوبة «مغطّى» بحبر الطفل
     prec = كم من حبر الطفل يقع قرب الكلمة المطلوبة
     ratio= كمية الحبر مقارنةً بالكلمة (لكشف الخربشة الكثيفة أو الخط الواحد)
   ويُجرَّب الحبر كلّه وكل مجموعة منفصلة منه (للكتابة بجانب النموذج).
   تنبيه: هذا ليس تعرّفاً على الحروف؛ هو تحقّق من «شكل الكلمة وحجم حبرها».
   ===================================================================== */
const HW = {
  H: 64, T: 6, th: { cov: 0.70, prec: 0.60, rmin: 0.40, rmax: 2.4, amin: 0.5, amax: 2.0 },
  _glyph: {},
  fontReady() { return (document.fonts && document.fonts.load) ? document.fonts.load('120px Amiri', 'ابت').catch(() => {}) : Promise.resolve(); },
  // قناع الكلمة المطلوبة (حبر الخط) بعد قصّه على حدوده وتصغيره إلى الشبكة
  glyph(text) {
    if (this._glyph[text]) return this._glyph[text];
    const fs = 120; const m = document.createElement('canvas').getContext('2d');
    m.font = `${fs}px Amiri, serif`; m.direction = 'rtl';
    const tw = Math.ceil(m.measureText(text).width) + 80;
    const c = document.createElement('canvas'); c.width = tw; c.height = 300; const x = c.getContext('2d');
    x.font = `${fs}px Amiri, serif`; x.direction = 'rtl'; x.textAlign = 'right'; x.textBaseline = 'alphabetic'; x.fillStyle = '#000';
    x.fillText(text, tw - 40, 190);
    const d = x.getImageData(0, 0, tw, 300).data; let x0 = tw, x1 = 0, y0 = 300, y1 = 0;
    for (let yy = 0; yy < 300; yy++) for (let xx = 0; xx < tw; xx++) if (d[(yy * tw + xx) * 4 + 3] > 100) { if (xx < x0) x0 = xx; if (xx > x1) x1 = xx; if (yy < y0) y0 = yy; if (yy > y1) y1 = yy; }
    if (x1 <= x0 || y1 <= y0) return (this._glyph[text] = null);
    const cw = x1 - x0 + 1, ch = y1 - y0 + 1; const Hg = this.H; const Wg = Math.max(24, Math.min(420, Math.round(Hg * cw / ch)));
    const g = document.createElement('canvas'); g.width = Wg; g.height = Hg; const gx = g.getContext('2d');
    gx.drawImage(c, x0, y0, cw, ch, 0, 0, Wg, Hg);
    const gd = gx.getImageData(0, 0, Wg, Hg).data; const mask = new Uint8Array(Wg * Hg); let n = 0;
    for (let i = 0; i < Wg * Hg; i++) if (gd[i * 4 + 3] > 70) { mask[i] = 1; n++; }
    return (this._glyph[text] = { W: Wg, H: Hg, mask, n, aspect: cw / ch });
  },
  // مسافة كل خلية إلى أقرب خلية مشغولة (تقريب شامفر)
  dist(mask, W, H) {
    const INF = 1e9; const d = new Float32Array(W * H);
    for (let i = 0; i < d.length; i++) d[i] = mask[i] ? 0 : INF;
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) { const i = y * W + x; let v = d[i];
      if (x > 0) v = Math.min(v, d[i - 1] + 1); if (y > 0) { v = Math.min(v, d[i - W] + 1); if (x > 0) v = Math.min(v, d[i - W - 1] + 1.414); if (x < W - 1) v = Math.min(v, d[i - W + 1] + 1.414); } d[i] = v; }
    for (let y = H - 1; y >= 0; y--) for (let x = W - 1; x >= 0; x--) { const i = y * W + x; let v = d[i];
      if (x < W - 1) v = Math.min(v, d[i + 1] + 1); if (y < H - 1) { v = Math.min(v, d[i + W] + 1); if (x < W - 1) v = Math.min(v, d[i + W + 1] + 1.414); if (x > 0) v = Math.min(v, d[i + W - 1] + 1.414); } d[i] = v; }
    return d;
  },
  // نقاط الحبر (كثيفة) من الضربات؛ الضربة ذات النقطة الواحدة تُعدّ نقطة
  points(strokes) {
    const out = [];
    for (const s of strokes) { const p = s.pts || s; if (p.length === 1) { out.push([p[0][0], p[0][1]]); continue; }
      for (let i = 1; i < p.length; i++) { const a = p[i - 1], b = p[i]; const L = Math.hypot(b[0] - a[0], b[1] - a[1]); const n = Math.max(1, Math.ceil(L / 1.5)); for (let k = 0; k <= n; k++) out.push([a[0] + (b[0] - a[0]) * k / n, a[1] + (b[1] - a[1]) * k / n]); } }
    return out;
  },
  inkLength(strokes) { let L = 0; for (const s of strokes) { const p = s.pts || s; for (let i = 1; i < p.length; i++) L += Math.hypot(p[i][0] - p[i - 1][0], p[i][1] - p[i - 1][1]); } return L; },
  // مقارنة مجموعة ضربات بقناع هدف
  score(pts, gl) {
    const { W, H } = gl; let x0 = 1e9, x1 = -1e9, y0 = 1e9, y1 = -1e9;
    for (const p of pts) { if (p[0] < x0) x0 = p[0]; if (p[0] > x1) x1 = p[0]; if (p[1] < y0) y0 = p[1]; if (p[1] > y1) y1 = p[1]; }
    let bw = x1 - x0, bh = y1 - y0; if (bw < 1) bw = 1; const flat = bh < bw * 0.04; if (bh < 1) bh = 1;
    const um = new Uint8Array(W * H);
    for (const p of pts) { const gx = Math.round((p[0] - x0) / bw * (W - 1)); const gy = flat ? Math.round(H / 2) : Math.round((p[1] - y0) / bh * (H - 1)); um[gy * W + gx] = 1; }
    const dU = this.dist(um, W, H), dT = this.dist(gl.mask, W, H);
    let cov = 0, prec = 0, nu = 0, area = 0;
    for (let i = 0; i < W * H; i++) { if (gl.mask[i] && dU[i] <= this.T) cov++; if (um[i]) { nu++; if (dT[i] <= this.T) prec++; } if (dU[i] <= 1.5) area++; }
    return { cov: cov / gl.n, prec: nu ? prec / nu : 0, ratio: area / gl.n, flat, aspect: (bw / bh) / gl.aspect };
  },
  // يجمع الضربات في مجموعات منفصلة (للكتابة بجانب النموذج أو فوقه ثم نسخه)
  groups(strokes) {
    const g = [strokes];
    for (const axis of [0, 1]) {
      const bb = strokes.map(s => { const p = s.pts || s; let a = 1e9, b = -1e9; p.forEach(q => { a = Math.min(a, q[axis]); b = Math.max(b, q[axis]); }); return { s, a, b }; }).sort((u, v) => u.a - v.a);
      if (bb.length < 2) continue; const total = Math.max(...bb.map(o => o.b)) - Math.min(...bb.map(o => o.a)); if (total < 40) continue;
      let reach = bb[0].b, best = 0, at = -1;
      for (let i = 1; i < bb.length; i++) { const gap = bb[i].a - reach; if (gap > best) { best = gap; at = i; } reach = Math.max(reach, bb[i].b); }
      if (at > 0 && best > total * 0.12) { g.push(bb.slice(0, at).map(o => o.s)); g.push(bb.slice(at).map(o => o.s)); }
    }
    return g;
  },
  /**
   * strokes: ضربات الطفل. targets: نصوص مقبولة (الخط يُجرَّب مع الحركات وبدونها).
   * يعيد {ok, reason, cov, prec, ratio}
   */
  check(strokes, targets) {
    const live = strokes.filter(s => (s.pts || s).length);
    const all = this.points(live); const len = this.inkLength(live);
    if (live.length === 0 || all.length < 4 || (len < 14 && live.length < 3)) return { ok: false, reason: 'blank', cov: 0, prec: 0, ratio: 0 };
    let best = null;
    const variants = uniq(targets.flatMap(t => [strip(t), t])).map(t => this.glyph(t)).filter(Boolean);
    for (const grp of this.groups(live)) {
      const pts = this.points(grp); if (pts.length < 4) continue;
      for (const gl of variants) {
        const r = this.score(pts, gl); const q = Math.min(r.cov, r.prec) - Math.abs(Math.log(Math.max(r.ratio, 0.01))) * 0.05;
        const th = this.th; r.ok = r.cov >= th.cov && r.prec >= th.prec && r.ratio >= th.rmin && r.ratio <= th.rmax && (r.flat || (r.aspect >= th.amin && r.aspect <= th.amax)); r.q = q;
        if (!best || (r.ok && !best.ok) || (r.ok === best.ok && q > best.q)) best = r;
      }
    }
    if (!best) return { ok: false, reason: 'blank', cov: 0, prec: 0, ratio: 0 };
    if (best.ok) { best.reason = 'ok'; return best; }
    if (best.ratio > this.th.rmax) best.reason = 'blob';
    else if (best.flat || best.ratio < this.th.rmin) best.reason = 'line';
    else best.reason = 'miss';
    return best;
  },
};
// يظهر في الاختبارات
window.__hw = { HW, InkCanvas };
