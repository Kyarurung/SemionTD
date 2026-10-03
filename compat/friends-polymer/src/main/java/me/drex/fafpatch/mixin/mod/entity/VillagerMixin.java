package me.drex.fafpatch.mixin.mod.entity;

import com.llamalad7.mixinextras.sugar.Local;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.virtualentity.api.attachment.UniqueIdentifiableAttachment;
import me.drex.fafpatch.impl.entity.BasePolymerEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerMixin extends AbstractVillager {
    public VillagerMixin(EntityType<? extends AbstractVillager> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    public void setProfession(ValueInput valueInput, CallbackInfo ci) {
        updatePolymerEntity();
    }

    @Inject(method = "setVillagerData", at = @At("TAIL"))
    public void changeProfession(VillagerData villagerData, CallbackInfo ci, @Local(ordinal = 1) VillagerData oldVillagerData) {
        if (villagerData.profession().equals(oldVillagerData.profession())) return;
        updatePolymerEntity();
    }

    @Unique
    private void updatePolymerEntity() {
        UniqueIdentifiableAttachment attachment = UniqueIdentifiableAttachment.get(this, BasePolymerEntity.MODEL);
        if (attachment != null) {
            attachment.destroy();
        }
        PolymerEntityUtils.recreatePolymerEntity(this);
        PolymerEntityUtils.refreshEntity(this);
    }
}
