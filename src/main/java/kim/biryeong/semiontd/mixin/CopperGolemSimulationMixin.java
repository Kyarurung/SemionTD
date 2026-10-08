package kim.biryeong.semiontd.mixin;

import kim.biryeong.semiontd.entity.simulation.CopperGolemSimulationAccess;
import kim.biryeong.semiontd.game.simulation.CombatSimulationRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CopperGolem.class)
abstract class CopperGolemSimulationMixin implements CopperGolemSimulationAccess {
    @Shadow private void updateWeathering(ServerLevel world, RandomSource random, long time) { throw new AssertionError(); }

    @Inject(method = "updateWeathering", at = @At("HEAD"), cancellable = true)
    private void semiontd$logicalWeathering(ServerLevel world, RandomSource random, long time, CallbackInfo callback) {
        CopperGolem actor = (CopperGolem) (Object) this;
        if (CombatSimulationRuntime.controls(actor) && !CombatSimulationRuntime.stepping(actor)) {
            callback.cancel();
        }
    }

    @Override
    @Unique
    public void semiontd$finishCopperGolemTick() {
        CopperGolem actor = (CopperGolem) (Object) this;
        ServerLevel world = (ServerLevel) actor.level();
        updateWeathering(world, world.getRandom(), CombatSimulationRuntime.gameTime(world));
    }
}
