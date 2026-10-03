package me.drex.fafpatch.impl.entity.model;

import com.faboslav.friendsandfoes.common.FriendsAndFoes;
import com.faboslav.friendsandfoes.common.api.MoobloomVariant;
import com.faboslav.friendsandfoes.common.api.MoobloomVariantManager;
import com.faboslav.friendsandfoes.common.entity.*;
import eu.pb4.factorytools.api.virtualentity.emuvanilla.PolyModelInstance;
import eu.pb4.factorytools.api.virtualentity.emuvanilla.model.EntityModel;
import eu.pb4.factorytools.api.virtualentity.emuvanilla.model.LayerDefinition;
import eu.pb4.factorytools.api.virtualentity.emuvanilla.model.MeshTransformer;
import eu.pb4.factorytools.api.virtualentity.emuvanilla.model.ModelPart;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.drex.fafpatch.impl.entity.holder.MoobloomElementHolder;
import me.drex.fafpatch.impl.entity.model.entity.*;
import me.drex.fafpatch.impl.res.ResourcePackGenerator;
import net.minecraft.util.Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.*;
import java.util.function.Function;

public interface EntityModels {
    List<PolyModelInstance<?>> ALL = new ArrayList<>();

    Map<MoobloomElementHolder.RenderState, PolyModelInstance<CowModel<MoobloomEntity>>> MOOBLOOM = new HashMap<>() {{
        for (boolean isBaby : new boolean[]{false, true}) {
            for (MoobloomVariant moobloomVariant : MoobloomVariantManager.MOOBLOOM_VARIANT_MANAGER.getMoobloomVariants()) {
                LayerDefinition modelData;
                String path;
                if (isBaby) {
                    modelData = BabyCowModel.createBodyLayer();
                    path = "entity/moobloom/moobloom_" + moobloomVariant.getName() + "_baby";
                } else {
                    modelData = CowModel.createBodyLayer();
                    path = "entity/moobloom/moobloom_" + moobloomVariant.getName();
                }

                put(new MoobloomElementHolder.RenderState(moobloomVariant.getName(), isBaby), create(CowModel::new, modelData, FriendsAndFoes.makeID(path)));
            }
        }
    }};

    PolyModelInstance<CrabEntityModel> CRAB = create(CrabEntityModel::new, CrabEntityModel.getTexturedModelData(), FriendsAndFoes.makeID("entity/crab/crab"));

    PolyModelInstance<GlareEntityModel> GLARE = create(GlareEntityModel::new, GlareEntityModel.getTexturedModelData(), FriendsAndFoes.makeID("entity/glare/glare"));
    PolyModelInstance<GlareEntityModel> FLOWERING_GLARE = create(GlareEntityModel::new, GlareEntityModel.getTexturedModelData(), FriendsAndFoes.makeID("entity/glare/flowering_glare"));

    PolyModelInstance<IceologerIceChunkModel> ICE_CHUNK = create(IceologerIceChunkModel::new, IceologerIceChunkModel.getTexturedModelData(), FriendsAndFoes.makeID("entity/illager/ice_chunk"));
    PolyModelInstance<RascalEntityModel> RASCAL = create(RascalEntityModel::new, RascalEntityModel.getTexturedModelData(), FriendsAndFoes.makeID("entity/rascal/rascal"));

    PolyModelInstance<TuffGolemEntityModel> TUFF_GOLEM = create(TuffGolemEntityModel::new, TuffGolemEntityModel.getTexturedModelData(), FriendsAndFoes.makeID("entity/tuff_golem/tuff_golem"));
    PolyModelInstance<TuffGolemEntityModel> TUFF_GOLEM_CLOSED_EYES = create(TuffGolemEntityModel::new, TuffGolemEntityModel.getTexturedModelData(), FriendsAndFoes.makeID("entity/tuff_golem/closed_eyes"));
    EnumMap<TuffGolemEntity.Color, PolyModelInstance<TuffGolemEntityModel>> TUFF_GOLEM_CLOTH = Util.make(new EnumMap<>(TuffGolemEntity.Color.class), m -> {
        for (var color : TuffGolemEntity.Color.values()) {
            var instance = create(TuffGolemEntityModel::new, TuffGolemEntityModel.getTexturedModelData(), FriendsAndFoes.makeID("entity/tuff_golem/" + color.getName()));
            m.put(color, instance);
        }
    });

    MeshTransformer humanLikeScaling = MeshTransformer.scaling(0.9375F);
    LayerDefinition villagerData = LayerDefinition.create(VillagerModel.createBodyModel(), 64, 64).apply(humanLikeScaling);
    PolyModelInstance<VillagerModel> VILLAGER = create(modelPart -> new VillagerModel(modelPart, true), villagerData, FriendsAndFoes.makeID("entity/villager/villager"));
    Map<Identifier, PolyModelInstance<VillagerModel>> VILLAGER_PROFESSION = Util.make(new HashMap<>(), m -> {
        var instance = create(modelPart -> new VillagerModel(modelPart, true), villagerData, FriendsAndFoes.makeID("entity/villager/profession/beekeeper"));
        m.put(FriendsAndFoes.makeID("beekeeper"), instance);
    });

    Int2ObjectMap<PolyModelInstance<VillagerModel>> VILLAGER_PROFESSION_LEVEL = Util.make(new Int2ObjectOpenHashMap<>(), m -> {
        ResourcePackGenerator.LEVEL_LOCATIONS.forEach((level, Identifier) -> {
            var instance = create(modelPart -> new VillagerModel(modelPart, false), villagerData, FriendsAndFoes.makeID("entity/villager/profession_level/" + Identifier.getPath()));
            m.put(level, instance);
        });
    });
    Map<Identifier, PolyModelInstance<VillagerModel>> VILLAGER_TYPE = Util.make(new HashMap<>(), m -> {
        for (Identifier Identifier : BuiltInRegistries.VILLAGER_TYPE.keySet()) {
            var instance = create(modelPart -> new VillagerModel(modelPart, false), villagerData, FriendsAndFoes.makeID("entity/villager/type/" + Identifier.getPath()));
            m.put(Identifier, instance);
        }
    });


    PolyModelInstance<WildfireEntityModel> WILDFIRE = create(WildfireEntityModel::new, WildfireEntityModel.getTexturedModelData(), FriendsAndFoes.makeID("entity/wildfire/wildfire"));
    PolyModelInstance<IllagerModel<IllusionerEntity>> ILLUSIONER = create(IllagerModel::new, IllagerModel.createBodyLayer(), FriendsAndFoes.makeID("entity/illusioner/illusioner"));
    PolyModelInstance<IllagerModel<IceologerEntity>> ICEOLOGER = create(IllagerModel::new, IllagerModel.createBodyLayer(), FriendsAndFoes.makeID("entity/illager/iceologer"));
    EnumMap<MaulerEntity.Type, PolyModelInstance<MaulerEntityModel>> MAULER = Util.make(new EnumMap<>(MaulerEntity.Type.class), m -> {
        for (var type : MaulerEntity.Type.values()) {
            m.put(type, create(MaulerEntityModel::new, MaulerEntityModel.getTexturedModelData(), FriendsAndFoes.makeID("entity/mauler/mauler_" + type.getName())));
        }
    });

    static <T extends EntityModel<?>> PolyModelInstance<T> create(Function<ModelPart, T> modelCreator, LayerDefinition data, Identifier texture) {
        var instance = PolyModelInstance.create(modelCreator, data, texture);
        ALL.add(instance);
        return instance;
    }

}
