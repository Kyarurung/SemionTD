package kim.biryeong.semiontd.summon.invasion;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import kim.biryeong.semiontd.vfx.DisplayEffect;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InvasionVfxTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static List<DisplayEffect> allEffects() {
        return List.of(
                InvasionVfx.elfSlash(0.5F, 7L),
                InvasionVfx.goblinExecute(7L),
                InvasionVfx.priestBlast(2.5, 7L),
                InvasionVfx.priestHeal(7L),
                InvasionVfx.dwarfShot(new Vector3f(0, 1, 0.8F), new Vector3f(0, 1, 10), 7L),
                InvasionVfx.trollJavelin(new Vector3f(0, 2, 0), new Vector3f(0, 1, 6), 5),
                InvasionVfx.orcBerserk(7L),
                InvasionVfx.necroCast(46, 7L),
                InvasionVfx.necroRise(7L),
                InvasionVfx.necroBolt(7L),
                InvasionVfx.groundSlam(2.8, 7L),
                InvasionVfx.commanderSlash(0.5F, 7L));
    }

    @Test
    void everyUnitEffectIsSmallWellFormedAndVanishes() {
        for (DisplayEffect effect : allEffects()) {
            assertTrue(!effect.parts().isEmpty() && effect.parts().size() <= 12,
                    effect.id() + " must stay light because units fire it often");
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
    void everyUnitSpriteShipsItsTexture() throws IOException {
        for (var sprite : InvasionVfx.SPRITES) {
            try (var in = InvasionVfxTest.class.getResourceAsStream(sprite.texturePath())) {
                assertTrue(in != null && in.readAllBytes().length > 0, "Missing texture " + sprite.texturePath());
            }
        }
    }

    @Test
    void everyUnitHitsBeforeItsNextSwingStarts() {
        for (String unit : List.of("goblin_scout", "elf_assassin", "dark_priest", "dwarf_gunner", "troll_javelineer",
                "orc_warrior", "necromancer", "siege_golem", "legion_commander", "ogre_champion")) {
            InvasionUnits.Profile profile = InvasionUnits.profile(unit);
            assertTrue(profile.hitDelayTicks() < profile.intervalTicks(), unit + " must land its hit before the next attack");
        }
    }
}
