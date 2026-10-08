package kim.biryeong.semiontd.game;

import java.util.ArrayList;
import java.util.List;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.gametest.RuntimeArenaFixture;
import kim.biryeong.semiontd.gametest.SyntheticArenaFactory;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

public final class CombatMultiplierRuntimeTest implements RuntimeArenaFixture {
    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void logicalClockChangesRatesWithoutAdvancingPhysicalTicks(GameTestHelper context) {
        var world = context.getLevel();
        var server = world.getServer();
        var game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(),
                SyntheticArenaFactory.create(world, context.absolutePos(BlockPos.ZERO)));
        long physicalTime = world.getGameTime();
        int serverTicks = server.getTickCount();
        List<Long> familyTimes = new ArrayList<>();
        try {
            CombatSpeedRuntime.configure(server, game, 40.0F, 2);
            require(CombatSpeedRuntime.multiplier(world) == 2.0, "Movement/attack multiplier must be two");
            require(CombatSpeedRuntime.logicalSteps(world) == 2, "Timer budget must be two");
            CombatSpeedRuntime.runGameStep(0, () -> familyTimes.add(CombatSpeedRuntime.gameTime(world)));
            CombatSpeedRuntime.runGameStep(1, () -> familyTimes.add(CombatSpeedRuntime.gameTime(world)));
            require(familyTimes.equals(List.of(physicalTime - 1, physicalTime)), "Family clocks must visit each logical time");
            require(world.getGameTime() == physicalTime && server.getTickCount() == serverTicks,
                    "Clock context must not invoke world/server ticks");
            require(CombatSpeedRuntime.gameTime(world) == physicalTime, "Family context must restore on exit");
            try {
                CombatSpeedRuntime.runGameStep(0, () -> {throw new IllegalStateException("test");});
            } catch (IllegalStateException expected) {
                require(CombatSpeedRuntime.gameTime(world) == physicalTime, "Failure must not leak family context");
            }
            CombatSpeedRuntime.clear();
            require(CombatSpeedRuntime.multiplier(world) == 1.0 && CombatSpeedRuntime.logicalSteps(world) == 1,
                    "Fallback must remove multiplier and timer budget");
        } finally {
            CombatSpeedRuntime.clear();
        }
        context.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new net.minecraft.gametest.framework.GameTestAssertException(Component.literal(message), 0);
        }
    }
}
