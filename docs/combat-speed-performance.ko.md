# 전투 속도 8배 실험 성능

## 관측 결과

이번 통제 실험에서는 시뮬레이션의 CPU·할당량 절감이 확인되지 않았다. Engineer 1개 레인은 두 방식 모두 약 160 logical TPS를 유지했지만, 시뮬레이션의 논리 틱당 프로세스 CPU는 34.9%, 측정한 스레드 할당량은 82.4% 증가했다. Engineer 22개 복제 레인에서는 native 160.90 TPS에 비해 시뮬레이션은 83.87 TPS에 그쳤고, 같은 완료 작업 기준 프로세스 CPU는 2.55배, 측정한 스레드 할당량은 1.95배였다.

**8배는 GameTest 전용 실험 드라이버이며 운영 기능이 아니다. 프로덕션 최대 배율 5는 변경하지 않았다.** 이 수치는 실제 경기 전체나 모든 빌더의 성능을 대표하지 않으며, 아래 고정된 초기 Engineer 전투의 관측 결과다.

| 측정 항목 | 1개 레인 native | 1개 레인 simulation | 22개 레인 native | 22개 레인 simulation |
| --- | ---: | ---: | ---: | ---: |
| 실제 logical TPS | 160.447 | 160.053 | 160.896 | 83.869 |
| 실제 physical FPS | 160.447 | 20.058 | 160.896 | 20.077 |
| 완료 logical ticks | 3,910 | 3,910 | 3,910 | 3,910 |
| 측정 wall time, s | 24.369 | 24.429 | 24.301 | 46.620 |
| 프로세스 CPU, CPU-s / wall-s | 0.189 | 0.255 | 0.680 | 0.904 |
| 프로세스 CPU, ms / logical tick | 1.179 | 1.590 | 4.224 | 10.782 |
| 전체 main CPU, ms / logical tick | 0.388 | 0.719 | 2.673 | 6.626 |
| simulation worker CPU, ms / logical tick | 0 | 0.132 | 0 | 0.979 |
| main 할당, decimal MB / logical tick | 0.121 | 0.205 | 1.380 | 2.484 |
| simulation worker 할당, decimal MB / logical tick | 0 | 0.017 | 0 | 0.214 |
| GC 횟수 / 시간, ms | 0 / 0 | 0 / 0 | 7 / 120 | 28 / 276 |
| outer tick body 평균 / p95, ms | 0.480 / 1.061 | 0.957 / 1.247 | 2.428 / 5.198 | 1.454 / 2.256 |
| 완료 그룹 중 여러 physical frame에 걸친 수 | 해당 없음 | 0 | 해당 없음 | 351 |
| 그룹 최대 physical frame 수 | 해당 없음 | 1 | 해당 없음 | 4 |

각 열은 독립 JVM 2개의 측정 전투 10회를 합산한 값이다. TPS와 CPU 비용은 각 전투의 비율을 평균하지 않고 완료 틱·wall time·CPU의 합으로 계산했다. 할당량은 main과 simulation worker의 누적 할당 바이트이며, 프로세스 전체 할당량·최대 힙·잔존 메모리와 다르다. Native에는 이 전용 worker가 없으므로 해당 CPU와 할당량은 실제 0이다. GC는 프로세스의 모든 GC collector 카운터를 사용한다.

22개 레인의 simulation 측정 전투별 TPS 범위는 74.59–88.36이었다. Native는 160.35–161.42였다. Simulation의 physical 20 FPS 유지와 configured logical 160 TPS만으로 8배 처리량 달성을 주장할 수 없다.

## 입력과 소스 고정

실제 경기 `893854454113494679`에서 추출한 [Engineer 시작 배치](../src/gametest/resources/replay/engineer-opening.json)를 출발점으로 삼았다. 한 레인에 나무 발판 2개, 골렘·문·디스펜서 각 1개를 실제 `ProductionTowerService`로 배치하고, round 1의 자연 생성 돼지 12마리를 실제 전투 경로로 처치했다. 레인당 배치 비용은 131 diamond, 처치 보상은 36 diamond다. 게임·월드 seed, 사전 삽입 엔티티 identity·회전·RNG, 배치 순서, 기본 설정과 맵을 고정했다. 운영 서버 설정을 읽거나 변경하지 않았으며, 이 실험은 번들 기본 설정을 사용한다.

1개 부하에는 Engineer 레인 1개와 제어용 BLUE 레인을 구성했다. 22개 부하는 같은 Engineer 구성을 6개 팀 월드에 복제하고 레인별 Z 간격을 8로 두었다. 총 110개 타워·264마리 몬스터이며 실제 경기의 22인 빌더 구성·후반 라운드·전체 건설 기록을 재현한 것이 아니다. 두 부하 모두 한 전투가 391 logical ticks에서 자연 종료했다.

