package kim.biryeong.semiontd.mixin;

import kim.biryeong.semiontd.entity.simulation.MobSimulationAccess;
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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

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
}
