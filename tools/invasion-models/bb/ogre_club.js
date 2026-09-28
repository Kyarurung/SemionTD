// 오우거 투사의 가시 몽둥이. 오우거 모델과 따로 다듬은 뒤 오른손에 붙입니다.
//
// 좌표는 손잡이를 쥐는 점이 원점이고 몽둥이 머리는 +y로 섭니다. 위로 갈수록 굵어지는 통나무에 쇠띠 두 줄과 쇠 가시를 박았습니다.
// 텍스처는 바닐라식(paintPixels)입니다. 단독 확인: window.__buildStandaloneOgreClub.
(function (global) {
    const MATERIALS = {
        wood: { c: ['#3a2616', '#553a22', '#6e4c2e', '#8a6240'], lines: 3 },
        grip: { c: ['#2e1e14', '#46301f', '#5e412a', '#785638'] },
        iron: { c: ['#2a2d33', '#454a53', '#676e79', '#959eaa'], rim: true },
    };

    function ogreClub(B, group, grip) {
        const [gx, gy, gz] = grip;
        const at = (p) => [p[0] + gx, p[1] + gy, p[2] + gz];
        const box = (name, from, to, mat, opts) => {
            const o = Object.assign({}, opts || {});
            if (o.pivot) o.pivot = at(o.pivot);
            B.box(group, 'club_' + name, at(from), at(to), 'club.' + mat, o);
        };
        // 자루와 가죽 손잡이, 아래 끝 쇠 마개
        box('handle', [-1.3, -6, -1.3], [1.3, 12, 1.3], 'wood');
        box('wrap', [-1.5, -4, -1.5], [1.5, 4, 1.5], 'grip', { px: { north: (u, v) => (v % 2 === 0 ? 'club.grip:1' : undefined) } });
        box('pommel', [-1.8, -7.5, -1.8], [1.8, -6, 1.8], 'iron');
        // 머리: 세 마디로 위로 굵어지고 윗면을 쇠로 덮습니다.
        box('head_a', [-2.4, 10, -2.4], [2.4, 16, 2.4], 'wood');
        box('head_b', [-3.2, 15, -3.2], [3.2, 23, 3.2], 'wood');
        box('head_c', [-3.8, 22, -3.8], [3.8, 29.5, 3.8], 'wood');
        box('cap', [-3.3, 29.5, -3.3], [3.3, 30.7, 3.3], 'iron');
        box('band_a', [-3.45, 17.5, -3.45], [3.45, 18.8, 3.45], 'iron');
        box('band_b', [-4.05, 24.5, -4.05], [4.05, 25.8, 4.05], 'iron');
        // 쇠 가시: 네 면에서 두 줄씩 튀어나오고, 긴 축으로 45도 돌려 마름모 단면을 만듭니다.
        const spikes = [[20.5, 3.2, 1.6], [27.5, 3.8, 2.0]];
        spikes.forEach(([y, r, len], row) => {
            const o = row % 2 ? 1.2 : -1.2;
            box('spike_e' + row, [r - 0.1, y - 0.6 + o, -0.6], [r + len, y + 0.6 + o, 0.6], 'iron', { rot: [45, 0, 0], pivot: [r, y + o, 0] });
            box('spike_w' + row, [-r - len, y - 0.6 - o, -0.6], [-r + 0.1, y + 0.6 - o, 0.6], 'iron', { rot: [45, 0, 0], pivot: [-r, y - o, 0] });
            box('spike_n' + row, [-0.6, y - 0.6 - o, -r - len], [0.6, y + 0.6 - o, -r + 0.1], 'iron', { rot: [0, 0, 45], pivot: [0, y - o, -r] });
            box('spike_s' + row, [-0.6, y - 0.6 + o, r - 0.1], [0.6, y + 0.6 + o, r + len], 'iron', { rot: [0, 0, 45], pivot: [0, y + o, r] });
        });
        box('spike_top', [-0.6, 30.5, -0.6], [0.6, 32.5, 0.6], 'iron', { rot: [0, 45, 0], pivot: [0, 31, 0] });
    }

    const PREFIXED = Object.fromEntries(Object.entries(MATERIALS).map(([k, v]) => ['club.' + k, v]));
    global.InvasionParts = Object.assign(global.InvasionParts || {}, { ogreClub, ogreClubMaterials: PREFIXED });

    if (global.__buildStandaloneOgreClub) {
        const B = InvasionBB.makeBuilder('ogre_club');
        B.group('club', [0, 0, 0]);
        ogreClub(B, 'club', [0, 0, 0]);
        InvasionBB.paintPixels(B, PREFIXED, 'ogre_club');
        global.__standaloneResult = { cubes: B.cubes.length };
    }
})(window);
