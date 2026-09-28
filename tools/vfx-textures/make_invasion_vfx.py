"""침공군 유닛 연출 텍스처 생성기.

마왕 연출 생성기(make_demon_lord_vfx.py)의 도형 함수를 색만 바꿔 쓰고, 투창·연기·금빛 궤적처럼
새로 필요한 모양만 여기서 그립니다. 도트 규칙(작은 해상도, 끊은 색·투명도)은 같습니다.

출력: src/main/resources/semiontd/vfx/invasion/<이름>.png
실행: python tools/vfx-textures/make_invasion_vfx.py [--sheet out.png]
"""
import math
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


TEXTURES = [
    javelin, smoke,
    lambda: swing_arc("gold_arc", GOLD),
    lambda: base.beam("tracer", GOLD),
    lambda: base.flash("muzzle", base.FIRE),
    lambda: base.circle("holy_circle", HOLY, 6, 2, 12),
    lambda: base.flash("holy_flash", HOLY),
    lambda: base.shockwave("holy_ring", HOLY),
    lambda: base.circle("necro_circle", NECRO, 5, 2, 10),
    lambda: base.flame("necro_soul", NECRO),
    lambda: base.flash("necro_flash", NECRO),
    lambda: base.shockwave("dust_ring", EARTH),
    lambda: base.flash("gold_flash", GOLD),
]


if __name__ == "__main__":
    made = [make() for make in TEXTURES]
    print("wrote", len(made), "textures to", base.OUT)
    if "--sheet" in sys.argv:
        base.contact_sheet(made, sys.argv[sys.argv.index("--sheet") + 1])
