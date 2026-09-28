// 고블린 정찰병. 키 1미터(16) 남짓, 구리 골렘 같은 체격: 넓적한 큰 머리, 짧은 몸통, 가늘고 짧은 팔다리.
// 옆으로 길게 뻗은 뾰족귀, 매부리코, 노란 눈, 이빨 드러낸 웃음. 넝마 튜닉에 녹슨 칼을 쥡니다.
// 모델은 자유롭게 큐브로 짜고 텍스처는 바닐라식(paintPixels)입니다. Run after common.js and goblin_shiv.js.
(function () {
    const B = InvasionBB.makeBuilder('goblin_scout');
    const { group, box } = B;

    const M = {
        skin: { c: ['#3f5a1e', '#5f7f2a', '#86a63a', '#b1c95a'], soft: true },
        tunic: { c: ['#3a2a1a', '#56402a', '#735a3c', '#937652'], pleats: true },
        leather: { c: ['#2e1a0e', '#4a2c18', '#653e22', '#855632'], rim: true },
        hair: { c: ['#1a1410', '#2a2019', '#3b2e24', '#4e3f32'] },
        bone: { c: ['#958c70', '#b8ae8e', '#d6cdac', '#f1ecd8'] },
        dark: { c: ['#0b0a0c', '#141216', '#1d1a20', '#27232b'] },
        eye: { c: ['#b88a12', '#e0b21e', '#ffd84a', '#fff1a0'] },
    };

    // ---------------------------------------------------------------- skeleton
    group('root', [0, 0, 0]);
    group('body', [0, 5, 0], 'root');
    group('head', [0, 10.5, -0.5], 'body');
    for (const [side, sign] of [['right', 1], ['left', -1]]) {
        group(side + '_arm', [3.9 * sign, 10, 0], 'body');
        group(side + '_hand', [3.9 * sign, 4.8, 0], side + '_arm');
        group(side + '_leg', [1.5 * sign, 5, 0], 'body');
        group(side + '_ear', [4 * sign, 13.8, -0.5], 'head', [0, -18 * sign, 16 * sign]);
    }
    group('shiv', [3.9, 4, 0], 'right_hand', [-70, 0, 0]);

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
    // 얼굴(앞에서 본 8×6): B 눈썹, E/P 노란 눈과 검은 동공, s 피부, k 그늘, M 입, T 이빨
    box('head', 'skull', [-4, 10.5, -4], [4, 16.5, 3.5], 'skin', {
        px: {
            north: ['kkkkkkkk', 'sBBssBBs', 'sEPssPEs', 'ssssssss', 'sMTTTTMs', 'kkkkkkkk'],
            key: { k: 'skin:1', s: 'skin:2', B: 'skin:0', E: 'eye:2', P: 'dark:0', M: 'dark:1', T: 'bone:3' },
        },
    });
    // 매부리코: 앞으로 길게 나와 끝이 아래로 굽습니다.
    box('head', 'nose', [-0.7, 12.3, -6.2], [0.7, 13.7, -3.9], 'skin', { rot: [-18, 0, 0], pivot: [0, 13.7, -4] });
    // 정수리의 성긴 머리털 몇 가닥
    box('head', 'tuft_a', [-0.5, 16.3, -1.5], [0.5, 18.2, -0.5], 'hair', { rot: [-15, 0, 0], pivot: [0, 16.5, -1] });
    box('head', 'tuft_b', [0.8, 16.3, -0.2], [1.6, 17.6, 0.6], 'hair', { rot: [0, 0, -20], pivot: [1.2, 16.5, 0.2] });
    box('head', 'tuft_c', [-1.8, 16.3, 0.6], [-1.0, 17.4, 1.4], 'hair', { rot: [0, 0, 22], pivot: [-1.4, 16.5, 1] });
    // 귀: 옆으로 길게 뻗은 뾰족귀 판(앞에서 본 모습, 왼쪽 열이 바깥)
    both('ear', 'ear', [4, 12.3, -0.5], [11, 15.3, -0.5], 'skin', {
        plane: 'z', px: { north: ['ss.....', 'sssssss', '.kkksss'], key: { s: 'skin:2', k: 'skin:1' } },
    });

    // ---------------------------------------------------------------- body
    // 넝마 튜닉: 불룩한 배, 허리 끈, 들쭉날쭉한 아랫단
    box('body', 'torso', [-3, 5, -2], [3, 10.6, 2], 'tunic');
    box('body', 'belly', [-2.4, 5.6, -2.6], [2.4, 8.8, -1.9], 'tunic');
    box('body', 'belt', [-3.15, 6.4, -2.15], [3.15, 7.1, 2.15], 'leather');
    box('body', 'hem', [-3.3, 3.6, -2.3], [3.3, 5.4, 2.3], 'tunic', {
        px: {
            north: (u, v, w, h) => (v === h - 1 && u % 2 === 1 ? null : undefined),
            south: (u, v, w, h) => (v === h - 1 && u % 2 === 0 ? null : undefined),
            east: (u, v, w, h) => (v === h - 1 && u % 2 === 1 ? null : undefined),
            west: (u, v, w, h) => (v === h - 1 && u % 2 === 0 ? null : undefined),
            up: null,
        },
    });
    box('body', 'neck_rag', [-2.6, 10.2, -2.2], [2.6, 11, 2.2], 'tunic');

    // ---------------------------------------------------------------- arms & legs
    both('arm', 'arm', [3, 4.8, -0.9], [4.8, 10.3, 0.9], 'skin');
    both('arm', 'sleeve', [2.9, 8.2, -1.1], [4.9, 10.4, 1.1], 'tunic');
    both('hand', 'hand', [2.9, 3.4, -1.1], [4.9, 5.2, 1.1], 'skin', {
        px: { north: (u, v, w, h) => (v === h - 1 ? 'bone:2' : undefined) },   // 손톱
    });
    both('leg', 'leg', [0.5, 1, -1], [2.5, 5.2, 1], 'skin');
    both('leg', 'wrap', [0.4, 2.2, -1.1], [2.6, 3.4, 1.1], 'leather');
    both('leg', 'foot', [0.2, 0, -2.6], [2.8, 1.2, 1.1], 'skin', {
        px: { north: (u) => (u % 2 === 0 ? 'bone:2' : 'skin:1') },               // 발톱
    });

    // ---------------------------------------------------------------- weapon
    InvasionParts.goblinShiv(B, 'shiv', [3.9, 4, 0]);
    Object.assign(M, InvasionParts.goblinShivMaterials);

    InvasionBB.paintPixels(B, M, 'goblin_scout');
    if (InvasionParts.goblinAnimations) InvasionParts.goblinAnimations(B);
    return { cubes: B.cubes.length, groups: Object.keys(B.groups).length };
})();
