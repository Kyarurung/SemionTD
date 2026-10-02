// 공성 골렘. 군단장보다 작은 워든 체형(키 약 38): 넓고 두꺼운 몸통, 짧은 다리, 땅에 닿을 듯 긴 팔, 몸에 파묻힌 넓적한 머리.
// 돌로 깎은 몸에 쇠띠를 두르고, 가슴 한가운데 주황 룬 핵이 빛납니다. 오른 주먹은 쇠 공성추, 왼 주먹은 돌덩이.
// 모델은 자유롭게 큐브로 짜고 텍스처는 바닐라식(paintPixels)입니다. Run after common.js.
(function () {
    const B = InvasionBB.makeBuilder('siege_golem');
    const { group, box } = B;

    const M = {
        stone: { c: ['#3a3a3e', '#56565c', '#77777e', '#9a9aa2'], lines: 4, linesFront: true },
        stoneDark: { c: ['#2a2a2e', '#3e3e44', '#56565c', '#6e6e76'] },
        iron: { c: ['#2a2d33', '#454a53', '#676e79', '#959eaa'], rim: true },
        rune: { c: ['#a33a08', '#e0601a', '#ff9a2a', '#ffe08a'], noFade: true },
        dark: { c: ['#0b0a0c', '#141216', '#1d1a20', '#27232b'] },
    };

    // ---------------------------------------------------------------- skeleton
    group('root', [0, 0, 0]);
    group('body', [0, 11, 0], 'root');
    group('head', [0, 31, -3], 'body');
    for (const [side, sign] of [['right', 1], ['left', -1]]) {
        group(side + '_arm', [12.5 * sign, 29, 0], 'body');
        group(side + '_forearm', [12.5 * sign, 17, 0], side + '_arm');
        group(side + '_hand', [12.5 * sign, 8, 0], side + '_forearm');
        group(side + '_leg', [4.8 * sign, 11, 0], 'body');
    }

    const sides = [['right', 1], ['left', -1]];
    const flip = (from, to, opts) => {
        const o = Object.assign({}, opts || {}, { mirrored: true });
        if (o.rot) o.rot = [o.rot[0], -o.rot[1], -o.rot[2]];
        if (o.pivot) o.pivot = [-o.pivot[0], o.pivot[1], o.pivot[2]];
        return [[-to[0], from[1], from[2]], [-from[0], to[1], to[2]], o];
    };
    const both = (g, name, from, to, mat, opts) => {
        for (const [side, sign] of sides) {
            const target = typeof g === 'function' ? g(side) : side + '_' + g;
            if (sign === 1) box(target, side + '_' + name, from, to, mat, opts);
            else { const [f, t, o] = flip(from, to, opts); box(target, side + '_' + name, f, t, mat, o); }
        }
    };
    const inG = (g) => () => g;

    // ---------------------------------------------------------------- head
    // 넓적한 돌머리: 몸통 앞쪽에 반쯤 파묻히고, 쇠 투구 틈으로 주황 눈빛이 새어 나옵니다.
    box('head', 'skull', [-6, 30, -8], [6, 37, 1], 'stone');
    box('head', 'visor', [-6.3, 32.5, -8.4], [6.3, 35.5, -7.6], 'iron', {
        px: { north: (u, v, w, h) => (v === 1 && ((u >= 2 && u <= 4) || (u >= w - 5 && u <= w - 3)) ? 'rune:3' : (v === 1 ? 'dark:0' : undefined)) },
    });
    // 눈빛: 투구 틈의 주황 칸 위에 얇은 판을 덧대고, 이름 끝의 _glow로 게임에서 스스로 빛나게 합니다(SemionBilModelCache).
    both(inG('head'), 'eye_glow', [1.455, 33.5, -8.46], [4.362, 34.5, -8.46], 'rune', { plane: 'z', px: { north: () => 'rune:3', south: null } });
    box('head', 'brow', [-6.5, 35.2, -8.8], [6.5, 36.4, -7.4], 'stoneDark');
    box('head', 'jaw', [-5, 29.5, -8.6], [5, 31.5, -6], 'stoneDark');
    // 워든의 귀처럼 옆으로 뻗은 돌 뿔
    both(inG('head'), 'horn', [5.5, 34, -5], [9.5, 36.5, -2], 'stoneDark', { rot: [0, 0, 25], pivot: [6, 35, -3.5] });
    both(inG('head'), 'horn_tip', [8.8, 35.2, -4.5], [11, 37, -2.5], 'stone', { rot: [0, 0, 40], pivot: [9, 35.5, -3.5] });

    // ---------------------------------------------------------------- body
    // 몸통: 넓은 가슴돌과 쇠 가슴판, 한가운데 룬 핵, 등에 솟은 어깨돌, 허리 쇠띠
    box('body', 'chest', [-9, 20, -5.5], [9, 32, 5.5], 'stone');
    box('body', 'belly', [-7, 11, -4.5], [7, 20.5, 4.5], 'stone');
    box('body', 'plate', [-6.5, 21.5, -6.3], [6.5, 30.5, -5.4], 'iron', {
        px: { north: ['.............', 'r...........r', '.r.........r.', '..r.......r..', '.............', '.............', '..r.......r..', '.r.........r.', 'r...........r'].map(r => r.replace(/\./g, ' ')), key: { r: 'rune:2' } },
    });
    box('body', 'core_ring', [-2.6, 23.4, -6.9], [2.6, 28.6, -6.2], 'iron');
    box('body', 'core', [-1.8, 24.2, -7.3], [1.8, 27.8, -6.6], 'rune', {
        px: { north: (u, v, w, h) => (u > 0 && u < w - 1 && v > 0 && v < h - 1 ? 'rune:3' : 'rune:2') },
    });
    box('body', 'hump', [-7, 28, 2], [7, 34, 7], 'stone');
    box('body', 'band_waist', [-7.3, 17, -4.8], [7.3, 18.5, 4.8], 'iron');
    box('body', 'band_chest', [-9.3, 30.5, -5.8], [9.3, 32, 5.8], 'iron');
    // 등의 룬 균열(빛나는 금)
    box('body', 'back_crack', [-0.6, 21, 5.4], [0.6, 30, 5.8], 'rune', { px: { south: () => 'rune:3' } });

    // ---------------------------------------------------------------- arms
    // 어깨돌, 굵은 돌 팔, 쇠 팔찌. 오른 주먹은 쇠 공성추(모서리 가시), 왼 주먹은 돌덩이
    both('arm', 'shoulder', [8.5, 27, -5], [16.5, 33, 5], 'stone', { rot: [0, 0, -10], pivot: [12.5, 30, 0] });
    both('arm', 'shoulder_cap', [9, 32.5, -4.2], [16, 34, 4.2], 'stoneDark', { rot: [0, 0, -10], pivot: [12.5, 30, 0] });
    both('arm', 'upper_arm', [9.5, 16.5, -3.5], [15.5, 28, 3.5], 'stone');
    both('forearm', 'forearm', [9.3, 8, -3.8], [15.7, 17.5, 3.8], 'stone', {
        px: { north: (u, v, w, h) => (u === 3 && v >= 1 && v <= 5 ? 'rune:2' : undefined) },
    });
    both('forearm', 'bracer', [9, 10, -4.1], [16, 12, 4.1], 'iron');
    box('right_hand', 'ram', [8.4, 0.5, -5], [16.6, 8.5, 5], 'iron', {
        px: { east: (u, v, w, h) => ((u === 1 || u === w - 2) && (v === 1 || v === h - 2) ? 'iron:3' : undefined) },
    });
    for (const [i, x, z] of [[0, 9, -5.8], [1, 16, -5.8], [2, 9, 5.8], [3, 16, 5.8]]) {
        box('right_hand', 'ram_spike_' + i, [x - 0.6, 0.2, z - 0.6], [x + 0.6, 1.8, z + 0.6], 'iron', { rot: [0, 45, 0], pivot: [x, 1, z] });
    }
    box('right_hand', 'ram_face', [8.8, -0.2, -4.4], [16.2, 0.6, 4.4], 'stoneDark');
    box('left_hand', 'fist', [-16.2, 1.5, -4.4], [-8.8, 8.5, 4.4], 'stone', {
        px: { north: (u, v) => (v === 1 && u % 2 === 0 ? 'stone:1' : undefined) },
    });

    // ---------------------------------------------------------------- legs
    // 짧고 굵은 돌 기둥 다리와 넓적한 발
    both('leg', 'leg', [2, 2, -3.2], [7.6, 11.5, 3.2], 'stone');
    both('leg', 'knee_band', [1.8, 6.5, -3.5], [7.8, 8, 3.5], 'iron');
    both('leg', 'foot', [1.4, 0, -5.5], [8.2, 2.5, 3.8], 'stoneDark');

    InvasionBB.paintPixels(B, M, 'siege_golem');
    if (InvasionParts.siegeGolemAnimations) InvasionParts.siegeGolemAnimations(B);
    return { cubes: B.cubes.length, groups: Object.keys(B.groups).length };
})();
