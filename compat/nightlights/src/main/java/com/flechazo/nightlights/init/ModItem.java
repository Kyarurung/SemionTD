package com.flechazo.nightlights.init;

import com.flechazo.nightlights.item.WearableBlockItem;
import com.flechazo.nightlights.util.RegisterHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class ModItem {
    // Night Light Items
    public static final Item FROG_BLACK = registerWearableBlockItem("frog_black", ModBlock.FROG_BLACK);
    public static final Item FROG_BLUE = registerWearableBlockItem("frog_blue", ModBlock.FROG_BLUE);
    public static final Item FROG_BROWN = registerWearableBlockItem("frog_brown", ModBlock.FROG_BROWN);
    public static final Item FROG_CYAN = registerWearableBlockItem("frog_cyan", ModBlock.FROG_CYAN);
    public static final Item FROG_GRAY = registerWearableBlockItem("frog_gray", ModBlock.FROG_GRAY);
    public static final Item FROG_GREEN = registerWearableBlockItem("frog_green", ModBlock.FROG_GREEN);
    public static final Item FROG_LIGHT_BLUE = registerWearableBlockItem("frog_light_blue", ModBlock.FROG_LIGHT_BLUE);
    public static final Item FROG_LIGHT_GRAY = registerWearableBlockItem("frog_light_gray", ModBlock.FROG_LIGHT_GRAY);
    public static final Item FROG_LIME = registerWearableBlockItem("frog_lime", ModBlock.FROG_LIME);
    public static final Item FROG_MAGENTA = registerWearableBlockItem("frog_magenta", ModBlock.FROG_MAGENTA);
    public static final Item FROG_ORANGE = registerWearableBlockItem("frog_orange", ModBlock.FROG_ORANGE);
    public static final Item FROG_PINK = registerWearableBlockItem("frog_pink", ModBlock.FROG_PINK);
    public static final Item FROG_PURPLE = registerWearableBlockItem("frog_purple", ModBlock.FROG_PURPLE);
    public static final Item FROG_RED = registerWearableBlockItem("frog_red", ModBlock.FROG_RED);
    public static final Item FROG_WHITE = registerWearableBlockItem("frog_white", ModBlock.FROG_WHITE);
    public static final Item FROG_YELLOW = registerWearableBlockItem("frog_yellow", ModBlock.FROG_YELLOW);

    public static final Item MUSHROOM_BLACK = registerWearableBlockItem("mushroom_black", ModBlock.MUSHROOM_BLACK);
    public static final Item MUSHROOM_BLUE = registerWearableBlockItem("mushroom_blue", ModBlock.MUSHROOM_BLUE);
    public static final Item MUSHROOM_BROWN = registerWearableBlockItem("mushroom_brown", ModBlock.MUSHROOM_BROWN);
    public static final Item MUSHROOM_CYAN = registerWearableBlockItem("mushroom_cyan", ModBlock.MUSHROOM_CYAN);
    public static final Item MUSHROOM_GRAY = registerWearableBlockItem("mushroom_gray", ModBlock.MUSHROOM_GRAY);
    public static final Item MUSHROOM_GREEN = registerWearableBlockItem("mushroom_green", ModBlock.MUSHROOM_GREEN);
    public static final Item MUSHROOM_LIGHT_BLUE = registerWearableBlockItem("mushroom_light_blue", ModBlock.MUSHROOM_LIGHT_BLUE);
    public static final Item MUSHROOM_LIGHT_GRAY = registerWearableBlockItem("mushroom_light_gray", ModBlock.MUSHROOM_LIGHT_GRAY);
    public static final Item MUSHROOM_LIME = registerWearableBlockItem("mushroom_lime", ModBlock.MUSHROOM_LIME);
    public static final Item MUSHROOM_MAGENTA = registerWearableBlockItem("mushroom_magenta", ModBlock.MUSHROOM_MAGENTA);
    public static final Item MUSHROOM_ORANGE = registerWearableBlockItem("mushroom_orange", ModBlock.MUSHROOM_ORANGE);
    public static final Item MUSHROOM_PINK = registerWearableBlockItem("mushroom_pink", ModBlock.MUSHROOM_PINK);
    public static final Item MUSHROOM_PURPLE = registerWearableBlockItem("mushroom_purple", ModBlock.MUSHROOM_PURPLE);
    public static final Item MUSHROOM_RED = registerWearableBlockItem("mushroom_red", ModBlock.MUSHROOM_RED);
    public static final Item MUSHROOM_WHITE = registerWearableBlockItem("mushroom_white", ModBlock.MUSHROOM_WHITE);
    public static final Item MUSHROOM_YELLOW = registerWearableBlockItem("mushroom_yellow", ModBlock.MUSHROOM_YELLOW);

    public static final Item OCTOPUS_BLACK = registerWearableBlockItem("octopus_black", ModBlock.OCTOPUS_BLACK);
    public static final Item OCTOPUS_BLUE = registerWearableBlockItem("octopus_blue", ModBlock.OCTOPUS_BLUE);
    public static final Item OCTOPUS_BROWN = registerWearableBlockItem("octopus_brown", ModBlock.OCTOPUS_BROWN);
    public static final Item OCTOPUS_CYAN = registerWearableBlockItem("octopus_cyan", ModBlock.OCTOPUS_CYAN);
    public static final Item OCTOPUS_GRAY = registerWearableBlockItem("octopus_gray", ModBlock.OCTOPUS_GRAY);
    public static final Item OCTOPUS_GREEN = registerWearableBlockItem("octopus_green", ModBlock.OCTOPUS_GREEN);
    public static final Item OCTOPUS_LIGHT_BLUE = registerWearableBlockItem("octopus_light_blue", ModBlock.OCTOPUS_LIGHT_BLUE);
    public static final Item OCTOPUS_LIGHT_GRAY = registerWearableBlockItem("octopus_light_gray", ModBlock.OCTOPUS_LIGHT_GRAY);
    public static final Item OCTOPUS_LIME = registerWearableBlockItem("octopus_lime", ModBlock.OCTOPUS_LIME);
    public static final Item OCTOPUS_MAGENTA = registerWearableBlockItem("octopus_magenta", ModBlock.OCTOPUS_MAGENTA);
    public static final Item OCTOPUS_ORANGE = registerWearableBlockItem("octopus_orange", ModBlock.OCTOPUS_ORANGE);
    public static final Item OCTOPUS_PINK = registerWearableBlockItem("octopus_pink", ModBlock.OCTOPUS_PINK);
    public static final Item OCTOPUS_PURPLE = registerWearableBlockItem("octopus_purple", ModBlock.OCTOPUS_PURPLE);
    public static final Item OCTOPUS_RED = registerWearableBlockItem("octopus_red", ModBlock.OCTOPUS_RED);
    public static final Item OCTOPUS_WHITE = registerWearableBlockItem("octopus_white", ModBlock.OCTOPUS_WHITE);
    public static final Item OCTOPUS_YELLOW = registerWearableBlockItem("octopus_yellow", ModBlock.OCTOPUS_YELLOW);

    // Hanging Lights Items
    public static final Item HANGING_LIGHTS_BLACK = registerBlockItem("hanging_lights_black", ModBlock.HANGING_LIGHTS_BLACK);
    public static final Item HANGING_LIGHTS_BLUE = registerBlockItem("hanging_lights_blue", ModBlock.HANGING_LIGHTS_BLUE);
    public static final Item HANGING_LIGHTS_BROWN = registerBlockItem("hanging_lights_brown", ModBlock.HANGING_LIGHTS_BROWN);
    public static final Item HANGING_LIGHTS_CYAN = registerBlockItem("hanging_lights_cyan", ModBlock.HANGING_LIGHTS_CYAN);
    public static final Item HANGING_LIGHTS_DEFAULT = registerBlockItem("hanging_lights_default", ModBlock.HANGING_LIGHTS_DEFAULT);
    public static final Item HANGING_LIGHTS_GRAY = registerBlockItem("hanging_lights_gray", ModBlock.HANGING_LIGHTS_GRAY);
    public static final Item HANGING_LIGHTS_GREEN = registerBlockItem("hanging_lights_green", ModBlock.HANGING_LIGHTS_GREEN);
    public static final Item HANGING_LIGHTS_LIGHT_BLUE = registerBlockItem("hanging_lights_light_blue", ModBlock.HANGING_LIGHTS_LIGHT_BLUE);
    public static final Item HANGING_LIGHTS_LIGHT_GRAY = registerBlockItem("hanging_lights_light_gray", ModBlock.HANGING_LIGHTS_LIGHT_GRAY);
    public static final Item HANGING_LIGHTS_LIME = registerBlockItem("hanging_lights_lime", ModBlock.HANGING_LIGHTS_LIME);
    public static final Item HANGING_LIGHTS_MAGENTA = registerBlockItem("hanging_lights_magenta", ModBlock.HANGING_LIGHTS_MAGENTA);
    public static final Item HANGING_LIGHTS_ORANGE = registerBlockItem("hanging_lights_orange", ModBlock.HANGING_LIGHTS_ORANGE);
    public static final Item HANGING_LIGHTS_PINK = registerBlockItem("hanging_lights_pink", ModBlock.HANGING_LIGHTS_PINK);
    public static final Item HANGING_LIGHTS_PURPLE = registerBlockItem("hanging_lights_purple", ModBlock.HANGING_LIGHTS_PURPLE);
    public static final Item HANGING_LIGHTS_RED = registerBlockItem("hanging_lights_red", ModBlock.HANGING_LIGHTS_RED);
    public static final Item HANGING_LIGHTS_WHITE = registerBlockItem("hanging_lights_white", ModBlock.HANGING_LIGHTS_WHITE);
    public static final Item HANGING_LIGHTS_YELLOW = registerBlockItem("hanging_lights_yellow", ModBlock.HANGING_LIGHTS_YELLOW);

    // Fairy Lights Items
    public static final Item FAIRY_LIGHTS_BLACK = registerBlockItem("fairy_lights_black", ModBlock.FAIRY_LIGHTS_BLACK);
    public static final Item FAIRY_LIGHTS_BLUE = registerBlockItem("fairy_lights_blue", ModBlock.FAIRY_LIGHTS_BLUE);
    public static final Item FAIRY_LIGHTS_BROWN = registerBlockItem("fairy_lights_brown", ModBlock.FAIRY_LIGHTS_BROWN);
    public static final Item FAIRY_LIGHTS_CYAN = registerBlockItem("fairy_lights_cyan", ModBlock.FAIRY_LIGHTS_CYAN);
    public static final Item FAIRY_LIGHTS_DEFAULT = registerBlockItem("fairy_lights_default", ModBlock.FAIRY_LIGHTS_DEFAULT);
    public static final Item FAIRY_LIGHTS_GRAY = registerBlockItem("fairy_lights_gray", ModBlock.FAIRY_LIGHTS_GRAY);
    public static final Item FAIRY_LIGHTS_GREEN = registerBlockItem("fairy_lights_green", ModBlock.FAIRY_LIGHTS_GREEN);
    public static final Item FAIRY_LIGHTS_LIGHT_BLUE = registerBlockItem("fairy_lights_light_blue", ModBlock.FAIRY_LIGHTS_LIGHT_BLUE);
    public static final Item FAIRY_LIGHTS_LIGHT_GRAY = registerBlockItem("fairy_lights_light_gray", ModBlock.FAIRY_LIGHTS_LIGHT_GRAY);
    public static final Item FAIRY_LIGHTS_LIME = registerBlockItem("fairy_lights_lime", ModBlock.FAIRY_LIGHTS_LIME);
    public static final Item FAIRY_LIGHTS_MAGENTA = registerBlockItem("fairy_lights_magenta", ModBlock.FAIRY_LIGHTS_MAGENTA);
    public static final Item FAIRY_LIGHTS_ORANGE = registerBlockItem("fairy_lights_orange", ModBlock.FAIRY_LIGHTS_ORANGE);
    public static final Item FAIRY_LIGHTS_PINK = registerBlockItem("fairy_lights_pink", ModBlock.FAIRY_LIGHTS_PINK);
    public static final Item FAIRY_LIGHTS_PURPLE = registerBlockItem("fairy_lights_purple", ModBlock.FAIRY_LIGHTS_PURPLE);
    public static final Item FAIRY_LIGHTS_RED = registerBlockItem("fairy_lights_red", ModBlock.FAIRY_LIGHTS_RED);
    public static final Item FAIRY_LIGHTS_WHITE = registerBlockItem("fairy_lights_white", ModBlock.FAIRY_LIGHTS_WHITE);
    public static final Item FAIRY_LIGHTS_YELLOW = registerBlockItem("fairy_lights_yellow", ModBlock.FAIRY_LIGHTS_YELLOW);

    private static Item registerWearableBlockItem(String name, Block block) {
        return Registry.register(BuiltInRegistries.ITEM, RegisterHelper.id(name),
                new WearableBlockItem(block, new Item.Properties().setId(RegisterHelper.itemKey(name))));
    }

    private static Item registerBlockItem(String name, Block block) {
        return Registry.register(BuiltInRegistries.ITEM, RegisterHelper.id(name),
                new BlockItem(block, new Item.Properties().setId(RegisterHelper.itemKey(name))));
    }

    public static void init() {
    }
}