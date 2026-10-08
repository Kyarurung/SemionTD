package kim.biryeong.semiontd.game.replay;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.EnumMap;
import java.util.HexFormat;
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
import kim.biryeong.semiontd.map.ArenaLayout;
import kim.biryeong.semiontd.map.GameArena;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.map.TeamArena;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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

public final class ReplayOpeningCaptureTest {
    private static Driver active;
    private static boolean listenersRegistered;

    @GameTest(maxTicks = 1800)
    public void recordedEngineerOpeningUsesNativePlacementAndOptionalIndependentBattleCapture(GameTestHelper context) {
        registerListeners();
        MinecraftServer server = context.getLevel().getServer();
        RuntimeLevelHandle red = world(server, "red");
        RuntimeLevelHandle blue = world(server, "blue");
        for (RuntimeLevelHandle handle : List.of(red, blue)) {
            for (int x = -1; x <= 3; x++) {
                for (int z = -1; z <= 1; z++) {
                    handle.asLevel().setChunkForced(x, z, true);
                    handle.asLevel().getChunk(x, z);
                }
            }
        }
        context.startSequence().thenWaitUntil(() -> {
            for (RuntimeLevelHandle handle : List.of(red, blue)) {
                for (int x = 0; x <= 2; x++) {
                    context.assertTrue(handle.asLevel().areEntitiesActuallyLoadedAndTicking(new ChunkPos(x, 0)),
                            "Replay opening chunks must be entity ticking");
                }
            }
        }).thenExecute(() -> {
            try {
                if (active != null) {
                    throw new IllegalStateException("Concurrent capture drivers are unsupported");
                }
                Driver driver = new Driver(context, red, blue);
                if (driver.mode.equals("fixture")) {
                    driver.close();
                    context.succeed();
                } else {
                    active = driver;
                    driver.start();
                }
            } catch (Throwable failure) {
                ReplayCapture capture = ReplayCapture.current(red.asLevel());
                if (capture != null) {
                    capture.close();
                }
                red.unload();
                blue.unload();
                context.fail(Component.literal("Replay opening setup failed: " + failure));
            }
        });
    }

