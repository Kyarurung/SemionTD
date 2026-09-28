// Shared helpers for building invasion army models inside Blockbench (run through the MCP eval tool).
// The model faces -z (Blockbench north). The unit's right side is +x, like a player model.
(function (global) {
    const TEX = 256;   // texture pixels
    const UV = 256;    // UV space: one model pixel is one texel

    function hex(h) {
        return [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
    }

    function makeBuilder(projectName) {
        for (const p of ModelProject.all.slice()) {
            if (p.name === projectName) {
                p.saved = true;
                p.close(true);
            }
        }
        newProject(Formats.free);
        Project.name = projectName;
        Project.texture_width = UV;
        Project.texture_height = UV;

        const groups = {};
        const cubes = [];

        function group(name, origin, parent, rotation) {
            const g = new Group({ name, origin, rotation: rotation || [0, 0, 0] });
            g.addTo(parent ? groups[parent] : undefined).init();
            groups[name] = g;
            return g;
        }

        // box(group, name, from, to, material, {rot, pivot, plane, shape, decal})
        function box(groupName, name, from, to, material, opts) {
            opts = opts || {};
            const g = groups[groupName];
            const cube = new Cube({
                name,
                from,
                to,
                origin: opts.pivot || g.origin.slice(),
                rotation: opts.rot || [0, 0, 0],
                inflate: opts.inflate || 0,
                autouv: 0,
            }).addTo(g).init();
            cubes.push({ cube, material, opts, group: groupName });
            return cube;
        }

        // Right side is given; the left copy is mirrored across x = 0.
        function pair(groupRight, groupLeft, name, from, to, material, opts) {
            opts = opts || {};
            box(groupRight, 'right_' + name, from, to, material, opts);
            const mf = [-to[0], from[1], from[2]];
            const mt = [-from[0], to[1], to[2]];
            const mo = Object.assign({}, opts, {
                rot: opts.rot ? [opts.rot[0], -opts.rot[1], -opts.rot[2]] : undefined,
                pivot: opts.pivot ? [-opts.pivot[0], opts.pivot[1], opts.pivot[2]] : undefined,
                mirrored: true,
            });
            box(groupLeft, 'left_' + name, mf, mt, material, mo);
        }

        return { groups, cubes, group, box, pair };
    }

    // ------------------------------------------------------------------ painting

    const FACE_LIGHT = { up: 1.12, north: 1.0, east: 0.9, west: 0.9, south: 0.82, down: 0.62 };

    // projectors: [{groups: [...], materials: [...], paint: (worldPos, face) => '#rrggbb' | null}]
    // 3차원 공간에 정의한 띠를 여러 큐브 겉면에 이어 칠합니다. 멜빵처럼 여러 면을 가로지르는 무늬용입니다.
    function paintAll(builder, materials, textureName, projectors) {
        const canvas = document.createElement('canvas');
        canvas.width = TEX;
        canvas.height = TEX;
        const ctx = canvas.getContext('2d');
        const img = ctx.createImageData(TEX, TEX);
        const dens = TEX / UV;
        let x = 0, y = 0, row = 0;
        let seed = 1234567;
        const rand = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);

        function place(w, h) {
            if (x + w > TEX) { x = 0; y += row + 1; row = 0; }
            if (y + h > TEX) throw new Error('texture atlas full');
            const at = [x, y];
            x += w + 1;
            row = Math.max(row, h);
            return at;
        }

        function set(px, py, c, a) {
            const i = (py * TEX + px) * 4;
            img.data[i] = c[0]; img.data[i + 1] = c[1]; img.data[i + 2] = c[2]; img.data[i + 3] = a === undefined ? 255 : a;
        }

        const tex = new Texture({ name: textureName }).fromDataURL(blankPng()).add(false);
        tex.uv_width = UV;
        tex.uv_height = UV;

        for (const entry of builder.cubes) {
            const { cube, material, opts } = entry;
            const mat = materials[material];
            if (!mat) throw new Error('unknown material ' + material + ' on ' + cube.name);
            const sx = cube.to[0] - cube.from[0], sy = cube.to[1] - cube.from[1], sz = cube.to[2] - cube.from[2];
            const sizes = { north: [sx, sy], south: [sx, sy], east: [sz, sy], west: [sz, sy], up: [sx, sz], down: [sx, sz] };
            const keep = opts.plane ? { x: ['east', 'west'], y: ['up', 'down'], z: ['north', 'south'] }[opts.plane] : null;
            for (const face of Object.keys(sizes)) {
                if (keep && !keep.includes(face)) {
                    cube.faces[face].texture = null;
                    continue;
                }
                const [w, h] = sizes[face];
                // 판(귀·이빨·뿔)도 다른 파츠와 같은 밀도입니다. opts.dens로만 따로 줄 수 있습니다.
                const d = opts.dens || dens;
                const pw = Math.max(1, Math.ceil(w * d)), ph = Math.max(1, Math.ceil(h * d));
                const [ox, oy] = place(pw, ph);
                paintFace(set, ox, oy, pw, ph, face, mat, opts, rand, cube);
                const active = (projectors || []).filter(pr =>
                    (!pr.groups || pr.groups.includes(entry.group)) && (!pr.materials || pr.materials.includes(material)));
                if (active.length && !opts.plane) project(set, ox, oy, pw, ph, face, cube, active);
                cube.faces[face].uv = [ox / dens, oy / dens, (ox + pw) / dens, (oy + ph) / dens];
                cube.faces[face].texture = tex.uuid;
            }
        }
        ctx.putImageData(img, 0, 0);
        tex.fromDataURL(canvas.toDataURL('image/png'));
        Canvas.updateAll();
        return tex;
    }

    function faceCorners(f, t, face) {
        const [x0, y0, z0] = f, [x1, y1, z1] = t;
        switch (face) {
            case 'north': return [[x1, y1, z0], [x0, y1, z0], [x0, y0, z0], [x1, y0, z0]];
            case 'south': return [[x0, y1, z1], [x1, y1, z1], [x1, y0, z1], [x0, y0, z1]];
            case 'east': return [[x1, y1, z1], [x1, y1, z0], [x1, y0, z0], [x1, y0, z1]];
            case 'west': return [[x0, y1, z0], [x0, y1, z1], [x0, y0, z1], [x0, y0, z0]];
            case 'up': return [[x0, y1, z0], [x1, y1, z0], [x1, y1, z1], [x0, y1, z1]];
            default: return [[x0, y0, z1], [x1, y0, z1], [x1, y0, z0], [x0, y0, z0]];
        }
    }

    function project(set, ox, oy, w, h, face, cube, projectors) {
        const [tl, tr, br, bl] = faceCorners(cube.from, cube.to, face);
        const r = cube.rotation.map(v => v * Math.PI / 180);
        const m = new THREE.Matrix4().makeRotationFromEuler(new THREE.Euler(r[0], r[1], r[2], 'ZYX'));
        const pivot = new THREE.Vector3(...cube.origin);
        const light = FACE_LIGHT[face];
        for (let v = 0; v < h; v++) {
            for (let u = 0; u < w; u++) {
                const a = (u + 0.5) / w, b = (v + 0.5) / h;
                const top = tl.map((k, i) => k + (tr[i] - k) * a);
                const bottom = bl.map((k, i) => k + (br[i] - k) * a);
                const p = new THREE.Vector3(...top.map((k, i) => k + (bottom[i] - k) * b)).sub(pivot).applyMatrix4(m).add(pivot);
                for (const pr of projectors) {
                    const color = pr.paint(p, face);
                    if (color) {
                        set(ox + u, oy + v, hex(color).map(k => Math.max(0, Math.min(255, Math.round(k * light)))));
                        break;
                    }
                }
            }
        }
    }

    function blankPng() {
        const c = document.createElement('canvas');
        c.width = TEX;
        c.height = TEX;
        return c.toDataURL('image/png');
    }

    // Material: {c: [dark, mid, light, highlight] hex, streak: bool, rim: bool}
    function paintFace(set, ox, oy, w, h, face, mat, opts, rand, cube) {
        const ramp = mat.c.map(hex);
        const light = FACE_LIGHT[face];
        const side = face !== 'up' && face !== 'down';
        const mask = shapeMask(opts, face, w, h);
        for (let v = 0; v < h; v++) {
            for (let u = 0; u < w; u++) {
                if (mask && !mask[v][u]) continue;
                // 위가 밝고 아래로 갈수록 어두워지는 기본 음영
                let t = side && h > 1 ? 1 - v / (h - 1) : 0.6;
                let level = 0.8 + t * 1.3;
                // 대각선 하이라이트 줄 (mantaro 식 셀 음영)
                if (mat.streak && side && w > 6 && h > 6) {
                    const d = (u + (h - v)) % Math.max(10, Math.round(w * 0.9));
                    if (d < 2) level += 0.9;
                }
                level += (rand() - 0.5) * (mat.noise || 0.15);
                // 주름치마: 세로로 3칸마다 접힌 골을 어둡게 칠합니다.
                if (mat.pleats && side && w > 3 && u % 3 === 2) level -= 0.8;
                // 단: 옆면 맨 아랫줄을 지정한 색으로 두릅니다.
                if (mat.hem && side && v >= h - (mat.hemRows || 1)) {
                    set(ox + u, oy + v, hex(mat.hem).map(k => Math.max(0, Math.min(255, Math.round(k * light)))));
                    continue;
                }
                const edge = mask ? isEdge(mask, u, v) : (side && w > 3 && (u === 0 || u === w - 1)) || (side && h > 3 && v === h - 1);
                // 판의 날 쪽 가장자리는 어둡게 하지 않고 반짝이는 날로 칠합니다.
                if (edge && opts.edgeGlow && plateX(face, u, w, opts) < opts.edgeGlow.until) {
                    set(ox + u, oy + v, hex(opts.edgeGlow.color));
                    continue;
                }
                if (edge) level -= 0.7;
                if (mat.rim && side && v === 0 && h > 2) level += 1.2;
                const c = sample(ramp, level);
                set(ox + u, oy + v, c.map(k => Math.max(0, Math.min(255, Math.round(k * light)))));
            }
        }
        if (opts.decal && opts.decal[face]) {
            opts.decal[face]((u, v, color) => {
                if (u >= 0 && v >= 0 && u < w && v < h) set(ox + u, oy + v, hex(color));
            }, w, h, !!opts.mirrored);
        }
    }

    function sample(ramp, level) {
        const l = Math.max(0, Math.min(ramp.length - 1, level));
        const lo = Math.floor(l), hi = Math.min(ramp.length - 1, lo + 1), t = l - lo;
        return ramp[lo].map((k, i) => k + (ramp[hi][i] - k) * t);
    }

    function shapeMask(opts, face, w, h) {
        if (!opts.shape) return null;
        const mask = [];
        for (let v = 0; v < h; v++) {
            const row = [];
            const yf = 1 - (v + 0.5) / h;
            for (let u = 0; u < w; u++) {
                let xf = (u + 0.5) / w;
                if (face === 'north' || face === 'east') xf = 1 - xf;
                if (opts.mirrored) xf = 1 - xf;
                row.push(!!opts.shape(xf, yf));
            }
            mask.push(row);
        }
        return mask;
    }

    function plateX(face, u, w, opts) {
        let xf = (u + 0.5) / w;
        if (face === 'north' || face === 'east') xf = 1 - xf;
        if (opts.mirrored) xf = 1 - xf;
        return xf;
    }

    function isEdge(mask, u, v) {
        const at = (a, b) => b >= 0 && b < mask.length && a >= 0 && a < mask[0].length && mask[b][a];
        return !at(u + 1, v) || !at(u - 1, v) || !at(u, v + 1) || !at(u, v - 1);
    }

    // ------------------------------------------------------------------ animation

    // tracks: {bone: {rotation: [[t, [x,y,z]], ...], position: [...]}}
    function animation(builder, name, length, loop, tracks) {
        const anim = new Animation({ name, length, loop, snapping: 20 }).add(false);
        for (const [boneName, channels] of Object.entries(tracks)) {
            const g = builder.groups[boneName];
            if (!g) throw new Error('unknown bone ' + boneName);
            const animator = anim.getBoneAnimator(g);
            for (const [channel, keys] of Object.entries(channels)) {
                for (const [time, v] of keys) {
                    animator.addKeyframe({ channel, time: Math.round(time * 20) / 20, data_points: [{ x: v[0], y: v[1], z: v[2] }], interpolation: 'linear' });
                }
            }
        }
        return anim;
    }

    // ------------------------------------------------------------------ z-fighting
    // 서로 다른 큐브의 보이는 면이 같은 평면에서 겹치면(같은 방향, 거리 0.004 미만, 겹친 폭 0.02 초과) 짝으로 돌려줍니다.
    // 편집 모드의 쉬는 자세를 기준으로, 그룹·큐브 회전을 모두 반영한 월드 좌표로 비교합니다.
    function findZFighting() {
        const V = THREE.Vector3;
        const order = ['east', 'west', 'up', 'down', 'south', 'north'];
        const faces = [];
        Canvas.updateAll();
        for (const c of Cube.all) {
            if (!c.visibility || !c.mesh) continue;
            c.mesh.updateMatrixWorld(true);
            const pos = c.mesh.geometry.attributes.position;
            for (let f = 0; f < 6; f++) {
                const face = c.faces[order[f]];
                if (!face || face.texture === null || face.texture === false) continue;
                const pts = [0, 1, 3, 2].map(i => new V().fromBufferAttribute(pos, f * 4 + i).applyMatrix4(c.mesh.matrixWorld));
                const n = new V().subVectors(pts[1], pts[0]).cross(new V().subVectors(pts[3], pts[0]));
                if (n.length() < 1e-8) continue;
                n.normalize();
                faces.push({ cube: c, face: order[f], pts, n, d: n.dot(pts[0]) });
            }
        }
        const hits = [];
        for (let i = 0; i < faces.length; i++) {
            for (let j = i + 1; j < faces.length; j++) {
                const a = faces[i], b = faces[j];
                if (a.cube === b.cube || a.n.dot(b.n) < 0.9995 || Math.abs(a.d - b.d) > 0.004) continue;
                const u = new V().subVectors(a.pts[1], a.pts[0]).normalize();
                const w = new V().crossVectors(a.n, u);
                const A = a.pts.map(p => [p.dot(u), p.dot(w)]), B = b.pts.map(p => [p.dot(u), p.dot(w)]);
                let overlap = Infinity;
                for (const poly of [A, B]) {
                    for (let k = 0; k < 4; k++) {
                        const p0 = poly[k], p1 = poly[(k + 1) % 4];
                        const ax = [p0[1] - p1[1], p1[0] - p0[0]];
                        const len = Math.hypot(ax[0], ax[1]);
                        if (len < 1e-6) continue;
                        const pa = A.map(p => (p[0] * ax[0] + p[1] * ax[1]) / len);
                        const pb = B.map(p => (p[0] * ax[0] + p[1] * ax[1]) / len);
                        overlap = Math.min(overlap, Math.min(Math.max(...pa), Math.max(...pb)) - Math.max(Math.min(...pa), Math.min(...pb)));
                    }
                }
                if (overlap > 0.02) hits.push([a.cube, a.face, b.cube, b.face]);
            }
        }
        return hits;
    }

    // 겹치는 짝마다 부피가 작은 쪽(덧댄 띠·장식이 대개 작습니다)을 inflate로 조금 부풀려 면을 떼어 냅니다.
    // inflate는 UV 크기를 바꾸지 않고, BIL도 불러올 때 그대로 적용합니다.
    function fixZFighting() {
        const STEP = 0.03;
        const fixed = [];
        for (let pass = 0; pass < 4; pass++) {
            const hits = findZFighting();
            if (!hits.length) break;
            const volume = c => (c.to[0] - c.from[0]) * (c.to[1] - c.from[1]) * (c.to[2] - c.from[2]);
            const bumped = new Set();
            for (const [a, , b] of hits) {
                if (bumped.has(a) || bumped.has(b)) continue;
                const target = volume(a) < volume(b) ? a : b;
                target.inflate = Math.round(((target.inflate || 0) + STEP) * 1000) / 1000;
                bumped.add(target);
                fixed.push(target.name);
            }
        }
        return { fixed, remaining: findZFighting().map(([a, fa, b, fb]) => `${a.name}.${fa} ~ ${b.name}.${fb}`) };
    }

    function save(path) {
        const fs = require('fs');
        if (Modes.options.edit && !Modes.edit) Modes.options.edit.select();
        if (typeof Timeline !== 'undefined') Timeline.setTime(0);
        save.lastZFix = fixZFighting();
        Project.save_path = path;
        Project.export_path = path;
        fs.writeFileSync(path, Codecs.project.compile({ raw: false }));
        Project.saved = true;
    }

    // ------------------------------------------------------------------ plate shapes
    // 판 모양 함수. xf는 -x→+x(판 기준), yf는 아래→위 비율입니다.
    const shapes = {
        // 아래에서 위로 가늘어지며 바깥으로 휘는 엄니/뿔
        tusk: (curve, base) => (xf, yf) => {
            const c = 0.35 + curve * yf * yf;
            const half = (base || 0.3) * (1 - yf) + 0.05;
            return Math.abs(xf - c) <= half;
        },
        // 뾰족한 삼각 가시 (끝이 위)
        spike: (xf, yf) => Math.abs(xf - 0.5) <= 0.48 * (1 - yf) + 0.02,
        // 옆으로 누운 가시 (끝이 판의 +쪽, x판이면 +z)
        spikeSide: (xf, yf) => Math.abs(yf - 0.5) <= 0.48 * (1 - xf) + 0.02,
        // 머리카락 끝: 아래 가장자리를 count개의 뾰족한 가닥으로 자릅니다. depth는 가닥 사이 홈의 깊이(비율).
        strands: (count, depth, offset) => (xf, yf) => {
            const t = xf * count + (offset || 0);
            const tri = Math.abs(t - Math.floor(t) - 0.5) * 2;
            return yf >= depth * tri;
        },
        // 저해상도용 아래 이빨: 아랫줄은 꽉 차고, 윗줄은 송곳니 두 개만 솟습니다.
        fangs: (xf, yf) => yf < 0.5 || (xf > 0.15 && xf < 0.35) || (xf > 0.65 && xf < 0.85),
        // 아래턱에서 위로 솟은 이빨 줄. 가운데 두 개가 조금 더 깁니다.
        teeth: (count) => (xf, yf) => {
            const i = Math.min(count - 1, Math.floor(xf * count));
            const local = xf * count - i;
            const tall = (i === Math.floor(count / 2) - 1 || i === Math.floor(count / 2)) ? 1 : 0.75;
            return yf <= tall * (1 - Math.abs(local - 0.5) * 2 * 0.95);
        },
    };

    // ------------------------------------------------------------------ pixel painting
    // 블록벤치 마인크래프트 스타일 가이드를 따르는 칠하기입니다.
    //  - 모델 1칸이 텍스처 1픽셀이고, 노이즈 없이 램프(어둠→밝음 4단)의 정해진 칸만 씁니다.
    //  - 윗면·앞면이 밝고, 옆면은 아래쪽 줄이 한 단 어두우며(noFade 재질은 생략), 뒷면·아랫면은 어둡습니다.
    //  - 세부 무늬는 opts.px로 면마다 픽셀을 찍습니다. {north: grid | fn, ..., key: {문자: spec}}
    //    grid는 면을 바깥에서 본 모습 그대로(왼쪽 위부터) 적습니다. '.'은 투명입니다.
    //    fn(u, v, w, h)는 spec, null(투명), undefined(기본 음영)를 돌려줍니다.
    //    spec: 'mat'(그 재질의 기본 음영), 'mat:2'(램프 칸 고정), '#rrggbb'.
    //  - opts.bands: [[첫 줄, 끝 줄, 'mat'], ...] 옆면의 가로 줄을 다른 재질로 칠합니다(띠·장갑·부츠 테).
//  - opts.joints: [y, ...] 마디가 만나는 높이. 옆면에서 그 높이에 걸친 줄을 한 단 어둡게 칠합니다(팔꿈치·무릎 주름).
    //  - 판(plane)은 앞면(z판은 north, x판은 east) 기준 grid를 쓰고, 뒷면은 좌우를 뒤집어 칠합니다.
    //  - 왼쪽 복사본(mirrored)은 좌우를 뒤집어 오른쪽 기준으로 적은 무늬가 거울상이 되게 합니다.
    const BASE = { up: 3, north: 2, east: 2, west: 2, south: 1, down: 1 };

    function paintPixels(builder, materials, textureName) {
        const canvas = document.createElement('canvas');
        canvas.width = TEX;
        canvas.height = TEX;
        const ctx = canvas.getContext('2d');
        const img = ctx.createImageData(TEX, TEX);
        let x = 0, y = 0, row = 0;
        function place(w, h) {
            if (x + w > TEX) { x = 0; y += row + 1; row = 0; }
            if (y + h > TEX) throw new Error('texture atlas full');
            const at = [x, y];
            x += w + 1;
            row = Math.max(row, h);
            return at;
        }
        const tex = new Texture({ name: textureName }).fromDataURL(blankPng()).add(false);
        tex.uv_width = UV;
        tex.uv_height = UV;
        const ramps = {};
        const ramp = (k) => {
            if (!materials[k]) throw new Error('unknown material ' + k);
            return ramps[k] || (ramps[k] = materials[k].c.map(hex));
        };

        for (const entry of builder.cubes) {
            const { cube, material, opts } = entry;
            const sx = cube.to[0] - cube.from[0], sy = cube.to[1] - cube.from[1], sz = cube.to[2] - cube.from[2];
            const sizes = { north: [sx, sy], south: [sx, sy], east: [sz, sy], west: [sz, sy], up: [sx, sz], down: [sx, sz] };
            const keep = opts.plane ? { x: ['east', 'west'], y: ['up', 'down'], z: ['north', 'south'] }[opts.plane] : null;
            const px = opts.px || {};
            for (const face of Object.keys(sizes)) {
                // opts.dens: 이 큐브만 한 칸에 픽셀을 더 촘촘히 씁니다(눈·눈썹 판처럼 작은 데 그림이 많은 곳).
                const dens = opts.dens || 1;
                const [w, h] = sizes[face].map(n => Math.max(1, Math.round(n * dens)));
                if ((keep && !keep.includes(face)) || px[face] === null) {
                    cube.faces[face].texture = null;
                    continue;
                }
                // 판의 뒷면은 앞면 무늬를 좌우로 뒤집어 씁니다.
                let src = px[face], flip = !!opts.mirrored;
                if (opts.plane && src === undefined) {
                    const primary = { x: 'east', y: 'up', z: 'north' }[opts.plane];
                    if (face !== primary && px[primary] !== undefined) { src = px[primary]; flip = !flip; }
                }
                const [ox, oy] = place(w, h);
                const side = face !== 'up' && face !== 'down';
                for (let v = 0; v < h; v++) {
                    for (let u = 0; u < w; u++) {
                        const uu = flip ? w - 1 - u : u;
                        let spec;
                        if (typeof src === 'function') spec = src(uu, v, w, h);
                        else if (Array.isArray(src)) {
                            const ch = (src[v] || '')[uu];
                            spec = ch === undefined || ch === '.' ? null : (px.key && px.key[ch]) || material;
                        }
                        if (spec === null) continue;
                        let mat = material;
                        if (side && opts.bands) {
                            for (const [a, b, m] of opts.bands) if (v >= a && v <= b) mat = m;
                        }
                        let color;
                        if (typeof spec === 'string' && spec[0] === '#') color = hex(spec);
                        else {
                            let idx = null;
                            if (typeof spec === 'string') {
                                const [m, i] = spec.split(':');
                                mat = m;
                                if (i !== undefined) idx = +i;
                            }
                            const mdef = materials[mat];
                            if (idx === null) {
                                idx = BASE[face];
                                if (side && !mdef.noFade && !mdef.soft && h >= 3 && v >= h - Math.max(1, Math.round(h * 0.25))) idx -= 1;
                                // 맨살(soft): 아래 줄을 띠처럼 어둡게 하지 않고, 옆면(동·서)의 뒤쪽 세로 한 칸만 한 단 어둡게 해 팔다리를 둥글게 보이게 합니다.
                                if (mdef.soft && w >= 3 && ((face === 'east' && u === 0) || (face === 'west' && u === w - 1))) idx -= 1;
                                // 이음새(opts.joints): 팔꿈치·무릎처럼 마디가 만나는 높이(y) 위아래 한 줄씩을 한 단 어둡게 해 주름선을 긋습니다.
                                if (side && opts.joints) {
                                    const yc = cube.to[1] - (v + 0.5) * (cube.to[1] - cube.from[1]) / h;
                                    if (opts.joints.some(j => Math.abs(yc - j) < 0.75)) idx -= 1;
                                }
                                if (side && mdef.rim && v === 0) idx += 1;
                                if (side && mdef.pleats && w > 2 && u % 2 === 1) idx -= 1;
                                // 머리카락 결: 앞면을 뺀 옆·뒷면에 lines칸마다 한 단 어두운 세로 줄을 긋습니다(linesFront면 앞면에도 긋습니다. 돌결 등).
                                if (side && mdef.lines && (face !== 'north' || mdef.linesFront) && v >= 2 && u % mdef.lines === 1) idx -= 1;
                            }
                            if (side && mdef.hem && v === h - 1 && typeof spec !== 'string') {
                                color = ramp(mdef.hem)[Math.max(0, Math.min(3, BASE[face] - 1))];
                            } else {
                                const r = ramp(mat);
                                color = r[Math.max(0, Math.min(r.length - 1, idx))];
                            }
                        }
                        const i = ((oy + v) * TEX + ox + u) * 4;
                        img.data[i] = color[0]; img.data[i + 1] = color[1]; img.data[i + 2] = color[2]; img.data[i + 3] = 255;
                    }
                }
                cube.faces[face].uv = [ox, oy, ox + w, oy + h];
                cube.faces[face].texture = tex.uuid;
            }
        }
        ctx.putImageData(img, 0, 0);
        tex.fromDataURL(canvas.toDataURL('image/png'));
        Canvas.updateAll();
        return tex;
    }

    /**
     * 여러 회전(바깥 → 안쪽 순서, 각각 블록벤치 ZYX 오일러 도)을 하나로 합친 오일러 각.
     *
     * <p>BIL은 큐브 회전을 첫 번째 축 하나만 살려 아이템 모델로 굽습니다. 두 축 이상 도는 모양은 큐브가 아니라
     * 그룹(디스플레이 엔티티)에 회전을 몰아줘야 게임에서도 블록벤치와 같게 보이므로, 그때 씁니다.
     */
    function composeEuler(...rotations) {
        const D = Math.PI / 180;
        const q = new THREE.Quaternion();
        for (const r of rotations) {
            q.multiply(new THREE.Quaternion().setFromEuler(new THREE.Euler(r[0] * D, r[1] * D, r[2] * D, 'ZYX')));
        }
        const e = new THREE.Euler().setFromQuaternion(q, 'ZYX');
        return [e.x, e.y, e.z].map(v => Math.round(v / D * 1000) / 1000);
    }

    global.InvasionBB = { makeBuilder, paintAll, paintPixels, animation, save, hex, shapes, composeEuler, findZFighting, fixZFighting, TEX, UV };
})(window);
