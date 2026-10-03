package me.drex.fafpatch.mixin.mod.registry;

import com.faboslav.friendsandfoes.common.init.FriendsAndFoesPotions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teamresourceful.resourcefullib.common.registry.HolderRegistryEntry;
import eu.pb4.polymer.core.api.other.SimplePolymerPotion;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FriendsAndFoesPotions.class)
public abstract class FriendsAndFoesPotionsMixin {
    @WrapOperation(
        method = {
            "lambda$static$0",
            "lambda$static$1",
            "lambda$static$2"
        },
        at = @At(
            value = "NEW",
            target = "(Ljava/lang/String;[Lnet/minecraft/world/effect/MobEffectInstance;)Lnet/minecraft/world/item/alchemy/Potion;"
        )
    )
    private static Potion polymerify(String string, MobEffectInstance[] mobEffectInstances, Operation<HolderRegistryEntry<Potion>> original) {
        return new SimplePolymerPotion(string, mobEffectInstances);
    }
}
