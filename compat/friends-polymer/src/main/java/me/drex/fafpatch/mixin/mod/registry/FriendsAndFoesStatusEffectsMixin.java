package me.drex.fafpatch.mixin.mod.registry;

import com.faboslav.friendsandfoes.common.init.FriendsAndFoesStatusEffects;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teamresourceful.resourcefullib.common.registry.HolderRegistryEntry;
import com.teamresourceful.resourcefullib.common.registry.ResourcefulRegistry;
import eu.pb4.polymer.core.api.other.PolymerMobEffect;
import net.minecraft.world.effect.MobEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Supplier;

@Mixin(FriendsAndFoesStatusEffects.class)
public abstract class FriendsAndFoesStatusEffectsMixin {
    @WrapOperation(
        method = "<clinit>",
        at = @At(
            value = "INVOKE",
            remap = false,
            target = "Lcom/teamresourceful/resourcefullib/common/registry/ResourcefulRegistry;registerHolder(Ljava/lang/String;Ljava/util/function/Supplier;)Lcom/teamresourceful/resourcefullib/common/registry/HolderRegistryEntry;"
        )
    )
    private static HolderRegistryEntry<MobEffect> polymerify(ResourcefulRegistry<MobEffect> instance, String s, Supplier<MobEffect> tSupplier, Operation<HolderRegistryEntry<MobEffect>> original) {
        HolderRegistryEntry<MobEffect> registryEntry = original.call(instance, s, tSupplier);
        PolymerMobEffect.registerOverlay(registryEntry.get());
        return registryEntry;
    }

}
