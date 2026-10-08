package kim.biryeong.semiontd.game.replay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongConsumer;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.SummonConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TowerPlacementResult;
import kim.biryeong.semiontd.game.replay.benchmark.BenchmarkRecorder;
import kim.biryeong.semiontd.game.replay.benchmark.BenchmarkWorkload;
import kim.biryeong.semiontd.game.replay.benchmark.CombatBenchmarkHooks;
import kim.biryeong.semiontd.map.ArenaLayout;
import kim.biryeong.semiontd.map.GameArena;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.map.TeamArena;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.engineer.EngineerCircuitTower;
import kim.biryeong.semiontd.tower.engineer.EngineerPressStates;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeLevelConfig;
import xyz.nucleoid.fantasy.RuntimeLevelHandle;
import xyz.nucleoid.fantasy.util.VoidChunkGenerator;
import xyz.nucleoid.map_templates.BlockBounds;

public final class CombatSpeedBenchmarkTest {
    @GameTest(maxTicks = 100000)
    public void pacedEightfoldControlledEngineerBattles(GameTestHelper context) {
        if (!CombatBenchmarkHooks.enabled()) {
            context.succeed();
            return;
        }
        try {
            Driver driver = new Driver(context);
            CombatBenchmarkHooks.install(context.getLevel().getServer(), driver);
            driver.prepare();
        } catch (Throwable failure) {
            context.fail(Component.literal("Benchmark setup failed: " + failure));
        }
    }

    private static final class Driver implements CombatBenchmarkHooks.Listener {
        private final GameTestHelper context;
        private final MinecraftServer server;
        private final String mode = System.getProperty("semiontd.benchmark.mode");
        private final int lanes = Integer.getInteger("semiontd.benchmark.lanes", 1);
        private final int warmups = Integer.getInteger("semiontd.benchmark.warmups", 3);
        private final int measured = Integer.getInteger("semiontd.benchmark.measured", 5);
        private final boolean dry = Boolean.getBoolean("semiontd.benchmark.dry");
        private final JsonArray trials = new JsonArray();
        private final BenchmarkRecorder recorder = new BenchmarkRecorder();
        private final float previousRate;
        private Battle battle;
        private int repetition;
        private long physicalFrames;
        private long groupStartFrame;
        private long groupBase;
        private long groupTarget;
        private long groups;
        private long spilledGroups;
        private long maxGroupLag;
        private long beginWaits;
        private boolean extraThree;
        private boolean measuring;
        private boolean finished;
        private boolean done;
        private long logicalTicks;
        private long windowStart;
        private String baselineWorkHash;
        private Object session;
        private long settleUntil;
        private final List<ServerLevel> priorWorlds = new ArrayList<>();

        private Driver(GameTestHelper context) {
            this.context = context;
            server = context.getLevel().getServer();
            previousRate = server.tickRateManager().tickrate();
            require(Set.of("native", "simulation").contains(mode), "Benchmark mode must be native or simulation");
            require(lanes == 1 || lanes == 22, "Supported controlled workload sizes are one and twenty-two lanes");
            require(warmups >= 0 && measured > 0 && (dry || warmups >= 3 && measured >= 5), "Measured JVMs require three warmups and five trials");
            require(!System.getProperty("semiontd.benchmark.sourceRevision", "").isBlank(), "Verified source provenance is required");
            server.tickRateManager().setTickRate(mode.equals("native") ? 160F : 20F);
        }

        private void prepare() throws Exception {
            battle = new Battle(server, lanes);
            session = null;
            physicalFrames = groupStartFrame = groupBase = groupTarget = groups = spilledGroups = maxGroupLag = logicalTicks = 0;
            extraThree = measuring = finished = false;
        }

