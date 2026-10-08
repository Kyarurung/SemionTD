# 공개 경기 입력 기반 전투 정합성 검증

현재 완료된 독립 캡처의 정합성 결과는 **미통과**다. 동일한 통제 조건의 원본 native 두 실행은 전체 sample 배열이 정확히 일치했고, simulation 실행은 첫 차이를 논리 tick 107에서 검출했다. 이 문서는 고정된 검증 snapshot의 결과를 설명하며 전체 역사적 경기 재현이나 모든 빌더의 정합성을 의미하지 않는다.

검증은 Java 25 / Minecraft 26.3의 headless Fabric GameTest에서 수행했다. `GameTestServer.waitUntilNextTick()`은 대기 없이 task를 실행하므로 `physical_tps=40/20`은 실제 설정한 서버 프레임 clock 값이다. 벽시계 기준의 40/20 TPS 처리율이나 성능 개선을 측정한 결과로 해석하지 않는다.

## 입력과 비교 범위

관측 자료는 [경기 상세](https://semiontd.biryeong.kim/api/v1/matches/893854454113494679), [stats](https://semiontd.biryeong.kim/api/v1/stats), [patches](https://semiontd.biryeong.kim/api/v1/patches), [해당 버전 catalog](https://semiontd.biryeong.kim/api/v1/catalog?version=5e3a47fbdf1be38258b3a86ed48dcbd36792e2219f771886f3eda1fb623feab3), participant/round metrics다. fixture는 참가자 22명, 전체 build action 1,865개, 타워별 round row 2,073개를 보존한다. round cursor가 `invalid_query`로 거부되어 각 round를 `fromRound=toRound`, `limit=1000`으로 나누고 후속 cursor 및 중복 키가 없음을 확인했다.

전체 ledger는 [match fixture](../src/test/resources/replay/match-893854454113494679.json), 통제 opening 목록은 [controlled openings](../src/test/resources/replay/controlled-openings.json), 실제 GameTest 입력은 [engineer opening](../src/gametest/resources/replay/engineer-opening.json)에 있다. 닉네임·원래 플레이어 UUID·빌드 공유 코드·모델/외형 자산은 포함하지 않는다. 64-bit match ID와 비용·환불은 부동소수점을 거치지 않는다.

실제 전투 캡처는 `p02` 기술자의 첫 round 입력만 사용했다. `sequence=0..4`의 나무 발판 2개, 구리 골렘, T1 문, T1 발사기를 실제 `ProductionTowerService`로 설치하고 131 다이아 지출 및 lane-relative xyz를 검증했다. 원본의 actual tower UUID/입력 tick/맵이 없으므로 seed `1`, 초기 자원 10,000/10,000, 독립 Fantasy 합성 아레나, 준비 경계 순서를 통제 입력으로 명시했다. 자연 wave 1의 몬스터 12개와 현재 packaged default 규칙을 두 driver에 동일하게 적용했다. NORMAL 경기가 진행되도록 별도의 통제 BLUE 상대 lane을 두고 RED world의 상태와 callback을 캡처했다.

world/game/actor RNG, 타워·몬스터 logical UUID, entity UUID/integer ID를 명시적으로 공급했다. entity identity는 삽입 전에 지정하며 UUID lookup을 확인했다. UUID 기반 target tie와 `(tickCount + entityId) % 2`의 native goal cadence를 동일하게 유지했다. 생성자에서 이미 뽑힌 회전값은 RNG 재설정만으로 복원되지 않으므로 초기 yaw/pitch/head/body 및 이전 회전을 0으로 지정하고 spawn event에 실제 값을 기록했다. 이 값들은 관측되지 않은 초기 상태에 대한 테스트 조건이며 생산 소스의 일반 규칙을 바꾸지 않는다.

## 검증 snapshot과 context

| 항목 | 값 |
|---|---|
| 원본 생산 revision | `7a389bf04a5a81a5d1a84c2c36fcf1beb5bafe13` |
| simulation 생산 tree | `ff4a46ba8d2909796e030d8c4fee3607071b7b5a`와 생산/compat/의존성 파일 차이 없음 |
| 실제 simulation 캡처 commit | `32b043fb4333bad902aeaeb4488ba0b2d845f868` |
| 원래 경기 catalog | `5e3a47fbdf1be38258b3a86ed48dcbd36792e2219f771886f3eda1fb623feab3` |
| 원래 경기 augment | `fee5aec3d50be883fd34c25f01b639034638c6a3f343d97c8bbf4793c0a59b93` |
| 원래 경기 balance revision | `8e128257b182cf4f7fb4a65ee72ba4ef977276dbc4bf278302f2701f3f754c4e` |
| 통제 scenario SHA-256 | `66c2653799c583148215d2b87235658ff2a509a1beedc1187ee89d92c3d0a692` |
| 실제 적용 규칙 SHA-256 | `caf0e0ac7d835124ca0d21f1a57f35aae5ef885d96b14b10a23fd4ab41cffb25` |
| 합성 맵 SHA-256 | `62e6c2a3c09d234abb628cb063f188d0be6ecf83f48f97c290cf0d4aa52df334` |

원본 checkout에는 GameTest helper/mixin만 추가했으며 `src/main`, `compat`, Gradle 의존성 파일은 지정 revision과 동일함을 확인했다. simulation은 clean commit의 실제 `source_revision`을 기록했다. `run_capture.py`는 생산 소스의 미커밋 변경, 누락된 committed runtime, 이전 출력 파일, incomplete/invalid trace를 거부한다.

## 독립 캡처 결과

| 항목 | Native E | Native F | Simulation |
|---|---|---|---|
| run ID | `c36bce47-1c00-4d9b-bb13-08d1a0c59004` | `d77662a5-3926-4ab2-b7f7-1edeb230a654` | `2d4ed090-4e48-4953-8b76-98c089e72549` |
| 설정 physical frame clock | 40 | 40 | 20 |
| logical clock | 40 | 40 | 40 |
| 프레임당 요청 logical step | 1 | 1 | 2 |
| 완료 logical tick | 391 | 391 | 391 |
| 초기 상태 포함 sample | 392 | 392 | 392 |
| 관측 physical END callback | logical tick당 1회 | logical tick당 1회 | 6,768 |
| 승인된 2-step frame | 해당 없음 | 해당 없음 | 196 |
| attacks / damage | 96 / 96 | 96 / 96 | 94 / 94 |
| spawn / death / reward | 12 / 12 / 12 | 12 / 12 / 12 | 12 / 12 / 12 |
| instant dispenser shot | 12 | 12 | 12 |
| plate press / 상태 관측 | 8 / 784 | 8 / 784 | 8 / 784 |

원본 E/F는 서로 다른 JVM 실행이다. 전체 sample 배열에 대한 정확한 `==` 검사가 참이며, key 정렬·compact UTF-8 JSON sample array의 SHA-256은 둘 다 `ef212762c38173a763d68d65aaa4efc5b6bc869020c9c439e176305f8d379637`이다. metadata의 독립 run ID 때문에 원시 파일 전체의 byte hash는 서로 다르다.

simulation은 타워/actor health·position·target·cooldown·alive와 순서 있는 attacks/damage/deaths/spawns/rewards/projectiles/circuits를 각 완료 logical tick에서 캡처했다. 이 opening의 발사기는 실제 코드상 즉시 피해를 적용하므로 projectile 범위는 `INSTANT_DISPENSER_SHOT`이다. 회로 범위는 두 발판의 실제 press callback과 tick별 권위 상태이며 임의 회로의 내부 전파 전체를 검증한 결과가 아니다.

첫 차이:

```text
tick: 107
field: actors.wave/0.position[0]
native: 34.06142761762411
simulation: 34.096469558323115
position tolerance: 1e-7
```

tick 106까지 해당 상태와 event는 일치했다. tick 106의 골렘 X `34.540000000000006`과 선두 몬스터 X `34.04882497145719`에서 계산한 native `Entity.push`의 impulse `sqrt(dx) * 0.05000000074505806`은 `0.035041940699002974`다. 관측된 차이 `0.035041940699002794`와의 잔차는 약 `1.8e-16`이다. 해당 snapshot의 canonical actor filter는 Semion tower/monster/boss만 포함하여, 기술자가 소유한 일반 Copper Golem의 native body/push 단계가 logical 순서에 포함되지 않았다. 골렘의 scripted 위치와 첫 차이 시점의 health/target/cooldown은 같았다. 이 결과는 runtime 소유 범위 및 native body 순서를 조사할 근거이며 push를 끄거나 허용 오차를 늘려 통과 처리하지 않았다. 생산 runtime 수정은 담당 session과 별도로 조율한다.

첫 candidate 시도는 unpaced test frame 한도에 도달했다. 진단에서 1,600 physical END callback 동안 logical 59가 완료되고 session failure가 없음을 확인했다. 테스트 창만 maxTicks 20,000 / diagnostic bound 18,000으로 늘렸으며 자연 wave 종료·logical 최대 400·입력·기대값·허용 오차는 유지했다. worker join, server-thread sleep 또는 busy-spin을 추가하지 않았다. incomplete diagnostics는 `capture_complete=false`이며 comparator가 정합성 증거로 거부한다.

## 재실행 명령과 산출물

```powershell
python -B scripts/replay/fetch_match_fixture.py
python -B -m unittest discover -s scripts/replay -p test_compare_traces.py
.\gradlew.bat test --tests 'kim.biryeong.semiontd.game.replay.MatchReplayFixtureTest' --console=plain --no-daemon
python -B scripts/replay/run_capture.py native --root 'C:\path\to\reference-checkout' --output build/replay-analysis/native-new.json
python -B scripts/replay/run_capture.py simulation --output build/replay-analysis/simulation-new.json
python -B scripts/replay/compare_traces.py build/replay-analysis/native40-controlled.json build/replay-analysis/simulation-new.json --output build/replay-analysis/parity-new.json
```

실행한 범위: fixture JUnit 10개 통과, Python tooling 검사 14개 통과, independent seeded native GameTest E/F 각각 1개 통과, complete simulation GameTest 1개 통과. GameTest의 성공은 캡처 완성을 뜻하며 별도 comparator의 **미통과** 결과를 대체하지 않는다. 모드 전체 release gate는 main session의 별도 검증이다.

생성 산출물은 [원본](../build/replay-analysis/native40-controlled.json), [원본 반복](../build/replay-analysis/native40-controlled-repeat.json), [원본 정확한 반복성 proof](../build/replay-analysis/reference-review.json), [simulation](../build/replay-analysis/simulation20-complete.json), [첫 차이 report](../build/replay-analysis/parity-complete.json)에 저장한다. 이 파일들은 생성 상태이며 원본 fixture와 scripts로 재생성한다.

## 남는 한계

build action 실행 tick, 참가자 사이 전역 순서, 원래 맵/라인 원점, 실제 UUID와 생성/사망 순서, RNG 상태와 TPS 이력, 타겟 선택 이력, 플레이어 입력과 전체 운영 설정은 공개 telemetry로 복원되지 않는다. 과거 catalog와 기준 소스 default 사이에는 사용된 tower stat/edge price 차이 24개가 관측되었다. 이를 simulation 회귀와 섞지 않고 같은 source default로 적응한 통제 opening만 비교했다. 실제 client GPU 표시, 다중 플레이어 및 다른 빌더/후반 round의 완전한 정합성은 이 결과의 범위에 포함되지 않는다.
