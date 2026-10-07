package kim.biryeong.semiontd.ui.augment;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AugmentHudResourcesTest {
    @Test
    void regeneratingExistingResourcesRemovesTheLatinOnlyFilter(@TempDir Path directory) throws IOException {
        Path text = directory.resolve("texts/semion-augment.yml");
        Files.createDirectories(text.getParent());
        Files.writeString(text, "semion_augment_font:\n  merge-default-bitmap: true\n  use-unifont: true\n  include: []\n");

        AugmentHudResources.write(directory);

        var settings = new Properties();
        try (var reader = Files.newBufferedReader(text, StandardCharsets.UTF_8)) {
            settings.load(reader);
        }
        assertEquals("true", settings.getProperty("use-unifont"));
        assertEquals("true", settings.getProperty("merge-default-bitmap"));
        assertFalse(settings.containsKey("include"),
                "BetterHud 449 treats an empty include list as Latin-only, while no include key allows all Unifont glyphs.");
        String layouts = Files.readString(directory.resolve("layouts/semion-augment.yml"));
        assertTrue(layouts.contains("name: semion_augment_font"));
        assertTrue(layouts.contains("semion_augment_title_0"));
        assertTrue(layouts.contains("semion_augment_summary_2"));
    }
}
