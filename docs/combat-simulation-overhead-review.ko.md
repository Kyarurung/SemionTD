# 전투 시뮬레이션 동기화 구현 점검

검토 기준은 production `2e8897a70aad893cf91284a0c936a90af9c15d3e`이며, 사용자 요청에 따라 중복 계산과 메인·워커 호출 비용을 조사했다. 수치의 원본과 반복 절차는 [8배속 성능 보고서](combat-speed-performance.ko.md)에 있다. 이 문서는 구현을 점검한 결과이며 성능 수정 완료 보고서가 아니다.

## 측정으로 확인한 문제

동일한 기술자 22레인 전투에서 원본은 160.90 논리 TPS, 시뮬레이션은 83.87 논리 TPS였다. 논리 틱당 프로세스 CPU는 4.22ms에서 10.78ms로 증가했다. 메인 CPU만 비교해도 2.67ms에서 6.63ms로 늘고 전용 워커가 추가로 0.98ms를 사용했다. 메인·워커의 관측 할당량은 약 1.95배였다.

현재 구현은 Minecraft AI·타워·피해 처리와 이동 후속 처리를 서버 스레드에서 실행하고, 복사한 이동·충돌 및 회로 계산을 워커에 보낸다. 기존 실행 비용의 상당 부분이 메인에 남아 있으며 복사·검증·전달 비용도 더해진다. 기존 서버보다 가벼운 독립 전투 모델을 워커에서 통째로 실행하는 구조는 아니다.

측정 결과는 이 구조의 총비용 증가를 입증한다. 아래 각 호출 경로가 전체 회귀의 몇 퍼센트인지는 CPU 샘플이나 경로별 계측으로 구분해야 한다. 할당 바이트가 많다는 사실만으로 모든 지연을 GC 탓으로 돌리지 않는다.

별도 [JFR 진단](combat-speed-diagnostics.ko.md)에서는 메인 execution sample 516개 중 426개가 워커 완료 전달 콜백 아래에서 관측됐다. 그 안에는 AI 준비·충돌 입력 수집·이동 적용·후속 처리가 포함된다. 이를 “CPU의 82.6%가 큐 오버헤드”라고 해석하면 안 된다. 워커 실행 샘플은 2개라 세부 비용 비율을 추정하기에 부족했다. 최초 계측 시도의 반복 작업 해시 불일치는 재시도에서 재현되지 않았지만 원인은 미확정이며, 조사 결과에 남겨 두었다.

## 1. 전투체마다 직렬로 왕복한다

`CombatSimulationSession.Coordinator.drive()`는 다음 순서로 실행한다.

```text
메인: 전투체 A의 기존 AI 실행 → 충돌 후보 수집·입력 복사
  → 워커: A의 순수 이동·충돌 계산
  → 메인: A의 이동 적용·후속 훅 완료
  → 메인: 전투체 B 준비 → 워커 → 메인 → …
```

[Session](../src/main/java/kim/biryeong/semiontd/game/simulation/CombatSimulationSession.java)의 `submit`은 입력 하나만 보낸 뒤 반환한다. `ready`가 완료를 적용하고 `drive`를 다시 호출해야 다음 전투체로 진행한다. [Executor](../src/main/java/kim/biryeong/semiontd/game/simulation/CombatSimulationExecutor.java)는 단일 워커와 단일 미완료 요청을 사용하며, 요청마다 `Semaphore.release/acquire`와 `server.execute` 완료 전달을 수행한다. 22레인 전체도 이 하나의 직렬 흐름을 공유한다.

논리 틱 t에서 실제 물리 입력을 생성한 전투체 수를 N(t), 회로 요청 수를 C(t)라 하면 요청 수는 N(t)+C(t)다. 각 요청에는 메인→워커 전달과 워커→메인 완료 전달이 있다. 이는 소스상 전달 수이며 실제 OS 문맥 전환 횟수와 같다고 간주하면 안 된다. 몬스터는 순차 생성·사망하므로 누적 생성 수 264를 모든 틱의 N(t)로 대입하지 않는다.

