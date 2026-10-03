# 프로덕션 타워 카탈로그

Minecraft 26.3 / Java 25의 현재 공통 API를 사용하는 제작 가이드입니다. 빌더 목록과 테스트 배치는 [빌더와 타워](builders-and-towers.ko.md), 설정·공식 패치의 값은 [밸런스 문서](tower-balance-reference.ko.md)를 기준으로 합니다.

## 책임과 코드 위치

| 책임 | 현재 코드 | 제작 시 재사용하는 부분 |
|---|---|---|
| 빌더 정의·수명 | `job/SemionJob`, `job/JobRegistry`, `job/JobBuilderLifecycle`, `job/JobLaneLifecycle` | 직업 정의와 이벤트 실행 분리, 공통 dispatch, 단계별 정리 |
| 타워 정의 | `tower/TowerType`, `tower/catalog/ProductionTowerDefinitions` | 안정적인 ID·기본 스탯·외형·설명 |
| 계열 등록 | `tower/<family>/*TowerCatalogs`, `ProductionTowerCatalogs.reloadBuiltIns` | 팩토리·티어·스타터·업그레이드 그래프 |
| 배치·거래 | `tower/ProductionTowerService` | 단계·권한·좌표·점유·한도·지출 검사와 행동 기록 |
| 런타임 | `Tower`, `EntityBackedTower`, `ProductionTower`, `SupportTower` | 상태·복사·엔티티 수명·기본 공격·지원 실행 |
| 전투 | `entity/tower/SemionTowerEntity`, `goal/TowerAttackMonsterGoal` | 대상 탐색·기본 공격·스탯 동기화 |
| 밸런스와 설명 | `TowerBalanceRuntime`, `TowerDescriptionRegistry` | 로드된 수치·가격·능력과 설명 치환 |
| 범위 효과·연출 | `SemionTdApi.areaEffects()`, `TowerAreaDamage`, `TowerVfxService` | 대상 필터·피해 출처·통계·처치·팔레트·전송 예산 |

경로는 `src/main/java/kim/biryeong/semiontd` 아래입니다. 주민 ADV는 `VillagerTowerCatalogs`에서 함께 등록합니다. 설계도는 `BlueprintStates`가 동적으로 생성하며 `ProductionTowerCatalogs.reloadBuiltIns`에서 다시 설치합니다. 따라서 고정 계열 목록이나 스타터 총수를 새 제작 코드에 복사하지 않습니다.

새 클래스 이름은 패키지·상위 책임·하위 책임 순서로 정합니다. 특정 계열의 동작은 `tower/<family>`에 두고, 다른 수명이나 호출자가 있는 상태·등록·표시 책임만 분리합니다. 한 계열을 위해 별도 배치·피해·통계 체계를 만들 필요는 없습니다.

## 흑마법사·엔드에서 가져올 구성 방식

[흑마법사 패키지](../src/main/java/kim/biryeong/semiontd/tower/warlock)와 [엔드 패키지](../src/main/java/kim/biryeong/semiontd/tower/end)는 타워 훅의 연결, 규칙, 상태, 실행, 표시를 구분하는 기준입니다. 모든 빌더에 같은 개수의 클래스를 만들거나 두 빌더의 고유 규칙을 복사하는 기준은 아닙니다.

| 책임 | 흑마법사 | 엔드 | 다른 빌더에 적용할 기준 |
|---|---|---|---|
| 공통 훅 연결 | `WarlockTower` | `EndTower` | 타워 클래스는 해당 동작의 호출 순서와 수명을 연결합니다. |
| 설정 해석 | `WarlockConfig`, `WarlockConfigReader`, `WarlockRules` | `EndConfig`, `EndAbilityKey` | 여러 계산이 공유하는 규칙에 한정해 타입으로 표현하고 리로드 의미를 보존합니다. |
| 상태와 계산 | `WarlockState`, `WarlockSacrifice`, `WarlockProgressionSnapshot` | `EndTransferState`, `EndTransferSnapshot`, `EndTransferStacks` | 가변 상태, 입력 스냅샷, 순수 계산을 구분하면 경계와 복사를 서버 없이 검사할 수 있습니다. |
| 실행 절차 | `WarlockSacrificeController`, `WarlockAwakeningController` | `EndTransferController`, `EndEvolutionController` | 실제 대상 선택·상태 반영·실패/취소 처리를 해당 동작 책임 안에 둡니다. |
| 전투 계산 | `WarlockCombat` | `EndCombat` | 공통 피해 파이프라인에 넣을 고유 계산을 담당합니다. |
| 상세 표시 | `WarlockStatsAssembler`, `WarlockStatsView` | `EndStatsAssembler`, `EndStatsView` | 런타임 값을 모으는 단계와 표시 문자열을 만드는 단계를 구분합니다. |

