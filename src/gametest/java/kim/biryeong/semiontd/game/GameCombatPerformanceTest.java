package kim.biryeong.semiontd.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.augment.AugmentSnapshot;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.MonsterScalingConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectSet;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.goal.ApplyTowerTimedEffectGoal;
import kim.biryeong.semiontd.entity.goal.CooldownAbilityGoal;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.gametest.RuntimeArenaFixture;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.summon.SummonContext;
import kim.biryeong.semiontd.summon.SummonMonsterType;
import kim.biryeong.semiontd.summon.SummonRegistry;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerRoundMetricsTracker;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import kim.biryeong.semiontd.tower.engineer.EngineerCircuitTower;
import kim.biryeong.semiontd.tower.engineer.EngineerGolemTower;
import kim.biryeong.semiontd.tower.engineer.EngineerPressStates;
import kim.biryeong.semiontd.tower.engineer.EngineerTowers;
import kim.biryeong.semiontd.tower.magicschool.MagicSchoolCurriculum;
import kim.biryeong.semiontd.tower.magicschool.MagicSchoolSpell;
import kim.biryeong.semiontd.tower.magicschool.MagicSchoolTowers;
import kim.biryeong.semiontd.tower.magicschool.MagicSchoolWizardTower;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class GameCombatPerformanceTest implements RuntimeArenaFixture {
    private static final String FILTER = "semion-td-gametest:game_combat_performance_test_measures_fixed_combat";
    private static final Gson JSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
    private static final long SEED = 826031L;
    private static final int ROUND = 16;
    private static final int WARMUP_TICKS = 100;
    private static final int MEASURE_TICKS = 400;
    private static final int TOTAL_TICKS = WARMUP_TICKS + MEASURE_TICKS;
    private static final int SPAWN_INTERVAL = 37;
    private static final int SPAWN_BATCH = 2;
    private static final List<String> LIMITS = List.of(
            "EMPIRICAL_FIXED_SCENARIO; EXACT_CROSS_RUN_OUTCOME_COMPARISON_REQUIRED",
            "ENTITY_UUID_AND_ID_AND_CONSTRUCTOR_RANDOM_STATE_NOT_CONTROLLED",
            "POST_CONSTRUCTION_ENTITY_RNG_AND_ROTATION_RESET_ONLY",
            "UUID_TARGET_ORDER_AND_ENTITY_ID_AI_TICK_PARITY_CAN_CHANGE_RESULTS",
            "DIRECT_PRODUCTION_SUMMON_SPAWN; NO_PURCHASE_OR_GAME_MANAGER_ROUND_FLOW",
            "NO_SEMION_GAME_RANDOM_OR_THREAD_LOCAL_RANDOM_SEED_CONTROL",
            "MAIN_THREAD_START_TO_END_EVENT_SCOPE; WORKER_THREADS_EXCLUDED",
            "CPU_ALLOCATION_AND_MSPT_EXCLUDE_END_EVENT_CAPTURE; WALL_GC_AND_LATER_GC_PRESSURE_INCLUDE_IT",
            "COOLDOWN_RESETS_ARE_OBSERVED_CASTS; NOT_INSTRUMENTED_METHOD_CALL_COUNTS",
            "TWO_CORRELATED_LANES; NO_CLIENTS; NOT_FULL_MATCH_OR_BALANCE_ACCEPTANCE");
    private static final Method UNLOCK = method(MagicSchoolCurriculum.class, "unlockSpellTier",
            UUID.class, int.class, PlayerEconomy.class);
    private static final Method SELECT = method(MagicSchoolWizardTower.class, "selectSpell", MagicSchoolSpell.class);
    private static final Method GOLEM_ENTITY = method(EngineerGolemTower.class, "golemEntity", PlayerLane.class);
    private static final Field GOLEM_TARGET = field(EngineerGolemTower.class, "targetPlate");
    private static final Field GOLEM_LAST = field(EngineerGolemTower.class, "lastPressedPlate");
    private static final Field GOLEM_PRESSES = field(EngineerGolemTower.class, "pressesThisWave");
    private static final Field GOLEM_COOLDOWNS = field(EngineerGolemTower.class, "plateCooldowns");
    private static final Field ABILITIES = field(SemionMonsterEntity.class, "summonAbilityGoals");
    private static final Field REMAINING = field(CooldownAbilityGoal.class, "remainingCooldownTicks");
    private static final Field COOLDOWN = field(CooldownAbilityGoal.class, "cooldownTicks");
    private static final Field RETRY = field(CooldownAbilityGoal.class, "retryDelayTicks");
    private static final Field MAX_TARGETS = field(ApplyTowerTimedEffectGoal.class, "maxTargets");
    private static final Field ROUND_TRACKERS = field(PlayerLane.class, "roundTowerTrackers");
    private static boolean hooksRegistered;
    private static Run active;

    @GameTest(maxTicks = 1600)
    public void measuresFixedCombat(GameTestHelper context) {
        if (!Boolean.getBoolean("semiontd.combatProfile")) {
            SemionTd.LOGGER.info("COMBAT_PROFILE_NOT_RUN: opt-in absent; no combat or performance evidence.");
            context.succeed();
            return;
        }
        if (!FILTER.equals(System.getProperty("fabric-api.gametest.filter"))
                || !(context.getLevel().getServer() instanceof GameTestServer)) {
            context.fail(Component.literal("Combat profile requires its exact isolated filter and GameTestServer."));
            return;
        }
        require(active == null, "A combat profile is already active.");
        registerHooks();
        new Run(context).start();
    }

    private static void registerHooks() {
        if (hooksRegistered) return;
        hooksRegistered = true;
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            Run run = active;
            if (run != null && run.server == server) run.safely(run::beforeTick);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Run run = active;
            if (run != null && run.server == server) run.safely(run::afterTick);
        });
    }

    private static final class Board {
        final int index;
        final BlockPos base;
        final SemionPlayer player;
        final PlayerLane lane;
        final List<Tower> towers = new ArrayList<>();
        final List<Mob> entities = new ArrayList<>();
        final List<Spawn> spawns = new ArrayList<>();
        EngineerGolemTower golem;
        int warmupPresses;
        double warmupDamage;
        double warmupTaken;
        long measuredDebuffTicks;
        long measuredProtectedDamageTicks;
        double previousTaken;

        Board(int index, BlockPos base, SemionPlayer player, PlayerLane lane) {
            this.index = index;
            this.base = base;
            this.player = player;
            this.lane = lane;
        }
    }

    private static final class Spawn {
        final String key;
        final Monster monster;
        final SemionMonsterEntity entity;
        final CooldownAbilityGoal ability;
        final int cooldown;
        final int retry;
        int previousCooldown;
        long successfulResets;
        long warmupSuccessfulResets;
        long retryResets;

        Spawn(String key, Monster monster, SemionMonsterEntity entity) {
            this.key = key;
            this.monster = monster;
            this.entity = entity;
            List<?> abilities = (List<?>) read(ABILITIES, entity);
            require(abilities.size() == 1 && abilities.getFirst() instanceof ApplyTowerTimedEffectGoal,
                    "Vindicator must install its production single-target debuff goal.");
            ability = (CooldownAbilityGoal) abilities.getFirst();
            require(integer(MAX_TARGETS, ability) == 1, "The target-selection profile requires maxTargets=1.");
            cooldown = integer(COOLDOWN, ability);
            retry = integer(RETRY, ability);
            require(cooldown > 1 && retry > 1 && cooldown != retry, "Cooldown reset observations must be unambiguous.");
        }

        void observe() {
            int remaining = integer(REMAINING, ability);
            if (previousCooldown <= 1 && remaining == cooldown) successfulResets++;
            if (previousCooldown <= 1 && remaining == retry) retryResets++;
            previousCooldown = remaining;
        }
    }

    private static final class Run {
        final GameTestHelper context;
        final ServerLevel level;
        final MinecraftServer server;
        final BlockPos origin;
        final EconomyService economy = new EconomyService(EconomyConfig.defaultConfig());
        final MonsterScalingConfig scaling = MonsterScalingConfig.defaultConfig();
        final SummonMonsterType summon = SummonRegistry.find("vindicator").orElseThrow();
        final List<Board> boards = new ArrayList<>();
        final Map<UUID, SemionPlayer> players = new LinkedHashMap<>();
        final Map<Entity, String> entityKeys = new IdentityHashMap<>();
        final Map<String, String> sourceKeys = new LinkedHashMap<>();
        final List<Object> registrations = new ArrayList<>();
        final List<Object> outcomes = new ArrayList<>(TOTAL_TICKS + 1);
        final List<Object> diagnostics = new ArrayList<>(TOTAL_TICKS + 1);
        final Map<BlockPos, BlockState> originalBlocks = new LinkedHashMap<>();
        final Set<Long> forcedChunks = new HashSet<>();
        final Set<Long> arenaChunks = new HashSet<>();
        final long[] cpu = new long[MEASURE_TICKS];
        final long[] allocated = new long[MEASURE_TICKS];
        final long[] mspt = new long[MEASURE_TICKS];
        final com.sun.management.ThreadMXBean threads;
        final float originalTickRate;
        final Path output;
        Object input;
        Object environment;
        long threadId;
        long cpuBefore;
        long allocatedBefore;
        long gcCountBefore;
        long gcMillisBefore;
        long measurementStart;
        long measurementEnd;
        long gcCountAfter;
        long gcMillisAfter;
        int tick;
        int readinessTicks;
        boolean finishing;
        boolean wrote;
        boolean blocksModified;

        Run(GameTestHelper context) {
            this.context = context;
            level = context.getLevel();
            server = level.getServer();
            origin = new BlockPos(8192, 240, 8192);
            require(ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean,
                    "This profile requires HotSpot thread CPU and allocation counters.");
            threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
            originalTickRate = server.tickRateManager().tickrate();
            String variant = System.getProperty("semiontd.profileVariant", "unspecified").replaceAll("[^A-Za-z0-9._-]", "_");
            output = Path.of(System.getProperty("semiontd.combatProfileOutput",
                    "build/perf/combat-" + variant + ".jsonl")).toAbsolutePath().normalize();
        }

        void start() {
            safely(() -> {
                require(threads.isCurrentThreadCpuTimeSupported() && threads.isThreadAllocatedMemorySupported(),
                        "CPU and allocation counters must both be supported.");
                if (!threads.isThreadCpuTimeEnabled()) threads.setThreadCpuTimeEnabled(true);
                if (!threads.isThreadAllocatedMemoryEnabled()) threads.setThreadAllocatedMemoryEnabled(true);
                server.tickRateManager().setTickRate(20.0F);
                loadArena();
                waitForArena();
            });
        }

        void loadArena() {
            for (int index = 0; index < 2; index++) {
                BlockPos base = origin.offset(index * 32, 1, 0);
                for (int x = base.getX() >> 4; x <= (base.getX() + 17) >> 4; x++) {
                    for (int z = base.getZ() >> 4; z <= (base.getZ() + 31) >> 4; z++) {
                        long key = ChunkPos.pack(x, z);
                        arenaChunks.add(key);
                        if (level.setChunkForced(x, z, true)) forcedChunks.add(key);
                        level.getChunk(x, z);
                    }
                }
            }
        }

        void waitForArena() {
            if (arenaChunks.stream().anyMatch(key -> !level.areEntitiesActuallyLoadedAndTicking(
                    new ChunkPos(ChunkPos.getX(key), ChunkPos.getZ(key))))) {
                require(++readinessTicks < 600, "Forced arena chunks did not become entity-ticking.");
                context.runAfterDelay(1, () -> safely(this::waitForArena));
                return;
            }
            buildFloor();
            prepare();
        }

        void buildFloor() {
            for (int index = 0; index < 2; index++) {
                BlockPos base = origin.offset(index * 32, 1, 0);
                AABB bounds = new AABB(base.getX(), base.getY(), base.getZ(),
                        base.getX() + 18, base.getY() + 7, base.getZ() + 32);
                require(level.getEntitiesOfClass(Entity.class, bounds).isEmpty(),
                        "Combat arena collides with existing entities: " + bounds);
                for (int x = 0; x < 18; x++) for (int y = 0; y < 7; y++) for (int z = 0; z < 32; z++) {
                    BlockPos pos = base.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    require(state.isAir() && state.getFluidState().isEmpty() && level.getBlockEntity(pos) == null,
                            "Combat arena collides with an existing block: " + pos);
                    originalBlocks.put(pos, state);
                }
            }
            blocksModified = true;
            for (int index = 0; index < 2; index++) {
                BlockPos base = origin.offset(index * 32, 1, 0);
                for (int x = 0; x < 18; x++) for (int z = 0; z < 32; z++) {
                    level.setBlockAndUpdate(base.offset(x, 0, z), Blocks.STONE.defaultBlockState());
                }
            }
        }

        void prepare() {
            for (int index = 0; index < 2; index++) {
                UUID owner = UUID.nameUUIDFromBytes(("combat-profile-" + index).getBytes(StandardCharsets.UTF_8));
                EngineerPressStates.clear(owner);
                MagicSchoolCurriculum.clear(owner);
                var money = new PlayerEconomy(EconomyConfig.defaultConfig());
                money.addDiamond(100_000L);
                TeamId team = index == 0 ? TeamId.RED : TeamId.BLUE;
                var player = new SemionPlayer(owner, "combat-profile-" + index, team, index + 1, money);
                player.assignJob(JobRegistry.find(Identifier.fromNamespaceAndPath(SemionTd.MOD_ID,
                        index == 0 ? "engineer_towers" : "magic_school")).orElseThrow());
                players.put(owner, player);
                BlockPos base = origin.offset(index * 32, 1, 0);
                var lane = new PlayerLane(team, index + 1, owner, level, layout(base, index + 1));
                var board = new Board(index, base, player, lane);
                boards.add(board);
                AreaEffectLaneIndex.register(lane);
                lane.assignAugmentSnapshot(AugmentSnapshot.none());
                if (index == 0) engineer(board); else school(board);
            }
            for (Board board : boards) board.lane.markWaveStarted(ROUND);
            verifyAuras();
            input = map("schema", 2, "scenario", "engineer-and-protego-production-vindicator-v2",
                    "seed", SEED, "round", ROUND, "tickRate", 20, "warmupTicks", WARMUP_TICKS,
                    "measuredTicks", MEASURE_TICKS, "spawnIntervalTicks", SPAWN_INTERVAL,
                    "spawnBatchPerLane", SPAWN_BATCH, "worldSeed", level.getSeed(),
                    "origin", position(origin), "dimension", level.dimension().identifier().toString(),
                    "difficulty", level.getDifficulty().toString(),
                    "weather", map("raining", level.isRaining(), "thundering", level.isThundering(),
                            "rainLevelBits", Float.floatToRawIntBits(level.getRainLevel(1.0F)),
                            "thunderLevelBits", Float.floatToRawIntBits(level.getThunderLevel(1.0F))),
                    "originalBlockCount", originalBlocks.size(),
                    "originalBlocksSha256", digest(canonical(originalBlocks.entrySet().stream()
                            .map(entry -> map("position", position(entry.getKey()), "state", entry.getValue().toString())).toList())),
                    "summon", JSON.toJsonTree(summon),
                    "monsterScaling", scaling, "economy", EconomyConfig.defaultConfig(),
                    "towerBalance", TowerBalanceRuntime.current(), "augment", "NONE",
                    "boards", boards.stream().map(board -> map("lane", board.index, "team", board.player.teamId().name(), "job",
                            board.index == 0 ? "engineer_towers" : "magic_school",
                            "towers", board.towers.stream().map(tower -> map("type", tower.type().id(),
                                    "position", tower.originalPosition(),
                                    "spell", tower instanceof MagicSchoolWizardTower wizard
                                            ? wizard.selectedSpell().id() : null)).toList())).toList());
            environment = map("variant", System.getProperty("semiontd.profileVariant", "unspecified"),
                    "java", System.getProperty("java.runtime.version"), "vm", System.getProperty("java.vm.name"),
                    "os", System.getProperty("os.name"), "arch", System.getProperty("os.arch"),
                    "processors", Runtime.getRuntime().availableProcessors(),
                    "maxHeapBytes", Runtime.getRuntime().maxMemory(),
                    "heap", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().toString(),
                    "jvmArguments", ManagementFactory.getRuntimeMXBean().getInputArguments(),
                    "collectors", ManagementFactory.getGarbageCollectorMXBeans().stream().map(bean -> bean.getName()).toList(),
                    "gameTime", level.getGameTime(), "difficulty", level.getDifficulty().toString(),
                    "raining", level.isRaining(), "thundering", level.isThundering(),
                    "originalTickRate", originalTickRate, "forcedArenaChunks", arenaChunks.size(),
                    "measurementScope", "MAIN_SERVER_THREAD_START_EVENT_TO_END_EVENT_BEFORE_CAPTURE",
                    "limits", LIMITS);
            outcomes.add(capture());
            active = this;
        }

        void engineer(Board board) {
            board.golem = (EngineerGolemTower) add(board, EngineerTowers.COPPER_GOLEM.id(), 5, 12);
            EngineerTowers.PlateKind[] kinds = {EngineerTowers.PlateKind.GOLD, EngineerTowers.PlateKind.IRON,
                    EngineerTowers.PlateKind.STONE, EngineerTowers.PlateKind.WOOD};
            for (int i = 0; i < kinds.length; i++) add(board, EngineerTowers.plate(kinds[i]).id(), 6 + i * 2, 12);
            for (int x = 6; x <= 12; x++) add(board, EngineerTowers.REDSTONE_DUST.id(), x, 13);
            add(board, EngineerTowers.REDSTONE_DUST.id(), 9, 14);
            add(board, EngineerTowers.REDSTONE_DUST.id(), 11, 14);
            add(board, EngineerTowers.trap(EngineerTowers.TrapKind.DISPENSER, 1).id(), 9, 15);
            add(board, EngineerTowers.trap(EngineerTowers.TrapKind.SLIME, 1).id(), 11, 15);
        }

        void school(Board board) {
            for (int tier = 2; tier <= 4; tier++) require(invoke(UNLOCK, null, board.player.uuid(), tier,
                    board.player.economy()) == MagicSchoolCurriculum.PurchaseResult.PURCHASED,
                    "School spell tier must be purchased.");
            int[][] slots = {{9, 12}, {7, 14}, {9, 14}, {11, 14}, {9, 16}};
            for (int slot = 0; slot < slots.length; slot++) {
                var wizard = (MagicSchoolWizardTower) add(board, MagicSchoolTowers.BRAVE_ARCHWIZARD.id(),
                        slots[slot][0], slots[slot][1]);
                require(Boolean.TRUE.equals(invoke(SELECT, wizard,
                        slot == 0 ? MagicSchoolSpell.PROTEGO : MagicSchoolSpell.PROTEGO_MAXIMA)),
                        "Production school tower must accept its selected spell.");
                sourceKeys.put(Identifier.fromNamespaceAndPath(SemionTd.MOD_ID,
                        "magic_school_protego_" + wizard.logicalId()).toString(), "school-source-" + slot);
            }
        }

        Tower add(Board board, String id, int x, int z) {
            GridPosition position = new GridPosition(board.base.getX() + x, board.base.getY(), board.base.getZ() + z);
            Tower tower = ProductionTowerCatalog.find(id).orElseThrow().create(board.player.uuid(),
                    board.player.teamId(), board.index + 1, position);
            board.lane.addTower(tower);
            String key = "lane-" + board.index + "/tower-" + board.towers.size();
            Mob entity = tower instanceof EntityBackedTower backed ? backed.runtimeEntity(board.lane).orElseThrow()
                    : tower instanceof EngineerGolemTower golem ? (Mob) invoke(GOLEM_ENTITY, golem, board.lane) : null;
            board.towers.add(tower);
            board.entities.add(entity);
            if (entity != null) register(entity, key, tower.logicalId().toString());
            return tower;
        }

        void register(Mob entity, String key, String logicalId) {
            require(entity != null && entity.tickCount == 0, "Entity RNG must be reset before its first entity tick.");
            long seed = SEED ^ ((long) key.hashCode() << 32) ^ key.length();
            registrations.add(map("key", key, "spawnTick", tick, "uuid", entity.getUUID().toString(),
                    "logicalUuid", logicalId, "id", entity.getId(), "idParity", entity.getId() & 1,
                    "seed", seed, "constructorYawBits", Float.floatToRawIntBits(entity.getYRot()),
                    "initialEntityTick", entity.tickCount, "entityType", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()));
            entityKeys.put(entity, key);
            entity.getRandom().setSeed(seed);
            entity.setYRot(0.0F);
            entity.setXRot(0.0F);
            entity.setYHeadRot(0.0F);
            entity.setYBodyRot(0.0F);
            entity.yRotO = 0.0F;
            entity.xRotO = 0.0F;
            entity.yHeadRotO = 0.0F;
            entity.yBodyRotO = 0.0F;
        }

        void spawn(Board board) {
            Board sender = boards.get(1 - board.index);
            for (int offset = 0; offset < SPAWN_BATCH; offset++) {
                Monster monster = summon.createMonster(new SummonContext(null, sender.player), board.player.teamId(), board.index + 1, ROUND);
                Vec3 position = Vec3.atBottomCenterOf(board.base.offset(9 + offset, 1, 11));
                SemionMonsterEntity entity = board.lane.spawnMonsterAt(monster, position).orElseThrow();
                String key = "lane-" + board.index + "/monster-" + board.spawns.size();
                register(entity, key, monster.logicalId().toString());
                board.spawns.add(new Spawn(key, monster, entity));
            }
        }

        void beforeTick() {
            if (finishing) {
                long[] times = server.getTickTimesNanos();
                mspt[MEASURE_TICKS - 1] = times[Math.floorMod(server.getTickCount() - 1, times.length)];
                finish();
                return;
            }
            require(server.tickRateManager().tickrate() == 20.0F, "Combat tick rate changed from 20.");
            require(threadId == 0 || threadId == Thread.currentThread().threadId(), "Server thread changed.");
            threadId = Thread.currentThread().threadId();
            if (tick >= WARMUP_TICKS + 1) {
                long[] times = server.getTickTimesNanos();
                mspt[tick - WARMUP_TICKS - 1] = times[Math.floorMod(server.getTickCount() - 1, times.length)];
            }
            if (tick == WARMUP_TICKS) {
                gcCountBefore = gc(false);
                gcMillisBefore = gc(true);
                measurementStart = System.nanoTime();
                for (Board board : boards) {
                    board.warmupPresses = EngineerPressStates.count(board.player.uuid());
                    board.warmupDamage = damage(board, false);
                    board.warmupTaken = damage(board, true);
                    for (Spawn spawn : board.spawns) spawn.warmupSuccessfulResets = spawn.successfulResets;
                }
            }
            cpuBefore = threads.getCurrentThreadCpuTime();
            allocatedBefore = threads.getThreadAllocatedBytes(threadId);
            tick++;
            if ((tick - 1) % SPAWN_INTERVAL == 0) for (Board board : boards) spawn(board);
            for (Board board : boards) board.lane.tick(server, economy, players, scaling, tick);
        }

        void afterTick() {
            if (tick <= 0 || finishing) return;
            long cpuAfter = threads.getCurrentThreadCpuTime();
            long allocatedAfter = threads.getThreadAllocatedBytes(threadId);
            require(server.tickRateManager().tickrate() == 20.0F, "Combat tick rate changed during the server tick.");
            if (tick > WARMUP_TICKS) {
                int index = tick - WARMUP_TICKS - 1;
                cpu[index] = cpuAfter - cpuBefore;
                allocated[index] = allocatedAfter - allocatedBefore;
                require(cpuBefore >= 0 && allocatedBefore >= 0 && cpu[index] >= 0 && allocated[index] >= 0,
                        "CPU and allocation measurements must be available.");
            }
            for (Board board : boards) {
                for (Spawn spawn : board.spawns) spawn.observe();
                double taken = damage(board, true);
                boolean fourAuras = board.entities.stream().filter(SemionTowerEntity.class::isInstance)
                        .map(SemionTowerEntity.class::cast).anyMatch(entity -> auraCount(entity) == 4);
                if (tick > WARMUP_TICKS) {
                    if (board.entities.stream().filter(SemionTowerEntity.class::isInstance)
                            .map(SemionTowerEntity.class::cast).anyMatch(entity -> entity.activeEffectMagnitude(
                                    TimedEffectType.TOWER_ATTACK_SPEED_REDUCTION) > 0)) board.measuredDebuffTicks++;
                    if (fourAuras && taken > board.previousTaken) board.measuredProtectedDamageTicks++;
                }
                board.previousTaken = taken;
            }
            outcomes.add(capture());
            if (tick == TOTAL_TICKS) {
                measurementEnd = System.nanoTime();
                gcCountAfter = gc(false);
                gcMillisAfter = gc(true);
                finishing = true;
            }
        }

        Object capture() {
            Object outcome = map("tick", tick, "lanes", boards.stream().map(board -> {
                List<Object> towers = new ArrayList<>();
                for (int slot = 0; slot < board.towers.size(); slot++) {
                    Tower tower = board.towers.get(slot);
                    TowerRoundMetricsTracker tracker = tower.roundMetricsTracker();
                    towers.add(map("key", "lane-" + board.index + "/tower-" + slot, "type", tower.type().id(),
                            "healthBits", bits(tower.health()), "position", tower.position(),
                            "entity", entityState(board.entities.get(slot)),
                            "roundMetrics", metricState(tracker == null ? null : tracker.snapshot()),
                            "circuit", tower instanceof EngineerCircuitTower circuit
                                    ? level.getBlockState(circuit.circuitPosition()).toString() : null));
                }
                List<Object> monsters = board.spawns.stream().map(spawn -> (Object) map("key", spawn.key,
                        "type", spawn.monster.id(), "healthBits", bits(spawn.monster.health()),
                        "state", spawn.monster.state().name(), "progressBits", bits(spawn.monster.laneProgress()),
                        "activeTicks", spawn.monster.activeTicks(), "rewardGranted", spawn.monster.rewardGranted(),
                        "entity", entityState(spawn.entity), "abilityCooldown", spawn.previousCooldown,
                        "observedSuccessfulCasts", spawn.successfulResets, "observedRetryCasts", spawn.retryResets)).toList();
                List<Object> metrics = board.lane.roundTowerMetrics().stream()
                        .sorted(Comparator.comparing(TowerRoundMetricsSnapshot::towerTypeId))
                        .map(value -> (Object) map("type", value.towerTypeId(), "physicalBits", bits(value.physicalDamageDealt()),
                                "magicBits", bits(value.magicDamageDealt()), "takenBits", bits(value.damageTaken()),
                                "healingBits", bits(value.healingDone()), "kills", value.killCount(),
                                "alive", value.endAliveCount(), "deaths", value.deathCount(),
                                "firstCombatTick", value.firstCombatTick(), "lastCombatTick", value.lastCombatTick(),
                                "survivalTicks", value.survivalTicks())).toList();
                return map("lane", board.index, "towers", towers, "monsters", monsters, "metrics", metrics,
                        "cleared", board.lane.clearedThisRound(), "broken", board.lane.laneDefenseBroken(),
                        "leaks", board.lane.leakedCountThisRound(), "leakedThreatBits", bits(board.lane.leakedThreatThisRound()),
                        "rewardedKills", board.player.matchStats().monsterKills(),
                        "golem", board.golem == null ? null : golemState(board));
            }).toList());
            diagnostics.add(map("schema", 1, "lanes", boards.stream()
                    .map(board -> map("lane", board.index, "metricsTrackerOrder", metricTrackerOrder(board))).toList()));
            return outcome;
        }

        List<String> metricTrackerOrder(Board board) {
            Map<TowerRoundMetricsTracker, String> keys = new IdentityHashMap<>();
            for (int slot = 0; slot < board.towers.size(); slot++) {
                TowerRoundMetricsTracker tracker = board.towers.get(slot).roundMetricsTracker();
                if (tracker != null) {
                    require(keys.put(tracker, "lane-" + board.index + "/tower-" + slot) == null,
                            "Fixed combat towers must have distinct metric trackers.");
                }
            }
            List<String> order = new ArrayList<>();
            for (Object value : (Set<?>) read(ROUND_TRACKERS, board.lane)) {
                require(value instanceof TowerRoundMetricsTracker && keys.containsKey(value),
                        "Every lane metric tracker must belong to a fixed combat tower.");
                order.add(keys.get(value));
            }
            return order;
        }

        Object golemState(Board board) {
            Map<?, ?> cooldowns = (Map<?, ?>) read(GOLEM_COOLDOWNS, board.golem);
            List<Object> sorted = cooldowns.entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                    .map(entry -> (Object) map("position", entry.getKey(), "remainingTicks", entry.getValue())).toList();
            return map("target", read(GOLEM_TARGET, board.golem), "lastPressed", read(GOLEM_LAST, board.golem),
                    "pressesThisWave", integer(GOLEM_PRESSES, board.golem),
                    "pressesThisMatch", EngineerPressStates.count(board.player.uuid()), "cooldowns", sorted);
        }

        Object entityState(Mob entity) {
            if (entity == null) return null;
            LivingEntity target = entity instanceof SemionTowerEntity tower ? tower.currentAttackTarget() : entity.getTarget();
            List<Object> effects = entity instanceof SemionTowerEntity tower ? tower.effectSnapshot().stream()
                    .map(effect -> (Object) map("type", effect.type().name(),
                            "source", sourceKeys.getOrDefault(effect.sourceId(), effect.sourceId()),
                            "magnitudeBits", bits(effect.magnitude()), "remainingTicks", effect.remainingTicks(),
                            "persistent", effect.persistent()))
                    .sorted(Comparator.comparing(GameCombatPerformanceTest::canonical)).toList() : List.of();
            require(target == null || entityKeys.containsKey(target), "Combat target escaped the fixed scenario.");
            Vec3 motion = entity.getDeltaMovement();
            return map("key", entityKeys.get(entity), "age", entity.tickCount, "alive", entity.isAlive(),
                    "removed", entity.isRemoved(), "noAi", entity.isNoAi(),
                    "healthBits", Float.floatToRawIntBits(entity.getHealth()),
                    "maxHealthBits", Float.floatToRawIntBits(entity.getMaxHealth()),
                    "xBits", bits(entity.getX()), "yBits", bits(entity.getY()), "zBits", bits(entity.getZ()),
                    "vxBits", bits(motion.x), "vyBits", bits(motion.y), "vzBits", bits(motion.z),
                    "yawBits", Float.floatToRawIntBits(entity.getYRot()), "pitchBits", Float.floatToRawIntBits(entity.getXRot()),
                    "headYawBits", Float.floatToRawIntBits(entity.getYHeadRot()),
                    "target", entityKeys.get(target), "effects", effects);
        }

        void verifyAuras() {
            Board school = boards.get(1);
            SemionTowerEntity recipient = (SemionTowerEntity) school.entities.getFirst();
            List<TimedEffectSet.Snapshot> effects = recipient.effectSnapshot().stream()
                    .filter(effect -> effect.type() == TimedEffectType.TOWER_PROTEGO_MAXIMA_AURA).toList();
            Set<String> expected = new HashSet<>();
            for (int slot = 1; slot <= 4; slot++) expected.add(Identifier.fromNamespaceAndPath(SemionTd.MOD_ID,
                    "magic_school_protego_" + school.towers.get(slot).logicalId()).toString());
            require(effects.size() == 4 && effects.stream().allMatch(effect -> effect.persistent()
                            && effect.remainingTicks() == null && bits(effect.magnitude())
                            == bits(MagicSchoolSpell.PROTEGO_MAXIMA.value("auraReduction")))
                            && effects.stream().map(TimedEffectSet.Snapshot::sourceId).collect(java.util.stream.Collectors.toSet()).equals(expected),
                    "Exactly four production Maxima sources must reach the Protego recipient.");
            require(MagicSchoolSpell.PROTEGO_MAXIMA.ticks("maxAuraStacks") == 3,
                    "The production aura profile must select three of four sources.");
            require(recipient.activeEffectMagnitude(TimedEffectType.TOWER_PROTEGO) > 0,
                    "Protego self protection must be active.");
        }

        void finish() {
            active = null;
            require(outcomes.size() == TOTAL_TICKS + 1, "Every initial and post-tick outcome must be recorded.");
            require(java.util.Arrays.stream(mspt).allMatch(value -> value > 0), "Every measured completed MSPT must be present.");
            Board engineer = boards.get(0);
            Board school = boards.get(1);
            require(EngineerPressStates.count(engineer.player.uuid()) - engineer.warmupPresses >= 2,
                    "Measured Engineer combat must include at least two real golem plate presses.");
            require(damage(engineer, false) > engineer.warmupDamage, "Engineer traps must deal damage during measurement.");
            require(damage(school, false) > school.warmupDamage, "School AI must deal damage during measurement.");
            require(damage(school, true) > school.warmupTaken && school.measuredProtectedDamageTicks > 0,
                    "Protected school towers must take actual damage during measurement.");
            require(school.measuredDebuffTicks > 0, "Single-target production debuffs must affect school towers during measurement.");
            require(school.spawns.stream().mapToLong(spawn -> spawn.successfulResets - spawn.warmupSuccessfulResets).sum() > 0,
                    "At least one production single-target ability cast must be observed during measurement.");
            write("OBSERVED_VALID_PENDING_EXTERNAL_EQUIVALENCE", null);
            cleanup();
            context.succeed();
        }

        void write(String status, String failure) {
            if (wrote && !"FIXTURE_FAILED".equals(status)) return;
            List<String> lines = new ArrayList<>(outcomes.size() + registrations.size() + 3);
            String inputDigest = input == null ? null : digest(canonical(input));
            lines.add(canonical(map("kind", "manifest", "schema", 1, "inputSha256", inputDigest,
                    "comparisonInput", input, "environment", environment, "limits", LIMITS,
                    "externalAcceptance", "Require identical inputSha256 and every tick/outcomeSha256; fail on first mismatch; never compare performance after divergence.")));
            for (Object registration : registrations) lines.add(canonical(map("kind", "entity_initial", "state", registration)));
            for (int index = 0; index < outcomes.size(); index++) {
                Object outcome = outcomes.get(index);
                lines.add(canonical(map("kind", "tick", "outcomeSha256", digest(canonical(outcome)),
                        "outcome", outcome, "diagnostics", diagnostics.get(index))));
            }
            Object summary = map("kind", "summary", "status", status, "failure", failure, "ticks", tick,
                    "warmupTicks", WARMUP_TICKS, "measuredTicks", MEASURE_TICKS, "inputSha256", inputDigest,
                    "serverThreadCpuNanos", cpu, "serverThreadAllocatedBytes", allocated,
                    "completedServerTickNanos", mspt, "gcCount", gcCountAfter - gcCountBefore,
                    "gcMillis", gcMillisAfter - gcMillisBefore,
                    "wallSeconds", measurementStart == 0 ? null : (measurementEnd - measurementStart) / 1e9,
                    "cpuStatsNanos", stats(cpu), "allocationStatsBytes", stats(allocated), "msptStatsNanos", stats(mspt),
                    "coverage", boards.stream().map(board -> map("lane", board.index,
                            "measuredDamageBits", bits(damage(board, false) - board.warmupDamage),
                            "measuredDamageTakenBits", bits(damage(board, true) - board.warmupTaken),
                            "measuredDebuffTicks", board.measuredDebuffTicks,
                            "measuredProtectedDamageTicks", board.measuredProtectedDamageTicks,
                            "measuredGolemPresses", EngineerPressStates.count(board.player.uuid()) - board.warmupPresses,
                            "observedSuccessfulCasts", board.spawns.stream().mapToLong(spawn -> spawn.successfulResets).sum(),
                            "measuredSuccessfulCasts", board.spawns.stream().mapToLong(spawn -> spawn.successfulResets - spawn.warmupSuccessfulResets).sum(),
                            "observedRetryCasts", board.spawns.stream().mapToLong(spawn -> spawn.retryResets).sum())).toList());
            lines.add(canonical(summary));
            try {
                Files.createDirectories(output.getParent());
                Files.write(output, lines, StandardCharsets.UTF_8);
                wrote = true;
            } catch (IOException failureToWrite) {
                throw new IllegalStateException("Cannot write combat profile " + output, failureToWrite);
            }
            SemionTd.LOGGER.info("COMBAT_PROFILE_REPORT {}", canonical(map("status", status, "output", output.toString(),
                    "inputSha256", inputDigest, "ticks", tick, "failure", failure, "limits", LIMITS)));
        }

        void safely(Runnable action) {
            try {
                action.run();
            } catch (RuntimeException | AssertionError failure) {
                active = null;
                try { write("FIXTURE_FAILED", failure.toString()); }
                catch (RuntimeException writeFailure) { failure.addSuppressed(writeFailure); }
                try { cleanup(); }
                catch (RuntimeException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
                context.fail(Component.literal("Combat profile failed: " + failure));
            }
        }

        void cleanup() {
            active = null;
            List<Throwable> failures = new ArrayList<>();
            for (Board board : boards) {
                cleanupStep(board.lane::disableMonsters, failures);
                cleanupStep(board.lane::clearTowers, failures);
                cleanupStep(board.lane::clearRoundMonsterMetrics, failures);
                cleanupStep(() -> AreaEffectLaneIndex.unregister(board.lane), failures);
                cleanupStep(() -> EngineerPressStates.clear(board.player.uuid()), failures);
                cleanupStep(() -> MagicSchoolCurriculum.clear(board.player.uuid()), failures);
            }
            if (blocksModified) {
                for (Map.Entry<BlockPos, BlockState> entry : originalBlocks.entrySet()) {
                    cleanupStep(() -> level.setBlock(entry.getKey(), entry.getValue(),
                            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE), failures);
                }
                for (Map.Entry<BlockPos, BlockState> entry : originalBlocks.entrySet()) {
                    cleanupStep(() -> require(level.getBlockState(entry.getKey()).equals(entry.getValue()),
                            "Combat arena original block was not restored: " + entry.getKey()), failures);
                }
            }
            originalBlocks.clear();
            blocksModified = false;
            for (long key : forcedChunks) cleanupStep(() -> level.setChunkForced(ChunkPos.getX(key), ChunkPos.getZ(key), false), failures);
            forcedChunks.clear();
            cleanupStep(() -> server.tickRateManager().setTickRate(originalTickRate), failures);
            if (!failures.isEmpty()) {
                IllegalStateException failure = new IllegalStateException("Combat profile cleanup failed.");
                failures.forEach(failure::addSuppressed);
                throw failure;
            }
        }
    }

    private static void cleanupStep(Runnable action, List<Throwable> failures) {
        try { action.run(); }
        catch (RuntimeException | AssertionError failure) { failures.add(failure); }
    }
    private static LaneRegionLayout layout(BlockPos base, int laneId) {
        return new LaneRegionLayout(laneId, Vec3.atBottomCenterOf(base.offset(9, 1, 11)),
                List.of(Vec3.atBottomCenterOf(base.offset(9, 1, 20))),
                Vec3.atBottomCenterOf(base.offset(9, 1, 30)),
                BlockBounds.of(base.offset(0, 1, 0), base.offset(17, 6, 31)),
                List.of(new GridPosition(base.getX() + 9, base.getY(), base.getZ() + 25)));
    }

    private static Object metricState(TowerRoundMetricsSnapshot value) {
        if (value == null) return null;
        return map("type", value.towerTypeId(), "sampleCount", value.sampleCount(), "startCount", value.startCount(),
                "physicalBits", bits(value.physicalDamageDealt()), "magicBits", bits(value.magicDamageDealt()),
                "takenBits", bits(value.damageTaken()), "healingBits", bits(value.healingDone()),
                "kills", value.killCount(), "alive", value.endAliveCount(), "deaths", value.deathCount(),
                "firstCombatTick", value.firstCombatTick(), "lastCombatTick", value.lastCombatTick(),
                "survivalTicks", value.survivalTicks(),
                "waveStartMaxHealthBits", value.waveStartMaxHealth() == null ? null : bits(value.waveStartMaxHealth()),
                "enemyHpDamageBits", value.enemyHpDamage() == null ? null : bits(value.enemyHpDamage()),
                "augmentSpecialDamageBits", value.augmentSpecialDamageDealt() == null
                        ? null : bits(value.augmentSpecialDamageDealt()));
    }

    private static long auraCount(SemionTowerEntity entity) {
        return entity.effectSnapshot().stream().filter(effect -> effect.type() == TimedEffectType.TOWER_PROTEGO_MAXIMA_AURA
                && effect.persistent()).map(TimedEffectSet.Snapshot::sourceId).distinct().count();
    }

    private static double damage(Board board, boolean taken) {
        return board.lane.roundTowerMetrics().stream().mapToDouble(value -> taken ? value.damageTaken() : value.damageDealt()).sum();
    }

    private static long gc(boolean millis) {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(bean -> millis ? bean.getCollectionTime() : bean.getCollectionCount()).filter(value -> value >= 0).sum();
    }

    private static Object stats(long[] values) {
        long[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        return map("samples", values.length, "mean", java.util.Arrays.stream(values).average().orElse(0),
                "p50", sorted[sorted.length / 2], "p95", sorted[(int) Math.ceil(sorted.length * .95) - 1],
                "p99", sorted[(int) Math.ceil(sorted.length * .99) - 1], "max", sorted[sorted.length - 1]);
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) result.put((String) pairs[index], pairs[index + 1]);
        return result;
    }

    private static Object position(BlockPos pos) { return List.of(pos.getX(), pos.getY(), pos.getZ()); }
    private static long bits(double value) { return Double.doubleToRawLongBits(value); }
    private static String canonical(Object value) { return JSON.toJson(sorted(JSON.toJsonTree(value))); }

    private static JsonElement sorted(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject result = new JsonObject();
            value.getAsJsonObject().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> result.add(entry.getKey(), sorted(entry.getValue())));
            return result;
        }
        if (value.isJsonArray()) {
            JsonArray result = new JsonArray();
            value.getAsJsonArray().forEach(entry -> result.add(sorted(entry)));
            return result;
        }
        return value;
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static Method method(Class<?> owner, String name, Class<?>... arguments) {
        try {
            Method method = owner.getDeclaredMethod(name, arguments);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static Object invoke(Method method, Object target, Object... arguments) {
        try { return method.invoke(target, arguments); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    private static Object read(Field field, Object target) {
        try { return field.get(target); }
        catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
    }

    private static int integer(Field field, Object target) {
        try { return field.getInt(target); }
        catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
