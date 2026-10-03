package kim.biryeong.semiontd.tower.warlock;

import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.warlock.WarlockSacrificeTower;
import kim.biryeong.semiontd.tower.warlock.WarlockTower;
import kim.biryeong.semiontd.tower.warlock.WarlockTowers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import kim.biryeong.semiontd.tower.TowerCoreAugmentFixture;

public final class WarlockTowerAugmentCombatTest extends TowerCoreAugmentFixture {
    @GameTest
    public void warlockAbsorptionFiresOnceAndSharesOnlyGrowth(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context, "job_warlock_towers_s", "job_warlock_towers_g1", "job_warlock_towers_p")) {
            WarlockTower core = fixture.warlock(0);
            WarlockTower partner = fixture.warlock(1);
            WarlockSacrificeTower donor = new WarlockSacrificeTower(WarlockTowers.T1_RANGED_SLAVE,
                    fixture.owner, TeamId.RED, 1, fixture.position(2));
            fixture.add(donor);
            double donatedHealth = donor.currentMaxHealth();
            double donatedDamage = donor.sacrificeAttackDamage();
            SemionMonsterEntity target = fixture.target(fixture.entity(donor).position().add(0, 0, .5), 1000);
            SemionTowerEntity source = fixture.entity(core);
            source.setHealth(1);
            core.syncHealth(1);
            core.onDamaged(source, context.getLevel().damageSources().generic(), 1, 2, 1);
            require(donor.health() == 0, "The sacrifice is committed once.");
            requireClose(donatedDamage * 3, core.roundPhysicalDamageDealt(), "Testament uses the donor's physical damage type.");
            requireClose(donatedHealth, core.roundMagicDamageDealt(), "Only the absorbing core explodes the sacrifice.");
            requireClose(1000 - donatedDamage * 3 - donatedHealth, target.runtimeMonster().health(), "Both effects damage the target once.");
            requireClose(core.currentMaxHealth(), partner.currentMaxHealth(), "The partner receives the committed growth.");
            requireClose(0, partner.roundDamageDealt(), "Sharing never repeats testament or explosion.");
            context.succeed();
        }
    }

    @GameTest
    public void trueAwakeningUnlocksAtSixtyPercentWithAnotherCoreAlive(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context, "job_warlock_towers_g2")) {
            WarlockTower core = fixture.warlock(0);
            fixture.warlock(1);
            SemionTowerEntity source = fixture.entity(core);
            source.setHealth((float) (core.currentMaxHealth() * .60));
            core.syncHealth(source.getHealth());
            core.onDamaged(source, context.getLevel().damageSources().generic(), 1,
                    core.currentMaxHealth(), source.getHealth());
            requireClose(.20, core.finalDamageBonus(), "Awakening is unlocked without kills and ignores the living ally.");
            core.resetForRound(fixture.lane);
            requireClose(0, core.finalDamageBonus(), "The awakening damage bonus ends with the round.");
            context.succeed();
        }
    }
}
