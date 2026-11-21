#!/usr/bin/env python3
"""Иконки лаунчера из портрета: usage make_icons.py <richelieu.txt> <res dir>"""
import sys, os, subprocess, tempfile
sys.path.insert(0, os.path.dirname(__file__))
from preview import svg
from PIL import Image, ImageDraw

txt, res = sys.argv[1], sys.argv[2]
S = 768
s = svg(txt, 0, 0, crop='38 12 124 124')
js = """const {chromium}=require('playwright');(async()=>{const b=await chromium.launch();const p=await b.newPage({viewport:{width:%d,height:%d}});
await p.setContent('<body style="margin:0">'+%s.replace('<svg','<svg width="%d" height="%d"')+'</body>');await p.screenshot({path:'/tmp/_icon.png'});await b.close()})()""" % (S, S, repr(s), S, S)
f = tempfile.NamedTemporaryFile('w', suffix='.js', delete=False, dir='/tmp'); f.write(js); f.close()
subprocess.check_call(['node', f.name], env=dict(os.environ, NODE_PATH=subprocess.check_output(['npm', 'root', '-g']).decode().strip()))
os.unlink(f.name)
src = Image.open('/tmp/_icon.png').convert('RGBA')
for dens, px in (('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)):
    big = src.resize((px * 4, px * 4), Image.LANCZOS)
    mask = Image.new('L', big.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, big.size[0] - 1, big.size[1] - 1], radius=px * 4 * 0.22, fill=255)
    big.putalpha(mask)
    out = big.resize((px, px), Image.LANCZOS)
    d = os.path.join(res, 'mipmap-' + dens); os.makedirs(d, exist_ok=True)
    out.save(os.path.join(d, 'ic_launcher.png'))
print('icons ok')
