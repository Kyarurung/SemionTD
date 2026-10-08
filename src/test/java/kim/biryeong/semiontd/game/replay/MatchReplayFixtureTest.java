package kim.biryeong.semiontd.game.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kim.biryeong.semiontd.buildguide.BuildAction;
import kim.biryeong.semiontd.buildguide.BuildActionType;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.SummonConfig;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.summon.IncomeSummons;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.web.WebCatalogExporter;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class MatchReplayFixtureTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void ledgerPreservesAllActionsAndExactIntegerIds() {
        MatchReplayFixture fixture = MatchReplayFixture.load();
        JsonObject document = fixture.document();
        assertEquals(893854454113494679L, Long.parseLong(document.get("match_id").getAsString()));
        assertNotEquals(893854454113494679L, (long) Double.parseDouble(document.get("match_id").getAsString()));
        int total = 0;
        Map<BuildActionType, Integer> counts = new LinkedHashMap<>();
        for (var value : document.getAsJsonArray("participants")) {
            String slot = value.getAsJsonObject().get("slot").getAsString();
            List<MatchReplayFixture.RecordedAction> actions = fixture.actions(slot);
            total += actions.size();
            for (var action : actions) {
                counts.merge(action.action().type(), 1, Integer::sum);
            }
        }
        assertEquals(1865, total);
        assertEquals(Map.of(BuildActionType.TOWER_PLACE, 417, BuildActionType.TOWER_UPGRADE, 432,
                BuildActionType.TOWER_SELL, 37, BuildActionType.SUMMON, 731,
                BuildActionType.EMERALD_PRODUCTION_UPGRADE, 248), counts);
        assertTrue(document.get("round_metrics_complete").getAsBoolean());
        assertEquals(2073, document.getAsJsonArray("round_metrics").size());
        assertFalse(document.toString().contains("player_name"));
        assertFalse(document.toString().contains("player_id"));
        assertFalse(document.toString().contains("build_code"));
    }

    @Test
    void engineerFirstRoundLayoutMatchesRecordedRoundStartCounts() {
        MatchReplayFixture fixture = MatchReplayFixture.load();
        var actions = towerInputs(fixture, "p02", 1);
        var layout = fixture.reconstructTowerInputs(actions, Map.of());
        assertEquals(131L, layout.spent());
        assertEquals(5, layout.towers().size());
        assertEquals(List.of(), layout.costDifferences());
        Map<String, Long> counts = new LinkedHashMap<>();
        layout.towers().values().forEach(tower -> counts.merge(tower.type(), 1L, Long::sum));
        Map<String, Long> observed = new LinkedHashMap<>();
        fixture.document().getAsJsonArray("round_metrics").forEach(value -> {
            JsonObject row = value.getAsJsonObject();
            if ("p02".equals(row.get("slot").getAsString()) && row.get("round_number").getAsInt() == 1) {
                observed.put(row.get("tower_type_id").getAsString(), row.get("start_count").getAsLong());
            }
        });
        assertEquals(observed, counts);
        assertEquals(Map.of("engineer_plate_wood", 2L, "engineer_copper_golem", 1L,
                "engineer_door_t1", 1L, "engineer_dispenser_t1", 1L), counts);
    }

    @Test
    void warlockUpgradeUsesDirectedEdgeAndRetainsControlledIdentity() {
        MatchReplayFixture fixture = MatchReplayFixture.load();
        var actions = towerInputs(fixture, "p00", 1);
        var layout = fixture.reconstructTowerInputs(actions, Map.of());
        var upgraded = layout.towers().values().stream().filter(tower -> tower.type().equals("ranged_warlock_tower"))
                .findFirst().orElseThrow();
        assertEquals("p00/placement/1", upgraded.scenarioId());
        assertEquals(0L, upgraded.paid());
        assertEquals(110L, layout.spent());
        assertTrue(layout.costDifferences().isEmpty());
    }

    @Test
    void laterEngineerSaleUsesRecordedRefundWithoutInventingDeaths() {
        MatchReplayFixture fixture = MatchReplayFixture.load();
        var all = fixture.actions("p02");
        var layout = fixture.reconstructTowerInputs(List.of(all.get(0), all.get(10)), Map.of());
        assertEquals(8L, layout.spent());
        assertEquals(4L, layout.refunded());
        assertTrue(layout.towers().isEmpty());
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> fixture.reconstructTowerInputs(List.of(all.get(10)), Map.of())).getMessage().contains("sale source"));
    }

    @Test
    void refusesUnknownSourceOccupiedColumnAndNonTowerInputs() {
        MatchReplayFixture fixture = MatchReplayFixture.load();
        var actions = fixture.actions("p00");
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> fixture.reconstructTowerInputs(List.of(actions.get(2)), Map.of())).getMessage().contains("no source tower"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> fixture.reconstructTowerInputs(List.of(actions.get(3), actions.get(3)), Map.of())).getMessage().contains("occupied"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> fixture.reconstructTowerInputs(List.of(actions.get(5)), Map.of())).getMessage().contains("SUMMON"));
    }

    @Test
    void laneRelativeCoordinatesRequireExplicitOrigin() {
        var action = MatchReplayFixture.load().actions("p00").get(3);
        assertEquals(new GridPosition(38, -1, 3), action.action().position());
        assertEquals(new GridPosition(-12, 144, 50),
                MatchReplayFixture.absolutePosition(action, new GridPosition(-50, 145, 47)));
        assertThrows(NullPointerException.class, () -> MatchReplayFixture.absolutePosition(action, null));
        assertThrows(ArithmeticException.class,
                () -> MatchReplayFixture.absolutePosition(action, new GridPosition(Integer.MAX_VALUE, 0, 0)));
    }

    @Test
    void refusesCorruptOrderingPartialCoordinatesAndNegativeCostsBeforeBuildActionClampsThem() {
        for (String field : List.of("sequence", "position_y", "cost")) {
            JsonObject document = MatchReplayFixture.load().document();
            JsonObject first = document.getAsJsonArray("participants").get(2).getAsJsonObject()
                    .getAsJsonArray("actions").get(0).getAsJsonObject();
            if (field.equals("position_y")) {
                first.add(field, com.google.gson.JsonNull.INSTANCE);
            } else {
                first.addProperty(field, -1);
            }
            assertThrows(IllegalArgumentException.class, () -> new MatchReplayFixture(document).actions("p02"), field);
        }
    }

    @Test
    void catalogCostDifferencesRemainDiagnosticsAndRecordedSpendRemainsExact() {
        MatchReplayFixture fixture = MatchReplayFixture.load();
        var recorded = fixture.actions("p02").get(0);
        BuildAction action = recorded.action();
        var discounted = new MatchReplayFixture.RecordedAction(recorded.slot(), recorded.sequence(),
                new BuildAction(action.round(), action.type(), action.subjectId(), action.position(),
                        3L, 0L, 0, "", 0, action.positionMode()));
        var layout = fixture.reconstructTowerInputs(List.of(discounted), Map.of());
        assertEquals(3L, layout.spent());
        assertEquals(List.of(new MatchReplayFixture.CostDifference("p02", 0, 8L, 3L)), layout.costDifferences());
    }

    @Test
    void controlledOpeningSchedulesResolveOnlyRecordedTowerInputs() throws Exception {
        MatchReplayFixture fixture = MatchReplayFixture.load();
        JsonObject scenario = com.google.gson.JsonParser.parseString(Files.readString(
                Path.of("src/test/resources/replay/controlled-openings.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("CONTROLLED_SCENARIO", scenario.get("timing_origin").getAsString());
        assertEquals("EXPLICIT_TEST_INPUT_NOT_OBSERVED_SEED", scenario.get("seed_origin").getAsString());
        for (var value : scenario.getAsJsonArray("scenarios")) {
            JsonObject opening = value.getAsJsonObject();
            var all = fixture.actions(opening.get("slot").getAsString());
            List<MatchReplayFixture.RecordedAction> selected = opening.getAsJsonArray("action_sequences").asList()
                    .stream().map(sequence -> all.get(sequence.getAsInt())).toList();
            assertTrue(selected.stream().allMatch(action -> action.action().round() == 1));
            var layout = fixture.reconstructTowerInputs(selected, Map.of());
            assertEquals(opening.get("tower_count").getAsInt(), layout.towers().size());
            assertEquals(opening.get("recorded_diamond_spend").getAsLong(), layout.spent());
            assertTrue(layout.costDifferences().isEmpty());
        }
    }

    @Test
    void compareHistoricalCatalogWithActualCurrentFactoriesAndWriteCompatibilityEvidence() throws Exception {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        IncomeSummons.reloadBuiltIns(SummonConfig.defaultConfig());
        try {
            MatchReplayFixture fixture = MatchReplayFixture.load();
            var current = WebCatalogExporter.snapshot(0L);
            List<String> differences = new ArrayList<>();
            for (var value : fixture.document().getAsJsonObject("catalog").getAsJsonArray("towers")) {
                JsonObject historical = value.getAsJsonObject();
                String id = historical.get("id").getAsString();
                var entry = ProductionTowerCatalog.find(id);
                if (entry.isEmpty()) {
                    differences.add(id + ": missing current tower");
                    continue;
                }
                var tower = entry.get().type();
                compare(differences, id, "mineralCost", historical.get("mineralCost").getAsDouble(), tower.mineralCost());
                compare(differences, id, "maxHealth", historical.get("maxHealth").getAsDouble(), tower.maxHealth());
                compare(differences, id, "range", historical.get("range").getAsDouble(), tower.range());
                compare(differences, id, "damage", historical.get("damage").getAsDouble(), tower.damage());
                compare(differences, id, "attackIntervalTicks", historical.get("attackIntervalTicks").getAsDouble(), tower.attackIntervalTicks());
            }
            for (var value : fixture.document().getAsJsonObject("catalog").getAsJsonArray("upgrades")) {
                JsonObject historical = value.getAsJsonObject();
                String from = historical.get("fromTowerId").getAsString();
                String id = historical.get("id").getAsString();
                var source = ProductionTowerCatalog.find(from);
                var edge = source.flatMap(entry -> ProductionTowerCatalog.upgrade(entry.type(), id));
                if (edge.isEmpty()) {
                    differences.add(from + "->" + id + ": missing current upgrade");
                } else {
                    compare(differences, from + "->" + id, "mineralCost", historical.get("mineralCost").getAsDouble(), edge.get().mineralCost());
                }
            }
            for (var recorded : towerInputs(fixture, "p02", 1)) {
                var entry = ProductionTowerCatalog.find(recorded.action().subjectId()).orElseThrow();
                var tower = entry.create(java.util.UUID.nameUUIDFromBytes(recorded.slot().getBytes(StandardCharsets.UTF_8)),
                        kim.biryeong.semiontd.game.TeamId.RED, 1, recorded.action().position());
                assertEquals(recorded.action().subjectId(), tower.type().id());
                assertEquals(recorded.action().position(), tower.position());
            }
            JsonObject evidence = new JsonObject();
            evidence.addProperty("historical_catalog_version", fixture.document().get("catalog_version").getAsString());
            evidence.addProperty("current_catalog_version", current.versionHash());
            evidence.addProperty("scope", "USED_TOWER_CORE_STATS_AND_DIRECTED_COSTS_ONLY_NOT_ABILITY_OR_COMBAT_PARITY");
            evidence.add("differences", new com.google.gson.Gson().toJsonTree(differences));
            Path output = Path.of("build/replay-analysis/current-catalog-differences.json");
            Files.createDirectories(output.getParent());
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(evidence), StandardCharsets.UTF_8);
        } finally {
            ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
            IncomeSummons.reloadBuiltIns(SummonConfig.defaultConfig());
            WebCatalogExporter.clearCurrentVersion();
        }
    }

    private static void compare(List<String> differences, String id, String field, double historical, double current) {
        if (Double.compare(historical, current) != 0) {
            differences.add(id + "." + field + ": " + historical + " -> " + current);
        }
    }

    private static List<MatchReplayFixture.RecordedAction> towerInputs(MatchReplayFixture fixture, String slot, int round) {
        return fixture.actions(slot).stream().filter(action -> action.action().round() == round
                && action.action().type().name().startsWith("TOWER_")).toList();
    }
}
