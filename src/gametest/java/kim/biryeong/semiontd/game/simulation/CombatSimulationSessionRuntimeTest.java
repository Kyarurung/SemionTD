package kim.biryeong.semiontd.game.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterAttackStyle;
import kim.biryeong.semiontd.entity.monster.MonsterOrigin;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.ArenaCombatClock;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.gametest.SyntheticArenaFactory;
import kim.biryeong.semiontd.util.Scheduler;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeLevelConfig;
import xyz.nucleoid.fantasy.RuntimeLevelHandle;
import xyz.nucleoid.fantasy.util.VoidChunkGenerator;

public final class CombatSimulationSessionRuntimeTest {
    @GameTest(maxTicks = 240)
    public void realSessionCommitsTimersInputsDeathsAndWorldTasksAcrossTwentyAcceptedFrames(GameTestHelper context) {
        run(context, 2);
    }

    @GameTest(maxTicks = 240)
    public void oneStepFramesRetainTheSameNativeFortyStepTimerAndDeathTrace(GameTestHelper context) {
        run(context, 1);
    }

    private static void run(GameTestHelper context, int stepsPerFrame) {
        var server = context.getLevel().getServer();
        RuntimeLevelHandle handle = Fantasy.get(server).openTemporaryLevel(
                Identifier.fromNamespaceAndPath("semion-td-gametest", "combat_session_" + UUID.randomUUID()),
                new RuntimeLevelConfig().setGenerator(new VoidChunkGenerator(server))
                        .setGameRule(GameRules.SPAWN_MOBS, false));
        handle.setTickWhenEmpty(true);
        ServerLevel world = handle.asLevel();
        world.setChunkForced(0, 0, true);
        world.getChunk(0, 0);
        context.startSequence().thenWaitUntil(() -> context.assertTrue(
                world.areEntitiesActuallyLoadedAndTicking(new ChunkPos(0, 0)),
                "Session fixture chunk must be ticking")).thenExecute(() -> {
                    try {
                        Fixture fixture = new Fixture(world, handle, stepsPerFrame);
                        drive(context, fixture);
                    } catch (Throwable failure) {
                        handle.unload();
                        context.fail(Component.literal("Session fixture failed: " + failure));
                    }
                });
    }

