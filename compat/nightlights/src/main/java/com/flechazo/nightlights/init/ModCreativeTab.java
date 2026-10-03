package com.flechazo.nightlights.init;

import com.flechazo.nightlights.NightLights;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public class ModCreativeTab {
    public static CreativeModeTab NIGHT_LIGHTS_TAB;

    public static void register() {
        NIGHT_LIGHTS_TAB = register(FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.nightlights"))
                .icon(() -> new ItemStack(ModItem.FROG_RED))
                .displayItems((parameters, output) -> {
                    // Add all night light items
                    output.accept(ModItem.FROG_BLACK);
                    output.accept(ModItem.FROG_BLUE);
                    output.accept(ModItem.FROG_BROWN);
                    output.accept(ModItem.FROG_CYAN);
                    output.accept(ModItem.FROG_GRAY);
                    output.accept(ModItem.FROG_GREEN);
                    output.accept(ModItem.FROG_LIGHT_BLUE);
                    output.accept(ModItem.FROG_LIGHT_GRAY);
                    output.accept(ModItem.FROG_LIME);
                    output.accept(ModItem.FROG_MAGENTA);
                    output.accept(ModItem.FROG_ORANGE);
                    output.accept(ModItem.FROG_PINK);
                    output.accept(ModItem.FROG_PURPLE);
                    output.accept(ModItem.FROG_RED);
                    output.accept(ModItem.FROG_WHITE);
                    output.accept(ModItem.FROG_YELLOW);

                    // Add mushroom items
                    output.accept(ModItem.MUSHROOM_BLACK);
                    output.accept(ModItem.MUSHROOM_BLUE);
                    output.accept(ModItem.MUSHROOM_BROWN);
                    output.accept(ModItem.MUSHROOM_CYAN);
                    output.accept(ModItem.MUSHROOM_GRAY);
                    output.accept(ModItem.MUSHROOM_GREEN);
                    output.accept(ModItem.MUSHROOM_LIGHT_BLUE);
                    output.accept(ModItem.MUSHROOM_LIGHT_GRAY);
                    output.accept(ModItem.MUSHROOM_LIME);
                    output.accept(ModItem.MUSHROOM_MAGENTA);
                    output.accept(ModItem.MUSHROOM_ORANGE);
                    output.accept(ModItem.MUSHROOM_PINK);
                    output.accept(ModItem.MUSHROOM_PURPLE);
                    output.accept(ModItem.MUSHROOM_RED);
                    output.accept(ModItem.MUSHROOM_WHITE);
                    output.accept(ModItem.MUSHROOM_YELLOW);

                    // Add octopus items
                    output.accept(ModItem.OCTOPUS_BLACK);
                    output.accept(ModItem.OCTOPUS_BLUE);
                    output.accept(ModItem.OCTOPUS_BROWN);
                    output.accept(ModItem.OCTOPUS_CYAN);
                    output.accept(ModItem.OCTOPUS_GRAY);
                    output.accept(ModItem.OCTOPUS_GREEN);
                    output.accept(ModItem.OCTOPUS_LIGHT_BLUE);
                    output.accept(ModItem.OCTOPUS_LIGHT_GRAY);
                    output.accept(ModItem.OCTOPUS_LIME);
                    output.accept(ModItem.OCTOPUS_MAGENTA);
                    output.accept(ModItem.OCTOPUS_ORANGE);
                    output.accept(ModItem.OCTOPUS_PINK);
                    output.accept(ModItem.OCTOPUS_PURPLE);
                    output.accept(ModItem.OCTOPUS_RED);
                    output.accept(ModItem.OCTOPUS_WHITE);
                    output.accept(ModItem.OCTOPUS_YELLOW);

                    // Add hanging lights
                    output.accept(ModItem.HANGING_LIGHTS_BLACK);
                    output.accept(ModItem.HANGING_LIGHTS_BLUE);
                    output.accept(ModItem.HANGING_LIGHTS_BROWN);
                    output.accept(ModItem.HANGING_LIGHTS_CYAN);
                    output.accept(ModItem.HANGING_LIGHTS_DEFAULT);
                    output.accept(ModItem.HANGING_LIGHTS_GRAY);
                    output.accept(ModItem.HANGING_LIGHTS_GREEN);
                    output.accept(ModItem.HANGING_LIGHTS_LIGHT_BLUE);
                    output.accept(ModItem.HANGING_LIGHTS_LIGHT_GRAY);
                    output.accept(ModItem.HANGING_LIGHTS_LIME);
                    output.accept(ModItem.HANGING_LIGHTS_MAGENTA);
                    output.accept(ModItem.HANGING_LIGHTS_ORANGE);
                    output.accept(ModItem.HANGING_LIGHTS_PINK);
                    output.accept(ModItem.HANGING_LIGHTS_PURPLE);
                    output.accept(ModItem.HANGING_LIGHTS_RED);
                    output.accept(ModItem.HANGING_LIGHTS_WHITE);
                    output.accept(ModItem.HANGING_LIGHTS_YELLOW);

                    // Add fairy lights
                    output.accept(ModItem.FAIRY_LIGHTS_BLACK);
                    output.accept(ModItem.FAIRY_LIGHTS_BLUE);
                    output.accept(ModItem.FAIRY_LIGHTS_BROWN);
                    output.accept(ModItem.FAIRY_LIGHTS_CYAN);
                    output.accept(ModItem.FAIRY_LIGHTS_DEFAULT);
                    output.accept(ModItem.FAIRY_LIGHTS_GRAY);
                    output.accept(ModItem.FAIRY_LIGHTS_GREEN);
                    output.accept(ModItem.FAIRY_LIGHTS_LIGHT_BLUE);
                    output.accept(ModItem.FAIRY_LIGHTS_LIGHT_GRAY);
                    output.accept(ModItem.FAIRY_LIGHTS_LIME);
                    output.accept(ModItem.FAIRY_LIGHTS_MAGENTA);
                    output.accept(ModItem.FAIRY_LIGHTS_ORANGE);
                    output.accept(ModItem.FAIRY_LIGHTS_PINK);
                    output.accept(ModItem.FAIRY_LIGHTS_PURPLE);
                    output.accept(ModItem.FAIRY_LIGHTS_RED);
                    output.accept(ModItem.FAIRY_LIGHTS_WHITE);
                    output.accept(ModItem.FAIRY_LIGHTS_YELLOW);
                })
                .build());
    }

    private static CreativeModeTab register(CreativeModeTab tab) {
        return Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(NightLights.MODID, "nightlights"), tab);
    }

}
