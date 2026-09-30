package kim.biryeong.semiontd.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.config.ProgressionConfig;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.tower.blueprint.BlueprintDesign;
import kim.biryeong.semiontd.tower.blueprint.BlueprintStats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BlueprintPersistenceTest {
    @TempDir
    Path tempDir;

    @Test
    void legacyProfilesLoadWithoutBlueprintsAndSavedDesignsSurviveReload() throws Exception {
        Path path = tempDir.resolve("profiles.json");
        UUID playerId = UUID.randomUUID();
        Files.writeString(path, "{\n  \"" + playerId + "\": {\"lastKnownName\": \"Legacy\"}\n}\n");

        ProgressionService service = new ProgressionService(ProgressionConfig.defaultConfig(), path);
        assertTrue(service.profile(null, playerId, "Legacy").blueprints().isEmpty());

        BlueprintDesign archer = BlueprintDesign.of("궁수",
                new BlueprintStats(120.0, 12.0, 20, 7.0, 25, DamageType.MAGIC), "t1_cat_tower");
        BlueprintDesign tank = BlueprintDesign.of("방패",
                new BlueprintStats(600.0, 4.0, 30, 2.0, 50, DamageType.PHYSICAL), "t1_golem_tower");
        assertTrue(service.saveBlueprints(playerId, "Legacy", List.of(archer, tank)));

        SemionPlayerProfile saved = new ProgressionService(ProgressionConfig.defaultConfig(), path)
                .profile(null, playerId, "Legacy");
        assertEquals(List.of(archer, tank), saved.blueprints());
        assertEquals(DamageType.MAGIC, saved.blueprints().getFirst().stats().damageType());

        // 다른 프로필 변경(스카이박스 저장)이 설계도를 지우지 않아야 합니다.
        service.saveSelectedSkybox(null, playerId, "Legacy", "night");
        assertEquals(2, service.profile(null, playerId, "Legacy").blueprints().size());
    }
}
