package kim.biryeong.semiontd.game.simulation;

import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.CombatSpeedConfig;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterOrigin;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.ArenaCombatClock;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.entity.simulation.WorkerPhysics;
import kim.biryeong.semiontd.gametest.SyntheticArenaFactory;
import kim.biryeong.semiontd.tower.engineer.EngineerCircuitWorld;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeLevelConfig;
import xyz.nucleoid.fantasy.RuntimeLevelHandle;
import xyz.nucleoid.fantasy.util.VoidChunkGenerator;

public final class SemionGameManagerSimulationFaultTest {
    @GameTest(maxTicks = 160)
    public void startInputFaultStopsOnlyTheOwnedMatchAndRetainsItsFirstCause(GameTestHelper context) {
        run(context, true);
    }

    @GameTest(maxTicks = 160)
    public void endPresentationFaultStopsOnlyTheOwnedMatchWithoutRepeatingPresentation(GameTestHelper context) {
        run(context, false);
    }

    @GameTest(maxTicks = 200)
    public void physicalBalanceTicksCoalesceUntilTheAcceptedLogicalBoundary(GameTestHelper context) {
        run(context, null);
    }

    @GameTest(maxTicks = 200)
    public void fullInputCapacityRejectsSafelyAndRetriesBalanceAtTheNextAvailableFrame(GameTestHelper context) {
        run(context, null, true);
    }

    private static void run(GameTestHelper context, Boolean start) {
        run(context, start, false);
    }

    private static void run(GameTestHelper context, Boolean start, boolean capacity) {
        MinecraftServer server = context.getLevel().getServer();
        RuntimeLevelHandle handle = Fantasy.get(server).openTemporaryLevel(
                Identifier.fromNamespaceAndPath("semion-td-gametest", "manager_fault_" + UUID.randomUUID()),
                new RuntimeLevelConfig().setGenerator(new VoidChunkGenerator(server))
                        .setGameRule(GameRules.SPAWN_MOBS, false));
        handle.setTickWhenEmpty(true);
        ServerLevel world = handle.asLevel();
        world.setChunkForced(0, 0, true);
        world.getChunk(0, 0);
        context.startSequence().thenWaitUntil(() -> context.assertTrue(
                world.areEntitiesActuallyLoadedAndTicking(new ChunkPos(0, 0)), "Manager fault fixture chunk must be ticking"))
                .thenExecute(() -> {
                    try {
                        Fixture fixture = new Fixture(world, handle, !Boolean.TRUE.equals(start));
                        if (start == null) {
                            fixture.manager.beginCombatTick(server);
                            waitForBalanceWorker(context, fixture, 0, capacity);
                        } else if (!start) {
                            fixture.manager.beginCombatTick(server);
                            waitForEndFaultWorker(context, fixture, 0);
                        } else {
                            try (fixture) {
                                fixture.verify(start);
                                context.succeed();
                            }
                        }
                    } catch (Throwable failure) {
                        handle.unload();
                        context.fail(Component.literal("Manager synchronous fault containment failed: " + failure));
                    }
                });
    }

    private static void waitForEndFaultWorker(GameTestHelper context, Fixture fixture, int elapsed) {
        try {
            if (!fixture.workerEntered.get()) {
                require(elapsed < 120, "The native prefix must reach the held worker before END presentation fails");
                context.runAfterDelay(1, () -> waitForEndFaultWorker(context, fixture, elapsed + 1));
                return;
            }
            try (fixture) {
                fixture.verify(false);
                context.succeed();
            }
        } catch (Throwable failure) {
            closeFailure(context, fixture, failure);
        }
    }

