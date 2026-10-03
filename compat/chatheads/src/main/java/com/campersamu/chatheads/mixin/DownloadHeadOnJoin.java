package com.campersamu.chatheads.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.TextColor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.URI;

import static com.campersamu.chatheads.ChatHeadsInit.DEFAULT_HEAD_TEXTURE;
import static com.campersamu.chatheads.ChatHeadsInit.HEAD_CACHE;
import static com.campersamu.chatheads.ChatHeadsInit.LOGGER;
import static net.minecraft.network.chat.TextColor.fromRgb;

@Mixin(PlayerList.class)
public abstract class DownloadHeadOnJoin {
    //region Mixin Variables
    @Shadow
    @Final
    private MinecraftServer server;
    //endregion

    //Mixin into the player connect/join event and downlaod the skin for the player (needs a server restart to update)
    @Inject(method = "placeNewPlayer", at = @At("HEAD"))
    private void chatheads$invokeDownloadOnJoin(Connection connection, ServerPlayer player, CommonListenerCookie clientData, CallbackInfo ci) {
        final var profile = player.getGameProfile();
        //Use a new Thread since downloading a skin is slow and would slow down the player joining process
        Thread.ofVirtual().name("chatheads-skin").start(() -> {
            synchronized (HEAD_CACHE) {
                final TextColor[][] head = HEAD_CACHE.computeIfAbsent(player.getUUID(), uuid -> chatheads$getPlayerHead(profile, player));
                HEAD_CACHE.put(profile.id(), head);
            }
        });
    }

    //region Util
    @Unique
    private TextColor[][] chatheads$getPlayerHead(final GameProfile profile, final ServerPlayer player) {
        //get skin url
        var sessionService = server.services().sessionService();
        if (sessionService == null) return DEFAULT_HEAD_TEXTURE;
        final MinecraftProfileTexture playerSkin = sessionService.getTextures(profile).skin();

        //return default head if skin is null
        if (playerSkin == null) return DEFAULT_HEAD_TEXTURE;

        final String playerSkinUrl = playerSkin.getUrl();

        //return default head if skin url is null
        if (playerSkinUrl == null) return DEFAULT_HEAD_TEXTURE;

        //pull the picture
        final BufferedImage image;
        try {
            var request = URI.create(playerSkinUrl).toURL().openConnection();
            request.setConnectTimeout(5000);
            request.setReadTimeout(5000);
            try (var stream = request.getInputStream()) {
                image = ImageIO.read(stream);
            }
            if (image == null || image.getWidth() < 16 || image.getHeight() < 16) return DEFAULT_HEAD_TEXTURE;
        } catch (Exception e) {
            LOGGER.warn("Failed to get image for {}", player.getName().getString());
            LOGGER.warn(e.toString());
            return DEFAULT_HEAD_TEXTURE;
        }

        //generate the head
        final TextColor[][] playerHead = new TextColor[8][8];
        for (int x = 8; x < 16; x++) {
            for (int y = 8; y < 16; y++) {
                int rgb = image.getRGB(x, y);
                playerHead[y - 8][x - 8] = fromRgb(rgb & 0xffffff);
            }
        }

        return playerHead;
    }
    //endregion
}
