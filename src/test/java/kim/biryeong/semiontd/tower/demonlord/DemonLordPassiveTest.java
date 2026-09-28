package kim.biryeong.semiontd.tower.demonlord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

final class DemonLordPassiveTest {
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
    void passivesAreBoughtIntoSlotsEightAndNineAndRefundedInFull() {
        DemonLordState state = new DemonLordState(UUID.randomUUID());
        PlayerEconomy economy = economy(1000);
        long cleaveCost = DemonLordPassive.BLOOD_CLEAVE.cost();

        assertEquals(DemonLordSkillShop.Result.SUCCESS, DemonLordSkillShop.buyPassive(
                state, economy, DemonLordPassiveSlot.EIGHT, DemonLordPassive.BLOOD_CLEAVE));
        assertTrue(state.loadout().hasPassive(DemonLordPassive.BLOOD_CLEAVE));
        assertEquals(1000 - cleaveCost, economy.diamond());

        assertEquals(DemonLordSkillShop.Result.PASSIVE_ALREADY_SLOTTED, DemonLordSkillShop.buyPassive(
                state, economy, DemonLordPassiveSlot.NINE, DemonLordPassive.BLOOD_CLEAVE));
        assertEquals(DemonLordSkillShop.Result.SLOT_OCCUPIED, DemonLordSkillShop.buyPassive(
                state, economy, DemonLordPassiveSlot.EIGHT, DemonLordPassive.BOUNDLESS));
        assertEquals(DemonLordSkillShop.Result.SUCCESS, DemonLordSkillShop.buyPassive(
                state, economy, DemonLordPassiveSlot.NINE, DemonLordPassive.BOUNDLESS));
        assertTrue(state.boundless());

        assertEquals(DemonLordSkillShop.Result.SUCCESS,
                DemonLordSkillShop.removePassive(state, economy, DemonLordPassiveSlot.EIGHT));
        assertFalse(state.loadout().hasPassive(DemonLordPassive.BLOOD_CLEAVE));
        assertEquals(1000 - DemonLordPassive.BOUNDLESS.cost(), economy.diamond(), "Removing refunds everything paid.");
    }

    @Test
    void passivesCannotBeChangedDuringCombat() {
        DemonLordState state = new DemonLordState(UUID.randomUUID());
        PlayerEconomy economy = economy(1000);
        state.enterCombat();
        assertEquals(DemonLordSkillShop.Result.IN_COMBAT, DemonLordSkillShop.buyPassive(
                state, economy, DemonLordPassiveSlot.EIGHT, DemonLordPassive.LEGION_ECHO));
        assertEquals(1000, economy.diamond());
    }

    @Test
    void passivesSurviveTheStateBeingRebuilt() {
        UUID owner = UUID.randomUUID();
        DemonLordState state = DemonLordStates.getOrCreate(owner);
        PlayerEconomy economy = economy(1000);
        DemonLordSkillShop.buyPassive(state, economy, DemonLordPassiveSlot.NINE, DemonLordPassive.LEGION_ECHO);
        DemonLordStates.clear(owner);
        DemonLordState rebuilt = DemonLordStates.getOrCreate(owner);
        assertEquals(DemonLordPassive.LEGION_ECHO,
                rebuilt.loadout().passive(DemonLordPassiveSlot.NINE).orElseThrow().passive(),
                "A passive is paid for with diamonds and must not vanish on reconnect.");
    }

    @Test
    void everyPassivePublishesItsPriceInTheBalanceFile() {
        TowerBalanceConfig bundled = TowerBalanceConfig.defaultConfig();
        TowerBalanceConfig code = TowerBalanceConfig.codeDefaults();
        for (DemonLordPassive passive : DemonLordPassive.values()) {
            assertTrue(bundled.ability(passive.configId(), "cost", -1) > 0, passive + " must have a price");
            assertEquals(code.abilities().get(passive.configId()), bundled.abilities().get(passive.configId()),
                    passive + " drifted between code and the bundled resource");
            assertFalse(passive.description().isEmpty());
        }
        assertEquals(5, (int) bundled.ability(DemonLordPassive.LEGION_ECHO.configId(), "copies", -1));
    }

    private static PlayerEconomy economy(long diamonds) {
        PlayerEconomy economy = new PlayerEconomy(EconomyConfig.defaultConfig());
        economy.overrideStartingValues(diamonds, 0, 0, 0);
        return economy;
    }
}
