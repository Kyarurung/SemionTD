package kim.biryeong.semiontd.entity.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class SemionBilModelGlowTest {
    @Test
    void onlyElementsNamedGlowGetLightEmission() {
        JsonObject model = JsonParser.parseString("""
                {"elements": [
                  {"name": "right_eye_glow"},
                  {"name": "skull"},
                  {"name": "rune_glow", "light_emission": 7}
                ]}""").getAsJsonObject();

        assertEquals(1, SemionBilModelCache.applyGlow(model));
        var elements = model.getAsJsonArray("elements");
        assertEquals(SemionBilModelCache.GLOW_LIGHT, elements.get(0).getAsJsonObject().get("light_emission").getAsInt());
        assertFalse(elements.get(1).getAsJsonObject().has("light_emission"));
        assertEquals(7, elements.get(2).getAsJsonObject().get("light_emission").getAsInt(), "An explicit value is kept.");
    }

    @Test
    void golemNecromancerAndCommanderEyesGlow() throws IOException {
        for (String key : new String[]{"siege_golem", "necromancer", "legion_commander"}) {
            JsonObject model = read("/model/semion-td/invasion/" + key + ".bbmodel");
            assertEquals(2, SemionBilModelCache.applyGlow(model), key + " should have two glowing eyes");
            for (JsonElement element : model.getAsJsonArray("elements")) {
                String name = element.getAsJsonObject().get("name").getAsString();
                if (name.endsWith(SemionBilModelCache.GLOW_SUFFIX)) {
                    assertEquals(SemionBilModelCache.GLOW_LIGHT, element.getAsJsonObject().get("light_emission").getAsInt());
                }
            }
        }
    }

    @Test
    void multiAxisCubesAreWrappedInGroupsThatCarryTheirRotation() throws IOException {
        JsonObject model = read("/model/semion-td/invasion/dark_priest.bbmodel");
        java.util.Map<String, JsonArray> before = new java.util.HashMap<>();
        for (JsonElement element : model.getAsJsonArray("elements")) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("rotation")) {
                before.put(object.get("uuid").getAsString(), object.getAsJsonArray("rotation").deepCopy());
            }
        }

        assertEquals(7, SemionBilModelCache.wrapMultiAxisRotations(model), "The priest's side skirt panels and bust are tilted on several axes.");
        assertEquals(0, SemionBilModelCache.wrapMultiAxisRotations(model), "Wrapping twice changes nothing.");

        java.util.Map<String, JsonObject> groups = new java.util.HashMap<>();
        for (JsonElement group : model.getAsJsonArray("groups")) {
            groups.put(group.getAsJsonObject().get("uuid").getAsString(), group.getAsJsonObject());
        }
        int wrapped = 0;
        for (JsonElement element : model.getAsJsonArray("elements")) {
            JsonObject object = element.getAsJsonObject();
            long axes = !object.has("rotation") ? 0 : object.getAsJsonArray("rotation").asList().stream()
                    .filter(v -> Math.abs(v.getAsDouble()) > 1.0e-4).count();
            assertTrue(axes <= 1, object.get("name").getAsString() + " still rotates on several axes");
            JsonObject parent = parentOf(model.getAsJsonArray("outliner"), object.get("uuid").getAsString());
            if (parent != null && groups.get(parent.get("uuid").getAsString()).get("name").getAsString().endsWith("_rot")) {
                assertEquals(before.get(object.get("uuid").getAsString()), groups.get(parent.get("uuid").getAsString()).get("rotation"),
                        "The wrapping group carries the cube's original rotation.");
                wrapped++;
            }
        }
        assertEquals(7, wrapped);
    }

    private static JsonObject parentOf(JsonArray children, String uuid) {
        for (JsonElement child : children) {
            if (!child.isJsonObject()) {
                continue;
            }
            JsonArray nested = child.getAsJsonObject().getAsJsonArray("children");
            for (JsonElement grandChild : nested) {
                if (grandChild.isJsonPrimitive() && uuid.equals(grandChild.getAsString())) {
                    return child.getAsJsonObject();
                }
            }
            JsonObject found = parentOf(nested, uuid);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static JsonObject read(String path) throws IOException {
        try (InputStream stream = SemionBilModelGlowTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