이 구조는 전투 순서를 보존하지만 레인 수가 늘어도 전투체 계산이 동시에 진행되지는 않는다. 이동 계산이 짧으면 큐·깨우기·검증·재진입 비용을 상쇄하기 어렵다. `Mode.NONE`인 입력도 현재 동일한 요청 경로를 지난다.

## 2. 이동 계산의 앞부분을 반복한다

[EntitySimulationBridge.snapshot](../src/main/java/kim/biryeong/semiontd/entity/simulation/EntitySimulationBridge.java)은 충돌 후보 검색 범위를 얻기 위해 `WorkerPhysics.travelMovement(travel)`을 메인에서 실행한다. 이후 [WorkerPhysics.advance](../src/main/java/kim/biryeong/semiontd/entity/simulation/WorkerPhysics.java)가 같은 이동 벡터를 다시 계산한다.

적용 시에는 `EntitySimulationBridge.apply`가 기존 `actor.travel(...)`을 실행한다. 이 경로는 원래 이동 처리의 앞뒤 동작을 유지하고, [EntitySimulationCollisionMixin](../src/main/java/kim/biryeong/semiontd/mixin/EntitySimulationCollisionMixin.java)이 `collide`에서 워커의 충돌 결과를 반환한다. 따라서 이동 벡터 산출과 이동 처리의 일부는 반복되지만, 네이티브 충돌 계산 전체를 한 번 더 수행한다고 표현하면 부정확하다.

워커로 보낼 입력을 준비하면서 이미 계산한 이동 벡터를 다시 계산하는 부분은 공유 가능한 후보다. 다만 `travel`의 중력·마찰·유체·밀림·낙하 처리까지 단순히 제거하면 정합성이 깨지므로 별도 검증이 필요하다.

## 3. 충돌 자료 수집과 변환은 메인에 남아 있다

`EntitySimulationBridge.snapshot`은 매 전투체에서 `getEntityCollisions`와 `getBlockCollisions`를 호출한다. 얻은 `VoxelShape`를 `toAabbs`로 풀고 별도 `WorkerPhysics.Box` 목록, Y 좌표 목록, `Collider`와 입력 객체로 변환한다. 워커의 충돌 판정 전에 후보 탐색과 값 변환 비용을 메인에서 지불한다.

바닐라 충돌 후보 자료를 워커에서 직접 읽는 것은 스레드 안전성을 보장하지 않는다. 개선하려면 정적인 블록 형상과 변하는 엔티티 형상을 분리하고, 수정 버전과 수명에 맞춰 값 데이터를 재사용해야 한다. 움직이거나 생성·제거된 엔티티의 충돌 상자를 오래된 캐시로 대체해서는 안 된다.

## 4. 기술자 회로를 매 틱 다시 수집·복원한다

[EngineerCircuitWorld](../src/main/java/kim/biryeong/semiontd/tower/engineer/EngineerCircuitWorld.java)에는 다음 반복 경로가 있다.

1. `request` → `refreshTopology`: [EngineerCircuitTopology.capture](../src/main/java/kim/biryeong/semiontd/tower/engineer/EngineerCircuitTopology.java)로 노드·간선·연결 정보를 다시 만든 뒤 이전 그래프와 비교한다. 같은 그래프여도 수집·객체 생성은 이미 수행했다.
2. 워커의 `calculate`: `EngineerCircuitSimulation.restore`로 모델과 조회 맵을 새로 만든 뒤 논리 틱을 진행한다.
3. 메인의 `accept`: 결과를 설치하기 전 `restore`를 다시 호출해 검증하고 생성된 모델은 사용하지 않는다.
4. `pressPlate`: 토폴로지를 다시 수집하고, 즉시 회로 결과가 필요한 경로에서 메인에서도 `accept(calculate(request))`를 실행한다.

