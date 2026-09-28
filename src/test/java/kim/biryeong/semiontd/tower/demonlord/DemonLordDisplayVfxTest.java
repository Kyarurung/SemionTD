package kim.biryeong.semiontd.tower.demonlord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import kim.biryeong.semiontd.vfx.DisplayEffect;
import kim.biryeong.semiontd.vfx.DisplayShapes;
import kim.biryeong.semiontd.vfx.DisplaySprite;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DemonLordDisplayVfxTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everySkillBuildsAWellFormedDisplayEffect() {
        for (DisplayEffect effect : allEffects()) {
            assertFalse(effect.parts().isEmpty(), effect.id() + " must have parts");
            assertTrue(effect.parts().size() <= 80, effect.id() + " must stay within the element budget");
            for (DisplayEffect.Part part : effect.parts()) {
                int previous = -1;
                for (DisplayEffect.Frame frame : part.frames()) {
                    assertTrue(frame.tick() >= previous && frame.tick() <= effect.lifetime(),
                            effect.id() + " frames must stay in order and inside the lifetime");
                    assertTrue(frame.tick() == 0 || frame.tick() >= 2,
                            effect.id() + " must wait for the client to receive the first pose");
                    Vector3f scale = frame.pose().scale();
                    assertTrue(Float.isFinite(scale.x) && Float.isFinite(scale.y) && Float.isFinite(scale.z)
                            && scale.x >= 0 && scale.y >= 0 && scale.z >= 0, effect.id() + " scale must be valid");
                    assertTrue(frame.pose().translation().isFinite() && frame.pose().rotation().isFinite(),
                            effect.id() + " pose must be finite");
                    previous = frame.tick();
                }
                Vector3f last = part.last().scale();
                assertTrue(last.x * last.y * last.z < 1.0e-4,
                        effect.id() + " parts must vanish by the end instead of popping out");
            }
        }
    }

    /** 땅에 까는 장은 땅에서 GROUND_LIFT 이상 떠 있어야 z-fighting이 나지 않습니다. 범위 스탯으로 줄이거나 키워도 같습니다. */
    @Test
    void groundLayersFloatJustAboveTheGround() {
        for (DisplayEffect base : allEffects()) {
            for (DisplayEffect effect : List.of(base, base.scaled(0.5), base.scaled(2.0))) {
                for (DisplayEffect.Part part : effect.parts()) {
                    if (part.sprite().shape() != DisplaySprite.Shape.FLAT) {
                        continue;
                    }
                    for (DisplayEffect.Frame frame : part.frames()) {
                        float y = frame.pose().translation().y;
                        // 땅과 나란히 누운 판(위쪽이 그대로 위를 보는 판)만 봅니다. 기울인 칼날 궤적은 제외합니다.
                        Vector3f up = frame.pose().rotation().transform(new Vector3f(0, 1, 0));
                        if (up.y > 0.999F && y >= 0.0F && y <= DisplayEffect.GROUND_BAND) {
                            assertTrue(y >= DisplayShapes.GROUND_LIFT - 1.0e-6F,
                                    effect.id() + " has a ground layer touching the ground at y=" + y);
                        }
                    }
                }
            }
        }
        assertEquals(0.005F, DisplayShapes.ground(0), 1.0e-6F);
        assertEquals(0.01F, DisplayShapes.ground(1), 1.0e-6F);
    }

    @Test
    void everyUsedSpriteShipsItsTexture() throws IOException {
        for (kim.biryeong.semiontd.vfx.DisplaySprite sprite : DemonLordDisplayVfx.SPRITES) {
            try (var in = DemonLordDisplayVfxTest.class.getResourceAsStream(sprite.texturePath())) {
                assertTrue(in != null && in.readAllBytes().length > 0, "Missing texture " + sprite.texturePath());
            }
        }
        for (DisplayEffect effect : allEffects()) {
            for (DisplayEffect.Part part : effect.parts()) {
                assertTrue(DemonLordDisplayVfx.SPRITES.contains(part.sprite()),
                        effect.id() + " uses a sprite that is not packed: " + part.sprite().name());
            }
        }
    }

    /** 미리보기 페이지가 읽는 키프레임 묶음을 build/vfx-preview 아래에 씁니다. */
    @Test
    void exportsKeyframesForThePreviewPage() throws IOException {
        StringBuilder json = new StringBuilder("[");
        List<DisplayEffect> effects = allEffects();
        for (int index = 0; index < effects.size(); index++) {
            if (index > 0) {
                json.append(",\n");
            }
            json.append(effects.get(index).toJson());
        }
        json.append("]\n");
        Path out = Path.of("build", "vfx-preview", "demon_lord_vfx.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, json.toString(), StandardCharsets.UTF_8);
        assertTrue(Files.size(out) > 1000);
    }

    private static List<DisplayEffect> allEffects() {
        List<DisplayEffect> effects = new ArrayList<>();
        for (DemonLordSkill skill : DemonLordSkill.values()) {
            effects.add(DemonLordVfx.preview(skill, 7L));
        }
        effects.add(DemonLordDisplayVfx.demonWingsShockwave(4.0, 7L));
        effects.add(DemonLordDisplayVfx.arcaneLaunch(7L));
        effects.add(DemonLordDisplayVfx.gripOfDoom(false, 0.0, 7L));
        effects.add(DemonLordDisplayVfx.echo(7L));
        effects.add(DemonLordDisplayVfx.fiendDismiss(7L));
        effects.add(DemonLordDisplayVfx.bloodCleave(2.5, 7L));
        return effects;
    }
}
