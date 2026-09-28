"""엘프 암살자 플레이어 스킨(64x64, 슬림 팔)을 그립니다.

바닐라 스킨 레이아웃을 따르고, 얼굴·옷·머리카락은 한 칸 한 픽셀로 칠합니다.
겉층(모자·자켓·소매·바지)은 앞머리, 등까지 내려오는 긴 머리, 치마 자락에 씁니다.

실행: python make_elf_skin.py [출력 경로]
"""
import sys
from PIL import Image

# 램프: 그림자 → 밝음. 그림자 쪽은 푸르게, 밝은 쪽은 따뜻하게 색조를 옮깁니다.
SKIN = ['#9c6b6b', '#c98f86', '#e8b8a4', '#f7d9c4']
SKIN_SHADE = '#dba696'
HAIR = ['#8d8aa8', '#b9b6cf', '#dcdbea', '#f8f7ff']
SCARF = ['#4a0f24', '#7d1a2c', '#a92b35', '#d24a45']
BODICE = ['#1c1528', '#2c2140', '#3f3059', '#584577']
SKIRT = ['#1f1730', '#302447', '#433463', '#5c4a82']
TIGHTS = ['#111018', '#1b1a26', '#282638', '#3a3750']
LEATHER = ['#1a1216', '#2a1d22', '#3d2a2e', '#57403f']
BOOT = ['#140f14', '#231a21', '#35272f', '#4c3a42']
SILVER = ['#565b70', '#8a91a5', '#bcc3d2', '#eef2f8']
IRIS = ['#3b1a6e', '#6a33b5', '#9d6ae6', '#c9a8ff']
LASH = '#726f89'
WHITE_TOP = '#c0c0c2'
WHITE = '#f4f3fa'

img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


def put(x, y, c):
    if c is not None:
        img.putpixel((x, y), rgb(c))


def fill(x0, y0, w, h, fn):
    """fn(u, v, w, h) -> '#rrggbb' | None. u·v는 면 안 좌표(왼쪽 위 기준)."""
    for v in range(h):
        for u in range(w):
            put(x0 + u, y0 + v, fn(u, v, w, h))


def grid(x0, y0, rows, key):
    for v, row in enumerate(rows):
        for u, ch in enumerate(row):
            if ch != '.':
                put(x0 + u, y0 + v, key[ch])


def box_faces(ox, oy, w, h, d, faces):
    """박스 UV 한 벌(ox, oy에서 시작)의 여섯 면에 칠합니다. faces: {이름: fn}."""
    rects = {
        'top': (ox + d, oy, w, d), 'bottom': (ox + d + w, oy, w, d),
        'right': (ox, oy + d, d, h), 'front': (ox + d, oy + d, w, h),
        'left': (ox + d + w, oy + d, d, h), 'back': (ox + d + w + d, oy + d, w, h),
    }
    for name, fn in faces.items():
        x, y, fw, fh = rects[name]
        if isinstance(fn, list):
            grid(x, y, fn[0], fn[1])
        else:
            fill(x, y, fw, fh, fn)


def shade(ramp, base, bottom_dark=True):
    """면 기본 음영: base 칸, 아래쪽 1/4은 한 단 어둡게."""
    def fn(u, v, w, h):
        i = base
        if bottom_dark and h >= 3 and v >= h - max(1, round(h / 4)):
            i -= 1
        return ramp[max(0, min(3, i))]
    return fn


def rows(spec_rows):
    """줄마다 (재질 램프, 칸)을 주면 그 줄을 칠합니다. None이면 비웁니다."""
    def fn(u, v, w, h):
        spec = spec_rows[min(v, len(spec_rows) - 1)]
        if spec is None:
            return None
        if callable(spec):
            return spec(u, v, w, h)
        ramp, i = spec
        return ramp[i] if isinstance(ramp, list) else ramp
    return fn


