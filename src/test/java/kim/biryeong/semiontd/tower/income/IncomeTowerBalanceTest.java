package kim.biryeong.semiontd.tower.income;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import kim.biryeong.semiontd.config.SummonConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class IncomeTowerBalanceTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void levelCostsIncomeAndStatsGrowPerLevel() {
        assertEquals(100, IncomeTowerBalance.upgradeCost(100, 1));
        assertEquals(150, IncomeTowerBalance.upgradeCost(100, 2));
        assertEquals(250, IncomeTowerBalance.upgradeCost(100, 4));
        assertEquals(3, IncomeTowerBalance.incomeAt(3, 1));
        assertEquals(15, IncomeTowerBalance.incomeAt(3, 5));
        assertEquals(0.85, IncomeTowerBalance.statMultiplier(1), 1.0E-9);
        assertEquals(0.85 * 2.4, IncomeTowerBalance.statMultiplier(5), 1.0E-9);
        assertEquals(55, IncomeTowerBalance.sellRefund(110));
    }

    @Test
    void waveScaleFollowsLaneMonstersWithoutBossSpikesOrDips() {
        WaveConfig waves = WaveConfig.defaultConfig();
        assertEquals(IncomeTowerBalance.WaveScale.NONE, IncomeTowerBalance.waveScale(waves, 1));
        double previousHealth = 1.0;
        double previousAttack = 1.0;
        for (int round = 2; round <= 30; round++) {
            IncomeTowerBalance.WaveScale scale = IncomeTowerBalance.waveScale(waves, round);
            assertTrue(scale.health() >= previousHealth, "Health scale must never drop at round " + round);
            assertTrue(scale.attackDamage() >= previousAttack, "Attack scale must never drop at round " + round);
            previousHealth = scale.health();
            previousAttack = scale.attackDamage();
        }
        IncomeTowerBalance.WaveScale round10 = IncomeTowerBalance.waveScale(waves, 10);
        IncomeTowerBalance.WaveScale round15 = IncomeTowerBalance.waveScale(waves, 15);
        IncomeTowerBalance.WaveScale round19 = IncomeTowerBalance.waveScale(waves, 19);
        assertTrue(round10.health() > 1.5, "Lane monsters are clearly tougher by round 10.");
        assertTrue(round19.health() > round10.health(), "Scaling keeps rising into the late rounds.");
        assertTrue(round15.health() < 20.0, "A single boss round must not blow up the income unit scale.");
    }

    /** 비싼 유닛일수록 타워 수를 더 차지하고, 번들 설정과 코드 기본값이 같습니다. */
    @Test
    void expensiveIncomeTowersTakeMoreTowerSlots() {
        kim.biryeong.semiontd.config.TowerBalanceConfig bundled = kim.biryeong.semiontd.config.TowerBalanceConfig.defaultConfig();
        kim.biryeong.semiontd.config.TowerBalanceConfig code = kim.biryeong.semiontd.config.TowerBalanceConfig.codeDefaults();
        for (String id : IncomeTowerBalance.UNIT_IDS) {
            String towerId = IncomeTowerBalance.towerId(id);
            double slots = bundled.ability(towerId, kim.biryeong.semiontd.tower.TowerCapacity.CONFIG_KEY, -1);
            assertTrue(slots >= 1.0, id + " must take at least one slot");
            assertEquals(code.abilities().get(towerId), bundled.abilities().get(towerId), id + " drifted between code and bundle");
        }
        assertEquals(1.0, bundled.ability(IncomeTowerBalance.towerId("goblin_scout"), "towerSlotCost", -1));
        assertEquals(3.0, bundled.ability(IncomeTowerBalance.towerId("ogre_champion"), "towerSlotCost", -1));
    }

    @Test
    void everyIncomeTowerUnitHasAnInvasionModelInTheDefaults() {
        SummonConfig defaults = SummonConfig.defaultConfig();
        for (String id : IncomeTowerBalance.UNIT_IDS) {
            SummonConfig.SummonDefinition definition = defaults.summons().get(id);
            assertTrue(definition != null && definition.enabled(), "Missing default summon " + id);
            if ("creaking".equals(id)) {
                // The creaking keeps the vanilla model instead of a Blockbench one.
                assertEquals("minecraft:creaking", definition.entityTypeId());
                assertEquals(null, definition.blockbenchModelId());
            } else {
                assertEquals("semion-td:invasion/" + id, definition.blockbenchModelId());
            }
            assertTrue(definition.emeraldCost() > 0 && definition.incomeGain() > 0, "Unit " + id + " must cost and pay.");
        }
    }
}
