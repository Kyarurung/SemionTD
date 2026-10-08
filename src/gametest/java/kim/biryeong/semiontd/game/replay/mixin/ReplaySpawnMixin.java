package kim.biryeong.semiontd.game.replay.mixin;

import kim.biryeong.semiontd.game.replay.ReplayCapture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class ReplaySpawnMixin {
    @Inject(method = "addFreshEntity", at = @At("RETURN"))
    private void recordSpawn(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        ReplayCapture capture = ReplayCapture.current((ServerLevel) (Object) this);
        if (capture != null) {
            capture.spawn(entity, callback.getReturnValue());
        }
    }
}
