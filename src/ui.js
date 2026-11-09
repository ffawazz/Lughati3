/* =====================================================================
   مولّدات الأسئلة (كلها تستعمل فقط ما تعلّمه الطالب حتى الدرس الحالي)
   ===================================================================== */
'use strict';

const SUN = 'تثدذرزسشصضطظلن';
const TENSE_NAME = { 'ماضٍ': 'مَاضٍ', 'مضارع': 'مُضَارِعٌ', 'أمر': 'أَمْرٌ' };
const MADD_NAME = { 'ا': 'الْأَلِفُ', 'و': 'الْوَاوُ', 'ي': 'الْيَاءُ' };
const TW_NAME = { 'ً': 'تَنْوِينُ الْفَتْحِ', 'ٌ': 'تَنْوِينُ الضَّمِّ', 'ٍ': 'تَنْوِينُ الْكَسْرِ' };

const learned = () => LESSONS.slice(0, Store.data.cur + 1);          // كل ما تعلّمناه حتى الآن
const scopeLessons = () => (Store.data.scope === 'all' ? learned() : [LESSONS[Math.min(Store.data.focus, Store.data.cur)]]);
const focusLesson = () => LESSONS[Math.min(Store.data.focus, Store.data.cur)];
const lessonIdx = id => LESSONS.findIndex(l => l.id === id);

function mc(prompt, ans, ds, o = {}) {
  ds = uniq(ds.filter(d => d !== ans)); if (ds.length < 2) return null;
  const choices = shuffle([ans].concat(shuffle(ds).slice(0, 2)));
  return Object.assign({ kind: 'mc', prompt, speak: o.speak || prompt, choices, ans: choices.indexOf(ans), hint: o.hint || '', concept: o.concept || '' }, o.extra || {});
}
function wordsOf(ls) {
  const o = [];
  ls.forEach(l => {
    (l.words || []).forEach(w => o.push(w)); (l.vocab || []).forEach(v => o.push(v.w)); (l.dict || []).forEach(w => o.push(w));
    (l.plural || []).forEach(p => { o.push(p[0]); o.push(p[1]); }); (l.ant || []).forEach(p => { o.push(p[0]); o.push(p[1]); });
  });
  return uniq(o).filter(w => !/\s/.test(w));
}
const baseLetters = s => Array.from(strip(s)).filter(ch => /[ء-ي]/.test(ch));

