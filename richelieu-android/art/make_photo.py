#!/usr/bin/env python3
"""Готовит лицо из фото для аватара: вырез с мягким краем -> assets/face.png и art/face_meta.json.
usage: make_photo.py <photo_upright.png> <assets dir>
Координаты ориентиров заданы в пикселях исходного фото (подобраны вручную для конкретного снимка)."""
import sys, os, json
import numpy as np
from PIL import Image, ImageFilter

photo, assets = sys.argv[1], sys.argv[2]
im = Image.open(photo).convert('RGB')

BOX = (840, 670, 1515, 1390)          # область лица в исходном фото
SC = 0.6                              # масштаб растра в приложении
EYE_L, EYE_R = (975, 815), (1295, 785)
MOUTH_Y, MOUTH_X = 1125, 1160         # линия смыкания губ
CORNER_L, CORNER_R = 1030, 1290       # уголки рта (по x)
UNIT = 0.125                          # единиц холста (200x240) на пиксель исходного фото
ANCHOR_PX, ANCHOR_UNIT = (1175, 800), (100.0, 99.0)

c = im.crop(BOX)
w, h = c.size
# маска: контур лица по ключевым точкам (в пикселях фото), мягкий край
from PIL import ImageDraw
POLY = [(900, 745), (1130, 700), (1390, 735), (1450, 775), (1485, 870), (1488, 1000), (1465, 1120), (1415, 1215), (1345, 1282),
        (1270, 1335), (1200, 1365), (1120, 1342), (1050, 1292), (975, 1230), (930, 1130), (880, 1000), (860, 880)]
mask = Image.new('L', (w, h), 0)
ImageDraw.Draw(mask).polygon([(x - BOX[0], y - BOX[1]) for x, y in POLY], fill=255)
mask = mask.filter(ImageFilter.GaussianBlur(9))
mask = mask.point(lambda v: 0 if v < 40 else min(255, int((v - 40) * 255 / 175)))  # сжать край внутрь
c.putalpha(mask)
out = c.resize((round(w * SC), round(h * SC)), Image.LANCZOS)
os.makedirs(assets, exist_ok=True)
out.save(os.path.join(assets, 'face.png'), optimize=True)

def bp(x, y): return ((x - BOX[0]) * SC, (y - BOX[1]) * SC)
# цвет кожи шеи — среднее по подбородку
chin = np.clip(np.asarray(im.crop((1150, 1290, 1250, 1340))).reshape(-1, 3).mean(0) * 1.18, 0, 255)
lid = np.asarray(im.crop((1240, 720, 1340, 750))).reshape(-1, 3).mean(0)
meta = {
    'bx': ANCHOR_UNIT[0] - (ANCHOR_PX[0] - BOX[0]) * UNIT,
    'by': ANCHOR_UNIT[1] - (ANCHOR_PX[1] - BOX[1]) * UNIT,
    'u': UNIT / SC,
    'mouthY': round(bp(0, MOUTH_Y)[1] / 6) * 6,   # кратно строке сетки (6 px)
    'mouthX': bp(MOUTH_X, 0)[0],
    'cornerL': bp(CORNER_L, 0)[0], 'cornerR': bp(CORNER_R, 0)[0],
    'eyeL': bp(*EYE_L), 'eyeR': bp(*EYE_R),
    'eyeRx': 34 * 0.9, 'eyeRy': 15,
    'skin': '#%02x%02x%02x' % tuple(int(v) for v in chin),
    'lid': '#%02x%02x%02x' % tuple(int(v) for v in lid),
    'bmpW': out.size[0], 'bmpH': out.size[1],
}
json.dump(meta, open(os.path.join(os.path.dirname(__file__), 'face_meta.json'), 'w'), indent=1)
print(meta)
