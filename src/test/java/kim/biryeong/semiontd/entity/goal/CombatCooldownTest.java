package kim.biryeong.semiontd.entity.goal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CombatCooldownTest {
    @Test
    void batchedEventsKeepAlternatingGoalTimestampOffsets() {
        CombatCooldown cooldown = new CombatCooldown();
        cooldown.advance(5.0);
        assertEquals(0.0, cooldown.eventOffset(2.0));
        cooldown.restart(3.0);
        assertEquals(6.0, cooldown.eventOffset(2.0));
        cooldown.restart(3.0);
        cooldown.advance(5.0);
        assertEquals(2.0, cooldown.eventOffset(2.0));
        cooldown.restart(3.0);
        assertEquals(8.0, cooldown.eventOffset(2.0));
    }

    @Test
    void doubledProgressPreservesOddIntervalAttackCounts() {
        for (int interval : new int[]{1, 3, 7, 13, 21}) {
            assertEquals(attacks(400, 1.0, interval), attacks(200, 2.0, interval));
        }
    }

    @Test
    void fractionalProgressRetainsRemaindersInsteadOfRoundingCooldowns() {
        for (int interval : new int[]{1, 3, 13}) {
            assertEquals(attacks(600, 1.0, interval), attacks(400, 1.5, interval));
            assertEquals(attacks(900, 1.0, interval), attacks(400, 2.25, interval));
        }
    }

    @Test
    void switchingRatesKeepsAlreadyElapsedCooldown() {
        CombatCooldown cooldown = new CombatCooldown();
        int actual = 0;
        for (int frame = 0; frame < 120; frame++) {
            actual += advance(cooldown, frame < 60 ? 1.5 : 2.0, 13);
        }
        assertEquals(attacks(210, 1.0, 13), actual);
    }

    @Test
    void missedTargetsDoNotBankUnlimitedAttacks() {
        CombatCooldown cooldown = new CombatCooldown();
        for (int frame = 0; frame < 1000; frame++) {
            cooldown.advance(5.0);
        }
        assertEquals(5, advance(cooldown, 5.0, 1));
    }

    @Test
    void fastAttacksAreBoundedWithoutLosingSupportedRate() {
        CombatCooldown cooldown = new CombatCooldown();
        for (int frame = 0; frame < 200; frame++) {
            assertEquals(5, advance(cooldown, 5.0, 1));
        }
        assertTrue(advance(cooldown, Double.POSITIVE_INFINITY, 1) <= CombatCooldown.MAX_EVENTS);
    }

    private static int attacks(int frames, double multiplier, int interval) {
        CombatCooldown cooldown = new CombatCooldown();
        int attacks = 0;
        for (int frame = 0; frame < frames; frame++) {
            attacks += advance(cooldown, multiplier, interval);
        }
        return attacks;
    }

    private static int advance(CombatCooldown cooldown, double multiplier, int interval) {
        cooldown.advance(multiplier);
        int count = 0;
        while (cooldown.ready() && count < CombatCooldown.MAX_EVENTS) {
            cooldown.restart(interval);
            count++;
        }
        return count;
    }
}
