package kim.biryeong.semiontd.balance.manage;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.balance.manage.BalanceDtos.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.network.chat.ClickEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BalanceUpdateLogTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() {SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();}

    @Test void exportsOnlyAppliedActualChangesWithoutPrivateMetadataAndKeepsStableLinks() throws Exception {
        var defaults = BalanceBundle.defaults();
        var fields = new BalanceFieldRegistry(defaults, null, Set.of()).fields(defaults, null);
        String id = UUID.randomUUID().toString();
        for (DeploymentState state : DeploymentState.values()) {
            String stateId = state == DeploymentState.APPLIED ? id : UUID.randomUUID().toString();
            var update = deployment(stateId, state);
            BalanceUpdateLog.export(directory, List.of(update), fields);
            Path file = directory.resolve("web_catalog/balance-updates/" + stateId + ".json");
            if (state != DeploymentState.APPLIED) {
                assertFalse(Files.exists(file));
            } else {
                String json = Files.readString(file);
                assertFalse(json.contains("PRIVATE"));
                var payload = JsonParser.parseString(json).getAsJsonObject();
                assertEquals(Set.of("schemaVersion", "requestId", "appliedAt", "changes"), payload.keySet());
                var change = payload.getAsJsonArray("changes").get(0).getAsJsonObject();
                assertEquals(4, change.get("before").getAsDouble());
                assertEquals(6, change.get("after").getAsDouble());
                BalanceUpdateLog.export(directory, List.of(update), fields);
                assertEquals(json, Files.readString(file));
            }
        }
        try (var files = Files.list(directory.resolve("web_catalog/balance-updates"))) {assertEquals(1, files.count());}
        var click = assertInstanceOf(ClickEvent.OpenUrl.class, BalanceUpdateLog.link(id).getStyle().getClickEvent());
        assertEquals("https://semiontd.biryeong.kim/patches/" + id, click.uri().toString());
        assertThrows(IllegalArgumentException.class, () -> BalanceUpdateLog.link("../../private"));
    }

    private static BalanceDeployment deployment(String id, DeploymentState state) {
        return new BalanceDeployment(id, state, "PRIVATE-revision", "PRIVATE-next", ApplyMode.NEXT_MATCH,
                "PRIVATE-reason", "PRIVATE-actor", "PRIVATE-source", 1000, state == DeploymentState.APPLIED ? 1234L : null,
                "PRIVATE-schedule", "SYNCED", "PRIVATE-error", List.of(new BalanceChange("tower:/towers/t1_pig_tower/damage", 4, 6)));
    }
}
