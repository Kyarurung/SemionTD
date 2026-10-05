package kim.biryeong.semiontd.tower.magicschool;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.augment.*;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.*;

class MagicSchoolAugmentsTest {
    private final UUID owner = UUID.randomUUID();
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @BeforeEach @AfterEach void reset() {
        MagicSchoolCurriculum.clear(owner);
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @Test void silverUnlocksMuggleAndPrismUnlocksOnlyCursesWithoutCurriculumPrerequisites() {
        var student = wizard(MagicSchoolTowers.FRESHMAN);
        assertFalse(student.selectSpell(MagicSchoolSpell.MUGGLE_WAND));
        student.syncAugments(snapshot(MagicSchoolAugments.MUGGLE_WAND), null);
        assertTrue(student.selectSpell(MagicSchoolSpell.MUGGLE_WAND));
        assertEquals(20, student.resolveFinalAttackInterval(22));
        assertFalse(student.selectSpell(MagicSchoolSpell.STUPEFY));
        for (var type : MagicSchoolTowers.all().stream().filter(MagicSchoolTowers::isWizard).toList()) {
            var caster = wizard(type);
            assertFalse(caster.selectSpell(MagicSchoolSpell.CRUCIO));
            caster.syncAugments(snapshot(MagicSchoolAugments.UNFORGIVABLE_CURSES), null);
            for (var spell : MagicSchoolSpell.values()) {
                if (spell.tier() != 6) continue;
                assertEquals(caster.maxSpellTier() == 5, caster.selectSpell(spell), type.id() + ": " + spell);
            }
            assertFalse(caster.selectSpell(MagicSchoolSpell.RENNERVATE), "Prism does not unlock ordinary tier-five spells.");
        }
    }

    @Test void graduateMultipliesHistoryAndHouseGainsAndExtendsOnlyTheT3Cap() {
        var funds = new PlayerEconomy(EconomyConfig.defaultConfig());
        funds.addDiamond(1000);
        MagicSchoolCurriculum.purchase(owner, MagicSchoolCurriculum.Upgrade.MAGIC_HISTORY, funds);
        for (var type : MagicSchoolTowers.all().stream().filter(MagicSchoolTowers::isWizard).toList()) {
            var caster = wizard(type);
            double oldCap = caster.maxProficiency();
            caster.syncAugments(snapshot(MagicSchoolAugments.GRADUATE_SCHOOL), null);
            double house = MagicSchoolTowers.belongsToHouse(type, MagicSchoolTowers.RAVENCLAW) ? 1.15 : 1;
            assertEquals(10 * 1.3 * 1.3 * house, caster.gainProficiency(10, null), 1e-8);
            assertEquals(oldCap + (MagicSchoolTowers.isArchWizard(type) ? 100 : 0), caster.maxProficiency());
            caster.gainProficiency(10000, null);
            assertEquals(caster.maxProficiency(), caster.proficiency());
            if (MagicSchoolTowers.isArchWizard(type)) assertEquals(500, caster.proficiency());
        }
    }

    @Test void graduateBackfillsParametersAndHonorsCustomValuesWithoutEnablingTheGlobalPool() {
        var config = new AugmentConfig(false, false, null,
                Map.of(MagicSchoolAugments.GRADUATE_SCHOOL, Map.of("proficiencyGainBonus", .4)), Set.of());
        assertEquals(100, config.parameter(MagicSchoolAugments.GRADUATE_SCHOOL, "proficiencyCapBonus", 0));
        assertEquals(config, AugmentConfig.fromJson(config.toJson()));
        assertFalse(config.enabled());
        assertFalse(config.publicPoolEnabled());
        var caster = wizard(MagicSchoolTowers.WISE_ARCHWIZARD);
        caster.syncAugments(new AugmentSnapshot(config, snapshot(MagicSchoolAugments.GRADUATE_SCHOOL).selections()), null);
        assertEquals(10 * 1.15 * 1.4, caster.gainProficiency(10, null), 1e-8);
        for (double bad : new double[]{-1, 1.5}) {
            assertThrows(IllegalArgumentException.class, () -> new AugmentConfig(false, false, null,
                    Map.of(MagicSchoolAugments.GRADUATE_SCHOOL, Map.of("proficiencyCapBonus", bad)), Set.of()));
        }
    }

    static AugmentSnapshot snapshot(String... ids) {
        return new AugmentSnapshot(AugmentConfig.defaults(), Arrays.stream(ids).map(id ->
                new PlayerAugmentState.Selection(5, AugmentCatalog.find(id).orElseThrow().rarity(), id,
                        PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none())).toList());
    }

    private MagicSchoolWizardTower wizard(TowerType type) {
        return (MagicSchoolWizardTower) ProductionTowerCatalog.find(type.id()).orElseThrow()
                .create(owner, TeamId.RED, 1, new GridPosition(1, 64, 1));
    }
}
