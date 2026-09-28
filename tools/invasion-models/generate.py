#!/usr/bin/env python3
"""Generate the invasion army Blockbench models.

Writes one .bbmodel per unit to src/main/resources/model/semion-td/invasion/ with an embedded
procedural pixel-art texture and the four runtime animations (idle, walk, attack, death).

Conventions follow the pinned BIL importer:
  * the model faces -z (Blockbench north); BIL turns the root by 180 degrees in game;
  * keyframe rotation (x, y, z) is applied as (-x, -y, z) in ZYX order, position as (-x, y, z);
  * one outliner group becomes one bone/item model, so every moving part is its own group;
  * cubes are never rotated, because item model elements only rotate on one axis.

Run from the repository root:  python tools/invasion-models/generate.py [--preview DIR]
"""

from __future__ import annotations

import argparse
import base64
import io
import json
import math
import random
import uuid
from dataclasses import dataclass, field
from pathlib import Path

from PIL import Image

NAMESPACE = uuid.UUID("5b2d7c1e-6f3a-4a9e-8c41-2f0d9b7e1a53")
# 텍스처는 512 픽셀, UV 공간은 256입니다. 한 칸(1/16 블록)에 텍셀 2×2가 들어가 면이 촘촘해집니다.
TEX = 512
UV = 256
DENS = TEX // UV
OUT_DIR = Path("src/main/resources/model/semion-td/invasion")


def uid(*parts: str) -> str:
    return str(uuid.uuid5(NAMESPACE, "/".join(parts)))


# --------------------------------------------------------------------------- materials


def rgb(hex_value: str) -> tuple[int, int, int]:
    hex_value = hex_value.lstrip("#")
    return tuple(int(hex_value[i:i + 2], 16) for i in (0, 2, 4))


@dataclass(frozen=True)
class Material:
    """A pixel-art colour ramp, darkest to lightest, and a surface pattern."""

    ramp: tuple
    pattern: str = "plain"

    def color(self, level: float):
        index = max(0, min(len(self.ramp) - 1, int(round(level))))
        return self.ramp[index]

    def smooth(self, level: float):
        """Blend neighbouring ramp tones in half steps for soft gradients on large faces."""
        level = max(0.0, min(len(self.ramp) - 1.0, round(level * 2) / 2))
        low = int(level)
        high = min(len(self.ramp) - 1, low + 1)
        t = level - low
        return mix(self.ramp[low], self.ramp[high], t)


def material(*hexes: str, pattern: str = "plain") -> Material:
    return Material(tuple(rgb(h) for h in hexes), pattern)


# 면 방향별 밝기 단계. 위가 가장 밝고 아래가 가장 어둡습니다(위에서 비추는 조명).
FACE_OFFSET = {"up": 0.9, "north": 0.0, "east": -0.35, "west": -0.35, "south": -0.55, "down": -1.5}
BAYER = ((0.0, 0.5), (0.75, 0.25))


def shade(color, factor):
    return tuple(max(0, min(255, int(round(c * factor)))) for c in color)


def mix(a, b, t):
    return tuple(int(round(x + (y - x) * t)) for x, y in zip(a, b))


# Shared palettes. Hue-shifted: shadows lean cool, highlights lean warm.
ORC_SKIN = material("2c4418", "3f5f22", "55792c", "6c9036", "86a948", pattern="skin")
ORC_SKIN_DARK = material("243912", "33501c", "466826", "5a7f31", "6f943c", pattern="skin")
TUSK = material("9a9278", "bdb596", "d9d2b4", "ece6cd", "fbf8ea")
LEATHER = material("2e1a0f", "442817", "5b3820", "73492b", "8b5c38", pattern="leather")
LEATHER_DARK = material("1f130b", "2e1c11", "3d2717", "4d321e", "5e3e26", pattern="leather")
FUR = material("4a3a2a", "65513b", "80684d", "9b8262", "b39b79", pattern="fur")
IRON = material("2f3237", "474b52", "60656d", "7b8189", "a3a9b1", pattern="metal")
STEEL_EDGE = material("5d636c", "7f8690", "a2a9b3", "c4cad2", "e6eaef", pattern="metal")
WOOD = material("3a2413", "54361d", "6c4727", "845a33", "9b6e40", pattern="wood")
RAG = material("3d1410", "561d16", "70271d", "873325", "9c4130", pattern="cloth")
HAIR_BLACK = material("111014", "1c1a20", "29262e", "37333d", "47424f", pattern="hair")

ELF_SKIN = material("463a60", "5b4d7b", "716296", "8878ae", "a092c6", pattern="skin")
ELF_HAIR = material("8f8aa8", "b0abc6", "cdc9df", "e2def0", "f5f3fb", pattern="hair")
ELF_LEATHER = material("2a2039", "3a2d4e", "4a3b63", "5c4b78", "6f5e8e", pattern="leather")
ELF_ARMOR = material("3a2d55", "4e3c72", "62508c", "7866a6", "9080be", pattern="leather")
ELF_CLOAK = material("171223", "221a31", "2e2442", "3b2f54", "4a3c67", pattern="cloth")
ELF_STEEL = material("4a4b62", "6a6d88", "8e92ad", "b3b7cf", "d9dcec", pattern="metal")
ELF_GLOW = material("5b1fa8", "7a33d4", "9b55f0", "bd84ff", "ddbaff")

TROLL_SKIN = material("1f4a4a", "2d6563", "3f807c", "539b95", "6cb5ad", pattern="skin")
TROLL_SKIN_DARK = material("173a3a", "224f4e", "306764", "3f807b", "519891", pattern="skin")
TROLL_HAIR = material("5c140c", "7d2214", "a0321e", "c2452b", "dd5f3d", pattern="hair")
HIDE = material("4a331d", "65472a", "806039", "9a7949", "b3925c", pattern="leather")
FLINT = material("33363c", "4a4e56", "636872", "7e848f", "9ba1ab", pattern="stone")
FEATHER = material("8a8474", "aaa392", "c7c1b0", "ddd8c9", "f0ece2", pattern="hair")

KNIGHT_STEEL = material("23262c", "353a43", "4b515c", "646b77", "858d99", pattern="metal")
KNIGHT_PLATE = material("2d3139", "444a55", "5e6571", "7a828f", "a0a8b4", pattern="metal")
KNIGHT_DARK = material("121317", "1c1e23", "272a31", "33373f", "41454e", pattern="metal")
GOLD = material("6b4d14", "8f6a1e", "b38a2c", "d1a93f", "ecc964", pattern="gold")
CRIMSON = material("3a0707", "550d0d", "701414", "8c1c1c", "a82828", pattern="cloth")
HORN = material("7d735a", "9f9577", "bfb595", "d9d0b2", "eee8d0")
MAIL = material("1d1f24", "2b2e35", "3a3e47", "4a4f59", "5d636e", pattern="mail")


# --------------------------------------------------------------------------- model building


@dataclass
class Cube:
    name: str
    frm: tuple[float, float, float]
    to: tuple[float, float, float]
    material: Material
    decals: dict = field(default_factory=dict)
    faces: dict = field(default_factory=dict)
    uuid: str = ""
    # 한 축, ±45도 이내만. 마인크래프트 아이템 모델 요소가 그 이상을 받지 않고, BIL은 첫 축만 씁니다.
    rot: tuple = (0.0, 0.0, 0.0)
    pivot: tuple | None = None
    # 두께 0인 판. "x"/"y"/"z"는 두께가 없는 축이고, 그 축을 바라보는 두 면만 만듭니다.
    plane: str | None = None
    # 판의 모양. (xf, yf) -> bool, xf는 -x→+x, yf는 아래→위 비율. 거짓이면 그 텍셀은 투명합니다.
    shape: object = None
    shape_flip: bool = False


@dataclass
class Bone:
    name: str
    origin: tuple[float, float, float]
    parent: "Bone | None"
    cubes: list = field(default_factory=list)
    children: list = field(default_factory=list)
    uuid: str = ""
    # 그룹 회전은 BIL이 표시 변환으로 처리하므로 여러 축, 아무 각도나 됩니다.
    rotation: tuple = (0.0, 0.0, 0.0)


