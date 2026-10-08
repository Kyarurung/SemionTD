package kim.biryeong.semiontd.gametest;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.RoundWaveConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.config.WaveMonsterEntry;
import kim.biryeong.semiontd.config.WaveSpawnMode;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterDimensions;
import kim.biryeong.semiontd.entity.monster.MonsterOrigin;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.CombatStepRunner;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.PlayerRoundMetricsSnapshot;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TowerRoundMetricsSnapshot;
import kim.biryeong.semiontd.test.tower.TestTower;
import kim.biryeong.semiontd.trait.TraitLoadout;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

public final class CombatStepTest implements RuntimeArenaFixture {
    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void pairedStepsPreserveSpawnsTimedDamageGasAndTowerMetrics(GameTestHelper context) {
        for (int interval : new int[] {1, 3}) {
            List<StepSnapshot> reference = simulate(context, interval, false);
            List<StepSnapshot> accelerated = simulate(context, interval, true);
            equal(reference, accelerated, "Every logical state must match sequential world/game stepping");
            equal(1, accelerated.get(1).monsters().getFirst().age(), "First spawn must tick between the two game steps");
            equal(0, accelerated.getFirst().monsters().getFirst().age(), "A new spawn must not tick before its first world step");
            if (accelerated.get(1).monsters().getFirst().health() >= 100.0) {
                throw new AssertionError("Ignite must deal damage in the extra entity step");
            }
            equal(accelerated.getFirst().gas() + 7, accelerated.get(19).gas(), "Twenty logical ticks produce one gas payment");
            equal(20L, accelerated.getLast().towers().getFirst().survivalTicks(), "Metrics must count logical survival ticks");
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void preparationAndPayoutBoundariesCannotConsumeExtraSteps(GameTestHelper context) {
        SemionGame game = game(context, 1, List.of(entry("single", 1)));
        try {
            set(game, "currentPrepareDurationTicks", 1);
            grouped(game, context, 5, () -> {throw new AssertionError("Prepare entry must not tick extra worlds");});
            equal(RoundPhase.LANE_WAVE, game.phase(), "Preparation must enter the wave");
            equal(0, game.phaseTicks(), "Preparation must not consume a wave step");
            game.tick(context.getLevel().getServer());
            for (PlayerLane lane : lanes(game)) {
                for (Monster monster : lane.activeMonsters()) {
                    entity(context, monster).setHealth(0);
                }
            }
            grouped(game, context, 5, () -> {throw new AssertionError("Cleared wave must stop before extra worlds");});
            equal(RoundPhase.ROUND_PAYOUT, game.phase(), "Wave completion must defer payout");
            equal(1, game.currentRound(), "Wave completion must not advance round twice");
            long minerals = game.players().get(playerId("red")).economy().mineral();
            long income = game.players().get(playerId("red")).economy().income();
            grouped(game, context, 5, () -> {throw new AssertionError("Payout must not tick extra worlds");});
            equal(RoundPhase.PREPARE_AND_SUMMON, game.phase(), "Payout must enter preparation");
            equal(0, game.phaseTicks(), "New preparation must retain its full duration");
            equal(2, game.currentRound(), "Income payout must advance the round once");
            equal(minerals + income, game.players().get(playerId("red")).economy().mineral(), "Income must pay exactly once");
            List<?> metrics = ((Map<?, ?>) get(game, "roundMetricsByPlayer")).get(playerId("red")) instanceof List<?> list ? list : List.of();
            equal(1, metrics.size(), "Round metrics must be captured exactly once");
            equal(2, ((PlayerRoundMetricsSnapshot) metrics.getFirst()).waveDurationTicks(), "Metrics must preserve logical wave duration");
        } finally {
            game.close();
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void finalDefenseThresholdIsReachedOnTheSecondLogicalStep(GameTestHelper context) {
        SemionGame game = game(context, 3, List.of(entry("waiting", 4)));
        try {
            enterWave(context, game);
            set(game, "phaseTicks", SemionGame.DEFAULT_WAVE_FINAL_DEFENSE_TICKS - 2);
            grouped(game, context, 2, () -> {
                equal(SemionGame.DEFAULT_WAVE_FINAL_DEFENSE_TICKS - 1, game.phaseTicks(), "First step must precede final defense");
                equal(1, lanes(game).getFirst().activeMonsters().size(), "First step must only spawn one queued monster");
            });
            equal(SemionGame.DEFAULT_WAVE_FINAL_DEFENSE_TICKS, game.phaseTicks(), "Second step must reach final defense exactly");
            equal(4, lanes(game).getFirst().activeMonsters().size(), "Final defense must release the remaining queue once");
        } finally {
            game.close();
        }
        context.succeed();
    }

    private static List<StepSnapshot> simulate(GameTestHelper context, int interval, boolean accelerated) {
        SemionGame game = game(context, interval, List.of(entry("first", 2), entry("second", 2)));
        try {
            PlayerLane red = game.playerLane(playerId("red")).orElseThrow();
            TestTower tower = new TestTower(red.ownerPlayer(), TeamId.RED, 1,
                    GridPosition.from(context.absolutePos(new BlockPos(1, 1, 3))));
            red.addTower(tower);
            enterWave(context, game);
            set(game, "activeMatchTicks", 0L);
            for (PlayerLane lane : lanes(game)) {
                lane.enqueueSummonedMonster(Monster.fromWaveEntry(entry("paid", 1), lane.teamId(), lane.laneId(), MonsterOrigin.NORMAL_PAID));
                lane.enqueueSummonedMonster(Monster.fromWaveEntry(entry("paid", 1), lane.teamId(), lane.laneId(), MonsterOrigin.NORMAL_PAID));
            }
            List<StepSnapshot> snapshots = new ArrayList<>();
            Runnable worlds = () -> {
                for (PlayerLane lane : lanes(game)) {
                    for (Monster monster : List.copyOf(lane.activeMonsters())) {
                        SemionMonsterEntity entity = entity(context, monster);
                        entity.setNoAi(true);
                        entity.setNoGravity(true);
                        if (entity.tickCount == 0) {
                            entity.applyTimedEffect(TimedEffectType.MONSTER_STUN, 1, 3);
                            entity.applyIgnite(red.ownerPlayer(), tower, TraitLoadout.none(), 2, 0, 1, 3, 1);
                        }
                        entity.tickCount++;
                        entity.tick();
                    }
                }
            };
            Runnable logical = () -> {
                game.tick(context.getLevel().getServer());
                snapshots.add(new StepSnapshot(game.phaseTicks(), game.currentTick(),
                        game.players().get(red.ownerPlayer()).economy().gas(),
                        red.activeMonsters().stream().map(monster -> {
                            SemionMonsterEntity entity = entity(context, monster);
                            return new MonsterSnapshot(monster.id(), entity.tickCount, entity.getHealth(),
                                    entity.activeTimedEffectTicks(TimedEffectType.MONSTER_STUN));
                        }).toList(), red.roundTowerMetrics()));
            };
            for (int wallTick = 0; wallTick < (accelerated ? 10 : 20); wallTick++) {
                worlds.run();
                if (accelerated) {
                    CombatStepRunner.run(2, () -> game.phase() == RoundPhase.LANE_WAVE, () -> {
                        worlds.run();
                        return true;
                    }, logical);
                } else {
                    logical.run();
                }
            }
            return snapshots;
        } finally {
            game.close();
        }
    }

    private static SemionGame game(GameTestHelper context, int interval, List<WaveMonsterEntry> entries) {
        RoundWaveConfig wave = new RoundWaveConfig(1, WaveSpawnMode.ROUND_ROBIN, interval,
                Map.of(RoundWaveConfig.DEFAULT_LANE_KEY, entries));
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), new WaveConfig(List.of(wave), 20, null),
                SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO)));
        game.configureAugments(new AugmentConfig(false, false, null, Map.of(), Set.of()));
        ((Random) get(game, "random")).setSeed(1234);
        if (!game.start(context.getLevel().getServer(), new ParticipantSelectionPlan(MatchMode.NORMAL, List.of(
                new AssignedParticipant(playerId("red"), "combat-red", TeamId.RED, 1),
                new AssignedParticipant(playerId("blue"), "combat-blue", TeamId.BLUE, 1)), Set.of(), 2))) {
            throw new AssertionError("Synthetic match must start");
        }
        game.players().values().forEach(player -> player.economy().overrideStartingValues(1000, 0, 13, 7));
        return game;
    }

