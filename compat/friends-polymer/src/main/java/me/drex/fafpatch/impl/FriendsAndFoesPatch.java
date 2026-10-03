package me.drex.fafpatch.impl;

import com.faboslav.friendsandfoes.common.FriendsAndFoes;
import com.faboslav.friendsandfoes.common.init.FriendsAndFoesVillagerProfessions;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.resourcepack.extras.api.ResourcePackExtras;
import eu.pb4.polymer.resourcepack.extras.api.format.item.ItemAsset;
import eu.pb4.polymer.resourcepack.extras.api.format.item.model.BasicItemModel;
import eu.pb4.polymer.resourcepack.extras.api.format.item.tint.ConstantTintSource;
import me.drex.fafpatch.impl.entity.BasePolymerEntity;
import me.drex.fafpatch.impl.res.ResourcePackGenerator;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class FriendsAndFoesPatch implements ModInitializer {
    public static final String MOD_ID = "friendsandfoes-polymer-patch";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final List<Runnable> LATE_INIT = new ArrayList<>();

    @Override
    public void onInitialize() {
        PolymerResourcePackUtils.addModAssets(FriendsAndFoes.MOD_ID);
        PolymerResourcePackUtils.addModAssets("flowerymooblooms");
        PolymerResourcePackUtils.addModAssets(MOD_ID);
        ResourcePackExtras.forDefault().addBridgedModelsFolder(FriendsAndFoes.makeID("block"));
        ResourcePackExtras.forDefault().addBridgedModelsFolder(FriendsAndFoes.makeID("entity"), (id, b) -> {
            return new ItemAsset(new BasicItemModel(id, List.of(new ConstantTintSource(0xFFFFFF))), new ItemAsset.Properties(true, true));
        });
        ResourcePackGenerator.setup();

        PolymerEntityUtils.registerPolymerEntityConstructor(EntityTypes.VILLAGER, villager -> {
            if (villager.getVillagerData().profession().is(FriendsAndFoesVillagerProfessions.BEEKEEPER_KEY)) {
                return new BasePolymerEntity(villager);
            } else {
                return null;
            }
        });
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
