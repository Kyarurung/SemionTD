# 성능 최적화 분석과 검증

## 현재 판정

**최종 production 변경은 효과 합산과 엔지니어 발판 선택 두 개로 유지한다.** 타깃 K=1 최적화는 K=3 통제 경로의 할당 증가와 일부 시간 역전 때문에 철회했다. v2·v3는 각각 별도 JVM 6회·18단계를 측정한 과거 세 변경 조합이다. **최종 두 변경을 직접 비교한 v4 합성 6회·18단계와 JFR에서는 효과 K3·엔지니어 선택의 반복된 비용 감소와 빈 효과 경로 보완을 확인했다.** 일부 꼬리 지연·GC 증가, 통제군 변동, 외부 부하의 누락·미귀속 Java 한계도 함께 확인했다. 여섯 실행을 모두 보존하며 모든 경로의 개선이나 실전 가속 배율로 확대하지 않는다.

최신 진단 fixture를 포함한 `test runGameTest remapJar`도 통과했다. JUnit은 첫 gate의 2,017개 결과(실패·오류 0, 기존 건너뜀 2)를 재사용했고, 두 번째 gate에서 필수 GameTest 968개를 새로 실행했다. JAR도 첫 gate와 동일한 것을 검증했다.

실제 전투의 전체 엄격 결과 비교는 불일치했다. DA1/DB1 진단 기록에서는 타워별 값이 모두 같았고 실제 합산 순서를 바꿔 상대편 집계 차이를 정확히 재현했다. 원인 확인 범위는 이 두 기록이며 초기 CA1/CB1에 소급 적용하지 않는다. **엄격 결과 실패와 환경 gameTime 차이는 유지되므로 실전 성능 비교를 금지한다.** 전체 gate 성공을 실전 속도 개선이나 모든 전투의 완전 동일성으로 해석하지 않는다.

| 검증 | 최종 상태 | 범위 |
|---|---|---|
| v2·v3 합성 A/B·JFR | 완료 | 각각 18단계 × 별도 JVM 6회. 개선·회귀·통제군 변동·부하 한계 기록 |
| 집중 JUnit | 기준·v3 후보 각각 20개 통과 | 실패·오류·건너뜀 0 |
| 실제 전투 개별 GameTest | CA1/CA2/CB1/DA1/DB1 각각 1/1 통과 | runtime fixture assertion 통과와 엄격 동등성은 별개 |
| 실제 전투 엄격 비교 | 실패 유지 | CA1/CA2 501/501, CA1/CB1 176/501, DA1/DB1 429/501 동일. 환경도 불일치 |
| DA1/DB1 집계 원인 | 기록 범위 내 재현 완료 | 개별 tracker 10,521/10,521 동일, 실제 순서·교차 순서 합산 재현 |
| 전체 gate-1 | 통과 | JUnit 2,017개 실행: 실패/오류 0·건너뜀 2, 필수 GameTest 968, JAR 생성·검사 |
| 최신 전체 gate-2 | 통과 | JUnit·remapJar UP-TO-DATE, XML 267개·JAR 해시 동일. GameTest 968개 새 실행 |
| 최종 두 변경 조합의 정량 성능 | v4 합성 6회·JFR·부하 검토 완료 | 측정 경로 개선과 일부 지연·GC/통제 변동을 함께 확인. 미귀속 Java·GPU 누락 등 한계, 실전 비교 불성립 |
| 운영·실제 클라이언트·멀티플레이·배포 | 이번 최적화에서 미실행 | 커밋·푸시·배포 및 운영/기존 대화형 서버 시작·재시작 없음 |

사용자가 요청한 개별 분석 보고서이며, 이 차수의 결과·검증 한계를 상시 규칙이나 배포 완료로 취급하지 않는다.

## 기준 소스와 보존 범위

저장소 `C:\SemionTD`, 브랜치 `master`, 기준 commit `f2f3c2e53babce83ac07f3ea4be364d4ce349bae`다. 기준은 commit 단독이 아니라 **commit + 아래 기존 변경 13개**다. 기존 증강 카드·권한·화면 닫기 변경을 두 비교군에 동일하게 포함했다.

로컬 근거 루트(이하 `W`)는 `C:\Users\Kiruy\Documents\Codex\2026-10-05\task\optimization-2026-10-09`다. 최초 캡처 UTC는 `2026-10-09T09:36:37.976919+00:00`이며 원본·해시는 `baseline-worktree.json`, `baseline-changes\`에 있다. 세이브·운영 설정·안정 ID·라이선스를 변경하지 않았다. 구매/비공개 자산 공개 제외 원칙을 유지하며 이번 최적화의 커밋·푸시·배포는 수행하지 않았다.

| 기존 변경 경로 | 기준 SHA-256 |
|---|---|
| `docs/config-reference.ko.md` | `003319063b771af7fbbd106fd9be19754f46285aca7efbd4ec6ac1d4718d9f94` |
| `src/gametest/java/kim/biryeong/semiontd/augment/AugmentCaptureServer.java` | `cbe54e1cc78c712d0d6a64151aecb23e7b6c16895096374cc4b5e1400c324891` |
| `src/gametest/java/kim/biryeong/semiontd/augment/AugmentOfferPresentationTest.java` | `409fa5b16ffeda33eeceeb0bfb3b2485a2e73fe9b348e5c1e2c68f4485e35a4a` |
| `src/gametest/java/kim/biryeong/semiontd/ui/AugmentCardClientCapture.java` | `d34c230569fae65eafdffb2c2cc4ca16f791fab6937aa2570d62c448f3111d7e` |
| `src/main/java/kim/biryeong/semiontd/augment/AugmentCatalog.java` | `d96952fca0e45c520638c66a2cf395cf01e21e3bcde21f0b0358fc905e47e74e` |
| `src/main/java/kim/biryeong/semiontd/augment/AugmentService.java` | `b40208b5496ffd2329b97de929f5f4541f148c29fc5de43bac6482097497ab2b` |
| `src/main/java/kim/biryeong/semiontd/augment/PlayerAugmentState.java` | `234262f8b2a9fd3a78b27abbbe4c82f750627759092f52f06549ba24f0e41431` |
| `src/main/java/kim/biryeong/semiontd/ui/SemionDialogService.java` | `84874b938b664340309d9dd7bef395dcf736d981bc6e9231d55dfaeda7ffe44d` |
| `src/main/java/kim/biryeong/semiontd/ui/augment/AugmentCardDialog.java` | `7cc0d477a340cf924cc92e3229079aea6dbaf7c4cc37c901422fe293f4397779` |
| `src/main/java/kim/biryeong/semiontd/ui/augment/AugmentCardFrames.java` | `eca8b998632441ae02ac73dfb279c188a422a69305968c85face61292817b67d` |
| `src/main/java/kim/biryeong/semiontd/ui/augment/AugmentOfferGui.java` | `5247511f17af76abaf1cfbb62ddd440c907f1105d7784851ee08897ba14ad9fc` |
| `src/test/java/kim/biryeong/semiontd/augment/AugmentCatalogStateTest.java` | `c3223f0bae1fd63cc3b76a707d21c4a86134232a2852e176e3215fa7906fd5bd` |
| `src/test/java/kim/biryeong/semiontd/ui/augment/AugmentCardFramesTest.java` | `e148f3953e7382fa035b9f2fad195dcfe68127f629de17371ea89346ee41a1b6` |

### v2 측정 스냅샷

두 manifest는 각 3,823개 파일이며 production 세 파일만 다르다. 테스트·fixture·빌드 설정·문서는 동일하다. 격리 루트는 `C:\Users\Kiruy\AppData\Local\Temp\semion-algorithm-checks-lgu0uoh5\{baseline,candidate}`다.

| 변형 | profileRevision: LF 정규화 manifest SHA-256 | Windows CRLF manifest 실제 byte SHA-256 |
|---|---|---|
| baseline | `8198987e8caaf3852f00701465f226fec5793212f1936ceb4ceb1dbd2b9f07ea` | `45d3100deb7a3ca04fe61bcde042df10a5d9430c3b689bc8c411519c82b911b6` |
| candidate v2 | `13892070337a061391887d6433eaf1dbe12771cf6f20d188a0ae7072830062da` | `1e5cae06d2243b1680375f2bdc7ea0748bd409f81f67bac2c12c7d434bb4f463` |

파일별 해시는 `{baseline,candidate}-profile-v2-source-manifest.json`에 있다. v2 실행 식별자는 **JSON text를 LF로 정규화한 UTF-8 SHA-256**이다. 실제 저장 바이트와의 차이는 줄바꿈이며 두 방식 모두 재검증했다.

공통 합성 fixture source SHA-256은 `4ec04e775f87ede9ff9e5e185f680540b35c98a425f4045428102546acc5897e`, class SHA-256은 `c6051786926fef4e56c4878fc124f17642aae47cc2ba6e1c87ef2fc847a895cf`다. 모든 실행에서 이 값과 단계별 input/output SHA-256을 검증했다.

### v3 준비 스냅샷

`isolated-checkouts-profile-v3.json`의 캡처 UTC는 `2026-10-09T11:09:31.386994+00:00`다. 전투 fixture를 포함해 각 3,824개 파일이며 서로 다른 production 파일은 여전히 세 개다. 두 격리 소스 문서는 v2 당시 동일 snapshot을 보존했고 runtime/test/build candidate는 현재 저장소와 대조했다.

| v3 변형 | UTF-8/LF manifest 실제 byte SHA-256 |
|---|---|
| baseline | `4e599b3b69414e4a614342177aa50ef02f288b274720e1c6c042c6794ceab19d` |
| candidate | `ebfa916f0008704ec36c665bd91d1d8b22fd418781ba61fc511c716999763ec8` |

파일은 `{baseline,candidate}-profile-v3-source-manifest.json`이다. 고정 좌표 전투 fixture SHA-256은 `e49dc09827708f29f5b0247093bbbbeb77d9775af14a5f7d9b0a006eb3b5cc4b`다. 물리적 경로가 같아도 내용은 바뀌었으므로 v2 해시·성능을 현재 파일에 적용하지 않는다. 최초 집중 JUnit용 v1 기록은 `isolated-checkouts-focused-v1.json`, `focused-validation.json`, `focused-results\`로 보존했다.

### 최종 후보: 타깃 최적화 철회

타깃 K=1 빠른 경로는 철회했다. v3 후보 원본을 `candidate-production-v3\`에 보존한 뒤 저장소와 candidate 격리 소스의 `EntityGoalTargetSelection.java`를 기준 사본으로 복원했다. 복원 파일 SHA-256은 `9c67c6c983ba4accf202ad637454734ecc1dfe71a797be766a484ff66bab5581`이다. 초기 13개 변경은 이 파일과 무관하다.

최종 production 차이는 `TimedEffectSet.java`와 `EngineerGolemTower.java` 두 개다. `isolated-checkouts-final.json` 및 `candidate-final-source-manifest.json`의 UTF-8/LF 실제 byte SHA-256은 `93d6fd2b8b6dc04f5286ca21a511624a718d7278d4e47364ffcfea8c5f615f48`이다. baseline은 위 v3 baseline manifest와 같다. **이 snapshot 준비 당시에는 최종 두 변경 조합의 정량 성능이 미측정이었다. 이후 v4에서 같은 두 production 변경 조합의 합성 측정을 완료했다.** 타깃 분기의 코드 형태가 JIT·할당에 영향을 줄 수 있으므로 세 변경이 들어 있던 v3 결과를 최종 조합의 보증 수치로 옮기지 않는다. 이후 최종 조합의 전투 fixture와 두 전체 gate를 실행한 결과는 아래에 별도로 기록한다.

### 최신 진단 fixture 스냅샷

`isolated-checkouts-delivery.json`은 2026-10-09 11:43:01 UTC의 각 3,824개 파일을 기록한다. 두 production 차이는 효과 합산과 엔지니어 선택 그대로이며, 새 fixture는 개별 tracker raw bits와 실제 합산 iteration order를 진단용으로 추가했다. 원래 aggregate 결과 비교는 유지한다.

| 변형 | UTF-8/LF manifest 실제 byte SHA-256 |
|---|---|
| baseline 진단 | `4c490c261721023df9dc3134ea4d6109606b4438aefebdf5c0c43c798ddd1266` |
| candidate 진단 | `ba684fbf100cca95bd4b60cd89eb6bab04fecd0779e38f8c0027cf1f6e9ce7f1` |

파일별 해시는 `{baseline,candidate}-final-diagnostic-source-manifest.json`에 있다. 새 전투 fixture SHA-256은 `6f14aa9402af9ceb3cd2cfd3e4be904aa5379430d522993c80f3af3aca673df3`다. 문서 snapshot은 격리 소스에서 기존 v2 상태를 보존했으며 runtime/test/build를 현재 후보와 대조했다. 앞의 `93d6fd...` 스냅샷과 첫 gate 결과는 별도 이력으로 보존한다. 이 진단 snapshot 준비 당시 최종 두 변경 조합의 합성 정량 비교는 미측정이었다. 뒤의 v4에서 같은 source manifest로 직접 합성 측정했으며 실제 전투 타이밍은 여전히 비교 근거로 사용하지 않는다.

## 환경과 실행 순서

Windows 11 Home `10.0.26300`, AMD Ryzen 7 7800X3D(8코어/16논리 프로세서), 물리 메모리 67,771,465,728바이트다. JDK `C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot`의 Temurin `25.0.4.1+1-LTS`, Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3 및 동일 Gradle runtime graph를 사용했다. `environment.json`과 JSONL 환경 필드가 근거다.

측정 JVM은 `-Xms2g -Xmx2g -XX:+UseG1GC -XX:ActiveProcessorCount=8`이며 JSONL에서 최대 heap 2,147,483,648바이트·CPU 8개·G1을 확인했다. Gradle은 `-Xmx1G -XX:ActiveProcessorCount=4`, worker 2개다. 매 실행 새 격리 run/world를 생성했으며 운영 세이브를 복사하지 않았다.

v2 측정일은 2026-10-09, 실제 순서는 A1f→B1f→B2f→A2f→A3f→B3f다. 분석기 A1/B1, A2/B2, A3/B3은 라벨에서 f를 뺀 짝이다.

| 실제 순서 | 시작 UTC | 종료 UTC | 결과 |
|---|---|---|---|
| A1f (baseline) | 10:24:41 | 10:25:47 | 18단계, exit 0 |
| B1f (candidate) | 10:28:41 | 10:29:34 | 18단계, exit 0 |
| B2f (candidate) | 10:29:35 | 10:30:17 | 18단계, exit 0 |
| A2f (baseline) | 10:30:18 | 10:31:17 | 18단계, exit 0 |
| A3f (baseline) | 10:31:18 | 10:32:18 | 18단계, exit 0 |
| B3f (candidate) | 10:32:19 | 10:33:00 | 18단계, exit 0 |

v2는 다른 무거운 작업을 멈추도록 조율한 구간이다. 사전 CPU 15%·GPU 5%, 종료 CPU 1%·GPU 8%였고 두 점검에서 게임/테스트 Java 프로세스는 없었다. 종료 시 Prism 런처만 남았다. **연속 부하 기록은 없으므로 전체 구간에서 간섭이 완전히 없었다고 단정할 수 없다.** 운영 및 이전 대화형 서버 25578·51716·51718은 시작하지 않았다.

초기 `smoke-candidate.log`는 PowerShell에서 따옴표 없는 `-Psemiontd.algorithmProfile=true`가 분리돼 Gradle 단계에서 실패했다. 이후 문자열 배열로 인수를 전달했다. 초기 smoke 및 `A1`은 fixture v1 준비 실행이며 최종 비교에서 제외했다. pilot A1은 엔지니어 N=512도 틱당 1,024회여서 과도한 할당·시간이 발생했다. 최종 v2는 양쪽 동일하게 크기별 반복 수를 줄였다.

v3는 별도 조율한 창에서 2026-10-09 11:11:57~11:17:55 UTC에 여섯 실행을 마쳤다. `profile-v3-window-status.json`은 정상 종료와 여섯 JSONL의 18단계 fixture source/class·입력·출력·seed·반복 수·JVM 필드 동일성을 확인한다. 이 상태 파일의 당시 JFR 분석은 `PENDING`이었으며, 이후 아래 v3 공식 비교에서 여섯 기록을 검증했다. 전투 fixture나 전체 gate는 시작하지 않았고 측정 창을 반환했다.

v3 사전 CPU 17%·GPU 31%, 사후 CPU 23%·GPU 36%였다. 처음 사용자 Minecraft javaw 세 개(PID 32320/35916/38660)가 있었으며 자동 종료하지 않았다. 측정 도중 이 프로세스들이 종료되고 A3g 중간인 11:16:13.843 UTC에 새 javaw 35044가 시작됐다. 새 프로세스는 GameTest/Gradle이 아니다. 시스템 CPU 표본 356개는 최소 12.21%·중앙값 28.22%·최대 95.12%이며 벤치마크 자체 부하도 포함한다. `profile-v3-system-load.json` 및 `profile-v3-external-process-cpu.jsonl`이 근거다. 외부 CPU 수집은 A1 초반부터 시작했고 처음 세 PID만 대상으로 했으므로 새 PID 구간의 개별 CPU는 기록되지 않았다. **안정된 외부 부하를 단정하거나 클라이언트가 없던 v2와 섞지 않는다.** 아래 v3 분석은 이 제약을 유지한 상태에서 내부 A/B의 관측 범위만 판정한다.

빈 효과 N=0의 v3 임시 batch ns/op 원시값은 A1g/B1g 38.140625/5.045573, A2g/B2g 32.519531/8.432292, A3g/B3g 40.017578/9.578776이며 후보의 batch 할당은 세 실행 모두 0B/op다. 이는 최초 관측치이며 아래 JFR·통제군 분석과 함께 해석한다. 실전 성능 개선 판정에는 사용하지 않는다. 실행 출처는 `profile-v3-series.json`과 각 `*-run.json`에 있다.

## 측정한 변경 후보와 실제 호출 위치

N은 입력 또는 해당 효과의 기여 수, K는 허용 source 수이다. Big-O는 비교·순회 비용이고 객체 할당·간접 호출·JIT 효과는 별도 측정 대상이다.

| 후보 | 기존 구현 | 후보 구현 | 기대하는 비용 차이 | 보존하는 동작 |
|---|---|---|---|---|
| [EntityGoalTargetSelection.first](../src/main/java/kim/biryeong/semiontd/entity/goal/EntityGoalTargetSelection.java) | 작은 입력은 복사·전체 정렬. 큰 입력의 limit=1도 Candidate/PriorityQueue와 결과 목록 생성 | `limit == 1 && order != null`에 최초 원소부터 한 번 순회 | limit=1은 N−1회 비교. 큰 입력의 기존 점근 복잡도도 O(N)이므로 주 이점 후보는 할당 감소 | 첫 동률, nullable 원소를 처리하는 comparator, 결과의 독립성·가변성, null comparator의 기존 경로. 입력·RNG 불변 |
| [EngineerGolemTower.choosePlate](../src/main/java/kim/biryeong/semiontd/tower/engineer/EngineerGolemTower.java) | 후보 전체 `sorted(...).map(...).findFirst()` | 같은 필터·comparator의 `min(...).map(...)` | 최악 O(N log N) → O(N), 정렬용 저장 제거. 기존 comparator 내부 계산 비용은 남음 | 소유자·발판 종류·쿨다운 key·직전 발판 제외, 우선순위→거리²→원래 X→Z와 완전 동률의 최초 원소 |
| [TimedEffectSet.multiplicativeMagnitude](../src/main/java/kim/biryeong/semiontd/effect/TimedEffectSet.java) | Double 목록 수집, finite 필터, 전체 내림차순 정렬 | K=1 최대값 순회. 그 외 primitive 최소힙으로 상위 K 선택 후 K개만 정렬 | O(N log N)/O(N) 저장 → O(N log K + K log K)/O(K) 저장. K=1은 배열 없이 O(N). K≈N은 별도 측정 필요 | source별 중복 개수·finite 제외·clamp·상위 K, 내림차순의 **동일한 곱셈 순서** 및 `1−(1−x)` 단일값 반올림. 갱신·삭제·만료·조회 무변경 |

타깃 선택은 공용 entity goal 경로에서 호출된다. 골렘 발판 선택은 현재 발판이 없거나 유효하지 않을 때 재실행되며, 항상 매 틱 모든 골렘이 정렬한다고 가정하지 않는다. 방어 효과 계산은 [MagicSchoolSpellCombat.protection](../src/main/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolSpellCombat.java)과 [SemionTowerEntity](../src/main/java/kim/biryeong/semiontd/entity/tower/SemionTowerEntity.java)의 실제 피해 처리 및 상세 표시에서 사용한다. 자기 방어 K=1과 오라 K 설정의 비용을 구분한다.

이 변경에는 장기 캐시, 생명주기 invalidation, 수동 인라이닝, RNG 호출 또는 게임 배속 변경이 없다. HotSpot이 작은 메서드를 실제로 인라인했는지는 아직 확인하지 않았다. 소스에서 호출이 줄었다는 이유만으로 JIT 개선을 주장하지 않는다. [전체 빌더 구현 비교](production-tower-catalog.ko.md#빌더별-구현-방식-선택)의 책임 분리 장점 역시 성능 결과와 구분한다.

## 동작 보존 검사

- [EntityGoalTargetSelectionTest](../src/test/java/kim/biryeong/semiontd/entity/goal/EntityGoalTargetSelectionTest.java): 기존 안정 정렬 oracle, 입력 크기·limit 경계, 동률 identity, 순차 리스트의 N−1회 비교, 가변 반환 목록, null comparator, 다음 호출의 입력 변경.
- [EngineerGolemTargetSelectionTest](../src/test/java/kim/biryeong/semiontd/tower/engineer/EngineerGolemTargetSelectionTest.java): 실제 private 메서드와 기존 정렬 oracle 대조. 고정 seed 96개 fixture, 우선순위·거리·좌표·높이를 포함한 완전 동률, 소유자·종류·0/음수 값도 포함한 쿨다운 key·직전 발판 제외, 제거·상태 변경 반영.
- [TimedEffectSetTest](../src/test/java/kim/biryeong/semiontd/effect/TimedEffectSetTest.java): 고정 seed로 1,200회의 적용·갱신·제거·tick 연산 후 세 종류·아홉 제한을 기존 정렬 oracle과 **raw double bits**로 대조. NaN/무한대/경계값·단일값 반올림·만료·조회 불변성 포함. 오차 허용치를 도입하지 않았다.

위 검사는 기존·후보 구현에서 모두 실행해 통과했다. N=512, limit=1의 N−1회 비교는 기존 힙 경로도 만족하므로 후보만의 성능 개선 증거로 사용하지 않는다. 기존 lane 스냅샷·콜백 시점 멤버십·FIFO·복사/정리·하이퍼 캐리 계약을 수정하지 않았지만, 전체 release gate로 넓은 회귀 범위를 확인해야 한다.

### 최신 집중 검증과 v3 보완

| 클래스 | 기준 | 후보 v3 |
|---|---:|---:|
| TimedEffectSetTest | 7 통과 | 7 통과 |
| EntityGoalTargetSelectionTest | 9 통과 | 9 통과 |
| EngineerGolemTargetSelectionTest | 4 통과 | 4 통과 |
| 합계 | 20, 실패·오류·건너뜀 0 | 20, 실패·오류·건너뜀 0 |

후보 첫 suite XML 시각 `2026-10-09T10:43:44.600Z`, 기준 `2026-10-09T10:49:20.770Z`다. 근거는 `focused-v3-results\{baseline,candidate}\TEST-*.xml`, `focused-v3-{baseline,candidate}-results.json`, `{baseline,candidate}-v3-focused-junit.log`다. 다음 명령을 두 루트에서 순차 실행했다. Test는 단일 fork·512m heap·CPU 2개로 제한했다. 빌드·테스트 시간은 성능 A/B가 아니다.

```powershell
.\gradlew.bat compileJava compileTestJava test --tests 'kim.biryeong.semiontd.effect.TimedEffectSetTest' --tests 'kim.biryeong.semiontd.entity.goal.EntityGoalTargetSelectionTest' --tests 'kim.biryeong.semiontd.tower.engineer.EngineerGolemTargetSelectionTest' --offline --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1G -XX:ActiveProcessorCount=2' --init-script 'C:\Users\Kiruy\AppData\Local\Temp\semion-algorithm-checks-lgu0uoh5\resource-limits.gradle' --console=plain --no-daemon
```

v3는 timed/persistent source map이 모두 없을 때 공개 메서드에서 직접 반환하고 sourced 계산을 `sourcedMultiplicativeMagnitude`, 한 기여 계산을 `singleSourceMagnitude`로 분리했다. 효과가 없으면 0, 유효한 unsourced만 있으면 clamp와 `1 - (1 - x)` 반올림을 유지한다. unsourced 경계값 테스트도 limit 1/2/3/MAX로 확대했다. 힙·정렬 순서 및 다른 두 production 후보는 유지했다. v2 원본은 `candidate-production-v2\`에 있다. **성능 해결 판정은 새 A/B 이후다.**

## 측정 설계와 재현

[GameAlgorithmPerformanceTest](../src/gametest/java/kim/biryeong/semiontd/entity/goal/GameAlgorithmPerformanceTest.java)는 opt-in `SYNTHETIC_PATHS_ONLY`다. seed 26031009로 사전 생성한 16개 입력을 순환하고 oracle·input/output digest·호출 수·체크섬을 확인한다. 매 JVM 같은 18단계 순서, 단계별 warmup 80틱·측정 150틱이다.

| 경로 | N | 호출/틱 | 측정 호출/실행 |
|---|---|---|---|
| 효과 K=3, 타깃 K=1 | 6 / 32 / 128 / 512 | 각 1,024 | 각 153,600 |
| 엔지니어 발판 | 6 / 32 / 128 / 512 | 1,024 / 256 / 64 / 16 | 153,600 / 38,400 / 9,600 / 2,400 |
| 빈 효과 K=3 / 단일 효과 K=1 / 효과 N=32 K=1 | 0 / 1 / 32 | 각 1,024 | 각 153,600 |
| 통제: 효과 K=0 / 타깃 K=3 / 체크섬만 | 32 / 32 / 0 | 각 1,024 | 각 153,600 |

엔지니어 반복 수는 `max(16, base * 8 / max(8, N) / 16 * 16)`, base=1,024다. 같은 N의 A/B 호출 수는 동일하다. N 간 총시간을 그대로 비교하면 안 된다. bridge는 캐시한 MethodHandle이며 매 호출 reflection lookup이 아니다.

호스트 seed 0·설정 tick rate 20을 확인했지만 합성 world/lane은 null이고 entity가 없다. round는 해당 없음, speed는 headless throughput이다. GameTest는 20Hz로 제한하지 않아 20TPS 실전 결과로 해석할 수 없다. 테스트 구조물 좌표는 실행마다 달랐으며 합성 계산은 좌표를 사용하지 않는다. 동일 seed만으로 동일한 실전 world를 검증했다고 주장하지 않는다.

실제 인수·UTC·exit·run directory는 각 `*-run.json`에 있다. 다음은 A1f 인수를 PowerShell에 안전하게 인용한 형태다. 재실행은 새 manifest·라벨로 기존 산출물을 보존한다.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat runGameTest '-Psemiontd.algorithmProfile=true' '-Psemiontd.profileVariant=A1f' '-Psemiontd.profileRevision=8198987e8caaf3852f00701465f226fec5793212f1936ceb4ceb1dbd2b9f07ea' '-Psemiontd.profileSeed=26031009' '-Psemiontd.profileWorldSeed=0' '-Psemiontd.profileWarmupTicks=80' '-Psemiontd.profileSampleTicks=150' '-Psemiontd.profileRepetitions=1024' --offline --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx1G -XX:ActiveProcessorCount=4' --init-script 'C:\Users\Kiruy\Documents\Codex\2026-10-05\task\optimization-2026-10-09\profile-limits.gradle' --console=plain --no-daemon
```