        @Override
        public void beforeFrame(MinecraftServer ignored) {
            run(() -> {
                if (measuring) {
                    recorder.frameBoundary(mode.equals("native") ? 160 : 20);
                    if (finished) {
                        completeTrial();
                        return;
                    }
                }
                if (battle == null) {
                    if (System.nanoTime() < settleUntil) { return; }
                    for (ServerLevel oldWorld : priorWorlds) {
                        for (ServerLevel loadedWorld : server.getAllLevels()) {
                            require(oldWorld != loadedWorld, "The previous benchmark world is still active before the next trial");
                        }
                    }
                    priorWorlds.clear();
                    prepare();
                    return;
                }
                if (done || !battle.loaded()) {
                    return;
                }
                if (!measuring && !battle.settled()) { return; }
                if (!measuring) {
                    battle.populate();
                    battle.startWave();
                    configure();
                    if (mode.equals("simulation")) {
                        Class<?> type = Class.forName("kim.biryeong.semiontd.game.simulation.CombatSimulationSession");
                        session = type.getConstructor(MinecraftServer.class, SemionGame.class).newInstance(server, battle.game);
                        type.getMethod("setStepObserver", LongConsumer.class).invoke(session, (LongConsumer) this::logicalStep);
                        Object executor = ReplayCapture.field(ReplayCapture.field(session, "coordinator"), "executor");
                        recorder.worker((Thread) ReplayCapture.field(executor, "worker"));
                    } else {
                        recorder.worker(null);
                    }
                    battle.work.start();
                    beginWaits = CombatBenchmarkHooks.baseWaitCallCount(server);
                    windowStart = System.nanoTime();
                    recorder.begin();
                    measuring = true;
                }
                physicalFrames++;
                configure();
                require(server.tickRateManager().tickrate() == (mode.equals("native") ? 160F : 20F), "Actual physical frame clock differs from benchmark mode");
                if (session != null && (boolean) call("idle")) {
                    if (groupTarget != 0) {
                        long lag = physicalFrames - groupStartFrame;
                        maxGroupLag = Math.max(maxGroupLag, lag);
                        if (lag > 1) { spilledGroups++; }
                    }
                    groupBase = logicalTicks;
                    groupTarget = groupBase + 8;
                    groupStartFrame = physicalFrames;
                    extraThree = false;
                    groups++;
                    call("beginFrame", int.class, 5);
                }
                require(System.nanoTime() - windowStart < 120000000000L, "A battle exceeded the bounded 120 second measurement window");
            });
        }

        @Override
        public void afterFrame(MinecraftServer ignored) {
            run(() -> {
                if (!measuring || done) { return; }
                configure();
                if (session == null) {
                    battle.game.tick(server);
                    logicalStep(++logicalTicks);
                } else {
                    call("endFrame");
                    Throwable failure = (Throwable) call("failure");
                    if (failure != null) { throw new IllegalStateException("Measured simulation failed", failure); }
                }
                recorder.outerEnd();
            });
        }