    private static void registerListeners() {
        if (listenersRegistered) {
            return;
        }
        listenersRegistered = true;
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            Driver driver = active;
            if (driver != null) {
                driver.run(() -> driver.begin(server));
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Driver driver = active;
            if (driver != null) {
                driver.run(() -> driver.end(server));
            }
        });
    }

    private static RuntimeLevelHandle world(MinecraftServer server, String suffix) {
        RuntimeLevelHandle handle = Fantasy.get(server).openTemporaryLevel(
                Identifier.fromNamespaceAndPath("semion-td-gametest", "replay_" + suffix + "_" + UUID.randomUUID()),
                new RuntimeLevelConfig().setGenerator(new VoidChunkGenerator(server)).setSeed(1L).setGameTime(0)
                        .setGameRule(GameRules.SPAWN_MOBS, false));
        handle.setTickWhenEmpty(true);
        return handle;
    }

    private static final class Driver implements AutoCloseable {
        private final GameTestHelper context;
        private final RuntimeLevelHandle red;
        private final RuntimeLevelHandle blue;
        private final SemionGame game;
        private final ReplayCapture capture;
        private final String mode = System.getProperty("semiontd.replay.capture", "fixture");
        private final float previousRate;
        private Object session;
        private long tick;
        private boolean finishing;
        private boolean closed;

        private Driver(GameTestHelper context, RuntimeLevelHandle red, RuntimeLevelHandle blue) throws Exception {
            this.context = context;
            this.red = red;
            this.blue = blue;
            MinecraftServer server = context.getLevel().getServer();
            previousRate = server.tickRateManager().tickrate();
            if (!Set.of("fixture", "native", "simulation").contains(mode)) {
                throw new IllegalArgumentException("Unknown replay capture mode " + mode);
            }
            ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
            JsonObject scenario;
            try (var reader = new InputStreamReader(java.util.Objects.requireNonNull(
                    getClass().getResourceAsStream("/replay/engineer-opening.json")), StandardCharsets.UTF_8)) {
                scenario = JsonParser.parseReader(reader).getAsJsonObject();
            }
            ArenaLayout layout = layout();
            for (RuntimeLevelHandle handle : List.of(red, blue)) {
                ServerLevel world = handle.asLevel();
                world.getRandom().setSeed(1L);
                for (int x = 0; x <= 47; x++) {
                    for (int z = 0; z <= 7; z++) {
                        world.setBlock(new BlockPos(x, 64, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
            Map<TeamId, TeamArena> arenas = new EnumMap<>(TeamId.class);
            for (TeamId team : TeamId.values()) {
                arenas.put(team, new TeamArena(team, () -> {}, team == TeamId.RED ? red.asLevel() : blue.asLevel(), layout));
            }
            EconomyConfig economy = EconomyConfig.defaultConfig();
            WaveConfig waves = WaveConfig.defaultConfig();
            game = new SemionGame(economy, waves, new GameArena(arenas));
            ((Random) ReplayCapture.field(game, "random")).setSeed(1L);
            JsonObject metadata = new JsonObject();
            metadata.addProperty("driver", mode.equals("native") ? "NATIVE_REFERENCE" : "ASYNC_LOGICAL_SIMULATION");
            metadata.addProperty("run_id", UUID.randomUUID().toString());
            String revision = System.getProperty("semiontd.replay.sourceRevision", "");
            if (!mode.equals("fixture") && (!revision.matches("[0-9a-f]{40}")
                    || mode.equals("native") && !revision.equals("7a389bf04a5a81a5d1a84c2c36fcf1beb5bafe13"))) {
                throw new IllegalArgumentException("Capture requires a verified production source revision");
            }
            metadata.addProperty("source_revision", revision);
            metadata.addProperty("physical_tps", mode.equals("native") ? 40 : 20);
            metadata.addProperty("logical_tps", 40);
            metadata.addProperty("logical_steps_per_frame", mode.equals("native") ? 1 : 2);
            metadata.addProperty("trace_scope", "PER_LOGICAL_TICK");
            metadata.addProperty("timing_origin", "CONTROLLED_SCENARIO");
            metadata.addProperty("seed", "1");
            metadata.addProperty("scenario_sha256", hash(scenario));
            JsonObject rules = new JsonObject();
            Gson gson = new Gson();
            rules.add("tower_balance", gson.toJsonTree(TowerBalanceConfig.defaultConfig()));
            rules.add("economy", gson.toJsonTree(economy));
            rules.add("waves", gson.toJsonTree(waves));
            rules.add("summons", gson.toJsonTree(SummonConfig.defaultConfig()));
            rules.addProperty("traits", "none/none");
            rules.addProperty("augments", "none in round one");
            rules.addProperty("rng_policy", "world1_game1_actorPlacement104729_wave1000001_ambient2000001");
            metadata.addProperty("rules_sha256", hash(rules));
            metadata.addProperty("map_sha256", hash(gson.toJsonTree(layout)));
            metadata.addProperty("historical_catalog_version", scenario.get("historical_catalog_version").getAsString());
            metadata.addProperty("adaptation", "SAME_PACKAGED_DEFAULT_RULES_AND_SYNTHETIC_MAP_NOT_HISTORICAL_FULL_REPLAY");
            metadata.addProperty("projectile_scope", "ENGINEER_INSTANT_DISPENSER_SHOTS_NOT_BALLISTIC_ENTITIES");
            metadata.addProperty("circuit_scope", "ORDERED_PLATE_PRESS_CALLBACKS_AND_EACH_LOGICAL_TICK_AUTHORITATIVE_PLATE_STATES");
            metadata.addProperty("cooldown_scope", "ACTUAL_TRAP_COUNTER_AND_MAX_1_GOAL_CALLS_UNTIL_READY");
            capture = new ReplayCapture(red.asLevel(), metadata);
            UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000002");
            UUID opponent = UUID.fromString("00000000-0000-0000-0000-000000000003");
            require(game.selectJob(owner, Identifier.parse("semion-td:engineer_towers")), "Engineer job is selectable");
            require(game.start(server, new ParticipantSelectionPlan(MatchMode.NORMAL,
                    List.of(new AssignedParticipant(owner, "fixture-engineer", TeamId.RED, 1),
                            new AssignedParticipant(opponent, "fixture-opponent", TeamId.BLUE, 1)), Set.of(), 2)),
                    "A real two-team normal fixture starts");
            var lane = game.playerLane(owner).orElseThrow();
            capture.bind(lane);
            game.players().get(owner).economy().overrideStartingValues(10000, 10000, 0, 1);
            for (var value : scenario.getAsJsonArray("actions")) {
                JsonObject action = value.getAsJsonObject();
                int sequence = action.get("sequence").getAsInt();
                BlockPos position = new BlockPos(action.get("position_x").getAsInt(),
                        64 + action.get("position_y").getAsInt(), action.get("position_z").getAsInt());
                capture.placement(sequence, () -> require(ProductionTowerService.placeTower(game, owner, position,
                        action.get("subject_id").getAsString()) == TowerPlacementResult.SUCCESS, "Observed placement " + sequence));
                var tower = lane.towerAt(GridPosition.from(position));
                require(tower != null && tower.paidMineralCost() == action.get("cost").getAsLong(),
                        "Observed coordinates and directed spend are preserved");
                capture.component(tower, sequence);
            }
            require(lane.towers().size() == 5 && game.players().get(owner).economy().diamond() == 9869,
                    "Five actual placements spend exactly 131 diamonds");
        }

        private void start() throws Exception {
            MinecraftServer server = context.getLevel().getServer();
            server.tickRateManager().setTickRate(mode.equals("native") ? 40 : 20);
            var phaseTicks = SemionGame.class.getDeclaredField("phaseTicks");
            phaseTicks.setAccessible(true);
            phaseTicks.set(game, ((Number) ReplayCapture.field(game, "currentPrepareDurationTicks")).intValue());
            game.tick(server);
            require(game.phase() == RoundPhase.LANE_WAVE, "Native preparation transitions to its real wave");
            capture.start();
            if (mode.equals("simulation")) {
                Class<?> type = Class.forName("kim.biryeong.semiontd.game.simulation.CombatSimulationSession");
                session = type.getConstructor(MinecraftServer.class, SemionGame.class).newInstance(server, game);
                type.getMethod("setStepObserver", LongConsumer.class).invoke(session, (LongConsumer) step -> {
                    tick = step;
                    capture.sample(step);
                    finishing = tick >= 400 || game.phase() != RoundPhase.LANE_WAVE;
                });
            }
        }

        private void begin(MinecraftServer server) throws Exception {
            require(server.tickRateManager().tickrate() == (mode.equals("native") ? 40 : 20),
                    "The actual physical server rate must match the declared capture driver");
            if (session != null && !finishing && (boolean) call("idle")) {
                call("beginFrame", int.class, 2);
            }
        }

        private void end(MinecraftServer server) throws Exception {
            if (session == null) {
                game.tick(server);
                capture.sample(++tick);
                finishing = tick >= 400 || game.phase() != RoundPhase.LANE_WAVE;
            } else {
                call("endFrame");
            }
            if (finishing && (session == null || (boolean) call("idle") || (boolean) call("isClosed"))) {
                capture.write(Path.of(System.getProperty("semiontd.replay.output")));
                close();
                context.succeed();
            }
        }

        private Object call(String method) throws Exception {
            return session.getClass().getMethod(method).invoke(session);
        }

        private Object call(String method, Class<?> parameter, Object value) throws Exception {
            return session.getClass().getMethod(method, parameter).invoke(session, value);
        }

        private void run(CheckedOperation operation) {
            try {
                operation.run();
            } catch (Throwable failure) {
                try {
                    close();
                } finally {
                    context.fail(Component.literal("Independent replay capture failed at logical tick " + tick + ": " + failure));
                }
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            active = null;
            try {
                if (session != null) {
                    call("close");
                }
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            } finally {
                capture.close();
                game.close();
                red.unload();
                blue.unload();
                context.getLevel().getServer().tickRateManager().setTickRate(previousRate);
            }
        }
    }

    private static ArenaLayout layout() {
        Vec3 spawn = new Vec3(0.5, 65, 2.5);
        Vec3 boss = new Vec3(46.5, 65, 2.5);
        Map<Integer, LaneRegionLayout> lanes = new java.util.LinkedHashMap<>();
        for (int id = 1; id <= 4; id++) {
            lanes.put(id, new LaneRegionLayout(id, spawn, List.of(new Vec3(38.5, 65, 2.5),
                    new Vec3(45.5, 65, 2.5)), boss, BlockBounds.of(new BlockPos(0, 64, 0), new BlockPos(47, 69, 7)),
                    List.of(new GridPosition(44, 64, 2))));
        }
        return new ArenaLayout(new Vec3(1.5, 65, 4.5), boss, lanes);
    }

    private static String hash(JsonElement value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(new Gson().toJson(sorted(value)).getBytes(StandardCharsets.UTF_8)));
    }

    private static JsonElement sorted(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject result = new JsonObject();
            value.getAsJsonObject().keySet().stream().sorted().forEach(key -> result.add(key, sorted(value.getAsJsonObject().get(key))));
            return result;
        }
        if (value.isJsonArray()) {
            var result = new com.google.gson.JsonArray();
            value.getAsJsonArray().forEach(item -> result.add(sorted(item)));
            return result;
        }
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    @FunctionalInterface
    private interface CheckedOperation {
        void run() throws Exception;
    }
}