const GEN = {
  sunmoon(ls) {
    const pool = wordsOf(ls).filter(w => { const p = strip(w); return p.startsWith('ال') && p.length >= 4; });
    if (pool.length < 2) return null; const w = pick(pool); const p = strip(w); const sun = SUN.includes(p[2]);
    return mc(`مَا نَوْعُ (أَلْ) فِي كَلِمَةِ {{${w}}} ؟`, sun ? 'شَمْسِيَّةٌ' : 'قَمَرِيَّةٌ', ['شَمْسِيَّةٌ', 'قَمَرِيَّةٌ', 'أَصْلِيَّةٌ'],
      { speak: `مَا نَوْعُ أَلْ فِي كَلِمَةِ ${w} ؟`, concept: 'sunmoon', hint: `بَعْدَ (أَلْ) حَرْفُ «${p[2]}» وَهُوَ حَرْفٌ ${sun ? 'شَمْسِيٌّ ، فَاللَّامُ شَمْسِيَّةٌ لَا تُنْطَقُ' : 'قَمَرِيٌّ ، فَاللَّامُ قَمَرِيَّةٌ تُنْطَقُ'}` });
  },
  shadda(ls) {
    const pool = wordsOf(ls).filter(w => w.includes('ّ')); if (!pool.length) return null; const w = pick(pool);
    const sh = new Set(); let last = ''; for (const ch of w) { if (ch === 'ّ') sh.add(last); else if (/[ء-ي]/.test(ch)) last = ch; }
    const ans = Array.from(sh)[0]; const others = uniq(baseLetters(w)).filter(c => !sh.has(c));
    if (others.length < 2) return null;
    return mc(`أَيُّ حَرْفٍ عَلَيْهِ شَدَّةٌ فِي كَلِمَةِ {{${w}}} ؟`, ans, others, { speak: `أَيُّ حَرْفٍ عَلَيْهِ شَدَّةٌ فِي كَلِمَةِ ${w} ؟`, concept: 'shadda', hint: `الشَّدَّةُ عَلَى حَرْفِ «${ans}» فَنَقْرَؤُهُ مُشَدَّدًا`, extra: { big: true } });
  },
  tanween(ls) {
    const pool = wordsOf(ls).filter(w => /[ًٌٍ]/.test(w)); if (!pool.length) return null; const w = pick(pool);
    const m = w.match(/[ًٌٍ]/)[0];
    return mc(`مَا نَوْعُ التَّنْوِينِ فِي كَلِمَةِ {{${w}}} ؟`, TW_NAME[m], Object.values(TW_NAME), { speak: `مَا نَوْعُ التَّنْوِينِ فِي كَلِمَةِ ${w} ؟`, concept: 'tanween', hint: `${TW_NAME[m]} لِأَنَّ عَلَى آخِرِ الْكَلِمَةِ عَلَامَتَيْنِ مِنْ نَوْعٍ وَاحِدٍ` });
  },
  madd(ls) {
    const pool = ls.flatMap(l => l.madd || []); if (!pool.length) return null; const [w, k] = pick(pool);
    return mc(`مَا حَرْفُ الْمَدِّ فِي كَلِمَةِ {{${w}}} ؟`, MADD_NAME[k], Object.values(MADD_NAME), { speak: `مَا حَرْفُ الْمَدِّ فِي كَلِمَةِ ${w} ؟`, concept: 'madd', hint: `نَمُدُّ الصَّوْتَ بِـ${MADD_NAME[k]} فِي هَذِهِ الْكَلِمَةِ` });
  },
  taa(ls) {
    const cand = wordsOf(ls).filter(w => /[ةت]$/.test(strip(w)) && strip(w).length >= 3);
    if (!cand.length) return null; const w = pick(cand); const p = strip(w); const marb = p.endsWith('ة');
    const idx = w.lastIndexOf(marb ? 'ة' : 'ت');
    return mc(`أَكْمِلِ الْكَلِمَةَ بِالْحَرْفِ الصَّحِيحِ : {{${w.slice(0, idx)}◌}}`, marb ? 'ة' : 'ت', ['ة', 'ت', 'ه'], { speak: `أَكْمِلِ الْكَلِمَةَ بِالْحَرْفِ الصَّحِيحِ : ${w}`, concept: 'taa', hint: `تُكْتَبُ هَكَذَا : ${w} ، وَنِهَايَتُهَا ${marb ? 'تَاءٌ مَرْبُوطَةٌ (ة)' : 'تَاءٌ مَفْتُوحَةٌ (ت)'}`, extra: { big: true } });
  },
  comp(ls) {
    const pool = ls.flatMap(l => l.mcq || []); if (!pool.length) return null; const [q, a, d1, d2] = pick(pool);
    const shown = q.replace(/\(([^)]+)\)/g, '{{$1}}');
    return mc(shown, a, [d1, d2], { speak: q.replace(/[()]/g, ''), concept: 'comp', hint: q.replace('____', a).replace(/[()]/g, '') });
  },
  tense(ls) {
    const pool = ls.flatMap(l => l.tense || []); if (!pool.length) return null; const [w, k] = pick(pool);
    return mc(`مَا نَوْعُ الْفِعْلِ {{${w}}} ؟`, TENSE_NAME[k], Object.values(TENSE_NAME), { speak: `مَا نَوْعُ الْفِعْلِ ${w} ؟`, concept: 'tense', hint: `${w} فِعْلٌ ${TENSE_NAME[k]}` });
  },
  question(ls) {
    const pool = ls.flatMap(l => l.question || []); if (!pool.length) return null; const it = pick(pool);
    const names = uniq(learned().flatMap(l => l.qwords || []));
    const ds = shuffle(names.filter(n => n !== it.a));
    return mc(`اخْتَرْ أَدَاةَ الِاسْتِفْهَامِ الْمُنَاسِبَةَ :`, it.a, ds, { speak: `اخْتَرْ أَدَاةَ الِاسْتِفْهَامِ الْمُنَاسِبَةَ : ${it.s.replace('____', ' ... ')}`, concept: 'question', hint: `${it.s.replace('____', it.a)}`, extra: { sub: it.s, clue: it.clue } });
  },
  plural(ls) {
    const pool = ls.flatMap(l => l.plural || []); if (pool.length < 3) return null; const [s, p] = pick(pool);
    if (Math.random() < 0.5) return mc(`مَا جَمْعُ كَلِمَةِ {{${s}}} ؟`, p, pool.map(x => x[1]), { speak: `مَا جَمْعُ كَلِمَةِ ${s} ؟`, concept: 'plural', hint: `جَمْعُ ${s} هُوَ ${p}` });
    return mc(`مَا مُفْرَدُ كَلِمَةِ {{${p}}} ؟`, s, pool.map(x => x[0]), { speak: `مَا مُفْرَدُ كَلِمَةِ ${p} ؟`, concept: 'plural', hint: `مُفْرَدُ ${p} هُوَ ${s}` });
  },
  antonym(ls) {
    const pool = ls.flatMap(l => l.ant || []); if (pool.length < 3) return null; const [a, b] = pick(pool);
    const ds = pool.map(x => x[1]).concat(pool.map(x => x[0])).filter(x => !pool.some(q => (q[0] === a && q[1] === x) || (q[1] === a && q[0] === x)));
    return mc(`مَا ضِدُّ كَلِمَةِ {{${a}}} ؟`, b, ds, { speak: `مَا ضِدُّ كَلِمَةِ ${a} ؟`, concept: 'antonym', hint: `ضِدُّ ${a} هُوَ ${b}` });
  },
  meaning(ls) {
    const pool = ls.flatMap(l => (l.vocab || []).filter(v => v.m)); if (pool.length < 3) return null; const v = pick(pool);
    return mc(`مَا مَعْنَى {{${v.w}}} ؟`, v.m, pool.map(x => x.m), { speak: `مَا مَعْنَى ${v.w} ؟`, concept: 'meaning', hint: `${v.w} : ${v.m}` });
  },
  style(ls) {
    const pool = ls.flatMap(l => l.style || []); if (!pool.length) return null; const it = pick(pool);
    const names = uniq(learned().flatMap(l => (l.style || []).map(s => s.a).concat(l.stylenames || [])));
    if (names.length < 3) return null;
    return mc(`مَا أُسْلُوبُ الْجُمْلَةِ ؟ {{${it.s}}}`, it.a, names, { speak: `مَا أُسْلُوبُ الْجُمْلَةِ ؟ ${it.s}`, concept: 'style', hint: `${it.s} : أُسْلُوبُ ${it.a}` });
  },
  alif(ls) {
    const pool = wordsOf(ls).filter(w => /(ى|اء)$/.test(strip(w))); if (!pool.length) return null; const w = pick(pool); const mq = strip(w).endsWith('ى');
    return mc(`كَلِمَةُ {{${w}}} آخِرُهَا :`, mq ? 'أَلِفٌ مَقْصُورَةٌ' : 'أَلِفٌ مَمْدُودَةٌ', ['أَلِفٌ مَقْصُورَةٌ', 'أَلِفٌ مَمْدُودَةٌ', 'تَاءٌ مَرْبُوطَةٌ'], { speak: `كَلِمَةُ ${w} آخِرُهَا مَاذَا ؟`, concept: 'alif', hint: mq ? 'تُكْتَبُ عَلَى صُورَةِ يَاءٍ بِلَا نُقَطٍ وَتُنْطَقُ أَلِفًا' : 'أَلِفٌ بَعْدَهَا هَمْزَةٌ فِي آخِرِ الْكَلِمَةِ' });
  },
  tafdeel(ls) {
    const pool = ls.flatMap(l => l.tafdeel || []); if (pool.length < 3 && uniq(pool.map(p => p.a)).length < 3) { const all = learned().flatMap(l => l.tafdeel || []); if (uniq(all.map(p => p.a)).length < 3 || !pool.length) return null; }
    const all = learned().flatMap(l => l.tafdeel || []); const it = pick(pool);
    return mc(`أَكْمِلْ بِاسْمِ التَّفْضِيلِ : {{${it.q}}}`, it.a, all.map(p => p.a).concat(['أَكْبَرُ', 'أَقْصَرُ']), { speak: `أَكْمِلْ بِاسْمِ التَّفْضِيلِ : ${it.q.replace('____', ' ... ')}`, concept: 'tafdeel', hint: it.q.replace('____', it.a) });
  },
  nakira(ls) {
    const pool = ls.flatMap(l => l.nakira || []); if (pool.length < 3) return null; const [n, m] = pick(pool);
    if (strip(n).startsWith('ال')) return mc(`حَوِّلِ الْمَعْرِفَةَ إِلَى نَكِرَةٍ : {{${n}}}`, m, pool.map(x => x[1]).concat(pool.map(x => x[0]).filter(x => x !== n)).filter(x => !strip(x).startsWith('ال')), { speak: `حَوِّلِ الْمَعْرِفَةَ إِلَى نَكِرَةٍ : ${n}`, concept: 'nakira', hint: `نَحْذِفُ (أَلْ) وَنُنَوِّنُ : ${n} ← ${m}` });
    return mc(`حَوِّلِ النَّكِرَةَ إِلَى مَعْرِفَةٍ : {{${n}}}`, m, pool.map(x => x[1]).filter(x => strip(x).startsWith('ال')), { speak: `حَوِّلِ النَّكِرَةَ إِلَى مَعْرِفَةٍ : ${n}`, concept: 'nakira', hint: `نَزِيدُ (أَلْ) وَنَحْذِفُ التَّنْوِينَ : ${n} ← ${m}` });
  },
  taajjub(ls) {
    const pool = ls.flatMap(l => l.taajjub || []); const all = learned().flatMap(l => l.taajjub || []); if (!pool.length || uniq(all.map(x => x.f)).length < 3) return null; const it = pick(pool);
    return mc(`${it.t} ← مَا ____ ${it.n} !`, it.f, all.map(x => x.f), { speak: `${it.t} . مَا ... ${it.n} . اخْتَرِ الْكَلِمَةَ الْمُنَاسِبَةَ`, concept: 'taajjub', hint: `مَا ${it.f} ${it.n} !` , extra: { sub: it.t } });
  },
  hamza(ls) {
    const pool = ls.flatMap(l => l.hamza || []); if (!pool.length) return null; const [w, k] = pick(pool);
    return mc(`الْهَمْزَةُ فِي أَوَّلِ كَلِمَةِ {{${w}}} هِيَ :`, k === 'wasl' ? 'هَمْزَةُ وَصْلٍ' : 'هَمْزَةُ قَطْعٍ', ['هَمْزَةُ وَصْلٍ', 'هَمْزَةُ قَطْعٍ', 'هَمْزَةٌ مُتَوَسِّطَةٌ'], { speak: `الْهَمْزَةُ فِي أَوَّلِ كَلِمَةِ ${w} ، وَصْلٌ أَمْ قَطْعٌ ؟`, concept: 'hamza', hint: k === 'wasl' ? 'تُنْطَقُ فِي بَدْءِ الْكَلَامِ فَقَطْ وَتَسْقُطُ فِي وَسَطِهِ' : 'تُنْطَقُ دَائِمًا وَتُكْتَبُ فَوْقَ الْأَلِفِ أَوْ تَحْتَهَا' });
  },
  fail(ls) {
    const pool = ls.flatMap(l => l.fail || []); if (!pool.length) return null; const it = pick(pool);
    if (it[3] === 'f') return mc(`مَا اسْمُ الْفَاعِلِ الْمُؤَنَّثُ مِنَ الْفِعْلِ {{${it[0]}}} ؟`, it[2], [it[1]].concat(pool.filter(x => x !== it).map(x => x[2])), { speak: `مَا اسْمُ الْفَاعِلِ الْمُؤَنَّثُ مِنَ الْفِعْلِ ${it[0]} ؟`, concept: 'fail', hint: `${it[0]} ← ${it[1]} ← ${it[2]}` });
    const fa = Math.random() < 0.5;
    return mc(`مَا ${fa ? 'اسْمُ الْفَاعِلِ' : 'اسْمُ الْمَفْعُولِ'} مِنَ الْفِعْلِ {{${it[0]}}} ؟`, fa ? it[1] : it[2], [fa ? it[2] : it[1]].concat(pool.filter(x => x !== it && x[3] !== 'f').map(x => fa ? x[1] : x[2])), { speak: `مَا ${fa ? 'اسْمُ الْفَاعِلِ' : 'اسْمُ الْمَفْعُولِ'} مِنَ الْفِعْلِ ${it[0]} ؟`, concept: 'fail', hint: `${it[0]} ← ${it[1]} (فَاعِلٌ) ← ${it[2]} (مَفْعُولٌ)` });
  },
};

