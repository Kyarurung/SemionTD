package kim.biryeong.semiontd.tower.plant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kim.biryeong.semiontd.vfx.DisplayEffect;
import kim.biryeong.semiontd.vfx.DisplaySprite;
import kim.biryeong.semiontd.vfx.DisplaySpriteResourcePack;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlantDisplayVfxTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static List<DisplayEffect> allEffects() {
        return List.of(
                PlantDisplayVfx.pitcherLob(new Vector3f(0, 0, 12), 5.0, 2.5, true, 7L),
                PlantDisplayVfx.lilacCone(0.0F, 3.0, 70.0, 7L),
                PlantDisplayVfx.tulipNova(3.0, 7L),
                PlantDisplayVfx.meadowPulse(4.0, 7L),
                PlantDisplayVfx.meadowHeal(7L),
                PlantDisplayVfx.sandSlow(7L),
                PlantDisplayVfx.mineFuse(7L),
                PlantDisplayVfx.mineBurst(3.0, 7L),
                PlantDisplayVfx.pandaImpact(1.5, 7L));
    }

    @Test
    void everyPlantEffectIsLightWellFormedAndVanishes() {
        for (DisplayEffect effect : allEffects()) {
            assertTrue(!effect.parts().isEmpty() && effect.parts().size() <= 16,
                    effect.id() + " must stay light because towers fire it often");
            for (DisplayEffect.Part part : effect.parts()) {
                int previous = -1;
                for (DisplayEffect.Frame frame : part.frames()) {
                    assertTrue(frame.tick() >= previous && frame.tick() <= effect.lifetime(), effect.id() + " frame order");
                    assertTrue(frame.tick() == 0 || frame.tick() >= 2, effect.id() + " must let the client receive the first pose");
                    previous = frame.tick();
                }
                Vector3f last = part.last().scale();
                assertTrue(last.x * last.y * last.z < 1.0e-4, effect.id() + " parts must vanish instead of popping out");
            }
        }
    }

    @Test
    void everyPlantSpriteShipsItsTexture() throws IOException {
        for (DisplaySprite sprite : PlantDisplayVfx.SPRITES) {
            try (var in = PlantDisplayVfxTest.class.getResourceAsStream(sprite.texturePath())) {
                assertTrue(in != null && in.readAllBytes().length > 0, "Missing texture " + sprite.texturePath());
            }
        }
    }

    /** 원기둥 벽 모델은 판 16장이고, 아이템 모델이 허용하는 22.5° 단위(±45° 이내) 회전만 씁니다. */
    @Test
    void cylinderModelUsesSixteenPanelsWithLegalRotations() {
        String model = DisplaySpriteResourcePack.modelJson(PlantDisplayVfx.WALL_TULIP);
        assertEquals(16, model.split("\"from\"").length - 1, "sixteen panels");
        Matcher angles = Pattern.compile("\"angle\": (-?[0-9.]+)").matcher(model);
        int rotated = 0;
        while (angles.find()) {
            double angle = Double.parseDouble(angles.group(1));
            assertTrue(Math.abs(angle) <= 45.0 && Math.abs(angle / 22.5 - Math.rint(angle / 22.5)) < 1.0e-6, "angle " + angle);
            rotated++;
        }
        assertEquals(12, rotated, "four panels sit on the axes, twelve are turned");
    }

    /** 미리보기 페이지가 읽는 키프레임을 build/vfx-preview/plant_vfx.json에 씁니다. */
    @Test
    void exportsKeyframesForThePreviewPage() throws IOException {
        StringBuilder json = new StringBuilder("[");
        List<DisplayEffect> effects = allEffects();
        for (int index = 0; index < effects.size(); index++) {
            json.append(index > 0 ? ",\n" : "").append(effects.get(index).toJson());
        }
        json.append("]\n");
        java.nio.file.Path out = java.nio.file.Path.of("build", "vfx-preview", "plant_vfx.json");
        java.nio.file.Files.createDirectories(out.getParent());
        java.nio.file.Files.writeString(out, json.toString(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(java.nio.file.Files.size(out) > 1000);
    }
}
