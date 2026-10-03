package com.phoen1x.nightlightspatch.mixin;

import com.flechazo.nightlights.init.ModItem;
import com.phoen1x.nightlightspatch.impl.item.PolyBaseItem; // Припустимо, що у вас є цей клас-реалізація
import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.world.item.Item;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ModItem.class, remap = false)
public class ModItemMixin {
    @Inject(method = "registerWearableBlockItem", at = @At("RETURN"), remap = false)
    private static void polymerifyWearableBlockItem(String name, net.minecraft.world.level.block.Block block, CallbackInfoReturnable<Item> cir) {
        Item registeredItem = cir.getReturnValue();
        polymerify(registeredItem);
    }

    @Inject(method = "registerBlockItem", at = @At("RETURN"), remap = false)
    private static void polymerifyBlockItem(String name, net.minecraft.world.level.block.Block block, CallbackInfoReturnable<Item> cir) {
        Item registeredItem = cir.getReturnValue();
        polymerify(registeredItem);
    }

    private static void polymerify(Item registeredItem) {
        PolymerItem polymerItem = new PolyBaseItem(registeredItem);
        PolymerItem.registerOverlay(registeredItem, polymerItem);
    }
}