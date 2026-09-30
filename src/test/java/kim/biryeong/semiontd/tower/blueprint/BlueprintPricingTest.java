package kim.biryeong.semiontd.tower.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.tower.vfx.BuilderPalette;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.job.BlueprintTowerJob;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.TowerCapacity;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BlueprintPricingTest {
    private static final UUID OWNER = UUID.fromString("0a1b2c3d-0000-4000-8000-000000000001");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @AfterEach
    void clearBlueprints() {
        BlueprintStates.clearAll();
    }

    private static BlueprintStats stats(double health, double dps, double range) {
        return new BlueprintStats(health, dps, 20, range, 25, DamageType.PHYSICAL);
    }

    @Test
    void priceMatchesTheMedianTowerOfEachCostBracket() {
        // 기존 빌더 타워 가격대별 중앙값: 가격 50·130·260 → 체력 88·140·200, 초당 피해 8·16.7·26.7, 사거리 5.5·6.25·7.
        assertBetween(40, 60, BlueprintPricing.price(stats(88, 8.0, 5.5)));
        assertBetween(110, 150, BlueprintPricing.price(stats(140, 16.7, 6.25)));
        assertBetween(230, 300, BlueprintPricing.price(stats(200, 26.7, 7.0)));
    }

    @Test
    void strongerDesignsCostMoreThanProportionally() {
        long base = BlueprintPricing.price(stats(150, 15.0, 6.0));
        long doubled = BlueprintPricing.price(stats(300, 30.0, 6.0));
        assertTrue(doubled > base * 2.5, "Doubling every stat must more than double the price: " + base + " -> " + doubled);
        assertTrue(BlueprintPricing.price(stats(150, 15.0, 9.0)) > base, "More range must cost more.");
        assertTrue(BlueprintPricing.price(stats(400, 15.0, 6.0)) > base, "More health must cost more.");
        assertTrue(BlueprintPricing.price(new BlueprintStats(150, 15.0, 20, 6.0, 25, DamageType.TRUE)) > base,
                "True damage must cost more than physical damage.");
    }

    @Test
    void limitsRejectOutOfRangeStats() {
        assertTrue(BlueprintPricing.validate(stats(150, 15.0, 6.0)).isEmpty());
        assertTrue(BlueprintPricing.validate(stats(150, 15.0, 17.0)).isPresent(), "Range above the cap must be rejected.");
        assertTrue(BlueprintPricing.validate(stats(10, 15.0, 6.0)).isPresent(), "Health below the floor must be rejected.");
        assertTrue(BlueprintPricing.validate(new BlueprintStats(150, 15.0, 4, 6.0, 25, DamageType.PHYSICAL)).isPresent(),
                "Attack interval below the floor must be rejected.");
    }

    @Test
    void expensiveBlueprintsTakeMoreTowerSlots() {
        assertEquals(1, BlueprintPricing.slotCost(149));
        assertEquals(2, BlueprintPricing.slotCost(150));
        assertEquals(3, BlueprintPricing.slotCost(350));
    }

    @Test
    void namesAreTrimmedStrippedOfMarkupAndCapped() {
        assertEquals("redabc", BlueprintStates.sanitizeName("  <red>abc "));
        assertEquals("", BlueprintStates.sanitizeName("   "));
        assertEquals("", BlueprintStates.sanitizeName("12345678901234567"));
        assertEquals("장미 궁수", BlueprintStates.sanitizeName("장미 궁수"));
    }

    @Test
    void blueprintsRegisterAsOwnedStarterTowersAndSurviveCatalogReload() {
        String visual = BlueprintVisuals.options().getFirst().sourceTowerId();
        BlueprintStates.Creation creation = BlueprintStates.create(OWNER, "궁수", stats(120, 12.0, 7.0), visual);
        assertTrue(creation.success(), creation.message());
        Blueprint blueprint = creation.blueprint();
        assertTrue(blueprint.towerId().startsWith(BlueprintTowers.ID_PREFIX));
        assertEquals(blueprint.towerId().toLowerCase(java.util.Locale.ROOT), blueprint.towerId());

        ProductionTowerCatalog.CatalogEntry entry = ProductionTowerCatalog.find(blueprint.towerId()).orElseThrow();
        assertTrue(entry.starter());
        TowerType type = entry.type();
        assertEquals(blueprint.price(), type.mineralCost());
        assertEquals(120.0, type.maxHealth(), 1.0e-9);
        assertEquals(blueprint.slotCost(), TowerCapacity.slotCost(type));
        assertEquals(BuilderPalette.BLUEPRINT, TowerVfxService.paletteFor(type));
        BlueprintTowerJob job = (BlueprintTowerJob) JobRegistry.find(BlueprintTowerJob.ID).orElseThrow();
        assertTrue(job.includesTowerInCatalog(type));
        assertFalse(job.canUseTower(null, type), "Without a player context nobody may build a blueprint.");

        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        assertTrue(ProductionTowerCatalog.find(blueprint.towerId()).isPresent(), "A config reload must keep blueprints.");

        BlueprintStates.clear(OWNER);
        assertTrue(ProductionTowerCatalog.find(blueprint.towerId()).isEmpty(), "Clearing must remove the catalog entry.");
    }

    @Test
    void creationRejectsBadNamesStatsAndVisuals() {
        String visual = BlueprintVisuals.options().getFirst().sourceTowerId();
        assertEquals(BlueprintStates.Result.INVALID_NAME, BlueprintStates.create(OWNER, " ", stats(120, 12.0, 7.0), visual).result());
        assertEquals(BlueprintStates.Result.INVALID_STATS, BlueprintStates.create(OWNER, "a", stats(120, 12.0, 30.0), visual).result());
        assertEquals(BlueprintStates.Result.UNKNOWN_VISUAL, BlueprintStates.create(OWNER, "a", stats(120, 12.0, 7.0), "no_such_tower").result());
        assertTrue(BlueprintVisuals.options().stream().noneMatch(option -> BlueprintTowers.isBlueprintId(option.sourceTowerId())));
    }

    private static void assertBetween(long min, long max, long actual) {
        assertTrue(actual >= min && actual <= max, "Expected " + min + ".." + max + " but was " + actual);
    }
}
