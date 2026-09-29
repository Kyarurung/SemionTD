"""침공군 유닛 연출 텍스처 생성기.

마왕 연출 생성기(make_demon_lord_vfx.py)의 도형 함수를 색만 바꿔 쓰고, 투창·연기·금빛 궤적처럼
새로 필요한 모양만 여기서 그립니다. 도트 규칙(작은 해상도, 끊은 색·투명도)은 같습니다.

출력: src/main/resources/semiontd/vfx/invasion/<이름>.png
실행: python tools/vfx-textures/make_invasion_vfx.py [--sheet out.png]
"""
import math
import random
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_demon_lord_vfx as base  # noqa: E402
from make_demon_lord_vfx import Canvas, alpha_step, hexc, ramp  # noqa: E402

base.OUT = os.path.join(base.ROOT, "src", "main", "resources", "semiontd", "vfx", "invasion")

GOLD = [hexc(c) for c in ("3a2606", "7a520e", "c98d1c", "f4c542", "ffe9a0", "fffbe8")]
HOLY = [hexc(c) for c in ("3d0f2a", "8a2a63", "d9559b", "ff9ed0", "ffe3f1", "ffffff")]
NECRO = [hexc(c) for c in ("06210f", "0f5226", "1f9a45", "5ee07a", "c8ffd4", "ffffff")]
EARTH = [hexc(c) for c in ("2a1d12", "4f3a26", "7d6243", "a88c68", "d6c3a3", "f3eadb")]
SMOKE = [hexc(c) for c in ("2b2b31", "4a4a52", "6e6e78", "9a9aa4", "c8c8d0", "ececf0")]
WOOD = [hexc(c) for c in ("2e1a0c", "55321a", "7a4a26", "9c6538")]
IRON = [hexc(c) for c in ("3a3d44", "70757f", "b4b9c2", "eef1f5")]


def swing_arc(name, colors):
    """마왕 검 궤적과 같은 110° 부채꼴을 다른 색으로 그립니다."""
    c = Canvas(64, 64)

    def fn(x, y):
        dx, dy = x - 32.0, y - 32.0
        r = math.hypot(dx, dy)
        if r < 15.0 or r > 31.5:
            return None
        angle = math.degrees(math.atan2(dx, dy))
        if abs(angle) > 55.0:
            return None
        t = (angle + 55.0) / 110.0
        radial = (r - 15.0) / 16.5
        heat = t ** 1.4 * (0.35 + 0.65 * radial ** 1.5)
        if radial > 0.9:
            heat = max(heat, 0.35 + 0.65 * t)
        if heat < 0.08:
            return None
        return ramp(colors, heat * 1.05 + 0.05), alpha_step(0.2 + heat)
    c.field(fn)
    return c.save(name)


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


def javelin():
    """트롤 투창. 세로로 긴 판(위가 창끝)이라 교차 모델에 씌워 날아가는 방향으로 눕힙니다."""
    c = Canvas(8, 64)
    for y in range(64):
        if y < 12:
            half = (y / 12.0) * 2.6                      # 뾰족한 쇠 창끝
            for x in range(8):
                d = abs(x + 0.5 - 4.0)
                if d <= half + 0.3:
                    c.put(x, y, IRON[3] if d < 0.8 else IRON[2 if y > 4 else 1], 255)
        elif y < 15:
            for x in range(2, 6):                        # 가죽 감개
                c.put(x, y, WOOD[0], 255)
        else:
            for x in range(3, 5):                        # 나무 자루
                c.put(x, y, WOOD[2] if x == 3 else WOOD[1], 255)
            if y > 58:
                for x in range(1, 7):                    # 꽁지 깃
                    if abs(x + 0.5 - 4.0) < (y - 58) * 0.7:
                        c.put(x, y, NECRO[2] if (x + y) % 2 else NECRO[1], 255)
    return c.save("javelin")


def smoke():
    """뭉게 연기 한 덩이(은신 연막·순간 처치 먼지)."""
    c = Canvas(16, 16)
    blobs = [(8, 9, 5.2), (5, 7, 3.4), (11, 7, 3.6), (8, 5, 3.4)]

    def fn(x, y):
        best = 0.0
        for bx, by, br in blobs:
            best = max(best, 1.0 - math.hypot(x - bx, y - by) / br)
        if best <= 0.0:
            return None
        light = best + (0.25 if y < 8 else 0.0)
        return ramp(SMOKE, 0.2 + light * 0.7), alpha_step(0.25 + best * 0.9)
    c.field(fn)
    return c.save("smoke")


GOBLIN = [hexc(c) for c in ("1f3a0a", "3f7a14", "7fc22a", "c8f25a", "f2ffc8", "ffffff")]
ELF = [hexc(c) for c in ("0f2438", "1f4f7a", "3f8fd1", "8fd0ff", "e3f5ff", "ffffff")]


