// 정원사(식물 빌더 엘리트 타워). 카자미 유카 풍: 초록 곱슬 단발, 붉은 눈, 흰 블라우스, 붉은 체크 조끼와 발목까지 오는
// 붉은 체크 치마, 노란 스카프 타이, 분홍 양산. 암흑 신관과 같은 골격·치마 방식으로 짭니다.
//  - 체격: 마인크래프트 플레이어(머리 8, 몸통 12, 다리 12, 키 32), 여성.
//  - 얼굴: 바닐라 스킨처럼 한 칸 한 픽셀(속눈썹·흰자·붉은 눈동자), 턱은 머리통과 같은 폭의 네모.
//  - 머리카락: ellen_fighter식(평평한 정수리 + 깎은 띠, 같은 규격 가닥). 턱선까지 오는 곱슬 단발이라 가닥 끝이 바깥으로 뻗칩니다.
//  - 오른손에 양산(gardener_parasol.js). 텍스처는 바닐라식(paintPixels).
// Run inside Blockbench after common.js and gardener_parasol.js (and gardener_anims.js for animations).
(function () {
    const B = InvasionBB.makeBuilder('gardener');
    const { group, box } = B;

    const M = {
        skin: { c: ['#a07878', '#cfa39a', '#efcbbd', '#fbe4d8'], soft: true },
        hair: { c: ['#2c5a30', '#44843f', '#66ad55', '#96d47a'], lines: 3, noFade: true },
        hairShade: { c: ['#1f4424', '#2c5a30', '#44843f', '#66ad55'] },
        blouse: { c: ['#b4b0bc', '#d6d3de', '#eeedf4', '#ffffff'] },
        plaid: { c: ['#5a0c12', '#9c1a22', '#c92f36', '#e85454'] },
        plaidSkirt: { c: ['#5a0c12', '#9c1a22', '#c92f36', '#e85454'], pleats: true, hem: 'plaid' },
        tie: { c: ['#8a6a10', '#c8a020', '#f0cc40', '#fff090'] },
        iris: { c: ['#5a0a0a', '#a81818', '#e0302a', '#ff8070'] },
        white: { c: ['#b8b6c8', '#dddbe8', '#f4f3fa', '#ffffff'] },
        shoe: { c: ['#2a1a12', '#402a1c', '#5a3c28', '#7a5438'] },
        button: { c: ['#7a6a30', '#b09a40', '#e0c860', '#fff0a0'] },
    };

    // 체크무늬: 세 칸마다 어두운 격자. 면마다 같은 무늬를 씁니다.
    const plaid = (base) => (u, v) => {
        const lineU = u % 3 === 0, lineV = v % 3 === 0;
        if (lineU && lineV) return base + ':0';
        if (lineU || lineV) return base + ':1';
        return undefined;
    };
    const PLAID = { north: plaid('plaid'), south: plaid('plaid'), east: plaid('plaid'), west: plaid('plaid') };
    const PLAID_SKIRT = { north: plaid('plaidSkirt'), south: plaid('plaidSkirt'), east: plaid('plaidSkirt'), west: plaid('plaidSkirt') };

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
    // 양산: 손을 내리면 자루가 곧게 서 덮개가 머리 위에 옵니다.
    group('parasol', [5.2, 11.6, 0], 'right_hand');

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
    // h/H/d 머리카락, s/k 피부(밝음/그늘), L 속눈썹, W/w 흰자, i/I 붉은 눈동자(위/아래)
    const HEAD_KEY = {
        h: 'hair:2', H: 'hair:3', d: 'hair:1', s: 'skin:2', k: '#dfb3a6', L: '#2a1a1a', W: '#c9c4cf', w: 'white:2',
        b: 'hairShade:1', c: 'hairShade:0', i: 'iris:1', I: 'iris:2',
    };
    const INNER_STRANDS = (u) => (u % 3 === 1 ? 'hair:0' : 'hair:1');
    box('head', 'skull', [-4, 26, -4], [4, 32, 4], 'skin', {
        px: {
            north: ['hhbhhbhh', 'ddcddcdd', 'ddcddcdd', 'bbcbbcbb', 'hkkkkkkh', 'LWissiWL'],
            up: (u) => (u === 3 ? 'hair:1' : 'hair:2'),
            east: INNER_STRANDS, west: INNER_STRANDS, south: INNER_STRANDS,
            key: HEAD_KEY,
        },
    });
    box('head', 'jaw', [-4, 24, -4], [4, 26, 4], 'skin', { px: { north: ['LwIssIwL', 'kssssssk'], key: HEAD_KEY } });
    both(inG('head'), 'brow', [1, 26.9, -4.03], [4, 27.9, -4.03], 'hairShade',
        { plane: 'z', rot: [0, 0, 3], pivot: [2.5, 27.4, -4.03], px: { north: ['BBB'], south: null, key: { B: 'hairShade:1' } } });
    both(inG('head'), 'ear', [4, 26.6, -0.8], [4.6, 28.8, 0.6], 'skin');

    // 머리카락: 평평한 정수리 + 깎은 띠
    const CROWN_UP = { up: () => 'hair:3' };
    const CAP_STRANDS = (u) => (u % 3 === 1 ? 'hair:1' : 'hair:2');
    box('head', 'hair_cap', [-4.3, 31, -4.3], [4.3, 32.4, 4.3], 'hair',
        { px: Object.assign({ north: CAP_STRANDS, east: CAP_STRANDS, west: CAP_STRANDS, south: CAP_STRANDS }, CROWN_UP) });
    box('head', 'hair_top', [-3.2, 32.4, -3.2], [3.2, 33.2, 3.2], 'hair', { px: { up: (u, v, w) => (u === Math.floor(w / 2) ? 'hair:2' : 'hair:3') } });
    const CH = 36, CL = 1.5;
    both(inG('head'), 'hair_chamfer', [3.2, 32.5, -3.4], [3.2 + CL, 33.2, 3.4], 'hair', { rot: [0, 0, -CH], pivot: [3.2, 33.2, 0], px: CROWN_UP });
    box('head', 'hair_chamfer_front', [-3.4, 32.5, -3.2 - CL], [3.4, 33.2, -3.2], 'hair', { rot: [-CH, 0, 0], pivot: [0, 33.2, -3.2], px: CROWN_UP });
    box('head', 'hair_chamfer_back', [-3.4, 32.5, 3.2], [3.4, 33.2, 3.2 + CL], 'hair', { rot: [CH, 0, 0], pivot: [0, 33.2, 3.2], px: CROWN_UP });

    const SW = 1.2, SD = 0.8, ROOT = 32.3;
    // 곱슬: 가닥 하나를 마디 몇 개로 쪼개 마디마다 꺾습니다. 가운데서 안으로 한 번 말렸다가 끝이 바깥으로 튕깁니다.
    // segs = [[길이 비율, 꺾는 각], ...]. 마디는 앞 마디 끝에서 이어지고, 조금 겹쳐 틈이 보이지 않게 합니다.
    const RAD = Math.PI / 180, JOIN = 0.3;
    const chain = (len, segs, base, step) => {
        let along = 0, side = 0, total = segs.reduce((sum, [f]) => sum + f, 0);
        segs.forEach(([f, bend], k) => {
            const L = len * f / total, th = base + bend;
            step(k, L, th, along, side);
            side += L * Math.sin(th * RAD);
            along += L * Math.cos(th * RAD);
        });
    };
    // 앞머리: 이마를 덮는 결 고운 가닥들. 가운데는 짧고 양옆으로 갈수록 길어지며, 끝이 바깥으로 말립니다.
    group('bangs', [0, ROOT, -3.7], 'head', [10, 0, 0]);
    [[-3.6, 5.4, -10], [-2.4, 4.2, -8], [-1.2, 3.6, -5], [0, 3.2, 3], [1.2, 3.6, 5], [2.4, 4.2, 8], [3.6, 5.4, 10]]
        .forEach(([x, len, rz], i) => {
            const out = x < 0 || (x === 0 && i % 2) ? -1 : 1;
            chain(len, [[0.6, 0], [0.4, out * 26]], rz, (k, L, th, along, side) => {
                const cx = x + side, top = ROOT + 0.1 - along;
                box('bangs', 'bang_' + i + (k ? '_' + k : ''), [cx - SW / 2, top - L, -3.7 - SD], [cx + SW / 2, top + (k ? JOIN : 0), -3.7],
                    i % 3 === 0 ? 'hairShade' : 'hair', { rot: [0, 0, th], pivot: [cx, top, -3.7 - SD / 2] });
            });
        });
    // 이마 덮개: 앞머리 뒤로 이마에 붙은 가닥을 한 줄 깔아, 이마를 텍스처 대신 머리카락 큐브로 덮습니다. 끝은 눈썹 바로 위에서 들쭉날쭉합니다.
    [-3.5, -2.5, -1.5, -0.5, 0.5, 1.5, 2.5, 3.5].forEach((x, i) => {
        const end = 28.0 + [0.2, 0, 0.5, 0.1, 0.4, 0, 0.3, 0.1][i];
        box('head', 'fringe_' + i, [x - 0.6, end, -4.35], [x + 0.6, ROOT, -4.02], i % 2 ? 'hair' : 'hairShade');
    });
    // 옆머리: 턱선까지. 볼 옆에서 안으로 한 번 말렸다가 끝이 바깥으로 크게 뻗칩니다(곱슬).
    const SIDE_CURLS = [[[0.45, 0], [0.3, -14], [0.25, 30]], [[0.4, 0], [0.35, -18], [0.25, 34]], [[0.5, 0], [0.25, -10], [0.25, 26]]];
    [[-3.2, 7.6, 10], [-2.0, 7.2, 12], [-0.6, 7.6, 14], [0.8, 7.0, 12], [2.2, 6.6, 11], [3.3, 6.2, 9]].forEach(([z, len, tilt], i) => {
        chain(len, SIDE_CURLS[i % 3], tilt, (k, L, th, along, side) => {
            const x0 = 3.6 + side, top = ROOT - along;
            both(inG('head'), 'side_hair_' + i + (k ? '_' + k : ''), [x0, top - L, z - SW / 2], [x0 + SD, top + (k ? JOIN : 0.1), z + SW / 2],
                i % 2 ? 'hairShade' : 'hair', { rot: [0, 0, th], pivot: [x0, top, z] });
        });
    });
    for (const [sx, sz, len] of [[1, 1, 7.2], [-1, 1, 7.2], [1, -1, 6.8], [-1, -1, 6.8]]) {
        const cx = 4.25 * sx, cz = 4.25 * sz;
        box('head', 'corner_hair_' + (sx > 0 ? 'r' : 'l') + (sz > 0 ? 'b' : 'f'), [cx - SW / 2, 32.2 - len, cz - SD / 2], [cx + SW / 2, 32.3, cz + SD / 2],
            'hair', { rot: [0, 45 * sx * sz, 0], pivot: [cx, 32.2, cz] });
    }
    // 뒷머리: 목덜미까지 오는 단발. 뒤통수를 따라 내려오다 한 번 안으로 말리고 끝이 뒤로 뻗칩니다(곱슬).
    // x 회전이 음수면 끝이 뒤(+z)로 갑니다.
    const BACK_CURLS = [[[0.45, 0], [0.3, 12], [0.25, -30]], [[0.4, 0], [0.35, 16], [0.25, -36]]];
    [-3.6, -2.4, -1.2, 0, 1.2, 2.4, 3.6].forEach((x, i) => {
        const len = 7.4 + [0, 0.5, 0.2, 0.7, 0.3, 0.5, 0][i];
        chain(len, BACK_CURLS[i % 2], -14, (k, L, th, along, side) => {
            const z0 = 3.6 - side, top = ROOT - along;
            box('hair_back', 'long_hair_' + i + (k ? '_' + k : ''), [x - SW / 2, top - L, z0], [x + SW / 2, top + (k ? JOIN : 0.1), z0 + SD],
                i % 2 ? 'hair' : 'hairShade', { rot: [th, 0, 0], pivot: [x, top, z0] });
        });
    });
    [-3.0, -1.8, -0.6, 0.6, 1.8, 3.0].forEach((x, i) => {
        chain(5.6 + (i % 2) * 0.5, [[0.6, 0], [0.4, -20]], -9, (k, L, th, along, side) => {
            const z0 = 3.9 - side, top = ROOT - along;
            box('head', 'back_hair_' + i + (k ? '_' + k : ''), [x - SW / 2, top - L, z0], [x + SW / 2, top + (k ? JOIN : 0.1), z0 + SD], 'hair',
                { rot: [th, 0, 0], pivot: [x, top, z0] });
        });
    });

    // ---------------------------------------------------------------- torso
    // 흰 블라우스 위에 앞이 트인 붉은 체크 조끼(금 단추), 목에 노란 스카프 타이.
    box('torso', 'chest', [-4, 20.4, -2], [4, 24, 2], 'blouse');
    box('torso', 'waist', [-2.9, 15, -1.6], [2.9, 19.8, 1.6], 'blouse');
    // 가슴(암흑 신관과 같은 모양)
    group('right_bust', [2.08702, 23.36808, -2.77873], 'torso', [90, -70, -90]);
    box('right_bust', 'right_bust', [2.38027, 20.3643, -4.18253], [4.63027, 23.6143, -0.53253], 'blouse',
        { rot: [-0.88045, -9.96156, 5.07673], pivot: [3.83702, 21.61808, -2.77873] });
    group('left_bust', [-2.08702, 23.36808, -2.77873], 'torso', [115.50555, 67.73126, 117.27317]);
    box('left_bust', 'left_bust', [-4.58702, 20.36808, -4.42873], [-2.33702, 23.61808, -0.77873], 'blouse',
        { rot: [0, 0, -5], pivot: [-3.83702, 21.61808, -2.77873] });
    box('torso', 'bust_centre', [-0.225, 19.86492, -3.45185], [0.225, 22.76492, -0.45185], 'blouse',
        { rot: [14.75, 0, 0], pivot: [0, 20.83992, -1.05185] });
    // 조끼: 앞판 좌우(가운데가 트여 블라우스가 보임), 뒤판, 옆판. 앞판 가장자리에 금 단추.
    const vestFront = (u, v, w) => (u === w - 1 && v % 3 === 1 ? 'button:2' : plaid('plaid')(u, v));
    // 허리: 치마 윗단(±3.3)보다 좁은 ±3.2까지 조이고, 허리 위에서 비스듬한 판으로 가슴 폭(±4.1)까지 벌어집니다.
    const WAIST_W = 3.2, WAIST_TOP = 18.0, FLARE = -Math.atan2(4.1 - WAIST_W, 20.4 - WAIST_TOP) * 180 / Math.PI;
    const flare = { rot: [0, 0, FLARE], pivot: [WAIST_W, WAIST_TOP, 0] };
    both(inG('torso'), 'vest_front', [1.1, 15.2, -2.15], [WAIST_W, 19.6, -1.85], 'plaid', { px: { north: vestFront, south: plaid('plaid') } });
    box('torso', 'vest_back', [-WAIST_W, 15.2, 1.85], [WAIST_W, 20.4, 2.15], 'plaid', { px: PLAID });
    box('torso', 'vest_back_upper', [-4.1, 20.3, 1.85], [4.1, 24, 2.15], 'plaid', { px: PLAID });
    both(inG('torso'), 'vest_side', [WAIST_W - 0.3, 15.2, -2.0], [WAIST_W, WAIST_TOP + 0.1, 2.0], 'plaid', { px: PLAID });
    both(inG('torso'), 'vest_side_upper', [3.85, 20.3, -2.0], [4.15, 24, 2.0], 'plaid', { px: PLAID });
    // 벌어지는 구간: 옆벽과 앞뒤 판을 같은 각으로 눕혀 허리에서 가슴으로 이어 줍니다.
    both(inG('torso'), 'vest_flare_side', [WAIST_W - 0.3, WAIST_TOP, -2.0], [WAIST_W, 20.5, 2.0], 'plaid', Object.assign({ px: PLAID }, flare));
    both(inG('torso'), 'vest_flare_front', [WAIST_W - 1.2, WAIST_TOP, -2.15], [WAIST_W, 20.5, -1.85], 'plaid', Object.assign({ px: PLAID }, flare));
    both(inG('torso'), 'vest_flare_back', [WAIST_W - 1.2, WAIST_TOP, 1.85], [WAIST_W, 20.5, 2.15], 'plaid', Object.assign({ px: PLAID }, flare));
    box('torso', 'vest_waist', [-3.1, 15.0, -1.85], [3.1, 16.0, 1.85], 'plaid', { px: PLAID });
    // 가슴 위 조끼: 흰 블라우스 가슴을 바깥쪽에서 감싸며 굴곡을 따라 덮습니다. 가슴 앞면 윤곽(아래 선반 → 가장 나온 곳 →
    // 어깨로 비스듬히 들어감)을 판 몇 장으로 이어 붙이고, 안쪽 가장자리는 위로 갈수록 바깥으로 물러나 V자 앞섶이 됩니다.
    // 윤곽점 [y, z(앞면)]과 판마다 안쪽 가장자리 x. 판은 아래 점에서 위 점으로 x축 회전해 눕습니다.
    const BUST_PROFILE = [[19.6, -2.15], [20.1, -3.45], [20.7, -3.55], [22.1, -3.2], [23.5, -2.8], [24.1, -2.1]];
    const LAPEL_INNER = [1.6, 2.1, 2.4, 2.8, 3.15];
    const LAPEL_OUTER = [3.75, 4.12, 4.12, 4.12, 4.12];   // 가슴 아래 선반은 허리에서 벌어지는 판 안쪽에서 끝납니다.
    const VEST_T = 0.25, VEST_GAP = 0.04;
    for (let k = 0; k + 1 < BUST_PROFILE.length; k++) {
        const [y1, z1] = BUST_PROFILE[k], [y2, z2] = BUST_PROFILE[k + 1];
        const len = Math.hypot(y2 - y1, z2 - z1) + 0.08, th = Math.atan2(z2 - z1, y2 - y1) * 180 / Math.PI;
        const front = z1 - VEST_GAP;
        both(inG('torso'), 'vest_lapel_' + k, [LAPEL_INNER[k], y1, front - VEST_T], [LAPEL_OUTER[k], y1 + len, front], 'plaid',
            { rot: [th, 0, 0], pivot: [0, y1, front], px: k === 0 ? Object.assign({}, PLAID, { up: () => 'plaid:1' }) : PLAID });
        // 옆면: 가슴 옆구리가 흰색으로 비치지 않게 판 바깥 끝에서 뒤(옆판)까지 채웁니다.
        const depth = -2.0 - Math.min(z1, z2);
        if (depth > 0.3) {
            both(inG('torso'), 'vest_wrap_' + k, [LAPEL_OUTER[k] - 0.28, y1, front - VEST_T], [LAPEL_OUTER[k], y1 + len, front + depth], 'plaid',
                { rot: [th, 0, 0], pivot: [0, y1, front], px: PLAID });
        }
    }
    // 깃: 흰 블라우스 깃이 조끼 위로 접혀 나옵니다.
    both(inG('torso'), 'collar', [0.4, 23.1, -2.3], [2.6, 24.2, -2.0], 'blouse', { rot: [0, 0, -18], pivot: [0.4, 24.2, -2.15] });
    // 스카프 타이: 목의 큰 매듭에서 가슴 앞으로 두 자락이 벌어져 늘어집니다(가슴보다 앞이라 정면에서 잘 보입니다).
    const TIE_TAIL = { north: (u, v, w) => (u === 0 || u === w - 1 ? 'tie:1' : 'tie:2') };
    box('torso', 'tie_knot', [-1.2, 22.7, -3.3], [1.2, 24.1, -2.0], 'tie', { px: { north: (u, v, w) => (u === 0 || u === w - 1 ? 'tie:1' : 'tie:3') } });
    box('torso', 'tie_drape', [-0.9, 21.9, -3.95], [0.9, 23.1, -3.2], 'tie', { rot: [-12, 0, 0], pivot: [0, 23.1, -3.3] });
    both(inG('torso'), 'tie_tail', [0.0, 19.4, -4.15], [1.2, 22.4, -3.9], 'tie', { rot: [0, 0, 9], pivot: [0, 22.4, -4.0], px: TIE_TAIL });

    // ---------------------------------------------------------------- skirt
    // 발목까지 오는 붉은 체크 치마(암흑 신관과 같은 여덟 장 구성).
    box('body', 'hips', [-3.3, 12, -1.3], [3.3, 15.4, 1.3], 'plaid', { px: PLAID });
    box('body', 'hips_core', [-2.4, 12, -2], [2.4, 15.4, 2], 'plaid', { px: PLAID });
    group('skirt_right', [1.8, 15.2, 0], 'body');
    group('skirt_left', [-1.8, 15.2, 0], 'body');
    group('skirt_front', [0, 15.45, -1.9], 'body', [7.5, 0, 0]);
    group('skrit_back', [0, 15.45, 2.1], 'body', [-7.5, 0, 0]);
    const SPREAD = 3.70743;
    const SIDE = 9;
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
            ['', [-4.8, 1.7, -0.3], [-2.2, 15.2, 0], [0, 90, -SIDE], [-3.5, 15.2, 0]],
            ['_fill_l', [-3.5, 1.7, 1.03], [-2.6, 15.2, 1.27], [-90, 86.2926, -90 - SIDE], [-3.5, 15.2, 1.3]],
            ['_fill_r', [-4.4, 1.7, -1.57], [-3.5, 15.2, -1.33], [90, 86.2926, 90 - SIDE], [-3.5, 15.2, -1.3]]]],
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
            ['', [2.2, 1.7, -0.3], [4.8, 15.2, 0], [0, -90, SIDE], [3.5, 15.2, 0]],
            ['_fill_l', [3.5, 1.7, -1.57], [4.4, 15.2, -1.33], [90, -86.2926, SIDE - 90], [3.5, 15.2, -1.3]],
            ['_fill_r', [2.6, 1.7, 1.03], [3.5, 15.2, 1.27], [-90, -86.2926, 90 + SIDE], [3.5, 15.2, 1.3]]]],
        ['skirt_front', 'robe_7', [2.33345, 15.45, -1.33345], [9, 315, 0], [
            ['', [1.58345, 1.95, -1.63345], [3.08345, 15.45, -1.33345], [0, 0, 0], [2.33345, 15.45, -1.33345]],
            ['_fill_l', [1.58345, 1.95, -1.60345], [2.48345, 15.45, -1.36345], [0, 0, -SPREAD], [1.58345, 15.45, -1.33345]],
            ['_fill_r', [2.18345, 1.95, -1.60345], [3.08345, 15.45, -1.36345], [0, 0, SPREAD], [3.08345, 15.45, -1.33345]]]],
    ];
    for (const [parent, name, origin, rotation, parts] of panels) {
        if (origin) group(name, origin, parent, rotation);
        for (const [suffix, from, to, rot, pivot] of parts) {
            box(origin ? name : parent, name + suffix, from, to, 'plaidSkirt', { rot, pivot, px: PLAID_SKIRT });
        }
    }
    // 속주름: 앞뒤판은 옆판보다 더 벌어져서 밑단 쪽 대각판과 옆판 사이가 트입니다. 그 사이 안쪽에 판을 하나씩 더 대
    // 옆에서 봐도 다리나 뒤가 비치지 않게 합니다.
    const GAP_R = 2.6, GAP_W = 1.2, GAP_TILT = 13;
    [['fl', 67.5, -1, -1], ['bl', 112.5, -1, 1], ['br', 247.5, 1, 1], ['fr', 292.5, 1, -1]].forEach(([id, yaw, sx, sz]) => {
        const rad = yaw * Math.PI / 180;
        const cx = sx * Math.abs(Math.sin(rad)) * GAP_R, cz = sz * Math.abs(Math.cos(rad)) * GAP_R;
        const name = 'robe_gap_' + id;
        group(name, [cx, 15.3, cz], 'body', [GAP_TILT, yaw, 0]);
        box(name, name, [cx - GAP_W, 1.9, cz - 0.3], [cx + GAP_W, 15.3, cz], 'plaidSkirt', { px: PLAID_SKIRT });
    });

    // ---------------------------------------------------------------- arms
    // 흰 블라우스 긴소매, 손목에 붉은 체크 띠, 맨손.
    both('arm', 'upper_arm', [4.1, 18, -1.3], [6.3, 24, 1.3], 'blouse');
    both('forearm', 'sleeve', [3.9, 12.8, -1.4], [6.5, 18.3, 1.4], 'blouse', { bands: [[4, 5, 'plaid']] });
    both('hand', 'hand', [4.3, 10.6, -1.1], [6.1, 12.8, 1.1], 'skin');

    // ---------------------------------------------------------------- legs
    const legSkin = (inner) => ({ px: { south: (u, v, w) => (u === w - 1 ? 'skin:1' : 'skin:2'), [inner]: () => 'skin:2' } });
    for (const [name, g, from, to] of [['thigh', 'leg', [0.4, 6, -1.5], [3.2, 12.2, 1.5]], ['shin', 'shin', [0.5, 0.6, -1.4], [3.1, 6.2, 1.4]]]) {
        box('right_' + g, 'right_' + name, from, to, 'skin', legSkin('west'));
        const [f, t, o] = flip(from, to, legSkin('east'));
        box('left_' + g, 'left_' + name, f, t, 'skin', o);
    }
    both('shin', 'shoe', [0.4, 0, -3.2], [3.2, 1.4, 1.6], 'shoe');

    // ---------------------------------------------------------------- parasol
    InvasionParts.gardenerParasol(B, 'parasol', [5.2, 11.6, 0]);
    Object.assign(M, InvasionParts.gardenerParasolMaterials);

    InvasionBB.paintPixels(B, M, 'gardener');
    if (InvasionParts.gardenerAnimations) InvasionParts.gardenerAnimations(B);
    return { cubes: B.cubes.length, groups: Object.keys(B.groups).length };
})();
