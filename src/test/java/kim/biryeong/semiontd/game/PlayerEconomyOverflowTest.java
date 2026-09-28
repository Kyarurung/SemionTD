package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import kim.biryeong.semiontd.config.EconomyConfig;
import org.junit.jupiter.api.Test;

class PlayerEconomyOverflowTest {
    @Test
    void productionPastTheCapIsBankedAndSpentFirstButNeverRaisesWhatIsAffordable() {
        PlayerEconomy economy = new PlayerEconomy(EconomyConfig.defaultConfig());
        economy.addEmeraldWithOverflow(40, 50);
        assertEquals(40, economy.emerald());
        assertEquals(0, economy.emeraldOverflow());

        economy.addEmeraldWithOverflow(130, 50);
        assertEquals(50, economy.emerald(), "The spendable balance stays at the cap.");
        assertEquals(120, economy.emeraldOverflow(), "Production past the cap is banked.");

        assertFalse(economy.spendEmerald(60), "A purchase above the cap stays unaffordable.");
        assertTrue(economy.spendEmerald(50));
        assertEquals(50, economy.emerald(), "Spending draws the banked overflow first.");
        assertEquals(70, economy.emeraldOverflow());

        assertTrue(economy.spendEmerald(50));
        assertTrue(economy.spendEmerald(50));
        assertEquals(0, economy.emeraldOverflow());
        assertEquals(20, economy.emerald(), "Once the bank is empty the balance itself is spent.");
    }

    @Test
    void ordinaryCappedProductionStillDiscardsTheExcess() {
        PlayerEconomy economy = new PlayerEconomy(EconomyConfig.defaultConfig());
        economy.addEmerald(130, 50);
        assertEquals(50, economy.emerald());
        assertEquals(0, economy.emeraldOverflow());
    }
}
