"""마왕 스킬 연출 텍스처 생성기.

손으로 찍은 도트처럼 보이도록, 작은 원본 해상도(16~64px)에서 모양을 거리 함수로 잡은 뒤
색은 4~6단 램프로, 투명도는 몇 단계로만 끊어 칠합니다. 마인크래프트는 텍스처를 늘릴 때
보간하지 않으므로(nearest) 이렇게 끊어 두면 바닐라 도트 느낌이 납니다.

출력: src/main/resources/semiontd/vfx/demon_lord/<이름>.png
실행: python tools/vfx-textures/make_demon_lord_vfx.py [--sheet out.png]
"""
import math
import os
import random
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "src", "main", "resources", "semiontd", "vfx", "demon_lord")


def hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


CRIMSON = [hexc(c) for c in ("3a0710", "7a0f1e", "c21f34", "ff4458", "ff9aa4", "fff0f2")]
VIOLET = [hexc(c) for c in ("1f0a33", "4b1a7a", "8a3fd1", "c07cff", "ecd7ff", "ffffff")]
MAGENTA = [hexc(c) for c in ("3a0a3d", "7d1f86", "d14fd8", "ff8cf5", "ffe3fb", "ffffff")]
SOUL = [hexc(c) for c in ("0a2a33", "146a80", "2fb4d6", "7de3f2", "e6fdff", "ffffff")]
FIRE = [hexc(c) for c in ("5a1206", "a8300c", "e8641a", "ffb13b", "ffe9a0", "fffbe8")]
OBSIDIAN = [hexc(c) for c in ("0b0710", "1d1228", "33204a", "5a2f85")]
STEEL = [hexc(c) for c in ("2a2c31", "4a4e56", "7b808a", "b4b9c2", "e3e7ec", "ffffff")]
SHADOW = [hexc(c) for c in ("050307", "120b16", "22152a", "3a2244")]

ALPHA_STEPS = (0, 70, 130, 190, 255)


def ramp(colors, t):
    t = max(0.0, min(1.0, t))
    return colors[min(len(colors) - 1, int(t * len(colors)))]


def alpha_step(a):
    a = max(0.0, min(1.0, a))
    return ALPHA_STEPS[min(len(ALPHA_STEPS) - 1, int(round(a * (len(ALPHA_STEPS) - 1))))]


class Canvas:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.px = np.zeros((h, w, 4), dtype=np.uint8)

    def put(self, x, y, color, alpha=255):
        if 0 <= x < self.w and 0 <= y < self.h and alpha > 0:
            self.px[y, x] = (*color, alpha)

    def over(self, x, y, color, alpha=255):
        """이미 칠한 칸보다 불투명할 때만 덮습니다."""
        if 0 <= x < self.w and 0 <= y < self.h and alpha > self.px[y, x, 3]:
            self.px[y, x] = (*color, alpha)

    def field(self, fn):
        for y in range(self.h):
            for x in range(self.w):
                r = fn(x + 0.5, y + 0.5)
                if r:
                    color, alpha = r
                    self.put(x, y, color, alpha)

    def save(self, name):
        os.makedirs(OUT, exist_ok=True)
        Image.fromarray(self.px, "RGBA").save(os.path.join(OUT, name + ".png"))
        return name


# ---------------------------------------------------------------- shapes

def slash():
    """부채꼴로 쓸고 나가는 초승달 칼날. 텍스처 아래쪽(= 바닥에 누이면 앞쪽)으로 부풉니다."""
    c = Canvas(64, 32)

    def fn(x, y):
        cx = 32.0
        outer = math.hypot(x - cx, y + 9) - 39.0       # 바깥 원(앞 가장자리), 아래 끝 y≈30
        inner = math.hypot(x - cx, y + 21) - 40.0      # 안쪽 원, 아래 끝 y≈19
        if outer > 0 or inner < 0:
            return None
        depth = -outer / max(0.5, (inner - outer))    # 0 = 앞날, 1 = 뒤쪽
        tip = max(0.0, 1.0 - abs(x - cx) / 33.0)        # 가운데 두껍고 끝으로 갈수록 가늘게
        heat = (1.0 - depth) ** 1.3 * (0.3 + 0.7 * tip)
        if heat < 0.07:
            return None
        return ramp(CRIMSON, heat * 1.1 + 0.05), alpha_step(0.3 + heat)
    c.field(fn)
    return c.save("slash")


