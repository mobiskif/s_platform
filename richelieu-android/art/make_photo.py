#!/usr/bin/env python3
"""Готовит фото для «говорящего» аватара: assets/face.jpg + assets/avatar.properties.
usage: make_photo.py <photo_upright.png> <assets dir>
Ориентиры (в пикселях исходного фото) подобраны вручную для конкретного снимка."""
import sys, os
import numpy as np
from PIL import Image

photo, assets = sys.argv[1], sys.argv[2]
im = Image.open(photo).convert('RGB')

BOX = (390, 150, 1740, 1650)           # какую часть фото показывать (голова и плечи)
SC = 0.5                               # масштаб растра в приложении
EYE_L, EYE_R = (975, 815), (1295, 785)
MOUTH_X, MOUTH_Y = 1160, 1130          # линия смыкания губ
CORNER_L, CORNER_R = 1030, 1290        # уголки рта
FACE_C = (1175, 1000)                  # центр лица (для выравнивания в окне)
CHIN_Y = 1365                          # низ подбородка
STEP = 10                              # шаг сетки деформации (px растра)

c = im.crop(BOX)
out = c.resize((round(c.size[0] * SC), round(c.size[1] * SC)), Image.LANCZOS)
os.makedirs(assets, exist_ok=True)
out.save(os.path.join(assets, 'face.jpg'), quality=88, optimize=True)

def bp(x, y): return ((x - BOX[0]) * SC, (y - BOX[1]) * SC)
lid = np.asarray(im.crop((1240, 720, 1340, 750))).reshape(-1, 3).mean(0)   # тон кожи века/лба
mouthy = round(bp(0, MOUTH_Y)[1] / STEP) * STEP
P = {
    'w': out.size[0], 'h': out.size[1], 'step': STEP,
    'mouthX': bp(MOUTH_X, 0)[0], 'mouthY': mouthy,
    'cornerL': bp(CORNER_L, 0)[0], 'cornerR': bp(CORNER_R, 0)[0],
    'eyeLx': bp(*EYE_L)[0], 'eyeLy': bp(*EYE_L)[1], 'eyeRx': bp(*EYE_R)[0], 'eyeRy': bp(*EYE_R)[1],
    'eyeW': 51 * SC, 'eyeH': 25 * SC,
    'faceX': bp(*FACE_C)[0], 'faceY': bp(*FACE_C)[1],
    'jaw': 67 * SC, 'sigma': 250 * SC,
    'chinY': bp(0, CHIN_Y)[1], 'decay': 200 * SC,
    'lid': '#%02x%02x%02x' % tuple(int(v) for v in lid),
}
with open(os.path.join(assets, 'avatar.properties'), 'w') as f:
    for k, v in P.items():
        f.write('%s=%s\n' % (k, ('%g' % v) if not isinstance(v, str) else v))
print(P)
