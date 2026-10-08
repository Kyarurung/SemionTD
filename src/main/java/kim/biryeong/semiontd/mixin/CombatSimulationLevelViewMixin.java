package kim.biryeong.semiontd.mixin;

import java.util.List;
import java.util.function.Predicate;
import kim.biryeong.semiontd.game.simulation.CombatSimulationRuntime;
import kim.biryeong.semiontd.tower.engineer.EngineerCircuitWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
abstract class CombatSimulationLevelViewMixin {
    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true)
    private void semiontd$logicalBlockState(BlockPos position, CallbackInfoReturnable<BlockState> callback) {
        if ((Object) this instanceof ServerLevel world && CombatSimulationRuntime.usesView(world)) {
            EngineerCircuitWorld circuit = EngineerCircuitWorld.current(world);
            if (circuit != null) {
                callback.setReturnValue(circuit.getBlockState(position, callback.getReturnValue()));
            }
        }
    }

    @Inject(method = "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
            at = @At("HEAD"), cancellable = true)
    private void semiontd$logicalEntities(Entity excluded, AABB box, Predicate<? super Entity> predicate,
            CallbackInfoReturnable<List<Entity>> callback) {
        if ((Object) this instanceof ServerLevel world) {
            CombatSimulationRuntime.Owner owner = CombatSimulationRuntime.activeOwner(world);
            if (owner != null) {
                callback.setReturnValue(CombatSimulationRuntime.entities(world, excluded, box, predicate));
            }
        }
    }

    @Inject(method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;Ljava/util/List;I)V",
            at = @At("HEAD"), cancellable = true)
    private <T extends Entity> void semiontd$logicalTypedEntities(EntityTypeTest<Entity, T> type, AABB box,
            Predicate<? super T> predicate, List<? super T> result, int maximum, CallbackInfo callback) {
        if ((Object) this instanceof ServerLevel world) {
            CombatSimulationRuntime.Owner owner = CombatSimulationRuntime.activeOwner(world);
            if (owner != null) {
                CombatSimulationRuntime.entities(world, type, box, predicate, result, maximum);
                callback.cancel();
            }
        }
    }
}
