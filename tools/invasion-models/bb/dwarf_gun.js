// 드워프 총병의 나팔총(블런더버스). 드워프 모델과 따로 다듬은 뒤 오른손에 붙입니다.
//
// 좌표는 개머리 목(오른손이 쥐는 곳)이 원점이고 총구는 +y로 뻗습니다. 총열은 +z 쪽(위), 방아쇠울은 -z 쪽(아래)입니다.
// 손 그룹 안에서 x축 -90도로 눕히면 총구가 앞(-z)을, 총열 위가 위를 향합니다. 텍스처는 바닐라식(paintPixels)입니다.
// 단독 확인: window.__buildStandaloneDwarfGun.
(function (global) {
    const MATERIALS = {
        wood: { c: ['#3a2413', '#56371e', '#704a2a', '#8c6038'] },
        iron: { c: ['#2f3238', '#4a4f57', '#6b727c', '#9aa2ad'], rim: true },
        brass: { c: ['#6b4f1a', '#9a7426', '#c99a3a', '#f0c65a'] },
        dark: { c: ['#0b0a0c', '#141216', '#1d1a20', '#27232b'] },
    };

    function dwarfGun(B, group, grip) {
        const [gx, gy, gz] = grip;
        const at = (p) => [p[0] + gx, p[1] + gy, p[2] + gz];
        const box = (name, from, to, mat, opts) => {
            const o = Object.assign({}, opts || {});
            if (o.pivot) o.pivot = at(o.pivot);
            B.box(group, 'gun_' + name, at(from), at(to), 'gun.' + mat, o);
        };
        // 개머리판: 뒤로 갈수록 두꺼워지는 나무, 끝에 황동 덧쇠
        box('stock', [-0.9, -8, -1.8], [0.9, -3, 1.2], 'wood', { rot: [8, 0, 0], pivot: [0, -3, 0] });
        box('butt_plate', [-1, -8.8, -2], [1, -8, 1.4], 'brass', { rot: [8, 0, 0], pivot: [0, -3, 0] });
        box('wrist', [-0.6, -3.2, -0.8], [0.6, 1, 0.8], 'wood');
        // 총열: 개머리 위로 이어지는 쇠 총열, 황동 띠 두 줄, 끝이 나팔처럼 벌어진 총구
        box('forestock', [-0.7, 0, -0.5], [0.7, 12, 0.5], 'wood');
        box('barrel', [-0.8, -1, 0.3], [0.8, 16, 1.9], 'iron');
        box('band_a', [-0.95, 4, 0.1], [0.95, 5, 2.1], 'brass');
        box('band_b', [-0.95, 10, 0.1], [0.95, 11, 2.1], 'brass');
        box('muzzle', [-1.3, 15.5, -0.3], [1.3, 18, 2.5], 'brass', {
            px: { up: (u, v, w, h) => (u > 0 && u < w - 1 && v > 0 && v < h - 1 ? 'gun.dark:0' : undefined) },
        });
        // 부싯돌 격발 장치와 방아쇠울
        box('lock_plate', [0.7, -1.5, -0.2], [1.1, 1.5, 1.2], 'brass');
        box('hammer', [0.75, 0.5, 1.2], [1.05, 1.6, 1.9], 'iron', { rot: [-20, 0, 0], pivot: [0.9, 0.5, 1.2] });
        box('trigger_guard', [-0.3, -2.2, -1.5], [0.3, 0, -0.8], 'brass');
    }

    const PREFIXED = Object.fromEntries(Object.entries(MATERIALS).map(([k, v]) => ['gun.' + k, v]));
    global.InvasionParts = Object.assign(global.InvasionParts || {}, { dwarfGun, dwarfGunMaterials: PREFIXED });

    if (global.__buildStandaloneDwarfGun) {
        const B = InvasionBB.makeBuilder('dwarf_gun');
        B.group('gun', [0, 0, 0]);
        dwarfGun(B, 'gun', [0, 0, 0]);
        InvasionBB.paintPixels(B, PREFIXED, 'dwarf_gun');
        global.__standaloneResult = { cubes: B.cubes.length };
    }
})(window);
