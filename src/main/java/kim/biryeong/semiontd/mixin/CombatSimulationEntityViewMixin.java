package kim.biryeong.semiontd.mixin;

import kim.biryeong.semiontd.game.simulation.CombatSimulationRuntime;
import kim.biryeong.semiontd.game.simulation.CombatSimulationRuntime.EntityView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
abstract class CombatSimulationEntityViewMixin {
    @Shadow private Vec3 position;
    @Shadow private AABB bb;
    @Shadow private Vec3 deltaMovement;
    @Shadow private boolean onGround;
    @Shadow private BlockPos blockPosition;
    @Shadow private ChunkPos chunkPosition;

    @Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Entity;blockPosition:Lnet/minecraft/core/BlockPos;", opcode = Opcodes.GETFIELD))
    private BlockPos semiontd$logicalBlockCache(Entity entity) {
        EntityView view = CombatSimulationRuntime.view(entity);
        return view == null ? ((CombatSimulationEntityViewMixin) (Object) entity).blockPosition
                : BlockPos.containing(view.position());
    }

    @Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Entity;chunkPosition:Lnet/minecraft/world/level/ChunkPos;", opcode = Opcodes.GETFIELD))
    private ChunkPos semiontd$logicalChunkCache(Entity entity) {
        EntityView view = CombatSimulationRuntime.view(entity);
        return view == null ? ((CombatSimulationEntityViewMixin) (Object) entity).chunkPosition
                : ChunkPos.containing(BlockPos.containing(view.position()));
    }

    @Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Entity;position:Lnet/minecraft/world/phys/Vec3;", opcode = Opcodes.GETFIELD))
    private Vec3 semiontd$logicalPosition(Entity entity) {
        EntityView view = CombatSimulationRuntime.view(entity);
        return view == null ? ((CombatSimulationEntityViewMixin) (Object) entity).position : view.position();
    }

    @Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Entity;bb:Lnet/minecraft/world/phys/AABB;", opcode = Opcodes.GETFIELD))
    private AABB semiontd$logicalBox(Entity entity) {
        EntityView view = CombatSimulationRuntime.view(entity);
        return view == null ? ((CombatSimulationEntityViewMixin) (Object) entity).bb : view.box();
    }

    @Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Entity;bb:Lnet/minecraft/world/phys/AABB;", opcode = Opcodes.PUTFIELD))
    private void semiontd$setLogicalBox(Entity entity, AABB value) {
        EntityView view = CombatSimulationRuntime.view(entity);
        if (view == null) {
            ((CombatSimulationEntityViewMixin) (Object) entity).bb = value;
        } else {
            view.box(value);
        }
    }

    @Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Entity;deltaMovement:Lnet/minecraft/world/phys/Vec3;", opcode = Opcodes.GETFIELD))
    private Vec3 semiontd$logicalVelocity(Entity entity) {
        EntityView view = CombatSimulationRuntime.view(entity);
        return view == null ? ((CombatSimulationEntityViewMixin) (Object) entity).deltaMovement : view.velocity();
    }

    @Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Entity;deltaMovement:Lnet/minecraft/world/phys/Vec3;", opcode = Opcodes.PUTFIELD))
    private void semiontd$setLogicalVelocity(Entity entity, Vec3 value) {
        EntityView view = CombatSimulationRuntime.view(entity);
        if (view == null) {
            ((CombatSimulationEntityViewMixin) (Object) entity).deltaMovement = value;
        } else {
            view.velocity(value);
        }
    }

    @Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Entity;onGround:Z", opcode = Opcodes.GETFIELD))
    private boolean semiontd$logicalGround(Entity entity) {
        EntityView view = CombatSimulationRuntime.view(entity);
        return view == null ? ((CombatSimulationEntityViewMixin) (Object) entity).onGround : view.onGround();
    }

    @Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Entity;onGround:Z", opcode = Opcodes.PUTFIELD))
    private void semiontd$setLogicalGround(Entity entity, boolean value) {
        EntityView view = CombatSimulationRuntime.view(entity);
        if (view == null) {
            ((CombatSimulationEntityViewMixin) (Object) entity).onGround = value;
        } else {
            view.onGround(value);
        }
    }

    @Inject(method = "setPosRaw", at = @At("HEAD"), cancellable = true)
    private void semiontd$setLogicalPosition(double x, double y, double z, CallbackInfo callback) {
        EntityView view = CombatSimulationRuntime.view((Entity) (Object) this);
        if (view != null) {
            view.position(new Vec3(x, y, z));
            callback.cancel();
        }
    }

    @Inject(method = "getInBlockState", at = @At("HEAD"), cancellable = true)
    private void semiontd$logicalInBlockState(CallbackInfoReturnable<BlockState> callback) {
        Entity entity = (Entity) (Object) this;
        EntityView view = CombatSimulationRuntime.view(entity);
        if (view != null) {
            callback.setReturnValue(entity.level().getBlockState(BlockPos.containing(view.position())));
        }
    }

    @Inject(method = "setPosRaw", at = @At("RETURN"))
    private void semiontd$nativePositionChanged(double x, double y, double z, CallbackInfo callback) {
        CombatSimulationRuntime.positionChanged((Entity) (Object) this);
    }

    @Inject(method = "remove", at = @At("RETURN"))
    private void semiontd$removed(Entity.RemovalReason reason, CallbackInfo callback) {
        CombatSimulationRuntime.removed((Entity) (Object) this);
    }

    @Inject(method = "blockPosition", at = @At("HEAD"), cancellable = true)
    private void semiontd$logicalBlockPosition(CallbackInfoReturnable<BlockPos> callback) {
        EntityView view = CombatSimulationRuntime.view((Entity) (Object) this);
        if (view != null) {
            callback.setReturnValue(BlockPos.containing(view.position()));
        }
    }

    @Inject(method = "chunkPosition", at = @At("HEAD"), cancellable = true)
    private void semiontd$logicalChunkPosition(CallbackInfoReturnable<ChunkPos> callback) {
        EntityView view = CombatSimulationRuntime.view((Entity) (Object) this);
        if (view != null) {
            callback.setReturnValue(ChunkPos.containing(BlockPos.containing(view.position())));
        }
    }
}
