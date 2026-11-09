#!/usr/bin/env python3
"""يجمع src/ + الخطوط في ملف index.html واحد في جذر المستودع."""
import base64, pathlib, sys

root = pathlib.Path(__file__).resolve().parent.parent
src = root / 'src'
fonts = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else root / 'src' / 'fonts'


def b64(p):
    return base64.b64encode(pathlib.Path(p).read_bytes()).decode()


face = ''
for fam, fn, w in [('Amiri', 'amiri-arabic-400-normal.woff2', 400), ('Tajawal', 'tajawal-arabic-700-normal.woff2', 700), ('Tajawal', 'tajawal-arabic-800-normal.woff2', 800)]:
    face += "@font-face{font-family:'%s';font-weight:%d;font-style:normal;font-display:block;src:url(data:font/woff2;base64,%s) format('woff2');}\n" % (fam, w, b64(fonts / fn))

html = f"""<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no,viewport-fit=cover">
<meta name="theme-color" content="#6c4bff">
<title>لغتي – الصف الثالث</title>
<style>
{face}
{(src / 'app.css').read_text(encoding='utf-8')}
</style>
</head>
<body>
<div id="app" data-screen="home">
  <header id="top">
    <button id="back" aria-label="رجوع">➡️</button>
    <div id="title">لُغَتِي</div>
    <button id="starsBtn" aria-label="النجوم">⭐ <span id="starCount">٠</span></button>
    <button id="gear" aria-label="للوالدين">⚙️</button>
  </header>
  <main id="screen"></main>
</div>
<div id="toast"></div>
<script>
{(src / 'content.js').read_text(encoding='utf-8')}
</script>
<script>
{(src / 'engine.js').read_text(encoding='utf-8')}
</script>
<script>
{(src / 'ui.js').read_text(encoding='utf-8')}
</script>
</body>
</html>
"""
out = root / 'index.html'
out.write_text(html, encoding='utf-8')
print('wrote', out, len(html) // 1024, 'KB')