흑마법사의 희생은 대상을 캡처하고 이득을 계산한 뒤 실제 제거가 성공해야 상태에 반영합니다. 엔드의 전이는 여러 틱의 부분 기여를 추적하고 중단 시 되돌리며, 라운드 보너스와 영구 보너스를 구분합니다. 둘을 하나의 범용 성장 컨트롤러로 합치면 실패·복사·리로드 의미가 달라질 수 있으므로 각 상태 계약을 유지합니다.

테스트도 같은 경계를 따릅니다. `WarlockSacrificeTest`·`WarlockStateTest`는 계산과 상태, `EndTransferDomainTest`·`EndTransferLifecycleTest`는 전이와 정리, 각 가족의 `*TowerIntegrationTest`·`*TowerRuntimeTest`는 서버 동작을 검증합니다. 새 추출에는 실제 책임에 맞는 이름과 테스트를 함께 두며, 기존 레거시 이름을 기계적으로 복사하지 않습니다.

## 빌더별로 적용하는 책임 경계

흑마법사·엔드의 기존 책임 분리는 유지하고, 다른 계열에서는 실제로 독립된 실행·상태·표시·계산 책임을 추출합니다. 아래는 현재 코드에 연결된 예시입니다. 모든 빌더를 같은 파일 수로 나누거나 모든 타워에 controller/state/view를 만드는 규칙은 아닙니다. 기존 `MageTowerRuntime`·`MageTowerLifecycle`, `AtlantisPressure`·`AtlantisStates`, `IllagerRaidState`·`IllagerTargetPolicy`처럼 이미 경계가 있는 계열은 그 구조를 먼저 활용합니다.

| 분리할 책임 | 현재 구현과 실제 진입점 | 함께 볼 테스트 |
|---|---|---|
| 런타임 설명 생성 | `ArmyTowerStatsView.create`, `PetTowerStatsView.create`, `PlantTowerStatsView.create`; 펫은 상위 클래스 설명을 인자로 받아 유지 | `ArmyTowerStatsViewTest`, `PetTowerStatsViewTest`, `PlantTowerStatsViewTest` |
| 형태·확률 결과의 표시 | `AdversaryTowerFormStatsView.append`는 형태와 표시용 상태를 받고, `GambleTowerStatsView.upgradeTooltipLines`·`runtimeDetailLines`는 현재 설정과 상태를 읽음 | `AdversaryTowerFormStatsViewTest`, `GambleTowerStatsViewTest` |
| 지원 행동의 주기와 복사 | `HeroCompanionSupportController.tick`·`copyFrom`·`resetRound`; 설명은 `HeroCompanionStatsView.abilities`, 공통 fallback은 `HeroCompanionAbilityDefaults` | `HeroCompanionSupportControllerTest` |
| 독립적인 공간 계산 | `BodyTowerTargetGeometry.eyeDirection`·`insideEyeRay`, `DemonLordLaneGeometry.laneCentre`·`containsHorizontally` | `BodyTowerTargetGeometryTest`, `DemonLordLaneGeometryTest` |
| 특정 효과의 합산 규칙 | `DeveloperTowerPatchEfficiency.resolve`는 수신 타워와 레인에서 패치 효율을 계산 | `DeveloperTowerPatchEfficiencyTest` |
| 소환물 수명 관리 | `BlueprintTowerSummonController.summon`·`expire`·`dismiss`는 소환 ID, 만료 시각과 소환 진행을 관리 | 서버 엔티티와 소환자 사망 후 수명을 확인하는 `BlueprintTowerModuleTest` |
| 회로 탐색 | `EngineerTrapSignalController.select`는 `EngineerTrapSignalSnapshot` 맵에서 방향·거리·누른 시각을 비교 | `EngineerTrapSignalControllerTest` |
| 누적 자원의 소비와 복원 | `NetherBloodChargeController.recordNaturalLoss`·`consume`, `OceanCurrentController.recordSpent`·`tideActive`; 각각 `snapshot`·`restore` 제공 | `NetherBloodChargeControllerTest`, `OceanCurrentControllerTest` |
| 상태 전이 | `FrostFullOperationState.beginWave`·`record`·`activate`·`endWave`, `SuccubusDreamState.add`·`tickCounters`·`clearStacksForWake` | `FrostFullOperationStateTest`, `SuccubusDreamStateTest` |
| 여러 타워의 연결 갱신 | `ResonanceTowerLinkController.refresh`는 연결 계산, 상태 적용, 친구 선택, 오라 적용의 순서를 보존 | `ResonanceTowerLinkControllerTest`, `ResonanceTowerTest` |

