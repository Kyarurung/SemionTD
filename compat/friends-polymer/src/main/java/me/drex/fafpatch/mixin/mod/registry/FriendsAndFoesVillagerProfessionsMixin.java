package me.drex.fafpatch.mixin.mod.registry;

import com.faboslav.friendsandfoes.common.init.FriendsAndFoesVillagerProfessions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teamresourceful.resourcefullib.common.registry.RegistryEntry;
import com.teamresourceful.resourcefullib.common.registry.ResourcefulRegistry;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Supplier;

@Mixin(FriendsAndFoesVillagerProfessions.class)
public abstract class FriendsAndFoesVillagerProfessionsMixin {
    @WrapOperation(
        method = "<clinit>",
        at = @At(
            value = "INVOKE",
            remap = false,
            target = "Lcom/teamresourceful/resourcefullib/common/registry/ResourcefulRegistry;register(Ljava/lang/String;Ljava/util/function/Supplier;)Lcom/teamresourceful/resourcefullib/common/registry/RegistryEntry;"
        )
    )
    private static RegistryEntry<VillagerProfession> polymerify(
        ResourcefulRegistry<VillagerProfession> instance, String s, Supplier<VillagerProfession> iSupplier,
        Operation<RegistryEntry<VillagerProfession>> original
    ) {
        RegistryEntry<VillagerProfession> registryEntry = original.call(instance, s, iSupplier);
        PolymerSyncedObject.setSyncedObject(BuiltInRegistries.VILLAGER_PROFESSION, registryEntry.get(), (obj, ctx) -> BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NONE).value());
        return registryEntry;
    }
}
