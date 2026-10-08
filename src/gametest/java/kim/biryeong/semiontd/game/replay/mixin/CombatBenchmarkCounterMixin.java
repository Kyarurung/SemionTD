package kim.biryeong.semiontd.game.replay.mixin;

import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.replay.ReplayCapture;
import kim.biryeong.semiontd.game.replay.benchmark.BenchmarkWorkload;
import kim.biryeong.semiontd.tower.engineer.EngineerCircuitTower;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReplayCapture.class)
public abstract class CombatBenchmarkCounterMixin {
    @Unique
    private void count(String kind, Object target, double amount, boolean accepted) {
        if (Boolean.getBoolean("semiontd.benchmark.enabled")) {
            BenchmarkWorkload.event((ReplayCapture) (Object) this, kind, target, amount, accepted);
        }
    }

    @Inject(method = "prepareSpawn", at = @At("RETURN"))
    private void identity(Entity entity, CallbackInfo callback) {
        if (Boolean.getBoolean("semiontd.benchmark.enabled")) {
            BenchmarkWorkload.identity((ReplayCapture) (Object) this, entity);
        }
    }

    @Inject(method = "attack", at = @At("HEAD"))
    private void attack(Entity source, Entity target, String kind, double amount, CallbackInfo callback) {
        count("attack", target, amount, true);
    }

    @Inject(method = "damage", at = @At("HEAD"))
    private void damage(Entity source, LivingEntity target, String kind, double requested, double dealt,
            boolean killed, CallbackInfo callback) {
        count("damage", target, dealt, true);
    }

    @Inject(method = "spawn", at = @At("RETURN"))
    private void spawn(Entity target, boolean accepted, CallbackInfo callback) {
        count("spawn", target, 0, accepted);
    }

    @Inject(method = "death", at = @At("HEAD"))
    private void death(Entity target, CallbackInfo callback) {
        count("death", target, 0, true);
    }

    @Inject(method = "afterReward", at = @At("RETURN"))
    private void reward(Monster monster, Map<UUID, SemionPlayer> players, CallbackInfo callback) {
        count("reward", monster, 0, true);
    }

    @Inject(method = "projectile", at = @At("HEAD"))
    private void projectile(Entity source, Entity target, double damage, boolean pierce, CallbackInfo callback) {
        count("shot", target, damage, true);
    }

    @Inject(method = "plate", at = @At("RETURN"))
    private void plate(EngineerCircuitTower plate, boolean accepted, CallbackInfo callback) {
        count("press", plate, 0, accepted);
    }
}
