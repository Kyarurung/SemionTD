package me.drex.fafpatch.mixin.mod.registry;

import net.fabricmc.fabric.impl.object.builder.FabricEntityDataRegistryImpl;
import net.minecraft.core.Registry;
import net.minecraft.network.syncher.EntityDataSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FabricEntityDataRegistryImpl.class)
public interface FabricEntityDataRegistryImplAccessor {
    @Accessor
    static Registry<EntityDataSerializer<?>> getHandlerRegistry() {
        throw new AssertionError();
    };
}