이 도우미들은 해당 빌더 패키지 내부 구현입니다. 다른 빌더가 그대로 호출하는 공용 API로 노출하지 않습니다. 표시 도우미는 전투 상태나 RNG를 바꾸지 않고, 월드 엔티티를 다루는 컨트롤러는 순수 계산으로 취급하지 않습니다. `SuccubusDreamState`도 소유자·타워·몬스터·레인 참조를 갖는 가변 상태이므로 불변 스냅샷과 구분합니다.

복사와 초기화는 필드 수가 아니라 수명 기준으로 설계합니다. 용사 지원은 라운드 초기화 때 pulse만 초기화하고 cooldown은 유지하며, 업그레이드는 둘 다 복사합니다. 네더 충전은 적립 당시 임계값을 FIFO로 소비하고, 해양은 누적 소비량과 조수 시간을 별도로 보존합니다. 각 규칙의 경계 테스트와 실제 타워 훅의 GameTest를 함께 유지해야 분리 과정의 동작 변화를 잡을 수 있습니다.

## 직업 이벤트와 레인 생명주기

직업 클래스는 ID·이름·설명·타워 권한·경제 보정에 집중합니다. 이벤트는 `SemionJob`의 기존 8개 훅 → `JobBuilderLifecycle`의 ID별 등록 → `JobLifecycle` 구현으로 전달합니다. 계열 고유 처리는 `Job<Builder>Lifecycle`에 둡니다. 현재 기본 직업을 포함한 33개 등록 중 22개는 전용 구현, 11개는 `JobLifecycle.NONE`을 사용합니다. 동작이 없는 직업에 빈 전용 클래스를 만들 필요는 없습니다.

새 직업은 `JobRegistry`와 생명주기 등록을 함께 갱신합니다. production 직업 클래스에 이벤트 override를 다시 추가하지 않고, 필요한 `JobLifecycle` 메서드만 구현합니다. 등록된 생명주기 구현도 공유 객체이므로 플레이어별 가변 상태를 필드에 두지 않습니다. 실제 상태와 전투 규칙은 기존 계열 서비스·타워에 남기고, 생명주기 구현은 호출 순서를 연결합니다.

| 훅 | 이전 실행 위치 | 현재 실행 경로 | 보존할 고유 동작·조건 |
|---|---|---|---|
| `onSelected` | `SemionJob`의 빈 기본 훅 | 공통 dispatch → `JobLifecycle` 기본 동작 | 시작 자원 적용 후, `team.addPlayer` 이전. 현재 별도 선택 동작 없음 |
| `onMatchStarted` | 각 직업의 override | 공통 dispatch → 각 `Job<Builder>Lifecycle` | 초기화와 팀 등록, 설계도 설치, 해적 경기 상태 개설, 마왕 경기 성장 초기화 |
| `onRoundStarted` | 각 직업의 override | 동일한 공통 경로 | 번개 공유 추첨, 개발자 예산, 용사 퀘스트, 고대 도시 라운드 처리. 활성·비탈락 팀만 호출 |
| `onRoundEnded` | 각 직업의 override | 동일한 공통 경로 | 군인 전역·환급, 식물 수입, 마법사 라운드 종료, 해적 타워 처리, 퀘스트 완료·다음 선택 한도 |
| `onEliminated` | 각 직업의 override | 동일한 공통 경로 | 온라인 참가자의 선행 정리·관전 전환 후, 모든 팀원의 소유자 상태와 팀 효과 정리 |
| `onMatchClosed` | 일부 직업 override와 경기 종료 정리 | 전용 구현 후 경기 전체의 단계별 정리 | 반복 호출 가능. 우민 습격·번개 폭풍도 종료 시 해제 |
| `onSummonedMonster` | `SemionJob`의 빈 기본 훅 | 공통 dispatch → `JobLifecycle` 기본 동작 | 기존 소환 인자 전달, 현재 별도 동작 없음 |
| `onMonsterKilled` | 고대 도시·흑마법사·마왕 override | 각 전용 구현으로 dispatch | 영토 확장·각성 진척·전투 중 경험치 처리, 한 처치의 중복 반영 방지 |

