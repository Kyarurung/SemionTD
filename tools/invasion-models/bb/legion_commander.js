// 군단장. 워든만 한 체급(키 약 46): 넓고 두꺼운 흉갑, 옆으로 길게 뻗어 바깥 끝이 뾰족한 견갑(뿔·가시 없음),
// 가로 틈 투구와 붉은 깃 볏, 등 뒤 붉은 망토. 오른손에 소드스태프, 왼팔에 탑 방패.
// 모델은 자유롭게 큐브로 짜고 텍스처는 바닐라식(paintPixels)입니다. Run after common.js and commander_arms.js.
(function () {
    const B = InvasionBB.makeBuilder('legion_commander');
    const { group, box } = B;

    const M = {
        steel: { c: ['#2a2d33', '#454a53', '#676e79', '#959eaa'], rim: true },
        steelDark: { c: ['#1c1e23', '#2e3238', '#43484f', '#5f666f'] },
        gold: { c: ['#6b4a12', '#9a6c1c', '#c9962c', '#f0c75a'] },
        red: { c: ['#4a0f14', '#72161d', '#9a2229', '#c43a3a'], lines: 3 },
        leather: { c: ['#2e1a0e', '#4a2c18', '#653e22', '#855632'], rim: true },
        chain: { c: ['#26282c', '#3c4046', '#555a62', '#737982'], pleats: true },
        dark: { c: ['#0b0a0c', '#141216', '#1d1a20', '#27232b'] },
        glow: { c: ['#7a1a08', '#c0360e', '#f2641c', '#ffb050'] },
    };

    // ---------------------------------------------------------------- skeleton
    group('root', [0, 0, 0]);
    group('body', [0, 14, 0], 'root');
    group('torso', [0, 19, 0], 'body');
    group('head', [0, 35, -1], 'torso');
    group('cape', [0, 34, 5.8], 'torso');
    for (const [side, sign] of [['right', 1], ['left', -1]]) {
        group(side + '_arm', [12.5 * sign, 32, 0], 'torso');
        group(side + '_forearm', [12.5 * sign, 21.5, 0], side + '_arm');
        group(side + '_hand', [12.5 * sign, 11.5, 0], side + '_forearm');
        group(side + '_leg', [4.2 * sign, 14, 0], 'body');
        group(side + '_shin', [4.2 * sign, 7.5, 0], side + '_leg');
        group(side + '_foot', [4.2 * sign, 1.5, 0], side + '_shin');
    }
    // 소드스태프: 기본 자세에서는 자루가 앞으로 눕고, 애니메이션에서 아래팔을 앞으로 90도 들면 자루가 곧게 서서
    // 물미가 땅을 짚습니다(기수처럼). 자루는 주먹에서만 팔과 교차해 팔뚝을 뚫지 않습니다.
    group('sword_staff', [12.5, 9.5, 0], 'right_hand', [-90, 0, 0]);
    // 탑 방패(블록벤치에서 맞춘 위치): 손잡이 끈이 왼손 바로 위 팔뚝에 오고, 기본 자세에서는 앞뒤로 눕습니다.
    // 소드스태프처럼 애니메이션에서 아래팔을 앞으로 들면 방패가 곧게 섭니다.
    group('shield', [-16.5, 16, 0], 'left_forearm', [-90, 0, 0]);

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
    // 투구: 검게 그을린 강철. 성난 V자 눈두덩 아래로 비스듬한 눈 틈이 붉게 빛나고,
    // 얼굴 가운데 능선이 앞으로 뾰족하게 튀어나옵니다. 아래는 세로 숨구멍 창살과 송곳니처럼 들쭉날쭉한 턱 가장자리.
    box('head', 'helm', [-4.5, 35, -6], [4.5, 44, 3], 'steelDark', {
        px: { north: (u, v, w, h) => (v >= 4 && v <= 5 ? 'dark:0' : undefined) },   // 눈 틈 뒤의 어둠
    });
    box('head', 'helm_ridge', [-0.7, 43.5, -6.3], [0.7, 44.6, 3.2], 'gold');
    for (const [side, sign] of sides) {
        const bx = (name, from, to, mat, opts) => {
            if (sign === 1) box('head', side + '_' + name, from, to, mat, opts);
            else { const [f, t, o] = flip(from, to, opts); box('head', side + '_' + name, f, t, mat, o); }
        };
        // 눈 틈: 눈꼬리 쪽이 올라간 가는 붉은 빛
        // 이름 끝의 _glow로 게임에서 스스로 빛납니다(SemionBilModelCache).
        bx('eye_slit_glow', [0.7, 39.5, -6.15], [3.7, 40.1, -5.95], 'glow', { rot: [0, 0, 14], pivot: [0.7, 39.8, -6.05], px: { north: () => 'glow:2' } });
        // 눈두덩: 안쪽이 낮은 V자로 눈 틈 위를 덮습니다.
        bx('brow', [0.1, 40.3, -6.9], [4.7, 41.4, -5.8], 'steel', { rot: [0, 0, 18], pivot: [0.1, 40.85, -6.35] });
        // 볼 가리개: 앞 끝이 바깥으로 벌어집니다.
        bx('cheek', [4.2, 35, -6.2], [5.2, 39.8, -1], 'steel', { rot: [0, -15, 0], pivot: [4.7, 37.4, -1] });
    }
    // 얼굴 가운데 능선: 45도 돌린 막대로 앞으로 뾰족하게
    box('head', 'prow', [-0.65, 35.4, -6.85], [0.65, 43.2, -5.55], 'steel', { rot: [0, 45, 0], pivot: [0, 39.3, -6.2] });
    // 턱 가리개: 세로 숨구멍 창살과 송곳니처럼 들쭉날쭉한 아래 가장자리
    box('head', 'faceplate', [-3.8, 35, -6.6], [3.8, 38.6, -5.9], 'steel', {
        px: {
            north: (u, v, w, h) => {
                if (v === h - 1 && u % 2 === 1) return null;
                if (v < h - 1 && u % 2 === 1 && u > 0 && u < w - 1) return 'dark:0';
                return undefined;
            },
        },
    });
    box('head', 'plume', [-0.8, 44.3, -4.5], [0.8, 47, 4], 'red', { rot: [-10, 0, 0], pivot: [0, 44.3, 0] });
    box('head', 'plume_tail', [-0.7, 38, 3.5], [0.7, 45, 5], 'red', { rot: [-18, 0, 0], pivot: [0, 45, 4] });
    box('head', 'gorget', [-5, 33.5, -5], [5, 36, 3.5], 'steelDark');

    // ---------------------------------------------------------------- torso
    // 흉갑: 넓은 가슴판 위에 가운데 능선과 금테, 배는 겹판
    const chestTrim = (u, v, w, h) => (v === 0 || u === Math.floor(w / 2)) ? 'gold:2' : undefined;
    box('torso', 'cuirass', [-9, 19, -5.5], [9, 35, 5.5], 'steel', { px: { north: chestTrim } });
    both(inG('torso'), 'breast', [0.2, 27, -6.6], [8.2, 34, -5.2], 'steel', { rot: [-8, 0, 0], pivot: [4.2, 34, -5.5] });
    for (let i = 0; i < 3; i++) {
        const y = 19.5 + i * 2.4;
        box('torso', 'fauld_' + i, [-8.2 + i * 0.3, y, -6.2], [8.2 - i * 0.3, y + 2.2, -5.3], 'steel');
    }
    box('torso', 'backplate', [-8.5, 20, 5.3], [8.5, 34, 6.2], 'steelDark');
    // 망토: 어깨 뒤에서 종아리까지 늘어지는 붉은 천(애니메이션 뼈)
    box('cape', 'cape', [-8.5, 6, 5.8], [8.5, 34, 6.6], 'red', { rot: [-6, 0, 0], pivot: [0, 34, 6.2] });
    box('cape', 'cape_clasp', [-9, 32.5, 5.4], [9, 34.5, 6.8], 'gold');

    // 허리: 가죽 띠와 금 버클, 사슬 치마와 넓적다리 판갑
    box('body', 'hips', [-7.5, 12, -4.5], [7.5, 19.2, 4.5], 'chain');
    box('body', 'belt', [-8, 17.5, -5], [8, 19.5, 5], 'leather');
    box('body', 'buckle', [-1.5, 17.2, -5.5], [1.5, 19.8, -4.9], 'gold');
    both(inG('body'), 'tasset', [1.2, 10, -5.6], [7.8, 17.8, -4.8], 'steel', { rot: [8, 0, 0], pivot: [4.5, 17.8, -5.2] });

    // ---------------------------------------------------------------- arms
    // 견갑: 어깨에서 옆으로 길게 뻗은 판 세 겹. 바깥으로 갈수록 좁아지고 끝은 비스듬히 잘려 뾰족합니다.
    for (const [side, sign] of sides) {
        const g = side + '_arm';
        const bx = (name, from, to, mat, opts) => {
            if (sign === 1) box(g, side + '_' + name, from, to, mat, opts);
            else { const [f, t, o] = flip(from, to, opts); box(g, side + '_' + name, f, t, mat, o); }
        };
        bx('pauldron_top', [8, 33, -5], [19, 35.5, 5], 'steel', { rot: [0, 0, -8], pivot: [8, 34, 0] });
        bx('pauldron_mid', [9, 30.8, -4.4], [20.5, 33.2, 4.4], 'steel', { rot: [0, 0, -12], pivot: [9, 32, 0] });
        bx('pauldron_low', [10, 28.8, -3.8], [21.5, 31, 3.8], 'steelDark', { rot: [0, 0, -16], pivot: [10, 30, 0] });
        bx('pauldron_trim', [8, 32.6, -5.2], [19.2, 33.2, 5.2], 'gold', { rot: [0, 0, -8], pivot: [8, 34, 0] });
        // 바깥 끝: 45도 돌린 판으로 뾰족하게 마무리
        bx('pauldron_tip', [17.5, 30.2, -4.2], [21.5, 34.2, 4.2], 'steel', { rot: [0, 0, 45], pivot: [19.5, 32.2, 0] });
    }
    both('arm', 'upper_arm', [9.5, 21, -3.2], [15.5, 32, 3.2], 'chain');
    both('arm', 'rerebrace', [9.3, 23, -3.5], [15.7, 28, 3.5], 'steel');
    both('forearm', 'vambrace', [9.6, 12, -3.3], [15.4, 21.8, 3.3], 'steel');
    both('forearm', 'vambrace_trim', [9.4, 20.5, -3.5], [15.6, 21.5, 3.5], 'gold');
    both('hand', 'gauntlet', [9.3, 7, -3.5], [15.7, 12, 3.5], 'steelDark', {
        px: { north: (u, v) => (v === 1 && u % 2 === 0 ? 'steel:2' : undefined) },
    });

    // ---------------------------------------------------------------- legs
    both('leg', 'cuisse', [1.3, 7, -3], [7.1, 14.2, 3], 'steel');
    both('shin', 'greave', [1.5, 1.5, -2.8], [6.9, 7.8, 2.8], 'steel');
    both('shin', 'poleyn', [1.4, 6.5, -3.4], [7, 8.8, -2.6], 'gold');
    both('foot', 'sabaton', [1.2, 0, -5], [7.2, 2.5, 3], 'steelDark');

    // ---------------------------------------------------------------- arms (weapons)
    InvasionParts.commanderSwordStaff(B, 'sword_staff', [12.5, 9.5, 0]);
    InvasionParts.commanderShield(B, 'shield', [-16.5, 16, 0]);
    Object.assign(M, InvasionParts.commanderArmsMaterials);

    InvasionBB.paintPixels(B, M, 'legion_commander');
    if (InvasionParts.commanderAnimations) InvasionParts.commanderAnimations(B);
    return { cubes: B.cubes.length, groups: Object.keys(B.groups).length };
})();
