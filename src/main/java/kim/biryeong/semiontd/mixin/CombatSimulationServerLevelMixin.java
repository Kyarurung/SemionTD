package kim.biryeong.semiontd.mixin;

import kim.biryeong.semiontd.game.simulation.CombatSimulationRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
abstract class CombatSimulationServerLevelMixin {
    @Inject(method = "addEntity", at = @At("RETURN"))
    private void semiontd$added(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValue()) {
            CombatSimulationRuntime.added(entity);
        }
    }
}
