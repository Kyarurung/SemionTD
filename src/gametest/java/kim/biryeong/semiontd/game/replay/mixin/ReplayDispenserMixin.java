package kim.biryeong.semiontd.game.replay.mixin;

import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.replay.ReplayCapture;
import kim.biryeong.semiontd.tower.engineer.EngineerTrapTower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EngineerTrapTower.class)
public abstract class ReplayDispenserMixin {
    @Inject(method = "fireDispenser", at = @At("HEAD"))
    private void recordShot(PlayerLane lane, SemionTowerEntity source, SemionMonsterEntity target,
            double damage, boolean pierce, CallbackInfo callback) {
        ReplayCapture capture = ReplayCapture.current(source);
        if (capture != null) {
            capture.projectile(source, target, damage, pierce);
        }
    }
}
