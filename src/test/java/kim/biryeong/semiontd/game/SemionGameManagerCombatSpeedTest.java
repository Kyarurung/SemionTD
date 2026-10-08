package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import kim.biryeong.semiontd.config.CombatSpeedConfig;
import org.junit.jupiter.api.Test;

final class SemionGameManagerCombatSpeedTest {
    @Test
    void accelerationRequiresAnEnabledNormalWaveWithoutSandboxOrOverload() {
        CombatSpeedConfig enabled = new CombatSpeedConfig(true, 40.0F, 25.0);

        assertEquals(40.0F, SemionGameManager.combatTickRateTarget(enabled, RoundPhase.LANE_WAVE, false, false));
        assertEquals(20.0F, SemionGameManager.combatTickRateTarget(enabled, RoundPhase.PREPARE_AND_SUMMON, false, false));
        assertEquals(20.0F, SemionGameManager.combatTickRateTarget(enabled, RoundPhase.LANE_WAVE, true, false));
        assertEquals(20.0F, SemionGameManager.combatTickRateTarget(enabled, RoundPhase.LANE_WAVE, false, true));
        assertEquals(20.0F, SemionGameManager.combatTickRateTarget(CombatSpeedConfig.defaultConfig(), RoundPhase.LANE_WAVE, false, false));
    }

    @Test
    void arenaAccelerationPreservesTheRequestedCombatRateAtTwentyServerTps() {
        CombatSpeedConfig arena = new CombatSpeedConfig(true, 40.0F, 25.0);
        CombatSpeedConfig legacy = new CombatSpeedConfig(true, 40.0F, 25.0, CombatSpeedConfig.Mode.SERVER);

        assertEquals(CombatSpeedConfig.Mode.MULTIPLIER, arena.mode());
        assertEquals(20.0F, arena.serverTickRate(40.0F));
        assertEquals(40.0F, legacy.serverTickRate(40.0F));
        assertEquals(20.0F, legacy.serverTickRate(20.0F));
        assertEquals(40.0F, SemionGameManager.combatTickRateTarget(arena, RoundPhase.LANE_WAVE, false, false));
    }

    @Test
    void accelerationWaitsTwoSecondsAndRequiresTwentyPercentHeadroom() {
        CombatSpeedConfig enabled = new CombatSpeedConfig(true, 40.0F, 25.0);

        assertEquals(40, SemionGameManager.COMBAT_SPEED_ENTRY_DELAY_TICKS);
        assertEquals(20.0, SemionGameManager.combatSpeedEntryThresholdMillis(enabled));
        assertTrue(SemionGameManager.isCombatSpeedEntrySafe(enabled, 20.0));
        assertFalse(SemionGameManager.isCombatSpeedEntrySafe(enabled, 20.01));
    }

    @Test
    void overloadSkipsExactlyTheNextWave() {
        SemionGameManager manager = new SemionGameManager();

        manager.blockCombatSpeedForWave();

        assertTrue(manager.beginCombatSpeedWave());
        assertFalse(manager.beginCombatSpeedWave());
    }
}
