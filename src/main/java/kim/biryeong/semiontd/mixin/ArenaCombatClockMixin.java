package kim.biryeong.semiontd.mixin;

import kim.biryeong.semiontd.game.ArenaCombatClock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelAccessor.class)
interface ArenaCombatClockMixin {
    @Inject(method = "getGameTime", at = @At("RETURN"), cancellable = true)
    private void semiontd$arenaGameTime(CallbackInfoReturnable<Long> callback) {
        if ((Object) this instanceof ServerLevel world) {
            callback.setReturnValue(ArenaCombatClock.gameTime(world, callback.getReturnValue()));
        }
    }
}