function conceptsAvailable() { return Object.keys(CONCEPTS).filter(c => CONCEPTS[c].from <= Store.data.cur); }
function genConcept(c, ls) { for (let t = 0; t < 12; t++) { const q = GEN[c](ls); if (q) return q; } return null; }
function genRules(n, only) {
  const ls = scopeLessons(); const out = []; const seen = new Set();
  const cs = only ? [only] : conceptsAvailable(); let guard = 0;
  while (out.length < n && guard++ < 120) { const c = only || pick(cs); const q = genConcept(c, ls); const key = q && (q.prompt + '|' + (q.sub || '')); if (q && !seen.has(key)) { seen.add(key); out.push(q); } }
  return out;
}
function availableConcepts() { const ls = scopeLessons(); return conceptsAvailable().filter(c => genConcept(c, ls)); }

// اسمع واختر
function genHear(n) {
  const ls = scopeLessons(); const pool = uniq(ls.flatMap(l => (l.vocab || []).map(v => v.w).concat(l.dict || [])).filter(w => !/\s/.test(w)));
  const all = wordsOf(learned()).filter(w => !/\s/.test(w)); const out = [];
  shuffle(pool).slice(0, n).forEach(w => {
    const like = shuffle(all.filter(x => x !== w)).sort((a, b) => Math.abs(strip(a).length - strip(w).length) - Math.abs(strip(b).length - strip(w).length)).slice(0, 8);
    const q = mc('اسْمَعْ ثُمَّ اخْتَرِ الْكَلِمَةَ الَّتِي سَمِعْتَهَا', w, like, { speak: w, concept: 'hear', hint: `الْكَلِمَةُ هِيَ : ${w}`, extra: { autospeak: true, speakOnly: w, big: true } });
    if (q) out.push(q);
  });
  return out;
}
// رتّب الحروف
function genBuild(n) {
  const ls = scopeLessons(); const pool = uniq(ls.flatMap(l => (l.vocab || []).map(v => v.w).concat(l.dict || [])).filter(w => !/\s/.test(w) && strip(w).length >= 3 && strip(w).length <= 8));
  return shuffle(pool).slice(0, n).map(w => { const L = baseLetters(w); let sh = shuffle(L); for (let k = 0; k < 6 && sh.join('') === L.join(''); k++) sh = shuffle(L);
    return { kind: 'build', prompt: 'رَتِّبِ الْحُرُوفَ لِتَكُونَ كَلِمَةً', speak: w, target: L, show: w, hint: `الْكَلِمَةُ : ${w}`, letters: sh, concept: 'build' }; });
}
// الكلمة الشاذة
function genOdd(n) {
  const ls = scopeLessons(); const out = []; const maps = [];
  const wp = wordsOf(ls).filter(w => { const p = strip(w); return p.startsWith('ال') && p.length >= 4; });
  const sm = { 'شَمْسِيَّةٌ': wp.filter(w => SUN.includes(strip(w)[2])), 'قَمَرِيَّةٌ': wp.filter(w => !SUN.includes(strip(w)[2])) }; maps.push({ m: sm, q: 'أَيُّ كَلِمَةٍ تَخْتَلِفُ فِي نَوْعِ (أَلْ) ؟' });
  const tp = ls.flatMap(l => l.tense || []); const tm = {}; tp.forEach(([w, k]) => (tm[TENSE_NAME[k]] = tm[TENSE_NAME[k]] || []).push(w)); maps.push({ m: tm, q: 'أَيُّ فِعْلٍ يَخْتَلِفُ عَنِ الْآخَرَيْنِ ؟' });
  const wt = wordsOf(ls).filter(w => /[ًٌٍ]/.test(w)); const twm = {}; wt.forEach(w => { const k = TW_NAME[w.match(/[ًٌٍ]/)[0]]; (twm[k] = twm[k] || []).push(w); }); maps.push({ m: twm, q: 'أَيُّ كَلِمَةٍ تَخْتَلِفُ فِي نَوْعِ التَّنْوِينِ ؟' });
  const md = ls.flatMap(l => l.madd || []); const mm = {}; md.forEach(([w, k]) => (mm[MADD_NAME[k]] = mm[MADD_NAME[k]] || []).push(w)); maps.push({ m: mm, q: 'أَيُّ كَلِمَةٍ يَخْتَلِفُ حَرْفُ الْمَدِّ فِيهَا ؟' });
  let guard = 0;
  while (out.length < n && guard++ < 80) {
    const cand = maps.filter(o => Object.values(o.m).filter(a => a.length >= 2).length >= 1 && Object.values(o.m).filter(a => a.length >= 1).length >= 2); if (!cand.length) break;
    const o = pick(cand); const keys = Object.keys(o.m); const big = shuffle(keys.filter(k => o.m[k].length >= 2)); const A = big[0]; const B = shuffle(keys.filter(k => k !== A && o.m[k].length >= 1))[0]; if (!B) continue;
    const two = shuffle(o.m[A]).slice(0, 2); const odd = pick(o.m[B]); const q = mc(o.q, odd, two, { speak: o.q + ' ' + two.concat([odd]).join(' ، '), concept: 'odd', hint: `${two.join(' وَ ')} : ${A} ، أَمَّا ${odd} فَهِيَ ${B}`, extra: { big: true } });
    if (q) out.push(q);
  }
  return out;
}
// أكمل الجملة بالكلمة الناقصة
const PUNCT = /^[،.؟!:؛]+$/;
function genFill(n) {
  const ls = scopeLessons(); const out = []; const pre = ls.flatMap(l => (l.fill || []).map(f => ({ f, l })));
  const sents = ls.flatMap(l => (l.noText ? [] : l.text).map(s => ({ s, l })));
  const items = [];
  pre.forEach(({ f }) => { const q = mc(`أَكْمِلِ الْجُمْلَةَ :`, f.a, f.d, { speak: `أَكْمِلِ الْجُمْلَةَ : ${f.s.replace('____', ' ... ')}`, concept: 'fill', hint: f.s.replace('____', f.a), extra: { sub: f.s, big: false } }); if (q) items.push(q); });
  sents.forEach(({ s, l }) => {
    const toks = s.split(' ').filter(Boolean); const cand = toks.map((t, i) => i).filter(i => !PUNCT.test(toks[i]) && strip(toks[i]).length >= 4 && !/^[:]/.test(toks[i]));
    if (toks.length < 4 || !cand.length) return;
    const i = pick(cand); const a = toks[i]; const others = uniq(l.text.join(' ').split(' ').filter(t => !PUNCT.test(t) && strip(t) !== strip(a) && strip(t).length >= 3));
    const sub = toks.map((t, k) => (k === i ? '____' : t)).join(' ');
    const q = mc('أَكْمِلِ الْجُمْلَةَ :', a, others, { speak: `أَكْمِلِ الْجُمْلَةَ : ${sub.replace('____', ' ... ')}`, concept: 'fill', hint: s, extra: { sub } }); if (q) items.push(q);
  });
  return shuffle(items).slice(0, n);
}
// رتّب الجملة
function genOrder(n) {
  const ls = scopeLessons(); const out = [];
  shuffle(ls.flatMap(l => (l.order || []).map(s => s))).forEach(s => {
    const w = s.split(' ').filter(Boolean); if (w.length < 3) return; let sh = shuffle(w); for (let k = 0; k < 6 && sh.join(' ') === w.join(' '); k++) sh = shuffle(w);
    out.push({ kind: 'order', prompt: 'رَتِّبِ الْكَلِمَاتِ لِتَكُونَ جُمْلَةً مُفِيدَةً', speak: 'رَتِّبِ الْكَلِمَاتِ لِتَكُونَ جُمْلَةً مُفِيدَةً', target: w, words: sh, hint: s, concept: 'order' });
  });
  return out.slice(0, n);
}

/* =====================================================================
   عناصر الكتابة
   ===================================================================== */
function genWrite(mode, n = 5) {
  const ls = scopeLessons(); const out = []; const clauses = ls.flatMap(l => l.copy || []);
  const words = uniq(ls.flatMap(l => (l.dict || []).concat((l.vocab || []).map(v => v.w))));
  const single = words.filter(w => !/\s/.test(w));
  const mk = {
    trace: () => shuffle(single).slice(0, Math.max(0, n - 1)).map(t => ({ mode: 'trace', text: t })).concat(shuffle(clauses).slice(0, 1).map(t => ({ mode: 'trace', text: t }))),
    copy: () => shuffle(clauses).slice(0, n).map(t => ({ mode: 'copy', text: t })),
    dict: () => shuffle(single).slice(0, n).map(t => ({ mode: 'dict', text: t })),
    letters: () => shuffle(single.filter(w => strip(w).length >= 4)).slice(0, n).map(w => {
      const L = Array.from(w); const bases = []; L.forEach((ch, i) => { if (/[ء-ي]/.test(ch) && i > 0) bases.push(i); });
      const k = bases.length > 5 ? 2 : 1; const gaps = shuffle(bases).slice(0, k).sort((a, b) => a - b); const shown = L.map((ch, i) => (gaps.includes(i) ? '◌' : ch)).join('');
      return { mode: 'letters', text: w, shown, missing: gaps.map(i => L[i]).join(' ، ') };
    }),
    short: () => shuffle(ls.flatMap(l => l.short || [])).slice(0, Math.min(n, 3)).map(s => ({ mode: 'short', text: s.a, q: s.q, free: !!s.free })),
  };
  if (mode === 'mixed') { ['trace', 'copy', 'dict', 'letters', 'short'].forEach(m => out.push(...mk[m]().slice(0, 1))); return out; }
  return mk[mode]().slice(0, n);
}