선택 해제 훅은 현재 없습니다. 재접속도 선택·경기 시작 훅을 다시 실행하지 않습니다. 레인이 필요한 초기화는 실제 연결 이후 훅에서 처리합니다. 연결 종료 시에는 `JobBuilderLifecycle.onPlayerDisconnected`가 도박 표시·마왕·냉기 플레이어 정리의 기존 순서를 담당합니다.

팀 탈락 시 `JobBuilderLifecycle.onPlayerEliminated(ServerPlayer)`는 기존 온라인 마왕 정리 1회를 담당합니다. 관전 팀 배정·이동 전에 호출하며, 이후 모든 팀원의 직업 `onEliminated`를 호출하는 순서를 유지합니다. 새 탈락 동작이나 오프라인 플레이어 훅을 추가한 것은 아닙니다.

`JobLaneLifecycle`은 `PlayerLane` 내부의 경계별 작업을 묶습니다. 플레이어 라운드 훅과 타워 웨이브 훅을 하나로 합치지 않습니다.

| 공통 진입점 | 유지해야 할 경계 |
|---|---|
| `beforeRoundReset` | 임시 복제 제거 전 계열·증강 정리 |
| `afterTemporaryCopiesRemoved` | 임시 복제 제거 후 냉기 가동·마왕 전투·초월·몽환 상태 정리, 개별 타워 reset 전 |
| `prepareWave` | 증강·냉기 웨이브 준비와 초월 정리, 라운드 추적 정보 초기화 전 |
| `beforeTowerWaveStarted` | 타워별 `onWaveStarted` 호출 전 군단·주민 ADV·우민·냉기 처리 |
| `afterTowerWaveStarted` | 타워 훅과 라운드 특성 적용 후 공명·체력·대적자·미래 기관 스냅샷, 해적·예비군·마왕 전투 진입 |
| `beforeTowersCleared` | 타워 제거 콜백 전에 계열·증강 정리 |

마왕의 전투 진입은 실제 웨이브 시작 이후에 유지합니다. 준비 단계의 `onRoundStarted`로 옮기면 상점·핫바 동작이 달라집니다. 경기 전체의 주민 ADV 웨이브 시작·완료도 `JobBuilderLifecycle.onWaveStarted`·`onWaveCleared`를 통해 기존 위치에서 호출합니다.

경기 종료 순서는 참가자별 `onMatchClosed` → `JobBuilderLifecycle.closePlayerRuntime(game)`의 마왕 온라인/오프라인 정리 → `closeBeforeLanes` → 팀·레인·타워 종료 → `closeAfterLanes`입니다. 두 정리 단계는 `JobBuilderLifecycle`에 있으며 선택 직업과 무관한 모든 참가자 UUID를 대상으로 합니다. 전행 단계는 주민 ADV·고대 도시·기술자, 후행 단계는 모든 참가자의 아틀란티스 상태·압력을 먼저 정리한 뒤 대적자·마법사·미래 기관·여왕·용사·군인 상태를 정리합니다. 대적자 설치 점수는 타워 제거 도중 조정되므로 후행 정리를 앞당기지 않습니다.

`JobBuilderLifecycleTest`로 등록·공통 위임·소유자 범위·반복 종료를 검사합니다. `JobBuilderLifecycleRuntimeTest`는 고대 도시 처치 상한·라운드 초기화·종료 정리와 대적자 타워 제거 후 정리 순서를 실제 서버에서 검사하며, 계열 GameTest도 유지합니다. 우민·번개의 종료 정리 보완을 제외한 본문 이동은 게임 규칙 변경의 근거가 되지 않습니다.

## 등록과 업그레이드

현재 구현 예시는 [OceanTowerCatalogs](../src/main/java/kim/biryeong/semiontd/tower/ocean/OceanTowerCatalogs.java)입니다. 타입 정의, 런타임 클래스, 등록 클래스를 함께 읽습니다.

