package kim.biryeong.semiontd.mixin;

import kim.biryeong.semiontd.entity.simulation.EntitySimulationBridge;
import kim.biryeong.semiontd.entity.simulation.LivingEntitySimulationAccess;
import kim.biryeong.semiontd.game.simulation.CombatSimulationRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntitySimulationMixin extends Entity implements LivingEntitySimulationAccess {
    protected LivingEntitySimulationMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Shadow protected int autoSpinAttackTicks;
    @Shadow protected abstract void removeFrost();
    @Shadow protected abstract void tryAddFrost();
    @Shadow protected abstract void checkAutoSpinAttack(AABB before, AABB after);
    @Shadow protected abstract void pushEntities();
    @Shadow public abstract boolean isSensitiveToWater();
    @Shadow private void updatingUsingItem() { throw new AssertionError(); }
    @Shadow private void detectEquipmentUpdates() { throw new AssertionError(); }
    @Shadow private boolean checkBedExists() { throw new AssertionError(); }
    @Shadow private void refreshDirtyAttributes() { throw new AssertionError(); }
    @Shadow protected int fallFlyTicks;
    @Shadow private int currentImpulseContextResetGraceTime;

    @Redirect(method = "*", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/LivingEntity;tickCount:I", opcode = Opcodes.GETFIELD))
    private int semiontd$logicalLivingAge(LivingEntity actor) {
        return CombatSimulationRuntime.stepping(actor) ? CombatSimulationRuntime.entityTick(actor) : actor.tickCount;
    }

    @Inject(method = {"updatingUsingItem", "detectEquipmentUpdates", "refreshDirtyAttributes"}, at = @At("HEAD"), cancellable = true)
    private void semiontd$logicalItemPhase(CallbackInfo callback) {
        Entity actor = (Entity) (Object) this;
        if (CombatSimulationRuntime.controls(actor) && !CombatSimulationRuntime.stepping(actor)) {
            callback.cancel();
        }
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/damagesource/CombatTracker;recheckStatus()V"))
    private void semiontd$logicalCombatTracker(CombatTracker tracker) {
        if (!CombatSimulationRuntime.controls((Entity) (Object) this)) {
            tracker.recheckStatus();
        }
    }

    @Redirect(method = "tick", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/LivingEntity;fallFlyTicks:I", opcode = Opcodes.PUTFIELD))
    private void semiontd$logicalGlideAge(LivingEntity actor, int age) {
        if (!CombatSimulationRuntime.controls(actor)) {
            fallFlyTicks = age;
        }
    }

    @Redirect(method = "tick", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/LivingEntity;currentImpulseContextResetGraceTime:I", opcode = Opcodes.PUTFIELD))
    private void semiontd$logicalImpulseAge(LivingEntity actor, int age) {
        if (!CombatSimulationRuntime.controls(actor)) {
            currentImpulseContextResetGraceTime = age;
        }
    }

    @Inject(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getBoundingBox()Lnet/minecraft/world/phys/AABB;",
            ordinal = 0), cancellable = true)
    private void semiontd$captureTravel(CallbackInfo callback) {
        if (EntitySimulationBridge.capture((LivingEntity) (Object) this)) {
            callback.cancel();
        }
    }

    @Override
    @Unique
    public void semiontd$prepareLivingTick() {
        LivingEntity actor = (LivingEntity) (Object) this;
        updatingUsingItem();
        detectEquipmentUpdates();
        if (tickCount % 20 == 0) {
            actor.getCombatTracker().recheckStatus();
        }
        if (actor.isSleeping() && (!canInteractWithLevel() || !checkBedExists())) {
            actor.stopSleeping();
        }
    }

    @Override
    @Unique
    public void semiontd$finishLivingTick() {
        LivingEntity actor = (LivingEntity) (Object) this;
        fallFlyTicks = actor.isFallFlying() ? fallFlyTicks + 1 : 0;
        if (actor.isSleeping()) {
            setXRot(0.0F);
        }
        refreshDirtyAttributes();
        if (currentImpulseContextResetGraceTime > 0) {
            currentImpulseContextResetGraceTime--;
        }
    }

    @Override
    @Unique
    public void semiontd$finishLivingAi(AABB beforeTravelBox) {
        LivingEntity actor = (LivingEntity) (Object) this;
        applyEffectsFromBlocks();
        if (level() instanceof ServerLevel serverLevel) {
            if (!isInPowderSnow || !canFreeze()) {
                setTicksFrozen(Math.max(0, getTicksFrozen() - 2));
            }
            removeFrost();
            tryAddFrost();
            if (tickCount % 40 == 0 && isFullyFrozen() && canFreeze()) {
                actor.hurtServer(serverLevel, damageSources().freeze(), 1.0F);
            }
        }
        if (autoSpinAttackTicks > 0) {
            autoSpinAttackTicks--;
            checkAutoSpinAttack(beforeTravelBox, getBoundingBox());
        }
        pushEntities();
        if (level() instanceof ServerLevel serverLevel && isSensitiveToWater() && isInWaterOrRain()) {
            actor.hurtServer(serverLevel, damageSources().drown(), 1.0F);
        }
    }
}
