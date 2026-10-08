package kim.biryeong.semiontd.game.simulation;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.augment.AugmentRarity;
import kim.biryeong.semiontd.balance.manage.ApplyMode;
import kim.biryeong.semiontd.balance.manage.BalanceGameRuntime;
import kim.biryeong.semiontd.command.SemionCommands;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterOrigin;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.simulation.WorkerPhysics;
import kim.biryeong.semiontd.game.ArenaCombatClock;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TeamMoneyTransferRequest;
import kim.biryeong.semiontd.game.TeamMoneyTransferResultType;
import kim.biryeong.semiontd.gametest.RuntimePlayerFixture;
import kim.biryeong.semiontd.gametest.SyntheticArenaFactory;
import kim.biryeong.semiontd.summon.SummonResultType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeLevelConfig;
import xyz.nucleoid.fantasy.RuntimeLevelHandle;
import xyz.nucleoid.fantasy.util.VoidChunkGenerator;

public final class CombatPurchaseBoundaryTest {
    @GameTest(maxTicks = 240)
    public void heldNativeStepDefersWholePurchasesAndFeedbackUntilOneAcceptedBoundary(GameTestHelper context) {
        start(context, false);
    }

    @GameTest(maxTicks = 240)
    public void failedNativeStepRejectsPurchasesAndLiveBalanceWithoutSpendingOrEnqueueing(GameTestHelper context) {
        start(context, true);
    }

    private static void start(GameTestHelper context, boolean fault) {
        var server = context.getLevel().getServer();
        RuntimeLevelHandle handle = Fantasy.get(server).openTemporaryLevel(
                Identifier.fromNamespaceAndPath("semion-td-gametest", "purchase_boundary_" + UUID.randomUUID()),
                new RuntimeLevelConfig().setGenerator(new VoidChunkGenerator(server))
                        .setGameRule(GameRules.SPAWN_MOBS, false));
        handle.setTickWhenEmpty(true);
        ServerLevel world = handle.asLevel();
        world.setChunkForced(0, 0, true);
        world.getChunk(0, 0);
        context.startSequence().thenWaitUntil(() -> context.assertTrue(
                world.areEntitiesActuallyLoadedAndTicking(new ChunkPos(0, 0)), "Purchase arena must be ticking"))
                .thenExecute(() -> {
                    try {
                        Fixture fixture = new Fixture(context, world, handle, fault);
                        fixture.session.beginFrame(1);
                        drive(context, fixture, 0);
                    } catch (Throwable failure) {
                        handle.unload();
                        context.fail(Component.literal("Purchase fixture failed: " + failure));
                    }
                });
    }

