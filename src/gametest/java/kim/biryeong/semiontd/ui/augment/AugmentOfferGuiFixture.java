package kim.biryeong.semiontd.ui.augment;

import java.util.function.BooleanSupplier;
import java.util.function.ToIntFunction;
import kim.biryeong.semiontd.augment.AugmentService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class AugmentOfferGuiFixture implements AutoCloseable {
    public final AugmentOfferGui gui;
    private final TestTitle title = new TestTitle();

    public AugmentOfferGuiFixture(ServerPlayer player, AugmentService.Screen screen, long startedNanos,
            BooleanSupplier valid, ToIntFunction<String> execute, boolean canReroll) {
        gui = new AugmentOfferGui(player, screen, startedNanos, valid, execute, canReroll, title);
    }

    public void loseRenderer() {
        title.available = false;
    }

    public boolean rendererClosed() {
        return title.closed;
    }

    @Override
    public void close() {
        gui.close();
    }

    private static final class TestTitle implements AugmentHudTitle {
        boolean available = true;
        boolean closed;

        @Override
        public boolean available() {
            return available && !closed;
        }

        @Override
        public Component render(long elapsedNanos) {
            return Component.literal("격리 테스트 증강 카드");
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