1. `JobRegistry.registerBuiltIns()`에 빌더를 연결합니다. 직업은 공유 객체이므로 플레이어별 가변 상태는 키가 있는 상태 서비스에 둡니다.
2. `TowerBalanceRuntime.resolve(type)`로 실제 설정이 적용된 타입을 등록합니다. 직접 설치할 1티어는 `registerStarter`, 상위 티어는 `register(type, factory, tier)`를 사용합니다.
3. 모든 끝점을 등록한 후 업그레이드를 연결합니다. 업그레이드 ID를 유지하고 비용은 `TowerBalanceRuntime.upgradeCost(from, upgradeId)`로 읽습니다.
4. 계열의 등록 함수를 `ProductionTowerCatalogs.reloadBuiltIns(...)`에 연결합니다. `canUseTower`로 실제 배치를 허용하고 `includesTowerInCatalog`로 공개 소유권을 정확히 하나 지정합니다.
5. 패키지 기본값, 부분 설정 병합, 설명과 테스트를 함께 작성합니다. 기존 운영 설정의 값을 덮어쓰거나 삭제된 운영 파일을 되살리는 절차는 아닙니다.

아래는 이미 정의된 `from`, `to`, `factory`를 사용하는 등록 구문입니다. 실제 계열에서는 모든 타입을 먼저 등록한 뒤 링크를 만듭니다.

```java
TowerType resolvedFrom = TowerBalanceRuntime.resolve(from);
TowerType resolvedTo = TowerBalanceRuntime.resolve(to);
ProductionTowerCatalog.registerStarter(resolvedFrom, factory);
ProductionTowerCatalog.register(resolvedTo, factory, 2);
ProductionTowerCatalog.linkUpgrade(
        resolvedFrom,
        to.id(),
        to.displayName(),
        resolvedTo,
        TowerBalanceRuntime.upgradeCost(from, to.id())
);
```

`TowerFactory`는 타입·소유자·팀·레인·원래 좌표·현재 좌표를 받아 `Tower`를 만듭니다. 두 좌표를 보존해야 이동과 최종 방어, 업그레이드가 같은 의미를 유지합니다. 기본 팩토리는 `ProductionTower::new`이며, 고유 동작이 있을 때만 계열 팩토리를 지정합니다.

배치와 업그레이드는 `ProductionTowerService`를 통과시킵니다. 업그레이드 시 `Tower.copyFrom(previous, upgradeCost)`가 투자금과 공통 상태를 전달합니다. `TowerDataKey`의 단순 값은 공통 복사를 사용하고, 별도 필드나 독립 복사가 필요한 가변 값만 `copyRuntimeStateFrom`에서 처리합니다. 목적지의 설치 가격은 업그레이드 가격이 아닙니다.

## 런타임과 공통 훅

일반 기본 공격 타워는 `ProductionTower`를 우선합니다. 엔티티 수명만 필요하고 기본 동작과 충돌할 때 `EntityBackedTower`, 주기적 지원 행동에는 `SupportTower.execute(PlayerLane)`를 검토합니다. 프로덕션 타워는 테스트용 `TestTower`를 상속하지 않습니다.

| 필요한 동작 | 사용할 훅 또는 API |
|---|---|
| 실제 피해·처치 결과에 따른 효과 | `onAttackResolved` |
| 귀속된 타워 피해 | `Tower.damageTargetResult(...)` |
| 광역 피해·지원 | `SemionTdApi.areaEffects()`, `TowerAreaDamage` |
| 웨이브 시작의 명단/연결 캡처 | `onWaveStarted` |
| 동적 설명 | `runtimeDetailLines()`와 공용 timed-effect 표시 |
| 변경된 엔티티 외형 동기화 | `visual()`과 `onStateChanged()` |
| 경기 종료·취소 시 상태 정리 | `JobLifecycle.onMatchClosed`와 `JobBuilderLifecycle`의 레인 제거 전후 정리 |

직접 엔티티 피해를 주면 출처·물리/마법 통계·처치 전달을 빠뜨릴 수 있으므로 공통 경로를 사용합니다. `aggroPriority`는 몬스터가 타워를 고르는 우선순위입니다. 타워의 적 선택 규칙은 `selectAttackTarget`과 공용 goal에서 다룹니다.