필터는 `semion-td-gametest:game_algorithm_performance_test_measures_deterministic_algorithms`다. 일반 전체 gate에서 opt-in 없이 `ALGORITHM_PROFILE_NOT_RUN`으로 통과하는 것은 성능 검증이 아니다. `build/26.3-gametest-run-dir.txt`는 마지막 실행만 가리켜 과거 출처는 개별 metadata를 사용한다.

| 지표 | 범위·한계 |
|---|---|
| CPU ns/op | 서버 스레드 ThreadMXBean batch 전후 ÷ 호출 수. batch·체크섬·counter 포함. 전체 프로세스 CPU 아님 |
| 할당 B/op | 같은 스레드 allocation counter 차이. 입력 사전 구성·다른 스레드 제외, heap 증가량 아님 |
| batch·active ops/s | batch nanoTime, checksum·bridge 포함. 메서드 단독 비용·CPU 시간 아님 |
| wall ops/s | 전체 창 wall time, 호스트·대기·JIT 영향 포함 |
| host MSPT | batch의 이전 완료 tick ring. 합성 GameTest이며 실제 전투 MSPT 아님 |
| JVM GC count/ms | 창의 VM 전체 counter. 다른 작업·JFR·이전 할당 영향 포함. 0회가 부담 0의 증거는 아님 |
| JFR | custom stage begin/end 구간. 경계 GC는 겹친 duration만. 할당 weight는 샘플 추정치이며 직접 batch B/op와 다름 |

## v2 결과

각 JVM 3회의 중앙값·범위·CV는 기술 통계이며 유의성 검정·합격 기준이 아니다. CV는 모집단 표준편차/평균이고 평균 0이면 N/A다. flags도 검토 신호다. 개별값·추가 p50/p99/max·짝별 변화는 `comparison-v2.json`, `comparison-v2-{individual,summary,pairs}.csv`에 있다.

### CPU·할당·합성 host p95·active 처리량

108개 단계 CPU 값의 공약수는 **15,625,000ns(15.625ms)**이고 22개는 0이었다. 0은 무비용이 아니라 해상도 미확정이며 비율을 표시하지 않는다. 1~3 quantum의 양수도 ns/op 정밀도를 뜻하지 않는다. **CV 0도 같은 눈금에 모인 결과일 수 있어 안정성 증거가 아니다.** 작은 입력은 다음 batch 분포·통제군과 함께 해석한다.

| Workload | N | Ops/tick | CPU ns/op A → B | CPU median change | CPU CV% A / B | Bytes/op A → B | Host p95 ms A → B | Active ops/s change | Flags |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| effects_top_three [low input] | 6 | 1024 | 0.000 → 101.725 | N/A (CPU counter resolution; raw delta +101.7) | 141.42 / 81.65 | 184.001 → 97.533 | 0.308 → 0.455 | -20.15% | CPU_COUNTER_RESOLUTION_UNRESOLVED, HIGH_RUN_VARIATION, LOW_INPUT_REGRESSION_SIGNAL |
| target_limit_one [low input] | 6 | 1024 | 203.451 → 0.000 | N/A (CPU counter resolution; raw delta -203.5) | 20.20 / 141.42 | 80.000 → 48.000 | 0.372 → 0.168 | +333.55% | CPU_COUNTER_RESOLUTION_UNRESOLVED, HIGH_RUN_VARIATION |
| engineer_choose_plate [low input] | 6 | 1024 | 3,356.934 → 1,932.780 | -42.42% | 8.78 / 4.30 | 11,254.000 → 7,413.500 | 4.372 → 2.833 | +72.28% | HIGH_RUN_VARIATION |
| effects_top_three | 32 | 1024 | 1,118.978 → 101.725 | -90.91% | 4.42 / 35.36 | 1,088.000 → 40.000 | 1.194 → 0.243 | +621.18% | HIGH_RUN_VARIATION |
| target_limit_one | 32 | 1024 | 406.901 → 406.901 | +0.00% | 49.21 / 12.86 | 232.000 → 48.000 | 0.797 → 0.401 | +77.76% | HIGH_RUN_VARIATION |
| engineer_choose_plate | 32 | 256 | 26,855.469 → 10,986.328 | -59.09% | 5.58 / 10.78 | 94,152.500 → 37,991.000 | 7.765 → 3.479 | +160.65% | HIGH_RUN_VARIATION |
| effects_top_three | 128 | 1024 | 6,001.790 → 610.352 | -89.83% | 3.13 / 22.01 | 3,712.000 → 40.000 | 9.287 → 0.592 | +1243.17% | HIGH_RUN_VARIATION |
| target_limit_one | 128 | 1024 | 1,525.879 → 1,118.978 | -26.67% | 16.33 / 8.08 | 176.000 → 48.000 | 1.561 → 1.297 | +28.31% | HIGH_RUN_VARIATION |
| engineer_choose_plate | 128 | 64 | 148,111.979 → 42,317.708 | -71.43% | 5.82 / 0.00 | 541,236.500 → 149,788.500 | 11.443 → 2.960 | +260.55% | HIGH_RUN_VARIATION |
| effects_top_three | 512 | 1024 | 42,419.434 → 2,034.505 | -95.20% | 1.30 / 2.40 | 15,144.000 → 40.000 | 50.463 → 2.262 | +2050.76% | HIGH_RUN_VARIATION |
| target_limit_one | 512 | 1024 | 6,001.790 → 4,781.087 | -20.34% | 8.87 / 1.01 | 176.000 → 48.000 | 6.718 → 5.131 | +25.49% | HIGH_RUN_VARIATION |
| engineer_choose_plate | 512 | 16 | 820,312.500 → 169,270.833 | -79.37% | 2.99 / 3.72 | 2,922,446.500 → 604,271.000 | 14.845 → 2.826 | +383.41% | HIGH_RUN_VARIATION, REGRESSION_SIGNAL |
| effects_top_three [low input] | 0 | 1024 | 0.000 → 101.725 | N/A (CPU counter resolution; raw delta +101.7) | N/A / 0.00 | 24.000 → 0.000 | 0.202 → 0.120 | -78.07% | CPU_COUNTER_RESOLUTION_UNRESOLVED, HIGH_RUN_VARIATION, LOW_INPUT_REGRESSION_SIGNAL |
| effects_top_one [low input] | 1 | 1024 | 0.000 → 101.725 | N/A (CPU counter resolution; raw delta +101.7) | 141.42 / 70.71 | 104.000 → 0.000 | 0.034 → 0.051 | -51.15% | CPU_COUNTER_RESOLUTION_UNRESOLVED, HIGH_RUN_VARIATION, LOW_INPUT_REGRESSION_SIGNAL |
| effects_top_one | 32 | 1024 | 1,118.978 → 305.176 | -72.73% | 11.79 / 17.68 | 1,088.000 → 64.000 | 1.708 → 0.314 | +336.62% | HIGH_RUN_VARIATION |
| control_effects_limit_zero [control] | 32 | 1024 | 0.000 → 0.000 | N/A (CPU counter resolution; raw delta +0) | 141.42 / N/A | 0.000 → 0.000 | 0.033 → 0.020 | +43.62% | CPU_COUNTER_RESOLUTION_UNRESOLVED, HIGH_RUN_VARIATION, NEGATIVE_CONTROL_DRIFT |
| control_target_limit_three [control] | 32 | 1024 | 712.077 → 610.352 | -14.29% | 11.66 / 22.01 | 256.000 → 262.400 | 0.751 → 0.919 | +15.63% | HIGH_RUN_VARIATION, NEGATIVE_CONTROL_DRIFT, REGRESSION_SIGNAL |
| control_checksum_only [control] | 0 | 1024 | 0.000 → 0.000 | N/A (CPU counter resolution; raw delta +0) | N/A / N/A | 0.000 → 0.000 | 0.021 → 0.016 | -0.99% | CPU_COUNTER_RESOLUTION_UNRESOLVED, HIGH_RUN_VARIATION, NEGATIVE_CONTROL_DRIFT |