/* =====================================================================
   الواجهة
   ===================================================================== */
const App = { stack: [], cv: null, round: null, reading: false };
const el = () => $('#screen');
function setTitle(t) { $('#title').innerHTML = t; }
function stars() { $('#starCount').textContent = AR_DIGITS(Store.data.stars); }
function go(name, p = {}) { cleanup(); App.stack.push({ name, p }); render(); }
function replace(name, p = {}) { cleanup(); App.stack[App.stack.length - 1] = { name, p }; render(); }
function back() { cleanup(); if (App.stack.length > 1) App.stack.pop(); render(); return App.stack.length >= 1; }
function cleanup() { TTS.stop(); App.reading = false; if (App.cv) { App.cv.destroy(); App.cv = null; } }
window.__back = () => { if (App.stack.length > 1) { back(); return true; } return false; };
function render() {
  const { name, p } = App.stack[App.stack.length - 1]; const s = SCREENS[name]; $('#app').dataset.screen = name;
  $('#back').style.visibility = App.stack.length > 1 ? 'visible' : 'hidden';
  el().innerHTML = ''; el().scrollTop = 0; s(p); stars();
}
function toast(msg) { const t = $('#toast'); t.innerHTML = msg; t.classList.add('on'); clearTimeout(toast._t); toast._t = setTimeout(() => t.classList.remove('on'), 2200); }
const lessonTag = l => `الْوَحْدَةُ ${AR_DIGITS(l.u)} – الدَّرْسُ ${AR_DIGITS(l.n)}`;
function speakBtn(txt, cls = '') { return `<button class="spk ${cls}" data-say="${esc(txt)}" aria-label="اسمع">🔊</button>`; }
function bindSay(root) { $$('[data-say]', root).forEach(b => b.addEventListener('click', ev => { ev.stopPropagation(); TTS.speak(b.dataset.say); })); }
function scopeChip() {
  const all = Store.data.scope === 'all';
  return `<div class="scope"><span class="chip lesson-chip">${esc(focusLesson().title)}</span>
    <button class="seg ${all ? '' : 'on'}" data-scope="lesson">هَذَا الدَّرْسُ</button><button class="seg ${all ? 'on' : ''}" data-scope="all">كُلُّ مَا تَعَلَّمْنَاهُ</button></div>`;
}
function bindScope(root, again) { $$('[data-scope]', root).forEach(b => b.addEventListener('click', () => { Store.data.scope = b.dataset.scope; Store.save(); again(); })); }

const SCREENS = {};

/* ---------- الرئيسية ---------- */
SCREENS.home = () => {
  setTitle('لُغَتِي');
  const l = LESSONS[Store.data.cur]; const done = LESSONS.filter(x => Store.isDone(x.id)).length;
  el().innerHTML = `
  <section class="hero"><div class="hero-t" id="greet">${esc(Praise.greet())}</div>${speakBtn(Praise.greet())}</section>
  <section class="where"><div>نَحْنُ الْآنَ فِي <b>${lessonTag(l)}</b> : ${esc(l.title)}</div><div class="done">الدُّرُوسُ الْمُنْجَزَةُ : ${AR_DIGITS(done)} مِنْ ${AR_DIGITS(LESSONS.length)}</div></section>
  <section class="tiles home">
    <button class="tile t-write big" data-go="writeMenu"><span class="em">✍️</span><span>الْكِتَابَةُ بِالْقَلَمِ</span><small>اُكْتُبْ وَتَدَرَّبْ</small></button>
    <button class="tile t-read" data-go="readMenu"><span class="em">📖</span><span>الْقِرَاءَةُ</span></button>
    <button class="tile t-quick" data-go="quickMenu"><span class="em">⚡</span><span>مُرَاجَعَةٌ سَرِيعَةٌ</span></button>
    <button class="tile t-map" data-go="lessons"><span class="em">🗺️</span><span>دُرُوسِي</span></button>
  </section>
  <button class="parent-link" data-go="gate">👪 لِلْوَالِدَيْنِ</button>`;
  bindSay(el()); $$('[data-go]', el()).forEach(b => b.addEventListener('click', () => go(b.dataset.go)));
  TTS.speak(Praise.greet());
};

/* ---------- الدروس ---------- */
SCREENS.lessons = () => {
  setTitle('دُرُوسِي');
  let h = `<div class="lessons">`;
  UNITS.forEach(u => {
    h += `<div class="unit"><h3>الْوَحْدَةُ ${AR_DIGITS(u.n)} : ${esc(u.title)}</h3><div class="lrow">`;
    LESSONS.filter(l => l.u === u.n).forEach(l => {
      const i = lessonIdx(l.id); const lock = i > Store.data.cur; const done = Store.isDone(l.id); const d = Store.lesson(l.id); const foc = i === Math.min(Store.data.focus, Store.data.cur);
      h += `<button class="lcard ${lock ? 'lock' : ''} ${foc ? 'focus' : ''}" data-i="${i}" ${lock ? 'aria-disabled="true"' : ''}>
        <div class="lnum">${AR_DIGITS(l.n)}</div><div class="ltitle">${esc(l.title)}</div>
        <div class="lbadges"><span class="${d.writing ? 'ok' : ''}">✍️</span><span class="${d.reading ? 'ok' : ''}">📖</span><span class="${d.review ? 'ok' : ''}">⚡</span></div>
        <div class="lmark">${lock ? '🔒' : done ? '<span class="check">✓</span>' : ''}</div><div class="lstars">⭐ ${AR_DIGITS(d.stars)}</div></button>`;
    });
    h += `</div></div>`;
  });
  el().innerHTML = h + `</div><p class="note">اُضْغُطْ عَلَى دَرْسٍ لِتَخْتَارَهُ ثُمَّ ابْدَأِ التَّدْرِيبَ . 🔒 دُرُوسٌ لَمْ نَصِلْ إِلَيْهَا بَعْدُ .</p>`;
  $$('.lcard', el()).forEach(b => b.addEventListener('click', () => { const i = +b.dataset.i; if (i > Store.data.cur) { toast('هَذَا الدَّرْسُ لَمْ نَصِلْ إِلَيْهِ بَعْدُ 🔒'); return; } Store.data.focus = i; Store.save(); toast(`اخْتَرْتَ : ${esc(LESSONS[i].title)}`); render(); TTS.speak(LESSONS[i].title); }));
};