    private static void waitForBalanceWorker(GameTestHelper context, Fixture fixture, int elapsed, boolean capacity) {
        try {
            if (!fixture.workerEntered.get()) {
                require(elapsed < 120, "The native actor must reach the held worker calculation");
                context.runAfterDelay(1, () -> waitForBalanceWorker(context, fixture, elapsed + 1, capacity));
                return;
            }
            int[] boundaries = {0};
            set(fixture.manager, "balanceBoundary", (java.util.function.Consumer<kim.biryeong.semiontd.balance.manage.BalanceChangeService.Boundary>)
                    boundary -> boundaries[0]++);
            int[] accepted = {0};
            int[] rejected = {0};
            if (capacity) {
                for (int input = 0; input < 256; input++) {
                    fixture.session.input(() -> accepted[0]++);
                }
                boolean strictRejection = false;
                try {
                    fixture.session.input(() -> rejected[0]++);
                } catch (RejectedExecutionException full) {
                    strictRejection = true;
                }
                require(strictRejection, "Direct session input must retain its explicit capacity rejection contract");
                require(CombatSimulationRuntime.input(fixture.world, () -> rejected[0]++),
                        "Rejected external input must be consumed rather than leaking into synchronous gameplay fallback");
            }
            for (int frame = 0; frame < 20; frame++) {
                fixture.manager.tick(fixture.server);
            }
            require(boundaries[0] == 0 && fixture.game.currentTick() == 0,
                    "Physical END callbacks cannot apply balance while a native actor prefix is held");
            var inputs = (Deque<?>) get(get(fixture.session, "coordinator"), "inputs");
            if (capacity) {
                require(inputs.size() == 256 && accepted[0] == 0 && rejected[0] == 0,
                        "A full queue cannot apply, replace or add rejected inputs during the held native prefix");
                require(Boolean.FALSE.equals(get(fixture.manager, "balanceTickPending")) && fixture.session.failure() == null,
                        "Capacity rejection must reset the pending balance flag for retry without faulting the match");
            } else {
                require(inputs.size() == 1, "Repeated physical balance ticks must coalesce into exactly one accepted input");
            }
            fixture.releaseWorker.release();
            if (capacity) {
                waitForCapacityBoundary(context, fixture, boundaries, accepted, rejected, 0);
            } else {
                waitForBalanceBoundary(context, fixture, boundaries, 0);
            }
        } catch (Throwable failure) {
            closeFailure(context, fixture, failure);
        }
    }

    private static void waitForCapacityBoundary(GameTestHelper context, Fixture fixture, int[] boundaries,
            int[] accepted, int[] rejected, int elapsed) {
        try {
            if (!fixture.session.idle()) {
                require(elapsed < 120 && fixture.session.failure() == null, "The full input queue must drain after the accepted logical step");
                context.runAfterDelay(1, () -> waitForCapacityBoundary(context, fixture, boundaries, accepted, rejected, elapsed + 1));
                return;
            }
            require(accepted[0] == 256 && rejected[0] == 0 && boundaries[0] == 0,
                    "All accepted inputs must execute once and rejected external inputs must never mutate gameplay");
            require(fixture.game.currentTick() == 1 && fixture.session.logicalTickCount() == 1,
                    "Draining capacity cannot replay or add a logical combat step");
            fixture.manager.tick(fixture.server);
            require(boundaries[0] == 1 && Boolean.FALSE.equals(get(fixture.manager, "balanceTickPending")),
                    "The next physical frame with available capacity must apply its deferred balance callback once");
            require(fixture.session.failure() == null && rejected[0] == 0 && fixture.game.currentTick() == 1,
                    "Balance retry must preserve a healthy owner without synchronous gameplay fallback");
            fixture.close();
            context.succeed();
        } catch (Throwable failure) {
            closeFailure(context, fixture, failure);
        }
    }

    private static void waitForBalanceBoundary(GameTestHelper context, Fixture fixture, int[] boundaries, int elapsed) {
        try {
            if (boundaries[0] == 0) {
                require(elapsed < 120 && fixture.session.failure() == null, "The accepted step must finish before applying its queued balance input");
                context.runAfterDelay(1, () -> waitForBalanceBoundary(context, fixture, boundaries, elapsed + 1));
                return;
            }
            require(boundaries[0] == 1 && fixture.game.currentTick() == 1 && fixture.session.logicalTickCount() == 1,
                    "The one coalesced balance callback must execute after the first committed logical step");
            fixture.close();
            context.succeed();
        } catch (Throwable failure) {
            closeFailure(context, fixture, failure);
        }
    }

