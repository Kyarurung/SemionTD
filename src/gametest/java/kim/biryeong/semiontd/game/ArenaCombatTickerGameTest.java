package kim.biryeong.semiontd.game;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.MonsterAttackStyle;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.map.GameArena;
import kim.biryeong.semiontd.map.TeamArena;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.fantasy.RuntimeWorldHandle;
import xyz.nucleoid.fantasy.util.VoidChunkGenerator;

public final class ArenaCombatTickerGameTest {
    private static Fixture observedFixture;
    private static List<ServerLevel> observedOrder;

    static {
        ServerTickEvents.START_WORLD_TICK.register(world -> {
            Fixture fixture = observedFixture;
            if (fixture != null && fixture.game.arena().containsWorld(world)) {
                observedOrder.add(world);
                require(fixture.first.asWorld().getGameTime() == fixture.second.asWorld().getGameTime(),
                        "Both arenas must see the same substep clock before either world ticks.");
                ArenaCombatTicker.tick(world.getServer(), fixture.game);
            }
        });
    }

    @GameTest
    public void extraTicksAdvanceOnlyArenaClocksInServerOrder(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        try (Fixture fixture = new Fixture(server)) {
            ServerLevel first = fixture.first.asWorld();
            ServerLevel second = fixture.second.asWorld();
            long vanillaTime = server.overworld().getGameTime();
            List<ServerLevel> expected = new ArrayList<>();
            for (ServerLevel world : server.getAllLevels()) {
                if (fixture.game.arena().containsWorld(world)) {
                    expected.add(world);
                }
            }
            observedOrder = new ArrayList<>();
            observedFixture = fixture;
            ArenaCombatTicker.tick(server, fixture.game);
            require(observedOrder.equals(expected), "Each live arena must tick once in server iteration order.");
            require(first.getGameTime() == vanillaTime + 1 && second.getGameTime() == vanillaTime + 1,
                    "Fantasy arena clocks must advance despite inheriting overworld time.");
            require(server.overworld().getGameTime() == vanillaTime, "The overworld must not accelerate.");
            setPhase(fixture.game, RoundPhase.PREPARE_AND_SUMMON);
            ArenaCombatTicker.tick(server, fixture.game);
            require(first.getGameTime() == vanillaTime + 1, "Preparation must retain the clock offset without ticking.");
            setPhase(fixture.game, RoundPhase.LANE_WAVE);
            ArenaCombatTicker.tick(server, fixture.game);
            require(first.getGameTime() == vanillaTime + 2, "A later combat phase must continue the same clock.");
        } finally {
            observedFixture = null;
            observedOrder = null;
        }
        context.succeed();
    }

    @GameTest
    public void extraTicksKeepAlternatingGoalsEffectsAndDelayedHits(GameTestHelper context) {
        Fixture fixture = new Fixture(context.getLevel().getServer());
        ServerLevel world = fixture.first.asWorld();
        world.setChunkForced(0, 0, true);
        world.getChunk(0, 0);
        CountingMonster monster = new CountingMonster(world);
        monster.setPos(4.0, 64.0, 4.0);
        monster.setNoGravity(true);
        world.addFreshEntity(monster);
        awaitEntityTicking(context, fixture, monster, 0);
    }

    private static void awaitEntityTicking(GameTestHelper context, Fixture fixture, CountingMonster monster, int attempts) {
        context.runAfterDelay(1, () -> {
            try {
                if (!((ServerLevel) monster.level()).isPositionEntityTicking(monster.blockPosition()) || monster.tickCount < 2) {
                    require(attempts < 40, "The isolated arena entity chunk did not become tickable.");
                    awaitEntityTicking(context, fixture, monster, attempts + 1);
                    return;
                }
                int beforeTicks = monster.tickCount;
                int beforeGoals = monster.goalTicks;
                monster.applyTimedEffect(TimedEffectType.MONSTER_DAMAGE_REDUCTION, 0.25, 3);
                monster.setAttackStyle(new MonsterAttackStyle() {
                    @Override
                    public int hitDelayTicks() {
                        return 3;
                    }

                    @Override
                    public void hit(SemionMonsterEntity attacker, LivingEntity target) {
                        monster.hits++;
                    }
                });
                monster.startAttack(monster);
                ArenaCombatTicker.tick(monster.level().getServer(), fixture.game);
                ArenaCombatTicker.tick(monster.level().getServer(), fixture.game);
                require(monster.tickCount == beforeTicks + 2, "Two substeps must execute two complete entity ticks.");
                require(monster.goalTicks == beforeGoals + 1, "Default goals must retain alternating-tick cadence.");
                require(monster.activeTimedEffectTicks(TimedEffectType.MONSTER_DAMAGE_REDUCTION) == 1,
                        "Timed effects must advance on every entity substep.");
                require(monster.hits == 0, "A three-tick delayed attack must not hit early.");
                ArenaCombatTicker.tick(monster.level().getServer(), fixture.game);
                require(monster.hits == 1 && !monster.hasPendingHit(), "Delayed attacks must use the advanced entity clock.");
                require(monster.activeTimedEffectTicks(TimedEffectType.MONSTER_DAMAGE_REDUCTION) == 0,
                        "Effects must expire on the same third substep.");
                fixture.close();
                context.succeed();
            } catch (Throwable failure) {
                fixture.close();
                throw failure;
            }
        });
    }

    private static void setPhase(SemionGame game, RoundPhase phase) {
        try {
            Field field = SemionGame.class.getDeclaredField("phase");
            field.setAccessible(true);
            field.set(game, phase);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class CountingMonster extends SemionMonsterEntity {
        private int goalTicks;
        private int hits;

        private CountingMonster(ServerLevel world) {
            super(SemionEntityTypes.MONSTER, world);
        }

        @Override
        protected void registerGoals() {
            goalSelector.addGoal(0, new Goal() {
                @Override
                public boolean canUse() {
                    return true;
                }

                @Override
                public void tick() {
                    goalTicks++;
                }
            });
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final RuntimeWorldHandle first;
        private final RuntimeWorldHandle second;
        private final SemionGame game;

        private Fixture(MinecraftServer server) {
            first = createWorld(server);
            try {
                second = createWorld(server);
            } catch (RuntimeException exception) {
                first.unload();
                throw exception;
            }
            game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), new GameArena(Map.of(
                    TeamId.RED, new TeamArena(TeamId.RED, () -> {}, first.asWorld(), null),
                    TeamId.BLUE, new TeamArena(TeamId.BLUE, () -> {}, second.asWorld(), null))));
            setPhase(game, RoundPhase.LANE_WAVE);
        }

        private static RuntimeWorldHandle createWorld(MinecraftServer server) {
            RuntimeWorldHandle handle = Fantasy.get(server).openTemporaryWorld(
                    ResourceLocation.fromNamespaceAndPath("semion-td", "combat_tick_test_" + UUID.randomUUID()),
                    new RuntimeWorldConfig().setGenerator(new VoidChunkGenerator(server)).setShouldTickTime(false));
            handle.setTickWhenEmpty(true);
            return handle;
        }

        @Override
        public void close() {
            first.unload();
            second.unload();
        }
    }
}
