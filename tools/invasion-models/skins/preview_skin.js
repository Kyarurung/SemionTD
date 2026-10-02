// 플레이어 스킨 미리보기: 블록벤치에 슬림 팔 플레이어 모양을 만들고 스킨 PNG를 박스 UV로 입힙니다.
// 겉층(모자·자켓·소매·바지)은 바닐라처럼 부풀려 겹칩니다. 선택으로 뾰족귀(머리 장식 자리)를 붙입니다.
// 사용: window.__skinPreview = { name, png, slim, ears } 를 정한 뒤 이 파일을 eval 합니다.
(function () {
    const cfg = window.__skinPreview;
    for (const p of ModelProject.all.slice()) if (p.name === cfg.name) { p.saved = true; p.close(true); }
    newProject(Formats.free);
    Project.name = cfg.name;
    Project.box_uv = true;
    Project.texture_width = 64;
    Project.texture_height = 64;
    // 같은 경로를 다시 열면 블록벤치가 캐시를 쓰므로, 파일을 매번 읽어 데이터 URL로 넣습니다.
    const png = 'data:image/png;base64,' + require('fs').readFileSync(cfg.png).toString('base64');
    const tex = new Texture({ name: 'skin' }).fromDataURL(png).add(false);

    const root = new Group({ name: 'player', origin: [0, 0, 0] }).addTo().init();
    const cube = (name, from, to, uv, inflate) => {
        const c = new Cube({ name, from, to, box_uv: true, uv_offset: uv, inflate: inflate || 0 }).addTo(root).init();
        c.applyTexture(tex, true);
        return c;
    };
    const aw = cfg.slim ? 3 : 4;
    cube('head', [-4, 24, -4], [4, 32, 4], [0, 0]);
    cube('hat', [-4, 24, -4], [4, 32, 4], [32, 0], 0.5);
    cube('body', [-4, 12, -2], [4, 24, 2], [16, 16]);
    cube('jacket', [-4, 12, -2], [4, 24, 2], [16, 32], 0.25);
    cube('right_arm', [4, 12, -2], [4 + aw, 24, 2], [40, 16]);
    cube('right_sleeve', [4, 12, -2], [4 + aw, 24, 2], [40, 32], 0.25);
    cube('left_arm', [-4 - aw, 12, -2], [-4, 24, 2], [32, 48]);
    cube('left_sleeve', [-4 - aw, 12, -2], [-4, 24, 2], [48, 48], 0.25);
    cube('right_leg', [0, 0, -2], [4, 12, 2], [0, 16]);
    cube('right_pants', [0, 0, -2], [4, 12, 2], [0, 32], 0.25);
    cube('left_leg', [-4, 0, -2], [0, 12, 2], [16, 48]);
    cube('left_pants', [-4, 0, -2], [0, 12, 2], [0, 48], 0.25);

    if (cfg.ears) {
        // 머리 장식 자리의 뾰족귀: 판 한 장씩, 뒤·위로 기울입니다(작은 별도 텍스처).
        const c = document.createElement('canvas');
        c.width = 16; c.height = 16;
        const g = c.getContext('2d');
        const rows = ['ss...', '.ssss', '..kss'];
        rows.forEach((r, v) => [...r].forEach((ch, u) => {
            if (ch === '.') return;
            g.fillStyle = ch === 's' ? '#e8b8a4' : '#c98f86';
            g.fillRect(u, v, 1, 1);
        }));
        const earTex = new Texture({ name: 'ears' }).fromDataURL(c.toDataURL()).add(false);
        for (const sign of [1, -1]) {
            const eg = new Group({ name: sign > 0 ? 'right_ear' : 'left_ear', origin: [4.5 * sign, 27.8, -0.5], rotation: [0, -25 * sign, 20 * sign] }).addTo(root).init();
            const from = sign > 0 ? [4.5, 26.6, -0.5] : [-9.5, 26.6, -0.5];
            const to = sign > 0 ? [9.5, 29.6, -0.5] : [-4.5, 29.6, -0.5];
            const e = new Cube({ name: eg.name, from, to, box_uv: false }).addTo(eg).init();
            for (const f of ['north', 'south']) {
                e.faces[f].texture = earTex.uuid;
                const flip = (f === 'south') !== (sign < 0);
                e.faces[f].uv = flip ? [5, 0, 0, 3] : [0, 0, 5, 3];
            }
            for (const f of ['east', 'west', 'up', 'down']) e.faces[f].texture = null;
        }
    }
    Canvas.updateAll();
    return { cubes: Cube.all.length };
})();
