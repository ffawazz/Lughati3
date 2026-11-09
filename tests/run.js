/* اختبارات Playwright (Chromium بلا واجهة): node tests/run.js
   المتغيّر SHOTS = مجلد لحفظ لقطات الشاشة */
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const URL = 'file://' + path.resolve(__dirname, '..', 'index.html');
const SHOTS = process.env.SHOTS || '';
let pass = 0, fail = 0; const fails = [];
const T = (ok, name, extra = '') => { if (ok) { pass++; console.log('  ✓', name, extra); } else { fail++; fails.push(name); console.log('  ✗ FAIL', name, extra); } };
const shot = async (p, n) => { if (SHOTS) { fs.mkdirSync(SHOTS, { recursive: true }); await p.screenshot({ path: path.join(SHOTS, n + '.png') }); } };

async function fresh(browser, vp = { width: 1280, height: 800 }, init) {
  const ctx = await browser.newContext({ viewport: vp, hasTouch: true });
  const page = await ctx.newPage(); const errs = [];
  page.on('pageerror', e => errs.push('PAGEERR ' + e.message)); page.on('console', m => { if (m.type() === 'error') errs.push('CONSOLE ' + m.text()); });
  if (init) await page.addInitScript(init);
  await page.goto(URL); await page.waitForFunction(() => window.__app && document.fonts.status === 'loaded'); await page.waitForTimeout(150);
  page.errs = errs; return page;
}
const setCur = (p, cur, scope = 'lesson') => p.evaluate(([c, s]) => { const S = __app.Store.data; S.cur = c; S.focus = c; S.scope = s; __app.Store.save(); __app.App.stack = [{ name: 'home', p: {} }]; __app.render(); }, [cur, scope]);

/* ---- توليد ضربات محاكاة من هيكل الخط ---- */
const SIM = `
window.__sim = {
  skeleton(text, font, size) {
    const c = document.createElement('canvas'); const x = c.getContext('2d'); x.font = size + 'px ' + font; x.direction = 'rtl';
    const tw = Math.ceil(x.measureText(text).width) + 40; c.width = tw; c.height = Math.round(size * 2.2);
    x.font = size + 'px ' + font; x.direction = 'rtl'; x.textAlign = 'right'; x.fillStyle = '#000'; x.fillText(text, tw - 20, size * 1.4);
    const d = x.getImageData(0, 0, c.width, c.height).data; const W = c.width, H = c.height; const g = new Uint8Array(W * H);
    for (let i = 0; i < W * H; i++) g[i] = d[i * 4 + 3] > 110 ? 1 : 0;
    // Zhang-Suen
    let ch = true; const idx = (x, y) => y * W + x;
    while (ch) { ch = false;
      for (let step = 0; step < 2; step++) { const rm = [];
        for (let y = 1; y < H - 1; y++) for (let xx = 1; xx < W - 1; xx++) { if (!g[idx(xx, y)]) continue;
          const p2 = g[idx(xx, y - 1)], p3 = g[idx(xx + 1, y - 1)], p4 = g[idx(xx + 1, y)], p5 = g[idx(xx + 1, y + 1)], p6 = g[idx(xx, y + 1)], p7 = g[idx(xx - 1, y + 1)], p8 = g[idx(xx - 1, y)], p9 = g[idx(xx - 1, y - 1)];
          const B = p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9; if (B < 2 || B > 6) continue;
          const seq = [p2, p3, p4, p5, p6, p7, p8, p9, p2]; let A = 0; for (let k = 0; k < 8; k++) if (!seq[k] && seq[k + 1]) A++; if (A !== 1) continue;
          if (step === 0) { if (p2 * p4 * p6 !== 0 || p4 * p6 * p8 !== 0) continue; } else { if (p2 * p4 * p8 !== 0 || p2 * p6 * p8 !== 0) continue; }
          rm.push(idx(xx, y)); }
        rm.forEach(i => g[i] = 0); if (rm.length) ch = true; } }
    const pts = []; let x0 = 1e9, y0 = 1e9, x1 = -1, y1 = -1;
    for (let y = 0; y < H; y++) for (let xx = 0; xx < W; xx++) if (g[idx(xx, y)]) { pts.push([xx, y]); x0 = Math.min(x0, xx); y0 = Math.min(y0, y); x1 = Math.max(x1, xx); y1 = Math.max(y1, y); }
    // نحوّل الهيكل إلى خطوط متصلة بالمشي إلى أقرب جار
    const left = new Map(pts.map(p => [p[0] + ',' + p[1], p])); const strokes = [];
    while (left.size) { let cur = left.values().next().value; const st = [cur]; left.delete(cur[0] + ',' + cur[1]);
      for (;;) { let nxt = null; for (let dx = -2; dx <= 2 && !nxt; dx++) for (let dy = -2; dy <= 2; dy++) { const k = (cur[0] + dx) + ',' + (cur[1] + dy); if (left.has(k)) { nxt = left.get(k); break; } }
        if (!nxt) break; st.push(nxt); left.delete(nxt[0] + ',' + nxt[1]); cur = nxt; }
      strokes.push(st); }
    return { strokes, x0, y0, x1, y1, w: x1 - x0 + 1, h: y1 - y0 + 1 };
  },
  place(sk, sx, sy, ox, oy, jit) { // يحوّل الهيكل إلى ضربات بنفس صيغة لوحة الكتابة
    const J = () => (jit ? (Math.random() - 0.5) * 2 * jit : 0);
    return sk.strokes.map(st => ({ pts: st.map(p => [ox + (p[0] - sk.x0) * sx + J(), oy + (p[1] - sk.y0) * sy + J(), 0.5]) }));
  },
  blob(w, h, ox, oy) { const pts = []; const step = 7; let dir = 1; for (let y = 0; y <= h; y += step) { for (let x = 0; x <= w; x += 4) pts.push([ox + (dir > 0 ? x : w - x), oy + y + (x % 8 < 4 ? 0 : 3), 0.5]); dir *= -1; } return [{ pts }]; },
  line(w, h, ox, oy) { const pts = []; for (let x = 0; x <= w; x += 3) pts.push([ox + x, oy + h / 2 + Math.sin(x / 40) * h * 0.35, 0.5]); return [{ pts }]; },
};`;