# ------------------------------------------------------------------ head (0,0) 8x8x8
FACE_KEY = {
    'h': HAIR[2], 'H': HAIR[3], 'd': HAIR[1], 's': SKIN[2], 'k': SKIN_SHADE,
    'B': HAIR[0], 'L': LASH, 'W': WHITE_TOP, 'w': WHITE, 'i': IRIS[1], 'I': IRIS[2],
}
box_faces(0, 0, 8, 8, 8, {
    'top': lambda u, v, w, h: HAIR[1] if u == 3 else HAIR[2],
    'bottom': lambda u, v, w, h: SKIN[1],
    'front': [[
        'hHhhhhHh',
        'hhhdhhhh',
        'hdhhhdhh',
        'hBBkkBBh',
        'LWissiWL',
        'LwIssIwL',
        'ssssssss',
        'kssssssk',
    ], FACE_KEY],
    'right': lambda u, v, w, h: SKIN[1] if (u == w - 1 and v >= 6) else HAIR[1],
    'left': lambda u, v, w, h: SKIN[1] if (u == 0 and v >= 6) else HAIR[1],
    'back': lambda u, v, w, h: HAIR[1] if u % 3 == 1 else HAIR[2],
})
# 모자 층 (32,0): 앞머리 가닥, 옆·뒤는 부피를 주고 아랫줄을 들쭉날쭉하게 비웁니다.
HAT_KEY = {'h': HAIR[2], 'H': HAIR[3], 'd': HAIR[1]}
ragged = lambda u, v, w, h: None if (v == h - 1 and u % 2 == 1) else (HAIR[1] if u % 3 == 1 else HAIR[2])
box_faces(32, 0, 8, 8, 8, {
    'top': lambda u, v, w, h: HAIR[2] if u == 3 else HAIR[3],
    'front': [[
        'HhHHhhHh',
        'hhdhhdhh',
        'hdh.hhdh',
        'd......d',
        '........',
        '........',
        '........',
        '........',
    ], HAT_KEY],
    'right': ragged,
    'left': ragged,
    'back': lambda u, v, w, h: HAIR[1] if u % 3 == 1 else HAIR[2],
})

# ------------------------------------------------------------------ body (16,16) 8x12x4
lace = lambda u, v, w, h: (SCARF[2] if (u + v) % 2 else BODICE[0]) if u in (3, 4) else BODICE[2]
body_front = rows([
    (SCARF, 2), (SCARF, 2), (SCARF, 1),
    lambda u, v, w, h: BODICE[3] if u in (1, 2, 5, 6) else BODICE[2],   # 가슴 하이라이트
    lambda u, v, w, h: BODICE[1] if u in (0, 7) else BODICE[2],
    lace, lace, lace,
    lambda u, v, w, h: SILVER[3] if u in (3, 4) else LEATHER[2],       # 허리띠와 버클
    lambda u, v, w, h: SKIRT[1] if u % 2 else SKIRT[2],
    lambda u, v, w, h: SKIRT[1] if u % 2 else SKIRT[2],
    lambda u, v, w, h: SKIRT[1] if u % 2 else SKIRT[2],
])
body_side = rows([(SCARF, 1), (SCARF, 1), (SCARF, 0), (BODICE, 1), (BODICE, 1), (BODICE, 1), (BODICE, 1), (BODICE, 1),
                  (LEATHER, 1), (SKIRT, 1), (SKIRT, 1), (SKIRT, 0)])
body_back = rows([(SCARF, 1), (SCARF, 1), (SCARF, 0), (BODICE, 1), (BODICE, 1), (BODICE, 1), (BODICE, 1), (BODICE, 1),
                  (LEATHER, 1), lambda u, v, w, h: SKIRT[0] if u % 2 else SKIRT[1],
                  lambda u, v, w, h: SKIRT[0] if u % 2 else SKIRT[1], lambda u, v, w, h: SKIRT[0] if u % 2 else SKIRT[1]])