### Batch 시간과 반복 변동

각 실행의 평균 batch 시간을 호출 수로 나눴다. 대괄호는 세 JVM의 최소·최대다. 비용 기준 음수는 감소, 양수는 증가다.

| 경로 | N | A batch ns/op 중앙값 [최소, 최대] | B batch ns/op 중앙값 [최소, 최대] | batch CV% A / B | A1→B1 / A2→B2 / A3→B3 변화 |
|---|---:|---:|---:|---:|---:|
| effects_top_three | 6 | 130.29 [128.98, 173.20] | 163.17 [143.81, 195.70] | 14.25 / 12.78 | +50.20% / +26.50% / -16.97% |
| target_limit_one | 6 | 209.24 [152.59, 257.63] | 48.26 [45.84, 63.92] | 20.79 / 15.21 | -58.11% / -76.93% / -82.21% |
| engineer_choose_plate | 6 | 3360.58 [3103.52, 3903.16] | 1950.70 [1919.23, 2063.31] | 9.65 / 3.13 | -33.52% / -42.89% / -50.02% |
| effects_top_three | 32 | 1113.93 [1108.54, 1158.95] | 154.46 [153.38, 249.73] | 2.01 / 24.30 | -86.23% / -77.47% / -86.67% |
| target_limit_one | 32 | 485.84 [379.62, 894.99] | 273.32 [271.00, 307.97] | 37.86 / 5.95 | -65.59% / -28.00% / -44.22% |
| engineer_choose_plate | 32 | 27739.49 [26322.02, 30288.67] | 10642.49 [10499.47, 13539.81] | 5.84 / 12.12 | -48.56% / -62.15% / -64.86% |
| effects_top_three | 128 | 6311.38 [5943.70, 6434.85] | 469.89 [466.64, 638.98] | 3.35 / 15.33 | -89.25% / -92.70% / -92.61% |
| target_limit_one | 128 | 1419.99 [1176.06, 1701.16] | 1106.71 [1089.38, 1200.20] | 14.98 / 4.30 | +2.05% / -23.28% / -34.94% |
| engineer_choose_plate | 128 | 153945.60 [143521.62, 164620.08] | 42696.99 [41860.26, 43997.31] | 5.59 / 2.05 | -70.25% / -71.42% / -74.57% |
| effects_top_three | 512 | 42671.35 [42197.85, 43559.44] | 1984.01 [1947.09, 2084.96] | 1.32 / 2.91 | -95.06% / -95.45% / -95.44% |
| target_limit_one | 512 | 5890.94 [5107.90, 6046.79] | 4694.44 [4548.00, 4754.27] | 7.23 / 1.86 | -8.09% / -22.80% / -21.38% |
| engineer_choose_plate | 512 | 821059.83 [779939.12, 844611.38] | 169847.08 [165073.46, 170980.71] | 3.28 / 1.52 | -78.22% / -79.90% / -79.76% |
| effects_top_three | 0 | 20.81 [19.83, 21.10] | 94.90 [94.60, 104.65] | 2.64 / 4.76 | +427.72% / +349.79% / +354.64% |
| effects_top_one | 1 | 13.13 [13.13, 20.18] | 26.88 [15.13, 30.99] | 21.47 / 27.61 | +104.74% / +135.97% / -25.00% |
| effects_top_one | 32 | 1141.34 [1060.45, 1524.95] | 261.40 [260.70, 275.08] | 16.31 / 2.49 | -74.06% / -82.90% / -77.10% |
| control_effects_limit_zero | 32 | 6.74 [5.44, 9.11] | 4.69 [4.48, 4.76] | 21.43 / 2.60 | -48.53% / -12.43% / -33.51% |
| control_target_limit_three | 32 | 662.00 [569.17, 754.27] | 572.51 [551.86, 671.74] | 11.42 / 8.74 | +18.02% / -13.52% / -26.84% |
| control_checksum_only | 0 | 2.72 [2.72, 4.13] | 2.75 [2.75, 2.75] | 20.81 / 0.08 | +1.05% / +1.17% / -33.42% |

### GC와 wall 처리량

GC는 각 실행의 원시 순서 A1,A2,A3 / B1,B2,B3다. N에 따라 호출 수가 달라 행 간 총시간은 동일 실전 workload가 아니다. 처리량은 해당 분모의 기술 통계이며 지속 TPS가 아니다.

| 경로 | N | GC 횟수 A1,A2,A3 / B1,B2,B3 | GC ms A1,A2,A3 / B1,B2,B3 | wall ops/s 중앙값 변화 |
|---|---:|---:|---:|---:|
| effects_top_three | 6 | 0,0,0 / 0,0,0 | 0,0,0 / 0,0,0 | -18.57% |
| target_limit_one | 6 | 0,0,0 / 0,0,0 | 0,0,0 / 0,0,0 | +119.55% |
| engineer_choose_plate | 6 | 1,2,2 / 1,0,1 | 12,18,25 / 13,0,4 | +72.94% |
| effects_top_three | 32 | 1,1,0 / 0,1,0 | 11,9,0 / 0,11,0 | +482.87% |
| target_limit_one | 32 | 0,0,0 / 0,0,0 | 0,0,0 / 0,0,0 | +67.07% |
| engineer_choose_plate | 32 | 3,5,3 / 1,1,1 | 36,34,32 / 11,9,4 | +158.44% |
| effects_top_three | 128 | 1,3,0 / 0,0,0 | 4,8,0 / 0,0,0 | +1139.64% |
| target_limit_one | 128 | 0,0,0 / 0,0,0 | 0,0,0 / 0,0,0 | +26.25% |
| engineer_choose_plate | 128 | 4,6,5 / 1,2,1 | 14,16,16 / 11,19,4 | +257.77% |
| effects_top_three | 512 | 2,4,2 / 0,0,0 | 4,8,4 / 0,0,0 | +2015.81% |
| target_limit_one | 512 | 0,0,0 / 0,0,0 | 0,0,0 / 0,0,0 | +25.42% |
| engineer_choose_plate | 512 | 5,9,6 / 1,1,1 | 6,20,7 / 12,10,3 | +381.30% |
| effects_top_three | 0 | 0,0,0 / 0,0,0 | 0,0,0 / 0,0,0 | -69.53% |
| effects_top_one | 1 | 0,0,0 / 0,0,0 | 0,0,0 / 0,0,0 | -32.71% |
| effects_top_one | 32 | 1,1,0 / 0,0,0 | 1,1,0 / 0,0,0 | +319.18% |
| control_effects_limit_zero | 32 | 0,0,0 / 0,0,0 | 0,0,0 / 0,0,0 | +27.76% |
| control_target_limit_three | 32 | 0,1,0 / 0,0,0 | 0,0,0 / 0,0,0 | +16.11% |
| control_checksum_only | 0 | 0,0,0 / 0,0,0 | 0,0,0 / 0,0,0 | +10.46% |

## 개선·회귀·통제군 해석

**엔지니어는 가장 일관된 후보다.** N=6/32/128/512 모든 짝에서 batch가 빨랐고 CPU 중앙값은 42.42%/59.09%/71.43%/79.37%, 할당은 약 34%/60%/72%/79% 감소했다. comparator의 종류 조회·중심 위치 계산 등 기존 비용도 포함한다. 모든 GC 지표가 개선된 것은 아니다. N=512 A1→B1 JFR GC 정지 겹침은 5.708→12.586ms, N=128 A2→B2는 16.233→18.292ms로 늘었다. 같은 짝 batch 비용은 약 78%·71% 감소했다.

**큰 효과 입력은 개선됐지만 v2 빈 입력은 회귀다.** K=3·N=32/128/512 CPU 중앙값은 90.91%/89.83%/95.20% 감소, 할당은 1,088/3,712/15,144B에서 각 40B가 됐다. N=0 batch는 20.81→94.90ns/op, 짝별 +427.72%/+349.79%/+354.64%로 모두 느렸다. 여섯 실행 모두 해당 GC는 0이다. batch p50도 기준 7.5~9.0μs에서 후보 96.6~104.4μs로 늘어 드문 최댓값만의 현상이 아니다. 큰 입력 이득으로 회귀를 상쇄해 완료 처리하지 않았다.

N=1·K=1은 13.13→26.88ns/op, 짝별 +104.74%/+135.97%/−25.00%다. N=6·K=3도 +50.20%/+26.50%/−16.97%로 섞였다. 작은 입력 전체의 일관된 회귀는 확정하지 않는다. N=1 B2 p50은 약 10.1μs로 A2 12.5μs보다 작지만 평균 약 31.7μs·p95 148.2μs로 긴 꼬리가 있었다. 짧은 warmup/JIT 변화 가능성은 가설이며 원인은 미확정이다.

**타깃 K=1은 할당과 대부분 batch가 개선됐지만 모든 짝이 개선된 것은 아니다.** 후보는 모든 크기 48B/op다. N=6/32/512 세 짝은 모두 빨랐고 N=128은 +2.05%/−23.28%/−34.94%다. N=128/512 CPU 중앙값은 26.67%/20.34% 감소했지만 작은 경로 CPU 감소율은 해상도 제약을 받는다. 추가 구조 변경 없이 유지한 후보이며 실전 효과는 미확인이다.

**변경하지 않은 통제도 흔들렸다.** 효과 K=0 active 처리량 중앙값 +43.62%, 타깃 K=3 batch 중앙값 약 −13.52%이나 짝별 +18.02%/−13.52%/−26.84%다. 타깃 K=3 host p95는 0.751→0.919ms로 악화했다. 체크섬만의 active 처리량 중앙값 −0.99%, A3는 다른 기준 실행보다 느렸다. 통제 값을 빼서 보정하거나 작은 차이를 모두 코드 효과로 귀속하지 않았다.

### JFR·JIT와 측정 경계

| 실행 | 검증 구간 | 서버 Java 샘플 | 샘플 없는 구간 | 구간 GC 정지 겹침 합계 ms |
|---|---:|---:|---:|---:|
| A1 | 18 | 1241 | 2 | 86.243 |
| B1 | 18 | 229 | 1 | 48.618 |
| B2 | 18 | 271 | 3 | 48.017 |
| A2 | 18 | 1302 | 3 | 113.984 |
| A3 | 18 | 907 | 5 | 83.796 |
| B3 | 18 | 276 | 3 | 14.730 |

18개 측정 구간 합계다. 샘플 감소에는 측정 시간 감소도 반영되므로 샘플 감소율을 CPU 개선율로 쓰지 않는다. 원시 JFR과 `jfr print --json` export를 보존했고 최종 검증은 `ALL_SIX_VALIDATED`다.

- 대상 호출과 `EngineerTowers.findKey` 비용이 잡혔으며 호스트 서비스도 포함됐다. K=0 통제에는 `SemionTipService.tick`이 관찰됐다. 빈 효과 B1의 batch 직접 할당은 0인데 stage JFR weight는 약 1MB다. JFR weight를 batch B/op로 대체하지 않는다.
- N=6 효과 A1의 완료 tick 합계는 약 89.306ms, wall은 38.248ms다. tick에는 창 시작 준비가 포함되고 첫 host 최대는 54.208ms다. 범위 차이를 실전 지연으로 해석하지 않는다.
- 80틱 warmup은 고정 시간·JIT 안정 보장이 아니다. 빠른 N=1에서는 약 1ms일 수 있다. 150개 batch 원시 시계열 전체는 저장하지 않아 재컴파일과 꼬리를 직접 맞출 수 없다.
- v2 javap 메서드 크기는 효과 186→501바이트, 타깃 299→383바이트다. JVM `FreqInlineSize=325`, `MaxInlineSize=35`를 넘는 크기가 인라이닝에 영향을 줄 수 있다는 **가설**이다. 기본 JFR에는 실제 inlining 결정 로그가 없으며 크기만으로 원인을 확정하지 않는다.
- 추가 원인 분석은 긴 warmup·최소 측정 시간·원시 batch 시계열로 작은 경로와 통제군을 검사한다. `PrintInlining` 진단은 타이밍 실행과 분리한다. v3 첫 재측정은 같은 18단계 fixture로 비교한다.

## v3 재측정 결과와 남은 반례

v3도 여섯 실행 × 18단계의 input/output·fixture source/class·호출 수·JVM 필드를 대조했고 JFR stage까지 `ALL_SIX_VALIDATED`다. `comparison-v3.{json,md}`와 `comparison-v3-{individual,summary,pairs}.csv`가 최종 집계다. 앞의 외부 클라이언트 교체·CPU 표본 누락은 그대로 한계다. **v3 내부 비교에서 빈 효과의 이전 회귀는 재현되지 않았지만, 타깃 통제 경로 할당 증가가 남아 최적화 전체를 완료 판정하지 않는다.**

v3 CPU도 15.625ms 양자이며 108개 중 26개가 0, 48개가 3양자 이하다. 양수여도 작은 CPU 비용과 CV 0을 정밀·안정 증거로 해석하지 않는다. 표는 v2와 같은 범위·단위이며 합성 host MSPT를 전투 MSPT로 바꾸어 읽지 않는다.

### v3 CPU·할당·host p95·active 처리량