[EngineerCircuitSimulation.restore](../src/main/java/kim/biryeong/semiontd/tower/engineer/EngineerCircuitSimulation.java)는 단순 포인터 연결이 아니다. 생성자에서 노드·간선과 조회 구조를 만들고 스냅샷 상태를 검증·복원한다. 기술자 22레인 부하에서 우선 조사해야 할 실제 반복 작업이다. 기술자 전용 경로이므로 이 부하에서의 비중을 모든 빌더로 일반화하지 않는다.

그래프 변경 시 재구성하고 매 틱에는 변한 입력·시간·상태만 진행하는 구조가 후보다. 외부 신호 변화, 블록 변경, 회전·업그레이드·판매·임시 복제·경기 종료를 놓치지 않는 무효화가 선행되어야 한다. 발판 즉시 반응을 다음 틱으로 미루는 식으로 비용을 줄여서는 안 된다.

## 5. 완료 상태를 매 논리 틱 전체 복사한다

`CombatSimulationSession.completeStep`은 매 논리 틱 `captureCompletedState`를 호출한다. 이 함수는 월드별 전체 엔티티 목록을 만들고, 관리 대상의 `EntityView`와 표시 콜백을 다시 구성한다. 기본 프레임에 여러 논리 틱이 들어가면 이 완료 스냅샷 생성도 반복된다. 물리 프레임의 `publishCompleted`는 마지막 완료 상태를 게시한다.

실패·일시정지·진행 중 상태와 마지막 완료 상태를 분리해야 하므로 스냅샷을 전부 삭제할 수는 없다. 변경된 값만 복사하거나 완료 버퍼를 재사용하는 방식이 후보다. 새 스냅샷이 아직 진행 중인 상태를 참조하게 만들거나, 표시 주기만 줄이면서 전투 시계를 건너뛰어서는 안 된다.

가상 좌표 조회에는 `ThreadLocal`, 소유 월드 판정, 엔티티별 view 조회가 반복된다. 공간 인덱스는 초기화 후 증분 이동하므로 매 조회마다 전체 월드를 재구축하는 구현은 아니다. 이 비용과 위의 완료 스냅샷 전체 순회를 구분한다.

## AI·전투 전체를 워커로 이동할 수 있는가

AI·목표 선택·쿨타임·피해 계산이 본질적으로 메인 스레드에서만 가능한 것은 아니다. 현재 코드의 결합 때문에 메인에서 실행하고 있다. `MonsterAttackTargetGoal`은 살아 있는 엔티티·Navigation·MoveControl·LookControl을 사용한다. `TowerAttackMonsterGoal.attack`은 피해 계산, 증강 콜백, 실제 적용, 이름표·VFX·사운드를 한 실행 경로로 호출한다. `SemionMonsterEntity.applySemionDamageResult`도 런타임 피해 계산과 엔티티 체력·피격·제거를 함께 수행한다.

사용자가 제시한 서버 구성에서 확인한 경로 모드는 Pathetic Mobs다. 확인한 서버 백업 revision `83f270bbd82a5c76ae2773531498c6bc5ce16d4a`의 manifest에는 `pathetic-mobs-1.0.0+1.21.8.jar`가 비활성 참고 기록으로 분류되며, 26.3 활성 모드와 이번 GameTest 의존성에는 포함되지 않는다. 이는 해당 백업의 상태이며 모든 운영 인스턴스의 설치 상태를 뜻하지 않는다.

