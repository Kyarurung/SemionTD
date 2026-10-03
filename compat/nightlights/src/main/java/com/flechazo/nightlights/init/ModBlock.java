package com.flechazo.nightlights.init;

import com.flechazo.nightlights.block.CeilingLightBlock;
import com.flechazo.nightlights.block.NightLightFrogBlock;
import com.flechazo.nightlights.block.NightLightMushroomBlock;
import com.flechazo.nightlights.block.NightLightOctopusBlock;
import com.flechazo.nightlights.util.RegisterHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class ModBlock {

    // Night Light Blocks (Frog, Mushroom, Octopus)
    public static final Block FROG_BLACK = registerNightLight("frog_black");
    public static final Block FROG_BLUE = registerNightLight("frog_blue");
    public static final Block FROG_BROWN = registerNightLight("frog_brown");
    public static final Block FROG_CYAN = registerNightLight("frog_cyan");
    public static final Block FROG_GRAY = registerNightLight("frog_gray");
    public static final Block FROG_GREEN = registerNightLight("frog_green");
    public static final Block FROG_LIGHT_BLUE = registerNightLight("frog_light_blue");
    public static final Block FROG_LIGHT_GRAY = registerNightLight("frog_light_gray");
    public static final Block FROG_LIME = registerNightLight("frog_lime");
    public static final Block FROG_MAGENTA = registerNightLight("frog_magenta");
    public static final Block FROG_ORANGE = registerNightLight("frog_orange");
    public static final Block FROG_PINK = registerNightLight("frog_pink");
    public static final Block FROG_PURPLE = registerNightLight("frog_purple");
    public static final Block FROG_RED = registerNightLight("frog_red");
    public static final Block FROG_WHITE = registerNightLight("frog_white");
    public static final Block FROG_YELLOW = registerNightLight("frog_yellow");

    public static final Block MUSHROOM_BLACK = registerNightLight("mushroom_black");
    public static final Block MUSHROOM_BLUE = registerNightLight("mushroom_blue");
    public static final Block MUSHROOM_BROWN = registerNightLight("mushroom_brown");
    public static final Block MUSHROOM_CYAN = registerNightLight("mushroom_cyan");
    public static final Block MUSHROOM_GRAY = registerNightLight("mushroom_gray");
    public static final Block MUSHROOM_GREEN = registerNightLight("mushroom_green");
    public static final Block MUSHROOM_LIGHT_BLUE = registerNightLight("mushroom_light_blue");
    public static final Block MUSHROOM_LIGHT_GRAY = registerNightLight("mushroom_light_gray");
    public static final Block MUSHROOM_LIME = registerNightLight("mushroom_lime");
    public static final Block MUSHROOM_MAGENTA = registerNightLight("mushroom_magenta");
    public static final Block MUSHROOM_ORANGE = registerNightLight("mushroom_orange");
    public static final Block MUSHROOM_PINK = registerNightLight("mushroom_pink");
    public static final Block MUSHROOM_PURPLE = registerNightLight("mushroom_purple");
    public static final Block MUSHROOM_RED = registerNightLight("mushroom_red");
    public static final Block MUSHROOM_WHITE = registerNightLight("mushroom_white");
    public static final Block MUSHROOM_YELLOW = registerNightLight("mushroom_yellow");

    public static final Block OCTOPUS_BLACK = registerNightLight("octopus_black");
    public static final Block OCTOPUS_BLUE = registerNightLight("octopus_blue");
    public static final Block OCTOPUS_BROWN = registerNightLight("octopus_brown");
    public static final Block OCTOPUS_CYAN = registerNightLight("octopus_cyan");
    public static final Block OCTOPUS_GRAY = registerNightLight("octopus_gray");
    public static final Block OCTOPUS_GREEN = registerNightLight("octopus_green");
    public static final Block OCTOPUS_LIGHT_BLUE = registerNightLight("octopus_light_blue");
    public static final Block OCTOPUS_LIGHT_GRAY = registerNightLight("octopus_light_gray");
    public static final Block OCTOPUS_LIME = registerNightLight("octopus_lime");
    public static final Block OCTOPUS_MAGENTA = registerNightLight("octopus_magenta");
    public static final Block OCTOPUS_ORANGE = registerNightLight("octopus_orange");
    public static final Block OCTOPUS_PINK = registerNightLight("octopus_pink");
    public static final Block OCTOPUS_PURPLE = registerNightLight("octopus_purple");
    public static final Block OCTOPUS_RED = registerNightLight("octopus_red");
    public static final Block OCTOPUS_WHITE = registerNightLight("octopus_white");
    public static final Block OCTOPUS_YELLOW = registerNightLight("octopus_yellow");

    // Hanging Lights
    public static final Block HANGING_LIGHTS_BLACK = registerCeilingLight("hanging_lights_black");
    public static final Block HANGING_LIGHTS_BLUE = registerCeilingLight("hanging_lights_blue");
    public static final Block HANGING_LIGHTS_BROWN = registerCeilingLight("hanging_lights_brown");
    public static final Block HANGING_LIGHTS_CYAN = registerCeilingLight("hanging_lights_cyan");
    public static final Block HANGING_LIGHTS_DEFAULT = registerCeilingLight("hanging_lights_default");
    public static final Block HANGING_LIGHTS_GRAY = registerCeilingLight("hanging_lights_gray");
    public static final Block HANGING_LIGHTS_GREEN = registerCeilingLight("hanging_lights_green");
    public static final Block HANGING_LIGHTS_LIGHT_BLUE = registerCeilingLight("hanging_lights_light_blue");
    public static final Block HANGING_LIGHTS_LIGHT_GRAY = registerCeilingLight("hanging_lights_light_gray");
    public static final Block HANGING_LIGHTS_LIME = registerCeilingLight("hanging_lights_lime");
    public static final Block HANGING_LIGHTS_MAGENTA = registerCeilingLight("hanging_lights_magenta");
    public static final Block HANGING_LIGHTS_ORANGE = registerCeilingLight("hanging_lights_orange");
    public static final Block HANGING_LIGHTS_PINK = registerCeilingLight("hanging_lights_pink");
    public static final Block HANGING_LIGHTS_PURPLE = registerCeilingLight("hanging_lights_purple");
    public static final Block HANGING_LIGHTS_RED = registerCeilingLight("hanging_lights_red");
    public static final Block HANGING_LIGHTS_WHITE = registerCeilingLight("hanging_lights_white");
    public static final Block HANGING_LIGHTS_YELLOW = registerCeilingLight("hanging_lights_yellow");

    // Fairy Lights
    public static final Block FAIRY_LIGHTS_BLACK = registerCeilingLight("fairy_lights_black");
    public static final Block FAIRY_LIGHTS_BLUE = registerCeilingLight("fairy_lights_blue");
    public static final Block FAIRY_LIGHTS_BROWN = registerCeilingLight("fairy_lights_brown");
    public static final Block FAIRY_LIGHTS_CYAN = registerCeilingLight("fairy_lights_cyan");
    public static final Block FAIRY_LIGHTS_DEFAULT = registerCeilingLight("fairy_lights_default");
    public static final Block FAIRY_LIGHTS_GRAY = registerCeilingLight("fairy_lights_gray");
    public static final Block FAIRY_LIGHTS_GREEN = registerCeilingLight("fairy_lights_green");
    public static final Block FAIRY_LIGHTS_LIGHT_BLUE = registerCeilingLight("fairy_lights_light_blue");
    public static final Block FAIRY_LIGHTS_LIGHT_GRAY = registerCeilingLight("fairy_lights_light_gray");
    public static final Block FAIRY_LIGHTS_LIME = registerCeilingLight("fairy_lights_lime");
    public static final Block FAIRY_LIGHTS_MAGENTA = registerCeilingLight("fairy_lights_magenta");
    public static final Block FAIRY_LIGHTS_ORANGE = registerCeilingLight("fairy_lights_orange");
    public static final Block FAIRY_LIGHTS_PINK = registerCeilingLight("fairy_lights_pink");
    public static final Block FAIRY_LIGHTS_PURPLE = registerCeilingLight("fairy_lights_purple");
    public static final Block FAIRY_LIGHTS_RED = registerCeilingLight("fairy_lights_red");
    public static final Block FAIRY_LIGHTS_WHITE = registerCeilingLight("fairy_lights_white");
    public static final Block FAIRY_LIGHTS_YELLOW = registerCeilingLight("fairy_lights_yellow");

    private static Block registerNightLight(String name) {
        ResourceKey<Block> blockKey = RegisterHelper.blockKey(name);

        Block block;
        if (name.startsWith("frog_")) {
            block = new NightLightFrogBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.FLOWER_POT)
                            .noOcclusion()
                            .sound(SoundType.GLASS)
                            .setId(blockKey)
            );
        } else if (name.startsWith("mushroom_")) {
            block = new NightLightMushroomBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.FLOWER_POT)
                            .noOcclusion()
                            .sound(SoundType.GLASS)
                            .setId(blockKey)
            );
        } else if (name.startsWith("octopus_")) {
            block = new NightLightOctopusBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.FLOWER_POT)
                            .noOcclusion()
                            .sound(SoundType.GLASS)
                            .setId(blockKey)
            );
        } else {
            block = new NightLightFrogBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.FLOWER_POT)
                            .noOcclusion()
                            .sound(SoundType.GLASS)
                            .setId(blockKey)
            );
        }

        return Registry.register(BuiltInRegistries.BLOCK, RegisterHelper.id(name), block);
    }

    private static Block registerCeilingLight(String name) {
        ResourceKey<Block> blockKey = RegisterHelper.blockKey(name);

        Block block = new CeilingLightBlock(
                BlockBehaviour.Properties.ofFullCopy(Blocks.FLOWER_POT)
                        .noOcclusion()
                        .sound(SoundType.GLASS)
                        .noCollision()
                        .instabreak()
                        .setId(blockKey)
        );

        return Registry.register(BuiltInRegistries.BLOCK, RegisterHelper.id(name), block);
    }

    public static void init() {
    }
}