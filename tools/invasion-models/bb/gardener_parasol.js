// 정원사의 양산. 정원사 모델과 따로 다듬은 뒤 오른손에 붙입니다.
//
// 좌표는 손잡이를 쥐는 점이 원점이고 양산 머리(덮개)는 +y로 섭니다. 나무 자루 아래에 굽은 손잡이, 위에 분홍 덮개
// 여덟 장(가운데서 바깥으로 비스듬히 내려앉는 판, 흰 테)과 꼭지가 달립니다. 텍스처는 바닐라식(paintPixels)입니다.
(function (global) {
    const MATERIALS = {
        wood: { c: ['#4a2a18', '#6e4226', '#915a34', '#b57a4a'] },
        canopy: { c: ['#b04a78', '#dc6f9e', '#f59ac0', '#ffc8dc'], hem: 'frill' },
        frill: { c: ['#c8c0c8', '#e4dde4', '#f6f2f6', '#ffffff'] },
        tip: { c: ['#8a7a40', '#c0a850', '#e8d070', '#fff0a8'] },
    };

    const CANOPY_Y = 23;       // 덮개 가운데 높이(손잡이에서)
    const CANOPY_R = 7.2;      // 덮개 반지름
    const TILT = -22;          // 판이 바깥으로 내려앉는 각(음수가 아래로 처짐)

    function parasol(B, group, grip) {
        const [gx, gy, gz] = grip;
        const at = (p) => [p[0] + gx, p[1] + gy, p[2] + gz];
        const box = (g, name, from, to, mat, opts) => {
            const o = Object.assign({}, opts || {});
            if (o.pivot) o.pivot = at(o.pivot);
            B.box(g, 'parasol_' + name, at(from), at(to), 'parasol.' + mat, o);
        };
        // 자루와 굽은 손잡이(J자: 아래로 내려와 뒤로 휘어 올라감)
        box(group, 'shaft', [-0.4, -2.5, -0.4], [0.4, CANOPY_Y + 0.5, 0.4], 'wood');
        box(group, 'handle_down', [-0.5, -5, -0.5], [0.5, -2.2, 0.5], 'wood');
        box(group, 'handle_curve', [-0.5, -5.8, -0.2], [0.5, -4.8, 2.2], 'wood');
        box(group, 'handle_up', [-0.5, -5.2, 1.6], [0.5, -3.6, 2.6], 'wood');
        // 덮개: 판 여덟 장. 판마다 그룹 하나(가운데를 축으로 y 회전 + 바깥으로 기울임)라 BIL에서도 회전이 삽니다.
        const width = 2 * CANOPY_R * Math.sin(Math.PI / 8) + 0.6;
        for (let k = 0; k < 8; k++) {
            const name = 'canopy_' + k;
            B.group(name, at([0, CANOPY_Y, 0]), group, [TILT, k * 45, 0]);
            box(name, 'panel_' + k, [-width / 2, CANOPY_Y - 0.1, -CANOPY_R], [width / 2, CANOPY_Y + 0.15, 0], 'canopy',
                { px: { up: (u, v, w, h) => (v === 0 ? 'parasol.frill:3' : (u === 0 || u === w - 1 ? 'parasol.canopy:1' : undefined)),
                        down: (u, v) => (v === 0 ? 'parasol.frill:1' : 'parasol.canopy:0') } });
        }
        box(group, 'crown', [-1.1, CANOPY_Y - 0.2, -1.1], [1.1, CANOPY_Y + 0.9, 1.1], 'canopy');
        box(group, 'tip', [-0.35, CANOPY_Y + 0.9, -0.35], [0.35, CANOPY_Y + 2.8, 0.35], 'tip');
    }

    const PREFIXED = Object.fromEntries(Object.entries(MATERIALS).map(([k, v]) => ['parasol.' + k, v]));
    PREFIXED['parasol.canopy'] = Object.assign({}, MATERIALS.canopy, { hem: 'parasol.frill' });
    global.InvasionParts = Object.assign(global.InvasionParts || {}, { gardenerParasol: parasol, gardenerParasolMaterials: PREFIXED });
})(window);
