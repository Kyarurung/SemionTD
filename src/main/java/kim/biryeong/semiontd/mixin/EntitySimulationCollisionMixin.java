package kim.biryeong.semiontd.mixin;

import kim.biryeong.semiontd.entity.simulation.EntitySimulationBridge;
import kim.biryeong.semiontd.game.simulation.CombatSimulationRuntime;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntitySimulationCollisionMixin {
    @Inject(method = "updateFluidInteraction", at = @At("HEAD"), cancellable = true)
    private void semiontd$logicalFluidPhase(CallbackInfoReturnable<Boolean> callback) {
        Entity actor = (Entity) (Object) this;
        if (CombatSimulationRuntime.controls(actor) && !CombatSimulationRuntime.stepping(actor)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "collide", at = @At("HEAD"), cancellable = true)
    private void semiontd$workerCollision(Vec3 movement, CallbackInfoReturnable<Vec3> callback) {
        Vec3 resolved = EntitySimulationBridge.collision((Entity) (Object) this, movement);
        if (resolved != null) {
            callback.setReturnValue(resolved);
        }
    }
}