    private static void enterWave(GameTestHelper context, SemionGame game) {
        set(game, "currentPrepareDurationTicks", 1);
        game.tick(context.getLevel().getServer());
        equal(RoundPhase.LANE_WAVE, game.phase(), "Fixture must enter wave");
    }

    private static void grouped(SemionGame game, GameTestHelper context, int steps, Runnable worlds) {
        boolean eligible = !game.isSandboxMode() && !game.isTutorialMode() && game.phase() == RoundPhase.LANE_WAVE;
        int round = game.currentRound();
        CombatStepRunner.run(steps, () -> eligible && game.phase() == RoundPhase.LANE_WAVE && game.currentRound() == round,
                () -> {
                    worlds.run();
                    return true;
                }, () -> game.tick(context.getLevel().getServer()));
    }

    private static List<PlayerLane> lanes(SemionGame game) {
        return game.teams().values().stream().filter(team -> team.active()).flatMap(team -> team.laneGroup().lanes().stream()).toList();
    }

    private static SemionMonsterEntity entity(GameTestHelper context, Monster monster) {
        return (SemionMonsterEntity) context.getLevel().getEntity(monster.minecraftEntityId());
    }

    private static WaveMonsterEntry entry(String id, int count) {
        return new WaveMonsterEntry(id, 100, 0, 1, AttackKind.MELEE, "minecraft:husk", null,
                MonsterDimensions.DEFAULT, 0, count, 0, 1, 2, 20);
    }

    private static UUID playerId(String name) {
        return UUID.nameUUIDFromBytes(("combat-step-" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static Object get(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void set(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void equal(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private record MonsterSnapshot(String id, int age, float health, int stunTicks) {
    }

    private record StepSnapshot(int phaseTicks, long currentTick, long gas, List<MonsterSnapshot> monsters,
            List<TowerRoundMetricsSnapshot> towers) {
    }
}
