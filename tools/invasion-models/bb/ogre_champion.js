// 오우거 투사. 군단장보다 큰 거인(키 약 55): 뚱뚱한 배, 몸에 비해 작은 머리와 튀어나온 아래턱·송곳니, 땅까지 닿을 듯 긴 팔.
// 검투사 차림으로 청동 뿔 투구, 오른팔 청동 마디 팔갑(매니카)과 어깨판, 가죽 허리띠와 붉은 앞치마, 청동 정강이받이를 둘렀습니다.
// 무기(가시 몽둥이)는 ogre_club.js에서 따로 만들어 오른손에 붙입니다. 모델은 자유 큐브, 텍스처는 바닐라식(paintPixels)입니다.
// Run after common.js, ogre_club.js.
(function () {
    const B = InvasionBB.makeBuilder('ogre_champion');
    const { group, box } = B;

    const M = Object.assign({
        skin: { c: ['#5e4636', '#80604a', '#a07c60', '#bc9878'], soft: true },
        bronze: { c: ['#5a3a18', '#8a5a26', '#b8823a', '#e0b060'], rim: true },
        leather: { c: ['#2e1e14', '#46301f', '#5e412a', '#785638'] },
        cloth: { c: ['#4a1a16', '#6e2820', '#90382c', '#b04a3a'], hem: 'leather' },
        bone: { c: ['#8a7e66', '#b0a488', '#d4caa8', '#eee6c8'] },
    }, InvasionParts.ogreClubMaterials);

    // ---------------------------------------------------------------- skeleton
    group('root', [0, 0, 0]);
    group('body', [0, 20, 0], 'root');
    group('torso', [0, 24, 0], 'body');
    group('head', [0, 42, -4], 'torso');
    for (const [side, sign] of [['right', 1], ['left', -1]]) {
        group(side + '_arm', [15 * sign, 40, 0], 'torso');
        group(side + '_forearm', [15 * sign, 29, 0], side + '_arm');
        group(side + '_hand', [15 * sign, 17.5, 0], side + '_forearm');
        group(side + '_leg', [5.5 * sign, 20, 0], 'body');
        group(side + '_shin', [5.5 * sign, 10.5, 0], side + '_leg');
    }
    group('club', [15, 14, 0], 'right_hand', [-90, 0, 0]);

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
    // 작은 머리: 청동 투구 아래로 작은 눈, 뭉툭한 코, 앞으로 튀어나온 아래턱과 위로 솟은 송곳니
    box('head', 'skull', [-4.5, 40, -9], [4.5, 50, -1], 'skin', {
        px: {
            north: ['sssssssss', 'sssssssss', 'sssssssss', 'sssssssss', 'BBBsssBBB', 'sWPsssPWs', 'sssssssss', 'sssssssss', 'sssssssss', 'sssssssss'],
            key: { s: 'skin', B: 'skin:0', W: '#e8dca0', P: '#1a1010' },
        },
    });
    box('head', 'nose', [-1.2, 42.4, -10], [1.2, 44.4, -9], 'skin');
    box('head', 'jaw', [-4.8, 39.5, -10], [4.8, 42.6, -2.5], 'skin', {
        px: { north: (u, v, w) => (v === 0 && u > 0 && u < w - 1 ? 'skin:0' : undefined) },
    });
    both(inG('head'), 'tusk', [2.5, 42.2, -10.3], [3.5, 44.1, -9.4], 'bone', { rot: [0, 0, -10], pivot: [3, 42.2, -9.8] });
    box('head', 'helm', [-5, 46.5, -9.6], [5, 51.5, -0.5], 'bronze');
    box('head', 'helm_rim', [-5.3, 46.1, -9.9], [5.3, 47.1, -0.2], 'bronze');
    box('head', 'nasal', [-0.6, 44.6, -10.2], [0.6, 47.1, -9.7], 'bronze');
    box('head', 'crest', [-0.8, 51.5, -8.5], [0.8, 52.7, -1], 'bronze');
    both(inG('head'), 'horn', [5, 48.5, -6], [8, 50.3, -4], 'bone', { rot: [0, 0, 30], pivot: [5, 49, -5] });
    both(inG('head'), 'horn_tip', [7.2, 50.5, -5.7], [8.5, 53.5, -4.3], 'bone', { rot: [0, 0, -15], pivot: [7.8, 50.5, -5] });

    // ---------------------------------------------------------------- torso
    // 불룩한 배와 넓은 가슴, 목 대신 솟은 승모근. 배꼽과 가슴 아래 선은 텍스처로 그립니다.
    box('torso', 'belly', [-10, 21, -8.5], [10, 33, 6], 'skin', {
        px: { north: (u, v, w) => (v === 5 && (u === 9 || u === 10) ? 'skin:0' : (v === 0 && u > 2 && u < w - 3 ? 'skin:1' : undefined)) },
    });
    box('torso', 'chest', [-11, 32, -7], [11, 42, 6], 'skin', {
        px: { north: (u, v, w) => (v === 8 && ((u >= 3 && u <= 9) || (u >= 12 && u <= 18)) ? 'skin:1' : undefined) },
    });
    box('torso', 'traps', [-8, 41, -4], [8, 45, 5], 'skin');
    box('torso', 'belt', [-10.4, 20.5, -8.9], [10.4, 23.8, 6.4], 'leather');
    box('torso', 'buckle', [-2.2, 20, -9.5], [2.2, 24.3, -8.7], 'bronze', {
        px: { north: (u, v, w, h) => (u > 0 && u < w - 1 && v > 0 && v < h - 1 ? 'leather:1' : undefined) },
    });

    // ---------------------------------------------------------------- hips
    box('body', 'hips', [-9.5, 16.5, -6], [9.5, 21, 5.5], 'leather');
    box('body', 'apron', [-4, 7, -7.8], [4, 20.5, -7.8], 'cloth', { plane: 'z', rot: [-6, 0, 0], pivot: [0, 20.5, -7.8] });
    box('body', 'apron_back', [-5, 9, 5.8], [5, 20.5, 5.8], 'cloth', { plane: 'z', rot: [6, 0, 0], pivot: [0, 20.5, 5.8] });

    // ---------------------------------------------------------------- arms
    both('arm', 'upper_arm', [11.5, 28.5, -4.2], [18.5, 42, 4.2], 'skin', { joints: [28.5] });
    // 오른팔: 청동 어깨판과 세 겹 마디 팔갑
    box('right_arm', 'pauldron', [11, 38.5, -5.2], [19.8, 43.5, 5.2], 'bronze', { rot: [0, 0, -18], pivot: [15, 41, 0] });
    box('right_arm', 'pauldron_top', [11.5, 43, -4.4], [18.5, 44.4, 4.4], 'bronze', { rot: [0, 0, -18], pivot: [15, 41, 0] });
    for (let i = 0; i < 3; i++) {
        const y = 36 - i * 3.2;
        box('right_arm', 'manica_' + i, [11.1, y, -4.6], [18.9, y + 3.4, 4.6], 'bronze', { rot: [0, 0, -6], pivot: [15, y + 1.7, 0] });
    }
    box('left_arm', 'left_armband', [-18.8, 36, -4.5], [-11.2, 38, 4.5], 'leather');
    both('forearm', 'forearm', [11.8, 17, -3.8], [18.2, 29.5, 3.8], 'skin', { joints: [28.5, 17.5] });
    both('forearm', 'wrap', [11.5, 18, -4.1], [18.5, 23.5, 4.1], 'leather', {
        px: { north: (u, v) => (v % 2 === 1 ? 'leather:1' : undefined) },
    });
    box('right_forearm', 'vambrace', [11.3, 24, -4.3], [18.7, 28, 4.3], 'bronze');
    both('hand', 'fist', [11.6, 11, -4], [18.4, 17.5, 4], 'skin', { joints: [17.5],
        px: { north: (u, v) => (v === 1 && u % 2 === 1 ? 'skin:1' : undefined) },
    });

    // ---------------------------------------------------------------- legs
    both('leg', 'thigh', [1.5, 10, -4.2], [9.5, 20.5, 4.2], 'skin', { joints: [10] });
    both('leg', 'thigh_wrap', [1.3, 16, -4.4], [9.7, 18.5, 4.4], 'leather');
    both('shin', 'shin', [2, 2, -3.6], [9, 10.8, 3.6], 'skin', { joints: [10, 2.6] });
    both('shin', 'greave', [1.7, 2.5, -4.3], [9.3, 10.2, -3.3], 'bronze', {
        px: { north: (u, v, w) => (u === Math.floor(w / 2) ? 'bronze:3' : undefined) },
    });
    both('shin', 'knee', [2.8, 9.2, -4.8], [8.2, 11.8, -3.2], 'bronze');
    both('shin', 'foot', [1.5, 0, -6.5], [9.5, 2.6, 4], 'skin', { joints: [2.6],
        px: { north: (u, v) => (v === 1 && u % 3 === 2 ? 'skin:0' : undefined) },
    });
    both('shin', 'sandal', [1.3, 0, -6.7], [9.7, 0.8, 4.2], 'leather');

    // ---------------------------------------------------------------- weapon
    InvasionParts.ogreClub(B, 'club', [15, 14, 0]);

    InvasionBB.paintPixels(B, M, 'ogre_champion');
    if (InvasionParts.ogreChampionAnimations) InvasionParts.ogreChampionAnimations(B);
    return { cubes: B.cubes.length, groups: Object.keys(B.groups).length };
})();
