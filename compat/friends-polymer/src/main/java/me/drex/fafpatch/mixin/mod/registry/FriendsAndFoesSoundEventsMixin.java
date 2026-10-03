package me.drex.fafpatch.mixin.mod.registry;

import com.faboslav.friendsandfoes.common.init.FriendsAndFoesSoundEvents;
import com.teamresourceful.resourcefullib.common.registry.HolderRegistryEntry;
import com.teamresourceful.resourcefullib.common.registry.RegistryEntry;
import eu.pb4.polymer.core.api.other.PolymerSoundEvent;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FriendsAndFoesSoundEvents.class)
public abstract class FriendsAndFoesSoundEventsMixin {
    @Inject(
        method = "registerSoundEvent",
        remap = false,
        at = @At("TAIL")
    )
    private static void polymerify(String path, CallbackInfoReturnable<RegistryEntry<SoundEvent>> cir) {
        PolymerSoundEvent.registerOverlay(cir.getReturnValue().get());
    }

    @Inject(
        method = "registerHolderSoundEvent",
        remap = false,
        at = @At("TAIL")
    )
    private static void polymerify2(String path, CallbackInfoReturnable<HolderRegistryEntry<SoundEvent>> cir) {
        PolymerSoundEvent.registerOverlay(cir.getReturnValue().get());
    }
}
