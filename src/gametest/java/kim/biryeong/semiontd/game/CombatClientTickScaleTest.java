package kim.biryeong.semiontd.game;

import java.lang.reflect.Method;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.gametest.SyntheticArenaFactory;
import kim.biryeong.semiontd.tower.demonlord.DemonLordService;
import kim.biryeong.semiontd.tower.demonlord.DemonLordState;
import kim.biryeong.semiontd.tower.plant.PandaTower;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class CombatClientTickScaleTest {
    @GameTest
    public void scopedRatePreservesLegacyPandaAndPlayerScalingAndResets(GameTestHelper context) throws Exception {
        var level = context.getLevel();
        var server = level.getServer();
        float previousRate = server.tickRateManager().tickrate();
        var arena = SyntheticArenaFactory.create(level, context.absolutePos(BlockPos.ZERO));
        var game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), arena);
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        SemionTowerEntity panda = new SemionTowerEntity(SemionEntityTypes.TOWER, level);
        DemonLordState state = new DemonLordState(player.getUUID());
        state.enterCombat();
        try {
            CombatSpeedRuntime.clear();
            server.tickRateManager().setTickRate(20.0F);
            syncPlayer(player, state);
            double normalMove = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
            double normalAttack = player.getAttributeValue(Attributes.ATTACK_SPEED);
            float normalFlight = player.getAbilities().getFlyingSpeed();
            require(dashTicks(panda) == 32, "Normal panda dash must span 32 logical ticks");

            server.tickRateManager().setTickRate(40.0F);
            syncPlayer(player, state);
            double legacyMove = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
            double legacyAttack = player.getAttributeValue(Attributes.ATTACK_SPEED);
            float legacyFlight = player.getAbilities().getFlyingSpeed();
            int legacyDash = dashTicks(panda);
            int legacyCooldown = ClientTickScale.toClientTicks(server, level, 80);
            require(legacyDash == 64, "Legacy40 panda dash must span 64 logical ticks");
            require(legacyCooldown == 40, "Legacy40 cooldown must display 40 client ticks");

            server.tickRateManager().setTickRate(20.0F);
            CombatSpeedRuntime.configure(server, game, 40.0F);
            syncPlayer(player, state);
            requireClose(legacyMove, player.getAttributeValue(Attributes.MOVEMENT_SPEED), "Scoped movement");
            requireClose(legacyAttack, player.getAttributeValue(Attributes.ATTACK_SPEED), "Scoped attack speed");
            requireClose(legacyFlight, player.getAbilities().getFlyingSpeed(), "Scoped flight");
            require(dashTicks(panda) == legacyDash, "Scoped panda contact cadence must match legacy40");
            require(ClientTickScale.toClientTicks(server, level, 80) == legacyCooldown,
                    "Scoped cooldown must match legacy40");
            requireClose(1.0, ClientTickScale.ratio(server), "Unscoped conversion stays at server20");
            for (var other : server.getAllLevels()) {
                if (other != level) {
                    requireClose(1.0, ClientTickScale.ratio(server, other), "Unrelated world stays at server20");
                }
            }

            CombatSpeedRuntime.clear();
            syncPlayer(player, state);
            requireClose(normalMove, player.getAttributeValue(Attributes.MOVEMENT_SPEED), "Reset movement");
            requireClose(normalAttack, player.getAttributeValue(Attributes.ATTACK_SPEED), "Reset attack speed");
            requireClose(normalFlight, player.getAbilities().getFlyingSpeed(), "Reset flight");
            require(dashTicks(panda) == 32, "Reset panda duration");
            require(ClientTickScale.toClientTicks(server, level, 80) == 80, "Reset cooldown duration");
            context.succeed();
        } finally {
            CombatSpeedRuntime.clear();
            server.tickRateManager().setTickRate(previousRate);
            DemonLordService.cleanupPlayer(player);
            player.discard();
            panda.discard();
        }
    }

    private static void syncPlayer(ServerPlayer player, DemonLordState state) throws Exception {
        Method movement = DemonLordService.class.getDeclaredMethod("syncMoveSpeed", ServerPlayer.class, DemonLordState.class);
        movement.setAccessible(true);
        movement.invoke(null, player, state);
        Method scaling = DemonLordService.class.getDeclaredMethod("syncTickScale", ServerPlayer.class, DemonLordState.class, long.class);
        scaling.setAccessible(true);
        scaling.invoke(null, player, state, player.level().getGameTime());
    }

    private static int dashTicks(SemionTowerEntity panda) throws Exception {
        Method method = PandaTower.class.getDeclaredMethod("dashTicks", SemionTowerEntity.class);
        method.setAccessible(true);
        return (int) method.invoke(null, panda);
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
