package me.drex.fafpatch.mixin.mod.registry;

import com.faboslav.friendsandfoes.common.FriendsAndFoes;
import com.faboslav.friendsandfoes.common.init.FriendsAndFoesItemGroups;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teamresourceful.resourcefullib.common.registry.RegistryEntry;
import com.teamresourceful.resourcefullib.common.registry.ResourcefulRegistry;
import eu.pb4.polymer.core.api.item.PolymerCreativeModeTabUtils;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Supplier;

@Mixin(FriendsAndFoesItemGroups.class)
public abstract class FriendsAndFoesItemGroupsMixin {
    @WrapOperation(
        method = "<clinit>",
        at = @At(
            value = "INVOKE",
            remap = false,
            target = "Lcom/teamresourceful/resourcefullib/common/registry/ResourcefulRegistry;register(Ljava/lang/String;Ljava/util/function/Supplier;)Lcom/teamresourceful/resourcefullib/common/registry/RegistryEntry;"
        )
    )
    private static RegistryEntry<CreativeModeTab> polymerify(ResourcefulRegistry<CreativeModeTab> instance, String s, Supplier<CreativeModeTab> iSupplier, Operation<RegistryEntry<CreativeModeTab>> original) {
        PolymerCreativeModeTabUtils.registerPolymerCreativeModeTab(FriendsAndFoes.makeID(s), iSupplier.get());
        return null;
    }
}
