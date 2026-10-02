// 엘프 암살자. 백발, 목만 감싸고 뒤로 꼬리가 흐르는 스카프, 짧은 주름치마, 타이즈와 부츠.
// 양손에 단검을 역수로 쥡니다. Run inside Blockbench after common.js and elf_dagger.js.
//
// 체격은 마인크래프트 플레이어와 같습니다(머리 8, 몸통 12, 다리 12, 키 32).
// 모델은 cirno/remilia처럼 자유롭게 짭니다: 눈은 흰자·눈동자·하이라이트·속눈썹을 얇은 큐브로 겹치고,
// 머리카락은 뿌리·끝 두 마디 가닥을 회전 그룹으로 부채처럼 펼칩니다.
// 텍스처만 바닐라식입니다(paintPixels: 노이즈 없는 4단 램프, 윗면·앞면이 밝음).
(function () {
    const B = InvasionBB.makeBuilder('elf_assassin');
    const { group, box } = B;

    // 램프는 [그림자, 어둠, 중간, 밝음]. 그림자 쪽은 푸르게, 밝은 쪽은 따뜻하게 색조를 옮깁니다.
    const M = {
        skin: { c: ['#9c6b6b', '#c98f86', '#e8b8a4', '#f7d9c4'], soft: true },
        blush: { c: ['#c77c80', '#dc8f90', '#eca0a0', '#f5b8b2'] },
        hair: { c: ['#8d8aa8', '#b9b6cf', '#dcdbea', '#f8f7ff'], lines: 3, noFade: true },
        hairShade: { c: ['#6f6c8c', '#8d8aa8', '#b9b6cf', '#dcdbea'] },
        scarf: { c: ['#4a0f24', '#7d1a2c', '#a92b35', '#d24a45'] },
        bodice: { c: ['#1c1528', '#2c2140', '#3f3059', '#584577'] },
        skirt: { c: ['#1f1730', '#302447', '#433463', '#5c4a82'], pleats: true, hem: 'scarf' },
        tights: { c: ['#111018', '#1b1a26', '#282638', '#3a3750'] },
        leather: { c: ['#1a1216', '#2a1d22', '#3d2a2e', '#57403f'], rim: true },
        boot: { c: ['#140f14', '#231a21', '#35272f', '#4c3a42'], rim: true },
        silver: { c: ['#565b70', '#8a91a5', '#bcc3d2', '#eef2f8'] },
        iris: { c: ['#3b1a6e', '#6a33b5', '#9d6ae6', '#c9a8ff'] },
        white: { c: ['#b8b6c8', '#dddbe8', '#f4f3fa', '#ffffff'] },
        dark: { c: ['#0d0b12', '#16131d', '#211c2a', '#2e2839'] },
    };

    // ---------------------------------------------------------------- skeleton
    group('root', [0, 0, 0]);
    group('body', [0, 12, 0], 'root');
    group('torso', [0, 15, 0], 'body');
    group('head', [0, 24, 0], 'torso');
    group('hair_back', [0, 32.3, 3.6], 'head');
    group('scarf_tail', [0, 23, 3.9], 'torso');
    for (const [side, sign] of [['right', 1], ['left', -1]]) {
        group(side + '_arm', [5.2 * sign, 22.5, 0], 'torso');
        group(side + '_forearm', [5.2 * sign, 18, 0], side + '_arm');
        group(side + '_hand', [5.2 * sign, 12.5, 0], side + '_forearm');
        // 역수: 자루는 주먹을 앞뒤로 꿰뚫고, 날은 주먹 뒤로 뻗어 살짝 아래를 향합니다. 날 쪽은 아래를 봅니다.
        group(side + '_dagger', [5.2 * sign, 11.9, 0], side + '_hand', [105, 0, 0]);
        group(side + '_leg', [1.8 * sign, 12, 0], 'body');
        group(side + '_shin', [1.8 * sign, 6, 0], side + '_leg');
    }

    const sides = [['right', 1], ['left', -1]];
    const flip = (from, to, opts) => {
        const o = Object.assign({}, opts || {}, { mirrored: true });
        if (o.rot) o.rot = [o.rot[0], -o.rot[1], -o.rot[2]];
        if (o.pivot) o.pivot = [-o.pivot[0], o.pivot[1], o.pivot[2]];
        return [[-to[0], from[1], from[2]], [-from[0], to[1], to[2]], o];
    };
    // 오른쪽 좌표로 적고 왼쪽은 x를 뒤집습니다. g는 'arm'처럼 쓰면 right_arm/left_arm, 함수면 그 결과 그룹입니다.
    const both = (g, name, from, to, mat, opts) => {
        for (const [side, sign] of sides) {
            const target = typeof g === 'function' ? g(side) : side + '_' + g;
            if (sign === 1) box(target, side + '_' + name, from, to, mat, opts);
            else { const [f, t, o] = flip(from, to, opts); box(target, side + '_' + name, f, t, mat, o); }
        }
    };
    // 좌우 대칭 그룹. 오른쪽 기준 원점·회전을 주면 왼쪽은 x와 y·z 회전 부호를 뒤집습니다.
    const groupBoth = (name, origin, parent, rot) => {
        const r = rot || [0, 0, 0];
        for (const [side, sign] of sides) {
            group(side + '_' + name, [origin[0] * sign, origin[1], origin[2]],
                typeof parent === 'function' ? parent(side) : parent, sign === 1 ? r : [r[0], -r[1], -r[2]]);
        }
    };

    // ---------------------------------------------------------------- head
    // 머리: 위쪽 머리통(눈까지)과 그 아래 네모난 아래턱으로 나눕니다(바닐라 머리 8×8을 6+2로 나눈 것).
    // 얼굴은 바닐라 스킨처럼 한 칸 한 픽셀로 머리통·아래턱 앞면에 바로 그립니다.
    // 눈은 눈꼬리부터 속눈썹 1×2 · 흰자 1×2 · 눈동자 1×2 순서입니다(흰자 2×2 중 안쪽 줄을 눈동자가 덮음).
    // h/H/d 머리카락, s/k 피부(밝음/그늘), L 속눈썹, W/w 흰자(위 그늘/아래), i/I 눈동자(위/아래).
    // 앞머리 바로 밑 줄과 턱 가장자리·아랫줄을 한 단 어둡게 칠해 얼굴에 입체감을 줍니다.
    const HEAD_KEY = {
        h: 'hair:2', H: 'hair:3', d: 'hair:1', s: 'skin:2', L: '#726f89', W: '#c0c0c2', w: 'white:2',
        b: 'hairShade:1', c: 'hairShade:0',
        i: 'iris:1', I: 'iris:2', k: '#dba696',
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
    // 아래턱: 머리통과 같은 폭의 네모난 턱(바닐라 머리 8×8의 아래 두 줄). 가장자리 칸만 살짝 그늘지게 칠합니다.
    box('head', 'jaw', [-4, 24, -4], [4, 26, 4], 'skin', { px: { north: ['LwIssIwL', 'kssssssk'], key: HEAD_KEY } });
    // 눈썹: 3×1 판(한 칸 한 픽셀)을 눈꼬리 쪽이 살짝 올라가게 기울입니다.
    both(() => 'head', 'brow', [1, 26.9, -4.03], [4, 27.9, -4.03], 'hairShade',
        { plane: 'z', rot: [0, 0, 2.5], pivot: [2.5, 27.4, -4.03], px: { north: ['BBB'], south: null, key: { B: 'hairShade:1' } } });

    // 머리카락(ellen_fighter식): 정수리는 평평한 윗판과, 네 모서리를 대패로 민 듯 비스듬히 깎은 띠로 둥글게 만듭니다.
    // 윗면은 한 색으로 칠해 판과 띠가 한 면처럼 이어 보이게 하고, 가운데 가르마만 한 단 어둡게 둡니다.
    // 가닥은 깎인 띠 밑에서 한 덩어리 큐브로 내려와 이음매를 가립니다.
    const CROWN_UP = { up: () => 'hair:3' };
    const CAP_STRANDS = (u) => (u % 3 === 1 ? 'hair:1' : 'hair:2');
    box('head', 'hair_cap', [-4.3, 31, -4.3], [4.3, 32.4, 4.3], 'hair',
        { px: Object.assign({ north: CAP_STRANDS, east: CAP_STRANDS, west: CAP_STRANDS, south: CAP_STRANDS }, CROWN_UP) });
    box('head', 'hair_top', [-3.2, 32.4, -3.2], [3.2, 33.2, 3.2], 'hair', { px: { up: (u, v, w, h) => (u === Math.floor(w / 2) ? 'hair:2' : 'hair:3') } });
    // 깎은 띠: 윗판 가장자리(3.2, 33.2)에서 아랫판 가장자리(4.3, 32.4)로 내려가는 비탈(약 36도)을 덮는 얇은 판
    const CH = 36, CL = 1.5;
    both(() => 'head', 'hair_chamfer', [3.2, 32.5, -3.4], [3.2 + CL, 33.2, 3.4], 'hair', { rot: [0, 0, -CH], pivot: [3.2, 33.2, 0], px: CROWN_UP });
    box('head', 'hair_chamfer_front', [-3.4, 32.5, -3.2 - CL], [3.4, 33.2, -3.2], 'hair', { rot: [-CH, 0, 0], pivot: [0, 33.2, -3.2], px: CROWN_UP });
    box('head', 'hair_chamfer_back', [-3.4, 32.5, 3.2], [3.4, 33.2, 3.2 + CL], 'hair', { rot: [CH, 0, 0], pivot: [0, 33.2, 3.2], px: CROWN_UP });

    // 가닥은 모두 같은 규격(폭 SW × 두께 SD)이고 길이와 기울기만 다릅니다.
    const SW = 1.2, SD = 0.8, ROOT = 32.3;
    // 앞머리: 일곱 가닥. 가운데는 짧아 눈썹 위에서 끝나고 바깥은 볼까지 내려옵니다. [x, 길이, 기울기]
    group('bangs', [0, ROOT, -3.7], 'head', [16, 0, 0]);
    [[-3.6, 6.0, -6], [-2.4, 4.8, -3], [-1.2, 4.0, 5], [0, 3.7, -6], [1.2, 4.1, 4], [2.4, 4.7, -3], [3.6, 6.2, 7]]
        .forEach(([x, len, rz], i) => {
            box('bangs', 'bang_' + i, [x - SW / 2, ROOT - len, -3.7 - SD], [x + SW / 2, ROOT + 0.1, -3.7], i % 3 === 0 ? 'hairShade' : 'hair',
                { rot: [0, 0, rz], pivot: [x, ROOT + 0.1, -3.7 - SD / 2] });
        });
    // 이마 덮개: 앞머리 뒤로 이마에 붙은 가닥을 한 줄 깔아, 이마를 텍스처 대신 머리카락 큐브로 덮습니다. 끝은 눈썹 바로 위에서 들쭉날쭉합니다.
    [-3.5, -2.5, -1.5, -0.5, 0.5, 1.5, 2.5, 3.5].forEach((x, i) => {
        const end = 28.0 + [0.2, 0, 0.5, 0.1, 0.4, 0, 0.3, 0.1][i];
        box('head', 'fringe_' + i, [x - 0.6, end, -4.35], [x + 0.6, ROOT, -4.02], i % 2 ? 'hair' : 'hairShade');
    });
    // 옆머리: 귀 앞 두 가닥과 귀 뒤 세 가닥. 뿌리는 정수리 가장자리, 끝은 바깥으로 벌어집니다. [z, 길이, 기울기]
    [[-3.2, 9.8, 6], [-2.0, 9.2, 8], [0.5, 8.4, 10], [1.7, 8.8, 12], [2.9, 8.2, 11]].forEach(([z, len, tilt], i) => {
        both(() => 'head', 'side_hair_' + i, [3.6, ROOT - len, z - SW / 2], [3.6 + SD, ROOT + 0.1, z + SW / 2], i % 2 ? 'hairShade' : 'hair',
            { rot: [0, 0, tilt], pivot: [3.6, ROOT, z] });
    });
    // 네 귀퉁이: 45도로 돌린 가닥이 머리 모서리를 둥글게 감쌉니다.
    for (const [sx, sz, len] of [[1, 1, 8.4], [-1, 1, 8.4], [1, -1, 8.0], [-1, -1, 8.0]]) {
        const cx = 4.25 * sx, cz = 4.25 * sz;
        box('head', 'corner_hair_' + (sx > 0 ? 'r' : 'l') + (sz > 0 ? 'b' : 'f'), [cx - SW / 2, 32.2 - len, cz - SD / 2], [cx + SW / 2, 32.3, cz + SD / 2],
            'hair', { rot: [0, 45 * sx * sz, 0], pivot: [cx, 32.2, cz] });
    }
    // 긴 뒷머리(애니메이션 뼈 hair_back, 정수리에서 흔들림): 정수리 뒤 가장자리에서 시작한 일곱 가닥이 등까지 내려옵니다.
    // 끝 길이를 조금씩 달리해 들쭉날쭉하게 하고, 그 위 이음매마다 짧은 가닥 여섯 개를 한 겹 더 얹어 뒤통수를 채웁니다.
    [-3.6, -2.4, -1.2, 0, 1.2, 2.4, 3.6].forEach((x, i) => {
        const len = 14.6 + [0, 0.9, 0.3, 1.2, 0.4, 0.8, 0][i];
        box('hair_back', 'long_hair_' + i, [x - SW / 2, ROOT - len, 3.6], [x + SW / 2, ROOT + 0.1, 3.6 + SD], i % 2 ? 'hair' : 'hairShade',
            { rot: [-7, 0, 0], pivot: [x, ROOT, 3.6] });
    });
    [-3.0, -1.8, -0.6, 0.6, 1.8, 3.0].forEach((x, i) => {
        const len = 7.2 + (i % 2) * 0.5;
        box('head', 'back_hair_' + i, [x - SW / 2, ROOT - len, 3.9], [x + SW / 2, ROOT + 0.1, 3.9 + SD], 'hair',
            { rot: [-11, 0, 0], pivot: [x, ROOT, 3.9] });
    });
    // 뾰족귀: 옆머리 뒤에서 뒤·위로 뻗는 판
    groupBoth('ear', [4.4, 27.8, -0.5], 'head', [0, -25, 20]);
    both('ear', 'ear', [4.4, 26.6, -0.5], [9.4, 29.6, -0.5], 'skin', {
        plane: 'z',
        px: { north: ['ss...', '.ssss', '..kss'], key: { s: 'skin:2', k: 'skin:1' } },
    });

    // ---------------------------------------------------------------- torso
    // 몸통: 가슴은 넓고 허리는 조금 좁은 소매 없는 코르셋 조끼. 가운데 붉은 끈이 X자로 엇갈립니다.
    // remilia처럼 허리를 가슴의 3분의 2 정도로 조입니다(가슴 8 → 허리 5.2).
    // 가슴과 허리 사이, 허리와 골반 사이는 비스듬히 기운 판을 옆·뒤에 덧대 계단 없이 이어지게 합니다.
    box('torso', 'chest', [-4, 20.2, -2], [4, 24, 2], 'bodice');
    box('torso', 'waist', [-2.6, 15.4, -1.55], [2.6, 20.4, 1.55], 'bodice');
    // 가슴: 좌우 두 덩이. 몸통(가운데)과 윗면 경사판·아래 둥근 받침으로 모양을 잡고 가운데 골을 남깁니다.
    // 가슴(블록벤치에서 직접 다듬은 모양): 좌우 덩이를 가운데로 모으고 앞으로 반 칸 내밀어, 세 축으로 비스듬히
    // 돌려 모서리가 앞·아래를 향하게 합니다. 가운데에는 얇은 판을 세워 골을 채웁니다. 그룹은 y만 45도 돕니다.
    // BIL은 큐브 회전을 한 축만 살리므로, 세 축 회전은 그룹에 합쳐 두고 큐브는 돌리지 않습니다(블록벤치에서 보이는 모양 그대로).
    group('right_bust', [1.75, 20.7, -2.1], 'torso', InvasionBB.composeEuler([0, 45, 0], [-11.22402, 36.98934, -12.41085]));
    box('right_bust', 'right_bust', [-0.25, 19.45, -3.85], [2, 21.7, -0.85], 'bodice');
    group('left_bust', [-1.75, 20.7, -2.1], 'torso', InvasionBB.composeEuler([0, -45, 0], [-11.57942, -36.9486, 12.46765]));
    box('left_bust', 'left_bust', [-2, 19.45, -3.85], [0.5, 21.7, -0.85], 'bodice');
    box('torso', 'bust_centre', [-0.125, 19.56492, -2.55185], [0.125, 21.71492, 0.44815], 'bodice',
        { rot: [-9.5, 0, 0], pivot: [0, 20.83992, -1.05185] });
    // 옆구리: 가슴 가장자리(x 4, y 20.4)에서 허리(x 2.7, y 17.4)로 좁아지는 선
    both(() => 'torso', 'flank_upper', [3.4, 17.4, -1.7], [4.0, 20.4, 1.7], 'bodice', { rot: [0, 0, -25], pivot: [4.0, 20.4, 0] });
    // 골반 위: 허리(x 2.7, y 18)에서 허리띠(x 3.25, y 15.9)로 다시 벌어지는 선
    both(() => 'torso', 'flank_lower', [2.1, 15.9, -1.5], [2.7, 18.0, 1.5], 'bodice', { rot: [0, 0, 15], pivot: [2.7, 18.0, 0] });
    // 등: 가슴 뒷면(z 2)에서 허리 뒷면(z 1.55)으로
    box('torso', 'back_taper', [-3.2, 17.6, 1.3], [3.2, 20.4, 1.95], 'bodice', { rot: [9, 0, 0], pivot: [0, 20.4, 1.95] });
    box('torso', 'lace_gap', [-0.3, 15.9, -1.62], [0.3, 19.6, -1.5], 'dark');
    for (const [i, y] of [[0, 16.3], [1, 17.4], [2, 18.5]]) {
        box('torso', 'lace_a' + i, [-0.8, y, -1.73], [0.8, y + 0.3, -1.59], 'scarf', { rot: [0, 0, 30], pivot: [0, y + 0.15, -1.65] });
        box('torso', 'lace_b' + i, [-0.8, y, -1.75], [0.8, y + 0.3, -1.61], 'scarf', { rot: [0, 0, -30], pivot: [0, y + 0.15, -1.67] });
    }
    // 스카프: 목만 감싸고, 오른쪽 앞에 짧은 매듭 끝, 뒤로 긴 꼬리 두 가닥은 뒷머리 안쪽으로 흘러 끝만 머리카락 밑으로 나옵니다.
    box('torso', 'scarf', [-4.3, 21.6, -2.6], [4.3, 24.3, 2.6], 'scarf', { px: { up: null } });
    box('torso', 'scarf_fold', [-4.4, 21.4, -2.75], [4.4, 22.1, 2.75], 'scarf', { px: { up: null } });
    box('torso', 'scarf_knot', [2.4, 21.8, -3.0], [3.9, 23.6, -2.3], 'scarf');
    box('torso', 'scarf_end', [2.9, 19.2, -2.95], [3.9, 22.0, -2.55], 'scarf', { rot: [0, 0, 8], pivot: [3.4, 22.0, -3.75] });
    box('torso', 'scarf_back', [-2, 21.4, 2.5], [2, 24.3, 4.2], 'scarf');
    box('scarf_tail', 'scarf_tail_a', [0, 12.5, 3.6], [2, 23, 4.2], 'scarf', { rot: [-6, 0, 0], pivot: [1, 23, 3.9] });
    box('scarf_tail', 'scarf_tail_b', [-2, 14.5, 3.6], [0, 23, 4.2], 'scarf', { rot: [-5, 0, 0], pivot: [-1, 23, 3.9] });

    // ---------------------------------------------------------------- hips & skirt
    // 골반과 허리띠는 모서리를 깎은 팔각형(넓은 판 + 깊은 판 두 장)이라 치마가 둥글게 감쌀 수 있습니다.
    box('body', 'hips', [-3.15, 12, -1.1], [3.15, 15.6, 1.1], 'tights');
    box('body', 'hips_core', [-2.2, 12, -1.85], [2.2, 15.6, 1.85], 'tights');
    box('torso', 'belt', [-3.25, 15, -1.15], [3.25, 15.9, 1.15], 'leather');
    box('torso', 'belt_core', [-2.25, 15, -1.95], [2.25, 15.9, 1.95], 'leather');
    box('torso', 'buckle', [-0.6, 14.9, -2.15], [0.6, 16.0, -1.95], 'silver');
    both(() => 'torso', 'pouch', [2.3, 14.1, -2.2], [3.3, 15.6, -1.4], 'leather', { rot: [0, -30, 0], pivot: [2.8, 15, -1.8] });
    // 주름치마(cirno식): 허리띠 팔각형에 맞춘 여덟 장을 모두 같은 각도로 벌립니다.
    // 벌리면 아랫단이 넓어지므로, 판마다 양끝에 좁은 조각을 윗모서리 기준으로 바깥으로 살짝 돌려 틈을 메웁니다.
    // [반지름(판까지 거리), 윗변 폭]: 앞·뒤 / 대각 / 옆
    const FLARE = 24, LEN = 5, TOP = 15;
    const SPEC = { straight: [2.0, 4.95], diag: [3.2, 1.3], flank: [3.35, 2.35] };
    const spread = Math.atan((LEN * Math.sin(FLARE * Math.PI / 180) * Math.tan(Math.PI / 8)) / LEN) * 180 / Math.PI;
    for (let k = 0; k < 8; k++) {
        const kind = k % 4 === 0 ? 'straight' : k % 4 === 2 ? 'flank' : 'diag';
        const [r, w] = SPEC[kind];
        const g = 'skirt_' + k;
        group(g, [0, TOP, 0], 'body', [0, k * 45, 0]);
        group(g + '_flare', [0, TOP, -r], g, [FLARE, 0, 0]);
        box(g + '_flare', g, [-w / 2, TOP - LEN, -r - 0.3], [w / 2, TOP, -r], 'skirt');
        for (const s of [-1, 1]) {
            const x = s * (w / 2);
            const from = s < 0 ? [x, TOP - LEN, -r - 0.27] : [x - 0.9, TOP - LEN, -r - 0.27];
            const to = s < 0 ? [x + 0.9, TOP, -r - 0.03] : [x, TOP, -r - 0.03];
            box(g + '_flare', g + (s < 0 ? '_fill_l' : '_fill_r'), from, to, 'skirt', { rot: [0, 0, s * spread], pivot: [x, TOP, -r] });
        }
    }

    // ---------------------------------------------------------------- arms
    // 윗팔은 맨살에 가죽 팔찌, 아래팔은 은테 두른 가죽 토시, 손은 장갑.
    // 맨팔은 가늘게, 토시와 장갑은 그보다 한 겹 두껍게 감쌉니다.
    both('arm', 'upper_arm', [4.1, 18, -1.1], [6.3, 24, 1.1], 'skin');
    both('arm', 'arm_band', [4.0, 20.8, -1.2], [6.4, 21.4, 1.2], 'leather');
    both('forearm', 'forearm', [3.9, 13, -1.35], [6.5, 18.2, 1.35], 'leather');
    both('forearm', 'bracer_trim', [3.8, 17.2, -1.5], [6.6, 17.8, 1.5], 'silver');
    both('hand', 'hand', [4.0, 10.8, -1.25], [6.4, 13.1, 1.25], 'boot');

    // ---------------------------------------------------------------- legs
    both('leg', 'thigh', [0.3, 6, -1.7], [3.3, 12.2, 1.7], 'tights');
    both('shin', 'shin', [0.4, 0.5, -1.6], [3.2, 6.2, 1.6], 'boot');
    both('shin', 'boot_cuff', [0.2, 5, -1.85], [3.4, 6.6, 1.85], 'boot');
    both('shin', 'boot_strap', [0.3, 2.6, -1.75], [3.3, 3.1, 1.75], 'silver');
    both('shin', 'toe', [0.4, 0.3, -2.8], [3.2, 1.9, -1.5], 'boot');
    both('shin', 'sole', [0.3, 0, -2.9], [3.3, 0.4, 1.7], 'dark');
    // 오른쪽 허벅지에 예비 단검 칼집
    box('right_leg', 'thigh_strap', [0.2, 8.2, -1.85], [3.4, 8.8, 1.85], 'leather', { px: { up: null, down: null } });
    box('right_leg', 'sheath', [3.3, 7.2, -0.8], [4.0, 11.2, 0.8], 'leather');

    // ---------------------------------------------------------------- daggers
    InvasionParts.elfDagger(B, 'right_dagger', [5.2, 11.9, 0], { edgeBack: true });
    InvasionParts.elfDagger(B, 'left_dagger', [-5.2, 11.9, 0], { edgeBack: true });
    Object.assign(M, InvasionParts.elfDaggerMaterials);

    InvasionBB.paintPixels(B, M, 'elf_assassin');
    if (InvasionParts.elfAnimations) InvasionParts.elfAnimations(B);
    return { cubes: B.cubes.length, groups: Object.keys(B.groups).length };
})();
