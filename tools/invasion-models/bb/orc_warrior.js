// 오크 전사. 철골렘만 한 덩치(키 약 43): 넓은 어깨와 가슴, 무릎까지 내려오는 굵은 팔, 낮게 앞으로 내민 머리.
// 모델은 자유롭게 큐브로 짜고, 텍스처는 바닐라식(paintPixels: 노이즈 없는 4단 램프, 윗면·앞면이 밝음)입니다.
// 얼굴·배 근육·무릎 같은 세부는 픽셀로 그립니다. Run inside Blockbench after common.js and orc_axe.js.
(function () {
    const B = InvasionBB.makeBuilder('orc_warrior');
    const { group, box } = B;

    const M = {
        skin: { c: ['#2f4a22', '#4a6b32', '#668c42', '#86ad58'], soft: true },
        skinDark: { c: ['#223a18', '#385626', '#4f7234', '#6b9446'] },
        pants: { c: ['#2a1c14', '#443022', '#5c4231', '#78573f'] },
        leather: { c: ['#2e1a0e', '#4a2c18', '#653e22', '#855632'], rim: true },
        boot: { c: ['#1c120b', '#2f1f14', '#43301f', '#5a432d'], rim: true },
        fur: { c: ['#4b3b2a', '#6a553d', '#8a7253', '#ad946f'], lines: 2 },
        iron: { c: ['#34373d', '#50555d', '#727a84', '#a3abb5'], rim: true },
        bone: { c: ['#958c70', '#b8ae8e', '#d6cdac', '#f1ecd8'] },
        rag: { c: ['#3b120d', '#5a1d14', '#77291c', '#973a28'], pleats: true },
        hair: { c: ['#0f0d10', '#1c191e', '#2b2730', '#3c3743'], lines: 2 },
        dark: { c: ['#0b0a0c', '#141216', '#1d1a20', '#27232b'] },
        eye: { c: ['#b88a12', '#e0b21e', '#ffd84a', '#fff1a0'] },
        pupil: { c: ['#5a0906', '#8a120b', '#c01e12', '#ff3b24'] },
    };

    // ---------------------------------------------------------------- skeleton (애니메이션 뼈 이름은 그대로)
    group('root', [0, 0, 0]);
    group('body', [0, 16, 0], 'root');
    group('torso', [0, 21, 0], 'body');
    group('head', [0, 33, -2], 'torso');
    for (const [side, sign] of [['right', 1], ['left', -1]]) {
        group(side + '_arm', [11.5 * sign, 31, 0], 'torso');
        group(side + '_forearm', [11.5 * sign, 19, 0], side + '_arm');
        group(side + '_hand', [11.5 * sign, 9, 0], side + '_forearm', sign > 0 ? [35, 0, 0] : [0, 0, 0]);
        group(side + '_leg', [4.5 * sign, 16, 0], 'body');
        group(side + '_shin', [4.5 * sign, 8, 0], side + '_leg');
        group(side + '_foot', [4.5 * sign, 2, 0], side + '_shin');
    }
    group('axe', [11.5, 6.5, 0], 'right_hand', [-90, 0, 0]);

    const sides = [['right', 1], ['left', -1]];
    const flip = (from, to, opts) => {
        const o = Object.assign({}, opts || {}, { mirrored: true });
        if (o.rot) o.rot = [o.rot[0], -o.rot[1], -o.rot[2]];
        if (o.pivot) o.pivot = [-o.pivot[0], o.pivot[1], o.pivot[2]];
        return [[-to[0], from[1], from[2]], [-from[0], to[1], to[2]], o];
    };
    // 오른쪽 좌표로 적고 왼쪽은 x를 뒤집습니다. g는 'arm'이면 right_arm/left_arm, 함수면 그 결과 그룹입니다.
    const both = (g, name, from, to, mat, opts) => {
        for (const [side, sign] of sides) {
            const target = typeof g === 'function' ? g(side) : side + '_' + g;
            if (sign === 1) box(target, side + '_' + name, from, to, mat, opts);
            else { const [f, t, o] = flip(from, to, opts); box(target, side + '_' + name, f, t, mat, o); }
        }
    };
    const inBody = (g) => () => g;

    // ---------------------------------------------------------------- head
    // 얼굴(앞에서 본 8×9): k 그늘, s 피부, B 눈두덩, E/P 노란 눈과 붉은 눈동자, N 코 그늘, M 입.
    const FACE = {
        k: 'skin:1', s: 'skin:2', B: 'skinDark:1', E: 'eye:2', P: 'pupil:2', N: 'skin:1', M: 'dark:1',
    };
    box('head', 'skull', [-4, 33, -7], [4, 42, 1], 'skin', {
        px: {
            north: ['kkkkkkkk', 'ssssssss', 'BBBssBBB', 'sEPssPEs', 'ssssssss', 'sssNNsss', 'ssssssss', 'kMMMMMMk', 'kkkkkkkk'],
            key: FACE,
        },
    });
    // 튀어나온 눈두덩과 뭉툭한 코
    box('head', 'brow', [-4.2, 38.5, -7.7], [4.2, 39.6, -6.8], 'skinDark');
    box('head', 'nose', [-0.9, 35.6, -7.9], [0.9, 37.6, -6.9], 'skin');
    // 주걱턱: 머리보다 넓게 앞으로 나온 아래턱, 아랫니 한 줄과 위로 솟은 엄니
    box('head', 'jaw', [-4.6, 31.4, -7.8], [4.6, 34.4, -0.5], 'skin', {
        px: { north: ['kTkTkTkTk', 'sssssssss', 'kkkkkkkkk'], key: { k: 'skin:1', s: 'skin:2', T: 'bone:3' } },
    });
    both(inBody('head'), 'tusk', [2.6, 33.8, -8.2], [3.7, 37.2, -7.1], 'bone', { rot: [0, 0, -10], pivot: [3.15, 33.8, -7.65] });
    // 정수리에서 뒤로 넘긴 검은 머리 다발과 묶은 꽁지
    box('head', 'mohawk', [-1.1, 42, -6.5], [1.1, 43.6, 1.2], 'hair');
    box('head', 'topknot', [-0.9, 38, 1], [0.9, 42.8, 2.4], 'hair', { rot: [18, 0, 0], pivot: [0, 42.8, 1.7] });
    box('head', 'topknot_band', [-1.0, 41.4, 0.8], [1.0, 42.2, 2.6], 'leather', { rot: [18, 0, 0], pivot: [0, 42.8, 1.7] });
    // 귀: 옆으로 크게 뻗은 뾰족귀 판
    for (const [side, sign] of sides) group(side + '_ear', [4 * sign, 38, -3], 'head', [0, -20 * sign, 12 * sign]);
    both('ear', 'ear', [4, 36.5, -3], [10, 39.5, -3], 'skin', {
        plane: 'z', px: { north: ['sss...', 'ssssss', '.kksss'], key: { s: 'skin:2', k: 'skin:1' } },
    });

    // ---------------------------------------------------------------- torso
    // 몸통: 넓은 가슴통과 등 뒤로 솟은 승모근 혹. 배에는 복근을 픽셀로 그립니다.
    const abs = (u, v) => (u === 8 || u === 9 || (v % 3 === 2 && u > 4 && u < 13)) ? 'skin:1' : undefined;
    box('torso', 'chest', [-9, 21, -5], [9, 33, 5], 'skin', { px: { north: abs } });
    box('torso', 'hump', [-6.5, 31, -2.5], [6.5, 35.5, 5.2], 'skin');
    both(inBody('torso'), 'trap', [2.5, 31.5, -3], [9, 34.5, 4], 'skin', { rot: [0, 0, -18], pivot: [5.5, 33, 0] });
    both(inBody('torso'), 'pec', [0.3, 26.5, -6], [8.3, 32.8, -4.8], 'skin', { rot: [-6, 0, 0], pivot: [4.3, 32.8, -5] });
    // 허리: 가죽 띠와 해골 버클, 앞뒤로 늘어진 넝마 앞치마
    box('body', 'hips', [-6.5, 14, -4.2], [6.5, 21, 4.2], 'pants');
    box('body', 'belt', [-7, 19.2, -4.7], [7, 21.4, 4.7], 'leather');
    box('body', 'buckle', [-1.3, 18.9, -5.2], [1.3, 21.7, -4.6], 'bone', {
        px: { north: ['bbb', 'dbd', 'bbb'], key: { b: 'bone:3', d: 'dark:1' } },
    });
    box('body', 'loincloth_front', [-3.2, 8.5, -5], [3.2, 19.3, -4.4], 'rag', { rot: [6, 0, 0], pivot: [0, 19.3, -4.7] });
    box('body', 'loincloth_back', [-3.6, 9.5, 4.4], [3.6, 19.3, 5], 'rag', { rot: [-6, 0, 0], pivot: [0, 19.3, 4.7] });

    // ---------------------------------------------------------------- arms
    // 팔: 둥근 어깨살, 굵은 윗팔, 가죽 토시 두른 아래팔, 큰 주먹(손가락 없이 마디만 그림)
    both('arm', 'deltoid', [8.4, 26, -3.6], [14.6, 33.2, 3.6], 'skin', { joints: [26] });
    both('arm', 'upper_arm', [9, 18.5, -3], [14, 27, 3], 'skin', { joints: [26, 18.5] });
    both('forearm', 'forearm', [9.2, 9, -2.8], [13.8, 19.5, 2.8], 'skin', { joints: [18.5, 9.5] });
    both('forearm', 'bracer', [8.8, 10.5, -3.2], [14.2, 16.5, 3.2], 'leather');
    both('forearm', 'bracer_band', [8.7, 12.8, -3.3], [14.3, 13.6, 3.3], 'iron');
    both('hand', 'fist', [8.8, 4, -3.3], [14.2, 9.5, 3.3], 'skin', { joints: [9.5],
        px: { north: (u, v) => (v === 1 && u % 2 === 1 ? 'skin:1' : undefined) },
    });
    // 오른쪽 어깨만 쇠 견갑 두 겹
    box('right_arm', 'pauldron', [7.8, 31, -4.3], [15.3, 34.2, 4.3], 'iron', { rot: [0, 0, -16], pivot: [11.5, 32.5, 0] });
    box('right_arm', 'pauldron_low', [9.2, 28.4, -4.1], [15.6, 31.4, 4.1], 'iron', { rot: [0, 0, -16], pivot: [11.5, 32.5, 0] });

    // ---------------------------------------------------------------- legs
    // 다리: 짧고 굵은 가죽 바지, 털가죽 감은 정강이, 넓적한 장화
    both('leg', 'thigh', [1.5, 8, -3], [7.5, 16.2, 3], 'pants');
    both('shin', 'shin', [1.8, 1.8, -2.7], [7.2, 8.4, 2.7], 'pants');
    both('shin', 'fur_wrap', [1.5, 4.5, -3], [7.5, 8, 3], 'fur');
    both('shin', 'knee', [2.5, 7.2, -3.3], [6.5, 9, -2.6], 'leather');
    both('foot', 'boot', [1.3, 0, -5], [7.7, 2.6, 3.2], 'boot');
    both('foot', 'boot_cuff', [1.4, 2.2, -3.2], [7.6, 3.2, 3.2], 'boot');

    // ---------------------------------------------------------------- axe
    InvasionParts.orcAxe(B, 'axe', [11.5, 6.5, 0]);
    Object.assign(M, InvasionParts.orcAxeMaterials);

    InvasionBB.paintPixels(B, M, 'orc_warrior');
    if (InvasionParts.orcAnimations) InvasionParts.orcAnimations(B);
    return { cubes: B.cubes.length, groups: Object.keys(B.groups).length };
})();
