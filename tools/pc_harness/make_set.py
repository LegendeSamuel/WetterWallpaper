"""Testsatz wie der Handy-Import: background.png (1080x2340), depth.png + sky.png in Modellauflösung.
Aufruf: py -I make_set.py <tools-ordner> <bild> <zielordner>"""
import sys
from collections import deque
import numpy as np
from PIL import Image

sys.path.insert(0, sys.argv[1])
import split_layers as sl  # noqa: E402

photo = sl.portrait_crop(Image.open(sys.argv[2]).convert("RGB"), 0.5)
w = 518
h = round(w * photo.height / photo.width / 14) * 14
x = np.asarray(photo.resize((w, h), Image.BICUBIC), np.float32) / 255
x = ((x - sl.MEAN) / sl.STD).transpose(2, 0, 1)[None]
sess = sl.ort.InferenceSession(str(sl.MODEL), providers=["CPUExecutionProvider"])
d = sess.run(None, {sess.get_inputs()[0].name: x})[0][0]
near = (d - d.min()) / (d.max() - d.min() + 1e-6)

sky = np.zeros_like(near, bool)
q = deque((0, i) for i in range(w) if near[0, i] < 0.008)
for _, i in q:
    sky[0, i] = True
while q:
    yy, xx = q.popleft()
    for ny, nx in ((yy + 1, xx), (yy - 1, xx), (yy, xx + 1), (yy, xx - 1)):
        if 0 <= ny < h and 0 <= nx < w and not sky[ny, nx] and near[ny, nx] < 0.008:
            sky[ny, nx] = True
            q.append((ny, nx))
out = sys.argv[3]
photo.save(out + r"\background.png")
Image.fromarray((np.sqrt(near) * 255).round().astype(np.uint8)).save(out + r"\depth.png")
Image.fromarray((sky * 255).astype(np.uint8)).save(out + r"\sky.png")
print("ok", w, h, "Himmel", sky.mean().round(3))
