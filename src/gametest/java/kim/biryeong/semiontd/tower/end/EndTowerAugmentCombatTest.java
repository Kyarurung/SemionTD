package kim.biryeong.semiontd.tower.end;

import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.TowerType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import kim.biryeong.semiontd.tower.TowerCoreAugmentFixture;

public final class EndTowerAugmentCombatTest extends TowerCoreAugmentFixture {
    @GameTest
    public void voidMineSurvivesCoreDeathAndUsesEightTargetCap(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context, EndAugments.MINE)) {
            EndTower core = fixture.end(EndTowers.BASE_END_TOWER);
            core.onWaveStarted(fixture.lane, 5);
            core.tick(fixture.lane);
            EndAugments effects = new EndAugments();
            effects.onTransferCompleted(core, core, 100);
            effects.tickMines(core, fixture.lane);
            Vec3 center = new Vec3(core.position().x() + .5, core.position().y() + 1, core.position().z() + .5);
            for (int i = 0; i < 9; i++) {fixture.target(center.add(.1 * i, 0, .5), 1000);}
            fixture.lane.killTower(core);
            effects.tickMines(core, fixture.lane);
            requireClose(800, core.roundMagicDamageDealt(), "A stored mine survives the core's combat death and hits eight targets.");
            require(effects.mineCount() == 0, "The mine is consumed once.");
            effects.tickMines(core, fixture.lane);
            requireClose(800, core.roundMagicDamageDealt(), "A consumed mine cannot explode twice.");
            context.succeed();
        }
    }

    @GameTest
    public void growthHitsNativeSecondaryTargetsOnceAndExtraAttacksCannotConsumeItsCharge(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context, EndAugments.GROWTH)) {
            EndTower core = fixture.end(EndTowers.BASE_END_TOWER);
            for (int offset = 1; offset <= 4; offset++) {
                fixture.add(new EndTower(EndTowers.T3_END_CRYSTAL_TOWER, fixture.owner, TeamId.RED, 1,
                        fixture.position(offset)));
            }
            core.onWaveStarted(fixture.lane, 5);
            int ticks = (int) Math.ceil(EndConfig.RUNTIME.transfer().durationTicks() * core.transferDurationMultiplier());
            for (int tick = 0; tick < ticks; tick++) {core.tick(fixture.lane);}
            require(core.transferStats().endCrystalCount() == 12, "Four native crystal transfers must unlock splash.");
            require(core.runtimeDetailLines().stream().anyMatch(line -> line.contains("전달 4기 / 추가 피해 충전")),
                    "Three completed transfers must leave one charge for the next ordinary attack.");
            SemionTowerEntity source = fixture.entity(core);
            SemionMonsterEntity primary = fixture.target(source.position().add(1, 0, 0), 1000);
            SemionMonsterEntity secondary = fixture.target(primary.position().add(0, 0, core.splashRadius() * .5), 1000);
            SemionMonsterEntity outside = fixture.target(primary.position().add(0, 0, core.splashRadius() + .25), 1000);
            double attackDamage = source.attackDamageAmount(primary);
            double nativeDamage = core.resolveBasicAttackOutgoingDamage(source, primary, attackDamage);
            double splashDamage = nativeDamage * EndConfig.RUNTIME.splash().damageRatio();
            double growthDamage = core.resolveBasicAttackOutgoingDamage(source, primary, attackDamage * 1.5);

            AugmentCombat.additionalAttack(source, primary, 1);
            requireClose(nativeDamage, 1000 - primary.runtimeMonster().health(), "An extra attack retains its native primary damage.");
            requireClose(splashDamage, 1000 - secondary.runtimeMonster().health(), "An extra attack retains native splash targeting.");
            requireClose(0, core.roundMagicDamageDealt(), "An augment extra attack must not trigger growth magic damage.");
            require(core.runtimeDetailLines().stream().anyMatch(line -> line.contains("추가 피해 충전")),
                    "An augment extra attack must preserve the pending growth charge.");

            attack(source, primary);
            requireClose(nativeDamage * 2 + growthDamage, 1000 - primary.runtimeMonster().health(),
                    "The ordinary primary target must take exactly 150% extra magic damage.");
            requireClose(splashDamage * 2 + growthDamage, 1000 - secondary.runtimeMonster().health(),
                    "The native secondary target must receive the same 150% extra magic damage.");
            requireClose(growthDamage * 2, core.roundMagicDamageDealt(), "Growth damage must be attributed as magic for both native targets.");
            require(core.runtimeDetailLines().stream().anyMatch(line -> line.contains("추가 피해 대기")),
                    "The ordinary attack must consume the stored charge.");

            attack(source, primary);
            requireClose(nativeDamage * 3 + growthDamage, 1000 - primary.runtimeMonster().health(), "The consumed charge cannot hit the primary again.");
            requireClose(splashDamage * 3 + growthDamage, 1000 - secondary.runtimeMonster().health(), "The consumed charge cannot hit the secondary again.");
            requireClose(growthDamage * 2, core.roundMagicDamageDealt(), "A consumed charge cannot produce more magic damage.");
            requireClose(1000, outside.runtimeMonster().health(), "Growth cannot expand the native secondary target set.");
            context.succeed();
        }
    }

    @GameTest
    public void breathFiresTwiceFourTicksApartAndReselectsInsideRange(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context, EndAugments.BREATH)) {
            TowerType base = EndTowers.BASE_END_TOWER;
            TowerType giant = new TowerType(base.id(), base.displayName(), base.category(), base.mineralCost(),
                    1_000_000, base.range(), base.damage(), base.attackIntervalTicks(), base.aggroPriority(),
                    base.description(), base.visual(), base.upgradeOptions());
            EndTower core = fixture.end(giant);
            core.onWaveStarted(fixture.lane, 5);
            core.tick(fixture.lane);
            require(core.state() == EndTowerState.DRAGON, "The large core evolves before the breath timer.");
            SemionTowerEntity source = fixture.entity(core);
            SemionMonsterEntity first = fixture.target(source.position().add(2, 0, 0), 1000);
            source.recordCurrentAttackTarget(first);
            for (int i = 1; i < 120; i++) {core.tick(fixture.lane);}
            double firstShot = core.roundMagicDamageDealt();
            require(firstShot > 0, "The first breath fires at six seconds.");
            first.setPos(source.position().add(100, 0, 0));
            SemionMonsterEntity replacement = fixture.target(source.position().add(0, 0, 2), 1000);
            for (int i = 0; i < 3; i++) {core.tick(fixture.lane);}
            requireClose(firstShot, core.roundMagicDamageDealt(), "The second breath waits four ticks.");
            core.tick(fixture.lane);
            require(replacement.runtimeMonster().health() < 1000, "The second shot reselects a target inside native range.");
            requireClose(firstShot * 2, core.roundMagicDamageDealt(), "Exactly two equal breaths fired.");
            core.tick(fixture.lane);
            requireClose(firstShot * 2, core.roundMagicDamageDealt(), "The card name does not create a third breath.");
            context.succeed();
        }
    }

    private static void attack(SemionTowerEntity source, SemionMonsterEntity target) {
        double damage = source.attackDamageAmount(target);
        var result = source.damageTargetResult(target, damage);
        source.recordAttack(target, damage, result.outgoingDamage(), result.dealtDamage(), result.killed());
    }
}
