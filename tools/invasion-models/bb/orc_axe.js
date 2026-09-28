// 오크 전투 도끼. 오크 모델과 따로 다듬은 뒤 손 그룹에 붙입니다.
//
// 좌표는 손잡이를 쥐는 점이 원점이고, 자루는 +y로 섭니다. 날은 -z 쪽입니다.
// 날은 x판 두 장에 픽셀로 초승달을 그리고(east 면 기준: 왼쪽 열이 +z 소켓 쪽, 오른쪽 열이 -z 날 끝),
// 텍스처는 바닐라식(paintPixels)으로 칠합니다.
(function (global) {
    const MATERIALS = {
        wood: { c: ['#3a2413', '#56371e', '#704a2a', '#8c6038'] },
        wrap: { c: ['#241509', '#3a2414', '#51341f', '#6b472b'], rim: true },
        iron: { c: ['#34373d', '#50555d', '#727a84', '#a3abb5'] },
        steel: { c: ['#6a717a', '#939ba4', '#c3cad2', '#eef2f5'] },
        bone: { c: ['#958c70', '#b8ae8e', '#d6cdac', '#f1ecd8'] },
        blood: { c: ['#3a0806', '#560d09', '#72140e', '#8e1d14'] },
    };

    function orcAxe(B, group, grip) {
        const [gx, gy, gz] = grip;
        const at = (p) => [p[0] + gx, p[1] + gy, p[2] + gz];
        const box = (name, from, to, mat, opts) => {
            const o = Object.assign({}, opts || {});
            if (o.pivot) o.pivot = at(o.pivot);
            B.box(group, 'axe_' + name, at(from), at(to), 'axe.' + mat, o);
        };

        // 자루: 나무에 가죽 손잡이, 쇠 마개와 쇠띠
        box('haft', [-0.7, -6, -0.7], [0.7, 21, 0.7], 'wood');
        box('grip', [-0.9, -4, -0.9], [0.9, 4, 0.9], 'wrap');
        box('pommel', [-1.1, -7.5, -1.1], [1.1, -6, 1.1], 'iron');
        box('band', [-0.9, 10, -0.9], [0.9, 11, 0.9], 'iron');
        // 머리: 자루를 감싼 소켓
        box('socket', [-1.1, 14.5, -1.6], [1.1, 21.5, 1.6], 'iron');
        box('cap', [-0.9, 21.5, -1.3], [0.9, 22.5, 1.3], 'iron');
        // 날: 초승달 판 두 장. E 날, i 쇠, I 가운데 홈, r 핏자국.
        const BLADE = [
            '.....EEE',
            '....iiEE',
            '...iiiiE',
            '..iiiiiE',
            'iiiiiiiE',
            'iiiiIirE',
            'iiiiIrrE',
            'iiiiIirE',
            'iiiiIiiE',
            'iiiiiiiE',
            '..iiiiiE',
            '...iiiiE',
            '....iiEE',
            '.....EEE',
        ];
        const key = { E: 'axe.steel:3', i: 'axe.iron:2', I: 'axe.iron:1', r: 'axe.blood:2' };
        for (const x of [0.35, -0.35]) {
            box(x > 0 ? 'blade_r' : 'blade_l', [x, 11, -9.6], [x, 25, -1.6], 'iron', { plane: 'x', px: { east: BLADE, key } });
        }
        box('blade_spine', [-0.35, 13, -3.2], [0.35, 23, -1.6], 'iron');
        // 등 가시: 소켓 뒤로 뻗은 작은 삼각 판
        box('spike', [0, 16, 1.6], [0, 20, 5.6], 'iron', {
            plane: 'x', px: { east: ['ii..', 'iiiE', 'iiiE', 'ii..'], key },
        });
        // 뼈 부적
        box('charm_cord', [-0.15, 11.5, 1.2], [0.15, 14.5, 1.5], 'wrap');
        box('charm', [-0.8, 9.8, 0.8], [0.8, 11.8, 2.3], 'bone');
    }

    const PREFIXED = Object.fromEntries(Object.entries(MATERIALS).map(([k, v]) => ['axe.' + k, v]));
    global.InvasionParts = Object.assign(global.InvasionParts || {}, { orcAxe, orcAxeMaterials: PREFIXED });

    if (global.__buildStandaloneAxe) {
        const B = InvasionBB.makeBuilder('orc_axe');
        B.group('axe', [0, 0, 0]);
        orcAxe(B, 'axe', [0, 0, 0]);
        InvasionBB.paintPixels(B, PREFIXED, 'orc_axe');
        global.__standaloneResult = { cubes: B.cubes.length };
    }
})(window);
