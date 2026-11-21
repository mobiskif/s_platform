#!/usr/bin/env python3
"""Рендерит richelieu.txt (+ фото-аватар) в PNG через Chromium — для превью и иконки.
usage: preview.py <richelieu.txt> <out.png> [mouth 0..1] [blink 0..1] [size]
Алгоритм деформации челюсти и положения рта/век повторяет RichelieuView.java."""
import sys, subprocess, tempfile, os, base64, io, math
import numpy as np
from PIL import Image

JAW_PX = 40.0   # макс. раскрытие челюсти в пикселях растра лица (как JAW_PX в RichelieuView)

def parse_meta(s):
    m = {}
    for kv in s.split():
        k, v = kv.split('=')
        m[k] = [float(x) for x in v.split(',')] if ',' in v else (v if v.startswith('#') else float(v))
    return m

def smooth(t):
    t = min(1.0, max(0.0, t)); return t * t * (3 - 2 * t)

def jaw_w(y, ym):  # вертикальный вес: 0 выше губ, 1 ниже
    return smooth((y - (ym - 6)) / 12.0)

def jaw_h(x, mx):  # горизонтальное затухание
    return math.exp(-(((x - mx) / 190.0) ** 2))

def warp(img, meta, d):
    """Сдвиг нижней части лица вниз на d·w·h (обратная выборка, билинейно)."""
    a = np.asarray(img.convert('RGBA')).astype(float)
    H, W = a.shape[:2]
    ym, mx = meta['mouthY'], meta['mouthX']
    xs = np.arange(W); hx = np.exp(-(((xs - mx) / 190.0) ** 2))
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    src = yy.copy()
    for _ in range(6):
        t = np.clip((src - (ym - 6)) / 12.0, 0, 1); w = t * t * (3 - 2 * t)
        src = yy - d * w * hx[None, :]
    y0 = np.clip(np.floor(src).astype(int), 0, H - 1); y1 = np.clip(y0 + 1, 0, H - 1); f = (src - np.floor(src))[..., None]
    out = a[y0, xx.astype(int)] * (1 - f) + a[y1, xx.astype(int)] * f
    return Image.fromarray(out.clip(0, 255).astype('uint8'), 'RGBA')

def photo_svg(meta, face_png, mouth, blink):
    img = Image.open(face_png)
    d = JAW_PX * mouth
    if d > 0.5: img = warp(img, meta, d)
    buf = io.BytesIO(); img.save(buf, 'PNG'); b64 = base64.b64encode(buf.getvalue()).decode()
    u, bx, by = meta['u'], meta['bx'], meta['by']
    g = '<g transform="translate(%g %g) scale(%g)">' % (bx, by, u)
    g += '<image width="%d" height="%d" href="data:image/png;base64,%s"/>' % (img.size[0], img.size[1], b64)
    ym, mx, cl, cr = meta['mouthY'], meta['mouthX'], meta['cornerL'], meta['cornerR']
    if d > 1.5:
        bot = ym + d + 6
        g += ('<path d="M %g %g Q %g %g %g %g C %g %g %g %g %g %g Z" fill="#3a0c10"/>' %
              (cl + 4, ym, mx, ym - 5, cr - 4, ym, cr - 12, ym + (bot - ym) * 1.33, cl + 12, ym + (bot - ym) * 1.33, cl + 4, ym))
        g += '<rect x="%g" y="%g" width="76" height="%g" rx="3" fill="#d9cdb5" fill-opacity=".85"/>' % (mx - 18, ym - 2, 3 + min(d * 0.2, 6))
        g += '<ellipse cx="%g" cy="%g" rx="36" ry="%g" fill="#a94a4a" fill-opacity=".9"/>' % (mx, ym + d * 0.72, d * 0.24)
    if blink > 0.02:
        for ex, ey in (meta['eyeL'], meta['eyeR']):
            rx, ry = meta['eyeRx'], meta['eyeRy']
            h = ry * blink
            g += ('<ellipse cx="%g" cy="%g" rx="%g" ry="%g" fill="%s" fill-opacity=".95"/>' % (ex, ey - ry + h, rx, h, meta['lid']))
            if blink > 0.5:
                g += '<path d="M %g %g Q %g %g %g %g" fill="none" stroke="#3a2a24" stroke-width="2" stroke-linecap="round"/>' % (ex - rx * .85, ey - ry + 2 * h, ex, ey - ry + 2 * h + 3, ex + rx * .85, ey - ry + 2 * h)
    return g + '</g>'

def svg(txt, mouth=0.0, blink=0.0, crop=None):
    out = []
    face_png = os.path.join(os.path.dirname(os.path.abspath(txt)), 'face.png')
    for line in open(txt, encoding='utf-8'):
        g, fill, op, stroke, sw, d = line.rstrip('\n').split('|')
        if g == 'photo':
            out.append(photo_svg(parse_meta(d), face_png, mouth, blink)); continue
        op = float(op)
        a = 'd="%s" ' % d
        a += 'fill="%s" ' % fill if fill != '-' else 'fill="none" '
        if fill != '-': a += 'fill-opacity="%g" ' % op
        if stroke != '-': a += 'stroke="%s" stroke-width="%s" stroke-opacity="%g" stroke-linecap="round" ' % (stroke, sw, op)
        out.append('<path %s/>' % a)
    vb = crop or '0 0 200 240'
    return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="%s">%s</svg>' % (vb, ''.join(out))

if __name__ == '__main__':
    txt, png = sys.argv[1], sys.argv[2]
    mouth = float(sys.argv[3]) if len(sys.argv) > 3 else 0
    blink = float(sys.argv[4]) if len(sys.argv) > 4 else 0
    size = int(sys.argv[5]) if len(sys.argv) > 5 else 800
    w, h = size, int(size * 1.2)
    s = svg(txt, mouth, blink)
    js = """const {chromium}=require('playwright');(async()=>{const b=await chromium.launch();const p=await b.newPage({viewport:{width:%d,height:%d}});
await p.setContent('<body style="margin:0">'+%s.replace('<svg','<svg width="%d" height="%d"')+'</body>');await p.screenshot({path:%s});await b.close()})()""" % (
        w, h, repr(s), w, h, repr(png))
    f = tempfile.NamedTemporaryFile('w', suffix='.js', delete=False, dir='/tmp'); f.write(js); f.close()
    subprocess.check_call(['node', f.name], env=dict(os.environ, NODE_PATH=subprocess.check_output(['npm', 'root', '-g']).decode().strip()))
    os.unlink(f.name)