| Workload | N | Ops/tick | CPU ns/op A → B | CPU median change | CPU CV% A / B | Bytes/op A → B | Host p95 ms A → B | Active ops/s change | Flags |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| effects_top_three [low input] | 6 | 1024 | 305.176 → 101.725 | -66.67% | 40.41 / 0.00 | 184.001 → 40.001 | 0.494 → 0.414 | +80.55% | HIGH_RUN_VARIATION |
| target_limit_one [low input] | 6 | 1024 | 305.176 → 101.725 | N/A (CPU counter resolution; raw delta -203.5) | 0.00 / 81.65 | 80.000 → 48.000 | 0.491 → 0.213 | +326.46% | CPU_COUNTER_RESOLUTION_UNRESOLVED, HIGH_RUN_VARIATION |
| engineer_choose_plate [low input] | 6 | 1024 | 3,763.835 → 2,136.230 | -43.24% | 10.04 / 2.21 | 11,254.000 → 7,413.500 | 6.197 → 3.229 | +79.78% | HIGH_RUN_VARIATION |
| effects_top_three | 32 | 1024 | 1,118.978 → 203.451 | -81.82% | 15.29 / 28.28 | 1,088.000 → 40.000 | 1.756 → 0.356 | +524.82% | HIGH_RUN_VARIATION |
| target_limit_one | 32 | 1024 | 305.176 → 305.176 | +0.00% | 0.00 / 25.71 | 176.000 → 48.000 | 0.805 → 0.635 | +4.54% | HIGH_RUN_VARIATION |
| engineer_choose_plate | 32 | 256 | 30,110.677 → 12,207.031 | -59.46% | 21.08 / 4.20 | 94,152.500 → 37,991.000 | 11.818 → 3.954 | +155.50% | HIGH_RUN_VARIATION |
| effects_top_three | 128 | 1024 | 6,306.966 → 508.626 | -91.94% | 14.77 / 26.73 | 3,712.000 → 40.000 | 9.961 → 0.804 | +1151.85% | HIGH_RUN_VARIATION |
| target_limit_one | 128 | 1024 | 1,729.329 → 1,322.428 | -23.53% | 18.40 / 13.16 | 176.000 → 48.000 | 2.716 → 2.234 | +20.26% | HIGH_RUN_VARIATION |
| engineer_choose_plate | 128 | 64 | 151,367.188 → 47,200.521 | -68.82% | 8.38 / 6.00 | 541,236.500 → 149,788.500 | 12.696 → 4.424 | +227.99% | HIGH_RUN_VARIATION |
| effects_top_three | 512 | 1024 | 46,081.543 → 2,237.956 | -95.14% | 5.33 / 10.29 | 15,144.000 → 40.000 | 59.277 → 2.553 | +2036.11% | HIGH_RUN_VARIATION |
| target_limit_one | 512 | 1024 | 8,036.296 → 5,086.263 | -36.71% | 24.02 / 10.52 | 176.000 → 48.000 | 9.973 → 5.966 | +60.43% | HIGH_RUN_VARIATION |
| engineer_choose_plate | 512 | 16 | 859,375.000 → 175,781.250 | -79.55% | 18.56 / 4.56 | 2,922,446.500 → 604,271.000 | 18.284 → 3.577 | +375.15% | HIGH_RUN_VARIATION |
| effects_top_three [low input] | 0 | 1024 | 0.000 → 0.000 | N/A (CPU counter resolution; raw delta +0) | 141.42 / N/A | 24.000 → 0.000 | 0.211 → 0.038 | +352.32% | CPU_COUNTER_RESOLUTION_UNRESOLVED, HIGH_RUN_VARIATION |
| effects_top_one [low input] | 1 | 1024 | 0.000 → 0.000 | N/A (CPU counter resolution; raw delta +0) | N/A / N/A | 104.000 → 0.000 | 0.057 → 0.040 | +131.74% | CPU_COUNTER_RESOLUTION_UNRESOLVED, HIGH_RUN_VARIATION, LOW_INPUT_REGRESSION_SIGNAL |
| effects_top_one | 32 | 1024 | 1,322.428 → 0.000 | N/A (CPU counter resolution; raw delta -1322) | 22.64 / 141.42 | 1,088.000 → 0.000 | 2.224 → 0.199 | +1083.77% | CPU_COUNTER_RESOLUTION_UNRESOLVED, HIGH_RUN_VARIATION |
| control_effects_limit_zero [control] | 32 | 1024 | 0.000 → 0.000 | N/A (CPU counter resolution; raw delta +0) | N/A / N/A | 0.000 → 0.000 | 0.041 → 0.040 | +37.57% | CPU_COUNTER_RESOLUTION_UNRESOLVED, HIGH_RUN_VARIATION, NEGATIVE_CONTROL_DRIFT |
| control_target_limit_three [control] | 32 | 1024 | 712.077 → 610.352 | -14.29% | 7.07 / 8.32 | 256.000 → 312.000 | 0.988 → 0.731 | +16.87% | HIGH_RUN_VARIATION, NEGATIVE_CONTROL_DRIFT, REGRESSION_SIGNAL |
| control_checksum_only [control] | 0 | 1024 | 0.000 → 0.000 | N/A (CPU counter resolution; raw delta +0) | N/A / N/A | 0.000 → 0.000 | 0.027 → 0.029 | -10.23% | CPU_COUNTER_RESOLUTION_UNRESOLVED, HIGH_RUN_VARIATION, NEGATIVE_CONTROL_DRIFT, REGRESSION_SIGNAL |

### v3 batch 시간과 변동

대괄호는 세 JVM의 최소·최대다. 짝별 비용 증감과 CV를 중앙값과 함께 제시한다.


| 경로 | N | A batch ns/op 중앙값 [최소, 최대] | B batch ns/op 중앙값 [최소, 최대] | CV% A / B | A1→B1 / A2→B2 / A3→B3 변화 |
|---|---:|---:|---:|---:|---:|
| effects_top_three | 6 | 180.32 [175.53, 189.14] | 99.87 [97.88, 115.79] | 3.10 / 7.67 | -43.10% / -35.78% / -48.25% |
| target_limit_one | 6 | 318.03 [182.19, 321.99] | 74.58 [62.89, 77.62] | 23.71 / 8.86 | -80.23% / -57.39% / -76.84% |
| engineer_choose_plate | 6 | 3788.77 [3199.56, 4334.48] | 2107.50 [2076.06, 2387.89] | 12.28 / 6.40 | -36.97% / -35.11% / -51.38% |
| effects_top_three | 32 | 1136.75 [1099.91, 1449.40] | 181.93 [136.35, 218.61] | 12.76 / 18.80 | -80.12% / -84.00% / -90.59% |
| target_limit_one | 32 | 421.22 [322.89, 508.04] | 402.91 [337.16, 423.40] | 18.12 / 9.49 | -4.35% / +31.13% / -33.64% |
| engineer_choose_plate | 32 | 30189.02 [28828.65, 45506.77] | 11815.47 [11765.75, 12723.10] | 21.70 / 3.64 | -57.86% / -59.01% / -74.15% |
| effects_top_three | 128 | 6210.55 [6162.93, 8382.28] | 496.11 [465.64, 601.57] | 14.96 / 11.18 | -90.24% / -92.01% / -94.44% |
| target_limit_one | 128 | 1679.07 [1214.63, 1864.59] | 1396.20 [1247.04, 1726.42] | 17.24 / 13.75 | -25.73% / +42.14% / -25.12% |
| engineer_choose_plate | 128 | 155171.54 [148015.29, 175006.36] | 47309.95 [42375.00, 50302.43] | 7.16 / 7.00 | -69.51% / -66.02% / -75.79% |
| effects_top_three | 512 | 46124.61 [41959.62, 48471.88] | 2159.28 [1779.90, 2220.96] | 5.92 / 9.50 | -95.18% / -94.85% / -96.33% |
| target_limit_one | 512 | 8003.69 [4820.46, 9388.27] | 4988.77 [4685.23, 5964.35] | 25.83 / 10.47 | -41.46% / +23.73% / -46.86% |
| engineer_choose_plate | 512 | 853591.54 [780983.33, 1332000.75] | 179648.50 [174525.12, 203755.29] | 24.72 / 6.85 | -79.55% / -77.00% / -84.70% |
| effects_top_three | 0 | 38.14 [32.52, 40.02] | 8.43 [5.05, 9.58] | 8.64 / 25.04 | -86.77% / -74.07% / -76.06% |
| effects_top_one | 1 | 19.46 [14.82, 21.88] | 8.40 [8.30, 9.70] | 15.67 / 7.28 | -50.13% / -44.00% / -61.64% |
| effects_top_one | 32 | 1429.83 [1061.83, 1680.78] | 120.79 [98.27, 155.74] | 18.28 / 18.93 | -89.11% / -88.62% / -94.15% |
| control_effects_limit_zero | 32 | 7.83 [4.98, 10.84] | 5.69 [3.80, 8.74] | 30.36 / 33.50 | -64.96% / +14.32% / +11.62% |
| control_target_limit_three | 32 | 699.66 [548.96, 833.79] | 598.69 [578.30, 770.08] | 16.76 / 13.25 | -14.43% / +40.28% / -30.64% |
| control_checksum_only | 0 | 3.41 [2.76, 4.50] | 3.80 [3.78, 5.89] | 20.13 / 22.04 | +72.66% / +36.80% / -15.56% |

### v3 GC·wall 처리량


| 경로 | N | GC 횟수 A1,A2,A3 / B1,B2,B3 | GC ms A1,A2,A3 / B1,B2,B3 | wall ops/s 중앙값 변화 |
|---|---:|---:|---:|---:|
| effects_top_three | 6 | 0,0,0 / 0,0,0 | 0,0,0 / 0,0,0 | +16.04% |
| target_limit_one | 6 | 1,0,0 / 0,0,0 | 14,0,0 / 0,0,0 | +150.06% |
| engineer_choose_plate | 6 | 2,2,2 / 1,1,1 | 19,19,20 / 13,4,11 | +74.16% |
| effects_top_three | 32 | 0,0,2 / 0,0,0 | 0,0,7 / 0,0,0 | +396.09% |
| target_limit_one | 32 | 0,0,0 / 0,0,0 | 0,0,0 / 0,0,0 | +5.35% |
| engineer_choose_plate | 32 | 3,5,5 / 1,1,3 | 13,32,25 / 11,4,19 | +151.98% |
| effects_top_three | 128 | 0,0,1 / 0,0,0 | 0,0,6 / 0,0,0 | +1067.38% |
| target_limit_one | 128 | 0,0,0 / 0,0,0 | 0,0,0 / 0,0,0 | +18.73% |
| engineer_choose_plate | 128 | 4,7,6 / 1,1,1 | 14,19,18 / 13,5,10 | +223.81% |
| effects_top_three | 512 | 2,2,4 / 0,0,0 | 4,1,10 / 0,0,0 | +1990.01% |
| target_limit_one | 512 | 0,0,0 / 0,0,0 | 0,0,0 / 0,0,0 | +59.94% |
| engineer_choose_plate | 512 | 5,10,8 / 1,1,3 | 6,16,18 / 14,4,18 | +370.44% |
| effects_top_three | 0 | 0,0,0 / 0,0,0 | 0,0,0 / 0,0,0 | +162.03% |
| effects_top_one | 1 | 0,0,0 / 0,0,0 | 0,0,0 / 0,0,0 | +45.90% |
| effects_top_one | 32 | 0,0,0 / 0,0,0 | 0,0,0 / 0,0,0 | +903.38% |
| control_effects_limit_zero | 32 | 0,0,0 / 0,0,0 | 0,0,0 / 0,0,0 | +3.75% |
| control_target_limit_three | 32 | 0,0,0 / 0,0,0 | 0,0,0 / 0,0,0 | +15.41% |
| control_checksum_only | 0 | 0,0,0 / 0,0,0 | 0,0,0 / 0,0,0 | -29.29% |

### v3 판정

- **빈/작은 효과:** N=0 batch 중앙값 38.14→8.43ns/op, 세 짝 −86.77%/−74.07%/−76.06%다. N=1·K=1은 19.46→8.40ns/op, −50.13%/−44.00%/−61.64%다. 후보의 두 경로 할당은 모두 0B/op다. N=6·K=3도 세 짝 batch가 감소했고 할당은 약 184→40B/op다. v3의 빈 효과 회귀는 이번 합성 비교에서 재현되지 않았다. CPU 자체는 짧아 해상도 제약을 받으며 실전/무부하 확정 개선은 아니다. N=1 첫 짝 host 최대값은 0.1069→0.1286ms로 증가해 모든 꼬리 지표가 좋아진 것도 아니다.
- **엔지니어:** 모든 크기·세 짝에서 batch 감소, CPU 중앙값은 N=6/32/128/512 각각 −43.24%/−59.46%/−68.82%/−79.55%다. 할당은 v2와 같은 크기별 감소를 보였다. GC는 예외가 있다. N=512 JFR 정지 겹침 A1→B1은 6.225→13.915ms, A3→B3는 17.145→18.041ms로 증가했다. 큰 비용 감소가 GC 전 지표 개선을 뜻하지 않는다.
- **타깃 K=1:** 후보 할당은 모두 48B/op로 줄었으나 N=32/128/512 두 번째 짝 batch는 +31.13%/+42.14%/+23.73%로 악화했다. 나머지 두 짝은 감소했다. 중앙값 감소만으로 일관된 성능 개선이라 하지 않는다.
- **타깃 K=3 통제의 할당 반례:** 기준 A1/A2/A3는 256/280/256B/op, 후보 B1/B2/B3는 312/344/312B/op다. 각 +56/+64/+56B/op, +21.88%/+22.86%/+21.88%로 세 짝 모두 증가했다. batch는 −14.43%/+40.28%/−30.64%로 섞였다. 변경하지 않은 분기를 타더라도 같은 메서드의 형태가 JIT에 영향을 줄 가능성이 있으나 아직 원인은 확정하지 않았다. 외부 CPU 부하만으로 할당 증가를 무시하지 않는다. 이 반례와 K=1 일부 시간 역전을 근거로 타깃 후보를 철회했다.
- **그 외 통제:** 효과 K=0 batch는 −64.96%/+14.32%/+11.62%, 체크섬만은 +72.66%/+36.80%/−15.56%다. 체크섬 host 평균은 세 짝 모두 증가했고 wall 처리량도 세 짝 모두 감소했다. 통제 변화는 빼서 보정하지 않으며 작은 차이의 인과 해석을 제한한다.

JFR의 K=3 측정 구간 stack frame에서 `EntityGoalTargetSelection.first`는 v3 기준 A1/A2/A3가 모두 `Inlined`(28/22/26 표본), 후보 B1/B2/B3가 모두 `JIT compiled`(24/14/9 표본)로 관찰됐다. v2도 기준은 Inlined 8/30/13, 후보는 JIT compiled 18/22/25 표본으로 상태 차이가 반복됐다. **표본 내 프레임 상태 차이는 확인했지만 메서드 크기 때문에 인라이닝이 거절됐다는 원인은 확정하지 않았다.** `jdk.CompilerInlining` 이벤트나 escape-analysis 결정 로그가 없다. v2의 K=3 할당은 256/312/256→288.331/262.4/256B/op로 일관되게 늘지 않았으며, 세 쌍 약 22% 증가는 v3에서 확인한 사실이다. 독립 검토의 원시 수치·프레임 근거는 `v3-independent-measurement-review.json`에 있다. 동일한 자료에서 발견된 개선과 반례를 함께 남긴다. 타깃 후보는 철회했다. 기존 큰 입력의 K=1도 이미 O(N)이었고, K=3 할당의 세 쌍 일관 증가와 K=1 일부 시간 역전을 감수할 충분한 근거가 없다는 판단이다. v3 판정 당시 최종 두 변경 조합은 별도로 미측정 상태였다. 이후 v4에서 최종 두 변경을 직접 측정했으며 세 변경이 포함된 이 v3 결과와 섞지 않는다.

### v3 JFR와 출처

각 실행의 18개 측정 창만 집계했다. 샘플 수는 실행 길이와 sampling의 영향도 받는다. N=0 기준 A1g는 서버 Java 샘플 0인데 JFR 서버 스레드 allocation weight가 8,902,912B였다. 후보 N=0의 직접 batch 할당은 모두 0이다. 역시 JFR weight와 batch 직접 할당의 범위·추정 방식이 달라 하나로 합치지 않는다.


| 실행 | 시작 UTC | 종료 UTC | 서버 Java 샘플 | 샘플 없는 구간 | JFR GC 정지 겹침 합계 ms |
|---|---|---|---:|---:|---:|
| A1g | 11:11:57 | 11:13:05 | 961 | 3 | 69.172 |
| B1g | 11:13:06 | 11:13:54 | 202 | 3 | 50.278 |
| B2g | 11:13:55 | 11:14:42 | 318 | 3 | 16.477 |
| A2g | 11:14:44 | 11:15:46 | 1260 | 3 | 88.942 |
| A3g | 11:15:48 | 11:17:01 | 1260 | 3 | 102.085 |
| B3g | 11:17:02 | 11:17:55 | 194 | 2 | 57.510 |

실행 manifest는 `profile-v3-series.json`, 상태·외부 부하 제약은 `profile-v3-window-status.json` 및 앞의 CPU 표본 파일에 있다. 다음 원본을 실제 SHA-256과 다시 대조했다. 경로는 `profiles\algorithm-profile-{라벨}.jsonl` / `.jfr`다.


| 실행 | JSONL SHA-256 | JFR SHA-256 |
|---|---|---|
| A1g | `6cacd8a7a9b67d45ff508cd55948b9577f31cc3c31dae2dc4aa4ea2e6af7f07e` | `b9d62c4a9193b82d8dbacc292cbd4a201e81f82fc646663a818eb356c218a41c` |
| B1g | `6ecb72ec8a80a8d40cb559dfeebea09e9e3a1b8d6055674c70adc148f344614d` | `f2fd0c0ba0758685cb1453abdfb12d404a3d3bf8bcc2209630cd3a88de2b4a03` |
| B2g | `154427b70a026545e13676caae3ccbcd91eb2b2e825706f249f07e111e4e5344` | `f582497dbade31dc3678392b04d4062baf40fb7a8507d8ab582981349d6f4384` |
| A2g | `0effdc024e5f1a2a5321d12204e2758c394ef61bd5858a012a8c5cccfba1bdb6` | `fe6785ef2d937e301b39ee4f8c474a64272ccbebcf77b3e8a32689889730968a` |
| A3g | `4584ad00d5c428f60bd2805195d30d5e005ff6f1b9014f641df819228bf758c7` | `2ff6ba998b3bf0e09e62a6d289efa5ad6d15265f3af890eb2b8141bb61b984cc` |
| B3g | `353413a69efcd416cb20c8d5911850f95328261e57001fc8f169b991683576e5` | `42a5b60b5057f65aca6d45924f3426aa2f60d10fcf0188888b0941ebacc4c4fc` |

분석 재현은 W에서 v2 명령의 입력을 A1g/B1g/B2g/A2g/A3g/B3g로 바꾸고 `--out profiles\comparison-v3`를 사용한다. JFR JSON export와 원본 해시를 같은 라벨로 유지한다. 세 JVM 반복·고정 단계 순서·짧은 warmup·측정 도중 클라이언트 교체라는 한계가 있어 통계적 유의성·실전 TPS·확정 인라이닝 원인을 주장하지 않는다.


## v2 산출물과 분석 재현

