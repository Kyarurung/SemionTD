# 공개 경기 입력 기반 전투 정합성 검증

**기술자 첫 라운드 통제 시나리오의 독립 native/simulation 비교가 통과했다.** 두 native JVM 실행과 최종 simulation 실행의 전체 sample 배열이 정확히 같다. 초기 상태 및 391 logical tick의 392 sample에서 health·position·target·cooldown·alive와 순서 있는 공격·피해·생성·사망·보상·발사기·발판 event를 비교했다. 전체 역사적 경기나 모든 빌더의 정합성을 의미하지 않는다.

Java 25 / Minecraft 26.3 headless Fabric GameTest의 `waitUntilNextTick()`은 대기 없이 task를 실행한다. `physical_tps=40/20`은 실제 설정한 서버 frame clock이며 벽시계 기준 처리율이나 성능 개선 실측이 아니다. 최종 simulation의 7,238 physical END callback은 빠르게 실행된 테스트 프레임 수다. 196회의 승인된 2-step 요청에서 wave 종료까지 391 logical tick을 완료했다. 고정 sleep, worker join 또는 server-thread busy-spin을 추가하지 않았다.

## 관측 입력과 통제 조건

자료는 [경기 상세](https://semiontd.biryeong.kim/api/v1/matches/893854454113494679), [stats](https://semiontd.biryeong.kim/api/v1/stats), [patches](https://semiontd.biryeong.kim/api/v1/patches), [경기 버전 catalog](https://semiontd.biryeong.kim/api/v1/catalog?version=5e3a47fbdf1be38258b3a86ed48dcbd36792e2219f771886f3eda1fb623feab3), participant/round metrics다. [match fixture](../src/test/resources/replay/match-893854454113494679.json)는 참가자 22명, build action 1,865개, 타워별 round row 2,073개를 보존한다. round cursor가 `invalid_query`로 거부되어 1~31 round를 `fromRound=toRound`, `limit=1000`으로 나누고 후속 cursor 및 중복 키가 없음을 확인했다. 닉네임·원래 player UUID·빌드 공유 코드·모델 자산은 제외했으며 match ID와 비용·환불은 부동소수점을 거치지 않는다.

[controlled openings](../src/test/resources/replay/controlled-openings.json)의 기술자·흑마법사·우민 입력 재구성은 JUnit으로 검사한다. 실제 전투는 [engineer opening](../src/gametest/resources/replay/engineer-opening.json)의 `p02`, sequence 0~4만 사용했다. 나무 발판 2개, 구리 골렘, T1 문, T1 발사기를 실제 `ProductionTowerService`로 설치해 131 다이아 지출과 lane-relative xyz를 확인했다. 독립 Fantasy 합성 아레나의 라인 원점 `(0,64,0)`, 초기 자원 10,000/10,000, seed `1`, 준비 경계 순서는 통제 입력이다. 자연 wave 1의 몬스터 12개를 실제 큐에서 생성했다. NORMAL 진행을 위한 별도 통제 BLUE lane을 두고 RED world를 캡처했다. 소환·생산 강화·다른 참가자·이후 증강·플레이어 조작은 battle 범위에서 제외했다.

world/game/actor RNG, tower/monster logical UUID, entity UUID/integer ID를 삽입 전에 공급했다. 실제 UUID lookup을 확인하고 UUID 기반 target tie 및 `(tickCount + entityId) % 2`의 native goal cadence를 동일하게 유지했다. 생성자에서 뽑힌 회전은 RNG 재설정만으로 돌아가지 않으므로 초기 yaw/pitch/head/body 및 이전 회전을 0으로 통제하고 spawn event에 실제 값을 기록했다. 공개 데이터에 없는 초기 상태를 테스트에서 정한 조건이며 생산 소스의 일반 규칙 변경이 아니다.

## 검증된 소스와 context

| 항목 | 값 |
|---|---|
| 원본 생산 revision | `f2f3c2e53babce83ac07f3ea4be364d4ce349bae` |
| 최종 main 생산 commit | `2588b2e22a1168121795d4d0fca7bf9c6518cf56` |
| 실제 candidate 캡처 commit | `55ae98cd1062a67a99cd58e0b15ab9a07d1bedb0` |
| 원래 경기 catalog | `5e3a47fbdf1be38258b3a86ed48dcbd36792e2219f771886f3eda1fb623feab3` |
| 원래 경기 augment | `fee5aec3d50be883fd34c25f01b639034638c6a3f343d97c8bbf4793c0a59b93` |
| 원래 경기 balance revision | `8e128257b182cf4f7fb4a65ee72ba4ef977276dbc4bf278302f2701f3f754c4e` |
| 통제 scenario SHA-256 | `66c2653799c583148215d2b87235658ff2a509a1beedc1187ee89d92c3d0a692` |
| 실제 적용 규칙 SHA-256 | `76e60668049dc23e1aa955cd4fa3dd91ff0fafb1e8db7c750a7aef0ba0c826a8` |
| 합성 맵 SHA-256 | `62e6c2a3c09d234abb628cb063f188d0be6ecf83f48f97c290cf0d4aa52df334` |

원본 checkout에는 GameTest helper/mixin만 추가했고 `src/main`, `compat`, Gradle 의존성 파일이 지정 upstream revision과 동일함을 확인했다. candidate의 같은 생산 파일들은 최종 main commit과 차이가 없다. `run_capture.py`는 미커밋 생산 변경, 누락된 committed runtime, 기존 출력 파일, incomplete/invalid trace를 거부한다. source revision을 비교를 위해 임의로 바꾸지 않는다. 실제 적용 규칙 hash에는 packaged balance/economy/wave/summon 설정, 특성·증강 조건 및 RNG/identity mapping을 포함한다.

## 독립 실행 결과

| 항목 | Native A | Native B | Final simulation |
|---|---|---|---|
| run ID | `63dd825c-f026-4496-a051-c039aac28b49` | `d643ce5b-47d2-464a-bdd9-25cb9a4c799e` | `5ffbc3f2-c70d-4272-9bd3-e53145ccb5fa` |
| physical frame clock | 40 | 40 | 20 |
| logical clock / 요청 step | 40 / 1 | 40 / 1 | 40 / 2 |
| logical tick / sample | 391 / 392 | 391 / 392 | 391 / 392 |
| physical END callback | 391 | 391 | 7,238 |
| 승인 요청 frame | 391 | 391 | 196 |
| attacks / damage | 96 / 96 | 96 / 96 | 96 / 96 |
| spawn / death / reward | 12 / 12 / 12 | 12 / 12 / 12 | 12 / 12 / 12 |
| instant dispenser shot | 12 | 12 | 12 |
| plate press / 상태 관측 | 8 / 784 | 8 / 784 | 8 / 784 |
| kill reward diamond | 36 | 36 | 36 |

세 실행은 서로 다른 JVM이다. native A/B 반복성과 native/candidate comparator는 모두 `equal=true`, `samples_compared=392`다. 위치 허용 오차는 `1e-7`이고 다른 수치와 event 순서는 정확히 비교했다. 메인 체크아웃에서는 위치 허용 오차를 0으로 지정한 비교도 통과했다. 추가로 전체 sample 배열의 `==`도 세 실행 모두 참이다. key 정렬·compact UTF-8 JSON sample array의 SHA-256은 모두 `ef212762c38173a763d68d65aaa4efc5b6bc869020c9c439e176305f8d379637`이다. 독립 run ID 때문에 원시 파일 byte hash는 다르며 [검증 증거](replay-simulation-evidence.json)에 각 파일 SHA-256과 metadata를 남겼다.

실행한 검증은 fixture JUnit 10개 통과, Python tooling 검사 14개 통과, 최신 pinned native A/B 각각 필수 GameTest 1개 통과, 최종 candidate 필수 GameTest 1개 통과다. 최신 native 두 실행은 56초/30초, 최종 candidate는 1분 1초에 build/capture를 완료했다. 시작·컴파일을 포함한 시간이며 실제 TPS 성능 측정이 아니다.

메인 체크아웃의 최종 `.\gradlew.bat test runGameTest remapJar --console=plain --no-daemon`도 3분 40초에 성공했다. JUnit 2,112개는 실패·오류 0건과 제외 2개이며 필수 GameTest 1,003개 전부 통과했고 배포 JAR을 생성했다. 최종 생산 코드는 위의 캡처 commit과 동일하며 그 이후에는 회귀 테스트와 검증 문서만 추가했다. 서버 START/END의 오류 격리, 입력 큐 256개 포화와 다음 틱의 밸런스 재시도, 구매·팀 재화·피해·회로·골렘 접촉·회전·스윙·풍화·아이템 줍기 검증을 포함한다. 배포 JAR에서 시뮬레이션 클래스와 필수 mixin을 확인하고 테스트용 캡처 클래스와 보호된 모델 자산은 제외했다.

## 검출 후 수정한 차이와 범위

이전 candidate의 첫 차이는 logical tick 107, 선두 몬스터 X `34.06142761762411` 대 `34.096469558323115`였다. 차이 `0.035041940699002794`는 골렘과 몬스터 거리의 native `Entity.push` impulse `0.035041940699002974`와 약 `1.8e-16` 잔차로 같았다. 기술자 소유 일반 Copper Golem body가 logical actor 순서에 없었다. 담당 구현은 등록된 골렘을 canonical 순서에 포함하고 물리 단계의 중복 body/loot/heading을 막으며 native logical body·push·weathering 및 정확한 SwingState 현재 값을 보존했다. push를 끄거나 비교 기대값·허용 오차를 바꾸지 않았다. 최종 캡처에서 전체 배열 일치를 다시 확인했다.

초기 candidate의 test frame 한도 실패는 진단상 1,600 physical END callback 동안 logical 59가 완료되고 failure가 없는 unpaced worker 진행이었다. 테스트 창만 maxTicks 20,000 / diagnostic bound 18,000으로 늘렸다. 자연 wave 종료, logical 최대 400, 입력, 채널과 허용 오차는 유지했다. incomplete diagnostics는 `capture_complete=false`로 저장하고 comparator가 정합성 증거로 거부한다.

공개 action에는 실행 tick과 참가자 사이 전역 순서가 없다. 원래 map/라인 원점, actual UUID, RNG 상태·TPS 이력, target 이력, 생성/사망 순서, player 입력 및 전체 운영 설정도 복원되지 않는다. 과거 catalog와 source default의 사용 tower stat/edge 가격 차이 24개를 구분했고 동일한 최신 source default로 적응한 opening만 비교했다. 이 opening의 dispenser는 즉시 피해를 적용하므로 projectile 범위는 `INSTANT_DISPENSER_SHOT`이다. 회로는 두 발판의 실제 press callback 및 tick별 권위 상태이며 임의 회로 내부 전파 전체는 아니다. client GPU 표시, real multiplayer, 다른 builder/후반 round와 실제 20/40 TPS 성능은 이 증거의 범위 밖이다.

## 재실행과 산출물

```powershell
python -B scripts/replay/fetch_match_fixture.py
python -B -m unittest discover -s scripts/replay -p test_compare_traces.py
.\gradlew.bat test --tests 'kim.biryeong.semiontd.game.replay.MatchReplayFixtureTest' --console=plain --no-daemon
python -B scripts/replay/run_capture.py native --root 'C:\path\to\reference-checkout' --output 'C:\path\to\captures\native-a.json'
python -B scripts/replay/run_capture.py native --root 'C:\path\to\reference-checkout' --output 'C:\path\to\captures\native-b.json'
python -B scripts/replay/compare_traces.py 'C:\path\to\captures\native-a.json' 'C:\path\to\captures\native-b.json' --repeat-reference
python -B scripts/replay/run_capture.py simulation --output build/replay-analysis/simulation-new.json
python -B scripts/replay/compare_traces.py 'C:\path\to\captures\native-a.json' build/replay-analysis/simulation-new.json --output build/replay-analysis/parity-new.json
```

검증된 filter는 `semion-td-gametest:replay_opening_capture_test*`다. 캡처는 [native A](../build/replay-analysis/native40-latest-a.json), [native B](../build/replay-analysis/native40-latest-b.json), [native 반복성](../build/replay-analysis/native-repeatability-latest.json), [final simulation](../build/replay-analysis/simulation20-final.json), [final parity](../build/replay-analysis/parity-final.json), [source review](../build/replay-analysis/source-review-final.json)에 저장한다. 생성 파일은 공개 fixture·script 및 test-only collector를 적용한 pinned reference checkout에서 재생성한다.
