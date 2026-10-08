package kim.biryeong.semiontd.game.replay.mixin;

import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.replay.ReplayCapture;
import kim.biryeong.semiontd.tower.Tower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerLane.class)
public abstract class ReplayTowerIdentityMixin {
    @Inject(method = "addTower", at = @At("HEAD"))
    private void seedLogicalIdentity(Tower tower, CallbackInfo callback) {
        ReplayCapture capture = ReplayCapture.current(((PlayerLane) (Object) this).arenaWorld());
        if (capture != null) {
            capture.prepareTower(tower);
        }
    }
}
