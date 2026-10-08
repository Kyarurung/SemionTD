package kim.biryeong.semiontd.mixin;

import kim.biryeong.semiontd.entity.simulation.MobSimulationAccess;
import kim.biryeong.semiontd.game.simulation.CombatSimulationRuntime;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobSimulationMixin extends LivingEntity implements MobSimulationAccess {
    protected MobSimulationMixin(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    @Shadow private void burnUndead() { throw new AssertionError(); }
    @Shadow public abstract boolean canPickUpLoot();
    @Shadow protected abstract Vec3i getPickupReach();
    @Shadow public abstract boolean wantsToPickUp(ServerLevel level, ItemStack stack);
    @Shadow protected abstract void pickUpItem(ServerLevel level, ItemEntity item);
    @Shadow protected abstract void updateControlFlags();

    @Redirect(method = "*", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/Mob;tickCount:I", opcode = Opcodes.GETFIELD))
    private int semiontd$logicalMobAge(Mob actor) {
        return CombatSimulationRuntime.stepping(actor) ? CombatSimulationRuntime.entityTick(actor) : actor.tickCount;
    }

    @Inject(method = "baseTick", at = @At("HEAD"), cancellable = true)
    private void semiontd$logicalBasePhase(CallbackInfo callback) {
        Mob actor = (Mob) (Object) this;
        if (CombatSimulationRuntime.controls(actor) && !CombatSimulationRuntime.stepping(actor)) {
            callback.cancel();
        }
    }

    @Inject(method = "updateControlFlags", at = @At("HEAD"), cancellable = true)
    private void semiontd$logicalControlFlags(CallbackInfo callback) {
        Mob actor = (Mob) (Object) this;
        if (CombatSimulationRuntime.controls(actor) && !CombatSimulationRuntime.stepping(actor)) {
            callback.cancel();
        }
    }

    @Override
    @Unique
    public void semiontd$prepareLivingAi() {
        super.aiStep();
    }

    @Override
    @Unique
    public void semiontd$finishMobAi() {
        if (is(EntityTypeTags.BURN_IN_DAYLIGHT)) {
            burnUndead();
        }
        if (level() instanceof ServerLevel serverLevel && canPickUpLoot() && isAlive() && !dead
                && serverLevel.getGameRules().get(GameRules.MOB_GRIEFING)) {
            Vec3i reach = getPickupReach();
            for (ItemEntity item : level().getEntitiesOfClass(ItemEntity.class,
                    getBoundingBox().inflate(reach.getX(), reach.getY(), reach.getZ()))) {
                if (!item.isRemoved() && !item.getItem().isEmpty() && !item.hasPickUpDelay()
                        && wantsToPickUp(serverLevel, item.getItem())) {
                    pickUpItem(serverLevel, item);
                }
            }
        }
    }

    @Override
    @Unique
    public void semiontd$finishMobTick() {
        if (tickCount % 5 == 0) {
            updateControlFlags();
        }
    }
}