경로는 `W\profiles\algorithm-profile-{라벨}.jsonl` / `.jfr`이며 아래 해시를 실제 파일과 재대조했다. 개인 환경 자료·JFR·라이선스 자산을 외부 공개 저장소에 올리지 않았다.

| 실행 | JSONL SHA-256 | JFR SHA-256 |
|---|---|---|
| A1f | `b08ea6b28b5d142ddf1793efeb328544f17cbf567571bb4d44a397d0a8a0db5a` | `4547b743a858ef128008f5e6ce9cff8d34f74917c4a2cc0f6e54596ee51eb747` |
| B1f | `90a262cee5b41f898e303e18d01f63c7ecce43568daff38b8e09b5c5d05bccb7` | `c1c6f378985e9d5369b63591c3fe1351c3f3f418fadfe3e794cdb049627eba15` |
| B2f | `1d450f78b3d3c172ca578fb55278b8de94533d625e1cd24f4e37fc1886a67ca0` | `e085d3bcf71f779a952397a80d133195663266e3767751a88c7aafc659e9cfb7` |
| A2f | `22cd3557d5ce45dbe3e60e78ad1abe35cffb67bd5ae1e975c22bec4d9ea8dc0d` | `a383f18b0abd55ca2c7945ca6c9aa9e6bc159ecf5b2e897a9d19068cab1b2c2e` |
| A3f | `1f899e5eb3c7160b53de2136d303e1d69fcf2b131938d7e7c67453585b123cf8` | `2b7b31ab3fbad3a88a7124ec684c5b5df652ac4a29f3b6ceecac095131a0a600` |
| B3f | `0e00114b1646e509cd8740dad24b640b3a94a3bcbb8a80c88ed4b83e786874dc` | `65eb3b8414b68ba2626918a7ea967d45813c0380cd80d902cecfb3e8c053e134` |

`profile-series.json`은 라벨·UTC·source manifest·해시를 묶는다. `*-run.json`·`*.log`는 인수·run directory·exit 근거다. `comparison-v2.json`에는 export/원본 해시, 입출력 대조, 개별·집계 지표가 있다. JSONL만 분석한 `comparison-provisional.*`은 중간 자료이고 최종은 JFR까지 검증한 `comparison-v2.*`다.

```powershell
python .\analyze_algorithm_profiles.py --run A1=profiles\algorithm-profile-A1f.jsonl --run B1=profiles\algorithm-profile-B1f.jsonl --run B2=profiles\algorithm-profile-B2f.jsonl --run A2=profiles\algorithm-profile-A2f.jsonl --run A3=profiles\algorithm-profile-A3f.jsonl --run B3=profiles\algorithm-profile-B3f.jsonl --out profiles\comparison-v2
```

W에서 실행했고 JSONL 옆 `.jfr.json`을 요구한다. Python은 `C:\Users\Kiruy\AppData\Local\Python\bin\python.exe`다. `export-profile-jfr.py`는 해당 JDK를 `-Xmx256m -XX:ActiveProcessorCount=2`로 제한했다. 분석 재실행은 서버를 시작하지 않는다.

## 실제 전투 기능 검증과 집계 진단

[GameCombatPerformanceTest](../src/gametest/java/kim/biryeong/semiontd/game/GameCombatPerformanceTest.java)의 고정 좌표 실제 전투를 기준 코드에서 CA1·CA2 두 번 실행해 각각 1/1 GameTest를 통과했다. CA1은 2026-10-09 11:31:05~11:31:52 UTC, CA2는 11:32:15~11:33:03 UTC이며 두 실행 exit 0이다. Gradle은 worker 1·CPU 2개·heap 1GB, GameTest는 CPU 4개·heap 2GB로 제한했다. 실행 전 CPU 20%·가용 메모리 32.78GB를 확인했다. 이는 기능 검증 준비 점검이며 안정된 성능 측정 부하의 증거는 아니다.

`profiles\combat-baseline-repeat-comparison.json`에서 두 파일 자체 검증은 모두 `SELF_VALIDATED`, 입력 SHA-256은 동일했다. 초기 상태와 500 tick을 합친 **501/501 outcome을 모두 엄격 비교했고 관측 결과의 최초 차이는 없었다.** 환경과 결과를 독립적으로 비교하므로 환경 차이가 있어도 501개 전부의 결과 대조를 생략하지 않았다.

그러나 환경 `manifest.environment["gameTime"]`이 617 대 1040으로 달라 전체 엄격 판정은 **`DIVERGED_PERFORMANCE_COMPARISON_FORBIDDEN`**, `performanceComparisonAllowed=false`다. 시간 차이를 정규화하거나 허용 오차로 지우지 않았다. UUID·logical UUID·constructor yaw 등 통제하지 못한 생성 상태도 달랐지만 이 두 기록의 관측 outcome에서는 차이가 없었다. 이는 제한된 시나리오 두 기록의 기능 결과 근거이며, 모든 전투의 동일성이나 결정성·MSPT·CPU·처리량 개선 근거가 아니다.

| 기록 | JSONL SHA-256 |
|---|---|
| CA1 | `2f91b7e4eb2bf91e34c89f934b09de280bc13189fdb264bedd1e1479c89562f3` |
| CA2 | `f40f496b3ffed522b4fd751d7ca557c687eca95f2fbb17bb810fa94d65e524e0` |

`CA1-run.json`·`CA2-run.json`에는 정확한 인수·source manifest·새 run directory·UTC·exit가 있고 `CA1.log`·`CA2.log`에 실행 근거가 있다. 최종 두 변경 후보 CB1은 2026-10-09 11:33:28~11:34:40 UTC에 exit 0, GameTest 1/1을 통과했다. 하지만 `profiles\combat-final-comparison.json`에서 기준 CA1과 후보 CB1의 501개 outcome 중 **176개만 일치**했다. 첫 관측 차이는 tick 44의 `outcome["lanes"][1]["metrics"][0]["magicBits"]`이며 raw bits `4650085302091542126` 대 `4650085302091542127`로 1 ULP 차이다. 환경의 첫 차이인 gameTime 617 대 830과 별도로 모든 결과를 대조했다.

두 JSONL 자체 검증은 통과했지만 전체 엄격 판정은 `DIVERGED_PERFORMANCE_COMPARISON_FORBIDDEN`이다. **후보의 기능 동등성이 통과했다고 판정하지 않는다.** 이 초기 CA1/CB1 단계에서는 전투 상태 차이인지 생성/집계 순서 차이인지 미확정이었다. 당시 개별 tracker·순서를 기록하지 않았으므로 이후 DA1/DB1의 원인 재현 결과를 초기 기록에 소급 확정하지 않는다. 허용 오차를 추가하거나 값을 정규화해 실패를 숨기지 않는다. CB1 JSONL SHA-256은 `81a2c952d95602b010232982442dba75c21f0aa65183abbcd58e01b198d5fbe7`이다. 최종 manifest `93d6fd2b8b6dc04f5286ca21a511624a718d7278d4e47364ffcfea8c5f615f48`의 `final-gate-1`은 이후 아래와 같이 통과했다. 이 suite 통과가 앞의 엄격 전투 결과 불일치를 무효화하지 않는다. 이 단계의 전투 타이밍 수치나 속도 비율은 보고하지 않는다.

초기 CA1/CB1 추가 대조에서는 325개의 차이가 모두 학교 aggregate `magicBits` 1~2 ULP였고 501개 모든 tick에서 metrics 외 관측 상태 차이는 없었다. 소스의 `roundTowerMetrics`는 `IdentityHashMap` 기반 tracker set의 순서대로 double 값을 합산한다. 초기 기록에 대해 집계 순서 반올림은 유력한 가설이지만, 당시 개별 tracker·순서를 기록하지 않아 그 기록의 인과를 소급 확정할 수 없다. 기존 aggregate 비교를 유지하고 개별 타워 tracker raw bits와 실제 iteration order를 기록하는 fixture 진단을 추가해 검증한다. 허용 오차 도입이나 차이 무시는 하지 않는다.

진단 필드를 추가한 저장소 fixture와 첫 전체 gate의 격리 소스는 구분한다. `final-gate-1`은 앞서 기록한 manifest의 격리 소스를 유지하며 실행 중 변경하지 않았고 이후 정상 완료했다. 새 진단 fixture의 manifest·hash는 앞의 최신 스냅샷 표에 기록했다. DA1 baseline은 11:43:02~11:43:59 UTC, DB1 candidate는 11:44:20~11:45:15 UTC에 각각 exit 0·1/1 GameTest를 통과했다. 근거는 `DA1-run.json`·`DB1-run.json`이다. 각 실행 성공과 엄격 결과 동일성은 별개이며, 새 기록의 엄격 비교·합산 순서 진단은 아래와 같이 완료했으며 최신 fixture의 두 번째 전체 gate도 아래와 같이 완료했다.

### 진단 기록 DA1/DB1의 집계 순서 원인

`profiles\combat-diagnostic-comparison.json`의 엄격 outcome은 **429/501만 동일**하고 첫 차이는 tick 44 학교 `magicBits`의 raw bits `4650085302091542126` 대 `4650085302091542128`(2 ULP)이다. 환경 gameTime도 492 대 579로 다르다. 전체 판정은 여전히 `DIVERGED_PERFORMANCE_COMPARISON_FORBIDDEN`이며 허용 오차나 정규화로 통과시키지 않았다.

`profiles\combat-aggregation-cause.json`은 별도 원인 분석으로 `AGGREGATION_ORDER_CAUSE_CONFIRMED_FOR_THESE_RECORDINGS`를 기록한다.

| 재현 검사 | 결과 |
|---|---|
| 타워별 tracker snapshot의 모든 raw field | 10,521/10,521 동일 |
| 각 기록의 실제 identity-set 순서로 자체 합산 재현 | 기준·후보 각각 1,002/1,002 tick-lane 동일 |
| 기준 개별 값 + 후보 순서 → 후보 aggregate | 1,002/1,002 동일 |
| 후보 개별 값 + 기준 순서 → 기준 aggregate | 1,002/1,002 동일 |
| aggregate를 제외한 outcome 차이 | 0 |
| aggregate 차이 | 학교 magicBits 72개: −1 ULP 22개, +1 ULP 6개, +2 ULP 44개 |