레인 변경은 공용 등록·제거·교체 경로로 수행합니다. 틱 순회는 순서가 있는 스냅샷과 현재 멤버십을 함께 검사하므로 콜백 중 제거와 다음 틱에 참여할 추가를 구분합니다. 공유 가능한 집계만 재사용하고, 순차 자원 배분·난수·동률 선택·반올림 순서는 보존합니다. 새 캐시를 두면 이동·업그레이드·사망·리로드·경기 종료 등 해당 값의 변경 지점을 모두 처리합니다.

## 설정·외형·표시

`TowerBalanceConfig.defaultConfig()`는 `BundledBalanceDefaults`를 통해 `src/main/resources/semiontd/balance-defaults/tower_balance.json`을 읽고 코드 기본값을 fallback으로 사용합니다. 타워 공통 스탯은 `towers`, 업그레이드 간선 가격은 `upgradeCosts`, 고유 동작의 값은 `abilities`에 둡니다. `withMissingDefaults`는 기존 값을 유지하면서 새 키를 채워야 합니다.

숫자 설명은 `TowerDescriptionRegistry.registerTemplate`에 등록하고 `TowerBalanceRuntime.resolve(...)`로 해석합니다. 동적인 스택·모드·자원은 `runtimeDetailLines()`로 보여 줍니다. 설명 문자열에 기본값을 다시 적어 런타임과 다른 규칙을 만들지 않습니다.

외형은 현재 [entity/visual](../src/main/java/kim/biryeong/semiontd/entity/visual)의 typed builder를 사용합니다. `VillagerVisual`, `WolfVisual`, `PandaVisual`, `BlockDisplayVisual` 등 가장 가까운 타입의 실제 26.3 import와 API를 확인합니다. 임의의 tracked-data 문자열이나 예전 Minecraft 패키지를 복사하지 않습니다. Blockbench 모델은 해당 모델 적용 스킬과 리소스팩 생성 경로를 따릅니다.

각 새 빌더는 전용 `BuilderPalette`를 갖고 `TowerVfxService.paletteFor(...)`에서 모든 타워가 그 팔레트로 연결되어야 합니다. 실제 공격·범위·특수 연출이 이 경로를 사용하는지도 검증합니다. enum 추가만으로 시각 구현이 완료되지는 않습니다.

## 구현과 함께 검증하기

JUnit은 `src/test/java`, Fabric GameTest는 `src/gametest/java`의 대응 책임 패키지에 둡니다. 새 테스트명은 `Test`로 끝내되 GameTest 어노테이션·호출 인터페이스·소스 세트는 유지합니다.

- `TowerBuilderCatalogContractTest`: 빌더별 카탈로그 생성·소유권·좌표·업그레이드·투자금 복사. 동적 설계도도 fixture로 생성합니다.
- `tower/<family>`의 JUnit: 고유 계산·상태·설정 병합·경계값. GameTest: 실제 배치·전투·업그레이드·라운드/경기 정리. 예시는 `OceanTowerRuntimeTest`, `PlantTowerIntegrationTest`입니다.
- `GameTestParticipantFixture`, `AugmentCombatFixture`, `AugmentControllerFixture`: 필요한 준비 책임을 공유합니다. 구체 테스트만 `src/gametest/resources/fabric.mod.json`에 등록하며 공통 빌더 간 계약은 공통 패키지에 남깁니다.
- `ConfigPublishedPatchContractTest`, `WebCatalogExporterTest`, `TowerVfxGameTest`: 패키지 기본값과 실제 로딩, 공개 소유권·설명, VFX 연결을 검증합니다. 기존 사용자 오버라이드가 유지되어야 합니다.

구현 변경의 최종 검증 명령은 다음과 같습니다.

```text
./gradlew test runGameTest remapJar --console=plain --no-daemon
git diff --check
```

Windows에서는 `.\gradlew.bat`를 사용합니다. 저장소의 `runGameTest`는 격리 디렉터리에 새 월드를 생성하며 필요한 패치 의존성을 갖춰야 합니다. `remapJar`는 현재 26.3 빌드에서 배포 산출물을 확인하는 호환 태스크이므로 예전 remapping 설정을 가져오지 않습니다.

리팩토링의 책임 분리와 알고리즘 개선은 별도로 검증합니다. 입력 규모·호출 빈도·색인 갱신·할당 비용을 포함한 동일 조건 측정 없이 성능 향상을 단정하지 않습니다. 서버 GameTest는 실제 클라이언트의 폰트·VFX·모델 렌더링이나 다중 접속 성능 검증을 대신하지 않습니다. 운영 시작·배포·푸시는 별도 승인 범위입니다.
