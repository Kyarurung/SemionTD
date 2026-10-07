package kim.biryeong.semiontd.ui.augment;

import kim.biryeong.semiontd.augment.AugmentDisplayRole;
import kim.biryeong.semiontd.augment.AugmentService;
import kim.biryeong.semiontd.ui.SemionText;
import kim.biryeong.semiontd.ui.rp.SemionUiFont;
import kr.toxicity.hud.api.BetterHudAPI;
import kr.toxicity.hud.api.configuration.HudComponentSupplier;
import kr.toxicity.hud.api.hud.Hud;
import kr.toxicity.hud.api.player.HudPlayer;
import net.kyori.adventure.platform.modcommon.impl.NonWrappingComponentSerializer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

final class BetterHudOfferTitle implements AugmentHudTitle {
    private final HudPlayer player;
    private final HudComponentSupplier<Hud> renderer;
    private final String owner = java.util.UUID.randomUUID().toString();
    private boolean closed;

    private BetterHudOfferTitle(HudPlayer player, Hud hud, AugmentService.Screen screen) {
        this.player = player;
        player.getVariableMap().put("semion_augment_owner", owner);
        for (int i = 0; i < 3; i++) {
            var card = screen.cards().get(i);
            player.getVariableMap().put(key("title", i), card.definition().displayName());
            player.getVariableMap().put(key("summary", i), SemionText.mini(card.summary()).getString());
            player.getVariableMap().put(key("role", i), Integer.toString(AugmentDisplayRole.of(card.definition()).ordinal()));
            player.getVariableMap().put(key("role_name", i), AugmentDisplayRole.of(card.definition()).label());
        }
        var rarity = screen.cards().getFirst().definition().rarity();
        player.getVariableMap().put("semion_augment_rarity", Integer.toString(rarity.ordinal()));
        player.getVariableMap().put("semion_augment_rarity_name", switch (rarity) {
            case SILVER -> "실버";
            case GOLD -> "골드";
            case PRISMATIC -> "프리즘";
        });
        renderer = hud.createRenderer(player);
    }

    static AugmentHudTitle create(ServerPlayer viewer, AugmentService.Screen screen) {
        var api = BetterHudAPI.inst();
        if (api == null || api.isOnReload()) return null;
        var hud = api.getHudManager().getHud(AugmentHudResources.HUD_NAME);
        var player = api.getPlayerManager().getHudPlayer(viewer.getUUID());
        return hud == null || player == null ? null : new BetterHudOfferTitle(player, hud, screen);
    }

    @Override
    public boolean available() {
        return !closed && !BetterHudAPI.inst().isOnReload()
                && owner.equals(player.getVariableMap().get("semion_augment_owner"));
    }

    @Override
    public Component render(long elapsedNanos) {
        if (!available()) return null;
        for (int i = 0; i < 3; i++) player.getVariableMap().put(key("step", i),
                Integer.toString(AugmentOfferLayout.stage(i, elapsedNanos)));
        var result = Component.empty();
        for (var component : renderer.get()) {
            result.append(NonWrappingComponentSerializer.INSTANCE.serialize(component.component().build()
                    .font(BetterHudAPI.inst().getDefaultKey())));
            result.append(SemionUiFont.offset(-component.width()));
        }
        return result;
    }

    @Override
    public void close() {
        closed = true;
        if (!player.getVariableMap().remove("semion_augment_owner", owner)) return;
        for (int i = 0; i < 3; i++) {
            for (String field : new String[]{"title", "summary", "role", "role_name", "step"})
                player.getVariableMap().remove(key(field, i));
        }
        player.getVariableMap().remove("semion_augment_rarity");
        player.getVariableMap().remove("semion_augment_rarity_name");
    }

    private static String key(String field, int card) {
        return "semion_augment_" + field + "_" + card;
    }
}