class Model:
    def __init__(self, key: str, display: str):
        self.key = key
        self.display = display
        self.bones: dict[str, Bone] = {}
        self.root = self.bone("root", (0, 0, 0))
        self.animations: list[dict] = []
        self.rng = random.Random(key)

    def bone(self, name, origin, parent: str | None = None, rotation=(0, 0, 0)) -> Bone:
        parent_bone = self.bones[parent] if parent else None
        bone = Bone(name, tuple(float(v) for v in origin), parent_bone, uuid=uid(self.key, "bone", name),
                    rotation=tuple(float(v) for v in rotation))
        if parent_bone:
            parent_bone.children.append(bone)
        self.bones[name] = bone
        return bone

    def cube(self, bone: str, name, frm, to, material, decals=None, rot=(0, 0, 0), pivot=None,
             plane=None, shape=None, shape_flip=False):
        rot = tuple(float(v) for v in rot)
        if sum(1 for v in rot if v) > 1 or any(abs(v) > 45 for v in rot):
            raise ValueError(f"{self.key}/{name}: cube rotation must be one axis within 45 degrees: {rot}")
        cube = Cube(name, tuple(float(v) for v in frm), tuple(float(v) for v in to), material,
                    decals or {}, uuid=uid(self.key, "cube", bone, name), rot=rot,
                    pivot=None if pivot is None else tuple(float(v) for v in pivot),
                    plane=plane, shape=shape, shape_flip=shape_flip)
        self.bones[bone].cubes.append(cube)
        return cube

    def mirrored(self, bone: str, name, frm, to, material, decals=None, rot=(0, 0, 0), pivot=None,
                 plane=None, shape=None):
        """Same cube on the other side of x = 0. Rotations about y and z flip sign."""
        mirrored_pivot = None if pivot is None else (-pivot[0], pivot[1], pivot[2])
        return self.cube(bone, name, (-to[0], frm[1], frm[2]), (-frm[0], to[1], to[2]), material, decals,
                         rot=(rot[0], -rot[1], -rot[2]), pivot=mirrored_pivot, plane=plane, shape=shape,
                         shape_flip=True)

    # ------------------------------------------------------------------- animation

    def animation(self, name, length, loop, tracks):
        """tracks: {bone: {channel: [(time, (x, y, z)), ...]}}"""
        animators = {}
        for bone_name, channels in tracks.items():
            bone = self.bones[bone_name]
            keyframes = []
            for channel, keys in channels.items():
                for time, value in keys:
                    keyframes.append({
                        "channel": channel,
                        "data_points": [{"x": fmt(value[0]), "y": fmt(value[1]), "z": fmt(value[2])}],
                        "uuid": uid(self.key, "kf", name, bone_name, channel, str(time)),
                        "time": time,
                        "color": -1,
                        "interpolation": "linear",
                        "bezier_linked": True,
                        "bezier_left_time": [-0.1, -0.1, -0.1],
                        "bezier_left_value": [0, 0, 0],
                        "bezier_right_time": [0.1, 0.1, 0.1],
                        "bezier_right_value": [0, 0, 0],
                    })
            animators[bone.uuid] = {"name": bone.name, "type": "bone", "keyframes": keyframes}
        self.animations.append({
            "uuid": uid(self.key, "anim", name),
            "name": name,
            "loop": loop,
            "override": False,
            "length": length,
            "snapping": 24,
            "selected": False,
            "anim_time_update": "",
            "blend_weight": "",
            "start_delay": "",
            "loop_delay": "",
            "animators": animators,
        })

    # ------------------------------------------------------------------- export

    def all_cubes(self):
        for bone in self.bones.values():
            for cube in bone.cubes:
                yield bone, cube

    def build(self) -> dict:
        image = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
        packer = ShelfPacker(TEX, TEX)
        elements = []
        for bone, cube in self.all_cubes():
            faces = {}
            for face, (w, h) in face_sizes(cube).items():
                pw, ph = max(1, math.ceil(w * DENS)), max(1, math.ceil(h * DENS))
                x, y = packer.place(pw, ph)
                paint_face(image, x, y, pw, ph, cube, face, self.rng)
                faces[face] = {"uv": [x / DENS, y / DENS, x / DENS + w, y / DENS + h], "texture": 0}
            cube.faces = dict(faces)
            # 판은 두 면만 칠합니다. 나머지 면도 있어야 블록벤치가 열 수 있으므로 텍스처 없이 둡니다.
            # BIL은 텍스처 없는 면을 버립니다.
            for face in ("north", "east", "south", "west", "up", "down"):
                faces.setdefault(face, {"uv": [0, 0, 0, 0], "texture": None})
            element = {
                "name": cube.name,
                "box_uv": False,
                "rescale": False,
                "locked": False,
                "render_order": "default",
                "allow_mirror_modeling": True,
                "from": list(cube.frm),
                "to": list(cube.to),
                "autouv": 0,
                "color": 0,
                "origin": list(cube.pivot or bone.origin),
                "faces": faces,
                "type": "cube",
                "uuid": cube.uuid,
            }
            if any(cube.rot):
                element["rotation"] = list(cube.rot)
            elements.append(element)
        self.image = image
        buffer = io.BytesIO()
        image.save(buffer, format="PNG")
        source = "data:image/png;base64," + base64.b64encode(buffer.getvalue()).decode("ascii")
        return {
            "meta": {"format_version": "4.5", "model_format": "free", "box_uv": False},
            "name": self.key,
            "model_identifier": "semion_td_invasion_" + self.key,
            "visible_box": [2, 3, 0],
            "variable_placeholders": "",
            "variable_placeholder_buttons": [],
            "timeline_setups": [],
            "unhandled_root_fields": {},
            "resolution": {"width": UV, "height": UV},
            "elements": elements,
            "outliner": [outline(self.root)],
            "textures": [{
                "path": "",
                "name": self.key + ".png",
                "folder": "block",
                "namespace": "",
                "id": "0",
                "particle": False,
                "render_mode": "default",
                "render_sides": "auto",
                "frame_time": 1,
                "frame_order_type": "loop",
                "frame_order": "",
                "frame_interpolate": False,
                "visible": True,
                "mode": "bitmap",
                "saved": False,
                "uuid": uid(self.key, "texture"),
                "relative_path": "",
                "width": TEX,
                "height": TEX,
                "uv_width": UV,
                "uv_height": UV,
                "source": source,
            }],
            "animations": self.animations,
        }


def fmt(value: float) -> str:
    return ("%.3f" % value).rstrip("0").rstrip(".") if value != int(value) else str(int(value))


def outline(bone: Bone) -> dict:
    node = {
        "name": bone.name,
        "origin": list(bone.origin),
        "color": 0,
        "uuid": bone.uuid,
        "export": True,
        "mirror_uv": False,
        "isOpen": True,
        "locked": False,
        "visibility": True,
        "autouv": 0,
        "children": [c.uuid for c in bone.cubes] + [outline(child) for child in bone.children],
    }
    if any(bone.rotation):
        node["rotation"] = list(bone.rotation)
    return node


def face_sizes(cube: Cube):
    sx = cube.to[0] - cube.frm[0]
    sy = cube.to[1] - cube.frm[1]
    sz = cube.to[2] - cube.frm[2]
    faces = {
        "north": (sx, sy), "south": (sx, sy),
        "east": (sz, sy), "west": (sz, sy),
        "up": (sx, sz), "down": (sx, sz),
    }
    keep = {"x": ("east", "west"), "y": ("up", "down"), "z": ("north", "south")}.get(cube.plane)
    return {k: v for k, v in faces.items() if keep is None or k in keep}


class ShelfPacker:
    def __init__(self, width, height):
        self.width, self.height = width, height
        self.x = self.y = self.row = 0

    def place(self, w, h):
        if self.x + w > self.width:
            self.x, self.y, self.row = 0, self.y + self.row, 0
        if self.y + h > self.height:
            raise ValueError("texture atlas full")
        position = (self.x, self.y)
        self.x += w
        self.row = max(self.row, h)
        return position


# --------------------------------------------------------------------------- painting


