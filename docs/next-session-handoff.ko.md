# 서버 유지보수 인수인계

이 문서는 Minecraft 26.3 이식의 빌드·검증 및 명시적으로 승인된 배포의 인수인계 절차를 다룬다. 공유 소스는 `C:\steve-td`, 운영 설치는 `C:\SemionTD`다. 빌드 완료와 운영 배포·기동 승인은 별개이며 운영 월드를 개발 테스트에 사용하지 않는다.

## 운영 기준

| 항목 | 현재 값 |
|---|---|
| 저장소 | `steve-td` |
| 기본 브랜치 | `master` |
| Minecraft | `26.3` |
| Fabric Loader | `0.19.5` |
| Java target | `25` |
| 현재 검증 JDK | `25.0.4.101` |
| 빌드 JAR | `build/libs/semion-td-1.0-SNAPSHOT+26.3.jar` |
| 서버 루트 | `C:\SemionTD` |
| 배포 JAR | `C:\SemionTD/mods/semion-td-1.0-SNAPSHOT+26.3.jar` |
| 운영 설정·데이터 | `C:\SemionTD/config/semion-td/` |

버전 판단에는 `gradle.properties`와 `src/main/resources/fabric.mod.json`을 사용한다. 명령어는 `SemionCommands`, 설정 기본값은 `SemionConfigLoader`와 각 config record를 기준으로 확인한다.

## 새 작업 환경 준비

1. Java 25를 설치한다.
2. 저장소를 clone하고 `master`를 checkout한다.
3. 소유자에게 비공개 리소스 원본을 받아 `src/main/resources/assets/semion-td/`에 배치한다.
4. `./gradlew compileJava`로 의존성과 toolchain을 확인한다.

### 비공개 리소스 규칙

`src/main/resources/assets/semion-td/`의 모델, 텍스처, 애니메이션은 별도 구매한 리소스다. `.gitignore`가 이 경로를 제외한다.

```bash
git check-ignore -v src/main/resources/assets/semion-td/textures/item/cosmetics/rabbit_body.png
git status --ignored --short src/main/resources/assets/semion-td
```

두 번째 명령은 디렉터리를 `!!`로 표시해야 한다. `git add -f`를 사용하지 않는다. 공개 GitHub Release나 공개 CI artifact에도 리소스가 포함된 JAR을 올리지 않는다. 새 담당자는 소유자가 관리하는 비공개 전달 경로로 원본을 받는다.

## 배포 전 검증

```bash
./gradlew test runGameTest remapJar
```

완료 기준:

- 단위 테스트가 통과한다.
- 등록된 모든 필수 Fabric GameTest가 통과한다. 실패가 남으면 배포 검증 완료로 처리하지 않는다.
- `build/libs/semion-td-1.0-SNAPSHOT+26.3.jar`가 생성된다.
- `git diff --check`가 오류를 출력하지 않는다.
- `git status --short`에 `src/main/resources/assets/semion-td/`가 나타나지 않는다.

테스트 수·결과·산출물 SHA-256은 해당 실행의 보고서에 보관한다. 26.3은 난독화 해제된 배포 구조를 사용하므로 `remapJar`는 배포 JAR을 생성하는 `jar` 태스크에 연결된 호환 진입점이다.

리소스팩은 원본 ZIP을 보존한 별도 복사본에서 필요한 자산을 복구하고, 현재 Polymer/danta 생성 결과와 병합한다. 현재 셰이더를 오래된 원본 셰이더로 덮어쓰지 않는다. 스카이박스는 알파 252 마커 픽셀만 조명·안개를 우회하며, 26.3의 `mix(overlay.rgb, color.rgb, overlay.a)`에서 원래 색을 유지하려면 중립 오버레이 알파가 1이어야 한다. 일반 아이템·OIT 분기는 유지한다. 실제 전달 팩의 SHA-1과 클라이언트 다운로드 캐시를 대조하고 GUI scale 2에서 하늘·HUD·폰트·반투명 표시를 확인한다.

운영 모드를 정리하기 전 직접·전이 의존성, 설정과 저장된 레지스트리 ID를 확인한다. Nightlights, PolyFactory, PolyDecorations처럼 저장 콘텐츠에 사용되는 모드는 호환 버전 또는 검증된 이식본 없이 제거하지 않는다. 선택 모드는 삭제 대신 해시와 복원 경로가 기록된 별도 디렉터리로 옮긴다.

## 운영 서버 배포

배포와 운영 서버 시작·재시작은 각각 사용자의 명시적 지시가 필요하다. 다음 절차는 자체적으로 실행 권한을 부여하지 않는다.