공개 [1.21.8 경로 구현](https://github.com/biryeongtrain/pathetic-mobs/blob/220a91dcd8e263564df93eb209b139b7af55994a/src/main/java/kim/biryeong/pathetic/pathfinding/PatheticPathfinding.java)은 수평 네 방향 탐색, 직선·우회 빠른 경로를 사용하지만 `async(false)`와 `resultBlocking()`으로 호출한다. 공개 26.1.2 소스도 같은 실행 정책이며 [환경 제공자](https://github.com/biryeongtrain/pathetic-mobs/blob/837bb6b3cd2da9ef786d9c1baa01d5fe469ca7c9/src/main/java/kim/biryeong/pathetic/pathfinding/FabricNavigationPointProvider.java)는 월드 블록·충돌 형상과 몹의 경로 비용을 직접 읽는다. 경로 알고리즘이 2D라는 사실만으로 현재 월드 접근까지 워커 안전해지지는 않는다. 확인한 branch HEAD가 백업 JAR의 정확한 빌드 revision임을 검증한 것은 아니다.

근본적인 개선 후보는 전투 상태를 워커가 소유하고 전투체 순회를 워커 안에서 끝내는 구조다.

| 워커가 소유·계산할 것 | 메인과 연결할 것 |
|---|---|
| 안정적인 ID, 위치, 체력·보호막, 효과·쿨타임·RNG, 목표·경로 | 승인된 입력과 변경된 맵·점유 데이터 전달 |
| 목표 선택 → 이동 → 공격 → 피해 → 처치·스폰의 정해진 순서 | 순서가 있는 결과와 Minecraft 엔티티 생성·제거 반영 |
| 값 기반 2D 이동 격자와 충돌 모델 | 마지막 완료 상태의 모델·이름표·패킷·VFX 동기화 |

이때 전투체마다 메인으로 돌아오는 대신 틱 또는 여러 틱을 한 계산 단위로 진행할 수 있다. 같은 틱 내부에서도 앞선 공격의 사망·스폰·효과를 다음 전투체가 보는 순서를 유지한다. 빌더별 피해 API를 두 벌 만들지 않고 기존 공용 API의 계산·상태 변경 부분을 전투 모델에 연결하고 Minecraft 부수 효과는 어댑터로 분리해야 한다.

이 설계는 현재 부분 계산 분리의 작은 수정이 아니라 기존 빌더 훅·월드 조회·피해 적용의 책임 변경이다. 단순히 기존 Goal을 다른 스레드에서 호출하거나 2D 경로 탐색으로 교체하는 것만으로 완료되지 않는다. 경로·충돌 규칙을 변경하면 원본 40 TPS 정합성의 기준에도 영향을 주므로 실제 모드 버전과 규칙을 고정해 검증해야 한다.

## 수정 우선순위와 검증 조건

우선 경로별 호출 수·CPU 샘플·대기 시간을 측정하고 다음 변경을 각각 비교한다. 현재 문서에서는 이 변경을 적용했다고 주장하지 않는다.

1. 계산할 이동이 없는 입력의 워커 왕복 제거. 기존 AI·효과·처치·후속 단계는 같은 순서로 실행한다.
2. 기술자 회로의 변경 없는 토폴로지 재수집과 모델 재생성 축소. 입력 버전·소유권·오래된 결과 거부는 유지한다.
3. 이동 벡터 중복 계산과 충돌 형상 변환 재사용. 유체·계단·마찰·밀림의 동일성을 검증한다.
4. 완료 상태·표시 캡처의 증분 갱신과 버퍼 재사용.
5. 실제 이득이 확인된 경우 더 큰 계산 단위로 워커 작업 구성.

모든 전투체를 먼저 준비하고 나중에 한꺼번에 적용하면 앞선 전투체의 이동·사망·소환·피해가 다음 전투체에 반영되는 기존 순서를 바꿀 수 있다. 레인별 병렬화 역시 공유 자원·지원 효과·경기 종료 조건과 교차 레인 접근을 확인한 후 설계해야 한다.

검증은 원본 40 TPS와의 틱별 상태·이벤트 비교, 단일/22레인 8배 실험의 동일 작업량, 전체 메인·프로세스 CPU와 실제 논리 TPS를 함께 사용한다. 물리 20 TPS 유지나 낮은 틱 본문 MSPT만으로 최적화 성공을 보고하지 않는다. 기존 `Tower` 피해·기본 공격·광역 피해 API는 재사용하고, 성능 수정 때문에 빌더별 새 피해 API를 도입하지 않는다.