    private static void drive(GameTestHelper context, Fixture fixture) {
        try {
            fixture.session.endFrame();
            if (fixture.session.isClosed()) {
                throw new AssertionError("The active fixture wave must retain its simulation owner");
            }
            if (fixture.session.idle()) {
                if (fixture.frames == 40 / fixture.stepsPerFrame) {
                    fixture.verify();
                    fixture.close();
                    context.succeed();
                    return;
                }
                fixture.frames++;
                fixture.session.beginFrame(fixture.stepsPerFrame);
                if (fixture.frames == 1) {
                    fixture.session.input(() -> fixture.inputs.add("first:" + fixture.game.currentTick()));
                    fixture.session.input(() -> fixture.inputs.add("second:" + fixture.game.currentTick()));
                }
            }
            if (++fixture.physicalFrames >= 210) {
                throw new AssertionError("The cooperative worker must finish its bounded accepted frames");
            }
            context.runAfterDelay(1, () -> drive(context, fixture));
        } catch (Throwable failure) {
            fixture.close();
            context.fail(Component.literal("Native combat session failed: " + failure));
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final ServerLevel world;
        private final RuntimeLevelHandle handle;
        private final SemionGame game;
        private final CombatSimulationSession session;
        private final SemionMonsterEntity survivor;
        private final SemionMonsterEntity corpse;
        private final long startTime;
        private final int startAge;
        private final int stepsPerFrame;
        private final List<Long> steps = new ArrayList<>();
        private final List<Long> hits = new ArrayList<>();
        private final List<Long> tasks = new ArrayList<>();
        private final List<String> inputs = new ArrayList<>();
        private int frames;
        private int physicalFrames;
        private boolean closed;

        @SuppressWarnings("unchecked")
        private Fixture(ServerLevel world, RuntimeLevelHandle handle, int stepsPerFrame) throws ReflectiveOperationException {
            this.world = world;
            this.handle = handle;
            this.stepsPerFrame = stepsPerFrame;
            for (int x = 0; x < 8; x++) {
                for (int z = 0; z < 8; z++) {
                    world.setBlock(new BlockPos(x, 64, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(),
                    SyntheticArenaFactory.create(world, new BlockPos(0, 64, 0)));
            game.teams().get(TeamId.RED).activate();
            game.teams().get(TeamId.BLUE).activate();
            PlayerLane lane = new PlayerLane(TeamId.RED, 1, UUID.randomUUID(), world,
                    game.arena().lane(TeamId.RED, 1).orElseThrow());
            game.teams().get(TeamId.RED).laneGroup().addLane(lane);
            survivor = monster(world, new Vec3(1.5, 65, 1.5), "session_survivor");
            lane.activeMonsters().add(survivor.runtimeMonster());
            corpse = monster(world, new Vec3(6.5, 65, 1.5), "session_corpse");
            corpse.setHealth(0.0F);
            var phase = SemionGame.class.getDeclaredField("phase");
            phase.setAccessible(true);
            phase.set(game, RoundPhase.LANE_WAVE);
            var waveTeams = SemionGame.class.getDeclaredField("currentWaveTeamIds");
            waveTeams.setAccessible(true);
            ((Set<TeamId>) waveTeams.get(game)).add(TeamId.RED);
            startTime = world.getGameTime();
            startAge = survivor.tickCount;
            survivor.applyTimedEffect(TimedEffectType.MONSTER_ATTACK_DAMAGE_BONUS, 0.25, 40);
            survivor.setAttackStyle(new MonsterAttackStyle() {
                @Override
                public int hitDelayTicks() { return 3; }

                @Override
                public void hit(SemionMonsterEntity attacker, LivingEntity target) {
                    hits.add(attacker.combatTickCount() - startAge);
                }
            });
            survivor.startAttack(survivor);
            Scheduler.INSTANCE.submit(world, ignored -> tasks.add(world.getGameTime() - startTime), 2);
            session = new CombatSimulationSession(world.getServer(), game);
            session.setStepObserver(tick -> {
                require(world.getGameTime() == startTime + tick, "Each native callback uses its logical world clock");
                require(game.currentTick() == tick, "Game work runs once per committed logical step");
                require(session.entityTick(survivor) == startAge + tick, "Native actor age advances once per logical step");
                require(survivor.activeTimedEffectTicks(TimedEffectType.MONSTER_ATTACK_DAMAGE_BONUS) == 40 - tick,
                        "Owned timed effects must advance exactly once per native logical actor tick");
                if (tick < 20) {
                    require(!corpse.isRemoved(), "A dead actor must retain its native death animation duration");
                } else {
                    require(corpse.isRemoved(), "Dead actor ambient ticks must complete native removal at tick twenty");
                }
                steps.add(tick);
            });
        }

        private void verify() {
            require(frames == 40 / stepsPerFrame && session.logicalTickCount() == 40 && steps.size() == 40,
                    "Both one-step and two-step accepted frames must commit the same forty logical steps");
            require(inputs.equals(List.of("first:1", "second:1")), "Pending-step inputs must run in order at the next boundary");
            require(tasks.equals(List.of(1L)), "Legacy two-tick world tasks must run once at the first logical deadline");
            require(hits.equals(List.of(3L)), "Pending attack must resolve once at its native logical age");
            require(world.getGameTime() == startTime + 40, "Outside the scope the world exposes the latest completed clock");
            require(survivor.activeTimedEffectTicks(TimedEffectType.MONSTER_ATTACK_DAMAGE_BONUS) == 0,
                    "Forty logical ticks expire the original effect");
            session.input(() -> survivor.startAttack(survivor));
            survivor.tickCount = startAge + 200;
            session.close();
            require(session.isClosed() && !CombatSimulationRuntime.controls(world), "Idle handoff must unregister the owner");
            require(survivor.tickCount == startAge + 40, "Handoff must adopt completed age even behind physical frame age");
            require(world.getGameTime() == startTime + 40, "Handoff must also retain the completed world clock");
            world.tickNonPassenger(survivor);
            world.tickNonPassenger(survivor);
            require(hits.equals(List.of(3L)), "Pending hit must remain pending through resumed ages forty-one and forty-two");
            world.tickNonPassenger(survivor);
            require(hits.equals(List.of(3L, 43L)), "Pending hit must resolve once at its original age forty-three deadline");
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                var phase = SemionGame.class.getDeclaredField("phase");
                phase.setAccessible(true);
                phase.set(game, RoundPhase.ENDED);
                session.close();
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            } finally {
                survivor.discard();
                corpse.discard();
                game.teams().values().forEach(team -> team.closeRuntime());
                ArenaCombatClock.remove(world);
                handle.unload();
            }
        }
    }

    private static SemionMonsterEntity monster(ServerLevel world, Vec3 position, String id) {
        Monster monster = new Monster(id, TeamId.RED, 1, Optional.empty(), Optional.empty(),
                1_000, 0, 0, AttackKind.MELEE, "minecraft:zombie", 0);
        monster.setOrigin(MonsterOrigin.NATURAL_WAVE);
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, world);
        entity.configureFrom(monster, null);
        entity.setNoAi(true);
        entity.setNoGravity(true);
        entity.setPos(position);
        world.addFreshEntity(entity);
        monster.markMinecraftEntitySpawned(entity.getId(), position.x, position.y, position.z);
        return entity;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
