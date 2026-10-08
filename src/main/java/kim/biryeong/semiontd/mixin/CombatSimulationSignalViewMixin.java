package kim.biryeong.semiontd.mixin;

import kim.biryeong.semiontd.game.simulation.CombatSimulationRuntime;
import kim.biryeong.semiontd.tower.engineer.EngineerCircuitWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.SignalGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SignalGetter.class)
interface CombatSimulationSignalViewMixin {
    @Inject(method = "hasNeighborSignal", at = @At("HEAD"), cancellable = true)
    private void semiontd$logicalNeighborSignal(BlockPos position, CallbackInfoReturnable<Boolean> callback) {
        if ((Object) this instanceof ServerLevel world && CombatSimulationRuntime.usesView(world)) {
            EngineerCircuitWorld circuit = EngineerCircuitWorld.current(world);
            Boolean powered = circuit == null ? null : circuit.neighborSignal(position);
            if (powered != null) {
                callback.setReturnValue(powered);
            }
        }
    }
}
