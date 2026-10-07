package kim.biryeong.semiontd.ui.augment;

import java.io.IOException;
import java.io.UncheckedIOException;
import kr.toxicity.hud.api.BetterHudAPI;
import kr.toxicity.hud.api.mod.ModBootstrap;

final class BetterHudResourceHook {
    private BetterHudResourceHook() { }

    static void register() {
        ModBootstrap.PRE_RELOAD_EVENT.register(ignored -> {
            try {
                AugmentHudResources.write(BetterHudAPI.inst().bootstrap().dataFolder().toPath());
            } catch (IOException exception) {
                throw new UncheckedIOException("Unable to prepare SemionTD augment HUD resources", exception);
            }
        });
    }
}
