// 트롤 투창. 트롤 모델과 따로 다듬은 뒤 오른손과 등 화살통에 붙입니다.
//
// 좌표는 손잡이(자루 가운데)가 원점이고 창끝은 +y로 뻗습니다. 창날은 x판 한 장에 픽셀로 그리고
// (east 면 기준), 텍스처는 바닐라식(paintPixels)입니다.
(function (global) {
    const MATERIALS = {
        wood: { c: ['#3a2413', '#56371e', '#704a2a', '#8c6038'] },
        wrap: { c: ['#241509', '#3a2414', '#51341f', '#6b472b'] },
        stone: { c: ['#3d4146', '#5b6066', '#7e848b', '#a9aeb4'] },
        feather: { c: ['#3b2d25', '#5a463a', '#7b6452', '#a08a74'] },
    };

    // len: 자루 길이(가운데 기준 위아래로 나뉨), prefix: 여러 자루를 한 그룹에 둘 때 이름 앞머리
    function trollJavelin(B, group, grip, prefix, len) {
        const [gx, gy, gz] = grip;
        const L = len || 24;
        const at = (p) => [p[0] + gx, p[1] + gy, p[2] + gz];
        const box = (name, from, to, mat, opts) => {
            const o = Object.assign({}, opts || {});
            if (o.pivot) o.pivot = at(o.pivot);
            B.box(group, (prefix || group) + '_' + name, at(from), at(to), 'jav.' + mat, o);
        };
        const lo = -L * 0.45, hi = L * 0.55;
        box('shaft', [-0.4, lo, -0.4], [0.4, hi, 0.4], 'wood');
        box('grip', [-0.5, -1.5, -0.5], [0.5, 1.5, 0.5], 'wrap');
        box('binding', [-0.5, hi - 0.8, -0.5], [0.5, hi, 0.5], 'wrap');
        // 뗀석기 창날: S 돌, s 그늘, E 날 끝
        box('head', [0, hi, -1], [0, hi + 4, 1], 'stone', {
            plane: 'x', px: { east: ['.E', 'SE', 'sS', 'sS'], key: { E: 'jav.stone:3', S: 'jav.stone:2', s: 'jav.stone:1' } },
        });
        // 자루 끝 깃털 두 장
        for (const [n, z] of [['fletch_a', 0.4], ['fletch_b', -0.4]]) {
            box(n, [0, lo, z], [0, lo + 2.5, z + (z > 0 ? 1 : -1)], 'feather', { plane: 'x' });
        }
    }

    const PREFIXED = Object.fromEntries(Object.entries(MATERIALS).map(([k, v]) => ['jav.' + k, v]));
    global.InvasionParts = Object.assign(global.InvasionParts || {}, { trollJavelin, trollJavelinMaterials: PREFIXED });
})(window);
