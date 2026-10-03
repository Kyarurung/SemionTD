package com.flechazo.nightlights;

import com.flechazo.nightlights.init.ModBlock;
import com.flechazo.nightlights.init.ModCreativeTab;
import com.flechazo.nightlights.init.ModItem;
import net.fabricmc.api.ModInitializer;

public class NightLights implements ModInitializer {

    public static String MODID = "nightlights";

    @Override
    public void onInitialize() {
        ModBlock.init();
        ModItem.init();
        ModCreativeTab.register();
    }
}