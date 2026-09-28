// 엘프 암살자의 단검. 엘프 모델과 따로 다듬은 뒤 양손에 붙입니다.
//
// 좌표는 손잡이를 쥐는 점이 원점이고, 날은 +y로 뻗습니다. 날은 x판 한 장이고 날 쪽이 -z입니다.
// 모든 부품은 1픽셀 단위입니다(마인크래프트 스타일 가이드: 1픽셀보다 작은 요소 금지).
// 역수(逆手)로 쥘 때는 손 그룹 안에서 그룹을 x축으로 크게 돌려 날이 아래·뒤를 향하게 합니다.
(function (global) {
    const MATERIALS = {
        grip: { c: ['#120f17', '#1e1924', '#2d2536', '#3f3449'] },
        silver: { c: ['#565b70', '#8a91a5', '#bcc3d2', '#eef2f8'] },
        blade: { c: ['#4f5669', '#7c8599', '#aeb6c6', '#e6ebf2'] },
        rune: { c: ['#3b1a6e', '#6a33b5', '#9d6ae6', '#c9a8ff'] },
    };

    // opts.edgeBack: 날 쪽을 +z로 뒤집습니다. 역수로 쥐어 x축으로 90도 넘게 돌리면 날이 아래를 봅니다.
    function elfDagger(B, group, grip, opts) {
        const [gx, gy, gz] = grip;
        const at = (p) => [p[0] + gx, p[1] + gy, p[2] + gz];
        const box = (name, from, to, mat, opts) => {
            const o = Object.assign({}, opts || {});
            if (o.pivot) o.pivot = at(o.pivot);
            B.box(group, group + '_' + name, at(from), at(to), 'dagger.' + mat, o);
        };

        box('pommel', [-0.5, -2.5, -0.5], [0.5, -1.5, 0.5], 'silver');
        box('grip', [-0.5, -1.5, -0.5], [0.5, 1.5, 0.5], 'grip');
        box('guard', [-0.5, 1.5, -1.5], [0.5, 2.5, 1.5], 'silver');
        // 날: east(+x)에서 본 모습. 왼쪽이 +z(등), 오른쪽이 -z(날). 가운데 보랏빛 룬이 흐릅니다.
        box('blade', [0, 2.5, -1.5], [0, 8.5, 1.5], 'blade', {
            plane: 'x',
            px: {
                east: [
                    '..E',
                    '.bE',
                    'sbE',
                    'srE',
                    'srE',
                    'sbE',
                ].map(row => (opts && opts.edgeBack ? row.split('').reverse().join('') : row)),
                key: { E: 'dagger.silver:3', b: 'dagger.blade:2', s: 'dagger.blade:1', r: 'dagger.rune:2' },
            },
        });
    }

    const PREFIXED = Object.fromEntries(Object.entries(MATERIALS).map(([k, v]) => ['dagger.' + k, v]));
    global.InvasionParts = Object.assign(global.InvasionParts || {}, { elfDagger, elfDaggerMaterials: PREFIXED });

    if (global.__buildStandaloneDagger) {
        const B = InvasionBB.makeBuilder('elf_dagger');
        B.group('dagger', [0, 0, 0]);
        elfDagger(B, 'dagger', [0, 0, 0]);
        InvasionBB.paintPixels(B, PREFIXED, 'elf_dagger');
        global.__standaloneResult = { cubes: B.cubes.length };
    }
})(window);
