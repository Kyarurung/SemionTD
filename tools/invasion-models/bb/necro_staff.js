// 강령술사의 해골 지팡이. 강령술사 모델과 따로 다듬은 뒤 오른손에 붙입니다.
//
// 좌표는 손잡이를 쥐는 점이 원점이고 지팡이 머리는 +y로 섭니다. 검게 그을린 나무 자루에 뼈마디를 끼우고,
// 끝에는 뼈 갈퀴 두 개가 해골을 떠받치며 해골 위로 초록 영혼불이 탑니다. 텍스처는 바닐라식(paintPixels)입니다.
// 단독 확인: window.__buildStandaloneNecroStaff.
(function (global) {
    const MATERIALS = {
        wood: { c: ['#1c1712', '#2a221a', '#3a3024', '#4c3f30'] },
        bone: { c: ['#8a8470', '#b0aa92', '#d2ccb2', '#ece8d4'], rim: true },
        wrap: { c: ['#141a16', '#1f2822', '#2c3830', '#3c4a40'] },
        soul: { c: ['#1f6a2a', '#3aa84a', '#6ee87a', '#c8ffcc'], noFade: true },
        dark: { c: ['#070808', '#0e100f', '#161917', '#1e221f'] },
    };

    function necroStaff(B, group, grip) {
        const [gx, gy, gz] = grip;
        const at = (p) => [p[0] + gx, p[1] + gy, p[2] + gz];
        const box = (name, from, to, mat, opts) => {
            const o = Object.assign({}, opts || {});
            if (o.pivot) o.pivot = at(o.pivot);
            B.box(group, 'staff_' + name, at(from), at(to), 'nstaff.' + mat, o);
        };
        // 자루: 그을린 나무, 헝겊 손잡이, 뼈마디 셋, 아래 끝 뼈 물미
        box('shaft', [-0.55, -14, -0.55], [0.55, 21, 0.55], 'wood');
        box('grip', [-0.7, -3, -0.7], [0.7, 3, 0.7], 'wrap', { px: { north: (u, v) => (v % 2 ? 'nstaff.wrap:1' : undefined) } });
        for (const [i, y] of [[0, -9], [1, 6], [2, 13]]) box('knob_' + i, [-0.8, y, -0.8], [0.8, y + 1, 0.8], 'bone');
        box('ferrule', [-0.7, -15.2, -0.7], [0.7, -14, 0.7], 'bone');
        // 뼈 갈퀴: 양옆에서 바깥으로 벌어졌다가 해골 쪽으로 휩니다.
        box('socket', [-1, 20, -1], [1, 21.4, 1], 'bone');
        for (const [s, name] of [[1, 'r'], [-1, 'l']]) {
            [[1.3, 21.8, -30], [2.2, 24, 0], [1.8, 26.1, 30]].forEach(([x, y, a], i) => {
                const cx = x * s;
                box('prong_' + name + i, [cx - 0.4, y - 1.3, -0.4], [cx + 0.4, y + 1.3, 0.4], 'bone', { rot: [0, 0, a * s], pivot: [cx, y, 0] });
            });
        }
        // 해골: 초록으로 빛나는 눈구멍, 이 빠진 아래턱
        box('skull', [-1.6, 22, -1.8], [1.6, 25, 1.4], 'bone', {
            px: { north: ['bbb', 'GbG', 'bDb'], key: { b: 'nstaff.bone', G: 'nstaff.soul:3', D: 'nstaff.dark:0' } },
        });
        box('skull_jaw', [-1.2, 21.3, -1.7], [1.2, 22.1, 1.0], 'bone', { px: { north: (u) => (u === 1 ? 'nstaff.dark:1' : undefined) } });
        // 영혼불: 45도 돌린 초록 큐브 둘을 겹쳐 흔들리는 불꽃처럼
        box('flame', [-0.9, 25, -0.9], [0.9, 27.4, 0.9], 'soul', { rot: [0, 45, 0], pivot: [0, 26, 0] });
        box('flame_tip', [-0.5, 26.8, -0.5], [0.5, 28.8, 0.5], 'soul', { rot: [0, 45, 0], pivot: [0, 27.5, 0], px: { north: () => 'nstaff.soul:3', east: () => 'nstaff.soul:3' } });
        box('flame_side', [0.4, 25.6, -0.3], [1.0, 26.8, 0.3], 'soul', { rot: [0, 0, -20], pivot: [0.7, 25.6, 0] });
    }

    const PREFIXED = Object.fromEntries(Object.entries(MATERIALS).map(([k, v]) => ['nstaff.' + k, v]));
    global.InvasionParts = Object.assign(global.InvasionParts || {}, { necroStaff, necroStaffMaterials: PREFIXED });

    if (global.__buildStandaloneNecroStaff) {
        const B = InvasionBB.makeBuilder('necro_staff');
        B.group('staff', [0, 0, 0]);
        necroStaff(B, 'staff', [0, 0, 0]);
        InvasionBB.paintPixels(B, PREFIXED, 'necro_staff');
        global.__standaloneResult = { cubes: B.cubes.length };
    }
})(window);
