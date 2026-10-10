# Performance optimization analysis and verification

## Current all-builder optimization and controlled measurements

This section is the authoritative description of the current implementation and measurements. The earlier records below remain historical evidence; their completion statements, candidate versions and failed combat comparisons do not describe this experiment. `reference_order.md` was not available; the existing performance and builder maintenance documents supplied the review criteria. New material is in English and the historical record is retained unchanged.

All 33 production builders use the optimized private lane membership array. Family-specific changes cover 14 builder families, including the 11 families already changed in the incoming workspace. In the final controlled experiment, all **153/153** cells had identical input and observed-result hashes across three stages and three fresh JVM forks per stage. An equal-weight sum of the 33 **N=32 factory-board median costs** changed from **4.9976 ms** to **3.8731 ms**, a **22.50% reduction**. This is a synthetic mixture of lane-tick costs, not the MSPT or TPS of a complete match.

This is a verified optimization candidate with selective benefits, **not a uniform performance win**: at N=32, **15/33 builders became faster and 18/33 became slower**, with a median builder reduction of **-4.02%**. Animal accounts for **83.33%** of the original aggregate cost, so its **+23.92%** reduction dominates that aggregate. Engineer's measured reduction is **-61.76%**, meaning a latency regression. All individual regressions are retained below. The goal of improving every builder's latency and proving TPS preservation/MSPT reduction has not been established by this workload.

The Engineer/effect optimizations in baseline `2b431d1c6c85fca14c045a3c6bce492b1940b665` were already present in every stage. Their historical gains are not added to this percentage. The operating server was not started, restarted, modified or used as an input world. Deployment and push are outside this validation.

### Baseline, responsibility separation and optimized stages

| Stage | Production implementation | Purpose |
|---|---|---|
| Original (B) | Pinned commit `2b431d1c`; restore the 17 modified baseline production files; add only the full-sort reference helper used by the synthetic kernel | Reference behavior before the incoming 11-family changes and this follow-up |
| Separation (S) | B plus three extractions: common area selection, Blueprint selection and lane tick snapshot access; retain full sorting and a fresh list copy | Measure those responsibility extractions without their algorithm/cache changes |
| Optimized (C) | Current production files, including the incoming changes and the new membership cache, stable bounded selection, Animal guard and Frost counters | Measure the complete current production combination |

S isolates the three stated extractions; it is not an independently separated version of every historical family optimization. All stages use the same performance fixture and resources. Four incoming GameTest files that call newly added package helpers are restored to HEAD in B/S so those stages compile; the workspace release gate retains their new checks. The new regression suite is not claimed to have run against incompatible baseline APIs.

### Responsibilities, algorithms and total costs

Definitions: N is towers in one lane, A is Animal towers, M is candidate monsters, K is a target cap, G is board columns, and E is an enum's fixed entry count. Auxiliary space excludes the input and the caller's already-required entity snapshot.

| Path / family | Change | Before -> after time | Memory and update cost |
|---|---|---|---|
| All 33 / `PlayerLane.tickTowers` | Cache a privately owned Tower[] membership snapshot; retain the identity-membership check and every tower/flush call | Snapshot creation O(N) each call -> O(1) unchanged lookup, O(N) rebuild; the complete tick remains O(N) plus tower work | Retain O(N) references per lane in one array, without a list wrapper or iterator. Never expose or mutate the array. Add/replace/successful removal/clear invalidate before subsequent callbacks. First use and a mutation burst still pay O(N); no asynchronous work |
| Common capped area / Blueprint multishot | Pure `AreaTargetSelection`; complete selection before callbacks; stable encounter ties and duplicate slots | Full O(M log M) sorting -> O(M) for K=1; O(M min(K,M)) insertion for M<=16; O(M log K + K log K) for M>16 and 1<K<M | O(min(K,M)) bounded storage; reusable heap entries avoid per-replacement entry allocation. Caller entity capture/filter remains O(M) time/space. Full sorting remains for M>16 and K>=M. The small-input quadratic branch has a fixed M<=16 bound |
| Animal | Resolve UNION presence once; skip the leader-kind distinct/sort list only when UNION is absent | Remove unused leader-list work; whole repeated refresh remains O(A^2 N), up to O(N^3) | No game-time cache: movement, leader death/revival, healing and augment changes can affect later calls in the same tick |
| Frost | One snapshot scan with four independent family counters and original thresholds | O(N) repeated filtering/four scans -> O(N) one scan | Retain the snapshot; remove owner-filtered list and family stream pipelines. Preserve dead/temporary counting, owner/team rules, family thresholds and final clamp |
| Demon Lord | Build an occupied X/Z-column set once for free-position search | O(GN) membership search -> expected O(G+N); hash worst-case remains a qualification | O(N) local keys plus the existing result; rebuilt per invocation, original X/Z order and floor lookup preserved |
| Future Agency | Separate unsorted nearby enumeration/counting from ordered target enumeration | Count path O(M log M) -> O(M); ordered path unchanged | Remove count-only sorted result. Preserve range/entity filters and the progress/UUID-string order for consumers requiring it |
| Mage | Share the priority comparator and select only the in-range primary where a list is unnecessary | O(M log M) -> O(M) for that primary selection | Existing live-monster capture remains. World-cast ordered list and subsequent cast sequencing retained |
| Thunder | Ask the area API for candidate count instead of building/sorting a second list for emptiness | Remove O(M log M) redundant sort; area query remains O(M) | Area snapshot/callback traversal remains; no early exit that would change area API observation |
| Hero | Stable one-pass selection of the two lowest health ratios | O(N log N) -> O(N) | O(1) selection state; Double.compare, encounter ties, owner/type/liveness and complete pre-heal selection preserved |
| Legion / Pirate | Count the providers preceding the current provider in the original stable order | O(N log N) -> O(N) per selection | O(1) local state; no cross-call rank cache. Whole all-provider/all-recipient work can remain quadratic |
| Gamble | Stable minimum under the existing reversed-score/distance/position comparator | O(N log N) sorting -> O(N) selection | Nested link-count/filter costs remain; no claim of a lower total complexity for the whole spectator operation |
| Ocean | Resolve the recycled recipient once inside one allocation calculation | O(N^2) repeated minimum -> O(N) average map/scan cost | O(N) allocation map remains. Reuse is restricted to unchanged water inputs before delivery; merge order and lazy logical-ID behavior preserved |
| Developer | Short-circuit bug membership without building the complete bug set | O(token count) -> O(token count), fewer intermediates | String splitting still allocates; no persistent parsed cache or changed data format |
| Magic School | Static immutable ID lookup | O(E) scan/enum-array copy -> average O(1) lookup | O(E) once at class initialization; null/unknown IDs retain empty results |

The other builder families benefit from the common lane path and were included in the source audit and measurement matrix. Their independent combat/round algorithms were not rewritten without an equivalent, useful optimization. No balance, persistent IDs, saves, permissions, random rules, resource allocation order or floating-point aggregation order was intentionally changed. The previously identified Army initial-command subtraction overflow and Succubus cooldown-key lifetime are separate behavioral defects, not silently fixed as performance work.

For C lane calls and P snapshot rebuilds at a fixed N, membership-snapshot overhead is O(C + PN), with O(N) retained space. Multiple mutations before the next call coalesce into one rebuild; mutation before every call retains the O(CN) worst case. The tower loop itself still performs C*N visits, plus callbacks. Hash-table bounds above are expected/average bounds, not collision guarantees: a conservative linear-per-lookup worst-case bound is O(N^2 + GN) for constructing/querying Demon Lord's local set, O(N^2) for Ocean's allocation-map operations, and O(E) per Magic School lookup. Magic School pays O(E) expected initialization once, with a conservative O(E^2) collision bound and O(E) retained storage; E is fixed by the enum. Key hashing/comparison costs are additional. No cache assumes a periodic full invalidation is free, and no claim removes the required sorting/iteration or entity capture from the complete operation.

Function inlining is evaluated through actual Java 25 JFR compiler decisions, not presumed from shorter code. The dispatch, one-target scan, small-input insertion and bounded heap are separate methods; a direct singleton path bypasses selection. RandomAccess lists use indexed scanning for K=1; sequential lists use an iterator so that branch does not become quadratic. No parallelism was added, so snapshot transfer, synchronization and nondeterministic result application costs were not introduced.

### Reproducible comparison servers and workload

`tools/builder-performance/run.py` creates three separate temporary source/runtime trees. Each `runGameTest` creates a fresh isolated world under its checkout's build directory. Private licensed assets are excluded from copied sources; Spark is added only to diagnostic test runtime, not production dependencies. Baselines, fixture SHA-256, source manifests, commands, logs, output hashes and host-load samples are retained under ignored `build/builder-performance/`. In this session, `C:\SemionTD` contained the actual Git/Gradle source checkout; the legacy `C:\steve-td` path was absent. Operational installation paths must be verified separately.

| Setting | Value |
|---|---|
| Target/runtime | Minecraft 26.3; Java 25.0.4.1; Fabric Loader 0.19.5; API 0.161.0+26.3 |
| Server JVM | -XX:+UseG1GC, -XX:ActiveProcessorCount=4, -Xms2g, -Xmx2g; heap 2147483648 bytes |
| Host | Ryzen 7 7800X3D, 8 cores / 16 logical processors; about 64 GiB RAM; Windows |
| World/input seed | World 0; input/level RNG seed 26031010; origin (0,64,0); initial game time 10000 |
| Builder matrix | Every registered production builder excluding the default/test jobs; N=0,1,8,32; catalogue factories cycled in stable type-ID order |
| Preconditioning | One full board matrix and one full selection matrix; cleanup/reset between boards; discarded timing cells |
| Timing | 128 warmup batches + 32 sampled batches, 16 lane calls per batch; first lane call recorded separately |
| Selection matrix | M=0,1,8,16,32,128,512; K=1,3,8; fixed tied integer inputs; 1024 calls per batch; same warmup/sample counts |
| JVM order | B1,S1,C1,C2,S2,B2,B3,S3,C3; sequential, three fresh JVM forks per stage |
| Formal instrumentation | Direct nanoTime / ThreadMXBean counters; Spark and JFR off |
| Diagnostics | Separate B/C filtered server runs; Spark Java sampler at 1 ms + JFR profile/CompilerInlining; not used for percentage tables |

The production call path is `Events.END_SERVER_TICK -> SemionGameManager.tick -> SemionGame.tick -> PlayerLane.tick/tickTowers`. Active prepare/combat paths normally invoke lane work once per server tick (20 calls per real second at 20 TPS); game-time scaling and paused phases can change actual invocation frequency. The fixture calls the real lane method directly. It advances game time once per batch, so 16 calls share that time, and uses factory boards with no enemies, no entity AI ticks, no job selection, no clients and bundled default configuration. Some catalogue mixtures are not economically legal matches. Optional augments and every tower variant are not exhaustively represented. This measures those concrete boards and the selected pure kernel, not an entire server tick, wave, legal build plan or production traffic distribution. No active `config/semion-td/` directory was available in this source workspace.

Input hashes include tower order/types/positions and fixed Queen cards. Observed hashes include each sampled board's health/max-health raw double bits, position and entire runtime-detail lines; selection kernels compare the full stable-sort result. Nine matching hashes demonstrate equality of these observations, not all hidden state, RNG state, persistence or arbitrary combat traces. Fixed coordinates and explicit initial cards remove fixture nondeterminism without stripping tooltips or weakening assertions. Each cell retains raw batches plus first/last states for diagnosis.

Reduction = `100 * (B - C) / B`; negative values mean slower. A stage value is the median of its three fork medians. Allocation is the median of three per-fork means from the server-thread allocated-byte counter. Timing includes required lane callbacks and selection output construction/checksum; trace hashing is outside the measured block. Setup includes per-board catalogue reload, lane creation/index registration, Blueprint setup, catalogue lookup and tower creation/placement. Global server startup, floor setup and final cleanup are outside per-board setup but present in the run log. The first call is the first call for that newly created board after preconditioning, not a cold JVM. The initial preconditioning sweep and its cleanup are outside the reported cell costs but retained in full run time; no startup-cost reduction is claimed. GC windows include warmup and trace construction, so they are not isolated tick GC costs. Counter reads and the host observer add constant costs shared by all stages.

### Every builder: small-input behavior and N=32 costs

All rows below passed input/observed-trace comparison. Times are microseconds per lane call. N=0 is an empty-board control, not a gameplay optimization claim. Small negative results and unchanged family paths must not be hidden by the equal-weight aggregate.

| Builder ID (namespace semion-td) | N=0 reduction | N=1 reduction | N=8 reduction | N=32 reduction | B/S/C at N=32 (us/call) | B/C at N=32 (bytes/call) |
|---|---:|---:|---:|---:|---:|---:|
| villager_towers | -54.55% | -72.86% | -35.29% | +33.65% | 67.041/65.188/44.478 | 136824.0/136576.0 |
| villager_adv_towers | -6.67% | +34.10% | -7.81% | -6.30% | 40.059/39.159/42.584 | 137464.0/137216.0 |
| undead_towers | -44.44% | +45.45% | +21.84% | -63.18% | 10.066/13.231/16.425 | 40944.0/40600.0 |
| animal_towers | +23.08% | +29.95% | -40.21% | +23.92% | 4164.628/4082.184/3168.419 | 3964560.0/2768184.0 |
| warlock_towers | +17.65% | -77.70% | -79.37% | +26.77% | 47.306/28.575/34.641 | 59216.0/58744.0 |
| legion_towers | -88.89% | -58.33% | -1.52% | +32.70% | 338.647/635.072/227.919 | 356448.0/173320.0 |
| resonance_towers | +0.00% | -6.06% | -5.58% | -11.77% | 5.125/9.500/5.728 | 34064.0/33720.0 |
| illager_towers | -11.11% | -6.67% | -16.47% | -85.06% | 4.350/7.013/8.050 | 27664.0/27320.0 |
| nether | -11.11% | -80.19% | +31.07% | -24.90% | 30.950/30.359/38.656 | 193552.0/193208.0 |
| end_towers | -95.00% | -60.82% | -72.33% | +4.73% | 14.203/8.322/13.531 | 53936.0/53592.0 |
| ocean | +16.67% | -58.06% | +8.77% | -75.86% | 4.791/7.591/8.425 | 27664.0/27320.0 |
| ancient_city | +44.12% | -18.87% | -86.15% | -4.02% | 21.006/20.734/21.850 | 65568.0/65224.0 |
| adversary_towers | +41.18% | -3.23% | +3.37% | -47.97% | 2.847/4.769/4.213 | 11859.0/11515.0 |
| mage_towers | -55.56% | -75.00% | -74.93% | -34.56% | 4.919/5.491/6.619 | 27664.0/27320.0 |
| engineer_towers | -44.44% | +17.72% | -10.26% | -61.76% | 3.481/3.231/5.631 | 18880.0/18472.0 |
| insect_towers | -111.11% | +5.56% | -3.48% | +2.36% | 11.922/11.694/11.641 | 41400.0/41056.0 |
| future_agency_towers | +0.00% | -85.48% | +9.47% | -59.47% | 4.719/4.769/7.525 | 27664.0/27320.0 |
| queen_towers | +5.26% | +10.87% | +6.82% | +9.59% | 10.750/18.394/9.719 | 48760.0/48416.0 |
| hero_party | +44.44% | +1.49% | +3.87% | +7.56% | 35.116/51.212/32.459 | 56924.0/56703.6 |
| atlantis | +11.11% | +2.47% | +40.54% | +34.75% | 17.841/16.850/11.641 | 58164.3/57820.3 |
| plant_towers | +28.57% | +3.18% | -7.69% | +3.18% | 16.928/17.850/16.391 | 45773.6/45429.6 |
| army | +15.79% | -1.75% | -3.70% | +32.31% | 16.309/12.609/11.041 | 47696.0/47352.0 |
| thunder | -11.11% | +37.61% | -12.82% | +32.27% | 24.519/22.166/16.606 | 85216.0/84872.0 |
| demon_lord_towers | +0.00% | +5.88% | +0.82% | -52.14% | 1.534/2.925/2.334 | 2320.0/1976.0 |
| gamble_towers | +37.50% | +29.41% | +12.12% | +2.70% | 16.191/12.294/15.753 | 50216.0/49891.3 |
| succubus | -11.11% | +7.50% | -68.76% | -1.87% | 15.234/13.709/15.519 | 59184.0/58696.0 |
| body | +0.00% | +4.24% | -97.66% | -5.59% | 5.028/7.088/5.309 | 28816.0/28472.0 |
| pet_towers | -11.11% | +13.93% | -8.73% | +1.05% | 21.119/19.094/20.897 | 70608.0/70696.0 |
| developer | +40.00% | -2.86% | +0.93% | +7.55% | 4.263/4.459/3.941 | 12160.0/11816.0 |
| frost | -11.11% | -1.59% | +22.52% | -42.88% | 6.784/9.903/9.694 | 24180.4/23839.9 |
| pirate | -12.50% | -9.68% | -12.09% | -10.43% | 8.300/12.681/9.166 | 36328.0/35984.0 |
| blueprint | +0.00% | +0.00% | -4.09% | -60.39% | 4.466/8.244/7.162 | 27664.0/27320.0 |
| magic_school | +0.00% | +0.00% | -5.29% | -11.18% | 17.191/17.522/19.113 | 38440.0/38096.0 |

### Distribution, setup, first call, allocation and GC

For N=32, mean and p95 are medians of the three per-fork statistics; max is the largest individual batch-normalized sample across all forks. A batch-normalized p95/max is not an individual tower-call latency distribution. The fork range shows the minimum/maximum of three fork medians. Every cell's complete mean/median/p95/max, CPU, setup and allocation data remain in `comparison.json` and the nine raw JSON profiles.

| Builder | B/C mean (us) | B/C p95 (us) | B/C max (us) | B/C fork-median range (us) |
|---|---:|---:|---:|---|
| adversary_towers | 3.457/4.180 | 5.338/5.537 | 5.688/6.000 | 2.722..4.903 / 3.341..5.278 |
| ancient_city | 22.052/22.781 | 36.425/27.775 | 44.462/37.862 | 20.262..21.634 / 21.769..22.294 |
| animal_towers | 4550.771/3294.451 | 6059.919/4013.950 | 7474.356/4568.562 | 4021.734..4233.031 / 3020.953..3230.838 |
| army | 16.246/12.776 | 18.450/18.244 | 20.081/19.975 | 9.613..18.797 / 9.681..17.566 |
| atlantis | 17.866/11.528 | 19.562/18.556 | 21.244/19.125 | 17.231..19.344 / 11.022..17.028 |
| blueprint | 5.266/8.446 | 7.669/8.312 | 8.700/344.988 | 4.409..5.209 / 5.144..8.369 |
| body | 5.098/5.333 | 5.631/5.881 | 8.725/8.569 | 4.631..7.969 / 5.247..7.888 |
| demon_lord_towers | 1.627/2.100 | 2.219/2.506 | 2.669/3.019 | 1.519..2.541 / 1.341..2.572 |
| developer | 4.147/3.567 | 4.975/4.456 | 9.694/5.200 | 4.075..4.581 / 2.328..4.013 |
| end_towers | 12.640/18.175 | 15.756/15.162 | 16.688/284.244 | 8.375..14.534 / 12.831..18.341 |
| engineer_towers | 3.836/5.302 | 5.906/6.588 | 7.406/7.588 | 3.350..3.506 / 3.134..6.956 |
| frost | 6.757/9.504 | 10.800/13.475 | 14.287/16.706 | 5.431..9.472 / 9.241..10.088 |
| future_agency_towers | 5.324/7.723 | 8.694/8.881 | 9.650/10.256 | 4.406..7.550 / 7.216..9.566 |
| gamble_towers | 16.599/17.631 | 24.106/23.544 | 26.131/26.619 | 8.894..17.122 / 10.934..17.031 |
| hero_party | 35.415/31.404 | 59.406/58.275 | 62.831/65.562 | 29.556..48.544 / 28.903..47.197 |
| illager_towers | 4.356/7.856 | 4.581/8.550 | 8.675/8.975 | 4.213..8.025 / 4.584..8.422 |
| insect_towers | 11.814/11.626 | 13.606/13.312 | 521.706/15.944 | 7.144..12.363 / 8.044..12.359 |
| legion_towers | 348.416/211.081 | 392.812/264.300 | 505.688/270.931 | 336.913..352.831 / 144.675..264.794 |
| mage_towers | 5.997/6.420 | 8.512/8.287 | 9.113/9.144 | 4.550..7.256 / 5.250..7.981 |
| magic_school | 16.061/17.111 | 19.269/21.019 | 27.194/21.675 | 9.906..18.091 / 16.856..19.631 |
| nether | 31.134/47.973 | 34.038/65.719 | 64.463/66.938 | 29.691..48.066 / 31.447..48.872 |
| ocean | 5.836/8.401 | 8.562/8.844 | 9.075/9.387 | 4.450..8.762 / 5.438..8.653 |
| pet_towers | 21.272/23.554 | 22.094/29.675 | 41.156/550.094 | 19.169..38.378 / 20.359..32.950 |
| pirate | 8.679/9.210 | 11.713/14.281 | 15.637/19.806 | 8.262..14.125 / 8.044..16.387 |
| plant_towers | 16.495/16.826 | 19.719/20.175 | 22.769/22.581 | 11.153..17.409 / 14.178..19.525 |
| queen_towers | 10.811/10.017 | 11.244/13.450 | 19.331/17.031 | 10.094..18.303 / 9.700..10.669 |
| resonance_towers | 5.290/6.029 | 5.713/8.631 | 253.800/10.088 | 5.078..5.259 / 5.291..8.869 |
| succubus | 20.307/15.666 | 26.419/19.769 | 31.456/27.394 | 13.484..24.175 / 14.756..26.309 |
| thunder | 24.442/16.893 | 25.356/17.694 | 25.712/24.988 | 14.200..24.725 / 15.963..24.288 |
| undead_towers | 10.127/16.220 | 13.006/17.481 | 17.706/19.488 | 8.928..15.972 / 9.672..17.859 |
| villager_adv_towers | 54.971/47.012 | 70.338/67.987 | 81.700/81.144 | 37.678..65.366 / 39.150..66.862 |
| villager_towers | 65.437/46.517 | 69.094/73.950 | 90.506/75.463 | 39.316..67.334 / 43.447..64.278 |
| warlock_towers | 47.804/46.188 | 53.487/52.263 | 57.975/414.613 | 25.734..52.997 / 27.403..51.306 |

The following N=32 values include full per-board setup rather than only the recurring cache lookup. CPU values use the platform counter and can be zero/quantized for short blocks; they do not prove a CPU-speed reduction by themselves. GC shows the totals over the three per-board windows, including warmup and observation work. Retained heap/object graphs, off-thread/native allocations, GPU and whole-machine disk throughput were not measured; O(N)/O(K) retained-space claims are algorithmic bounds, not measured heap savings.

| Builder | B/C setup (ms) | B/C setup allocation (KiB) | B/C first call (us) | B/C first-call allocation (bytes) | B/C mean CPU (us/call) | B/C GC collections / milliseconds |
|---|---:|---:|---:|---:|---:|---|
| adversary_towers | 11.18/12.50 | 8640.88/8648.84 | 26.10/28.60 | 11824.00/11624.00 | 30.52/0.00 | 0 collections, 0 ms / 0 collections, 0 ms |
| ancient_city | 10.20/12.77 | 8437.64/8453.75 | 35.90/71.50 | 65568.00/65368.00 | 30.52/0.00 | 1 collections, 4 ms / 0 collections, 0 ms |
| animal_towers | 12.66/13.62 | 10539.03/9967.95 | 5333.10/3342.20 | 4172944.00/2976712.00 | 4486.08/3295.90 | 34 collections, 176 ms / 24 collections, 153 ms |
| army | 11.28/11.23 | 8705.89/8713.36 | 26.00/30.10 | 47696.00/47496.00 | 0.00/30.52 | 1 collections, 4 ms / 0 collections, 0 ms |
| atlantis | 11.48/10.81 | 8649.91/8657.38 | 278.90/253.60 | 141320.00/141120.00 | 30.52/0.00 | 0 collections, 0 ms / 0 collections, 0 ms |
| blueprint | 7.95/7.99 | 8560.98/8568.63 | 14.40/13.20 | 28432.00/28232.00 | 0.00/0.00 | 0 collections, 0 ms / 1 collections, 6 ms |
| body | 8.07/10.59 | 8428.76/8435.48 | 21.80/18.50 | 28816.00/28616.00 | 30.52/30.52 | 0 collections, 0 ms / 0 collections, 0 ms |
| demon_lord_towers | 9.64/11.80 | 8327.22/8325.23 | 11.10/20.80 | 2320.00/2120.00 | 0.00/0.00 | 0 collections, 0 ms / 0 collections, 0 ms |
| developer | 10.29/10.31 | 8452.27/8452.98 | 7.00/10.30 | 12160.00/11960.00 | 0.00/30.52 | 0 collections, 0 ms / 0 collections, 0 ms |
| end_towers | 7.87/11.57 | 8455.76/8462.73 | 18.80/22.40 | 53936.00/53736.00 | 0.00/30.52 | 0 collections, 0 ms / 1 collections, 4 ms |
| engineer_towers | 11.86/11.50 | 8489.95/8498.21 | 19.30/36.60 | 18912.00/18648.00 | 0.00/0.00 | 0 collections, 0 ms / 0 collections, 0 ms |
| frost | 10.52/10.25 | 8519.73/8526.45 | 250.20/192.20 | 197696.00/197496.00 | 0.00/0.00 | 0 collections, 0 ms / 0 collections, 0 ms |
| future_agency_towers | 10.84/11.52 | 8694.93/8702.48 | 24.30/24.40 | 27664.00/27464.00 | 0.00/0.00 | 0 collections, 0 ms / 0 collections, 0 ms |
| gamble_towers | 12.73/10.81 | 9027.86/9034.48 | 25.50/24.50 | 49008.00/48808.00 | 30.52/0.00 | 0 collections, 0 ms / 1 collections, 5 ms |
| hero_party | 26.51/24.17 | 10247.97/10267.81 | 727.00/702.40 | 411952.00/412840.00 | 30.52/61.04 | 3 collections, 10 ms / 0 collections, 0 ms |
| illager_towers | 7.23/7.74 | 8421.17/8427.39 | 11.30/10.90 | 27664.00/27464.00 | 0.00/0.00 | 0 collections, 0 ms / 0 collections, 0 ms |
| insect_towers | 10.95/11.97 | 8661.44/8668.41 | 18.00/15.80 | 41400.00/41200.00 | 0.00/30.52 | 1 collections, 8 ms / 1 collections, 8 ms |
| legion_towers | 10.62/12.62 | 8502.96/8509.18 | 760.10/367.80 | 386112.00/203128.00 | 335.69/213.62 | 1 collections, 4 ms / 2 collections, 19 ms |
| mage_towers | 9.92/11.25 | 8509.16/8516.13 | 7.30/10.70 | 27664.00/27464.00 | 0.00/0.00 | 0 collections, 0 ms / 1 collections, 4 ms |
| magic_school | 24.59/20.04 | 9940.48/9948.14 | 24.50/27.20 | 38440.00/38240.00 | 0.00/30.52 | 1 collections, 4 ms / 0 collections, 0 ms |
| nether | 11.04/10.12 | 8418.57/8425.54 | 68.20/64.70 | 193552.00/193352.00 | 30.52/61.04 | 1 collections, 5 ms / 1 collections, 3 ms |
| ocean | 10.53/11.34 | 8429.72/8436.62 | 12.50/21.40 | 27664.00/27464.00 | 30.52/0.00 | 0 collections, 0 ms / 0 collections, 0 ms |
| pet_towers | 15.23/19.90 | 11985.74/12003.96 | 37.70/49.90 | 70608.00/70840.00 | 30.52/30.52 | 0 collections, 0 ms / 1 collections, 8 ms |
| pirate | 15.35/15.66 | 9322.72/9329.44 | 17.60/24.60 | 36328.00/36128.00 | 30.52/0.00 | 0 collections, 0 ms / 1 collections, 1 ms |
| plant_towers | 13.77/14.93 | 9215.20/9219.22 | 195.80/227.00 | 172360.00/172368.00 | 30.52/30.52 | 0 collections, 0 ms / 0 collections, 0 ms |
| queen_towers | 13.18/11.98 | 8886.77/8893.73 | 44.50/47.70 | 48760.00/48560.00 | 0.00/30.52 | 0 collections, 0 ms / 1 collections, 4 ms |
| resonance_towers | 7.15/7.12 | 8461.69/8467.91 | 7.10/7.50 | 34064.00/33864.00 | 0.00/0.00 | 2 collections, 7 ms / 0 collections, 0 ms |
| succubus | 9.50/14.15 | 8876.84/8883.65 | 30.70/41.70 | 59184.00/58840.00 | 30.52/30.52 | 0 collections, 0 ms / 1 collections, 7 ms |
| thunder | 9.25/8.81 | 9102.99/9109.71 | 9.20/9.00 | 27664.00/27464.00 | 0.00/0.00 | 0 collections, 0 ms / 0 collections, 0 ms |
| undead_towers | 11.06/11.80 | 8425.20/8431.42 | 40.40/47.30 | 40944.00/41080.00 | 30.52/30.52 | 0 collections, 0 ms / 1 collections, 11 ms |
| villager_adv_towers | 9.81/8.16 | 8517.96/8524.18 | 195.00/194.60 | 165984.00/164984.00 | 30.52/30.52 | 0 collections, 0 ms / 1 collections, 11 ms |
| villager_towers | 12.28/10.04 | 8490.64/8524.23 | 248.70/240.00 | 159008.00/158904.00 | 91.55/61.04 | 1 collections, 11 ms / 0 collections, 0 ms |
| warlock_towers | 13.95/13.92 | 10042.37/10048.59 | 55.60/60.90 | 59216.00/58888.00 | 30.52/30.52 | 1 collections, 4 ms / 1 collections, 7 ms |

### Stable capped-selection kernel

These are tied-integer selection workloads inside the isolated Fabric server. They validate and measure the shared selector, not a world entity lookup or a measured combat impact. CPU/setup/GC were not collected per kernel. Values are nanoseconds and allocated bytes per selection call; output hashCode/checksum cost is included in all stages.

| K | M | B ns/call | S ns/call | C ns/call | Reduction | B/C bytes/call | C mean / p95 / max (ns/call) |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 0 | 65.23 | 61.38 | 1.56 | +97.60% | 360.00/0.00 | 1.57/1.66/1.66 |
| 1 | 1 | 83.11 | 161.57 | 9.86 | +88.13% | 400.00/24.00 | 10.71/11.13/19.34 |
| 1 | 8 | 242.97 | 230.91 | 15.87 | +93.47% | 424.00/24.00 | 15.93/16.11/38.77 |
| 1 | 16 | 542.33 | 524.27 | 20.51 | +96.22% | 456.00/24.00 | 20.55/20.90/50.68 |
| 1 | 32 | 1214.26 | 1274.51 | 29.69 | +97.56% | 728.00/24.00 | 29.77/30.57/34.08 |
| 1 | 128 | 5666.46 | 5737.89 | 147.12 | +97.40% | 1336.00/24.00 | 149.55/158.89/168.46 |
| 1 | 512 | 27543.12 | 28268.55 | 634.23 | +97.70% | 3640.00/24.00 | 636.58/652.93/730.76 |
| 3 | 0 | 66.50 | 127.29 | 5.27 | +92.07% | 360.00/0.00 | 5.27/5.37/8.69 |
| 3 | 1 | 85.55 | 170.12 | 10.06 | +88.24% | 400.00/24.00 | 10.13/10.35/29.20 |
| 3 | 8 | 259.38 | 245.12 | 97.46 | +62.42% | 432.00/144.00 | 98.72/103.22/178.91 |
| 3 | 16 | 556.74 | 566.36 | 149.85 | +73.08% | 464.00/144.00 | 150.34/153.91/208.30 |
| 3 | 32 | 1229.44 | 1199.17 | 270.90 | +77.97% | 736.00/368.00 | 354.11/483.01/561.91 |
| 3 | 128 | 5678.61 | 5616.89 | 637.74 | +88.77% | 1344.00/368.00 | 656.47/778.52/1224.41 |
| 3 | 512 | 27673.68 | 26898.54 | 2234.81 | +91.92% | 3648.00/368.00 | 2291.55/2616.80/3274.61 |
| 8 | 0 | 69.29 | 129.15 | 5.27 | +92.39% | 360.00/0.00 | 5.27/5.37/7.62 |
| 8 | 1 | 86.38 | 170.02 | 10.06 | +88.36% | 400.00/24.00 | 10.24/11.52/25.20 |
| 8 | 8 | 291.85 | 283.64 | 133.74 | +54.17% | 448.00/192.00 | 138.62/186.23/282.62 |
| 8 | 16 | 592.53 | 576.76 | 262.45 | +55.71% | 480.00/192.00 | 265.41/276.76/502.05 |
| 8 | 32 | 1304.30 | 1232.91 | 548.00 | +57.99% | 752.00/584.00 | 560.81/614.06/727.25 |
| 8 | 128 | 5763.57 | 6017.24 | 1067.29 | +81.48% | 1360.00/584.00 | 1087.40/1165.43/1935.16 |
| 8 | 512 | 27846.19 | 27862.11 | 2812.99 | +89.90% | 3664.00/584.00 | 2857.97/3160.84/4577.34 |

### Rejected candidates, environment and remaining verification

Earlier candidate-v1, candidate-v2, candidate-v3, candidate-v4 and candidate-v5 suites and failed runs remain archived in the evidence directory. v1 had differing absolute world coordinates and independently random initial Queen cards; affected cells were blocked, then the inputs were corrected and all nine forks rerun. v2 showed a large small-input slowdown despite its N=32 aggregate improvement. v3 added a direct singleton path, RandomAccess min scan and a separate small-sort helper but did not consistently remove the small-sort slowdown. Production selection uses bounded stable insertion for M<=16. Candidate-v4 still showed small-input reversals; its diagnostics recorded changing JIT inlining decisions and deoptimizations. The final experiment adds one full board-matrix sweep and one full selection-matrix sweep before recording cells, then reruns all nine forks. Candidate-v5 retained 19 N=32 time regressions despite its aggregate gain. The final production candidate replaces the cached list with a private typed array, and the fixture separates board and selection responsibilities to reduce caller complexity. All nine stages/forks were rerun. Preconditioning reduces initial compilation/profile-order effects; it does not prove that every method has reached a permanent steady state. Negative final values above remain explicit; lower Big-O, compilation or a shorter function is not sufficient evidence of a speedup.

A user-owned Java 21 Minecraft client remained running. The observer recorded system CPU/free RAM and selected process CPU/private working set/I/O rates without reading command lines or credentials. The observation cost is included in every run. CPU/I/O and other foreground activity were not held constant, only three forks were sampled, and confidence intervals/significance were not established. The process was not stopped. Sequential balanced execution reduces ordering bias but does not remove host noise or justify every small percentage as a causal improvement.

Spark diagnostics sample the actual lane, Animal refresh/leader work and selection paths, alongside catalogue reload/setup. Inclusive sampled times overlap and must not be summed or substituted for steady-state measurements. Windows Java-sampler elapsed stacks are not exact CPU counters. JFR allocation weights are sampled estimates; the formal allocation tables use direct thread counters. Diagnostic bootstrap warnings and expected negative-fixture exceptions are retained in logs rather than filtered into a false clean-log claim.

No full-match target was specified beyond TPS preservation/MSPT reduction. This fixture cannot establish either server-wide metric. The historical strict combat comparison failure is still a failure and is not replaced by factory-board equality. A complete controlled combat experiment would need identical legal rosters/augments/enemy waves, fixed RNG and clocks, entity AI, team interactions, round lifecycle, persistence and resource effects, plus complete result equivalence before accepting a percentage. It costs longer runs and a stronger fixture; combat snapshot caches would additionally require exact invalidation at intervening state changes. The safe membership cache is retained without extending it to combat values. Real multiplayer and client GPU rendering were not run. No production-world validation is claimed.

The measured individual latency target remains unmet for 18 N=32 rows, including Engineer. JIT tiering, caller profiles and host/compiler activity are possible contributors; the diagnostic inlining/deoptimization observations do not prove the cause of Engineer's regression. A follow-up with foreground compilation would reduce compiler-queue overlap but change the production JVM conditions and startup cost. A deterministic complete-match replay would provide realistic input weights and combat coverage at the cost of a substantially stronger fixture and longer runs. Restoring the original lane copy is a reversible alternative for prioritizing the cheap-board latency baseline, but gives up the measured recurring-allocation reduction and requires a fresh comparison/release gate. None of these unmeasured alternatives is claimed as a fix.

The final diagnostics used the same exact filter, fixed inputs and measurement lengths in B/C, but shorter warmup (32), 10 samples and batch 1. The following are inclusive Spark **sampled milliseconds over the entire diagnostic recording**, not nanoseconds per operation or performance percentages. A missing top-80 entry is not evidence that a method never executed.

| Actual sampled path | B inclusive ms | C inclusive ms |
|---|---:|---:|
| kim.biryeong.semiontd.tower.ProductionTowerCatalogs.reloadBuiltIns | 1733.0 | 1454.0 |
| kim.biryeong.semiontd.game.PlayerLane.tickTowers | 833.0 | 676.0 |
| kim.biryeong.semiontd.tower.animal.AnimalStackTower.refreshAnimalStacks | 532.0 | 415.0 |
| kim.biryeong.semiontd.tower.animal.AnimalStackTower.refreshLeaderState | 413.0 | 273.0 |
| kim.biryeong.semiontd.tower.area.AreaTargetSelection.sortedFirst | 10558.0 | 887.0 |

JFR recorded 132 board-window events in each stage, with 91933 B and 91153 C compiler-inlining events. These are individual compile-site decisions; a success does not mean every invocation or every caller is inlined.

| Optimized callee | Inlined | Actual JIT decision | Event count |
|---|---|---|---:|
| kim/biryeong/semiontd/game/PlayerLane.towerSnapshot | false | callee is too large | 3 |
| kim/biryeong/semiontd/game/PlayerLane.towerSnapshot | true | inline (hot) | 1 |
| kim/biryeong/semiontd/tower/area/AreaTargetSelection$Ranked.<init> | true | inline | 1 |
| kim/biryeong/semiontd/tower/area/AreaTargetSelection$Ranked.<init> | true | inline (hot) | 1 |
| kim/biryeong/semiontd/tower/area/AreaTargetSelection.bounded | false | already compiled into a medium method | 4 |
| kim/biryeong/semiontd/tower/area/AreaTargetSelection.bounded | false | callee is too large | 5 |
| kim/biryeong/semiontd/tower/area/AreaTargetSelection.bounded | false | too big | 1 |
| kim/biryeong/semiontd/tower/area/AreaTargetSelection.first | false | already compiled into a medium method | 5 |
| kim/biryeong/semiontd/tower/area/AreaTargetSelection.first | false | callee is too large | 5 |
| kim/biryeong/semiontd/tower/area/AreaTargetSelection.first | false | too big | 1 |
| kim/biryeong/semiontd/tower/area/AreaTargetSelection.first | true | inline (hot) | 3 |
| kim/biryeong/semiontd/tower/area/AreaTargetSelection.lambda$bounded$0 | true | inline | 1 |
| kim/biryeong/semiontd/tower/area/AreaTargetSelection.lambda$bounded$0 | true | inline (hot) | 4 |
| kim/biryeong/semiontd/tower/area/AreaTargetSelection.small | false | already compiled into a big method | 7 |
| kim/biryeong/semiontd/tower/area/AreaTargetSelection.small | false | callee is too large | 5 |
| kim/biryeong/semiontd/tower/area/AreaTargetSelection.small | false | too big | 1 |
| kim/biryeong/semiontd/tower/area/AreaTargetSelection.sortedFirst | false | callee is too large | 10 |
| kim/biryeong/semiontd/tower/area/AreaTargetSelection.sortedFirst | false | not inlineable | 1 |
| kim/biryeong/semiontd/tower/area/AreaTargetSelection.sortedFirst | true | inline (hot) | 8 |

| Final fork | Host CPU mean / min / max (%) | Minimum free RAM (GiB) | Observer errors |
|---|---:|---:|---:|
| original-f1 | 25.22/8/57 | 32.83 | 0 |
| original-f2 | 20.09/4/41 | 33.56 | 0 |
| original-f3 | 27.40/9/65 | 33.28 | 0 |
| separated-f1 | 18.77/6/45 | 32.98 | 0 |
| separated-f2 | 20.06/7/43 | 33.53 | 0 |
| separated-f3 | 33.14/12/74 | 33.89 | 0 |
| optimized-f1 | 25.36/12/48 | 32.88 | 0 |
| optimized-f2 | 21.32/6/43 | 33.56 | 0 |
| optimized-f3 | 26.00/10/57 | 33.88 | 0 |

### Release gate and artifact inspection

`build test runGameTest remapJar --rerun-tasks` completed successfully for the final workspace in `workspace-commit-final-gate`. JUnit XML contains **2049 tests**, **0 failures**, **0 errors**, and **2 existing skips**; **979 required Fabric GameTests** passed. The normal release gate's opt-in profiling test returns success without measuring; the nine separately filtered runs provide the performance evidence.

Checks cover stable ties/duplicate target identities, small/large/linked-list selection, IEEE double order, immutable output, nested lane mutations/clear-readd, Animal intermediate state changes, Frost family thresholds, and world-backed Blueprint/common-area selection and callback snapshots. Incoming family regression tests remain enabled. Test expectations were not weakened.

On unobfuscated Minecraft 26.3, this build's `remapJar` compatibility task depends on the normal distributable `jar`; the task was retained and executed. `semion-td-1.0-SNAPSHOT+26.3.jar` passed ZIP CRC and safe-entry checks, contains Minecraft 26.3 / Java >=25 metadata and Java-25 major-version-69 changed classes matching compiled output, and contains **415 local private asset files**, all byte-identical to their source files, and **0 test class entries**. This owned-resource build is a local validation artifact and is **not public-release safe**; the comparison checkouts exclude the private assets. SHA-256: `de8349217929ff72149db6a72a8cc0be36918e1593d54270ab94fd2d55ae182f`. No private resources were deleted, force-added, uploaded or published. The earlier artifact check incorrectly required a local validation JAR to have zero private entries; that failure remains in the log. The corrected check verifies every local asset against source bytes and records the artifact as unsuitable for public release. This is build verification, not deployment authorization.

The standard `build` also produces a `-sources.jar` companion. Artifact verification distinguishes it from the executable distributable instead of treating two classifiers as duplicate outputs. Both local JARs passed CRC/safe-path checks, preserve source-identical private resources and exclude the test-only network mixin configuration. Neither local artifact was published.

### GameTest network-fixture repair

Follow-up build-log inspection traced embedded-channel promise/close failures through BetterHud's background boss-bar sends, Minecraft `Connection.sendPacket` and Netty's outbound buffer. The regression test failed before the repair because a background sender wrote directly to the embedded channel while the server thread was blocked. The GameTest-only `RuntimeNetworkConnectionMixin` now hands that same private send stage to the owning server thread for embedded play connections in a GameTest server. Real socket/client channels retain their normal execution path. No packets are discarded and no HUD update or test is disabled. The hook is excluded from the production JAR.

`RuntimeNetworkThreadingTest` verifies that 64 queued packets retain their order, every completion listener runs once, and server-thread sends remain immediate. Its second case closes a connection after enqueueing but before delivery and requires a closed-channel failure callback on the server thread. Calling the original private send stage preserves that completion instead of re-enqueueing on an already disconnected connection. The focused regression checks and the subsequent full build use fresh isolated worlds. This repair changes GameTest sources/metadata only; all measured production files and the performance fixture remain byte-identical to the optimized measurements. It does not establish an additional performance percentage or actual-client rendering result. Netty's [EmbeddedChannel source](https://github.com/netty/netty/blob/4.2/transport/src/main/java/io/netty/channel/embedded/EmbeddedChannel.java) provides the underlying embedded buffer/event-loop implementation.

### Commands and evidence provenance

Run these from the reviewed source repository with Java 25 and Python 3. Use `prepare` once for a fresh evidence workspace; it refuses to overwrite existing evidence. On an existing workspace, archive a completed suite before `sync`/rerunning; do not mix different source manifests under the same fork names.

```powershell
python tools/builder-performance/run.py prepare --baseline 2b431d1c6c85fca14c045a3c6bce492b1940b665
python tools/builder-performance/run.py suite --warmup 128 --samples 32 --batch 16
python tools/builder-performance/run.py compare
python tools/builder-performance/run.py fetch-spark
python tools/builder-performance/run.py run --variant original --mode profile --tag original-diag --warmup 32 --samples 10 --batch 1 --spark --jfr
python tools/builder-performance/run.py run --variant optimized --mode profile --tag optimized-diag --warmup 32 --samples 10 --batch 1 --spark --jfr
python tools/builder-performance/run.py run --variant workspace --mode gate --tag workspace-final-gate
python tools/builder-performance/run.py verify --tag workspace-final-gate
python tools/builder-performance/run.py archive --archive-name candidate-v2
python tools/builder-performance/run.py sync
```

The actual runner also pins one Gradle worker, a 1 GiB Gradle heap, one 1 GiB JUnit fork and the stated 2 GiB server heap. Every run descriptor identifies its fresh world path, command, source/output/log/load SHA-256 and exit status. JVM build/startup wall time is not used as an optimization percentage. `ProfileInspection.java` reads local Spark/JFR files with the isolated Spark JAR on its classpath; it does not upload them. Spark command syntax follows [official Spark documentation](https://spark.lucko.me/docs/Command-Usage); the server-backed test workflow follows [Fabric automatic testing](https://docs.fabricmc.net/develop/automatic-testing).

The follow-up runner pins `prepare` to the documented original commit by default and accepts an explicit `--baseline`. It resolves and records that commit before copying sources. Restoring Original/S GameTests and verifying changed production classes use the recorded baseline, so committing C does not silently redefine B as C. The nine fork descriptors below retain the earlier loaded runner hash; the subsequent baseline-pinning change is not retroactively attributed to those executions.

| Final fork | Source manifest SHA-256 | Result JSON SHA-256 |
|---|---|---|
| original-f1 | `1948c8b644687815d9934ab998bd59d8e00998add2e686f913cce0c7b292fc5c` | `e56dfcd9457de20c8587bddcb2c32716a9c2414eb4cf1075ff4d8205414f3588` |
| original-f2 | `1948c8b644687815d9934ab998bd59d8e00998add2e686f913cce0c7b292fc5c` | `3a40b30efd245cf5f0a7d19d0e40d6cfacb091b64ffe881ab6c71283c2994f91` |
| original-f3 | `1948c8b644687815d9934ab998bd59d8e00998add2e686f913cce0c7b292fc5c` | `92d9830128cc88d0a6b984a47c6f74a9ccc46f4c733082c7ed386f4c7f40c1b0` |
| separated-f1 | `8974936174847243abbbf84ddbbb7098d59ac77621aa1d64bf8d1c18c383e61b` | `570e4416122f9ebfaa1f857edcfb93d709ad34d2d8c5a856129d5cf189715b81` |
| separated-f2 | `8974936174847243abbbf84ddbbb7098d59ac77621aa1d64bf8d1c18c383e61b` | `4db2a1098016679ad33610621b8470235adc5524ac9e9bfa5d0d7170844921f4` |
| separated-f3 | `8974936174847243abbbf84ddbbb7098d59ac77621aa1d64bf8d1c18c383e61b` | `a11dfa2b1e8a04f36867774f6e6dede7ef2aaa53d34f80ebdd6356a7f6ff5739` |
| optimized-f1 | `e61e1523247f3889b3b2696c928d1f218ecd98eb3e436e9c4bfe4887ceae5baa` | `a1fb7d06f162b923e475ba12324fd0ad04d48616fdd2ef7423ba2a4394660d02` |
| optimized-f2 | `e61e1523247f3889b3b2696c928d1f218ecd98eb3e436e9c4bfe4887ceae5baa` | `bbe4e07df9fa492e8353c9aaa089afdc82fe87b42b4a59e9b1a91be0f3d4929a` |
| optimized-f3 | `e61e1523247f3889b3b2696c928d1f218ecd98eb3e436e9c4bfe4887ceae5baa` | `88bc9b7f878382bc38350850bf39e7c2d25c49dc169a8a84c3ed22052e99daa7` |

Fixture SHA-256: `0cd03ec9fe02aab4d9dc3ada2087b3a9b5539f00ec3b2abee4e09081d57e4dab`. All nine descriptors record the same loaded runner SHA-256: `5e6231ce6f34a45ebdfc54796aaf62b4c9924ad6460ef597757bd471273a4b03`. Compare refuses incomplete forks, source changes between a stage's forks, unequal JVM/world/settings, or inconsistent execution/output provenance. Per-cell percentages are blocked when input or observed-result hashes differ.

---

## Historical records

The original historical evidence is preserved below.

## 2b431d1c에 포함된 앞선 두 변경 차수의 판정

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


## 전체 빌더 후속 최적화

### 범위와 현재 진행표

후속 기준은 `2b431d1c6c85fca14c045a3c6bce492b1940b665`다. [JobRegistry.registerBuiltIns](../src/main/java/kim/biryeong/semiontd/job/JobRegistry.java)의 현재 등록을 다시 대조하여 빌더 33종을 확인했다. `DefaultJob`은 별도 기본 직업이며 33종에 포함하지 않는다. 공식 빌더 필터 14개만을 전체로 취급하지 않는다. 앞선 측정값·철회 결과·실패는 위에 보존한다.

상태는 코드 조사, 실제 수정, 동작 검증, 정량 측정을 구분한다. `공통 개선 적용`은 해당 공통 경로를 호출할 때의 적용을 뜻하며 빌더 자체의 성능 검증 완료가 아니다. 현재 새 빌더별 정량 측정은 모두 **미측정**이며, `측정상 개선 불필요`로 판정한 빌더는 없다. 수정하지 않는 판단도 코드·호출 빈도와 계측 근거 없이 완료로 승격하지 않는다.

개별 소스 검토는 **33종 모두 완료**했다. 후속 개별 변경은 **11종·운영 소스 12파일**이며, 마도사만 2파일이다. 나머지 22종은 아래 근거에 따라 유지하거나 후보로 남겼다. 이는 모든 경로의 최적화 완료를 뜻하지 않는다. 등록 표시명에서 번개는 람쥐썬더, 도박은 겜블, Adversary는 히어로, HeroParty는 용사다.

| 빌더 | 개별 코드 조사 | 병목 후보·구현 | 후속 동작 검증 | 후속 정량 측정 |
|---|---|---|---|---|
| [주민 빌더](../src/main/java/kim/biryeong/semiontd/job/VillagerTowerJob.java) | 개별 검토 완료 | 유지: 지원 pulse·공유 AoE의 콜백 계약 | 통합 gate4 통과; 개별 전수실행 아님 | **미측정** |
| [주민 ADV 빌더](../src/main/java/kim/biryeong/semiontd/job/VillagerAdvTowerJob.java) | 개별 검토 완료 | 후보: CONTEST 미도래 정렬 생략; lazy UUID 계약 미해결 | 통합 gate4 통과; 개별 전수실행 아님 | **미측정** |
| [언데드 빌더](../src/main/java/kim/biryeong/semiontd/job/UndeadTowerJob.java) | 개별 검토 완료 | 유지: RNG shuffle·빈 탐색·부활 순서 | 통합 gate4 통과; 개별 전수실행 아님 | **미측정** |
| [동물 빌더](../src/main/java/kim/biryeong/semiontd/job/AnimalTowerJob.java) | 개별 검토 완료 | 고위험 후보: O(A²N) 중첩 갱신; 틱 캐시 보류 | 통합 gate4 통과; 개별 전수실행 아님 | **미측정** |
| [흑마법사](../src/main/java/kim/biryeong/semiontd/job/WarlockTowerJob.java) | 개별 검토 완료 | 유지: 희생은 이미 min; passive 캐시 수명 검증 필요 | 통합 gate4 통과; 개별 전수실행 아님 | **미측정** |
| [무리 빌더](../src/main/java/kim/biryeong/semiontd/job/LegionTowerJob.java) | 개별 검토 완료 | 변경: 염소 중첩 선택을 안정 순위 스캔으로 | 선정 변경 경로 통과 (최종 gate4) | **미측정** |
| [무블룸 빌더](../src/main/java/kim/biryeong/semiontd/job/ResonanceTowerJob.java) | 개별 검토 완료 | 후보: cycle 미도래 정렬 생략; lazy UUID 계약 미해결 | 통합 gate4 통과; 개별 전수실행 아님 | **미측정** |
| [우민 빌더](../src/main/java/kim/biryeong/semiontd/job/IllagerTowerJob.java) | 개별 검토 완료 | 유지: forced target·mark 만료·volley snapshot | 통합 gate4 통과; 개별 전수실행 아님 | **미측정** |
| [네더 빌더](../src/main/java/kim/biryeong/semiontd/job/NetherTowerJob.java) | 개별 검토 완료 | 유지: 단계 전환·혈액 FIFO·피해원 생성 순서 | 통합 gate4 통과; 개별 전수실행 아님 | **미측정** |
| [엔드 빌더](../src/main/java/kim/biryeong/semiontd/job/EndTowerJob.java) | 개별 검토 완료 | 유지: 사후 지뢰·화상·흡수의 시점과 순서 | 통합 gate4 통과; 개별 전수실행 아님 | **미측정** |
| [바다 빌더](../src/main/java/kim/biryeong/semiontd/job/OceanTowerJob.java) | 개별 검토 완료 | 변경: 재활용 공급의 최저 수분 수신자 검색 1회 | 선정 변경 경로 통과 (최종 gate4) | **미측정** |
| [고대 도시 빌더](../src/main/java/kim/biryeong/semiontd/job/AncientCityTowerJob.java) | 개별 검토 완료 | 후보: Warden top-k·sculk 인덱스; 수명 검증 필요 | 통합 gate4 통과; 개별 전수실행 아님 | **미측정** |
| [히어로 빌더](../src/main/java/kim/biryeong/semiontd/job/AdversaryTowerJob.java) | 개별 검토 완료 | 유지: 강제 선택·변신·점수 정리의 재진입 계약 | 통합 gate4 통과; 개별 전수실행 아님 | **미측정** |
| [마도사 빌더](../src/main/java/kim/biryeong/semiontd/job/MageTowerJob.java) | 개별 검토 완료 | 변경: 일반 주문 primary만 안정 min 선택 | 선정 변경 경로 통과 (최종 gate4) | **미측정** |
| [기술자](../src/main/java/kim/biryeong/semiontd/job/EngineerTowerJob.java) | 개별 검토 완료 | 유지: 앞선 min·회로 개선과 구분; 나머지 수명 검토 | 통합 gate4 통과; 개별 전수실행 아님 | **미측정** |
| [벌레 빌더](../src/main/java/kim/biryeong/semiontd/job/InsectTowerJob.java) | 개별 검토 완료 | 유지: isDestroyed 부작용·부활·소환 큐 순서 | 통합 gate4 통과; 개별 전수실행 아님 | **미측정** |
| [미래기관 빌더](../src/main/java/kim/biryeong/semiontd/job/FutureAgencyTowerJob.java) | 개별 검토 완료 | 변경: 밀집 통제 개수 계산에서 정렬 제거 | 선정 변경 경로 통과 (최종 gate4) | **미측정** |
| [붉은 여왕 빌더](../src/main/java/kim/biryeong/semiontd/job/QueenTowerJob.java) | 개별 검토 완료 | 유지: 강제 선택·카드·runner·외형 갱신 계약 | 통합 gate4 통과; 개별 전수실행 아님 | **미측정** |
| [용사 빌더](../src/main/java/kim/biryeong/semiontd/job/HeroPartyTowerJob.java) | 개별 검토 완료 | 변경: 사제 치유 대상 2개 안정 선택 | 선정 변경 경로 통과 (최종 gate4) | **미측정** |
| [아틀란티스 빌더](../src/main/java/kim/biryeong/semiontd/job/AtlantisTowerJob.java) | 개별 검토 완료 | 후보: 영역·압력 반복 검색; 소비/중첩 계약 보류 | 통합 gate4 통과; 개별 전수실행 아님 | **미측정** |
| [식물 빌더](../src/main/java/kim/biryeong/semiontd/job/PlantTowerJob.java) | 개별 검토 완료 | 후보: 토양 count; 시점·지형·RNG 계약 보류 | 통합 gate4 통과; 개별 전수실행 아님 | **미측정** |
| [군대 빌더](../src/main/java/kim/biryeong/semiontd/job/ArmyTowerJob.java) | 개별 검토 완료 | 유지: 지휘 주기 overflow 의심은 별도 기능 문제 | 통합 gate4 통과; 개별 전수실행 아님 | **미측정** |
| [람쥐썬더 빌더](../src/main/java/kim/biryeong/semiontd/job/ThunderTowerJob.java) | 개별 검토 완료 | 변경: 존재 확인의 추가 목록·정렬 제거 | 선정 변경 경로 통과 (최종 gate4) | **미측정** |
| [마왕 빌더](../src/main/java/kim/biryeong/semiontd/job/DemonLordTowerJob.java) | 개별 검토 완료 | 변경: 소환 점유열의 호출 범위 Set 조회 | 선정 변경 경로 통과 (최종 gate4) | **미측정** |
| [겜블 빌더](../src/main/java/kim/biryeong/semiontd/job/GambleTowerJob.java) | 개별 검토 완료 | 변경: 관전자 1인 선택을 안정 min으로 | 선정 변경 경로 통과 (최종 gate4) | **미측정** |
| [서큐버스 빌더](../src/main/java/kim/biryeong/semiontd/job/SuccubusTowerJob.java) | 개별 검토 완료 | 고위험 후보: 전체 꿈 상태 스캔; 재진입 캐시 보류 | 통합 gate4 통과; 개별 전수실행 아님 | **미측정** |
| [신체 빌더](../src/main/java/kim/biryeong/semiontd/job/BodyTowerJob.java) | 개별 검토 완료 | 유지: ray/범위·단계별 콜백 계약 | 통합 gate4 통과; 개별 전수실행 아님 | **미측정** |
| [반려동물 빌더](../src/main/java/kim/biryeong/semiontd/job/PetTowerJob.java) | 개별 검토 완료 | 유지: 유대는 이벤트 갱신; 생장·최종방어 계약 | 통합 gate4 통과; 개별 전수실행 아님 | **미측정** |
| [개발자 빌더](../src/main/java/kim/biryeong/semiontd/job/DeveloperTowerJob.java) | 개별 검토 완료 | 변경: hasBug CSV 조회에서 Set 생성 제거 | 선정 변경 경로 통과 (최종 gate4) | **미측정** |
| [혹한 빌더](../src/main/java/kim/biryeong/semiontd/job/FrostTowerJob.java) | 개별 검토 완료 | 후보: wave family count; 즉시타·냉장 snapshot 유지 | 통합 gate4 통과; 개별 전수실행 아님 | **미측정** |
| [해적 빌더](../src/main/java/kim/biryeong/semiontd/job/PirateTowerJob.java) | 개별 검토 완료 | 변경: 닻 중첩 선택을 안정 순위 스캔으로 | 선정 변경 경로 통과 (최종 gate4) | **미측정** |
| [빌더 빌더](../src/main/java/kim/biryeong/semiontd/job/BlueprintTowerJob.java) | 개별 검토 완료 | 후보: alive list/top-k; 타격 순서·소환 수명 유지 | 통합 gate4 통과; 개별 전수실행 아님 | **미측정** |
| [마법학교 빌더](../src/main/java/kim/biryeong/semiontd/job/MagicSchoolTowerJob.java) | 개별 검토 완료 | 변경: 주문 ID immutable map 조회 | 선정 변경 경로 통과 (최종 gate4) | **미측정** |
| [무직 (DefaultJob)](../src/main/java/kim/biryeong/semiontd/job/DefaultJob.java) | 개별 검토 완료 | 전용 전투 경로 없음; 33종에 포함하지 않음 | 전용 경로 없음; 공통 gate4 통과 | 해당 전용 경로 없음 |

### 후속 기능 검증과 미측정 범위

기능 검증은 고정한 격리 소스 사본에서 단일 실행자가 수행했다. 첫 실행과 재실행을 구분하며, 위 기존 두 변경 차수의 측정·실패 기록과 섞지 않는다.

| 실행 | 실제 결과 | 해석 |
|---|---|---|
| functional-gate-1 | `compileTestJava`에서 Legion 회귀의 존재하지 않는 `T2_GOAT_TOWER`/`T3_GOAT_TOWER` 상수 참조 9건으로 실패 | JUnit 실행 전 컴파일 실패. 원본 로그를 보존했다. |
| 회귀 입력·상수 수정 | 실제 `T2_STRONG_GOAT_TOWER`/`T3_EXTREME_GOAT_TOWER`로 수정. 순위 oracle 입력은 허용값 0/1/2/3/8로 분리하고, 음수 ability는 기존 런타임의 거절과 기존 설정 보존을 별도 assertThrows로 검증 | 음수를 허용하도록 기대를 완화하지 않았다. 첫 실패 로그·manifest·실패 테스트 원문을 보존하고, 같은 격리 디렉터리에 수정본을 반영한 새 manifest로 재실행했다. 실패 소스는 W2의 `failed-gate1-sources`에 보존했다. |
| functional-gate-2 JUnit | XML 271개, tests 2,043 중 성공 2,041, failures 0, errors 0, 기존 skipped 2. 신규 26개 포함 | 이번 후속 소스의 새 실행 결과. |
| functional-gate-2 GameTest | `All 974 required tests passed :)` (기존 968 + 신규 6) | 새 GameTest 6개 실제 발견·실행. |
| functional-gate-2 전체 | `BUILD SUCCESSFUL in 4m 26s`, exit 0 | `test runGameTest remapJar` gate 성공. `remapJar`는 UP-TO-DATE였으며 같은 production 소스로 gate1에서 생성된 JAR의 내부/해시 확인은 별도 기록한다. |
| gate2 이후 파일 대조 | Gamble 소스·GameTest의 CRLF→LF 정리, DemonLordPassiveTest의 자기동일식 assert 2줄 삭제 확인 | production 의미 변경은 없지만 최종 바이트 단위 증거를 맞추기 위해 새 manifest로 전체 gate를 다시 실행한다. gate2 결과를 최종 파일의 결과로 소급하지 않는다. |
| functional-gate-3 | 준비 스크립트 cp949 decode 실패 후 이전 manifest로 잘못 시작; configuration 단계에서 Ctrl+C 중단 | 검증 성공으로 계산하지 않으며 원본 로그를 보존했다. |
| functional-gate-4 (최종) | 새 JUnit tests 2,043 중 성공 2,041, failures 0, errors 0, 기존 skipped 2; 필수 GameTest 974 전부 통과. `BUILD SUCCESSFUL in 4m`, exit 0 | 최종 manifest SHA-256 `8eeb5e9e61aa3bc563440e59ca44f47d940c97669d843b06a2f342c36720e2df`. 로그의 `22:44:37`에 974개 통과 확인. |
| JAR 내부·소스 대조 | SHA-256 `91a67d4696ac4c6b9c1a94474d81aa6894159002a28063ceb2d70ca42f50c46c`, 29,653,724 bytes, ZIP 정상, private assets 0, test/benchmark classes 0 | `remapJar UP-TO-DATE`. 변경한 top-level class 12개는 JAR와 compiled main 바이트 전부 일치. manifest 3,829개 중 작성 중인 보고서 1개를 제외한 저장소 3,828개 및 격리 사본 3,829개 전부 일치, 미기록 추가 파일 0. |
| 새 빌더별 성능·할당 | **33종 모두 미측정** | 복잡도 개선은 소스상 판단. MSPT/TPS·할당량·GPU 향상 수치로 바꾸지 않는다. |

선정 JUnit 클래스는 DemonLordPassive 9, DeveloperBugLookup 5, HeroCompanionSupportController 6, LegionGoatStackSelection 6, MagicSchoolSpellLookup 3, OceanAugments 7, PirateAnchorSelection 6으로 합계 42개다. 이 숫자에는 기존 사례도 포함되므로 신규 26개와 더하지 않는다. 전체 suite 통과는 변경 없는 22종의 모든 상태·월드·렌더링 경로를 전수 실행했다는 뜻이 아니다. 실제 클라이언트 GPU, 실멀티플레이, 운영 world/reload와 새 성능 측정은 완료로 판정하지 않는다.

후속 원본 증거 경로 `W2`는 `C:\Users\Kiruy\Documents\Codex\2026-10-05\task\builder-optimization-2026-10-09`다. `functional-gate-1.log`, `functional-gate-2.log`, `candidate-source-manifest-gate2.json`은 로컬 작업 증거이며 저장소에 포함된 파일이 아니다. manifest SHA-256은 `d349813fd1a28c4df85c9cb80234bf3bb31b815ed083c8281becdd04af8a9687`, 기준 HEAD는 `2b431d1c6c85fca14c045a3c6bce492b1940b665`, 재실행 격리 사본은 `%TEMP%\semion-builders-checks-9mstn4o5\candidate`다. 재현 시 이 manifest와 일치하는 소스·테스트·리소스를 준비하고 저장소의 Gradle wrapper로 `test runGameTest remapJar`를 수행한다. 최종 실행 옵션·JAR/로그 해시는 바로 아래에 별도로 기록한다.


최종 원본은 같은 W2의 `candidate-source-manifest-gate4.json`, `functional-gate-4.log`, `functional-gate-4-run.json`, `functional-gate-evidence.json`이다. 이 원본·개별 JUnit XML은 로컬 검증 증거이며 원격 저장소 독자가 저장소에서 열 수 있는 첨부물로 표시하지 않는다.

| 최종 증거 | SHA-256 |
|---|---|
| candidate-source-manifest-gate4.json | `8eeb5e9e61aa3bc563440e59ca44f47d940c97669d843b06a2f342c36720e2df` |
| functional-gate-4.log | `e901ec6fab2b42006125af1d69d8d4b79e4fb1538c00e645df8e1c587c8df614` |
| functional-gate-4-run.json | `5373aca3c14570383ceb506b227e5ba65d5712c913a2a79025f64af7d6305882` |
| functional-gate-evidence.json | `17668dbd89f4948adc8326c1b0bac28f72f99d4bede676b11682295cd681fd1e` |

최종 실행은 UTC `2026-10-09T13:40:41.617376`부터 `13:44:42.230314`까지다. GameTest 개별 XML은 발견되지 않았으므로 974개 통과의 근거는 완전한 runGameTest 표준출력 로그다. JUnit XML 271개의 개별 해시와 suite별 개수는 evidence JSON에 기록했다. 기존 skip 2개는 `EndBalanceRepositoryContractTest.siblingBalanceRepositoryMatchesTheEndAbilityContract()` 및 `siblingBalanceRepositoryContainsOnlyKnownTowerAbilityKeys()`이며 성공 수에 넣지 않았다.

재현 명령은 manifest와 일치하는 격리 candidate 디렉터리에서 다음과 같다. W2 변수는 위에 적은 로컬 증거 경로를 가리킨다.

```powershell
.\gradlew.bat test runGameTest remapJar --offline --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1G -XX:ActiveProcessorCount=2' --init-script "$W2\functional-gate-limits.gradle" --console=plain --no-daemon
```

init script SHA-256은 `30f517cc0e816711cd376ef3c9363a0d83dd2c1832dfc90f0a4954b8325c4fc0`이다. Test는 maxParallelForks=1, heap=1g, ActiveProcessorCount=4; runGameTest는 min/max heap=2g, G1GC, ActiveProcessorCount=4로 제한했다. 이는 기능 검증 실행 제한이며 시간·할당 벤치마크 환경이나 성능 비교 결과가 아니다.

### 공통 호출 경로와 판정 경계

- `PlayerLane.tickTowers`는 레인 타워 스냅샷을 순회하고 현재 membership을 다시 확인한 후 개별 `tick`을 호출한다. 이어 무리·언데드·주민 ADV·공명·우민 상태, 동기화와 곤충 flush를 실행한다. 스냅샷과 membership 검사는 콜백 중 제거·추가 관찰 규칙이다. 단순 캐시로 생략하지 않는다. 근거: [PlayerLane](../src/main/java/kim/biryeong/semiontd/game/PlayerLane.java).
- `Tower.tick`은 생존·수면·쿨다운을 확인하며 준비된 경우에만 `execute`한다. `EntityBackedTower.tick`의 엔티티 경로와 `TowerAttackMonsterGoal.tick`의 기본 공격은 따로 추적한다. 타깃 재검색 간격 상수는 5틱이며 캐시 무효 시의 재조회도 존재하므로 모든 조회를 정확히 5틱당 한 번이라고 단정하지 않는다. 근거: [Tower](../src/main/java/kim/biryeong/semiontd/tower/Tower.java), [EntityBackedTower](../src/main/java/kim/biryeong/semiontd/tower/EntityBackedTower.java), [TowerAttackMonsterGoal](../src/main/java/kim/biryeong/semiontd/entity/tower/goal/TowerAttackMonsterGoal.java).
- 앞선 `TimedEffectSet` 최적화의 실제 production 진입은 `SemionTowerEntity.activeMultiplicativeEffectMagnitude`와 마법학교의 Protego/Protego Maxima 피해 경감이다. 다른 빌더도 해당 효과를 받는 엔티티 경로를 사용할 수 있지만 모든 빌더의 모든 공격이나 틱이 개선됐다는 뜻은 아니다. 근거: [MagicSchoolSpellCombat](../src/main/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolSpellCombat.java), [SemionTowerEntity](../src/main/java/kim/biryeong/semiontd/entity/tower/SemionTowerEntity.java).
- 공통 범위 API는 활성 레인·서버 스레드·생존·제거·lane·제외 ID·반경·추가 predicate를 검사한 후보 스냅샷에 callback을 순서대로 적용한다. 정렬·목록을 제거하더라도 이 계약을 직접 월드 검색으로 바꾸지 않는다. 근거: [AreaEffectService](../src/main/java/kim/biryeong/semiontd/tower/area/AreaEffectService.java).
- `SemionTowerEntity.aiStep`은 효과 tick과 최대 체력 변화, 재생·손실, 이름·외형·비행 상태를 갱신한다. `TowerVfxService`는 실제 공격·보조 공격·회복·주문·범위 이벤트를 큐에 넣고 lane/수신자 예산으로 처리한다. 호출이 없는 경로를 매틱 비용으로 합산하거나 서버 단위 테스트를 GPU 표시 검증으로 바꾸지 않는다. 근거: [TowerVfxService](../src/main/java/kim/biryeong/semiontd/entity/tower/vfx/TowerVfxService.java).

지원 `Tower.execute`에서 성공 후 cooldown=d를 설정하고 이후 호출은 감소 후 return하므로, 별도 감소가 없는 연속 활성 상태의 실행 간격은 **d+1 lane tick 호출**이다. 평타 AI의 카운터와 같은 것으로 취급하지 않는다. forced targeting은 일반 cache보다 먼저 실행되므로 특히 MagicSchool/Adversary/Queen/Illager의 조회를 일괄 5틱이라고 표기하지 않는다.

조사는 현재 소스와 테스트를 직접 추적했다. 기존 CodeGraph 인덱스는 10월 2–3일 자료이고 현재 HEAD 연결이 확인되지 않아 현재 소스의 근거로 사용하지 않았다. 아래 수치·주기는 코드 상수 또는 런타임 fallback이며 운영 balance 값 검증이 아니다. Big-O와 임시 자료구조는 소스 분석이고 JIT 이후 실제 할당량은 측정하지 않았다.

### 개별 빌더 상세 감사의 읽는 방법

각 절의 8영역 표는 경로 색인이다. 바로 뒤에 호출 주기, 복잡도·할당, 순서·거리·제거·월드 수명·결정성, 기존 및 추가 회귀 근거를 통합했다. 파일 링크는 이 문서에서 상대적인 `../src/...` 경로다. 남은 경로 약어 `M/`, `M:`, `main/`은 `src/main/java/kim/biryeong/semiontd/`, `T/`, `T:`, `U:`, `test/`, `unit/`은 `src/test/java/kim/biryeong/semiontd/`, `G/`, `G:`, `gt/`은 `src/gametest/java/kim/biryeong/semiontd/`를 뜻한다. `:숫자`는 조사 당시 소스 줄이며 연속한 `:숫자`는 바로 앞의 명명된 파일을 이어 가리킨다. 변경 파일은 아래 각 조사 묶음의 줄번호 기준을 함께 사용한다. 여러 줄을 적은 링크는 첫 줄로 연결한다. 테스트 클래스만 적힌 경우에는 클래스 파일로 연결한다.

실행 상태는 위 통합 gate 표가 최신이다. 상세 기록에서 담당자가 실행하지 않았다는 서술은 조사·패치 준비 단계의 수행 범위를 보존한 것이며, 최신 gate의 미실행을 의미하지 않는다. 후속 정량 성능은 모든 절에서 계속 미측정이다.

<a id="builder-villager"></a>

### 1. 주민 빌더 — Villager

판정: **유지: 지원 pulse·공유 AoE의 콜백 계약**. 개별 조사 완료, 새 성능 미측정. 통합 gate4 통과를 개별 전수실행으로 확대하지 않는다.

복잡도: N=lane 타워, A=동물, G=eligible 염소, C=공간 후보, Q=소환 대기, I=환영, R=합체 재료, P=참가자. 미변경 줄은 기준 HEAD, Legion 선택/회귀는 후속 조사 줄이다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | 기본 entity/base tick와 Allay 지원 실행 |
| attack | 일반·splash·thorn·cat 및 공격/사망 효과 |
| target | 공통 목표와 AntiTankerCat 최대HP 선택 |
| AoE | 지원 pulse·반사·공격/사망 AoE |
| stack | 지원 효과의 source/기간·중첩 |
| summon / absorb | 가족 전용 소환·희생·흡수 없음 |
| sync | 엔티티 재생성·HP와 지원 효과 동기화 |
| VFX | 가족 palette·공통 공격/범위/회복 이벤트 |

#### 경로·주기·비용·경계·수명과 회귀 근거

##### 타워·매틱·공격·타깃

- [src/main/java/kim/biryeong/semiontd/tower/villager/VillagerTowerCatalogs.java:13–33](../src/main/java/kim/biryeong/semiontd/tower/villager/VillagerTowerCatalogs.java#L13)는 일반 주민 티어별 runtime을 연결한다. T1 일반 공격은 ProductionTower, 상위는 VillagerSplash/Thorn/Cat, 지원은 Allay. [src/main/java/kim/biryeong/semiontd/job/VillagerTowerJob.java:31](../src/main/java/kim/biryeong/semiontd/job/VillagerTowerJob.java#L31)은 base만 허용한다.
- [src/main/java/kim/biryeong/semiontd/tower/villager/VillagerThornTower.java:80–96](../src/main/java/kim/biryeong/semiontd/tower/villager/VillagerThornTower.java#L80): 매틱 thorn cooldown 감소 O(1), wave-start에서 giantTicks=0 (`:100`), 증강 GIANT일 때 configured interval(코드 fallback 60)을 채운 후 공유 nearestTargets AoE(최대수 fallback 12). entity 존재/생존·수면 조건 및 round reset giantTicks=-1 (`:64–70`) 유지 필요.
- [src/main/java/kim/biryeong/semiontd/tower/villager/VillagerThornTower.java:34–46](../src/main/java/kim/biryeong/semiontd/tower/villager/VillagerThornTower.java#L34): 피격 시 쿨다운이 없으면 주변 공유 thorns AoE, 이후 설정 cooldown. `:149–155` 생존스택 증가 시 최대HP 증가분만 현재HP에 더한다. final defense `:74`, round reset `:64`는 증가 시점이 서로 다르다.
- [src/main/java/kim/biryeong/semiontd/tower/villager/VillagerSplashTower.java:66–80](../src/main/java/kim/biryeong/semiontd/tower/villager/VillagerSplashTower.java#L66): 매 기본공격 공유 splash 후 T3 공격횟수 증가; 목표가 살아 있으면 임계점마다 추가 기본공격→secondary VFX→재귀 onAttack→kill hook. 저장된 공격횟수/사망 분기가 재귀 횟수를 결정한다. 이를 반복문으로 단순 치환하거나 primary/secondary 순서를 바꾸지 않았다.
- [src/main/java/kim/biryeong/semiontd/tower/villager/AntiTankerCatTower.java:32–44](../src/main/java/kim/biryeong/semiontd/tower/villager/AntiTankerCatTower.java#L32): eligible candidates에서 최대 maxHealth를 `max`로 선택 O(C), 같은 값은 encounter order. runtime monster가 없으면 entity maxHealth를 쓴다. 이미 전원 정렬을 하지 않는다.
- [src/main/java/kim/biryeong/semiontd/tower/villager/LaneClearCatTower.java:44–47,79–109](../src/main/java/kim/biryeong/semiontd/tower/villager/LaneClearCatTower.java#L44): kill마다 exploded UUID HashSet을 만들고 시체 폭발. 증강 chain은 depth cap 및 exploded dedup, AugmentCombat trigger 억제. 각 폭발은 공통 area API에 위임; 영역 밖/죽은 대상/순서를 임의 재사용하면 의미가 변한다.

##### 범위·스택·소환·동기화·VFX

- [src/main/java/kim/biryeong/semiontd/tower/villager/AllayTower.java:44–60](../src/main/java/kim/biryeong/semiontd/tower/villager/AllayTower.java#L44)은 tier별 heal/weapon/armorer pulse. `:63–131`의 요청은 등록 타워 only (`:134–141`), target별 blocked-until TowerDataKey를 world gameTime과 비교 (`:172–177`). heal 실제 적용 또는 buff 변화 시에만 cooldown 예약과 area-onChange VFX. 대상 없음/모두 blocked이면 다음 틱 재조회한다. pulse당 공간수집과 O(C) 적용, request·lambda·result 및 효과 update 비용이 있다.
- 무기상은 sourced damage+speed, 방어구상은 heal+damage reduction. 동일 전역 source ID 때문에 여러 제공자의 동일 효과 중첩 규칙을 바꾸면 안 된다. Allay 지원 주기 조정은 `:196–229`, ADV heal-line은 최소20틱이며 일반 주민은 최소1틱.
- 스택은 Thorn/Splash의 필드이며 업그레이드 복사는 각각 `:126`, `:84`; Cat death-stack copy는 LaneClear `:73`, AntiTanker `:84`. 성장량/HP 동기화는 서로 같지 않다. [src/main/java/kim/biryeong/semiontd/tower/villager/VillagerAugments.java:54–80](../src/main/java/kim/biryeong/semiontd/tower/villager/VillagerAugments.java#L54)은 wave temporary inheritance/extra-attack reset, 첫 사망 때 레인 grows 전체에 사용표시 후 거리·logicalId min 상속. O(N), 죽음 이벤트 빈도.
- [src/main/java/kim/biryeong/semiontd/tower/villager/VillagerAugments.java:95](../src/main/java/kim/biryeong/semiontd/tower/villager/VillagerAugments.java#L95) longevity 추가타는 실제 피해·생존·남은 횟수 guard 후 source 추가타를 실행. 기본 주민에는 독립적인 소환/희생/흡수, 월드/플레이어 정적 상태 저장소가 없다. 콜백 기반 폭발/지원과 tower-local 스택이 주 기능이다.
- 기본/추가/area VFX는 공통 VILLAGER palette. 직접 pulse·splash·corpse explosion style 사용 위치는 위 코드에 있다.

##### 위험·테스트·결정

- 순서 위험: chain depth/UUID dedup, callback 중 death-stack 증가, splash/추가타/kill 순서, 지원 blocked-time 예약은 유지한다. 좌표범위·owner/lane 검사는 공유 area 계층과 family 필터를 모두 고려해야 한다.
- [src/gametest/java/kim/biryeong/semiontd/tower/villager/VillagerTowerRuntimeTest.java:36](../src/gametest/java/kim/biryeong/semiontd/tower/villager/VillagerTowerRuntimeTest.java#L36) heal+duplicate block, `:98,137,168` 생존/maxHP/업그레이드, `:205` sourced buff, `:245` 탱커 보너스, `:297` 기본 폭발 non-chain, `:331` 근처 몬스터·타워 사망스택, `:419` cat copy.
- [src/gametest/java/kim/biryeong/semiontd/tower/villager/VillagerTowerAugmentCombatTest.java:18,49,78,102](../src/gametest/java/kim/biryeong/semiontd/tower/villager/VillagerTowerAugmentCombatTest.java#L18): chain cap, 상속 단회, longevity trigger 억제, giant 60틱/reset. [src/test/java/kim/biryeong/semiontd/tower/villager/VillagerAugmentsTest.java:25,42](../src/test/java/kim/biryeong/semiontd/tower/villager/VillagerAugmentsTest.java#L25): tier-family/health ratio. [src/test/java/kim/biryeong/semiontd/tower/villager/AllayTowerIntervalTest.java:9](../src/test/java/kim/biryeong/semiontd/tower/villager/AllayTowerIntervalTest.java#L9) 최소주기.
- 검토 후 소스 유지. O(1) 계산/작은 runtime helper 인라이닝 또는 이벤트 호출의 단순 정리만으로 성능 개선이라고 부르지 않는다.

<a id="builder-villageradv"></a>

### 2. 주민 ADV 빌더 — VillagerAdv

판정: **후보: CONTEST 미도래 정렬 생략; lazy UUID 계약 미해결**. 개별 조사 완료, 새 성능 미측정. 통합 gate4 통과를 개별 전수실행으로 확대하지 않는다.

복잡도: N=lane 타워, A=동물, G=eligible 염소, C=공간 후보, Q=소환 대기, I=환영, R=합체 재료, P=참가자. 미변경 줄은 기준 HEAD, Legion 선택/회귀는 후속 조사 줄이다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | 주민 공통 tick + 경험치 flush·CONTEST |
| attack | 주민 공격 경로와 역할/증강 후처리 |
| target | 공통 선택 + CONTEST 대표 순서 |
| AoE | 주민 공유 AoE와 역할 증강 |
| stack | 경험치·rank·역할·강화/CONTEST 상태 |
| summon / absorb | 희생/흡수 없음; 가족 전투 소환 없음 |
| sync | 교체 시 역할·경험치·pending membership |
| VFX | ADV palette·승급/역할 이벤트 |

#### 경로·주기·비용·경계·수명과 회귀 근거

##### 공통 주민 동작과 추가 경로

- [src/main/java/kim/biryeong/semiontd/tower/villager/VillagerTowerCatalogs.java:51–69](../src/main/java/kim/biryeong/semiontd/tower/villager/VillagerTowerCatalogs.java#L51)는 별도 ADV tower ID들을 같은 runtime 클래스에 연결. [src/main/java/kim/biryeong/semiontd/job/VillagerAdvTowerJob.java:27](../src/main/java/kim/biryeong/semiontd/job/VillagerAdvTowerJob.java#L27)은 ADV만 허용; 기본 Villager의 ownership과 분리된다. 따라서 위 Villager 공격/지원/사망 동작을 모두 물려받되 별도 경험치·평판·증강이 추가된다.
- [src/main/java/kim/biryeong/semiontd/tower/villager/VillagerAdvStates.java:76–111](../src/main/java/kim/biryeong/semiontd/tower/villager/VillagerAdvStates.java#L76) wave start에 P명의 ADV 참가자/각 lane tower를 순회하여 경험치 snapshot 레코드를 만든다. 필드는 player UUID, lane, Tower 참조, tier, experience, normalGain, mentorRatio. 서버 thread에서 현재 tower effects 갱신 후 single-thread daemon executor로 순수 결과 계산(`:251–270`, max mentor/min mentee, 첫 동률 유지), 결과 batch를 player queue에 넣는다.
- [src/main/java/kim/biryeong/semiontd/game/SemionGame.java:1394](../src/main/java/kim/biryeong/semiontd/game/SemionGame.java#L1394) → `VillagerAdvStates.applyPending:114–135`가 매틱 player queues를 drain. 현재 player job과 **동일 lane 객체**를 확인하고 `lane.towers().contains(result.tower())`를 결과마다 검사한다. batch에 E개 결과가 있으면 membership만 O(E·N), 보통 웨이브 1회 결과 도착 시의 burst. 오래된/제거된 타워 결과는 적용하지 않는다.
- [src/main/java/kim/biryeong/semiontd/tower/villager/VillagerAdvStates.java:194–242](../src/main/java/kim/biryeong/semiontd/tower/villager/VillagerAdvStates.java#L194)는 경험치·평판을 8종 sourced timed effect로 refresh. 동기화는 wave start/pending result/wave clear/leak/배치/업그레이드에 연결되어 있으며 매틱 모든 tower effects를 refresh하는 독립 루프는 없다. 배치·업그레이드 호출은 [src/main/java/kim/biryeong/semiontd/tower/ProductionTowerService.java:112,346](../src/main/java/kim/biryeong/semiontd/tower/ProductionTowerService.java#L112).
- [src/main/java/kim/biryeong/semiontd/tower/villager/VillagerAdvAugments.java:79–105](../src/main/java/kim/biryeong/semiontd/tower/villager/VillagerAdvAugments.java#L79)의 CONTEST가 **매틱 병목 후보**다. world/증강 guard 뒤 eligible+alive 전체를 경험치 desc/logicalId 오름차순 정렬하고 리스트 및 LinkedHashMap role representatives를 만든 뒤에야 각 NEXT_CONTEST due를 확인. 기본120틱 action에도 정렬 O(N log N)+O(N) storage를 매틱 지불한다. due이면 지원 additionalAction 또는 nearest1 additionalAttack. 역할별 대표와 실행 순서가 정렬 순서에 의존한다.
- [src/main/java/kim/biryeong/semiontd/tower/villager/VillagerAdvAugments.java:55–63](../src/main/java/kim/biryeong/semiontd/tower/villager/VillagerAdvAugments.java#L55) 수석졸업 선발은 pending 적용 뒤 snapshot 성격의 정렬이며 `:107–116`은 실제 피해가 난 공격에서 nearest extra targets(기본2)를 공유 API로 공격. `:66–72`는 레인/최종방어 전체 사거리 계산; `:125–127` 상세 UI의 역할대표 확인은 sorted-findFirst이나 UI 조회 빈도다.

##### 상태 수명·VFX·없는 기능

- [src/main/java/kim/biryeong/semiontd/tower/villager/VillagerAdvStates.java:33–38](../src/main/java/kim/biryeong/semiontd/tower/villager/VillagerAdvStates.java#L33) 평판 ConcurrentHashMap, player별 pending queue, 1개 daemon executor. `:52–61` clear removes map entry; in-flight task가 보유한 이전 queue에는 나중에 결과가 넣어져도 map에서 분리되어 새 게임에 적용되지 않는다. snapshot은 순수 숫자 외 lane/tower 참조를 갖기 때문에 executor가 지연되면 과거 월드 객체의 일시적 보유가 가능하다. 이를 영구 누수 또는 현재 버그로 단정하지 않는다.
- [src/main/java/kim/biryeong/semiontd/job/JobVillagerAdvLifecycle.java:8,13](../src/main/java/kim/biryeong/semiontd/job/JobVillagerAdvLifecycle.java#L8) match start/elimination clear, [src/main/java/kim/biryeong/semiontd/job/JobBuilderLifecycle.java:141](../src/main/java/kim/biryeong/semiontd/job/JobBuilderLifecycle.java#L141) close-before-lanes clear. CONTEST/graduate 데이터는 [src/main/java/kim/biryeong/semiontd/job/JobLaneLifecycle.java:34,55](../src/main/java/kim/biryeong/semiontd/job/JobLaneLifecycle.java#L34)의 reset/start 경계. 기본 생존효과 배율은 [src/main/java/kim/biryeong/semiontd/tower/villager/VillagerAdvStates.java:182](../src/main/java/kim/biryeong/semiontd/tower/villager/VillagerAdvStates.java#L182)의 ADV 0.5.
- [src/main/java/kim/biryeong/semiontd/tower/villager/VillagerAdvReputationBossBarService.java:26–53](../src/main/java/kim/biryeong/semiontd/tower/villager/VillagerAdvReputationBossBarService.java#L26)는 [src/main/java/kim/biryeong/semiontd/game/SemionGameManager.java:1871](../src/main/java/kim/biryeong/semiontd/game/SemionGameManager.java#L1871)에서 매틱 P명 확인, visible HashSet·bossbar key snapshot·title Component 생성. `:93–105` 표시 갱신과 별도 resync counter가 있어 단순 값 캐싱 시 reconnect/resync 생략 위험. UI rendering은 미검증.
- 전용 VILLAGER_ADV palette이며 기본 주민과 같은 공격/area VFX 엔트리를 소비한다. 독립 summon/absorb 경로는 없다.

##### 테스트·후보 결정

- [src/gametest/java/kim/biryeong/semiontd/tower/villager/VillagerAdvancedTowerRuntimeTest.java:61](../src/gametest/java/kim/biryeong/semiontd/tower/villager/VillagerAdvancedTowerRuntimeTest.java#L61) 등록/스타터, `:116` async 경험치/업그레이드 gate, `:157,162` mentor 선택·미선택 결과, `:210` half survival, `:249` 효과 maxHP healing, `:287` leak 평판.
- [src/gametest/java/kim/biryeong/semiontd/tower/animal/JobAdvAnimalResonanceGameTest.java:117,144](../src/gametest/java/kim/biryeong/semiontd/tower/animal/JobAdvAnimalResonanceGameTest.java#L117)는 파일 위치가 animal이지만 실제 ADV graduate extra attack/contest support action regression을 포함한다. 감사에서 이를 누락하지 않았다.
- [src/test/java/kim/biryeong/semiontd/tower/villager/VillagerAdvAugmentsTest.java](../src/test/java/kim/biryeong/semiontd/tower/villager/VillagerAdvAugmentsTest.java), [src/test/java/kim/biryeong/semiontd/tower/villager/VillagerAdvReputationBossBarServiceTest.java:9,15](../src/test/java/kim/biryeong/semiontd/tower/villager/VillagerAdvReputationBossBarServiceTest.java#L9)도 존재.
- 우선 후보: 정렬 전에 eligible+alive+due 존재만 검사하고 아무도 due가 아니면 정렬/대표집계를 생략. 실행 틱은 기존 snapshot/정렬/대표선정/콜백 순서 유지. 각 tower별 interval 설정·사망·move·reconnect가 있으므로 장기 대표 캐시는 제안하지 않는다. 아직 미구현이며 root에 보고했다. 추가로 comparator의 `Tower.logicalId()`는 지연 UUID 생성 경로일 수 있으므로 정렬 생략에 따른 최초 생성시점 및 RNG 경합까지 확인해야 한다(root 지적). 이를 검증하기 전 안전한 무동작 fast path로 단정하지 않는다.

<a id="builder-undead"></a>

### 3. 언데드 빌더 — Undead

판정: **유지: RNG shuffle·빈 탐색·부활 순서**. 개별 조사 완료, 새 성능 미측정. 통합 gate4 통과를 개별 전수실행으로 확대하지 않는다.

복잡도: N=lane 타워, A=동물, G=eligible 염소, C=공간 후보, Q=소환 대기, I=환영, R=합체 재료, P=참가자. 미변경 줄은 기준 HEAD, Legion 선택/회귀는 후속 조사 줄이다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | entity tick·debuff execute·revival 서비스 |
| attack | splash/추가타 shuffle·피해입력 기반 흡혈 |
| target | 공통 목표·랜덤 추가타의 후보 순서 |
| AoE | debuff·사망·광역 피해/치료 |
| stack | 부활·반사·처치/기부 증강 상태 |
| summon / absorb | KING 사망횟수 기반 임시복사·부활 |
| sync | 부활 시 HP/위치·pending 및 round 정리 |
| VFX | Undead palette·revival/area 이벤트 |

#### 경로·주기·비용·경계·수명과 회귀 근거

##### 매틱·공격·타깃·범위

- [src/main/java/kim/biryeong/semiontd/tower/undead/UndeadAnimalTower.java:39–47](../src/main/java/kim/biryeong/semiontd/tower/undead/UndeadAnimalTower.java#L39) cooldown=0이면 AoE debuff를 조회하고 후보가 있을 때만 `scanIntervalTicks`로 예약. 성공 후 I번 감소하고 다음 틱 다시 조회; 대상 없으면 매틱 재시도. `:55–83` radius/targetTeam 필터, attack-damage reduction, T2 tower-damage-taken bonus, magnitude 변화 있을 때만 onChange DEBUFF. O(C) 공간조회/적용이며 실패 cadence를 늦추면 새로운 적 최초 디버프 시점이 달라진다.
- Zombie [src/main/java/kim/biryeong/semiontd/tower/undead/UndeadZombieTower.java:28](../src/main/java/kim/biryeong/semiontd/tower/undead/UndeadZombieTower.java#L28) 기본공격 입력 damageAmount 비례 heal, `:33` kill flat damage boost. Husk [src/main/java/kim/biryeong/semiontd/tower/undead/UndeadHuskTower.java:36,41,53,68](../src/main/java/kim/biryeong/semiontd/tower/undead/UndeadHuskTower.java#L36)은 공격 흡혈, 피격 boost+thorns, 매틱 cooldown 감소; 실제 AoE hitCount>0인 경우에만 thorn heal과 cooldown 부여. Drowned [src/main/java/kim/biryeong/semiontd/tower/undead/UndeadDrownedTower.java:45–77](../src/main/java/kim/biryeong/semiontd/tower/undead/UndeadDrownedTower.java#L45)은 치명타 직전 1HP 보존/일정기간 피해0, 라운드마다 reset. 일반/ignoreReduction 두 damage entrypoint가 같은 guard를 쓴다.
- ranged skeleton [src/main/java/kim/biryeong/semiontd/tower/undead/UndeadRangedSkeletonTower.java:56–65,106–125](../src/main/java/kim/biryeong/semiontd/tower/undead/UndeadRangedSkeletonTower.java#L56): 매 공격 radius-bonus 후보를 직접 entity targetSearchBox로 수집 후 stream→list→ArrayList, world RNG에서 nextLong 1개를 꺼내 새 Random으로 전체 shuffle, 앞 count를 별도 list로 만든다. O(C) shuffle와 O(C) 임시저장. random top-k/reservoir로 바꾸면 같은 seed에서 선택 및 RNG 소비가 달라져 무단 최적화 금지. 검색 box 자체와 별도 거리반경의 교집합을 유지해야 한다.
- melee skeleton [src/main/java/kim/biryeong/semiontd/tower/undead/UndeadMeleeSkeletonTower.java:52–65](../src/main/java/kim/biryeong/semiontd/tower/undead/UndeadMeleeSkeletonTower.java#L52) 공통 splash, 각 기본/secondary 입력피해 흡혈. 이 가족의 legacy 흡혈 입력을 Warlock의 actual-dealt 방식으로 바꾸는 것은 성능 최적화가 아니라 gameplay 변경이므로 수행하지 않았다.
- [src/main/java/kim/biryeong/semiontd/tower/undead/UndeadAugments.java:130–144](../src/main/java/kim/biryeong/semiontd/tower/undead/UndeadAugments.java#L130) bone charge는 actual dealt>0 및 trigger guard → nearest count AoE로 target 목록 snapshot → 각 추가공격. 이 목록은 callback-time 변경과 추가타 trigger suppression을 보존한다.

##### 스택·부활·소환·동기화·VFX

- [src/main/java/kim/biryeong/semiontd/tower/undead/UndeadRangedSkeletonTower.java:80–103](../src/main/java/kim/biryeong/semiontd/tower/undead/UndeadRangedSkeletonTower.java#L80), [src/main/java/kim/biryeong/semiontd/tower/undead/UndeadMeleeSkeletonTower.java:80–123](../src/main/java/kim/biryeong/semiontd/tower/undead/UndeadMeleeSkeletonTower.java#L80): 근처 몬스터/타워 죽음마다 스택, copy는 제외; melee maxHP 증가 시 현재HP 보상 후 state sync. melee death-stack 범위는 `:96–105`의 configured sphere. 업그레이드는 cap된 스택 복사.
- [src/main/java/kim/biryeong/semiontd/tower/undead/UndeadTowerSupport.java:32–52](../src/main/java/kim/biryeong/semiontd/tower/undead/UndeadTowerSupport.java#L32): flat boost와 worldTime 만료 timestamp는 tower-local. 별도 tick expiry sweep 없이 damage getter에서 확인한다.
- [src/main/java/kim/biryeong/semiontd/tower/undead/UndeadAugments.java:91–110](../src/main/java/kim/biryeong/semiontd/tower/undead/UndeadAugments.java#L91): 실제 부족HP 대비 overflow를 계산한 뒤 자기 heal, DONATION 조건일 때 area 타워들을 ArrayList에 모아 health min의 한 아군에 heal. O(C)+O(C) 목록과 선형 min. target identity/tie/area eligibility를 유지하는 단일 최솟값 collector는 낮은 우선순위 후보지만 미구현.
- [src/main/java/kim/biryeong/semiontd/tower/undead/UndeadAugments.java:154–177](../src/main/java/kim/biryeong/semiontd/tower/undead/UndeadAugments.java#L154): KING death threshold에 기존 same-original temporary copies를 O(N) count, 설정 cap 안에서 catalog factory로 복사 생성, logical source 연결·health/damage snapshot·final-defense 전달, `lane.addTower`. 복사는 성장·증강에서 제외. 소환 주기는 death-count 기반이며 매틱이 아니다.
- [src/main/java/kim/biryeong/semiontd/tower/undead/UndeadAugments.java:189–207](../src/main/java/kim/biryeong/semiontd/tower/undead/UndeadAugments.java#L189): 레인당 매틱 N개 pending revive 카운터를 확인하고 만료 시 onRemoved→REVIVED→저장 위치/HP→markRevived→onPlaced. `:214–225` reset/removal clears. [src/main/java/kim/biryeong/semiontd/game/PlayerLane.java:990](../src/main/java/kim/biryeong/semiontd/game/PlayerLane.java#L990) pending revival은 레인 붕괴 판단에 반영된다. 전체 타워 scan O(N)이지만 직접 deadline queue로 바꾸면 판매·reset·최종방어 수명 순서를 함께 다뤄야 한다.
- world 객체를 key로 하는 가족 정적 저장소는 없다. 소환/부활 상태는 typed tower data, 독립 피해 boost는 tower 필드. 기본/추가/DEBUFF/PULSE VFX는 UNDEAD palette. family 자체 forced-target override는 없다.

##### 기존 테스트·결정

- [src/gametest/java/kim/biryeong/semiontd/tower/undead/UndeadTowerRuntimeTest.java:45](../src/gametest/java/kim/biryeong/semiontd/tower/undead/UndeadTowerRuntimeTest.java#L45) debuff, `:175` fixed-damage last stand, `:217` extra-target +2 range, `:257` death stacks +5 range. [src/gametest/java/kim/biryeong/semiontd/tower/undead/UndeadAugmentsGameTest.java:32,88,113,144](../src/gametest/java/kim/biryeong/semiontd/tower/undead/UndeadAugmentsGameTest.java#L32) bone nearest3, actual overflow lowest ally, king cap/원본 판매, actual death 부활20/reset. [src/gametest/java/kim/biryeong/semiontd/tower/undead/UndeadTowerAugmentCombatTest.java:17](../src/gametest/java/kim/biryeong/semiontd/tower/undead/UndeadTowerAugmentCombatTest.java#L17) 최종 수비자 revive 이전 lane break 방지.
- [src/test/java/kim/biryeong/semiontd/tower/undead/UndeadAugmentsTest.java:33,59,89](../src/test/java/kim/biryeong/semiontd/tower/undead/UndeadAugmentsTest.java#L33) charge/ownership/copy/revive/reset 단위 계약.
- 소스 변경 없음. RNG 소모를 보존하지 못하는 shuffle 단축, 스캔 빈도 늦추기, healing 의미 변경은 제외했다.

<a id="builder-animal"></a>

### 4. 동물 빌더 — Animal

판정: **고위험 후보: O(A²N) 중첩 갱신; 틱 캐시 보류**. 개별 조사 완료, 새 성능 미측정. 통합 gate4 통과를 개별 전수실행으로 확대하지 않는다.

복잡도: N=lane 타워, A=동물, G=eligible 염소, C=공간 후보, Q=소환 대기, I=환영, R=합체 재료, P=참가자. 미변경 줄은 기준 HEAD, Legion 선택/회귀는 후속 조사 줄이다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | 각 Animal tick의 전체 재계산 O(A²N) |
| attack | Pig/Wolf/Rabbit/Fox 공격 후 기믹 |
| target | 공통 목표·Fox 후보 재포장 |
| AoE | Pig/Wolf/leader splash·공유 범위 |
| stack | 종류/무리/연계·최대체력 비율 |
| summon / absorb | 별도 소환/희생·흡수 없음 |
| sync | callback 중 배치·제거·업그레이드·재계산 |
| VFX | Animal palette·지원 및 공격 이벤트 |

#### 경로·주기·비용·경계·수명과 회귀 근거

##### 핵심 반복 비용

- [src/main/java/kim/biryeong/semiontd/tower/animal/AnimalStackTower.java:55,61,67](../src/main/java/kim/biryeong/semiontd/tower/animal/AnimalStackTower.java#L55)은 배치/제거/**모든 동물의 매틱**마다 `refreshAnimalStacks(lane)` 실행.
- `:263–276`은 레인 전체에서 모든 동물에 refreshStacks를 먼저 호출한 뒤 다시 전원 refreshLeaderState. 각 동물 countMatchingTowers `:247–260`은 레인 전수 count 및 FRIENDSHIP 조건 전수 anyMatch. **죽은 동료도 count에 포함**하며 자신만 제외/owner 동일/family 동일이다.
- `:279–326` refreshLeaderState는 owner의 alive leaders를 매번 전수 수집→distinct→정렬→toList, other living leader 전수 anyMatch, active same-family leader 전수 findFirst. union은 다른 leader 종류 목록이고 findActiveLeader는 first lane encounter, alive/maxStacks/radius 조건. aura center `:334`는 live entity center 우선, 없으면 grid center다.
- 따라서 A개 동물의 매틱 비용은 O(A²·N + A²·L log L), L은 leader 종류 수이며 현 family는 소수 고정. A≈N이면 O(N³) 스캔이 된다. 최대치만 비교해 세 번째 항을 과장하지 않도록 구분한다. 각 refreshLeaderState의 리스트·distinct set·stream과 center Vec3가 반복 생성된다.
- 단순 frame당1회 cache는 원래 각 동물 tick 사이의 사망/이동/소환/지원/HP 변화를 보지 못한다. HP는 [src/main/java/kim/biryeong/semiontd/tower/animal/PigTower.java:81–103](../src/main/java/kim/biryeong/semiontd/tower/animal/PigTower.java#L81), union 변경은 [src/main/java/kim/biryeong/semiontd/tower/animal/AnimalStackTower.java:297](../src/main/java/kim/biryeong/semiontd/tower/animal/AnimalStackTower.java#L297)에서 실제 수정된다. 이 결과를 통합하는 것은 단순 계산 이동이 아니므로 이번 구현에서 보류했다.

##### 공격·타깃·범위·상태

- Pig [src/main/java/kim/biryeong/semiontd/tower/animal/PigTower.java:36–52,74–89](../src/main/java/kim/biryeong/semiontd/tower/animal/PigTower.java#L36): 스택 health/damage, cap damage reduction, T3/leader max stacks splash; 스택 증가 HP delta만 회복, 감소 cap. Wolf [src/main/java/kim/biryeong/semiontd/tower/animal/WolfTower.java:34–48,70–72](../src/main/java/kim/biryeong/semiontd/tower/animal/WolfTower.java#L34) stack damage/interval, T2+ splash. Rabbit [src/main/java/kim/biryeong/semiontd/tower/animal/RabbitTower.java:30–45,66–76](../src/main/java/kim/biryeong/semiontd/tower/animal/RabbitTower.java#L30) damage/interval/range 및 T3+maxstack 살아있는 동일 대상 secondary 1회. AoE는 공유 TowerAreaDamage, 추가타는 공통 result/kill/VFX 사용.
- Fox [src/main/java/kim/biryeong/semiontd/tower/animal/FoxTower.java:36–51](../src/main/java/kim/biryeong/semiontd/tower/animal/FoxTower.java#L36)은 후보 C개를 FoxTargetCandidate 레코드 C개+list로 변환, 거리 두 번 계산, [src/main/java/kim/biryeong/semiontd/tower/animal/FoxTargetingPolicy.java:15–25](../src/main/java/kim/biryeong/semiontd/tower/animal/FoxTargetingPolicy.java#L15)에서 maxHP>0, 공격범위, 처형threshold를 만족하는 healthRatio min → distance min 선택. 완전 동률은 encounter 유지. O(C)이나 candidate wrapper allocation을 없애는 accessor 방식은 안전 후보가 될 수 있으며 아직 미구현.
- Fox [src/main/java/kim/biryeong/semiontd/tower/animal/FoxTower.java:66–70,88–90](../src/main/java/kim/biryeong/semiontd/tower/animal/FoxTower.java#L66)는 근처 죽음의 영구 attack bonus cap, 업그레이드 copy. 가족 전반의 aura/stack은 현재 레인에서 계산되며 tower-local fields만 저장.
- [src/main/java/kim/biryeong/semiontd/tower/animal/AnimalStackTower.java:158–189](../src/main/java/kim/biryeong/semiontd/tower/animal/AnimalStackTower.java#L158) actual-damage/trigger guard, LEADER splash, PACK이면 owner+family 전체 list를 만들어 첫 타워의 PACK_HITS 사용; 임계공격이면 lane encounter 순으로 살아있고 entity/attack-range/target/sleep 조건이 맞는 최대 allies(기본3)만 추가공격. 단순 first alive로 counter를 바꾸면 죽은 첫 타워를 포함하던 카운터 소유가 달라진다.
- wave/reset PACK_HITS clear `:146–154`, upgrade leader requirement `:194–198`는 realStacks와 다른 living leader 존재를 사용. FRIENDSHIP의 가상 +1은 업그레이드 realStacks를 채우지 않는다.
- 소환/흡수, player/world별 별도 정적 state map, family forced-target override는 없다. 기본공격/secondary/splash/leader splash는 ANIMAL palette 공통 경로.

##### 기존 테스트·결정

- [src/gametest/java/kim/biryeong/semiontd/tower/animal/AnimalTowerRuntimeTest.java:40,115](../src/gametest/java/kim/biryeong/semiontd/tower/animal/AnimalTowerRuntimeTest.java#L40) Fox target/kill stack, `:160,206,282` pig/wolf/rabbit, `:365` leader upgrade 조건, `:453` range/owner/stacks/death/sale aura, `:537` exact wolf/rabbit/fox bonuses.
- [src/gametest/java/kim/biryeong/semiontd/tower/animal/JobAdvAnimalResonanceGameTest.java:42,66](../src/gametest/java/kim/biryeong/semiontd/tower/animal/JobAdvAnimalResonanceGameTest.java#L42): pack fifth hit 3 allies/no recharge, leader splash 정확한 radius/damage.
- [src/test/java/kim/biryeong/semiontd/tower/animal/FoxTargetingPolicyTest.java:11,26,36,47](../src/test/java/kim/biryeong/semiontd/tower/animal/FoxTargetingPolicyTest.java#L11) ratio/range/tie/cap, [src/test/java/kim/biryeong/semiontd/tower/animal/AnimalAugmentsTest.java](../src/test/java/kim/biryeong/semiontd/tower/animal/AnimalAugmentsTest.java), [src/test/java/kim/biryeong/semiontd/tower/animal/AnimalTowerBalanceConfigTest.java:151,170](../src/test/java/kim/biryeong/semiontd/tower/animal/AnimalTowerBalanceConfigTest.java#L151) leader catalog/default merge.
- 가장 큰 알고리즘 후보지만 동작변화 위험 때문에 미수정. 단일 동물마다 전체 family를 재계산하는 이유와 중간 상태 관측을 먼저 서버 회귀로 명문화해야 한다.

<a id="builder-warlock"></a>

### 5. 흑마법사 — Warlock

판정: **유지: 희생은 이미 min; passive 캐시 수명 검증 필요**. 개별 조사 완료, 새 성능 미측정. 통합 gate4 통과를 개별 전수실행으로 확대하지 않는다.

복잡도: N=lane 타워, A=동물, G=eligible 염소, C=공간 후보, Q=소환 대기, I=환영, R=합체 재료, P=참가자. 미변경 줄은 기준 HEAD, Legion 선택/회귀는 후속 조사 줄이다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | passive 능력치 조회·각성 상태 tick |
| attack | 기본 공격과 피격 시 희생 |
| target | 희생 대상은 기존 min·소유/거리 조건 |
| AoE | 각성/회복 등 실제 요청 경로 |
| stack | 각성 단계·희생 누적·영구/일시 효과 |
| summon / absorb | kill 성공 후 희생 확정·실패 롤백 |
| sync | owner/lane·각성 reset·죽음/제거 |
| VFX | 각성 전용 VFX 및 공통 공격 |

#### 경로·주기·비용·경계·수명과 회귀 근거

##### 매틱·공격·희생·타깃

- [src/main/java/kim/biryeong/semiontd/tower/warlock/WarlockTower.java:182–185](../src/main/java/kim/biryeong/semiontd/tower/warlock/WarlockTower.java#L182)는 currentLane 갱신 → base tick → awakening tick. 별도 매틱 전체 타깃 scan은 없지만 `:66–79` maxHP/damage getter의 passive 조회가 [src/main/java/kim/biryeong/semiontd/tower/warlock/WarlockSacrificeController.java:93–102](../src/main/java/kim/biryeong/semiontd/tower/warlock/WarlockSacrificeController.java#L93)의 O(N) count를 매번 수행한다. source 자신 제외, alive/owner/path matching sacrifice line만 count. lifetime cache를 하려면 모든 사망/판매/교체/이동/추가와 config 변경을 다뤄야 한다.
- [src/main/java/kim/biryeong/semiontd/tower/warlock/WarlockTower.java:114–139](../src/main/java/kim/biryeong/semiontd/tower/warlock/WarlockTower.java#L114) 피격 시 base는 HP<=0, specialized는 threshold 이하에서 absorbNearest; ranged low aggro, melee reverse aggro. [src/main/java/kim/biryeong/semiontd/tower/warlock/WarlockSacrificeController.java:35–38](../src/main/java/kim/biryeong/semiontd/tower/warlock/WarlockSacrificeController.java#L35)는 이미 linear min이다. `:134–139` priority→거리→x/y/z tie 순서, 모두 같으면 lane encounter. `:109–124` self/augment/temp/dead/core/owner/path/range 필터. radius<=0의 의미는 `WarlockRules.SacrificeRule` 계약 및 테스트상 무제한이므로 다른 family radius와 동일 취급 금지.
- 희생 commit 순서는 [src/main/java/kim/biryeong/semiontd/tower/warlock/WarlockSacrificeController.java:43–61](../src/main/java/kim/biryeong/semiontd/tower/warlock/WarlockSacrificeController.java#L43): target snapshot/center/gain → lane.killTower 성공 → previous maxHP → state commit → stats/heal → sacrifice VFX → augment effects. kill 실패 시 영구 성장 없음. 재정렬/다른 snapshot 시점/콜백 이동은 의미 변경.
- 공격 [src/main/java/kim/biryeong/semiontd/tower/warlock/WarlockTower.java:143](../src/main/java/kim/biryeong/semiontd/tower/warlock/WarlockTower.java#L143) → [src/main/java/kim/biryeong/semiontd/tower/warlock/WarlockCombat.java:65–78](../src/main/java/kim/biryeong/semiontd/tower/warlock/WarlockCombat.java#L65)은 splash를 먼저 실행하고 primary 실제피해 흡혈. splash `:88–116`은 resolvedOutgoing 기반 damage, target별 실제 dealt 기반 heal과 ignite. DamageLifeSteal 적용은 `:40–46` 40 기준 efficiency·기존 ratio×10. 단순 attempted 피해로 치환하면 안 된다.
- [src/main/java/kim/biryeong/semiontd/tower/warlock/WarlockAugments.java:25–55](../src/main/java/kim/biryeong/semiontd/tower/warlock/WarlockAugments.java#L25): 성공 희생 시 partnership owner의 living other cores O(N) 성장공유, testament은 entity 후보 O(C) nearest min, explosive은 공유 nearest-limited area. 성장/카운터 공유 범위가 각각 다르다. 가족 custom forced/ordinary target override는 없고 기본 entity goal을 사용한다.

##### 각성·상태·VFX·없는 기능

- [src/main/java/kim/biryeong/semiontd/tower/warlock/WarlockAwakeningController.java:24–45](../src/main/java/kim/biryeong/semiontd/tower/warlock/WarlockAwakeningController.java#L24): 진행도 snapshot, threshold, 실제 entity HP 역동기화, 마지막 생존자 여부(또는 증강 우회), once/round state. sacrifice 후 갱신된 HP로 판정한다. [src/main/java/kim/biryeong/semiontd/tower/warlock/WarlockTower.java:357–366](../src/main/java/kim/biryeong/semiontd/tower/warlock/WarlockTower.java#L357) 마지막 생존 판정은 membership contains O(N)+alive noneMatch O(N).
- [src/main/java/kim/biryeong/semiontd/tower/warlock/WarlockAwakeningController.java:75–87](../src/main/java/kim/biryeong/semiontd/tower/warlock/WarlockAwakeningController.java#L75)은 매틱 heal eligibility 확인 후 configured regeneration interval; `:90–114`은 각성 후 매틱 entity 조회하지만 VFX는 charge 4/8/12틱, burst16틱, 이후 aura8틱/스파크20틱. 사망/제거 entity는 조기 반환, round reset glow clear.
- [src/main/java/kim/biryeong/semiontd/tower/warlock/WarlockTower.java:205](../src/main/java/kim/biryeong/semiontd/tower/warlock/WarlockTower.java#L205)은 mutable WarlockState 독립 copy, `:189` round-only reset, `:58` lane reference clear. 영구 성장/라운드 성장/각성은 별개 lifetime. [src/main/java/kim/biryeong/semiontd/tower/warlock/WarlockSacrificeTower.java:37,43,49](../src/main/java/kim/biryeong/semiontd/tower/warlock/WarlockSacrificeTower.java#L37) 배치/제거/사망마다 모든 cores stats refresh O(N), core 각각 passive scan으로 mutation event에 O(WN) 발생 가능.
- [src/main/java/kim/biryeong/semiontd/job/JobWarlockLifecycle.java:15](../src/main/java/kim/biryeong/semiontd/job/JobWarlockLifecycle.java#L15) reward kill에서 awakening progress 단일 증가, `:33` clear. `WarlockAwakeningProgress`는 player UUID keyed progression이며 world/entity를 담지 않는다. 시작/제거/close/reuse 테스트 존재.
- 별도 자동 소환 큐는 없다. 희생탑은 플레이어 배치 타워이며 흡수 시 죽이고 성장으로 전환한다. WARLOCK palette와 sacrifice/charge/burst/aura 특수 VFX를 실제 사용한다.

##### 기존 테스트·결정

- [src/test/java/kim/biryeong/semiontd/tower/warlock/WarlockSacrificeControllerTest.java:28,70,86,97,108](../src/test/java/kim/biryeong/semiontd/tower/warlock/WarlockSacrificeControllerTest.java#L28) eligibility/path/passive/augment 제외/tie; [src/test/java/kim/biryeong/semiontd/tower/warlock/WarlockSacrificeTest.java:114,123](../src/test/java/kim/biryeong/semiontd/tower/warlock/WarlockSacrificeTest.java#L114) 무제한 radius/failed kill no mutation; [src/test/java/kim/biryeong/semiontd/tower/warlock/WarlockStateTest.java:48,65,83,97](../src/test/java/kim/biryeong/semiontd/tower/warlock/WarlockStateTest.java#L48) deep state copy/path progression/각성 once/reset.
- [src/gametest/java/kim/biryeong/semiontd/tower/warlock/WarlockTowerRuntimeTest.java:177,249,367,476,481,536,599,678,726,851](../src/gametest/java/kim/biryeong/semiontd/tower/warlock/WarlockTowerRuntimeTest.java#L177) true damage sacrifice, ranged/melee priority+gain, cross-family sacrifice vs passive 분리, invalid no growth, awakening/reset/post-sacrifice HP, death debuff.
- [src/gametest/java/kim/biryeong/semiontd/tower/warlock/WarlockTowerAugmentCombatTest.java:79,126,151,168,219](../src/gametest/java/kim/biryeong/semiontd/tower/warlock/WarlockTowerAugmentCombatTest.java#L79) VFX cadence/reset/death, absorption single/share-only, augmented awakening, 실제 hit lifesteal/splash 한도.
- [src/gametest/java/kim/biryeong/semiontd/tower/warlock/WarlockTowerIntegrationTest.java:26](../src/gametest/java/kim/biryeong/semiontd/tower/warlock/WarlockTowerIntegrationTest.java#L26) match start/elimination/close/player reuse. 다수 config/combat/stats/description unit suite도 존재.
- 이미 min이므로 sort 최적화 대상 아님. 현재 cached state 도입은 무효화 비용/동작 위험 대비 근거 부족하여 유지.

<a id="builder-legion"></a>

### 6. 무리 빌더 — Legion

판정: **변경: 염소 중첩 선택을 안정 순위 스캔으로**. 선정 변경 경로 최종 gate4 통과. 새 성능 미측정.

복잡도: N=lane 타워, A=동물, G=eligible 염소, C=공간 후보, Q=소환 대기, I=환영, R=합체 재료, P=참가자. 미변경 줄은 기준 HEAD, Legion 선택/회귀는 후속 조사 줄이다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | 종별 tick·독 상태·소환 큐·clone 갱신 |
| attack | Bee·Parrot·Slime 및 실제 피해 증강 |
| target | 공통 목표·공유 목표·염소 제공자 순위 |
| AoE | 염소 buff·독/범위·clone splash |
| stack | 염소 상한·동률·source 및 세대/재료 스택 |
| summon / absorb | 분산 소환·합체·원본별 재료·치명타 흡수 |
| sync | 원본 제거·만료·round/close·runtime sync |
| VFX | Legion palette·buff/독/소환 효과 |

#### 경로·주기·비용·경계·수명과 회귀 근거

##### 매틱·공격·스택·지원

- [src/main/java/kim/biryeong/semiontd/tower/legion/IllusionSummonerTower.java:41–60](../src/main/java/kim/biryeong/semiontd/tower/legion/IllusionSummonerTower.java#L41) wave-start cleanup→profile→offset→enqueue; 각 summoner tick에서 base+clones. clone 생성은 `:128–155` runtime catalog factory/trait/shared attack target 연결, lane 등록 towers 목록에는 추가하지 않는 entity clone이다.
- `:256–290`은 clone list 역순으로 entity validity/death 체크, runtime HP/위치 sync→runtime.tick→entity.syncTowerState→age expiry. 기본 O(I), 중간 삭제는 ArrayList shift로 최악 O(I²), 역순 순서는 능력 호출 순서이기도 하다. clone이 Slime/Parrot/Goat 등 catalog runtime이면 그 가족 동작을 수행한다.
- Chicken/Penguin [src/main/java/kim/biryeong/semiontd/tower/legion/LegionChickenTower.java:27](../src/main/java/kim/biryeong/semiontd/tower/legion/LegionChickenTower.java#L27), [src/main/java/kim/biryeong/semiontd/tower/legion/LegionPenguinTower.java:27](../src/main/java/kim/biryeong/semiontd/tower/legion/LegionPenguinTower.java#L27)은 공격마다 [src/main/java/kim/biryeong/semiontd/tower/legion/LegionTowerAbilities.java:16–29](../src/main/java/kim/biryeong/semiontd/tower/legion/LegionTowerAbilities.java#L16) shared basic-attack splash. Slime [src/main/java/kim/biryeong/semiontd/tower/legion/LegionSlimeTower.java:29–44](../src/main/java/kim/biryeong/semiontd/tower/legion/LegionSlimeTower.java#L29) configured regen counter와 HP change sync. Parrot [src/main/java/kim/biryeong/semiontd/tower/legion/LegionParrotTower.java:46–56](../src/main/java/kim/biryeong/semiontd/tower/legion/LegionParrotTower.java#L46) 공격 stack cap/직접 entity stat sync, round reset0.
- Bee [src/main/java/kim/biryeong/semiontd/tower/legion/BeeTower.java:40,71–81](../src/main/java/kim/biryeong/semiontd/tower/legion/BeeTower.java#L40)는 매 벌 tick에서 own owner의 same-family 타워 수 O(N) count(사망 필터 없음), 제거 `:117`은 다른 벌도 갱신. 벌 B개면 O(BN) per tick; Animal과 달리 매 벌이 전원 stack refresh를 실행하지 않는다.
- Bee 공격 `:46–62`은 target poison marker 및 source별 BeePoison 전달. [src/main/java/kim/biryeong/semiontd/entity/monster/SemionMonsterEntity.java:951–981](../src/main/java/kim/biryeong/semiontd/entity/monster/SemionMonsterEntity.java#L951)는 몬스터 틱마다 source별 map entry를 tick, State/TickResult/Optional/새 BeePoisonState 할당, 만료/사망 시 제거. 피해는 `:986–1005` actual dealt/stat/kill/source attribution. 단순 TimedEffectSet 독 하나로 병합할 수 없다.
- Goat [src/main/java/kim/biryeong/semiontd/tower/legion/LegionGoatTower.java:55–103](../src/main/java/kim/biryeong/semiontd/tower/legion/LegionGoatTower.java#L55)은 active support cooldown 계약을 사용, REGISTERED_AND_CLONES radius area와 가족/owner/team/lane/alive filters, 안정 stack slot으로 damage/reduction 두 효과, clone은 별도 source 배열. 성공 effect refresh가 있어야 active cooldown 설정; 효과/후보 없음은 다음 틱 재시도. 스택 source는 제공자 UUID가 아니라 정렬 순위 슬롯 1~3이며 제거/사망 시 남은 제공자가 재색인된다.

##### 승인된 최적화

- 이전 `LegionGoatTower.stackIndexFor` (`:117`)는 lane 전체 goat filter→canBuff→stable sorted(original x/y/z/type ID)→limit(maxStacks clamp1..3)→toList→this identity 검색. AoE filter와 실제 apply callback에서 각각 호출되어 대상당 보통 두 번 O(N+G log G), O(G) sort buffer + 작은 결과 list를 만들었다.
- 변경 `:117–137`: eligible goat를 순회해 this보다 comparator상 먼저 오는 항목 수와 this 첫 encounter 여부만 계산. comparator 동률이면 this 첫 등장 이전 항목만 count. this 중복참조는 첫 index 의미를 유지. 최대치 clamp는 기존 `maxStacks` 그대로. 비용 O(N), O(1) 추가 상태(기존 lane iterator/OptionalInt 수준); cross-target/per-tick cache 없음.
- effect request filter/callback의 두 호출은 보존하므로 양 시점 사이 health/position/membership/config 변화를 각각 다시 읽는다. 원본 순서·거리 geometry·owner/team/lane·clone 분기·timed effect source 및 magnitude/duration은 변경하지 않았다.
- 변경 파일: [src/main/java/kim/biryeong/semiontd/tower/legion/LegionGoatTower.java](../src/main/java/kim/biryeong/semiontd/tower/legion/LegionGoatTower.java), 신규 [src/test/java/kim/biryeong/semiontd/tower/legion/LegionGoatStackSelectionTest.java](../src/test/java/kim/biryeong/semiontd/tower/legion/LegionGoatStackSelectionTest.java). `git diff --check -- <두 파일>` 실행 통과. Gradle/JUnit/GameTest 실행은 root 통합 검증 대기다. 실제 MSPT/TPS/할당량 측정 결과는 없다.
- 신규 테스트 6개: 기존 stable sorted oracle와 크기0/1/2/3/4/8/32/128, 허용 max 0/1/2/3/8, seeded random8 표본; exact tie/중복 identity; original sort 좌표 vs current 거리; sphere 경계; health/movement/removal/replacement/reload; owner/team/lane/self-health/null target. 음수 max=-3은 `TowerBalanceRuntime.apply`의 기존 검증 계약상 IllegalArgumentException으로 거절되므로 별도 assertThrows 테스트로 입력을 분리하고 이전 current config identity 보존까지 확인한다. 원본 목록 미변경도 확인한다. 중복 항목만 테스트 reflection으로 넣으며 실제 PlayerLane.addTower는 중복등록을 막는다.

##### 소환·합체·흡수·월드 수명 위험

- [src/main/java/kim/biryeong/semiontd/tower/legion/IllusionCloneSpawnQueue.java:17–38](../src/main/java/kim/biryeong/semiontd/tower/legion/IllusionCloneSpawnQueue.java#L17)는 global TreeMap<dueTick,ArrayDeque>; enqueue O(K log D), due bucket FIFO. `:42–70` configured maxSpawnsPerTick까지 유효 spawn 수행, invalid item은 quota를 쓰지 않으므로 큰 invalid backlog는 한 틱에 많이 drain 가능. `:157` valid는 owner/source lane membership O(N); child는 wave identity+expiry+original logicalId membership ([src/main/java/kim/biryeong/semiontd/tower/legion/LegionAugments.java:274](../src/main/java/kim/biryeong/semiontd/tower/legion/LegionAugments.java#L274)) 확인.
- 취소 [src/main/java/kim/biryeong/semiontd/tower/legion/IllusionCloneSpawnQueue.java:73,95](../src/main/java/kim/biryeong/semiontd/tower/legion/IllusionCloneSpawnQueue.java#L73)는 pending Q개 순회. 이동/round reset/removal ([src/main/java/kim/biryeong/semiontd/tower/legion/IllusionSummonerTower.java:63,69,77,321](../src/main/java/kim/biryeong/semiontd/tower/legion/IllusionSummonerTower.java#L63)), manager clear ([src/main/java/kim/biryeong/semiontd/game/SemionGameManager.java:1980,2393](../src/main/java/kim/biryeong/semiontd/game/SemionGameManager.java#L1980))를 통해 references 정리. 정적 큐가 lane/source/owner를 보유하므로 누락된 취소는 world 수명 위험, 현재 경로에서는 보존한다.
- [src/main/java/kim/biryeong/semiontd/tower/legion/LegionGlobalIllusionTower.java:28–48](../src/main/java/kim/biryeong/semiontd/tower/legion/LegionGlobalIllusionTower.java#L28)는 wave 자동소환 없음; 본체 사망 시 living owned towers의 List.copyOf snapshot을 복제 예약, 본체 죽은 후에도 clone tick을 계속한다. 본체 alive guard를 공통화하면 동작이 깨진다.
- [src/main/java/kim/biryeong/semiontd/tower/legion/LegionAugments.java:38,42](../src/main/java/kim/biryeong/semiontd/tower/legion/LegionAugments.java#L38)은 PlayerLane-keyed WAVES; wave init은 investment+logicalId max로 factory original 선정. `:67–91` register는 sameType/owner 첫 existing clone으로 합체, material마다 origin/generation/expiry 보존. `:107–154` actual-hit factory generation/reservation과 charisma. `:157–174` lethal guard에서 자신 origin material이 있는 첫 clone을 흡수해 once-round 생존.
- `:178–204` 매틱 List.copyOf(clones), 재료 이전합/expiry 제거/남은합·rebuild, runtime sync. O(I+R) 정상, clone.remove의 선형 삭제가 다수면 O(I²). `:207–245` 원본 제거 시 해당 재료만 제거·남은 body/비율 유지, round/close clear clone discard와 child cancel. snapshot 제거로 callback mutation 관측이 바뀔 위험이 있어 미수정.
- [src/main/java/kim/biryeong/semiontd/job/JobLaneLifecycle.java:32,54,76](../src/main/java/kim/biryeong/semiontd/job/JobLaneLifecycle.java#L32)가 reset/wave/teardown, [src/main/java/kim/biryeong/semiontd/game/PlayerLane.java:483](../src/main/java/kim/biryeong/semiontd/game/PlayerLane.java#L483) source removal을 연결한다. 독립 player keyed progression은 없지만 lane map+global queue+entity-backed poisons가 각각 다른 수명이다.

##### 기존 테스트

- [src/gametest/java/kim/biryeong/semiontd/tower/legion/LegionTowerRuntimeTest.java:62](../src/gametest/java/kim/biryeong/semiontd/tower/legion/LegionTowerRuntimeTest.java#L62) poison, `:152` shared target, `:209` wave clone count, `:364` reset/removal pending cancel, `:420` game close entity/queue cleanup, `:469` >10 spawn spread, `:506` trait inheritance, `:570` per-tick quota, `:686` Slime clone regen, `:726` global death Parrot runtime, `:823` prepare pulse/dialog, `:893–984` body+clone buff/3-stack cap/provider death reindex.
- [src/gametest/java/kim/biryeong/semiontd/tower/legion/LegionAugmentGameTest.java:36,41,91,134,171](../src/gametest/java/kim/biryeong/semiontd/tower/legion/LegionAugmentGameTest.java#L36) factory generations/shared quota, merged material generation/splash, lethal/charisma, source removal preserves other material.
- [src/test/java/kim/biryeong/semiontd/tower/legion/BeeStingPolicyTest.java:10,24](../src/test/java/kim/biryeong/semiontd/tower/legion/BeeStingPolicyTest.java#L10) stack/duration/tick, [src/test/java/kim/biryeong/semiontd/tower/legion/BeeTowerCatalogTest.java:27](../src/test/java/kim/biryeong/semiontd/tower/legion/BeeTowerCatalogTest.java#L27), [src/test/java/kim/biryeong/semiontd/tower/legion/IllusionAugmentExclusionTest.java:24](../src/test/java/kim/biryeong/semiontd/tower/legion/IllusionAugmentExclusionTest.java#L24) factory 전 augment tower 거부.

<a id="builder-resonance"></a>

### 7. 무블룸 빌더 — Resonance

판정: **후보: cycle 미도래 정렬 생략; lazy UUID 계약 미해결**. 개별 조사 완료, 새 성능 미측정. 통합 gate4 통과를 개별 전수실행으로 확대하지 않는다.

복잡도: N=lane 타워, R=가족 타워, M=lane 몬스터, C=broadphase 후보, K=피해 대상, S=sculk 셀, D=지뢰, B=화상, A=흡수. 줄은 기준 HEAD이며 OceanWaterTower 208행 이후는 후속 수정에서 +6행이다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | lane 매틱 cycle 정렬·due 부작용 |
| attack | aspect별 추가 피해·harmony 조건 |
| target | 기본 타깃 공통; friend 선택은 별도 |
| AoE | wave/frost/amplify 독립 AoE |
| stack | link·charge·cycle 번호·상위 슬롯 |
| summon / absorb | 가족 자체 소환·흡수 없음 |
| sync | wave link snapshot·HP 비율·복사 |
| VFX | secondary/AreaVfxSpec 공유 소비 |

#### 경로·주기·비용·경계·수명과 회귀 근거

##### 호출과 비용

- 매틱: 자체 tick override 없음. [src/main/java/kim/biryeong/semiontd/tower/resonance/ResonanceService.java:33-48](../src/main/java/kim/biryeong/semiontd/tower/resonance/ResonanceService.java#L33)가 lane cycle 증강 유무를 확인하고, 모든 생존 Resonance를 `resonanceLevel desc, logicalId asc`로 정렬해 list를 만든다. O(N + R log R), O(R) 임시 메모리. production 호출은 [src/main/java/kim/biryeong/semiontd/game/PlayerLane.java:626](../src/main/java/kim/biryeong/semiontd/game/PlayerLane.java#L626) 매틱이다.
- 주기/스택: [src/main/java/kim/biryeong/semiontd/tower/resonance/ResonanceTower.java:92-111](../src/main/java/kim/biryeong/semiontd/tower/resonance/ResonanceTower.java#L92)은 waveStart, lastCycle, harmonyCharge를 보유한다. `cycleDue:98-104`가 기본 120틱 단위 번호를 비교하고 **lastCycle을 갱신**한다. 상위 maxTowers를 초과한 타워와 entity가 없는 타워도 due/count를 소비하는 원래 루프 계약이 있다. `pulseCharge:368-375`, `harmonyCharge:181-195`는 기본/실제 명중 조건이 다르다.
- 링크/오라: [src/main/java/kim/biryeong/semiontd/tower/resonance/ResonanceTowerLinkController.java:17-46](../src/main/java/kim/biryeong/semiontd/tower/resonance/ResonanceTowerLinkController.java#L17)은 생존 roster list, 링크 HashMap, 타워별 link list, owner별 friend HashMap을 만든다. :49-60은 입력 encounter order의 limit(maxLinks), 같은 owner/team/lane, 다른 aspect, Chebyshev 거리 `<= range`를 사용한다. :68-89는 타워별 두 번의 전체 aura max 스캔. O(R²), 링크 list 저장 O(R·min(R,L)). friend는 :140-149에서 links desc, distance asc, logicalId asc 및 linked.contains로 선택한다. 그러나 production 호출은 [src/main/java/kim/biryeong/semiontd/job/JobLaneLifecycle.java:62](../src/main/java/kim/biryeong/semiontd/job/JobLaneLifecycle.java#L62) -> `ResonanceService.captureWaveStart:22-30`뿐이므로 매틱 링크 재계산은 없다.
- 공격/대상: 기본 target 선택 hook 없음(공통 정책). [src/main/java/kim/biryeong/semiontd/tower/resonance/ResonanceTower.java:212-221](../src/main/java/kim/biryeong/semiontd/tower/resonance/ResonanceTower.java#L212)은 aspect별 공격 후 효과. focus :255-265 단일 magic; wave :269-286 splash+주기 pulse로 최대 두 독립 AoE; frost :289-302 debuff+주기 AoE; amplify :305-326 타워 AoE. harmony :181-195는 dealt>0 및 trigger suppression 조건, maxTargets nearest 제한.
- 범위효과/VFX: [src/main/java/kim/biryeong/semiontd/tower/resonance/ResonanceTower.java:378-432](../src/main/java/kim/biryeong/semiontd/tower/resonance/ResonanceTower.java#L378)는 shared request, callback별 damage/kill/debuff를 사용한다. 요청당 O(C)+공통 선택 비용 및 요청/lambda/제외 set 생성. 두 pulse를 합치면 첫 damage 이후 사망/kill hook/효과 적용 순서가 바뀐다. secondary 및 AreaVfxSpec을 기존 서비스로 전달한다.
- 상태동기화: :76-85는 링크 변화에 따른 max health 비율을 보존하고 실제 최대체력 변화 때만 onStateChanged. :153-164의 복사로 wave snapshot/cycle deadline/charge 유지. display :141-146의 top list 정렬은 UI 호출 비용 O(N+R log R), 매틱으로 분류하지 않았다.
- 소환/흡수: 이 가족 자체 소환·흡수 기능 없음. Java entity reference를 장기 저장하지 않고 friend는 UUID. 전역 mutable 가족 state 없음.

##### 후보/제약

1. **미수정, 우선 후보:** side-effect-free cycle due 검사로 모두 not-due일 때만 원래 정렬을 생략한다. 하나라도 due면 기존 정렬/loop 그대로 수행해야 한다. due를 먼저 소비하거나 상위 K만 due 평가하면 deadline/count/없는 entity의 슬롯 소비가 달라진다. no-due tick O(N+R), 추가 캐시 수명 없음. 다만 [src/main/java/kim/biryeong/semiontd/tower/Tower.java:312-318](../src/main/java/kim/biryeong/semiontd/tower/Tower.java#L312)의 logicalId는 최초 조회 시 UUID.randomUUID를 만들며, 원래 sort comparator는 동률에서 이 생성을 유발한다. 정렬을 건너뛰면 UUID 최초 생성 시점/순서가 달라질 수 있으므로 이것까지 동등성을 해결하기 전에는 안전 확정 후보가 아니다. 아직 별도 승인/구현 없음.
2. wave-start link/aura 스캔 합치기·owner grouping은 낮은 호출 빈도이고 원래 link encounter limit, 친구 선정 후 aura 계산, logicalId의 lazy RNG 생성 순서가 중요하다. 현재 단계 보류.

기존 테스트: [src/test/java/kim/biryeong/semiontd/tower/resonance/ResonanceAugmentsTest.java:62](../src/test/java/kim/biryeong/semiontd/tower/resonance/ResonanceAugmentsTest.java#L62) cycle 경계/동일 tick/upgrade deadline 직접 확인, [src/test/java/kim/biryeong/semiontd/tower/resonance/ResonanceTowerLinkControllerTest.java:36,54](../src/test/java/kim/biryeong/semiontd/tower/resonance/ResonanceTowerLinkControllerTest.java#L36) friend tie/owner/team/lane/exclusion, [src/test/java/kim/biryeong/semiontd/tower/resonance/ResonanceTowerTest.java:133,147,179](../src/test/java/kim/biryeong/semiontd/tower/resonance/ResonanceTowerTest.java#L133) wave 교체/오라 제거/업그레이드 snapshot. [src/gametest/java/kim/biryeong/semiontd/tower/resonance/ResonanceTowerRuntimeTest.java:262,335](../src/gametest/java/kim/biryeong/semiontd/tower/resonance/ResonanceTowerRuntimeTest.java#L262) debuff/aura 및 magic/no ignite. 테스트 실행 안 함.

<a id="builder-illager"></a>

### 8. 우민 빌더 — Illager

판정: **유지: forced target·mark 만료·volley snapshot**. 개별 조사 완료, 새 성능 미측정. 통합 gate4 통과를 개별 전수실행으로 확대하지 않는다.

복잡도: N=lane 타워, R=가족 타워, M=lane 몬스터, C=broadphase 후보, K=피해 대상, S=sculk 셀, D=지뢰, B=화상, A=흡수. 줄은 기준 HEAD이며 OceanWaterTower 208행 이후는 후속 수정에서 +6행이다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | raid 효과 refresh·volley 처리·bossbar |
| attack | raid/omen/mark·예약 volley |
| target | forced mark 선택이 cache보다 선행 |
| AoE | 추가 피해·표식/효과 영역 |
| stack | raid·omen·mark의 서로 다른 만료 |
| summon / absorb | 가족 소환·흡수 없음 |
| sync | owner 상태·wave/close·UI 10회 재동기화 |
| VFX | 공통 공격/secondary 및 family palette |

#### 경로·주기·비용·경계·수명과 회귀 근거

##### 호출과 비용

- 매틱: [src/main/java/kim/biryeong/semiontd/tower/illager/IllagerTower.java:156-159,232-255](../src/main/java/kim/biryeong/semiontd/tower/illager/IllagerTower.java#L156)는 active raid/health/entity 검사를 거쳐 3개 sourced timed-effect refresh. owner state 조회 여러 번과 entity 조회 1회, O(1) 가족 로직 + 공통 timed effect contribution 집계 비용. duration을 주기적으로 갱신하는 원래 계약이므로 cadence 임의 변경 금지.
- 대상: :73-100 forced marked 대상 우선, 이어 low-health min/high-health max/income priority max 각각 O(C), 정렬 없음. :266-275 forcedMarkedTarget는 표식 조건 및 priority max. 공통 goal :172,222-227이 forced target을 **매틱** 조회하기 때문에 기본 5틱 캐시가 이 scan을 생략하지 않는다. forced 없음+일반 재선택 tick에는 goal :238-254의 새 후보와 IllagerTower :74의 forced 표식 조회가 중복될 수 있다.
- 공격/표식: [src/main/java/kim/biryeong/semiontd/tower/illager/IllagerTower.java:114-132](../src/main/java/kim/biryeong/semiontd/tower/illager/IllagerTower.java#L114)는 owner native mark/raid/income/omen 배율; :162-221은 mark 적용 뒤 shared splash. :142-153 omen은 첫 유효 명중에서 사용 여부를 먼저 소비하고 살아 있는 target에 적용. [src/main/java/kim/biryeong/semiontd/tower/illager/IllagerMarks.java:33-52](../src/main/java/kim/biryeong/semiontd/tower/illager/IllagerMarks.java#L33)는 owner별 omen key 문자열/Identifier/MonsterDataKey를 조회마다 만들고 만료 시 제거. :76-88 native activeMark는 읽기처럼 보이나 inactive(다른 owner 포함)일 때 MARK를 제거한다.
- 상태/증강: [src/main/java/kim/biryeong/semiontd/tower/illager/IllagerRaidStates.java:36-65](../src/main/java/kim/biryeong/semiontd/tower/illager/IllagerRaidStates.java#L36) round-start tower count snapshot/ambush; :67-80은 lane tick마다 pending volleys를 한 번 소비, volley마다 `List.copyOf(lane.towers())` O(V·N) 후 현재 entity/target 추가 공격. 각 volley snapshot이므로 한 번 만든 roster를 재사용하면 callback 중 변화 반영 시점이 달라질 수 있다.
- kill/범위효과: [src/main/java/kim/biryeong/semiontd/tower/illager/IllagerMarks.java:95-111](../src/main/java/kim/biryeong/semiontd/tower/illager/IllagerMarks.java#L95)은 최초 attributed kill의 MARK_TRANSFERRED flag 후 nearest maxTargets mark transfer magic AoE. :114-125는 native/omen full original duration 복사. 추가 공격 중 trigger 억제로 재귀 불가. native mark는 expiresAt까지 포함([src/main/java/kim/biryeong/semiontd/tower/illager/IllagerMark.java:15-19](../src/main/java/kim/biryeong/semiontd/tower/illager/IllagerMark.java#L15)), omen은 `activeTicks>=expiresAt` 만료(:48)로 경계가 다르다. 강제표식 구체 radius는 Euclidean squared `<=`(:22-29).
- VFX: raid 활성 효과는 [src/main/java/kim/biryeong/semiontd/tower/illager/IllagerRaidStates.java:155-181](../src/main/java/kim/biryeong/semiontd/tower/illager/IllagerRaidStates.java#L155)의 pending flag 1회 소비 뒤 살아 있는 tower별 공통 VFX. [src/main/java/kim/biryeong/semiontd/tower/illager/IllagerRaidBossBarService.java:30-62](../src/main/java/kim/biryeong/semiontd/tower/illager/IllagerRaidBossBarService.java#L30)는 manager tick(:1870)에서 player scan/HashSet/keySet copy, :112-136은 매 update title Component 생성과 10회마다 add packet 재동기화. 즉 fixed packet cadence가 있는 UI로 임의 축소 금지.
- 소환/흡수: 자체 소환/흡수 없음. mark 전달은 monster-owned typed state, 습격 상태는 UUID->scalar record. [src/main/java/kim/biryeong/semiontd/job/JobIllagerLifecycle.java:8,18,23,28](../src/main/java/kim/biryeong/semiontd/job/JobIllagerLifecycle.java#L8) match-start/round-end/elimination/close 모두 clear. bossbar service :74-105도 player/offline/game 종료 정리. 전역 world reference 누수 증거 없음.

##### 후보/제약

1. **미수정:** 동일 goal tick의 targetCandidates 재사용은 공유 목표 엔진 범위 후보다. 무표식 상태만 기억해 강제검색 5틱 지연을 주면 IllagerTowerRuntimeTest의 즉시 override 계약을 깨뜨린다. 특히 activeMark의 삭제 부작용 때문에 단순 memoization은 별도 정확성 검증 필요.
2. omenKey 반복 생성은 owner-key map 전역 캐시보다 bounded tower-local/monster-owned 구조가 필요하다. owner/monster 종료 및 오래된 save 계약까지 고려해야 하므로 보류. activeMark가 다른 owner 조회로 기존 native mark를 제거하는 현행 동작은 정확성 별도 검토 후보이며 이번 성능변경에 섞지 않았다.

기존 테스트: [src/test/java/kim/biryeong/semiontd/tower/illager/IllagerMarkTest.java:17,41,51,69](../src/test/java/kim/biryeong/semiontd/tower/illager/IllagerMarkTest.java#L17) 전달/만료/owner/비중첩(마지막 테스트 내용 직접 확인), [src/test/java/kim/biryeong/semiontd/tower/illager/IllagerRaidStateTest.java:11,42,54,83](../src/test/java/kim/biryeong/semiontd/tower/illager/IllagerRaidStateTest.java#L11) gauge/reset/one-shot/bossbar. [src/gametest/java/kim/biryeong/semiontd/tower/illager/IllagerTowerRuntimeTest.java:42,168](../src/gametest/java/kim/biryeong/semiontd/tower/illager/IllagerTowerRuntimeTest.java#L42) cached target 즉시 override 및 timed effects; [src/gametest/java/kim/biryeong/semiontd/tower/illager/IllagerAugmentsGameTest.java:64,99,137](../src/gametest/java/kim/biryeong/semiontd/tower/illager/IllagerAugmentsGameTest.java#L64) transfer/공통 kill/volley 재충전 차단. 실행 안 함.

<a id="builder-nether"></a>

### 9. 네더 빌더 — Nether

판정: **유지: 단계 전환·혈액 FIFO·피해원 생성 순서**. 개별 조사 완료, 새 성능 미측정. 통합 gate4 통과를 개별 전수실행으로 확대하지 않는다.

복잡도: N=lane 타워, R=가족 타워, M=lane 몬스터, C=broadphase 후보, K=피해 대상, S=sculk 셀, D=지뢰, B=화상, A=흡수. 줄은 기준 HEAD이며 OceanWaterTower 208행 이후는 후속 수정에서 +6행이다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | 부활→효과/감쇠→phase→super |
| attack | 변신·강제공격·혈액 charge |
| target | 공통 선택 및 현재 runtime 상태 |
| AoE | 각 phase별 pulse/피해 요청 |
| stack | 혈액 FIFO·부활/phase 상태 |
| summon / absorb | 외부 전투 소환·희생흡수와 구별 |
| sync | 교체 HP·source 위치·round/world 수명 |
| VFX | 전환 시 전용 VFX·상시 scan 없음 |

#### 경로·주기·비용·경계·수명과 회귀 근거

##### 호출과 비용

- 매틱: [src/main/java/kim/biryeong/semiontd/tower/nether/NetherTower.java:93-121](../src/main/java/kim/biryeong/semiontd/tower/nether/NetherTower.java#L93)은 revival countdown -> entity 조회 -> 4개 dynamic effect refresh -> decay/pulse counter 감소 -> 실제 decay -> phase counter 감소 -> super 순서. 이 순서가 critical 체력 임계/transition pulse에 관여한다. :409-414는 existing activeMonsters list의 isEmpty라 O(1), 스캔/복사 아님.
- 상태동기화: :416-454는 damage/reduction/missing-health/zombie-speed effect를 매틱 refresh; :351-367은 scalar health 변경과 entity.setHealth, 필요 시 discard. currentMaxHealth/healthRatio 반복 조회는 있으나 변환/피해 callback 이후 값이 달라질 수 있어 범위를 넓힌 캐시 금지. :149-175는 removed/dead와 unload를 구분하고 zombie 변환, :370-406은 entity 교체/health/forceAttackReady/효과/transition pulse/VFX를 순서대로 수행.
- 공격/대상: :220-242는 Wither highHealth max 1패스 후 없으면 healthRatio min 1패스, 다른 skeleton은 min, 다른 타입 공통. comparator tie는 encounter-first. :245-278 기본 hit마다 lifesteal/공격수/mark slot/splash/pulse/extra attack 조건. :282-307 kill buff 및 실제 dealt 기반 blood charge/phase splash. 기존 기본 lifesteal 경로와 resolved 증강 경로는 서로 다르며 이번 변경 없음.
- AoE/2차 목표: :519-584는 shared splash/pulse API, :586-621의 extraAttack은 trigger 때 추가 world query -> filter -> min. 무조건 매틱 추가 scan은 아니다. 비교 기준은 tower 위치의 Euclidean squared `<= secondaryRange²`, lane filter, primary 제외, alive/runtime 유효. shared API로 변경하면 owner/final-defense/stealth/제외 조건 차이를 검증해야 한다.
- 스택: [src/main/java/kim/biryeong/semiontd/tower/nether/NetherBloodChargeController.java:9-21](../src/main/java/kim/biryeong/semiontd/tower/nether/NetherBloodChargeController.java#L9)은 실제 natural loss를 threshold로 나누어 `ArrayDeque<Double>` FIFO charge. loss가 q threshold를 넘으면 O(q), q boxed Double. threshold 변화에도 기존 각 charge 크기를 유지하므로 scalar count로 바꾸면 안 된다. reset :28-31, snapshot/restore :33-40. maxMarkStacks는 sourceId별 rotation. [src/main/java/kim/biryeong/semiontd/tower/nether/NetherTower.java:687-690](../src/main/java/kim/biryeong/semiontd/tower/nether/NetherTower.java#L687)은 mark 발동 때 type/owner/lane/**현재 위치**/suffix로 identifier 생성한다.
- 소환/흡수: 외부 타워 소환/흡수 없음. native zombie 변환+TOTEM 지연 자기 재생성(:93-104,125-131)이 있음. :179-192에서 round reset, :332-348에서 독립 FIFO snapshot 복사 및 health 비율 유지. tower-local 객체만 보유.
- VFX: transition과 extra attack은 공통 `TowerVfxService`, 범위 request는 onTrigger. per-tick family VFX 없음(매틱 decay health update와 구분).

##### 후보/제약

- **미수정:** dynamic health/stat 계산을 같은 refresh 호출 안에서만 한 번 계산할 수 있는지 계측 후 검토. 매틱 cadence 자체는 체력 손실/회복/phase 전환과 coupled하므로 유지.
- per-attack sourceId 캐시는 위치/업그레이드/owner/lane/mark slot 변화에 invalidation이 필요하고 source identity가 gameplay stacking 키다. 수동 JIT 인라인/자잘한 getter 이동은 제안하지 않음.
- FIFO는 사용하지 않고 heal로 생존하는 긴 wave에서 늘 수 있으나 round reset이 있고 저장 단위가 의미 있으므로 현재 코드만으로 누수 판정 불가.

기존 테스트: [src/test/java/kim/biryeong/semiontd/tower/nether/NetherBloodChargeControllerTest.java:11,25,42](../src/test/java/kim/biryeong/semiontd/tower/nether/NetherBloodChargeControllerTest.java#L11) threshold 변화/FIFO 독립복사/epsilon; [src/test/java/kim/biryeong/semiontd/tower/nether/NetherAugmentsTest.java:26,46,60,85](../src/test/java/kim/biryeong/semiontd/tower/nether/NetherAugmentsTest.java#L26) charge/복사/20틱 1회 revive/60틱 phase. [src/gametest/java/kim/biryeong/semiontd/tower/nether/NetherTowerRuntimeTest.java:145,282,316](../src/gametest/java/kim/biryeong/semiontd/tower/nether/NetherTowerRuntimeTest.java#L145) magic secondary/Wither 정책/proxy 재생성; [src/gametest/java/kim/biryeong/semiontd/tower/nether/NetherAugmentsGameTest.java:28,103,139](../src/gametest/java/kim/biryeong/semiontd/tower/nether/NetherAugmentsGameTest.java#L28) 실제 reduced decay/phase/totem. 실행 안 함.

<a id="builder-end"></a>

### 10. 엔드 빌더 — End

판정: **유지: 사후 지뢰·화상·흡수의 시점과 순서**. 개별 조사 완료, 새 성능 미측정. 통합 gate4 통과를 개별 전수실행으로 확대하지 않는다.

복잡도: N=lane 타워, R=가족 타워, M=lane 몬스터, C=broadphase 후보, K=피해 대상, S=sculk 셀, D=지뢰, B=화상, A=흡수. 줄은 기준 HEAD이며 OceanWaterTower 208행 이후는 후속 수정에서 +6행이다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | 파괴 검사 전 지뢰/화상·transfer tick |
| attack | breath/assault·연속 hit/화상 |
| target | 독립 query·범위/통로/대체 목표 |
| AoE | 지뢰 재조회·breath sweep·다단 피해 |
| stack | transfer 기록·burn/mine 만료 |
| summon / absorb | 스냅샷 흡수·partial transfer·rollback |
| sync | kill 성공·귀환 바닥·source 제거 |
| VFX | EndVfx 패킷/기하·수명 유지 |

#### 경로·주기·비용·경계·수명과 회귀 근거

##### 호출과 비용

- 매틱 순서: [src/main/java/kim/biryeong/semiontd/tower/end/EndTower.java:177-204](../src/main/java/kim/biryeong/semiontd/tower/end/EndTower.java#L177)는 mines, 기존 burns를 **isDestroyed 전**에 처리. 이후 살아 있는 hatched core는 transfer, transfer VFX, stat refresh, evolution reconcile, completion heal, periodic transfer heal, counts/combat sync, assault, breath, regen, super. 사망 뒤 mine/burn 지속은 의도된 테스트 계약.
- 흡수: [src/main/java/kim/biryeong/semiontd/tower/end/EndTransferController.java:29-51](../src/main/java/kim/biryeong/semiontd/tower/end/EndTransferController.java#L29)은 매틱 lane roster O(N), identity present set clear/rebuild, eligible source computeIfAbsent 후 A entries 진행. [src/main/java/kim/biryeong/semiontd/tower/end/EndTransferState.java:11-39](../src/main/java/kim/biryeong/semiontd/tower/end/EndTransferState.java#L11) identity map/set을 재사용하므로 매번 새 membership set을 만들지는 않는다. 상태 delta 적용(:47-58), interrupt rollback(:61-70), :90-107 committed snapshot은 O(A). controller :140-153 completion은 실제 `lane.killTowers` 결과 identity set으로 확인, 성공만 stacks/증강, 실패 rollback. 순서 변경/성공추정 금지.
- 할당: controller :231-277은 tick마다 TransferTick + completion/particle ArrayList 2개 + TickResult 및 반환 list snapshot. [src/main/java/kim/biryeong/semiontd/tower/end/EndTransferState.java:80-87](../src/main/java/kim/biryeong/semiontd/tower/end/EndTransferState.java#L80) scalar progression snapshot은 호출마다 새 record이며 tower의 stats/regen/damage hooks에서 반복. 할당 최적화 후보이나 immutable detached snapshot과 rollback/partial health 계약을 유지해야 한다.
- 공격/스택: [src/main/java/kim/biryeong/semiontd/tower/end/EndCombat.java:117-172](../src/main/java/kim/biryeong/semiontd/tower/end/EndCombat.java#L117)은 shared splash callback마다 actual-dealt lifesteal, ignite 및 secondary list. :21-114의 snapshot 기반 스탯은 O(1)/설정 threshold list 크기. [src/main/java/kim/biryeong/semiontd/tower/end/EndTower.java:282-304](../src/main/java/kim/biryeong/semiontd/tower/end/EndTower.java#L282)는 성장 추가 magic hit에 실제 secondary list를 전달하므로 primary만 남기거나 list를 생략하면 안 된다. 타깃 기본선택은 공통, assault 중 :441-442에서 공격 금지.
- 지뢰: [src/main/java/kim/biryeong/semiontd/tower/end/EndAugments.java:98-121](../src/main/java/kim/biryeong/semiontd/tower/end/EndAugments.java#L98)은 D개 매틱 `getEntitiesOfClass(...).isEmpty()`로 존재를 확인하고 발동 시 shared nearestTargets AoE를 다시 조회. O(D·C)+발동시 선택, AABB/list 임시 생성, 10틱마다 mine particle. mineSource는 death 후 남아서 지뢰 피해 귀속을 유지하며 reset :38-45에 null.
- breath: [src/main/java/kim/biryeong/semiontd/tower/end/EndAugments.java:75-95](../src/main/java/kim/biryeong/semiontd/tower/end/EndAugments.java#L75) activeTicks로 기본 120틱 시작, shots 사이 기본4틱; current target 유효/range면 재사용(:124-131), 아니면 world min. :134-154는 전방 직사각형 (forward 0..length, abs sideways<=width/2) 포함 경계를 shared sphere broadphase 위에서 필터한다.
- assault: [src/main/java/kim/biryeong/semiontd/tower/end/EndDragonAssault.java:71-160](../src/main/java/kim/biryeong/semiontd/tower/end/EndDragonAssault.java#L71) 상태머신. RUSHING/BREATHING 각각 60tick, :185-202의 sweep는 매 tick lane.activeMonsters O(M) + entity 조회로 y envelope 구축 후 shared AoE를 다시 조회. sweep는 [0,to] 누적구간 + UUID hit set이므로 늦게 이동/생성된 적의 1회 타격 계약이 있다. y envelope를 고정하면 높은 적 누락 위험. :261-280 burn 매틱 O(B), interval 때 current damage, removed/dead/dominated/team/lane 검사 후 제거. reset :45-53이 hit/burn/source 모두 제거, cancel은 flight만 멈춘다.
- 귀환: [src/main/java/kim/biryeong/semiontd/tower/end/EndDragonReturnPosition.java:15-42](../src/main/java/kim/biryeong/semiontd/tower/end/EndDragonReturnPosition.java#L15)의 lane xz 열 검사, 더 가까운 열만 floor/chunk/collision 및 own/reinforcing tower occupancy 조회. RETURNING 4틱 뒤 이후20틱마다 재시도(:149). tower는 실제 위치/관리 위치 예약을 별도로 유지(:62-70). 타워 이동/철거/world collision이 바뀌므로 고정 결과 캐시 금지.
- VFX: transfer controller :207-211은 stable identity offset으로 5틱마다, [src/main/java/kim/biryeong/semiontd/tower/end/EndVfx.java:33-37](../src/main/java/kim/biryeong/semiontd/tower/end/EndVfx.java#L33) charge는 3틱마다, rush :40-46은 폭에 비례한 입자 호출, breath :50-66은 5*7 beam samples+폭 wave 매틱. raw sendParticles+Vec3 생성이며 공통 area effect VFX budget과 별도인 경로. appearance 계약/실제 packet tests 존재, 임의 감축 미수행.
- 자체 새로운 전투 타워 소환 없음. EGG/PHANTOM/DRAGON은 한 runtime tower 상태이며 시각 proxy와 별개. absorption은 존재. cleanup은 [src/main/java/kim/biryeong/semiontd/tower/end/EndTower.java:86-152,316-329,452-455](../src/main/java/kim/biryeong/semiontd/tower/end/EndTower.java#L86), 전역 family state 없음. presentTowerSnapshot은 clearProgress에서 비우지 않아 reset 뒤 다음 capture 전까지 이전 roster를 잡을 수 있으나 tower-owned bounded retention; world leak 확정 증거 없음. source dead reference는 mine/burn 지속의 의도된 수명.

##### 후보/제약

- **미수정:** 빈 transfer state에서 새 TransferTick/ArrayList/record 생성을 피할 수 있으나 captureTargets 이후에만 판단하고 statsChanged/rollback/partially killed completion 순서를 보존해야 한다. progression snapshot 공유는 call-local 범위 또는 명시적 버전 invalidation이 필요.
- mine existence scan을 short-circuit/shared selection 하나로 합치는 후보는 현재 shared filter/order/죽은 source 허용/첫 trigger의 side effect/target cap를 통합 검증해야 한다. source alive guard 추가는 명백한 회귀.
- assault sweep의 double scan은 확인되었지만 y envelope, final-defense, late entry, hit UUID exclusion 때문에 범위 필터 동등성 근거 없이 변경하지 않음.

기존 테스트: [src/test/java/kim/biryeong/semiontd/tower/end/EndTransferControllerTest.java:52,87,107,140](../src/test/java/kim/biryeong/semiontd/tower/end/EndTransferControllerTest.java#L52), [src/test/java/kim/biryeong/semiontd/tower/end/EndTransferLifecycleTest.java:55,84,141](../src/test/java/kim/biryeong/semiontd/tower/end/EndTransferLifecycleTest.java#L55) gradual/rollback/no-free-heal/immutable snapshot/reload/committed copy/new identity; [src/test/java/kim/biryeong/semiontd/tower/end/EndCombatProgressionTest.java:217,239,372,391](../src/test/java/kim/biryeong/semiontd/tower/end/EndCombatProgressionTest.java#L217) snapshot/overflow/lifesteal/splash. [src/gametest/java/kim/biryeong/semiontd/tower/end/EndTowerAugmentCombatTest.java:15](../src/gametest/java/kim/biryeong/semiontd/tower/end/EndTowerAugmentCombatTest.java#L15) 사망 후 mine 및 cap, :84 breath 간격/재타깃, :120 lifesteal; [src/gametest/java/kim/biryeong/semiontd/tower/end/EndDragonAssaultTest.java:134](../src/gametest/java/kim/biryeong/semiontd/tower/end/EndDragonAssaultTest.java#L134) 사망 뒤 burn 지속; [src/gametest/java/kim/biryeong/semiontd/tower/end/EndDragonLaneAssaultTest.java:208,307,351](../src/gametest/java/kim/biryeong/semiontd/tower/end/EndDragonLaneAssaultTest.java#L208) late/high enemies/귀환/blocked 재시도; [src/gametest/java/kim/biryeong/semiontd/tower/end/EndDragonBreathVfxTest.java:18](../src/gametest/java/kim/biryeong/semiontd/tower/end/EndDragonBreathVfxTest.java#L18) packet geometry; [src/gametest/java/kim/biryeong/semiontd/tower/end/EndTowerIntegrationTest.java:32](../src/gametest/java/kim/biryeong/semiontd/tower/end/EndTowerIntegrationTest.java#L32) lifecycle close. 실행 안 함.

<a id="builder-ocean"></a>

### 11. 바다 빌더 — Ocean

판정: **변경: 재활용 공급의 최저 수분 수신자 검색 1회**. 선정 변경 경로 최종 gate4 통과. 새 성능 미측정.

복잡도: N=lane 타워, R=가족 타워, M=lane 몬스터, C=broadphase 후보, K=피해 대상, S=sculk 셀, D=지뢰, B=화상, A=흡수. 줄은 기준 HEAD이며 OceanWaterTower 208행 이후는 후속 수정에서 +6행이다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | tide·transfer·탈수20·지원 execute |
| attack | hunter·support·water supply |
| target | 최대 HP 선택·수분 최저 선택 |
| AoE | 수분 공급/지원의 범위와 source 재확인 |
| stack | 물/조류·수신 분배·EPS 누적 |
| summon / absorb | 소환 없음; transfer/공급은 실제 경로 |
| sync | 원래 위치/ID snapshot·water marker 복원 |
| VFX | 공급 목적지별 전용 호출·공유 AoE |

#### 경로·주기·비용·경계·수명과 회귀 근거

##### 호출과 비용

- 매틱: [src/main/java/kim/biryeong/semiontd/tower/ocean/OceanTower.java:115-123](../src/main/java/kim/biryeong/semiontd/tower/ocean/OceanTower.java#L115) tide->super execute->transfer cooldown->dehydration. :377-394 탈수 상태는20틱마다 health/entity/VFX 갱신, :438-455 tide floor 유지. [src/main/java/kim/biryeong/semiontd/tower/ocean/OceanCurrentController.java:14-34](../src/main/java/kim/biryeong/semiontd/tower/ocean/OceanCurrentController.java#L14) ticks/spent/charges scalar O(1), 실제 지출만 :425-434에서 기록, upgrade :323-332은 snapshot 독립복사.
- 공격/타깃: :171-180 hunter max-health O(C), ties encounter-first; :184-201 물량 및 income 조건 계산, :251-266 기존 물을 기준으로 splash/extra cost를 결정한 뒤 drain. :270-287 actual dealt와 charge로 nearest capped magic current 1회. callback suppression 보존.
- 지원/치유: :126-159,348-370은 nearbyOceanCombatTowers(:397-406) O(N) list 후 entity mapping/filter list 한 번 더. 성공 시에만 spend/cooldown. 거리는 current GridPosition Euclidean squared <= radius². tank onDamaged :219-247는 cooldown/살아있음/water 조건 후 non-tank recipients list, pool/N 분배+VFX; 대상들은 water 수령 후에 VFX를 보냄.
- 물 공급: [src/main/java/kim/biryeong/semiontd/tower/ocean/OceanWaterTower.java:104-110,159-191](../src/main/java/kim/biryeong/semiontd/tower/ocean/OceanWaterTower.java#L104)은 wave 시작에 originalPosition 반경 snapshot으로 stable SUPPLY_TARGET_ID set, 이후 공급마다 lane 현재 생존 목록과 ID 매칭. snapshot 밖 새 타워는 즉시 공급대상이 아니며 upgrade는 typed data 복사로 ID 유지. source가 final-defense면 공급 중단. supply는 recipients, allocations LinkedHashMap, suppliedTargets, immutable result를 할당한다.
- **구현한 부분**: 기준 :194-207의 supplyAllocations가 포화 target마다 동일 min(water,logicalId)을 다시 계산해 O(R²). 현재 :194-213은 첫 포화 target에서 한 번만 lazy min을 계산하여 O(R), O(1) 추가 공간. 물 실제 적용은 caller의 allocations 반환 이후 :178-188이므로 선택 중 수분은 불변. 원래 comparator, EPS predicate, LinkedHashMap merge/순서/Double::sum 순서는 변경하지 않았다. lazy logicalId 생성도 첫 선택 시점 그대로다.
- 여전히 남는 비용: 기준 :242-249 supplyStackMultiplier는 recipient마다 모든 source를 scan해 O(R·N); source-count는 현재 살아 있는 non-final-defense 물탑의 originalPosition radius를 사용하고 wave captured recipient set과 다른 의미다. 이를 snapshot 캐시로 바꾸면 source removal/death/range reload 변화가 늦게 반영됨.
- VFX: [src/main/java/kim/biryeong/semiontd/tower/ocean/OceanVfx.java:37-41](../src/main/java/kim/biryeong/semiontd/tower/ocean/OceanVfx.java#L37) destinations list 생성. :78-101는 recipient마다 9 supply sample+14 ring+1 splash =24 sendParticles 호출, source pulse :19-34는14 ring+1 splash. 탈수 :44-51는14+10 ring+2 particle calls. 공급 성공/탱크전달/20틱 탈수 이벤트 단위이며 공통 budget으로 자동 합쳐진다고 가정하면 안 된다.
- 소환/흡수 없음. water marker만 world에 배치한다. [src/main/java/kim/biryeong/semiontd/tower/ocean/OceanWaterTower.java:267-293](../src/main/java/kim/biryeong/semiontd/tower/ocean/OceanWaterTower.java#L267)(기준)는 원래 공기 블록 저장, 현재 marker와 동일할 때만 제거/죽음 때 복원하므로 외부 변경 보호. `OceanTower.currentLane`은 tower-local strong reference로 onPlaced/tick/reset에서 갱신, 전역 map 없음; 외부에서 제거된 타워를 계속 보관하지 않는 한 이 필드만으로 leak 확정 불가.

##### 구현/검증

수정 파일: [src/main/java/kim/biryeong/semiontd/tower/ocean/OceanWaterTower.java](../src/main/java/kim/biryeong/semiontd/tower/ocean/OceanWaterTower.java), [src/test/java/kim/biryeong/semiontd/tower/ocean/OceanAugmentsTest.java](../src/test/java/kim/biryeong/semiontd/tower/ocean/OceanAugmentsTest.java).

추가 3 tests: :70 logicalId 동률/첫 allocation 삽입순서/물 불변/0.1 sequential raw bits, :93 EPS 양쪽/모두 포화/빈 list/non-recycle, :108 중복타워 및 0/-0/음수/0.1/subnormal/MAX/±Infinity/NaN의 기존 순차 double 합산. `git diff --check -- <두 파일>` 통과. JUnit/Gradle 실행은 root 통합 담당. 실제 성능 수치 없음.

기존 테스트: [src/test/java/kim/biryeong/semiontd/tower/ocean/OceanAugmentsTest.java:54](../src/test/java/kim/biryeong/semiontd/tower/ocean/OceanAugmentsTest.java#L54) pre-attenuation 회계, [src/test/java/kim/biryeong/semiontd/tower/ocean/OceanCurrentControllerTest.java:11,28](../src/test/java/kim/biryeong/semiontd/tower/ocean/OceanCurrentControllerTest.java#L11) tide/잔여/복사, [src/test/java/kim/biryeong/semiontd/tower/ocean/OceanTowerCatalogTest.java:105,128,189,201](../src/test/java/kim/biryeong/semiontd/tower/ocean/OceanTowerCatalogTest.java#L105) water softcap/source decay/cooldown/marker. [src/gametest/java/kim/biryeong/semiontd/tower/ocean/OceanAugmentsGameTest.java:36](../src/gametest/java/kim/biryeong/semiontd/tower/ocean/OceanAugmentsGameTest.java#L36) connected-only 재분배와2 source attenuation 직접 내용 확인, :72 current cap; [src/gametest/java/kim/biryeong/semiontd/tower/ocean/OceanTowerRuntimeTest.java:35,202,273,352,470,514,623](../src/gametest/java/kim/biryeong/semiontd/tower/ocean/OceanTowerRuntimeTest.java#L35) marker 복원/round/source removal/tank/support/heal/placement 거절. 실행 안 함.

<a id="builder-ancientcity"></a>

### 12. 고대 도시 빌더 — AncientCity

판정: **후보: Warden top-k·sculk 인덱스; 수명 검증 필요**. 개별 조사 완료, 새 성능 미측정. 통합 gate4 통과를 개별 전수실행으로 확대하지 않는다.

복잡도: N=lane 타워, R=가족 타워, M=lane 몬스터, C=broadphase 후보, K=피해 대상, S=sculk 셀, D=지뢰, B=화상, A=흡수. 줄은 기준 HEAD이며 OceanWaterTower 208행 이후는 후속 수정에서 +6행이다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | 주문 execute 실패 재시도·sculk/mark |
| attack | Warden 주문/강화 공격 |
| target | HP/거리/UUID 문자열 정렬 |
| AoE | sculk 영역·각성 중심의 순서 |
| stack | mark source 최대·owner/강화 상태 |
| summon / absorb | 가족 소환·흡수 없음 |
| sync | 확장 이벤트·원본/소유자 정리 |
| VFX | 전용 raw VFX 및 shared event |

#### 경로·주기·비용·경계·수명과 회귀 근거

##### 호출과 비용

- 매틱: [src/main/java/kim/biryeong/semiontd/tower/ancientcity/AncientCityTower.java:88-94](../src/main/java/kim/biryeong/semiontd/tower/ancientcity/AncientCityTower.java#L88) retaliation counter->super active spell->awakened city. 주문 cooldown은 성공한 경우만(:159-181, 공통 Tower.tick). catalyst는 active execute 없음, 피격 시 :127-155 cooldown pulse를 실행.
- 대상/주문: :344-356은 shared AoE callback으로 candidates list 생성. source range와 owned sculk expansion filter를 사용하며 범위 밖/다른 소유 스컬크 제외. Sensor/Shrieker :331-341는 현재 target이 candidate이면 재사용, 아니면 nearest min O(C). Warden :293-317는 candidates 전체 copy+maxHealth desc/distance asc/UUID **문자열** asc sort 후 targetCount limit list: O(C log C), O(C+K). comparator의 UUID.toString 할당도 존재. UUID.compareTo로 바꾸면 signed 비교 순서가 달라지므로 단순 교체 불가.
- 영역/스택: [src/main/java/kim/biryeong/semiontd/tower/ancientcity/AncientCityStates.java:122-139](../src/main/java/kim/biryeong/semiontd/tower/ancientcity/AncientCityStates.java#L122) resonanceActive는 main+finalDefense set xz 선형 검색 O(S); :165-176 sculkBelow는 현재 combatTerritory의 xz stream findFirst O(S). 이들은 range predicate/피해/defense에서 반복되어 후보당 O(S)가 된다. :141-152 damage bonus는 main territory count 기반이며 actual resonance 여부는 해당 타워 위치에 따른다.
- awakened city: Tower :410-457는 wave/alive/warden/augment/awake 및 owner cooldown 확인 후 strongestWarden을 찾는다. :393-401은 roster scan+live entity 검사, comparator마다 entity 재조회 및 resolved magic damage 계산(그 안에 resonance O(S)). due지만 marked target이 없어 pulse를 claim하지 못하면 다음 tick에도 모든 warden이 반복할 수 있다. :427-438은 encounter-order LinkedHashSet으로 최대 centers, 조건과 callback에서 sculkBelow가 중복된다. :440 이후 shared owner cooldown을 claim하고 center별 nearest cap pulse를 실행; center 중복 방지와 sequential damage로 인한 후속 target eligibility를 보존해야 한다.
- 성장: [src/main/java/kim/biryeong/semiontd/tower/ancientcity/AncientCityStates.java:35-71,83-120](../src/main/java/kim/biryeong/semiontd/tower/ancientcity/AncientCityStates.java#L35)은 처음 seed, wave 최초1회, attributed kill cap, finalDefense seed 최초1회. :208-235는 기존 territory의 모든 neighbor를 frontier PQ/queued HashSet에 넣고 Manhattan/x/z/y 순서로 확장. floor :276-307은 lane xz inclusive 경계와 y 하향 world scan/collision/fluid/blockEntity 규칙. 총 기존 S 및 새 frontier 정렬/블록검사 비용은 이벤트 때 발생, 매틱 확장은 아님.
- marks: [src/main/java/kim/biryeong/semiontd/tower/ancientcity/AncientCityMarks.java:70-93](../src/main/java/kim/biryeong/semiontd/tower/ancientcity/AncientCityMarks.java#L70)는 monster-owned owner->source->ActiveMark map. 조회 때 전체 owner/source expired prune 및 해당 owner max(겹침은 합산 아님), O(number of marks), apply마다 immutable ActiveMark. :48-49,:63-65 빈 최상위 state 제거. unused expired marks는 다음 읽기 또는 monster 수명 종료까지 남지만 전역 entity reference 없음.
- 상태동기화/수명: onPlaced/onStateChanged는 seed 확인(:64-73), upgrade는 cooldown/wave flag 복사(:208-212). owner state는 UUID->BlockPos sets/scalars, [src/main/java/kim/biryeong/semiontd/job/JobAncientCityLifecycle.java:8,23](../src/main/java/kim/biryeong/semiontd/job/JobAncientCityLifecycle.java#L8) match-start/elimination, [src/main/java/kim/biryeong/semiontd/job/JobBuilderLifecycle.java:142](../src/main/java/kim/biryeong/semiontd/job/JobBuilderLifecycle.java#L142) close-before-lanes 정리. world reference를 static state에 저장하지 않는다. 맵 블록 변화는 world gameplay이며 임의 reset/복원 최적화 금지.
- VFX: [src/main/java/kim/biryeong/semiontd/tower/ancientcity/AncientCityVfx.java:21-70](../src/main/java/kim/biryeong/semiontd/tower/ancientcity/AncientCityVfx.java#L21) raw particle growth(13 calls)/catalyst(23)/sensor(15 beam+14 ring)/shrieker(49)/warden(target당19 beam+1). 마법 발동 시 O(K)이며 common AoE VFX와 함께 실행되는 경우도 있다. :108-130의 Vec3/sample/trig 비용, packet appearance 계약 때문에 샘플 감소 미수행.
- 자체 소환/흡수 없음. 스컬크 world 영역 생성, 센서 표식/owner cooldown이 가족 메커닉.

##### 후보/제약

1. **미수정:** Warden full sort->bounded top K는 확실한 알고리즘 후보지만 health/distance/UUID 문자열/encounter ties와 선택 후 순서를 정확히 보존해야 한다. 공통 nearest helper를 그대로 쓰면 우선순위가 다르다.
2. **미수정:** xz territory lookup index는 O(S)->평균 O(1) 가능하나 모든 seed/death/wave/finalDefense/clear 경로와 동일 xz 다른 y의 기존 findFirst 의미를 정의해야 함. safe 작은 패치 범위보다 커서 보류.
3. strongestWarden score/entity를 한 scan당 계산하는 후보는 outgoing modifier의 side effect/기존 max의 logicalId tie/owner+finalDefense 조건을 검증한 후. 일정 시간 캐시는 reload/체력/증강/죽음/스컬크 성장 영향 때문에 미제안.

기존 테스트: [src/test/java/kim/biryeong/semiontd/tower/ancientcity/AncientCityTowerCatalogTest.java:88,137,166](../src/test/java/kim/biryeong/semiontd/tower/ancientcity/AncientCityTowerCatalogTest.java#L88) cap/config/owner mark expiry, [src/test/java/kim/biryeong/semiontd/tower/ancientcity/AncientCityAugmentsTest.java:36,49](../src/test/java/kim/biryeong/semiontd/tower/ancientcity/AncientCityAugmentsTest.java#L36) chain target cap/동일 source 비중첩. [src/gametest/java/kim/biryeong/semiontd/tower/ancientcity/AncientCityAugmentsGameTest.java:40,116,150,176,222](../src/gametest/java/kim/biryeong/semiontd/tower/ancientcity/AncientCityAugmentsGameTest.java#L40) pre-hit health/range/secondary/강한 warden6 center/중복셀8cap; [src/gametest/java/kim/biryeong/semiontd/tower/ancientcity/AncientCityTowerRuntimeTest.java:33,178,255](../src/gametest/java/kim/biryeong/semiontd/tower/ancientcity/AncientCityTowerRuntimeTest.java#L33) 지속성장/magic/finalDefense 타 lane. 실행 안 함.

<a id="builder-adversary"></a>

### 13. 히어로 빌더 — Adversary

판정: **유지: 강제 선택·변신·점수 정리의 재진입 계약**. 개별 조사 완료, 새 성능 미측정. 통합 gate4 통과를 개별 전수실행으로 확대하지 않는다.

복잡도: T=lane 타워, M=lane 활성 몬스터, K=필터 대상, C=회로, P=소환 대기, J=5장 창 조커. 별도 표기 없는 줄은 기준 HEAD의 위치다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | Fox entity/mace/sculk/team tick |
| attack | 무기·focus·변신 후 실제 kill credit |
| target | forced min/max·각성 타깃 |
| AoE | double query·정렬/top-k·팀 buff/heal |
| stack | 점수/처치/팀·rival progression |
| summon / absorb | rival entity 성공 변신·wave 흡수 |
| sync | remove 중 reconcile·teardown 순서 |
| VFX | fake visual·전용/공유 VFX |

#### 경로·주기·비용·경계·수명과 회귀 근거

**매틱/공격/조회**

- [src/main/java/kim/biryeong/semiontd/tower/adversary/AdversaryFoxTower.java:237-258](../src/main/java/kim/biryeong/semiontd/tower/adversary/AdversaryFoxTower.java#L237): 매틱 super→finale/shield 카운터→entity 조회→mace channel→sculk queue→팀 효과→지원 VFX 순서. `tickMace:1141`은 UUID world lookup, 유효성·거리·현재 대상 변경을 매틱 다시 확인하고 10tick 간격 focus VFX. `scheduleSculkBlast:1260`의 큐는 동시에 1건만 허용한다. 대기 record를 매틱 교체하지만 무제한 큐로 볼 수 없다.
- forced/default selection `:262-310`, `:808-850`: final-defense 후보 범위 필터 리스트와 owned rival 리스트를 생성한다. 자기 rival가 우선이며 highest progress→priority 동률 규칙, BIG_GAME의 income max-health→priority 규칙을 보존해야 한다. 후보 수 O(K), 리스트 O(K); 정렬이 아닌 max이므로 억지로 다른 선택 구조를 도입하지 않았다.
- 일반 공격 `:364-441`: 실제 피해가 양수일 때 native 대상 집합을 갱신하고 폼별 스플래시·추가타·연속스택 후 finale 추가타. MACE/SCULK는 피해 0의 기본 ray에서도 자체 channel을 예약하는 예외다. golden/spyglass/echo 대상 UUID와 kill/reset 규칙은 실제 dealt damage 및 같은 대상 여부에 종속된다.
- 추가타/폭발 `:957-1108,1209-1250,1313-1358,1519-1529`: 공통 area API로 후보를 수집한 다음 거리/직선 projection을 정렬하여 cap을 적용한다. 스플래시는 UUID 집합 생성 후 area API를 다시 호출하므로 범위 스캔 2회와 O(K log K) 정렬이 남는다. 직접 피해 순서인 BREEZE/폭죽과 UUID membership만 고르는 스플래시의 순서 계약은 다르다. 폭죽 projection 동률 encounter, 각 index의 피해 ratio, 중앙 방어 타 lane 확장, source.isValidAttackTarget을 유지해야 한다.
- `focusFireAttackerCount:1549-1578`: 피격마다 lane 몬스터 스냅샷 O(M) 및 world 조회. 실제 source가 snapshot 밖에서 공격 중이면 추가로 한 번 센다. 임의 캐시는 target 변경·삭제·이동·world 상태를 놓칠 수 있다.

**범위/스택/소환·흡수/수명/VFX**

- [src/main/java/kim/biryeong/semiontd/tower/adversary/AdversaryTeamEffects.java:52-101](../src/main/java/kim/biryeong/semiontd/tower/adversary/AdversaryTeamEffects.java#L52): 팀 디버프는 코드 기본 20tick마다 팀당 한 번(팀 ordinal offset, WeakHashMap claim). 치료는 Bell/Ominous 코드기본 60tick, Beacon 40tick이며 source entity ID offset이 있다(:104-150,288-304). 팀 lane/tower 스냅샷 O(T), 치료 후보 health-ratio→fox UUID 정렬 O(K log K), 디버프 대상 LinkedHashSet 중복제거 O(M). Unsourced strongest-only duration refresh를 sourced sum으로 바꾸면 안 된다.
- [src/main/java/kim/biryeong/semiontd/tower/adversary/AdversaryRivalTower.java:144-169](../src/main/java/kim/biryeong/semiontd/tower/adversary/AdversaryRivalTower.java#L144): 실제 몬스터 spawn 성공 뒤 logical rival를 hidden 전환하고 death notification을 소비한다. 실패는 원 타워 유지. `:178-192`의 exact proxy/owner/kind/enhanced/once-credit 확인 후 점수 반영. reset/remove의 `:266-284`는 해당 UUID proxy를 lane 목록에서 제거하고 entity discard. 제거마다 [src/main/java/kim/biryeong/semiontd/tower/adversary/AdversaryProgressStates.java:79-123](../src/main/java/kim/biryeong/semiontd/tower/adversary/AdversaryProgressStates.java#L79)가 lane 스냅샷→demotion→replacement를 수행하므로 R회 제거 시 O(RT) 이상의 실제 작업이 생길 수 있다.
- 흡수 [src/main/java/kim/biryeong/semiontd/tower/adversary/AdversaryFoxTower.java:1505-1516](../src/main/java/kim/biryeong/semiontd/tower/adversary/AdversaryFoxTower.java#L1505)는 wave 시작에 캡처한 health/damage 상한 기준, 실제 rival kill에서만 증가. `:767-781`의 upgrade health ratio 및 흡수값 copy, transient 스택 reset 순서 보존. virtual/logical health bridge 및 focus damage는 `:470-616`에 있고 최적화하지 않았다.
- team map은 lifecycle에서 unregister, progress map은 teardown 뒤 clear. local currentLane/entity와 native UUID 집합은 타워 수명 소유. 확정한 장기 누수는 없다. 고정 수명이 없는 새 cache는 만들지 않았다. AdversaryVfx는 공유 서비스 외에도 직접 vanilla particles를 보내는 기존 전용 경로가 있다([src/main/java/kim/biryeong/semiontd/tower/adversary/AdversaryVfx.java:24-55](../src/main/java/kim/biryeong/semiontd/tower/adversary/AdversaryVfx.java#L24)); 외형 검증 없이 통합하거나 호출수를 줄이지 않았다.

**기존 검증/판정**

- [src/gametest/java/kim/biryeong/semiontd/tower/adversary/AdversaryTowerIntegrationTest.java:40](../src/gametest/java/kim/biryeong/semiontd/tower/adversary/AdversaryTowerIntegrationTest.java#L40) 팀 지원/치유/디버프, `:265` 진화·logical ID·health ratio, `:359` magic 통계, `:410` splash/focus-fire, `:463` mace/sculk.
- [src/gametest/java/kim/biryeong/semiontd/tower/adversary/AdversaryTowerCentralAttackTest.java:27,32](../src/gametest/java/kim/biryeong/semiontd/tower/adversary/AdversaryTowerCentralAttackTest.java#L27) final-defense에서만 타 lane 추가타, [src/gametest/java/kim/biryeong/semiontd/tower/adversary/AdversaryTowerRuntimeTest.java:32](../src/gametest/java/kim/biryeong/semiontd/tower/adversary/AdversaryTowerRuntimeTest.java#L32) 진화 공유점수·판매 반환. [src/test/java/kim/biryeong/semiontd/tower/adversary](../src/test/java/kim/biryeong/semiontd/tower/adversary)의 CombatContract/ProgressState/RivalLedger/FormStatsView가 수치·원장 근거다.
- **이번 변경 없음.** 후보는 AoE 중복 수집/거리 top-K 및 hit-time snapshot 비용이지만 shared-area revalidation/피해 callback/타이 순서를 먼저 계측해야 한다. 순수한 일반 소환 큐는 없으며 rival 변환이 해당 역할이다.

<a id="builder-mage"></a>

### 14. 마도사 빌더 — Mage

판정: **변경: 일반 주문 primary만 안정 min 선택**. 선정 변경 경로 최종 gate4 통과. 새 성능 미측정.

복잡도: T=lane 타워, M=lane 활성 몬스터, K=필터 대상, C=회로, P=소환 대기, J=5장 창 조커. 별도 표기 없는 줄은 기준 HEAD의 위치다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | core 조회→pending/cooldown→일반 primary |
| attack | 일반/세계/미사일 주문·mana refund |
| target | 일반만 min; 세계/미사일 목록 유지 |
| AoE | line/chain/bomb/frost의 순차 조회 |
| stack | 마나·주문 횟수·pending 상태 |
| summon / absorb | 일시 타워 교체; 정규 소환/흡수 없음 |
| sync | 실피해/환급 순서·종료 map clear |
| VFX | 주문/line/area·bossbar |

#### 경로·주기·비용·경계·수명과 회귀 근거

**매틱/공격/조회/범위**

- [src/main/java/kim/biryeong/semiontd/tower/mage/MageWizardTower.java:129-210](../src/main/java/kim/biryeong/semiontd/tower/mage/MageWizardTower.java#L129): wave-active 이후 entity+선택 주문+살아있는 core 확인은 cooldown/예약 대기보다 먼저 실행한다. 매 active wizard tick에 `MageTowerRuntime.hasCore:22-26` O(T) 검사. missile/bomb/collapse 분기, cast cooldown, mana retry 순서 유지.
- [src/main/java/kim/biryeong/semiontd/tower/mage/MageTowerRuntime.java:29-49](../src/main/java/kim/biryeong/semiontd/tower/mage/MageTowerRuntime.java#L29)(시작 HEAD): activeMonsters의 logical health/ID→world 엔티티→alive/removed/runtime 필터로 live snapshot O(M), stealth/dominated 제외 후 wave 우선·진행도 내림차순 stable sort. 승인 변경은 일반 tick의 첫 대상만 min. 두 income 태그 getter와 laneProgress는 순수 값 조회여서 sort 이전 범위 필터가 UUID/RNG/lazy logicalId를 바꾸지 않는다. 동률은 최초 encounter.
- missile `Wizard:212-229`는 기존 전체 목록 경로 유지. wind cutter `:233-261`은 line projection [0,maxRange] 및 직선거리 <=width², 정렬 후 cap을 유지한다. chain `:264-280`은 live list를 복제하고 매 hit 후 remove→다음 nearest 선택을 반복(O(chainLength*M)); 피해 callback 후 새 기준 위치/후속 selection이므로 목록 전체 캐시는 신중해야 한다.
- frost/bomb `:284-337,351-362`는 nearest 정렬 cap + UUID 집합 + shared area 두 번째 탐색. closed radius <= 유지. collapse `:339-348`는 지연 후 source 중심 shared area. `damageOne:365-378`은 매 타깃마다 현재 마나/주변 증폭/랭크를 다시 읽으므로 kill refund 이후 다음 target의 배율을 캐시해선 안 된다. `damageArea:382-393`는 호출 시 한 번 계산하는 별도 계약.
- prophet [src/main/java/kim/biryeong/semiontd/tower/mage/MageProphetTower.java:72-97](../src/main/java/kim/biryeong/semiontd/tower/mage/MageProphetTower.java#L72): armed/succeeded/core/생존 검사 뒤 매틱 live snapshot 전체 생성→처음 일치하는 income 예언→TRUE damage 결과에서만 성공·보상. 단일 findFirst에 live 리스트 생성이 남지만 isDestroyed/core 확인의 side effect를 제거하지 않았다.

**스택/지원/수명/VFX**

- 주문 횟수/랭크는 `Wizard:515-537`의 마나 지출 성공 이후 증가. refund once, third-cast replay, world-cast cap과 mana consumption 순서 보존. 주변 마법사 목록 `Runtime:69-80`은 health>0, 같은 owner, position 거리 <=를 쓰며 amplification/barrier(:432-500)는 O(T) 목록을 매 공격 계산/피격 시 만든다. 동결한 범위 이외 safe 후보다.
- core 제거 [src/main/java/kim/biryeong/semiontd/tower/mage/MageCoreTower.java:29-49](../src/main/java/kim/biryeong/semiontd/tower/mage/MageCoreTower.java#L29)는 reset과 실제 lane membership 제거를 구분, mana clear 뒤 wizard/prophet 예약을 교체한다. [src/main/java/kim/biryeong/semiontd/tower/mage/MageTowerLifecycle.java:12-35](../src/main/java/kim/biryeong/semiontd/tower/mage/MageTowerLifecycle.java#L12)는 죽지 않은 타워 mana 생산 합산 후 temporary tower 복원. 스냅샷과 original/current 위치·health ratio·spellCasts 유지가 필요하다.
- [src/main/java/kim/biryeong/semiontd/tower/mage/MageStates.java:9-21](../src/main/java/kim/biryeong/semiontd/tower/mage/MageStates.java#L9) keyed state 및 시작/탈락/teardown 후 clear. wizard local currentLane/bomb center/timers는 tower 수명. 일반 소환/흡수 시스템은 없음. temporary spell 형태 교체는 존재한다. VFX는 secondary attack, prophecy lightning, buff area와 공통 area 경로.

**기존 검증/판정**

- [src/gametest/java/kim/biryeong/semiontd/tower/mage/MageGameTest.java](../src/gametest/java/kim/biryeong/semiontd/tower/mage/MageGameTest.java) 시작 HEAD `:33` third replay/world cap, `:79` flood 생산, `:112` 예언 first income, `:179` dead-unit mana·final-defense support, `:243` wind 10target/magic. [src/test/java/kim/biryeong/semiontd/tower/mage/MageTowerCatalogTest.java](../src/test/java/kim/biryeong/semiontd/tower/mage/MageTowerCatalogTest.java), MageAugmentsTest가 config/catalog/rank 공식 근거.
- **일반 주문 primary 선택만 구현, 실행 검증 대기.** 미사일 단일 타깃 및 prophet live-list short-circuit, AoE top-K, 주변 wizard anyMatch는 별도 후보다. hasCore를 cooldown 이후로 옮기는 방식은 isDestroyed callback 및 코어 파괴 timing이 달라질 수 있어 무조건 안전하지 않다.

<a id="builder-engineer"></a>

### 15. 기술자 — Engineer

판정: **유지: 앞선 min·회로 개선과 구분; 나머지 수명 검토**. 개별 조사 완료, 새 성능 미측정. 통합 gate4 통과를 개별 전수실행으로 확대하지 않는다.

복잡도: T=lane 타워, M=lane 활성 몬스터, K=필터 대상, C=회로, P=소환 대기, J=5장 창 조커. 별도 표기 없는 줄은 기준 HEAD의 위치다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | plate cooldown·runtime 복구·trap queue |
| attack | 발판/회로·지연 발사 |
| target | 앞선 min 선택·현재 필터 |
| AoE | 회로/발판 영역 및 연결 snapshot |
| stack | 회로·plate cooldown·pending shots |
| summon / absorb | 전용 소환·흡수는 없음 |
| sync | BFS/cache 수명·block 복원·weak map |
| VFX | 공통/전용 발판·trap·bossbar |

#### 경로·주기·비용·경계·수명과 회귀 근거

- [src/main/java/kim/biryeong/semiontd/tower/engineer/EngineerGolemTower.java:127-180](../src/main/java/kim/biryeong/semiontd/tower/engineer/EngineerGolemTower.java#L127): 매틱 ensure entity, 이름/무적/NoAI 상태 재적용, plateCooldown map replaceAll+removeIf O(P), last/target plate 조회, 이동. 발판 재선택은 `:196-210`의 **기존 min** O(T), priority 내림차순→distance→x→z, exact tie 최초 encounter. `lane.towerAt`는 기존 위치조회 API다. `:248-306` UUID world lookup으로 golem 재생성, remove `:71`에서 discard. 새 소환 최적화 없음.
- [src/main/java/kim/biryeong/semiontd/tower/engineer/EngineerTrapTower.java:187-266](../src/main/java/kim/biryeong/semiontd/tower/engineer/EngineerTrapTower.java#L187): wave/alive/world/source 확인 후 pending shots를 매틱 훑고 due 항목 처리; 자동공장 설정 주기, 지연 TNT, physical power→recent plate, edge activation, fuse, active duration, door/dispenser/slime 순. `plateActivation:680-691`은 매 연결조회 때 lane에서 owner 회로 Map<C> 새 구성. [src/main/java/kim/biryeong/semiontd/tower/engineer/EngineerTrapSignalController.java:21-67](../src/main/java/kim/biryeong/semiontd/tower/engineer/EngineerTrapSignalController.java#L21)는 이미 역방향 BFS O(C+E)+최선 plate O(C). 주어진 방향/최근 시각의 inclusive bounds/positive-distance cycle/좌표 tie를 보존해야 한다. topology cache는 회로배치·제거·upgrade·방향·pressed timestamp invalidation이 필요해 미도입.
- dispenser `Trap:436-442`는 live list O(M)→preferred UUID→progress max. 피격 후 followup마다 새 targets를 선택한다. pierce는 shared area의 nearestTargets, door는 코드 기본 10tick retarget, slime는 활성 매틱 slow 3ticks 갱신, TNT는 폭발 시 pressCount로 cap 결정, piston는 radius/immune-until/final-defense 필터→거리 정렬 cap→teleport 순서(:394-519).
- trap pendingShots/Explosion 및 source state는 disable/reset/remove/final-defense에서 정리(:149-184,530-545). 문 upper ElementHolder는 onRemoved에서 destroy(:630). Circuit `:62-95`는 원래 블록을 저장하고 자기 배치블록이 남아 있을 때만 복원. static plates는 WeakHashMap<Level,Set<BlockPos>> 및 빈 set 제거(:200). owner press count는 match/elimination/pre-lane close에서 정리. 흡수·전투 스택 시스템은 없고 press count가 family 누적량 역할.
- VFX: golem press 직접 particles(:309), trap activation/fuse periodic VFX(:548-587), 실제 area path. 보스바는 wire 개수를 다시 센다([src/main/java/kim/biryeong/semiontd/tower/engineer/EngineerRedstoneBossBarService.java:94](../src/main/java/kim/biryeong/semiontd/tower/engineer/EngineerRedstoneBossBarService.java#L94) 주변). 고정 이름 매틱 재적용 및 작은 배열 기본값 생성(:720-730)은 계측 없는 수동 micro optimization 대상으로 선택하지 않았다.
- 기존 근거: [src/test/java/kim/biryeong/semiontd/tower/engineer/EngineerGolemTargetSelectionTest.java:51,90,107,119](../src/test/java/kim/biryeong/semiontd/tower/engineer/EngineerGolemTargetSelectionTest.java#L51) stable-sort 대비 min/우선순위/타이/상태변경; [src/test/java/kim/biryeong/semiontd/tower/engineer/EngineerTrapSignalControllerTest.java:29,58,75,91](../src/test/java/kim/biryeong/semiontd/tower/engineer/EngineerTrapSignalControllerTest.java#L29) BFS/방향/사이클/시각/좌표. [src/gametest/java/kim/biryeong/semiontd/tower/engineer/EngineerGameTest.java:78,193,240,317,375,439,497,602,668,742,797,840,875](../src/gametest/java/kim/biryeong/semiontd/tower/engineer/EngineerGameTest.java#L78) actual block/copper golem/direction/door/press/TNT/dispenser/piston/slime/augment 억제.
- **이미 완료된 발판·BFS 외 신규 변경 없음.** 회로 snapshot 비용은 남아 있고 각 trap의 live target 리스트/피스톤 sort는 별도 최적화 후보다. 정리 코드가 있어 확정 누수로 보고하지 않았다.

<a id="builder-insect"></a>

### 16. 벌레 빌더 — Insect

판정: **유지: isDestroyed 부작용·부활·소환 큐 순서**. 개별 조사 완료, 새 성능 미측정. 통합 gate4 통과를 개별 전수실행으로 확대하지 않는다.

복잡도: T=lane 타워, M=lane 활성 몬스터, K=필터 대상, C=회로, P=소환 대기, J=5장 창 조커. 별도 표기 없는 줄은 기준 HEAD의 위치다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | isDestroyed 부작용·revive·소환 flush |
| attack | 곤충별 공격·죽음 explosion |
| target | 살아있는 linked spawner 탐색 |
| AoE | HATCH top2·폭발 영역 |
| stack | 부활 anchor·종류별 link/larva 상태 |
| summon / absorb | 유충 FIFO·부활·entity 성공 조건 |
| sync | anchor death·remove/reset·queue clear |
| VFX | 첫 anchor·소환/폭발 VFX |

#### 경로·주기·비용·경계·수명과 회귀 근거

- 기본공격 타깃 override 없음: 공통 goal 사용. bee만 [src/main/java/kim/biryeong/semiontd/tower/insect/InsectUnitTower.java:90-104](../src/main/java/kim/biryeong/semiontd/tower/insect/InsectUnitTower.java#L90) contact 사거리 <=에서 직접 죽는 정상 lifecycle로 진입하고 death explosion을 사용한다. spawner [src/main/java/kim/biryeong/semiontd/tower/insect/InsectSpawnerTower.java:64-72](../src/main/java/kim/biryeong/semiontd/tower/insect/InsectSpawnerTower.java#L64)는 wave/alive gate 뒤 코드기본 80tick radius pulse.
- [src/main/java/kim/biryeong/semiontd/tower/insect/InsectUnitTower.java:149-194](../src/main/java/kim/biryeong/semiontd/tower/insect/InsectUnitTower.java#L149)의 **isDestroyed는 순수 조회가 아니다**: 최초 죽음 시 부활 anchor 선택, 상태를 먼저 commit하고 larvae 예약·폭발 피해 callback을 실행한다. 부활 대기 중 `isDestroyed:154`와 `tick:198-211` 둘 다 `livingLinkedSpawners` 전체 목록 O(T) 생성. linked lookup `:390-408`는 owner→living anchor(여기서 spawner.isDestroyed 호출)→기존 original-position key 또는 colony radius. List.contains keys로 최악 O(T*anchorCount). 단순 anyMatch로 바꾸면 뒤쪽 spawner의 isDestroyed/death callback 횟수가 달라질 수 있어 바로 바꾸지 않았다.
- death snapshot은 spawn anchor의 originalPosition distinct 순서 보존(:167-175), revival은 처음 살아있는 anchor가 VFX source(:337-353). revival tick 증가→max health effect 초기화→새 entity 생성→주변 부활시간 단축 순서. HATCH nearest 2(설정값) 후보는 O(T log T) 정렬이며 closed radius와 lane encounter tie를 보존해야 한다(:354-364).
- 폭발은 죽을 때의 currentMaxHealth 기반, 실제 범위 shared area + validAttackTarget, MAGIC pipeline(:181-191). revivalsThisRound 기반 health loss/받는피해 스택 및 first fresh buff(:85-113), shell once(:142) reset/copy(:226-254) 모두 존재. 흡수는 없음.
- [src/main/java/kim/biryeong/semiontd/tower/insect/InsectAugments.java:42-60](../src/main/java/kim/biryeong/semiontd/tower/insect/InsectAugments.java#L42)은 실제 source stats로 temporary larvae를 예약한다. `flush:63-72`는 lane별 pending snapshot→removeAll→source membership 재확인→FIFO addTower. O(P²) removeAll+O(P*T) membership 가능하나 기본 active cap 8로 경계가 작고 순서·다음 snapshot 참여가 핵심이다. pending record는 lane/source/larva를 강하게 참조하므로 `JobInsectLifecycle:6-12` 및 `JobLaneLifecycle:80` clear가 필수. 이미 있음.
- 기존 근거: [src/gametest/java/kim/biryeong/semiontd/tower/insect/InsectGameTest.java:36](../src/gametest/java/kim/biryeong/semiontd/tower/insect/InsectGameTest.java#L36) HATCH+shell, `:83` colony anchor 취소, `:106` larvae 8슬롯 재사용, `:187,228,280` death position/final-defense/owner, `:307` deathHealth/magic/exact-radius, `:374` 제거·spawner 죽음 비폭발, `:413,463,505` bee contact와 9종 explosion. [src/gametest/java/kim/biryeong/semiontd/tower/insect/InsectTowerAugmentSelectionTest.java](../src/gametest/java/kim/biryeong/semiontd/tower/insect/InsectTowerAugmentSelectionTest.java) 및 [src/test/java/kim/biryeong/semiontd/tower/insect/InsectTowerCatalogTest.java](../src/test/java/kim/biryeong/semiontd/tower/insect/InsectTowerCatalogTest.java)도 존재.
- **이번 변경 없음.** 살아있는 anchor 유무조회 리스트 할당은 후보지만 순수 predicates로 바꾸기 전 side-effect callback 동등성 증명이 필요. 목록 순서와 부활 VFX source가 중요한 경로는 count/min만으로 대체할 수 없다.

<a id="builder-futureagency"></a>

### 17. 미래기관 빌더 — FutureAgency

판정: **변경: 밀집 통제 개수 계산에서 정렬 제거**. 선정 변경 경로 최종 gate4 통과. 새 성능 미측정.

복잡도: T=lane 타워, M=lane 활성 몬스터, K=필터 대상, C=회로, P=소환 대기, J=5장 창 조커. 별도 표기 없는 줄은 기준 HEAD의 위치다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | Agent withdrawal/opening·피해 생존 스캔 |
| attack | 밀집 damage count·제압 공격 |
| target | count 필터 공유; 제압 정렬 유지 |
| AoE | 동일 범위/중복 lane candidate |
| stack | 정책·crossfire window·opening |
| summon / absorb | carry clone·withdraw/linked source |
| sync | UUID window·wave/reset/final-defense |
| VFX | Agent palette·정책/공격 이벤트 |

#### 경로·주기·비용·경계·수명과 회귀 근거

- agent 매틱 [src/main/java/kim/biryeong/semiontd/tower/futureagency/FutureAgencyAgentTower.java:75-80](../src/main/java/kim/biryeong/semiontd/tower/futureagency/FutureAgencyAgentTower.java#L75)은 withdrawn이면 super 생략, opening RETURN 종료 tick에만 state refresh. 기본 COMBAT 타깃은 progress max O(K)(:265-269). 공격 보너스는 매 공격 [src/main/java/kim/biryeong/semiontd/tower/futureagency/FutureAgencyBalance.java:84-91](../src/main/java/kim/biryeong/semiontd/tower/futureagency/FutureAgencyBalance.java#L84) survivor count O(T); escort/RESCUE 피격 bonus도 living state 조회와 O(T) scan(:304-330,518-527).
- **승인 변경 지점**: 밀집통제 `:292-296`는 순서가 필요 없는 count였음. old `targets:539-547`의 hasEntity→world lookup→entity class→entity alive→distance²<=radius² 필터를 그대로 공유. runtime health를 새로 검사하지 않고, removed/world guards를 새로 추가하지 않았다. 동일 엔티티 중복 lane 항목도 모두 세며 excluded는 UUID가 아닌 참조 `!=`이다. suppression `:391-403`는 기존 progress desc/UUID text asc 정렬→primary 제외→cap-1→UUID Set→shared area 순서를 그대로 유지했다.
- 원본/생존본 연결은 owner+laneId+originalPosition; waveSurvivors `:134-144`는 전체 wave hook 후 캡처. TIME_LOOP는 snapshot health를 복구하며 매 대상 lane membership을 다시 확인(:335-358). carry `:459-498`는 살아있는 linked encounter 순서로 cap 보존, 남는 survivor를 제거, 새 survivor를 한 개 생성/withdraw. 업그레이드·판매 `:203-220`는 연결된 생존본을 교체/제거한다. 일반 summon queue나 흡수는 없고 carry clone이 family 생성 메커니즘이다.
- crossfire `:406-427`는 원본 소유 UUID→두 종류 lastHit/rooted 기록, window/cooldown, 서로 다른 원본 분리. Map은 waveStarted/reset/final-defense에서 clear(:129-130,158,177). 한 wave 내 공격 대상 UUID 수에 비례하는 메모리는 남으나 전체 경기/월드 누수로 단정하지 않았다. entity 자체는 map에 없다. snapshot survivor 객체들은 round boundary까지 유지되므로 raw 캐시 무효화 주의.
- policy state [src/main/java/kim/biryeong/semiontd/tower/futureagency/FutureAgencyStates.java:77-96](../src/main/java/kim/biryeong/semiontd/tower/futureagency/FutureAgencyStates.java#L77)는 RNG shuffle 순서/fresh pool fallback/maxStacks/선택 횟수 규약. 리더 정책 갱신 [src/main/java/kim/biryeong/semiontd/tower/futureagency/FutureAgencyLeaderTower.java:151-158](../src/main/java/kim/biryeong/semiontd/tower/futureagency/FutureAgencyLeaderTower.java#L151)은 모든 owner agent health→state 갱신; world-save VFX(:161) 목록 순서 유지. state는 match/elimination/teardown 후 clear.
- 기존 근거: [src/gametest/java/kim/biryeong/semiontd/tower/futureagency/FutureAgencyGameTest.java](../src/gametest/java/kim/biryeong/semiontd/tower/futureagency/FutureAgencyGameTest.java) 시작 HEAD `:40` 두 survivor+time loop, `:147` clean-lane 두 선택, `:187` move/attack slow, `:210` survivor cap. [src/gametest/java/kim/biryeong/semiontd/tower/futureagency/FutureAgencyCrossfireTest.java:19,51,76,98](../src/gametest/java/kim/biryeong/semiontd/tower/futureagency/FutureAgencyCrossfireTest.java#L19) 실제 secondary 피해·trigger 억제·원본 분리·40/160tick 경계. [src/test/java/kim/biryeong/semiontd/tower/futureagency/FutureAgencyTowerCatalogTest.java:78,102,123,145,260](../src/test/java/kim/biryeong/semiontd/tower/futureagency/FutureAgencyTowerCatalogTest.java#L78) policy/반복제안/stage/survivor cap.
- **count 최적화 구현, 실행 검증 대기.** wave snapshot O(T²), repeated survivor count/links, suppression sort cap은 별도 후보다. living checks가 entity/death에 연관되어 캐시를 무리하게 도입하지 않았다.

<a id="builder-queen"></a>

### 18. 붉은 여왕 빌더 — Queen

판정: **유지: 강제 선택·카드·runner·외형 갱신 계약**. 개별 조사 완료, 새 성능 미측정. 통합 gate4 통과를 개별 전수실행으로 확대하지 않는다.

복잡도: T=lane 타워, M=lane 활성 몬스터, K=필터 대상, C=회로, P=소환 대기, J=5장 창 조커. 별도 표기 없는 줄은 기준 HEAD의 위치다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | overlay·pulse·runner·enemy/card tick |
| attack | 카드별 공격·splash·heal |
| target | forced valid list·shrink 또는 maxHP |
| AoE | 카드 이중 query·회복 재시도 |
| stack | permanent shrink·poker/joker 상태 |
| summon / absorb | runner 생성/접촉/귀환; 흡수 없음 |
| sync | runner success·owner 종료·overlay |
| VFX | ItemStack/pose 매틱·보스바·카드 이벤트 |

#### 경로·주기·비용·경계·수명과 회귀 근거

- [src/main/java/kim/biryeong/semiontd/tower/queen/QueenTower.java:118-140](../src/main/java/kim/biryeong/semiontd/tower/queen/QueenTower.java#L118) 매틱 super→장비 overlay→설정 pulse→runner 이동/접촉→active enemies→owner card 최근전투+radius anyMatch→charge→dispatch. 적/카드 탐색 O(M+T), final-defense는 team lanes 전체 몬스터 anyMatch(:188-195). [src/main/java/kim/biryeong/semiontd/tower/queen/QueenCardTower.java:132-138](../src/main/java/kim/biryeong/semiontd/tower/queen/QueenCardTower.java#L132) 매틱 overlay 후 HEART가 치료 가능한 타워를 찾는데, 아무도 치료되지 않으면 cooldown을 재설정하지 않아 다음 tick 재탐색한다.
- Queen forced selection `QueenTower:54-64`, card `QueenCardTower:160-175`는 valid 리스트 O(K) 생성 후 아직 충분히 작아지지 않은 min permanentStatScale, 없으면 max maxHealth. card는 valid current target이 축소 대상이면 유지한다. 정확 동률은 encounter, 기본 범위/중앙방어와 강제선택의 빈도는 공통 goal 계약이다. 한 pass로 합치는 후보는 있지만 current target/폴백/side effects 증명이 필요하다.
- card splash `:268-287`는 lane active logical alive→entity→거리 정렬 cap→UUID Set→shared area 재탐색. 공통 nearestTargets로 단순 바꾸면 최초 모집단, exact tie, 제거/불가대상으로 cap이 소진되는 구간이 달라질 수 있다. heart `:290-312`는 REGISTERED 및 family filter, 실제 회복 수치 기록. death AoE(:220)는 분리된 이벤트다.
- shrink [src/main/java/kim/biryeong/semiontd/tower/queen/QueenShrink.java:19-38](../src/main/java/kim/biryeong/semiontd/tower/queen/QueenShrink.java#L19)는 permanent scale floor/현재 logical와entity health 최소값/실행가능 health floor/visual min 및 로그 기반 appliedPoints를 갱신한다. debuff는 persistent source로 attack-speed 감소 cap .70(:46). 단순 스택 합계만으로 치환할 수 없는 경계다.
- [src/main/java/kim/biryeong/semiontd/tower/queen/QueenPoker.java:16-103](../src/main/java/kim/biryeong/semiontd/tower/queen/QueenPoker.java#L16): wave마다 row grouping→coordinate sort→연속 5장 창→조커 rank 배정(최악 O(13^J), J<=5, 최상위 hand에서 조기 종료). 각 leaf의 suit distinct/map/hand 평가 할당, overlapping window 누적 health와 최초 개선 시 카드 배정을 보존. 조커 기존 rank를 먼저 탐색하고 엄격 ordinal 개선만 저장한다. 특정 최적 조합만 찾는 알고리즘은 tie 카드 identity/시각화에 영향을 주므로 이번 범위에서 제외.
- giant [src/main/java/kim/biryeong/semiontd/tower/queen/QueenGiantRunner.java:48-100](../src/main/java/kim/biryeong/semiontd/tower/queen/QueenGiantRunner.java#L48) actual entity spawn 성공 뒤 state attach, 매틱 이동 후 shared area contact; contacted UUID 집합으로 1회, return 시작 시 clear(:85-90). 처치→실제 effectiveMaxHealth 기준 execution 성장→transferShrink 최초 성공 대상 순서가 gameplay 결과를 바꿀 수 있다(:123-162). source entity와 path를 runner가 보유하므로 round reset/QueenStates.clear에서 endRunner/discard 필수(:105-107). 있음. 일반 흡수는 없고 처치 기반 executionHealth 성장이 존재한다.
- overlay [src/main/java/kim/biryeong/semiontd/entity/visual/TowerEquipmentVisual.java:13-48](../src/main/java/kim/biryeong/semiontd/entity/visual/TowerEquipmentVisual.java#L13)는 매 호출 4개 ItemStack.copy + pose 생성 + teleport/회전 동기화. Queen/Card onRemoved에서 discard/null(:93-96 / :107-110). 프레임 보간·장비 상태 변경을 보존해야 하므로 dirty-cache는 계측+실제 GPU 검증 전 미도입. `QueenStates.begin`이 새 state로 교체하는 경우 기존 runner 정리 여부는 호출 수명 점검 후보이나 정상 onMatchStarted 경로 외 재진입 증거가 없어 확정 누수로 보고하지 않는다.
- 기존 근거: [src/gametest/java/kim/biryeong/semiontd/tower/queen/QueenGameTest.java:44](../src/gametest/java/kim/biryeong/semiontd/tower/queen/QueenGameTest.java#L44) damage→attack speed, `:106` joker/guard/row, `:150` return+shrink transfer, `:201` shrink/giant, `:424` path fallback, `:457` interpolation/pose overlay, `:489` splash cap, `:518` heal/death/poker wave snapshot. [src/test/java/kim/biryeong/semiontd/tower/queen/QueenTowerCatalogTest.java](../src/test/java/kim/biryeong/semiontd/tower/queen/QueenTowerCatalogTest.java)의 poker/shrink/config/catalog도 존재.
- **이번 변경 없음.** 가장 눈에 띄는 지속 할당은 overlay와 splash 수집, 드문 고비용은 joker 조합. 게임 결과와 render 동등성을 확인하지 않은 개별 최적화는 강제하지 않았다.

<a id="builder-heroparty"></a>

### 19. 용사 빌더 — HeroParty

판정: **변경: 사제 치유 대상 2개 안정 선택**. 선정 변경 경로 최종 gate4 통과. 새 성능 미측정.

복잡도는 각 항목의 타워 T, 몬스터 M, 지원 제공자 P, 수혜자 H, 영역 Z, 전역 엔티티 E, 관전자 P 등 현장 정의를 따른다. 소스 상수/fallback과 실제 운영 설정은 구분한다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | fake visual·동료 support cooldown |
| attack | 무기·추가타·quest/장비 |
| target | 동료 min·mage 밀도 비교·사제 top2 |
| AoE | world party·Tome·knight/bard provider |
| stack | 공격횟수·quest·장비·모험점수 |
| summon / absorb | 동료는 배치타워; 전투 소환/흡수 없음 |
| sync | 비율보존·wave/reset/upgrade·state clear |
| VFX | fake player scan/packet·secondary/heal |

#### 경로·주기·비용·경계·수명과 회귀 근거

- **매틱/상태동기화:** [src/main/java/kim/biryeong/semiontd/tower/hero/HeroPartyTower.java:69](../src/main/java/kim/biryeong/semiontd/tower/hero/HeroPartyTower.java#L69) → `FakePlayerTowerVisuals.tick`; 동료는 [src/main/java/kim/biryeong/semiontd/tower/hero/HeroCompanionTower.java:168](../src/main/java/kim/biryeong/semiontd/tower/hero/HeroCompanionTower.java#L168) → support.tick. support는 매틱 role/cooldown 분기 후 knight/bard cooldown20(실제21tick), priest 설정 interval+1([src/main/java/kim/biryeong/semiontd/tower/hero/HeroCompanionSupportController.java:52-77](../src/main/java/kim/biryeong/semiontd/tower/hero/HeroCompanionSupportController.java#L52)). base tick이 죽음으로 return해도 override의 support/visual 호출은 이어지는 기존 계약이다.
- **타깃/공격:** [src/main/java/kim/biryeong/semiontd/tower/hero/HeroCompanionTower.java:56-74,407](../src/main/java/kim/biryeong/semiontd/tower/hero/HeroCompanionTower.java#L56) archer boss/maxHP/거리, rogue HP비율/거리, mage 주변3block밀도/거리의 min. Mage comparator가 비교마다 양쪽에 O(M) nearbyCount를 반복해 O(M²), 스트림·comparator 구성 할당. 한 후보당 밀도 1회 계산으로 반복 수를 줄일 수 있으나 현재 패치에서 제외. [src/main/java/kim/biryeong/semiontd/tower/hero/HeroTower.java:146-168](../src/main/java/kim/biryeong/semiontd/tower/hero/HeroTower.java#L146) 각 기본공격에서 무기 횟수/퀘스트 갱신 후 무기별 효과, 추가증강. `:306-322` 검1/장궁2/지팡이2 추가타는 월드 후보 안정거리정렬 O(MlogM), 고정 소수만 소비. common nearestTargets는 tie가 logicalId라 그대로 대체하면 안 됨.
- **범위/지원:** [src/main/java/kim/biryeong/semiontd/tower/hero/HeroPartyTower.java:134-147](../src/main/java/kim/biryeong/semiontd/tower/hero/HeroPartyTower.java#L134) 같은owner 살아있는 파티를 source AABB+96에서 월드조회하고 거리정렬·copy; 단순 lane 목록과 동치 아님. Tome [src/main/java/kim/biryeong/semiontd/tower/hero/HeroTower.java:373-385](../src/main/java/kim/biryeong/semiontd/tower/hero/HeroTower.java#L373) HP정렬이 추가되어 거리순이 동률의 2차 기준이 됨. 사제 [src/main/java/kim/biryeong/semiontd/tower/hero/HeroCompanionSupportController.java:80](../src/main/java/kim/biryeong/semiontd/tower/hero/HeroCompanionSupportController.java#L80)는 거리제한 없는 lane소유 파티 치료이며 entity 없으면 logical health fallback도 유지. knight/bard `:157,188,227,261` AoE 각 수혜타워마다 다시 lane의 strongestProvider를 찾음: 지원타워 P/수혜 H에 O(P*H*T) 비용 가능. 최고tier 동률 첫 encounter·grid3D 거리<=·includeSelf 정책·효과 source 공유 중요.
- **방어/스택/소환흡수:** [src/main/java/kim/biryeong/semiontd/tower/hero/HeroPartyTower.java:188-206](../src/main/java/kim/biryeong/semiontd/tower/hero/HeroPartyTower.java#L188) 피격 또는 상세UI마다 lane 몬스터 snapshot O(M)/할당해 자신을 목표로 하는 몹 계산. 공격횟수·퀘스트/장비/모험점수 state가 있고, [src/main/java/kim/biryeong/semiontd/tower/hero/HeroTower.java:136,173](../src/main/java/kim/biryeong/semiontd/tower/hero/HeroTower.java#L136), [src/main/java/kim/biryeong/semiontd/tower/hero/HeroCompanionTower.java:162,174](../src/main/java/kim/biryeong/semiontd/tower/hero/HeroCompanionTower.java#L162)에서 wave reset과 upgrade copy. 동료는 배치된 타워이며 별도 동적 전투 소환/흡수 자원 서비스 없음. 치료는 drain이 아님.
- **VFX/수명:** [src/main/java/kim/biryeong/semiontd/tower/hero/HeroPartyTower.java:35,48,54,63,111](../src/main/java/kim/biryeong/semiontd/tower/hero/HeroPartyTower.java#L35) placement/변경/제거/죽음/체력비율보존 갱신. [src/main/java/kim/biryeong/semiontd/tower/hero/FakePlayerTowerVisuals.java:414-474](../src/main/java/kim/biryeong/semiontd/tower/hero/FakePlayerTowerVisuals.java#L414) 매tick world player P 전수검사, HashSet visible+tracking copy, 2tick마다 움직였을 때만 전송하되 viewers() 목록을 teleport/head 각각 재구성. static IdentityHashMap `:65`는 `:164` 명시적 remove로 해제, [src/main/java/kim/biryeong/semiontd/tower/hero/HeroPartyStates.java:29](../src/main/java/kim/biryeong/semiontd/tower/hero/HeroPartyStates.java#L29)는 bossbar도 제거, [src/main/java/kim/biryeong/semiontd/job/JobBuilderLifecycle.java:160](../src/main/java/kim/biryeong/semiontd/job/JobBuilderLifecycle.java#L160)은 lane teardown 후 state clear. **누수후보:** anchor 외부삭제/월드불일치 시 inner Visual.tick(:415)→inner remove(:509)는 static VISUALS entry 자체를 제거하지 않음. 정상 onRemoved/onDeath는 제거하므로 일반누수 확정 아님; 외부삭제 재현과 공통 fakevisual 모든사용자 검토 필요. 가문 전용 파일이라도 이 클래스는 Pirate/MagicSchool/Pet/Succubus도 호출하므로 이번 수정 제외.
- **기존검증:** [src/test/java/kim/biryeong/semiontd/tower/hero/HeroPartyTowerCatalogTest.java](../src/test/java/kim/biryeong/semiontd/tower/hero/HeroPartyTowerCatalogTest.java), [src/test/java/kim/biryeong/semiontd/tower/hero/HeroCompanionSupportControllerTest.java](../src/test/java/kim/biryeong/semiontd/tower/hero/HeroCompanionSupportControllerTest.java), [src/test/java/kim/biryeong/semiontd/tower/hero/HeroAugmentsTest.java](../src/test/java/kim/biryeong/semiontd/tower/hero/HeroAugmentsTest.java), [src/test/java/kim/biryeong/semiontd/tower/hero/FakePlayerTowerVisualsTest.java](../src/test/java/kim/biryeong/semiontd/tower/hero/FakePlayerTowerVisualsTest.java); HeroTowerIntegrationTest의 placement/equipment/quest/fakeplayer lifecycle, focusFire, 동료전투, 지원 unlock/중첩/연속성, HeroCentralChainAttackTest. 신규 priest 선택 회귀 추가. 실제 GPU·멀티플레이 시각검증 미실시.
- **판정:** 사제 top2 적용. 나머지 각자 다른 후보집합/동률/퀘스트 callback/체력변경이 있으므로 무리한 shared snapshot/cache 금지.

<a id="builder-atlantis"></a>

### 20. 아틀란티스 빌더 — Atlantis

판정: **후보: 영역·압력 반복 검색; 소비/중첩 계약 보류**. 개별 조사 완료, 새 성능 미측정. 통합 gate4 통과를 개별 전수실행으로 확대하지 않는다.

복잡도는 각 항목의 타워 T, 몬스터 M, 지원 제공자 P, 수혜자 H, 영역 Z, 전역 엔티티 E, 관전자 P 등 현장 정의를 따른다. 소스 상수/fallback과 실제 운영 설정은 구분한다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | dolphin 압력·zone 중첩 갱신 |
| attack | 물살/연쇄·자원 소비 |
| target | world/nearest 안정 거리 선택 |
| AoE | 3D 피해·XZ 지원·영역 overlap |
| stack | 압력·zone·강한 owner 우선 |
| summon / absorb | 자체 소환/흡수 없음 |
| sync | 배치/죽음 영역 rebuild·만료/close |
| VFX | zone/공격 전용 및 공통 event |

#### 경로·주기·비용·경계·수명과 회귀 근거

- **매틱:** [src/main/java/kim/biryeong/semiontd/tower/atlantis/AtlantisTower.java:120-145](../src/main/java/kim/biryeong/semiontd/tower/atlantis/AtlantisTower.java#L120) dolphin은 매tick releaseLapsedPressure; turtle g2도 same, turtle zone scan은 런타임 설정(소스 fallback10tick), zone VFX fallback40tick([src/main/java/kim/biryeong/semiontd/tower/atlantis/AtlantisBalance.java:24,89](../src/main/java/kim/biryeong/semiontd/tower/atlantis/AtlantisBalance.java#L24)). tsunami는 wave부터 설정120tick마다. axolotl execute는 설정40+1tick, action 실패면 base execute 재시도 특성 존재([src/main/java/kim/biryeong/semiontd/tower/atlantis/AtlantisTower.java:244,283](../src/main/java/kim/biryeong/semiontd/tower/atlantis/AtlantisTower.java#L244)).
- **대상·범위:** 기본공격은 shared goal. zone owned filter는 매scan 리스트 구성(`:159`), zone마다 AoE monster 스캔 후 각 target에 모든zone overlapCount O(Z), 그리고 전체lane ally 스캔+각 overlap O(T*Z)(`:175-234`, [src/main/java/kim/biryeong/semiontd/tower/atlantis/AtlantisStates.java:73](../src/main/java/kim/biryeong/semiontd/tower/atlantis/AtlantisStates.java#L73)). 전체 Z존이면 높은 경우 O(Z²*(M+T)), 월드 broadphase와 hit수를 따로 고려해야 한다. 구형 [src/main/java/kim/biryeong/semiontd/tower/atlantis/PressureZone.java:24](../src/main/java/kim/biryeong/semiontd/tower/atlantis/PressureZone.java#L24)은 3D <=, 지원bonus [src/main/java/kim/biryeong/semiontd/tower/atlantis/AtlantisTower.java:629](../src/main/java/kim/biryeong/semiontd/tower/atlantis/AtlantisTower.java#L629)는 grid XZ <=: 서로 통합 금지.
- **공격·스택·연쇄:** [src/main/java/kim/biryeong/semiontd/tower/atlantis/AtlantisTower.java:291-338](../src/main/java/kim/biryeong/semiontd/tower/atlantis/AtlantisTower.java#L291) 실피해>0만 스택, killed 우선 burst, zone 증폭·axolotl/conduit bonus는 각각 lane O(T) 조회(`:598`). 압력 주인은 더 강한 damage만 교체하고 같은damage는 기존주인 유지([src/main/java/kim/biryeong/semiontd/tower/atlantis/AtlantisPressure.java:112-120](../src/main/java/kim/biryeong/semiontd/tower/atlantis/AtlantisPressure.java#L112)). `monstersFrom(:77)`는 전플레이어 공유 ENTRIES E 전수스캔+copy를 매dolphin마다 수행해 O(D*E)/tick, 후 각 압력대상 zone scan. owner/source 인덱스는 source 교체·삭제·연쇄 callback·HashMap encounter 폭발순서 보존 검증 필요하여 보류.
- **삭제/결정성:** [src/main/java/kim/biryeong/semiontd/tower/atlantis/AtlantisTower.java:359-390](../src/main/java/kim/biryeong/semiontd/tower/atlantis/AtlantisTower.java#L359) vanished/removed는 pressure entry 제거, dead는 PlayerLane 처치통지까지 지연, expiry·zone이탈 시 폭발. `:402-460`은 압력 consume→연쇄 enter→전이→피해/재귀→finally exit 순서. [src/main/java/kim/biryeong/semiontd/tower/atlantis/AtlantisPressure.java:202-246](../src/main/java/kim/biryeong/semiontd/tower/atlantis/AtlantisPressure.java#L202)은 unique/depth cap, owner별 ThreadLocal chain을 finally에서 해제. `:496-506` nearby는 모두 수집→안정거리정렬→최대 count이므로 encounter 동률. common nearestTargets는 UUID tie 및 candidate 수<=limit일 때 정렬하지 않으므로 **단순 전환 거절**. tsunami의 모든후보 sort 후 최대거리 선택도 첫 zone/동률 보존해야 한다.
- **상태/수명/VFX:** placement/remove/sold/final-defense/reset에서 전체zone rebuild([src/main/java/kim/biryeong/semiontd/tower/atlantis/AtlantisTower.java:77-115](../src/main/java/kim/biryeong/semiontd/tower/atlantis/AtlantisTower.java#L77)), 실제 death는 deceased 명시제외 후 rebuild(`:532-553`). [src/main/java/kim/biryeong/semiontd/tower/atlantis/AtlantisStates.java:95-167](../src/main/java/kim/biryeong/semiontd/tower/atlantis/AtlantisStates.java#L95)은 anchor선택·lane경로·owners중간목록·각zone 생성 O(T+Z+path) 및 원소수 별 할당. 재구성은 흔하지만 grid 위치 변경/사망/전방재배치의 gameplay 계약. static ZONES/ENTRIES는 start/elimination 및 lane 종료 후 전참가자 clear([src/main/java/kim/biryeong/semiontd/job/JobAtlantisLifecycle.java:8](../src/main/java/kim/biryeong/semiontd/job/JobAtlantisLifecycle.java#L8), [src/main/java/kim/biryeong/semiontd/job/JobBuilderLifecycle.java:149](../src/main/java/kim/biryeong/semiontd/job/JobBuilderLifecycle.java#L149)). 존VFX는 throttled, burstVFX는 폭발마다([src/main/java/kim/biryeong/semiontd/tower/atlantis/AtlantisVfx.java:86,110](../src/main/java/kim/biryeong/semiontd/tower/atlantis/AtlantisVfx.java#L86)) 고정 wall columns/rays+샘플hit. 소환/생명흡수 없음; pressure 전이는 별도 상태전이.
- **기존검증:** [src/test/java/kim/biryeong/semiontd/tower/atlantis/AtlantisPressureTest.java](../src/test/java/kim/biryeong/semiontd/tower/atlantis/AtlantisPressureTest.java)(소유/경계/chain), AtlantisTowerCatalogTest/AtlantisVfxTest; [src/gametest/java/kim/biryeong/semiontd/tower/atlantis/AtlantisTowerRuntimeTest.java](../src/gametest/java/kim/biryeong/semiontd/tower/atlantis/AtlantisTowerRuntimeTest.java)(시작/탈락/종료 clear), [src/gametest/java/kim/biryeong/semiontd/tower/atlantis/AtlantisTowerIntegrationTest.java](../src/gametest/java/kim/biryeong/semiontd/tower/atlantis/AtlantisTowerIntegrationTest.java)(전이2명·연쇄12명·거북죽음12명·tsunami시간/이동·존capacity/소유/삭제/미드/아군방어·burst attribution). 별도 owner/source index의 순서동치·reentrant mutation 성능회귀는 아직 없음.
- **판정:** 높은 중복비용 확인, 현재 안전후보보다 상태무효화와 동률 위험이 커서 변경 보류. 최적화 완료로 표시하지 않는다.

<a id="builder-plant"></a>

### 21. 식물 빌더 — Plant

판정: **후보: 토양 count; 시점·지형·RNG 계약 보류**. 개별 조사 완료, 새 성능 미측정. 통합 gate4 통과를 개별 전수실행으로 확대하지 않는다.

복잡도는 각 항목의 타워 T, 몬스터 M, 지원 제공자 P, 수혜자 H, 영역 Z, 전역 엔티티 E, 관전자 P 등 현장 정의를 따른다. 소스 상수/fallback과 실제 운영 설정은 구분한다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | soil pulse·root tick·gardener execute |
| attack | 종별 공격·mine·dash·성장 |
| target | 종별 world/lane 후보 구별 |
| AoE | soil/floor·XZ trigger 대 sphere |
| stack | growth·soil·crit RNG·hit UUID |
| summon / absorb | gardener drain/dominate; 소환 구분 |
| sync | 실HP 합산·지형 복원·source 정리 |
| VFX | soil/root·dash·치료/범위 event |

#### 경로·주기·비용·경계·수명과 회귀 근거

- **매틱/주기:** [src/main/java/kim/biryeong/semiontd/game/PlayerLane.java:573](../src/main/java/kim/biryeong/semiontd/game/PlayerLane.java#L573)에서 토양환경 1회/tick. [src/main/java/kim/biryeong/semiontd/tower/plant/PlantSoilEnvironment.java:30-86](../src/main/java/kim/biryeong/semiontd/tower/plant/PlantSoilEnvironment.java#L30): soil없으면 return, global 환경pulse(default20tick) 또는 roots 증강일때 진행; pulse는 전체tower 두 성장종류 각각 합산+적용(`:95-135` 총4번 snapshot), then monster snapshot O(M). roots이면 pulse아닌 tick도 스캔. monster별 owner 동일 MonsterDataKey/Identifier를 새 생성(`:76`)하고 첫tower currentRound를 읽음; key는 hoist 가능한 작은후보이나 피해 callback 이후 라운드/roster 읽기는 달라질 수 있어 단순 전체호이스팅 제외.
- **토양조회/스택:** [src/main/java/kim/biryeong/semiontd/tower/plant/PlantSoilStates.java:97](../src/main/java/kim/biryeong/semiontd/tower/plant/PlantSoilStates.java#L97) column HashMap 기대O(1), soil별 count `:139`는 매번 전체 tile S 스캔. [src/main/java/kim/biryeong/semiontd/tower/plant/PlantCombatTower.java:106](../src/main/java/kim/biryeong/semiontd/tower/plant/PlantCombatTower.java#L106) bloom 피해 조회 때 count O(S), 환경은 mycelium/desert 각각 count 1회; owner별 enum count index 가능하나 terraform 성공/실패·release·clear 모두 원자일치 확인 필요. terraform `:37`은 (2r+1)² columns * floor 높이H, release `:62` O(S)+조건부 원복. 구조적 개선후보이나 현재 패치 미적용. 성장라운드/data map copy, 첫 배치 highest동일family상속, round reset증가, addGrowth시 기존 HP비율보존([src/main/java/kim/biryeong/semiontd/tower/plant/PlantCombatTower.java:81,193,204](../src/main/java/kim/biryeong/semiontd/tower/plant/PlantCombatTower.java#L81)). crit RNG는 supercrit 먼저 성공하면 normal RNG 소비하지 않는 순서(`:126`) 보존 필요.
- **공격/범위:** 기본 target shared; [src/main/java/kim/biryeong/semiontd/tower/plant/PlantCombatTower.java:227](../src/main/java/kim/biryeong/semiontd/tower/plant/PlantCombatTower.java#L227) 공격 AoE는 shared 피해·효과, meadow support pulse `:445`는 source 제외 REGISTERED, heal overlap은 첫회복 tick만 기록해 창을 고정(`:511-538`), desert 반사피해 `:393`는 개별피격. 소스 없이 가장강한효과와 source효과 의미를 변경하지 않았다.
- **지뢰/Panda:** [src/main/java/kim/biryeong/semiontd/tower/plant/PlantMineTower.java:108-146](../src/main/java/kim/biryeong/semiontd/tower/plant/PlantMineTower.java#L108) triggerInterval+1 검사/fuse 대기→발동시점 새 AoE. triggered `:161`은 lane monster snapshot O(M), **XZ 원 <=**이며 높이 무시; AoE와 그대로 공유 불가. fuse 점화 뒤 대상이 없어져도 취소 안 함. Panda [src/main/java/kim/biryeong/semiontd/tower/plant/PandaTower.java:101,129](../src/main/java/kim/biryeong/semiontd/tower/plant/PandaTower.java#L101)는 idle 주기, dash중 cooldown0으로 매tick 실제 move→sweep; nearest `:304`는 3D <= / encounter tie 최소 O(M), dashHits는 UUID로 1회 피격, sweep request가 Set.copyOf+filter로 exclusion 보존(`:250`). reset/death/remove/final-defense에서 dash종료·hit setclear(`:140-159`).
- **Gardener 흡수/지배:** [src/main/java/kim/biryeong/semiontd/tower/plant/GardenerTower.java:196-235](../src/main/java/kim/biryeong/semiontd/tower/plant/GardenerTower.java#L196) execute는 cooldown2이므로 3tick간격, 20tick pulse에서는 healfield/dominate, 빈대상일 때 별도스킬쿨다운을 쓰지 않아 반복스캔. target strongest/mostHurt/drain 각 lane snapshot O(M/T)이며 필터가 다르다. `monstersNear(:519)`는 alive runtime, !removed/!stealthed/!dominated 3D<=. `enemiesNear(:539)`는 월드 AABB inflate(radius,3,radius), targetTeam same·3D<=라 final-defense 다른lane 포함. 지배는 새소환이 아니라 기존monster 제외카운트/진영/stun을 설정(`:323`), 주기피해, 멀면 순간이동/미드따라가기. 흡수 `:430`는 각 공격 전후 실제 runtime HP차 합산→그 시점 부상아군 snapshot→동일 pool/n 분배(그 뒤새피해/새부상자 포함 금지). VFX sources/targets 최대6수집, 실제 lifeDrain renderer는 5/4만 그림.
- **수명/VFX:** Gardener onRemoved/releaseDomination은 진영/제외카운트 해제, 라운드종료/이미clear/미드면 보상없이 discard+lane제거(`:388-424`). 이것 때문에 리스트snapshot 일괄제거 금지. terraform onRemoved 원상복구→worldTree갱신([src/main/java/kim/biryeong/semiontd/tower/plant/PlantTerraformTower.java:89](../src/main/java/kim/biryeong/semiontd/tower/plant/PlantTerraformTower.java#L89)); state는 player키이고 blockstate만 보관, world강한참조 없음. 시작/탈락 clear, 정상 lane clear는 terraform teardown으로 흙반환. VFX는 [src/main/java/kim/biryeong/semiontd/tower/plant/PlantDisplayVfx.java:74](../src/main/java/kim/biryeong/semiontd/tower/plant/PlantDisplayVfx.java#L74) 매이벤트 DisplayEffect 새생성, lifetime/towerBudget 적용, seed=world tick; meadow 성공치료때, mine 점화/폭발, panda첫충돌, gardener시전·지배pulse 때 생성. 성능만으로 client수명/시각빈도 변경 불가.
- **기존검증:** PlantTowerCatalogTest/PlantTowerStatsViewTest/PlantDisplayVfxTest, [src/gametest/java/kim/biryeong/semiontd/tower/plant/PlantTowerIntegrationTest.java](../src/gametest/java/kim/biryeong/semiontd/tower/plant/PlantTowerIntegrationTest.java)(흙교체/판매/정산/성장·worldTree·roots최초진입·지뢰재장전·tier·회복겹침·Panda이동/중복/종료·desert귀속·Gardenerheal/dominate/drain), PlantTowerAugmentCombatTest. count index·외부world unload·roots key할당 비교검증은 미추가.
- **판정:** 실제 큰후보는 soil별 count 재계산과 다중 snapshot. mutation·배치성공/실패·damage callback을 관통하는 구현/검증이 필요하여 현재 변경 보류. 가문전체 최적화완료 주장은 불가.

<a id="builder-army"></a>

### 22. 군대 빌더 — Army

판정: **유지: 지휘 주기 overflow 의심은 별도 기능 문제**. 개별 조사 완료, 새 성능 미측정. 통합 gate4 통과를 개별 전수실행으로 확대하지 않는다.

복잡도는 각 항목의 타워 T, 몬스터 M, 지원 제공자 P, 수혜자 H, 영역 Z, 전역 엔티티 E, 관전자 P 등 현장 정의를 따른다. 소스 상수/fallback과 실제 운영 설정은 구분한다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | 장비·지휘 cooldown; overflow 의심 |
| attack | 병과 기본공격·지원·전역 후처리 |
| target | senior 탐색·owner/family 조건 |
| AoE | 지원 대상·훈련/방어 효과 |
| stack | 계급·퇴역 최근2·지원 자원 |
| summon / absorb | 예비2 임시타워·퇴역/계승 |
| sync | wave/death/retirement 순서·state close |
| VFX | 장비/병과 palette·지원 event |

#### 경로·주기·비용·경계·수명과 회귀 근거

- **매틱/동기화:** [src/main/java/kim/biryeong/semiontd/tower/army/ArmyTower.java:305](../src/main/java/kim/biryeong/semiontd/tower/army/ArmyTower.java#L305) super tick→currentLane 저장→entity lookup와 equipmentVisual.sync 매tick. 의도된 command는 20tick마다 각lower tower가 전체lane senior 합산 O(A*T); grid XZ<=, 같은owner·같은가문·상위rank만, single COMMAND_SOURCE에서 cap하여 sourced효과중복 없음(`:330-373`).
- **중요 기존 기능결함 후보:** lastCommandTick는 Long.MIN_VALUE(`:56`), `:313`의 `now-lastCommandTick < 20`에는 sentinel guard가 없음. 정상 now>=0은 subtraction overflow로 음수가 되어 초기 refreshCommand가 실행되지 않을 수 있다. 이번 성능수정에 섞지 않았다. root에서 별도 기능결함으로 보고·재현 필요. 따라서 실제 활성핫패스로 O(A*T)/20이라 단정하면 안 되고 '의도 경로'로 표기.
- **공격/스택:** 타깃 shared. artillery onAttackResolved `:378`는 resolvedOutgoingDamage 비례 AoE·원래primary 제외·실피해통계경로. veteran증강은 junior 유효타마다 lane snapshot O(T), 조건통과 senior의 세번째공격마다 runWithoutTriggers로 동종원피해type 유지(`:389-440`). promotion은 NATURAL_WAVE 처치만, round1회 cap; rank 서비스는 wave시작 주변지원합산 snapshot O(T) (`:163,229`)→round끝 completeServiceWave (`:176`)이며 전투중사망도 service 적용. current health 영향없는 준비snapshot이므로 매tick공유값으로 바꾸면 안 됨.
- **범위/경제/소환:** supportBonus는 살아있는동일owner 전체lane O(T) (`:485`), radius는 적용하지 않는다. 퇴역은 환급계산→제거성공→메달/퇴역1회기록 순서([src/main/java/kim/biryeong/semiontd/job/JobArmyLifecycle.java:26-48](../src/main/java/kim/biryeong/semiontd/job/JobArmyLifecycle.java#L26)). [src/main/java/kim/biryeong/semiontd/tower/army/ArmyStates.java:42](../src/main/java/kim/biryeong/semiontd/tower/army/ArmyStates.java#L42) retirements ArrayDeque는 최근2개만 유지; `:70` wave후처리에서 최대2 reserve 임시타워 생성·설치·onWaveStarted. reserve는 rank/trait 제외/원래snapshot 피해·체력, reset temporary cleanup에서 제거. 별도 흡수 없음.
- **상태/수명/VFX:** rank, pendingService, veteran 횟수는 upgrade copy ([src/main/java/kim/biryeong/semiontd/tower/army/ArmyTower.java:112](../src/main/java/kim/biryeong/semiontd/tower/army/ArmyTower.java#L112)), 웨이브마다 kill/attack카운터 리셋. 장비overlay는 onRemoved 제거(`:141`), 메달/augment maps clear는 시작·탈락 및 모든lane정리후([src/main/java/kim/biryeong/semiontd/tower/army/ArmyStates.java:30](../src/main/java/kim/biryeong/semiontd/tower/army/ArmyStates.java#L30), [src/main/java/kim/biryeong/semiontd/job/JobBuilderLifecycle.java:161](../src/main/java/kim/biryeong/semiontd/job/JobBuilderLifecycle.java#L161)). retirement record는 type/position/numbers만 있어 world entity 보유하지 않음. 승급/지휘진입/퇴역은 shared AoE VFX, 자기하나를 보여주려 `playRankVfx(:567)`가 REGISTERED 전수filter하는 O(T) 이벤트비용; 빈도가 낮고 shared budget/정책을 우회하지 않음.
- **기존검증:** ArmyRankTest/ArmyStatesTest/ArmyTowerCatalogTest/ArmyTowerStatsViewTest/ArmyAugmentsTest; ArmyTowerRuntimeTest의 placement-upgrade/service, 13wave퇴역·실패lane·cleanup, artillery원피해, 특별진급, 예비군snapshot·소멸, veteran3회. **초기 command tick overflow 검증 부재**.
- **판정:** 억지수정 없음. 초기timer기능결함 별도, tick shared cache·수익순서변경은 비승인.

<a id="builder-thunder"></a>

### 23. 람쥐썬더 빌더 — Thunder

판정: **변경: 존재 확인의 추가 목록·정렬 제거**. 선정 변경 경로 최종 gate4 통과. 새 성능 미측정.

복잡도는 각 항목의 타워 T, 몬스터 M, 지원 제공자 P, 수혜자 H, 영역 Z, 전역 엔티티 E, 관전자 P 등 현장 정의를 따른다. 소스 상수/fallback과 실제 운영 설정은 구분한다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | network snapshot10·발전기/폭풍 |
| attack | chain·relay·battery 소모 |
| target | 방문집합 encounter chain·거리 relay |
| AoE | 존재확인 AoE는 candidateCount 소비 |
| stack | 전하·battery3발·network·wave RNG |
| summon / absorb | 소환/흡수 자원 서비스 없음 |
| sync | 현재 health·network/lane/round 정리 |
| VFX | relay/chain·범위 event |

#### 경로·주기·비용·경계·수명과 회귀 근거

- **매틱:** [src/main/java/kim/biryeong/semiontd/tower/thunder/ThunderTower.java:99-126](../src/main/java/kim/biryeong/semiontd/tower/thunder/ThunderTower.java#L99) 각tower당 10tick마다 full lane grid snapshot + battery 매tick. [src/main/java/kim/biryeong/semiontd/tower/thunder/ThunderPower.java:92-119](../src/main/java/kim/biryeong/semiontd/tower/thunder/ThunderPower.java#L92)는 owner필터/살아있는가문, g1 rod는 사망해도 발전, tank 발전은 현재 잃은HP 비율(`:140`)이므로 T개의tower면 전체 O(T²)/10 평균(각tower의scan시점은다름). placement/wave forcedscan, storm roll wave1회 shared owner state. health·죽음·부활·타입reload·wave RNG와 타워별10tick 위상 때문에 global per-tickcache는 동치아님.
- **공격/선택/범위:** 기본target shared. [src/main/java/kim/biryeong/semiontd/tower/thunder/ThunderTower.java:164-203](../src/main/java/kim/biryeong/semiontd/tower/thunder/ThunderTower.java#L164) 매공격 visited HashSet를 만든 뒤 chain/relay/충전발사/살아남은primary기절·접지순서. chain `:308`은 target주변 radius, primary 제외, encountered 후보중 첫k에 resolved피해: nearest로 바꾸면 안 됨. relay `:369`는 살아있는 같은owner 다람쥐를 원source거리stable sort→k(기본2), relay별 공통AoE의 새로운 visited 대상(기본5). chain이 추가한visited를 그대로 공유해 중복타격 방지. 모든tower 공격마다 visited set은 작은 낭비후보지만 reentrant augment/chain ordering 검증 대비 이득 작아 미수정.
- **스택/충전/흡수:** waveActive·sourcealive·수면/trigger/증강/power gating 후 존재질의. 빈범위 20tick마다 최대3발 저장(fallback), 적존재하면 연속idle카운터0, 다음실피해공격에서 storedShots=0 후 native 추가타. reset과 upgrade복사 유지([src/main/java/kim/biryeong/semiontd/tower/thunder/ThunderTower.java:396,404](../src/main/java/kim/biryeong/semiontd/tower/thunder/ThunderTower.java#L396) 변경후). armadillo는 같은owner immunity source와 개인stun cooldown, grounding magnitude상승때만VFX. damageAbsorb는 항상 incoming피해비율감소 property이며 소환/저장흡수 시스템 없음.
- **VFX/수명:** [src/main/java/kim/biryeong/semiontd/tower/thunder/ThunderTower.java:420](../src/main/java/kim/biryeong/semiontd/tower/thunder/ThunderTower.java#L420) death방출만, 판매/upgrade에는발동안함, died tower onKill전파off. static STORMS는 UUID→record뿐([src/main/java/kim/biryeong/semiontd/tower/thunder/ThunderStates.java:20](../src/main/java/kim/biryeong/semiontd/tower/thunder/ThunderStates.java#L20)) start/elimination/close clear([src/main/java/kim/biryeong/semiontd/job/JobThunderLifecycle.java:7,19,24](../src/main/java/kim/biryeong/semiontd/job/JobThunderLifecycle.java#L7)); spawnedEntity는 tower-local strong ref라 removed tower 자체가 수거될 때 해제, 새global worldcache 추가 없음. chain/relay ARC 및 방출VFX는 shared service budget; stun시/새mark시 추가이벤트. [src/main/java/kim/biryeong/semiontd/tower/thunder/ThunderVfx.java:95,122,169](../src/main/java/kim/biryeong/semiontd/tower/thunder/ThunderVfx.java#L95) 샘플hit·고정arms/segments.
- **기존검증:** ThunderPowerTest/ThunderTowerCatalogTest/ThunderVfxTest; [src/gametest/java/kim/biryeong/semiontd/tower/thunder/ThunderGameTest.java](../src/gametest/java/kim/biryeong/semiontd/tower/thunder/ThunderGameTest.java)(충전3발/범위내재충전금지, g2확장, relay중복금지, primary죽은후chain·통계귀속, owner기절immunity, stun이동/공격/heal). 이번 presence boundary/lifecycle 회귀1개 추가.
- **판정:** 존재질의 추가정렬제거 적용. 공통 스캔까지없앤것은 아님. 전력망snapshot공유/relay선택/visited 최적화는 미적용.

<a id="builder-demonlord"></a>

### 24. 마왕 빌더 — DemonLord

판정: **변경: 소환 점유열의 호출 범위 Set 조회**. 선정 변경 경로 최종 gate4 통과. 새 성능 미측정.

복잡도: T=lane 타워, M=activeMonsters, E=월드 엔티티, A=범위 후보, G=도박꾼, S=관전자 링크, P=바닥 열, D=개 무리, O=Pet 주인, C=Pet 동료, U/V=전체 꿈 타워/몬스터, u/v=현재 lane 꿈 상태. HashSet은 충돌/생성/리사이즈를 포함한 평균 복잡도다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | 서비스·carrier·hotbar/bossbar·pending |
| attack | 입력 스킬·마검·검기 실제피해 |
| target | 90도/최근접·표식5·어그로 world5 |
| AoE | 소용돌이 pull→damage·지옥불/균열 |
| stack | XP·level·진행도 snapshot |
| summon / absorb | 마수/echo·freeColumns·실피해 흡혈 |
| sync | disconnect·carrier/marks·진행도 수명 |
| VFX | DisplayEffect/키프레임·follow/seed |

#### 경로·주기·비용·경계·수명과 회귀 근거

주 경로 파일은 [src/main/java/kim/biryeong/semiontd/tower/demonlord/](../src/main/java/kim/biryeong/semiontd/tower/demonlord/) 기준.

| 기능 | 실제 경로·주기·비용/할당 | 판정 |
|---|---|---|
| 매틱/상태 동기화 | [src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordService.java:293-366](../src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordService.java#L293): lane owner/job/온라인 확인, traits/augments/lane/team 동기화 → carrier → hotbar → bossbar/movement/tickscale → combat pending. `:325` 20틱마다 loadout dirty. `:551-577` bossbar 문구 Component 생성 매틱. `:988-1022` 매틱 최대 7 binding carrier 대조, loadout.view EnumMap 사본, catalog/entity 조회. | 작은 고정 크기 B 경로. 단순 캐시/수동 인라인 후보로 수정하지 않음. reload/slot교체/엔티티제거 재생성 유지 필요. [src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordSkillShop.java:61-65](../src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordSkillShop.java#L61) resolved는 이미 catalog lookup이며 매틱 설명 재렌더가 아님. |
| 공격/타깃 | [src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordService.java:175-279](../src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordService.java#L175) 입력/피해 이벤트와 마검, [src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordSkills.java:100-118](../src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordSkills.java#L100) 손아귀는 앞 90도 후보 중 최근접, `:824-839` 공간 AABB+구형<=+canFight. [src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordExecuteMarks.java:45-73](../src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordExecuteMarks.java#L45) 5틱마다 activeMonsters snapshot O(M), 임시 HashMap; `:81-110` 표식 차이만 패킷 전송. | 거리 동률 첫 encounter, stealth/canFight, 본인 lane→final-defense 허용 전환 보존. 월드 검색을 lane목록으로 무조건 치환하면 보스/최종방어 후보가 달라질 수 있음. |
| 범위/지속기 | [src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordSkills.java:665-670](../src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordSkills.java#L665) 검기→지옥불→균열→마수→소용돌이 순서. `:255-288` 소용돌이는 매틱 pull 검색+설정 damage interval일 때 별도 shared AoE. `:709-747` 지옥불은 expiry 우선 후 nextPulse; `:407-435` 균열 간격별 순차 wave; `:785-810` 공통 area API, centralDefense에서 acrossLanes. | 범위 후보 O(A); 동일 틱 두 검색을 합치면 이동 적용/후속 피해 후보 의미가 바뀔 수 있어 보류. pending의 처리 순서, 타워/altar 제거 시 null 처리, 만료의 >= 조건 보존. |
| 패시브 검기/흡혈 | [src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordPassives.java:146-185](../src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordPassives.java#L146) 활성 검기별 매틱 swept AABB → 후보 ArrayList 사본 → 원점거리 정렬 O(A log A), HashSet hit IDs. 최초 맞은 적만 cleave/finisher. `:53-72` cleave는 실제 준 피해 합산 후 회복 cap. | 정렬은 모든 hit의 순서와 최초 발동 대상에 의미가 있어 제거하지 않음. 단일 min으로 바꿀 수 없음. 하이퍼캐리 특성/증강·피해 curve·실피해 흡혈/한도 변경 없음. |
| 소환 | [src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordSkills.java:297-346](../src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordSkills.java#L297) 마수 기존 소환 해제→배치→state 기록, 매틱 lane.towers.contains O(T), 만료/사망 제거. [src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordPassives.java:200-219,234-277](../src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordPassives.java#L200) 침공군/군단 echo는 호출·웨이브 단위, 목록 shuffle과 바닥 freePositions. `:306-333` 점유열 snapshot 최적화 적용. | 임시복제본 next-round 제거, wave callback 수동 호출, 본래 owner 귀속 유지. 바닥 열 반환 순서와 난수 소비를 oracle 대조 대상으로 추가. 마수의 선형 contains는 공용 membership 공개 API 변경이 필요하므로 수정하지 않음. |
| 스택/진행도 | [src/main/java/kim/biryeong/semiontd/job/JobDemonLordLifecycle.java:17,38-49,60-68](../src/main/java/kim/biryeong/semiontd/job/JobDemonLordLifecycle.java#L17) 실제 kill credit 및 라운드 경험치. `DemonLordState` player-local 상태. [src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordStates.java:46-61,93-109](../src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordStates.java#L46) 재접속/teardown용 진행도 snapshot. | 완료되지 않은 일반 하이퍼캐리 계약을 이번 최적화로 해결했다고 주장하지 않음. native stack/level 수치 보존. |
| VFX | [src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordVfx.java:26-29,49-54](../src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordVfx.java#L26) DisplayEffect.spawn/follow, world gameTime seed. [src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordDisplayVfx.java:250-331,484-585](../src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordDisplayVfx.java#L250) cast별 display/키프레임 구성; 지속기 길이에 비례한 frame 할당. | DemonLord는 특수 display 연출 경로다. 일반 TowerVfx area particle로 바꾸지 않음. 시드·수명·follow target·지면 snap 보존, 실제 GPU 검증 미실행. |
| 제거/world | [src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordService.java:387-414,465-475,988-998,1047-1051](../src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordService.java#L387): disconnect/close에서 carrier onRemoved/detach, bossbar/marks/tickratio/hotbar 제거. lane identity 바뀌면 carrier 재생성. [src/main/java/kim/biryeong/semiontd/job/JobBuilderLifecycle.java:107-134](../src/main/java/kim/biryeong/semiontd/job/JobBuilderLifecycle.java#L107) 온라인/오프라인 match 정리. | 이번 새 Set은 호출 종료와 함께 폐기되고 entity/lane를 보관하지 않음. 기존 `PROGRESSION`은 clear에서 snapshot을 넣고 resetProgression은 새 DemonLord match에서만 보임. 종료 후 UUID별 값 잔류 후보(월드/엔티티 참조는 없음), 재접속 계약 때문에 성급히 삭제하지 않음. |

추가 후보: 비전투 [src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordService.java:601-615](../src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordService.java#L601)는 5틱마다 `level.getAllEntities()` O(E) 전체 어그로 해제 확인. 현재 타깃이 player인 다른 lane 몬스터도 정리하는 계약이므로 lane-only 범위 축소는 안전한 동등 변경이 아님. `DemonLordExecuteMarks`의 activeMonsters 사본/임시 now map도 매 5틱 할당이지만 snapshot 시점과 update 패킷 순서 근거 없이는 풀링하지 않음.

기존 핵심 검증: [src/gametest/java/kim/biryeong/semiontd/tower/demonlord/DemonLordGameTest.java:297](../src/gametest/java/kim/biryeong/semiontd/tower/demonlord/DemonLordGameTest.java#L297) 공유 피해·통계·kill, `:348` carrier 교체/제거, `:480` 다른 lane/어그로, `:662` 처형 표식, `:919` scaling, `:977` 반복범위+실피해흡혈, `:1025` soul-drain cap. [src/gametest/java/kim/biryeong/semiontd/tower/demonlord/DemonLordHotbarGameTest.java](../src/gametest/java/kim/biryeong/semiontd/tower/demonlord/DemonLordHotbarGameTest.java), [src/gametest/java/kim/biryeong/semiontd/tower/demonlord/DemonLordSwapInputTest.java](../src/gametest/java/kim/biryeong/semiontd/tower/demonlord/DemonLordSwapInputTest.java), [src/gametest/java/kim/biryeong/semiontd/tower/demonlord/DemonLordTickScaleSyncTest.java](../src/gametest/java/kim/biryeong/semiontd/tower/demonlord/DemonLordTickScaleSyncTest.java) 유지. [src/test/java/kim/biryeong/semiontd/tower/demonlord/DemonLordPassiveTest.java](../src/test/java/kim/biryeong/semiontd/tower/demonlord/DemonLordPassiveTest.java), [src/test/java/kim/biryeong/semiontd/tower/demonlord/DemonLordTowerCatalogTest.java](../src/test/java/kim/biryeong/semiontd/tower/demonlord/DemonLordTowerCatalogTest.java), [src/test/java/kim/biryeong/semiontd/tower/demonlord/DemonLordAugmentsTest.java](../src/test/java/kim/biryeong/semiontd/tower/demonlord/DemonLordAugmentsTest.java), [src/test/java/kim/biryeong/semiontd/tower/demonlord/DemonLordDisplayVfxTest.java](../src/test/java/kim/biryeong/semiontd/tower/demonlord/DemonLordDisplayVfxTest.java) 유지.

해당 없음: 일반 설치 제단의 자동 기본 공격(제단은 숨은 carrier이고 lane 목록 제외), Succubus식 적 스택 흡수. 마왕은 직접 입력/별도 state pool이며 마수/복제 소환은 실제 존재하므로 미구현으로 분류하지 않음.

<a id="builder-gamble"></a>

### 25. 겜블 빌더 — Gamble

판정: **변경: 관전자 1인 선택을 안정 min으로**. 선정 변경 경로 최종 gate4 통과. 새 성능 미측정.

복잡도: T=lane 타워, M=activeMonsters, E=월드 엔티티, A=범위 후보, G=도박꾼, S=관전자 링크, P=바닥 열, D=개 무리, O=Pet 주인, C=Pet 동료, U/V=전체 꿈 타워/몬스터, u/v=현재 lane 꿈 상태. HashSet은 충돌/생성/리사이즈를 포함한 평균 복잡도다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | gambler/fake visual·support20·VFX40 |
| attack | physical→살아있을 때 magic·jackpot |
| target | 관전자 stable min·linked relookup |
| AoE | jackpot·support 공유 영역 |
| stack | wave RNG·점수·링크상한·enum 상태 |
| summon / absorb | 소환·희생흡수 없음 |
| sync | originalPosition·upgrade·label cleanup |
| VFX | fake visual·label·공유/전용 VFX |

#### 경로·주기·비용·경계·수명과 회귀 근거

주 경로 파일은 [src/main/java/kim/biryeong/semiontd/tower/gamble/](../src/main/java/kim/biryeong/semiontd/tower/gamble/) 기준.

| 기능 | 실제 경로·주기·비용/할당 | 판정 |
|---|---|---|
| 매틱 | [src/main/java/kim/biryeong/semiontd/tower/gamble/GamblerTower.java:104-109](../src/main/java/kim/biryeong/semiontd/tower/gamble/GamblerTower.java#L104) base tick 뒤 idle facing과 equipment visual sync. [src/main/java/kim/biryeong/semiontd/tower/gamble/GambleSupportTower.java:99-118](../src/main/java/kim/biryeong/semiontd/tower/gamble/GambleSupportTower.java#L99) label 위치 매틱, 살아있을 때 linked effect 20틱 재적용, VFX 설정 주기(기본40, [src/main/java/kim/biryeong/semiontd/tower/gamble/GambleBalance.java:19,85](../src/main/java/kim/biryeong/semiontd/tower/gamble/GambleBalance.java#L19)). | 캐시하지 않음. support 사망 체크와 동기화 순서 유지. |
| 일반 공격/범위 | [src/main/java/kim/biryeong/semiontd/tower/gamble/GamblerTower.java:187-242](../src/main/java/kim/biryeong/semiontd/tower/gamble/GamblerTower.java#L187) 공통 basic attack outgoing 계산 후 physical→살아있으면 magic 순서, native splash, 조건부 jackpot nearestTargets 공통 AoE. [src/main/java/kim/biryeong/semiontd/tower/gamble/PokerTableTower.java:149-171](../src/main/java/kim/biryeong/semiontd/tower/gamble/PokerTableTower.java#L149) 사망 시 strongest-only debuff 전체 적용 후 폭발. | 공통 nearest topK 개선 수혜 가능. 물리 처치 후 magic 생략/이중 kill/실제 dealt 통계, debuff→damage 순서 보존. Poker의 두 AoE scan은 의미 있는 두 단계라 합치지 않음. |
| 관전자 대상 | [src/main/java/kim/biryeong/semiontd/tower/gamble/GambleRoundEffects.java:60-92](../src/main/java/kim/biryeong/semiontd/tower/gamble/GambleRoundEffects.java#L60): source link 먼저 release, owner/alive/entity/range<=/maxLink 필터 후 점수·거리·원래좌표 우선. | 전체 정렬→min 적용. 필터 전부 여전히 평가하며 comparator 및 live entity 조회를 동일하게 유지. 정렬 버퍼 제거, 링크 수 O(S) per G 스캔은 별도 남음. |
| 지원효과/재조회 | [src/main/java/kim/biryeong/semiontd/tower/gamble/GambleSupportTower.java:121-185](../src/main/java/kim/biryeong/semiontd/tower/gamble/GambleSupportTower.java#L121) 웨이브 시작 RNG roll→reward/label/state→assign→공통 registered AoE, 원래위치 linked 리스트. `:319-331` 매20틱 대상별 T목록 재검색 O(L*T), `:266-278` VFX 주기에도 별도 재검색+positive/negative ArrayList. | 장기 대상 캐시를 추가하면 upgrade/replacement가 같은 originalPosition으로 효과를 이어 받는 계약을 깰 수 있다. 호출별 map 후보는 있지만 적은 L/주기, 원래 first match, callbacks 검증 전 보류. |
| 상태/RNG | [src/main/java/kim/biryeong/semiontd/tower/gamble/GambleState.java:9-36](../src/main/java/kim/biryeong/semiontd/tower/gamble/GambleState.java#L9) immutable state, 능력 EnumSet 사본. [src/main/java/kim/biryeong/semiontd/tower/gamble/GamblerTower.java:118-129](../src/main/java/kim/biryeong/semiontd/tower/gamble/GamblerTower.java#L118) upgrade jackpot/health비율, 라운드 charge reset. `:265-272` 업그레이드 지원타워 소유/생존 탐색 O(T). 베팅/주사위/포커는 행동·웨이브 때 실행. | UI 조회가 RNG를 소비하지 않도록 유지. 기존 shuffle/draw/보상 순서 변경 없음. 고정 3카드 sort/작은 enum stream은 억지 수정 대상 아님. |
| VFX/수명 | [src/main/java/kim/biryeong/semiontd/tower/gamble/GambleRollLabels.java:22,42-60,103-111](../src/main/java/kim/biryeong/semiontd/tower/gamble/GambleRollLabels.java#L22) lane weak key 아래 owner→Tower→ArmorStand label, 매틱 teleport. `:63-96,175-179` source/all clear시 discard. [src/main/java/kim/biryeong/semiontd/tower/gamble/GambleRoundEffects.java:114-141](../src/main/java/kim/biryeong/semiontd/tower/gamble/GambleRoundEffects.java#L114) owner source/link/label clear. [src/main/java/kim/biryeong/semiontd/job/JobGambleLifecycle.java:7-39](../src/main/java/kim/biryeong/semiontd/job/JobGambleLifecycle.java#L7) start/end/eliminate/close에서 명시 정리. | WeakHashMap 값의 Tower가 attachedLane 강참조를 가질 수 있어 weak key만 믿으면 안 됨. 정상 명시 cleanup 확인; 비정상 direct 제거/owner 변경 경로 검증 후보. 이번 변경은 수명 구조에 영향 없음. |

기존 핵심 검증: [src/gametest/java/kim/biryeong/semiontd/tower/gamble/GambleGameTest.java:407](../src/gametest/java/kim/biryeong/semiontd/tower/gamble/GambleGameTest.java#L407) 최고점수/3link cap, `:457` owner/source stacking와 round cleanup, `:546` health ratio/basic splash, `:664` force close/reuse, `:747` reload, `:903` jackpot target cap/추가공격, `:943` 물리/마법 방어. 신규 [src/gametest/java/kim/biryeong/semiontd/tower/gamble/GambleSpectatorSelectionTest.java](../src/gametest/java/kim/biryeong/semiontd/tower/gamble/GambleSpectatorSelectionTest.java) 2개는 sorting 없는 선택의 실제 world branch 회귀. 단위 [src/test/java/kim/biryeong/semiontd/tower/gamble/GambleTowerTest.java](../src/test/java/kim/biryeong/semiontd/tower/gamble/GambleTowerTest.java), [src/test/java/kim/biryeong/semiontd/tower/gamble/GamblePokerTest.java](../src/test/java/kim/biryeong/semiontd/tower/gamble/GamblePokerTest.java), [src/test/java/kim/biryeong/semiontd/tower/gamble/GambleSlotsTest.java](../src/test/java/kim/biryeong/semiontd/tower/gamble/GambleSlotsTest.java), [src/test/java/kim/biryeong/semiontd/tower/gamble/GambleAugmentsTest.java](../src/test/java/kim/biryeong/semiontd/tower/gamble/GambleAugmentsTest.java), [src/test/java/kim/biryeong/semiontd/tower/gamble/GambleTowerStatsViewTest.java](../src/test/java/kim/biryeong/semiontd/tower/gamble/GambleTowerStatsViewTest.java) 유지.

해당 없음: native 소환/소환 흡수/적 꿈 스택 서비스. 도박 score/능력/charge와 sourced persistent support effects는 존재하므로 "스택 없음"으로 뭉뚱그리지 않음.

<a id="builder-succubus"></a>

### 26. 서큐버스 빌더 — Succubus

판정: **고위험 후보: 전체 꿈 상태 스캔; 재진입 캐시 보류**. 개별 조사 완료, 새 성능 미측정. 통합 gate4 통과를 개별 전수실행으로 확대하지 않는다.

복잡도: T=lane 타워, M=activeMonsters, E=월드 엔티티, A=범위 후보, G=도박꾼, S=관전자 링크, P=바닥 열, D=개 무리, O=Pet 주인, C=Pet 동료, U/V=전체 꿈 타워/몬스터, u/v=현재 lane 꿈 상태. HashSet은 충돌/생성/리사이즈를 포함한 평균 복잡도다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | 전체 꿈 tower/monster 상태 순회 |
| attack | 수면·꿈 피해·execute 귀속 |
| target | 기존 min·sleepwalk UUID 우선 |
| AoE | lullaby enemy/ally 정렬+다시 query |
| stack | 수면·ready·흡수 능력치 |
| summon / absorb | transfer·흡수; 별도 소환 없음 |
| sync | 재진입·owner/monster 만료·후보 잔류 |
| VFX | fake visual·꿈/회복·공유 이벤트 |

#### 경로·주기·비용·경계·수명과 회귀 근거

주 경로 파일은 [src/main/java/kim/biryeong/semiontd/tower/succubus/](../src/main/java/kim/biryeong/semiontd/tower/succubus/) 기준.

| 기능 | 실제 경로·주기·비용/할당 | 판정 |
|---|---|---|
| 매틱 상태 | [src/main/java/kim/biryeong/semiontd/tower/succubus/SuccubusDreams.java:160-199](../src/main/java/kim/biryeong/semiontd/tower/succubus/SuccubusDreams.java#L160): 매 lane tick TOWERS 전체 entry snapshot O(U), lane 상태마다 T 목록 linear find와 TowerKey 생성 O(u*T); MONSTERS 전체 UUID 정렬 snapshot O(V log V), lane 상태마다 activeMonsters entity search O(v*M). | 고비용 최우선 후속 후보. 단순 per-lane 캐시/iteration 순서 변경 보류. state/entry snapshot은 deep copy가 아니며 콜백이 새 상태 생성/다른 상태 갱신/처형/전파 가능. |
| 반복 동기화 | `:202-247,314-332` 활성 counter→wake/expire/smoke→persistent effects 갱신. 각 상태마다 `hasLivingSuccubus`→`:399-405` owner/living core 재검색 O(T). core 없어진 순간 증폭 해제도 반영한다. | 단일 tick core 존재 캐시가 콜백 중 core 사망/추가를 못 보면 다름. 증폭/reload/owner attribution이 같은 시점일 때만 공유 가능. clear된 stack0 상태에도 sleep history 등이 남으므로 무조건 map 제거하면 안 됨. |
| 일반 타깃/공격 | [src/main/java/kim/biryeong/semiontd/tower/succubus/SuccubusTower.java:96-104](../src/main/java/kim/biryeong/semiontd/tower/succubus/SuccubusTower.java#L96) 후보 min: core sleepCount↓/stacks↓/distance↑, 기타 stacks↓/distance↑. `:126-145` 실제 dealt>0·비처치에 role별 stack. `:155-173` sleepwalker 반격 UUID→readyAt와 실제 health-loss threshold. | 이미 선형 min. snapshot caching/정렬 후보 아님. counter map은 round reset(:231-235)까지 사망UUID를 남길 수 있으나 UUID/Long만 보관하고 round 경계에서 제거. |
| 자장가 범위 | [src/main/java/kim/biryeong/semiontd/tower/succubus/SuccubusTower.java:188-217](../src/main/java/kim/biryeong/semiontd/tower/succubus/SuccubusTower.java#L188): pulse마다 activeMonsters entity 필터+stack 정렬 top enemyLimit O(M log M), towers 거리/stack 정렬 top allyLimit O(T log T), UUID/타워 Set, 뒤이어 공통 area API 다시 query/filter. | 후속 topK 후보지만 enemy/ally의 서로 다른 stack priority, 원래 encounter tie, logical tower 좌표 vs entity 좌표, 정확 경계<=, source 포함, 엔티티/타워 필터를 모두 보존해야 함. 공용 nearest 옵션으로 단순 변경 불가. |
| 수면 공격/전파 | [src/main/java/kim/biryeong/semiontd/tower/succubus/SuccubusDreams.java:208-220,370-392](../src/main/java/kim/biryeong/semiontd/tower/succubus/SuccubusDreams.java#L208) 간격 도달때 lane 몬스터 list 생성+최근접 min. UUID 정렬은 sleepwalk 최대대상 우선권과 연결. `:283-311` 꿈 전파→tower 전파→추가 magic AoE, reentrancy guard. `:335-367` contagion depth/target cap/면역/kill origin. | 중복 query를 묶을 때 순서와 callback-time membership/health 변화를 놓치지 않아야 한다. 전파 전에 target 사본을 재사용하면 새/제거/처형 결과가 달라질 수 있으므로 미수정. |
| 흡수 | [src/main/java/kim/biryeong/semiontd/tower/succubus/SuccubusTower.java:118-122](../src/main/java/kim/biryeong/semiontd/tower/succubus/SuccubusTower.java#L118) core attributed onKill, [src/main/java/kim/biryeong/semiontd/tower/succubus/SuccubusDreams.java:272-280](../src/main/java/kim/biryeong/semiontd/tower/succubus/SuccubusDreams.java#L272) sleep execution 경로. [src/main/java/kim/biryeong/semiontd/tower/succubus/SuccubusAbsorption.java:16-30](../src/main/java/kim/biryeong/semiontd/tower/succubus/SuccubusAbsorption.java#L16) owner state O(1) 증가→health/state sync→VFX. | native kill과 wake-area kill의 구분 및 실행 1회 귀속을 기존 GameTest로 보존. 소환 흡수가 아니라 적 처치 통계 흡수. |
| VFX/제거 | [src/main/java/kim/biryeong/semiontd/tower/succubus/SuccubusTower.java:59-87](../src/main/java/kim/biryeong/semiontd/tower/succubus/SuccubusTower.java#L59) core FakePlayer attach/refresh/tick/remove; [src/main/java/kim/biryeong/semiontd/tower/succubus/SuccubusDreams.java:228-244](../src/main/java/kim/biryeong/semiontd/tower/succubus/SuccubusDreams.java#L228) 수면 10틱 smoke, [src/main/java/kim/biryeong/semiontd/tower/succubus/SuccubusVfx.java:50-104](../src/main/java/kim/biryeong/semiontd/tower/succubus/SuccubusVfx.java#L50) shared TowerVfx area style. [src/main/java/kim/biryeong/semiontd/tower/succubus/SuccubusDreams.java:419-469](../src/main/java/kim/biryeong/semiontd/tower/succubus/SuccubusDreams.java#L419) lane/player/entity 상태 제거, [src/main/java/kim/biryeong/semiontd/job/JobSuccubusLifecycle.java:8-18](../src/main/java/kim/biryeong/semiontd/job/JobSuccubusLifecycle.java#L8) match/eliminate/close. | 전역 map state가 lane/monster/lastSource 강참조를 가짐. `LULLABY_READY_AT` clearLane은 현재 등록 타워만 검사(:427), 이미 제거된 대상 key는 owner clear까지 잔류 가능. family 아닌 owner가 전파 대상이면 마지막 clear 보장 추가 조사 필요. 추정 후보이며 런타임 leak 재현으로 확정하지 않음. |

보존: 전체 UUID 정렬/첫 encounter 동률, tower original owner+position key, 상태 sourceOwner 최초귀속과 lastSource 최신값 구분, immunity와 sleepCount 수명, clearLane 시점(temporary copies 제거 뒤; [src/main/java/kim/biryeong/semiontd/job/JobLaneLifecycle.java:39-44](../src/main/java/kim/biryeong/semiontd/job/JobLaneLifecycle.java#L39)), 범위 구형 포함 경계, world entity ID 재조회, 제거 후 state.monster fallback을 임의 삭제하지 않음.

기존 핵심 검증: [src/gametest/java/kim/biryeong/semiontd/tower/succubus/SuccubusGameTest.java:42](../src/gametest/java/kim/biryeong/semiontd/tower/succubus/SuccubusGameTest.java#L42) 수면공격40틱, `:95` contagion세대/대상수, `:133` sleepwalk3/stun, `:179` third sleep/면역, `:212` attributed 흡수, `:265` wake순서, `:300,:325` area흡수제외/재귀차단, `:354,:387` cap/core exclusion, `:446` smoke/정리. [src/test/java/kim/biryeong/semiontd/tower/succubus/SuccubusDreamStateTest.java](../src/test/java/kim/biryeong/semiontd/tower/succubus/SuccubusDreamStateTest.java), [src/test/java/kim/biryeong/semiontd/tower/succubus/SuccubusVfxTest.java](../src/test/java/kim/biryeong/semiontd/tower/succubus/SuccubusVfxTest.java), catalog unit 유지.

해당 없음: native 소환/임시 clone 제작/플레이어 inventory 조작. 꿈 스택·적 처치 흡수·fake-player 시각화는 실제 경로 존재. 이번 개별 production 수정 없음; 캐시보다 의미 보존 회귀가 먼저 필요.

<a id="builder-body"></a>

### 27. 신체 빌더 — Body

판정: **유지: ray/범위·단계별 콜백 계약**. 개별 조사 완료, 새 성능 미측정. 통합 gate4 통과를 개별 전수실행으로 확대하지 않는다.

복잡도: T=lane 타워, M=activeMonsters, E=월드 엔티티, A=범위 후보, G=도박꾼, S=관전자 링크, P=바닥 열, D=개 무리, O=Pet 주인, C=Pet 동료, U/V=전체 꿈 타워/몬스터, u/v=현재 lane 꿈 상태. HashSet은 충돌/생성/리사이즈를 포함한 평균 복잡도다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | heart 지원 execute·부위별 상태 |
| attack | brain/eyes/genital의 단계별 효과 |
| target | ray/geometry 및 추가 대상 정렬 |
| AoE | projection·sphere·heart broadcast |
| stack | hit count·heart 스택·fixed source |
| summon / absorb | 전용 소환·흡수 없음 |
| sync | upgrade/copy·타워 local 상태 |
| VFX | 부위 palette·ray/area·지원 event |

#### 경로·주기·비용·경계·수명과 회귀 근거

주 경로 파일은 [src/main/java/kim/biryeong/semiontd/tower/body/](../src/main/java/kim/biryeong/semiontd/tower/body/) 기준.

| 기능 | 실제 경로·주기·비용/할당 | 판정 |
|---|---|---|
| 매틱/실행 | [src/main/java/kim/biryeong/semiontd/tower/body/BodyTower.java:70-114](../src/main/java/kim/biryeong/semiontd/tower/body/BodyTower.java#L70): common cooldown 실행은 heart만 성공. waveActive/alive/sleep gate 후 List.copyOf(lane.towers) O(T), 리스트 순서대로 다른 살아있는 Body 기관 action. 공용 target range0(:443-450), 추격false. | 일반 AI의 자체 공격 조회 없음. 심장별 박동마다 snapshot이 필요하고 콜백 중 변경 의미를 보존해야 함. 등록시 owner filter가 없으므로 owner 한정 최적화 금지. |
| 타깃 | `:117-143` brain target scan list→우선순위 max→공유 splash. `:184-208` eye 기본 경로방향, 증강 있으면 별도 scan/min 최근접, 이후 ray filtered AoE. `:211-231` genital primary max 후 주변 list→거리 정렬→limit. `:262-288` 공통 area API로 대상 collect, 임시 ArrayList. | primary/secondary 중복 scan은 서로 다른 center/filter. genital topK 후보이나 primary 죽어도 후속 공격을 진행하는 계약 및 query 후 callback 순서 보존 필요. 이번 수정 없음. |
| 범위/경계 | [src/main/java/kim/biryeong/semiontd/tower/body/BodyTowerTargetGeometry.java:15-28](../src/main/java/kim/biryeong/semiontd/tower/body/BodyTowerTargetGeometry.java#L15) final defense는 마지막 유효 경로 segment의 역방향. `:38-45` projection 0..range 포함, horizontal perpendicular<=width². 실제 area request는 별도 구형 범위를 먼저 적용한다. | ray helper가 높이 무시한다고 전체 AoE가 무한 높이인 것은 아님. geometry 경계와 shared sphere gate 모두 보존. |
| 스택/피격 | [src/main/java/kim/biryeong/semiontd/tower/body/BodyTower.java:145-180](../src/main/java/kim/biryeong/semiontd/tower/body/BodyTower.java#L145) brain unsourced strongest/refresh, skin 고정3source 순회·전체 refresh+빈slot1개. `:234-255` genital UUID hitcount 2회째 magic/slow. `:318-327` owned body death heart 영구스택, `:346-394` skin health-loss 기준 heart별 cooldown/추가 heartbeat. | 고정3 순회 최적화 필요 낮음. genus hitcount는 primary kill시 제거, wave start/lane clear 전체clear(:465-479), 다른 타워 kill 후 죽은UUID는 그때까지 남을 수 있으나 순수 UUID/Integer이며 wave bound. |
| 상태/업그레이드/VFX | `:396-402` cooldown 확정에서 entity 조회 두 번(첫 refresh 후 interval), `:454-500` eye 방향/round/copy, hitcount map deep copy. heartbeat/eye/genital VFX는 TowerVfxService(:92-95,208,238). | duplicate entity 조회는 작은 고정 비용; refresh의 상태변경 의미 검증 없이 memoize하지 않음. 스택/방향/쿨다운과 VFX 순서 유지. owner별 static 상태/월드 캐시 없음. |

기존 핵심 검증: [src/gametest/java/kim/biryeong/semiontd/tower/body/BodyGameTest.java:35](../src/gametest/java/kim/biryeong/semiontd/tower/body/BodyGameTest.java#L35) 박동추가1회, `:53` eye/아드레날린, `:88` 흡수·자해 제외 skin, `:131,:156` heart/wave gating, `:189` 영구사망스택, `:214` brain strongest, `:240,:268` 2타 magic/primary죽음후extra, `:293,:319` 방향/finaldefense. [src/test/java/kim/biryeong/semiontd/tower/body/BodyTowerTargetGeometryTest.java](../src/test/java/kim/biryeong/semiontd/tower/body/BodyTowerTargetGeometryTest.java) exact boundary, [src/test/java/kim/biryeong/semiontd/tower/body/BodyTowerCatalogTest.java](../src/test/java/kim/biryeong/semiontd/tower/body/BodyTowerCatalogTest.java) upgrade/round stack, [src/test/java/kim/biryeong/semiontd/tower/body/BodyAugmentsTest.java](../src/test/java/kim/biryeong/semiontd/tower/body/BodyAugmentsTest.java) 유지.

해당 없음: native 소환/적흡수/플레이어 공유 static map. 독립 기본공격은 비활성이고 심장 signal이 공격원을 움직인다. [src/main/java/kim/biryeong/semiontd/job/JobBuilderLifecycle.java:33](../src/main/java/kim/biryeong/semiontd/job/JobBuilderLifecycle.java#L33) job lifecycle NONE은 정상이며 타워 callback 경로를 뜻없이 삭제하면 안 됨.

<a id="builder-pet"></a>

### 28. 반려동물 빌더 — Pet

판정: **유지: 유대는 이벤트 갱신; 생장·최종방어 계약**. 개별 조사 완료, 새 성능 미측정. 통합 gate4 통과를 개별 전수실행으로 확대하지 않는다.

복잡도: T=lane 타워, M=activeMonsters, E=월드 엔티티, A=범위 후보, G=도박꾼, S=관전자 링크, P=바닥 열, D=개 무리, O=Pet 주인, C=Pet 동료, U/V=전체 꿈 타워/몬스터, u/v=현재 lane 꿈 상태. HashSet은 충돌/생성/리사이즈를 포함한 평균 복잡도다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | pet fake owner tick; bond는 이벤트 갱신 |
| attack | bird heal·cat 영역·leader |
| target | imprint/주인/yard·heal top-k |
| AoE | Chebyshev·yard·공유 범위 |
| stack | loyalty/growth/bond·최종방어 freeze |
| summon / absorb | 소환·희생흡수 없음 |
| sync | 성장 fullHP 대 bond비율·remove |
| VFX | fake owner·family palette·회복/공격 |

#### 경로·주기·비용·경계·수명과 회귀 근거

주 경로 파일은 [src/main/java/kim/biryeong/semiontd/tower/pet/](../src/main/java/kim/biryeong/semiontd/tower/pet/) 기준.

| 기능 | 실제 경로·주기·비용/할당 | 판정 |
|---|---|---|
| 매틱 | [src/main/java/kim/biryeong/semiontd/tower/pet/PetTower.java:207-212](../src/main/java/kim/biryeong/semiontd/tower/pet/PetTower.java#L207): base cooldown + owner만 FakePlayer tick. native companion targeting은 공통 goal. 별도의 매틱 PetBondService 호출은 없음. | PetBondService 비용을 매틱 비용이라고 보고하면 부정확. 이벤트·웨이브에서의 반복 refresh가 주 후보. |
| 배치관계/주기 | [src/main/java/kim/biryeong/semiontd/tower/pet/PetTower.java:183-188,216-245,275-279,426-430](../src/main/java/kim/biryeong/semiontd/tower/pet/PetTower.java#L183): 배치/죽음/제거/각 pet wave/reset/성장 때 refresh, [src/main/java/kim/biryeong/semiontd/game/PlayerLane.java:268-275](../src/main/java/kim/biryeong/semiontd/game/PlayerLane.java#L268) augment 변경 때 refresh. [src/main/java/kim/biryeong/semiontd/tower/pet/PetBondService.java:37-74](../src/main/java/kim/biryeong/semiontd/tower/pet/PetBondService.java#L37) 전체 pet/owner/companion lists, yard HashMap/lists, pack map. | 한 wave에 각 pet callback이 refresh하므로 Ppet번 재계산 가능. 이벤트들을 단순 1회로 합치면 앞 pet 성장/adult·health와 뒤 pet bond 계산 시점이 달라질 수 있으므로 보류. |
| 반복 scan 복잡도 | [src/main/java/kim/biryeong/semiontd/tower/pet/PetBondService.java:119-135](../src/main/java/kim/biryeong/semiontd/tower/pet/PetBondService.java#L119) companion마다 owner find/min O(C*O); `:82-110` BFS에서 각 dog가 전체 dogs 순회 O(D²); `:138-168` companion별 yard 필터 list, family adult species distinct, owner 재조회. 전체 refresh는 O(T+C*O+D²+sum(yard_i²)) 계열, 다수 임시 list/map/set/deque. | 장기 캐시보다는 refresh 호출 내부 owner/yard aggregation/좌표 index 후보. 그러나 원래 encounter tie, 동일owner/team/lane 분리, 겹치는original/current좌표, adult변화 기준을 회귀로 보존해야 한다. 아직 미수정. |
| 범위/연결 | [src/main/java/kim/biryeong/semiontd/tower/pet/PetBondService.java:53-54](../src/main/java/kim/biryeong/semiontd/tower/pet/PetBondService.java#L53) 어느 pet라도 finalDefense면 전체 refresh skip(기존 yard freeze). `:179-191` 3D Chebyshev, 인접은 거리>0 && <=YARD_RADIUS, owner radius는 별도. `:122-133` loyalty owner가 남으면 거리와 무관 유지, 첫 imprint nearest tie encounter. | 최종방어 겹침, 주인 업그레이드 원래 충성 위치, 부활, 죽은 owner도 남아 있을 때 loyalty 유지와 active=false를 구별. 새 cache의 invalidation이 훨씬 넓으므로 보류. |
| 공격/치료 | [src/main/java/kim/biryeong/semiontd/tower/pet/PetTower.java:249-272](../src/main/java/kim/biryeong/semiontd/tower/pet/PetTower.java#L249) actual dealt 후 bird heal/cat splash/leader hit. `:383-405` leader lane 순서로 다른종 최대선택, 유효타깃·수면·사거리 live check. `:483-520` bird 환자 filter→healthRatio sort→limit, selfHeal 먼저→snapshot patients 순서. | sort+limit topK 후보 O(T log T), 기본1/증강여러명. 선택 완료 전 healing하면 자기치료/다른환자 우선순위가 달라질 수 있음. full tie에서 기존 첫 lane 순서 유지 필요. |
| AoE/성장/시각화 | [src/main/java/kim/biryeong/semiontd/tower/pet/PetTower.java:453-480](../src/main/java/kim/biryeong/semiontd/tower/pet/PetTower.java#L453) cat shared nearestTargets(k)→TowerAreaDamage, `:410-449` bond cap/health 증가/성년 전이 refresh, praise cap 초과 kill도 소비. `:164-179` yard 최대체력 변화는 비율보존, `:337-352` copy. heal TowerVfx(:506-510), owner FakePlayer attach/refresh/remove(:191-230). | 공통 nearest topK 수혜. 성장 maxHealth 증분 채움과 pack 변경 비율보존은 서로 다른 규칙이라 합치지 않음. 제거/사망 fake-player cleanup 기존 유지. |
| 수명 | PetBondService는 static 상태 map 없음. PetTower의 currentLane은 tower-local이고 copy 때 전달되며 onRemoved에서는 명시 null 안 함. FakePlayer static map cleanup은 onDeath/onRemoved에 있음. | 제거된 tower가 다른 장기 서비스에 남는 경우 currentLane chain 점검 후보이나 이 family 단독 leak 확정 근거 없음. 본 변경에서 장기 참조 추가 없음. |

기존 핵심 검증: [src/test/java/kim/biryeong/semiontd/tower/pet/PetBondServiceTest.java](../src/test/java/kim/biryeong/semiontd/tower/pet/PetBondServiceTest.java) 첫귀속/동률·대각/다른owner/owner제거/연결무리, [src/test/java/kim/biryeong/semiontd/tower/pet/PetBondGrowthTest.java](../src/test/java/kim/biryeong/semiontd/tower/pet/PetBondGrowthTest.java) 성장/칭찬cap/죽은동료/ownerupgrade, [src/test/java/kim/biryeong/semiontd/tower/pet/PetAugmentsTest.java](../src/test/java/kim/biryeong/semiontd/tower/pet/PetAugmentsTest.java) 확대yard와dog인접분리/실제성년/familyhealth, [src/gametest/java/kim/biryeong/semiontd/tower/pet/PetGameTest.java:40](../src/gametest/java/kim/biryeong/semiontd/tower/pet/PetGameTest.java#L40) leader 순서/비재귀, `:73,:172` self+allies/최저체력, `:196,:229` owner부활/finaldefense freeze, `:273` cat AoE cap. [src/gametest/java/kim/biryeong/semiontd/tower/pet/PetCatRangeTest.java](../src/gametest/java/kim/biryeong/semiontd/tower/pet/PetCatRangeTest.java) 실제 사거리 유지.

해당 없음: 적흡수/native 임시소환/매틱 yard graph rebuild/owner 공격. Pet 동료는 배치 타워이며 owner는 비공격이다. job lifecycle NONE([src/main/java/kim/biryeong/semiontd/job/JobBuilderLifecycle.java:38](../src/main/java/kim/biryeong/semiontd/job/JobBuilderLifecycle.java#L38))과 별개로 위 타워 이벤트에서 성장한다.

<a id="builder-developer"></a>

### 29. 개발자 빌더 — Developer

판정: **변경: hasBug CSV 조회에서 Set 생성 제거**. 선정 변경 경로 최종 gate4 통과. 새 성능 미측정.

복잡도: T=타워, N=몬스터 후보, A=닻 후보, B=CSV 토큰, E=bug enum, S=spell enum, Q=예약 포탄, R=ready 포탄, I=빙판, C=저주 시전자, P=world 플레이어. 소환 minion 수 K는 Blueprint 절에서 별도 정의한다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | wave/stall/bug scalar timer |
| attack | patch splash·overkill·bug hooks |
| target | lock UUID·최대HP/거리·판결 |
| AoE | patch/overkill shared area |
| stack | patch·instability·bug CSV·optimization |
| summon / absorb | 소환/희생흡수/피해비례흡혈 없음 |
| sync | typed live data·copy/reload·pending clear |
| VFX | DeveloperVfx·shared area·reproduce |

#### 경로·주기·비용·경계·수명과 회귀 근거

##### 코드 경로·비용
- DeveloperTower.tick([src/main/java/kim/biryeong/semiontd/tower/developer/DeveloperTower.java:600](../src/main/java/kim/biryeong/semiontd/tower/developer/DeveloperTower.java#L600))은 wave/stall/cacheMiss/overkill/zombie scalar 타이머 O(1) 갱신 후 super. stall 종료 stat sync(:611)는 필수. wave start(:630)는 pending patch 승격→실패 RNG→stat sync, reset(:684)은 잔여 효과와 타이머를 정리한다.
- modifyAttackDamage(:270 부근) 및 stat hooks는 hasBug/hasOptimization을 여러 번 호출한다. 이전 hasBug는 매번 DeveloperTowerData.bugs(:230)의 CSV split, 각 token enum 해석, LinkedHashSet 전체 생성 후 contains였다.
- selectAttackTarget(:448)은 locked UUID 후보 O(N) 탐색 후 health 최대/거리 최대/첫 원소, judgement 거리 최소(:479). 동점 encounter order, lock 해제, lastCandidateCount를 유지해야 한다. RNG 실패는 wave(:661), 공격 정확도 등 기존 순서를 유지했다.
- patch splash(:530)는 공격 후 shared MonsterAreaEffectRequest/TowerAreaDamage. overkill execute(:567)는 죽은 대상 위치·source를 저장한 펄스, cooldown=5(:593), 잔여 타이머는 매틱 감소. patch count/amount, instability, bug CSV는 typed data([src/main/java/kim/biryeong/semiontd/tower/developer/DeveloperTowerData.java:35](../src/main/java/kim/biryeong/semiontd/tower/developer/DeveloperTowerData.java#L35)); wave 타이머는 타워 필드.
- hasOptimization 및 copiedBugs도 CSV→Set을 재구성하나 승인 범위 밖이라 그대로다. 장기 cache는 typed data 직접 변경/copy/upgrade 무효화가 필요하므로 채택하지 않았다.

##### 이번 수정
- [src/main/java/kim/biryeong/semiontd/tower/developer/DeveloperTowerData.java:242](../src/main/java/kim/biryeong/semiontd/tower/developer/DeveloperTowerData.java#L242) hasBug만 같은 현재 encoded/split(",")/DeveloperBug.fromKey를 사용해 찾으면 반환한다. fromKey([src/main/java/kim/biryeong/semiontd/tower/developer/DeveloperBug.java:266](../src/main/java/kim/biryeong/semiontd/tower/developer/DeveloperBug.java#L266))는 trim/Locale.ROOT lowercase/enum 조회로 부작용 없음.
- 최악 O(B*E)+CSV split은 유지. LinkedHashSet 및 entry 생성 제거, 앞 토큰에서 찾으면 이후 enum 해석 생략. split 문자열/배열, enum values 배열, Optional 비용은 남는다. bugs() mutable set·첫 등장 순서·저장 형식은 미수정.
- null bug는 기존처럼 데이터 getter를 읽지 않는다. null tower/encoded, blank/unknown/빈 token/중복/끝 comma, 대소문자·공백 해석을 유지한다. 장기 상태 cache 없음.

##### 수명·해당 없음·VFX
- 가족 전용 소환 entity/희생·흡수/피해비례 lifesteal 없음. 예외처리/가비지컬렉션 회복, 부호반전, 좀비 유예는 별도 기존 기믹.
- DeveloperStates map(:20)의 PendingReproduction(:71)은 tower 참조를 보유하나 새 round(:92) 및 JobDeveloperLifecycle([src/main/java/kim/biryeong/semiontd/job/JobDeveloperLifecycle.java:9/33/38](../src/main/java/kim/biryeong/semiontd/job/JobDeveloperLifecycle.java#L9)) start/eliminate/close에서 정리된다.
- DeveloperTower.spawnedEntity(:93)는 마지막 source 강한 참조이고 reset에서 null로 지우지 않아 구 entity를 붙잡을 수 있는 후보. 외부 무기한 root 증거가 없으므로 누수 확정 아님. MEMORY_LEAK 이름은 게임 기믹이지 Java 누수 증거가 아니다.
- DeveloperVfx.show([src/main/java/kim/biryeong/semiontd/tower/developer/DeveloperVfx.java:22](../src/main/java/kim/biryeong/semiontd/tower/developer/DeveloperVfx.java#L22))→공통 area(:27), reproduce secondary(:44); patch splash/overkill 공통 VFX. 가족 상시 VFX scan 없음.

##### 검증 근거
- 기존 [src/test/java/kim/biryeong/semiontd/tower/developer/DeveloperTowerTest.java](../src/test/java/kim/biryeong/semiontd/tower/developer/DeveloperTowerTest.java), [src/test/java/kim/biryeong/semiontd/tower/developer/DeveloperPatchServiceTest.java](../src/test/java/kim/biryeong/semiontd/tower/developer/DeveloperPatchServiceTest.java), DeveloperTowerPatchEfficiencyTest.
- [src/gametest/java/kim/biryeong/semiontd/tower/developer/DeveloperGameTest.java:141](../src/gametest/java/kim/biryeong/semiontd/tower/developer/DeveloperGameTest.java#L141)(핫픽스/승격),184(splash),258(치명피해 회복),319(upgrade),355(stall); [src/gametest/java/kim/biryeong/semiontd/tower/developer/DeveloperTowerRuntimeTest.java:31](../src/gametest/java/kim/biryeong/semiontd/tower/developer/DeveloperTowerRuntimeTest.java#L31)(재현 클릭/취소).
- 새 [src/test/java/kim/biryeong/semiontd/tower/developer/DeveloperBugLookupTest.java](../src/test/java/kim/biryeong/semiontd/tower/developer/DeveloperBugLookupTest.java) 5개: 전체 enum×token 변형의 기존 bugs reference 비교, null getter, null bug short-circuit, live mutation/copy/reload, mutable set encounter 순서. 실행은 root 통합 gate에 위임.

<a id="builder-frost"></a>

### 30. 혹한 빌더 — Frost

판정: **후보: wave family count; 즉시타·냉장 snapshot 유지**. 개별 조사 완료, 새 성능 미측정. 통합 gate4 통과를 개별 전수실행으로 확대하지 않는다.

복잡도: T=타워, N=몬스터 후보, A=닻 후보, B=CSV 토큰, E=bug enum, S=spell enum, Q=예약 포탄, R=ready 포탄, I=빙판, C=저주 시전자, P=world 플레이어. 소환 minion 수 K는 Blueprint 절에서 별도 정의한다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | FullOperation·ice patch·refrigerants |
| attack | splash→thaw→즉시 추가 공격 |
| target | 즉시타마다 현재 후보 재조회 |
| AoE | cooling/eruption·ice/AGE capped AoE |
| stack | frozen/refrigeration·strongest aura |
| summon / absorb | 소환/희생흡수/lifesteal 없음 |
| sync | source refs·wave expire·team/owner clear |
| VFX | protection/ice 주기·shared buff |

#### 경로·주기·비용·경계·수명과 회귀 근거

##### 코드 경로·비용
- PlayerLane.tick(:570/571)→FrostFullOperationService.tick([src/main/java/kim/biryeong/semiontd/tower/frost/FrostFullOperationService.java:153](../src/main/java/kim/biryeong/semiontd/tower/frost/FrostFullOperationService.java#L153)) + FrostAugments.tick(:114). 비활성이면 map 조회 후 종료. 완전가동은 매 tick owned/live T 타워 timed effect refresh(:415), chill interval마다 source 탐색(:351)+shared AoE(:302). 만료(:162) 시 효과/frozen/sound 정리.
- FrostCoolingTower.execute([src/main/java/kim/biryeong/semiontd/tower/frost/FrostCoolingTower.java:44](../src/main/java/kim/biryeong/semiontd/tower/frost/FrostCoolingTower.java#L44))는 타입 공격 interval의 방향성 monster AoE와 allied exceptional AoE(:78), VFX(:80). FrostEruptionCoolingTower.execute(:69)는 aura refresh 설정(:82), FrostHealingTower.execute(:50)는 healIntervalTicks 기본100(:60). 지원 scheduler의 d+1 호출 경계를 유지한다.
- FrostSplashTower.onAttackResolved(:163): splash→조건부 thaw→즉시 추가 공격 순서. selectAttackTarget(:56)은 냉장 priority/거리 또는 threshold의 인컴 maxHealth O(N). immediate attack(:300)은 각 발마다 world query(:335)하여 O(k*N), 후보 목록/stream/toList 반복. 앞 발 사망·냉장 변화가 다음 발 선택에 반영되어 공유 snapshot으로 단순 치환 불가.
- wave family count는 Vanguard(:44), Healing(:65), Splash(:89) 각각 T 스캔. FrostTeamEffects.snapshotEruptionStacks(:41)는 lanes snapshot→owned tower snapshot/filter list(:52)→4 family count 스캔(:55~58/117). 동일 snapshot 4 count 단일 pass가 후보이나 wave당 비용이며 우선 수정하지 않았다.
- FrostAugments.tick(:118)은 매tick expired ice 제거 후 I개 AoE O(I*N), VFX는 time%10(:147). AGE interval 기본40(:124)마다 refrigerants prune 및 LinkedHashMap 처음 maxSources의 toList(:125~129), source마다 capped nearest AoE. 콜백에서 냉장 전파가 map을 수정할 수 있어 snapshot 의미를 보존해야 한다.
- iceDeaths set은 wave 사망 ID 수만큼 증가하고 endWave/clearPlayer(:55/59)에서 정리. snapshot allocation과 추적 비용을 빼고 O() 개선을 주장하면 안 된다.

##### 수명·해당 없음·VFX
- 전용 소환 entity/희생·흡수/lifesteal 없음. 냉장·해동·치유는 타워/몬스터/웨이브 상태다.
- FrostTeamEffects.monsterEntity(:130)는 ID + runtimeMonster identity/alive/removed를 검증. aura는 own/ally 구분, unsourced strongest non-stacking(:112) 보존.
- FrostAugments는 source/target entity refs를 기간/웨이브 동안 유지. source 사망 필터를 임의 추가하면 기존 전파 의미가 바뀔 수 있어 그대로다.
- JobFrostLifecycle([src/main/java/kim/biryeong/semiontd/job/JobFrostLifecycle.java:8/18/25](../src/main/java/kim/biryeong/semiontd/job/JobFrostLifecycle.java#L8)) 및 JobLaneLifecycle(:35/57/78)가 map/team/효과 start/reset/shutdown을 연결한다.
- cooling wave, eruption aura, FullOperation 단일 shared buff event(:321), ice patch 간헐 area VFX가 실제 소비 경로. client 렌더 검증 미실행.

##### 검증 근거·판정
- [src/test/java/kim/biryeong/semiontd/tower/frost/FrostTowerTest.java:538/552/609](../src/test/java/kim/biryeong/semiontd/tower/frost/FrostTowerTest.java#L538)(가족 snapshot), [src/test/java/kim/biryeong/semiontd/tower/frost/FrostFullOperationStateTest.java:21/38](../src/test/java/kim/biryeong/semiontd/tower/frost/FrostFullOperationStateTest.java#L21)(dedup/expiry).
- [src/gametest/java/kim/biryeong/semiontd/tower/frost/FrostGameTest.java:129/160](../src/gametest/java/kim/biryeong/semiontd/tower/frost/FrostGameTest.java#L129)(빙판),228(source/target cap),295(target),326/729(즉시 공격),363(완전가동),804(팀 strongest aura); [src/gametest/java/kim/biryeong/semiontd/tower/frost/FrostTowerAugmentCombatTest.java:19](../src/gametest/java/kim/biryeong/semiontd/tower/frost/FrostTowerAugmentCombatTest.java#L19).
- 개별 소스 수정 없음. 공통 top-k 최적화 소비는 AGE/thaw 경로. 효과 주기/스냅샷/재진입을 바꿀 위험 대비 독립 개선 근거가 부족하여 억지 변경하지 않았다.

<a id="builder-pirate"></a>

### 31. 해적 빌더 — Pirate

판정: **변경: 닻 중첩 선택을 안정 순위 스캔으로**. 선정 변경 경로 최종 gate4 통과. 새 성능 미측정.

복잡도: T=타워, N=몬스터 후보, A=닻 후보, B=CSV 토큰, E=bug enum, S=spell enum, Q=예약 포탄, R=ready 포탄, I=빙판, C=저주 시전자, P=world 플레이어. 소환 minion 수 K는 Blueprint 절에서 별도 정의한다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | fake visual·chest cashout·shell queue |
| attack | parrot/slow/splash·RNG수입 |
| target | 닻 stable rank·shell retarget |
| AoE | anchor guard·cannon capped AoE |
| stack | spend/admiral·anchor상한·shell ready |
| summon / absorb | 소환/희생흡수 없음; shell은 예약공격 |
| sync | membership·cashout callback·close |
| VFX | fake visual·chest/admiral/anchor |

#### 경로·주기·비용·경계·수명과 회귀 근거

##### 코드 경로·비용
- PirateTower.tick([src/main/java/kim/biryeong/semiontd/tower/pirate/PirateTower.java:42](../src/main/java/kim/biryeong/semiontd/tower/pirate/PirateTower.java#L42)): 공유 tick→fake visual→ready chest cashout. 공격후(:57)는 augment→parrot opening count/stat sync→first iron slow→splash. 처치(:58)는 RNG income→ferryman 지급. diamond/emerald 바닥 나눗셈, RNG 순서 유지.
- wave start(:46) applyAnchorGuard(:127)는 수신자마다 isSelectedAnchorFor(:182). 이전 filter→stable strength desc sort→limit(k)→self anyMatch는 O(T+A log A), 정렬 storage O(A).
- PirateStates.recordDiamondSpend(:25)/EmeraldSpend(:40)는 owned pirate T 스캔/stat sync; admiral threshold 횟수×admiral scan. ferrymanIncome(:62)는 지급마다 T 스캔. notifyTowerSold(:66)는 snapshot/filter list 후 anchor bonus→singer chest acceleration. cashOutChest(:145)는 membership 확인→remove 성공→경제/판매 callback, 중간 연쇄 성숙 가능.
- PirateAugments.tick([src/main/java/kim/biryeong/semiontd/tower/pirate/PirateAugments.java:150](../src/main/java/kim/biryeong/semiontd/tower/pirate/PirateAugments.java#L150))은 Q를 ready list 필터 후 removeAll(ready), 최악 O(Q*R), ready FIFO snapshot 실행. priority queue는 deadline 순서를 insertion 순서와 바꿀 수 있어 별도 후보로 보류.
- shell(:177)은 membership/source alive/range/sleep/target 유효성 재확인. invalid target은 shared area list→선형 선택. cannon(:115)은 wave source/damage snapshot, interval마다 team lane progress 최대(:163)→capped AoE(:170). source fallback도 기존 계약.

##### 이번 수정
- isSelectedAnchorFor(:182)를 stable rank 선형 판정으로 교체: O(T), 추가 storage O(1). iterator 등 상수 객체까지 무할당이라고 주장하지 않는다.
- strength Double.compare와 본인 첫 eligible occurrence 전 동점 수를 사용. 중복 identity, encounter ties, 본인 없음/범위 밖 false 유지.
- 기존 조건은 닻 타입/같은 team/inclusive 3D 거리이며 owner/alive/world/entity 추가 조건을 넣지 않았다. 각 anchor의 현재 radius/strength/bonus, 선택자의 maxStacks를 매 호출 다시 읽고 장기 cache 없음.
- maxStacks는 TowerBalanceConfig.abilityInt(:1999)→roundedNonNegativeInt(:2782)의 <=0→0 정규화, INT_MAX cap. runtime apply는 음수 ability를 거절(:2003 이후)하므로 기존 stream.limit에 음수가 전달되는 정상 경로 없음.

##### 수명·해당 없음·VFX
- 별도 소환 entity/희생·흡수/lifesteal 없음. Shell은 예약 공격 record.
- PirateStates.State(:72)는 game/player 강한 refs, PirateAugments는 source/target/tower refs. begin/endWave(:115/134), PirateStates.close(:15), JobPirateLifecycle(:14/16)에서 정리. missing tower shell은 membership 실패로 소진. 무기한 root 누수 근거 없음.
- FakePlayerTowerVisuals attach/tick/remove(:41~48), PirateTowerVfx chest/admiral/anchor, 공통 splash/secondary가 실제 VFX. anchorGuardSource의 originalPosition 기반 source ID 그대로.

##### 검증 근거
- U:tower/pirate/PirateEconomyTest. [src/gametest/java/kim/biryeong/semiontd/tower/pirate/PirateAugmentGameTest.java:35](../src/gametest/java/kim/biryeong/semiontd/tower/pirate/PirateAugmentGameTest.java#L35)(포탄간격),62(retarget/cancel),86(cannon snapshot), [src/gametest/java/kim/biryeong/semiontd/tower/pirate/PirateTowerAugmentCombatTest.java:21](../src/gametest/java/kim/biryeong/semiontd/tower/pirate/PirateTowerAugmentCombatTest.java#L21)(upgrade).
- 새 [src/test/java/kim/biryeong/semiontd/tower/pirate/PirateAnchorSelectionTest.java](../src/test/java/kim/biryeong/semiontd/tower/pirate/PirateAnchorSelectionTest.java) 6개. 기존 sorted/limit oracle와 seeded 150 rosters×모든 후보 비교, ties/중복/본인없음/팀·타 owner/죽은 논리타워/no-world/3D inclusive 경계/0/negative config gate/reload·bonus live 변화 포함. 실행은 root 통합 gate에 위임.

<a id="builder-blueprint"></a>

### 32. 빌더 빌더 — Blueprint

판정: **후보: alive list/top-k; 타격 순서·소환 수명 유지**. 개별 조사 완료, 새 성능 미측정. 통합 gate4 통과를 개별 전수실행으로 확대하지 않는다.

복잡도: T=타워, N=몬스터 후보, A=닻 후보, B=CSV 토큰, E=bug enum, S=spell enum, Q=예약 포탄, R=ready 포탄, I=빙판, C=저주 시전자, P=world 플레이어. 소환 minion 수 K는 Blueprint 절에서 별도 정의한다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | 소환 만료→base·periodic modules |
| attack | primary→package→multishot·실피해흡혈 |
| target | FIRST fast path·priority min/max |
| AoE | splash/line/chain·heal/haste/range |
| stack | focus/frenzy·module 상태 |
| summon / absorb | minion 실제 소환·lifesteal; 희생흡수 없음 |
| sync | ID 추적·reset/remove dismiss·catalog fallback |
| VFX | BLUEPRINT palette·secondary/aura/UI |

#### 경로·주기·비용·경계·수명과 회귀 근거

##### 코드 경로·비용
- BlueprintTower.tick([src/main/java/kim/biryeong/semiontd/tower/blueprint/BlueprintTower.java:437](../src/main/java/kim/biryeong/semiontd/tower/blueprint/BlueprintTower.java#L437))은 summons.expire를 super health/sleep보다 먼저 실행. BlueprintTowerSummonController.expire(:24)는 minions 수 K에 O(K) entity ID lookup/iterator removal, removed/dead/expiry/logical health 조건에서 discard. summon(:40)은 interval/count 통과 후 type/runtime tower/entity 생성, RNG angle, addFreshEntity 성공 시에만 map 추가.
- execute(:444)는 periodic module 있을 때 entity lookup. PULSE_TICKS=20(:47), 3 pulses마다 heal/haste/range(:464), aura duration20*3+20; shared d+1 호출 경계. detection(:513)은 pulse마다 world query+inclusive sphere. aura는 각 모듈 별도 area request이며 heal→haste→range/owner source(:95) 순서 보존.
- BlueprintTargetPriority.select(:42)는 FIRST fast exit, 다른 타입은 alive list O(N) 생성 후 min/max. encounter ties, SENT_FIRST sender/laneProgress와 fallback 유지.
- onAttackResolved(:163): primary stack→hitPackage→multishot. hitPackage(:201): onhit→splash→line→chain→actual-dealt lifesteal. 각 단계 RNG/kill callbacks 존재.
- multishot(:302)은 source-range query+ArrayList copy→primary-distance 기준 stable 전체 sort(:316)→k개 공격, O(N log N), O(N) lists. source-range와 primary-nearness의 서로 다른 중심, dominated/lane filters, callback-time death/RNG 순서 때문에 공통 nearest API로 단순 치환 불가.
- focus/frenzy(:183)는 타워 local scalar. splash(:329) 공통 area, line(:368) LineTargets. chain(:380)은 AtomicInteger로 원래 area encounter order 앞 jumps만 유효 damage. nearest 순서로 변경하면 기믹 변화.
- lifesteal(:215)은 각 hitPackage 실제 dealt 비례 healing. hypercarry sacrifice/transfer와 다른 기존 기믹. knockback은 immunity deadline→progress→navigation→teleport(:291~298) 순서.

##### 수명·상태·VFX
- cachedStats(:74)는 blueprint definition snapshot이고 종료 catalog 삭제 직전 fallback을 제공한다. 새 global config cache 없음.
- summons.dismiss(:68)는 resetForRound(:539)/onRemoved(:548)에서 discard+clear. minion은 integer ID로 추적하므로 UUID/world 재사용까지 안전하다고 단정하지 않는다; 정상 lane arena 수명은 유지.
- BlueprintStates(:22)의 정의/player maps와 JobBlueprintLifecycle(:11/21/26)의 match 설치/owner clear로 catalog/player refs 정리. 계정 BlueprintLibrary 영속성은 누수와 구별.
- 별도 희생/흡수 없음. summon 및 lifesteal은 실제 존재해 '해당없음' 처리 금지.
- BLUEPRINT palette primary, secondary multishot/line/knockback, shared splash/chain/corpse/thorns/aura. runtimeDetailLines(:554)는 UI 때 list 생성.

##### 검증 근거·판정
- [src/test/java/kim/biryeong/semiontd/tower/blueprint/BlueprintPricingTest.java](../src/test/java/kim/biryeong/semiontd/tower/blueprint/BlueprintPricingTest.java), progression/BlueprintPersistenceTest. [src/gametest/java/kim/biryeong/semiontd/tower/blueprint/BlueprintTowerBuilderTest.java:36](../src/gametest/java/kim/biryeong/semiontd/tower/blueprint/BlueprintTowerBuilderTest.java#L36)(build/close), [src/gametest/java/kim/biryeong/semiontd/tower/blueprint/BlueprintTowerModuleTest.java:143](../src/gametest/java/kim/biryeong/semiontd/tower/blueprint/BlueprintTowerModuleTest.java#L143)(multishot),186(line/nonstack),235(focus/frenzy/summon),305(소환자 사망 후 수명),369(aura expiry).
- 이번 개별 소스 수정 없음. 후보 alive-list 제거/selection top-k는 실제 필터·스냅샷·재진입 계약 검증 후 별도 판단.

<a id="builder-magicschool"></a>

### 33. 마법학교 빌더 — MagicSchool

판정: **변경: 주문 ID immutable map 조회**. 선정 변경 경로 최종 gate4 통과. 새 성능 미측정.

복잡도: T=타워, N=몬스터 후보, A=닻 후보, B=CSV 토큰, E=bug enum, S=spell enum, Q=예약 포탄, R=ready 포탄, I=빙판, C=저주 시전자, P=world 플레이어. 소환 minion 수 K는 Blueprint 절에서 별도 정의한다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | wizard tick·Leviosa/Rennervate·curse tick |
| attack | spell onHit·실피해 gate |
| target | forced 매 goal tick·ID tie·spell lookup |
| AoE | secondary/transfer·EPISKEY·control |
| stack | curriculum/tier·curse/house·cooldown |
| summon / absorb | transfiguration props/DeathEaters·흡수 없음 |
| sync | caster/target refs·owner/round/props clear |
| VFX | fake visual·protection10·curse/control5 |

#### 경로·주기·비용·경계·수명과 회귀 근거

##### 코드 경로·비용
- MagicSchoolWizardTower.tick([src/main/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolWizardTower.java:493](../src/main/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolWizardTower.java#L493)): super→fake visual→protection10tick VFX→Leviosa gate→Rennervate gate. 같은 tick 여러 runtimeEntity 조회 존재. 앞 콜백 생존/상태변화에 민감해 묶어 캐싱하지 않음.
- Leviosa nextTick은 floor 지연 보정(:511), Rennervate now+interval(:518)로 서로 다르다.
- supportsForcedAttackTargeting=true(:440); TowerAttackMonsterGoal.findPreferredTarget(:172)→selectForcedTarget(:222)가 cache 전에 targetCandidates를 호출하므로 wizard 후보 world scan/선택은 매 goal tick 경로다.
- selectAttackTarget(:431)는 target-valid/in-range filter 후 lumos/control priority→house→distance→entity ID min. explicit ID tie를 encounter tie로 바꾸지 않는다.
- selectedSpell(:61)은 typed ID를 find 후 현재 maxSpellTier/curriculum 검사. primary damage(:397), interval(:417), tick(:501/512/515), onAttackResolved(:529), MagicSchoolSpellCombat.targetPriority(:30) comparator에서 반복된다.
- onHit([src/main/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolSpellCombat.java:103](../src/main/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolSpellCombat.java#L103))은 disarm/stun/splash/heal/wound/extra-hit/lumos/Crucio/Imperio switch; actual dealt>0일 때만 호출(Wizard:532). capped secondary(:140)/transfer(:165)는 shared nearest 소비.
- EPISKEY(:182)는 eligible allied wizard list 수집 후 health ratio/entityID min; O(T) 이중 순회/list 후보지만 area result/VFX/수신 cooldown 계약 때문에 보류.
- SemionMonsterEntity.tick(:256)→MagicSchoolMonsterSpells.tick(:60)는 몬스터당 C curses O(C) iterator; 기본 dot interval2/duration40은 enum config. VFX5tick, identity-keyed caster damage/source snapshot, vulnerability/stun target state. dot hit/kill→iterator removal(:85)→control reset(:87) 순서를 유지. controlTarget(:116)은 shared nearest1 area query.

##### 이번 수정
- MagicSchoolSpell.find([src/main/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolSpell.java:133](../src/main/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolSpell.java#L133))의 values array clone+stream linear lookup을 class-init immutable ID map(:50/125)으로 교체.
- build expected O(S), retained O(S); lookup expected O(1), hash worst O(S). 조회별 enum array clone/stream pipeline 제거, Optional은 유지.
- putIfAbsent로 선언순 first match 보존. 현재 enum unique ID regression 추가. null/unknown empty, case/whitespace/display name 무정규화 유지. selectedSpell live tier/curriculum 계산은 변경하지 않았다.

##### 소환·상태·수명·VFX
- transfiguration barrel/bomb([src/main/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolTransfiguration.java:23](../src/main/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolTransfiguration.java#L23)), DeathEaters(:37) 존재. props는 onKill prune, last-wizard death(:67), owner clear(:78), combatLane clear(:87)에서 정리.
- 피해 비례 lifesteal/희생 흡수 없음. EPISKEY 회복과 lethal potion(Wizard:365)은 별도 기믹.
- curriculum UUID map([src/main/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolCurriculum.java:17](../src/main/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolCurriculum.java#L17))은 JobMagicSchoolLifecycle(:7/12/17)→clear→props clear. 공통 Tower round reset(:1134)에서 school effects clear.
- MonsterSpells refs는 target/effect 수명. dead-target early path는 curses를 clear하지만 controller refs는 entity collection/clearRound까지 남을 수 있는 지점이며 무기한 root 증거 없음.
- FakePlayerTowerVisuals onRemoved/onDeath(Wizard:481/487) 정리. skin map은 계정 preference 수명이라 무조건 match-clear하지 않는다.
- Broomsticks.beforeFinalDefense(:19)는 laneId sort→max/current-health sort 후 선택 이동. event-only이고 순서 계약 있어 그대로.
- HogwartsTower(:19/59/64)는 noAI/range0/no-basic 비전투 관리 타워이므로 wizard 주문 hot path를 적용하지 않는다.
- production spell attack 및 protection10tick/curse-control5tick/wound/heal/area가 전용 palette·TowerVfxService 소비. lookup 수정은 event spell snapshot/수신자/렌더러를 변경하지 않는다.

##### 검증 근거
- [src/test/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolSpellTest.java:100/126](../src/test/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolSpellTest.java#L100)(live tier/curriculum),201(reload), Proficiency/Curriculum/Catalog tests.
- [src/gametest/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolSpellCombatTest.java:155](../src/gametest/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolSpellCombatTest.java#L155)(target),227(protection),252(extra hits),272(curses),371(heal cooldown),403(Rennervate),543(Leviosa cadence),676(VFX expiry).
- [src/gametest/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolCurriculumCombatTest.java:23/84](../src/gametest/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolCurriculumCombatTest.java#L23)(transfer),184/271(props); [src/gametest/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolTowerIntegrationTest.java:622](../src/gametest/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolTowerIntegrationTest.java#L622)(reload/proficiency),790(range),1131(graduation).
- 새 [src/test/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolSpellLookupTest.java](../src/test/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolSpellLookupTest.java) 3개 전체 ID identity/first-reference/unique 및 null/unknown/case/whitespace/display name 검증. 실행은 root 통합 gate에 위임.

<a id="builder-default"></a>

### 참고. 무직 (DefaultJob) — Default

판정: **전용 전투 경로 없음; 33종에 포함하지 않음**. 등록 33종 외 참고행이다.

복잡도: T=타워, N=몬스터 후보, A=닻 후보, B=CSV 토큰, E=bug enum, S=spell enum, Q=예약 포탄, R=ready 포탄, I=빙판, C=저주 시전자, P=world 플레이어. 소환 minion 수 K는 Blueprint 절에서 별도 정의한다.

| 영역 | 확인한 경로·기능 색인 |
|---|---|
| tick | 전용 타워 tick 없음 |
| attack | 전용 공격 없음 |
| target | 전용 대상조회 없음 |
| AoE | 전용 범위효과 없음 |
| stack | 전용 타워 스택 없음 |
| summon / absorb | 전용 소환/흡수 없음 |
| sync | 전용 전투 상태 없음 |
| VFX | 전용 VFX 없음 |

#### 경로·주기·비용·경계·수명과 회귀 근거

- [src/main/java/kim/biryeong/semiontd/job/DefaultJob.java:8](../src/main/java/kim/biryeong/semiontd/job/DefaultJob.java#L8)은 recruit ID/설명만 가진다. [src/main/java/kim/biryeong/semiontd/job/JobRegistry.java:31](../src/main/java/kim/biryeong/semiontd/job/JobRegistry.java#L31)에서 기본 singleton 등록, [src/main/java/kim/biryeong/semiontd/job/JobBuilderLifecycle.java:31](../src/main/java/kim/biryeong/semiontd/job/JobBuilderLifecycle.java#L31)에서 NONE.
- SemionJob.canUseTower(:90)=false, includesTowerInCatalog(:105)=그 결과. Default 전용 등록 타워, 매틱 공격, 대상 조회, 범위 효과, 스택, 소환·흡수, 상태동기화, VFX는 전부 해당 없음. shared Tower 비용을 Default 전용 개선인 것처럼 보고하지 않는다.
- 기본 summon permission/economy는 SemionJob 공통 경로로 구별한다.
- [src/test/java/kim/biryeong/semiontd/tower/TowerBuilderCatalogContractTest.java:37](../src/test/java/kim/biryeong/semiontd/tower/TowerBuilderCatalogContractTest.java#L37)은 Default를 명시 제외. 전체 production-family contract 통과를 Default 전용 타워 검사라고 표현하지 않는다. JobBuilderLifecycleTest는 registry/dispatch 공통 근거다.
- Default 수정 없음.

### 공통 근거와 선정 변경의 상세 계약 보충

다음은 개별 절과 함께 확인할 공통 호출 순서, 변경 알고리즘의 보존 조건, 추가 회귀 설계 및 남은 후보다. 담당 조사 단계의 실행 분담 설명은 현재 통합 gate 표로 갱신해 해석한다.

#### 조사 묶음 A — Villager, VillagerAdv, Undead, Animal, Warlock, Legion

복잡도: N=lane 타워, A=동물, G=eligible 염소, C=공간 후보, Q=소환 대기, I=환영, R=합체 재료, P=참가자. 미변경 줄은 기준 HEAD, Legion 선택/회귀는 후속 조사 줄이다.

##### 공통으로 실제 사용되는 경로 (조사·패치 준비 기록)

- 레인 tower tick: [src/main/java/kim/biryeong/semiontd/game/PlayerLane.java:617](../src/main/java/kim/biryeong/semiontd/game/PlayerLane.java#L617)에서 ordered snapshot으로 기존 타워를 실행하고 membership을 확인한다. 뒤이어 `:623–625` LegionAugments, UndeadAugments, VillagerAdvAugments가 레인당 실행된다. `Tower.tick` ([src/main/java/kim/biryeong/semiontd/tower/Tower.java:1166](../src/main/java/kim/biryeong/semiontd/tower/Tower.java#L1166))는 사망·수면이면 중단, cooldown>0이면 1 감소 후 중단, `execute` 성공 때만 `cooldownTicksAfterExecute` 값을 설정한다. 지원탑의 실패 pulse는 다음 틱에 재시도한다. [src/main/java/kim/biryeong/semiontd/tower/SupportTower.java:24](../src/main/java/kim/biryeong/semiontd/tower/SupportTower.java#L24)는 이 execute 계약만 좁힌다.
- [src/main/java/kim/biryeong/semiontd/tower/EntityBackedTower.java:185](../src/main/java/kim/biryeong/semiontd/tower/EntityBackedTower.java#L185)는 unload 복구 후 기본 tick. 배치 `:61`, 상태변화 `:92`, 제거 `:106`, 라운드 reset `:120`, 파괴 확인/위치·HP 역동기화 `:145`. 일반 공격은 tower execute가 아니라 [src/main/java/kim/biryeong/semiontd/entity/tower/goal/TowerAttackMonsterGoal.java:46](../src/main/java/kim/biryeong/semiontd/entity/tower/goal/TowerAttackMonsterGoal.java#L46)의 entity AI이다.
- 타깃 재검사 상수는 [src/main/java/kim/biryeong/semiontd/entity/tower/goal/TowerAttackMonsterGoal.java:22](../src/main/java/kim/biryeong/semiontd/entity/tower/goal/TowerAttackMonsterGoal.java#L22)의 5. 캐시를 5회 감소한 다음 재검색하는 `:191–219`이므로 이를 모든 상황의 정확한 5틱 주기로 단정하면 안 된다. shared target `:177`, forced target `:222`, final-defense 대체검색 `:230`, 제어된 적 회피 검색 `:159`는 별도 경로다. 일반 선택 `:238–262`는 후보/encounter 목록 구성 후 custom selection, 없으면 priority/stable offset/distance max. 기본 공격 주기는 `:156`에서 runtime interval을 다시 받는다.
- 공격은 [src/main/java/kim/biryeong/semiontd/entity/tower/goal/TowerAttackMonsterGoal.java:133–148](../src/main/java/kim/biryeong/semiontd/entity/tower/goal/TowerAttackMonsterGoal.java#L133) → 공유 damage → augment primary resolved → `SemionTowerEntity.recordAttack`; [src/main/java/kim/biryeong/semiontd/entity/tower/SemionTowerEntity.java:663](../src/main/java/kim/biryeong/semiontd/entity/tower/SemionTowerEntity.java#L663)에서 Legion 증강에 실제 피해가 전달된다. VFX는 [src/main/java/kim/biryeong/semiontd/entity/tower/goal/TowerAttackMonsterGoal.java:150](../src/main/java/kim/biryeong/semiontd/entity/tower/goal/TowerAttackMonsterGoal.java#L150), [src/main/java/kim/biryeong/semiontd/entity/tower/vfx/TowerVfxService.java:132](../src/main/java/kim/biryeong/semiontd/entity/tower/vfx/TowerVfxService.java#L132) 공격, `:196` 추가타, `:664` area 공통 경로.
- 전용 palette는 [src/main/java/kim/biryeong/semiontd/entity/tower/vfx/VfxBuilderPaletteSelection.java:60–76](../src/main/java/kim/biryeong/semiontd/entity/tower/vfx/VfxBuilderPaletteSelection.java#L60)에서 ADV/Villager/Undead/Animal/Warlock/Legion으로 각각 라우팅. [src/main/java/kim/biryeong/semiontd/entity/tower/vfx/BuilderPalette.java:9–14](../src/main/java/kim/biryeong/semiontd/entity/tower/vfx/BuilderPalette.java#L9)는 별도 enum 값. 실제 packet/GPU 품질은 검증하지 않았다.
- 배치/교체/제거 membership은 [src/main/java/kim/biryeong/semiontd/game/PlayerLane.java:426](../src/main/java/kim/biryeong/semiontd/game/PlayerLane.java#L426), `:452`, `:478`; 교체 시 구 객체 onRemoved → replacement attach/list set → onPlaced 순서다. 가족별 임의 장기 캐시는 이 중간 상태와 wave/reset/death/revival/world를 함께 다뤄야 한다.
- job 자체의 event lifecycle은 [src/main/java/kim/biryeong/semiontd/job/JobBuilderLifecycle.java:32,35,40,41](../src/main/java/kim/biryeong/semiontd/job/JobBuilderLifecycle.java#L32)에서 Animal/Legion/Undead/Villager는 NONE. VillagerAdv/Warlock은 `:63–64`의 별도 lifecycle. NONE은 타워 메커니즘 부재라는 뜻이 아니다.

##### 남은 검증 및 후보 우선순위 (조사·패치 준비 기록)

1. 구현 Legion: 새 [src/test/java/kim/biryeong/semiontd/tower/legion/LegionGoatStackSelectionTest.java](../src/test/java/kim/biryeong/semiontd/tower/legion/LegionGoatStackSelectionTest.java) + 기존 Legion goat GameTests + 전체 통합 gate는 root가 조율 실행. 코드 검토와 diff-check 외 실행 결과를 이 문서에서 주장하지 않는다.
2. VillagerAdv: CONTEST due가 없는 대부분의 틱에서 정렬/대표맵을 만들지 않는 국소 fast path 검토. 실행 틱 순서는 기존 sorted 그대로 유지해야 한다.
3. Animal: 매틱 O(A²N) 경로를 실제 lane/callback 상태전이 관측과 함께 재설계하는 별도 scope 필요. 무효화 없는 1/tick 캐시는 하지 않는다.
4. Fox candidate wrapper, Undead donation collector, Bee poison immutable tick allocation은 낮은 범위 후보. RNG/공유 area 수집·callback 순서·상태 수명을 보존한 채 이득 근거를 마련해야 한다.
5. 모든 항목은 코드 수준 비용 판단. 다른 작업과 성능 측정을 조율하기 전 신규 벤치마크/서버를 실행하지 않았다. 실제 client GPU, reload, 운영 balance 수치 검증도 이번 개별 감사 단계에서는 수행하지 않았다.

#### 조사 묶음 B — Resonance, Illager, Nether, End, Ocean, AncientCity

복잡도: N=lane 타워, R=가족 타워, M=lane 몬스터, C=broadphase 후보, K=피해 대상, S=sculk 셀, D=지뢰, B=화상, A=흡수. 줄은 기준 HEAD이며 OceanWaterTower 208행 이후는 후속 수정에서 +6행이다.

##### 공통 흐름과 제외 근거 (조사·패치 준비 기록)

- 등록은 [src/main/java/kim/biryeong/semiontd/job/JobRegistry.java:67-72](../src/main/java/kim/biryeong/semiontd/job/JobRegistry.java#L67), 카탈로그 등록은 [src/main/java/kim/biryeong/semiontd/tower/ProductionTowerCatalogs.java:53-58](../src/main/java/kim/biryeong/semiontd/tower/ProductionTowerCatalogs.java#L53). End/Nether/Ocean/Resonance는 [src/main/java/kim/biryeong/semiontd/job/JobBuilderLifecycle.java:34-39](../src/main/java/kim/biryeong/semiontd/job/JobBuilderLifecycle.java#L34)의 NONE, AncientCity/Illager는 :43/:54의 별도 lifecycle이다.
- [src/main/java/kim/biryeong/semiontd/game/PlayerLane.java:617-629](../src/main/java/kim/biryeong/semiontd/game/PlayerLane.java#L617)는 ordered `List.copyOf(towers)`와 membership 검사를 거쳐 타워별 tick 후 Resonance cycle, Illager volley, entity 상태 동기화를 순서대로 실행한다. 가족에서 이 순서/새 타워 대기/삭제된 타워 생략 계약을 바꾸지 않았다.
- [src/main/java/kim/biryeong/semiontd/game/PlayerLane.java:143-145](../src/main/java/kim/biryeong/semiontd/game/PlayerLane.java#L143)의 activeMonsters는 기존 list, :252-254의 towers는 towerView다. 단순 `activeMonsters().isEmpty()`/`towers()` 자체를 목록 복사로 계산하면 잘못이다.
- [src/main/java/kim/biryeong/semiontd/tower/Tower.java:1166-1177](../src/main/java/kim/biryeong/semiontd/tower/Tower.java#L1166)은 살아 있고 깨어 있을 때 cooldown을 감소시키며, execute가 성공한 경우만 cooldown을 재설정한다. Ocean 지원/치유/공급, AncientCity 주문은 대상 없음/물 부족으로 실패할 때 매틱 재시도할 수 있다.
- [src/main/java/kim/biryeong/semiontd/entity/tower/goal/TowerAttackMonsterGoal.java:159-299](../src/main/java/kim/biryeong/semiontd/entity/tower/goal/TowerAttackMonsterGoal.java#L159)의 기본 목표는 재조회 cooldown 5틱(:22), cached/current/shared/final-defense 분기, forced-target 선행 조회를 사용한다. 실제 공격은 :125-156의 공통 damage/augment/record/VFX 순서를 따른다.
- [src/main/java/kim/biryeong/semiontd/tower/EntityBackedTower.java:145-205](../src/main/java/kim/biryeong/semiontd/tower/EntityBackedTower.java#L145)는 entity 생존 확인과 health/position 동기화, chunk unload 판정과 loaded anchor에서의 재생성을 한다. 단순 entityId/Java reference 캐시로 교체하면 재생성 및 world 수명 처리를 건너뛸 수 있다.
- [src/main/java/kim/biryeong/semiontd/entity/tower/SemionTowerEntity.java:734-750](../src/main/java/kim/biryeong/semiontd/entity/tower/SemionTowerEntity.java#L734)의 sourced refresh는 전/후 magnitude·ticks 조회 및 callback까지 수행한다. [src/main/java/kim/biryeong/semiontd/effect/TimedEffectSet.java:78-105](../src/main/java/kim/biryeong/semiontd/effect/TimedEffectSet.java#L78)는 같은 magnitude일 때 기존 ActiveTimedEffect의 duration만 갱신한다. 따라서 매틱 refresh를 매틱 effect 객체 할당으로 단정하지 않았다. magnitude가 바뀌면 새 객체를 만든다.

##### 다음 담당자가 실행할 검증과 남은 제한 (조사·패치 준비 기록)

1. Ocean focused JUnit: `kim.biryeong.semiontd.tower.ocean.OceanAugmentsTest` (3개 새 테스트 포함), 기존 [src/test/java/kim/biryeong/semiontd/tower/ocean/OceanCurrentControllerTest.java](../src/test/java/kim/biryeong/semiontd/tower/ocean/OceanCurrentControllerTest.java), OceanTowerCatalogTest.
2. 동일 연결·source attenuation·source removal 실제 확인: [src/gametest/java/kim/biryeong/semiontd/tower/ocean/OceanAugmentsGameTest.java](../src/gametest/java/kim/biryeong/semiontd/tower/ocean/OceanAugmentsGameTest.java), OceanTowerRuntimeTest. 공통 catalog/area/targeting 변경이 병합되면 root가 전체 test/runGameTest/remapJar gate 수행.
3. 순수 allocation 알고리즘 baseline/current는 동일 roster/최초 logicalId 상태/포화 비율/중복·동률·float값으로 비교해야 한다. 벤치 실행/수치 주장은 root 조율 후.
4. no source edit outside Ocean family/test. 모든 후보에 대해 실제 client GPU rendering, multiplayer, live reload, 운영 world 검증 미실행.

#### 조사 묶음 C — Adversary, Mage, Engineer, Insect, FutureAgency, Queen

복잡도: T=lane 타워, M=lane 활성 몬스터, K=필터 대상, C=회로, P=소환 대기, J=5장 창 조커. 별도 표기 없는 줄은 기준 HEAD의 위치다.

##### 적용한 독립 최적화와 검증 상태 (조사·패치 준비 기록)

1. **FutureAgency 밀집 통제 집계**: `FutureAgencyAgentTower.modifyAttackDamage`의 정렬 후 count를 공유 필터 스트림의 count로 변경했다. 제압 공격의 `targets()`는 기존 진행도 내림차순 + UUID 문자열 오름차순 정렬을 유지한다. 최악 시간 `O(M + K log K)`에서 `O(M)`, 집계 경로 중간 리스트/정렬 버퍼 `O(K)` 제거. UUID 문자열 비교 할당도 집계 경로에서는 없어졌다. 캐시·새 전역 상태 없음.
2. **Mage 일반 주문 시작의 primary 선택**: `MageWizardTower.tick`의 일반 주문 분기만 `firstPrioritizedInRange`로 바꿨다. 기존 income 여부→음수 진행도 comparator를 family 안에서 공유한다. 실제 `liveMonsters()` 스냅샷 생성은 유지한다. 최악 시간 `O(M + K log K)`에서 `O(M)`, 정렬·우선순위 결과·범위 결과 리스트가 불필요해졌다. 기존 live 리스트 `O(M)` 할당은 여전히 남는다. 세계 주문 및 미사일의 목록 경로는 유지했다.

변경 파일은 정확히 5개다.

- [src/main/java/kim/biryeong/semiontd/tower/futureagency/FutureAgencyAgentTower.java](../src/main/java/kim/biryeong/semiontd/tower/futureagency/FutureAgencyAgentTower.java)
- [src/gametest/java/kim/biryeong/semiontd/tower/futureagency/FutureAgencyGameTest.java](../src/gametest/java/kim/biryeong/semiontd/tower/futureagency/FutureAgencyGameTest.java)
- [src/main/java/kim/biryeong/semiontd/tower/mage/MageTowerRuntime.java](../src/main/java/kim/biryeong/semiontd/tower/mage/MageTowerRuntime.java)
- [src/main/java/kim/biryeong/semiontd/tower/mage/MageWizardTower.java](../src/main/java/kim/biryeong/semiontd/tower/mage/MageWizardTower.java)
- [src/gametest/java/kim/biryeong/semiontd/tower/mage/MageGameTest.java](../src/gametest/java/kim/biryeong/semiontd/tower/mage/MageGameTest.java)

기존 등록 GameTest 클래스에 새 사례 2건을 추가했다. 등록 파일 변경은 없다.

- `FutureAgencyGameTest.denseControlCountMatchesSortedReferenceAcrossLiveLaneChanges`: 실제 world 엔티티로 이전 sorted-count reference 비교, 정확한 반경 경계, 바깥 대상, dead/removed, 타 lane, 중복 lane 항목, entity와 logical health의 차이, 6회 순서 회전/반전, 주대상 교체, 이동, 빈 결과, null lane/center 및 실제 `modifyAttackDamage` 분기 확인. DENSE_CONTROL 선택은 다른 정책을 maxStacks 총합 이하에서 소진하므로 특정 RNG 결과를 가정하지 않는다.
- `MageGameTest.primarySelectionMatchesStableSortAndKeepsSpellDamage`: 이전 stable-sort reference와 12회 순서 변경, wave/income(owner 태그 및 senderTeam 태그), 동일 진행도 최초 encounter, dead/removed/logical health 0, stealth/dominated, 닫힌 반경 경계·직후 위치 변화·0반경·empty/null lane 비교. 실제 일반 WIND_CUTTER의 primary 피해와 다른 방향 대상 무피해, 주문 횟수도 확인한다.

담당 5파일 `git diff --check` exit 0. 기존 FutureAgencyAgentTower의 끝 CRLF 관련 Git 경고만 있다. 정적 패치 검토 완료 후 source freeze를 root에 알렸다. **JUnit/GameTest/Gradle/벤치/서버/패키징은 이 에이전트가 실행하지 않았다. root의 격리 통합 검증이 남아 있다.** 새 테스트의 통과를 주장하지 않는다. 기존 엔지니어 발판 선택·회로 역방향 탐색, 공통 AoE top-K는 신규 변경 실적으로 계산하지 않는다.

##### 공통 실제 진입점 및 보존 조건 (조사·패치 준비 기록)

- [src/main/java/kim/biryeong/semiontd/game/PlayerLane.java:569,617](../src/main/java/kim/biryeong/semiontd/game/PlayerLane.java#L569): 타워 틱은 `List.copyOf(towers)` 순서로 돌고 membership을 다시 확인한다. 같은 callback에서 제거된 타워는 생략하며 새 타워는 다음 스냅샷부터 참여한다. 마지막 `syncTowerStates()` 뒤 `InsectAugments.flush()`가 실행된다(:628-629).
- [src/main/java/kim/biryeong/semiontd/tower/EntityBackedTower.java:185](../src/main/java/kim/biryeong/semiontd/tower/EntityBackedTower.java#L185): unloading reason과 anchor chunk를 확인하여 필요 시 엔티티 재생성 후 super.tick. entity 객체·ID를 장기 캐시하면 이 수명 계약을 깨뜨릴 수 있다.
- [src/main/java/kim/biryeong/semiontd/entity/tower/goal/TowerAttackMonsterGoal.java:22,159-298](../src/main/java/kim/biryeong/semiontd/entity/tower/goal/TowerAttackMonsterGoal.java#L22): 기본 타깃 재확인 설정은 5ticks. forced target은 캐시 검사 앞에서 매 goal tick 평가(:172,222). 공통 후보는 world AABB와 valid/stealth/dominated/custom/range 필터를 거친다. Adversary/Queen의 강제 선택 비용은 일반 재검색 주기보다 자주 발생할 수 있다.
- [src/main/java/kim/biryeong/semiontd/job/JobBuilderLifecycle.java:139-159](../src/main/java/kim/biryeong/semiontd/job/JobBuilderLifecycle.java#L139): Engineer 상태는 lanes teardown 전, Adversary/Mage/FutureAgency/Queen 상태는 teardown 후 정리. Adversary 제거 중 점수 reconcile이 state를 다시 만들 수 있어 순서를 바꾸면 안 된다. [src/main/java/kim/biryeong/semiontd/job/JobLaneLifecycle.java:64-65](../src/main/java/kim/biryeong/semiontd/job/JobLaneLifecycle.java#L64)는 타워 wave 시작 이후 Adversary/FutureAgency 스냅샷을 캡처한다.
- VFX 실제 family 배정은 [src/main/java/kim/biryeong/semiontd/entity/tower/vfx/VfxBuilderPaletteSelection.java:97-115](../src/main/java/kim/biryeong/semiontd/entity/tower/vfx/VfxBuilderPaletteSelection.java#L97); 검증 근거는 [src/gametest/java/kim/biryeong/semiontd/entity/tower/vfx/TowerVfxGameTest.java:141-153](../src/gametest/java/kim/biryeong/semiontd/entity/tower/vfx/TowerVfxGameTest.java#L141). 이번 변경은 VFX 이벤트·수신자·버짓·시각화 값을 바꾸지 않는다.
- Engineer/Mage/Queen 보스바는 active game tick 뒤 호출된다([src/main/java/kim/biryeong/semiontd/game/SemionGameManager.java:1863-1875](../src/main/java/kim/biryeong/semiontd/game/SemionGameManager.java#L1863)). 각각 service `:24`에서 플레이어들을 점검하고, client 재동기화는 10회의 service 호출마다 수행한다. 매 호출 생성되는 set/map/text 비용은 존재하지만 네트워크·UI 규약 검증 없이 갱신 빈도를 바꾸지 않았다.

##### 남은 검증/측정 (조사·패치 준비 기록)

- root의 격리 `test runGameTest remapJar` 결과와 새 GameTest 2건 실제 실행 결과를 통합해야 한다. source freeze 뒤 이 에이전트는 audit 파일만 작성한다.
- 현재 변경은 정렬 제거의 구조적 상한 개선이다. inputs·호출 빈도·entity lookup·allocation까지 동일 조건으로 통제한 measurement는 아직 없으므로 MSPT/TPS/서버 전체 개선 수치를 제시하지 않는다.
- 실제 client GPU/HUD/VFX 및 multiplayer는 실행하지 않았다. 코드에서 효과 값/순서/수신자 변경은 없다. 운영 서버 시작·재시작, live reload, config 덮어쓰기, deploy/commit/push는 수행하지 않았다.
- 6종 각각의 audit를 수행했다는 것과 모든 개별 경로의 최적화 완료는 다른 상태다. 이 fragment의 구현 완료는 FutureAgency count와 Mage 일반 primary 두 지점에 한정된다.

#### 조사 묶음 D — HeroParty, Atlantis, Plant, Army, Thunder

복잡도는 각 항목의 타워 T, 몬스터 M, 지원 제공자 P, 수혜자 H, 영역 Z, 전역 엔티티 E, 관전자 P 등 현장 정의를 따른다. 소스 상수/fallback과 실제 운영 설정은 구분한다.

##### 적용한 독립 최적화 (조사·패치 준비 기록)

| 빌더 | 변경과 근거 | 비용 변화 | 보존 계약 |
|---|---|---|---|
| Thunder | [src/main/java/kim/biryeong/semiontd/tower/thunder/ThunderTower.java:347,362](../src/main/java/kim/biryeong/semiontd/tower/thunder/ThunderTower.java#L347): battery는 존재 여부만 사용. 기존 수집 ArrayList와 거리 정렬을 제거하고 동일 AoE 요청 + UNCHANGED callback의 `candidateCount()>0` 사용 | 기존 공통 질의 O(M) + 불필요 O(M log M)/O(M) 추가 공간 → 공통 질의 O(M)만. 공통 엔티티 목록·결과 할당은 남음 | [src/main/java/kim/biryeong/semiontd/tower/area/AreaEffectService.java:57](../src/main/java/kim/biryeong/semiontd/tower/area/AreaEffectService.java#L57)에서 alive/removed/runtime/lane/excluded/구형 거리<=/filter 전부 적용, `:165`에서 전원 callback, `:176`의 candidateCount는 필터 통과 목록 크기. VFX none·공격효과 없는 callback 유지. 조기 종료/캐시/새 월드조회 없음 |
| HeroParty | [src/main/java/kim/biryeong/semiontd/tower/hero/HeroCompanionSupportController.java:80,99](../src/main/java/kim/biryeong/semiontd/tower/hero/HeroCompanionSupportController.java#L80): 사제의 전체 wounded 정렬을 두 최소대상 선택으로 교체 | O(T + W log W), 추가 공간 O(W) → O(T), 최대2개 immutable list/O(1) | 소유자→가문→0<health<max 순서 유지. ratio=health/max(1,max), Double.compare 엄격음수만 교체해 stable encounter 동률 유지. 첫치료 전에 두 대상 확정, 기존 2차치료비율·실제회복·퀘스트·VFX 그대로. 월드/틱 캐시 없음 |

변경 파일은 위 production 2개와 아래 기존 테스트 2개만이다. 코드 주석은 추가하지 않았다.

- [src/test/java/kim/biryeong/semiontd/tower/hero/HeroCompanionSupportControllerTest.java:90](../src/test/java/kim/biryeong/semiontd/tower/hero/HeroCompanionSupportControllerTest.java#L90): 기존 full stable sorted reference와 64+후보·256개 seeded permutation 비교. 타주인/타가문/죽음/만피/NaN/중복객체 후보 포함.
- [src/test/java/kim/biryeong/semiontd/tower/hero/HeroCompanionSupportControllerTest.java:114](../src/test/java/kim/biryeong/semiontd/tower/hero/HeroCompanionSupportControllerTest.java#L114): 동률 encounter·선택 뒤 health 변화에도 사전 두대상 snapshot·새 선택의 재평가·빈/한명 후보.
- [src/gametest/java/kim/biryeong/semiontd/tower/thunder/ThunderGameTest.java:69](../src/gametest/java/kim/biryeong/semiontd/tower/thunder/ThunderGameTest.java#L69): empty, 다른 lane, 구 반경 바로 밖/정확경계, dead/removed/runtime 없는 entity, 제거즉시반영, 새entity반영, lane unregister 검사. 같은 기존 등록클래스에 추가, 새 method는 combat_arena, class는 RuntimeArenaFixture로 readiness와 assertion failure 전달 유지. 기존 battery 3발 소모 테스트도 남아 실제 caller 연결을 검사한다.
- `git diff --check` 2026-10-09 성공(exit0). 다른 agent 파일의 CRLF→LF 경고만 존재. JUnit/GameTest/패키징 실행 결과는 root 통합 검증으로 채워야 한다.

##### 공통 실제 실행 경로 (조사·패치 준비 기록)

[src/main/java/kim/biryeong/semiontd/game/PlayerLane.java:563-620](../src/main/java/kim/biryeong/semiontd/game/PlayerLane.java#L563)은 queued spawn → ordered tower snapshot(현재 membership 확인) → 토양 환경 → 몬스터 상태동기화/처치통지/삭제 순서다. [src/main/java/kim/biryeong/semiontd/tower/Tower.java:1166](../src/main/java/kim/biryeong/semiontd/tower/Tower.java#L1166)의 base tick은 health<=0/수면에서 return, cooldown>0이면 1 감소하고 return한다. 따라서 cooldown=N은 연속 execute 사이 N+1 tick이며 override가 super 뒤 수행하는 추가 작업은 base의 return으로 자동 중단되지 않는다. 기본 공격은 별도 [src/main/java/kim/biryeong/semiontd/entity/tower/goal/TowerAttackMonsterGoal.java:22,46,159,186,286](../src/main/java/kim/biryeong/semiontd/entity/tower/goal/TowerAttackMonsterGoal.java#L22) 경로(재조회 cooldown=5, invalidation/target 조건으로 재조회); 모든 가족의 매틱 공격목표 조회로 오해하면 안 된다. 삭제 중 callback으로 membership 변경될 수 있어 snapshot을 live view로 무조건 바꾸지 않았다.

##### 통합 시 남은 검증 / 한계 (조사·패치 준비 기록)

1. root에서 focused JUnit 두새테스트 및 기존 support test 실행, Thunder 새 GameTest·기존battery/chain/relay, 기존 HeroSupport integration 실행. 전체 gate `test runGameTest remapJar`는 root 단일 실행으로 조율.
2. 실제모드의 각빌더 비용수치/할당량은 현재 D가 측정하지 않음. 분석상 개선과 실측 claim을 분리. 병행 HOI/Gradle/서버가 없는 측정창에 root가 동일입력 baseline/변경 비교 필요.
3. 새 VFX/플레이어표현변경없음. GPU·실멀티플레이·운영reload·운영서버시작 미실시.
4. Army overflow 기능결함·Hero 외부anchor삭제 후 VISUALS 잔류 후보는 별도사안이며 이번 두 성능패치의 해결로 표기하지 않음.

#### 조사 묶음 E — DemonLord, Gamble, Succubus, Body, Pet

복잡도: T=lane 타워, M=activeMonsters, E=월드 엔티티, A=범위 후보, G=도박꾼, S=관전자 링크, P=바닥 열, D=개 무리, O=Pet 주인, C=Pet 동료, U/V=전체 꿈 타워/몬스터, u/v=현재 lane 꿈 상태. HashSet은 충돌/생성/리사이즈를 포함한 평균 복잡도다.

##### 실제 적용한 변경과 검증 준비 (조사·패치 준비 기록)

| 변경 | 기존 → 변경 | 보존 조건 | 회귀 |
|---|---|---|---|
| Gamble 관전자 1인 선택 | [src/main/java/kim/biryeong/semiontd/tower/gamble/GambleRoundEffects.java:72](../src/main/java/kim/biryeong/semiontd/tower/gamble/GambleRoundEffects.java#L72)의 sorted(comparator).findFirst → min(comparator). 정렬 O(G log G), O(G) 버퍼 → O(G) 비교, O(1) 선택 축약 상태. 필터의 O(T*S) 링크 수 조회 비용은 그대로이므로 전체 선택을 무조건 O(T)라고 표현하지 않는다. | owner/생존/runtimeEntity/radius<=/링크한도 필터, 점수 내림차순→실시간 거리→원래 x/y/z, 완전동률 첫 identity, sourceId 이전 링크 해제, 호출마다 현재 entity 조회 유지. RNG 호출 없음. 캐시 없음. | 신규 [src/gametest/java/kim/biryeong/semiontd/tower/gamble/GambleSpectatorSelectionTest.java](../src/gametest/java/kim/biryeong/semiontd/tower/gamble/GambleSpectatorSelectionTest.java) 두 @GameTest. RuntimeArenaFixture+combat_arena. 등록은 root 담당. 기존 GambleGameTest의 3링크 cap 회귀 유지. |
| DemonLord 소환 빈열 조회 | [src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordPassives.java:306](../src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordPassives.java#L306)의 P개 칸마다 T 타워 noneMatch → 호출 시작에 Set<Long> 점유 (x,z) 생성. 조회 부분 O(P*T) → 평균 O(T+P), 공간 O(T). floor resolve 비용은 그대로. `columnKey`는 x/z 전체 signed 32비트 각각 보존. | 바닥 조회 횟수·순서, 결과 리스트 순서, Y 무시, owner/team/health 필터 없음, 중복열, RNG 소비 순서 유지. 장기 캐시/월드 참조 없음. | 기존 `DemonLordPassiveTest.occupiedColumnsKeepAllBitsOfBothCoordinates` 81 경계쌍. 기존 `DemonLordGameTest.summonFreePositionsMatchNestedScanAfterTowerChanges`: 실제 floor resolver + 기존 nested oracle, 빈/추가/이동/제거/중복/다른높이/죽은foreign타워/경계열/다른lane, seeded shuffle 및 후속 RNG 비교. |

수정 범위는 위 production 2개, 기존 DemonLord JUnit/GameTest 2개, 신규 Gamble GameTest 1개다. 담당 tracked diff에 git diff --check 통과, 신규 파일 포함 UTF-8 읽기/줄끝 공백 0 확인. JUnit +1, GameTest +3이며 실행 결과는 아직 없다. 코드 주석 추가 없음. 성능 측정/게임플레이 통과 주장은 하지 않음. 완료 상태는 root 통합 실행 결과로 갱신 필요.

##### 남은 검증과 결론 범위 (조사·패치 준비 기록)

- root의 통합 JUnit + GameTest + remapJar, entrypoint 등록 및 정확한 신규 테스트 발견 확인 필요.
- 원형/최적화 동일 입력 벤치는 root 조율 전 미실행. 비교 횟수/복잡도 감소만 소스상 확인했으며 MSPT/TPS/실서버 개선 주장 없음.
- 코드 범위 최소변경만 적용. Succubus/Pet/Body는 감사 후 고위험 캐시를 보류한 명시적 결과이며 누락이 아니다.
- 수명 잔류는 후속 재현 후보로 구분했다. 기존 world cleanup/재접속/kill attribution/성장밸런스를 "최적화" 명목으로 바꾸지 않았다.

#### 조사 묶음 F — Developer, Frost, Pirate, Blueprint, MagicSchool, Default

복잡도: T=타워, N=몬스터 후보, A=닻 후보, B=CSV 토큰, E=bug enum, S=spell enum, Q=예약 포탄, R=ready 포탄, I=빙판, C=저주 시전자, P=world 플레이어. 소환 minion 수 K는 Blueprint 절에서 별도 정의한다.

##### 공통 호출 주기와 경계 (조사·패치 준비 기록)

- PlayerLane.tickTowers([src/main/java/kim/biryeong/semiontd/game/PlayerLane.java:617](../src/main/java/kim/biryeong/semiontd/game/PlayerLane.java#L617))는 ordered snapshot + membership 재확인 후 각 tower.tick. EntityBackedTower.tick([src/main/java/kim/biryeong/semiontd/tower/EntityBackedTower.java:185](../src/main/java/kim/biryeong/semiontd/tower/EntityBackedTower.java#L185))은 unload 재생성 후 Tower.tick([src/main/java/kim/biryeong/semiontd/tower/Tower.java:1166](../src/main/java/kim/biryeong/semiontd/tower/Tower.java#L1166)).
- Tower.tick은 health/sleep 검사, cooldown>0이면 감소 후 return, execute 성공 시 cooldown=d. 다른 감소가 없는 연속 활성 상태의 지원 execute 간격은 d+1 lane tick 호출이다. 평타 goal은 cooldown 감소 후 같은 tick 0 판정을 하므로 별도다.
- TowerAttackMonsterGoal([src/main/java/kim/biryeong/semiontd/entity/tower/goal/TowerAttackMonsterGoal.java:22](../src/main/java/kim/biryeong/semiontd/entity/tower/goal/TowerAttackMonsterGoal.java#L22))은 기본 재검사 상수5와 cache(:191), forced(:222), final-defense(:230) 경로가 다르다. 모든 빌더에 '5틱마다 scan'을 적용하면 부정확하다.
- EntityBackedTower.runtimeEntity(:43)는 world ID 조회 후 removed, fallback reference의 ID/world/removed를 확인한다. 새 장기 entity cache를 만들지 않았다.
- VfxBuilderPaletteSelection([src/main/java/kim/biryeong/semiontd/entity/tower/vfx/VfxBuilderPaletteSelection.java:58/133/142/148/151](../src/main/java/kim/biryeong/semiontd/entity/tower/vfx/VfxBuilderPaletteSelection.java#L58))이 Blueprint/Developer/Frost/Pirate/MagicSchool 전용 palette를 각각 반환하며 TowerVfxService.paletteFor(:624) 및 event context(:798)가 소비한다.
- Pirate player visual과 MagicSchool wizard는 FakePlayerTowerVisuals.tick([src/main/java/kim/biryeong/semiontd/tower/hero/FakePlayerTowerVisuals.java:414](../src/main/java/kim/biryeong/semiontd/tower/hero/FakePlayerTowerVisuals.java#L414))에서 매 tick P 플레이어 스캔, visible HashSet, tracked snapshot을 만들고 2tick 이동 조건(:446)으로 packet을 보낸다. viewers(:470) 목록도 생성한다. 가족 밖 공통 비용으로 기록하고 수정하지 않았다.

##### 전달 파일·검증 상태 (조사·패치 준비 기록)

수정 production 3개:
- [src/main/java/kim/biryeong/semiontd/tower/developer/DeveloperTowerData.java:242](../src/main/java/kim/biryeong/semiontd/tower/developer/DeveloperTowerData.java#L242)
- [src/main/java/kim/biryeong/semiontd/tower/pirate/PirateTower.java:182](../src/main/java/kim/biryeong/semiontd/tower/pirate/PirateTower.java#L182)
- [src/main/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolSpell.java:50/125/133](../src/main/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolSpell.java#L50)

신규 regression 3개(총14 tests):
- [src/test/java/kim/biryeong/semiontd/tower/developer/DeveloperBugLookupTest.java](../src/test/java/kim/biryeong/semiontd/tower/developer/DeveloperBugLookupTest.java) (5)
- [src/test/java/kim/biryeong/semiontd/tower/pirate/PirateAnchorSelectionTest.java](../src/test/java/kim/biryeong/semiontd/tower/pirate/PirateAnchorSelectionTest.java) (6)
- [src/test/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolSpellLookupTest.java](../src/test/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolSpellLookupTest.java) (3)

balance/persistent ID/RNG/callback/후보 범위 정책과 기존 테스트를 바꾸지 않았고 신규 코드 주석 없음. source freeze 후 독립 검증/벤치 실행 없음. 통합 test/runGameTest/remapJar, 동일입력 A/B 시간·할당 측정, 최종 docs/performance-optimization-report.ko.md 통합은 root가 수행한다.

### 미해결 후보와 다음 측정의 경계

Animal의 중첩 상태 재계산, VillagerAdv/Resonance의 미도래 정렬 생략, Succubus의 전역 꿈 상태 순회는 비용 후보로 남아 있다. callback 재진입, lazy logicalId UUID 생성 시점·순서, 배치/교체/죽음/부활/월드 수명을 보존할 근거 없이 전체 틱 캐시를 도입하지 않았다. 나머지 22종의 유지 판단 역시 측정상 개선 불필요 판정이 아니다.

Army의 초기 지휘 deadline 산술 overflow, fake-player visual의 외부 anchor 삭제 후 잔류, 다른 가족의 owner/UUID/state/reference 잔류는 **재현이 필요한 별도 기능·수명 가설**이다. 확정 누수나 이번 성능 패치의 해결 실적으로 기록하지 않는다. 정상 제거·종료 경로와 재접속/영속 상태 계약을 먼저 구분해야 한다.

앞선 타깃 최적화 철회, 부동소수점 1–2 ULP 차이, 전투 정규화·실행 실패·측정 한계는 위 원문에 그대로 남겼다. 이번 11종 변경의 기능 gate 통과로 그 기록을 지우거나 새 성능 개선 수치를 추정하지 않는다. 새 정량 비교는 다른 프로젝트/클라이언트 실행과 조율한 유휴 구간에서 동일 입력·호출 빈도·source manifest를 통제한 뒤 별도 추가한다. 운영 서버 시작·재시작, balance 덮어쓰기, 커밋·푸시·배포는 이 통합 문서 작업에서 수행하지 않았다.

## 남은 빌더 후보의 후속 검증

### 범위와 판정 기준

이 절은 위 보고서 2,373행·385,229바이트(SHA-256 `e2cdd7d16a5f170e7c6f8295073eca818749af66a478b4cbb69b832cafae5224`)를 그대로 보존한 뒤 추가한 후속 기록이다. 기준 production은 기존 gate4로 검증한 11종·12개 production 파일을 포함한 미커밋 26파일 상태다. 이 후속 조사에서 main production이나 기존 테스트를 추가로 변경하지 않았다.

자료는 앞서 정의한 로컬 작업 경로 W2의 `followup-a.md`부터 `followup-f.md`, `followup-root.md`이며, 핵심 판정과 근거를 이 절에 통합했다. 해당 원본 및 새 fixture는 작업 폴더 전용으로 원격 저장소에 포함된 첨부물이나 신규 저장소 테스트가 아니다. 원본 감사의 준비 단계 표현과 아래 최신 실행 상태를 구분한다.

현재 판정은 네 가지를 사용한다. **추가변경불필요**는 지정한 경로가 이미 선형 선택·상수 조회·의미 있는 주기 처리를 수행한다는 정적 판단이며 미측정이다. **검증후보**는 최소 변경의 경계를 제시했지만 채택·성능 향상은 확정하지 않은 항목이다. **보류**는 관찰 가능한 순서·상태·수명 차이가 있는 항목이다. **기존 선정경로 기능검증완료**는 앞선 gate4에서 확인한 11종의 한정된 변경만 가리킨다.

빌더 전체를 한 종류의 판정으로 덮지 않는다. 한 빌더에서 이미 효율적인 경로를 유지하면서 다른 후보를 보류할 수 있다. 모든 새 성능·할당 실측은 계속 **미측정**이며, HOI 활성 중에는 시작하지 않는다. 정적 복잡도를 MSPT/TPS 개선률로 환산하지 않는다.

아래 T는 lane 타워, M은 현재 조회 대상 몬스터, K는 선택 상한, A는 Animal 수 또는 별도 표시한 공간 후보, L은 leader/링크 후보, S는 soil/관전자 링크, U/V는 전역 꿈 타워/몬스터 상태, u/v는 현재 lane 상태다. Big-O의 입력과 실제 호출 조건을 각 행에 명시했다. 주기 숫자는 소스 상수·기본값이며 운영 balance 실측이 아니다.

### 등록 순서별 34항목의 현재 판단

[JobRegistry.java:31,61–93](../src/main/java/kim/biryeong/semiontd/job/JobRegistry.java#L31)의 Default 등록 뒤 built-in 33종 순서를 따른다. 0번 Default는 기본 직업이며 빌더 33종에 포함하지 않는다. “기존 선정”은 아래 11행뿐이고 새 Animal 실험 후보를 main 변경 수에 더하지 않는다.

| 순서·빌더 | 현재 판정 | 실제 빈도·복잡도와 소스 근거 | 효율적이거나 보류하는 이유 |
|---|---|---|---|
| 0. Default / 무직 | 추가변경불필요(전용 경로 없음·미측정) | [DefaultJob.java:8](../src/main/java/kim/biryeong/semiontd/job/DefaultJob.java#L8), [SemionJob.java:90,105](../src/main/java/kim/biryeong/semiontd/job/SemionJob.java#L90): 전용 타워 catalog·tick·공격·범위·소환·VFX 없음 | 공통 경제/권한 비용을 Default 전용 개선으로 계산하지 않는다. |
| 1. Villager / 주민 | 지정 경로 추가변경불필요(정적·미측정) | [AntiTankerCatTower.java:32–44](../src/main/java/kim/biryeong/semiontd/tower/villager/AntiTankerCatTower.java#L32)의 목표 재선택은 O(M) max. [AllayTower.java:44–141](../src/main/java/kim/biryeong/semiontd/tower/villager/AllayTower.java#L44) 지원 실행 때 O(M) 조회/적용; 빈 결과는 다음 tick 재시도. Thorn 카운터는 O(1) | 이미 full sort가 없다. 지원 예약·splash 재귀·사망 chain 순서가 실제 효과를 결정한다. 공유 runtime은 ADV도 사용한다. |
| 2. VillagerAdv / 주민 ADV | 보류: 정렬 전 no-due 반환 | [VillagerAdvAugments.java:79–88,131–132](../src/main/java/kim/biryeong/semiontd/tower/villager/VillagerAdvAugments.java#L79)의 CONTEST는 매 lane tick O(T log T), O(T) 후보/대표맵; [VillagerAdvStates.java:114–135](../src/main/java/kim/biryeong/semiontd/tower/villager/VillagerAdvStates.java#L114) 결과 E건의 membership은 O(E·T) | 경험치 동률 정렬이 due 전에도 lazy ID를 생성한다. 비동률 일괄 ID 선생성도 같은 계약이 아니다. 비동기 결과의 lane identity·현재 membership을 유지한다. |
| 3. Undead / 언데드 | 기존 RNG/선택 경로 추가변경불필요; collector 후보 보류 | [UndeadRangedSkeletonTower.java:106–125](../src/main/java/kim/biryeong/semiontd/tower/undead/UndeadRangedSkeletonTower.java#L106) 추가타마다 O(M) shuffle·world nextLong 1회. [UndeadAugments.java:91–110,189–207](../src/main/java/kim/biryeong/semiontd/tower/undead/UndeadAugments.java#L91) 흡혈 overflow 때 수집+min O(M), 부활 확인 매 tick O(T) | shuffle 축약은 난수 소비/선택을 바꾼다. DONATION min을 callback 안으로 옮기면 HP 읽기 시점이 달라진다. 미대상 debuff 재시도를 늦추지 않는다. |
| 4. Animal / 동물 | 검증후보: UNION 국소 guard의 격리 기능 대조 통과; 전체 틱 캐시 보류 | [AnimalStackTower.java:67–69,263–303](../src/main/java/kim/biryeong/semiontd/tower/animal/AnimalStackTower.java#L67) 각 동물 tick이 가족 전원을 갱신하여 O(A²T+A²L log L). 후보는 UNION 미선택일 때만 한 호출의 leader 수집을 생략 | 중간 사망·부활·이동·augment 변경을 다음 동물 tick이 관측한다. 후보는 전체 refresh/HP/callback을 유지하며 main에 미적용·성능 미측정이다. |
| 5. Warlock / 흑마법사 | 희생 선택 추가변경불필요; passive 캐시 보류 | [WarlockSacrificeController.java:35–56,93–102](../src/main/java/kim/biryeong/semiontd/tower/warlock/WarlockSacrificeController.java#L35) 희생은 피격 조건에서 O(T) min, passive는 능력치 조회마다 O(T). 희생탑 변이 때 core별 재조회 | 이미 단일 min이다. snapshot→kill 성공→commit→HP 순서, 범위≤0의 특수 의미와 다른 가문 희생 조건을 보존한다. |
| 6. Legion / 무리 | 기존 선정경로 기능검증완료; 순위 추가변경불필요 | [LegionGoatTower.java:66–68,105–143](../src/main/java/kim/biryeong/semiontd/tower/legion/LegionGoatTower.java#L66) 지원 수신자별 stable rank O(T), 선택 상태 O(1). [LegionAugments.java:178–204](../src/main/java/kim/biryeong/semiontd/tower/legion/LegionAugments.java#L178) clone/material tick O(I+R), 다중 선형 제거는 O(I²) 가능 | AoE predicate/callback의 독립 재조회와 동률·중복 identity를 유지했다. 합체 재료/원본/예약 큐 캐시는 보류한다. |
| 7. Resonance / 무블룸 | 보류: no-due·상위 K 먼저 검사 | [ResonanceService.java:33–48](../src/main/java/kim/biryeong/semiontd/tower/resonance/ResonanceService.java#L33) 매 tick O(T+R log R), O(R) 목록. [ResonanceTower.java:98–104](../src/main/java/kim/biryeong/semiontd/tower/resonance/ResonanceTower.java#L98) cycleDue는 lastCycle을 쓰며 cap 초과·entity 없음도 소비 | ID 생성과 due 소비를 둘 다 보존해야 한다. [ResonanceTowerLinkController.java:17–89](../src/main/java/kim/biryeong/semiontd/tower/resonance/ResonanceTowerLinkController.java#L17) O(R²) 링크는 wave 시작이지 매틱 재계산이 아니다. |
| 8. Illager / 우민 | 보류: forced target·표식 조회 캐시 | [IllagerTower.java:73–100,156–159,232–275](../src/main/java/kim/biryeong/semiontd/tower/illager/IllagerTower.java#L73) 강제 선택은 goal tick 경로, 효과 3종 refresh는 매 tick. [IllagerMarks.java:43–52,76–88](../src/main/java/kim/biryeong/semiontd/tower/illager/IllagerMarks.java#L43) 조회가 상태를 지움 | 외부 owner의 native mark 조회가 원본 표식을 삭제한다. native 끝 tick 포함/omen 끝 tick 제외, volley별 snapshot을 합치지 않는다. |
| 9. Nether / 네더 | 확인한 국소 경로 추가변경불필요; 장기 캐시 보류 | [NetherTower.java:93–121,409–414,586–621](../src/main/java/kim/biryeong/semiontd/tower/nether/NetherTower.java#L93) 빈 목록 isEmpty O(1), 추가타 발동 때만 O(M) min. [NetherBloodChargeController.java:9–40](../src/main/java/kim/biryeong/semiontd/tower/nether/NetherBloodChargeController.java#L9) FIFO는 과거 charge 값을 보관 | 이미 선형/상수 경로다. revival→효과→감쇠→실제 HP→phase 순서와 threshold 변경 전 charge 값이 의미 있다. |
| 10. End / 엔드 | 보류: 사후 효과·흡수 캐시; 빈 결과 할당은 검증후보 | [EndTower.java:177–204](../src/main/java/kim/biryeong/semiontd/tower/end/EndTower.java#L177) 사망 검사보다 mines/burns tick이 먼저. [EndTransferController.java:29–51,140–153,231–277](../src/main/java/kim/biryeong/semiontd/tower/end/EndTransferController.java#L29) 매 tick O(T+A), identity 상태 재사용 | source 사망 뒤 효과, partial delta·kill 성공·rollback은 실동작이다. empty record 공유도 기존 transaction을 지난 뒤에만 검토한다. |
| 11. Ocean / 바다 | 기존 선정경로 기능검증완료; source 캐시 보류 | [OceanWaterTower.java:194–213](../src/main/java/kim/biryeong/semiontd/tower/ocean/OceanWaterTower.java#L194) 공급 allocations의 반복 min O(R²)→O(R). [OceanWaterTower.java:248–255](../src/main/java/kim/biryeong/semiontd/tower/ocean/OceanWaterTower.java#L248) 수신자별 현재 공급원 O(RT)은 남음 | wave recipient 자격은 originalPosition snapshot, source count는 현재 생존/설정이다. 실패 execute에 cooldown을 추가하지 않는다. |
| 12. AncientCity / 고대 도시 | 검증후보: 후보 snapshot 뒤 로컬 stable top-K | [AncientCityTower.java:159–181,293–317,344–356](../src/main/java/kim/biryeong/semiontd/tower/ancientcity/AncientCityTower.java#L159) 유효 후보가 있는 Warden 시전에서 O(M log M); 빈 대상은 검색만 재시도 | maxHP↓→거리↑→entity UUID 문자열↑·완전동률 encounter·중복 슬롯을 보존해야 한다. sculk xz 인덱스는 다른 y/수명 때문에 별도 보류다. |
| 13. Adversary / 히어로 | 강제 selector 추가변경불필요; 공격 snapshot 캐시 보류 | [AdversaryFoxTower.java:237–310,957–1023,1549](../src/main/java/kim/biryeong/semiontd/tower/adversary/AdversaryFoxTower.java#L237) forced max O(M), 추가사격/splash는 공격 때. [AdversaryTeamEffects.java:52–150](../src/main/java/kim/biryeong/semiontd/tower/adversary/AdversaryTeamEffects.java#L52) team refresh20·회복40/60 등 각 interval | focus는 hit 시점의 현재 target을 읽고 firework는 index별 피해가 다르다. 단일 sculk 예약을 무한 queue 부하로 취급하지 않는다. |
| 14. Mage / 마도사 | 기존 선정경로 기능검증완료; missile 확장은 보류 | [MageWizardTower.java:181,217–222,364,437](../src/main/java/kim/biryeong/semiontd/tower/mage/MageWizardTower.java#L181) 준비된 일반 시전만 min, missile은 발사별 탐색. [MageTowerRuntime.java:34–59](../src/main/java/kim/biryeong/semiontd/tower/mage/MageTowerRuntime.java#L34) live snapshot O(M)+min | 일반 선택은 이미 선형. 여러 발의 retarget/core 손실·hit별 mana refund 이후 피해를 고정하면 안 된다. |
| 15. Engineer / 기술자 | 앞선 발판·회로 선택 추가변경불필요; topology 캐시 보류 | [EngineerGolemTower.java:127–196](../src/main/java/kim/biryeong/semiontd/tower/engineer/EngineerGolemTower.java#L127) 유효 plate 유지, 재선택 O(T) min. [EngineerTrapSignalController.java:21](../src/main/java/kim/biryeong/semiontd/tower/engineer/EngineerTrapSignalController.java#L21) 회로 BFS O(C+E)+min; 장치별 발사/활성 interval | 기존 2b 이전 min을 새 11종 실적으로 중복 계산하지 않는다. press/power/topology 및 world 복구가 바뀌므로 전역 틱 캐시를 쓰지 않는다. |
| 16. Insect / 벌레 | 보류: anyMatch·연결키 선필터·캐시 | [InsectUnitTower.java:149–212,390–408](../src/main/java/kim/biryeong/semiontd/tower/insect/InsectUnitTower.java#L149) 부활 대기 tick/파괴 조회의 전체 anchor 검사, 최악 O(T×연결키수). 기본 spawner pulse80·HATCH는 부활 이벤트 | 뒤에서 키로 탈락할 생성기도 isDestroyed가 먼저 HP/위치를 동기화한다. 목록이 필요한 VFX/키 snapshot과 boolean 조회를 구분한다. |
| 17. FutureAgency / 미래기관 | 기존 선정경로 기능검증완료; 추가 count 변경불필요 | [FutureAgencyAgentTower.java:294,540–556](../src/main/java/kim/biryeong/semiontd/tower/futureagency/FutureAgencyAgentTower.java#L294) 밀집 damage 평가마다 O(M) count; suppression은 진행도↓/entity UUID 문자열↑ 정렬 유지. survivor bonus는 평가마다 O(T) | 같은 active 항목의 world lookup·중복·identity exclusion을 유지했다. logical health/removed/lane 재검증을 임의 추가하지 않는다. |
| 18. Queen / 붉은 여왕 | 단일 selector 추가변경불필요; common-nearest 치환 보류 | [QueenCardTower.java:160,205–211,268–287](../src/main/java/kim/biryeong/semiontd/tower/queen/QueenCardTower.java#L160) 강제 선택 O(M), splash hit 때 범위 후보 C개 전체 정렬로 O(M+C log C), 최악 O(M log M) 후 공통 AoE 재질의. [QueenPoker.java:16–103](../src/main/java/kim/biryeong/semiontd/tower/queen/QueenPoker.java#L16) poker는 wave 경계, joker 최대 13^J | family 동일거리 tie는 lane encounter, common은 cap 초과 때 logical UUID다. 회복 실패 재시도·runner 이동/접촉·overlay 수명을 캐시로 합치지 않는다. |
| 19. HeroParty / 용사 | 기존 선정경로 기능검증완료; 사제/궁수/도적 추가변경불필요 | [HeroCompanionSupportController.java:52–120](../src/main/java/kim/biryeong/semiontd/tower/hero/HeroCompanionSupportController.java#L52) 사제 설정 d+1 tick 때 O(T), 2개 저장. [HeroCompanionTower.java:56–73,407–411](../src/main/java/kim/biryeong/semiontd/tower/hero/HeroCompanionTower.java#L56) mage 밀도 비교는 O(M²) | mage 밀도 호출당 재계산 축소도 O(M²)은 남는다. Knight/Bard 제공자 재조회·fake visual 수명은 별도 보류다. |
| 20. Atlantis / 아틀란티스 | 보류: nearest 치환·source 인덱스; 로컬 선택은 후보 | [AtlantisTower.java:120–145,359–388,496–506](../src/main/java/kim/biryeong/semiontd/tower/atlantis/AtlantisTower.java#L120) dolphin 매 tick O(D·E), turtle 기본10 tick; 전달/폭발마다 O(M log M) stable 거리 정렬 | common은 cap 이하 무정렬·동률 UUID·0 limit 거부다. 압력 폭발의 HashMap 순서·owner 교체·재진입/정리를 보존해야 한다. |
| 21. Plant / 식물 | soilAt/Panda/지뢰 추가변경불필요; count 인덱스 후보 | [PlantSoilStates.java:97–103,139–164,175–190](../src/main/java/kim/biryeong/semiontd/tower/plant/PlantSoilStates.java#L97) 단일 조회 기대 O(1), count O(S). 실제 피해와 환경 기본20 tick에서 반복; Roots는 비-pulse도 순회 | count는 현재 물리 블록 수가 아니라 성공 등록된 soil 수다. 성공 put/remove/owner clear에 맞춘 인덱스만 가능하며 RNG·성장/효과 순서는 유지한다. |
| 22. Army / 군대 | 기존 기능 결함 재현확정; 성능 변경 보류 | [ArmyTower.java:56,305–317,330–373](../src/main/java/kim/biryeong/semiontd/tower/army/ArmyTower.java#L56) 장비는 매 tick. 의도상 command20 tick·O(T)이지만 초기 Long.MIN_VALUE 차감 overflow로 첫 실행이 차단됨 | 별도 실제 GT에서 기대0.12/실제0 확인. 의도한 O(A·T)/20을 현재 활성 부하로 쓰지 않는다. 수정은 별도 기능 단계다. |
| 23. Thunder / 람쥐썬더 | 기존 선정경로 기능검증완료; battery 선택 추가변경불필요 | [ThunderTower.java:99–125,347–391](../src/main/java/kim/biryeong/semiontd/tower/thunder/ThunderTower.java#L99) 각 타워 grid10 tick O(T), battery 조건 충족 시 동일 AoE O(M)의 candidateCount. 추가 목록/정렬은 제거됨 | 살아있는 상태·rod 예외·잃은 HP·storm roll·타워별 위상과 relay visited 순서를 전역 캐시로 고정하지 않는다. |
| 24. DemonLord / 마왕 | 기존 선정경로 기능검증완료; 점유 선택 추가변경불필요 | [DemonLordPassives.java:200–219,234–277,306–331](../src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordPassives.java#L200) 소환 이벤트의 열 점유 평균 O(T+F), O(T) Set. [DemonLordService.java:601–615](../src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordService.java#L601) 비전투 어그로 해제는5 tick O(E) | 모든 가문/owner/죽은 roster/xz를 포함하며 바닥·shuffle 순서를 유지. world 어그로를 lane 목록으로 줄이거나 관통 검기를 min으로 바꾸지 않는다. |
| 25. Gamble / 겜블 | 기존 선정경로 기능검증완료; 링크 count는 후보 | [GambleSupportTower.java:121–160](../src/main/java/kim/biryeong/semiontd/tower/gamble/GambleSupportTower.java#L121) 관전자 선택은 wave 시작. [GambleRoundEffects.java:60–100](../src/main/java/kim/biryeong/semiontd/tower/gamble/GambleRoundEffects.java#L60) min O(G), link count 포함 전체 O(T+G·S+G) | 선택을 매틱 O(T)로 축약하지 않는다. release 후 호출별 위치 count-map은 후보지만 synchronized 재진입·live entity/중복 key 순서를 검증해야 한다. |
| 26. Succubus / 서큐버스 | 보류: 전역 상태 캐시; key 할당 축소는 후보 | [SuccubusDreams.java:160–199,314–332,395–405](../src/main/java/kim/biryeong/semiontd/tower/succubus/SuccubusDreams.java#L160) 매 lane tick O(U+uT+V log V+vM)+state별 core 조회. [SuccubusTower.java:96–104](../src/main/java/kim/biryeong/semiontd/tower/succubus/SuccubusTower.java#L96) 일반 min은 O(M) | 두 snapshot 생성 시점·mutable state·entity fallback·UUID 순서·wake 재진입이 다르다. 제거 cooldown 잔류는 별도 수명 계약 문제다. |
| 27. Body / 신체 | heartbeat/눈/primary 추가변경불필요; secondary1 min은 후보 | [BodyTower.java:70–143,184–255](../src/main/java/kim/biryeong/semiontd/tower/body/BodyTower.java#L70) 심장 d+1 tick ordered snapshot, brain max/눈 최근접 O(M), 눈 ray는 전원 관통. genital은 primary 후 새 query+정렬 | 다른 owner Body도 심장 수신자다. brain→후속 기관 피해 순서, ray의 3D 공통 필터, primary 사망 뒤 secondary를 보존해야 한다. |
| 28. Pet / 반려동물 | 매틱 관계 캐시 불필요; 단일 환자 min은 후보 | [PetBondService.java:37–135,171–197](../src/main/java/kim/biryeong/semiontd/tower/pet/PetBondService.java#L37) refresh≈O(T+C·O+C²+D²); [PetTower.java:184–279,410–429](../src/main/java/kim/biryeong/semiontd/tower/pet/PetTower.java#L184) 배치/사망/제거/성장/round별 호출, tick은 visual만 | round에 Pet P개면 refresh도 대략 P회 가능하다. dead-owner loyalty·originalPosition·final-defense freeze를 유지한다. [PetTower.java:483–503](../src/main/java/kim/biryeong/semiontd/tower/pet/PetTower.java#L483) 치유1명만 min 후보다. |
| 29. Developer / 개발자 | 기존 선정경로 기능검증완료; live lookup 유지 | [DeveloperTowerData.java:230–242](../src/main/java/kim/biryeong/semiontd/tower/developer/DeveloperTowerData.java#L230) hasBug는 stat/공격 hook마다 최악 O(B·E), split 유지·Set 제거. [DeveloperTower.java:448,479,600](../src/main/java/kim/biryeong/semiontd/tower/developer/DeveloperTower.java#L448) min/max O(M), timer O(1) | bugs()의 mutable·중복제거·첫 등장 순서는 별도 소비 계약이다. direct write/copy/reload 무효화 없는 장기 파싱 캐시는 쓰지 않는다. |
| 30. Frost / 혹한 | 검증후보: 동일 호출 네 count 통합; wave cache 보류 | [FrostTeamEffects.java:41–58,117](../src/main/java/kim/biryeong/semiontd/tower/frost/FrostTeamEffects.java#L41) wave snapshot 하나를 네 계열로 반복 count; 현재/단일순회 모두 O(T). [FrostFullOperationService.java:153,415](../src/main/java/kim/biryeong/semiontd/tower/frost/FrostFullOperationService.java#L153) 활성 상태는 매 tick O(T) | 계열별 threshold3/6/9 후 clamp, dead/temp 포함과 owner/team을 유지한다. wave callback 사이 roster 변경과 저장된 스탯을 전역 cache로 합치지 않는다. |
| 31. Pirate / 해적 | 기존 선정경로 기능검증완료; shell 분리는 후보 보류 | [PirateTower.java:128–139,182–208](../src/main/java/kim/biryeong/semiontd/tower/pirate/PirateTower.java#L128) anchor rank O(T), 수신자별이면 전체 O(T²) 가능. [PirateAugments.java:150](../src/main/java/kim/biryeong/semiontd/tower/pirate/PirateAugments.java#L150) ready list/removeAll 최악 O(Q·R) | helper team 조건과 실제 recipient owner/team/combatLane 필터를 구분한다. deadline heap은 ready 포탄의 encounter 실행 순서를 바꿀 수 있다. |
| 32. Blueprint / 빌더 빌더 | 보류: common-nearest 치환; 로컬 stable top-K 후보 | [BlueprintTower.java:302–318](../src/main/java/kim/biryeong/semiontd/tower/blueprint/BlueprintTower.java#L302) 매 multishot O(M log M), O(M) snapshot; [BlueprintTowerSummonController.java:24,68](../src/main/java/kim/biryeong/semiontd/tower/blueprint/BlueprintTowerSummonController.java#L24) 만료 매 tick O(K) | 후보는 source 사거리지만 정렬은 primary 거리다. 항상 거리순/encounter tie·dominated 제외·고정 cap/무보충 및 chain encounter를 보존한다. |
| 33. MagicSchool / 마법학교 | 기존 선정경로 기능검증완료; 조회/강제 선택 추가변경불필요 | [MagicSchoolSpell.java:50,125,133](../src/main/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolSpell.java#L50) ID 조회 기대 O(1), startup O(S). [MagicSchoolWizardTower.java:61,431,440,493](../src/main/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolWizardTower.java#L61) live 학습조건·goal tick O(M) 강제 선택 | map은 상태/target cache가 아니다. 보호는 다른 가문에도 적용되지만 lookup의 VFX caller는 wizard guard 안이다. curses/props/source·round 수명을 유지한다. |

각 “추가변경불필요”는 해당 셀에 적은 경로에 한정한다. 잔여 후보가 있는 빌더를 측정상 불필요 또는 전 경로 최적화 완료로 승격하지 않는다. 작업 폴더 전용 fixture 결과도 기존 974개 release GameTest 결과에 합산하지 않는다.

### 앞선 상세 감사의 모호한 설명 정정

**공통 AoE에는 현재 bounded top-K가 없다.** [AreaEffectService.java:71–76](../src/main/java/kim/biryeong/semiontd/tower/area/AreaEffectService.java#L71)은 후보 수가 maxTargets보다 클 때만 거리→Monster.logicalId로 **전체 정렬**한 뒤 limit한다. cap 이하에서는 encounter 순서를 그대로 쓴다. 앞선 감사 보충에서 “공통 AoE top-K 완료/수혜”라고 읽힐 수 있는 표현은 이 설명으로 정정한다. 앞선 실험의 철회 기록과 TimedEffectSet의 효과 top-K를 혼동하지 않는다.

**세 가지 동률 순서가 다르다.** [Monster.java:67,360–361](../src/main/java/kim/biryeong/semiontd/entity/monster/Monster.java#L67)의 logicalId는 생성 시 정해진 final UUID의 단순 getter다. [Tower.java:88–106,312–318](../src/main/java/kim/biryeong/semiontd/tower/Tower.java#L88)는 생성자에서 ID를 채우지 않고 첫 logicalId 조회에서 UUID를 생성·저장한다. AncientCity/FutureAgency의 entity UUID 문자열 순서는 UUID.compareTo의 signed long 순서와도 다르다. encounter 순서, UUID 값 비교, UUID 문자열 비교를 서로 바꿔 쓰지 않는다.

**Insect anchor의 부작용은 정확히 구분한다.** [InsectUnitTower.java:390–408](../src/main/java/kim/biryeong/semiontd/tower/insect/InsectUnitTower.java#L390)은 연결키/거리 필터보다 먼저 isLivingAnchor를 평가한다. 생성기에는 [EntityBackedTower.java:145–167](../src/main/java/kim/biryeong/semiontd/tower/EntityBackedTower.java#L145)의 world lookup·health/position 동기화가 일어나고, [Tower.java:1155–1159](../src/main/java/kim/biryeong/semiontd/tower/Tower.java#L1155)의 round metrics alive 갱신까지 이어질 수 있다. 생성기 조회 자체가 곤충 사망 폭발을 실행한다는 뜻은 아니다. 사망 폭발·유충 예약은 [InsectUnitTower.java:149–194](../src/main/java/kim/biryeong/semiontd/tower/insect/InsectUnitTower.java#L149)의 곤충 override에 있다.

**구형 범위 검사는 broadphase 뒤에 있다.** [AreaEffectService.java:55–70,226–234](../src/main/java/kim/biryeong/semiontd/tower/area/AreaEffectService.java#L55)은 AABB와 entity bounding box가 겹친 후보에 3D 거리≤radius²를 적용한다. 수평 exact boundary와 몬스터 발이 box 위쪽 Y면에만 닿는 경우가 같은 포함 결과를 보장하지 않는다. 필터를 직접 거리 계산 하나로 대체하지 않는다.

**공통 action은 선택을 자동 보충하지 않는다.** [AreaEffectService.java:153–181](../src/main/java/kim/biryeong/semiontd/tower/area/AreaEffectService.java#L153)은 선정된 목록을 순서대로 돌며 각 action 직전 위치를 보관한다. 앞 callback이 뒤 후보를 제거해도 전체 재질의·빈자리 보충·생존 재필터를 자동 수행하지 않는다. 새 top-K나 nearest 치환의 계약은 대상 집합뿐 아니라 snapshot과 타격/VFX 순서까지 포함한다.

**일괄 주기 설명도 제한한다.** [Tower.java:1166–1177](../src/main/java/kim/biryeong/semiontd/tower/Tower.java#L1166)의 성공 execute cooldown=d는 방해 없는 경우 다음 실행까지 d+1 lane tick이다. [TowerAttackMonsterGoal.java:172,191–227](../src/main/java/kim/biryeong/semiontd/entity/tower/goal/TowerAttackMonsterGoal.java#L172)의 forced target은 일반 cache 검사보다 먼저 호출된다. Pet 관계 갱신과 Resonance 링크, Frost family-count는 wave/event 경로이며 “비싼 루프가 있다”는 이유만으로 매틱 부하로 계산하지 않는다.

### 최소 후보의 허용 경계와 현재 준비 상태

#### Animal: 현재 호출의 UNION 전용 수집만 생략

[AnimalStackTower.java:279–303](../src/main/java/kim/biryeong/semiontd/tower/animal/AnimalStackTower.java#L279)의 원본은 previousMaxHealth/previousUnion을 읽고 leader 목록을 만든 뒤 has(UNION)을 확인한다. 격리 후보는 두 previous 값을 읽은 직후 has(UNION)을 **한 번** 읽어 false인 경우만 목록 생성을 생략한다. true이면 원래 stream/distinct/sort를 그대로 사용한다.

[Tower.java:108–110,153–155,288–290,325–327](../src/main/java/kim/biryeong/semiontd/tower/Tower.java#L108)의 type/owner/health/augmentSnapshot, [AnimalStackTower.java:84–86](../src/main/java/kim/biryeong/semiontd/tower/animal/AnimalStackTower.java#L84)의 isLeader는 현재 production에서 필드/ID 읽기이며 해당 tower getter override는 찾지 못했다. 제거 구간에는 logicalId·RNG·heal·damage·callback이 없다. hasUnion을 호출 간 저장하지 않으며 이후 leader 존재·범위·HP·onStateChanged 순서도 그대로다.

이 후보는 non-UNION 호출의 O(T+L log L) 하위 스캔·임시 자료구조만 줄이고 O(A²T) 전체 refresh는 남긴다. 전체 틱 캐시와는 다른 변경이다. W2의 `followup-a/candidate-production/src/main/.../AnimalStackTower.java` 및 `animal-union-guard.patch`에만 있고 **main에는 미적용**이다.

원본 SHA-256은 `b9c13361232ac4a3ee2cc3452c20f63b3833b6f8d9e5c366ae56cfc5033cfc19`, 후보는 `4835f1cd1f937228889b7f42662bf97433f30533763f4ef7ae72fb64020998bc`다. 동일한 새 Animal JUnit2가 gate4 production baseline과 candidate에서 통과했다. candidate에서는 기존 Animal 회귀도 함께 통과했으며 아래 실행표에 별도로 집계했다. 실험 뒤 격리 파일을 원본으로 복원했고 main 원본 해시도 유지했다. 한정된 기능 대조 통과이며 성능 향상 확정이 아니다.

#### AncientCity: Warden 후보 snapshot 뒤의 bounded 선택

[AncientCityTower.java:344–356](../src/main/java/kim/biryeong/semiontd/tower/ancientcity/AncientCityTower.java#L344)의 기존 query/filter를 그대로 지난 목록에만 적용하는 후보다. 목표는 O(M log K+K log K)의 선택이지만 runtime maxHP↓→거리↑→entity UUID 문자열↑와 동률 encounter index, 같은 참조 중복 슬롯을 유지해야 한다. 선택은 첫 피해 전에 완료하고 [AncientCityTower.java:301–317](../src/main/java/kim/biryeong/semiontd/tower/ancientcity/AncientCityTower.java#L301)의 hit별 현재 healthBonusDamage·kill·sculk 변화는 그대로 둔다.

작업 폴더 prototype JUnit은 full stable sort와 identity 목록을 대조한다. Ranked 레코드를 최악 O(M)개 만들고 기존 후보 목록도 남으므로 heap 보유 O(K)를 총할당 O(K)로 보고하지 않는다. 작은 M/K에서 더 빠르다는 근거가 없고 production patch도 없다. 실제 cast/execute GameTest와 모델의 증명 범위는 별개다.

#### Frost: 한 snapshot의 네 독립 count만 통합

[FrostTeamEffects.java:41–58,117](../src/main/java/kim/biryeong/semiontd/tower/frost/FrostTeamEffects.java#L41)에서 현재 호출의 owner/team/fallback roster snapshot을 유지하고 네 독립 카운터를 한 순회에서 계산하는 최소 후보다. 현재와 후보 모두 O(T)이며 상수 계수·stream/list 비용의 차이만 기대한다. 네 계열의 상호배타성을 가정한 else-if나 총합 하나의 threshold로 바꾸지 않는다.

[FrostBalance.java:248,261](../src/main/java/kim/biryeong/semiontd/tower/frost/FrostBalance.java#L248)의 계열별 threshold 후 최종 clamp, 죽은/임시복제 등록 타워 포함을 유지한다. [FrostVanguardTower.java:44–64](../src/main/java/kim/biryeong/semiontd/tower/frost/FrostVanguardTower.java#L44) 등 각 wave callback은 그 시점 roster를 읽고 스탯을 저장하므로 wave 전역 cache나 이후 항상 live 재계산은 모두 다르다. 단일순회 oracle fixture만 준비됐고 production에는 미적용이다.

#### Pet·Body: 실제로 한 명만 필요한 분기

[PetTower.java:483–503](../src/main/java/kim/biryeong/semiontd/tower/pet/PetTower.java#L483)의 bird 치유에서 maxPatients==1이면 같은 yard/owner/team/lane·부상 필터 뒤 ratio min으로 축약할 여지가 있다. 선정은 grown-up selfHeal **이전**이며 maxHP≤0 fallback과 완전동률 첫 encounter를 보존해야 한다. 여러 환자 분기는 기존 정렬을 유지하는 최소안이고 아직 patch나 후보 실행은 없다.

[BodyTower.java:211–255](../src/main/java/kim/biryeong/semiontd/tower/body/BodyTower.java#L211)의 extraTargets==1도 primary 실제 타격 **후** 기존 secondary query를 유지한 채 거리 min만 적용하는 후보다. primary가 죽어도 secondary는 진행한다. primary 전에 query를 당기거나 common nearest의 UUID tie를 쓰면 동작이 달라진다. 새 Body fixture는 heartbeat/ray 계약을 보강하며 이 미구현 min 후보를 검증했다고 계산하지 않는다.

#### 추가 자료구조 후보는 수명 비용까지 포함

Plant count 인덱스는 [PlantSoilStates.java:75,139–164,175–190](../src/main/java/kim/biryeong/semiontd/tower/plant/PlantSoilStates.java#L75)의 성공 등록/제거/owner clear와 동일 수명이어야 한다. 외부 블록 변경에도 registry count는 유지될 수 있고 release는 외부 블록을 덮어쓰지 않는다. 실제 블록 종류를 polling하는 캐시는 대체안이 아니다.

Gamble은 기존 link release 뒤 호출 한 번의 위치→count map을 만들면 O(T+G·S+G)를 평균 O(S+T+G)로 줄일 후보가 있다. [GambleRoundEffects.java:60–100](../src/main/java/kim/biryeong/semiontd/tower/gamble/GambleRoundEffects.java#L60)의 synchronized 재진입·live 조회 및 작은 S의 생성비용을 아직 검증하지 않아 미구현이다.

Succubus는 [SuccubusDreams.java:160–199,511–512](../src/main/java/kim/biryeong/semiontd/tower/succubus/SuccubusDreams.java#L160)의 반복 TowerKey 생성만 null-safe owner/originalPosition 값 비교로 바꾸는 작은 후보부터 검토할 수 있다. 전체 상태 순회 상한은 그대로다. per-lane/per-tick cache는 mutable state 귀속·entity fallback·순차 snapshot·wake 전파 때문에 별도 보류다.

Pirate의 ready/pending 분리는 [PirateAugments.java:150](../src/main/java/kim/biryeong/semiontd/tower/pirate/PirateAugments.java#L150)의 encounter 순서와 “먼저 제거한 뒤 callback”을 보존해야 한다. End empty-result 공유는 [EndTransferController.java:231–277](../src/main/java/kim/biryeong/semiontd/tower/end/EndTransferController.java#L231)의 capture·partial delta·completion·rollback을 지나야 한다. 두 항목 모두 작은 상태에서 자료구조 비용을 정당화할 실측이 없어 patch로 확대하지 않았다.

### 동등하지 않은 대체안과 재현할 계약

#### 정렬 전 due 검사

VillagerAdv의 경험치 동률 및 Resonance의 같은 level 정렬은 아직 ID가 없는 타워를 비교할 때 UUID를 저장한다. 모든 ID를 먼저 만들면 원래 동률 비교가 없던 목록에도 새 생성이 발생한다. Resonance cycleDue를 probe로 재사용하면 lastCycle을 먼저 써서 뒤 원래 루프의 발동까지 소비한다. cap 초과·missing entity의 슬롯 소비도 보존 대상이다.

ADV JUnit은 실제 private comparator/startWave와 실제 captureGraduate를 사용하되 ServerLevel 없는 full CONTEST tick은 실행하지 않는다. Resonance GameTest는 실제 service와 world 시간에서 같은 level/다른 level의 ID 생성 및 due 소비를 확인하도록 준비했다. 정확한 난수 UUID 값이나 난수 호출 순서 전체를 증명하는 fixture는 아니다.

#### 공통 nearest로 바꿀 수 없는 가족 선택

Atlantis는 후보 수≤cap에도 항상 거리순이고 같은 거리는 encounter 순서, count0은 빈 목록이다. common nearest는 cap 초과 때만 정렬하고 UUID tie를 쓰며 0 limit를 거부한다. 정상 기대 GameTest는 실제 nearby와 실제 공통 API의 이 차이를 검사한다.

Queen splash의 동일거리 선택은 lane 첫 cap개지만 common nearest는 logical UUID가 다른 대상을 고를 수 있다. 준비 GameTest는 실제 동일거리 몬스터와 실제 카드 shrink로 차이를 검증한다. ID final 필드를 조작하거나 가짜 comparator만 비교하지 않는다.

Blueprint multishot은 source 사거리에서 후보를 얻어 primary 중심 거리로 정렬한다. aroundTower는 정렬 중심이 다르고 aroundTarget은 후보 범위가 달라진다. actual secondary damage GameTest는 dominated/source 밖 제외와 첫 피해 callback의 뒤 대상 제거 뒤 **cap 밖 대상을 보충하지 않음**을 확인한다. 별도 순수 모델 JUnit 3개만으로 production 동등성을 인증하지 않는다.

#### 상태 읽기와 wave snapshot 시점

Insect의 앞쪽 live anchor가 이미 true여도 뒤쪽 생성기의 죽음/이동을 동기화한다. 기존 목록을 anyMatch로 바꾸거나 연결키를 먼저 거르면 이 상태 갱신이 빠진다. 실제 entity를 가진 GameTest는 후행 생성기의 stale 논리 상태와 마지막 live anchor 사망을 주입한다.

Animal의 fixture는 실제 Animal.tick 사이 사망·부활·위치 이동·augment 변경과 실제 lane.removeTower를 사용한다. null-world의 grid fallback 경로이므로 live entity center·chunk/world 교체까지 검증했다고 쓰지 않는다. Frost fixture도 실제 두 wave callback 사이 roster 변경을 직접 주입하며 전체 자동 wave 연쇄의 발생 빈도를 측정하지 않는다.

Pet 관계는 이벤트마다 다시 계산되지만 dead owner 좌표가 남아 있으면 loyalty가 유지되고, 어느 Pet이든 final-defense 상태이면 기존 관계가 동결된다. 이벤트 경로를 없애거나 매틱 live 관계로 바꾸는 것은 속도 정리가 아니다. 새로운 JUnit은 이 key·HP 비율·소유자/팀/lane 경계를 직접 호출한다.

Succubus는 TOWERS snapshot 처리 뒤 MONSTERS snapshot을 만든다. 전자는 새 entry를 다음 tick에 보지만 기존 state의 변경은 뒤 순회가 볼 수 있고, 전파로 생긴 monster state는 같은 tick 뒤 snapshot에 들어올 수 있다. 정상 재진입 fixture는 observer callback을 이용한 stress 경계이며 모든 wake/contagion 전투 입력을 대체하지 않는다.

### 기존 변경의 실제 공유 영향

| 경로 | 타 빌더와의 실제 관계 | 확인 근거·확대하면 안 되는 주장 |
|---|---|---|
| TimedEffectSet / 마법학교 protection | 다른 가문도 일반 피격과 상세 표시에서 빈 효과 또는 보호 효과를 조회 | [SemionTowerEntity.java:822–823,1284–1297](../src/main/java/kim/biryeong/semiontd/entity/tower/SemionTowerEntity.java#L822), [MagicSchoolSpellCombat.java:36–40](../src/main/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolSpellCombat.java#L36), [SemionDialogService.java:1667](../src/main/java/kim/biryeong/semiontd/ui/SemionDialogService.java#L1667). 피해경감 우회 API까지 모든 공격이라고 확대하지 않는다. 이전 차수 변경이다. |
| Protego Maxima / allied entity 지원 | 같은 월드·범위의 같은 팀이며 다른 owner·lane·가문도 가능 | [MagicSchoolSpellCombat.java:50–73](../src/main/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolSpellCombat.java#L50), [TowerAreaEffectRequest.java:55–57](../src/main/java/kim/biryeong/semiontd/api/area/TowerAreaEffectRequest.java#L55), [AreaEffectService.java:101–127](../src/main/java/kim/biryeong/semiontd/tower/area/AreaEffectService.java#L101). 기본 REGISTERED 요청의 owner/lane 제한과 다르다. |
| Legion 염소 / Hero 사제 / Ocean 공급 | 각각 Legion 동일 owner/team/lane, HeroParty owner·부상자, 이미 만든 Ocean 수신자에 한정 | [LegionGoatTower.java:105–113](../src/main/java/kim/biryeong/semiontd/tower/legion/LegionGoatTower.java#L105), [HeroCompanionSupportController.java:80–120](../src/main/java/kim/biryeong/semiontd/tower/hero/HeroCompanionSupportController.java#L80), [OceanWaterTower.java:176–213](../src/main/java/kim/biryeong/semiontd/tower/ocean/OceanWaterTower.java#L176). helper의 일반 Tower 인자를 타 가문 전체 수혜로 해석하지 않는다. |
| Pirate anchor / 경제 | 같은 owner의 다른 가문 REGISTERED 타워도 guard 수혜 가능. 판매한 타워가 Pirate가 아니어도 경제 hook은 호출 가능 | [PirateTower.java:128–139](../src/main/java/kim/biryeong/semiontd/tower/pirate/PirateTower.java#L128), [AreaEffectService.java:201–206](../src/main/java/kim/biryeong/semiontd/tower/area/AreaEffectService.java#L201), [ProductionTowerService.java:140–154](../src/main/java/kim/biryeong/semiontd/tower/ProductionTowerService.java#L140). provider rank의 team 조건과 recipient 필터를 분리한다. Pirate state 없는 플레이어는 빠른 반환이다. |
| DemonLord 소환 점유/복사 | 팀 lane의 모든 가문·foreign/dead/다른 y 타워도 기존처럼 xz 점유에 참여하며 copyable 다른 가문도 pool에 포함 | [DemonLordPassives.java:200–303,306–331](../src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordPassives.java#L200). 다른 가문의 자체 tick을 최적화한 변경은 아니다. |
| Future count / Mage primary / Thunder battery | 같은 몬스터 집합 의미를 소비하지만 공통 target/area API는 변경하지 않음 | [FutureAgencyAgentTower.java:294,540–556](../src/main/java/kim/biryeong/semiontd/tower/futureagency/FutureAgencyAgentTower.java#L294), [MageWizardTower.java:181](../src/main/java/kim/biryeong/semiontd/tower/mage/MageWizardTower.java#L181), [ThunderTower.java:347–366](../src/main/java/kim/biryeong/semiontd/tower/thunder/ThunderTower.java#L347). 타 가문 내부 lookup 횟수 감소라고 계산하지 않는다. |
| MagicSchoolSpell.find / Developer.hasBug / Gamble min | wizard·명령·guard된 VFX 조회, Developer 데이터 소비, owner Gambler 선택 경로 | [MagicSchoolSpell.java:133](../src/main/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolSpell.java#L133), [TowerVfxService.java:712–718](../src/main/java/kim/biryeong/semiontd/entity/tower/vfx/TowerVfxService.java#L712), [DeveloperTower.java:751](../src/main/java/kim/biryeong/semiontd/tower/developer/DeveloperTower.java#L751), [GambleRoundEffects.java:60–100](../src/main/java/kim/biryeong/semiontd/tower/gamble/GambleRoundEffects.java#L60). 타 가문의 모든 VFX/CSV/target 비용 개선은 아니다. |

root의 임시 `SharedProtectionFollowupGameTest`는 실제 Villager와 Undead 수혜자, 다른 owner/lane, 제공자4개의 최대3중첩, 실제 피격량, 제공자 entity 교체 후 source ID 및 각 lane round 정리를 검사하도록 준비했다. 이 한 fixture를 등록 33종 전부의 VFX/GPU·보호 전수 검증으로 확대하지 않는다.

### 작업 폴더 전용 fixture와 실행 상태

이 단계의 “baseline”은 순수 2b HEAD가 아니라 **기존 gate4의 26파일 production 상태**다. Thunder 후속 helper 등은 해당 상태를 전제로 한다. main과 별도인 `Temp\semion-builder-followup-t6e6xe3h\candidate` 한 디렉터리에 작업 폴더 fixture를 추가해 baseline을 실행했고, Animal 비교 때만 해당 production 파일을 후보로 전환했다. run별 manifest를 보존한 뒤 Animal 원본으로 복원했으며 baseline/candidate 전용 디렉터리 두 개를 만든 것은 아니다. main의 테스트 등록과 기존 gate4 증거는 유지한다.

| 묶음 | 정상 기대 fixture | 실제로 검증하는 범위 | 주된 한계 |
|---|---|---|---|
| A | JUnit4 | 실제 Animal tick의 중간 변화/제거, ADV production comparator/startWave/graduate | 무서버; full CONTEST tick·live entity center 아님 |
| B | JUnit6 + GT6 | Illager 표식 경계3, Ancient bounded prototype3, Resonance service3, Ancient actual cast/execute3 | prototype은 production patch가 아니며 총할당 감소를 입증하지 않음 |
| C | GT2 | Insect 후행 anchor 동기화, Queen 실제 shrink 선택 경계 | 전체 캐시/공통 selector의 성능 이득 검증 아님 |
| D | GT5 | Atlantis 선택 차이, Plant registry/RNG, Hero 실제 치유, Thunder/common AoE 경계 | 실제 해당 계약만 검사; Army 결함 fixture와 분리 |
| E | JUnit10 + GT4 | Succubus state traversal5·Pet event5, 꿈 재진입/정상 cleanup2·Body heartbeat/ray2 | JUnit private roster는 membership 자체를 검사하지 않음; GT는 별도 실제 mutation |
| F | JUnit7 + GT2 | Frost 실제 snapshot4, Blueprint 순수 모델3, 실제 multishot2 | 모델과 runtime을 구분; Frost 네 계열 모든 조합 전수 아님 |
| root | GT1 | 실제 타 가문 Protego Maxima 보호 | selected cross-family 계약이며 GPU/전 빌더 전수 아님 |
| 합계 | **JUnit27 + 필수 GT20** | 정상 통과를 기대하는 후속 진단 그룹 | Minecraft built-in optional1은 별도이며 신규 fixture 수에 넣지 않음 |

파일은 W2의 `followup-a`부터 `followup-f`, `followup-root` 아래에 있다. F의 `junit/`, `gametest/`, D의 `normal/` 등 인계 경로는 격리 실행자가 대응 sourceSet으로 복사한다. 새 테스트 의존성을 추가하지 않았으며 기존 JUnit/reflection/RuntimeArenaFixture 패턴을 사용한다. Mockito가 있는 것으로 가정하지 않는다.

정상 fixture가 통과한다는 것은 거절한 캐시/nearest 치환이 안전해졌다는 뜻이 아니다. 현재 계약 또는 두 구현의 차이를 확인하는 진단이 다수다. 후보 알고리즘 자체를 바꾸지 않은 상태의 통과와 실제 candidate production 대조를 구분한다.

| 후속 실행 | 현재 상태 | 기존 결과와의 관계 |
|---|---|---|
| followup-normal-1 | JUnit27 전부 통과. GT는 필수20+optional1=21 중20통과/Body1실패 | Body fixture의 partner-brain owner별 AreaEffectLaneIndex 등록 누락으로 확인. 제품 버그/새 성능 회귀로 계산하지 않고 실패 로그를 보존했다. |
| followup-normal-2 | 필수 GT20 전부 통과 + optional1 = 총21 통과, BUILD SUCCESSFUL(51초). JUnit은 UP-TO-DATE | JUnit27의 새 실행 증거는 normal1이다. Body fixture의 owner별 등록만 보완했고 기대 동작을 완화하지 않았다. main production/기존 테스트 변경 없음. |
| followup-army-1 | 별도 필수 GT1 의도된 실패로 초기 지휘 결함 재현확정; optional1 통과 | 정상 회귀 pass/fail이나 성능 개선 수치와 섞지 않는다. |
| followup-succubus-1 / -2 | 1차: 잔류 정리 기대 실패1 + optional1 통과. 2차: 같은 기대 실패1 유지 + 동일 key 재설치 관측1 통과 + optional1 통과 = 총3 | 잔류와 실제 부여 억제를 관측했다. 판매 재설치 수명 규칙 및 전체 match 영향은 미확정이며 새 production 회귀가 아니다. |
| followup-animal-candidate-1 | JUnit17 전부 통과(실패·skip0), 기존 Animal 필수 GT17 + optional1 = 총18 통과, BUILD SUCCESSFUL(1분7초) | 작업 폴더 candidate만 적용해 기존 Animal 회귀와 새 중간 상태/제거 JUnit2를 확인했다. 실험 뒤 격리 production도 원본으로 복원. main 미적용·성능 미측정. |
| 성능·할당·GPU | 전부 미측정 | HOI 활성 중 새 benchmark/JFR/서버 성능 실행 없음. 기능 실행 소요시간을 성능 비교로 사용하지 않는다. |

### 성능 수정과 분리한 결함 재현 및 수명 계약 관측

#### Army 초기 지휘

[ArmyTower.java:56,313–316](../src/main/java/kim/biryeong/semiontd/tower/army/ArmyTower.java#L56)은 `now - lastCommandTick`을 계산한다. 초기 lastCommandTick=Long.MIN_VALUE일 때 정상 now≥0에서 Long.MIN_VALUE를 빼면 long overflow로 차이가 음수가 되어 `<20` 조건에서 조기 반환한다. Atlantis/Thunder의 sentinel guard와 다르다. 따라서 “20 tick마다 현재 command 스캔 비용이 발생한다”는 확정 설명은 보류한다.

`ArmyCommandInitializationKnownDefectTest`는 실제 두 타워 배치·공개 진급 후 첫 tick의 초기 지휘 bonus를 요구한다. `followup-army-1`에서 gameTime=11, initialTimer=Long.MIN_VALUE, subtraction=-9223372036854775797, interval=20, timerAfter=Long.MIN_VALUE, expectedBonus=0.12, actualBonus=0으로 **실제 재현됐다**. timer를 조작하거나 현재 버그 동작을 정상으로 assert하지 않았다.

이후 sentinel guard를 넣으면 지금 막힌 효과와 조회가 새로 활성화될 수 있다. 이는 기능 수정이며 성능 향상으로 보고할 수 없다. 퇴역/보상/예비군 수명 정리와 섞어 고치지 않는다.

#### Succubus 제거된 대상의 자장가 cooldown

[SuccubusDreams.java:85–93,160–199,419–468,511–512](../src/main/java/kim/biryeong/semiontd/tower/succubus/SuccubusDreams.java#L85)의 LULLABY_READY_AT는 owner UUID+originalPosition→Long이다. 실제 removeTower 뒤 꿈 tick이 TOWERS state를 지워도, clearLane은 현재 roster key만 지우고 source-owner clearPlayer는 제거된 foreign target-owner key와 다르다. 정상 roster가 남은 cleanup과 구별해야 한다.

`SuccubusRemovedLullabyCleanupTest`는 “lane reset에서 제거된 target key도 정리되어야 한다”는 제안 수명 계약을 검사하는 별도 기대 실패 fixture다. 실제 제거·tick·lane 정리·source-owner 정리 뒤 cooldown key 잔류를 재현했다. 실패 후 target owner까지 finally 정리한다. 잔류의 재현과 설계 규칙 위반/실제 match 영향 확정은 다른 판정이다.

추가 `followup-succubus-2`는 새 Tower 인스턴스를 동일 owner/originalPosition에 등록한 실제 부여 결과를 대조했다. `remainingCooldown=80`, `sameKeyAccepted=false`, `freshAccepted=true`, `afterTargetClearAccepted=true`를 관측해 잔류 key가 남은 deadline 전에 같은 위치 재설치의 꿈 부여를 억제함을 확인했다. 다른 위치와 target-owner 정리 후에는 부여가 성공했다. 두 번째 관측 fixture가 통과해도 첫 번째 정리 기대 실패는 그대로 보존했다.

[production-tower-catalog.ko.md:97](production-tower-catalog.ko.md#L97)은 임시 복제 제거 뒤 몽환 상태 정리를 설명하며 [PlayerLane.java:364–368](../src/main/java/kim/biryeong/semiontd/game/PlayerLane.java#L364), [JobLaneLifecycle.java:44](../src/main/java/kim/biryeong/semiontd/job/JobLaneLifecycle.java#L44)가 구현 근거다. 반면 [SuccubusTowers.java:121–125](../src/main/java/kim/biryeong/semiontd/tower/succubus/SuccubusTowers.java#L121) 공개 설명은 pulse 간격·반경·인원·1스택만 다루고 80틱 제한이나 판매 재설치의 보존 의도를 명시하지 않는다. 따라서 **잔류·실제 부여 억제는 확정, reset 규칙 불일치는 의심, 게임 규칙 위반과 전체 경기 영향은 미확정**으로 구분한다. upgrade의 originalPosition 공유 계약도 임의로 지우지 않는다.

이 map은 UUID/좌표/Long을 보유하므로 자체적으로 world/lane/entity를 붙드는 누수 증거는 아니다. 단조롭게 진행하는 clock의 거절 조건은 readyAt까지지만 pulse를 놓친 뒤 실제 지연 전체가 80틱에 한정되는 것은 아니다. [SemionGame.java:1420–1425](../src/main/java/kim/biryeong/semiontd/game/SemionGame.java#L1420) 준비 단계도 tickTowers를 호출하고 자장가 execute에는 waveActive gate가 없으므로 준비 시간25초만으로 영향이 없다고 결론내리지 않는다. 실제 pulse·lane.resetForRound·전체 shutdown·더 낮은 새 world gameTime 사례는 별도 통합 재현이 필요하다. TOWERS/MONSTERS의 강참조 state와 혼동하지 않는다.

### 별도 기능 수정 단계로 넘길 버그 추적

발견된 실제 버그는 사용자 지시에 따라 이후 별도 수정 단계에서 모아 처리한다. 현재 성능 비교에 섞거나 production을 미리 고치지 않는다. 다음 표는 재현·영향·원인·발견 버전·규칙 대조·회귀를 함께 추적한다.

| 추적 항목 | 재현·원인과 파일 | 영향·규칙 대조 | 발견 버전·회귀 구분 | 검증·후속 처리 |
|---|---|---|---|---|
| Army 초기 지휘 | [ArmyTower.java:56,313–316](../src/main/java/kim/biryeong/semiontd/tower/army/ArmyTower.java#L56) sentinel 차감 overflow. 실제 now11/기대0.12/실제0·timer MIN 유지 | 첫 지휘 효과가 활성화되지 않음. 인접 상위 계급의 damageBuff와 maxCommandBonus를 적용하는 코드 계약 및 다른 가족 sentinel guard와 대조 | 기준2b의 기존 코드에서 발생; 이번11종 패치가 만든 회귀 아님 | 별도 GT로 재현확정. production 미수정. 후속 기능 단계에서 초기/다음20tick·계급/제거/round 경계를 회귀로 보존 |
| Succubus 제거 cooldown 잔류 | [SuccubusDreams.java:419–468,511–512](../src/main/java/kim/biryeong/semiontd/tower/succubus/SuccubusDreams.java#L419) clearLane은 현재 roster key, clearPlayer는 target-owner key만 지움. 제거된 foreign target은 source-owner 정리로 남음 | 잔류 및 같은 owner/좌표 새 타워의 80틱 deadline 전 부여 거절 관측. 다른 좌표·target-owner 정리 후 성공. 판매 재설치 수명 규칙·전체 경기 위반 미확정; world/entity 강참조 누수 주장 없음 | 기준2b와 바이트 동일한 기존 코드에서 발견; 새 성능 회귀 아님 | 별도 기대 실패1 유지 + 관측1 통과. 정리 규칙 확정 후 실제 pulse/round reset·upgrade/death/revival·match 경계를 별도 기능 단계에서 회귀 추적 |
| Body 후속 fixture 실패 | partner brain의 owner별 AreaEffectLaneIndex 등록 누락 | 테스트 setup 결함이며 제품 heartbeat/partner 규칙 위반 근거 아님. 수정 후 brain-first 피해7.399999999999636 / skin-first7.0으로 실제 순서 영향 확인 | 작업 폴더의 새 진단 fixture에서 발생; production 버그 아님 | 실패 로그 보존, workspace fixture만 수정, 정상2 통과. 제품 버그 수정 목록에는 넣지 않음 |

각 별도 실행의 추가1은 `minecraft:always_pass` built-in **optional(required=false)**이다. 정상 그룹의 필수20, Army의 필수1, Succubus 1차의 필수1·2차의 필수2에 합산하지 않는다. 별도 결함 재현 실패를 기존 release gate4의 974개 실패로 소급하지 않는다.

발견 버전은 W2의 `followup-verification/observed-defect-source-versions.json`에서 확인했다. ArmyTower.java SHA-256은 `38190937d44eab30589835fd34f44d352003687b10b8e59699be8e0f0d84cabb`, SuccubusDreams.java는 `94f7cfd48eda2bbb2953346e717f8774899070b6dfc07f54f937f90deab8123b`이며 두 파일 모두 HEAD `2b431d1c` blob과 현재 worktree가 바이트 동일하다.

원본 후속 로그·실행 descriptor·source manifest는 W2의 `followup-verification/` 아래 `followup-normal-1`, `followup-normal-2`, `followup-army-1`, `followup-succubus-1`, `followup-succubus-2`, `followup-animal-candidate-1` 접두 파일로 보존한다. 이들은 로컬 증거이며 저장소 포함 파일이 아니다. 모든 후속 기능 실행 프로세스는 종료됐고 성능 측정은 실행하지 않았다.

정상2 source manifest SHA-256은 `4f83ecedb1a5efba4dc025a1e6ca2b2947bc89c174718f112c4f63e5fcd62917`, 로그는 `09fc4309960ee4097a1ebc7ec6b6a95d4c576d2141c544cd7f4dd78d51cce19f`다. Animal candidate1 manifest는 `52d543dc9de2a5a5426592b8dea040a57c05eac01cb7f912e2cef4ccb7096a56`, 로그는 `df127c63d9c3fc0d052d3001814da828584a044db97ed10c56c5522da41524aa`다. 실행 descriptor의 `purpose`는 `ISOLATED_REPRODUCTIONS_NOT_PERFORMANCE_MEASUREMENT`이며 이 실행 시간들은 성능 비교값이 아니다.

최종 묶음 `W2/followup-verification/followup-functional-evidence.json`의 SHA-256은 `29fbd0c4f38dcdebc0f7c3253fb38880638405074b4621a0b8fc441c9d780b43`다. 6개 실행의 정확한 명령·소스 manifest·로그 hash·JUnit XML별 hash, optional 정의, 버그 분류를 담는다. 보고서를 제외한 main 원본3,828파일의 gate4 hash 일치와 격리 Animal 원복도 확인했다. 정상 JUnit27 중 Ancient prototype3·Blueprint 모델3은 합성 oracle이며 production runtime 검증 수로 바꾸지 않는다.

정상 그룹의 실행 명령은 해당 격리 candidate에서 `gradlew.bat test runGameTest --offline --max-workers=1 "-Dorg.gradle.jvmargs=-Xmx1G -XX:ActiveProcessorCount=2" --init-script <W2>/followup-verification/normal-fixture-limits.gradle --console=plain --no-daemon`이었다. Animal은 `animal-fixture-limits.gradle`, 별도 결함 재현은 descriptor의 제한된 GameTest 등록과 `runGameTest` 명령을 사용했다. fixture·entrypoint·manifest를 먼저 복원해야 하며 이 명령을 현재 main에서 그대로 실행한 검증은 아니다.

### 측정 전 준비와 유휴 구간 예약안

W2의 `followup-measurement-plan-f.md`는 실행 계획이다. 현재는 **11종 변경 구현 및 선정 경로 기능 검증**, **작업 폴더 진단 fixture 검증**, **측정 설계 준비**, **정량 성능 전부 미측정**을 각각 구분한다. 새 11종을 동일 입력으로 호출할 timing harness는 아직 구현되지 않았다. HOI 사용 중에는 새 정량 실측을 시작하지 않는다.

비교 A는 HEAD `2b431d1c6c85fca14c045a3c6bce492b1940b665`, B는 그 위의 기존 11종·12 production 파일이다. 이 기준 A에는 앞선 공통 TimedEffectSet/Engineer 변경이 이미 들어 있으므로 이번 성과로 다시 세지 않는다. Animal은 B 대 B+guard의 별도 후보 C이며 11종 A/B 결과에 섞지 않는다.

기존 [GameAlgorithmPerformanceTest.java:153–165](../src/gametest/java/kim/biryeong/semiontd/entity/goal/GameAlgorithmPerformanceTest.java#L153)는 앞선 공통 효과·Engineer·target/control workload용이다. 그 결과나 기존 combat/lane-tick 하네스의 존재가 이번 11종의 동일 입력 측정을 대신하지 않는다. 입력 hash·checksum·ThreadMXBean/JFR 기록 설계는 재사용할 수 있지만 실제 분기·상태 복원·출력 대조는 새로 준비해야 한다.

| 변경 경로 | A/B에 사용할 실제 동일 진입점 | 새로 필요한 준비·해석 제한 |
|---|---|---|
| Ocean | [OceanWaterTower.java:194](../src/main/java/kim/biryeong/semiontd/tower/ocean/OceanWaterTower.java#L194) supplyAllocations | map 삽입 순서·순차 double bits·입력 water 불변 대조; 공급 pulse 전체와 helper 비용 구분 |
| Hero | [HeroCompanionSupportController.java:80](../src/main/java/kim/biryeong/semiontd/tower/hero/HeroCompanionSupportController.java#L80) healParty 또는 동일 controller 상태의 tick | 새 lowestWoundedTargets는 A에 없음. healing 전 두 대상 선택 및 heal/guard/cooldown 상태 복원 |
| Legion | [LegionGoatTower.java:116](../src/main/java/kim/biryeong/semiontd/tower/legion/LegionGoatTower.java#L116) stackIndexFor | 같은 사전 해석 MethodHandle·stable rank/중복 identity/경계; 지원 predicate/action 재조회 횟수 구분 |
| Pirate | [PirateTower.java:182](../src/main/java/kim/biryeong/semiontd/tower/pirate/PirateTower.java#L182) isSelectedAnchorFor | A private/B package 접근 차이는 동일 MethodHandle로 통제. helper와 실제 recipient 지원 필터 구분 |
| MagicSchool | [MagicSchoolSpell.java:133](../src/main/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolSpell.java#L133) find | hot lookup과 class-init map 생성 분리, 전체 ID/null/unknown·문자열 identity/동등값 대조 |
| Developer | [DeveloperTowerData.java:242](../src/main/java/kim/biryeong/semiontd/tower/developer/DeveloperTowerData.java#L242) hasBug | 같은 CSV/탐색 위치·null short-circuit·copy/write/reload; 입력 문자열 생성은 timing 밖 |
| FutureAgency | [FutureAgencyAgentTower.java:273](../src/main/java/kim/biryeong/semiontd/tower/futureagency/FutureAgencyAgentTower.java#L273) modifyAttackDamage | 새 count helper는 A에 없음. 실제 dense 정책/월드 resolve·count 포화 전 gate·damage bits 확인 |
| Mage | [MageWizardTower.java:129](../src/main/java/kim/biryeong/semiontd/tower/mage/MageWizardTower.java#L129) tick | 새 primary helper는 A에 없음. 같은 mana/core/cooldown/projectile·RNG 상태로 실제 준비된 시전 재생 |
| Thunder | [ThunderTower.java:347](../src/main/java/kim/biryeong/semiontd/tower/thunder/ThunderTower.java#L347) chargeBattery | A 목록/B boolean helper를 직접 동일 함수라고 비교하지 않음. query 도달·idleCharge/storedShots 복원 |
| Gamble | [GambleRoundEffects.java:60](../src/main/java/kim/biryeong/semiontd/tower/gamble/GambleRoundEffects.java#L60) assignSpectator | 동일 synchronized 호출, release→assign 링크 상태·cap·live entity 및 source 반복 재할당 복원 |
| DemonLord | [DemonLordPassives.java:306](../src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordPassives.java#L306) freePositions | 동일 loaded floor/world query와 x→z 반환 순서; occupancy 합성 kernel 및 상위 shuffle 비용 구분 |

측정 전에 A/B source·production class·동일 harness hash, JDK/JVM/GC·설정·world seed·고정 입력 manifest를 동결한다. 새 helper가 A에 없으면 공통 상위 production 호출을 사용하며, 복제 reference/kernel은 별도 항목으로 표시한다. 반환 identity/순서와 double bits, HP/mana/water·cooldown/link/effect·RNG 다음 값·callback/VFX 순서를 warmup 전후 비교한다. 결과가 다르면 그 cell의 성능 비교를 중단한다.

제안 입력은 seed `26031009L`·cell당 결정적16변형, 일반 roster/monster 규모 `0,1,2,8,32,128,512`이며 512는 합성 stress다. 처음에는 11경로별 소규모·대규모·조건 분기를 3개씩 골라 **33 cell**만 측정한다. 실제 경기 빈도나 workload 가중치라는 뜻이 아니다. mutable 경로는 매 논리 operation에 같은 상태를 복원하고 setup/reset 비용도 별도 기록한다.

| 준비 단계 | 완료 조건 | 예약 제안과 한계 |
|---|---|---|
| 공통 runner 작성 | 11 진입점 adapter, 입력·상태 trace, 동일 reset/cleanup, 최소 sink 및 출력 형식 준비 | 아직 미구현. 문서·fixture가 있다는 이유로 측정 준비 완료라 하지 않음 |
| 격리 compile·dry-run | 양쪽 runner compile, 모든33 cell 최소1회와 경계 gate, 실제 변경 분기 진입 및 전역 상태 정리 확인 | 빌드/서버 부하도 유휴 조율 필요. 소요 이력이 없어 완료 시간 보장 없음 |
| 작은 pilot | local1·world1 경로의 반복 수/타이머/할당/상태 복원 확인 | 첫 유휴 창 **15–20분** 제안. 최종 성능값이 아니며 시간이 부족하면 다음 창으로 넘김 |
| time/allocation | 목표 warmup3초+측정5초, 별도 fresh JVM의 ABBA4 fork | active window `33×8×4=1,056초=17.6분` 하한. variant당2 fork라 작은 차이 확정에는 추가 pair가 필요할 수 있음 |
| JFR 진단 | time run과 분리하여 A/B 각1 fresh fork·동일 설정 | `33×8×2=528초=8.8분` 추가 하한. setup/reset·startup·실패 재실행 제외 |
| 확장·Animal | 필요한 stress/추가 pair 및 별도 Animal off T8/off T128/on T128 | 33 cell 예산 밖. 후보 채택·성능 결과 모두 별도 판단 |

본 실행의 잠정 예약안은 **45–75분의 연속 유휴 구간**이다. 근거는 active window 합26.4분에 startup/setup/reset/정리 여유를 더한 계획상 판단뿐이며, 하네스가 없고 reset 비용도 미측정이므로 실제 실행시간이나 완료 보장이 아니다. 3초 warmup의 충분성도 pilot에서 확인해야 한다. HOI 재개 시 배치가 끝난 뒤 멈추고 해당 fork의 오염 여부를 기록한다.

time은 배치 wall/CPU nanos와 ops, allocation은 지원되는 같은 thread allocated bytes/op, GC는 fork별 count/time을 기록한다. JFR-on/off는 서로 다른 표본으로 분리하며 allocation sample을 전수 byte 합계로 취급하지 않는다. fork 단위 분포·흔들림·control 비용과 원본 결과를 함께 제시하고 helper speedup을 합산해 전체 MSPT/TPS나 GPU 개선률로 환산하지 않는다.

### 남은 판단과 기록 원칙

정상 후속 실행, known-defect 실행, Animal 후보 대조는 서로 다른 증거다. fixture 컴파일/API 실패가 나오면 원본 로그와 실패 소스를 보존하고, fixture 문제인지 현재 runtime 계약 차이인지 분리한다. 통과가 확인되기 전 예상 수를 실적 수로 적지 않는다.

후속 baseline/candidate 실행은 main production/기존 테스트/보고서 앞부분을 바꾸지 않는다. 작업 폴더의 prototype·진단 source를 저장소에 추가하거나 release regression으로 등록한 것으로 설명하지 않는다. 현재 11종 선정 변경의 기능 검증 범위와 이번 새 후보의 상태를 계속 분리한다.

성능 비교는 root가 별도 유휴 구간을 확보한 뒤 동일 입력·source manifest·호출 조건을 정할 때만 진행할 수 있다. 지금은 성능 순위, 전체 개선률, 할당 감소량, “측정상 불필요” 판정이 없다. 앞선 타깃 최적화 철회·ULP 차이·실패와 이 절의 새 보류 사유를 그대로 보존한다.

이 후속 기록은 33종과 Default의 누락 없는 재평가 및 한정된 동작 검증이다. 모든 빌더의 모든 경로가 최적화됐다는 결론이나 운영 배포·커밋·푸시 승인으로 해석하지 않는다.
