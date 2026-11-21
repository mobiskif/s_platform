#!/usr/bin/env python3
"""Рендерит richelieu.txt в SVG/PNG (Chromium) — для превью и иконки.
usage: preview.py <richelieu.txt> <out.png> [mouth 0..1] [blink 0..1] [size]"""
import sys, subprocess, tempfile, os

def svg(txt, mouth=0.0, blink=0.0, crop=None):
    out = []
    for line in open(txt, encoding='utf-8'):
        g, fill, op, stroke, sw, d = line.rstrip('\n').split('|')
        op = float(op)
        a = 'd="%s" ' % d
        a += 'fill="%s" ' % fill if fill != '-' else 'fill="none" '
        if fill != '-': a += 'fill-opacity="%g" ' % op
        if stroke != '-': a += 'stroke="%s" stroke-width="%s" stroke-opacity="%g" stroke-linecap="round" ' % (stroke, sw, op)
        el = '<path %s/>' % a
        if g == 'mouth':
            el = '<g transform="translate(0 136) scale(1 %g) translate(0 -136)">%s</g>' % (max(mouth, 0.001), el)
        elif g == 'jaw':
            el = '<g transform="translate(0 %g)">%s</g>' % (mouth * 7, el)
        elif g == 'lids':
            el = '<g transform="translate(0 94) scale(1 %g) translate(0 -94)">%s</g>' % (blink, el)
        out.append(el)
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
