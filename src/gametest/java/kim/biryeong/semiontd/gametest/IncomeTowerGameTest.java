package kim.biryeong.semiontd.gametest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.DemonLordTowerJob;
import kim.biryeong.semiontd.summon.SummonResultType;
import kim.biryeong.semiontd.tower.income.IncomeTower;
import kim.biryeong.semiontd.tower.income.IncomeTowerBalance;
import kim.biryeong.semiontd.tower.income.IncomeTowerService;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

public final class IncomeTowerGameTest {
    /** 침공군 모델 10종이 BIL에서 실제로 불러와지는지(발광·여러 축 회전 전처리를 거친 JSON 포함) 확인합니다. */
    @GameTest
    public void everyInvasionUnitModelLoadsThroughBil(GameTestHelper context) {
        for (String unit : IncomeTowerBalance.UNIT_IDS) {
            require(kim.biryeong.semiontd.entity.model.SemionBilModelCache.load("semion-td:invasion/" + unit).isPresent(),
                    "The " + unit + " model must load.");
            var model = kim.biryeong.semiontd.entity.model.SemionBilModelCache.load("semion-td:invasion/" + unit).orElseThrow();
            require(kim.biryeong.semiontd.entity.model.BilDeathVisual.spawn(context.getLevel(),
                            net.minecraft.world.phys.Vec3.atBottomCenterOf(context.absolutePos(new BlockPos(1, 1, 1))), 90.0F, model, 1.0F),
                    "A dead " + unit + " must leave its death animation behind.");
        }
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void incomeTowerTakesASlotPaysIncomeAndAttacksEveryWaveWithoutBeingConsumed(GameTestHelper context) {
        UUID owner = stableUuid("income-tower-owner");
        UUID enemy = stableUuid("income-tower-enemy");
        SemionGame game = null;
        try {
            game = startedGame(context, owner, enemy);
            PlayerEconomy economy = game.players().get(owner).economy();
            economy.addEmerald(100_000);
            PlayerLane lane = game.playerLane(owner).orElseThrow();
            long incomeBefore = economy.income();
            long emeraldBefore = economy.emerald();
            int slotsBefore = game.towerCapacityUsed(owner);
            long buildCost = game.summonShop().find("goblin_scout").orElseThrow().gasCost();
            long incomePerLevel = game.summonShop().find("goblin_scout").orElseThrow().incomeGain();

            PlayerLane enemyBuildLane = game.playerLane(enemy).orElseThrow();
            game.players().get(enemy).economy().addEmerald(100_000);
            require(IncomeTowerService.build(game, enemy, emptyPosition(enemyBuildLane, 0), "goblin_scout")
                            == IncomeTowerService.Result.NOT_DEMON_LORD,
                    "Other builders must not build income towers.");
            require(game.summonMonster(enemy, "goblin_scout").type() != SummonResultType.SUCCESS,
                    "Invasion units must not be sold as ordinary income summons.");

            BlockPos position = emptyPosition(lane, 0);
            require(IncomeTowerService.build(game, owner, position, "goblin_scout") == IncomeTowerService.Result.SUCCESS,
                    "Building a goblin income tower must succeed.");
            GridPosition grid = GridPosition.from(position);
            require(lane.towerAt(grid) instanceof IncomeTower, "The lane must hold the income tower.");
            IncomeTower tower = (IncomeTower) lane.towerAt(grid);
            require(game.towerCapacityUsed(owner) == slotsBefore + 1, "An income tower must take a tower slot.");
            require(economy.emerald() == emeraldBefore - buildCost, "Building must be paid in emerald.");
            require(economy.income() == incomeBefore + incomePerLevel, "Building must raise the round income.");
            require(!tower.canBeSold() && tower.sellRefundAmount() == 0,
                    "The diamond sale path must be closed for income towers.");

            require(IncomeTowerService.upgrade(game, owner, grid) == IncomeTowerService.Result.SUCCESS,
                    "Levelling the income tower must succeed.");
            require(tower.level() == 2, "Level up must raise the level.");
            require(economy.income() == incomeBefore + incomePerLevel * 2, "Each level must add its income.");
            require(economy.emerald() == emeraldBefore - buildCost - IncomeTowerBalance.upgradeCost(buildCost, 1),
                    "Level up must be paid in emerald.");
            require(IncomeTowerService.setTarget(game, owner, grid, TeamId.BLUE) == IncomeTowerService.Result.SUCCESS,
                    "Targeting a living enemy team must succeed.");
            require(IncomeTowerService.setTarget(game, owner, grid, TeamId.RED) == IncomeTowerService.Result.INVALID_TARGET,
                    "An income tower must not target its own team.");

            BlockPos soldPosition = emptyPosition(lane, 0);
            require(IncomeTowerService.build(game, owner, soldPosition, "goblin_scout") == IncomeTowerService.Result.SUCCESS,
                    "A second tower must be buildable.");
            long emeraldBeforeSale = economy.emerald();
            require(IncomeTowerService.sell(game, owner, GridPosition.from(soldPosition)) == IncomeTowerService.Result.SUCCESS,
                    "Selling an income tower must succeed.");
            require(economy.emerald() == emeraldBeforeSale + IncomeTowerBalance.sellRefund(buildCost),
                    "Selling must refund part of the emerald.");
            require(economy.income() == incomeBefore + incomePerLevel * 2, "Selling must take back that tower's income.");

            PlayerLane enemyLane = game.playerLane(enemy).orElseThrow();
            for (var team : game.teams().values()) {
                team.laneGroup().disableMonsters();
            }
            int queuedBefore = enemyLane.queuedSummonCount();
            int guard = game.remainingPrepareSeconds() * 20 + 40;
            while (game.phase() == RoundPhase.PREPARE_AND_SUMMON && guard-- > 0) {
                game.tick(context.getLevel().getServer());
            }
            require(game.phase() == RoundPhase.LANE_WAVE, "The prepare phase must end.");
            require(enemyLane.queuedSummonCount() == queuedBefore + 1,
                    "The income tower must send one unit to the targeted lane when prepare ends.");
            require(lane.towerAt(grid) == tower, "The income tower must stay after sending its unit.");
            require(tower.entityId().isEmpty(), "The income tower body must be hidden while the round is fought.");
            lane.resetForRound();
            require(tower.entityId().isPresent(), "The income tower body must come back for the next preparation.");
            context.succeed();
        } finally {
            if (game != null) {
                game.close();
            }
        }
    }

    private static SemionGame startedGame(GameTestHelper context, UUID owner, UUID enemy) {
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO))
        );
        require(game.selectJob(owner, DemonLordTowerJob.ID), "Income towers belong to the demon lord.");
        require(game.start(
                context.getLevel().getServer(),
                new ParticipantSelectionPlan(
                        MatchMode.NORMAL,
                        List.of(
                                new AssignedParticipant(owner, "income-red", TeamId.RED, 1),
                                new AssignedParticipant(enemy, "income-blue", TeamId.BLUE, 1)
                        ),
                        Set.of(),
                        2
                )
        ), "Income tower test game must start.");
        return game;
    }

    private static BlockPos emptyPosition(PlayerLane lane, int skip) {
        var bounds = lane.laneLayout().laneArea();
        int found = 0;
        for (int x = bounds.min().getX(); x <= bounds.max().getX(); x++) {
            for (int z = bounds.min().getZ(); z <= bounds.max().getZ(); z++) {
                BlockPos candidate = new BlockPos(x, bounds.min().getY(), z);
                if (lane.canPlaceTowerAt(candidate) && !lane.hasTowerAt(GridPosition.from(candidate))) {
                    if (found++ == skip) {
                        return candidate;
                    }
                }
            }
        }
        throw new AssertionError("No empty income tower position was found.");
    }

    private static UUID stableUuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
