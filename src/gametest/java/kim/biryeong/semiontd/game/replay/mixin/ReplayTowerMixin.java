package kim.biryeong.semiontd.game.replay.mixin;

import java.util.ArrayDeque;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.replay.ReplayCapture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SemionTowerEntity.class)
public abstract class ReplayTowerMixin {
    @Unique
    private final ArrayDeque<Double> replay$health = new ArrayDeque<>();

    @Inject(method = "recordAttack(Lkim/biryeong/semiontd/entity/monster/SemionMonsterEntity;DDDZ)V", at = @At("HEAD"))
    private void recordAttack(SemionMonsterEntity target, double attempted, double outgoing, double dealt,
            boolean killed, CallbackInfo callback) {
        SemionTowerEntity actor = (SemionTowerEntity) (Object) this;
        ReplayCapture capture = ReplayCapture.current(actor);
        if (capture != null) {
            capture.attack(actor, target, "TOWER_ATTACK", attempted);
        }
    }

    @Inject(method = "applyDamage", at = @At("HEAD"))
    private void beforeDamage(ServerLevel world, DamageSource source, double amount, CallbackInfo callback) {
        SemionTowerEntity actor = (SemionTowerEntity) (Object) this;
        if (ReplayCapture.current(actor) != null) {
            replay$health.push(actor.runtimeTower() == null ? (double) actor.getHealth() : actor.runtimeTower().health());
        }
    }

    @Inject(method = "applyDamage", at = @At("RETURN"))
    private void afterDamage(ServerLevel world, DamageSource source, double amount, CallbackInfo callback) {
        SemionTowerEntity actor = (SemionTowerEntity) (Object) this;
        ReplayCapture capture = ReplayCapture.current(actor);
        if (capture != null) {
            double health = actor.runtimeTower() == null ? actor.getHealth() : actor.runtimeTower().health();
            capture.damage(source.getEntity(), actor, "TOWER_DAMAGE", amount,
                    Math.max(0, replay$health.pop() - health), health <= 0);
        }
    }
}
