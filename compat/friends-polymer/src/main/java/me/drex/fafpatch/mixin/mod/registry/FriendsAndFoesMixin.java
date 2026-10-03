package me.drex.fafpatch.mixin.mod.registry;

import com.faboslav.friendsandfoes.common.FriendsAndFoes;
import me.drex.fafpatch.impl.FriendsAndFoesPatch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FriendsAndFoes.class)
public class FriendsAndFoesMixin {
    @Inject(method = "init", remap = false, at = @At("TAIL"))
    private static void lateInit(CallbackInfo ci) {
        FriendsAndFoesPatch.LATE_INIT.forEach(Runnable::run);
        FriendsAndFoesPatch.LATE_INIT.clear();
    }
}