def swing_arc_frames(name, colors, frames):
    """플립북용 검 궤적 여러 장. 앞 장일수록 선명하고, 뒤로 갈수록 꼬리부터 사라지며 색이 어두워지고 도트가
    듬성듬성 빠져 흐려집니다. 게임에서는 한 틱마다 한 장씩 바꿔 끼워 흐려지는 것처럼 보입니다."""
    names = []
    for k in range(frames):
        fade = k / max(1, frames - 1)                   # 0 = 첫 장, 1 = 마지막 장
        c = Canvas(64, 64)

        def fn(x, y, fade=fade):
            dx, dy = x - 32.0, y - 32.0
            r = math.hypot(dx, dy)
            if r < 15.0 or r > 31.5:
                return None
            angle = math.degrees(math.atan2(dx, dy))
            if abs(angle) > 55.0:
                return None
            t = (angle + 55.0) / 110.0
            if t < fade * 0.55:                          # 꼬리부터 사라집니다
                return None
            radial = (r - 15.0) / 16.5
            heat = t ** 1.4 * (0.35 + 0.65 * radial ** 1.5)
            if radial > 0.9:
                heat = max(heat, 0.35 + 0.65 * t)
            heat *= 1.0 - fade * 0.45
            if heat < 0.08:
                return None
            # 흐려질수록 도트를 규칙적으로 빼서 성기게 합니다(투명도를 보간하지 못하는 디스플레이용).
            if fade > 0 and ((int(x) * 7 + int(y) * 13) % 10) < fade * 6:
                return None
            return ramp(colors, heat * 1.05 + 0.05), alpha_step((0.2 + heat) * (1.0 - fade * 0.35))
        c.field(fn)
        names.append(c.save(f"{name}_{k}"))
    return names