/* ---------- قوائم الأقسام ---------- */
function menu(title, tiles, extra = '') {
  setTitle(title);
  el().innerHTML = `${scopeChip()}${extra}<div class="tiles menu">${tiles.map(t => `<button class="tile ${t.cls || ''}" data-act="${t.act}" ${t.dis ? 'disabled' : ''}><span class="em">${t.em}</span><span>${t.t}</span>${t.s ? `<small>${t.s}</small>` : ''}</button>`).join('')}</div>`;
  bindScope(el(), render);
}
SCREENS.writeMenu = () => {
  const f = focusLesson();
  menu('الْكِتَابَةُ ✍️', [
    { act: 'trace', em: '🖍️', t: 'تَتَبَّعْ', s: 'اُكْتُبْ فَوْقَ الْكَلِمَةِ', cls: 't-write' }, { act: 'copy', em: '📝', t: 'اِنْسَخِ الْجُمْلَةَ', s: 'اُكْتُبْهَا بِخَطٍّ جَمِيلٍ', cls: 't-copy' },
    { act: 'dict', em: '👂', t: 'إِمْلَاءٌ', s: 'اسْمَعْ ثُمَّ اُكْتُبْ', cls: 't-dict' }, { act: 'letters', em: '🔡', t: 'حُرُوفٌ نَاقِصَةٌ', s: 'أَكْمِلِ الْكَلِمَةَ', cls: 't-letters' },
    { act: 'short', em: '💬', t: 'أَجِبْ بِالْكِتَابَةِ', s: 'جُمْلَةٌ قَصِيرَةٌ', cls: 't-short' }, { act: 'mixed', em: '🌈', t: 'جَوْلَةٌ مُتَنَوِّعَةٌ', s: 'مِنْ كُلِّ نَوْعٍ', cls: 't-mixed' },
  ], `<p class="sub">${lessonTag(f)}</p>`);
  $$('[data-act]', el()).forEach(b => b.addEventListener('click', () => { const items = genWrite(b.dataset.act); if (!items.length) { toast('لَا تُوجَدُ تَمَارِينُ مِنْ هَذَا النَّوْعِ فِي هَذَا الدَّرْسِ بَعْدُ'); return; } go('write', { items, mode: b.dataset.act }); }));
};
SCREENS.readMenu = () => {
  menu('الْقِرَاءَةُ 📖', [
    { act: 'along', em: '🔊', t: 'اِقْرَأْ مَعِي', s: 'تَلْوِينُ الْكَلِمَةِ أَثْنَاءَ الْقِرَاءَةِ', cls: 't-read' },
    { act: 'fill', em: '🧩', t: 'أَكْمِلِ الْجُمْلَةَ', s: 'اِخْتَرِ الْكَلِمَةَ النَّاقِصَةَ', cls: 't-fill' },
    { act: 'order', em: '🔀', t: 'رَتِّبِ الْجُمْلَةَ', s: 'أَعِدِ الْكَلِمَاتِ إِلَى مَكَانِهَا', cls: 't-order' },
  ]);
  $$('[data-act]', el()).forEach(b => b.addEventListener('click', () => {
    const a = b.dataset.act; if (a === 'along') return go('along');
    const items = a === 'fill' ? genFill(5) : genOrder(5); if (!items.length) return toast('لَا تُوجَدُ جُمَلٌ كَافِيَةٌ بَعْدُ');
    go('round', { title: a === 'fill' ? 'أَكْمِلِ الْجُمْلَةَ 🧩' : 'رَتِّبِ الْجُمْلَةَ 🔀', items, section: 'reading' });
  }));
};
SCREENS.quickMenu = () => {
  const cs = availableConcepts();
  menu('مُرَاجَعَةٌ سَرِيعَةٌ ⚡', [
    { act: 'rules', em: '📚', t: 'الْقَوَاعِدُ', s: 'مِنْ كُلِّ مَا تَعَلَّمْنَا', cls: 't-rules' },
    { act: 'hear', em: '👂', t: 'اسْمَعْ وَاخْتَرْ', s: 'أَيُّ كَلِمَةٍ سَمِعْتَ ؟', cls: 't-hear' },
    { act: 'build', em: '🔤', t: 'رَتِّبِ الْحُرُوفَ', s: 'كَوِّنِ الْكَلِمَةَ', cls: 't-build' },
    { act: 'odd', em: '🕵️', t: 'الْكَلِمَةُ الشَّاذَّةُ', s: 'مَنْ يَخْتَلِفُ ؟', cls: 't-odd' },
  ], `<div class="concepts"><div class="sub">اِخْتَرْ قَاعِدَةً مُحَدَّدَةً :</div>${cs.map(c => `<button class="cchip" data-c="${c}">${CONCEPTS[c].icon} ${esc(CONCEPTS[c].name)}</button>`).join('')}</div>`);
  const start = (act, c) => {
    let items = []; let title = '';
    if (act === 'rules') { items = genRules(5, c); title = c ? `${CONCEPTS[c].icon} ${CONCEPTS[c].name}` : 'الْقَوَاعِدُ 📚'; }
    if (act === 'hear') { items = genHear(5); title = 'اسْمَعْ وَاخْتَرْ 👂'; }
    if (act === 'build') { items = genBuild(5); title = 'رَتِّبِ الْحُرُوفَ 🔤'; }
    if (act === 'odd') { items = genOdd(5); title = 'الْكَلِمَةُ الشَّاذَّةُ 🕵️'; }
    if (!items.length) return toast('لَا تَكْفِي الْكَلِمَاتُ بَعْدُ ، جَرِّبْ «كُلُّ مَا تَعَلَّمْنَاهُ»');
    go('round', { title, items, section: 'review' });
  };
  $$('[data-act]', el()).forEach(b => b.addEventListener('click', () => start(b.dataset.act)));
  $$('[data-c]', el()).forEach(b => b.addEventListener('click', () => start('rules', b.dataset.c)));
};

/* ---------- جولة الأسئلة (٥ أسئلة، ٣ اختيارات) ---------- */
SCREENS.round = p => {
  setTitle(p.title); const R = App.round = { items: p.items, i: 0, stars: 0, section: p.section, title: p.title, tries: 0, lock: false };
  const draw = () => {
    const q = R.items[R.i]; R.tries = 0; R.lock = false;
    const dots = R.items.map((_, k) => `<i class="${k < R.i ? 'd' : k === R.i ? 'c' : ''}"></i>`).join('');
    let body = '';
    const qcard = `<div class="qcard" id="qcard" role="button" tabindex="0" aria-label="اضغط لتسمع السؤال">${speakBtn(q.speak)}<div class="qtext ${q.big ? 'qbig' : ''}">${q.speakOnly ? '<span class="ear">👂</span>' : ''}${redify(q.prompt)}${q.sub ? `<div class="qsub">${q.clue ? `<span class="clue">الْجَوَابُ : ${esc(q.clue)}</span>` : ''}${redify(q.sub)}</div>` : ''}</div></div>`;
    if (q.kind === 'mc') body = `<div class="choices">${q.choices.map((c, k) => `<button class="choice c${k}" data-k="${k}">${esc(c)}</button>`).join('')}</div>`;
    else if (q.kind === 'build') body = `<div class="tray" id="tray"></div><div class="bank" id="bank">${q.letters.map((l, k) => `<button class="chip-l" data-k="${k}">${l}</button>`).join('')}</div><div class="row"><button class="btn ghost" id="undoL">↩️ تَرَاجَعْ</button></div>`;
    else body = `<div class="tray words" id="tray"></div><div class="bank words" id="bank">${q.words.map((l, k) => `<button class="chip-w" data-k="${k}">${esc(l)}</button>`).join('')}</div><div class="row"><button class="btn ghost" id="undoL">↩️ تَرَاجَعْ</button></div>`;
    el().innerHTML = `<div class="round"><div class="dots">${dots}<span class="sc">⭐ ${AR_DIGITS(R.stars)}</span></div>${qcard}${body}<div class="dock"><div class="fb" id="fb"></div></div></div>`;
    bindSay(el()); const qc = $('#qcard'); qc.addEventListener('click', () => TTS.speak(q.speak));
    if (q.kind === 'mc') $$('.choice', el()).forEach(b => b.addEventListener('click', () => answer(+b.dataset.k)));
    else buildHandlers(q);
    TTS.speak(q.speak);
  };
  const feedback = (ok, q, last) => {
    const fb = $('#fb'); const msg = ok ? Praise.okMsg() : Praise.tryMsg();
    fb.className = 'fb ' + (ok ? 'good' : 'bad');
    fb.innerHTML = `<div class="msg">${ok ? '🌟 ' : '💪 '}${esc(msg)}</div>${last && q.hint ? `<div class="hintx">${redify(q.hint)}</div>` : ''}${last ? `<button class="btn next" id="next">${R.i + 1 >= R.items.length ? 'النَّتِيجَةُ 🏁' : 'التَّالِي ◀'}</button>` : ''}`;
    if (last) $('#next').addEventListener('click', () => { R.i++; if (R.i >= R.items.length) finish(); else draw(); });
    if (ok) { fb.classList.add('pop'); }
    TTS.speak(msg.replace(/[!؟]/g, ''), { keep: false });
  };
  const answer = k => {
    if (R.lock) return; const q = R.items[R.i]; const btn = $$('.choice', el())[k];
    if (k === q.ans) { R.lock = true; btn.classList.add('right'); const first = R.tries === 0; if (first) { R.stars++; $('.sc').textContent = '⭐ ' + AR_DIGITS(R.stars); } $$('.choice', el()).forEach(b => b.disabled = true); feedback(true, q, true); }
    else { R.tries++; btn.classList.add('wrong'); btn.disabled = true; if (R.tries >= 2) { R.lock = true; $$('.choice', el())[q.ans].classList.add('right'); $$('.choice', el()).forEach(b => b.disabled = true); feedback(false, q, true); } else feedback(false, q, false); }
  };
  const buildHandlers = q => {
    const tray = $('#tray'), bank = $('#bank'); const need = q.kind === 'build' ? q.target.length : q.target.length; const got = [];
    const val = () => got.map(g => (q.kind === 'build' ? q.letters[g] : q.words[g]));
    const show = () => { tray.innerHTML = got.map(g => `<span class="${q.kind === 'build' ? 'chip-l placed' : 'chip-w placed'}">${esc(q.kind === 'build' ? q.letters[g] : q.words[g])}</span>`).join(''); $$('#bank button', el()).forEach(b => b.disabled = got.includes(+b.dataset.k)); };
    $$('#bank button', el()).forEach(b => b.addEventListener('click', () => {
      if (R.lock) return; got.push(+b.dataset.k); show(); TTS.speak(q.kind === 'build' ? q.letters[+b.dataset.k] : q.words[+b.dataset.k], { force: false });
      if (got.length === need) {
        const ok = val().join(q.kind === 'build' ? '' : ' ') === q.target.join(q.kind === 'build' ? '' : ' ');
        if (ok) { R.lock = true; const first = R.tries === 0; if (first) { R.stars++; $('.sc').textContent = '⭐ ' + AR_DIGITS(R.stars); } $('#bank').classList.add('done'); feedback(true, Object.assign({}, q, { hint: q.show || q.hint }), true); }
        else { R.tries++; tray.classList.add('shake'); setTimeout(() => tray.classList.remove('shake'), 500); if (R.tries >= 2) { R.lock = true; feedback(false, q, true); } else { feedback(false, q, false); setTimeout(() => { got.length = 0; show(); }, 700); } }
      }
    }));
    $('#undoL').addEventListener('click', () => { if (R.lock) return; got.pop(); show(); });
  };
  const finish = () => replace('results', { stars: R.stars, total: R.items.length, section: R.section, title: R.title });
  draw();
};