def slash_line(name, colors):
    """한 줄로 긋는 칼자국(빌보드). 가운데가 가장 굵고 밝으며 양 끝으로 가늘어집니다. 가로로 누운 모양이라
    게임에서 판을 돌려(굴림) 사선으로 씁니다."""
    c = Canvas(64, 64)

    def fn(x, y):
        t = (x - 2.0) / 60.0                             # 0 = 시작, 1 = 끝
        if t < 0.0 or t > 1.0:
            return None
        # 앞쪽(끝)이 조금 더 굵은 비대칭 잎 모양: 긋는 방향으로 힘이 실립니다.
        half = 3.4 * math.sin(math.pi * t) ** 0.8 * (0.55 + 0.45 * t)
        d = abs(y - 32.0)
        if d > half + 0.3:
            return None
        core = 1.0 - d / max(half, 0.4)
        heat = core * (0.55 + 0.45 * math.sin(math.pi * t))
        return ramp(colors, 0.25 + heat * 0.85), alpha_step(0.35 + core * 0.8)
    c.field(fn)
    return c.save(name)


def dust_ring():
    """흙먼지 충격파: 매끈한 고리 대신 크기가 제각각인 흙덩이가 고리를 이루고, 군데군데 끊겨 있습니다.
    안쪽엔 옅은 먼지 얼룩만 남습니다."""
    rnd = random.Random("dust_ring")
    c = Canvas(64, 64)
    # 옅은 먼지
    for _ in range(46):
        a = rnd.uniform(0, math.tau)
        r = rnd.uniform(14, 27)
        cx, cy = 32 + math.cos(a) * r, 32 + math.sin(a) * r
        for dy in range(-2, 3):
            for dx in range(-2, 3):
                if dx * dx + dy * dy <= 4 and rnd.random() < 0.7:
                    c.over(int(cx + dx), int(cy + dy), EARTH[2], 70)
    # 흙덩이 고리(끊긴 곳 셋)
    gaps = [rnd.uniform(0, math.tau) for _ in range(3)]
    a = 0.0
    while a < math.tau:
        if all(abs(math.atan2(math.sin(a - g), math.cos(a - g))) > 0.22 for g in gaps):
            size = rnd.choice((1, 1, 2, 2, 3))
            r = 28.5 + rnd.uniform(-2.5, 1.5)
            cx, cy = 32 + math.cos(a) * r, 32 + math.sin(a) * r
            for dy in range(-size, size + 1):
                for dx in range(-size, size + 1):
                    if abs(dx) + abs(dy) <= size + 0.5:
                        top = dy < 0 or (dy == 0 and dx < 0)
                        c.put(int(cx + dx), int(cy + dy), EARTH[4 if top else 2], 255)
            c.put(int(cx), int(cy), EARTH[5], 255)
        a += rnd.uniform(0.09, 0.2)
    return c.save("dust_ring")


def holy_ring():
    """성스러운 파동: 얇은 두 겹 고리(바깥은 실선, 안쪽은 점선) 위에 네 갈래 반짝임 여덟 개. 속은 비어 있습니다."""
    c = Canvas(64, 64)
    for y in range(64):
        for x in range(64):
            r = math.hypot(x + 0.5 - 32, y + 0.5 - 32)
            a = math.atan2(y + 0.5 - 32, x + 0.5 - 32)
            if 29.6 < r < 31.4:
                c.put(x, y, HOLY[4], 255)
            elif 28.4 < r <= 29.6:
                c.put(x, y, HOLY[2], 200)
            elif 21.3 < r < 22.5 and int((a + math.pi) / (math.tau / 48)) % 2 == 0:
                c.put(x, y, HOLY[3], 230)
    for i in range(8):
        a = math.tau * i / 8
        cx, cy = int(32 + math.cos(a) * 30), int(32 + math.sin(a) * 30)
        for k in range(-3, 4):
            color = HOLY[5] if abs(k) <= 1 else HOLY[3]
            c.put(cx + k, cy, color, 255)
            c.put(cx, cy + k, color, 255)
    return c.save("holy_ring")


TEXTURES = [
    javelin, smoke,
    lambda: swing_arc_frames("gold_arc", GOLD, 4),
    lambda: base.beam("tracer", GOLD),
    lambda: base.flash("muzzle", base.FIRE),
    lambda: base.circle("holy_circle", HOLY, 6, 2, 12),
    lambda: base.flash("holy_flash", HOLY),
    holy_ring,
    lambda: base.circle("necro_circle", NECRO, 5, 2, 10),
    lambda: base.flame("necro_soul", NECRO),
    lambda: base.flash("necro_flash", NECRO),
    dust_ring,
    lambda: base.flash("gold_flash", GOLD),
    lambda: slash_line("slash_goblin", GOBLIN),
    lambda: slash_line("slash_elf", ELF),
]


if __name__ == "__main__":
    made = []
    for make in TEXTURES:
        result = make()
        made.extend(result if isinstance(result, list) else [result])
    print("wrote", len(made), "textures to", base.OUT)
    if "--sheet" in sys.argv:
        base.contact_sheet(made, sys.argv[sys.argv.index("--sheet") + 1])
