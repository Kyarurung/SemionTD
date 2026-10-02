// 드워프 총병. 작은 철골렘 같은 체격(키 약 34): 짧고 굵은 다리, 넓은 가슴통, 무릎까지 내려오는 굵은 팔, 큰 머리.
// 큰 코와 땋은 생강빛 수염, 황동 고글 얹은 쇠 투구, 가죽 흉갑 위 푸른 튜닉, 큰 허리 버클. 오른손에 나팔총.
// 모델은 자유롭게 큐브로 짜고 텍스처는 바닐라식(paintPixels)입니다. Run after common.js and dwarf_gun.js.
(function () {
    // 좌표는 키 26 기준으로 적고 SCALE배로 키웁니다(키 약 34). 텍스처 밀도는 1/SCALE로 줄여 픽셀 무늬를 그대로 유지합니다.
    const SCALE = 1.3;
    const RAW = InvasionBB.makeBuilder('dwarf_gunner');
    const sc = (p) => p.map(v => v * SCALE);
    const B = Object.assign({}, RAW, {
        group: (name, origin, parent, rotation) => RAW.group(name, sc(origin), parent, rotation),
        box: (g, name, from, to, mat, opts) => {
            const o = Object.assign({}, opts || {});
            if (o.pivot) o.pivot = sc(o.pivot);
            o.dens = (o.dens || 1) / SCALE;
            return RAW.box(g, name, sc(from), sc(to), mat, o);
        },
    });
    const { group, box } = B;

    const M = {
        skin: { c: ['#8a5a44', '#b57a5c', '#d69a78', '#eebc98'], soft: true },
        beard: { c: ['#6b2e12', '#9a4a1c', '#c26a2a', '#e08c44'], lines: 2 },
        tunic: { c: ['#1e2a44', '#2e3f63', '#44598a', '#5f78b0'] },
        leather: { c: ['#2e1a0e', '#4a2c18', '#653e22', '#855632'], rim: true },
        iron: { c: ['#2f3238', '#4a4f57', '#6b727c', '#9aa2ad'], rim: true },
        brass: { c: ['#6b4f1a', '#9a7426', '#c99a3a', '#f0c65a'] },
        boot: { c: ['#1c120b', '#2f1f14', '#43301f', '#5a432d'], rim: true },
        glass: { c: ['#1c4a52', '#2d7280', '#4fa3b0', '#9ad8e0'] },
        dark: { c: ['#0b0a0c', '#141216', '#1d1a20', '#27232b'] },
    };

    // ---------------------------------------------------------------- skeleton
    group('root', [0, 0, 0]);
    group('body', [0, 7, 0], 'root');
    group('head', [0, 18, -1], 'body');
    for (const [side, sign] of [['right', 1], ['left', -1]]) {
        group(side + '_arm', [7.2 * sign, 17, 0], 'body');
        group(side + '_forearm', [7.2 * sign, 11, 0], side + '_arm');
        group(side + '_hand', [7.2 * sign, 5.5, 0], side + '_forearm');
        group(side + '_leg', [2.6 * sign, 7, 0], 'body');
    }
    group('gun', [7.2, 4.6, 0], 'right_hand', [-90, 0, 0]);

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
    // 얼굴(앞에서 본 7×7): 투구 그늘 아래 작은 눈과 짙은 눈썹, 볼은 불그스름
    box('head', 'skull', [-3.5, 18, -4.5], [3.5, 25, 2.5], 'skin', {
        px: {
            north: ['kkkkkkk', 'kkkkkkk', 'BBBsBBB', 'sEsssEs', 'rsssssr', 'sssssss', 'sssssss'],
            key: { k: 'skin:1', s: 'skin:2', B: 'beard:0', E: 'dark:0', r: 'skin:3' },
        },
    });
    box('head', 'nose', [-0.9, 19.6, -5.7], [0.9, 21.8, -4.4], 'skin');
    // 쇠 투구: 둥근 머리통 덮개, 챙, 정수리 볏과 황동 고글
    box('head', 'helmet', [-3.9, 22.4, -4.9], [3.9, 26, 2.9], 'iron');
    box('head', 'helmet_rim', [-4.3, 22.2, -5.3], [4.3, 22.9, 3.3], 'iron');
    box('head', 'helmet_crest', [-0.6, 26, -4.5], [0.6, 26.8, 2.5], 'brass');
    box('head', 'goggle_band', [-4, 23.6, -5], [4, 24.4, 3], 'leather');
    both(inG('head'), 'goggle', [0.4, 23.2, -5.6], [2.8, 25, -4.8], 'brass', {
        px: { north: (u, v, w, h) => (u > 0 && u < w - 1 && v > 0 && v < h - 1 ? 'glass:3' : undefined) },
    });
    // 수염: 얼굴 아래를 덮고 배꼽까지 내려오는 큰 수염, 콧수염, 땋은 두 가닥
    box('head', 'beard', [-3.8, 14.2, -5.3], [3.8, 20.4, -3.6], 'beard');
    box('head', 'beard_sides', [-3.7, 17, -4.8], [3.7, 20.8, 0.5], 'beard');
    box('head', 'mustache', [-3.2, 19.6, -5.6], [3.2, 20.5, -4.8], 'beard');
    both(inG('head'), 'braid', [1.2, 11, -5.2], [2.4, 14.5, -4.2], 'beard', { rot: [0, 0, 6], pivot: [1.8, 14.5, -4.7] });
    both(inG('head'), 'braid_tie', [1.1, 12.2, -5.3], [2.5, 12.9, -4.1], 'brass', { rot: [0, 0, 6], pivot: [1.8, 14.5, -4.7] });

    // ---------------------------------------------------------------- body
    // 넓은 가슴통: 가죽 흉갑 위 푸른 튜닉, 어깨 쇠판, 큰 황동 버클의 허리띠와 탄약 주머니
    box('body', 'torso', [-6, 10.5, -3.8], [6, 18.2, 3.8], 'tunic');
    box('body', 'breastplate', [-4.8, 11.5, -4.3], [4.8, 17.5, -3.7], 'leather');
    box('body', 'belly', [-5, 7, -3.5], [5, 11, 3.5], 'tunic');
    box('body', 'belt', [-5.3, 9.2, -3.8], [5.3, 10.8, 3.8], 'leather');
    box('body', 'buckle', [-1.6, 8.8, -4.3], [1.6, 11.2, -3.7], 'brass', {
        px: { north: (u, v, w, h) => (u > 0 && u < w - 1 && v > 0 && v < h - 1 ? 'dark:1' : undefined) },
    });
    both(inG('body'), 'pouch', [3, 7.8, -4.3], [5, 10, -3.6], 'leather');
    both(inG('body'), 'shoulder', [4.8, 16.5, -3.4], [8.8, 18.8, 3.4], 'iron', { rot: [0, 0, -12], pivot: [6.8, 17.6, 0] });

    // ---------------------------------------------------------------- arms
    // 굵은 팔: 튜닉 소매, 가죽 토시, 큰 주먹(손가락 없이 마디만 그림)
    both('arm', 'upper_arm', [5.8, 11, -2], [8.6, 17.5, 2], 'tunic');
    both('forearm', 'forearm', [5.9, 5.5, -1.9], [8.5, 11.2, 1.9], 'skin');
    both('forearm', 'bracer', [5.7, 6, -2.1], [8.7, 9.5, 2.1], 'leather');
    both('hand', 'fist', [5.6, 3.4, -2.2], [8.8, 6, 2.2], 'skin', {
        px: { north: (u, v) => (v === 0 && u % 2 === 1 ? 'skin:1' : undefined) },
    });

    // ---------------------------------------------------------------- legs
    both('leg', 'leg', [0.6, 2, -2], [4.6, 7.2, 2], 'tunic');
    both('leg', 'boot', [0.4, 0, -3.2], [4.8, 3, 2.2], 'boot');
    both('leg', 'boot_cuff', [0.3, 2.6, -2.3], [4.9, 3.6, 2.3], 'boot');

    // ---------------------------------------------------------------- gun
    InvasionParts.dwarfGun(B, 'gun', [7.2, 4.6, 0]);
    Object.assign(M, InvasionParts.dwarfGunMaterials);

    InvasionBB.paintPixels(RAW, M, 'dwarf_gunner');
    if (InvasionParts.dwarfAnimations) InvasionParts.dwarfAnimations(RAW);
    return { cubes: RAW.cubes.length, groups: Object.keys(RAW.groups).length };
})();