    private static void closeFailure(GameTestHelper context, Fixture fixture, Throwable failure) {
        try {
            fixture.close();
        } catch (Throwable cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
        context.fail(Component.literal("Manager balance boundary failed: " + failure));
    }

    private static final class Fixture implements AutoCloseable {
        private final MinecraftServer server;
        private final ServerLevel world;
        private final RuntimeLevelHandle handle;
        private final float originalTickRate;
        private final SemionGameManager manager = new SemionGameManager();
        private final SemionGame game;
        private final CountingMonster actor;
        private final CombatSimulationSession session;
        private final long startTime;
        private final AtomicBoolean workerEntered = new AtomicBoolean();
        private final Semaphore releaseWorker = new Semaphore(0);

        @SuppressWarnings("unchecked")
        private Fixture(ServerLevel world, RuntimeLevelHandle handle, boolean holdWorker) throws ReflectiveOperationException {
            this.server = world.getServer();
            this.world = world;
            this.handle = handle;
            originalTickRate = server.tickRateManager().tickrate();
            for (int x = 0; x < 8; x++) {
                for (int z = 0; z < 8; z++) {
                    world.setBlock(new BlockPos(x, 64, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(),
                    SyntheticArenaFactory.create(world, new BlockPos(0, 64, 0)));
            set(game, "phase", RoundPhase.LANE_WAVE);
            Monster monster = new Monster("manager_fault_actor", kim.biryeong.semiontd.game.TeamId.RED, 1,
                    Optional.empty(), Optional.empty(), 1_000, 0, 0, AttackKind.MELEE, "minecraft:zombie", 0);
            monster.setOrigin(MonsterOrigin.NATURAL_WAVE);
            actor = new CountingMonster(world);
            actor.configureFrom(monster, null);
            actor.setNoGravity(true);
            actor.setNoAi(false);
            actor.setPos(new Vec3(1.5, 65, 1.5));
            actor.setRemainingFireTicks(20);
            world.addFreshEntity(actor);
            monster.markMinecraftEntitySpawned(actor.getId(), actor.getX(), actor.getY(), actor.getZ());
            if (holdWorker) {
                game.teams().get(TeamId.RED).activate();
                game.teams().get(TeamId.BLUE).activate();
                PlayerLane lane = new PlayerLane(TeamId.RED, 1, UUID.randomUUID(), world, game.arena().lane(TeamId.RED, 1).orElseThrow());
                game.teams().get(TeamId.RED).laneGroup().addLane(lane);
                lane.activeMonsters().add(monster);
                ((Set<TeamId>) get(game, "currentWaveTeamIds")).add(TeamId.RED);
            }
            startTime = world.getGameTime();
            AtomicBoolean entered = workerEntered;
            Semaphore gate = releaseWorker;
            Function<WorkerPhysics.Input, WorkerPhysics.Result> calculation = input -> {
                entered.set(true);
                try {
                    if (!gate.tryAcquire(20, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out releasing the test worker");
                    }
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(failure);
                }
                return WorkerPhysics.advance(input);
            };
            session = holdWorker ? new CombatSimulationSession(server, game, calculation)
                    : new CombatSimulationSession(server, game);
            set(manager, "activeGame", game);
            set(manager, "combatSimulation", session);
            set(manager, "simulationGame", game);
            set(manager, "simulationRound", game.currentRound());
            set(manager, "combatSpeedConfig", new CombatSpeedConfig(true, 40.0F, 50.0, CombatSpeedConfig.Mode.SIMULATION));
            set(manager, "previousCombatSpeedPhase", RoundPhase.LANE_WAVE);
            set(manager, "managesTickRate", true);
        }

        @SuppressWarnings("unchecked")
        private void verify(boolean start) throws ReflectiveOperationException {
            int previousAiTicks = actor.aiTicks;
            var expected = new IllegalStateException(start ? "injected START input failure" : "injected END presentation failure");
            int[] invocations = {0};
            Runnable throwing = () -> {
                invocations[0]++;
                actor.setRemainingFireTicks(19);
                throw expected;
            };
            require(session.failure() == null, "The manager must encounter the first failure during its actual callback");
            if (start) {
                Object coordinator = get(session, "coordinator");
                ((Deque<Runnable>) get(coordinator, "inputs")).addLast(throwing);
            } else {
                ((Map<Entity, Runnable>) get(session, "completedVisuals")).put(actor, throwing);
            }
            server.tickRateManager().setTickRate(40.0F);
            if (start) {
                manager.beginCombatTick(server);
            } else {
                manager.tick(server);
            }
            require(session.failure() == expected && manager.combatSimulationFailed(), "The original synchronous fault must be retained");
            require(Boolean.TRUE.equals(get(manager, "combatSimulationFailureReported")), "The manager must report the fault at its callback boundary");
            require(server.tickRateManager().tickrate() == 20.0F, "The rest of the server must return to twenty ticks per second");
            require(get(manager, "combatSimulation") == session && manager.activeGame().orElseThrow() == game,
                    "Containment cannot dispose or replace the affected match");
            require(!session.isClosed() && CombatSimulationRuntime.controls(world)
                    && EngineerCircuitWorld.current(world) != null, "Containment must retain NPC and circuit ownership");
            for (int frame = 0; frame < 20; frame++) {
                manager.beginCombatTick(server);
                manager.tick(server);
                world.tickNonPassenger(actor);
            }
            require(invocations[0] == 1 && session.failure() == expected, "Later frames cannot retry the failed input or presentation");
            require(get(manager, "combatSimulation") == session && !session.isClosed(), "Later frames must retain the stopped session");
            require(game.currentTick() == 0 && session.logicalTickCount() == 0 && world.getGameTime() == startTime,
                    "Stopped match frames cannot advance game work or completed clocks");
            require(actor.aiTicks == previousAiTicks && actor.getRemainingFireTicks() == 19,
                    "Physical frames cannot fall back to native AI or ambient work in the stopped match");
            session.close();
            session.close();
            require(session.isClosed() && !CombatSimulationRuntime.controls(world)
                    && EngineerCircuitWorld.current(world) == null, "Explicit disposal must release retained resources without repeating the fault");
            require(invocations[0] == 1, "Explicit disposal cannot retry failed presentation");
            world.tickNonPassenger(actor);
            require(actor.aiTicks == previousAiTicks + 1 && actor.getRemainingFireTicks() == 18, "Native work may resume after explicit disposal");
        }

        @Override
        public void close() throws ReflectiveOperationException {
            try {
                releaseWorker.release();
                set(game, "phase", RoundPhase.ENDED);
                session.close();
                actor.discard();
                set(manager, "combatSimulation", null);
                set(manager, "simulationGame", null);
                set(manager, "simulationRound", 0);
                set(manager, "activeGame", null);
                game.close();
            } finally {
                server.tickRateManager().setTickRate(originalTickRate);
                ArenaCombatClock.remove(world);
                handle.unload();
            }
        }
    }

    private static final class CountingMonster extends SemionMonsterEntity {
        private int aiTicks;

        private CountingMonster(ServerLevel world) { super(SemionEntityTypes.MONSTER, world); }

        @Override
        protected void customServerAiStep(ServerLevel world) {
            aiTicks++;
            super.customServerAiStep(world);
        }
    }

    private static Object get(Object target, String name) throws ReflectiveOperationException {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set(Object target, String name, Object value) throws ReflectiveOperationException {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
