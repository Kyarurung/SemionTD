"""식물 빌더 연출 텍스처 생성기.

마왕 연출 생성기(make_demon_lord_vfx.py)의 캔버스·램프·섬광 함수를 쓰고, 물방울·꽃잎·잎·포자처럼 식물에 필요한
모양은 여기서 그립니다. 도트 규칙(작은 해상도, 끊은 색·투명도)은 같습니다.

출력: src/main/resources/semiontd/vfx/plant/<이름>.png
실행: python tools/vfx-textures/make_plant_vfx.py [--sheet out.png]
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_demon_lord_vfx as base  # noqa: E402
from make_demon_lord_vfx import Canvas, alpha_step, hexc, ramp  # noqa: E402

base.OUT = os.path.join(base.ROOT, "src", "main", "resources", "semiontd", "vfx", "plant")

WATER = [hexc(c) for c in ("0e2a4a", "1d5a8c", "3a8fd0", "7cc4f2", "c8ecff", "ffffff")]
LEAF = [hexc(c) for c in ("12300f", "235c1b", "3f8f2a", "72c145", "b8ec84", "f0ffd8")]
VINE = [hexc(c) for c in ("1a2a10", "2f4f1a", "4f7a28", "7fae3c")]
TULIP = [hexc(c) for c in ("4a0a12", "8f1a24", "d8343a", "ff6b5e", "ffb4a4", "fff0ea")]
LILAC = [hexc(c) for c in ("2e1640", "5a2f80", "9460c4", "c79bf0", "ecd9ff", "ffffff")]
SAND = [hexc(c) for c in ("5a4424", "8c6c3c", "c2a064", "e2c98e", "f4e6c2", "fffaf0")]
SPORE = [hexc(c) for c in ("2a1f33", "4e3a5e", "7c6390", "b09cc4", "e2d8ee", "ffffff")]
MUSHROOM = [hexc(c) for c in ("4a0808", "8e1414", "d83030", "ff7060", "ffd0c8", "ffffff")]
EARTH = [hexc(c) for c in ("2a1d12", "4f3a26", "7d6243", "a88c68", "d6c3a3", "f3eadb")]
WIND = [hexc(c) for c in ("2f4a3a", "5f8f72", "9fcfae", "d4f2dc", "f2fff6", "ffffff")]
BAMBOO = [hexc(c) for c in ("1d3a12", "3a6a1e", "5f9a2e", "92c850", "d0f08c")]


def water_drop():
    """물병 식물이 쏘는 물방울. 위가 뾰족한 눈물 모양에 왼쪽 위 반사광."""
    c = Canvas(16, 16)

    def fn(x, y):
        dx, dy = x - 8.0, y - 9.5
        r = math.hypot(dx, dy)
        body = r <= 5.2
        tip = y < 9.5 and abs(dx) <= (y - 1.5) * 0.62
        if not (body or tip) or y < 1.5:
            return None
        light = 0.35 + 0.5 * max(0.0, 1.0 - math.hypot(x - 6.2, y - 8.0) / 4.5)
        if r > 4.4 and body:
            light = 0.2
        return ramp(WATER, light), 255
    c.field(fn)
    c.put(6, 7, WATER[5], 255)
    c.put(6, 8, WATER[4], 255)
    return c.save("water_drop")


def water_ring():
    """물 튀는 고리: 물결치는 두 겹 테와 바깥으로 튄 물방울 점."""
    rnd = random.Random("water_ring")
    c = Canvas(64, 64)
    for y in range(64):
        for x in range(64):
            dx, dy = x + 0.5 - 32, y + 0.5 - 32
            r = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            wave = math.sin(a * 10.0) * 1.2
            if 26.0 + wave < r < 28.6 + wave:
                c.put(x, y, WATER[4], 255)
            elif 24.6 + wave < r <= 26.0 + wave:
                c.put(x, y, WATER[2], 200)
            elif 17.0 + wave * 0.6 < r < 18.4 + wave * 0.6 and int((a + math.pi) / (math.tau / 36)) % 2 == 0:
                c.put(x, y, WATER[3], 150)
    for _ in range(18):
        a = rnd.uniform(0, math.tau)
        r = rnd.uniform(29.5, 31.2)
        cx, cy = int(32 + math.cos(a) * r), int(32 + math.sin(a) * r)
        c.put(cx, cy, WATER[5], 255)
        c.over(cx + 1, cy, WATER[3], 190)
    return c.save("water_ring")


def vine_ring():
    """포충낭 덩굴 고리: 두 가닥이 꼬인 덩굴 테에 잎이 드문드문 돋아 있습니다(물병 식물의 속박 범위)."""
    c = Canvas(64, 64)
    for y in range(64):
        for x in range(64):
            dx, dy = x + 0.5 - 32, y + 0.5 - 32
            r = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            s1 = 28.0 + math.sin(a * 7.0) * 1.3
            s2 = 28.0 - math.sin(a * 7.0) * 1.3
            if abs(r - s1) < 0.9:
                c.put(x, y, VINE[3], 255)
            elif abs(r - s2) < 0.9:
                c.over(x, y, VINE[2], 255)
    for i in range(14):
        a = math.tau * i / 14 + 0.12
        for k in range(4):
            rr = 29.5 + k * 0.8
            ox = math.cos(a + 0.06 * k) * rr
            oy = math.sin(a + 0.06 * k) * rr
            color = LEAF[4] if k < 2 else LEAF[3]
            c.over(int(32 + ox), int(32 + oy), color, 255)
            c.over(int(32 + ox + math.cos(a + 1.6)), int(32 + oy + math.sin(a + 1.6)), LEAF[2], 255)
    return c.save("vine_ring")


def petal(name, colors):
    """꽃잎 한 장(빌보드). 뿌리가 좁고 끝이 둥글며, 가운데 결이 밝습니다."""
    c = Canvas(16, 16)

    def fn(x, y):
        t = (y - 2.0) / 12.0                              # 0 = 끝(위), 1 = 뿌리(아래)
        if t < 0.0 or t > 1.0:
            return None
        half = 5.2 * math.sin(math.pi * (0.2 + 0.8 * (1.0 - t))) ** 0.8 * (1.0 - 0.55 * t)
        dx = abs(x - 8.0)
        if dx > half:
            return None
        light = 0.35 + 0.45 * (1.0 - dx / max(half, 0.4)) + 0.2 * (1.0 - t)
        if dx < 0.6 and t > 0.25:
            light = 0.95
        return ramp(colors, light), 255
    c.field(fn)
    return c.save(name)


def petal_ring(name, colors):
    """꽃잎 고리: 여덟 장 꽃잎 모양으로 테가 물결치는 고리(튤립 계열 광역). 속은 비고 꽃심 점이 둘러 있습니다."""
    c = Canvas(64, 64)
    for y in range(64):
        for x in range(64):
            dx, dy = x + 0.5 - 32, y + 0.5 - 32
            r = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            lobe = abs(math.cos(a * 4.0))                 # 여덟 장
            outer = 24.0 + 7.5 * lobe ** 0.7
            if outer - 2.2 < r <= outer:
                c.put(x, y, colors[4], 255)
            elif outer - 4.2 < r <= outer - 2.2:
                c.put(x, y, colors[2], 230)
            elif outer - 9.0 < r <= outer - 4.2 and lobe > 0.5:
                c.put(x, y, colors[1], alpha_step(0.35))
    for i in range(16):
        a = math.tau * (i + 0.5) / 16
        cx, cy = int(32 + math.cos(a) * 18), int(32 + math.sin(a) * 18)
        c.put(cx, cy, hexc("ffe46a"), 255)
    return c.save(name)


def pollen():
    """꽃가루 뭉치(라일락). 작은 알갱이 몇 개가 뭉쳐 있고 가운데가 밝습니다."""
    c = Canvas(16, 16)
    rnd = random.Random("pollen")
    for bx, by, br in ((8, 8, 4.2), (5, 6, 2.6), (11, 7, 2.8), (7, 11, 2.6), (11, 11, 2.2)):
        for y in range(16):
            for x in range(16):
                d = math.hypot(x + 0.5 - bx, y + 0.5 - by)
                if d <= br:
                    c.over(x, y, ramp(LILAC, 0.45 + 0.45 * (1.0 - d / br)), 255 if d < br - 0.9 else 190)
    for _ in range(10):
        a = rnd.uniform(0, math.tau)
        r = rnd.uniform(5.0, 7.2)
        c.over(int(8 + math.cos(a) * r), int(8 + math.sin(a) * r), LILAC[4], 190)
    c.put(8, 8, LILAC[5], 255)
    c.put(7, 8, LILAC[4], 255)
    return c.save("pollen")


def leaf():
    """치유 잎사귀(빌보드). 비스듬한 타원 잎에 가운데 잎맥."""
    c = Canvas(16, 16)

    def fn(x, y):
        u = (x - 8.0) * 0.7071 + (y - 8.0) * 0.7071
        v = -(x - 8.0) * 0.7071 + (y - 8.0) * 0.7071
        if (u / 6.8) ** 2 + (v / 3.3) ** 2 > 1.0:
            return None
        light = 0.35 + 0.45 * (1.0 - abs(v) / 3.3)
        if abs(v) < 0.5:
            light = 0.85
        return ramp(LEAF, light), 255
    c.field(fn)
    c.put(3, 3, VINE[2], 255)                             # 꼭지
    return c.save("leaf")


def heal_ring():
    """잔디 지원 파동: 옅은 초록 테 위에 새싹 모양 눈금이 돋은 고리."""
    c = Canvas(64, 64)
    for y in range(64):
        for x in range(64):
            r = math.hypot(x + 0.5 - 32, y + 0.5 - 32)
            if 29.4 < r < 31.2:
                c.put(x, y, LEAF[4], 255)
            elif 27.0 < r <= 29.4:
                c.put(x, y, LEAF[2], 150)
            elif 20.0 < r <= 27.0:
                c.put(x, y, LEAF[2], alpha_step((r - 20.0) / 7.0 * 0.3))
    for i in range(12):
        a = math.tau * i / 12
        cx, cy = 32 + math.cos(a) * 27.5, 32 + math.sin(a) * 27.5
        for k in range(3):
            c.put(int(cx - math.cos(a) * k), int(cy - math.sin(a) * k), LEAF[5] if k == 0 else LEAF[3], 255)
        c.put(int(cx - math.cos(a) * 2 + math.cos(a + 1.57)), int(cy - math.sin(a) * 2 + math.sin(a + 1.57)), LEAF[4], 255)
    return c.save("heal_ring")


def puff(name, colors, seed):
    """뭉게 먼지 한 덩이(모래·흙)."""
    c = Canvas(16, 16)
    rnd = random.Random(seed)
    blobs = [(8, 9, 5.0)] + [(8 + rnd.uniform(-3.5, 3.5), 8 + rnd.uniform(-3.5, 2.0), rnd.uniform(2.6, 3.6)) for _ in range(3)]

    def fn(x, y):
        best = 0.0
        for bx, by, br in blobs:
            best = max(best, 1.0 - math.hypot(x - bx, y - by) / br)
        if best <= 0.0:
            return None
        light = 0.3 + 0.5 * best + (0.15 if y < 8 else 0.0)
        return ramp(colors, light), alpha_step(0.4 + best)
    c.field(fn)
    return c.save(name)


def spore():
    """떠다니는 포자(균사 지뢰). 밝은 알갱이와 옅은 빛무리."""
    c = Canvas(16, 16)

    def fn(x, y):
        r = math.hypot(x - 8, y - 8)
        if r > 6.5:
            return None
        if r < 2.0:
            return SPORE[5], 255
        if r < 3.2:
            return SPORE[4], 255
        return SPORE[3], alpha_step((6.5 - r) / 6.5 * 0.7)
    c.field(fn)
    return c.save("spore")


def spore_ring():
    """포자 폭발 고리: 매끈한 테 대신 뭉게뭉게한 포자 구름이 고리를 이루고, 붉은 버섯 반점이 섞여 있습니다."""
    rnd = random.Random("spore_ring")
    c = Canvas(64, 64)
    a = 0.0
    while a < math.tau:
        r = 27.0 + rnd.uniform(-1.5, 1.5)
        size = rnd.uniform(2.2, 3.8)
        cx, cy = 32 + math.cos(a) * r, 32 + math.sin(a) * r
        for dy in range(-4, 5):
            for dx in range(-4, 5):
                d = math.hypot(dx, dy)
                if d <= size:
                    light = 0.35 + 0.55 * (1.0 - d / size) + (0.1 if dy < 0 else 0.0)
                    c.over(int(cx + dx), int(cy + dy), ramp(SPORE, light), 255 if d < size - 0.8 else 190)
        a += rnd.uniform(0.18, 0.3)
    for i in range(7):
        a = math.tau * i / 7 + 0.3
        cx, cy = int(32 + math.cos(a) * 27), int(32 + math.sin(a) * 27)
        for dy in range(-1, 2):
            for dx in range(-1, 2):
                if abs(dx) + abs(dy) <= 1:
                    c.put(cx + dx, cy + dy, MUSHROOM[2], 255)
        c.put(cx, cy, MUSHROOM[4], 255)
    for _ in range(40):
        a = rnd.uniform(0, math.tau)
        r = rnd.uniform(8, 22)
        c.over(int(32 + math.cos(a) * r), int(32 + math.sin(a) * r), SPORE[3], 130)
    return c.save("spore_ring")


def bamboo_leaf():
    """판다 돌진에 흩날리는 대나무 잎. 가늘고 긴 창 모양."""
    c = Canvas(16, 16)

    def fn(x, y):
        u = (x - 8.0) * 0.8 + (y - 8.0) * 0.6
        v = -(x - 8.0) * 0.6 + (y - 8.0) * 0.8
        t = (u + 7.0) / 14.0
        if t < 0.0 or t > 1.0:
            return None
        half = 2.2 * math.sin(math.pi * t) ** 0.7
        if abs(v) > half:
            return None
        return ramp(BAMBOO, 0.4 + 0.5 * (1.0 - abs(v) / max(half, 0.3))), 255
    c.field(fn)
    return c.save("bamboo_leaf")


def wind_arc():
    """판다 돌진 앞의 바람 초승달(바닥 판). 볼록한 쪽이 앞(+Z, 그림 아래)이고 가운데가 두껍습니다."""
    c = Canvas(64, 64)

    def fn(x, y):
        dx, dy = x - 32.0, y - 32.0
        outer = math.hypot(dx, dy)
        inner = math.hypot(dx, dy + 9.0)
        if outer > 31.5 or inner < 29.5 or dy < 0.0:
            return None
        edge = 1.0 - min(1.0, (31.5 - outer) / 10.0)
        middle = 1.0 - min(1.0, abs(dx) / 31.5)
        heat = 0.2 + 0.55 * edge + 0.3 * middle
        if (int(x) + int(y) * 3) % 7 == 0 and heat < 0.6:
            return None
        return ramp(WIND, min(1.0, heat)), alpha_step(0.3 + 0.6 * min(1.0, heat))
    c.field(fn)
    return c.save("wind_arc")


TEXTURES = [
    water_drop, water_ring, vine_ring,
    lambda: petal("petal_tulip", TULIP),
    lambda: petal("petal_lilac", LILAC),
    lambda: petal_ring("petal_ring", TULIP),
    pollen, leaf, heal_ring,
    lambda: base.flash("leaf_flash", LEAF),
    lambda: puff("sand_puff", SAND, "sand"),
    lambda: puff("dust_puff", EARTH, "dust"),
    spore, spore_ring,
    lambda: base.flash("spore_flash", MUSHROOM),
    lambda: base.flash("water_flash", WATER),
    lambda: base.flash("tulip_flash", TULIP),
    bamboo_leaf,
    lambda: base.wall("wall_water", WATER, "water"),
    lambda: base.wall("wall_tulip", TULIP, "tulip"),
    lambda: base.wall("wall_leaf", LEAF, "leaf"),
    lambda: base.wall("wall_spore", SPORE, "spore"),
    lambda: base.beam("pollen_streak", LILAC),
    lambda: base.beam("wind_streak", WIND),
    wind_arc,
]


if __name__ == "__main__":
    made = []
    for make in TEXTURES:
        result = make()
        made.extend(result if isinstance(result, list) else [result])
    print("wrote", len(made), "textures to", base.OUT)
    if "--sheet" in sys.argv:
        base.contact_sheet(made, sys.argv[sys.argv.index("--sheet") + 1])
