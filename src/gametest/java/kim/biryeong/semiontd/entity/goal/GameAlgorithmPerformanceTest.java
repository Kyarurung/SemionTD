package kim.biryeong.semiontd.entity.goal;

import com.google.gson.Gson;
import com.sun.management.ThreadMXBean;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.IntToLongFunction;
import jdk.jfr.Configuration;
import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.effect.TimedEffectSet;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.engineer.EngineerCircuitTower;
import kim.biryeong.semiontd.tower.engineer.EngineerGolemTower;
import kim.biryeong.semiontd.tower.engineer.EngineerTowers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class GameAlgorithmPerformanceTest {
    private static final String FILTER = "semion-td-gametest:game_algorithm_performance_test_measures_deterministic_algorithms";
    private static final Gson JSON = new Gson();
    private static final int INPUTS = 16;
    private static final TimedEffectType EFFECT = TimedEffectType.TOWER_PROTEGO_MAXIMA_AURA;
    private static volatile long consumed;

    @GameTest(maxTicks = 120000)
    public void measuresDeterministicAlgorithms(GameTestHelper context) {
        if (!Boolean.getBoolean("semiontd.algorithmProfile")) {
            SemionTd.LOGGER.info("ALGORITHM_PROFILE_NOT_RUN: opt-in synthetic paths; no performance evidence.");
            context.succeed();
            return;
        }
        if (!FILTER.equals(System.getProperty("fabric-api.gametest.filter"))
                || !(context.getLevel().getServer() instanceof GameTestServer)) {
            context.fail(Component.literal("Algorithm profile requires its exact isolated filter and GameTestServer."));
            return;
        }
        new Run(context).start();
    }

    @Name("semiontd.AlgorithmProfileStage")
    public static final class StageEvent extends Event {
        public String variant;
        public String workload;
        public String inputSha256;
        public int size;
        public long operations;
    }

    private record Workload(String name, int size, IntToLongFunction answer, long[] expected,
                            String inputSha256, Runnable cleanup) {
        long run(int repetitions, boolean oracle) {
            long checksum = 1;
            for (int operation = 0; operation < repetitions; operation++) {
                int index = operation & (INPUTS - 1);
                checksum = checksum * 31 + (oracle ? expected[index] : answer.applyAsLong(index));
            }
            return checksum;
        }

        int repetitions(int requested) {
            return name.equals("engineer_choose_plate")
                    ? Math.max(INPUTS, requested * 8 / Math.max(8, size) / INPUTS * INPUTS) : requested;
        }

        void verify() {
            for (int index = 0; index < INPUTS; index++) {
                require(answer.applyAsLong(index) == expected[index], name + " oracle mismatch at " + index);
            }
        }
    }

    private static final class Run {
        private final GameTestHelper context;
        private final ThreadMXBean threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        private final long seed = Long.getLong("semiontd.profileSeed", 26031009L);
        private final int warmup = bounded("semiontd.profileWarmupTicks", 80, 10, 2000);
        private final int samples = bounded("semiontd.profileSampleTicks", 150, 20, 2000);
        private final int repetitions = bounded("semiontd.profileRepetitions", 1024, 16, 32768);
        private final String variant = System.getProperty("semiontd.profileVariant", "unspecified");
        private final List<Workload> workloads = new ArrayList<>();
        private final long[] batchNanos = new long[samples];
        private final long[] cpuNanos = new long[samples];
        private final long[] allocatedBytes = new long[samples];
        private final long[] serverNanos = new long[samples];
        private final List<Map<String, Object>> reports = new ArrayList<>();
        private Recording recording;
        private Path jfr;
        private Path jsonl;
        private StageEvent event;
        private int stage;
        private int tick;
        private long expectedChecksum;
        private long observedChecksum;
        private long startNanos;
        private long endNanos;
        private long gcCount;
        private long gcMillis;
        private long endGcCount;
        private long endGcMillis;

        Run(GameTestHelper context) { this.context = context; }

        void start() {
            safely(() -> {
                require(variant.matches("[A-Za-z0-9._-]{1,80}"), "Invalid profile variant.");
                require(repetitions % INPUTS == 0, "Repetitions must be divisible by " + INPUTS);
                require(context.getLevel().getSeed() == Long.getLong("semiontd.profileWorldSeed", 0L),
                        "Host GameTest world seed differs from the expected seed.");
                require(context.getLevel().getServer().tickRateManager().tickrate() == 20.0F,
                        "Host GameTest configured tick rate must remain 20.");
                require(threads.isCurrentThreadCpuTimeSupported() && threads.isThreadAllocatedMemorySupported(),
                        "Thread CPU/allocation counters are unavailable.");
                if (!threads.isThreadCpuTimeEnabled()) threads.setThreadCpuTimeEnabled(true);
                if (!threads.isThreadAllocatedMemoryEnabled()) threads.setThreadAllocatedMemoryEnabled(true);
                require(cpu() >= 0 && allocated() >= 0, "Thread CPU/allocation counters returned unavailable values.");
                require(!ManagementFactory.getGarbageCollectorMXBeans().isEmpty()
                                && ManagementFactory.getGarbageCollectorMXBeans().stream()
                                .allMatch(bean -> bean.getCollectionCount() >= 0 && bean.getCollectionTime() >= 0),
                        "GC counters are unavailable.");
                for (int size : new int[] {6, 32, 128, 512}) {
                    workloads.add(effects(seed, size, 3));
                    workloads.add(targets(seed, size, 1));
                    workloads.add(engineer(seed, size));
                }
                workloads.add(effects(seed, 0, 3));
                workloads.add(effects(seed, 1, 1));
                workloads.add(effects(seed, 32, 1));
                workloads.add(effects(seed, 32, 0));
                workloads.add(targets(seed, 32, 3));
                long[] control = new long[INPUTS];
                Random random = new Random(seed);
                for (int index = 0; index < INPUTS; index++) control[index] = random.nextLong();
                workloads.add(new Workload("control_checksum_only", 0, index -> control[index],
                        control.clone(), hash(JSON.toJson(control)), () -> {}));
                workloads.forEach(Workload::verify);
                jfr = Path.of("algorithm-profile-" + variant + ".jfr").toAbsolutePath();
                jsonl = Path.of("algorithm-profile-" + variant + ".jsonl").toAbsolutePath();
                require(!Files.exists(jfr) && !Files.exists(jsonl), "Profile outputs must be new files.");
                recording = new Recording(Configuration.getConfiguration("profile"));
                recording.disable("jdk.JVMInformation");
                recording.disable("jdk.InitialSystemProperty");
                recording.disable("jdk.InitialEnvironmentVariable");
                recording.enable(StageEvent.class);
                recording.start();
                stage = 0;
                tick = 0;
                context.runAfterDelay(1, this::sample);
            });
        }

        void sample() {
            safely(() -> {
                Workload workload = workloads.get(stage);
                int stageRepetitions = workload.repetitions(repetitions);
                if (tick > warmup && tick <= warmup + samples) {
                    var server = context.getLevel().getServer();
                    long[] history = server.getTickTimesNanos();
                    long previous = history[Math.floorMod(server.getTickCount() - 1, history.length)];
                    require(previous > 0, "Completed host tick duration is unavailable.");
                    serverNanos[tick - warmup - 1] = previous;
                }
                if (tick == warmup + samples) {
                    finishStage(workload);
                    return;
                }
                if (tick == warmup) {
                    expectedChecksum = workload.run(stageRepetitions, true);
                    observedChecksum = 1;
                    gcCount = gc(false);
                    gcMillis = gc(true);
                    event = new StageEvent();
                    event.variant = variant;
                    event.workload = workload.name;
                    event.inputSha256 = workload.inputSha256;
                    event.size = workload.size;
                    event.operations = (long) stageRepetitions * samples;
                    event.begin();
                    startNanos = System.nanoTime();
                }
                if (tick < warmup) {
                    consumed = workload.run(stageRepetitions, false);
                } else {
                    long beforeCpu = cpu();
                    long beforeAllocated = allocated();
                    long before = System.nanoTime();
                    long checksum = workload.run(stageRepetitions, false);
                    long after = System.nanoTime();
                    int index = tick - warmup;
                    batchNanos[index] = after - before;
                    allocatedBytes[index] = allocated() - beforeAllocated;
                    cpuNanos[index] = cpu() - beforeCpu;
                    require(checksum == expectedChecksum, workload.name + " measured checksum mismatch.");
                    require(cpuNanos[index] >= 0 && allocatedBytes[index] >= 0, "Counter delta is invalid.");
                    observedChecksum = observedChecksum * 31 + checksum;
                    consumed = checksum;
                    if (index == samples - 1) {
                        endNanos = after;
                        endGcCount = gc(false);
                        endGcMillis = gc(true);
                        event.end();
                        event.commit();
                    }
                }
                tick++;
                context.runAfterDelay(1, this::sample);
            });
        }

        private void finishStage(Workload workload) throws IOException {
            int stageRepetitions = workload.repetitions(repetitions);
            workload.verify();
            require(context.getLevel().getServer().tickRateManager().tickrate() == 20.0F,
                    "Host GameTest configured tick rate changed during measurement.");
            long expectedAggregate = 1;
            for (int index = 0; index < samples; index++) expectedAggregate = expectedAggregate * 31 + expectedChecksum;
            require(observedChecksum == expectedAggregate, "Aggregate checksum mismatch.");
            long operations = (long) stageRepetitions * samples;
            long activeNanos = Arrays.stream(batchNanos).sum();
            long totalCpu = Arrays.stream(cpuNanos).sum();
            long totalAllocated = Arrays.stream(allocatedBytes).sum();
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("status", "SYNTHETIC_PATHS_ONLY");
            report.put("variant", variant);
            report.put("revision", System.getProperty("semiontd.profileRevision", "unspecified"));
            report.put("fixtureSourceSha256", System.getProperty("semiontd.profileFixtureSha256", "unspecified"));
            report.put("fixtureClassSha256", classHash(GameAlgorithmPerformanceTest.class));
            report.put("seed", seed);
            report.put("hostWorldSeed", context.getLevel().getSeed());
            report.put("hostConfiguredTickRate", context.getLevel().getServer().tickRateManager().tickrate());
            report.put("syntheticWorld", "NONE_NULL_WORLD_NO_ENTITIES");
            report.put("combatRound", "NOT_APPLICABLE");
            report.put("combatSpeed", "NOT_APPLICABLE_HEADLESS_THROUGHPUT");
            report.put("workload", workload.name);
            report.put("size", workload.size);
            report.put("inputVariants", INPUTS);
            report.put("inputSha256", workload.inputSha256);
            report.put("warmupTicks", warmup);
            report.put("sampleTicks", samples);
            report.put("operationsPerTick", stageRepetitions);
            report.put("operations", operations);
            report.put("expectedChecksum", Long.toUnsignedString(expectedAggregate));
            report.put("observedChecksum", Long.toUnsignedString(observedChecksum));
            report.put("outputSha256", hash(JSON.toJson(workload.expected)));
            report.put("serverThreadCpuNanos", totalCpu);
            report.put("serverThreadCpuNanosPerOperation", (double) totalCpu / operations);
            report.put("serverThreadAllocatedBytes", totalAllocated);
            report.put("serverThreadBytesPerOperation", (double) totalAllocated / operations);
            report.put("vmGcCount", endGcCount - gcCount);
            report.put("vmGcMillis", endGcMillis - gcMillis);
            report.put("batchMilliseconds", stats(batchNanos));
            report.put("hostServerMspt", stats(serverNanos));
            report.put("activeSeconds", activeNanos / 1e9);
            report.put("wallSeconds", (endNanos - startNanos) / 1e9);
            report.put("operationsPerActiveSecond", operations * 1e9 / activeNanos);
            report.put("operationsPerWallSecond", operations * 1e9 / (endNanos - startNanos));
            report.put("javaVersion", System.getProperty("java.version"));
            report.put("javaVmName", System.getProperty("java.vm.name"));
            report.put("processors", Runtime.getRuntime().availableProcessors());
            report.put("maxHeapBytes", Runtime.getRuntime().maxMemory());
            report.put("gcNames", ManagementFactory.getGarbageCollectorMXBeans().stream().map(bean -> bean.getName()).toList());
            report.put("jfr", jfr.toString());
            report.put("limits", List.of("NO_FULL_COMBAT_OR_CLIENTS", "HOST_MSPT_INCLUDES_SYNTHETIC_BATCH_AND_GAME_TEST",
                    "CPU_AND_ALLOCATION_SCOPE_INCLUDES_BATCH_CHECKSUM_AND_COUNTER_OVERHEAD", "GC_SCOPE_IS_WHOLE_VM_MEASUREMENT_WINDOW",
                    "CONSTRUCTION_EXCLUDED", "FIXED_CASE_ORDER_JIT_AND_HEAP_EFFECTS_REQUIRE_SEPARATE_JVM_REPLICATIONS",
                    "NEGATIVE_CONTROLS_ARE_NOT_SUBTRACTED", "ENGINEER_USES_CACHED_METHOD_HANDLE"));
            String line = JSON.toJson(report);
            Files.writeString(jsonl, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            SemionTd.LOGGER.info("ALGORITHM_PROFILE {}", line);
            reports.add(report);
            tick = 0;
            if (++stage < workloads.size()) {
                context.runAfterDelay(1, this::sample);
            } else {
                recording.stop();
                recording.dump(jfr);
                recording.close();
                recording = null;
                workloads.forEach(value -> value.cleanup.run());
                SemionTd.LOGGER.info("ALGORITHM_PROFILE_COMPLETE {}", JSON.toJson(Map.of(
                        "variant", variant, "stages", reports.size(), "jfr", jfr.toString(), "jsonl", jsonl.toString())));
                context.succeed();
            }
        }

        private long cpu() { return threads.getCurrentThreadCpuTime(); }
        private long allocated() { return threads.getThreadAllocatedBytes(Thread.currentThread().threadId()); }

        private void safely(CheckedAction action) {
            try {
                action.run();
            } catch (Throwable failure) {
                if (recording != null) {
                    try { recording.close(); } catch (RuntimeException ignored) { }
                    recording = null;
                }
                for (Workload workload : workloads) {
                    try { workload.cleanup.run(); } catch (RuntimeException ignored) { }
                }
                context.fail(Component.literal("Algorithm profile failed: " + failure));
            }
        }
    }

    private static Workload effects(long seed, int size, int limit) {
        Random random = new Random(seed ^ 0x4d41474e49545544L ^ size);
        TimedEffectSet[] sets = new TimedEffectSet[INPUTS];
        long[] expected = new long[INPUTS];
        StringBuilder manifest = new StringBuilder("effects|").append(size).append('|').append(limit);
        for (int variant = 0; variant < INPUTS; variant++) {
            TimedEffectSet set = new TimedEffectSet();
            sets[variant] = set;
            List<Double> contributions = new ArrayList<>();
            for (int index = 0; index < size; index++) {
                double magnitude = (1 + random.nextInt(31)) / 1000.0;
                contributions.add(magnitude);
                manifest.append('|').append(Double.toHexString(magnitude));
                Identifier source = Identifier.fromNamespaceAndPath("semiontd", "algorithm_profile_" + index);
                if (index == 0) set.apply(EFFECT, magnitude, 1000);
                else if ((index & 1) == 0) set.apply(EFFECT, source, magnitude, 1000);
                else set.setPersistent(EFFECT, source, magnitude);
            }
            contributions.sort(Comparator.reverseOrder());
            double remaining = 1.0;
            for (int index = 0; index < Math.min(limit, contributions.size()); index++) {
                remaining *= 1.0 - contributions.get(index);
            }
            expected[variant] = Double.doubleToLongBits(1.0 - remaining);
        }
        String name = limit == 0 ? "control_effects_limit_zero" : limit == 1 ? "effects_top_one" : "effects_top_three";
        return new Workload(name, size,
                index -> Double.doubleToLongBits(sets[index].multiplicativeMagnitude(EFFECT, limit)),
                expected, hash(manifest.toString()), () -> {});
    }

    private record Target(int identity, int priority, int distance) {}

    private static Workload targets(long seed, int size, int limit) {
        Random random = new Random(seed ^ 0x544152474554L ^ size);
        List<List<Target>> inputs = new ArrayList<>();
        long[] expected = new long[INPUTS];
        Comparator<Target> order = Comparator.comparingInt(Target::priority).reversed().thenComparingInt(Target::distance);
        for (int variant = 0; variant < INPUTS; variant++) {
            List<Target> values = new ArrayList<>();
            for (int index = 0; index < size; index++) values.add(new Target(index, random.nextInt(7), random.nextInt(13)));
            Collections.shuffle(values, random);
            inputs.add(List.copyOf(values));
            expected[variant] = targetDigest(values.stream().sorted(order).limit(limit).toList());
        }
        return new Workload(limit == 1 ? "target_limit_one" : "control_target_limit_three", size,
                index -> targetDigest(EntityGoalTargetSelection.first(inputs.get(index), order, limit)),
                expected, hash(limit + "|" + JSON.toJson(inputs)), () -> {});
    }

    private static long targetDigest(List<Target> targets) {
        long result = targets.size();
        for (int index = 0; index < targets.size(); index++) result = result * 31 + targets.get(index).identity;
        return result;
    }

    private static final class EngineerAccess {
        static final MethodHandle CHOOSE = choose();

        private static MethodHandle choose() {
            try {
                return MethodHandles.privateLookupIn(EngineerGolemTower.class, MethodHandles.lookup())
                        .findVirtual(EngineerGolemTower.class, "choosePlate",
                                MethodType.methodType(GridPosition.class, PlayerLane.class, Vec3.class));
            } catch (ReflectiveOperationException failure) {
                throw new ExceptionInInitializerError(failure);
            }
        }

        static GridPosition select(EngineerGolemTower tower, PlayerLane lane, Vec3 origin) {
            try {
                return (GridPosition) CHOOSE.invokeExact(tower, lane, origin);
            } catch (Throwable failure) {
                throw new AssertionError(failure);
            }
        }
    }

    private static Workload engineer(long seed, int size) {
        Random random = new Random(seed ^ 0x454e47494e454552L ^ size);
        List<PlayerLane> lanes = new ArrayList<>();
        EngineerGolemTower[] golems = new EngineerGolemTower[INPUTS];
        Vec3[] origins = new Vec3[INPUTS];
        long[] expected = new long[INPUTS];
        StringBuilder manifest = new StringBuilder("engineer|").append(size);
        EngineerTowers.PlateKind[] kinds = EngineerTowers.PlateKind.values();
        for (int variant = 0; variant < INPUTS; variant++) {
            UUID owner = UUID.nameUUIDFromBytes((seed + ":engineer:" + size + ":" + variant).getBytes(StandardCharsets.UTF_8));
            UUID other = new UUID(owner.getMostSignificantBits(), owner.getLeastSignificantBits() ^ 1);
            var layout = new LaneRegionLayout(1, new Vec3(.5, 64, .5), List.of(new Vec3(.5, 64, 2.5)),
                    new Vec3(.5, 64, 10.5), BlockBounds.of(new BlockPos(-1024, 63, -1024), new BlockPos(1024, 66, 1024)),
                    List.of(new GridPosition(0, 63, 10)));
            PlayerLane lane = new PlayerLane(TeamId.RED, 1, owner, null, layout);
            lanes.add(lane);
            GridPosition home = new GridPosition(0, 64, 0);
            EngineerGolemTower golem = new EngineerGolemTower(EngineerTowers.COPPER_GOLEM, owner, TeamId.RED, 1, home, home);
            golems[variant] = golem;
            origins[variant] = new Vec3(random.nextInt(17) - 8, 65, random.nextInt(17) - 8);
            Map<GridPosition, Integer> cooldowns = new LinkedHashMap<>();
            List<EngineerCircuitTower> candidates = new ArrayList<>();
            GridPosition last = null;
            for (int index = 0; index < size; index++) {
                GridPosition position = new GridPosition((index % 32) * 2 - 32, 64, (index / 32) * 2 - 16);
                EngineerTowers.PlateKind kind = kinds[random.nextInt(kinds.length)];
                UUID towerOwner = index % 11 == 10 ? other : owner;
                EngineerCircuitTower plate = new EngineerCircuitTower(EngineerTowers.plate(kind), towerOwner,
                        TeamId.RED, 1, position, position);
                candidates.add(plate);
                if (index % 7 == 6) cooldowns.put(position, 10);
                if (index == 2) last = position;
                manifest.append('|').append(position).append(':').append(kind).append(':').append(towerOwner);
            }
            Collections.shuffle(candidates, random);
            candidates.forEach(lane::addTower);
            setField(golem, "plateCooldowns", cooldowns);
            setField(golem, "lastPressedPlate", last);
            Vec3 origin = origins[variant];
            GridPosition lastPressed = last;
            Comparator<EngineerCircuitTower> order = Comparator
                    .comparingInt((EngineerCircuitTower tower) -> tower.plateKind().priority()).reversed()
                    .thenComparingDouble(tower -> new Vec3(tower.originalPosition().x() + .5,
                            tower.originalPosition().y() + 1.0625, tower.originalPosition().z() + .5).distanceToSqr(origin))
                    .thenComparingInt(tower -> tower.originalPosition().x())
                    .thenComparingInt(tower -> tower.originalPosition().z());
            GridPosition chosen = candidates.stream().filter(tower -> owner.equals(tower.ownerPlayer()))
                    .filter(tower -> !cooldowns.containsKey(tower.originalPosition()))
                    .filter(tower -> !tower.originalPosition().equals(lastPressed))
                    .sorted(order).map(EngineerCircuitTower::originalPosition).findFirst().orElse(null);
            expected[variant] = positionDigest(chosen);
            manifest.append('|').append(origin).append('|').append(last).append('|').append(cooldowns.keySet());
            candidates.forEach(plate -> manifest.append('|').append(plate.originalPosition()));
        }
        return new Workload("engineer_choose_plate", size,
                index -> positionDigest(EngineerAccess.select(golems[index], lanes.get(index), origins[index])),
                expected, hash(manifest.toString()), () -> lanes.forEach(PlayerLane::clearTowers));
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = EngineerGolemTower.class.getDeclaredField(name);
            field.setAccessible(true);
            if (name.equals("plateCooldowns")) {
                @SuppressWarnings("unchecked")
                Map<GridPosition, Integer> existing = (Map<GridPosition, Integer>) field.get(target);
                @SuppressWarnings("unchecked")
                Map<GridPosition, Integer> supplied = (Map<GridPosition, Integer>) value;
                existing.putAll(supplied);
            } else {
                field.set(target, value);
            }
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static long positionDigest(GridPosition position) {
        if (position == null) return Long.MIN_VALUE;
        return ((long) position.x() * 31 + position.y()) * 31 + position.z();
    }

    private static Map<String, Double> stats(long[] values) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        return Map.of("mean", Arrays.stream(values).average().orElseThrow() / 1e6,
                "p50", sorted[(int) Math.ceil(sorted.length * .5) - 1] / 1e6,
                "p95", sorted[(int) Math.ceil(sorted.length * .95) - 1] / 1e6,
                "p99", sorted[(int) Math.ceil(sorted.length * .99) - 1] / 1e6,
                "max", sorted[sorted.length - 1] / 1e6);
    }

    private static long gc(boolean time) {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(bean -> time ? bean.getCollectionTime() : bean.getCollectionCount()).filter(value -> value >= 0).sum();
    }

    private static int bounded(String key, int fallback, int minimum, int maximum) {
        int value = Integer.getInteger(key, fallback);
        require(value >= minimum && value <= maximum, key + " is outside the supported range.");
        return value;
    }

    private static String classHash(Class<?> type) throws IOException {
        try (var input = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            require(input != null, "Fixture bytecode is unavailable.");
            return digest(input.readAllBytes());
        }
    }

    private static String hash(String input) { return digest(input.getBytes(StandardCharsets.UTF_8)); }

    private static String digest(byte[] input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface CheckedAction { void run() throws Exception; }
}
