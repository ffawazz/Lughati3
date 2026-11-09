// أداة ضبط: تقارن درجات الكلمة الصحيحة والخاطئة بخطوط مختلفة. تشغيل: node tests/tune.js
const { chromium } = require('playwright'); const path = require('path'); const fs = require('fs');
const src = fs.readFileSync(path.join(__dirname, 'run.js'), 'utf8'); const SIM = src.match(/const SIM = `([\s\S]*?)`;\n/)[1];
(async () => {
  const b = await chromium.launch(); const p = await b.newPage(); await p.goto('file://' + path.resolve(__dirname, '..', 'index.html')); await p.waitForFunction(() => window.__app); await p.evaluate(SIM);
  const words = ['الطَّائِرَةُ','بِهُدُوءٍ','السَّفَرُ','الْمَقْعَدُ','خُلُقًا','الْمُضِيفُ','التَّعَاوُنُ','الْهَاتِفُ','صَعَدَ عَادِلٌ إِلَى الطَّائِرَةِ بِهُدُوءٍ','رَدَّدَ عَادِلٌ دُعَاءَ السَّفَرِ'];
  const res = await p.evaluate(async (words) => {
    await HW.fontReady(); const S = __sim; const out = [];
    const fonts = ['Amiri', 'DejaVu Sans', 'FreeSerif'];
    const sk = {}; const key = (w, f) => w + '|' + f; for (const w of words) for (const f of fonts) sk[key(w, f)] = S.skeleton(strip(w), f, 120);
    for (const T of [5, 6, 7]) { HW.T = T; const right = [], wrong = [];
      for (const w of words) for (const f of fonts) { const r = HW.check(S.place(sk[key(w, f)], 1, 1, 40, 60, 2), [w]); right.push([r.cov, r.prec, r.ratio, r.aspect]);
        for (const o of words) { if (o === w) continue; const ao = strip(o).length, aw = strip(w).length; if (Math.abs(ao - aw) > 6 && (ao > 14) === (aw > 14)) { /* كلمات مختلفة الطول كثيراً */ }
          const r2 = HW.check(S.place(sk[key(o, f)], 1, 1, 40, 60, 2), [w]); wrong.push([r2.cov, r2.prec, r2.ratio, r2.aspect, w, o, f]); } }
      out.push({ T, right, wrong }); }
    window.__rw = null;
    return out;
  }, words);
  for (const { T, right, wrong } of res) {
    const q = (a, i, f) => { const v = a.map(x => x[i]).sort((x, y) => x - y); return v[Math.floor(v.length * f)].toFixed(2); };
    console.log(`T=${T}  صحيح: cov min/10%/50% = ${q(right,0,0)}/${q(right,0,.1)}/${q(right,0,.5)}  prec min/10% = ${q(right,1,0)}/${q(right,1,.1)} | خاطئ: cov 50%/90%/max = ${q(wrong,0,.5)}/${q(wrong,0,.9)}/${q(wrong,0,.999)}  prec 90% = ${q(wrong,1,.9)}`);
    for (const [cth, pth] of [[0.7, 0.58], [0.8, 0.65], [0.85, 0.7], [0.9, 0.75]]) {
      const rr = right.filter(x => x[0] >= cth && x[1] >= pth && x[2] >= .4 && x[2] <= 2.4 && x[3] >= .5 && x[3] <= 2).length / right.length, ww = wrong.filter(x => x[0] >= cth && x[1] >= pth && x[2] >= .4 && x[2] <= 2.4 && x[3] >= .5 && x[3] <= 2).length / wrong.length;
      console.log(`     عتبة cov≥${cth} prec≥${pth}: الصحيح يُقبل ${(rr * 100).toFixed(0)}%  الخاطئ يُقبل ${(ww * 100).toFixed(0)}%`);
    }
  }
  await b.close();
})();
