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
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.network.chat.Component;

public final class CombatStepTest implements RuntimeArenaFixture {
    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void twentyPhysicalFramesPreserveFortyLogicalTicksWithIntervalOne(GameTestHelper context) {
        verifyPhysicalFrames(context, 1);
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void twentyPhysicalFramesPreserveFortyLogicalTicksWithIntervalThree(GameTestHelper context) {
        verifyPhysicalFrames(context, 3);
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void preparationAndPayoutBoundariesCannotConsumeExtraSteps(GameTestHelper context) {
        SemionGame game = game(context, 1, List.of(entry("single", 1)));
        try {
            set(game, "currentPrepareDurationTicks", 1);
            grouped(game, context, 5);
            equal(RoundPhase.LANE_WAVE, game.phase(), "Preparation must enter the wave");
            equal(0, game.phaseTicks(), "Preparation must not consume a wave step");
            game.tick(context.getLevel().getServer());
            for (PlayerLane lane : lanes(game)) {
                for (Monster monster : lane.activeMonsters()) {
                    entity(context, monster).setHealth(0);
                }
            }
            grouped(game, context, 5);
            equal(RoundPhase.ROUND_PAYOUT, game.phase(), "Wave completion must defer payout");
            equal(1, game.currentRound(), "Wave completion must not advance round twice");
            long minerals = game.players().get(redPlayerId(game)).economy().mineral();
            long income = game.players().get(redPlayerId(game)).economy().income();
            grouped(game, context, 5);
            equal(RoundPhase.PREPARE_AND_SUMMON, game.phase(), "Payout must enter preparation");
            equal(0, game.phaseTicks(), "New preparation must retain its full duration");
            equal(2, game.currentRound(), "Income payout must advance the round once");
            equal(minerals + income, game.players().get(redPlayerId(game)).economy().mineral(), "Income must pay exactly once");
            List<?> metrics = ((Map<?, ?>) get(game, "roundMetricsByPlayer")).get(redPlayerId(game)) instanceof List<?> list ? list : List.of();
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
            CombatStepRunner.run(2, () -> game.phase() == RoundPhase.LANE_WAVE, () -> {
                game.tick(context.getLevel().getServer());
                if (game.phaseTicks() == SemionGame.DEFAULT_WAVE_FINAL_DEFENSE_TICKS - 1) {
                    equal(1, lanes(game).getFirst().activeMonsters().size(), "First step must only spawn one queued monster");
                }
            });
            equal(SemionGame.DEFAULT_WAVE_FINAL_DEFENSE_TICKS, game.phaseTicks(), "Second step must reach final defense exactly");
            equal(4, lanes(game).getFirst().activeMonsters().size(), "Final defense must release the remaining queue once");
        } finally {
            game.close();
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void practiceMatchesKeepOneLogicalStepPerPhysicalFrame(GameTestHelper context) {
        for (String mode : List.of("sandboxMode", "tutorialMode")) {
            SemionGame game = game(context, 1, List.of(entry("practice", 4)));
            try {
                enterWave(context, game);
                set(game, mode, true);
                grouped(game, context, 5);
                equal(1, game.phaseTicks(), "Practice matches must not receive extra logical steps");
                equal(1, lanes(game).getFirst().activeMonsters().size(), "Practice spawn cadence must remain unchanged");
            } finally {
                game.close();
            }
        }
        context.succeed();
    }

    private static void verifyPhysicalFrames(GameTestHelper context, int interval) {
        List<StepSnapshot> reference = new ArrayList<>();
        SemionGame referenceGame = timedGame(context, interval);
        try {
            for (int step = 0; step < 40; step++) {
                referenceGame.tick(context.getLevel().getServer());
                reference.add(snapshot(referenceGame));
            }
        } finally {
            referenceGame.close();
        }
        SemionGame game = timedGame(context, interval);
        long startTick = context.getTick();
        List<StepSnapshot> actual = new ArrayList<>();
        Map<Integer, Integer> previousFrameAges = new java.util.HashMap<>();
        var sequence = context.startSequence();
        for (int frame = 1; frame <= 20; frame++) {
            int expectedFrame = frame;
            sequence.thenIdle(1).thenExecute(() -> checked(context, game, () -> {
                equal((long) expectedFrame, context.getTick() - startTick, "Only ordinary physical server frames may pass");
                PlayerLane red = game.playerLane(redPlayerId(game)).orElseThrow();
                Map<Integer, Integer> agesBefore = red.activeMonsters().stream().collect(java.util.stream.Collectors.toMap(
                        Monster::minecraftEntityId, monster -> entity(context, monster).tickCount));
                previousFrameAges.forEach((id, age) -> equal(age + 1, agesBefore.get(id),
                        "Existing monsters must receive exactly one ordinary entity tick between frames"));
                grouped(game, context, 2, () -> {
                    keepEntitiesStationary(context, game);
                    actual.add(snapshot(game));
                });
                for (Monster monster : red.activeMonsters()) {
                    int expectedAge = agesBefore.getOrDefault(monster.minecraftEntityId(), 0);
                    equal(expectedAge, entity(context, monster).tickCount,
                            "Game-only steps must not tick existing or newly spawned entities");
                    previousFrameAges.put(monster.minecraftEntityId(), expectedAge);
                }
                equal(reference.subList(0, expectedFrame * 2), actual,
                        "Logical phase, gas, FIFO spawn and survival totals must match the reference");
                if (expectedFrame == 20) {
                    equal(40, game.phaseTicks(), "Twenty physical frames must advance forty logical wave ticks");
                    equal(14L, game.players().get(red.ownerPlayer()).economy().gas(), "Forty logical ticks must pay gas twice");
                    equal(40L, actual.getLast().towers().getFirst().survivalTicks(), "Survival metrics use logical time");
                    game.close();
                    context.succeed();
                }
            }));
        }
    }

    private static SemionGame timedGame(GameTestHelper context, int interval) {
        SemionGame game = game(context, interval, List.of(entry("first", 2), entry("second", 2)));
        PlayerLane red = game.playerLane(redPlayerId(game)).orElseThrow();
        TestTower tower = new TestTower(red.ownerPlayer(), TeamId.RED, 1,
                GridPosition.from(context.absolutePos(new BlockPos(1, 1, 3))));
        red.addTower(tower);
        enterWave(context, game);
        set(game, "activeMatchTicks", 0L);
        for (PlayerLane lane : lanes(game)) {
            lane.enqueueSummonedMonster(Monster.fromWaveEntry(entry("paid", 1), lane.teamId(), lane.laneId(), MonsterOrigin.NORMAL_PAID));
            lane.enqueueSummonedMonster(Monster.fromWaveEntry(entry("paid", 1), lane.teamId(), lane.laneId(), MonsterOrigin.NORMAL_PAID));
        }
        keepEntitiesStationary(context, game);
        return game;
    }

    private static void keepEntitiesStationary(GameTestHelper context, SemionGame game) {
        for (PlayerLane lane : lanes(game)) {
            for (Monster monster : lane.activeMonsters()) {
                SemionMonsterEntity entity = entity(context, monster);
                entity.setNoAi(true);
                entity.setNoGravity(true);
            }
            for (var tower : lane.towers()) {
                if (tower instanceof TestTower testTower) {
                    testTower.runtimeEntity(lane).ifPresent(entity -> {
                        entity.setNoAi(true);
                        entity.setNoGravity(true);
                    });
                }
            }
        }
    }

    private static StepSnapshot snapshot(SemionGame game) {
        PlayerLane lane = game.playerLane(redPlayerId(game)).orElseThrow();
        return new StepSnapshot(game.phaseTicks(), game.currentTick(), game.players().get(lane.ownerPlayer()).economy().gas(),
                lane.activeMonsters().stream().map(Monster::id).toList(), lane.roundTowerMetrics());
    }

    private static void checked(GameTestHelper context, SemionGame game, Runnable action) {
        try {
            action.run();
        } catch (Throwable cause) {
            game.close();
            if (cause instanceof GameTestAssertException assertion) {
                throw assertion;
            }
            var failure = new GameTestAssertException(Component.literal(cause.toString()), (int) context.getTick());
            failure.initCause(cause);
            throw failure;
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
                new AssignedParticipant(playerId(context, "red"), "combat-red", TeamId.RED, 1),
                new AssignedParticipant(playerId(context, "blue"), "combat-blue", TeamId.BLUE, 1)), Set.of(), 2))) {
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

    private static void grouped(SemionGame game, GameTestHelper context, int steps) {
        grouped(game, context, steps, () -> {});
    }

    private static void grouped(SemionGame game, GameTestHelper context, int steps, Runnable afterStep) {
        boolean eligible = !game.isSandboxMode() && !game.isTutorialMode() && game.phase() == RoundPhase.LANE_WAVE;
        int round = game.currentRound();
        CombatStepRunner.run(steps, () -> eligible && game.phase() == RoundPhase.LANE_WAVE && game.currentRound() == round,
                () -> {
                    game.tick(context.getLevel().getServer());
                    afterStep.run();
                });
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

    private static UUID playerId(GameTestHelper context, String name) {
        return UUID.nameUUIDFromBytes(("combat-step-" + context.absolutePos(BlockPos.ZERO) + "-" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static UUID redPlayerId(SemionGame game) {
        return game.players().values().stream().filter(player -> player.teamId() == TeamId.RED).findFirst().orElseThrow().uuid();
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

    private record StepSnapshot(int phaseTicks, long currentTick, long gas, List<String> monsters,
            List<TowerRoundMetricsSnapshot> towers) {
    }
}