/* ---------- النتيجة ---------- */
SCREENS.results = p => {
  setTitle('النَّتِيجَةُ 🏆'); const f = focusLesson();
  Store.addStars(f.id, p.stars); const L = Store.lesson(f.id); const good = p.stars >= Math.ceil(p.total * 0.6);
  if (good) { L[p.section]++; Store.save(); }
  const msg = Praise.endMsg(p.stars, p.total); const done = Store.isDone(f.id);
  el().innerHTML = `<div class="results"><div class="bigstars">${Array.from({ length: p.total }, (_, i) => `<span class="${i < p.stars ? 'on' : ''}" style="animation-delay:${i * 0.15}s">⭐</span>`).join('')}</div>
    <div class="rmsg">${esc(msg)}</div><div class="rsub">حَصَلْتَ عَلَى ${AR_DIGITS(p.stars)} مِنْ ${AR_DIGITS(p.total)} نُجُومٍ</div>
    ${done && good ? `<div class="doneBadge"><span class="check">✓</span> أَنْهَيْتَ دَرْسَ «${esc(f.title)}» !</div>` : ''}
    <div class="row"><button class="btn" id="again">🔁 جَوْلَةٌ جَدِيدَةٌ</button><button class="btn ghost" id="home">🏠 الرَّئِيسِيَّةُ</button></div></div>`;
  TTS.speak(msg);
  $('#again').addEventListener('click', () => { back(); });
  $('#home').addEventListener('click', () => { App.stack = [{ name: 'home', p: {} }]; render(); });
};

/* ---------- اقرأ معي ---------- */
SCREENS.along = () => {
  const f = focusLesson(); setTitle('اِقْرَأْ مَعِي 🔊');
  const red = new Set((f.red || []).concat(f.vocab.map(v => v.w)).map(strip));
  const isRed = t => { const x = strip(t); return red.has(x) || ((x[0] === 'و' || x[0] === 'ف') && red.has(x.slice(1))); };
  const tokens = []; f.text.forEach((s, si) => s.split(' ').filter(Boolean).forEach(t => tokens.push({ t, si, p: PUNCT.test(t) })));
  el().innerHTML = `<div class="along"><div class="lt">${esc(f.title)} ${f.noText ? '' : ''}</div>
    <div class="ptext" id="ptext">${tokens.map((k, i) => k.p ? `<span class="pn">${esc(k.t)}</span>` : `<span class="w ${isRed(k.t) ? 'red' : ''}" data-i="${i}">${esc(k.t)}</span>`).join(' ')}</div>
    ${f.noText ? '<p class="note">هَذِهِ جُمْلَةٌ وَاحِدَةٌ مِنْ أَوْرَاقِ الْعَمَلِ ، فَلَيْسَ لِهَذَا الدَّرْسِ مَقْطَعٌ قِرَائِيٌّ فِيهَا .</p>' : ''}
    <p class="note">اُضْغُطْ عَلَى أَيِّ كَلِمَةٍ لِتَسْمَعَهَا . الْكَلِمَاتُ الْحَمْرَاءُ هِيَ كَلِمَاتُ الدَّرْسِ .</p>
    <div class="row"><button class="btn" id="play">▶ اِقْرَأْ مَعِي</button><button class="btn ghost" id="stop">⏹ أَوْقِفْ</button><button class="btn ghost" id="slow">🐢 بَطِيءٌ</button></div></div>`;
  const words = $$('.w', el()); let slow = true;
  words.forEach(w => w.addEventListener('click', () => { if (App.reading) return; words.forEach(x => x.classList.remove('hl')); w.classList.add('hl'); TTS.speak(w.textContent, { rate: 0.8 }).then(() => w.classList.remove('hl')); }));
  $('#slow').addEventListener('click', e => { slow = !slow; e.target.textContent = slow ? '🐢 بَطِيءٌ' : '🐇 عَادِيٌّ'; });
  $('#stop').addEventListener('click', () => { App.reading = false; TTS.stop(); words.forEach(x => x.classList.remove('hl')); });
  $('#play').addEventListener('click', async () => {
    if (App.reading) return; App.reading = true; const token = ++App.readTok;
    for (const w of words) { if (!App.reading || token !== App.readTok) break; words.forEach(x => x.classList.remove('hl')); w.classList.add('hl'); w.scrollIntoView({ block: 'nearest' }); await TTS.speak(w.textContent, { rate: slow ? 0.75 : 1 }); }
    words.forEach(x => x.classList.remove('hl'));
    if (App.reading && token === App.readTok) { App.reading = false; const L = Store.lesson(f.id); L.reading++; Store.addStars(f.id, 1); stars(); toast('🌟 أَحْسَنْتَ ! قَرَأْتَ النَّصَّ كَامِلًا'); TTS.speak('أَحْسَنْتَ ! قَرَأْتَ النَّصَّ كَامِلًا'); }
  });
};
App.readTok = 0;