코드상 [PlayerLane의 tracker set](../src/main/java/kim/biryeong/semiontd/game/PlayerLane.java#L81)은 `IdentityHashMap` 기반이다. [roundTowerMetrics](../src/main/java/kim/biryeong/semiontd/game/PlayerLane.java#L646)는 그 비고정 반복 순서대로 같은 타워 종류를 merge하고, [TowerRoundMetricsSnapshot.merge](../src/main/java/kim/biryeong/semiontd/game/TowerRoundMetricsSnapshot.java#L118)는 double 값을 순차 합산한다. 이 진단 쌍에서는 개별 값이 같은데 실제 순서를 바꾼 합산이 상대편 결과를 정확히 재현하므로 **집계 순서에 따른 반올림 차이가 원인임을 확인했다.** logical UUID 기반 HashMap이 원인이라는 설명은 사용하지 않는다.

재현기는 첫 snapshot을 직접 복사하고 이후 Java binary64 덧셈·constructor clamp를 기록된 순서대로 적용했다. 허용 오차는 없고 자기 검사도 통과했다. 분석기는 `analyze_combat_aggregation.py`, SHA-256 `7eb5e0626bd579efc439f3b8bbfe28136e189d7b2ac6065c001a04d01f41fe40`다. DA1 JSONL SHA-256은 `528394a6050bca84833e897f92cd42072aa484b1579a0390779d6546bf154bbc`, DB1은 `1ba938435edc520232c2bb4024d605ed9d13f142816ce6fe32bc16758111ee7b`다.

원인 확정 범위는 개별 값·실제 순서를 기록한 **DA1/DB1 두 기록뿐**이다. 초기 CA1/CB1에는 해당 진단 필드가 없어 소급 확정하지 않는다. 이 분석은 기존 aggregate 검사를 제거하거나 전투 환경/전체 outcome의 엄격 비교를 완화하지 않는다. **429/501 엄격 실패, gameTime 불일치, 성능 비교 금지를 그대로 유지한다.** 전체 전투 결정성·모든 입력의 기능 동등성·실전 속도 향상을 주장하지 않는다.

## 첫 전체 gate 완료와 최신 fixture 검증 구분

`final-gate-1-evidence.json`의 source manifest는 `93d6fd2b8b6dc04f5286ca21a511624a718d7278d4e47364ffcfea8c5f615f48`다. 격리 candidate에서 2026-10-09 11:35:08~11:40:08 UTC에 `test runGameTest remapJar`가 exit 0으로 완료했다. 로그의 BUILD SUCCESSFUL은 4분 59초다. production 변경은 효과 합산·엔지니어 선택 두 개이며 타깃은 기준 구현으로 복원된 상태다.

| 항목 | 확인 결과 |
|---|---|
| JUnit | 총 2,017개, 실패 0·오류 0·건너뜀 2 |
| XML 근거 | 267개 suite XML 및 각각의 SHA-256 보존 |
| 필수 GameTest | 968개 통과 |
| opt-in 성능 테스트 | NOT_RUN. 일반 suite 통과를 새 성능 측정으로 간주하지 않음 |
| remapJar | 성공, ZIP 무결성 통과 |
| JAR 관련 class | TimedEffectSet·EntityGoalTargetSelection·EngineerGolemTower class가 해당 빌드 출력과 일치 |

건너뛴 두 검사는 `EndBalanceRepositoryContractTest.siblingBalanceRepositoryMatchesTheEndAbilityContract()`, `EndBalanceRepositoryContractTest.siblingBalanceRepositoryContainsOnlyKnownTowerAbilityKeys()`다. 테스트를 삭제하거나 기대치를 약화해 통과시키지 않았다.

JAR는 격리 candidate의 `build\libs\semion-td-1.0-SNAPSHOT+26.3.jar`, 29,652,741바이트, SHA-256 `7a8a27a456064f3647b9db649d1a8f10799b690b30081893d01613f1a025af4e`다. `final-gate-1-evidence.json`에서 ZIP 검증·관련 class 해시·비공개 asset prefix entry 0을 확인했다. 이 JAR를 운영에 배포하지 않았다.

```powershell
.\gradlew.bat test runGameTest remapJar --offline --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1G -XX:ActiveProcessorCount=2' --init-script 'C:\Users\Kiruy\Documents\Codex\2026-10-05\task\optimization-2026-10-09\final-gate-limits.gradle' --console=plain --no-daemon
```

첫 gate 이후 전투 집계 순서 진단용 필드가 fixture에 추가됐다. production 두 변경은 동일하지만 첫 gate가 그 최신 test fixture까지 실행한 것은 아니다. 새 fixture의 기준·후보 진단 실행은 각각 1/1 통과했고 엄격 비교·집계 원인 분석은 위처럼 완료했고 최신 fixture의 두 번째 gate 결과는 다음 절에 기록한다. 첫 gate 성공 근거와 기존 엄격 전투 비교 실패를 모두 보존한다.

## 최신 전체 gate 완료와 종료 상태

`final-gate-2-evidence.json`의 candidate source manifest는 `ba684fbf100cca95bd4b60cd89eb6bab04fecd0779e38f8c0027cf1f6e9ce7f1`이다. 진단 fixture SHA-256 `6f14aa9402af9ceb3cd2cfd3e4be904aa5379430d522993c80f3af3aca673df3`가 포함된 스냅샷에서 2026-10-09 11:45:37~11:48:56 UTC에 `test runGameTest remapJar`가 exit 0으로 완료했다. 로그의 BUILD SUCCESSFUL은 3분 18초다. 실제 인수는 앞의 gate-1과 동일하며 `final-gate-2-run.json`·`final-gate-2.log`에 보존했다.

| 항목 | gate-2에서 수행한 내용 |
|---|---|
| JUnit test | UP-TO-DATE. 첫 gate에서 실행한 2,017개 결과 재사용, 실패·오류 0·기존 건너뜀 2 |
| JUnit XML | 267개 모두 gate-1 해시와 동일. 2,017개를 두 번 실행했다고 세지 않음 |
| runGameTest | 최신 진단 fixture로 필수 968개 새 실행·통과 |
| opt-in 성능 프로필 | 전체 gate에서는 NOT_RUN. 별도 합성·전투 실행과 구분 |
| remapJar | UP-TO-DATE. 새 JAR를 만들었다고 주장하지 않음 |
| JAR 검증 | 29,652,741바이트, SHA-256 `7a8a27a456064f3647b9db649d1a8f10799b690b30081893d01613f1a025af4e`로 gate-1과 동일 |
| 호환·보존 | ZIP 무결성·관련 세 class 빌드 출력 일치, 초기 변경 13개 보존, runtime/test/build 소스가 현재 저장소와 일치 |

두 gate의 명령은 성공했지만 새 diagnostic outcome 전체가 같다는 뜻은 아니다. DA1/DB1의 429/501 엄격 비교 실패·환경 gameTime 차이·실전 성능 비교 금지를 유지한다. 이 gate 종료 당시에는 최종 두 production 변경 조합의 합성 A/B 정량 측정을 수행하지 않았으며, 이후 v4에서 별도 실행했다.

2026-10-09 11:50:45 UTC의 `functional-gate-load-after.json` 점검에서는 사용자 Minecraft 클라이언트 javaw PID 35044만 남았고 자체 검증 Java 프로세스는 없었다. 보호 대상 25578·51716·51718 listener도 없었다. CPU 22%·가용 메모리 33.77GB는 해당 시점의 종료 점검 값이며 측정 부하 안정성이나 속도 개선 근거가 아니다. 사용자가 접속한 곳은 별도 서버이며 기존 검증/운영 서버를 재시작하지 않았다.

## 미채택 후보와 검증 한계

`EndDragonReturnPosition`·`MagicSchoolBroomsticks`의 위치 반복·대상 재구성은 후속 조사로 남겼다. 콜백 중 바뀌는 상태를 하나의 캐시에 넣지 않았다. `PlayerLane.tickTowers` snapshot은 콜백 중 등록·제거 의미 때문에 유지했다. 이름표·설명·웨이브 캐시·큐도 빈도·크기·생성/유지 비용 근거가 부족해 채택하지 않았다.

`GameLaneTickPerformanceTest`는 noop dispatch 중심이고 현재 runtime의 Spark 의존성도 빠져 있어 실전 근거로 사용하지 않았다. `SeasonThreeLoadGameTest`는 실제 AI를 사용해도 이번 세 변경의 동일 입력·seed·CPU/할당/GC A/B 자료가 아니다. HOI JFR도 제외했다.

실제 전투 기준 반복의 관측 결과 동일성은 위에서 확인했으나 환경 시간은 달랐다. 최종 후보와 기준 간 전체 결과 대조는 불일치했고 새 진단 기록의 집계 순서 원인은 확인했으며, 최신 진단 fixture를 포함한 전체 gate도 통과했다. 생성 이후 seed 설정만으로 entity ID·UUID·AI parity 차이가 제거되는 것은 아니며, 결과가 같더라도 환경 차이가 있으면 성능 비교를 차단한다.

타깃 후보는 철회했으며 최종 두 변경 조합의 v4 합성 측정은 완료했다. 수치·JFR·외부 부하 판정은 아래 v4 절에 별도로 기록한다. 실제 전투 집계 진단과 최신 fixture의 전체 gate는 완료했다. 엄격 전투 동등성·성능 비교가 성립하지 않았다는 한계는 남는다.

```powershell
.\gradlew.bat test runGameTest remapJar --console=plain --no-daemon
git diff --check
```

위 일반 gate는 두 스냅샷에서 통과했으며 두 번째 JUnit·remapJar는 첫 결과를 재사용했다. JAR 무결성과 관련 class는 확인했지만 운영 배포·운영 reload·실제 클라이언트/멀티플레이 검증은 이번 작업에서 수행하지 않았다.

## 집계 소비 범위와 재측정 조건 보완

이 절은 v4 측정 이전의 추가 소스 조사로 보완한 설명이다. 이 소스 조사 단계에서는 production·테스트·비교기를 수정하거나 새 벤치마크를 실행하지 않았다. 앞의 429/501 엄격 비교 실패, 환경 차이, 기존 프로필과 실패 기록은 그대로 유지한다.

### 72틱 집계 차이가 전달되는 경로

fixture의 `magicBits`는 생산 코드의 `TowerRoundMetricsSnapshot.magicDamageDealt`를 raw double bits로 기록한 값이다. 이 값은 **기록·분석용 집계이며 화면 표시 전용은 아니다.** 다음 경로로 경기 이력과 SQLite 통계에 포함된다. 다만 DA1/DB1 fixture가 실제 경기 종료·영속 저장까지 실행한 것은 아니므로, 관측한 72틱 차이가 그대로 데이터베이스에 저장됐다고 주장하지 않는다.

| 단계 | 현재 소스에서 확인한 경로 |
|---|---|
| 집계 캡처 | [GameRoundMetricsSnapshot.capture](../src/main/java/kim/biryeong/semiontd/game/GameRoundMetricsSnapshot.java#L16)가 `lane.roundTowerMetrics()`를 읽고 46행에서 `PlayerRoundMetricsSnapshot`에 담는다. |
| 경기 결과에 포함 | [SemionGame.recordRoundMetrics](../src/main/java/kim/biryeong/semiontd/game/SemionGame.java#L1713)가 플레이어별 round 이력에 추가하고, [matchResult](../src/main/java/kim/biryeong/semiontd/game/SemionGame.java#L882)가 결과에 첨부한다. |
| 이력·통계 저장 | [SemionGameManager](../src/main/java/kim/biryeong/semiontd/game/SemionGameManager.java#L2462)가 경기 결과를 저장하고 2464행에서 직업 통계 서비스에 전달한다. 경기 이력 JSON에도 round metrics가 포함된다. |
| SQLite 값 | [SQLiteJobStatisticsStore](../src/main/java/kim/biryeong/semiontd/persistence/SQLiteJobStatisticsStore.java#L678)가 `setDouble(10, tower.magicDamageDealt())`로 저장한다. 대상은 [스키마](../src/main/java/kim/biryeong/semiontd/persistence/PersistenceJobStatisticsSchema.java#L183)의 `job_stat_participant_round_tower_metrics.magic_damage_dealt REAL`이다. |

현재 체크아웃의 정적 호출·필드·SQL 소비 경로를 조사한 범위에서는 **해당 집계 마법 피해를 전투·승패·보상·레이팅 계산에 다시 입력하는 production 경로가 확인되지 않았다.** 따라서 진단 쌍에서 나온 집계 반올림 차이를 실제 타격 피해나 지급 보상 차이로 해석하지 않는다. 동시에 저장될 수 있는 원시 통계 값의 차이를 단순 UI 차이라며 제거하지도 않는다.

| 구분 | 집계와 분리되어 있는 근거 |
|---|---|
| 실시간 HUD | [UiHudDamageRanking](../src/main/java/kim/biryeong/semiontd/ui/UiHudDamageRanking.java#L28)은 개별 `Tower.roundPhysicalDamageDealt/roundMagicDamageDealt/roundDamageTaken`을 읽는다. 위 round snapshot을 다시 읽는 경로가 아니다. |
| 라운드 보상·승패 | [tickPayout](../src/main/java/kim/biryeong/semiontd/game/SemionGame.java#L1571)은 보상 처리 뒤 기록을 캡처한다. [checkVictory](../src/main/java/kim/biryeong/semiontd/game/SemionGame.java#L1727)는 살아 있는 팀 수로 종료를 판단한 뒤 기록한다. `matchResult`의 승자 값도 생존 팀 판정에서 온다. |
| 진행 보상 | [SemionProgressionStore.recordMatch](../src/main/java/kim/biryeong/semiontd/progression/SemionProgressionStore.java#L154)는 `participant.winner()`에 따라 `rewardForWin/rewardForLoss`를 선택한다. 이 타워 피해 집계를 읽지 않는다. |
| 레이팅 | [RatingService.participant](../src/main/java/kim/biryeong/semiontd/rating/RatingService.java#L194)는 winner·기존 profile·placement score·`participant.stats()`를 넘긴다. [PlayerMatchStatsSnapshot](../src/main/java/kim/biryeong/semiontd/game/PlayerMatchStatsSnapshot.java#L3)은 별도 통계이며 이 타워 집계 피해 필드가 없다. |
| 저장된 통계 재조회 | [loadSnapshot](../src/main/java/kim/biryeong/semiontd/persistence/SQLiteJobStatisticsStore.java#L264)·[rebuild](../src/main/java/kim/biryeong/semiontd/persistence/SQLiteJobStatisticsStore.java#L318)는 facts·round outcome·summary 경로를 사용한다. 현재 production SQL에서 해당 tower-metrics 테이블을 직접 SELECT해 게임 판단에 넣는 소비 경로는 확인되지 않았다. 이력 JSON 재읽기는 통계 재저장으로 이어진다. |

이 결론은 **현재 저장소의 정적 조사 범위**다. 저장소 밖의 독립 SQL 조회·분석 서비스나 향후 소비자는 검증하지 않았다. 저장소 안의 문서·테스트·오프라인 로그 분석 소비와 실제 게임 판단을 구분했다. 정적 경로상 재입력이 없다는 사실과, fixture에서 최종 보상·저장 결과를 실제로 비교하지 않았다는 한계는 별개의 사실이다.

### 전투 fixture가 관측한 것과 관측하지 않은 것

진단 쌍은 직접 소환한 두 레인의 고정 시나리오를 500틱 실행하고 초기 상태를 포함한 501개 snapshot을 비교했다. [capture](../src/gametest/java/kim/biryeong/semiontd/game/GameCombatPerformanceTest.java#L520), [entityState](../src/gametest/java/kim/biryeong/semiontd/game/GameCombatPerformanceTest.java#L585), [metricState](../src/gametest/java/kim/biryeong/semiontd/game/GameCombatPerformanceTest.java#L746)가 관측 범위의 근거다.

| 범위 | 기록·검증 수준 |
|---|---|
| 타워·개별 통계 | 종류, 체력, 위치, 회로 상태, 개별 tracker 통계의 raw field를 기록했다. DA1/DB1의 개별 tracker snapshot 10,521개가 모두 같았다. |
| 엔티티·몬스터 | 생존·제거·AI 상태, 체력·좌표·속도·회전 raw bits, 타깃, 타워 효과의 종류·출처·크기·남은 시간, 몬스터 상태·진행도·활동 tick·보상 지급 플래그·능력 쿨다운·관측된 성공/재시도 횟수를 기록했다. |
| 레인·엔지니어 | clear·방어선 파괴·누수·위협·킬 수, 골렘 목표/직전 발판·작동 횟수·발판 쿨다운을 기록했다. |
| 집계 | 원래 aggregate 비교를 유지했다. DA1/DB1의 72틱 마법 피해 합계 차이는 실제 iteration order로 재현했지만 전체 엄격 비교는 429/501 실패 상태다. |
| fixture 밖 | 플레이어 재화 잔액·수입·실제 지급 금액, GameManager의 전체 라운드 전환·승자·경기 종료·최종 보상, 경기 이력·SQLite 영속 저장 결과는 outcome에 없다. 구매·전체 경기·실제 클라이언트도 제외됐다. |

따라서 보상 지급 플래그가 같다는 관측을 실제 지급 금액이나 최종 승패·보상·저장 결과까지 같다는 증명으로 확대하지 않는다. 501개 snapshot에서 집계 이외 관측 필드가 일치하더라도 틱 사이 모든 행동·이벤트 로그가 완전히 동일하다는 뜻은 아니다. 앞의 정적 소비 경로 조사와 이 제한된 실행 관측을 합쳐 전체 게임 동등성이나 실전 속도 개선을 주장하지 않는다.

### 불일치한 환경 시간의 정확한 의미

불일치 필드는 `manifest.environment["gameTime"]`이다. [fixture 372행](../src/gametest/java/kim/biryeong/semiontd/game/GameCombatPerformanceTest.java#L372)의 `level.getGameTime()`을 기록한 **월드 game time**이며, `dayTime`·운영체제 시계·UTC 실행 시각·측정에 걸린 wall time과 다른 값이다.

| 실행 비교 | gameTime |
|---|---|
| CA1 / CA2 기준 반복 | 617 / 1040 |
| CA1 / CB1 초기 기준·후보 | 617 / 830 |
| DA1 / DB1 진단 기준·후보 | 492 / 579 |

이 필드는 `comparisonInput`에 포함되지 않고 별도의 environment에서 비교된다. 따라서 `inputSha256`이 같아도 환경은 다를 수 있다. 현재 비교기는 이 환경 값을 정확히 비교하며 허용 오차나 시간 정규화를 적용하지 않는다. attribution용 variant·일부 JVM property와 동적 heap 사용량의 기존 명시적 예외를 gameTime까지 넓히지 않았다. 외부 CPU/GPU 부하를 낮추는 것만으로 이 필드 차이나 strict aggregate 불일치가 해결되지는 않는다.

### 추가 측정을 시작할 조건

**아래 조건은 v4 이전에 작성한 후속 측정 준비안이다. 당시에는 충족 여부를 확인하지 않았고 새 측정을 하지 않았다. 이후 실행한 v4의 실제 부하·누락·제한은 다음 절에서 별도로 평가한다.** CPU가 임의의 몇 % 아래라는 값만으로 동일하고 안정적인 환경을 보증하지 않는다.

1. HOI의 compile·test·프로파일 작업이 끝났음을 확인하고 서로 겹치지 않는 측정 창을 조율한다. 다른 CPU·GPU·디스크 집약 작업도 측정 전체 동안 없도록 확인한다.
2. 사용자가 Minecraft 클라이언트를 자발적으로 종료한 상태를 사용하거나 별도 유휴 호스트를 사용한다. 클라이언트·다른 프로그램을 임의 종료하거나 OS 우선순위를 바꾸지 않는다.
3. 첫 JVM 시작 전부터 모든 측정과 종료 점검까지 외부 부하를 연속 관측한다. 처음 지정한 PID뿐 아니라 새로 생성·종료·교체되는 PID도 추적하고 사용자 클라이언트 활동과 CPU·GPU·디스크 부하를 기록한다. 프로세스 교체나 부하 급변을 발견하면 같은 조건으로 간주하지 않고 기록·검토한다.
4. 해당 호스트에서 baseline과 최종 두 변경 candidate의 source manifest·공통 fixture를 고정하고, 같은 JDK·heap·GC·인식 CPU·입력·워밍업·반복 수로 별도 JVM을 순차 실행한다. 교차 순서의 여러 비교 쌍과 통제군을 포함한다. 별도 호스트로 옮기면 그 호스트에서 양쪽을 모두 새로 측정하며 기존 PC baseline과 섞지 않는다.
5. 시작·전체 구간·종료 기록으로 안정된 동등 부하인지 확인한다. CPU counter의 15.625ms 양자화, 짧은 워밍업, 통제군 변동도 별도로 평가한다. 유리한 중앙값만 선택하거나 통제 값을 빼서 차이를 제거하지 않는다.
6. 실전 정량 비교는 동일 환경과 엄격 결과 검증이 성립한 뒤에만 허용한다. gameTime·원래 aggregate 불일치를 별도로 해결·검증하기 전에는 외부 부하 조건이 좋아져도 실전 속도 비율을 제시하지 않는다. 허용 오차 추가·실패 기록 수정·비교기 완화로 통과시키지 않는다.

후속 실행을 하더라도 이번 v2/v3 자료와 실패 기록은 그대로 보존하고 새 source/fixture 식별자·측정 기록·판정을 추가해야 한다.

## 최종 두 변경 조합 v4 합성 재측정

### 측정 범위와 고정 출처

사용자가 프로그램 종료 후 재측정을 요청한 차수다. `TimedEffectSet`과 `EngineerGolemTower` 두 production 변경만 포함한 후보를 기준 소스와 직접 비교했다. `EntityGoalTargetSelection`은 기준 상태로 복원된 동일 파일이다. v2·v3는 세 변경 조합이므로 h 시리즈의 수치와 합치거나 그 수치를 이 최종 조합의 성능으로 대신하지 않는다.

`isolated-checkouts-final-two-v4.json`의 양쪽 3,824개 파일 목록은 이전 최신 gate와 같은 runtime/test/build를 가리킨다. baseline manifest SHA-256은 `4c490c261721023df9dc3134ea4d6109606b4438aefebdf5c0c43c798ddd1266`, candidate는 `ba684fbf100cca95bd4b60cd89eb6bab04fecd0779e38f8c0027cf1f6e9ce7f1`이다. 해시는 실제 UTF-8/LF manifest 바이트 기준이다. 격리 문서는 v2 snapshot을 보존했으며 root 보고서 갱신과 구분한다. source descriptor의 `combinedPerformance=PENDING_NEW_FINAL_TWO_CHANGE_MEASUREMENT`는 준비 시점 메타데이터이며 최종 실행 상태는 별도 series/window 기록으로 확인한다.

공통 합성 fixture source SHA-256은 `4ec04e775f87ede9ff9e5e185f680540b35c98a425f4045428102546acc5897e`, class는 `c6051786926fef4e56c4878fc124f17642aae47cc2ba6e1c87ef2fc847a895cf`다. seed 26031009, 입력 변형 16개, 워밍업 80틱, 측정 150틱, 일반 단계 1,024회/틱과 엔지니어 크기별 반복 수를 유지했다. JDK 25.0.4.1, 측정 JVM G1·heap 2GiB·인식 CPU 8, Gradle heap 1GiB·인식 CPU 4·worker 2를 사용했다.

별도 JVM 순서는 **A1h → B1h → B2h → A2h → A3h → B3h**다. 여섯 실행의 18단계에서 입력·출력 checksum과 fixture/source 식별을 확인했다. `SYNTHETIC_PATHS_ONLY`·`NONE_NULL_WORLD_NO_ENTITIES`인 순수 경로 측정이며 실제 라운드·구매·클라이언트·멀티플레이 검증이 아니다. 전체 게임 동등성이나 실전 TPS/MSPT 개선 비율을 뜻하지 않는다.

| 실행 | variant | 시작 UTC | 종료 UTC | Gradle exit |
|---|---|---|---|---|
| A1h | baseline | 12:24:19 | 12:25:19 | 0 |
| B1h | candidate | 12:25:21 | 12:26:05 | 0 |
| B2h | candidate | 12:26:07 | 12:26:50 | 0 |
| A2h | baseline | 12:26:52 | 12:27:52 | 0 |
| A3h | baseline | 12:27:54 | 12:28:53 | 0 |
| B3h | candidate | 12:28:55 | 12:29:38 | 0 |

실행 날짜는 2026-10-09다. `profile-final-two-v4-window-02.json`은 관측 준비를 포함한 창을 12:24:11.211603–12:29:39.185269 UTC, `COMPLETE`·observer exit 0으로 기록한다. 실제 첫 Gradle 시작부터 마지막 종료까지는 12:24:19.4571347–12:29:38.4430614 UTC다. 실행 시간 자체를 알고리즘 성능 비율로 사용하지 않는다.

PowerShell의 `-P` 인수는 `run-profile-final-two-v4.ps1`의 문자열 배열로 전달했다. 옵션이 여러 토큰으로 나뉘었던 초기 v2 준비 실패와 같은 호출을 재사용하지 않았다. 개별 `*-run.json`에는 실제 인수·source hash·별도 GameTest run directory·종료 코드가 있고, `profile-final-two-v4-series.json`에는 원본 JSONL/JFR 경로와 SHA-256이 있다.

### 사전 점검과 관측 재시도 이력

12:15:33 UTC의 `profile-final-two-v4-load-before.json`에는 CPU 2%, GPU engine 최대/합계 1%, disk busy·throughput 0, 가용 메모리 41.25GiB, 선택한 Java·dotnet·MSBuild 등 검사 프로세스 없음이 기록돼 있다. **이는 한 시점의 점검값이며 측정 전체가 CPU 2%였다는 뜻이 아니다.** 과거 v3의 사용자 Minecraft 클라이언트 실행 상태를 이번 v4에 그대로 적용하지 않는다.

최초 관측 준비는 12:21:14–12:21:21 UTC에 중단됐다. `profile-final-two-v4-load.jsonl`·`profile-final-two-v4-window.json`을 그대로 보존했고 이 시도에서는 벤치마크 JVM을 한 번도 실행하지 않았다. 연속 사전 CPU 표본은 10.03/8.64/13.91%였으며 DWM의 machine-normalized CPU가 약 6.8%였다. 새 pwsh/conhost가 생성된 직후 rate 계산에 필요한 두 시점이 없어 개별 CPU/I/O counter에 `PDH_INVALID_DATA(0xC0000BBA)`가 발생했고, 최초 wrapper가 모든 오류를 차단하면서 준비 단계에서 종료했다. observer 자체 종료 코드는 0이다.

재시도 `run-profile-final-two-v4-with-load-02.py`는 준비 단계에서 **해당 새 PID 이벤트가 있고, 정확히 위 상태 코드이며, CPU/I/O rate가 null이고 attribution이 무효인 경우만** 이 초기 rate 부재를 허용한다. 전체 CPU·GPU·disk core counter의 수치·누락 검사는 유지했다. 오류를 0%로 대체하거나 과거 파일을 덮어쓰지 않았고 두 번째 기록 `profile-final-two-v4-load-02.jsonl`을 별도로 만들었다. 실제 전체 관측의 누락·프로세스 교체·부하 분포 평가는 다음 분석에 포함한다.

측정 중 프로그램을 임의 종료하거나 OS 우선순위를 변경하지 않았다. 사용자 종료 후 점검과 새 PID를 포함한 관측을 적용하고 HOI 작업과 겹치지 않도록 측정 창을 조율했다. 다만 프로그램 종료나 관측 성공만으로 외부 부하가 일정·동등했다고 가정하지 않는다. 2초 표본 사이의 짧은 프로세스/부하 spike, observer 자체 비용, PID identity와 counter의 누락 가능성은 별도 한계다.

### v4 CPU·직접 할당·host p95·처리량

`comparison-final-two-v4.json`은 입력/출력·fixture·seed·반복 수·JVM 조건과 JFR stage marker 18개를 여섯 실행 모두 검증했고 `jfrValidation=ALL_SIX_VALIDATED`다. `final-two-h-independent-review.json`은 최종 두 production 차이와 실행 source/원본 파일 hash를 독립 검증했다. 아래 A1/B1·A2/B2·A3/B3는 실제 h 라벨을 줄인 표기이며 시간상 실행 순서는 위와 같다.

각 값은 세 JVM의 중앙값이다. CPU·할당은 server-thread batch 계측이며 checksum/counter 비용을 포함한다. host p95는 합성 harness와 GameTest를 포함하므로 실전 MSPT가 아니다. Active ops/s는 합성 batch 시간, wall ops/s는 해당 단계 전체 벽시계 시간으로 나누며 지속 게임 TPS로 해석하지 않는다. 타깃 K1/K3 소스는 양쪽에서 동일하므로 모두 변화하지 않은 경로의 통제로 읽는다.

| 단계 | N | CPU ns/op A→B | CPU CV% A/B | 직접 B/op A→B | 합성 host p95 ms A→B | active ops/s 중앙값 변화 |
|---|---:|---:|---:|---:|---:|---:|
| 효과 K3 | 6 | 203.45 → 101.73 † | 70.71/56.57 | 184.00 → 40.00 | 0.48 → 0.29 | +82.42% |
| 타깃 K1 (동일 코드) | 6 | 203.45 → 101.73 | 28.28/35.36 | 80.00 → 80.00 | 0.40 → 0.40 | +13.48% |
| 엔지니어 | 6 | 3,356.93 → 2,034.51 | 7.41/6.34 | 11,254.00 → 7,413.50 | 5.07 → 2.55 | +66.14% |
| 효과 K3 | 32 | 1,118.98 → 101.73 | 11.00/35.36 | 1,088.00 → 40.00 | 1.69 → 0.35 | +483.73% |
| 타깃 K1 (동일 코드) | 32 | 305.18 → 406.90 | 40.41/28.78 | 176.00 → 176.00 | 0.70 → 0.67 | -0.64% |
| 엔지니어 | 32 | 26,855.47 → 11,800.13 | 4.57/4.35 | 94,152.50 → 37,991.00 | 10.67 → 4.69 | +133.47% |
| 효과 K3 | 128 | 6,103.52 → 610.35 | 2.09/8.32 | 3,712.00 → 40.00 | 6.79 → 0.93 | +961.21% |
| 타깃 K1 (동일 코드) | 128 | 1,220.70 → 1,322.43 | 13.42/9.35 | 176.00 → 176.00 | 1.36 → 2.30 | -6.75% |
| 엔지니어 | 128 | 139,973.96 → 43,945.31 | 7.77/9.02 | 541,236.50 → 149,788.50 | 9.62 → 3.31 | +208.84% |
| 효과 K3 | 512 | 42,419.43 → 2,237.96 | 1.38/2.18 | 15,144.00 → 40.00 | 46.83 → 2.64 | +1799.58% |
| 타깃 K1 (동일 코드) | 512 | 5,187.99 → 5,086.26 | 8.01/4.08 | 176.00 → 176.00 | 5.84 → 5.76 | +2.41% |
| 엔지니어 | 512 | 807,291.67 → 182,291.67 | 2.53/6.22 | 2,922,446.50 → 604,271.00 | 14.81 → 3.53 | +325.92% |
| 효과 K3 | 0 | 0.00 → 0.00 † | —/— | 24.00 → 0.00 | 0.28 → 0.04 | +356.52% |
| 효과 K1 | 1 | 0.00 → 0.00 † | —/— | 104.00 → 0.00 | 0.06 → 0.03 | +109.28% |
| 효과 K1 | 32 | 1,220.70 → 101.73 | 8.32/35.36 | 1,088.00 → 0.00 | 2.04 → 0.19 | +851.59% |
| 효과 K0 통제 | 32 | 0.00 → 0.00 † | 141.42/— | 0.00 → 0.00 | 0.03 → 0.04 | +1.27% |
| 타깃 K3 통제 | 32 | 610.35 → 508.63 | 0.00/8.84 | 256.00 → 256.00 | 1.02 → 0.64 | +24.89% |
| checksum 통제 | 0 | 0.00 → 0.00 † | 141.42/— | 0.00 → 0.00 | 0.02 → 0.02 | -17.85% |

† 0 CPU 표본을 포함해 비율 판단을 억제한 단계다. 전체 CPU 기록 108개 중 23개가 0이고 46개가 3양자 이하다. 값의 최대공약수는 15,625,000ns, 즉 15.625ms다. 0은 CPU 비용 없음이 아니며 작은 양수도 정밀한 효과 크기가 아니다. 같은 양자에 걸린 CV 0% 역시 안정성을 증명하지 않는다. 예를 들어 효과 K3 N6은 batch 시간이 세 쌍 모두 감소해도 CPU는 0→1양자·2→3양자·2→1양자로 일관되지 않았다.

### v4 batch 분포와 쌍별 변화

batch ns/op는 실행별 batch mean ms × 1,000,000 / operationsPerTick이다. 대괄호는 세 JVM의 min–max, CV는 모집단 표준편차/평균이다. 표의 중앙값은 tick p50과 다르며 JVM 세 번의 평균 batch 비용의 중앙값이다. 감소는 음수이며 세 비교 쌍을 모두 남긴다. 분포의 p50/p95/p99/max 원값은 원본 JSONL와 독립 검토 JSON에 보존했다.

| 단계 | N | A 중앙값 [min–max]; CV | B 중앙값 [min–max]; CV | A1→B1 | A2→B2 | A3→B3 |
|---|---:|---:|---:|---:|---:|---:|
| 효과 K3 | 6 | 170.96 [135.99–189.49]; CV 13.41% | 93.72 [60.80–106.38]; CV 22.09% | -55.29% | -45.18% | -43.86% |
| 타깃 K1 (동일 코드) | 6 | 187.47 [177.92–202.46]; CV 5.34% | 165.20 [149.82–225.96]; CV 18.23% | -26.00% | +27.01% | -11.88% |
| 엔지니어 | 6 | 3,381.56 [3,128.00–3,654.38]; CV 6.34% | 2,035.36 [2,004.44–2,166.87]; CV 3.40% | -35.92% | -40.70% | -39.81% |
| 효과 K3 | 32 | 1,194.65 [1,072.27–1,273.20]; CV 7.01% | 204.66 [150.90–249.42]; CV 19.97% | -85.93% | -80.41% | -82.87% |
| 타깃 K1 (동일 코드) | 32 | 392.24 [378.20–396.00]; CV 1.97% | 394.77 [313.43–462.43]; CV 15.61% | -20.85% | +0.65% | +22.27% |
| 엔지니어 | 32 | 28,547.49 [26,471.67–29,988.41]; CV 5.09% | 12,227.22 [11,010.66–12,379.11]; CV 5.16% | -58.41% | -59.23% | -56.64% |
| 효과 K3 | 128 | 6,030.61 [6,022.35–6,206.28]; CV 1.39% | 568.28 [472.58–569.54]; CV 8.46% | -92.39% | -90.54% | -90.58% |
| 타깃 K1 (동일 코드) | 128 | 1,225.68 [1,210.63–1,502.06]; CV 10.21% | 1,314.36 [1,252.57–1,431.59]; CV 5.57% | -16.61% | +18.25% | +7.24% |
| 엔지니어 | 128 | 141,501.76 [141,385.60–163,255.52]; CV 6.91% | 45,817.77 [43,812.58–50,798.49]; CV 6.27% | -71.93% | -64.10% | -69.01% |
| 효과 K3 | 512 | 42,475.94 [42,025.05–43,583.11]; CV 1.53% | 2,236.07 [2,043.69–2,239.72]; CV 4.21% | -95.31% | -94.74% | -94.67% |
| 타깃 K1 (동일 코드) | 512 | 5,129.88 [5,122.61–6,020.26]; CV 7.77% | 5,009.23 [4,886.67–5,383.69]; CV 4.15% | -10.57% | -2.35% | -4.61% |
| 엔지니어 | 512 | 803,730.33 [775,547.92–817,805.71]; CV 2.20% | 188,703.17 [165,927.08–193,657.42]; CV 6.61% | -75.03% | -76.93% | -79.36% |
| 효과 K3 | 0 | 36.12 [28.79–69.50]; CV 39.54% | 7.91 [4.33–8.19]; CV 25.79% | -77.33% | -88.62% | -84.96% |
| 효과 K1 | 1 | 21.32 [14.82–25.01]; CV 20.67% | 10.19 [6.48–14.66]; CV 32.05% | -1.04% | -52.22% | -74.10% |
| 효과 K1 | 32 | 1,193.61 [1,050.28–1,290.37]; CV 8.37% | 125.43 [104.93–211.02]; CV 31.23% | -88.06% | -91.21% | -83.65% |
| 효과 K0 통제 | 32 | 7.67 [4.89–9.93]; CV 27.46% | 7.57 [3.45–11.72]; CV 44.56% | +139.52% | -1.26% | -65.26% |
| 타깃 K3 통제 | 32 | 685.82 [623.34–700.65]; CV 5.00% | 549.15 [503.35–576.80]; CV 5.58% | -17.68% | -19.25% | -19.93% |
| checksum 통제 | 0 | 3.45 [2.77–3.99]; CV 14.66% | 4.21 [2.74–4.39]; CV 19.56% | +51.62% | +9.96% | -20.69% |

엔지니어 N6/32/128/512의 batch 중앙값은 각각 약 3,382→2,035ns, 28,547→12,227ns, 141,502→45,818ns, 803,730→188,703ns다. 네 크기 모두 세 쌍에서 batch와 CPU가 감소했고 직접 할당도 약 34.13/59.65/72.32/79.32% 감소했다. 효과 K3 N6/32/128/512도 세 쌍의 batch와 p95가 모두 감소했다. 직접 할당은 각각 약 184/1,088/3,712/15,144→40B/op다. N6의 약 0.000573B/op는 두 쪽에 공통으로 포함된 batch 계측 비용이다.

빈 효과 N0는 직접 할당 24→0B/op, batch 중앙값 약 36.12→7.91ns로 바뀌었다. 세 쌍의 batch와 p95가 모두 감소해 v2의 빈 효과 회귀가 이 최종 조합에서 다시 나타나지 않았다. 다만 CPU 전부 0·짧은 실제 워밍업·통제군 변동 때문에 정확한 상시 배율을 주장하지 않는다.

반례도 남는다. 효과 K1 N1은 할당 104→0B/op지만 첫 쌍 batch mean 감소는 1.04%뿐이고 p95는 21.2→28.4µs/1,024회로 33.96%, p99는 29.0→51.3µs/1,024회로 76.90% 증가했다. 나머지 두 쌍은 감소했다. 효과 K1 N32의 후보 할당은 B1/B2 0, **B3 26.25B/op**이므로 항상 무할당이라고 표현하지 않는다. 그 단계의 JFR 할당 샘플이 없더라도 직접 계측의 비영 할당을 무효로 하지 않는다.

### v4 GC와 wall 처리량

VM GC count/ms는 각 측정 창의 JVM 전체 집계이며 JFR pause는 stage marker와 겹치는 실제 pause 구간이다. 둘은 범위·해상도가 달라 같은 값으로 취급하지 않는다. 아래 슬래시 값의 순서는 각각 A1/A2/A3와 B1/B2/B3다. GC는 낮은 횟수와 분포를 함께 읽는다.

| 단계 | N | VM GC 횟수 A→B | VM GC ms A→B | JFR pause ms A→B | wall ops/s 중앙값 A→B |
|---|---:|---:|---:|---:|---:|
| 효과 K3 | 6 | 0/0/0 → 0/0/0 | 0/0/0 → 0/0/0 | 0.000/0.000/0.000 → 0.000/0.000/0.000 | 3,157,342.98 → 3,881,659.92 |
| 타깃 K1 (동일 코드) | 6 | 0/0/0 → 0/0/0 | 0/0/0 → 0/0/0 | 0.000/0.000/0.000 → 0.000/0.000/0.000 | 3,728,182.49 → 4,071,408.69 |
| 엔지니어 | 6 | 2/1/1 → 1/1/1 | 8/15/12 → 10/5/12 | 8.012/14.818/12.256 → 10.348/4.677/12.495 | 287,527.81 → 468,419.64 |
| 효과 K3 | 32 | 0/1/1 → 0/0/0 | 0/15/13 → 0/0/0 | 0.000/14.841/12.530 → 0.000/0.000/0.000 | 793,811.99 → 3,727,015.52 |
| 타깃 K1 (동일 코드) | 32 | 0/0/0 → 0/0/0 | 0/0/0 → 0/0/0 | 0.000/0.000/0.000 → 0.000/0.000/0.000 | 2,262,353.47 → 2,226,926.09 |
| 엔지니어 | 32 | 3/3/3 → 3/1/1 | 11/41/40 → 14/4/14 | 11.146/41.093/40.458 → 15.076/4.124/13.290 | 34,639.22 → 80,134.38 |
| 효과 K3 | 128 | 1/1/1 → 0/0/0 | 4/4/4 → 0/0/0 | 3.859/4.085/3.191 → 0.000/0.000/0.000 | 164,107.28 → 1,614,444.23 |
| 타깃 K1 (동일 코드) | 128 | 0/0/0 → 0/0/0 | 0/0/0 → 0/0/0 | 0.000/0.000/0.000 → 0.000/0.000/0.000 | 791,066.31 → 729,043.21 |
| 엔지니어 | 128 | 4/4/4 → 2/1/1 | 14/12/12 → 16/4/13 | 13.808/12.049/12.590 → 16.026/3.982/13.410 | 7,026.03 → 21,453.89 |
| 효과 K3 | 512 | 2/2/2 → 0/0/0 | 5/3/3 → 0/0/0 | 4.191/3.584/3.769 → 0.000/0.000/0.000 | 23,506.34 → 440,317.87 |
| 타깃 K1 (동일 코드) | 512 | 0/0/0 → 0/0/0 | 0/0/0 → 0/0/0 | 0.000/0.000/0.000 → 0.000/0.000/0.000 | 193,340.31 → 197,966.31 |
| 엔지니어 | 512 | 5/5/5 → 1/2/1 | 6/6/5 → 11/9/12 | 5.762/5.933/5.312 → 10.420/8.759/12.236 | 1,239.49 → 5,229.20 |
| 효과 K3 | 0 | 0/0/0 → 0/0/0 | 0/0/0 → 0/0/0 | 0.000/0.000/0.000 → 0.000/0.000/0.000 | 18,508,477.03 → 33,003,867.64 |
| 효과 K1 | 1 | 0/0/0 → 0/0/0 | 0/0/0 → 0/0/0 | 0.000/0.000/0.000 → 0.000/0.000/0.000 | 21,995,961.68 → 36,465,504.96 |
| 효과 K1 | 32 | 0/0/0 → 0/0/0 | 0/0/0 → 0/0/0 | 0.000/0.000/0.000 → 0.000/0.000/0.000 | 816,539.61 → 6,350,046.30 |
| 효과 K0 통제 | 32 | 0/0/0 → 0/0/0 | 0/0/0 → 0/0/0 | 0.000/0.000/0.000 → 0.000/0.000/0.000 | 43,403,317.42 → 39,995,833.77 |
| 타깃 K3 통제 | 32 | 0/0/0 → 0/0/0 | 0/0/0 → 0/0/0 | 0.000/0.000/0.000 → 0.000/0.000/0.000 | 1,395,502.23 → 1,764,482.89 |
| checksum 통제 | 0 | 0/0/0 → 0/0/0 | 0/0/0 → 0/0/0 | 0.000/0.000/0.000 → 0.000/0.000/0.000 | 51,172,707.89 → 45,516,505.66 |

**할당 감소가 모든 GC 지표 개선으로 이어지지는 않았다.** 엔지니어 N512의 GC pause는 A 5.762/5.933/5.312→B 10.420/8.759/12.236ms로 세 쌍 모두 증가했다. 같은 단계의 VM GC ms도 6/6/5→11/9/12로 증가했다. N6·N32의 첫 쌍과 N128의 첫·세 번째 쌍도 VM GC ms가 늘었다. 세 JVM 중앙값이나 전체 GC 합계만으로 이 반례를 숨기지 않는다.

### v4 통제군·JFR와 해석 한계

타깃 소스 SHA-256 `9c67c6c983ba4accf202ad637454734ecc1dfe71a797be766a484ff66bab5581`은 양쪽 동일하다. 그럼에도 타깃 K1 N32 batch는 −20.9/+0.6/+22.3%, N128은 −16.6/+18.3/+7.2%로 움직였고 N128 p95는 두 번째·세 번째 쌍에서 +91.4/+72.3% 증가했다. 효과 K0 통제의 batch는 +139.5/−1.3/−65.3%, checksum 통제는 +51.6/+10.0/−20.7%다. 수정 없는 타깃 K3도 세 쌍에서 약 17.7/19.3/19.9% 감소했다. 따라서 모든 시간 변화를 두 변경의 직접 인과로 단정하거나 통제 값을 빼서 보정하지 않는다.

타깃 K3의 직접 할당은 A 256/312/256→B 256/256/256B/op다. v3에서 후보에 일관되게 나타났던 약 22% 할당 증가는 h에서 재현되지 않았지만, A2의 추가 56B/op 원인은 확정하지 않았다. JFR의 `first` 프레임은 A1/A2/A3 Inlined 16/9/28표본, B1/B2/B3 Inlined 38/13/10표본으로 양쪽 모두 같은 분류였다. 이전 v3의 기준 Inlined·후보 JIT compiled 차이가 이번에는 관찰되지 않았다는 뜻이며, 소스 크기 임계값·컴파일러 인라이닝/escape-analysis 결정의 인과를 확정한 것은 아니다.

| 실행 | 검증 stage 수 | server Java 표본 | Java 표본 없는 stage | 측정 창 GC pause 합계 ms |
|---|---:|---:|---:|---:|
| A1h | 18 | 1311 | 3 | 46.779 |
| B1h | 18 | 296 | 3 | 51.869 |
| B2h | 18 | 321 | 2 | 21.541 |
| A2h | 18 | 1275 | 2 | 96.403 |
| A3h | 18 | 1250 | 3 | 90.107 |
| B3h | 18 | 291 | 4 | 51.431 |

JFR은 custom stage marker 안의 사건만 포함하고 경계를 가로지르는 GC duration을 겹친 길이로 잘랐다. 워밍업·단계 사이 이벤트는 제외했다. 샘플 할당 weight는 직접 server-thread batch 할당 총량이 아니며 샘플이 없는 stage는 CPU/할당이 0이라는 증거가 아니다. 짧은 작은 입력에서 샘플이 없고, 고정 stage 순서·JIT/힙 이력·profiler와 관측 도구 비용을 완전히 분리하지 못했다. 각 variant 세 JVM은 기술적 비교이며 통계적 유의성이나 운영 환경의 고정 개선율을 확립하지 않는다.

기준·후보의 함수 입력·출력 검증과 반복된 비용 감소는 **측정한 효과 합산·엔지니어 선택 경로의 개선 근거**다. 모든 경로·꼬리 지연·GC의 무회귀, 전체 서버 가속, 실제 전투 성능 또는 전체 게임 동등성의 증거는 아니다. 외부 부하의 상세 판정은 다음 절에 분리해 기록한다.

### v4 전체 부하·누락·미귀속 프로세스

`profile-final-two-v4-load-review.json`의 최종 판정은 `REVIEW_COMPLETE_WITH_ATTRIBUTION_AND_SAMPLING_LIMITS`다. 관측기는 12:24:12.140–12:29:39.173 UTC에 동작했고 12:24:14.168–12:29:38.171 UTC의 연속 163개 표본을 기록했다. 평균 간격은 2.000055초(min 1.995790/max 2.005017초)다. 첫 세 표본은 A1h 전에 기록됐고 마지막은 B3h 종료 직전까지 이어진다. 관측 종료와 별도의 사후 점검도 보존했다. 새 PID·instance/PID 변경·종료를 관측했지만 2초보다 짧은 프로세스와 순간 spike를 완전히 포착하지는 못한다.

아래 전체 CPU는 벤치마크 Java·Gradle 시작·관측기·OS 및 다른 프로그램을 모두 포함한다. GPU는 물리 엔진별 합산 중 최대값이며 전체 GPU 이용률의 합산값이 아니다. PhysicalDisk throughput은 십진 MB/s(1,000,000B/s)로 표기했다. Process I/O에는 네트워크·장치 I/O도 섞이므로 이를 실제 디스크 처리량으로 대신하지 않는다.

| 전체 관측 지표 | 유효 표본 수 | 평균 | p95 | 최대 |
|---|---:|---:|---:|---:|
| 전체 CPU | 163 | 24.327% | 37.702% | 56.348% |
| GPU 최대 물리 엔진 (완전 표본) | 162 | 1.914% | 2.462% | 4.748% |
| PhysicalDisk time | 163 | 1.093% | 5.452% | 7.303% |
| PhysicalDisk throughput | 163 | 2.706MB/s | 11.454MB/s | 22.759MB/s |

각 실행 구간의 부하는 다음과 같다. 시작·준비를 포함한 실행 단위 값이므로 이것으로 알고리즘별 외부 부하가 같았다고 판정하지 않는다. non-Java/non-observer CPU는 유효한 mapping의 이름별 합계일 뿐이며 OS·오케스트레이터와 누락이 포함돼 진짜 외부 부하 전체를 뜻하지 않는다.

| 실행 | 표본 수 | 전체 CPU 평균/p95/최대 % | non-Java/non-observer CPU 평균 % | GPU 완전 표본 평균/최대 % | disk 평균 MB/s |
|---|---:|---:|---:|---:|---:|
| A1h | 30 | 23.42/33.80/36.43 | 9.45 | 1.84/2.27 | 2.025 |
| B1h | 22 | 29.03/47.76/56.35 | 11.94 | 2.42/4.75 | 2.033 |
| B2h | 22 | 25.18/39.00/39.36 | 9.67 | 1.85/2.46 | 2.955 |
| A2h | 30 | 23.40/37.70/37.85 | 9.29 | 1.85/2.17 | 3.235 |
| A3h | 30 | 22.55/32.73/40.72 | 9.19 | 1.84/2.40 | 2.742 |
| B3h | 22 | 25.09/37.05/41.55 | 9.42 | 1.76/2.01 | 2.301 |

JFR 측정 stage와 조금이라도 겹치는 46개 표본의 전체 CPU는 평균 20.20%, p95 27.75%, 최대 31.73%다. 그러나 108개 stage 중 105개는 짧아서 stage 안에 온전히 들어가는 2초 표본이 없다. 같은 PDH 평균 구간이 여러 stage에 겹치므로 정밀한 단계별 부하나 독립 표본으로 취급하지 않는다. 전체 창에서 non-Java/non-observer CPU 평균은 9.80%였지만 이를 benchmark 비용에서 빼지 않는다.

| 계측 항목 | 확인한 사실과 남는 제한 |
|---|---|
| core counter | 163개 모두 Processor 전체 CPU·PhysicalDisk·Process ID 배열을 기록했다. setup/query/array-wide/core 오류는 0이다. 이것은 모든 개별 GPU·process rate 값이 완전했다는 뜻이 아니다. |
| process rate | `0xC0000BBA` 오류 375개를 보존했다. 안정된 PID mapping의 CPU/I/O 누락은 0이며 신규·교체 등 불안정 PID의 누락 행은 194개다. 오류 수와 행 수는 단위가 다르다. |
| Process(_Total) | 위 375개 중 13개는 PID 행이 없는 `Process(_Total)` CPU/I/O 값 오류다. 이를 새 PID의 정상 lifecycle로 분류하지 않았다. 전체 CPU는 별도의 `Processor(_Total)`, 디스크는 `PhysicalDisk(_Total)`을 사용했고 이 13개 값을 0으로 채우지 않았다. |
| GPU | sample 122에서 기존 DWM PID 1916의 GPU engine 17개가 누락됐다. 따라서 완전한 GPU 표본은 162개다. 해당 표본의 부분 관측 최대값을 완전한 GPU 최대값으로 사용하지 않았다. |
| process lifecycle | 새 PID 172회, instance/PID 변경 78회, 소멸 157회를 기록했다. 일부 보호 프로세스의 생성 시간 접근 실패로 PID 재사용을 모두 검증할 수 없고, 표본 사이 짧은 생존도 놓칠 수 있다. |
| 관측기 비용 | 수집 wall time 평균 19.71ms·p95 24.29ms, 관측기 PDH CPU 평균 약 0.0698% machine이다. 비용은 0이 아니며 serialization/flush·유발된 OS 작업까지 완전히 분리한 총비용이 아니다. |

GPU 누락의 관측 구간은 12:28:14.169–12:28:16.170 UTC로 어느 JFR 측정 stage와도 직접 겹치지 않는다. A3h의 첫 측정 stage는 12:28:31.102 UTC부터다. 다음 GPU 표본은 다시 완전했다. **이는 직접 측정 창 겹침이 없다는 사실이며 환경 영향의 인과가 완전히 배제됐다는 증명은 아니다.** 기존 DWM 값의 일시 누락을 새 게임 클라이언트 실행의 증거로 바꾸지 않는다.

A3h에서 추가 `java` PID 26608이 sample 134에 한 번 관측됐다. 그 표본의 약 12:28:38.168–12:28:40.170 UTC 구간은 JFR 측정 stage와 직접 겹치지 않는다. 그러나 생성 시각은 12:28:39.955342 UTC이고 다음 12:28:42.170 표본에서 부재하며 **실제 종료 시각은 모른다.** 이 가능한 생존 구간은 A3h 효과 K3 N512의 12:28:41.430900–12:28:47.896407 UTC 측정 시작 부분과 관측 타임스탬프상 최대 약 0.739초 겹칠 수 있다. 수집 경계에도 약 17ms의 불확실성이 있다. 타깃·엔지니어 측정 stage와는 이 가능한 생존 구간이 겹치지 않는다.

PID 26608의 CPU/I/O rate와 실행 역할을 귀속하지 못했으므로 HOI·Gradle·GameTest·Minecraft 클라이언트 중 하나라고 단정하지 않는다. 실행 메타데이터에 JVM별 PID/class를 저장하지 않아 다른 Java의 역할도 이름·시간만으로 완전히 확정할 수 없다. dotnet·MSBuild·명시적 Minecraft/Prism/javaw 이름은 관측되지 않았지만 `java.exe` 이름만으로 클라이언트 부재를 증명하지 않는다. 과거 v3 클라이언트 실행 가정도 이번 창에 적용하지 않는다.

소스/측정 파일 손상이나 core counter 실패는 확인되지 않았고 여섯 실행을 모두 보존했다. A3h를 제외해 평균을 좋게 만들거나 관측 부하를 빼서 보정하지 않는다. **깨끗한 독점 환경·동일한 외부 부하·짧은 간섭의 부재는 입증되지 않았다.** 따라서 큰 비용 감소가 여러 입력·세 쌍에서 반복된 관측은 유지하면서, 작은 지연 차이의 확정적 인과나 보편적 개선율은 주장하지 않는다.

`profile-final-two-v4-load-after.json`의 12:32:17.930864 UTC 점검은 JFR export 이후 CPU 12%, 가용 메모리 41.17GiB, disk busy 0·173,216B/s, GPU engine 최대/합계 1%, 선택한 검사 프로세스 및 보호 포트 listener 없음으로 기록됐다. 이 값은 측정에 포함하지 않는다. HOI 검증을 재개할 수 있도록 측정 창을 반환했으며 기존 운영/대화형 서버를 시작하지 않았다.

**최종 판단은 두 production 변경 유지, 추가 소스 변경 없음이다.** 효과 큰 입력·빈 경로와 엔지니어 선택의 반복된 개선 근거를 채택하되 N1 p95/p99·N512 GC 증가와 위 부하·해상도 한계를 남긴다. 이번 h 시리즈를 실제 전투 수치로 이관하지 않고 전투 엄격 실패·gameTime 차이와 비교 금지를 유지한다.

### v4 분석 산출물

W의 `profiles/comparison-final-two-v4.json`과 `.md`, `comparison-final-two-v4-individual.csv`·`comparison-final-two-v4-summary.csv`·`comparison-final-two-v4-pairs.csv`에는 모든 지표의 개별 실행값·중앙값·min/max/range/CV·세 쌍 변화와 flag가 있다. `final-two-h-independent-review.json`에는 production 차이·입출력·source/artifact 해시·직접 CPU 양자와 단계별 분포가 있으며 `final-two-h-jfr-detail-review.json`에는 효과 K1·타깃 K3의 프레임·표본 할당 상세가 있다. 원본은 `profiles/algorithm-profile-{A1h,B1h,B2h,A2h,A3h,B3h}.{jsonl,jfr}`와 JFR export JSON이다.

외부 부하 검토는 `profile-final-two-v4-load-review.json`, 원본은 `profile-final-two-v4-load-02.jsonl`, 창·종료 상태는 `profile-final-two-v4-window-02.json`과 `profile-final-two-v4-load-after.json`에 있다. 원본 load-02 SHA-256은 `41f8e0720c27d20f6f8b18f50a15c9f6e058d37f47278827602c77ea9ef11620`이다. 오류가 있는 첫 준비 기록도 별도로 보존했다.

기존 v2/v3·전투 실패·진단 기록은 덮어쓰지 않았다. 측정 후 현재 저장소의 runtime/test/build 1,568개 파일이 h candidate·이전 gate-2 manifest와 일치하고 초기 변경 13개도 보존됐음을 확인했다. 추가 구현 변경이 없으므로 같은 소스의 기존 gate 성공 근거를 유지한다. 이번 측정 때문에 전체 JUnit·GameTest를 새로 실행했다고 표현하지 않는다. 기존 전투 엄격 비교 501/501·176/501·429/501과 각각의 환경 gameTime 차이, `DIVERGED_PERFORMANCE_COMPARISON_FORBIDDEN` 상태, DA1/DB1 집계 원인 진단 및 관측 범위 한계는 그대로다.
