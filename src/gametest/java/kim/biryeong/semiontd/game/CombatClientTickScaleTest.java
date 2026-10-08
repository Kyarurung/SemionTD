package kim.biryeong.semiontd.game;

import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.gametest.RuntimeArenaFixture;
import kim.biryeong.semiontd.gametest.RuntimePlayerFixture;
import kim.biryeong.semiontd.gametest.SyntheticArenaFactory;
import kim.biryeong.semiontd.job.DemonLordTowerJob;
import kim.biryeong.semiontd.tower.demonlord.DemonLordService;
import kim.biryeong.semiontd.tower.demonlord.DemonLordStates;
import kim.biryeong.semiontd.tower.plant.PlantTowerTickScaleFixture;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public final class CombatClientTickScaleTest implements RuntimeArenaFixture {
    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void scopedRatePreservesLegacyPandaAndPlayerScalingAndResets(GameTestHelper context) {
        var level = context.getLevel();
        var server = level.getServer();
        float previousRate = server.tickRateManager().tickrate();
        var arena = SyntheticArenaFactory.create(level, context.absolutePos(BlockPos.ZERO));
        var game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), arena);
        try (var fixture = RuntimePlayerFixture.connect(context, level,
                Vec3.atCenterOf(context.absolutePos(new BlockPos(5, 2, 5))), GameType.ADVENTURE,
                UUID.randomUUID(), "combat-scale")) {
            var player = fixture.player();
            var participant = new SemionPlayer(player.getUUID(), player.getGameProfile().name(), TeamId.RED, 1,
                    new PlayerEconomy(EconomyConfig.defaultConfig()));
            participant.assignJob(new DemonLordTowerJob());
            var lane = new PlayerLane(TeamId.RED, 1, player.getUUID(), level,
                    arena.lane(TeamId.RED, 1).orElseThrow());
            Runnable syncPlayer = () -> DemonLordService.tick(lane, Map.of(player.getUUID(), participant));
            SemionTowerEntity panda = new SemionTowerEntity(SemionEntityTypes.TOWER, level);
            var state = DemonLordStates.getOrCreate(player.getUUID());
            state.enterCombat();
            state.consumePendingSpawn();
            try {
                CombatSpeedRuntime.clear();
                server.tickRateManager().setTickRate(20.0F);
                syncPlayer.run();
                double normalMove = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
                double normalAttack = player.getAttributeValue(Attributes.ATTACK_SPEED);
                float normalFlight = player.getAbilities().getFlyingSpeed();
                require(PlantTowerTickScaleFixture.dashTicks(panda) == 32, "Normal panda dash must span 32 logical ticks");

                server.tickRateManager().setTickRate(40.0F);
                syncPlayer.run();
                double legacyMove = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
                double legacyAttack = player.getAttributeValue(Attributes.ATTACK_SPEED);
                float legacyFlight = player.getAbilities().getFlyingSpeed();
                int legacyDash = PlantTowerTickScaleFixture.dashTicks(panda);
                int legacyCooldown = ClientTickScale.toClientTicks(server, level, 80);
                require(legacyDash == 64, "Legacy40 panda dash must span 64 logical ticks");
                require(legacyCooldown == 40, "Legacy40 cooldown must display 40 client ticks");

                server.tickRateManager().setTickRate(20.0F);
                CombatSpeedRuntime.configure(server, game, 40.0F);
                syncPlayer.run();
                requireClose(legacyMove, player.getAttributeValue(Attributes.MOVEMENT_SPEED), "Scoped movement");
                requireClose(legacyAttack, player.getAttributeValue(Attributes.ATTACK_SPEED), "Scoped attack speed");
                requireClose(legacyFlight, player.getAbilities().getFlyingSpeed(), "Scoped flight");
                require(PlantTowerTickScaleFixture.dashTicks(panda) == legacyDash, "Scoped panda contact cadence must match legacy40");
                require(ClientTickScale.toClientTicks(server, level, 80) == legacyCooldown,
                        "Scoped cooldown must match legacy40");
                requireClose(1.0, ClientTickScale.ratio(server), "Unscoped conversion stays at server20");
                for (var other : server.getAllLevels()) {
                    if (other != level) {
                        requireClose(1.0, ClientTickScale.ratio(server, other), "Unrelated world stays at server20");
                    }
                }

                CombatSpeedRuntime.clear();
                syncPlayer.run();
                requireClose(normalMove, player.getAttributeValue(Attributes.MOVEMENT_SPEED), "Reset movement");
                requireClose(normalAttack, player.getAttributeValue(Attributes.ATTACK_SPEED), "Reset attack speed");
                requireClose(normalFlight, player.getAbilities().getFlyingSpeed(), "Reset flight");
                require(PlantTowerTickScaleFixture.dashTicks(panda) == 32, "Reset panda duration");
                require(ClientTickScale.toClientTicks(server, level, 80) == 80, "Reset cooldown duration");
                context.succeed();
            } finally {
                DemonLordService.cleanupPlayer(player);
                panda.discard();
            }
        } finally {
            CombatSpeedRuntime.clear();
            server.tickRateManager().setTickRate(previousRate);
        }
    }

    private static void requireClose(double expected, double actual, String message) {
        require(Math.abs(expected - actual) < 1.0e-6, message + ": expected " + expected + ", got " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
