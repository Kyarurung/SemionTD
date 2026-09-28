// 강령술사. 깊은 두건으로 얼굴을 그늘에 묻고 초록 눈빛만 새어 나오는 마른 남자 술사입니다.
//  - 체격: 마인크래프트 플레이어(머리 8, 몸통 12, 다리 12, 키 32). 암흑 신관과 같은 뼈대(허벅지·정강이, 팔 세 마디).
//  - 얼굴: 두건 앞면 가운데를 비워 안쪽 머리통 앞면(그늘진 얼굴, 초록 눈)이 보이게 합니다.
//  - 옷: 해진 밑단의 짙은 녹흑색 로브(여덟 장 치마), 어깨 해골 장식, 너덜너덜한 뒤 망토, 뼈 목걸이와 밧줄 허리띠.
//  - 오른손에 해골 지팡이(necro_staff.js), 왼손 위에 영혼 구슬. 텍스처는 바닐라식(paintPixels).
// Run inside Blockbench after common.js and necro_staff.js.
(function () {
    const B = InvasionBB.makeBuilder('necromancer');
    const { group, box } = B;

    const M = {
        skin: { c: ['#5e6458', '#7e8478', '#9aa092', '#b4b8aa'], soft: true },
        robe: { c: ['#0c0f0d', '#151a17', '#202822', '#2c362f'] },
        robeSkirt: { c: ['#0c0f0d', '#151a17', '#202822', '#2c362f'], pleats: true },
        hood: { c: ['#0e120f', '#18201b', '#243028', '#324036'] },
        trim: { c: ['#1f3a26', '#2c5236', '#3c6c48', '#50885c'] },
        bone: { c: ['#8a8470', '#b0aa92', '#d2ccb2', '#ece8d4'] },
        rope: { c: ['#3a3020', '#54462e', '#6e5c3e', '#8a7650'] },
        soul: { c: ['#1f6a2a', '#3aa84a', '#6ee87a', '#c8ffcc'], noFade: true },
        dark: { c: ['#050606', '#0a0c0b', '#111412', '#181c19'], noFade: true },
    };

    // ---------------------------------------------------------------- skeleton
    group('root', [0, 0, 0]);
    group('body', [0, 12, 0], 'root');
    group('torso', [0, 15, 0], 'body');
    group('head', [0, 24, 0], 'torso');
    for (const [side, sign] of [['right', 1], ['left', -1]]) {
        group(side + '_arm', [5.2 * sign, 22.5, 0], 'torso');
        group(side + '_forearm', [5.2 * sign, 18, 0], side + '_arm');
        group(side + '_hand', [5.2 * sign, 12.5, 0], side + '_forearm');
        group(side + '_leg', [1.8 * sign, 12, 0], 'body');
        group(side + '_shin', [1.8 * sign, 6, 0], side + '_leg');
    }
    group('staff', [5.2, 12, 0], 'right_hand', [-90, 0, 0]);
    group('cloak', [0, 23.5, 2.6], 'torso');

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
    // 해진 밑단: 아래 두 줄에서 몇 칸을 비워 너덜너덜하게 합니다.
    const rag = (u, v, w, h) => ((v === h - 1 && u % 2 === 0) || (v === h - 2 && u % 3 === 1) ? null : undefined);

    // ---------------------------------------------------------------- head
    // 머리통 앞면은 두건 그늘 속의 얼굴: D 그늘, G 초록 눈빛. 눈은 가로 두 칸짜리 실눈(- -)이고, 눈 말고는 아무것도 드러내지 않습니다.
    box('head', 'skull', [-4, 26, -4], [4, 32, 4], 'skin', {
        px: {
            north: ['DDDDDDDD', 'DDDDDDDD', 'DGGDDGGD', 'DDDDDDDD', 'DDDDDDDD', 'DDDDDDDD'],
            key: { D: 'dark:0', G: 'soul:3', g: 'soul:1', k: 'skin:0' },
        },
    });
    // 눈빛: 실눈 칸 위에 얇은 판을 덧대고, 이름 끝의 _glow로 게임에서 스스로 빛나게 합니다(SemionBilModelCache).
    both(inG('head'), 'eye_glow', [1, 29, -4.06], [3, 30, -4.06], 'soul', { plane: 'z', px: { north: () => 'soul:3', south: null } });
    // 턱도 두건 그늘 속에 묻습니다. 밝은 뺨·턱 줄을 두면 눈과 이어져 웃는 얼굴(:))처럼 읽힙니다.
    box('head', 'jaw', [-3.2, 24, -4], [3.2, 26, 3.9], 'skin', { px: { north: ['DDDDDD', 'DDDDDD'], key: { D: 'dark:1' } } });
    // 두건: 머리를 감싸는 껍데기. 앞면 가운데를 비워 얼굴이 안쪽 그늘에 보이게 하고, 이마 위로 뾰족한 챙이 튀어나옵니다.
    box('head', 'hood', [-4.7, 23.6, -4.6], [4.7, 33, 4.8], 'hood', {
        px: { north: (u, v, w, h) => (u >= 1 && u <= w - 2 && v >= 2 ? null : undefined) },
    });
    box('head', 'hood_peak', [-2.6, 31.6, -6.2], [2.6, 33.2, -3], 'hood', { rot: [-22, 0, 0], pivot: [0, 33, -4.6] });
    box('head', 'hood_tip', [-1.4, 32.4, -3], [1.4, 34.2, 3.5], 'hood', { rot: [-12, 0, 0], pivot: [0, 33, 3.5] });
    box('head', 'hood_rim', [-4.9, 23.6, -4.9], [4.9, 24.4, 0], 'trim');

    // ---------------------------------------------------------------- torso
    // 마른 몸통에 여민 로브, 가운데 초록 단, 목의 뼈 목걸이
    box('torso', 'chest', [-4, 18, -2], [4, 24, 2], 'robe', {
        px: { north: (u, v, w) => (u === Math.floor(w / 2) ? 'trim:2' : undefined) },
    });
    box('torso', 'waist', [-3.5, 15, -1.8], [3.5, 18.2, 1.8], 'robe');
    box('torso', 'mantle', [-5.4, 20.2, -2.7], [5.4, 24.2, 2.7], 'robe', { px: { up: null, north: rag, east: rag, west: rag, south: rag } });
    for (const [i, x] of [[0, -2.4], [1, -0.8], [2, 0.8], [3, 2.4]]) {
        const y = 21.4 - (i === 1 || i === 2 ? 0.8 : 0);
        box('torso', 'necklace_' + i, [x - 0.5, y - 0.6, -3.1], [x + 0.5, y + 0.6, -2.6], 'bone');
    }
    box('torso', 'necklace_skull', [-0.8, 19.3, -3.3], [0.8, 20.8, -2.6], 'bone', { px: { north: ['DD', 'bb'], key: { D: 'dark:0', b: 'bone:2' } } });
    // 밧줄 허리띠와 매단 작은 해골
    box('torso', 'belt', [-3.7, 15.2, -2.05], [3.7, 16.2, 2.05], 'rope');
    box('torso', 'belt_skull', [-3.3, 12.6, -2.6], [-1.7, 14.4, -1.2], 'bone', { px: { north: ['DD', 'bb'], key: { D: 'dark:0', b: 'bone:2' } } });
    box('torso', 'belt_cord', [-2.7, 14.3, -2.3], [-2.3, 15.4, -1.9], 'rope');
    // 뒤 망토: 어깨에서 발목까지 늘어진 해진 천
    box('cloak', 'cloak', [-4.8, 2, 2.3], [4.8, 23.5, 2.8], 'hood', { rot: [8, 0, 0], pivot: [0, 23.5, 2.6], px: { south: rag, north: rag } });

    // ---------------------------------------------------------------- robe skirt
    // 발목까지 오는 여덟 장 치마(해진 밑단).
    box('body', 'hips', [-3.3, 12, -1.3], [3.3, 15.4, 1.3], 'robe');
    box('body', 'hips_core', [-2.4, 12, -2], [2.4, 15.4, 2], 'robe');
    const FLARE = 9, LEN = 13.5, TOP = 15.2;
    const SPEC = { straight: [2.1, 5.2], diag: [3.3, 1.5], flank: [3.5, 2.6] };
    const spread = Math.atan((LEN * Math.sin(FLARE * Math.PI / 180) * Math.tan(Math.PI / 8)) / LEN) * 180 / Math.PI;
    const RAG = { north: rag, south: rag };
    for (let k = 0; k < 8; k++) {
        const kind = k % 4 === 0 ? 'straight' : k % 4 === 2 ? 'flank' : 'diag';
        const [r, w] = SPEC[kind];
        const g = 'robe_' + k;
        group(g, [0, TOP, 0], 'body', [0, k * 45, 0]);
        group(g + '_flare', [0, TOP, -r], g, [FLARE, 0, 0]);
        box(g + '_flare', g, [-w / 2, TOP - LEN, -r - 0.3], [w / 2, TOP, -r], 'robeSkirt', { px: RAG });
        for (const s of [-1, 1]) {
            const x = s * (w / 2);
            const from = s < 0 ? [x, TOP - LEN, -r - 0.27] : [x - 0.9, TOP - LEN, -r - 0.27];
            const to = s < 0 ? [x + 0.9, TOP, -r - 0.03] : [x, TOP, -r - 0.03];
            box(g + '_flare', g + (s < 0 ? '_fill_l' : '_fill_r'), from, to, 'robeSkirt', { rot: [0, 0, s * spread], pivot: [x, TOP, -r], px: RAG });
        }
    }

    // ---------------------------------------------------------------- arms
    // 어깨 해골 장식, 좁은 윗소매, 해진 종 모양 아래소매, 마른 잿빛 손
    both('arm', 'upper_arm', [4.1, 18, -1.3], [6.3, 24, 1.3], 'robe');
    both('arm', 'shoulder_skull', [4.2, 23.4, -1.7], [7.2, 26.2, 1.5], 'bone', {
        px: { north: ['bbb', 'DbD', 'bbb'], key: { b: 'bone:2', D: 'dark:0' } },
    });
    both('arm', 'shoulder_jaw', [4.6, 22.7, -1.8], [6.8, 23.5, 0.8], 'bone', { px: { north: (u) => (u % 2 ? 'dark:1' : undefined) } });
    both('forearm', 'sleeve', [3.6, 12.6, -2], [6.8, 18.3, 2], 'robe', { px: { north: rag, south: rag, east: rag, west: rag } });
    both('forearm', 'sleeve_inner', [4.1, 12.4, -1.5], [6.3, 13, 1.5], 'dark');
    both('hand', 'hand', [4.3, 10.6, -1.1], [6.1, 12.8, 1.1], 'skin');
    // 왼손 위에 떠 있는 영혼 구슬
    box('left_hand', 'soul_orb', [-6.1, 8, -0.9], [-4.3, 9.8, 0.9], 'soul', { rot: [0, 45, 0], pivot: [-5.2, 8.9, 0], px: { north: () => 'soul:3' } });

    // ---------------------------------------------------------------- legs
    both('leg', 'thigh', [0.4, 6, -1.5], [3.2, 12.2, 1.5], 'robe');
    both('shin', 'shin', [0.5, 0.6, -1.4], [3.1, 6.2, 1.4], 'robe');
    both('shin', 'boot', [0.4, 0, -3.2], [3.2, 1.6, 1.6], 'dark');

    // ---------------------------------------------------------------- staff
    InvasionParts.necroStaff(B, 'staff', [5.2, 12, 0]);
    Object.assign(M, InvasionParts.necroStaffMaterials);

    InvasionBB.paintPixels(B, M, 'necromancer');
    if (InvasionParts.necromancerAnimations) InvasionParts.necromancerAnimations(B);
    return { cubes: B.cubes.length, groups: Object.keys(B.groups).length };
})();
