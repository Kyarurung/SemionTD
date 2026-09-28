// 트롤 투창병. 크리킹만 한 체격(키 약 44): 좁고 앙상한 몸통, 무릎까지 내려오는 긴 팔, 길고 가는 다리.
// 길쭉한 얼굴에 큰 매부리코, 작은 눈, 아래에서 솟은 짧은 엄니, 이끼 낀 머리칼. 오른손에 투창, 등에 투창 통.
// 모델은 자유롭게 큐브로 짜고 텍스처는 바닐라식(paintPixels)입니다. Run after common.js and troll_javelin.js.
(function () {
    const B = InvasionBB.makeBuilder('troll_javelineer');
    const { group, box } = B;

    const M = {
        skin: { c: ['#34443d', '#4c6358', '#6a8474', '#8fa894'], soft: true },
        moss: { c: ['#2c3b1a', '#43562a', '#5c733a', '#7a9450'], lines: 2 },
        fur: { c: ['#3b2d22', '#574332', '#735a44', '#927659'], lines: 2 },
        leather: { c: ['#2e1a0e', '#4a2c18', '#653e22', '#855632'], rim: true },
        bone: { c: ['#958c70', '#b8ae8e', '#d6cdac', '#f1ecd8'] },
        dark: { c: ['#0b0a0c', '#141216', '#1d1a20', '#27232b'] },
        eye: { c: ['#8a2a10', '#c0461a', '#f0782a', '#ffb060'] },
    };

    // ---------------------------------------------------------------- skeleton
    group('root', [0, 0, 0]);
    group('body', [0, 20, 0], 'root');
    group('torso', [0, 23, 0], 'body');
    group('head', [0, 33, -1.5], 'torso');
    for (const [side, sign] of [['right', 1], ['left', -1]]) {
        group(side + '_arm', [5.2 * sign, 32, 0], 'torso');
        group(side + '_forearm', [5.2 * sign, 22, 0], side + '_arm');
        group(side + '_hand', [5.2 * sign, 12.5, 0], side + '_forearm');
        group(side + '_leg', [2.4 * sign, 20, 0], 'body');
        group(side + '_shin', [2.4 * sign, 10.5, 0], side + '_leg');
        group(side + '_foot', [2.4 * sign, 1.5, 0], side + '_shin');
        group(side + '_ear', [3 * sign, 38.5, -1.5], 'head', [0, -15 * sign, 10 * sign]);
    }
    group('javelin', [5.2, 10.5, 0], 'right_hand', [-12, 0, 0]);
    group('quiver', [0, 28, 2.6], 'torso', [0, 0, -20]);

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
    // 길쭉한 얼굴(앞에서 본 6×9): m 이끼 머리, k 그늘, s 피부, B 눈두덩, E 작은 주황 눈, M 입
    box('head', 'skull', [-3, 33, -5], [3, 42, 1.5], 'skin', {
        px: {
            north: ['mmmmmm', 'mkkkkm', 'kBBBBk', 'sEssEs', 'ssssss', 'ssssss', 'ssssss', 'kMMMMk', 'kkkkkk'],
            key: { m: 'moss:2', k: 'skin:1', s: 'skin:2', B: 'skin:0', E: 'eye:2', M: 'dark:1' },
        },
    });
    // 큰 매부리코와 튀어나온 턱, 아래에서 솟은 짧은 엄니
    box('head', 'nose', [-0.9, 35.5, -8], [0.9, 38.5, -4.6], 'skin', { rot: [-20, 0, 0], pivot: [0, 38.5, -4.8] });
    box('head', 'jaw', [-3.2, 32.2, -5.6], [3.2, 34.4, 0.5], 'skin');
    both(inG('head'), 'tusk', [1.8, 33.8, -6], [2.6, 35.8, -5.2], 'bone', { rot: [0, 0, -8], pivot: [2.2, 33.8, -5.6] });
    // 이끼 낀 머리칼: 정수리 덮개와 뒤·옆으로 늘어진 덩어리
    box('head', 'hair_top', [-3.3, 41.6, -5.1], [3.3, 42.8, 2], 'moss');
    // 이마로 늘어진 이끼 덩어리 몇 개(길이가 제각각)
    for (const [i, x, len] of [[0, -2.4, 1.6], [1, -0.6, 1.0], [2, 1.2, 2.0], [3, 2.6, 1.3]]) {
        box('head', 'moss_clump_' + i, [x - 0.6, 42 - len, -5.5], [x + 0.6, 42, -4.9], 'moss');
    }
    box('head', 'hair_back', [-3.4, 34, 0.8], [3.4, 42, 2.6], 'moss', { rot: [-8, 0, 0], pivot: [0, 42, 1.5] });
    both(inG('head'), 'hair_side', [2.9, 35, -2.5], [3.8, 42, 2], 'moss', { rot: [0, 0, 6], pivot: [3.3, 42, 0] });
    // 귀: 옆으로 처진 길쭉한 판
    both('ear', 'ear', [3, 37, -1.5], [8, 40, -1.5], 'skin', {
        plane: 'z', px: { north: ['ss...', 'sssss', '.kkss'], key: { s: 'skin:2', k: 'skin:1' } },
    });

    // ---------------------------------------------------------------- torso
    // 앙상한 몸통: 갈비뼈를 픽셀로 그리고, 가슴 위에 뼈 목걸이를 겁니다.
    const ribs = (u, v) => (v >= 3 && v <= 8 && v % 2 === 1 && u !== 3) ? 'skin:1' : undefined;
    box('torso', 'chest', [-3.5, 23, -2], [3.5, 33, 2], 'skin', { px: { north: ribs } });
    box('torso', 'shoulders', [-5, 30.5, -2.2], [5, 33.2, 2.2], 'skin');
    box('torso', 'hunch', [-3, 29, 1.5], [3, 34, 3.2], 'skin');
    box('torso', 'necklace', [-2.6, 30.6, -2.5], [2.6, 31.2, -2.1], 'leather');
    for (const [i, x] of [[0, -1.6], [1, 0], [2, 1.6]]) {
        box('torso', 'fang_' + i, [x - 0.3, 29, -2.6], [x + 0.3, 30.7, -2.2], 'bone');
    }
    // 등의 투창 통: 가죽 통에 투창 두 자루
    box('quiver', 'quiver', [-1.3, 20, 2.2], [1.3, 30, 4.4], 'leather');
    InvasionParts.trollJavelin(B, 'quiver', [-0.4, 29, 3.3], 'quiver_jav_a', 16);
    InvasionParts.trollJavelin(B, 'quiver', [0.5, 28.5, 3.3], 'quiver_jav_b', 16);

    // 허리: 털가죽 두른 샅가리개와 가죽 띠
    box('body', 'hips', [-3.2, 18.5, -2], [3.2, 23.2, 2], 'fur');
    box('body', 'belt', [-3.4, 21.8, -2.2], [3.4, 22.8, 2.2], 'leather');
    box('body', 'flap_front', [-2.2, 13.5, -2.5], [2.2, 21.8, -2.1], 'fur', { rot: [4, 0, 0], pivot: [0, 21.8, -2.3] });
    box('body', 'flap_back', [-2.6, 14.5, 2.1], [2.6, 21.8, 2.5], 'fur', { rot: [-4, 0, 0], pivot: [0, 21.8, 2.3] });

    // ---------------------------------------------------------------- arms
    // 긴 팔: 가는 윗팔과 아래팔, 손목에 가죽 감개, 큰 손(손톱은 픽셀)
    both('arm', 'upper_arm', [4.2, 21.5, -1.2], [6.2, 32.8, 1.2], 'skin', { joints: [21.5] });
    both('forearm', 'forearm', [4.3, 12.2, -1.1], [6.1, 22.2, 1.1], 'skin', { joints: [21.5, 12.6] });
    both('forearm', 'wrap', [4.1, 12.5, -1.3], [6.3, 15.5, 1.3], 'leather');
    both('hand', 'hand', [3.9, 8, -1.6], [6.5, 12.6, 1.6], 'skin', { joints: [12.6],
        px: { north: (u, v, w, h) => (v === h - 1 && u % 2 === 0 ? 'bone:2' : undefined) },
    });

    // ---------------------------------------------------------------- legs
    // 길고 가는 다리: 무릎 마디가 불거지고 발은 크고 넓적합니다.
    both('leg', 'thigh', [1.3, 10.2, -1.3], [3.5, 20.3, 1.3], 'skin', { joints: [10.2] });
    both('shin', 'shin', [1.4, 1.5, -1.1], [3.4, 10.6, 1.1], 'skin', { joints: [10.2, 1.8] });
    both('shin', 'knee', [1.2, 9.2, -1.6], [3.6, 11, -0.9], 'skin');
    both('shin', 'ankle_wrap', [1.2, 2, -1.3], [3.6, 3.6, 1.3], 'leather');
    both('foot', 'foot', [0.8, 0, -4], [4, 1.8, 1.4], 'skin', { joints: [1.8],
        px: { north: (u) => (u % 2 === 0 ? 'bone:2' : 'skin:1') },
    });

    // ---------------------------------------------------------------- weapon
    InvasionParts.trollJavelin(B, 'javelin', [5.2, 10.5, 0]);
    Object.assign(M, InvasionParts.trollJavelinMaterials);

    InvasionBB.paintPixels(B, M, 'troll_javelineer');
    if (InvasionParts.trollAnimations) InvasionParts.trollAnimations(B);
    return { cubes: B.cubes.length, groups: Object.keys(B.groups).length };
})();
