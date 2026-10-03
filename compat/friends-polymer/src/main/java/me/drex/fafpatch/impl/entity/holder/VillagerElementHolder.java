package me.drex.fafpatch.impl.entity.holder;

import me.drex.fafpatch.impl.FriendsAndFoesPatch;
import me.drex.fafpatch.impl.entity.SimpleElementHolder;
import me.drex.fafpatch.impl.entity.model.EntityModels;
import me.drex.fafpatch.impl.entity.model.entity.VillagerModel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.Villager;

public class VillagerElementHolder extends SimpleElementHolder<Villager, VillagerModel> {
    public static final Identifier PROFESSION_LEVEL_LAYER = FriendsAndFoesPatch.id("villager_profession_level_layer");
    public static final Identifier PROFESSION_LAYER = FriendsAndFoesPatch.id("villager_profession_layer");
    public static final Identifier TYPE_LAYER = FriendsAndFoesPatch.id("villager_type_layer");


    public VillagerElementHolder(Villager entity) {
        super(entity);
        addConditionalLayer(villager -> villager.getVillagerData().type().unwrapKey().get().identifier(), TYPE_LAYER, EntityModels.VILLAGER_TYPE::get);
        addConditionalLayer(villager -> villager.getVillagerData().profession().unwrapKey().get().identifier(), PROFESSION_LAYER, EntityModels.VILLAGER_PROFESSION::get);
        addConditionalLayer(villager -> villager.getVillagerData().level(), PROFESSION_LEVEL_LAYER, EntityModels.VILLAGER_PROFESSION_LEVEL::get);
    }
}
