package kim.biryeong.semiontd.ui.augment;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class AugmentCardFramesTest {
    @Test
    void everySliceKeepsFullAdvanceAndEnabledButtonsRemainDistinct() {
        var cards = AugmentCardFrames.cards();
        var buttons = AugmentCardFrames.buttons();
        assertEquals(143 * 9, cards.getWidth());
        assertEquals(22 * 9, cards.getHeight());
        for (int variant = 0; variant < 9; variant++) {
            for (int row = 0; row < 22; row++) {
                assertEquals(255, cards.getRGB(variant * 143 + 142, row * 9 + 4) >>> 24,
                        "Every bitmap slice must retain a 144px advance for its complete click region");
            }
        }
        for (int rarity = 0; rarity < 3; rarity++) {
            assertNotEquals(buttons.getRGB(rarity * 286, 0), buttons.getRGB(rarity * 286 + 143, 0),
                    "An exhausted reroll needs a visibly different inactive border");
        }
        assertEquals(108, buttons.getHeight());
        for (int remaining = 0; remaining <= 5; remaining++) {
            for (int variant = 0; variant < 6; variant++) {
                for (int y = 0; y < 18; y++) {
                    assertEquals(255, buttons.getRGB(variant * 143 + 142, remaining * 18 + y) >>> 24);
                }
                boolean counterVisible = false;
                for (int y = 4; y < 13; y++) {
                    for (int x = 69; x < 100; x++) {
                        counterVisible |= buttons.getRGB(variant * 143 + x, remaining * 18 + y)
                                != buttons.getRGB(variant * 143 + 110, remaining * 18 + y);
                    }
                }
                assertTrue(counterVisible, "Every enabled and disabled button needs a legible remaining count");
            }
        }
        assertEquals(456, AugmentCardDialog.CONTENT_WIDTH);
        assertEquals(225, AugmentCardDialog.TOTAL_ROWS * 9);
    }
}
