// 군단장 무장: 소드스태프(긴 자루에 곧은 양날 칼날)와 탑 방패. 군단장 모델과 따로 다듬은 뒤 오른손·왼팔에 붙입니다.
//
// 소드스태프: 손잡이를 쥐는 점이 원점, 자루는 +y로 길게 서고 그 끝에 곧은 양날 칼날이 이어집니다.
//            곡면 대신 곧은 큐브로 짜서 날·홈·코등이가 또렷합니다.
// 방패: 팔에 거는 점이 원점, 앞면은 바깥(-x)을 향합니다. 가운데 판과 양옆 판을 몸 쪽으로 꺾어 둥근 탑 방패를 만듭니다.
// 텍스처는 바닐라식(paintPixels)입니다. 단독 확인: window.__buildStandaloneSwordStaff / __buildStandaloneShield.
(function (global) {
    const MATERIALS = {
        steel: { c: ['#2a2d33', '#454a53', '#676e79', '#959eaa'], rim: true },
        edge: { c: ['#6a717a', '#939ba4', '#c3cad2', '#eef2f5'] },
        gold: { c: ['#6b4a12', '#9a6c1c', '#c9962c', '#f0c75a'] },
        wood: { c: ['#2e1c10', '#472b18', '#613d23', '#7c5232'] },
        red: { c: ['#4a0f14', '#72161d', '#9a2229', '#c43a3a'] },
        leather: { c: ['#241509', '#3a2414', '#51341f', '#6b472b'] },
    };

    function commanderSwordStaff(B, group, grip) {
        const [gx, gy, gz] = grip;
        const at = (p) => [p[0] + gx, p[1] + gy, p[2] + gz];
        const box = (name, from, to, mat, opts) => {
            const o = Object.assign({}, opts || {});
            if (o.pivot) o.pivot = at(o.pivot);
            B.box(group, 'staff_' + name, at(from), at(to), 'arms.' + mat, o);
        };
        // 자루: 길이 50. 쥐는 곳 가죽, 중간 금 고리, 아래 끝 금 물미와 가시
        box('haft', [-0.6, -16, -0.6], [0.6, 33.5, 0.6], 'wood');
        box('grip', [-0.75, -3, -0.75], [0.75, 3, 0.75], 'leather');
        box('grip_ring', [-0.85, 7, -0.85], [0.85, 8, 0.85], 'gold');
        box('upper_grip', [-0.75, 27, -0.75], [0.75, 33, 0.75], 'leather');
        box('butt', [-0.9, -18, -0.9], [0.9, -16, 0.9], 'gold');
        box('butt_spike', [0, -21, -0.9], [0, -18, 0.9], 'gold', {
            plane: 'x', px: { east: ['.G.', '.G.', 'GGG'], key: { G: 'arms.gold:2' } },
        });
        // 코등이: 앞뒤로 넓은 금 가로대와 양끝 장식, 가운데 붉은 보석
        box('guard', [-0.9, 33.5, -3.8], [0.9, 35, 3.8], 'gold');
        box('guard_cap_front', [-1.1, 33.2, -4.6], [1.1, 35.3, -3.6], 'gold');
        box('guard_cap_back', [-1.1, 33.2, 3.6], [1.1, 35.3, 4.6], 'gold');
        box('gem', [-1.05, 33.8, -0.6], [1.05, 34.7, 0.6], 'red');
        // 곧은 양날 칼날: 두꺼운 가운데 몸체, 양쪽 밝은 날, 가운데 금 홈, 끝은 뾰족한 판
        box('blade', [-0.4, 35, -1.5], [0.4, 55, 1.5], 'steel');
        box('edge_front', [-0.25, 35, -2.1], [0.25, 55, -1.5], 'edge');
        box('edge_back', [-0.25, 35, 1.5], [0.25, 55, 2.1], 'edge');
        box('fuller', [-0.45, 36, -0.3], [0.45, 50, 0.3], 'gold');
        // 칼끝: 양날이 가운데로 모이는 대칭 삼각(폭 4). 가운데 줄은 몸체 색으로 이어 줍니다.
        box('tip', [0, 55, -2], [0, 60, 2], 'edge', {
            plane: 'x', px: { east: ['.EE.', '.EE.', 'ESSE', 'ESSE', 'ESSE'], key: { E: 'arms.edge:3', S: 'arms.steel:2' } },
        });
        // 코등이 아래 붉은 술
        box('tassel', [-0.6, 27, 0.8], [0.6, 33, 2], 'red', { rot: [-10, 0, 0], pivot: [0, 33, 1.4] });
    }

    function commanderShield(B, group, mount) {
        const [mx, my, mz] = mount;
        const at = (p) => [p[0] + mx, p[1] + my, p[2] + mz];
        const box = (name, from, to, mat, opts) => {
            const o = Object.assign({}, opts || {});
            if (o.pivot) o.pivot = at(o.pivot);
            B.box(group, 'shield_' + name, at(from), at(to), 'arms.' + mat, o);
        };
        // 크기: 높이 2H, 가운데 판 폭 2CW, 양옆 판 폭 SIDE(몸 쪽으로 BEND도 꺾음). 전체 약 17×28.
        const H = 14, CW = 4.5, SIDE = 4, BEND = 15;
        const X0 = -1.8, X1 = -0.6;    // 판 두께(바깥 -x)
        // 가운데 판: 금 십자 휘장
        const emblem = (u, v, w, h) => {
            const cx = (w - 1) / 2, cy = Math.round(h * 0.36);
            if (Math.abs(u - cx) < 1 && v > 3 && v < h - 4) return 'arms.gold:3';
            if (Math.abs(v - cy) < 1 && Math.abs(u - cx) < 3.5) return 'arms.gold:3';
            return undefined;
        };
        const E = CW + SIDE;           // 양옆 판 바깥 끝
        const back = { rot: [0, BEND, 0], pivot: [X1, 0, CW] };
        const front = { rot: [0, -BEND, 0], pivot: [X1, 0, -CW] };
        // 가운데 판은 양옆 판과의 이음매 틈이 보이지 않게 양쪽으로 0.25씩 넓혀 겹칩니다(블록벤치에서 맞춘 폭).
        box('center', [X0, -H, -CW - 0.25], [X1, H, CW + 0.25], 'red', { px: { west: emblem } });
        // 양옆 판: 이음매를 축으로 몸 쪽(+x)으로 꺾어 둥글게 감쌉니다.
        box('side_back', [X0, -H, CW - 0.1], [X1, H, E], 'red', back);
        box('side_front', [X0, -H, -E], [X1, H, -CW + 0.1], 'red', front);
        // 금테: 위·아래 띠와 양옆 바깥 모서리
        for (const [n, y0, y1] of [['rim_top', H - 1, H + 0.4], ['rim_bottom', -H - 0.4, -H + 1]]) {
            box(n, [X0 - 0.2, y0, -CW - 0.1], [X1 + 0.1, y1, CW + 0.1], 'gold');
            box(n + '_back', [X0 - 0.2, y0, CW - 0.2], [X1 + 0.1, y1, E + 0.1], 'gold', back);
            box(n + '_front', [X0 - 0.2, y0, -E - 0.1], [X1 + 0.1, y1, -CW + 0.2], 'gold', front);
        }
        box('rim_edge_back', [X0 - 0.2, -H, E - 0.7], [X1 + 0.1, H, E + 0.1], 'gold', back);
        box('rim_edge_front', [X0 - 0.2, -H, -E - 0.1], [X1 + 0.1, H, -E + 0.7], 'gold', front);
        // 가운데 방패 돌기와 팔 끈
        box('boss', [X0 - 0.9, -1.5, -1.5], [X0, 1.5, 1.5], 'steel');
        // 손잡이: 방패 안쪽에서 팔까지 이어지는 두꺼운 가죽 손잡이
        box('strap', [X1, -2.5, -2], [X1 + 3, 2.5, 2], 'leather');
    }

    const PREFIXED = Object.fromEntries(Object.entries(MATERIALS).map(([k, v]) => ['arms.' + k, v]));
    global.InvasionParts = Object.assign(global.InvasionParts || {}, {
        commanderSwordStaff, commanderShield, commanderArmsMaterials: PREFIXED,
    });

    // 단독 확인용 프로젝트
    if (global.__buildStandaloneSwordStaff) {
        const B = InvasionBB.makeBuilder('commander_sword_staff');
        B.group('sword_staff', [0, 0, 0]);
        commanderSwordStaff(B, 'sword_staff', [0, 0, 0]);
        InvasionBB.paintPixels(B, PREFIXED, 'commander_sword_staff');
        global.__standaloneResult = { cubes: B.cubes.length };
    }
    if (global.__buildStandaloneShield) {
        const B = InvasionBB.makeBuilder('commander_shield');
        B.group('shield', [0, 0, 0]);
        commanderShield(B, 'shield', [0, 0, 0]);
        InvasionBB.paintPixels(B, PREFIXED, 'commander_shield');
        global.__standaloneResult = { cubes: B.cubes.length };
    }
})(window);
