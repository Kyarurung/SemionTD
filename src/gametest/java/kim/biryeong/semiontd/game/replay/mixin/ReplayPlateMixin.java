package kim.biryeong.semiontd.game.replay.mixin;

import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.replay.ReplayCapture;
import kim.biryeong.semiontd.tower.engineer.EngineerCircuitTower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EngineerCircuitTower.class)
public abstract class ReplayPlateMixin {
    @Inject(method = "pressPlate", at = @At("RETURN"))
    private void recordPress(PlayerLane lane, CallbackInfoReturnable<Boolean> callback) {
        ReplayCapture capture = lane == null ? null : ReplayCapture.current(lane.arenaWorld());
        if (capture != null) {
            capture.plate((EngineerCircuitTower) (Object) this, callback.getReturnValue());
        }
    }
}
