package me.drex.fafpatch.mixin.mod.registry;

import com.faboslav.friendsandfoes.common.init.FriendsAndFoesItems;
import com.teamresourceful.resourcefullib.common.registry.RegistryEntry;
import eu.pb4.polymer.core.api.item.PolymerItem;
import me.drex.fafpatch.impl.item.PolyBaseItem;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;
import java.util.function.Supplier;

@Mixin(FriendsAndFoesItems.class)
public abstract class FriendsAndFoesItemsMixin {
    @Inject(
        method = "registerItem",
        remap = false,
        at = @At("TAIL")
    )
    private static void polymerify(
        String id, Function<Item.Properties, Item> factory, Supplier<Item.Properties> getter,
        CallbackInfoReturnable<RegistryEntry<Item>> cir
    ) {
        RegistryEntry<Item> registryEntry = cir.getReturnValue();
        var polymerItem = new PolyBaseItem(registryEntry.get());
        PolymerItem.registerOverlay(registryEntry.get(), polymerItem);
    }

    @Inject(
        method = "registerSpawnEgg",
        remap = false,
        at = @At("TAIL")
    )
    private static void polymerify2(
        String id, Supplier<? extends EntityType<? extends Mob>> typeIn, int primaryColorIn, int secondaryColorIn,
        CallbackInfoReturnable<RegistryEntry<Item>> cir
    ) {
        RegistryEntry<Item> registryEntry = cir.getReturnValue();
        var polymerItem = new PolyBaseItem(registryEntry.get());
        PolymerItem.registerOverlay(registryEntry.get(), polymerItem);
    }
}