def paint_face(image, x0, y0, w, h, cube: Cube, face, rng):
    """Soft ramp shading at 2x texel density: light from above, gentle falloff, no loose noise."""
    material = cube.material
    side = face not in ("up", "down")
    inside = face_mask(cube, face, w, h)
    for v in range(h):
        for u in range(w):
            if not inside[v][u]:
                continue
            level = 2.0 + FACE_OFFSET[face]
            if side and h > 1:
                falloff = min(1.0, h / (7.0 * DENS))
                level += (0.6 - 1.2 * (v / (h - 1))) * falloff
            level += BAYER[v % 2][u % 2] * 0.18 - 0.07
            level += rng.uniform(-0.05, 0.05)
            level += pattern_offset(material.pattern, u // DENS, v // DENS, max(1, w // DENS), max(1, h // DENS), face, rng)
            edge = (u == 0 or u == w - 1) and w > 2 * DENS
            if cube.plane:
                edge = any(not inside_at(inside, u + du, v + dv) for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if side and edge:
                level -= 0.45
            if side and v == h - 1 and h > 2 * DENS and not cube.plane:
                level -= 0.45
            image.putpixel((x0 + u, y0 + v), material.smooth(level) + (255,))
    decal = cube.decals.get(face)
    if decal:
        decal(DecalCanvas(image, x0, y0, w, h, face, material))


def inside_at(mask, u, v):
    return 0 <= v < len(mask) and 0 <= u < len(mask[0]) and mask[v][u]


def face_mask(cube: Cube, face, w, h):
    """Which texels of a face are solid. Only shaped planes cut anything out."""
    if not cube.shape:
        return [[True] * w for _ in range(h)]
    mask = []
    for v in range(h):
        row = []
        yf = 1.0 - (v + 0.5) / h
        for u in range(w):
            along = (u + 0.5) / w
            # 북쪽 면은 +x에서, 남쪽 면은 -x에서 u가 시작합니다.
            xf = 1.0 - along if face in ("north", "east") else along
            if cube.shape_flip:
                xf = 1.0 - xf
            row.append(bool(cube.shape(xf, yf)))
        mask.append(row)
    return mask


def pattern_offset(pattern, u, v, w, h, face, rng):
    if pattern == "skin":
        return -0.4 if rng.random() < 0.03 else 0.0
    if pattern == "leather":
        if face not in ("up", "down") and v == 1 and u % 2 == 0 and h > 3:
            return -0.8
        return -0.3 if rng.random() < 0.08 else 0.0
    if pattern == "metal":
        if face not in ("up", "down"):
            if v == 0:
                return 1.1
            if u == 1 and w > 3:
                return 0.6
        return 0.0
    if pattern == "gold":
        return 0.9 if (u + v) % 5 == 0 else 0.0
    if pattern == "cloth":
        return -0.6 if face not in ("up", "down") and u % 3 == 2 else 0.1
    if pattern == "hair" or pattern == "fur":
        return -0.7 if u % 2 else 0.25
    if pattern == "wood":
        axis = v if w >= h else u
        return -0.6 if axis % 3 == 0 else 0.0
    if pattern == "stone":
        return -0.7 if rng.random() < 0.18 else 0.0
    if pattern == "mail":
        return 0.7 if (u % 2 == v % 2) else -0.4
    return 0.0


class DecalCanvas:
    """Draws on one face in model pixels; each one covers DENS x DENS texels.

    (0, 0) is the top-left as seen from outside the face.
    """

    def __init__(self, image, x0, y0, tw, th, face, material):
        self.image, self.x0, self.y0, self.tw, self.th = image, x0, y0, tw, th
        self.w, self.h = math.ceil(tw / DENS), math.ceil(th / DENS)
        self.face, self.material = face, material

    def px(self, u, v, color):
        for dv in range(DENS):
            for du in range(DENS):
                self.fine(u * DENS + du, v * DENS + dv, color)

    def fine(self, tu, tv, color):
        """One texel, for details finer than a model pixel."""
        if 0 <= tu < self.tw and 0 <= tv < self.th:
            self.image.putpixel((self.x0 + tu, self.y0 + tv), tuple(color) + (255,))

    def rect(self, u, v, w, h, color):
        for dv in range(h):
            for du in range(w):
                self.px(u + du, v + dv, color)

    def mirror(self, u, v, color):
        """Symmetric pair across the vertical centre line."""
        self.px(u, v, color)
        self.px(self.w - 1 - u, v, color)

    def line(self, u0, v0, u1, v1, color):
        steps = max(abs(u1 - u0), abs(v1 - v0), 1)
        for i in range(steps + 1):
            self.px(round(u0 + (u1 - u0) * i / steps), round(v0 + (v1 - v0) * i / steps), color)

    def tone(self, level):
        """A colour from this face's own ramp."""
        return self.material.color(level)


# --------------------------------------------------------------------------- shared rig


def humanoid_walk(model, length, leg=32, arm=24, bob=0.6, extra=None):
    q, h, t3 = length / 4, length / 2, 3 * length / 4
    tracks = {
        "right_leg": {"rotation": [(0, (0, 0, 0)), (q, (leg, 0, 0)), (h, (0, 0, 0)), (t3, (-leg, 0, 0)), (length, (0, 0, 0))]},
        "left_leg": {"rotation": [(0, (0, 0, 0)), (q, (-leg, 0, 0)), (h, (0, 0, 0)), (t3, (leg, 0, 0)), (length, (0, 0, 0))]},
        "right_arm": {"rotation": [(0, (0, 0, 0)), (q, (-arm, 0, 0)), (h, (0, 0, 0)), (t3, (arm, 0, 0)), (length, (0, 0, 0))]},
        "left_arm": {"rotation": [(0, (0, 0, 0)), (q, (arm, 0, 0)), (h, (0, 0, 0)), (t3, (-arm, 0, 0)), (length, (0, 0, 0))]},
        "body": {"position": [(0, (0, 0, 0)), (q, (0, -bob, 0)), (h, (0, 0, 0)), (t3, (0, -bob, 0)), (length, (0, 0, 0))]},
    }
    for bone, channels in (extra or {}).items():
        tracks.setdefault(bone, {}).update(channels)
    model.animation("walk", length, "loop", tracks)


def humanoid_idle(model, length=2.0, breathe=0.35, sway=3, extra=None):
    h = length / 2
    tracks = {
        "body": {"position": [(0, (0, 0, 0)), (h, (0, breathe, 0)), (length, (0, 0, 0))]},
        "head": {"rotation": [(0, (0, 0, 0)), (h, (-3, 2, 0)), (length, (0, 0, 0))]},
        "right_arm": {"rotation": [(0, (0, 0, 0)), (h, (0, 0, -sway)), (length, (0, 0, 0))]},
        "left_arm": {"rotation": [(0, (0, 0, 0)), (h, (0, 0, sway)), (length, (0, 0, 0))]},
    }
    for bone, channels in (extra or {}).items():
        tracks.setdefault(bone, {}).update(channels)
    model.animation("idle", length, "loop", tracks)


def humanoid_death(model, lift, extra=None):
    """뒤로 쓰러집니다. 앞뒤 두께가 좌우보다 얇아 바닥에 덜 파묻힙니다."""
    tracks = {
        "root": {
            "rotation": [(0, (0, 0, 0)), (0.25, (12, 0, 0)), (0.8, (-90, 0, 4)), (0.95, (-84, 0, 4)), (1.1, (-90, 0, 4))],
            "position": [(0, (0, 0, 0)), (0.25, (0, -1.5, 0)), (0.8, (0, lift, 0)), (1.1, (0, lift, 0))],
        },
        "head": {"rotation": [(0, (0, 0, 0)), (0.25, (20, 0, 0)), (0.8, (-15, 25, 0)), (1.1, (-15, 25, 0))]},
        "right_arm": {"rotation": [(0, (0, 0, 0)), (0.25, (-20, 0, 0)), (0.8, (-150, 0, -30)), (1.1, (-160, 0, -35))]},
        "left_arm": {"rotation": [(0, (0, 0, 0)), (0.25, (-20, 0, 0)), (0.8, (-150, 0, 30)), (1.1, (-160, 0, 35))]},
        "right_leg": {"rotation": [(0, (0, 0, 0)), (0.25, (-25, 0, 0)), (0.8, (-10, 0, -8)), (1.1, (-10, 0, -8))]},
        "left_leg": {"rotation": [(0, (0, 0, 0)), (0.25, (-25, 0, 0)), (0.8, (5, 0, 8)), (1.1, (5, 0, 8))]},
    }
    for bone, channels in (extra or {}).items():
        tracks.setdefault(bone, {}).update(channels)
    model.animation("death", 1.5, "hold", tracks)


# --------------------------------------------------------------------------- decals

BLACK = rgb("0d0c10")
GLOW_RED = rgb("ff3b2f")
GLOW_ORANGE = rgb("ffb13b")
EYE_YELLOW = rgb("ffd23f")


def orc_skull(c: DecalCanvas):
    for u in range(1, c.w - 1):
        c.px(u, 2, c.tone(0))
    c.mirror(1, 3, c.tone(0))
    c.mirror(2, 3, EYE_YELLOW)
    c.mirror(3, 3, GLOW_RED)
    c.mirror(1, 4, c.tone(4))


def orc_brow(c: DecalCanvas):
    for u in range(c.w):
        c.px(u, c.h - 1, c.tone(0))
    c.mirror(3, 0, c.tone(1))


def orc_jaw(c: DecalCanvas):
    for u in range(c.w):
        c.px(u, 0, BLACK)
    for u in (1, 3, 5, 7):
        c.px(u, 0, TUSK.color(3))
    for u in range(c.w):
        c.px(u, 1, c.tone(1))


def nostrils(c: DecalCanvas):
    c.mirror(0, c.h - 1, c.tone(0))


def orc_chest(c: DecalCanvas):
    mid = c.w // 2
    for u in range(2, mid - 1):
        c.px(u, 4, c.tone(1))
        c.px(c.w - 1 - u, 4, c.tone(1))
    c.px(mid - 1, 5, c.tone(1))
    c.px(mid, 5, c.tone(1))
    for v in range(1, 5):
        c.px(mid - 1, v, c.tone(1))
    c.mirror(2, 2, c.tone(4))
    c.mirror(3, 2, c.tone(4))
    for i in range(c.h):
        u = 1 + round(i * (c.w - 4) / (c.h - 1))
        c.px(u, i, LEATHER.color(2))
        c.px(u + 1, i, LEATHER.color(1))
    c.px(3 + round(4 * (c.w - 4) / (c.h - 1)), 4, IRON.color(4))


def orc_abs(c: DecalCanvas):
    mid = c.w // 2
    for v in range(c.h - 1):
        c.px(mid, v, c.tone(1))
    for u in range(2, mid):
        c.px(u, 1, c.tone(1))
        c.px(c.w - 1 - u, 1, c.tone(1))
    c.px(1, 3, c.tone(4))


def skull_buckle(c: DecalCanvas):
    mid = c.w // 2
    c.rect(mid - 1, 0, 3, c.h, TUSK.color(3))
    c.px(mid - 1, 1, BLACK)
    c.px(mid + 1, 1, BLACK)
    c.px(mid, c.h - 1, TUSK.color(1))


def war_paint_rag(c: DecalCanvas):
    mid = c.w // 2
    c.line(mid - 2, 1, mid + 1, 4, TUSK.color(3))
    c.line(mid + 1, 1, mid - 2, 4, TUSK.color(3))
    for u in range(0, c.w, 2):
        c.px(u, c.h - 1, c.tone(0))


def knuckles(c: DecalCanvas):
    for u in range(c.w):
        c.px(u, 0, c.tone(4) if u % 2 == 0 else c.tone(1))


def rivets(c: DecalCanvas):
    for u in (1, c.w - 2):
        c.px(u, 1, c.tone(4))
    for u in range(c.w):
        c.px(u, c.h - 1, c.tone(0))


def axe_blade(c: DecalCanvas):
    for u in range(c.w):
        c.px(u, c.h - 1, STEEL_EDGE.color(4))
        c.px(u, c.h - 2, STEEL_EDGE.color(3))
    c.px(2, c.h - 3, rgb("5a0f0c"))
    c.px(3, c.h - 3, rgb("7a1a14"))
    c.px(2, c.h - 4, rgb("5a0f0c"))


def elf_face(c: DecalCanvas):
    for u in range(c.w):
        c.px(u, 0, ELF_HAIR.color(3))
        c.px(u, 1, ELF_HAIR.color(2) if u % 2 else ELF_HAIR.color(3))
    c.mirror(1, 3, GLOW_RED)
    c.mirror(2, 3, rgb("ff9aa8"))
    c.mirror(1, 2, c.tone(1))
    for v in range(4, c.h):
        for u in range(c.w):
            c.px(u, v, ELF_CLOAK.color(2 if v > 4 else 3))
    c.px(c.w // 2, 5, ELF_CLOAK.color(1))


def elf_chest(c: DecalCanvas):
    for u in range(c.w):
        c.px(u, 0, ELF_STEEL.color(3))
    for i in range(1, c.h - 1):
        c.px(i, i, ELF_LEATHER.color(1))
        c.px(c.w - 1 - i, i, ELF_LEATHER.color(1))
    c.px(c.w // 2, c.h // 2, ELF_GLOW.color(4))
    for u in range(c.w):
        c.px(u, c.h - 1, ELF_STEEL.color(2))


def corset(c: DecalCanvas):
    mid = c.w // 2
    for v in range(c.h):
        c.px(mid - 1, v, ELF_LEATHER.color(0))
        c.px(mid, v, ELF_LEATHER.color(0))
        if v % 2 == 1:
            c.px(mid - 1, v, ELF_STEEL.color(3))
            c.px(mid, v, ELF_STEEL.color(3))


def elf_sigil(c: DecalCanvas):
    mid = c.w // 2
    for v, half in ((2, 0), (3, 1), (4, 2), (5, 1), (6, 0)):
        c.px(mid - half, v, ELF_GLOW.color(2))
        c.px(mid + half - (1 if c.w % 2 == 0 else 0), v, ELF_GLOW.color(2))
    c.px(mid, 4, ELF_GLOW.color(4))


def silver_trim(c: DecalCanvas):
    for u in range(c.w):
        c.px(u, 0, ELF_STEEL.color(3))


def silver_buckle(c: DecalCanvas):
    mid = c.w // 2
    c.rect(mid - 1, 0, 2, c.h, ELF_STEEL.color(3))


def dagger_edge(c: DecalCanvas):
    for u in range(c.w):
        c.px(u, 0, ELF_STEEL.color(4))


def troll_face(c: DecalCanvas):
    c.mirror(1, 3, BLACK)
    c.mirror(2, 3, EYE_YELLOW)
    c.mirror(2, 4, c.tone(1))
    c.px(1, 5, c.tone(4))
    c.px(c.w - 3, 1, c.tone(4))


def troll_jaw(c: DecalCanvas):
    for u in range(c.w):
        c.px(u, 0, BLACK)
    for u in (1, 4, 7):
        c.px(u, 0, TUSK.color(3))
    c.px(2, 1, c.tone(4))


def troll_belly(c: DecalCanvas):
    for v in range(c.h - 1):
        for u in range(2, c.w - 2):
            c.px(u, v, c.tone(3))
    c.px(c.w // 2, c.h // 2, c.tone(1))


def tooth_necklace(c: DecalCanvas):
    for u in range(2, c.w - 2):
        v = round(3.0 * math.sin(math.pi * (u - 2) / (c.w - 5)))
        c.px(u, v, c.tone(1))
        if u % 3 == 1:
            c.px(u, v + 1, TUSK.color(4))
            c.px(u, v + 2, TUSK.color(2))


def claws(c: DecalCanvas):
    for u in range(0, c.w, 2):
        c.px(u, c.h - 1, TUSK.color(3))


def fringe(c: DecalCanvas):
    for u in range(c.w):
        if u % 2:
            c.px(u, c.h - 1, c.tone(0))
    c.px(c.w // 2, 2, TUSK.color(4))
    c.px(c.w // 2 - 1, 3, TUSK.color(3))


def faceplate(c: DecalCanvas):
    for u in range(c.w):
        c.px(u, 1, BLACK)
    c.mirror(1, 1, GLOW_RED)
    c.mirror(2, 1, GLOW_ORANGE)
    mid = c.w // 2
    c.px(mid, 0, c.tone(4))
    c.px(mid, 1, c.tone(3))
    for v in range(3, c.h):
        c.mirror(mid - 2, v, BLACK)


def helmet_band(c: DecalCanvas):
    for u in range(c.w):
        c.px(u, 0, GOLD.color(3))
    c.mirror(1, 2, c.tone(4))


def breastplate(c: DecalCanvas):
    mid = c.w // 2
    for u in range(c.w):
        c.px(u, 0, GOLD.color(3))
        c.px(u, c.h - 1, GOLD.color(2))
    c.rect(mid - 2, 1, 5, 5, CRIMSON.color(2))
    for v in range(1, 7):
        c.px(mid, v, GOLD.color(4))
    c.rect(mid - 2, 2, 5, 1, GOLD.color(3))
    c.px(mid - 1, 6, CRIMSON.color(1))
    c.px(mid + 1, 6, CRIMSON.color(1))


def tabard(c: DecalCanvas):
    mid = c.w // 2
    for u in range(c.w):
        c.px(u, 0, GOLD.color(3))
    c.rect(mid - 1, 2, 2, 4, GOLD.color(3))
    c.rect(mid - 2, 3, 4, 1, GOLD.color(3))
    for u in range(c.w):
        if u % 2 == 0:
            c.px(u, c.h - 1, CRIMSON.color(0))


def cape_back(c: DecalCanvas):
    mid = c.w // 2
    for u in range(c.w):
        c.px(u, c.h - 1, GOLD.color(2))
        c.px(u, c.h - 2, GOLD.color(3))
        c.px(u, 0, GOLD.color(2))
    for v in range(3, 11):
        c.px(mid, v, GOLD.color(3))
    c.rect(mid - 2, 5, 5, 1, GOLD.color(3))
    c.rect(mid - 1, 3, 3, 1, GOLD.color(4))


def gold_hem(c: DecalCanvas):
    for u in range(c.w):
        c.px(u, c.h - 1, GOLD.color(3))


def sword_blade(c: DecalCanvas):
    for u in range(c.w):
        c.px(u, 0, STEEL_EDGE.color(4))
        c.px(u, c.h - 1, STEEL_EDGE.color(4))
        if 1 < u < c.w - 2:
            c.px(u, c.h // 2, STEEL_EDGE.color(1))


# --------------------------------------------------------------------------- ear plates
# 귀는 두께 없는 판 하나에 모양을 투명으로 오려 냅니다. s는 뿌리(0)에서 끝(1)까지의 거리입니다.


def elf_ear_shape(xf, yf):
    s = 1.0 - xf
    return 0.95 * s ** 0.9 <= yf <= 0.7 + 0.3 * s


def orc_ear_shape(xf, yf):
    s = 1.0 - xf
    return 0.05 + 0.75 * s ** 1.1 <= yf <= 1.0 - 0.15 * s


def troll_ear_shape(xf, yf):
    s = 1.0 - xf
    return 0.5 * s ** 0.8 <= yf <= 1.0 - 0.5 * s


def ear_plates(m: Model, parent, root, rotation, length, height, material, shape):
    """Two ear plates, each in its own bone so the group can tilt on several axes."""
    x, y, z = root
    for side, sign in (("right", 1), ("left", -1)):
        bone = side + "_ear"
        m.bone(bone, (x if sign == 1 else -x, y, z), parent,
               rotation=(rotation[0], rotation[1] * sign, rotation[2] * sign))
        frm, to = (x - length, y - height / 2, z), (x, y + height / 2, z)
        if sign == 1:
            m.cube(bone, bone, frm, to, material, plane="z", shape=shape)
        else:
            m.mirrored(bone, bone, frm, to, material, plane="z", shape=shape)


# --------------------------------------------------------------------------- units


def orc_warrior() -> Model:
    m = Model("orc_warrior", "오크 전사")

    for side, add in (("right", m.cube), ("left", m.mirrored)):
        leg = side + "_leg"
        m.bone(leg, (-3 if side == "right" else 3, 10, 0), "root")
        add(leg, side + "_thigh", (-5.5, 4.5, -2.5), (-0.5, 10, 2.5), LEATHER)
        add(leg, side + "_boot", (-6, 0, -3.2), (0, 4.5, 2.8), LEATHER_DARK)
        add(leg, side + "_cuff", (-6.3, 3.8, -3.5), (0.3, 5.3, 3.1), FUR)

    m.bone("body", (0, 10, 0), "root")
    m.cube("body", "hips", (-6.5, 9.5, -3.6), (6.5, 12.5, 3.6), LEATHER, {"north": skull_buckle})
    m.cube("body", "loin_front", (-3, 3, -4.1), (3, 10, -3.6), RAG, {"north": war_paint_rag},
           rot=(8, 0, 0), pivot=(0, 10, -3.85))
    m.cube("body", "loin_back", (-3, 4.5, 3.6), (3, 10, 4.1), RAG)
    m.cube("body", "waist", (-5.5, 12.5, -3), (5.5, 16.5, 3), ORC_SKIN, {"north": orc_abs})
    m.cube("body", "chest", (-7, 16.5, -4), (7, 24.5, 3.5), ORC_SKIN, {"north": orc_chest})

    m.bone("head", (0, 23.5, -2.5), "body")
    m.cube("head", "skull", (-4, 23.5, -7.5), (4, 31, 0.5), ORC_SKIN, {"north": orc_skull})
    m.cube("head", "brow", (-4.5, 28.5, -8), (4.5, 30, -7.2), ORC_SKIN_DARK, {"north": orc_brow})
    m.cube("head", "jaw", (-4.5, 22.5, -8.6), (4.5, 26, -4), ORC_SKIN_DARK, {"north": orc_jaw})
    m.cube("head", "nose", (-1, 26.3, -8.2), (1, 28.2, -7.4), ORC_SKIN_DARK, {"north": nostrils})
    m.cube("head", "right_tusk", (-3.8, 25.5, -9.2), (-2.8, 28.6, -8.2), TUSK, rot=(0, 0, 14), pivot=(-3.3, 25.5, -8.7))
    m.mirrored("head", "left_tusk", (-3.8, 25.5, -9.2), (-2.8, 28.6, -8.2), TUSK, rot=(0, 0, 14), pivot=(-3.3, 25.5, -8.7))
    ear_plates(m, "head", (-4, 28.4, -4.4), (0, 28, -18), 5.6, 3.6, ORC_SKIN, orc_ear_shape)
    m.cube("head", "topknot", (-1.5, 31, -4.5), (1.5, 34, -1.5), HAIR_BLACK, rot=(-15, 0, 0), pivot=(0, 31, -3))
    m.cube("head", "topknot_band", (-1.8, 31, -4.8), (1.8, 32, -1.2), LEATHER)

    for side, add in (("right", m.cube), ("left", m.mirrored)):
        arm = side + "_arm"
        m.bone(arm, (-8.5 if side == "right" else 8.5, 22.5, 0), "body")
        add(arm, side + "_upper", (-11, 16, -2.5), (-6.5, 24, 2.5), ORC_SKIN)
        add(arm, side + "_forearm", (-11.8, 9.5, -3), (-5.8, 16.5, 3), ORC_SKIN)
        add(arm, side + "_bracer", (-12.1, 11, -3.3), (-5.5, 15, 3.3), LEATHER)
        add(arm, side + "_fist", (-11.5, 6.5, -2.8), (-6.1, 9.5, 2.8), ORC_SKIN_DARK, {"north": knuckles})
    pad = (-8.5, 24, 0)
    m.cube("right_arm", "right_pauldron", (-12.5, 21.5, -4), (-5.8, 26, 4), IRON, {"north": rivets, "west": rivets},
           rot=(0, 0, 15), pivot=pad)
    m.cube("right_arm", "right_spike_a", (-10.5, 26, -2), (-9.2, 28.8, -0.7), TUSK, rot=(0, 0, 30), pivot=(-9.85, 26, -1.35))
    m.cube("right_arm", "right_spike_b", (-10.5, 26, 0.7), (-9.2, 28, 2), TUSK, rot=(0, 0, 30), pivot=(-9.85, 26, 1.35))
    m.mirrored("left_arm", "left_strap", (-11.3, 20.5, -2.8), (-6.2, 22.5, 2.8), LEATHER)

    m.bone("axe", (-8.8, 8, 0), "right_arm")
    m.cube("axe", "axe_handle", (-9.55, 7.25, -19), (-8.05, 8.75, 3.5), WOOD)
    m.cube("axe", "axe_wrap", (-9.75, 7.05, -2), (-7.85, 8.95, 1.5), LEATHER_DARK)
    m.cube("axe", "axe_pommel", (-9.8, 6.8, 3.5), (-7.8, 9.2, 5), IRON)
    m.cube("axe", "axe_head", (-9.4, 0.5, -20), (-8.2, 7.25, -12.5), IRON, {"west": axe_blade, "east": axe_blade})
    m.cube("axe", "axe_back", (-9.4, 8.75, -18), (-8.2, 11.5, -14.5), IRON)

    humanoid_idle(m, 2.4, breathe=0.5, sway=2, extra={
        "body": {"position": [(0, (0, 0, 0)), (1.2, (0, 0.5, 0)), (2.4, (0, 0, 0))],
                 "rotation": [(0, (8, 0, 0)), (1.2, (10, 0, 0)), (2.4, (8, 0, 0))]},
        "head": {"rotation": [(0, (-8, 0, 0)), (1.2, (-11, 5, 0)), (2.4, (-8, 0, 0))]},
    })
    humanoid_walk(m, 1.0, leg=30, arm=18, bob=0.8, extra={
        "body": {"rotation": [(0, (8, 0, -2)), (0.5, (8, 0, 2)), (1.0, (8, 0, -2))],
                 "position": [(0, (0, 0, 0)), (0.25, (0, -0.8, 0)), (0.5, (0, 0, 0)), (0.75, (0, -0.8, 0)), (1.0, (0, 0, 0))]},
        "head": {"rotation": [(0, (-8, 0, 0)), (1.0, (-8, 0, 0))]},
    })
    m.animation("attack", 0.8, "once", {
        "right_arm": {"rotation": [(0, (0, 0, 0)), (0.3, (-175, 0, 8)), (0.42, (-35, 0, 0)), (0.55, (-30, 0, 0)), (0.8, (0, 0, 0))]},
        "left_arm": {"rotation": [(0, (0, 0, 0)), (0.3, (-40, 0, 12)), (0.42, (10, 0, 0)), (0.8, (0, 0, 0))]},
        "body": {"rotation": [(0, (8, 0, 0)), (0.3, (-8, 0, 0)), (0.42, (22, 0, 0)), (0.55, (20, 0, 0)), (0.8, (8, 0, 0))]},
        "head": {"rotation": [(0, (-8, 0, 0)), (0.3, (-12, 0, 0)), (0.42, (-18, 0, 0)), (0.8, (-8, 0, 0))]},
    })
    humanoid_death(m, lift=4.0)
    return m


def elf_assassin() -> Model:
    m = Model("elf_assassin", "엘프 암살자")

    for side, add in (("right", m.cube), ("left", m.mirrored)):
        leg = side + "_leg"
        m.bone(leg, (-1.6 if side == "right" else 1.6, 12, 0), "root")
        add(leg, side + "_thigh", (-3, 7, -1.5), (-0.2, 12, 1.5), ELF_LEATHER)
        add(leg, side + "_boot", (-3.2, 0, -2.2), (0, 7, 1.7), ELF_CLOAK)
        add(leg, side + "_cuff", (-3.4, 6, -2.0), (0.2, 7.5, 1.9), ELF_ARMOR, {"north": silver_trim})

    m.bone("body", (0, 12, 0), "root")
    m.cube("body", "hips", (-3.4, 11.5, -2), (3.4, 13, 2), ELF_LEATHER, {"north": silver_buckle})
    m.cube("body", "sash", (-1.3, 6, -2.2), (1.3, 12, -1.9), ELF_CLOAK)
    m.cube("body", "waist", (-2.8, 13, -1.7), (2.8, 17, 1.7), ELF_ARMOR, {"north": corset})
    m.cube("body", "chest", (-3.5, 17, -2), (3.5, 22.5, 2), ELF_ARMOR, {"north": elf_chest})
    m.cube("body", "collar", (-2.8, 22, -2.3), (2.8, 23.5, 2.3), ELF_CLOAK)
    m.bone("cloak", (0, 23, 2), "body")
    m.cube("cloak", "cape", (-3.8, 6, 2), (3.8, 23, 2.6), ELF_CLOAK, {"south": elf_sigil})
    m.cube("cloak", "hood", (-3.3, 21, 1.6), (3.3, 24.5, 3.4), ELF_CLOAK, rot=(-12, 0, 0), pivot=(0, 22.5, 2.5))

    m.bone("head", (0, 23, 0), "body")
    m.cube("head", "head", (-3.2, 23, -3.2), (3.2, 29.5, 3.2), ELF_SKIN, {"north": elf_face})
    m.cube("head", "hair_top", (-3.5, 28.5, -3.5), (3.5, 30.6, 3.5), ELF_HAIR)
    m.cube("head", "fringe", (-3.5, 26.8, -3.7), (-0.8, 29, -3.2), ELF_HAIR)
    m.cube("head", "hair_back", (-3.5, 21, 1.4), (3.5, 28.6, 3.6), ELF_HAIR)
    m.cube("head", "ponytail", (-1, 16.5, 3.4), (1, 24, 4.8), ELF_HAIR, rot=(-18, 0, 0), pivot=(0, 24, 4.1))
    m.cube("head", "fringe_side", (-3.7, 24.5, -3.4), (-3.2, 28.5, 0), ELF_HAIR, rot=(0, 0, -10), pivot=(-3.45, 28.5, -1.7))
    ear_plates(m, "head", (-3.2, 26.9, 0.2), (0, 25, -14), 4.6, 2.8, ELF_SKIN, elf_ear_shape)

    for side, add in (("right", m.cube), ("left", m.mirrored)):
        arm = side + "_arm"
        m.bone(arm, (-4.3 if side == "right" else 4.3, 21.5, 0), "body")
        add(arm, side + "_upper", (-5.4, 16, -1.2), (-3.2, 22.5, 1.2), ELF_LEATHER)
        add(arm, side + "_shoulder", (-5.8, 20.5, -1.6), (-3, 23, 1.6), ELF_ARMOR,
            {"north": silver_trim, "west": silver_trim, "east": silver_trim, "south": silver_trim})
        add(arm, side + "_glove", (-5.5, 11.5, -1.3), (-3.1, 16, 1.3), ELF_CLOAK)
        add(arm, side + "_hand", (-5.3, 9.8, -1.1), (-3.3, 11.5, 1.1), ELF_SKIN)
        dagger = side + "_dagger"
        m.bone(dagger, (-4.3 if side == "right" else 4.3, 10.6, 0), arm)
        add(dagger, side + "_grip", (-4.8, 10.1, -2.8), (-3.8, 11.1, 0.8), ELF_LEATHER)
        add(dagger, side + "_guard", (-5.3, 9.6, -3.4), (-3.3, 11.6, -2.8), ELF_STEEL)
        add(dagger, side + "_blade", (-4.65, 10.0, -9.5), (-3.95, 11.2, -3.4), ELF_STEEL, {"up": dagger_edge})
        add(dagger, side + "_point", (-4.65, 10.3, -11), (-3.95, 10.9, -9.5), ELF_STEEL)
        add(dagger, side + "_rune", (-4.7, 10.4, -8), (-3.9, 10.8, -5), ELF_GLOW)

    humanoid_idle(m, 1.6, breathe=0.25, sway=4, extra={
        "body": {"position": [(0, (0, 0, 0)), (0.8, (0, 0.25, 0)), (1.6, (0, 0, 0))],
                 "rotation": [(0, (8, 0, 0)), (0.8, (10, 0, 0)), (1.6, (8, 0, 0))]},
        "right_arm": {"rotation": [(0, (-25, 0, -8)), (0.8, (-30, 0, -12)), (1.6, (-25, 0, -8))]},
        "left_arm": {"rotation": [(0, (-25, 0, 8)), (0.8, (-30, 0, 12)), (1.6, (-25, 0, 8))]},
        "cloak": {"rotation": [(0, (6, 0, 0)), (0.8, (10, 0, 0)), (1.6, (6, 0, 0))]},
    })
    humanoid_walk(m, 0.6, leg=40, arm=30, bob=0.7, extra={
        "body": {"rotation": [(0, (14, 0, 0)), (0.6, (14, 0, 0))],
                 "position": [(0, (0, 0, 0)), (0.15, (0, -0.7, 0)), (0.3, (0, 0, 0)), (0.45, (0, -0.7, 0)), (0.6, (0, 0, 0))]},
        "cloak": {"rotation": [(0, (25, 0, 0)), (0.3, (35, 0, 0)), (0.6, (25, 0, 0))]},
    })
    m.animation("attack", 0.5, "once", {
        "right_arm": {"rotation": [(0, (-25, 0, -8)), (0.08, (15, 0, -5)), (0.16, (-95, -10, 0)), (0.3, (-60, 0, -5)), (0.5, (-25, 0, -8))]},
        "left_arm": {"rotation": [(0, (-25, 0, 8)), (0.16, (10, 0, 5)), (0.26, (-95, 10, 0)), (0.38, (-60, 0, 5)), (0.5, (-25, 0, 8))]},
        "body": {"rotation": [(0, (8, 0, 0)), (0.16, (16, 18, 0)), (0.26, (16, -18, 0)), (0.5, (8, 0, 0))]},
        "cloak": {"rotation": [(0, (6, 0, 0)), (0.2, (30, 0, 0)), (0.5, (6, 0, 0))]},
    })
    humanoid_death(m, lift=2.5, extra={
        "cloak": {"rotation": [(0, (0, 0, 0)), (0.8, (60, 0, 0)), (1.1, (70, 0, 0))]},
    })
    return m


def troll_javelineer() -> Model:
    m = Model("troll_javelineer", "트롤 투창병")

    for side, add in (("right", m.cube), ("left", m.mirrored)):
        leg = side + "_leg"
        m.bone(leg, (-3 if side == "right" else 3, 15, 1), "root")
        add(leg, side + "_thigh", (-5.5, 8, -1.5), (-0.5, 15, 3.5), TROLL_SKIN)
        add(leg, side + "_shin", (-5, 2, -1), (-1, 8, 3), TROLL_SKIN)
        add(leg, side + "_wrap", (-5.3, 4.5, -1.3), (-0.7, 6.5, 3.3), HIDE)
        add(leg, side + "_foot", (-6, 0, -4), (0, 2.5, 3.5), TROLL_SKIN_DARK, {"north": claws})

    m.bone("body", (0, 15, 1), "root")
    m.cube("body", "hips", (-6, 14, -2.2), (6, 17, 5.2), HIDE)
    m.cube("body", "loin_front", (-4, 6.5, -2.8), (4, 14.5, -2.2), HIDE, {"north": fringe})
    m.cube("body", "belly", (-5.5, 17, -2), (5.5, 22, 5), TROLL_SKIN, {"north": troll_belly})
    m.cube("body", "chest", (-6.5, 22, -2.5), (6.5, 29, 5.5), TROLL_SKIN, {"north": tooth_necklace})
    # 화살통은 등에 비스듬히 멥니다. 창대와 촉도 같은 축과 중심으로 함께 기울입니다.
    quiver = dict(rot=(0, 0, -28), pivot=(2.5, 23, 7))
    m.cube("body", "quiver", (0.5, 17, 5.5), (4.5, 29, 8.5), HIDE, **quiver)
    m.cube("body", "quiver_strap", (-6.2, 22, -2.7), (6.2, 23, -2.4), LEATHER_DARK, rot=(0, 0, -28), pivot=(0, 22.5, -2.55))
    m.cube("body", "spare_shaft_a", (1.2, 29, 6.3), (2.0, 35, 7.1), WOOD, **quiver)
    m.cube("body", "spare_shaft_b", (3.0, 29, 6.8), (3.8, 34, 7.6), WOOD, **quiver)
    m.cube("body", "spare_tip_a", (1.0, 35, 6.1), (2.2, 37, 7.3), FLINT, **quiver)
    m.cube("body", "spare_tip_b", (2.8, 34, 6.6), (4.0, 36, 7.8), FLINT, **quiver)

    m.bone("head", (0, 28, -2), "body")
    m.cube("head", "head", (-4, 27.5, -7), (4, 34.5, 1), TROLL_SKIN, {"north": troll_face})
    m.cube("head", "brow", (-4.3, 32, -7.5), (4.3, 33.5, -6.8), TROLL_SKIN_DARK)
    m.cube("head", "nose", (-1, 28.5, -10), (1, 31.8, -7), TROLL_SKIN_DARK, {"north": nostrils},
           rot=(-22, 0, 0), pivot=(0, 31.8, -7))
    m.cube("head", "jaw", (-4.2, 26.5, -7.6), (4.2, 29, -3), TROLL_SKIN_DARK, {"north": troll_jaw})
    m.cube("head", "right_tusk", (-3.4, 28, -8.2), (-2.4, 30.5, -7.2), TUSK, rot=(0, 0, 16), pivot=(-2.9, 28, -7.7))
    m.mirrored("head", "left_tusk", (-3.4, 28, -8.2), (-2.4, 30.5, -7.2), TUSK, rot=(0, 0, 16), pivot=(-2.9, 28, -7.7))
    ear_plates(m, "head", (-4, 31.2, -1.4), (0, 16, -6), 6.5, 3.0, TROLL_SKIN_DARK, troll_ear_shape)
    m.cube("head", "mohawk", (-1, 34.5, -6), (1, 38, 1.5), TROLL_HAIR)
    m.cube("head", "mohawk_tail", (-1, 32, 1), (1, 36, 3.5), TROLL_HAIR, rot=(-25, 0, 0), pivot=(0, 35, 1))

    for side, add in (("right", m.cube), ("left", m.mirrored)):
        arm = side + "_arm"
        m.bone(arm, (-8 if side == "right" else 8, 27, 1), "body")
        add(arm, side + "_upper", (-10.5, 19, -1.5), (-5.5, 28, 3.5), TROLL_SKIN)
        add(arm, side + "_forearm", (-11, 10, -2), (-5, 19, 4), TROLL_SKIN)
        add(arm, side + "_wrap", (-11.3, 11, -2.3), (-4.7, 14, 4.3), HIDE)
        add(arm, side + "_hand", (-10.7, 7, -1.7), (-5.3, 10, 3.7), TROLL_SKIN_DARK, {"north": claws})

    m.bone("javelin", (-8, 8.5, 1), "right_arm")
    m.cube("javelin", "shaft", (-8.5, 8, -14), (-7.5, 9, 8), WOOD)
    m.cube("javelin", "binding", (-8.7, 7.8, -12.5), (-7.3, 9.2, -11), LEATHER_DARK)
    m.cube("javelin", "tip", (-8.8, 7.6, -19), (-7.2, 9.4, -14), FLINT)
    m.cube("javelin", "tip_point", (-8.4, 8.1, -20.5), (-7.6, 8.9, -19), FLINT)
    m.cube("javelin", "fletching", (-9, 7.4, 5.5), (-7, 9.6, 8.5), FEATHER)

    humanoid_idle(m, 2.8, breathe=0.5, sway=3, extra={
        "body": {"position": [(0, (0, 0, 0)), (1.4, (0, 0.5, 0)), (2.8, (0, 0, 0))],
                 "rotation": [(0, (10, 0, 0)), (1.4, (12, 0, 0)), (2.8, (10, 0, 0))]},
        "head": {"rotation": [(0, (-10, 0, 0)), (1.4, (-12, -6, 0)), (2.8, (-10, 0, 0))]},
    })
    humanoid_walk(m, 1.3, leg=26, arm=20, bob=1.0, extra={
        "body": {"rotation": [(0, (10, 0, -3)), (0.325, (10, 0, 0)), (0.65, (10, 0, 3)), (0.975, (10, 0, 0)), (1.3, (10, 0, -3))],
                 "position": [(0, (0, 0, 0)), (0.325, (0, -1, 0)), (0.65, (0, 0, 0)), (0.975, (0, -1, 0)), (1.3, (0, 0, 0))]},
    })
    m.animation("attack", 1.0, "once", {
        "right_arm": {"rotation": [(0, (0, 0, 0)), (0.45, (-200, 0, 10)), (0.6, (-75, 0, 0)), (0.7, (-60, 0, 0)), (1.0, (0, 0, 0))]},
        "javelin": {"rotation": [(0, (0, 0, 0)), (0.45, (190, 0, 0)), (0.6, (70, 0, 0)), (0.7, (55, 0, 0)), (1.0, (0, 0, 0))]},
        "left_arm": {"rotation": [(0, (0, 0, 0)), (0.45, (-70, 0, 15)), (0.6, (-20, 0, 0)), (1.0, (0, 0, 0))]},
        "body": {"rotation": [(0, (10, 0, 0)), (0.45, (0, 25, 0)), (0.6, (22, -18, 0)), (0.75, (18, -10, 0)), (1.0, (10, 0, 0))]},
        "head": {"rotation": [(0, (-10, 0, 0)), (0.45, (-5, -20, 0)), (0.6, (-15, 15, 0)), (1.0, (-10, 0, 0))]},
    })
    humanoid_death(m, lift=4.5)
    return m


def legion_commander() -> Model:
    m = Model("legion_commander", "군단장")

    for side, add in (("right", m.cube), ("left", m.mirrored)):
        leg = side + "_leg"
        m.bone(leg, (-2.4 if side == "right" else 2.4, 12, 0), "root")
        add(leg, side + "_cuisse", (-4.6, 7, -2.3), (-0.2, 12, 2.3), KNIGHT_STEEL)
        add(leg, side + "_knee", (-4.8, 5.5, -2.8), (0, 7.5, 2.5), KNIGHT_PLATE, {"north": helmet_band})
        add(leg, side + "_greave", (-4.4, 2.5, -2.1), (-0.4, 5.5, 2.1), KNIGHT_STEEL)
        add(leg, side + "_sabaton", (-4.8, 0, -3), (0, 2.5, 2.4), KNIGHT_DARK)

    m.bone("body", (0, 12, 0), "root")
    m.cube("body", "fauld", (-5.2, 11, -3), (5.2, 13.5, 3), KNIGHT_DARK, {"north": helmet_band})
    m.cube("body", "tabard_front", (-3, 3.5, -3.4), (3, 13, -3), CRIMSON, {"north": tabard},
           rot=(6, 0, 0), pivot=(0, 13, -3.2))
    m.cube("body", "tabard_back", (-3, 4.5, 3), (3, 13, 3.4), CRIMSON)
    m.cube("body", "waist", (-4.5, 13.5, -2.6), (4.5, 17, 2.6), MAIL)
    m.cube("body", "breastplate", (-5.5, 17, -3.2), (5.5, 24.5, 3), KNIGHT_PLATE, {"north": breastplate})
    m.cube("body", "gorget", (-3.5, 24, -3), (3.5, 25.5, 3), KNIGHT_DARK)
    m.bone("cape", (0, 24, 3.2), "body")
    m.cube("cape", "cape", (-5.5, 3, 3), (5.5, 24, 3.8), CRIMSON, {"south": cape_back},
           rot=(-6, 0, 0), pivot=(0, 24, 3.4))
    m.cube("cape", "cape_clasp", (-5.8, 22.5, 2.7), (5.8, 24.5, 4.0), GOLD)

    m.bone("head", (0, 25, 0), "body")
    m.cube("head", "helmet", (-4, 25, -4.3), (4, 33, 4), KNIGHT_STEEL, {"north": helmet_band})
    m.cube("head", "faceplate", (-3.5, 25.5, -4.8), (3.5, 30, -4.3), KNIGHT_DARK, {"north": faceplate})
    m.cube("head", "crest", (-0.8, 33, -3.5), (0.8, 36, 4.5), CRIMSON)
    for side, add in (("right", m.cube), ("left", m.mirrored)):
        add("head", side + "_horn_base", (-6.5, 30, -1), (-4, 31.8, 1), HORN, rot=(0, 0, 12), pivot=(-4, 30.9, 0))
        add("head", side + "_horn_mid", (-7.6, 31, -0.8), (-6.1, 34.2, 0.6), HORN, rot=(0, 0, 22), pivot=(-6.85, 31, -0.1))
        add("head", side + "_horn_tip", (-8.1, 33.6, -0.5), (-7.1, 36.4, 0.3), HORN, rot=(0, 0, 38), pivot=(-7.6, 33.6, -0.1))

    for side, add in (("right", m.cube), ("left", m.mirrored)):
        arm = side + "_arm"
        m.bone(arm, (-6.8 if side == "right" else 6.8, 22.5, 0), "body")
        # 어깨 갑주: 옆으로 길게 뻗어 바깥 끝이 뾰족해집니다. 판을 바깥으로 갈수록 좁게 이어 붙입니다.
        shoulder = dict(rot=(0, 0, 8), pivot=(-6.8, 24, 0))
        hem = {"north": gold_hem, "west": gold_hem, "east": gold_hem, "south": gold_hem}
        add(arm, side + "_pauldron", (-13, 20.5, -4.4), (-3.8, 26.8, 4.4), KNIGHT_PLATE, hem, **shoulder)
        add(arm, side + "_pauldron_wing", (-16.5, 21.3, -3.4), (-13, 26.2, 3.4), KNIGHT_PLATE, hem, **shoulder)
        add(arm, side + "_pauldron_edge", (-19.5, 22.2, -2.2), (-16.5, 25.4, 2.2), KNIGHT_STEEL, **shoulder)
        add(arm, side + "_pauldron_point", (-21.5, 23, -1), (-19.5, 24.6, 1), STEEL_EDGE, **shoulder)
        add(arm, side + "_ridge", (-17, 26.2, -2.6), (-5.2, 27.3, 2.6), GOLD, **shoulder)
        add(arm, side + "_lame", (-14.5, 18.4, -4.1), (-4.4, 20.5, 4.1), KNIGHT_STEEL, hem, **shoulder)
        add(arm, side + "_lame_low", (-12.5, 16.6, -3.8), (-5, 18.4, 3.8), KNIGHT_STEEL, hem, **shoulder)
        add(arm, side + "_upper", (-8.6, 15.5, -2), (-4.8, 21, 2), MAIL)
        add(arm, side + "_vambrace", (-8.9, 11, -2.3), (-4.5, 15.5, 2.3), KNIGHT_STEEL)
        add(arm, side + "_gauntlet", (-9, 8.5, -2.4), (-4.4, 11, 2.4), KNIGHT_DARK)

    m.bone("sword", (-6.7, 9.6, 0), "right_arm")
    m.cube("sword", "grip", (-7.3, 9, -2), (-6.1, 10.2, 2.5), LEATHER_DARK)
    m.cube("sword", "pommel", (-7.6, 8.7, 2.5), (-5.8, 10.5, 3.8), GOLD)
    m.cube("sword", "crossguard", (-9.3, 8.4, -3), (-4.1, 10.8, -2), GOLD)
    m.cube("sword", "blade", (-7.4, 8.2, -21), (-6.0, 11.0, -3), STEEL_EDGE, {"west": sword_blade, "east": sword_blade})
    m.cube("sword", "blade_tip", (-7.3, 8.7, -23), (-6.1, 10.5, -21), STEEL_EDGE)

    humanoid_idle(m, 2.4, breathe=0.3, sway=2, extra={
        "right_arm": {"rotation": [(0, (-20, 0, 0)), (1.2, (-22, 0, -2)), (2.4, (-20, 0, 0))]},
        "cape": {"rotation": [(0, (4, 0, 0)), (1.2, (8, 0, 2)), (2.4, (4, 0, 0))]},
    })
    humanoid_walk(m, 1.1, leg=28, arm=16, bob=0.5, extra={
        "right_arm": {"rotation": [(0, (-20, 0, 0)), (0.275, (-30, 0, 0)), (0.55, (-20, 0, 0)), (0.825, (-10, 0, 0)), (1.1, (-20, 0, 0))]},
        "cape": {"rotation": [(0, (15, 0, 0)), (0.55, (22, 0, 0)), (1.1, (15, 0, 0))]},
    })
    m.animation("attack", 0.9, "once", {
        "body": {"rotation": [(0, (0, 0, 0)), (0.3, (0, 45, 0)), (0.5, (6, -55, 0)), (0.62, (6, -60, 0)), (0.9, (0, 0, 0))]},
        "right_arm": {"rotation": [(0, (-20, 0, 0)), (0.3, (-85, 0, -35)), (0.5, (-85, 0, 10)), (0.62, (-80, 0, 15)), (0.9, (-20, 0, 0))]},
        "sword": {"rotation": [(0, (0, 0, 0)), (0.3, (70, 0, 0)), (0.5, (85, 0, 0)), (0.62, (80, 0, 0)), (0.9, (0, 0, 0))]},
        "left_arm": {"rotation": [(0, (0, 0, 0)), (0.3, (-30, 0, 20)), (0.5, (10, 0, 30)), (0.9, (0, 0, 0))]},
        "cape": {"rotation": [(0, (4, 0, 0)), (0.3, (8, 0, -12)), (0.5, (18, 0, 15)), (0.9, (4, 0, 0))]},
        "head": {"rotation": [(0, (0, 0, 0)), (0.3, (0, -20, 0)), (0.5, (0, 25, 0)), (0.9, (0, 0, 0))]},
    })
    humanoid_death(m, lift=3.0, extra={
        "cape": {"rotation": [(0, (0, 0, 0)), (0.8, (70, 0, 0)), (1.1, (80, 0, 0))]},
    })
    return m


UNITS = [orc_warrior, elf_assassin, troll_javelineer, legion_commander]


# --------------------------------------------------------------------------- preview renderer


def euler(x, y, z):
    """BIL createQuaternion: rotateZ(z) * rotateY(y) * rotateX(x), degrees -> 3x3 matrix."""
    rx, ry, rz = (math.radians(a) for a in (x, y, z))
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    mx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    my = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    mz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return matmul(matmul(mz, my), mx)


def matmul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def apply(m, v):
    return tuple(sum(m[i][k] * v[k] for k in range(3)) for i in range(3))


def sample(animation, bone_uuid, channel, time):
    if not animation:
        return (0.0, 0.0, 0.0)
    animator = animation["animators"].get(bone_uuid)
    if not animator:
        return (0.0, 0.0, 0.0)
    keys = sorted((k for k in animator["keyframes"] if k["channel"] == channel), key=lambda k: k["time"])
    if not keys:
        return (0.0, 0.0, 0.0)
    value = lambda k: tuple(float(k["data_points"][0][a]) for a in "xyz")
    if time <= keys[0]["time"]:
        return value(keys[0])
    for before, after in zip(keys, keys[1:]):
        if before["time"] <= time <= after["time"]:
            span = after["time"] - before["time"]
            t = 0 if span == 0 else (time - before["time"]) / span
            a, b = value(before), value(after)
            return tuple(p + (q - p) * t for p, q in zip(a, b))
    return value(keys[-1])


def bone_transforms(model: Model, animation, time):
    """World transform (rotation matrix, translation) per bone, mirroring BIL's pose code."""
    result = {}

    def visit(bone, parent_rot, parent_pos, parent_origin):
        rot_anim = sample(animation, bone.uuid, "rotation", time)
        pos_anim = sample(animation, bone.uuid, "position", time)
        local = matmul(euler(-rot_anim[0], -rot_anim[1], rot_anim[2]), euler(*bone.rotation))
        offset = tuple(o - p for o, p in zip(bone.origin, parent_origin))
        offset = (offset[0] - pos_anim[0], offset[1] + pos_anim[1], offset[2] + pos_anim[2])
        world_pos = tuple(a + b for a, b in zip(parent_pos, apply(parent_rot, offset)))
        world_rot = matmul(parent_rot, local)
        result[bone.name] = (world_rot, world_pos, bone.origin)
        for child in bone.children:
            visit(child, world_rot, world_pos, bone.origin)

    identity = [[1, 0, 0], [0, 1, 0], [0, 0, 1]]
    visit(model.root, identity, (0.0, 0.0, 0.0), (0.0, 0.0, 0.0))
    return result


def face_corners(cube: Cube, face):
    """Corners (top-left, top-right, bottom-right, bottom-left) as seen from outside the face."""
    (x0, y0, z0), (x1, y1, z1) = cube.frm, cube.to
    return {
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[face]


def render(model: Model, animation=None, time=0.0, yaw=35, pitch=-20, scale=7, size=420):
    """Z-buffered preview. One textured quad per texel, flat colour."""
    view = matmul(euler(pitch, 0, 0), euler(0, yaw, 0))
    transforms = bone_transforms(model, animation, time)
    texture = model.image
    pixels = [(34, 36, 44)] * (size * size)
    depth = [-1e9] * (size * size)
    cx, cy = size / 2, size * 0.78

    def raster(a, b, c, color):
        (ax, ay, az), (bx, by, bz), (cx_, cy_, cz) = a, b, c
        area = (bx - ax) * (cy_ - ay) - (by - ay) * (cx_ - ax)
        if abs(area) < 1e-9:
            return
        minx, maxx = max(0, int(min(ax, bx, cx_))), min(size - 1, int(max(ax, bx, cx_)) + 1)
        miny, maxy = max(0, int(min(ay, by, cy_))), min(size - 1, int(max(ay, by, cy_)) + 1)
        for py in range(miny, maxy + 1):
            for px in range(minx, maxx + 1):
                x, y = px + 0.5, py + 0.5
                w0 = ((bx - x) * (cy_ - y) - (by - y) * (cx_ - x)) / area
                w1 = ((cx_ - x) * (ay - y) - (cy_ - y) * (ax - x)) / area
                w2 = 1 - w0 - w1
                if w0 < -1e-6 or w1 < -1e-6 or w2 < -1e-6:
                    continue
                z = w0 * az + w1 * bz + w2 * cz
                index = py * size + px
                if z > depth[index]:
                    depth[index] = z
                    pixels[index] = color

    for bone_name, bone in model.bones.items():
        rot, pos, origin = transforms[bone_name]
        for cube in bone.cubes:
            for face, info in cube.faces.items():
                u0, v0, u1, v1 = info["uv"]
                tl, tr, br, bl = face_corners(cube, face)
                pw, ph = max(1, math.ceil((u1 - u0) * DENS)), max(1, math.ceil((v1 - v0) * DENS))
                u0, v0 = u0 * DENS, v0 * DENS

                element_rot = euler(*cube.rot)
                element_pivot = cube.pivot or bone.origin

                def point(a, b):
                    top = tuple(p + (q - p) * a for p, q in zip(tl, tr))
                    bottom = tuple(p + (q - p) * a for p, q in zip(bl, br))
                    local = tuple(p + (q - p) * b for p, q in zip(top, bottom))
                    local = tuple(pv + d for pv, d in zip(element_pivot, apply(element_rot, tuple(l - pv for l, pv in zip(local, element_pivot)))))
                    world = tuple(w + d for w, d in zip(pos, apply(rot, tuple(l - o for l, o in zip(local, origin)))))
                    v = apply(view, world)
                    return (cx + v[0] * scale, cy - v[1] * scale, v[2])

                grid = [[point(i / pw, j / ph) for i in range(pw + 1)] for j in range(ph + 1)]
                for j in range(ph):
                    for i in range(pw):
                        color = texture.getpixel((int(u0) + i, int(v0) + j))
                        if color[3] == 0:
                            continue
                        p00, p10, p11, p01 = grid[j][i], grid[j][i + 1], grid[j + 1][i + 1], grid[j + 1][i]
                        raster(p00, p10, p11, color[:3])
                        raster(p00, p11, p01, color[:3])
    image = Image.new("RGB", (size, size))
    image.putdata(pixels)
    return image


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--preview", type=Path, help="Write preview PNGs into this directory")
    parser.add_argument("--json-bundle", type=Path, help="Write every model into one JSON file for the HTML viewer")
    args = parser.parse_args()
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    bundle = {}
    for factory in UNITS:
        model = factory()
        data = model.build()
        path = OUT_DIR / (model.key + ".bbmodel")
        path.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
        bundle[model.key] = {"display": model.display, "model": data}
        print("wrote", path, "elements", len(data["elements"]))
        if args.preview:
            args.preview.mkdir(parents=True, exist_ok=True)
            anims = {a["name"]: a for a in data["animations"]}
            frames = [
                render(model, None, 0, yaw=205, pitch=-15),
                render(model, None, 0, yaw=90 + 180, pitch=-10),
                render(model, anims["attack"], 0.3 if model.key != "troll_javelineer" else 0.45, yaw=235, pitch=-15),
                render(model, anims["attack"], 0.45 if model.key != "troll_javelineer" else 0.6, yaw=235, pitch=-15),
                render(model, anims["death"], 1.5, yaw=235, pitch=-35),
            ]
            sheet = Image.new("RGB", (420 * len(frames), 420))
            for index, frame in enumerate(frames):
                sheet.paste(frame, (420 * index, 0))
            sheet.save(args.preview / (model.key + ".png"))
            model.image.resize((TEX * 3, TEX * 3), Image.NEAREST).save(args.preview / (model.key + "_texture.png"))
    if args.json_bundle:
        args.json_bundle.write_text(json.dumps(bundle, ensure_ascii=False), encoding="utf-8")


if __name__ == "__main__":
    main()