| 소스 | 고정 revision |
| --- | --- |
| Native production / dependencies | `f2f3c2e53babce83ac07f3ea4be364d4ce349bae` |
| Simulation production / dependencies | `2e8897a70aad893cf91284a0c936a90af9c15d3e` |
| 실제 native 측정 HEAD, 테스트 추가 포함 | `347f2761634fae3b6e183f7ce6cabaa723b2948c` |
| 실제 simulation 측정 HEAD, 테스트 추가 포함 | `123ee15c9dd35dd2416afddd36bd28119351a072` |

실행 스크립트는 매 실행 전에 `src/main`, `compat`, `build.gradle`, `gradle.properties`, `settings.gradle`이 해당 production pin과 일치하고, production·dependency·benchmark instrumentation 변경이 커밋되어 있는지 확인했다. Native 참조에는 simulation production 코드를 병합하지 않았다. 공통 benchmark Java 파일 8개는 텍스트가 동일하며 체크아웃 줄바꿈 차이만 있다.

## 측정 절차와 범위

주 세션의 전체 release gate 종료와 명시적 호스트 대기 확인 후, 부하별로 native → simulation → simulation → native의 ABBA 순서로 독립 JVM 4개를 순차 실행했다. 두 부하를 합쳐 독립 JVM 8개, warmup 전투 24회, 측정 전투 40회다. 각 JVM은 warmup 3회 후 측정 5회를 실행했고, warmup 결과도 증거에 보존했다. 측정 중 다른 저장소 세션의 Java·Gradle·profiler 실행은 중지했으며, 운영 서버와 클라이언트는 시작하지 않았다. 일반 OS 작업까지 통제한 환경은 아니다.

환경은 Windows 11 `10.0` / `amd64`, Java `25.0.3`, OpenJDK 64-Bit Server VM, 논리 프로세서 12개, 실제 GameTest JVM maximum heap `8,346,664,960` bytes, G1이다. GameTest JVM의 별도 `-X` tuning은 없었다. Gradle daemon의 heap 설정을 측정 JVM heap으로 대체하지 않았다. 비교기는 JVM·heap·GC·tuning·시나리오·규칙·맵·seed가 일치해야 결과를 수용한다.

기본 GameTest 서버는 unpaced이므로 테스트 전용 mixin이 원래 `MinecraftServer.waitUntilNextTick` 구현을 호출했다. Native는 physical 160 TPS, simulation은 physical 20 TPS이며, 원래 deadline·`runAllTasks`·managed blocking과 대기 중 owner continuation을 유지했다. 별도 sleep·join·spin 드라이버를 만들지 않았다. Native의 Windows 타이머 병합과 catch-up burst를 포함한 모든 frame을 보존했고, 긴 frame·GC·작은 간격을 제거하거나 deadline을 매번 재설정하지 않았다.

Simulation 8배는 기존 session에 5 steps를 요청하고 다섯 번째 logical observer에서 3 steps를 추가하는 **테스트 전용** 방식이다. 이전 그룹 완료와 session idle을 확인한 후 다음 그룹을 요청한다. 부하별 측정 전투 10회에서 8-step 요청 490개, 완료된 8-step 그룹 480개, 자연 종료한 마지막 7-step 부분 그룹 10개를 실제로 기록했다. 22개 부하에서는 완료 그룹 351개가 여러 physical frame에 걸쳐 실행되어 총 936 frames가 필요했다. 이 실험은 production의 정상 배율 API가 8배를 지원한다는 증거가 아니다.

각 반복은 이전 Fantasy 월드를 닫고 unload를 요청한 뒤 1초 동안 비동기 settling을 거쳐 이전 월드가 사라졌는지 확인했다. 새 월드의 chunk가 준비되고 다시 1초 settling한 다음 actor를 생성하고 seed를 재설정했다. 월드 준비·배치·settling·warmup·최종 digest serialization은 측정 창에서 제외했으며 `System.gc()`를 호출하지 않았다.

전체 main CPU는 frame HEAD부터 다음 HEAD까지 측정해 native wait 중 실행되는 simulation owner continuation을 포함한다. 프로세스 CPU에는 GC·JIT·기타 JVM 작업이 포함된다. Simulation worker 카운터는 실제 thread ID를 사용하고 마지막 logical observer에서 자동 종료 직전 값을 보존한다. 사용할 수 없는 CPU·할당·GC 카운터는 `null`로 남기며 0으로 대체하지 않는다.

