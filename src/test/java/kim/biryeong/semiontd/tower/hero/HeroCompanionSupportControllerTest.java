package kim.biryeong.semiontd.tower.hero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class HeroCompanionSupportControllerTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void bardPulseAndCooldownKeepTheirDifferentRoundLifetimes() {
        var controller = controller(HeroCompanionRole.BARD);
        controller.tick(null);
        assertEquals(1, controller.pulseCount());
        assertEquals(20, controller.cooldownTicks());
        for (int tick = 0; tick < 20; tick++) {
            controller.tick(null);
        }
        assertEquals(1, controller.pulseCount());
        assertEquals(0, controller.cooldownTicks());
        controller.tick(null);
        assertEquals(2, controller.pulseCount());
        controller.resetRound();
        assertEquals(0, controller.pulseCount());
        assertEquals(20, controller.cooldownTicks());
    }

    @Test
    void upgradeCopiesSupportProgressWithoutSharingMutableControllerState() {
        var source = controller(HeroCompanionRole.BARD);
        source.tick(null);
        source.tick(null);
        var upgraded = controller(HeroCompanionRole.BARD);
        upgraded.copyFrom(source);
        assertEquals(19, upgraded.cooldownTicks());
        assertEquals(1, upgraded.pulseCount());
        upgraded.resetRound();
        upgraded.tick(null);
        assertEquals(18, upgraded.cooldownTicks());
        assertEquals(0, upgraded.pulseCount());
        assertEquals(19, source.cooldownTicks());
        assertEquals(1, source.pulseCount());
    }

    @Test
    void combatOnlyCompanionsDoNotRunSupportCadence() {
        for (HeroCompanionRole role : List.of(HeroCompanionRole.ARCHER, HeroCompanionRole.MAGE, HeroCompanionRole.ROGUE)) {
            var controller = controller(role);
            for (int tick = 0; tick < 25; tick++) {
                controller.tick(null);
            }
            assertEquals(0, controller.cooldownTicks());
            assertEquals(0, controller.pulseCount());
        }
    }

    @Test
    void abilityDetailsKeepTierUnlocksAndFallbackFormatting() {
        for (HeroCompanionRole role : HeroCompanionRole.values()) {
            assertTrue(HeroCompanionStatsView.abilities(role, 1, "test:missing").isEmpty());
            assertEquals(1, HeroCompanionStatsView.abilities(role, 2, "test:missing").size());
            assertEquals(2, HeroCompanionStatsView.abilities(role, 3, "test:missing").size());
            assertEquals(2, HeroCompanionStatsView.abilities(role, 4, "test:missing").size());
        }
        assertEquals(List.of(HeroPartyTowers.firstAbilityName(HeroCompanionRole.KNIGHT)
                        + ": 4번째 공격, 이동/공격 속도 -25% (2초)"),
                HeroCompanionStatsView.abilities(HeroCompanionRole.KNIGHT, 2, "test:missing"));
    }

    private static HeroCompanionSupportController controller(HeroCompanionRole role) {
        var position = new GridPosition(0, 0, 0);
        return new HeroCompanionSupportController(new HeroCompanionTower(HeroPartyTowers.companion(role, 3),
                UUID.fromString("00000000-0000-0000-0000-000000000801"), TeamId.RED, 1, position, position));
    }
}
