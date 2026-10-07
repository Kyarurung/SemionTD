package kim.biryeong.semiontd.ui.augment;

import kim.biryeong.semiontd.augment.AugmentService;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

interface AugmentHudTitle {
    boolean available();
    Component render(long elapsedNanos);
    void close();

    static AugmentHudTitle create(ServerPlayer player, AugmentService.Screen screen) {
        return FabricLoader.getInstance().isModLoaded("betterhud") ? BetterHudOfferTitle.create(player, screen) : null;
    }
}
