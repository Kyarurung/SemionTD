// 고블린 정찰병의 녹슨 칼(쉬브). 고블린 모델과 따로 다듬은 뒤 오른손에 붙입니다.
//
// 좌표는 손잡이를 쥐는 점이 원점이고, 날은 +y로 뻗습니다. 날은 x판 한 장에 픽셀로 톱니 날을 그립니다
// (east 면 기준: 왼쪽 열이 +z 등, 오른쪽 열이 -z 날). 텍스처는 바닐라식(paintPixels)입니다.
(function (global) {
    const MATERIALS = {
        rag: { c: ['#2a1c12', '#3f2b1b', '#574027', '#735636'] },
        rust: { c: ['#4a2412', '#6e3a1c', '#8f5428', '#b37238'] },
        iron: { c: ['#3c3f45', '#5c6168', '#80868e', '#aeb4bb'] },
        bone: { c: ['#958c70', '#b8ae8e', '#d6cdac', '#f1ecd8'] },
    };

    function goblinShiv(B, group, grip) {
        const [gx, gy, gz] = grip;
        const at = (p) => [p[0] + gx, p[1] + gy, p[2] + gz];
        const box = (name, from, to, mat, opts) => {
            const o = Object.assign({}, opts || {});
            if (o.pivot) o.pivot = at(o.pivot);
            B.box(group, group + '_' + name, at(from), at(to), 'shiv.' + mat, o);
        };
        // 헝겊 감은 뼈 손잡이
        box('grip', [-0.4, -1.2, -0.4], [0.4, 1.2, 0.4], 'rag');
        box('pommel', [-0.45, -1.8, -0.45], [0.45, -1.2, 0.45], 'bone');
        box('guard', [-0.4, 1.2, -1.0], [0.4, 1.7, 1.0], 'rust');
        // 녹슨 톱니 날: R 녹, i 쇠, E 날 끝(밝은 쇠)
        box('blade', [0, 1.7, -1], [0, 6.7, 1], 'iron', {
            plane: 'x',
            px: { east: ['.E', 'iE', 'Ri', 'iE', 'RE'], key: { E: 'shiv.iron:3', i: 'shiv.iron:2', R: 'shiv.rust:2' } },
        });
    }

    const PREFIXED = Object.fromEntries(Object.entries(MATERIALS).map(([k, v]) => ['shiv.' + k, v]));
    global.InvasionParts = Object.assign(global.InvasionParts || {}, { goblinShiv, goblinShivMaterials: PREFIXED });
})(window);