**Outer tick body MSPT만으로 비용 절감을 판정하면 안 된다.** 22개 부하의 body 평균은 simulation 1.454 ms로 native 2.428 ms보다 낮았지만, 전체 main CPU는 logical tick당 6.626 ms로 native 2.673 ms보다 높았다. 측정 창에서 main의 wait 내부 CPU도 simulation 총 25.563 s, native 1.984 s였다. body 밖의 대기 구간에서 owner 작업이 실행되므로 body 시간만 보고 계산량이 줄었다고 해석할 수 없다. 이 측정만으로 증가 원인을 특정 알고리즘으로 단정하지 않는다.

## 작업 동일성 및 증거

[증거 JSON](combat-speed-performance-evidence.json)은 실행별 source·production revision, 실제 환경, raw JSON/log의 SHA-256, warmup을 포함한 모든 전투의 TPS·CPU·할당·GC·그룹 카운터·작업 해시와 합산 결과를 보존한다. 같은 해시로 확인한 최종 타워·플레이어 상태는 부하별 1개를 저장해 중복을 줄였다. 전체 frame 기록은 커밋하지 않는 `build/replay-analysis/benchmark-measured-{1|22}-{A1|B1|B2|A2}.json`에 남겨 두었다. 원본 timing 자료가 없으면 해시는 그 자료의 동일성을 검증할 수 있을 뿐 모든 frame을 복원하지 못한다.

최종 상태와 실제 callback 작업량의 SHA-256은 각 부하의 모든 32개 전투에서 일치했다.

- 1개 레인: `e6b130e999dfb971a80591b259ce7b95aeb07c5f542951dccd0849a1e6f15a41`
- 22개 레인: `f405359852d212ad6316afb07b345bda7f95c80eccc8528e5413a2894779e722`

해시에는 logical tick 수, 자연 생성·공격·피해·처치·보상·디스펜서·발판 callback 카운터, 피해 합, 최종 타워·회로·경제 상태가 포함된다. 매 logical tick의 전체 상태 JSON을 직렬화하지 않아 측정 부담을 줄였다. 따라서 **이번 8배 실험의 해시 일치는 모든 틱의 궤적 동등성을 증명하지 않는다.** 별도 [replay 검증](replay-simulation-validation.ko.md)의 native 40 TPS 대 simulation physical 20 / logical 40 비교에서 확인한 392개 per-tick sample 동일성과 구분해야 한다.

측정용 GameTest 8회는 모두 성공했고, 비교기 2회는 ABBA·독립 JVM·환경·warmup·작업량 검사를 통과했다. 이전 default opt-out 전체 gate의 성공과 이번 opt-in 성능 측정은 서로 다른 검증이다. 실제 멀티플레이·네트워크·클라이언트 GPU 렌더링은 측정하지 않았다. 짧은 round 1과 제한된 JVM 수, Windows CPU 카운터·타이머 해상도, background JVM 작업을 고려하면 수치를 다른 부하의 고정 성능 비율로 일반화할 수 없다.

## 재실행

[run_benchmark.py](../scripts/replay/run_benchmark.py)는 격리된 native 참조와 simulation checkout을 사용하고, 측정 전에 다른 저장소 세션의 JVM 작업이 멈췄다는 확인을 요구한다. `--quiet-host-confirmed`는 실제 확인 후에만 전달한다. 각 실행에는 새 output 경로를 사용한다.

```powershell
python -B scripts/replay/run_benchmark.py native --root '<native-reference-root>' --lanes 1 --output 'build/replay-analysis/benchmark-measured-1-A1.json' --order A1 --quiet-host-confirmed
python -B scripts/replay/run_benchmark.py simulation --lanes 1 --output 'build/replay-analysis/benchmark-measured-1-B1.json' --order B1 --quiet-host-confirmed
python -B scripts/replay/run_benchmark.py simulation --lanes 1 --output 'build/replay-analysis/benchmark-measured-1-B2.json' --order B2 --quiet-host-confirmed
python -B scripts/replay/run_benchmark.py native --root '<native-reference-root>' --lanes 1 --output 'build/replay-analysis/benchmark-measured-1-A2.json' --order A2 --quiet-host-confirmed
python -B scripts/replay/summarize_benchmark.py build/replay-analysis/benchmark-measured-1-A1.json build/replay-analysis/benchmark-measured-1-B1.json build/replay-analysis/benchmark-measured-1-B2.json build/replay-analysis/benchmark-measured-1-A2.json --output build/replay-analysis/benchmark-summary-1.json
```

22개 부하는 위 순서의 `--lanes 1`을 `--lanes 22`로 바꾸고 모든 output 이름의 `-1-`과 summary의 `-1.json`을 `-22-`와 `-22.json`으로 바꾸어 별도로 실행한다. [summarize_benchmark.py](../scripts/replay/summarize_benchmark.py)는 dry run·불완전한 반복·환경 차이·최종 작업 해시 차이를 성능 비교로 수용하지 않는다.
