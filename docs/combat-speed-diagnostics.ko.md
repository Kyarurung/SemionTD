# 전투 시뮬레이션 JFR 진단

22개 Engineer 복제 레인의 별도 JFR 기록에서는 main의 simulation 준비·적용 작업이 native wait 중 owner continuation에서 실행되는 모습이 확인됐다. 성공한 재시도의 measured battle 5개 창에서 main execution sample 516개 중 native wait 경로는 449개였다. 이 관측은 [성능 기준선](combat-speed-performance.ko.md)에서 body MSPT가 낮아도 전체 main CPU가 증가한 결과와 일관된다. **이 기록은 계측 부하가 있는 진단이며 ABBA 성능 비교에 합칠 수 없다.** 프로덕션 소스·dependency pin `2e8897a70aad893cf91284a0c936a90af9c15d3e`와 최대 배율 5는 변경하지 않았다.

## 실행 범위

테스트 전용 `jdk.jfr.Recording`을 battle마다 `BenchmarkRecorder.begin()` 직전에 시작하고, `end()`의 마지막 snapshot 뒤에 종료·dump했다. `semiontd.BenchmarkWindow` event로 창을 표시해 준비·배치·settling·dump 구간을 분석에서 제외했다. Park event가 창 경계를 넘으면 겹치는 duration만 계산했다. Marker는 metric snapshot을 앞뒤로 감싸므로 두 시간 창에는 작은 계측 경계 차이가 있다.

JFR `profile` 설정에 execution sampling 10 ms, native method sampling 20 ms, allocation sample stack, threshold 0의 thread park stack을 활성화했다. 성공한 실행은 warmup 3회 후 measured 5회이며, 이 5개 marker 창의 합은 26.434 s다. 전체 8개 전투는 391 logical ticks와 기준선의 최종 작업 SHA-256 `f405359852d212ad6316afb07b345bda7f95c80eccc8528e5413a2894779e722`를 유지했다. 이 해시는 최종 상태·작업 카운터의 동일성이고 모든 틱의 궤적 동등성을 뜻하지 않는다.

성공한 측정 HEAD는 `d71732c34ea7c1378e33d5084e0156dccfce4ec1`이다. `diagnostic_profiled=true`를 run과 trial에 기록하며, 비교기는 해당 기록을 거부한다. 관련 회귀 검사를 포함한 Python 21개 검사가 통과했고, GameTest JVM 1개에서 opt-in test가 성공했다. 별도 reader를 순차 실행해 기록 10개를 읽었으며, 운영 서버·클라이언트를 시작하지 않았다.

## Execution sample

다음 비율의 분모는 main의 execution sample 516개다. Inclusive는 해당 메서드가 stack에 포함된 sample 수이므로 항목이 겹치며 합을 CPU 시간으로 환산하거나 100%로 더할 수 없다.

| Main stack 경로 | Inclusive samples | Main sample 중 비율 |
| --- | ---: | ---: |
| Native `waitUntilNextTick` | 449 | 87.0% |
| `CombatSimulationExecutor.lambda$notifyOwner$0` | 426 | 82.6% |
| `Coordinator.ready` | 424 | 82.2% |
| `NativeBridge.prepare` | 228 | 44.2% |
| `NativeBridge.apply` | 115 | 22.3% |
| `EntitySimulationBridge.snapshot` | 88 | 17.1% |
| `EntitySimulationBridge.finish` | 70 | 13.6% |

Leaf sample에서는 `IdentityHashMap.get` 60개, `ThreadLocalMap.getEntry` 32개, `getEntryAfterMiss` 23개, block `getCollisionShape` 21개 등이 관측됐다. 이것은 main에 snapshot·검증·엔티티 준비·적용과 관련된 작업이 남는다는 관측이며, 정확한 호출 횟수나 독립적인 비용 분해가 아니다.

Worker execution sample은 **2개뿐**이다. 짧은 작업과 대기 패턴을 이 주기의 sampler가 충분히 포착하지 못했으므로 worker hotspot 순위나 main 대비 CPU 비중을 추론할 수 없다. 기준선의 실제 thread CPU 카운터를 이 sample 비율로 대체하지 않는다.

## 할당 stack과 대기

Main allocation sample 2,460개에서 첫 번째 프로젝트 frame으로 분류한 결과다. 이 비율은 기록된 allocation sample 개수의 비율이며 실제 할당 바이트 비율이 아니다.

| 첫 프로젝트 frame | Samples | Main allocation sample 중 비율 |
| --- | ---: | ---: |
| `EntitySimulationBridge.finish` | 327 | 13.3% |
| `EntitySimulationBridge.snapshot` | 245 | 10.0% |
| `EntitySimulationBridge.collider` | 243 | 9.9% |
| `SemionMonsterEntity.travel` | 209 | 8.5% |
| `NativeBridge.valid` | 117 | 4.8% |

