package kim.biryeong.semiontd.tower.demonlord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class DemonLordSkillShopTest {
    private static final DemonLordSkill WAVE = DemonLordSkill.WAVE_OF_MALICE;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void reset() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        TowerBalanceRuntime.apply(defaults);
        ProductionTowerCatalogs.reloadBuiltIns(defaults);
        DemonLordStates.clearAllForTesting();
    }

    @Test
    void pricesComeFromTheOldAltarPlacementAndUpgradeCosts() {
        assertEquals(55, DemonLordSkillShop.purchaseCost(WAVE));
        for (int tier = 1; tier < DemonLordSkill.MAX_TIER; tier++) {
            assertEquals(TowerBalanceRuntime.upgradeCost(DemonLordTowers.tower(WAVE, tier),
                            DemonLordTowers.tower(WAVE, tier + 1).id()),
                    DemonLordSkillShop.upgradeCost(WAVE, tier));
        }
        assertEquals(0, DemonLordSkillShop.upgradeCost(WAVE, DemonLordSkill.MAX_TIER));
    }

    @Test
    void buyingPutsTierOneInTheChosenSlotAndChargesDiamonds() {
        DemonLordState state = new DemonLordState(UUID.randomUUID());
        PlayerEconomy economy = economy(1000);

        assertEquals(DemonLordSkillShop.Result.SUCCESS,
                DemonLordSkillShop.buy(state, economy, DemonLordBinding.OFFHAND, WAVE));

        DemonLordLoadout.Slot slot = state.loadout().slot(DemonLordBinding.OFFHAND).orElseThrow();
        assertEquals(WAVE, slot.skill());
        assertEquals(1, slot.tier());
        assertEquals(55, slot.paid());
        assertEquals(1000 - 55, economy.diamond());
        assertTrue(state.loadout().slot(DemonLordBinding.SLOT_1).isEmpty(),
                "The skill goes where the player put it, not into the first free key.");
    }

    @Test
    void aSkillLivesInOneSlotAndASlotHoldsOneSkill() {
        DemonLordState state = new DemonLordState(UUID.randomUUID());
        PlayerEconomy economy = economy(1000);
        DemonLordSkillShop.buy(state, economy, DemonLordBinding.SLOT_1, WAVE);

        assertEquals(DemonLordSkillShop.Result.SKILL_ALREADY_SLOTTED,
                DemonLordSkillShop.buy(state, economy, DemonLordBinding.SLOT_2, WAVE));
        assertEquals(DemonLordSkillShop.Result.SLOT_OCCUPIED,
                DemonLordSkillShop.buy(state, economy, DemonLordBinding.SLOT_1, DemonLordSkill.DEMON_WINGS));
        assertEquals(1000 - 55, economy.diamond(), "Rejected purchases must not charge.");
    }

    @Test
    void notEnoughDiamondsChangesNothing() {
        DemonLordState state = new DemonLordState(UUID.randomUUID());
        PlayerEconomy economy = economy(10);

        assertEquals(DemonLordSkillShop.Result.NOT_ENOUGH_DIAMOND,
                DemonLordSkillShop.buy(state, economy, DemonLordBinding.SLOT_1, WAVE));
        assertTrue(state.loadout().isEmpty());
        assertEquals(10, economy.diamond());
    }

    @Test
    void upgradingStacksThePaidAmountAndRemovingRefundsAllOfIt() {
        DemonLordState state = new DemonLordState(UUID.randomUUID());
        PlayerEconomy economy = economy(5000);
        DemonLordSkillShop.buy(state, economy, DemonLordBinding.SLOT_3, WAVE);
        long expectedPaid = DemonLordSkillShop.purchaseCost(WAVE);
        for (int tier = 1; tier < DemonLordSkill.MAX_TIER; tier++) {
            expectedPaid += DemonLordSkillShop.upgradeCost(WAVE, tier);
            assertEquals(DemonLordSkillShop.Result.SUCCESS,
                    DemonLordSkillShop.upgrade(state, economy, DemonLordBinding.SLOT_3));
        }
        DemonLordLoadout.Slot slot = state.loadout().slot(DemonLordBinding.SLOT_3).orElseThrow();
        assertEquals(DemonLordSkill.MAX_TIER, slot.tier());
        assertEquals(expectedPaid, slot.paid());
        assertEquals(DemonLordSkillShop.Result.MAX_TIER,
                DemonLordSkillShop.upgrade(state, economy, DemonLordBinding.SLOT_3));

        assertEquals(DemonLordSkillShop.Result.SUCCESS,
                DemonLordSkillShop.remove(state, economy, DemonLordBinding.SLOT_3));
        assertEquals(5000, economy.diamond(), "Removing a skill refunds every diamond put into the slot.");
        assertTrue(state.loadout().isEmpty());
        assertEquals(DemonLordSkillShop.Result.SLOT_EMPTY,
                DemonLordSkillShop.remove(state, economy, DemonLordBinding.SLOT_3));
    }

    @Test
    void theLoadoutIsLockedWhileFighting() {
        DemonLordState state = new DemonLordState(UUID.randomUUID());
        PlayerEconomy economy = economy(5000);
        DemonLordSkillShop.buy(state, economy, DemonLordBinding.SLOT_1, WAVE);
        state.enterCombat();

        assertEquals(DemonLordSkillShop.Result.IN_COMBAT,
                DemonLordSkillShop.buy(state, economy, DemonLordBinding.SLOT_2, DemonLordSkill.DEMON_WINGS));
        assertEquals(DemonLordSkillShop.Result.IN_COMBAT,
                DemonLordSkillShop.upgrade(state, economy, DemonLordBinding.SLOT_1));
        assertEquals(DemonLordSkillShop.Result.IN_COMBAT,
                DemonLordSkillShop.remove(state, economy, DemonLordBinding.SLOT_1));

        state.standDown();
        assertEquals(DemonLordSkillShop.Result.SUCCESS,
                DemonLordSkillShop.remove(state, economy, DemonLordBinding.SLOT_1));
    }

    /** 스킬은 다이아로 산 것이라, 상태가 버려졌다 되살아나도 그대로 남아야 합니다. */
    @Test
    void loadoutAndIncomeSettingsSurviveStateTeardown() {
        UUID playerId = UUID.randomUUID();
        DemonLordState state = DemonLordStates.getOrCreate(playerId);
        DemonLordSkillShop.buy(state, economy(1000), DemonLordBinding.DROP, DemonLordSkill.SOUL_DRAIN);
        state.setAutoIncomeThreshold(0.45);
        state.setAutoIncomeEnabled(false);

        DemonLordStates.clear(playerId);
        DemonLordState rebuilt = DemonLordStates.getOrCreate(playerId);

        assertEquals(DemonLordSkill.SOUL_DRAIN,
                rebuilt.loadout().slot(DemonLordBinding.DROP).orElseThrow().skill());
        assertEquals(0.45, rebuilt.autoIncomeThreshold(), 1.0E-9);
        assertFalse(rebuilt.autoIncomeEnabled());

        DemonLordStates.clear(playerId);
        DemonLordStates.resetProgression(playerId);
        assertTrue(DemonLordStates.getOrCreate(playerId).loadout().isEmpty(), "A new match starts empty.");
    }

    @Test
    void autoIncomeDefaultsToSeventyPercentOfTheCap() {
        DemonLordState state = new DemonLordState(UUID.randomUUID());
        assertEquals(0.7, state.autoIncomeThreshold(), 1.0E-9);
        assertTrue(state.autoIncomeEnabled());

        assertFalse(DemonLordIncome.shouldSend(699, 1000, 0.7));
        assertTrue(DemonLordIncome.shouldSend(700, 1000, 0.7));
        assertTrue(DemonLordIncome.shouldSend(0, 1000, 0.0));
        assertFalse(DemonLordIncome.shouldSend(999, 1000, 1.0));
        assertFalse(DemonLordIncome.shouldSend(5000, 0, 0.5), "No cap means nothing to measure against.");
    }

    @Test
    void autoIncomePicksTheMostExpensiveIncomeUnitItCanAfford() {
        record Unit(String id, long cost, long income) {
        }
        List<Unit> units = List.of(
                new Unit("cheap", 50, 5),
                new Unit("mid", 200, 20),
                new Unit("mid_better", 200, 25),
                new Unit("utility", 250, 0),
                new Unit("expensive", 800, 90));

        assertEquals("mid_better", DemonLordIncome.mostExpensiveAffordable(units, 300, Unit::cost, Unit::income)
                .orElseThrow().id(), "Highest cost wins, ties go to more income, zero-income units are skipped.");
        assertEquals("expensive", DemonLordIncome.mostExpensiveAffordable(units, 800, Unit::cost, Unit::income)
                .orElseThrow().id());
        assertTrue(DemonLordIncome.mostExpensiveAffordable(units, 49, Unit::cost, Unit::income).isEmpty());
    }

    private static PlayerEconomy economy(long diamonds) {
        PlayerEconomy economy = new PlayerEconomy(EconomyConfig.defaultConfig());
        economy.overrideStartingValues(diamonds, 0, 0, 0);
        return economy;
    }
}