box_faces(16, 16, 8, 12, 4, {
    'top': lambda u, v, w, h: SCARF[3],
    'bottom': lambda u, v, w, h: SKIRT[0],
    'front': body_front, 'right': body_side, 'left': body_side, 'back': body_back,
})
# 자켓 층 (16,32): 등으로 흘러내린 긴 머리(끝은 들쭉날쭉), 치마 아랫단이 살짝 벌어지게 치마 끝 두 줄, 스카프 매듭.
def long_hair(u, v, w, h):
    end = 8 + (1 if u % 3 == 0 else 0) - (1 if u % 3 == 2 else 0)
    if v > end:
        return None
    return HAIR[1] if u % 3 == 1 else HAIR[2]


def jacket_skirt(u, v, w, h):
    # 치마 자락은 한 겹 부풀어 보이게만 하고, 붉은 단은 다리 바지 층에만 둡니다.
    if v >= 10:
        return SKIRT[1] if u % 2 else SKIRT[2]
    return None


box_faces(16, 32, 8, 12, 4, {
    'front': lambda u, v, w, h: jacket_skirt(u, v, w, h) or (SCARF[3] if (u in (5, 6) and v in (1, 2)) else None),
    'right': jacket_skirt, 'left': jacket_skirt,
    'back': lambda u, v, w, h: long_hair(u, v, w, h) or jacket_skirt(u, v, w, h),
})

# ------------------------------------------------------------------ arms (slim 3x12x4)
def arm_face(base, outer=False):
    def fn(u, v, w, h):
        if v <= 5:
            if v == 3:
                return LEATHER[1 + (base > 1)]          # 가죽 팔찌
            return SKIN[base] if v else SKIN[min(3, base + 1)]
        if v == 6:
            return SILVER[base + 1] if base < 3 else SILVER[3]  # 토시 은테
        if v <= 9:
            return LEATHER[base]
        return BOOT[base]                               # 장갑
    return fn


for ox, oy in ((40, 16), (32, 48)):
    box_faces(ox, oy, 3, 12, 4, {
        'top': lambda u, v, w, h: SKIN[3],
        'bottom': lambda u, v, w, h: BOOT[1],
        'front': arm_face(2), 'right': arm_face(1), 'left': arm_face(1), 'back': arm_face(1),
    })


# ------------------------------------------------------------------ legs (4x12x4)
def leg_face(base, strap=False):
    def fn(u, v, w, h):
        if strap and v == 4:
            return LEATHER[base]                        # 허벅지 끈
        if v <= 5:
            return TIGHTS[base] if v < 5 else TIGHTS[base - 1]
        if v == 6:
            return BOOT[min(3, base + 1)]               # 부츠 테
        if v == 9:
            return SILVER[base]                         # 부츠 은띠
        if v == 11:
            return BOOT[0]                              # 밑창
        return BOOT[base]
    return fn


for (ox, oy), strap in (((0, 16), True), ((16, 48), False)):
    box_faces(ox, oy, 4, 12, 4, {
        'top': lambda u, v, w, h: TIGHTS[2],
        'bottom': lambda u, v, w, h: BOOT[0],
        'front': leg_face(2, strap), 'right': leg_face(1, strap), 'left': leg_face(1, strap), 'back': leg_face(1, strap),
    })


# 바지 층: 치마 자락이 허벅지 위쪽 세 줄을 덮고 맨 아래가 붉은 단입니다.
def skirt_leg(u, v, w, h):
    if v <= 1:
        return SKIRT[1] if u % 2 else SKIRT[2]
    if v == 2:
        return SCARF[1]
    return None


for ox, oy in ((0, 32), (0, 48)):
    box_faces(ox, oy, 4, 12, 4, {'front': skirt_leg, 'right': skirt_leg, 'left': skirt_leg, 'back': skirt_leg})

out = sys.argv[1] if len(sys.argv) > 1 else 'elf_assassin_skin.png'
img.save(out)
print('saved', out)
