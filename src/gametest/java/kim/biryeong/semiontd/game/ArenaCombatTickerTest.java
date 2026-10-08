package kim.biryeong.semiontd.game;

import com.mojang.authlib.GameProfile;
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
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeLevelConfig;
import xyz.nucleoid.fantasy.RuntimeLevelHandle;
import xyz.nucleoid.fantasy.util.VoidChunkGenerator;

public final class ArenaCombatTickerTest {
    private static Fixture observedFixture;
    private static List<ServerLevel> observedOrder;
    private static boolean stopDuringWorldTick;

    static {
        ServerTickEvents.START_LEVEL_TICK.register(world -> {
            Fixture fixture = observedFixture;
            if (fixture != null && fixture.game.arena().containsWorld(world)) {
                observedOrder.add(world);
                require(fixture.first.asLevel().getGameTime() == fixture.second.asLevel().getGameTime(),
                        "Both arenas must see the same substep clock before either world ticks.");
                require(!ArenaCombatTicker.tick(world.getServer(), fixture.game), "Reentrant world steps must report rejection.");
                if (stopDuringWorldTick) {
                    setPhase(fixture.game, RoundPhase.ROUND_PAYOUT);
                }
            }
        });
    }

    @GameTest
    public void phaseChangeDuringWorldStepReportsIncompleteGroup(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context.getLevel().getServer())) {
            observedFixture = fixture;
            observedOrder = new ArrayList<>();
            stopDuringWorldTick = true;
            require(!ArenaCombatTicker.tick(context.getLevel().getServer(), fixture.game),
                    "A phase change in a world callback must reject the extra logical step.");
            require(observedOrder.size() == 1, "A changed phase must stop before the remaining arena ticks.");
        } finally {
            observedFixture = null;
            observedOrder = null;
            stopDuringWorldTick = false;
        }
        context.succeed();
    }

    @GameTest
    public void missingRequiredWorldRejectsWholeGroupBeforeClockAdvance(GameTestHelper context) {
        Fixture fixture = new Fixture(context.getLevel().getServer());
        fixture.second.unload();
        awaitWorldUnloaded(context, fixture, 0);
    }

    private static void awaitWorldUnloaded(GameTestHelper context, Fixture fixture, int attempts) {
        context.runAfterDelay(1, () -> {
            MinecraftServer server = context.getLevel().getServer();
            try {
                ServerLevel missing = fixture.second.asLevel();
                if (server.getLevel(missing.dimension()) == missing) {
                    require(attempts < 40, "The isolated arena world did not unload.");
                    awaitWorldUnloaded(context, fixture, attempts + 1);
                    return;
                }
                long before = fixture.first.asLevel().getGameTime();
                require(!ArenaCombatTicker.tick(server, fixture.game), "A missing required world must reject the group.");
                require(fixture.first.asLevel().getGameTime() == before,
                        "Rejected preflight must not advance a remaining arena's clock.");
                fixture.close();
                context.succeed();
            } catch (Throwable failure) {
                fixture.close();
                context.fail(Component.literal(failure.getMessage() == null ? failure.toString() : failure.getMessage()));
            }
        });
    }

    @GameTest
    public void extraTicksAdvanceOnlyArenaClocksInServerOrder(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        try (Fixture fixture = new Fixture(server)) {
            ServerLevel first = fixture.first.asLevel();
            ServerLevel second = fixture.second.asLevel();
            long vanillaTime = server.overworld().getGameTime();
            List<ServerLevel> expected = new ArrayList<>();
            for (ServerLevel world : server.getAllLevels()) {
                if (fixture.game.arena().containsWorld(world)) {
                    expected.add(world);
                }
            }
            observedOrder = new ArrayList<>();
            observedFixture = fixture;
            require(ArenaCombatTicker.tick(server, fixture.game), "A complete arena group must report success.");
            require(observedOrder.equals(expected), "Each live arena must tick once in server iteration order.");
            require(first.getGameTime() == vanillaTime + 1 && second.getGameTime() == vanillaTime + 1,
                    "Fantasy arena clocks must advance despite inheriting overworld time.");
            require(server.overworld().getGameTime() == vanillaTime, "The overworld must not accelerate.");
            setPhase(fixture.game, RoundPhase.PREPARE_AND_SUMMON);
            require(!ArenaCombatTicker.tick(server, fixture.game), "Preparation must reject extra simulation.");
            require(first.getGameTime() == vanillaTime + 1, "Preparation must retain the clock offset without ticking.");
            setPhase(fixture.game, RoundPhase.LANE_WAVE);
            require(ArenaCombatTicker.tick(server, fixture.game), "A later combat phase must allow a complete group.");
            require(first.getGameTime() == vanillaTime + 2, "A later combat phase must continue the same clock.");
            ArenaCombatClock.remove(first);
            require(first.getGameTime() == vanillaTime && second.getGameTime() == vanillaTime + 2,
                    "Unloaded-world clock cleanup must not clear another arena's offset.");
        } finally {
            observedFixture = null;
            observedOrder = null;
        }
        context.succeed();
    }

    @GameTest
    public void playerSubstepPreservesTeleportButRestoresOrdinaryMovement(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        MinecraftServer server = world.getServer();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "combat-tick-test"), false);
        boolean[] teleport = {true};
        ServerPlayer player = new ServerPlayer(server, world, cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public void doTick() {
                if (teleport[0]) {
                    connection.teleport(4.0, 64.0, 6.0, 0.0F, 0.0F);
                } else {
                    setPos(8.0, 64.0, 9.0);
                }
            }
        };
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player, cookie) {
            @Override
            public void send(Packet<?> packet) {
            }
        };
        ArenaCombatTicker.tickPlayer(player);
        require(player.getX() == 4.0 && player.getZ() == 6.0, "Extra player simulation must preserve a same-world teleport.");
        teleport[0] = false;
        ArenaCombatTicker.tickPlayer(player);
        require(player.getX() == 4.0 && player.getZ() == 6.0, "Ordinary simulated movement must retain vanilla position restoration.");
        context.succeed();
    }

    @GameTest
    public void extraTicksKeepAlternatingGoalsEffectsAndDelayedHits(GameTestHelper context) {
        Fixture fixture = new Fixture(context.getLevel().getServer());
        ServerLevel world = fixture.first.asLevel();
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
                if (!((ServerLevel) monster.level()).areEntitiesActuallyLoadedAndTicking(monster.chunkPosition())
                        || monster.tickCount < 2) {
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
                context.fail(Component.literal(failure.getMessage() == null ? failure.toString() : failure.getMessage()));
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
        private final RuntimeLevelHandle first;
        private final RuntimeLevelHandle second;
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
                    TeamId.RED, new TeamArena(TeamId.RED, () -> {}, first.asLevel(), null),
                    TeamId.BLUE, new TeamArena(TeamId.BLUE, () -> {}, second.asLevel(), null))));
            setPhase(game, RoundPhase.LANE_WAVE);
        }

        private static RuntimeLevelHandle createWorld(MinecraftServer server) {
            RuntimeLevelHandle handle = Fantasy.get(server).openTemporaryLevel(
                    Identifier.fromNamespaceAndPath("semion-td", "combat_tick_test_" + UUID.randomUUID()),
                    new RuntimeLevelConfig().setGenerator(new VoidChunkGenerator(server)).setShouldTickTime(false)
                            .setGameTime(server.overworld().getGameTime()));
            handle.setTickWhenEmpty(true);
            return handle;
        }

        @Override
        public void close() {
            if (first.asLevel().getServer().getLevel(first.asLevel().dimension()) == first.asLevel()) {
                first.unload();
            }
            if (second.asLevel().getServer().getLevel(second.asLevel().dimension()) == second.asLevel()) {
                second.unload();
            }
        }
    }
}
