package me.drex.fafpatch.mixin.mod.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.virtualentity.api.attachment.UniqueIdentifiableAttachment;
import me.drex.fafpatch.impl.entity.BasePolymerEntity;
import me.drex.fafpatch.impl.entity.SimpleElementHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {
    public LivingEntityMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @ModifyExpressionValue(
        method = "aiStep",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;isClientSide()Z",
            ordinal = 2
        )
    )
    public boolean serverSideWalkAnimation(boolean original) {
        return original || PolymerEntity.get(this) instanceof BasePolymerEntity;
    }

    @ModifyArg(
        method = "take",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ClientboundTakeItemEntityPacket;<init>(III)V"
        ),
        index = 1
    )
    public int fixTakeItemEntityId(int original) {
        UniqueIdentifiableAttachment attachment = UniqueIdentifiableAttachment.get(this, BasePolymerEntity.MODEL);
        if (attachment == null) return original;
        var model = (SimpleElementHolder<?, ?>) attachment.holder();

        return model.leadAttachment.getEntityId();
    }
}