def wing():
    """박쥐 날개. 부채처럼 한 점에서 뼈가 퍼지지 않도록 실제 박쥐 날개 구조를 따릅니다.

    어깨(왼쪽)에서 위팔·아래팔 뼈가 앞날(위쪽 가장자리)을 따라 손목까지 뻗고, 손가락 뼈 네 가닥은 손목에서
    갈라져 나갑니다. 손가락 사이 막은 끝과 끝을 잇는 가장자리가 안쪽으로 오목하게 처지고, 맨 뒤 막은 몸통(왼쪽
    아래)까지 이어집니다. 손목에는 엄지 발톱이 튀어나옵니다.
    """
    c = Canvas(64, 64)
    shoulder = (3.0, 22.0)
    elbow = (21.0, 11.0)
    wrist = (38.0, 7.0)
    fingers = [(63.0, 9.0), (60.0, 28.0), (48.0, 43.0), (31.0, 50.0)]
    body = (4.0, 38.0)

    def scallop_center(a, b, inside, sag_ratio):
        """a–b 가장자리가 {inside} 쪽으로 처지는 호의 원(중심·반지름). 원 안은 막이 파인 곳입니다."""
        mx, my = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
        half = math.hypot(b[0] - a[0], b[1] - a[1]) / 2
        sag = max(0.5, half * 2 * sag_ratio)
        # 가장자리의 법선 중 막 바깥쪽(inside의 반대)을 고릅니다.
        nx, ny = -(b[1] - a[1]), b[0] - a[0]
        length = math.hypot(nx, ny)
        nx, ny = nx / length, ny / length
        if (inside[0] - mx) * nx + (inside[1] - my) * ny > 0:
            nx, ny = -nx, -ny
        k = (half * half - sag * sag) / (2 * sag)
        cx, cy = mx + nx * k, my + ny * k
        return cx, cy, math.hypot(a[0] - cx, a[1] - cy)

    # 막 조각: 손가락 사이 세 장(손목·끝·끝)과 몸통 쪽 한 장(어깨·팔꿈치·손목·마지막 손가락·몸통)
    panels = []
    for a, b in zip(fingers, fingers[1:]):
        panels.append(([wrist, a, b], (a, b)))
    panels.append(([shoulder, elbow, wrist, fingers[-1], body], (fingers[-1], body)))
    scallops = [scallop_center(a, b, wrist if len(poly) == 3 else elbow, 0.16) for poly, (a, b) in panels]

    def in_poly(x, y, poly):
        inside = False
        for (ax, ay), (bx, by) in zip(poly, poly[1:] + poly[:1]):
            if (ay > y) != (by > y) and x < (bx - ax) * (y - ay) / (by - ay) + ax:
                inside = not inside
        return inside

    for y in range(64):
        for x in range(64):
            px, py = x + 0.5, y + 0.5
            for (poly, _), (cx, cy, radius) in zip(panels, scallops):
                if not in_poly(px, py, poly):
                    continue
                gap = math.hypot(px - cx, py - cy) - radius
                if gap < 0:
                    break
                depth = math.hypot(px - wrist[0], py - wrist[1]) / 52.0
                if gap < 1.1:
                    c.put(x, y, CRIMSON[3], 255)            # 처진 가장자리의 붉은 테
                elif gap < 2.2:
                    c.put(x, y, CRIMSON[1], 235)
                else:
                    shade = SHADOW[3] if depth < 0.35 else SHADOW[2] if depth < 0.7 else SHADOW[1]
                    c.put(x, y, shade, 225)
                break
    # 막의 핏줄: 손가락 사이로 흐르는 가는 붉은 선
    for a, b in zip(fingers, fingers[1:]):
        mid = ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
        vein_end = (wrist[0] + (mid[0] - wrist[0]) * 0.72, wrist[1] + (mid[1] - wrist[1]) * 0.72)
        line(c, wrist, vein_end, CRIMSON[0], 200, width=0)
    # 뼈: 팔(앞날, 굵게)과 손목에서 갈라지는 손가락
    for a, b in ((shoulder, elbow), (elbow, wrist)):
        line(c, a, b, SHADOW[0], 255, width=1)
        line(c, (a[0], a[1] - 1), (b[0], b[1] - 1), CRIMSON[2], 255, width=0)
    for tip in fingers:
        line(c, wrist, tip, SHADOW[0], 255, width=0)
        c.over(int(tip[0]), int(tip[1]), CRIMSON[4], 255)
    # 관절 마디와 손목 엄지 발톱
    for joint, size in ((shoulder, 2), (elbow, 1), (wrist, 1)):
        for dx in range(-size, size + 1):
            for dy in range(-size, size + 1):
                if dx * dx + dy * dy <= size * size:
                    c.put(int(joint[0]) + dx, int(joint[1]) + dy, SHADOW[0], 255)
    for step, (dx, dy) in enumerate(((0, -1), (1, -2), (2, -3), (3, -3))):
        c.put(int(wrist[0]) + dx, int(wrist[1]) + dy, CRIMSON[4] if step == 3 else CRIMSON[2], 255)
    return c.save("wing")


def point_in_tri(px, py, a, b, c):
    def sign(p1, p2, p3):
        return (p1[0] - p3[0]) * (p2[1] - p3[1]) - (p2[0] - p3[0]) * (p1[1] - p3[1])
    d1 = sign((px, py), a, b)
    d2 = sign((px, py), b, c)
    d3 = sign((px, py), c, a)
    neg = d1 < 0 or d2 < 0 or d3 < 0
    pos = d1 > 0 or d2 > 0 or d3 > 0
    return not (neg and pos)


def line(c, a, b, color, alpha, width=0):
    steps = int(max(abs(b[0] - a[0]), abs(b[1] - a[1])) * 2) + 1
    for i in range(steps + 1):
        t = i / steps
        x = a[0] + (b[0] - a[0]) * t
        y = a[1] + (b[1] - a[1]) * t
        for ox in range(-width, width + 1):
            for oy in range(-width, width + 1):
                if abs(ox) + abs(oy) <= width:
                    c.put(int(x) + ox, int(y) + oy, color, alpha)


