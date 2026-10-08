package kim.biryeong.semiontd.mixin;

import kim.biryeong.semiontd.entity.simulation.EntitySimulationBridge;
import kim.biryeong.semiontd.game.simulation.CombatSimulationRuntime;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntitySimulationCollisionMixin {
    @Shadow private int invulnerableTime;

    @Redirect(method = "commonTick", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/Entity;invulnerableTime:I", opcode = Opcodes.GETFIELD, ordinal = 0))
    private int semiontd$logicalInvulnerability(Entity actor) {
        return CombatSimulationRuntime.controls(actor) ? 0 : invulnerableTime;
    }

    @Inject(method = "collide", at = @At("HEAD"), cancellable = true)
    private void semiontd$workerCollision(Vec3 movement, CallbackInfoReturnable<Vec3> callback) {
        Vec3 resolved = EntitySimulationBridge.collision((Entity) (Object) this, movement);
        if (resolved != null) {
            callback.setReturnValue(resolved);
        }
    }
}