1. 승인된 유지보수 범위와 서버 종료 상태를 확인한다.
2. 기존 런처, 모드, 리소스팩, 설정, 월드, 데이터베이스와 WAL/SHM을 보존하고 파일별 SHA-256으로 백업을 검증한다.
3. 전체 검증을 통과한 정확한 JAR·의존성·리소스팩 목록을 적용하고 원본과 대상 해시를 비교한다.
4. 기동 지시가 없으면 파일 적용 결과와 복원 경로를 전달하고 서버를 시작하지 않는다.

런처만 새 버전으로 교체해도 이전 모드가 호환되는 것은 아니다. 필수 콘텐츠나 전체 검증이 미완료인 설치는 시작 가능한 배포로 취급하지 않는다.

## 기동 확인

격리된 새 월드에서 수행한다. 운영 환경에서는 별도 기동 승인 후에만 수행하며 기존 운영 월드를 변환하는 테스트로 대체하지 않는다.

로그에서 다음 항목을 확인한다.

- `Semion TD initialized.`
- Polymer resource pack 생성 성공
- mixin 적용 실패, registry sync 실패, config parse 실패가 없음
- 로비 로드 완료

콘솔 smoke:

```text
semiontd status
semiontd create
semiontd status
semiontd status teams
semiontd reset
semiontd status
```

마지막 상태는 `activeGame=false`, `arenaLoaded=false`여야 한다. 전체 경기 smoke는 [Carpet QA Runbook](carpet-qa-runbook.ko.md)을 따른다.

## 롤백

1. 승인된 작업 범위 안에서 종료 상태를 확인한다.
2. 적용한 JAR을 별도 보관한다.
3. 기록된 백업 경로에서 이전 런처와 모드 세트를 복원하고 해시를 확인한다.
4. 설정이나 데이터 복원은 변경 범위와 백업 시점을 검토하여 별도 승인 범위에서 수행한다.
5. 기동 승인이 있는 경우에만 서버를 시작하고 검증한다.

JAR을 교체한 뒤 실행 중인 Java 프로세스가 자동으로 새 클래스를 읽지는 않는다. 로그 스택트레이스의 줄 번호가 현재 소스와 다르면 서버 시작 시각, JAR 수정 시각, SHA-256을 먼저 비교한다.

## 설정과 운영 데이터

서버를 끈 상태에서 다음 파일을 함께 백업한다.

- `profiles.json`, `cosmetics.json`, `build_guides.json`
- `semiontd.db`, `semiontd.db-wal`, `semiontd.db-shm`
- `job-statistics.db`, `job-statistics.db-wal`, `job-statistics.db-shm`
- `skyboxes/`, `music/`
- 운영자가 수정한 `*.json` 설정

적용 명령:

| 변경 | 적용 방법 |
|---|---|
| 경제, 웨이브, 타워 밸런스 등 일반 설정 | `/semiontd reload` |
| `cosmetics.json` | `/semiontd cosmetic reload` |
| `skyboxes/`, `music/` | `/semiontd resourcepack reload` |
| 맵 설정 | reset 후 다음 create |
| mod 내장 모델·텍스처와 Java 코드 | 새 JAR 배포 후 서버 재시작 |

운영 데이터 형식과 각 설정 필드는 [설정 파일](config-reference.ko.md)을 본다.

## 밸런스 패치 알림

서버는 공개 저장소 `biryeongtrain/semiontd-balance`의 `main` 최신 커밋을 5분마다 확인한다. 처음 확인할 때는 `balance_notification_state.json`에 현재 SHA만 저장하고 알림하지 않는다. 이후 새 커밋이 확인되면 그 시점에 접속 중인 플레이어에게 커밋 제목, 변경사항 최대 3개, GitHub 전체 내역 링크를 표시한다.

조회 실패나 잘못된 응답에서는 마지막 SHA를 바꾸지 않고 다음 주기에 다시 시도한다. 이 기능은 밸런스 파일을 내려받거나 `/semiontd reload`를 실행하지 않으므로, 실제 설정 반영은 기존 운영 절차대로 별도로 수행한다. Webhook 포트나 GitHub 인증 정보는 필요하지 않다.

## 현재 치장 시스템

- 서버는 Polymer 치장 아이템 123개를 등록한다. 머리 치장 121개와 왼손 `leiheng_blade`, `rabbit_body` 2개다.
- `cosmetics.json`은 `head`, `offhand` 슬롯을 저장한다. `slot`이 없는 기존 항목은 `head`로 읽는다.
- `/semiontd cosmetic add <id> <price> [head|offhand]`로 등록한다. 슬롯을 생략하면 `head`다.
- `/semiontd cosmetic update <id> <price> [head|offhand]`에서 슬롯을 생략하면 기존 슬롯을 유지한다.
- 플레이어는 머리와 왼손 치장을 함께 선택할 수 있다. 같은 슬롯에는 한 상품만 남는다.
- `profiles.json`의 현재 선택 필드는 `selectedCosmeticIds`다. 예전 `selectedCosmeticId`는 호환용으로 읽는다.
- 왼손 치장은 상점에서 해제한다. 손 교체, 드롭, 인벤토리 이동으로 제거하지 않는다.

