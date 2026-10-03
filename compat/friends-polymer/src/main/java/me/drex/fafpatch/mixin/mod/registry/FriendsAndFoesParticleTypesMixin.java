package me.drex.fafpatch.mixin.mod.registry;

import com.faboslav.friendsandfoes.common.FriendsAndFoes;
import com.faboslav.friendsandfoes.common.init.FriendsAndFoesParticleTypes;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teamresourceful.resourcefullib.common.registry.RegistryEntry;
import com.teamresourceful.resourcefullib.common.registry.ResourcefulRegistry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Supplier;

@Mixin(FriendsAndFoesParticleTypes.class)
public abstract class FriendsAndFoesParticleTypesMixin {
    @WrapOperation(
        method = "<clinit>",
        at = @At(
            value = "INVOKE",
            remap = false,
            target = "Lcom/teamresourceful/resourcefullib/common/registry/ResourcefulRegistry;register(Ljava/lang/String;Ljava/util/function/Supplier;)Lcom/teamresourceful/resourcefullib/common/registry/RegistryEntry;"
        )
    )
    private static RegistryEntry<ParticleType<?>> polymerify(ResourcefulRegistry<ParticleType<?>> instance, String s, Supplier<ParticleType<?>> iSupplier, Operation<RegistryEntry<ParticleType<?>>> original) {
        BuiltInRegistries.PARTICLE_TYPE.addAlias(FriendsAndFoes.makeID(s), BuiltInRegistries.PARTICLE_TYPE.getKey(ParticleTypes.TOTEM_OF_UNDYING));
        return new RegistryEntry<>() {
            @Override
            public ParticleType<?> get() {
                return ParticleTypes.TOTEM_OF_UNDYING;
            }

            @Override
            public Identifier getId() {
                return BuiltInRegistries.PARTICLE_TYPE.getKey(ParticleTypes.TOTEM_OF_UNDYING);
            }
        };
    }
}
