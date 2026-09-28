package kim.biryeong.semiontd.entity.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

    private static JsonObject read(String path) throws IOException {
        try (InputStream stream = SemionBilModelGlowTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
