package com.campersamu.chatheads;

import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import eu.pb4.polymer.autohost.impl.AutoHost;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

import static net.minecraft.network.chat.Component.literal;
import static net.minecraft.network.chat.TextColor.fromRgb;

public class ChatHeadsInit implements DedicatedServerModInitializer {
    //region Constants
    public static final String MODID = "chatheads", PLAYER = "player";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    public static final TextColor[][] DEFAULT_HEAD_TEXTURE = new TextColor[][]{   //hex 0xC01044 -> TextColor.fromRgb(0xC01044)
            {fromRgb(0x191919), fromRgb(0x0c0c0c), fromRgb(0x0c0c0c), fromRgb(0x0c0c0c), fromRgb(0x0c0c0c), fromRgb(0x0c0c0c), fromRgb(0x0c0c0c), fromRgb(0x191919)},
            {fromRgb(0x191919), fromRgb(0x0c0c0c), fromRgb(0x0c0c0c), fromRgb(0x0c0c0c), fromRgb(0x0c0c0c), fromRgb(0x0c0c0c), fromRgb(0x0c0c0c), fromRgb(0x191919)},
            {fromRgb(0x191919), fromRgb(0x191919), fromRgb(0x0c0c0c), fromRgb(0x0c0c0c), fromRgb(0x0c0c0c), fromRgb(0x0c0c0c), fromRgb(0x191919), fromRgb(0x191919)},
            {fromRgb(0x191919), fromRgb(0x191919), fromRgb(0x191919), fromRgb(0x191919), fromRgb(0x191919), fromRgb(0xffd7b0), fromRgb(0xffd7b0), fromRgb(0x191919)},

            {fromRgb(0xffd7b0), fromRgb(0xffd7b0), fromRgb(0xffd7b0), fromRgb(0xffd7b0), fromRgb(0xffd7b0), fromRgb(0xffd7b0), fromRgb(0xffd7b0), fromRgb(0xffd7b0)},
            {fromRgb(0xffd7b0), fromRgb(0xffd7b0), fromRgb(0xffd7b0), fromRgb(0xffd7b0), fromRgb(0xffd7b0), fromRgb(0xffd7b0), fromRgb(0xffd7b0), fromRgb(0xffd7b0)},

            {fromRgb(0xffd7b0), fromRgb(0xffd7b0), fromRgb(0xffd7b0), fromRgb(0xffd7b0), fromRgb(0xffd7b0), fromRgb(0xffd7b0), fromRgb(0xffd7b0), fromRgb(0xffd7b0)},
            {fromRgb(0xffd7b0), fromRgb(0xffd7b0), fromRgb(0xffd7b0), fromRgb(0xffd7b0), fromRgb(0xffd7b0), fromRgb(0xffd7b0), fromRgb(0xffd7b0), fromRgb(0xffd7b0)},
    };
    public static final Component DEFAULT_HEAD = paintHead(DEFAULT_HEAD_TEXTURE);
    public static final ConcurrentHashMap<UUID, TextColor[][]> HEAD_CACHE = new ConcurrentHashMap<>();
    //endregion

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void onInitializeServer() {
        //Add Mod Resources to Polymer Resource Pack
        PolymerResourcePackUtils.addModAssets(MODID);

        //Register Placeholder
        Placeholders.registerServer(Identifier.fromNamespaceAndPath(MODID, PLAYER), (ctx, arg) -> {
            if (!ctx.hasPlayer()) return PlaceholderResult.value(DEFAULT_HEAD);
            if (arg == null || arg.isEmpty())
                return PlaceholderResult.value(paintHead(HEAD_CACHE.getOrDefault(ctx.player().getUUID(), DEFAULT_HEAD_TEXTURE)));
            final var playerProfile = ctx.server().services().profileResolver().fetchByName(arg);
            return playerProfile.map(gameProfile -> PlaceholderResult.value(paintHead(HEAD_CACHE.getOrDefault(gameProfile.id(), DEFAULT_HEAD_TEXTURE))))
                    .orElseGet(() -> PlaceholderResult.value(DEFAULT_HEAD));
        });

        if (!AutoHost.config.enabled && !FabricLoader.getInstance().isModLoaded("arte")) {
            LOGGER.warn("""
              #####################################
                Polymer AutoHost is not enabled!
              The heads in chat might appear buggy!
               Go to config/polymer/autohost.json
                          to enable it!
              #####################################
              """);
        }
    }

    //region Util
    public static @NotNull Component paintHead(TextColor[][] head) {
        MutableComponent text = Component.empty();
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                text = text
                        .append(literal("" + (char) (((int) '\uF810') + y)).setStyle(Style.EMPTY.withColor(head[y][x]).withFont(new net.minecraft.network.chat.FontDescription.Resource(Identifier.fromNamespaceAndPath(MODID, "pixel")))))
                        .append(literal("\uE001").withStyle(Style.EMPTY.withFont(new net.minecraft.network.chat.FontDescription.Resource(Identifier.fromNamespaceAndPath(MODID, "pixel")))));
            }
            text = text.append(literal("\uE008").withStyle(Style.EMPTY.withFont(new net.minecraft.network.chat.FontDescription.Resource(Identifier.fromNamespaceAndPath(MODID, "pixel")))));
        }

        text.append(literal("  "));

        return text;
    }
    //endregion
}
