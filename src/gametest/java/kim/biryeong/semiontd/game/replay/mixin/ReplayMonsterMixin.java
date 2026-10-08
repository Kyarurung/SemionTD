package kim.biryeong.semiontd.game.replay.mixin;

import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.replay.ReplayCapture;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SemionMonsterEntity.class)
public abstract class ReplayMonsterMixin {
    @Inject(method = "startAttack(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"))
    private void recordAttack(LivingEntity target, CallbackInfo callback) {
        SemionMonsterEntity actor = (SemionMonsterEntity) (Object) this;
        ReplayCapture capture = ReplayCapture.current(actor);
        if (capture != null) {
            capture.attack(actor, target, "MONSTER_ATTACK", actor.attackDamageAmount());
        }
    }

    @Inject(method = "applySemionDamageResult", at = @At("RETURN"))
    private void recordDamage(DamageSource source, double amount, DamageType type,
            CallbackInfoReturnable<SemionMonsterEntity.AppliedDamageResult> callback) {
        SemionMonsterEntity actor = (SemionMonsterEntity) (Object) this;
        ReplayCapture capture = ReplayCapture.current(actor);
        if (capture != null) {
            var result = callback.getReturnValue();
            capture.damage(source.getEntity(), actor, type.name(), amount, result.appliedDamage(), result.killed());
        }
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void recordDeath(DamageSource source, CallbackInfo callback) {
        SemionMonsterEntity actor = (SemionMonsterEntity) (Object) this;
        ReplayCapture capture = ReplayCapture.current(actor);
        if (capture != null) {
            capture.death(actor);
        }
    }
}
