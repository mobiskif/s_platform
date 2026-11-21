#!/usr/bin/env python3
"""Генерирует векторное лицо кардинала Ришельё (слои путей) -> assets/richelieu.txt

Формат строки: группа|заливка|прозрачность|обводка|толщина|путь   ('-' = нет)
Группы: s (статика), brows (брови), lids (веки), mouth (полость рта), jaw (нижняя губа и бородка)
Путь: только абсолютные M L C Q Z, числа через пробел.
Система координат: 200 x 240, центр лица x = 100.
"""
import os, sys, json, re

META = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'face_meta.json')))

def shift(d, dy):
    """Сдвигает путь по y (в путях пары x y идут подряд)."""
    out, n = [], 0
    for t in d.split():
        if re.fullmatch(r'-?[0-9.]+', t):
            out.append('%g' % (float(t) + (dy if n % 2 else 0))); n += 1
        else:
            out.append(t)
    return ' '.join(out)

K = 0.5523  # константа Безье для окружности

def ell(cx, cy, rx, ry):
    return ("M {a} {cy} C {a} {y1} {x1} {t} {cx} {t} C {x2} {t} {b} {y1} {b} {cy} "
            "C {b} {y2} {x2} {bt} {cx} {bt} C {x1} {bt} {a} {y2} {a} {cy} Z").format(
        cx=cx, cy=cy, a=cx - rx, b=cx + rx, t=cy - ry, bt=cy + ry,
        x1=cx - rx * K, x2=cx + rx * K, y1=cy - ry * K, y2=cy + ry * K)

L = []  # (group, fill, opacity, stroke, sw, path)
def add(g, d, fill='-', op=1, stroke='-', sw=0):
    L.append((g, fill, op, stroke, sw, d))

SKIN, SKIN_D = META['skin'], '#a87b5a'
RED, RED_D = '#b5121b', '#6e0710'
HAIR = '#3b2f2a'

# --- фон: тёмный бархат + свечение за головой
add('s', 'M 0 0 L 200 0 L 200 240 L 0 240 Z', '#240910')
add('s', ell(100, 105, 92, 108), '#5a1822', 0.85)
add('s', ell(100, 105, 70, 86), '#7a2330', 0.45)

# --- плечи: красная мантелетта
add('s', 'M 0 240 L 0 214 C 22 192 55 180 76 172 L 124 172 C 145 180 178 192 200 214 L 200 240 Z', RED)
add('s', 'M 0 240 L 0 226 C 40 212 80 206 100 206 C 120 206 160 212 200 226 L 200 240 Z', RED_D, 0.35)
# --- белый рочет (воротник)
add('s', 'M 66 168 L 134 168 L 146 192 C 124 204 76 204 54 192 Z', '#f3eee2')
add('s', 'M 66 168 L 134 168 L 140 180 C 120 190 80 190 60 180 Z', '#c9c0ad', 0.45)
# --- красный вырез мантелетты поверх воротника (V)
add('s', 'M 54 192 C 76 204 124 204 146 192 L 150 200 C 124 214 76 214 50 200 Z', RED)
# --- шея
add('s', 'M 84 140 L 82 172 C 90 184 110 184 118 172 L 116 140 Z', SKIN)
add('s', 'M 83 150 L 117 150 L 118 172 C 110 184 90 184 82 172 Z', '#6b4630', 0.3)
add('s', ell(100, 156, 22, 8), '#4a2c1a', 0.28)
# --- пуговицы мантелетты
for i in range(5):
    add('s', ell(100, 214 + i * 7.5, 1.6, 1.6), RED_D)
# --- цепь и золотой наперсный крест
add('s', 'M 66 184 C 76 200 92 208 100 216', '-', 1, '#d9b13f', 1.4)
add('s', 'M 134 184 C 124 200 108 208 100 216', '-', 1, '#d9b13f', 1.4)
add('s', 'M 98 214 L 102 214 L 102 220 L 107 220 L 107 224 L 102 224 L 102 236 L 98 236 L 98 224 L 93 224 L 93 220 L 98 220 Z', '#e2bb45', 1, '#8d6a12', 0.6)

# --- ФОТО: здесь рисуется лицо из assets/face.png (анимируется в RichelieuView)
add('photo', ' '.join('%s=%s' % (k, ','.join('%g' % x for x in v) if isinstance(v, (list, tuple)) else v)
                      for k, v in sorted(META.items())))

# --- шапочка (бирета) кардинала
BIR_DY = 20
BIR = ('M 53 70 C 49 52 57 36 74 32 C 80 24 92 21 100 27 C 108 21 120 24 126 32 '
       'C 143 36 151 52 147 70 C 130 63 70 63 53 70 Z')
add('s', shift(BIR, BIR_DY), '#c1121f')
add('s', shift('M 53 70 C 70 63 130 63 147 70 L 146 74 C 128 67 72 67 54 74 Z', BIR_DY), '#8f0d18')
add('s', shift('M 100 27 C 99 40 100 52 100 64', BIR_DY), '-', 1, '#7a0a14', 1.5)
add('s', shift('M 74 32 C 78 44 80 56 82 65', BIR_DY), '-', 1, '#7a0a14', 1.5)
add('s', shift('M 126 32 C 122 44 120 56 118 65', BIR_DY), '-', 1, '#7a0a14', 1.5)
add('s', shift('M 62 46 C 66 40 72 36 78 34', BIR_DY), '-', 0.35, '#ff7b7b', 2.0)    # блик

def write(path):
    with open(path, 'w', encoding='utf-8') as f:
        for g, fill, op, stroke, sw, d in L:
            f.write('|'.join([g, fill, '%g' % op, stroke, '%g' % sw, d]) + '\n')

if __name__ == '__main__':
    write(sys.argv[1] if len(sys.argv) > 1 else 'richelieu.txt')