/* ---------- الكتابة بالقلم ---------- */
const WRITE_HELP = {
  trace: 'اُكْتُبْ فَوْقَ الْكَلِمَةِ بِالْقَلَمِ ( أَوْ بِجَانِبِهَا ) ثُمَّ اضْغَطْ ✔️',
  copy: 'اِنْسَخِ الْجُمْلَةَ بِخَطٍّ جَمِيلٍ ثُمَّ اضْغَطْ ✔️',
  dict: 'اسْتَمِعْ إِلَى الْكَلِمَةِ ثُمَّ اُكْتُبْهَا ، وَبَعْدَهَا اضْغَطْ ✔️ لِتَرَى الصَّوَابَ',
  letters: 'اُكْتُبِ الْحُرُوفَ النَّاقِصَةَ ثُمَّ اضْغَطْ ✔️ لِتَرَى الصَّوَابَ',
  short: 'اُكْتُبِ الْإِجَابَةَ ثُمَّ اضْغَطْ ✔️ لِتَرَى الْإِجَابَةَ النَّمُوذَجِيَّةَ',
};
const WRITE_MSG = {
  blank: 'لَمْ أَرَ كِتَابَةً بَعْدُ ✏️ اِكْتُبْ بِالْقَلَمِ ثُمَّ اضْغَطْ ✔️',
  blob: 'الْحِبْرُ كَثِيرٌ جِدًّا 🖍️ حَاوِلْ أَنْ تَكْتُبَ بِخَطٍّ أَرَقَّ',
  line: 'أَرَى خَطًّا وَاحِدًا فَقَطْ 🙂 اُكْتُبْ حُرُوفَ الْكَلِمَةِ كَامِلَةً',
  miss: 'قَرِيبٌ جِدًّا ! أَكْمِلْ أَوْ عَدِّلْ كِتَابَتَكَ وَسَأَنْظُرُ مَرَّةً أُخْرَى',
};
SCREENS.write = p => {
  const items = p.items; const W = App.round = { items, i: 0, stars: 0, section: 'writing', title: 'الْكِتَابَةُ', misses: 0, revealed: false };
  setTitle('الْكِتَابَةُ ✍️');
  const draw = () => {
    const it = items[W.i]; W.misses = 0; W.revealed = false; W.done = false;
    const dots = items.map((_, k) => `<i class="${k < W.i ? 'd' : k === W.i ? 'c' : ''}"></i>`).join('');
    const say = it.mode === 'dict' ? it.text : it.mode === 'short' ? it.q : it.mode === 'letters' ? it.text : it.text;
    let model = '';
    if (it.mode === 'copy') { const rs = new Set((focusLesson().red || []).map(strip)); model = `<div class="model" data-say="${esc(it.text)}">${speakBtn(it.text)}<div class="mtext">${it.text.split(' ').map(t => rs.has(strip(t)) ? `<span class="red">${esc(t)}</span>` : esc(t)).join(' ')}</div></div>`; }
    if (it.mode === 'dict') model = `<div class="model dict">${speakBtn(it.text, 'huge')}<div class="mtext hid">اسْمَعْ ثُمَّ اُكْتُبْ</div></div>`;
    if (it.mode === 'letters') model = `<div class="model">${speakBtn(it.text)}<div class="mtext">${esc(it.shown)}</div></div>`;
    if (it.mode === 'short') model = `<div class="model">${speakBtn(it.q)}<div class="mtext q">${esc(it.q)}</div></div>`;
    if (it.mode === 'trace') model = `<div class="model slim">${speakBtn(it.text)}<div class="mtext">${esc(it.text)}</div></div>`;
    el().innerHTML = `<div class="write">
      <div class="dots">${dots}<span class="sc">⭐ ${AR_DIGITS(W.stars)}</span></div>
      <div class="wtitle">${esc(WRITE_HELP[it.mode])}</div>${model}
      <div class="ans" id="ans" hidden></div>
      <div class="padwrap"><div class="pad" id="pad"></div>
        <div class="tools"><button class="tl on" data-tool="pen" aria-label="قلم">✏️<small>قَلَمٌ</small></button><button class="tl" data-tool="eraser" aria-label="ممحاة">🧽<small>مِمْحَاةٌ</small></button><button class="tl" id="undo" aria-label="تراجع">↩️<small>تَرَاجَعْ</small></button><button class="tl" id="clr" aria-label="مسح">🗑️<small>امْسَحْ</small></button></div></div>
      <div class="dock"><div class="fb" id="fb"></div>
      <div class="wbar"><button class="btn big" id="check">✔️ تَحَقَّقْ</button><button class="hold" id="parent"><span class="fill"></span><span class="lab">👪 الْكِتَابَةُ صَحِيحَةٌ ✅<small>لِلْوَالِدَيْنِ : اِضْغَطْ مُطَوَّلًا</small></span></button><button class="btn ghost" id="skip">تَخَطَّ ⏭</button></div></div></div>`;
    bindSay(el());
    $$('.model', el()).forEach(m => m.addEventListener('click', () => TTS.speak(say)));
    const lines = it.mode === 'trace' ? 2 : it.mode === 'copy' ? 3 : 2;
    const cv = App.cv = new InkCanvas($('#pad'), { lines, onchange: () => {} });
    cv.pad = true; $('#pad').classList.add('m-' + it.mode);
    if (it.mode === 'trace') cv.setGuide((c, w, h) => { const fs = fitFont(c, it.text, w * 0.9, h * 0.62, 140); c.font = `${fs}px Amiri, serif`; c.direction = 'rtl'; c.textAlign = 'center'; c.textBaseline = 'alphabetic'; c.fillStyle = 'rgba(86,104,176,.28)'; c.fillText(it.text, w / 2, h * 0.66); });
    $$('[data-tool]', el()).forEach(b => b.addEventListener('click', () => { cv.setTool(b.dataset.tool); $$('[data-tool]', el()).forEach(x => x.classList.toggle('on', x === b)); }));
    $('#undo').addEventListener('click', () => cv.undo()); $('#clr').addEventListener('click', () => { cv.clear(); $('#fb').innerHTML = ''; $('#fb').className = 'fb'; });
    $('#check').addEventListener('click', () => doCheck(it, cv)); $('#skip').addEventListener('click', next);
    holdButton($('#parent'), 900, () => pass(it, true));
    if (it.mode === 'dict') TTS.speak(it.text); else TTS.speak(say);
  };
  const fitFont = (c, text, maxW, maxH, max) => { let fs = max; c.font = `${fs}px Amiri, serif`; c.direction = 'rtl'; let w = c.measureText(text).width; if (w > maxW) fs = Math.floor(fs * maxW / w); return Math.max(26, Math.min(fs, maxH)); };
  const pass = (it, byParent) => {
    if (W.done) return; W.done = true; W.stars++; $('.sc').textContent = '⭐ ' + AR_DIGITS(W.stars);
    const fb = $('#fb'); const msg = Praise.okMsg(); fb.className = 'fb good pop';
    fb.innerHTML = `<div class="msg">🌟 ${esc(msg)}${byParent ? ' <small>· أَكَّدَ الْوَالِدَانِ</small>' : ''}</div><button class="btn next" id="next">${W.i + 1 >= items.length ? 'النَّتِيجَةُ 🏁' : 'التَّالِي ◀'}</button>`;
    $('#next').addEventListener('click', next); TTS.speak(msg.replace(/[!؟]/g, ''));
  };
  const next = () => { W.i++; if (W.i >= items.length) replace('results', { stars: W.stars, total: items.length, section: 'writing', title: 'الْكِتَابَةُ' }); else draw(); };
  const doCheck = (it, cv) => {
    const fb = $('#fb');
    if (it.mode === 'trace' || it.mode === 'copy') {
      if (W.done) return; const r = HW.check(cv.strokes, [it.text]); W.last = r;
      if (r.ok) return pass(it, false);
      W.misses++; fb.className = 'fb bad'; // الحبر يبقى كما هو
      fb.innerHTML = `<div class="msg">💪 ${esc(WRITE_MSG[r.reason] || WRITE_MSG.miss)}</div><div class="row"><button class="btn ghost" id="keep">✏️ أُكْمِلُ</button><button class="btn ghost" id="redo">🗑️ أُعِيدُ مِنَ الْبِدَايَةِ</button></div>`;
      $('#keep').addEventListener('click', () => { fb.innerHTML = ''; fb.className = 'fb'; }); $('#redo').addEventListener('click', () => { cv.clear(); fb.innerHTML = ''; fb.className = 'fb'; });
      TTS.speak(Praise.tryMsg().replace(/[!؟]/g, '')); return;
    }
    // إملاء / حروف ناقصة / إجابة قصيرة : نعرض الشكل الصحيح ثم يؤكّد الطالب أو الوالد
    if (!cv.hasInk()) { fb.className = 'fb bad'; fb.innerHTML = `<div class="msg">💪 ${esc(WRITE_MSG.blank)}</div>`; return; }
    if (W.revealed) return; W.revealed = true; const a = $('#ans'); a.hidden = false;
    const correct = it.mode === 'letters' ? `${it.text}<small>الْحُرُوفُ النَّاقِصَةُ : ${esc(it.missing)}</small>` : it.mode === 'short' ? `${esc(it.text)}` : esc(it.text);
    a.innerHTML = `<div class="al">${it.mode === 'short' ? 'الْإِجَابَةُ النَّمُوذَجِيَّةُ' : 'هَكَذَا تُكْتَبُ'} :</div><div class="at">${correct}</div>${speakBtn(it.mode === 'short' ? it.text : it.text)}`; bindSay(a);
    fb.className = 'fb'; fb.innerHTML = `<div class="msg">قَارِنْ كِتَابَتَكَ بِالْأَصْلِ ، هَلْ كَتَبْتَهَا صَحِيحَةً ؟</div><div class="row"><button class="btn good" id="yes">✅ نَعَمْ ، كَتَبْتُهَا صَحِيحَةً</button><button class="btn ghost" id="no">🔁 سَأُعِيدُ الْمُحَاوَلَةَ</button></div>`;
    $('#yes').addEventListener('click', () => { a.hidden = true; pass(it, false); }); $('#no').addEventListener('click', () => { cv.clear(); a.hidden = true; W.revealed = false; fb.innerHTML = ''; fb.className = 'fb'; });
    TTS.speak(it.text);
  };
  draw();
};
// زر الضغط المطوّل (لتأكيد الوالدين)
function holdButton(btn, ms, cb) {
  let t = null, raf = 0, t0 = 0; const fill = $('.fill', btn);
  const stop = () => { clearTimeout(t); cancelAnimationFrame(raf); fill.style.width = '0%'; btn.classList.remove('holding'); };
  const start = e => { e.preventDefault(); btn.classList.add('holding'); t0 = performance.now(); const tick = () => { fill.style.width = Math.min(100, (performance.now() - t0) / ms * 100) + '%'; raf = requestAnimationFrame(tick); }; tick(); t = setTimeout(() => { stop(); cb(); }, ms); };
  btn.addEventListener('pointerdown', start); ['pointerup', 'pointerleave', 'pointercancel'].forEach(ev => btn.addEventListener(ev, stop));
  btn.addEventListener('keydown', e => { if (e.key === 'Enter') cb(); });
}

