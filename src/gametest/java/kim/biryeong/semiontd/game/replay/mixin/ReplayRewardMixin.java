package kim.biryeong.semiontd.game.replay.mixin;

import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.game.EconomyService;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.replay.ReplayCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EconomyService.class)
public abstract class ReplayRewardMixin {
    @Inject(method = "awardMonsterKillReward", at = @At("HEAD"))
    private void beforeReward(Monster monster, Map<UUID, SemionPlayer> players, CallbackInfo callback) {
        ReplayCapture capture = ReplayCapture.current(monster);
        if (capture != null) {
            capture.beforeReward(monster, players);
        }
    }

    @Inject(method = "awardMonsterKillReward", at = @At("RETURN"))
    private void afterReward(Monster monster, Map<UUID, SemionPlayer> players, CallbackInfo callback) {
        ReplayCapture capture = ReplayCapture.current(monster);
        if (capture != null) {
            capture.afterReward(monster, players);
        }
    }
}