2026-07-16 운영 catalog에는 167개 항목이 있었다. 이 숫자는 운영 중 바뀔 수 있으므로 `/semiontd cosmetic list` 또는 `cosmetics.json`의 `entries` 길이로 다시 확인한다.

## 이번 코드 상태의 게임 변경

- 염소 지원 타워는 일반 무리 타워에도 피해 증가와 받는 피해 감소를 함께 부여한다.
- 환영에는 `cloneDamageBonus`, `cloneDamageReduction` 값을 따로 적용한다.
- 염소 버프는 최대 3스택이며 `buffDurationTicks` 동안 유지되고 pulse마다 갱신된다.
- 오른쪽 클릭 타워 상세에 피해 증가와 받는 피해 감소가 표시된다.

수치는 `tower_balance.json`의 염소 ability 항목과 [타워 수치 설정](tower-balance-reference.ko.md)을 함께 확인한다.

## 장애 확인 순서

1. `semiontd status`, `status teams`, `status lanes`, `status players`를 저장한다.
2. `logs/latest.log`에서 첫 예외를 찾는다.
3. 배포 JAR과 빌드 JAR의 SHA-256을 비교한다.
4. config parse 오류면 실패한 파일을 백업본과 비교한다.
5. 새 JAR에서만 재현되면 이전 JAR로 롤백한다.
6. 서버 상태가 꼬였지만 프로세스는 정상이라면 `semiontd reset` 후 status를 확인한다.

화면, HUD, DialogUtils, 리소스팩, 치장 모델 문제는 서버 로그만으로 닫지 않는다. 실제 클라이언트에서 확인하고 [서비스 준비 체크리스트](service-readiness-checklist.ko.md)에 날짜와 결과를 남긴다.

## 커밋 전 확인

```bash
git status --short
git diff --check
./gradlew test runGameTest remapJar
git status --ignored --short src/main/resources/assets/semion-td
```

문서, 코드, 테스트만 stage한다. 운영 config, DB, 로그, 백업 JAR, 구매 리소스는 commit하지 않는다.

## 호환 모듈과 저장 경로

`compat/`의 danta, Friends & Foes, Polymer 패치와 Nightlights는 메인 배포 JAR의 중첩 의존성이다. ChatHeads, WorldEdit Hang Fix, HoloDisplays, Sylcurity는 각각 별도 JAR로 설치한다. 각 모듈의 원본 라이선스와 `SOURCE_NOTICE`를 유지하고 메인 JAR만 교체하여 외부 모드 세트 검증을 생략하지 않는다.

Avatar 렌더러의 작업 스레드는 종료 이벤트에서 정리한다. 실제 서버가 저장을 끝낸 뒤 JVM까지 정상 종료되는지 확인한다. headless 검증 환경의 SessionService 부재는 ChatHeads 기본 아이콘으로 처리하며 실제 서버의 프로필 조회 동작과 구분한다.

26.3 저장소 이동으로 `playerdata`, `advancements`, `stats`, `player-mod-data`는 `players/data`, `players/advancements`, `players/stats`, `players/mod_data`로 옮겨질 수 있다. 파일 경로가 달라졌다는 이유만으로 삭제로 판단하지 말고 백업과 내용 해시를 대조한다. 월드 팩도 `world/resources.zip`에서 `world/resourcepacks/resources.zip`으로 이동하므로 현재 레이아웃을 확인한다. 실제 클라이언트 전달 파일은 `polymer/resource_pack.zip`이며 업로드 해시와 대조한다. 기존 설정의 자동 스키마 변환은 해당 config loader와 배포 전 소스를 비교한다.

외부 리소스팩 공급자는 생성된 팩의 SHA-1을 URL 파일명에 사용한다. R2 업로더를 별도 실행하는 구성에서는 팩을 새로 생성할 때마다 현재 해시의 파일을 업로드하고 공개 다운로드의 SHA-1·SHA-256을 확인해야 한다. 이전 버전 업로더 JAR이나 이전 팩 URL을 그대로 활성화하지 않는다. 자격 증명은 기존 비공개 운영 설정에서만 읽고 로그·소스에 넣지 않는다.

## 빌더 공통 경로와 책임 분리 검증

