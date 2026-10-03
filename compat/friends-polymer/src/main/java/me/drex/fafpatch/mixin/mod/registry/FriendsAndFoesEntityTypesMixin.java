package me.drex.fafpatch.mixin.mod.registry;

import com.faboslav.friendsandfoes.common.init.FriendsAndFoesEntityTypes;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teamresourceful.resourcefullib.common.registry.RegistryEntry;
import com.teamresourceful.resourcefullib.common.registry.ResourcefulRegistry;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import me.drex.fafpatch.impl.entity.BasePolymerEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Supplier;

@Mixin(FriendsAndFoesEntityTypes.class)
public abstract class FriendsAndFoesEntityTypesMixin {
    @WrapOperation(
        method = "<clinit>",
        at = @At(
            value = "INVOKE",
            remap = false,
            target = "Lcom/teamresourceful/resourcefullib/common/registry/ResourcefulRegistry;register(Ljava/lang/String;Ljava/util/function/Supplier;)Lcom/teamresourceful/resourcefullib/common/registry/RegistryEntry;"
        )
    )
    private static RegistryEntry<EntityType<?>> polymerify(ResourcefulRegistry<EntityType<?>> instance, String s, Supplier<EntityType<?>> iSupplier, Operation<RegistryEntry<EntityType<?>>> original) {
        RegistryEntry<EntityType<?>> registryEntry = original.call(instance, s, iSupplier);
        PolymerEntityUtils.registerOverlay(registryEntry.get(), x -> new BasePolymerEntity((Entity) x));
        return registryEntry;
    }
}
