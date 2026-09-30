// 정원사 애니메이션: idle / attack / skill.
//
// 키프레임 회전 부호는 orc_warrior_anims.js 머리말과 같습니다.
//  x < 0: 몸통·머리는 앞으로 숙이고, 아래로 뻗은 팔·다리는 끝이 뒤로 갑니다.
//  z > 0: 오른팔(+x)이 바깥으로 벌어집니다. 왼팔은 z < 0이 바깥입니다.
// 양산 방향은 오른 윗팔(a) + 아래팔(f) + 손목(h) x 회전 합이 0이면 곧게 위, 양수면 덮개가 뒤로 눕습니다.
// 손목 z가 음수면 덮개가 머리 쪽으로 기웁니다.
(function (global) {
    function gardenerAnimations(B) {
        const A = (name, length, loop, tracks) => InvasionBB.animation(B, name, length, loop, tracks);
        // 양산을 곧게 세워 든 자세: 팔을 조금 앞으로 굽혀 자루를 쥐고, 손목으로 되돌려 자루가 수직으로 섭니다. 왼손은 허리 앞에.
        const hold = {
            right_arm: [20, 0, 6], right_forearm: [45, 0, 0], right_hand: [-65, 0, -6],
            left_arm: [18, -20, 0], left_forearm: [50, 0, 0],
        };
        const keep = (len) => Object.fromEntries(Object.entries(hold).map(([k, v]) => [k, { rotation: [[0, v], [len, v]] }]));
        const withSkirt = (tracks) => {
            for (const side of ['right', 'left']) {
                const leg = tracks[side + '_leg'];
                if (leg && leg.rotation && !tracks['skirt_' + side]) {
                    tracks['skirt_' + side] = { rotation: leg.rotation.map(([t, r]) => [t, [r[0] * 0.6, 0, r[2] * 0.5]]) };
                }
            }
            return tracks;
        };
        const A2 = (name, length, loop, tracks) => A(name, length, loop, withSkirt(tracks));

        // ------------------------------------------------------------ idle
        // 느긋하게 서서 숨 쉬고, 곧게 세운 양산을 천천히 돌립니다.
        A2('idle', 3.0, 'loop', Object.assign(keep(3.0), {
            body: { position: [[0, [0, 0, 0]], [1.5, [0, -0.3, 0]], [3.0, [0, 0, 0]]] },
            torso: { rotation: [[0, [0, 0, 0]], [1.5, [-2, 0, 0]], [3.0, [0, 0, 0]]] },
            head: { rotation: [[0, [2, 0, 0]], [1.0, [2, 8, 3]], [2.0, [2, -6, -2]], [3.0, [2, 0, 0]]] },
            hair_back: { rotation: [[0, [0, 0, 0]], [1.5, [-2, 0, 1]], [3.0, [0, 0, 0]]] },
            parasol: { rotation: [[0, [0, 0, 0]], [1.5, [0, 180, 0]], [3.0, [0, 360, 0]]] },
        }));

        // ------------------------------------------------------------ attack
        // 러커식 가시: 양산을 들어 올렸다가(0.35초) 꼭지를 앞 땅에 내리꽂습니다(0.5초). 피해는 꽂는 순간입니다.
        A2('attack', 1.0, 'once', {
            right_arm: { rotation: [[0, hold.right_arm], [0.35, [150, 0, 10]], [0.5, [70, 0, 4]], [0.7, [70, 0, 4]], [1.0, hold.right_arm]] },
            right_forearm: { rotation: [[0, hold.right_forearm], [0.35, [20, 0, 0]], [0.5, [10, 0, 0]], [0.7, [10, 0, 0]], [1.0, hold.right_forearm]] },
            right_hand: { rotation: [[0, hold.right_hand], [0.35, [0, 0, 0]], [0.5, [80, 0, 0]], [0.7, [80, 0, 0]], [1.0, hold.right_hand]] },
            torso: { rotation: [[0, [0, 0, 0]], [0.35, [6, 0, 0]], [0.5, [-14, 0, 0]], [0.7, [-12, 0, 0]], [1.0, [0, 0, 0]]] },
            head: { rotation: [[0, [2, 0, 0]], [0.35, [-8, 0, 0]], [0.5, [10, 0, 0]], [1.0, [2, 0, 0]]] },
            body: { position: [[0, [0, 0, 0]], [0.35, [0, 0.5, 0]], [0.5, [0, -0.8, 0]], [1.0, [0, 0, 0]]] },
            left_arm: { rotation: [[0, hold.left_arm], [0.5, [-20, 0, -25]], [1.0, hold.left_arm]] },
            left_forearm: { rotation: [[0, hold.left_forearm], [0.5, [10, 0, 0]], [1.0, hold.left_forearm]] },
            hair_back: { rotation: [[0, [0, 0, 0]], [0.35, [4, 0, 0]], [0.5, [-8, 0, 0]], [1.0, [0, 0, 0]]] },
            right_leg: { rotation: [[0, [0, 0, 0]], [0.5, [-12, 0, 0]], [1.0, [0, 0, 0]]] },
        });

        // ------------------------------------------------------------ skill
        // 스킬: 양산을 머리 위로 높이 펼쳐 들고 왼손을 앞으로 내밀어 겨눕니다.
        A2('skill', 1.2, 'once', {
            right_arm: { rotation: [[0, hold.right_arm], [0.4, [170, 0, 12]], [0.9, [170, 0, 12]], [1.2, hold.right_arm]] },
            right_forearm: { rotation: [[0, hold.right_forearm], [0.4, [0, 0, 0]], [0.9, [0, 0, 0]], [1.2, hold.right_forearm]] },
            right_hand: { rotation: [[0, hold.right_hand], [0.4, [-170, 0, 0]], [0.9, [-170, 0, 0]], [1.2, hold.right_hand]] },
            parasol: { rotation: [[0, [0, 0, 0]], [0.4, [0, 120, 0]], [0.9, [0, 360, 0]], [1.2, [0, 360, 0]]] },
            left_arm: { rotation: [[0, hold.left_arm], [0.4, [90, -10, 0]], [0.9, [90, -10, 0]], [1.2, hold.left_arm]] },
            left_forearm: { rotation: [[0, hold.left_forearm], [0.4, [0, 0, 0]], [0.9, [0, 0, 0]], [1.2, hold.left_forearm]] },
            torso: { rotation: [[0, [0, 0, 0]], [0.4, [4, 0, 0]], [0.9, [4, 0, 0]], [1.2, [0, 0, 0]]] },
            head: { rotation: [[0, [2, 0, 0]], [0.4, [-6, 0, 0]], [1.2, [2, 0, 0]]] },
            hair_back: { rotation: [[0, [0, 0, 0]], [0.4, [-6, 0, 2]], [1.2, [0, 0, 0]]] },
        });
    }

    global.InvasionParts = Object.assign(global.InvasionParts || {}, { gardenerAnimations });
})(window);