`GameLaneWaveOrder`는 자연 웨이브의 순서·힐러 배치·보상 분배를 계산하고, `PlayerLane`은 큐·엔티티·레인 수명주기를 연결한다. `UiPlayerAvatarService`는 비동기 스킨 조회·캐시·아바타 이미지를 맡으며 대화상자 서비스는 배치와 문구를 유지한다. 공백이 있는 샌드박스 이름 등 Minecraft 프로필 이름이 아닌 값은 원격 요청 없이 기본 아바타를 사용한다.

레인 틱은 기존 identity 멤버십 인덱스를 사용한다. 콜백 전에 스냅숏을 만들므로 앞선 콜백에서 삭제된 타워는 실행하지 않고, 추가·교체된 타워는 다음 틱부터 실행한다. 타워 수 n에 대해 멤버십 조회의 합계가 O(n²)에서 평균 O(n)으로 줄며 스냅숏 할당 O(n)은 유지된다. 몬스터 FIFO는 ArrayDeque로 보관하여 앞 원소 제거 시 배열 이동을 없앤다. 큐 삽입은 상각 O(1), 드물게 확장 시 O(n), 전체 배출은 O(n)이며 기존 소환 순서·간격·보상을 유지한다.

`TowerBuilderCatalogContractTest`는 모든 등록 빌더의 소유권·팩터리·업그레이드 간선과 원래/현재 좌표·지불 비용 보존을 검사한다. 설계도 빌더는 실제 동적 생성 경로로 등록한다. `SEMIONTD_TEST_TOWER_BALANCE`를 제공하면 해당 복사본을 읽으며, 미지정 시 현재 내장 기본값을 사용한다. 운영자가 오래된 `tower_balance.json`을 삭제한 경우 예전 백업을 현재 설정으로 되살리지 않는다. 기존 로더는 다음 기동/리로드에서 누락 설정을 기본값으로 생성하므로 운영 적용 전에 의도를 확인한다.

`GameLaneTickPerformanceTest`는 `semiontd.laneProfile=true`와 정확한 GameTest 필터 `semion-td-gametest:game_lane_tick_performance_test_measures_lane_dispatch`를 함께 지정한 격리 환경에서만 측정한다. Spark가 설치된 테스트 런타임에서 로컬 `.sparkprofile` 저장 완료를 기다린다. 기본 전체 게이트에서는 명시적으로 NOT_RUN을 기록한다. 입력은 무동작 타워 6·32·128·512개, 틱마다 100회 레인 호출, 40틱 예열과 150틱 측정이다. 호출 시간·서버 틱 시간의 평균/중앙/p95/최대, 서버 스레드 할당량·GC를 기록한다. 실행마다 준비된 상태·모드·JDK·입력을 맞추고 기준/책임 분리/알고리즘 개선을 따로 비교한다. 이 합성 디스패치 측정은 실제 전투, 20 TPS 실시간 경기, 클라이언트 부하나 메모리 전체 개선을 입증하지 않는다.

운영에만 설치하는 HoloDisplays·Sylcurity·ChatHeads·WorldEdit Hang Fix 전용 회귀 테스트는 SemionTD 기본 테스트 소스와 런타임 의존성에서 분리했다. SemionTD가 사용하는 AvatarRenderer·Nightlights·Friends & Foes·공용 저장/통신/UI 검증은 유지한다. 외부 모드의 배포 검증은 각 모듈 책임으로 별도 수행하며 기본 게이트 통과를 그 모드의 재검증으로 확대 해석하지 않는다.

패치 기준 수치 동기화의 출처와 회귀 계약은 [밸런스 문서](tower-balance-reference.ko.md#공개-패치와-기본-설정의-동기화)를 따른다. 테스트 경기장은 서버 시작 이후 생성되므로 headless 서버의 거리 설정을 새 월드에도 전달한다. 청크의 실제 `ENTITY_TICKING` 준비를 최대 400틱 기다리며, 이동 관측 700틱과 누수 판정은 유지한다.

빌더별 GameTest는 `src/gametest/java/kim/biryeong/semiontd/tower/<builder>/`에 두고 패키지·상위 책임·하위 책임에 맞춘 `…Test` 이름을 사용한다. `SemionParticipantGameTest`, `AugmentCombatGameTest`, `AugmentControllerGameTest`에는 참여자·증강 공통 동작을 남기고 특정 빌더의 전투·증강 선택 검사는 해당 빌더 패키지로 분리했다. `GameTestParticipantFixture`, `AugmentCombatFixture`, `AugmentControllerFixture`는 중복 준비 코드를 공유하며 테스트 진입점에는 등록하지 않는다. 여러 빌더의 타겟 정책을 비교하는 검사는 `tower/TowerBuilderTargetPolicyTest`가 담당한다. 클래스 이동 시 GameTest 소스셋·애너테이션·진입점과 모든 기존 단언을 함께 보존한다.
