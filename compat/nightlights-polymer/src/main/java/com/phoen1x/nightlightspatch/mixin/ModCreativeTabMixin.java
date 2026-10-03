package com.phoen1x.nightlightspatch.mixin;

import com.flechazo.nightlights.init.ModCreativeTab;
import eu.pb4.polymer.core.api.item.PolymerCreativeModeTabUtils;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ModCreativeTab.class)
public class ModCreativeTabMixin {
    @Redirect(method = "register(Lnet/minecraft/world/item/CreativeModeTab;)Lnet/minecraft/world/item/CreativeModeTab;", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Registry;register(Lnet/minecraft/core/Registry;Lnet/minecraft/resources/Identifier;Ljava/lang/Object;)Ljava/lang/Object;"), remap = false)
    private static Object polymerifyItemGroup(Registry<CreativeModeTab> registry, Identifier identifier, Object entry) {
        CreativeModeTab itemGroup = (CreativeModeTab) entry;
        PolymerCreativeModeTabUtils.registerPolymerCreativeModeTab(identifier, itemGroup);
        return Registry.register(registry, identifier, itemGroup);
    }
}