// 드워프 총병 애니메이션: idle / walk / attack / death.
//
// 키프레임 회전 부호는 orc_warrior_anims.js 머리말과 같습니다.
//  x < 0: 몸통·머리는 앞으로 숙이고, 아래로 뻗은 팔·다리는 끝이 뒤로 갑니다.
//  z > 0: 오른팔(+x)이 바깥으로 벌어집니다. 왼팔은 z < 0이 바깥입니다.
// 총 방향은 오른 윗팔(a) + 아래팔(f) + 손목(h)의 x 회전 합이 0이면 앞으로 수평입니다.
// 위치 키프레임은 키 34(1.3배) 기준 값입니다.
(function (global) {
    function dwarfAnimations(B) {
        const A = (name, length, loop, tracks) => InvasionBB.animation(B, name, length, loop, tracks);
        // 쇠뇌 자세(바닐라 쇠뇌 든 플레이어처럼): 두 팔을 앞으로 뻗어 총을 가슴 높이에 수평으로 겨눕니다.
        // 오른손이 개머리 목을 쥐고, 왼팔은 안쪽으로 모아 총열 밑을 받칩니다.
        const aim = {
            right_arm: [80, 20, 0], right_forearm: [0, 0, 0], right_hand: [-80, 0, 20],   // 손목 z 20: 팔을 모은 만큼 총구를 정면으로(블록벤치에서 방향 측정)
            left_arm: [88, -40, 0], left_forearm: [0, 0, 0],
        };
        const keep = (len) => Object.fromEntries(Object.entries(aim).map(([k, v]) => [k, { rotation: [[0, v], [len, v]] }]));

        // ------------------------------------------------------------ idle
        A('idle', 2.4, 'loop', Object.assign(keep(2.4), {
            body: { position: [[0, [0, 0, 0]], [1.2, [0, -0.3, 0]], [2.4, [0, 0, 0]]], rotation: [[0, [-3, 0, 0]], [1.2, [-5, 0, 0]], [2.4, [-3, 0, 0]]] },
            head: { rotation: [[0, [3, 0, 0]], [0.8, [3, 8, 0]], [1.6, [3, -6, 0]], [2.4, [3, 0, 0]]] },
            right_arm: { rotation: [[0, aim.right_arm], [1.2, [78, 20, 0]], [2.4, aim.right_arm]] },
            left_arm: { rotation: [[0, aim.left_arm], [1.2, [86, -40, 0]], [2.4, aim.left_arm]] },
        }));

        // ------------------------------------------------------------ walk
        // 짧은 다리로 뒤뚱뒤뚱. 총은 겨눈 채 몸만 좌우로 흔들립니다.
        const T = 0.8, q = T / 4, h = T / 2, t3 = 3 * T / 4;
        A('walk', T, 'loop', Object.assign(keep(T), {
            right_leg: { rotation: [[0, [0, 0, 0]], [q, [30, 0, 0]], [h, [0, 0, 0]], [t3, [-30, 0, 0]], [T, [0, 0, 0]]] },
            left_leg: { rotation: [[0, [0, 0, 0]], [q, [-30, 0, 0]], [h, [0, 0, 0]], [t3, [30, 0, 0]], [T, [0, 0, 0]]] },
            body: {
                rotation: [[0, [-4, 0, 0]], [q, [-4, 0, 6]], [h, [-4, 0, 0]], [t3, [-4, 0, -6]], [T, [-4, 0, 0]]],
                position: [[0, [0, 0, 0]], [q, [0, 0.5, 0]], [h, [0, 0, 0]], [t3, [0, 0.5, 0]], [T, [0, 0, 0]]],
            },
            head: { rotation: [[0, [2, 0, 0]], [q, [2, 0, -4]], [h, [2, 0, 0]], [t3, [2, 0, 4]], [T, [2, 0, 0]]] },
        }));

        // ------------------------------------------------------------ attack
        // 사격: 겨눈 채 잠깐 숨을 멈췄다가(0.3초) 쏜 순간 반동으로 총구가 튀어 오르고 몸이 뒤로 밀린 뒤(0.4초) 다시 겨눕니다.
        A('attack', 0.9, 'once', {
            right_arm: { rotation: [[0, aim.right_arm], [0.3, [82, 20, 0]], [0.4, [98, 20, 0]], [0.6, [84, 20, 0]], [0.9, aim.right_arm]] },
            right_forearm: { rotation: [[0, aim.right_forearm], [0.4, [12, 0, 0]], [0.9, aim.right_forearm]] },
            right_hand: { rotation: [[0, aim.right_hand], [0.4, [-85, 0, 20]], [0.9, aim.right_hand]] },
            left_arm: { rotation: [[0, aim.left_arm], [0.3, [90, -40, 0]], [0.4, [104, -40, 0]], [0.6, [92, -40, 0]], [0.9, aim.left_arm]] },
            left_forearm: { rotation: [[0, aim.left_forearm], [0.9, aim.left_forearm]] },
            body: {
                rotation: [[0, [-3, 0, 0]], [0.3, [-6, 0, 0]], [0.4, [8, 0, 0]], [0.6, [-2, 0, 0]], [0.9, [-3, 0, 0]]],
                position: [[0, [0, 0, 0]], [0.3, [0, -0.4, 0]], [0.4, [0, 0.4, 0]], [0.9, [0, 0, 0]]],
            },
            head: { rotation: [[0, [3, 0, 0]], [0.3, [6, 0, 0]], [0.4, [-8, 0, 0]], [0.9, [3, 0, 0]]] },
            right_leg: { rotation: [[0, [0, 0, 0]], [0.3, [-12, 0, 0]], [0.9, [0, 0, 0]]] },
            left_leg: { rotation: [[0, [0, 0, 0]], [0.3, [14, 0, 0]], [0.9, [0, 0, 0]]] },
        });

        // ------------------------------------------------------------ death
        // 총을 놓치며 뒤로 벌러덩 넘어집니다.
        A('death', 1.4, 'hold', {
            root: {
                rotation: [[0, [0, 0, 0]], [0.25, [-8, 0, 0]], [0.8, [90, 0, -5]], [0.95, [84, 0, -5]], [1.1, [90, 0, -5]]],
                position: [[0, [0, 0, 0]], [0.8, [0, 5, 0]], [1.1, [0, 5, 0]]],
            },
            right_arm: { rotation: [[0, aim.right_arm], [0.8, [150, 0, 30]], [1.4, [155, 0, 35]]] },
            right_hand: { rotation: [[0, aim.right_hand], [0.8, [30, 0, 0]], [1.4, [30, 0, 0]]] },
            left_arm: { rotation: [[0, aim.left_arm], [0.8, [150, 0, -30]], [1.4, [155, 0, -35]]] },
            right_leg: { rotation: [[0, [0, 0, 0]], [0.8, [40, 0, 6]], [1.4, [30, 0, 6]]] },
            left_leg: { rotation: [[0, [0, 0, 0]], [0.8, [20, 0, -6]], [1.4, [15, 0, -6]]] },
            head: { rotation: [[0, [0, 0, 0]], [0.8, [20, 25, 0]], [1.4, [20, 25, 0]]] },
        });
    }

    global.InvasionParts = Object.assign(global.InvasionParts || {}, { dwarfAnimations });
})(window);