Worker allocation sample은 672개였다. `WorkerPhysics.Collider.intersects` 127개, `EngineerCircuitSimulation` 생성자 108개, `WorkerPhysics.Box.move` 91개, `collidersFor` 88개, 회로 `restore` 24개 등이 관측됐다. 회로 snapshot·복원 및 물리 value object 생성 경로는 소스 검토의 후보지만, 이 sample만으로 해당 경로를 삭제해도 되는지나 절감량을 판단할 수 없다.

Main JFR allocation weight 합은 96,786,318,872 bytes였지만 같은 진단 창의 ThreadMXBean main allocated bytes 합은 4,908,911,088 bytes였다. 이 차이의 원인을 이번 분석에서 확정하지 않았다. **JFR weight를 창별 정확한 바이트·원인별 비용 비율로 사용하지 않는다.** 원래 weight는 증거에 남기고, 위 표는 unweighted sample 개수만 사용한다. 실제 할당량 비교는 계측 없는 기준선의 thread 카운터를 따른다.

Main thread park는 482,406 events / 12.363 s, worker는 482,071 events / 24.087 s가 관측됐다. Main park stack은 native wait 경로, worker는 `CombatSimulationExecutor.runWorker` 경로에 속했다. 이 시간은 blocked time이고 CPU가 아니며, 스레드 간 기간이 겹치므로 더해서 wall time으로 해석할 수 없다. Park event 수를 lease·handoff 수로 대체하지 않는다. Threshold 0의 event 기록 자체도 실행에 부담을 준다.

중복 호출의 존재·정확한 횟수·필요성은 실제 소스와 호출 순서로 확인해야 한다. Inclusive sampling이나 snapshot·restore allocation sample만으로 AI·공격 callback이 중복 실행됐다고 주장하지 않는다. 이번 관측은 main continuation·엔티티 준비와 snapshot·적용·회로 및 물리 객체 생성 경로를 우선 검토할 근거다. 프로덕션 최적화나 gameplay 변경은 이 진단에 포함하지 않았다.

## 최초 실패와 증거 한계

최초 진단 HEAD `a65505cb6e4be5fbfa41b1fc2b7bc977bec24670`는 warmup 0에서 기준선 해시를 유지했으나 warmup 1 / logical tick 391의 반복 작업 해시 검사에서 실패했다. 최초 helper는 검사가 실패하기 전에 최종 상태를 저장하지 않았으므로 차이의 구체적인 필드와 원인을 확인하지 못했다. 두 JFR과 실패 log는 보존했다.

실제 실패 후 테스트 helper에 검사 직전 진단 상태 저장만 추가하고 같은 JFR 설정으로 한 번 재시도했다. 반복 동일성 검사를 제거하거나 완화하지 않았으며, 재시도에서는 8개 전투가 일치했다. 최초 실패는 해결됐다고 선언하지 않는다. 계측이 잠재적인 timing 의존성을 드러냈을 가능성을 배제할 수 없는 반복 안정성 위험으로 남기며, 계측 없는 8개 기준선 JVM의 성공 결과와 구분한다.

[진단 증거 JSON](combat-speed-diagnostics-evidence.json)은 source·environment·event 설정, warmup을 포함한 metric과 event count, marker 시간, 합산 stack 자료, raw JSON·log·JFR SHA-256을 보존한다. 모든 frame·JFR·상세 reader 출력은 `build/replay-analysis/`에 남기고 커밋하지 않는다. 원래 실패와 성공한 재시도를 모두 기록했으며, profiled TPS를 기준선 수치와 비교한 속도 결론을 내리지 않는다.

## 재실행

다른 저장소 세션의 JVM 작업을 중지하고 격리된 candidate checkout에서 새 output 경로로 실행한다. 별도 Gradle JVM과 GameTest JVM이 같은 JFR 파일에 기록하도록 `JAVA_TOOL_OPTIONS`에 recording 옵션을 넣지 않는다.

```powershell
python -B scripts/replay/run_benchmark.py simulation --lanes 22 --output build/replay-analysis/diagnostic-jfr-retry-22.json --order B1 --quiet-host-confirmed --diagnostic-jfr-dir build/replay-analysis/diagnostic-jfr-retry-22
java scripts/replay/ProfileDiagnostics.java build/replay-analysis/diagnostic-jfr-retry-22/battle-3.jfr build/replay-analysis/diagnostic-jfr-retry-22/battle-4.jfr build/replay-analysis/diagnostic-jfr-retry-22/battle-5.jfr build/replay-analysis/diagnostic-jfr-retry-22/battle-6.jfr build/replay-analysis/diagnostic-jfr-retry-22/battle-7.jfr
```

[ProfileDiagnostics.java](../scripts/replay/ProfileDiagnostics.java)는 JDK RecordingFile API로 marker를 먼저 읽고 event를 다시 순회한다. Execution은 stack별 중복 frame을 제거한 inclusive count, allocation은 첫 프로젝트 frame별 count·원래 weight, park는 창 경계로 자른 duration을 출력한다. 각 기록의 map은 상위 100개 항목까지 보존한다.