        private void logicalStep(long tick) {
            try {
                logicalTicks = tick;
                if (session != null && !extraThree && tick == groupBase + 5 && battle.game.phase() == RoundPhase.LANE_WAVE) {
                    extraThree = true;
                    call("beginFrame", int.class, 3);
                }
                require(tick < 5000 || battle.game.phase() != RoundPhase.LANE_WAVE, "The controlled battle did not naturally finish within five thousand logical ticks");
                if (battle.game.phase() != RoundPhase.LANE_WAVE) {
                    recorder.freezeWorker();
                    finished = true;
                }
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }

        private void completeTrial() throws Exception {
            measuring = false;
            battle.work.stop();
            JsonObject metrics = recorder.end(logicalTicks, CombatBenchmarkHooks.baseWaitCallCount(server) - beginWaits);
            metrics.addProperty("repetition", repetition);
            metrics.addProperty("warmup", repetition < warmups);
            metrics.addProperty("requested_eight_step_groups", groups);
            metrics.addProperty("final_group_requested_steps", groupTarget == 0 ? 0 : 8);
            metrics.addProperty("groups_spanning_multiple_physical_frames", spilledGroups);
            metrics.addProperty("maximum_group_physical_frames", maxGroupLag);
            metrics.addProperty("experimental_runtime", "TEST_ONLY_5_PLUS_3_EXISTING_MAX_5_BUDGET_PRODUCTION_8X_UNSUPPORTED");
            metrics.addProperty("active_benchmark_worlds", battle.handles.size());
            metrics.addProperty("settling_before_each_new_setup_ms", 1000);
            JsonObject workload = battle.digest(logicalTicks);
            String hash = hash(workload);
            require(baselineWorkHash == null || baselineWorkHash.equals(hash), "Repeated battles do not perform identical controlled work");
            baselineWorkHash = hash;
            metrics.addProperty("matched_work_sha256", hash);
            metrics.add("workload", workload);
            trials.add(metrics);
            System.out.println("BENCH trial complete mode=" + mode + " lanes=" + lanes + " repetition=" + repetition
                    + " logical=" + logicalTicks + " observedTPS=" + metrics.get("observed_logical_tps") + " work=" + hash);
            closeBattle();
            repetition++;
            if (repetition < warmups + measured) {
                battle = null;
                settleUntil = System.nanoTime() + 1000000000L;
            } else {
                JsonObject output = new JsonObject();
                output.addProperty("schema_version", 1);
                output.addProperty("run_id", UUID.randomUUID().toString());
                output.addProperty("source_revision", System.getProperty("semiontd.benchmark.sourceRevision"));
                output.addProperty("production_revision", System.getProperty("semiontd.benchmark.productionRevision"));
                output.addProperty("driver", mode);
                output.addProperty("engineer_lanes", lanes);
                output.addProperty("warmup_trials", warmups);
                output.addProperty("measured_trials", measured);
                output.addProperty("dry_run", dry);
                output.addProperty("abba_order", System.getProperty("semiontd.benchmark.order"));
                output.addProperty("physical_tps", mode.equals("native") ? 160 : 20);
                output.addProperty("logical_target_tps", 160);
                output.addProperty("production_eightfold_supported", false);
                output.addProperty("scenario_sha256", battle.scenarioHash);
                output.addProperty("rules_sha256", battle.rulesHash);
                output.addProperty("map_sha256", battle.mapHash);
                output.addProperty("seed", "1");
                output.addProperty("scope", "CONTROLLED_REPLICATED_ENGINEER_WORKLOAD_NOT_HISTORICAL_MATCH_OR_CLIENT_RENDERING");
                output.addProperty("instrumentation", "TICK_BODY_PLUS_WHOLE_MAIN_HEAD_TO_NEXT_HEAD_INCLUDING_OWNER_WAIT_COMPLETIONS");
                JsonObject environment = new JsonObject();
                environment.addProperty("java_version", System.getProperty("java.version"));
                environment.addProperty("vm_name", System.getProperty("java.vm.name"));
                environment.addProperty("os_name", System.getProperty("os.name"));
                environment.addProperty("os_version", System.getProperty("os.version"));
                environment.addProperty("architecture", System.getProperty("os.arch"));
                environment.addProperty("logical_processors", Runtime.getRuntime().availableProcessors());
                environment.addProperty("maximum_heap_bytes", Runtime.getRuntime().maxMemory());
                environment.add("vm_arguments", new Gson().toJsonTree(java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()));
                environment.add("gc_names", new Gson().toJsonTree(java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().stream().map(java.lang.management.GarbageCollectorMXBean::getName).toList()));
                output.add("environment", environment);
                output.add("trials", trials);
                Path path = Path.of(System.getProperty("semiontd.benchmark.output"));
                Files.createDirectories(path.toAbsolutePath().getParent());
                Files.writeString(path, new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(output) + "\n", StandardCharsets.UTF_8);
                done = true;
                CombatBenchmarkHooks.remove(server);
                server.tickRateManager().setTickRate(previousRate);
                context.succeed();
            }
        }

        private void configure() throws Exception {
            Class<?> scale = Class.forName("kim.biryeong.semiontd.game.ClientTickScale");
            float ratio;
            if (mode.equals("simulation")) {
                Class<?> speed = Class.forName("kim.biryeong.semiontd.game.CombatSpeedRuntime");
                var method = speed.getDeclaredMethod("configure", MinecraftServer.class, SemionGame.class, float.class, int.class);
                method.setAccessible(true);
                method.invoke(null, server, battle.game, 160F, 1);
                ratio = ((Number) scale.getMethod("ratio", MinecraftServer.class, ServerLevel.class)
                        .invoke(null, server, battle.handles.get(0).asLevel())).floatValue();
            } else {
                ratio = ((Number) scale.getMethod("ratio", MinecraftServer.class).invoke(null, server)).floatValue();
            }
            require(ratio == 8F, "The actual arena client clock ratio must be eight");
        }

        private Object call(String method) throws Exception { return session.getClass().getMethod(method).invoke(session); }
        private Object call(String method, Class<?> type, Object value) throws Exception { return session.getClass().getMethod(method, type).invoke(session, value); }
        @Override public void beforeNativeTick(MinecraftServer server) { recorder.nativeBegin(); }
        @Override public void afterNativeTick(MinecraftServer server) { recorder.nativeEnd(); }
        @Override public void beforeWait(MinecraftServer server) { recorder.waitBegin(); }
        @Override public void afterWait(MinecraftServer server) { recorder.waitEnd(); }

        private void closeBattle() throws Exception {
            if (session != null) { call("close"); }
            if (battle != null) {
                battle.handles.forEach(handle -> priorWorlds.add(handle.asLevel()));
                battle.close();
            }
            if (mode.equals("simulation")) {
                var clear = Class.forName("kim.biryeong.semiontd.game.CombatSpeedRuntime").getDeclaredMethod("clear");
                clear.setAccessible(true);
                clear.invoke(null);
            }
        }

        private void run(Checked action) {
            if (done) { return; }
            try {
                action.run();
            } catch (Throwable failure) {
                done = true;
                try { closeBattle(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
                CombatBenchmarkHooks.remove(server);
                server.tickRateManager().setTickRate(previousRate);
                context.fail(Component.literal("Benchmark failed at repetition " + repetition + "/logical " + logicalTicks + ": " + failure));
            }
        }
    }

    private static final class Battle implements AutoCloseable {
        private final List<RuntimeLevelHandle> handles = new ArrayList<>();
        private final List<ReplayCapture> captures = new ArrayList<>();
        private final List<AssignedParticipant> engineers = new ArrayList<>();
        private final BenchmarkWorkload work = new BenchmarkWorkload();
        private final SemionGame game;
        private final String scenarioHash;
        private final String rulesHash;
        private final String mapHash;
        private final MinecraftServer server;
        private final int teams;
        private final List<AssignedParticipant> participants = new ArrayList<>();
        private final Map<TeamId, ReplayCapture> teamCaptures = new EnumMap<>(TeamId.class);
        private final JsonObject opening;
        private boolean populated;
        private long readyAt;

        private Battle(MinecraftServer server, int laneCount) throws Exception {
            this.server = server;
            ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
            try (var reader = new InputStreamReader(java.util.Objects.requireNonNull(
                    getClass().getResourceAsStream("/replay/engineer-opening.json")), StandardCharsets.UTF_8)) {
                opening = JsonParser.parseReader(reader).getAsJsonObject();
            }
            Map<TeamId, TeamArena> arenas = new EnumMap<>(TeamId.class);
            JsonObject layouts = new JsonObject();
            teams = laneCount == 1 ? 2 : 6;
            for (int index = 0; index < teams; index++) {
                TeamId team = TeamId.values()[index];
                RuntimeLevelHandle handle = Fantasy.get(server).openTemporaryLevel(
                        Identifier.fromNamespaceAndPath("semion-td-gametest", "benchmark_" + team.name().toLowerCase() + "_" + UUID.randomUUID()),
                        new RuntimeLevelConfig().setGenerator(new VoidChunkGenerator(server)).setSeed(1L).setGameTime(0)
                                .setGameRule(GameRules.SPAWN_MOBS, false));
                handle.setTickWhenEmpty(true);
                handles.add(handle);
                ServerLevel world = handle.asLevel();
                for (int x = -1; x <= 3; x++) {
                    for (int z = -1; z <= 2; z++) { world.setChunkForced(x, z, true); world.getChunk(x, z); }
                }
                world.getRandom().setSeed(1L);
                ArenaLayout layout = layout();
                for (int x = 0; x < 48; x++) {
                    for (int z = 0; z < 32; z++) { world.setBlock(new BlockPos(x, 64, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL); }
                }
                arenas.put(team, new TeamArena(team, () -> {}, world, layout));
                layouts.add(team.name(), new Gson().toJsonTree(layout));
                ReplayCapture capture = new ReplayCapture(world, new JsonObject(), team.name());
                captures.add(capture);
                teamCaptures.put(team, capture);
                work.attach(capture, team.ordinal(), laneCount != 1 || team == TeamId.RED);
                int playerCount = laneCount == 1 ? 1 : Math.min(4, laneCount - index * 4);
                for (int lane = 1; lane <= playerCount; lane++) {
                    UUID playerId = UUID.nameUUIDFromBytes(("benchmark/" + team + "/" + lane).getBytes(StandardCharsets.UTF_8));
                    AssignedParticipant player = new AssignedParticipant(playerId, "benchmark-" + team + "-" + lane, team, lane);
                    participants.add(player);
                    if (laneCount != 1 || team == TeamId.RED) { engineers.add(player); }
                }
            }
            for (TeamId team : TeamId.values()) {
                arenas.putIfAbsent(team, new TeamArena(team, () -> {}, handles.get(0).asLevel(), layout()));
            }
            EconomyConfig economy = EconomyConfig.defaultConfig();
            WaveConfig waves = WaveConfig.defaultConfig();
            game = new SemionGame(economy, waves, new GameArena(arenas));
            ((Random) ReplayCapture.field(game, "random")).setSeed(1L);
            JsonObject scenario = opening.deepCopy();
            scenario.addProperty("engineer_lanes", laneCount);
            scenario.addProperty("replication", "SAME_ENGINEER_LAYOUT_OFFSET_Z8_PER_LANE_NOT_HISTORICAL_22_PLAYERS");
            scenarioHash = hash(scenario);
            mapHash = hash(layouts);
            JsonObject rules = new JsonObject();
            Gson gson = new Gson();
            rules.add("tower_balance", gson.toJsonTree(TowerBalanceConfig.defaultConfig()));
            rules.add("economy", gson.toJsonTree(economy));
            rules.add("waves", gson.toJsonTree(waves));
            rules.add("summons", gson.toJsonTree(SummonConfig.defaultConfig()));
            rules.addProperty("seed_mapping", "ReplayCapture stable team namespace/global-per-world-placement-sequence laneIndex5+recordedSeq/world1/game1/preinsertidentity_and_rotations0/globalEntityIdBase(teamOrdinal+1)*1000000");
            rules.addProperty("traits_augments", "none round1");
            rulesHash = hash(rules);
        }

        private void populate() throws Exception {
            if (populated) { return; }
            handles.forEach(handle -> handle.asLevel().getRandom().setSeed(1L));
            for (AssignedParticipant player : engineers) {
                require(game.selectJob(player.uuid(), Identifier.parse("semion-td:engineer_towers")), "Controlled engineer selection");
            }
            require(game.start(server, new ParticipantSelectionPlan(MatchMode.NORMAL, participants, Set.of(), teams)), "Controlled real game starts");
            for (AssignedParticipant player : engineers) {
                var lane = game.playerLane(player.uuid()).orElseThrow();
                ReplayCapture capture = teamCaptures.get(player.teamId());
                capture.bind(lane);
                game.players().get(player.uuid()).economy().overrideStartingValues(10000, 10000, 0, 1);
                for (var value : opening.getAsJsonArray("actions")) {
                    JsonObject action = value.getAsJsonObject();
                    int sequence = (player.laneId() - 1) * 5 + action.get("sequence").getAsInt();
                    BlockPos position = new BlockPos(action.get("position_x").getAsInt(), 64 + action.get("position_y").getAsInt(),
                            (player.laneId() - 1) * 8 + action.get("position_z").getAsInt());
                    capture.placement(sequence, () -> require(ProductionTowerService.placeTower(game, player.uuid(), position,
                            action.get("subject_id").getAsString()) == TowerPlacementResult.SUCCESS, "Controlled native placement " + sequence));
                    require(lane.towerAt(GridPosition.from(position)).paidMineralCost() == action.get("cost").getAsLong(), "Actual recorded placement cost");
                }
                require(lane.towers().size() == 5 && game.players().get(player.uuid()).economy().diamond() == 9869, "Five placements and 131 diamonds per controlled lane");
            }
            populated = true;
        }

        private boolean loaded() {
            for (RuntimeLevelHandle handle : handles) {
                for (int x = 0; x <= 2; x++) {
                    for (int z = 0; z <= 1; z++) {
                        if (!handle.asLevel().areEntitiesActuallyLoadedAndTicking(new ChunkPos(x, z))) { return false; }
                    }
                }
            }
            return true;
        }

        private boolean settled() {
            if (readyAt == 0) {
                readyAt = System.nanoTime() + 1000000000L;
                return false;
            }
            return System.nanoTime() >= readyAt;
        }

        private void startWave() throws Exception {
            var field = SemionGame.class.getDeclaredField("phaseTicks");
            field.setAccessible(true);
            field.set(game, ((Number) ReplayCapture.field(game, "currentPrepareDurationTicks")).intValue());
            game.tick(handles.get(0).asLevel().getServer());
            require(game.phase() == RoundPhase.LANE_WAVE, "Actual native round wave begins");
        }

        private JsonObject digest(long logicalTicks) throws Exception {
            JsonObject result = work.snapshot();
            result.addProperty("logical_ticks", logicalTicks);
            JsonArray players = new JsonArray();
            long rewards = 0;
            long kills = 0;
            for (AssignedParticipant participant : engineers) {
                var player = game.players().get(participant.uuid());
                var lane = game.playerLane(participant.uuid()).orElseThrow();
                JsonObject row = new JsonObject();
                row.addProperty("slot", participant.teamId() + "/" + participant.laneId());
                row.addProperty("diamond", player.economy().diamond());
                row.addProperty("emerald", player.economy().emerald());
                row.addProperty("monster_kills", player.matchStats().monsterKills());
                row.addProperty("kill_rewards", player.matchStats().killMinerals());
                row.addProperty("plate_presses", EngineerPressStates.count(player.uuid()));
                JsonArray towers = new JsonArray();
                for (var tower : lane.towers()) {
                    JsonObject state = new JsonObject();
                    state.addProperty("type", tower.type().id());
                    state.addProperty("health", tower.health());
                    state.addProperty("x", tower.position().x());
                    state.addProperty("y", tower.position().y());
                    state.addProperty("z", tower.position().z());
                    if (tower instanceof EngineerCircuitTower circuit) { state.addProperty("pressed", circuit.platePressed(lane)); }
                    towers.add(state);
                }
                row.add("towers", towers);
                players.add(row);
                rewards += player.matchStats().killMinerals();
                kills += player.matchStats().monsterKills();
            }
            result.add("players", players);
            result.addProperty("kill_diamonds", rewards);
            require(kills == 12L * engineers.size() && rewards == 36L * engineers.size(), "All twelve monsters per engineer lane must be killed with actual diamond rewards");
            require(result.get("monster_deaths").getAsLong() == kills, "Lightweight death callbacks cover the controlled workload");
            require(result.get("natural_spawns").getAsLong() == kills, "All natural spawned units must be part of the completed controlled workload");
            return result;
        }

        @Override
        public void close() {
            for (ReplayCapture capture : captures) { work.detach(capture); capture.close(); }
            game.close();
            handles.forEach(RuntimeLevelHandle::unload);
        }
    }

    private static ArenaLayout layout() {
        Map<Integer, LaneRegionLayout> lanes = new LinkedHashMap<>();
        for (int lane = 1; lane <= 5; lane++) {
            int z = (lane - 1) * 8;
            Vec3 spawn = new Vec3(0.5, 65, z + 2.5);
            Vec3 boss = new Vec3(46.5, 65, 18.5);
            lanes.put(lane, new LaneRegionLayout(lane, spawn, List.of(new Vec3(38.5, 65, z + 2.5),
                    new Vec3(45.5, 65, z + 2.5)), boss, BlockBounds.of(new BlockPos(0, 64, z), new BlockPos(47, 69, z + 7)),
                    List.of(new GridPosition(44, 64, z + 2))));
        }
        return new ArenaLayout(new Vec3(1.5, 65, 4.5), new Vec3(46.5, 65, 18.5), lanes);
    }

    private static String hash(JsonElement input) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new GsonBuilder().serializeNulls().create()
                .toJson(sorted(input)).getBytes(StandardCharsets.UTF_8)));
    }
    private static JsonElement sorted(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject result = new JsonObject();
            value.getAsJsonObject().keySet().stream().sorted().forEach(key -> result.add(key, sorted(value.getAsJsonObject().get(key))));
            return result;
        }
        if (value.isJsonArray()) {
            JsonArray result = new JsonArray();
            value.getAsJsonArray().forEach(item -> result.add(sorted(item)));
            return result;
        }
        return value;
    }
    private static void require(boolean condition, String message) { if (!condition) { throw new IllegalStateException(message); } }
    @FunctionalInterface private interface Checked { void run() throws Exception; }
}