def beam(name, colors):
    """세로로 뻗는 빛줄기. 가운데 심이 가장 밝고 양 끝은 흐려집니다. 좌우 대칭이라 교차 모델에 씁니다."""
    c = Canvas(16, 64)
    rnd = random.Random(name)

    def fn(x, y):
        across = 1.0 - abs(x - 8.0) / 7.5
        along = min(1.0, min(y, 64 - y) / 10.0)
        heat = across ** 1.6 * along
        if heat < 0.1:
            return None
        return ramp(colors, heat * 1.1), alpha_step(0.25 + heat)
    c.field(fn)
    for _ in range(10):
        x, y = rnd.randint(2, 13), rnd.randint(6, 57)
        c.put(x, y, colors[-2], 255)
    return c.save(name)


def spike():
    """땅에서 솟는 흑요석 가시. 아래가 넓고 위로 뾰족하며, 가운데로 진홍 균열이 빛납니다."""
    c = Canvas(16, 64)

    def fn(x, y):
        half = 7.5 * (y / 64.0) ** 0.9                  # 위(y=0)가 뾰족한 끝, 아래가 넓은 뿌리
        dx = abs(x - 8.0)
        if dx > half:
            return None
        if y < 6:
            return CRIMSON[4], 255
        if dx < 0.9 and y > 10:
            return CRIMSON[3] if (y // 5) % 2 == 0 else CRIMSON[2], 255
        side = (x - 8.0) / max(half, 0.5)
        shade = OBSIDIAN[3] if side < -0.55 else OBSIDIAN[2] if side < 0.1 else OBSIDIAN[1]
        if dx > half - 1.0:
            shade = OBSIDIAN[0]
        return shade, 255
    c.field(fn)
    return c.save("spike")


def claw():
    """움켜쥐는 발톱. 검은 발톱에 핏빛 날이 서 있고 끝이 살짝 안쪽으로 휩니다."""
    c = Canvas(16, 64)

    def fn(x, y):
        t = y / 64.0
        centre = 8.0 + 3.5 * (1.0 - t) ** 2        # 위로 갈수록 한쪽으로 휨
        half = 6.5 * t ** 0.7 + 0.6
        dx = x - centre
        if abs(dx) > half:
            return None
        if dx > half - 1.6:
            return (CRIMSON[4] if y < 20 else CRIMSON[3]), 255
        if dx < -half + 1.0:
            return SHADOW[0], 255
        return (SHADOW[3] if dx > 0 else SHADOW[2]), 255
    c.field(fn)
    return c.save("claw")


def blade():
    """단두대 칼날. 위는 두꺼운 테와 리벳, 아래는 비스듬한 날이 진홍으로 달아 있습니다."""
    c = Canvas(64, 64)
    for y in range(64):
        for x in range(64):
            edge_y = 44 + (x / 63.0) * 16           # 비스듬한 날
            if y > edge_y + 2:
                continue
            if y < 8:
                c.put(x, y, SHADOW[3] if y > 1 else SHADOW[1], 255)
                continue
            if y > edge_y - 1:
                c.put(x, y, CRIMSON[4] if y > edge_y else CRIMSON[3], 255)
                continue
            if y > edge_y - 4:
                c.put(x, y, STEEL[4], 255)
                continue
            band = (x + y // 3) % 22
            shade = STEEL[3] if band < 2 else STEEL[2] if x < 58 else STEEL[1]
            if x < 2 or x > 61:
                shade = STEEL[1]
            c.put(x, y, shade, 255)
    for rx in (8, 24, 40, 56):
        c.put(rx, 4, STEEL[4], 255)
        c.put(rx, 5, STEEL[1], 255)
    # 핏자국
    for (x, y) in [(20, 40), (21, 41), (21, 42), (44, 46), (45, 47), (45, 49), (46, 50)]:
        c.put(x, y, CRIMSON[1], 255)
    return c.save("blade")


def shockwave(name, colors):
    """퍼져 나가는 충격파 고리. 바깥 테가 가장 밝고 안쪽으로 부드럽게 사라집니다."""
    c = Canvas(64, 64)

    def fn(x, y):
        r = math.hypot(x - 32, y - 32)
        if r > 31.5:
            return None
        if r > 29:
            return colors[4], 255
        if r > 27:
            return colors[3], 230
        fade = (r - 17) / 10.0
        if fade <= 0.05:
            return None
        return ramp(colors, 0.25 + fade * 0.5), alpha_step(fade * 0.75)
    c.field(fn)
    for i in range(16):
        a = math.pi * 2 * i / 16
        for k in range(2):
            x = int(32 + math.cos(a) * (25 - k * 2))
            y = int(32 + math.sin(a) * (25 - k * 2))
            c.over(x, y, colors[4], 255)
    return c.save(name)


def arcane_wave(name, colors):
    """마력 파동: 매끈한 테 대신 번개처럼 들쭉날쭉한 바깥 테와, 안쪽으로 가시처럼 찔러 드는 방전 줄기."""
    rnd = random.Random(name)
    c = Canvas(64, 64)
    spikes = 22
    jitter = [rnd.uniform(-2.2, 2.2) for _ in range(spikes)]

    def edge(a):
        # 톱니 반지름: 칸마다 뾰족하게 솟았다 꺼집니다.
        u = (a / math.tau) * spikes
        i = int(u) % spikes
        f = u - int(u)
        tooth = 1.0 - abs(f - 0.5) * 2.0
        return 26.5 + tooth * 3.2 + jitter[i] * 0.5

    for y in range(64):
        for x in range(64):
            dx, dy = x + 0.5 - 32, y + 0.5 - 32
            r = math.hypot(dx, dy)
            a = math.atan2(dy, dx) % math.tau
            e = edge(a)
            if r > e or r > 31.5:
                continue
            gap = e - r
            if gap < 1.1:
                c.put(x, y, colors[4], 255)
            elif gap < 2.4:
                c.put(x, y, colors[3], 225)
            elif r > 19:
                fade = (r - 19) / max(1.0, e - 21)
                c.put(x, y, colors[1], alpha_step(fade * 0.55))
    # 안쪽으로 찔러 드는 방전 줄기
    for i in range(9):
        a = rnd.uniform(0, math.tau)
        r0 = edge(a) - 1
        x, y = 32 + math.cos(a) * r0, 32 + math.sin(a) * r0
        for _ in range(rnd.randint(4, 7)):
            a += rnd.uniform(-0.5, 0.5)
            nx, ny = x - math.cos(a) * 1.6, y - math.sin(a) * 1.6
            line(c, (x, y), (nx, ny), colors[4], 255, width=0)
            x, y = nx, ny
    return c.save(name)


def crack():
    """갈라진 땅. 가운데 그을음 위로 들쭉날쭉한 균열이 퍼지고 균열 속이 진홍으로 빛납니다."""
    c = Canvas(64, 64)
    rnd = random.Random("crack")
    c.field(lambda x, y: (SHADOW[1], alpha_step(0.55 * (1 - math.hypot(x - 32, y - 32) / 13)))
            if math.hypot(x - 32, y - 32) < 12 else None)
    for i in range(9):
        a = math.pi * 2 * i / 9 + rnd.uniform(-0.2, 0.2)
        x, y = 32.0, 32.0
        length = rnd.uniform(20, 30)
        for step in range(int(length)):
            a += rnd.uniform(-0.35, 0.35)
            x += math.cos(a)
            y += math.sin(a)
            glow = 1.0 - step / length
            c.put(int(x), int(y), CRIMSON[3] if glow > 0.55 else CRIMSON[2], 255)
            c.over(int(x) + 1, int(y), SHADOW[0], 230)
            c.over(int(x), int(y) + 1, SHADOW[0], 230)
            if step > 8 and rnd.random() < 0.08:
                bx, by, ba = x, y, a + rnd.choice((-1, 1)) * 0.9
                for _ in range(rnd.randint(3, 7)):
                    bx += math.cos(ba)
                    by += math.sin(ba)
                    c.put(int(bx), int(by), CRIMSON[1], 255)
    return c.save("crack")


def circle(name, colors, star_points, star_skip, runes):
    """바닥 마법진. 두 겹 고리 사이에 룬 눈금, 안쪽에 별(오망성·육망성)."""
    c = Canvas(64, 64)
    for y in range(64):
        for x in range(64):
            r = math.hypot(x + 0.5 - 32, y + 0.5 - 32)
            if 29.5 < r < 31.5:
                c.put(x, y, colors[3], 255)
            elif 24.5 < r < 25.8:
                c.put(x, y, colors[3], 255)
            elif 25.8 <= r <= 29.5:
                c.put(x, y, colors[1], 130)
            elif r < 24.5:
                c.put(x, y, colors[0], 70 if r > 20 else 0)
    # 고리 사이 룬 눈금
    for i in range(runes):
        a = math.pi * 2 * i / runes
        for k in range(3):
            rr = 26.8 + k
            c.put(int(32 + math.cos(a) * rr), int(32 + math.sin(a) * rr), colors[4], 255)
        a2 = a + math.pi / runes
        c.put(int(32 + math.cos(a2) * 27.6), int(32 + math.sin(a2) * 27.6), colors[4], 255)
    # 별
    pts = [(32 + math.sin(math.pi * 2 * i / star_points) * 24.0, 32 - math.cos(math.pi * 2 * i / star_points) * 24.0)
           for i in range(star_points)]
    for i in range(star_points):
        a, b = pts[i], pts[(i + star_skip) % star_points]
        line(c, a, b, colors[4], 255, width=0)
    # 가운데 눈
    for dy in range(-2, 3):
        for dx in range(-2, 3):
            if dx * dx + dy * dy <= 5:
                c.put(32 + dx, 32 + dy, colors[4] if dx * dx + dy * dy <= 1 else colors[2], 255)
    return c.save(name)


def rune():
    """떠오르는 룬 글자 하나."""
    c = Canvas(16, 16)
    glyph = [
        "................",
        "......####......",
        ".....#....#.....",
        "....#..##..#....",
        "...#..#..#..#...",
        "......#..#......",
        ".......##.......",
        "...##########...",
        ".......##.......",
        "......#..#......",
        ".....#....#.....",
        "....#......#....",
        "...#........#...",
        "................",
        "................",
        "................",
    ]
    for y, row in enumerate(glyph):
        for x, ch in enumerate(row):
            if ch == "#":
                c.put(x, y, MAGENTA[4], 255)
                for ox, oy in ((1, 0), (0, 1), (-1, 0), (0, -1)):
                    c.over(x + ox, y + oy, MAGENTA[1], 190)
    return c.save("rune")


def flame(name, colors):
    """일렁이는 불꽃 혀. 아래가 둥글고 위로 뾰족합니다."""
    c = Canvas(16, 16)

    def fn(x, y):
        t = y / 16.0                                     # 0 = 위(끝), 1 = 아래(뿌리)
        cx = 8.0 + math.sin(t * 6.0) * 1.3 * (1 - t)
        half = 7.2 * (math.sin(math.pi * min(1.0, 0.15 + t * 0.95)) ** 0.6) * (0.25 + 0.75 * t)
        dx = abs(x - cx)
        if dx > half or y < 0.5:
            return None
        heat = (1.0 - dx / max(half, 0.3)) * (0.45 + 0.55 * t)
        return ramp(colors, heat + 0.15), 255 if heat > 0.2 else 190
    c.field(fn)
    return c.save(name)


def shard():
    """튀는 핏빛 수정 파편."""
    c = Canvas(16, 16)

    def fn(x, y):
        d = abs(x - 8) / 4.0 + abs(y - 8) / 7.0
        if d > 1.0:
            return None
        light = 1.0 - d
        if x < 8 and y < 8:
            light += 0.35
        return ramp(CRIMSON, 0.2 + light * 0.8), 255
    c.field(fn)
    return c.save("shard")


def flash(name, colors):
    """터지는 섬광. 네 갈래 별빛과 둥근 광륜."""
    c = Canvas(32, 32)

    def fn(x, y):
        dx, dy = x - 16, y - 16
        r = math.hypot(dx, dy)
        star = max(0.0, 1.0 - min(abs(dx), abs(dy)) / 2.2) * max(0.0, 1.0 - r / 15.5)
        glow = max(0.0, 1.0 - r / 9.0)
        heat = max(star, glow)
        if heat < 0.1:
            return None
        return ramp(colors, 0.3 + heat * 0.75), alpha_step(0.3 + heat)
    c.field(fn)
    return c.save(name)


def barrier():
    """육각 방벽판. 반투명한 보라 판에 밝은 테와 안쪽 육각 무늬."""
    c = Canvas(32, 32)

    def hex_dist(x, y):
        x, y = abs(x - 16) / 15.0, abs(y - 16) / 15.5
        return max(x * 0.866 + y * 0.5, y)

    def fn(x, y):
        d = hex_dist(x, y)
        if d > 1.0:
            return None
        if d > 0.9:
            return VIOLET[4], 255
        if abs(d - 0.55) < 0.05:
            return VIOLET[3], 230
        return VIOLET[2], 110 if d > 0.3 else 70
    c.field(fn)
    return c.save("barrier")


def chain():
    """영혼 사슬 고리 하나."""
    c = Canvas(16, 32)

    def fn(x, y):
        dx, dy = (x - 8) / 5.5, (y - 16) / 13.0
        r = math.hypot(dx, dy)
        if 0.62 < r < 1.0:
            return (SOUL[4] if x < 8 else SOUL[3]), 255
        if 0.5 < r <= 0.62:
            return SOUL[1], 190
        return None
    c.field(fn)
    return c.save("chain")


def bolt():
    """하늘에서 내리꽂히는 마탄."""
    c = Canvas(16, 16)

    def fn(x, y):
        r = math.hypot(x - 8, y - 8)
        if r > 7.5:
            return None
        heat = 1.0 - r / 7.5
        return ramp(MAGENTA, 0.15 + heat), alpha_step(0.4 + heat)
    c.field(fn)
    return c.save("bolt")


def feather():
    """흩날리는 검은 깃."""
    c = Canvas(16, 16)

    def fn(x, y):
        dx, dy = (x - 8) / 4.6, (y - 8) / 7.6
        if dx * dx + dy * dy > 1.0:
            return None
        if abs(x - 8) < 0.6:
            return CRIMSON[2], 255
        return (SHADOW[3] if x < 8 else SHADOW[2]), 255
    c.field(fn)
    return c.save("feather")


def spike_block():
    """입체 가시 몸통(정육면체 여섯 면). 흑요석 결 사이로 진홍 균열이 비칩니다."""
    c = Canvas(16, 16)
    rnd = random.Random("spike_block")
    for y in range(16):
        for x in range(16):
            band = (x + y * 2) % 7
            shade = OBSIDIAN[2] if band < 2 else OBSIDIAN[1] if band < 5 else OBSIDIAN[3]
            c.put(x, y, shade, 255)
    x, y = rnd.randint(3, 12), 0
    while y < 16:
        c.put(x, y, CRIMSON[2], 255)
        if rnd.random() < 0.5:
            c.put(x + 1, y, CRIMSON[1], 255)
        x = max(1, min(14, x + rnd.choice((-1, 0, 1))))
        y += 1
    for _ in range(4):
        c.put(rnd.randint(0, 15), rnd.randint(0, 15), CRIMSON[3], 255)
    return c.save("spike_block")


def spike_core():
    """입체 가시 속의 빛나는 심. 가시 끝으로 삐져나와 끝만 붉게 빛납니다."""
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            t = 1.0 - (abs(x - 7.5) + abs(y - 7.5)) / 15.0
            c.put(x, y, ramp(CRIMSON, 0.45 + t * 0.6), 255)
    return c.save("spike_core")


def rift():
    """앞으로 뻗는 땅의 균열. 세로(텍스처 아래위)가 균열이 뻗는 방향이고, 가운데 틈이 진홍으로 끓습니다."""
    c = Canvas(16, 64)
    rnd = random.Random("rift")
    x = 8.0
    for y in range(64):
        x = max(4.0, min(11.0, x + rnd.uniform(-0.9, 0.9)))
        half = 2.2 + 1.2 * math.sin(y * 0.45) ** 2
        for px in range(16):
            d = abs(px + 0.5 - x)
            if d < 0.9:
                c.put(px, y, CRIMSON[4] if (y + px) % 3 else CRIMSON[5], 255)
            elif d < 1.8:
                c.put(px, y, CRIMSON[3], 255)
            elif d < half:
                c.put(px, y, SHADOW[1], 230)
            elif d < half + 1.5:
                c.put(px, y, SHADOW[2], 130)
        if rnd.random() < 0.12:
            bx, by = x, y
            direction = rnd.choice((-1, 1))
            for _ in range(rnd.randint(2, 4)):
                bx += direction
                by += rnd.choice((0, 1))
                c.put(int(bx), int(by), CRIMSON[2], 255)
    return c.save("rift")



def vortex():
    """심연 소용돌이 원반. 가운데로 말려 드는 나선 팔 네 개가 보라·자홍으로 빛나고, 가운데는 캄캄합니다."""
    c = Canvas(64, 64)

    def fn(x, y):
        dx, dy = x - 32.0, y - 32.0
        r = math.hypot(dx, dy)
        if r > 31.5 or r < 3.0:
            return None
        angle = math.atan2(dy, dx)
        arm = math.cos(4 * (angle + r * 0.16))           # 나선 팔 네 개
        edge = 1.0 - r / 31.5
        heat = max(0.0, arm) * (0.35 + 0.65 * (1.0 - edge)) * min(1.0, r / 8.0)
        if r < 7.0:
            return SHADOW[0], 255
        if heat < 0.18:
            if r < 12.0:
                return SHADOW[1], 200
            return (VIOLET[0], 110) if heat > 0.05 else None
        return ramp(VIOLET if arm < 0.75 else MAGENTA, 0.2 + heat * 0.85), alpha_step(0.3 + heat * 0.8)
    c.field(fn)
    return c.save("vortex")


def void_core():
    """공중에 떠 있는 검은 핵. 새까만 구에 보라 테두리가 타오르고, 가장자리에 빛이 휘어 감깁니다."""
    c = Canvas(32, 32)

    def fn(x, y):
        dx, dy = x - 16.0, y - 16.0
        r = math.hypot(dx, dy)
        if r > 15.5:
            return None
        if r < 9.0:
            return SHADOW[0], 255
        if r < 10.5:
            return VIOLET[4], 255
        if r < 12.0:
            return MAGENTA[3], 230
        glow = 1.0 - (r - 12.0) / 3.5
        return ramp(VIOLET, 0.3 + glow * 0.5), alpha_step(glow * 0.8)
    c.field(fn)
    for (x, y) in [(10, 11), (11, 10), (20, 21)]:
        c.put(x, y, VIOLET[1], 255)
    return c.save("void_core")

TEXTURES = [
    slash, rift, vortex, void_core, wing, spike_block, spike_core, claw, blade, crack, rune, shard, barrier, chain, bolt, feather,
    lambda: swing_arc_frames("swing_arc", CRIMSON, 4),
    lambda: beam("beam_crimson", CRIMSON),
    lambda: beam("beam_arcane", MAGENTA),
    lambda: beam("beam_soul", SOUL),
    lambda: shockwave("shockwave_crimson", CRIMSON),
    lambda: arcane_wave("shockwave_violet", VIOLET),
    lambda: circle("sigil", FIRE[:1] + CRIMSON[1:], 5, 2, 10),
    lambda: circle("circle_arcane", MAGENTA, 6, 2, 12),
    lambda: flame("flame", FIRE),
    lambda: flame("soul", SOUL),
    lambda: flash("flash_crimson", CRIMSON),
    lambda: flash("flash_arcane", MAGENTA),
]


def contact_sheet(names, path, scale=6):
    tiles = [Image.open(os.path.join(OUT, n + ".png")) for n in names]
    cell = 64 * scale
    cols = 6
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell, rows * cell), (22, 17, 26, 255))
    for i, tile in enumerate(tiles):
        big = tile.resize((tile.width * scale, tile.height * scale), Image.NEAREST)
        x = (i % cols) * cell + (cell - big.width) // 2
        y = (i // cols) * cell + (cell - big.height) // 2
        sheet.alpha_composite(big, (x, y))
    sheet.save(path)


if __name__ == "__main__":
    made = []
    for make in TEXTURES:
        result = make()
        made.extend(result if isinstance(result, list) else [result])
    print("wrote", len(made), "textures to", OUT)
    if "--sheet" in sys.argv:
        contact_sheet(made, sys.argv[sys.argv.index("--sheet") + 1])
