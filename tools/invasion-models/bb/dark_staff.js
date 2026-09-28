// 암흑 신관의 지팡이. 신관 모델과 따로 다듬은 뒤 오른손에 붙입니다.
//
// 좌표는 손잡이를 쥐는 점이 원점이고 지팡이 머리는 +y로 섭니다. 검은 자루 끝에 은 초승달이 분홍 수정을 떠받칩니다.
// 곡선은 곧은 막대 여러 개를 조금씩 기울여 이어 붙입니다. 텍스처는 바닐라식(paintPixels)입니다.
// 단독 확인: window.__buildStandaloneDarkStaff.
(function (global) {
    const MATERIALS = {
        wood: { c: ['#0b0a0e', '#16131b', '#221d29', '#312a3a'] },
        silver: { c: ['#565b70', '#8a91a5', '#bcc3d2', '#eef2f8'], rim: true },
        crystal: { c: ['#7a1f55', '#c2408a', '#f070b8', '#ffc4e6'] },
        wrap: { c: ['#2a0f22', '#431a37', '#63284f', '#86386c'] },
    };

    function darkStaff(B, group, grip) {
        const [gx, gy, gz] = grip;
        const at = (p) => [p[0] + gx, p[1] + gy, p[2] + gz];
        const box = (name, from, to, mat, opts) => {
            const o = Object.assign({}, opts || {});
            if (o.pivot) o.pivot = at(o.pivot);
            B.box(group, 'staff_' + name, at(from), at(to), 'staff.' + mat, o);
        };
        // 자루: 검은 나무, 손잡이는 자줏빛 천, 은 고리 두 줄, 아래 끝 은 물미
        box('shaft', [-0.55, -14, -0.55], [0.55, 22, 0.55], 'wood');
        box('grip', [-0.7, -3, -0.7], [0.7, 3, 0.7], 'wrap');
        box('ring_a', [-0.75, 8, -0.75], [0.75, 8.8, 0.75], 'silver');
        box('ring_b', [-0.75, 20.5, -0.75], [0.75, 21.5, 0.75], 'silver');
        box('ferrule', [-0.75, -15.5, -0.75], [0.75, -14, 0.75], 'silver');
        // 머리: 받침 위 초승달(양쪽 세 마디씩 바깥으로 벌어졌다 안으로 휨)
        box('socket', [-1, 21.5, -1], [1, 23, 1], 'silver');
        // [x, y, 기울기]: 마디를 조금씩 겹쳐 이음매가 벌어지지 않게 합니다.
        const arc = [[1.25, 23.8, -25], [2.2, 26.3, -4], [2.0, 28.8, 22]];
        for (const [s, name] of [[1, 'r'], [-1, 'l']]) {
            arc.forEach(([x, y, a], i) => {
                const cx = x * s;
                box('horn_' + name + i, [cx - 0.45, y - 1.9, -0.5], [cx + 0.45, y + 1.9, 0.5], 'silver',
                    { rot: [0, 0, a * s], pivot: [cx, y, 0] });
            });
        }
        // 분홍 수정: 45도 돌린 길쭉한 큐브 두 개를 겹쳐 뾰족한 결정처럼
        box('crystal', [-0.9, 24, -0.9], [0.9, 29.5, 0.9], 'crystal', { rot: [0, 45, 0], pivot: [0, 26.7, 0] });
        box('crystal_core', [-0.6, 23.4, -0.6], [0.6, 30.3, 0.6], 'crystal', { px: { up: () => 'staff.crystal:3' } });
    }

    const PREFIXED = Object.fromEntries(Object.entries(MATERIALS).map(([k, v]) => ['staff.' + k, v]));
    global.InvasionParts = Object.assign(global.InvasionParts || {}, { darkStaff, darkStaffMaterials: PREFIXED });

    if (global.__buildStandaloneDarkStaff) {
        const B = InvasionBB.makeBuilder('dark_staff');
        B.group('staff', [0, 0, 0]);
        darkStaff(B, 'staff', [0, 0, 0]);
        InvasionBB.paintPixels(B, PREFIXED, 'dark_staff');
        global.__standaloneResult = { cubes: B.cubes.length };
    }
})(window);
