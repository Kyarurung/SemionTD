package com.flechazo.nightlights.util;

import com.flechazo.nightlights.NightLights;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class RegisterHelper {
    public static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath(NightLights.MODID, name);
    }

    public static ResourceKey<Block> blockKey(String name) {
        return ResourceKey.create(Registries.BLOCK, id(name));
    }

    public static ResourceKey<Item> itemKey(String name) {
        return ResourceKey.create(Registries.ITEM, id(name));
    }
}