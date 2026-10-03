package me.drex.fafpatch.mixin.mod.registry;

import com.faboslav.friendsandfoes.common.FriendsAndFoes;
import com.faboslav.friendsandfoes.fabric.platform.EntitySerializers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntitySerializers.class)
public abstract class EntitySerializersMixin {
    @WrapOperation(
        method = "register",
        at = @At(
            value = "INVOKE",
            target = "Lnet/fabricmc/fabric/api/object/builder/v1/entity/FabricEntityDataRegistry;register(Lnet/minecraft/resources/Identifier;Lnet/minecraft/network/syncher/EntityDataSerializer;)V"
        )
    )
    public void dontRegister(Identifier id, EntityDataSerializer<?> handler, Operation<Void> original) {
        original.call(id, handler);
        if (id.getNamespace().equals(FriendsAndFoes.MOD_ID)) {
            RegistrySyncUtils.setServerEntry(FabricEntityDataRegistryImplAccessor.getHandlerRegistry(), handler);
        }
    }
}
