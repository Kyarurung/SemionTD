package me.drex.fafpatch.impl.entity;

import com.faboslav.friendsandfoes.common.init.FriendsAndFoesEntityTypes;
import eu.pb4.factorytools.api.virtualentity.emuvanilla.PolyModelInstance;
import eu.pb4.factorytools.api.virtualentity.emuvanilla.model.EntityModel;
import me.drex.fafpatch.impl.entity.holder.*;
import me.drex.fafpatch.impl.entity.model.EntityModels;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;

import java.util.IdentityHashMap;
import java.util.function.Function;

public class AnimatedEntities {

    protected static final IdentityHashMap<EntityType<?>, Function<Entity, ? extends SimpleElementHolder<?, ?>>> ENTITY_FACTORIES = new IdentityHashMap<>();

    static {
        registerEntity(FriendsAndFoesEntityTypes.CRAB.get(), EntityModels.CRAB, CrabElementHolder::new);
        registerEntity(FriendsAndFoesEntityTypes.GLARE.get(), EntityModels.GLARE, GlareElementHolder::new);
        registerEntity(FriendsAndFoesEntityTypes.ICEOLOGER.get(), EntityModels.ICEOLOGER, SimpleElementHolder::new);
        registerEntity(FriendsAndFoesEntityTypes.ILLUSIONER.get(), EntityModels.ILLUSIONER, SimpleElementHolder::new/*IllusionerElementHolder::new*/);
        registerEntity(FriendsAndFoesEntityTypes.ICE_CHUNK.get(), EntityModels.ICE_CHUNK, SimpleElementHolder::new);
        registerEntity(FriendsAndFoesEntityTypes.MAULER.get(), null, MaulerElementHolder::new);
        registerEntity(FriendsAndFoesEntityTypes.MOOBLOOM.get(), null, MoobloomElementHolder::new);
        registerEntity(FriendsAndFoesEntityTypes.RASCAL.get(), EntityModels.RASCAL, SimpleElementHolder::new);
        registerEntity(FriendsAndFoesEntityTypes.TUFF_GOLEM.get(), EntityModels.TUFF_GOLEM, TuffGolemElementHolder::new);
        registerEntity(FriendsAndFoesEntityTypes.WILDFIRE.get(), EntityModels.WILDFIRE, SimpleElementHolder::new);
        registerEntity(EntityTypes.VILLAGER, EntityModels.VILLAGER, VillagerElementHolder::new);
    }

    public static <T extends Entity, X extends EntityModel<T>> void registerEntity(EntityType<T> type, PolyModelInstance<X> defaultModel, Function<T, ? extends SimpleElementHolder<T, X>> factory) {
        ENTITY_FACTORIES.put(type, entity -> {
            SimpleElementHolder<T, X> elementHolder = factory.apply((T) entity);
            if (defaultModel != null) {
                elementHolder.setMainModel(defaultModel);
            }
            return elementHolder;
        });
    }
}