    private static void drive(GameTestHelper context, Fixture fixture, int polls) {
        try {
            if (!fixture.submitted && fixture.entered.getCount() == 0) {
                fixture.submitWhileHeld();
                fixture.submitted = true;
                fixture.release.countDown();
            }
            if (fixture.submitted && (fixture.fault ? fixture.session.failure() != null : fixture.session.idle())) {
                if (fixture.fault) {
                    fixture.verifyFault();
                } else {
                    fixture.verifyAccepted();
                }
                fixture.close();
                context.succeed();
                return;
            }
            if (polls >= 180) {
                throw new AssertionError("The held native step must reach its purchase boundary or retained failure");
            }
            fixture.session.endFrame();
            context.runAfterDelay(1, () -> drive(context, fixture, polls + 1));
        } catch (Throwable failure) {
            fixture.close();
            context.fail(Component.literal("Native purchase boundary failed: " + failure));
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final ServerLevel world;
        private final RuntimeLevelHandle handle;
        private final SemionGame game;
        private final SemionGameManager manager = new SemionGameManager();
        private final RuntimePlayerFixture online;
        private final RuntimePlayerFixture senderOnline;
        private final SemionPlayer buyer;
        private final SemionPlayer sender;
        private final PlayerLane target;
        private final SemionMonsterEntity actor;
        private final CombatSimulationSession session;
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicBoolean first = new AtomicBoolean(true);
        private final Output output = new Output();
        private final CommandSourceStack source;
        private final CommandSourceStack senderSource;
        private final boolean fault;
        private final Money baseline;
        private final long summonCost;
        private final long productionCost;
        private final long limitDiamondCost;
        private final long limitEmeraldCost;
        private final int initialNextRound;
        private final int initialGuiEvents;
        private final String requestId;
        private final Map<String, TeamMoneyTransferRequest> initialRequests;
        private boolean submitted;
        private boolean closed;

        @SuppressWarnings("unchecked")
        private Fixture(GameTestHelper context, ServerLevel world, RuntimeLevelHandle handle, boolean fault) throws Exception {
            this.world = world;
            this.handle = handle;
            this.fault = fault;
            for (int x = 0; x < 8; x++) {
                for (int z = 0; z < 8; z++) {
                    world.setBlock(new BlockPos(x, 64, z), Blocks.STONE.defaultBlockState(), 3);
                }
            }
            UUID playerId = UUID.randomUUID();
            online = RuntimePlayerFixture.connect(context, world, new Vec3(3.5, 65, 3.5),
                    GameType.ADVENTURE, playerId, "purchase-test");
            game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(),
                    SyntheticArenaFactory.create(world, new BlockPos(0, 64, 0)));
            field(game, "matchMode", MatchMode.NORMAL);
            buyer = addPlayer(playerId, "purchase-test", TeamId.RED);
            addPlayer(UUID.randomUUID(), "purchase-target", TeamId.BLUE);
            UUID senderId = UUID.randomUUID();
            senderOnline = RuntimePlayerFixture.connect(context, world, new Vec3(4.5, 65, 3.5),
                    GameType.ADVENTURE, senderId, "purchase-giver");
            sender = new SemionPlayer(senderId, "purchase-giver", TeamId.RED, 2, new PlayerEconomy(game.economyConfig()));
            sender.economy().overrideStartingValues(1_000_000, 1_000_000, 10, 1);
            game.players().put(senderId, sender);
            require(game.teams().get(TeamId.RED).addPlayer(sender, world, game.arena().lane(TeamId.RED, 2).orElseThrow()),
                    "The real teammate lane is registered");
            target = game.teams().get(TeamId.BLUE).laneGroup().lane(1).orElseThrow();
            verifyUnownedPurchases();
            var request = game.requestTeamMoney(buyer.uuid(), 10);
            require(request.type() == TeamMoneyTransferResultType.SUCCESS, "An unowned teammate request is created");
            requestId = request.requestId().orElseThrow();
            requests().put("expired-request", new TeamMoneyTransferRequest("expired-request", buyer.uuid(), TeamId.RED, 5, 0));
            initialRequests = Map.copyOf(requests());
            buyer.augments().initialize(17, AugmentConfig.defaults(),
                    List.of(AugmentRarity.SILVER, AugmentRarity.GOLD, AugmentRarity.PRISMATIC));
            buyer.augments().offer(5, 5, 10_000, ignored -> true);
            buyer.economy().overrideStartingValues(1_000_000, 1_000_000, 10, 1);
            field(game, "phase", RoundPhase.LANE_WAVE);
            field(game, "rosterLocked", true);
            var waveTeams = SemionGame.class.getDeclaredField("currentWaveTeamIds");
            waveTeams.setAccessible(true);
            ((Set<TeamId>) waveTeams.get(game)).add(TeamId.RED);
            actor = monster(world);
            game.playerLane(playerId).orElseThrow().activeMonsters().add(actor.runtimeMonster());
            session = new CombatSimulationSession(world.getServer(), game, input -> {
                if (first.getAndSet(false)) {
                    entered.countDown();
                    try {
                        if (!release.await(30, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Purchase test worker release timed out");
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(interrupted);
                    }
                    if (fault) {
                        throw new IllegalStateException("Injected purchase worker failure");
                    }
                }
                return WorkerPhysics.advance(input);
            });
            field(manager, "activeGame", game);
            field(manager, "combatSimulation", session);
            source = online.player().createCommandSourceStack().withSource(output);
            senderSource = senderOnline.player().createCommandSourceStack().withSource(output);
            baseline = money();
            initialNextRound = target.pendingNextRoundSummonCount();
            initialGuiEvents = buyer.augmentTelemetry().snapshot().guiEvents().size();
            summonCost = game.summonShop().find("zombie").orElseThrow().gasCost();
            productionCost = game.economyConfig().gasProduction().upgradeCost(baseline.productionCount());
            limitDiamondCost = game.nextTowerLimitPurchaseDiamondCost(playerId);
            limitEmeraldCost = game.nextTowerLimitPurchaseEmeraldCost(playerId);
        }

        private SemionPlayer addPlayer(UUID id, String name, TeamId teamId) {
            var team = game.teams().get(teamId);
            team.activate();
            var player = new SemionPlayer(id, name, teamId, 1, new PlayerEconomy(game.economyConfig()));
            player.economy().overrideStartingValues(1_000_000, 1_000_000, 10, 1);
            game.players().put(id, player);
            require(team.addPlayer(player, world, game.arena().lane(teamId, 1).orElseThrow()), "Buyer lane is registered");
            return player;
        }

        private void verifyUnownedPurchases() throws Exception {
            field(game, "phase", RoundPhase.PREPARE_AND_SUMMON);
            require(game.summonMonster(buyer.uuid(), "zombie").type() == SummonResultType.SUCCESS,
                    "Unowned preparation summons remain immediate");
            require(game.upgradeGasProduction(buyer.uuid()) && game.purchaseTowerLimit(buyer.uuid()),
                    "Unowned preparation upgrades remain immediate");
            field(game, "phase", RoundPhase.LANE_WAVE);
            field(game, "sandboxMode", true);
            require(game.summonMonster(buyer.uuid(), "zombie").type() == SummonResultType.SUCCESS,
                    "Unowned practice wave summons remain immediate");
            require(game.upgradeGasProduction(buyer.uuid()) && game.purchaseTowerLimit(buyer.uuid()),
                    "Unowned practice upgrades remain immediate");
            field(game, "sandboxMode", false);
        }

        private void submitWhileHeld() throws Exception {
            require(session.logicalTickCount() == 0 && !session.idle(), "Real physics work is held before completion");
            assertDirectRejected();
            invokePurchaseCommands();
            require(game.augmentService().handle(game, online.player(), confirmation(), false) == 1,
                    "The complete dialog confirmation is accepted as pending input");
            invokeTeamCommands();
            require(money().equals(baseline) && target.pendingNextRoundSummonCount() == initialNextRound,
                    "Held purchases cannot spend money, change income or enqueue monsters");
            require(requests().equals(initialRequests), "Held team commands cannot create, accept or prune requests");
            require(output.messages.isEmpty(), "Purchase commands cannot announce results before the input boundary");
            require(buyer.augmentTelemetry().snapshot().guiEvents().size() == initialGuiEvents,
                    "Dialog confirmation cannot record rejection or reopen before the input boundary");
        }

        private void assertDirectRejected() {
            require(game.summonMonster(buyer.uuid(), "zombie").type() == SummonResultType.INVALID_PHASE,
                    "Shared summon transaction rejects a direct caller outside the owned boundary");
            require(!game.upgradeGasProduction(buyer.uuid()) && !game.purchaseTowerLimit(buyer.uuid()),
                    "Shared upgrade transactions reject callers outside the owned boundary");
            require(game.requestTeamMoney(sender.uuid(), 12).type() == TeamMoneyTransferResultType.MATCH_ENDED
                    && game.acceptTeamMoneyRequest(sender.uuid(), requestId).type() == TeamMoneyTransferResultType.MATCH_ENDED,
                    "Shared team-money transactions reject callers before request pruning or transfer");
            require(requests().equals(initialRequests), "Direct rejection preserves even expired requests until a valid boundary");
            require(money().equals(baseline) && target.pendingNextRoundSummonCount() == initialNextRound,
                    "Direct rejections must preserve all purchase state");
        }

        private void invokePurchaseCommands() throws Exception {
            require(command("summon", "zombie") == 1, "Summon command accepts pending input");
            require(command("emeraldUp", null) == 1, "Production command accepts pending input");
            require(command("towerLimitUp", null) == 1, "Tower-limit command accepts pending input");
        }

        private void invokeTeamCommands() throws Exception {
            Method request = SemionCommands.class.getDeclaredMethod("requestTeamMoney", CommandSourceStack.class,
                    SemionGameManager.class, int.class);
            request.setAccessible(true);
            require((int) request.invoke(null, senderSource, manager, 12) == 1, "Team request command accepts pending input");
            Method accept = SemionCommands.class.getDeclaredMethod("acceptTeamMoney", CommandSourceStack.class,
                    SemionGameManager.class, String.class);
            accept.setAccessible(true);
            require((int) accept.invoke(null, senderSource, manager, requestId) == 1, "Team acceptance command accepts pending input");
        }

        private int command(String name, String summonId) throws Exception {
            Method method = summonId == null ? SemionCommands.class.getDeclaredMethod(name, CommandSourceStack.class,
                    SemionGameManager.class) : SemionCommands.class.getDeclaredMethod(name, CommandSourceStack.class,
                    SemionGameManager.class, String.class);
            method.setAccessible(true);
            return (int) (summonId == null ? method.invoke(null, source, manager)
                    : method.invoke(null, source, manager, summonId));
        }

        private String confirmation() throws Exception {
            var token = game.augmentService().getClass().getDeclaredField("sessionToken");
            token.setAccessible(true);
            return "session " + token.get(game.augmentService()) + " buy " + buyer.economyAugments().revision()
                    + " zombie current " + summonCost;
        }

        private void verifyAccepted() {
            require(session.logicalTickCount() == 1 && game.phase() == RoundPhase.LANE_WAVE,
                    "The healthy held step commits exactly once");
            require(buyer.economy().diamond() == baseline.diamond() - productionCost - limitDiamondCost + 10
                    && sender.economy().diamond() == baseline.senderDiamond() - 10,
                    "Accepted upgrades and the teammate transfer commit their actual diamond amounts once");
            require(buyer.economy().emerald() == baseline.emerald() - summonCost - limitEmeraldCost,
                    "Accepted summon and tower-limit purchase deduct emerald cost once");
            require(buyer.economy().emeraldProductionUpgradeCount() == baseline.productionCount() + 1
                    && buyer.economy().towerLimitPurchaseCount() == baseline.limitCount() + 1,
                    "Each accepted upgrade commits once");
            require(buyer.matchStats().summonedMonsters() == baseline.summons() + 1
                    && target.pendingNextRoundSummonCount() == initialNextRound + 1,
                    "The accepted wave summon is enqueued once for the next round");
            require(output.messages.size() == 5 && output.messages.stream().noneMatch(value -> value.contains("실패")),
                    "All purchase and team-money results are reported only after successful application");
            require(requests().size() == 1 && !requests().containsKey(requestId) && !requests().containsKey("expired-request")
                    && requests().values().iterator().next().requesterId().equals(sender.uuid()),
                    "One new request is created and the accepted and expired requests are removed at the boundary");
            require(buyer.augmentTelemetry().snapshot().guiEvents().size() == initialGuiEvents + 1,
                    "The existing PREPARE-only dialog confirmation rejects once at the actual boundary");
            Money accepted = money();
            session.endFrame();
            require(money().equals(accepted) && output.messages.size() == 5,
                    "Presentation repeats cannot execute accepted purchases again");
        }

        private void verifyFault() throws Exception {
            require(session.failure() != null && manager.combatSimulationFailed() && CombatSimulationRuntime.controls(world),
                    "The failed native step retains ownership and manager fault state");
            assertDirectRejected();
            invokePurchaseCommands();
            invokeTeamCommands();
            require(game.augmentService().handle(game, online.player(), confirmation(), false) == 1,
                    "Fault ownership consumes dialog input without native fallback");
            require(money().equals(baseline) && target.pendingNextRoundSummonCount() == initialNextRound,
                    "Queued and post-failure purchases cannot spend or enqueue");
            require(requests().equals(initialRequests), "A failed step cannot add, accept or prune any team requests");
            require(output.messages.isEmpty() && buyer.augmentTelemetry().snapshot().guiEvents().size() == initialGuiEvents,
                    "Failed purchases cannot announce completion or mutate dialog telemetry");
            var before = manager.captureBalanceBundle();
            var runtime = new BalanceGameRuntime(world.getServer(), manager, Files.createTempDirectory("balance-fault-"));
            String revision = runtime.revision();
            require(runtime.writeBlocked() != null && runtime.writeBlocked().contains("Combat simulation failed"),
                    "Managed balance reports the retained fault as its write blocker");
            for (ApplyMode mode : ApplyMode.values()) {
                try {
                    runtime.apply(before, mode, "fault-test", "must-not-apply");
                    throw new AssertionError("A failed match cannot accept a live-balance application");
                } catch (IllegalStateException expected) {
                    require(expected.getMessage().contains("Combat simulation failed"),
                            "Balance application rejects the fault before any other validation or mutation");
                }
                require(runtime.revision().equals(revision) && manager.captureBalanceBundle().revision().equals(before.revision()),
                        "Rejected balance updates cannot change the installed configuration or revision");
            }
        }

        private Money money() {
            var economy = buyer.economy();
            return new Money(economy.diamond(), economy.emerald(), economy.income(), economy.emeraldPerSec(),
                    economy.emeraldProductionUpgradeCount(), economy.towerLimitPurchaseCount(),
                    buyer.matchStats().summonedMonsters(), buyer.economyAugments().revision(), sender.economy().diamond());
        }

        @SuppressWarnings("unchecked")
        private Map<String, TeamMoneyTransferRequest> requests() {
            try {
                var field = SemionGame.class.getDeclaredField("teamMoneyRequests");
                field.setAccessible(true);
                return (Map<String, TeamMoneyTransferRequest>) field.get(game);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            release.countDown();
            try {
                field(game, "phase", RoundPhase.ENDED);
                session.close();
            } catch (Exception failure) {
                throw new AssertionError(failure);
            } finally {
                actor.discard();
                online.close();
                senderOnline.close();
                game.teams().values().forEach(team -> team.closeRuntime());
                ArenaCombatClock.remove(world);
                handle.unload();
            }
        }
    }

    private static SemionMonsterEntity monster(ServerLevel world) {
        var runtime = new Monster("purchase_boundary_actor", TeamId.RED, 1, Optional.empty(), Optional.empty(),
                10_000, 0, 0, AttackKind.MELEE, "minecraft:zombie", 0);
        runtime.setOrigin(MonsterOrigin.NATURAL_WAVE);
        var entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, world);
        entity.configureFrom(runtime, null);
        entity.setNoAi(true);
        entity.setNoGravity(true);
        entity.setPos(1.5, 65, 1.5);
        require(world.addFreshEntity(entity), "Native purchase actor is registered");
        runtime.markMinecraftEntitySpawned(entity.getId(), 1.5, 65, 1.5);
        return entity;
    }

    private static void field(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record Money(long diamond, long emerald, long income, long perSecond, int productionCount,
                         int limitCount, long summons, long augmentRevision, long senderDiamond) {
    }

    private static final class Output implements CommandSource {
        private final List<String> messages = new ArrayList<>();
        public void sendSystemMessage(Component message) { messages.add(message.getString()); }
        public boolean acceptsSuccess() { return true; }
        public boolean acceptsFailure() { return true; }
        public boolean shouldInformAdmins() { return false; }
    }
}
