package kim.biryeong.semiontd.game;

import com.google.gson.Gson;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import jdk.jfr.Configuration;
import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.gametest.RuntimeArenaFixture;
import kim.biryeong.semiontd.job.JobBuilderLifecycle;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.SemionJob;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerDataKey;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import kim.biryeong.semiontd.tower.area.AreaTargetSelection;
import kim.biryeong.semiontd.tower.blueprint.BlueprintStates;
import kim.biryeong.semiontd.tower.blueprint.BlueprintStats;
import kim.biryeong.semiontd.tower.blueprint.BlueprintVisuals;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class GameBuilderPerformanceTest implements RuntimeArenaFixture {
    private static final Gson JSON = new Gson();
    private static final String FILTER = "semion-td-gametest:game_builder_performance_test_measures_builder_lane_ticks";
    private static final long SEED = 26031010L;
    private static volatile long consumed;

    @Name("semiontd.BuilderProfileCell")
    public static final class CellEvent extends Event {
        public String builder;
        public int size;
        public long operations;
    }

    @GameTest(structure = "semion-td-gametest:combat_arena", maxTicks = 200000)
    public void measuresBuilderLaneTicks(GameTestHelper context) throws Exception {
        if (!Boolean.getBoolean("semiontd.builderProfile")) {
            context.succeed();
            return;
        }
        context.assertTrue(FILTER.equals(System.getProperty("fabric-api.gametest.filter"))
                && context.getLevel().getServer() instanceof GameTestServer,
                "Builder profiling requires its exact filter and an isolated GameTestServer");
        ThreadMXBean threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        context.assertTrue(threads.isThreadAllocatedMemorySupported() && threads.isCurrentThreadCpuTimeSupported(),
                "Direct CPU and allocation counters must be available");
        threads.setThreadAllocatedMemoryEnabled(true);
        threads.setThreadCpuTimeEnabled(true);
        int warmup = Integer.getInteger("semiontd.builderWarmup", 32);
        int samples = Integer.getInteger("semiontd.builderSamples", 32);
        int batch = Integer.getInteger("semiontd.builderBatch", 16);
        context.assertTrue(warmup >= 1 && samples >= 10 && batch >= 1, "Invalid measurement lengths");
        String variant = System.getProperty("semiontd.profileVariant", "unspecified");
        context.assertTrue(variant.matches("[A-Za-z0-9._-]{1,80}"), "Invalid variant");
        Path output = Path.of("builder-profile-" + variant + ".json");
        context.assertTrue(!Files.exists(output), "Profile output must be fresh");
        List<Map<String, Object>> cells = new ArrayList<>();
        var level = context.getLevel();
        var clock = (net.minecraft.world.level.storage.ServerLevelData) level.getLevelData();
        long originalTime = level.getGameTime();
        BlockPos origin = new BlockPos(0, 64, 0);
        List<BlockPos> floors = new ArrayList<>();
        boolean spark = Boolean.getBoolean("semiontd.builderSpark");
        boolean jfr = Boolean.getBoolean("semiontd.builderJfr");
        if (spark) {
            context.assertTrue(level.getServer().getCommands().getDispatcher().getRoot().getChild("spark") != null,
                    "Requested Spark runtime must be loaded");
            level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(),
                    "spark profiler start --force-java-sampler --interval 1");
        }
        try (Recording recording = new Recording(Configuration.getConfiguration("profile"))) {
            if (jfr) {
                recording.disable("jdk.JVMInformation");
                recording.disable("jdk.InitialSystemProperty");
                recording.disable("jdk.InitialEnvironmentVariable");
                recording.disable("jdk.SystemProcess");
                recording.enable(CellEvent.class);
                recording.enable("jdk.CompilerInlining");
                recording.start();
            }
            for (int x = 0; x < 20; x++) {
                for (int z = 0; z < 20; z++) {
                    BlockPos floor = origin.offset(x, -1, z);
                    floors.add(floor);
                    level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
                }
            }
            List<SemionJob> builders = JobRegistry.all().stream()
                    .filter(job -> job != JobRegistry.defaultJob())
                    .filter(job -> job.id().getNamespace().equals(SemionTd.MOD_ID)
                            && job.getClass().getPackageName().equals("kim.biryeong.semiontd.job")).toList();
            measureBoards(context, threads, builders, origin, warmup, samples, batch, cells);
            measureSelections(context, threads, warmup, samples, cells);
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("variant", variant);
            report.put("seed", SEED);
            report.put("preconditioningSweeps", 1);
            report.put("java", System.getProperty("java.version"));
            report.put("vmArguments", ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                    .filter(arg -> arg.startsWith("-Xm") || arg.startsWith("-XX:")).toList());
            report.put("heap", Runtime.getRuntime().maxMemory());
            report.put("jfr", jfr);
            report.put("spark", spark);
            report.put("worldSeed", level.getSeed());
            report.put("origin", List.of(origin.getX(), origin.getY(), origin.getZ()));
            report.put("clockStart", 10000);
            report.put("builders", builders.stream().map(job -> job.id().toString()).toList());
            report.put("limits", "FACTORY_BOARDS; DIRECT_LANE_TICKS; NO_ENTITY_AI_TICKS; NO_ENEMIES; NO_JOB_SELECTION; "
                    + "ONE_FULL_MATRIX_PRECONDITIONING_SWEEP; NO_CLIENTS; BUNDLED_CONFIG; FIXED_WORLD_CLOCK_PER_BATCH; NOT_FULL_MATCH_MSPT_OR_TPS");
            report.put("cells", cells);
            Files.writeString(output, JSON.toJson(report), StandardCharsets.UTF_8);
            if (jfr) {
                recording.stop();
                recording.dump(Path.of("builder-profile-" + variant + ".jfr"));
            }
        } finally {
            clock.setGameTime(originalTime);
            for (BlockPos floor : floors) level.setBlockAndUpdate(floor, Blocks.AIR.defaultBlockState());
            ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        }
        if (spark) {
            level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(),
                    "spark profiler stop --save-to-file");
            context.startSequence().thenWaitUntil(() -> {
                try (var files = Files.list(Path.of("config/spark"))) {
                    context.assertTrue(files.anyMatch(path -> path.toString().endsWith(".sparkprofile")),
                            "Spark profile must be saved locally before server exit");
                } catch (java.io.IOException failure) {
                    throw new IllegalStateException(failure);
                }
            }).thenSucceed();
        } else {
            context.succeed();
        }
    }

    private static void measureBoards(GameTestHelper context, ThreadMXBean threads, List<SemionJob> builders,
            BlockPos origin, int warmup, int samples, int batch, List<Map<String, Object>> cells) throws Exception {
        var level = context.getLevel();
        var clock = (net.minecraft.world.level.storage.ServerLevelData) level.getLevelData();
        for (int sweep = 0; sweep < 2; sweep++) {
            for (SemionJob builder : builders) {
                for (int size : new int[] {0, 1, 8, 32}) {
                    long setupStart = System.nanoTime();
                    long setupBytes = allocated(threads);
                    ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
                    UUID owner = fixed(builder.id() + ":" + size);
                    clock.setGameTime(10000);
                    level.getRandom().setSeed(SEED);
                    PlayerLane lane = new PlayerLane(TeamId.RED, 1, owner, level,
                            new LaneRegionLayout(1, Vec3.atBottomCenterOf(origin),
                                    List.of(Vec3.atBottomCenterOf(origin.offset(0, 0, 8))),
                                    Vec3.atBottomCenterOf(origin.offset(0, 0, 18)),
                                    BlockBounds.of(origin.offset(0, -1, 0), origin.offset(19, 4, 19)),
                                    List.of(GridPosition.from(origin.offset(0, 0, 18)))));
                    AreaEffectLaneIndex.register(lane);
                    try {
                        if (builder instanceof kim.biryeong.semiontd.job.BlueprintTowerJob) {
                            var created = BlueprintStates.create(owner, "Profile",
                                    new BlueprintStats(120, 12, 20, 7, 25, DamageType.PHYSICAL),
                                    BlueprintVisuals.options().getFirst().sourceTowerId());
                            context.assertTrue(created.success(), created.message());
                        }
                        var entries = ProductionTowerCatalog.all().stream()
                                .filter(entry -> entry.availability() == ProductionTowerCatalog.Availability.JOB
                                        && builder.includesTowerInCatalog(entry.type()))
                                .sorted(Comparator.comparing(entry -> entry.type().id())).toList();
                        context.assertTrue(!entries.isEmpty(), "No catalog entries for " + builder.id());
                        List<String> inputs = new ArrayList<>();
                        for (int index = 0; index < size; index++) {
                            var entry = entries.get(index % entries.size());
                            GridPosition position = GridPosition.from(origin.offset(index % 8 * 2, 0, index / 8 * 2));
                            Tower tower = entry.create(owner, TeamId.RED, 1, position);
                            tower.setData(TowerDataKey.of(Identifier.fromNamespaceAndPath("semiontd", "augment_logical_id"),
                                    UUID.class), fixed(builder.id() + ":" + size + ":" + index));
                            String input = tower.type().id() + ":" + index % 8 * 2 + ":" + index / 8 * 2;
                            if (tower instanceof kim.biryeong.semiontd.tower.queen.QueenCardTower cardTower) {
                                input += ":card=" + kim.biryeong.semiontd.tower.queen.QueenTowerPerformanceInputTest.preset(cardTower, index).label();
                            }
                            inputs.add(input);
                            lane.addTower(tower);
                        }
                        long setupNanos = System.nanoTime() - setupStart;
                        long setupAllocated = allocated(threads) - setupBytes;
                        long firstBytes = allocated(threads);
                        long firstStarted = System.nanoTime();
                        lane.tickTowers();
                        long firstNanos = System.nanoTime() - firstStarted;
                        long firstAllocated = allocated(threads) - firstBytes;
                        List<String> trace = new ArrayList<>();
                        List<Object> firstState = List.of();
                        List<Object> lastState = List.of();
                        long[] elapsed = new long[samples];
                        long[] cpu = new long[samples];
                        long[] bytes = new long[samples];
                        CellEvent event = new CellEvent();
                        event.builder = builder.id().toString();
                        event.size = size;
                        event.operations = (long) samples * batch;
                        long gcCount = gc(false);
                        long gcMillis = gc(true);
                        for (int sample = -warmup; sample < samples; sample++) {
                            clock.setGameTime(10000L + (long) (sample + warmup) * batch);
                            if (sample == 0 && sweep == 1) event.begin();
                            long beforeCpu = threads.getCurrentThreadCpuTime();
                            long beforeBytes = allocated(threads);
                            long before = System.nanoTime();
                            for (int tick = 0; tick < batch; tick++) lane.tickTowers();
                            long delta = System.nanoTime() - before;
                            long byteDelta = allocated(threads) - beforeBytes;
                            long cpuDelta = threads.getCurrentThreadCpuTime() - beforeCpu;
                            if (sample >= 0) {
                                elapsed[sample] = delta;
                                cpu[sample] = cpuDelta;
                                bytes[sample] = byteDelta;
                                List<Object> states = new ArrayList<>();
                                for (Tower tower : lane.towers()) {
                                    states.add(List.of(tower.type().id(), Double.doubleToRawLongBits(tower.health()),
                                            Double.doubleToRawLongBits(tower.currentMaxHealth()),
                                            List.of(tower.position().x() - origin.getX(), tower.position().y() - origin.getY(),
                                                    tower.position().z() - origin.getZ()),
                                            tower.runtimeDetailLines()));
                                }
                                trace.add(hash(JSON.toJson(states)));
                                if (sample == 0) firstState = states;
                                if (sample == samples - 1) lastState = states;
                            }
                        }
                        if (sweep == 1) {
                            event.end();
                            event.commit();
                        }
                        Map<String, Object> cell = new LinkedHashMap<>();
                        cell.put("builder", builder.id().toString());
                        cell.put("displayName", builder.displayName().getString());
                        cell.put("size", size);
                        cell.put("input", inputs);
                        cell.put("inputSha256", hash(JSON.toJson(inputs)));
                        cell.put("trace", trace);
                        cell.put("traceSha256", hash(JSON.toJson(trace)));
                        cell.put("firstState", firstState);
                        cell.put("lastState", lastState);
                        cell.put("batch", batch);
                        cell.put("warmup", warmup);
                        cell.put("samples", samples);
                        cell.put("nanos", elapsed);
                        cell.put("cpuNanos", cpu);
                        cell.put("allocatedBytes", bytes);
                        cell.put("setupNanos", setupNanos);
                        cell.put("setupAllocatedBytes", setupAllocated);
                        cell.put("firstTickNanos", firstNanos);
                        cell.put("firstTickAllocatedBytes", firstAllocated);
                        cell.put("gcCountIncludingWarmup", gc(false) - gcCount);
                        cell.put("gcMillisIncludingWarmup", gc(true) - gcMillis);
                        if (sweep == 1) cells.add(cell);
                    } finally {
                        JobBuilderLifecycle.closeBeforeLanes(Set.of(owner));
                        lane.clearTowers();
                        AreaEffectLaneIndex.unregister(lane);
                        JobBuilderLifecycle.closeAfterLanes(Set.of(owner));
                        BlueprintStates.clear(owner);
                    }
                }
            }
        }
    }

    private static void measureSelections(GameTestHelper context, ThreadMXBean threads, int warmup, int samples,
            List<Map<String, Object>> cells) throws Exception {
        for (int sweep = 0; sweep < 2; sweep++) {
            for (int size : new int[] {0, 1, 8, 16, 32, 128, 512}) {
                List<Integer> candidates = new ArrayList<>();
                var random = new java.util.Random(SEED);
                for (int index = 0; index < size; index++) candidates.add(random.nextInt(128));
                for (int limit : new int[] {1, 3, 8}) {
                    List<Integer> expected = candidates.stream().sorted().limit(limit).toList();
                    context.assertTrue(expected.equals(AreaTargetSelection.sortedFirst(candidates, Integer::compare, limit)),
                            "Stable selection oracle mismatch");
                    long[] nanos = new long[samples];
                    long[] bytes = new long[samples];
                    int operations = 1024;
                    for (int sample = -warmup; sample < samples; sample++) {
                        long beforeBytes = allocated(threads);
                        long before = System.nanoTime();
                        long checksum = 0;
                        for (int operation = 0; operation < operations; operation++) {
                            checksum += AreaTargetSelection.sortedFirst(candidates, Integer::compare, limit).hashCode();
                        }
                        long delta = System.nanoTime() - before;
                        long byteDelta = allocated(threads) - beforeBytes;
                        consumed = checksum;
                        if (sample >= 0) {
                            nanos[sample] = delta;
                            bytes[sample] = byteDelta;
                        }
                    }
                    if (sweep == 1) cells.add(Map.of("builder", "shared_stable_selection_k" + limit, "size", size,
                            "inputSha256", hash(JSON.toJson(candidates)), "traceSha256", hash(JSON.toJson(expected)),
                            "batch", operations, "nanos", nanos, "allocatedBytes", bytes,
                            "warmup", warmup, "samples", samples));
                }
            }
        }
    }

    private static long allocated(ThreadMXBean threads) {
        return threads.getThreadAllocatedBytes(Thread.currentThread().threadId());
    }

    private static long gc(boolean millis) {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(bean -> millis ? bean.getCollectionTime() : bean.getCollectionCount()).sum();
    }

    private static UUID fixed(String input) {
        return UUID.nameUUIDFromBytes(input.getBytes(StandardCharsets.UTF_8));
    }

    private static String hash(String input) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
    }
}