(async () => {
  const browser = await chromium.launch();

  /* ========== 1) كل الشاشات بلا أخطاء ========== */
  console.log('\n[1] الشاشات');
  let p = await fresh(browser);
  T(p.errs.length === 0, 'تحميل الصفحة بلا أخطاء', p.errs.join('|'));
  await shot(p, '01-home');
  const screens = [
    ['lessons', () => p.evaluate(() => __app.go('lessons'))], ['writeMenu', () => p.evaluate(() => __app.go('writeMenu'))], ['readMenu', () => p.evaluate(() => __app.go('readMenu'))],
    ['quickMenu', () => p.evaluate(() => __app.go('quickMenu'))], ['gate', () => p.evaluate(() => __app.go('gate'))], ['parent', () => p.evaluate(() => __app.go('parent'))],
    ['along', () => p.evaluate(() => __app.go('along'))],
  ];
  await setCur(p, 7, 'all');
  for (const [n, f] of screens) { await f(); await p.waitForTimeout(120); const txt = await p.evaluate(() => document.getElementById('screen').innerText.length); T(txt > 10, 'شاشة ' + n + ' تُعرض', ''); await shot(p, '02-' + n); }
  T(p.errs.length === 0, 'لا أخطاء بعد زيارة كل القوائم', p.errs.join('|'));

  // كل الدروس: شاشة اقرأ معي + كل جولات الكتابة والمراجعة والقراءة
  for (let i = 0; i < 8; i++) {
    await setCur(p, i, 'lesson');
    const r = await p.evaluate(() => {
      const out = {}; const A = __app;
      ['trace', 'copy', 'dict', 'letters', 'short', 'mixed'].forEach(m => out['w_' + m] = A.genWrite(m).length);
      out.fill = A.genFill(5).length; out.order = A.genOrder(5).length; out.hear = A.genHear(5).length; out.build = A.genBuild(5).length; out.odd = A.genOdd(5).length;
      out.rules = A.genRules(5).length; out.concepts = A.availableConcepts().length;
      return out;
    });
    const missing = Object.entries(r).filter(([k, v]) => v === 0).map(([k]) => k);
    console.log('   درس', i + 1, JSON.stringify(r)); T(r.rules === 5 && r.hear >= 3 && r.build >= 3 && r.fill >= 3, 'درس ' + (i + 1) + ': ٥ أسئلة قواعد و٣+ للباقي', missing.length ? 'ناقص: ' + missing.join(',') : '');
  }
  T(p.errs.length === 0, 'لا أخطاء أثناء التوليد', p.errs.join('|'));

  /* تشغيل جولة كاملة بالنقر */
  async function playRound(page, mode) {
    const n = await page.evaluate(() => __app.App.round.items.length);
    for (let i = 0; i < n; i++) {
      await page.waitForSelector('#qcard'); const kind = await page.evaluate(() => { const R = __app.App.round; return R.items[R.i].kind; });
      if (kind === 'mc') { const a = await page.evaluate(() => { const R = __app.App.round; return R.items[R.i].ans; }); if (mode === 'wrong') { const w = (a + 1) % 3; await page.click(`.choice[data-k="${w}"]`); const w2 = (a + 2) % 3; await page.click(`.choice[data-k="${w2}"]`); } else await page.click(`.choice[data-k="${a}"]`); }
      else { const order = await page.evaluate(() => { const R = __app.App.round; const q = R.items[R.i]; const arr = q.kind === 'build' ? q.letters : q.words; const tg = q.target; const used = new Set(); return tg.map(t => { const k = arr.findIndex((x, j) => x === t && !used.has(j)); used.add(k); return k; }); });
        for (const k of order) await page.click(`#bank button[data-k="${k}"]`); }
      await page.waitForSelector('#next'); await page.click('#next');
    }
    await page.waitForSelector('.results');
  }
  for (const [kind, label, fn] of [['rules', 'قواعد', () => __app.go('round', { title: 't', items: __app.genRules(5), section: 'review' })], ['hear', 'اسمع', () => __app.go('round', { title: 't', items: __app.genHear(5), section: 'review' })], ['build', 'حروف', () => __app.go('round', { title: 't', items: __app.genBuild(5), section: 'review' })], ['odd', 'شاذة', () => __app.go('round', { title: 't', items: __app.genOdd(5), section: 'review' })], ['fill', 'أكمل', () => __app.go('round', { title: 't', items: __app.genFill(5), section: 'reading' })], ['order', 'رتب', () => __app.go('round', { title: 't', items: __app.genOrder(5), section: 'reading' })]]) {
    await setCur(p, 7, 'all'); await p.evaluate(fn); await p.waitForSelector('#qcard'); if (kind === 'rules' || kind === 'hear') await shot(p, '03-round-' + kind);
    const choices = await p.evaluate(() => document.querySelectorAll('.choice').length);
    if (kind === 'rules' || kind === 'hear' || kind === 'odd' || kind === 'fill') T(choices === 3, label + ': ثلاثة اختيارات', 'n=' + choices);
    const items = await p.evaluate(() => __app.App.round.items.length); T(items === 5, label + ': خمسة أسئلة', 'n=' + items);
    await playRound(p, 'right'); const st = await p.evaluate(() => document.querySelectorAll('.bigstars span.on').length); T(st === 5, label + ': ٥ نجوم عند الإجابات الصحيحة', 'stars=' + st); if (kind === 'build') await shot(p, '03-results');
  }
  // إجابات خاطئة: لا نجوم، وتظهر الإجابة الصحيحة
  await setCur(p, 7, 'all'); await p.evaluate(() => __app.go('round', { title: 't', items: __app.genRules(5), section: 'review' })); await playRound(p, 'wrong');
  T((await p.evaluate(() => document.querySelectorAll('.bigstars span.on').length)) === 0, 'إجابات خاطئة بلا نجوم');
  T(p.errs.length === 0, 'لا أخطاء في كل أنواع الجولات', p.errs.join('|'));

  // كل مولّد قواعد على حدة يعمل لدى الدرس ٨ (وفق ما هو متاح)
  const per = await p.evaluate(() => { const o = {}; __app.availableConcepts().forEach(c => { const q = __app.genRules(3, c); o[c] = q.length; }); return o; });
  console.log('   مفاهيم الدرس ٨:', JSON.stringify(per)); T(Object.values(per).every(v => v === 3) && Object.keys(per).length >= 12, 'كل مفهوم متاح يُنتج ٣ أسئلة', Object.keys(per).length + ' مفهوماً');
  // لقطات المفاهيم
  await p.evaluate(() => __app.go('round', { title: 'x', items: __app.genRules(5, 'shadda'), section: 'review' })); await shot(p, '03-round-shadda');

  /* ========== 2) الاقتصار على ما تعلّمناه ========== */
  console.log('\n[2] الاقتصار على الدروس السابقة');
  p = await fresh(browser);
  const gate = await p.evaluate(() => {
    const tok = s => strip(String(s)).replace(/[^\s]*◌[^\s]*/g, ' ').replace(/[^ء-ي\s]/g, ' ').split(/\s+/).filter(t => t.length > 1);
    const textOf = l => JSON.stringify([l.text, l.vocab, l.words, l.ant, l.plural, l.madd, l.tense, l.tafdeel, l.nakira, l.taajjub, l.hamza, l.fail, l.question, l.style, l.stylenames, l.fill, l.mcq, l.order, l.short, l.dict, l.copy, l.qwords]);
    const toks = LESSONS.map(l => new Set(tok(textOf(l)))); const res = [];
    const generic = new Set(tok(JSON.stringify(Object.values(CONCEPTS).map(c => c.name)) + ' اسمع ثم اختر الكلمة التي سمعتها رتب الحروف لتكون كلمة جملة مفيدة الكلمات أكمل الجملة أي كلمة تختلف عن أختيها الآخرين نوع التنوين مفرد ضد معنى جمع حرف المد شدة عليه الفعل ماض مضارع أمر الاستفهام الجواب فعل اسم الفاعل المفعول المؤنث تقع هكذا قمرية شمسية أصلية تنطق تنطق لا هي هو وهي أما نحذف وننون نزيد التنوين نكرة معرفة تنوين آخر ياء ألف تاء مربوطة مفتوحة وصل قطع متوسطة هذه فيها أل حول الضم الفتح الكسر الاستفهام أداة المناسبة الصحيح الصحيحة النهاية تكتب هكذا ونهايتها تسقط وسط الكلام تنطق دائما فوق تحت الأسلوب أسلوب أي'));
    for (let cur = 0; cur < 8; cur++) {
      const earlier = new Set(); for (let i = 0; i <= cur; i++) toks[i].forEach(t => earlier.add(t));
      const later = new Set(); for (let i = cur + 1; i < 8; i++) toks[i].forEach(t => { if (!earlier.has(t) && !generic.has(t)) later.add(t); });
      const S = __app.Store.data; S.cur = cur; S.focus = cur; const bad = new Map(); let total = 0;
      for (const scope of ['lesson', 'all']) { S.scope = scope;
        for (let r = 0; r < 25; r++) {
          const qs = [].concat(__app.genRules(5), __app.genHear(5), __app.genBuild(5), __app.genOdd(5), __app.genFill(5), __app.genOrder(5));
          const ws = [].concat(...['trace', 'copy', 'dict', 'letters', 'short', 'mixed'].map(m => __app.genWrite(m)));
          const strs = qs.map(q => [q.prompt, q.speak, q.sub, q.hint, q.clue, (q.choices || []).join(' '), (q.target || []).join(' '), (q.words || []).join(' '), (q.show || '')].join(' ')).concat(ws.map(w => [w.text, w.q, w.shown].join(' ')));
          total += strs.length; strs.forEach(s => tok(s).forEach(t => { if (later.has(t) && !bad.has(t)) bad.set(t, s.replace(/\s+/g, ' ').slice(0, 70)); })); } }
      res.push({ cur, total, bad: Array.from(bad).slice(0, 6).map(([t, c]) => t + ' ⟸ ' + c) });
    }
    return res;
  });
  gate.forEach(r => T(r.bad.length === 0, `عند الدرس ${r.cur + 1}: لا كلمات من دروس لاحقة (فُحص ${r.total} عنصراً)`, r.bad.join(' ؛ ')));
  const lockCheck = await p.evaluate(() => { const S = __app.Store.data; S.cur = 0; S.focus = 0; S.scope = 'all'; return { c: __app.availableConcepts().sort(), n: __app.LESSONS.length }; });
  T(!lockCheck.c.includes('fail') && !lockCheck.c.includes('hamza') && !lockCheck.c.includes('tafdeel') && !lockCheck.c.includes('taajjub') && !lockCheck.c.includes('nakira'), 'مفاهيم الدروس اللاحقة مقفلة عند الدرس ١', lockCheck.c.join(','));

  /* ========== 3) جسر النطق ========== */
  console.log('\n[3] جسر النطق');
  const mock = `window.__calls=[];window.AndroidTTS={speak:function(t,r,id){window.__calls.push({t:t,r:r,id:id});setTimeout(function(){window.__ttsEvent('end',id)},30)},stop:function(){window.__calls.push({stop:1})},status:function(){return JSON.stringify({engine:'android',ready:true,arabic:true})},openSettings:function(){window.__calls.push({settings:1})}};`;
  p = await fresh(browser, { width: 1280, height: 800 }, mock);
  await p.evaluate(() => { __app.Store.data.cur = 7; __app.Store.data.scope = 'all'; __app.go('round', { title: 't', items: __app.genRules(5), section: 'review' }); });
  await p.waitForTimeout(200);
  let calls = await p.evaluate(() => window.__calls.filter(c => c.t));
  const q0 = await p.evaluate(() => __app.App.round.items[0].speak.replace(/\{\{|\}\}/g, ''));
  T(calls.length >= 1 && /[؀-ۿ]/.test(calls[calls.length - 1].t), 'السؤال يُنطق عبر AndroidTTS تلقائياً', calls.length ? calls[calls.length - 1].t.slice(0, 40) : 'لا نداء');
  const before = calls.length; await p.click('#qcard'); await p.waitForTimeout(100);
  calls = await p.evaluate(() => window.__calls.filter(c => c.t)); T(calls.length > before, 'الضغط على نص السؤال يعيد النطق');
  T(await p.evaluate(() => TTS.native === true), 'الجسر الأصلي يُكتشف');
  await p.evaluate(() => { __app.go('along'); }); await p.click('.ptext .w >> nth=0'); await p.waitForTimeout(100);
  T((await p.evaluate(() => window.__calls.filter(c => c.t).slice(-1)[0].t)).length > 1, 'الضغط على كلمة يُنطقها');
  // القراءة المتتابعة مع تلوين الكلمة
  await p.evaluate(() => { __app.Store.data.cur = 0; __app.Store.data.focus = 0; __app.render(); __app.go('along'); });
  await p.click('#play'); await p.waitForTimeout(250);
  T(await p.evaluate(() => !!document.querySelector('.ptext .w.hl')), 'تلوين الكلمة الحالية أثناء القراءة');
  await p.click('#stop'); T(await p.evaluate(() => !document.querySelector('.ptext .w.hl')), 'زر الإيقاف يزيل التلوين');
  T(p.errs.length === 0, 'لا أخطاء مع الجسر', p.errs.join('|'));
  // بلا جسر: المتصفح (لا أصوات في بيئة الاختبار) لا يعلّق ولا يخطئ
  p = await fresh(browser);
  const t0 = Date.now(); await p.evaluate(() => TTS.speak('مَرْحَبًا')); T(Date.now() - t0 < 6000 && p.errs.length === 0, 'الرجوع إلى speechSynthesis لا يعلّق ولا يخطئ', (Date.now() - t0) + 'ms');

  /* ========== 4) القلم: ضغط، رفض راحة اليد، الممحاة ========== */
  console.log('\n[4] خصائص القلم');
  p = await fresh(browser); await p.evaluate(() => { __app.Store.data.cur = 0; __app.render(); __app.go('write', { items: __app.genWrite('copy'), mode: 'copy' }); });
  await p.waitForSelector('.cv-ink');
  const ev = (type, o) => p.evaluate(([type, o]) => { const c = document.querySelector('.cv-ink'); const r = c.getBoundingClientRect(); const e = new PointerEvent(type, Object.assign({ bubbles: true, cancelable: true, isPrimary: true, clientX: r.left + o.x, clientY: r.top + o.y }, o.init)); c.dispatchEvent(e); }, [type, o]);
  async function stroke(init, pts, press) { await ev('pointerdown', { x: pts[0][0], y: pts[0][1], init: Object.assign({}, init, { pressure: press ? press(0) : init.pressure }) });
    for (let i = 1; i < pts.length; i++) await ev('pointermove', { x: pts[i][0], y: pts[i][1], init: Object.assign({}, init, { pressure: press ? press(i / (pts.length - 1)) : init.pressure }) });
    await ev('pointerup', { x: pts[pts.length - 1][0], y: pts[pts.length - 1][1], init: Object.assign({}, init, { buttons: 0 }) }); }
  const line = (y, x0 = 80, x1 = 380, n = 30) => Array.from({ length: n + 1 }, (_, i) => [x0 + (x1 - x0) * i / n, y]);
  const pen = { pointerId: 11, pointerType: 'pen', buttons: 1, pressure: 0.5 };
  await stroke(pen, line(60), t => 0.1 + 0.9 * t);
  const pr = await p.evaluate(() => __app.App.cv.strokes[0].pts.map(q => q[2]));
  T(pr.length > 20 && pr[0] < 0.2 && pr[pr.length - 1] > 0.9, 'ضغط القلم يُسجَّل مع كل نقطة', `من ${pr[0].toFixed(2)} إلى ${pr[pr.length - 1].toFixed(2)}`);
  // سماكة الرسم تزداد مع الضغط (نقيسها من البكسلات)
  await p.evaluate(() => __app.App.cv.clear());
  await stroke(Object.assign({}, pen, { pointerId: 12 }), line(60, 60, 200), () => 0.1); await stroke(Object.assign({}, pen, { pointerId: 13 }), line(140, 60, 200), () => 1.0);
  const thick = await p.evaluate(() => { const cv = __app.App.cv; const c = cv.ink; const x = c.getContext('2d'); const d = cv.dpr; const col = (cx, y0, y1) => { let n = 0; const im = x.getImageData(Math.round(cx * d), Math.round(y0 * d), 1, Math.round((y1 - y0) * d)).data; for (let i = 3; i < im.length; i += 4) if (im[i] > 128) n++; return n / d; }; return { light: col(130, 40, 80), heavy: col(130, 120, 160) }; });
  T(thick.heavy > thick.light * 2.2, 'الخط يثخن مع زيادة الضغط', `خفيف=${thick.light.toFixed(1)}px ثقيل=${thick.heavy.toFixed(1)}px`);
  // رفض راحة اليد بعد استخدام القلم
  const n0 = await p.evaluate(() => __app.App.cv.strokes.length);
  await stroke({ pointerId: 21, pointerType: 'touch', buttons: 1, pressure: 0.3 }, line(100));
  const n1 = await p.evaluate(() => ({ n: __app.App.cv.strokes.length, ig: __app.App.cv.stats.ignoredTouch, seen: __app.Store.data.set.penSeen }));
  T(n1.n === n0 && n1.ig > 0 && n1.seen === true, 'بعد استخدام القلم تُتجاهل لمسة اليد', `تجاهُل=${n1.ig}`);
  // أثناء الكتابة بالقلم تُتجاهل لمسة إضافية
  await ev('pointerdown', { x: 100, y: 90, init: Object.assign({}, pen, { pointerId: 31 }) }); await ev('pointermove', { x: 130, y: 95, init: Object.assign({}, pen, { pointerId: 31 }) });
  await stroke({ pointerId: 32, pointerType: 'touch', buttons: 1 }, line(150)); await ev('pointerup', { x: 130, y: 95, init: Object.assign({}, pen, { pointerId: 31, buttons: 0 }) });
  T((await p.evaluate(() => __app.App.cv.strokes.length)) === n0 + 1, 'لمسة اليد أثناء الكتابة لا تُرسم');
  // السماح بالإصبع من لوحة الوالدين
  await p.evaluate(() => { __app.Store.data.set.finger = true; });
  await stroke({ pointerId: 22, pointerType: 'touch', buttons: 1, pressure: 0.3 }, line(180));
  T((await p.evaluate(() => __app.App.cv.strokes.length)) === n0 + 2, 'يُقبل الإصبع عندما يسمح الوالدان');
  await p.evaluate(() => { __app.Store.data.set.finger = false; });
  // الفأرة تعمل (للاختبار/الحاسوب)
  const nm = await p.evaluate(() => __app.App.cv.strokes.length); await p.evaluate(() => { __app.Store.data.set.penSeen = false; });
  await stroke({ pointerId: 1, pointerType: 'mouse', buttons: 1 }, line(200)); T((await p.evaluate(() => __app.App.cv.strokes.length)) === nm + 1, 'الفأرة ترسم عندما لم يُستخدم قلم بعد');
  // الممحاة: طرف القلم (buttons=32) يمحو الضربة
  await p.evaluate(() => { __app.App.cv.clear(); });
  await stroke(Object.assign({}, pen, { pointerId: 41 }), line(120)); T((await p.evaluate(() => __app.App.cv.strokes.length)) === 1, 'ضربة للمحو');
  await stroke({ pointerId: 42, pointerType: 'pen', buttons: 32, button: 5, pressure: 0.5 }, [[200, 100], [200, 125], [205, 140]]);
  T((await p.evaluate(() => __app.App.cv.strokes.length)) === 0, 'طرف الممحاة في القلم يمحو', 'erased=' + (await p.evaluate(() => __app.App.cv.stats.erased)));
  await p.evaluate(() => { __app.App.cv.undo(); }); T((await p.evaluate(() => __app.App.cv.strokes.length)) === 1, 'التراجع يعيد ما مُحي');
  await p.click('[data-tool=eraser]'); await stroke(Object.assign({}, pen, { pointerId: 43 }), [[220, 100], [220, 125]]);
  T((await p.evaluate(() => __app.App.cv.strokes.length)) === 0, 'زر الممحاة على الشاشة يمحو أيضاً');
  T(p.errs.length === 0, 'لا أخطاء في اختبارات القلم', p.errs.join('|'));

  /* ========== 5) فاحص الخط بضربات محاكاة ========== */
  console.log('\n[5] فحص الكتابة بضربات محاكاة');
  p = await fresh(browser); await p.evaluate(SIM);
  const words = ['الطَّائِرَةُ', 'بِهُدُوءٍ', 'السَّفَرُ', 'الْمَقْعَدُ', 'خُلُقًا', 'صَعَدَ عَادِلٌ إِلَى الطَّائِرَةِ بِهُدُوءٍ'];
  const other = { 'الطَّائِرَةُ': 'الْمُعَلِّمُ', 'بِهُدُوءٍ': 'تَذَاكِرَ', 'السَّفَرُ': 'الْمَدْرَسَةُ', 'الْمَقْعَدُ': 'الْحَدِيقَةُ', 'خُلُقًا': 'الْهَاتِفُ', 'صَعَدَ عَادِلٌ إِلَى الطَّائِرَةِ بِهُدُوءٍ': 'قَرَّرَ فَوَّازٌ شِرَاءَ كِتَابٍ مُفِيدٍ' };
  const rows = [];
  for (const w of words) {
    const R = await p.evaluate(async ([w, o]) => {
      await HW.fontReady(); const S = window.__sim; const sk = S.skeleton(strip(w), 'Amiri', 120); const skH = S.skeleton(w, 'Amiri', 120); const res = {};
      const chk = st => { const r = HW.check(st, [w]); return { ok: r.ok, why: r.reason, cov: +r.cov.toFixed(2), prec: +r.prec.toFixed(2), ratio: +r.ratio.toFixed(2) }; };
      res.exact = chk(S.place(sk, 1, 1, 40, 60));
      res.exactHarakat = chk(S.place(skH, 1, 1, 40, 60));
      res.smaller = chk(S.place(sk, 0.45, 0.45, 500, 300));
      res.shifted = chk(S.place(sk, 0.8, 0.8, 700, 20));
      res.stretched = chk(S.place(sk, 1.3, 0.7, 100, 200));
      res.wobbly = chk(S.place(sk, 1, 1, 40, 60, 3));
      // بجانب النموذج: نسخة فوق النموذج وأخرى بجانبه (نسختان)
      res.beside = chk(S.place(sk, 1, 1, 40, 60).concat(S.place(sk, 0.6, 0.6, 40 + sk.w + 120, 80)));
      res.below = chk(S.place(sk, 1, 1, 40, 60).concat(S.place(sk, 1, 1, 40, 60 + sk.h + 140)));
      res.blob = chk(S.blob(sk.w, sk.h, 40, 60)); res.randomLine = chk(S.line(sk.w, sk.h, 40, 60)); res.blank = chk([]);
      res.dot = chk([{ pts: [[100, 100, .5]] }]); res.flat = chk([{ pts: Array.from({ length: 60 }, (_, i) => [40 + i * sk.w / 60, 100, .5]) }]);
      res.zigzag = chk([{ pts: Array.from({ length: 80 }, (_, i) => [40 + i * sk.w / 80, 60 + (i % 2 ? sk.h : 0), .5]) }]);
      // «خط مختلف» (بديل تقريبي لاختلاف خط الأطفال): هيكل بخط DejaVu Sans و FreeSerif
      for (const f of ['DejaVu Sans', 'FreeSerif']) { try { const sf = S.skeleton(strip(w), f, 120); res['font:' + f] = chk(S.place(sf, 1, 1, 40, 60)); } catch (e) { res['font:' + f] = { ok: null, why: String(e) }; } }
      const so = S.skeleton(strip(o), 'Amiri', 120); res.otherWord = chk(S.place(so, 1, 1, 40, 60));
      for (const f of ['DejaVu Sans', 'FreeSerif']) { try { const sf = S.skeleton(strip(o), f, 120); res['otherWord:' + f] = chk(S.place(sf, 1, 1, 40, 60)); } catch (e) { res['otherWord:' + f] = { ok: null }; } }
      return res;
    }, [w, other[w]]);
    rows.push([w, R]);
  }
  const want = { exact: true, exactHarakat: true, smaller: true, shifted: true, stretched: true, wobbly: true, beside: true, below: true, blob: false, randomLine: false, blank: false, dot: false, flat: false, zigzag: false };
  for (const [w, R] of rows) {
    console.log('   «' + w + '»');
    for (const [k, r] of Object.entries(R)) console.log(`      ${k.padEnd(22)} ${r.ok ? 'مقبول ' : 'مرفوض '} ${r.why || ''} cov=${r.cov} prec=${r.prec} ratio=${r.ratio}`);
    for (const [k, v] of Object.entries(want)) T(R[k].ok === v, `«${w.slice(0, 14)}» ${k} ${v ? 'مقبول' : 'مرفوض'}`, `(${R[k].why} cov=${R[k].cov} prec=${R[k].prec} ratio=${R[k].ratio})`);
    T(R.blank.why === 'blank', `«${w.slice(0, 14)}» الصفحة الفارغة تُسمّى blank`); T(R.blob.why === 'blob', `«${w.slice(0, 14)}» الخربشة الكثيفة تُسمّى blob`, R.blob.why); T(['line', 'miss'].includes(R.randomLine.why), `«${w.slice(0, 14)}» الخط العشوائي لا يُقبل`, R.randomLine.why);
  }
  // معلومات للتقرير (لا تُفشل الاختبار): كلمة أخرى وخط مختلف
  let fontOk = 0, fontN = 0, otherOk = 0, otherN = 0;
  rows.forEach(([w, R]) => { Object.entries(R).forEach(([k, r]) => { if (k.startsWith('font:')) { fontN++; if (r.ok) fontOk++; } if (k.startsWith('otherWord')) { otherN++; if (r.ok) otherOk++; } }); });
  console.log(`   [معلومة] الكتابة الصحيحة بخط مختلف قُبلت ${fontOk}/${fontN} ؛ كلمة خاطئة قُبلت ${otherOk}/${otherN}`);
  fs.writeFileSync(path.join(__dirname, 'last-checker-report.json'), JSON.stringify({ fontOk, fontN, otherOk, otherN, rows }, null, 1));
  T(p.errs.length === 0, 'لا أخطاء في الفاحص', p.errs.join('|'));

  /* ========== 6) مسار الكتابة في الواجهة ========== */
  console.log('\n[6] مسار الكتابة في الواجهة');
  p = await fresh(browser); await p.evaluate(SIM);
  await p.evaluate(() => { __app.Store.data.cur = 0; __app.Store.data.focus = 0; __app.Store.data.scope = 'lesson'; __app.render(); __app.go('write', { items: [{ mode: 'trace', text: 'بِهُدُوءٍ' }, { mode: 'copy', text: 'رَدَّدَ عَادِلٌ دُعَاءَ السَّفَرِ' }, { mode: 'dict', text: 'الْمُضِيفُ' }, { mode: 'letters', text: 'الطَّائِرَةُ', shown: 'الطَّ◌ائِرَةُ', missing: 'ا' }, { mode: 'short', text: 'اسْتِعْدَادًا لِإِقْلَاعِ الطَّائِرَةِ .', q: 'لِمَاذَا جَلَسَ عَادِلٌ ؟' }], mode: 'mixed' }); });
  await p.waitForSelector('.cv-ink'); await p.waitForTimeout(300); await shot(p, '04-write-trace-empty');
  // فارغ
  await p.click('#check'); T((await p.textContent('#fb')).includes('لَمْ أَرَ كِتَابَةً'), 'تحقق على صفحة فارغة: رسالة لطيفة');
  // خربشة: يبقى الحبر
  await p.evaluate(() => { const cv = __app.App.cv; cv.strokes.push(...__sim.blob(300, 120, 100, 80)); cv.redraw(); });
  const ink0 = await p.evaluate(() => __app.App.cv.strokes.length); await p.click('#check'); await p.waitForTimeout(100);
  const missTxt = await p.textContent('#fb'); T(missTxt.includes('الْحِبْرُ كَثِيرٌ'), 'خربشة كثيفة: رسالة مناسبة'); T((await p.evaluate(() => __app.App.cv.strokes.length)) === ink0, 'الحبر يبقى بعد الإخفاق'); await shot(p, '04-write-miss');
  // زر الوالدين: ضغط قصير لا يكفي ثم مطوّل يكفي
  const box = await (await p.$('#parent')).boundingBox(); await p.mouse.move(box.x + box.width / 2, box.y + box.height / 2); await p.mouse.down(); await p.waitForTimeout(250); await p.mouse.up();
  T(!(await p.evaluate(() => document.querySelector('.fb.good'))), 'ضغطة قصيرة على زر الوالدين لا تكفي');
  await p.mouse.move(box.x + box.width / 2, box.y + box.height / 2); await p.mouse.down(); await p.waitForTimeout(1150); await p.mouse.up();
  T(!!(await p.$('.fb.good')), 'ضغط مطوّل على «الكتابة صحيحة ✅» يمنح النجمة'); T((await p.textContent('.sc')).includes('١'), 'النجمة تُحتسب'); await shot(p, '04-write-parent-ok');
  await p.click('#next'); await p.waitForTimeout(150);
  // نسخ: نكتب الجملة بالضبط (محاكاة) فيُقبل
  await p.evaluate(() => { const cv = __app.App.cv; const w = cv.w, h = cv.h; });
  const ok1 = await p.evaluate(async () => { await HW.fontReady(); const sk = __sim.skeleton(strip('رَدَّدَ عَادِلٌ دُعَاءَ السَّفَرِ'), 'Amiri', 100); const cv = __app.App.cv; const s = Math.min(cv.w * 0.8 / sk.w, 1); cv.strokes.push(...__sim.place(sk, s, s, 60, 70)); cv.redraw(); return true; });
  await p.click('#check'); await p.waitForTimeout(200); T(!!(await p.$('.fb.good')), 'نسخ الجملة: يقبل الفاحص الكتابة الصحيحة'); await shot(p, '04-write-copy-ok'); await p.click('#next'); await p.waitForTimeout(150);
  // إملاء: يعرض الشكل الصحيح ثم يؤكد الطالب
  await p.evaluate(() => { const cv = __app.App.cv; cv.strokes.push(...__sim.line(200, 60, 100, 100)); cv.redraw(); });
  await p.click('#check'); await p.waitForTimeout(100); T(await p.evaluate(() => !document.getElementById('ans').hidden && document.querySelector('#ans .at').textContent.includes('الْمُضِيفُ')), 'الإملاء: يظهر الشكل الصحيح بعد التحقق'); await shot(p, '04-write-dict-reveal');
  T(!!(await p.$('#yes')) && !!(await p.$('#no')), 'أزرار التأكيد (نعم/أعيد) تظهر');
  await p.click('#no'); T((await p.evaluate(() => __app.App.cv.strokes.length)) === 0, '«سأعيد المحاولة» يمسح اللوحة');
  await p.evaluate(() => { const cv = __app.App.cv; cv.strokes.push(...__sim.line(200, 60, 100, 100)); cv.redraw(); }); await p.click('#check'); await p.click('#yes'); await p.waitForTimeout(100); T(!!(await p.$('.fb.good')), 'التأكيد يمنح النجمة'); await p.click('#next');
  // حروف ناقصة
  await p.evaluate(() => { const cv = __app.App.cv; cv.strokes.push(...__sim.line(60, 30, 100, 100)); cv.redraw(); }); await p.click('#check');
  T((await p.textContent('#ans')).includes('الْحُرُوفُ النَّاقِصَةُ'), 'الحروف الناقصة: يعرض الحروف الصحيحة'); await shot(p, '04-write-letters'); await p.click('#yes'); await p.click('#next');
  // إجابة قصيرة
  await p.evaluate(() => { const cv = __app.App.cv; cv.strokes.push(...__sim.line(300, 40, 60, 100)); cv.redraw(); }); await p.click('#check');
  T((await p.textContent('#ans')).includes('الْإِجَابَةُ النَّمُوذَجِيَّةُ'), 'الإجابة القصيرة: الإجابة النموذجية'); await shot(p, '04-write-short'); await p.click('#yes'); await p.click('#next'); await p.waitForSelector('.results');
  T((await p.evaluate(() => __app.Store.data.stars)) === 5, 'خمس نجوم محفوظة بعد الجولة', 'stars=' + (await p.evaluate(() => __app.Store.data.stars)));
  // الحفظ بعد إعادة التحميل
  await p.reload(); await p.waitForFunction(() => window.__app); T((await p.evaluate(() => __app.Store.data.stars)) === 5, 'النجوم تبقى بعد إعادة فتح التطبيق');
  T(p.errs.length === 0, 'لا أخطاء في مسار الكتابة', p.errs.join('|'));

  /* ========== 7) التقدم و✓ ولوحة الوالدين ========== */
  console.log('\n[7] التقدم ولوحة الوالدين');
  p = await fresh(browser);
  await p.evaluate(() => { const S = __app.Store; S.data.cur = 0; S.data.focus = 0; S.save(); });
  const done0 = await p.evaluate(() => __app.Store.isDone('1-1')); T(done0 === false, 'الدرس ليس منتهياً في البداية');
  await p.evaluate(() => { const S = __app.Store; ['writing', 'reading', 'review'].forEach(sec => { __app.App.stack = [{ name: 'home', p: {} }]; __app.go('results', { stars: 5, total: 5, section: sec, title: 't' }); }); });
  T(await p.evaluate(() => __app.Store.isDone('1-1')), 'الدرس يصبح ✓ بعد جولة كتابة وقراءة ومراجعة');
  await p.evaluate(() => __app.go('lessons')); await p.waitForTimeout(100); T(!!(await p.$('.lcard .check')), 'علامة ✓ تظهر في قائمة الدروس'); T((await p.$$('.lcard.lock')).length === 7, 'الدروس اللاحقة مقفلة 🔒', 'locked=' + (await p.$$('.lcard.lock')).length); await shot(p, '05-lessons');
  // بوابة الوالدين ثم اختيار «نحن الآن في»
  await p.evaluate(() => { __app.App.stack = [{ name: 'home', p: {} }]; __app.render(); }); await p.click('.parent-link'); await p.waitForSelector('.gq');
  const gq = await p.evaluate(() => document.querySelector('.gq').textContent); const nums = gq.replace(/[٠-٩]/g, d => '٠١٢٣٤٥٦٧٨٩'.indexOf(d)).match(/\d+/g).map(Number);
  const okv = nums[0] + nums[1]; const wrongBtn = await p.evaluate(v => { const bs = Array.from(document.querySelectorAll('.choice')); return bs.findIndex(b => +b.dataset.v !== v); }, okv);
  await p.click(`.choice >> nth=${wrongBtn}`); T(!!(await p.$('.gate')), 'إجابة خاطئة لا تفتح لوحة الوالدين');
  await p.click(`.choice[data-v="${okv}"]`); await p.waitForSelector('.parent'); T(true, 'الإجابة الصحيحة تفتح لوحة الوالدين'); await shot(p, '06-parent');
  await p.click('[data-cur="5"]'); await p.waitForTimeout(100); T((await p.evaluate(() => __app.Store.data.cur)) === 5, 'اختيار «نحن الآن في» الدرس ٦', 'cur=5'); T((await p.evaluate(() => JSON.parse(localStorage.getItem('lughati3.v1')).cur)) === 5, 'يُحفظ محلياً');
  await p.evaluate(() => { __app.App.stack = [{ name: 'home', p: {} }]; __app.render(); }); T((await p.textContent('.where')).includes('الْإِيثَارُ'), 'الصفحة الرئيسية تعرض الدرس الحالي');
  await p.evaluate(() => __app.go('lessons')); T((await p.$$('.lcard.lock')).length === 2, 'يُفتح حتى الدرس ٦ فقط', 'locked=' + (await p.$$('.lcard.lock')).length);
  T(p.errs.length === 0, 'لا أخطاء', p.errs.join('|'));

  /* ========== 8) تخطيطات: هاتف + لوحي بالعرض والطول ========== */
  console.log('\n[8] التخطيط على أحجام مختلفة');
  for (const [n, vp] of [['phone', { width: 390, height: 844 }], ['tablet-land', { width: 1280, height: 800 }], ['tablet-port', { width: 800, height: 1280 }]]) {
    p = await fresh(browser, vp); await p.evaluate(() => { const S = __app.Store.data; S.cur = 7; S.focus = 3; S.scope = 'all'; __app.render(); });
    const noScroll = await p.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1); T(noScroll, n + ': لا تمرير أفقي للصفحة');
    await shot(p, '07-' + n + '-home');
    await p.evaluate(() => __app.go('round', { title: 'x', items: __app.genRules(5), section: 'review' })); await p.waitForTimeout(150); await shot(p, '07-' + n + '-round');
    await p.evaluate(() => __app.go('write', { items: __app.genWrite('trace'), mode: 'trace' })); await p.waitForSelector('.cv-ink'); await p.waitForTimeout(250); await shot(p, '07-' + n + '-write');
    const pad = await p.evaluate(() => { const r = document.querySelector('#pad').getBoundingClientRect(); return { w: r.width, h: r.height, bottom: r.bottom, vh: innerHeight }; });
    T(pad.w > 250 && pad.h > 130, n + ': لوحة الكتابة كبيرة', `${Math.round(pad.w)}x${Math.round(pad.h)}`);
    T(p.errs.length === 0, n + ': لا أخطاء', p.errs.join('|'));
  }

  await browser.close();
  console.log(`\nالنتيجة: ${pass} نجح ، ${fail} فشل`); if (fail) { console.log('الإخفاقات:\n - ' + fails.join('\n - ')); process.exit(1); }
})().catch(e => { console.error('خطأ في الاختبار', e); process.exit(2); });
