// 암흑 신관. 인간, 흰 머리칼에 분홍 눈, 검은 사제복. 엘프 암살자와 같은 방식으로 짭니다.
//  - 체격: 마인크래프트 플레이어(머리 8, 몸통 12, 다리 12, 키 32), 여성. 가슴은 엘프처럼 모서리를 앞으로 돌린 큐브, 허리는 조금 좁힘.
//  - 얼굴: 바닐라 스킨처럼 한 칸 한 픽셀로 머리통 앞면에 그림(눈은 속눈썹·흰자·눈동자 1×2씩), 턱은 머리통과 같은 폭의 네모.
//  - 머리카락: ellen_fighter식(평평한 정수리 + 깎은 띠, 같은 규격 가닥), 어깨까지 오는 길이.
//  - 옷: 발목까지 오는 검은 수단(허리 팔각형에 맞춘 여덟 장 치마), 앞이 트인 어깨 망토, 목 뒤로 내린 두건,
//        앞으로 늘어진 영대(분홍 자수), 은 끈 허리띠, 넓은 종 모양 소매.
//  - 오른손에 지팡이(dark_staff.js). 텍스처는 바닐라식(paintPixels).
// Run inside Blockbench after common.js and dark_staff.js.
(function () {
    const B = InvasionBB.makeBuilder('dark_priest');
    const { group, box } = B;

    const M = {
        skin: { c: ['#a07878', '#cfa39a', '#efcbbd', '#fbe4d8'], soft: true },
        hair: { c: ['#8d8aa8', '#b9b6cf', '#dcdbea', '#f8f7ff'], lines: 3, noFade: true },
        hairShade: { c: ['#6f6c8c', '#8d8aa8', '#b9b6cf', '#dcdbea'] },
        robe: { c: ['#0e0c12', '#19161f', '#26212f', '#362f42'] },
        robeSkirt: { c: ['#0e0c12', '#19161f', '#26212f', '#362f42'], pleats: true, hem: 'trim' },
        capelet: { c: ['#0e0c12', '#19161f', '#26212f', '#362f42'], hem: 'trim' },
        trim: { c: ['#4a1438', '#7a2358', '#b0357c', '#e05aa8'] },
        silver: { c: ['#565b70', '#8a91a5', '#bcc3d2', '#eef2f8'] },
        iris: { c: ['#6e1a4a', '#b8307a', '#e860a8', '#ffa8d8'] },
        white: { c: ['#b8b6c8', '#dddbe8', '#f4f3fa', '#ffffff'] },
        dark: { c: ['#0b0a0e', '#141218', '#1d1a22', '#27232d'] },
    };

    // ---------------------------------------------------------------- skeleton
    group('root', [0, 0, 0]);
    group('body', [0, 12, 0], 'root');
    group('torso', [0, 15, 0], 'body');
    group('head', [0, 24, 0], 'torso');
    group('hair_back', [0, 32.3, 3.6], 'head');
    for (const [side, sign] of [['right', 1], ['left', -1]]) {
        group(side + '_arm', [5.2 * sign, 22.5, 0], 'torso');
        group(side + '_forearm', [5.2 * sign, 18, 0], side + '_arm');
        group(side + '_hand', [5.2 * sign, 12.5, 0], side + '_forearm');
        group(side + '_leg', [1.8 * sign, 12, 0], 'body');
        group(side + '_shin', [1.8 * sign, 6, 0], side + '_leg');
    }
    // 지팡이: 기본 자세에서는 앞으로 눕고, 애니메이션에서 아래팔을 앞으로 90도 들면 곧게 섭니다(군단장과 같은 방식).
    group('staff', [5.2, 12, 0], 'right_hand', [-90, 0, 0]);

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
    // h/H/d 머리카락, s/k 피부(밝음/그늘), L 속눈썹, W/w 흰자, i/I 분홍 눈동자(위/아래)
    const HEAD_KEY = {
        h: 'hair:2', H: 'hair:3', d: 'hair:1', s: 'skin:2', k: '#dfb3a6', L: '#3a2a3e', W: '#c9c4cf', w: 'white:2',
        b: 'hairShade:1', c: 'hairShade:0',
        i: 'iris:1', I: 'iris:2',
    };
    // 옆·뒷면 속머리 결: 세 칸마다 한 단 어두운 세로 줄.
    const INNER_STRANDS = (u) => (u % 3 === 1 ? 'hair:0' : 'hair:1');
    box('head', 'skull', [-4, 26, -4], [4, 32, 4], 'skin', {
        px: {
            // 앞머리 사이로 비치는 속머리: 세로 가닥 결을 긋고 아래로 갈수록 앞머리 그늘로 어두워지게 칠해 구멍처럼 보이지 않게 합니다.
            north: ['hhbhhbhh', 'ddcddcdd', 'ddcddcdd', 'bbcbbcbb', 'hkkkkkkh', 'LWissiWL'],
            up: (u) => (u === 3 ? 'hair:1' : 'hair:2'),
            east: INNER_STRANDS, west: INNER_STRANDS, south: INNER_STRANDS,
            key: HEAD_KEY,
        },
    });
    // 아래턱: 머리통과 같은 폭의 네모난 턱(바닐라 머리 8×8의 아래 두 줄).
    box('head', 'jaw', [-4, 24, -4], [4, 26, 4], 'skin', { px: { north: ['LwIssIwL', 'kssssssk'], key: HEAD_KEY } });
    both(inG('head'), 'brow', [1, 26.9, -4.03], [4, 27.9, -4.03], 'hairShade',
        { plane: 'z', rot: [0, 0, -3], pivot: [2.5, 27.4, -4.03], px: { north: ['BBB'], south: null, key: { B: 'hairShade:1' } } });
    // 사람 귀: 머리 옆 작은 살 큐브
    both(inG('head'), 'ear', [4, 26.6, -0.8], [4.6, 28.8, 0.6], 'skin');

    // 머리카락(ellen_fighter식): 평평한 정수리 + 깎은 띠, 가닥은 같은 규격. 어깨까지 오는 길이입니다.
    const CROWN_UP = { up: () => 'hair:3' };
    const CAP_STRANDS = (u) => (u % 3 === 1 ? 'hair:1' : 'hair:2');
    box('head', 'hair_cap', [-4.3, 31, -4.3], [4.3, 32.4, 4.3], 'hair',
        { px: Object.assign({ north: CAP_STRANDS, east: CAP_STRANDS, west: CAP_STRANDS, south: CAP_STRANDS }, CROWN_UP) });
    box('head', 'hair_top', [-3.2, 32.4, -3.2], [3.2, 33.2, 3.2], 'hair', { px: { up: (u, v, w) => (u === Math.floor(w / 2) ? 'hair:2' : 'hair:3') } });
    const CH = 36, CL = 1.5;
    both(inG('head'), 'hair_chamfer', [3.2, 32.5, -3.4], [3.2 + CL, 33.2, 3.4], 'hair', { rot: [0, 0, -CH], pivot: [3.2, 33.2, 0], px: CROWN_UP });
    box('head', 'hair_chamfer_front', [-3.4, 32.5, -3.2 - CL], [3.4, 33.2, -3.2], 'hair', { rot: [-CH, 0, 0], pivot: [0, 33.2, -3.2], px: CROWN_UP });
    box('head', 'hair_chamfer_back', [-3.4, 32.5, 3.2], [3.4, 33.2, 3.2 + CL], 'hair', { rot: [CH, 0, 0], pivot: [0, 33.2, 3.2], px: CROWN_UP });
    // 사제 모자: 정수리에 얹은 작고 납작한 검은 원통(필박스) 모자. 아래쪽에만 분홍 띠를 두릅니다.
    box('head', 'cap', [-3.4, 32.8, -3.4], [3.4, 35, 3.4], 'robe', { px: { up: () => 'robe:2' } });
    box('head', 'cap_band', [-3.55, 32.9, -3.55], [3.55, 33.7, 3.55], 'trim', { px: { up: null, down: null } });
    const SW = 1.2, SD = 0.8, ROOT = 32.3;
    // 앞머리: 가운데를 가르고 양옆으로 흘려 이마가 조금 보입니다.
    group('bangs', [0, ROOT, -3.7], 'head', [16, 0, 0]);
    [[-3.6, 6.2, -8], [-2.4, 5.0, -10], [-1.2, 3.6, -14], [1.2, 3.6, 14], [2.4, 5.0, 10], [3.6, 6.4, 8]]
        .forEach(([x, len, rz], i) => {
            box('bangs', 'bang_' + i, [x - SW / 2, ROOT - len, -3.7 - SD], [x + SW / 2, ROOT + 0.1, -3.7], i % 3 === 0 ? 'hairShade' : 'hair',
                { rot: [0, 0, rz], pivot: [x, ROOT + 0.1, -3.7 - SD / 2] });
        });
    // 옆머리: 귀를 덮고 턱 아래까지
    [[-3.2, 8.6, 5], [-2.0, 8.0, 7], [0.5, 7.4, 9], [1.7, 7.8, 10], [2.9, 7.2, 10]].forEach(([z, len, tilt], i) => {
        both(inG('head'), 'side_hair_' + i, [3.6, ROOT - len, z - SW / 2], [3.6 + SD, ROOT + 0.1, z + SW / 2], i % 2 ? 'hairShade' : 'hair',
            { rot: [0, 0, tilt], pivot: [3.6, ROOT, z] });
    });
    for (const [sx, sz, len] of [[1, 1, 8.0], [-1, 1, 8.0], [1, -1, 7.6], [-1, -1, 7.6]]) {
        const cx = 4.25 * sx, cz = 4.25 * sz;
        box('head', 'corner_hair_' + (sx > 0 ? 'r' : 'l') + (sz > 0 ? 'b' : 'f'), [cx - SW / 2, 32.2 - len, cz - SD / 2], [cx + SW / 2, 32.3, cz + SD / 2],
            'hair', { rot: [0, 45 * sx * sz, 0], pivot: [cx, 32.2, cz] });
    }
    // 뒷머리: 정수리에서 어깨 위까지
    [-3.6, -2.4, -1.2, 0, 1.2, 2.4, 3.6].forEach((x, i) => {
        const len = 9.4 + [0, 0.6, 0.2, 0.8, 0.3, 0.5, 0][i];
        box('hair_back', 'long_hair_' + i, [x - SW / 2, ROOT - len, 3.6], [x + SW / 2, ROOT + 0.1, 3.6 + SD], i % 2 ? 'hair' : 'hairShade',
            { rot: [-8, 0, 0], pivot: [x, ROOT, 3.6] });
    });
    [-3.0, -1.8, -0.6, 0.6, 1.8, 3.0].forEach((x, i) => {
        box('head', 'back_hair_' + i, [x - SW / 2, ROOT - 6.4 - (i % 2) * 0.5, 3.9], [x + SW / 2, ROOT + 0.1, 3.9 + SD], 'hair',
            { rot: [-11, 0, 0], pivot: [x, ROOT, 3.9] });
    });

    // ---------------------------------------------------------------- torso
    // 수단 윗도리: 가운데 분홍 단추 줄, 높은 깃, 어깨 망토, 목 뒤로 내린 두건
    const buttons = (u, v, w, h) => (u === Math.floor(w / 2) || u === Math.floor(w / 2) - 1) ? (v % 2 ? 'trim:3' : 'robe:1') : undefined;
    box('torso', 'chest', [-4, 19.5, -2], [4, 24, 2], 'robe', { px: { north: buttons } });
    box('torso', 'waist', [-2.9, 15, -1.6], [2.9, 19.8, 1.6], 'robe', { px: { north: buttons } });
    both(inG('torso'), 'flank', [3.2, 17.2, -1.8], [3.9, 20.4, 1.8], 'robe', { rot: [0, 0, -20], pivot: [3.9, 20.4, 0] });
    // 가슴(블록벤치에서 직접 다듬은 모양, 18:31 저장본 기준): 가슴 그룹을 옮겨 세 축으로 돌리고,
    // 덩이 큐브도 그 안에서 살짝 틉니다. 가운데 판은 앞으로 기울입니다.
    group('right_bust', [2.08702, 23.36808, -2.77873], 'torso', [90, -70, -90]);
    box('right_bust', 'right_bust', [2.38027, 20.3643, -4.18253], [4.63027, 23.6143, -0.53253], 'robe',
        { rot: [-0.88045, -9.96156, 5.07673], pivot: [3.83702, 21.61808, -2.77873] });
    group('left_bust', [-2.08702, 23.36808, -2.77873], 'torso', [115.50555, 67.73126, 117.27317]);
    box('left_bust', 'left_bust', [-4.58702, 20.36808, -4.42873], [-2.33702, 23.61808, -0.77873], 'robe',
        { rot: [0, 0, -5], pivot: [-3.83702, 21.61808, -2.77873] });
    box('torso', 'bust_centre', [-0.225, 19.86492, -3.45185], [0.225, 22.76492, -0.45185], 'robe',
        { rot: [14.75, 0, 0], pivot: [0, 20.83992, -1.05185] });
    // 어깨 망토: 치마처럼 목둘레에서 바깥으로 벌어지는 판들. 앞판은 가운데가 트여(x ±1.5) 좌우 두 장으로 갈라지고,
    // 그 틈으로 가슴이 드러납니다. 판마다 한 축으로만 기울입니다(앞뒤는 x, 옆은 z).
    const CAPE_TOP = 24.4, CAPE_FRONT = 3.6, CAPE_FLARE = 20;
    box('torso', 'capelet_top', [-5.5, CAPE_TOP - 0.4, -2.95], [5.5, CAPE_TOP, 2.95], 'robe', { px: { up: () => 'robe:2' } });
    both(inG('torso'), 'capelet_front', [1.5, CAPE_TOP - CAPE_FRONT, -2.95], [5.5, CAPE_TOP, -2.65], 'capelet',
        { rot: [CAPE_FLARE, 0, 0], pivot: [0, CAPE_TOP, -2.65] });
    box('torso', 'capelet_back', [-5.5, CAPE_TOP - 4.0, 2.65], [5.5, CAPE_TOP, 2.95], 'capelet',
        { rot: [-10, 0, 0], pivot: [0, CAPE_TOP, 2.65] });
    both(inG('torso'), 'capelet_side', [5.35, CAPE_TOP - 3.8, -2.8], [5.65, CAPE_TOP, 2.8], 'capelet',
        { rot: [0, 0, 14], pivot: [5.35, CAPE_TOP, 0] });
    box('torso', 'hood', [-3.6, 20.5, 2.2], [3.6, 25, 5.2], 'robe', { rot: [-10, 0, 0], pivot: [0, 25, 2.6] });
    // 영대: 목에서 내려오는 두 줄. 망토 앞판이 갈라진 틈 가장자리를 따라 한 도막으로 비스듬히 늘어지고, 끝에 은 술이 답니다.
    const stoleBand = (u, v) => (v % 5 === 2 ? 'trim:3' : undefined);
    const stoleTip = (u, v, w, h) => (v === h - 1 ? 'silver:2' : stoleBand(u, v));
    both(inG('torso'), 'stole_0', [1.45, 19.64495, -2.95], [2.35, CAPE_TOP, -2.8], 'trim',
        { px: { north: stoleTip }, rot: [23.04321, 0, 0], pivot: [1.35, CAPE_TOP, -2.8] });
    // 허리: 은 끈 띠
    box('torso', 'sash', [-3.6, 15.4, -2.05], [3.6, 16.2, 2.05], 'silver');

    // ---------------------------------------------------------------- robe skirt
    // 발목까지 오는 여덟 장 치마(블록벤치에서 직접 다듬은 모양, 10:08 저장본 기준).
    // 앞 세 장(robe_7·0·1)은 skirt_front, 뒤 세 장(robe_3·4·5)은 skrit_back 그룹에 묶어 허리 안쪽으로 당기고
    // 앞뒤로 7.5도씩 벌립니다. 옆 두 장(robe_2·6)은 그룹 없이 좌우 치맛자락(skirt_left/right) 안에 큐브로 바로 두어
    // 걸을 때 다리를 따라 흔들립니다. 판마다 가운데 판 하나와 양옆 이음 판(_fill_l/_fill_r) 두 장입니다.
    box('body', 'hips', [-3.3, 12, -1.3], [3.3, 15.4, 1.3], 'robe');
    box('body', 'hips_core', [-2.4, 12, -2], [2.4, 15.4, 2], 'robe');
    group('skirt_right', [1.8, 15.2, 0], 'body');
    group('skirt_left', [-1.8, 15.2, 0], 'body');
    group('skirt_front', [0, 15.45, -1.9], 'body', [7.5, 0, 0]);
    group('skrit_back', [0, 15.45, 2.1], 'body', [-7.5, 0, 0]);
    const SPREAD = 3.70743;
    // [부모, 판 이름, 그룹 원점(없으면 부모에 큐브로 바로 둠), 그룹 회전, [이름 뒤, from, to, 회전, 회전축] × 3]
    const panels = [
        ['skirt_front', 'robe_0', [0, 15.60643, -2.08769], [9, 0, 0], [
            ['', [-2.6, 1.98911, -2.12845], [2.6, 15.48911, -1.82845], [0, 0, 0], [0, 15.60643, -1.08769]],
            ['_fill_l', [-2.6, 2.10643, -1.35769], [-1.7, 15.60643, -1.11769], [0, 0, -SPREAD], [-2.6, 15.60643, -1.08769]],
            ['_fill_r', [1.7, 2.10643, -1.35769], [2.6, 15.60643, -1.11769], [0, 0, SPREAD], [2.6, 15.60643, -1.08769]]]],
        ['skirt_front', 'robe_1', [-2.33345, 15.45, -1.33345], [9, 45, 0], [
            ['', [-3.08345, 1.95, -1.63345], [-1.58345, 15.45, -1.33345], [0, 0, 0], [-2.33345, 15.45, -1.33345]],
            ['_fill_l', [-3.08345, 1.95, -1.60345], [-2.18345, 15.45, -1.36345], [0, 0, -SPREAD], [-3.08345, 15.45, -1.33345]],
            ['_fill_r', [-2.48345, 1.95, -1.60345], [-1.58345, 15.45, -1.36345], [0, 0, SPREAD], [-1.58345, 15.45, -1.33345]]]],
        ['skirt_left', 'robe_2', null, null, [
            ['', [-4.8, 1.7, -0.3], [-2.2, 15.2, 0], [0, 90, -9], [-3.5, 15.2, 0]],
            ['_fill_l', [-3.5, 1.7, 1.03], [-2.6, 15.2, 1.27], [-90, 86.2926, -99], [-3.5, 15.2, 1.3]],
            ['_fill_r', [-4.4, 1.7, -1.57], [-3.5, 15.2, -1.33], [90, 86.2926, 81], [-3.5, 15.2, -1.3]]]],
        ['skrit_back', 'robe_3', [-2.33345, 15.45, 1.33345], [9, 135, 0], [
            ['', [-3.08345, 1.95, 1.03345], [-1.58345, 15.45, 1.33345], [0, 0, 0], [-2.33345, 15.45, 1.33345]],
            ['_fill_l', [-3.08345, 1.95, 1.06345], [-2.18345, 15.45, 1.30345], [0, 0, -SPREAD], [-3.08345, 15.45, 1.33345]],
            ['_fill_r', [-2.48345, 1.95, 1.06345], [-1.58345, 15.45, 1.30345], [0, 0, SPREAD], [-1.58345, 15.45, 1.33345]]]],
        ['skrit_back', 'robe_4', [0, 15.45, 1.1], [9, 180, 0], [
            ['', [-2.6, 1.83267, 0.05923], [2.6, 15.33267, 0.35923], [0, 0, 0], [0, 15.45, 0.1]],
            ['_fill_l', [-2.6, 1.95, 0.83], [-1.7, 15.45, 1.07], [0, 0, -SPREAD], [-2.6, 15.45, 1.1]],
            ['_fill_r', [1.7, 1.95, 0.83], [2.6, 15.45, 1.07], [0, 0, SPREAD], [2.6, 15.45, 1.1]]]],
        ['skrit_back', 'robe_5', [2.33345, 15.45, 1.33345], [9, 225, 0], [
            ['', [1.58345, 1.95, 1.03345], [3.08345, 15.45, 1.33345], [0, 0, 0], [2.33345, 15.45, 1.33345]],
            ['_fill_l', [1.58345, 1.95, 1.06345], [2.48345, 15.45, 1.30345], [0, 0, -SPREAD], [1.58345, 15.45, 1.33345]],
            ['_fill_r', [2.18345, 1.95, 1.06345], [3.08345, 15.45, 1.30345], [0, 0, SPREAD], [3.08345, 15.45, 1.33345]]]],
        ['skirt_right', 'robe_6', null, null, [
            ['', [2.2, 1.7, -0.3], [4.8, 15.2, 0], [0, -90, 9], [3.5, 15.2, 0]],
            ['_fill_l', [3.5, 1.7, -1.57], [4.4, 15.2, -1.33], [90, -86.2926, -81], [3.5, 15.2, -1.3]],
            ['_fill_r', [2.6, 1.7, 1.03], [3.5, 15.2, 1.27], [-90, -86.2926, 99], [3.5, 15.2, 1.3]]]],
        ['skirt_front', 'robe_7', [2.33345, 15.45, -1.33345], [9, 315, 0], [
            ['', [1.58345, 1.95, -1.63345], [3.08345, 15.45, -1.33345], [0, 0, 0], [2.33345, 15.45, -1.33345]],
            ['_fill_l', [1.58345, 1.95, -1.60345], [2.48345, 15.45, -1.36345], [0, 0, -SPREAD], [1.58345, 15.45, -1.33345]],
            ['_fill_r', [2.18345, 1.95, -1.60345], [3.08345, 15.45, -1.36345], [0, 0, SPREAD], [3.08345, 15.45, -1.33345]]]],
    ];
    for (const [parent, name, origin, rotation, parts] of panels) {
        if (origin) group(name, origin, parent, rotation);
        for (const [suffix, from, to, rot, pivot] of parts) {
            box(origin ? name : parent, name + suffix, from, to, 'robeSkirt', { rot, pivot });
        }
    }

    // ---------------------------------------------------------------- arms
    // 윗팔은 좁은 소매, 아래팔은 끝이 넓어지는 종 모양 소매(분홍 단), 손은 창백한 맨손
    both('arm', 'upper_arm', [4.1, 18, -1.3], [6.3, 24, 1.3], 'robe');
    both('forearm', 'sleeve', [3.6, 12.6, -2], [6.8, 18.3, 2], 'robe', { bands: [[5, 5, 'trim']] });
    both('forearm', 'sleeve_inner', [4.1, 12.4, -1.5], [6.3, 13, 1.5], 'dark');
    both('hand', 'hand', [4.3, 10.6, -1.1], [6.1, 12.8, 1.1], 'skin');

    // ---------------------------------------------------------------- legs
    // 치마 속 맨다리와 뾰족한 신. 맨살 칠(블록벤치에서 다듬은 모양): 뒷면은 밝게 두고 한쪽 세로 모서리 칸만 그늘지게,
    // 안쪽 옆면(오른다리 서쪽, 왼다리 동쪽)은 그늘 없이 고르게 칠합니다. 안쪽 면이 좌우로 달라서 both 대신 따로 만듭니다.
    const legSkin = (inner) => ({ px: { south: (u, v, w) => (u === w - 1 ? 'skin:1' : 'skin:2'), [inner]: () => 'skin:2' } });
    for (const [name, g, from, to] of [['thigh', 'leg', [0.4, 6, -1.5], [3.2, 12.2, 1.5]], ['shin', 'shin', [0.5, 0.6, -1.4], [3.1, 6.2, 1.4]]]) {
        box('right_' + g, 'right_' + name, from, to, 'skin', legSkin('west'));
        const [f, t, o] = flip(from, to, legSkin('east'));
        box('left_' + g, 'left_' + name, f, t, 'skin', o);
    }
    both('shin', 'shoe', [0.4, 0, -3.2], [3.2, 1.2, 1.6], 'dark');

    // ---------------------------------------------------------------- staff
    InvasionParts.darkStaff(B, 'staff', [5.2, 12, 0]);
    Object.assign(M, InvasionParts.darkStaffMaterials);

    InvasionBB.paintPixels(B, M, 'dark_priest');
    if (InvasionParts.darkPriestAnimations) InvasionParts.darkPriestAnimations(B);
    return { cubes: B.cubes.length, groups: Object.keys(B.groups).length };
})();