/* ---------- بوابة الوالدين ---------- */
SCREENS.gate = () => {
  setTitle('لِلْوَالِدَيْنِ 👪'); const a = 6 + rnd(6), b = 4 + rnd(5); const ans = a + b;
  const ch = shuffle([ans, ans + 2, ans - 3]);
  el().innerHTML = `<div class="gate"><p>هَذِهِ الصَّفْحَةُ لِلْكِبَارِ فَقَطْ . حُلَّ الْمَسْأَلَةَ لِلدُّخُولِ :</p><div class="gq">${AR_DIGITS(a)} + ${AR_DIGITS(b)} = ؟</div>
  <div class="choices">${ch.map(c => `<button class="choice" data-v="${c}">${AR_DIGITS(c)}</button>`).join('')}</div></div>`;
  $$('.choice', el()).forEach(x => x.addEventListener('click', () => { if (+x.dataset.v === ans) replace('parent'); else { x.classList.add('wrong'); x.disabled = true; } }));
};

/* ---------- لوحة الوالدين ---------- */
SCREENS.parent = () => {
  setTitle('لَوْحَةُ الْوَالِدَيْنِ 👪'); const S = Store.data.set; const st = TTS.status();
  let sel = '';
  UNITS.forEach(u => { sel += `<div class="pu"><b>الْوَحْدَةُ ${AR_DIGITS(u.n)}</b>`; LESSONS.filter(l => l.u === u.n).forEach(l => { const i = lessonIdx(l.id); sel += `<button class="pl ${i === Store.data.cur ? 'on' : ''}" data-cur="${i}">الدَّرْسُ ${AR_DIGITS(l.n)} : ${esc(l.title)}</button>`; }); sel += `</div>`; });
  el().innerHTML = `<div class="parent">
    <section><h3>نَحْنُ الْآنَ فِي …</h3><p class="note">اِخْتَرْ آخِرَ دَرْسٍ وَصَلْتُمْ إِلَيْهِ فِي الْمَدْرَسَةِ . كُلُّ الْأَلْعَابِ تَسْتَخْدِمُ فَقَطْ مَا تَعَلَّمَهُ الطَّالِبُ حَتَّى هَذَا الدَّرْسِ .</p><div class="plist">${sel}</div></section>
    <section><h3>الدُّرُوسُ الْمُنْجَزَةُ ✓</h3><div class="plist">${LESSONS.map(l => `<button class="pl ${Store.isDone(l.id) ? 'on' : ''}" data-done="${l.id}">${Store.isDone(l.id) ? '✓ ' : ''}${esc(l.title)}</button>`).join('')}</div><p class="note">الدَّرْسُ يُصْبِحُ ✓ بَعْدَ نَجَاحِ جَوْلَةٍ فِي الْكِتَابَةِ وَالْقِرَاءَةِ وَالْمُرَاجَعَةِ ، وَيُمْكِنُكُمْ تَبْدِيلُ الْعَلَامَةِ هُنَا .</p></section>
    <section><h3>الْقَلَمُ وَالصَّوْتُ</h3>
      <label class="sw"><input type="checkbox" id="finger" ${S.finger ? 'checked' : ''}> السَّمَاحُ بِالْكِتَابَةِ بِالْإِصْبَعِ ( بَعْدَ اسْتِخْدَامِ الْقَلَمِ تُتَجَاهَلُ لَمَسَاتُ الْيَدِ تِلْقَائِيًّا )</label>
      <label class="sw"><input type="checkbox" id="snd" ${S.sound ? 'checked' : ''}> تَشْغِيلُ الصَّوْتِ</label>
      <label class="rg">سُرْعَةُ الْقِرَاءَةِ : <input type="range" id="rate" min="0.5" max="1.1" step="0.05" value="${S.rate}"> <span id="rv">${AR_DIGITS(S.rate)}</span></label>
      <div class="who">أُخَاطِبُ الطَّالِبَ : ${[['n', 'بِلَا تَحْدِيدٍ'], ['b', 'وَلَدٌ'], ['g', 'بِنْتٌ']].map(([v, t]) => `<button class="pl ${S.who === v ? 'on' : ''}" data-who="${v}">${t}</button>`).join('')}</div>
      <div class="row"><button class="btn" id="testv">🔊 جَرِّبِ الصَّوْتَ</button>${TTS.native ? '<button class="btn ghost" id="ttsset">⚙️ إِعْدَادَاتُ النُّطْقِ</button>' : ''}</div>
      <p class="note" id="ttsst">${st.arabic ? 'الصَّوْتُ الْعَرَبِيُّ مُتَوَفِّرٌ ✅' : 'لَمْ يُعْثَرْ عَلَى صَوْتٍ عَرَبِيٍّ فِي الْجِهَازِ ⚠️ . ثَبِّتْ «Speech Services by Google» وَحَمِّلِ اللُّغَةَ الْعَرَبِيَّةَ مِنْ إِعْدَادَاتِ النُّطْقِ .'} ( مُحَرِّكُ النُّطْقِ : ${esc(st.engine || '')} )</p></section>
    <section><h3>عَنِ الْمُحْتَوَى</h3><p class="note">التَّطْبِيقُ مَبْنِيٌّ عَلَى فِهْرِسِ كِتَابِ لُغَتِي ( الصَّفِّ الثَّالِثِ ، الْفَصْلِ الْأَوَّلِ ) وَأَوْرَاقِ الْعَمَلِ الْمَنْشُورَةِ فِي مَوْقِعِ كُتُبِي . نُصُوصُ الْقِرَاءَةِ هِيَ الْمَقَاطِعُ الْوَارِدَةُ فِي أَوْرَاقِ الْعَمَلِ ، وَلَيْسَتْ نَصَّ الدَّرْسِ كَامِلًا . يُرْجَى مُرَاجَعَةُ التَّشْكِيلِ وَالْإِجَابَاتِ مَعَ الْكِتَابِ الْمَدْرَسِيِّ .</p></section>
    <section><h3>التَّقَدُّمُ</h3><p>⭐ ${AR_DIGITS(Store.data.stars)} نَجْمَةً</p><button class="btn danger" id="reset">🗑️ مَسْحُ كُلِّ التَّقَدُّمِ</button></section></div>`;
  $$('[data-cur]', el()).forEach(b => b.addEventListener('click', () => { Store.data.cur = +b.dataset.cur; Store.data.focus = Math.min(Store.data.focus, Store.data.cur); if (Store.data.focus > Store.data.cur) Store.data.focus = Store.data.cur; Store.data.focus = Store.data.cur; Store.save(); render(); toast('تَمَّ الْحِفْظُ ✅'); }));
  $$('[data-done]', el()).forEach(b => b.addEventListener('click', () => { const L = Store.lesson(b.dataset.done); L.manual = !Store.isDone(b.dataset.done); if (!L.manual) { L.writing = L.reading = L.review = 0; } Store.save(); render(); }));
  $$('[data-who]', el()).forEach(b => b.addEventListener('click', () => { S.who = b.dataset.who; Store.save(); render(); }));
  $('#finger').addEventListener('change', e => { S.finger = e.target.checked; if (S.finger) S.penSeen = false; Store.save(); });
  $('#snd').addEventListener('change', e => { S.sound = e.target.checked; Store.save(); });
  $('#rate').addEventListener('input', e => { S.rate = +e.target.value; $('#rv').textContent = AR_DIGITS(S.rate); Store.save(); });
  $('#testv').addEventListener('click', () => TTS.speak('مَرْحَبًا ! أَنَا أَقْرَأُ لَكَ بِصَوْتٍ وَاضِحٍ', { force: true }));
  const ts = $('#ttsset'); if (ts) ts.addEventListener('click', () => TTS.settings());
  let armed = false; $('#reset').addEventListener('click', e => { if (!armed) { armed = true; e.target.textContent = 'اُضْغُطْ مَرَّةً أُخْرَى لِلتَّأْكِيدِ ⚠️'; setTimeout(() => { armed = false; e.target.textContent = '🗑️ مَسْحُ كُلِّ التَّقَدُّمِ'; }, 3500); } else { Store.reset(); App.stack = [{ name: 'home', p: {} }]; render(); } });
};

/* ---------- تشغيل ---------- */
function boot() {
  Store.load(); $('#back').addEventListener('click', () => back()); $('#gear').addEventListener('click', () => go('gate'));
  $('#starsBtn').addEventListener('click', () => toast(`⭐ عِنْدَكَ ${AR_DIGITS(Store.data.stars)} نَجْمَةً !`));
  App.stack = [{ name: 'home', p: {} }]; render();
  if (HW.fontReady) HW.fontReady();
}
window.__app = { App, Store, HW, TTS, GEN, genWrite, genRules, genHear, genBuild, genOdd, genFill, genOrder, SCREENS, go, back, render, availableConcepts, LESSONS, CONCEPTS };
document.addEventListener('DOMContentLoaded', boot);
