package com.phoen1x.nightlightspatch.mixin;

import com.flechazo.nightlights.block.CeilingLightBlock;
import com.flechazo.nightlights.block.NightLightFrogBlock;
import com.flechazo.nightlights.block.NightLightMushroomBlock;
import com.flechazo.nightlights.block.NightLightOctopusBlock;
import com.flechazo.nightlights.init.ModBlock;
import com.flechazo.nightlights.util.RegisterHelper;
import com.phoen1x.nightlightspatch.impl.block.BaseFactoryBlock;
import com.phoen1x.nightlightspatch.impl.block.DirectionalTrapdoorFactoryBlock;
import eu.pb4.factorytools.api.block.model.generic.BlockStateModelManager;
import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ModBlock.class, remap = false)
public class ModBlockMixin {
    @Inject(method = "registerNightLight", at = @At("RETURN"), remap = false)
    private static void polymerifyNightLight(String name, CallbackInfoReturnable<Block> cir) {
        Block registeredBlock = cir.getReturnValue();
        Identifier location = RegisterHelper.id(name);
        polymerify(location, registeredBlock);
    }

    @Inject(method = "registerCeilingLight", at = @At("RETURN"), remap = false)
    private static void polymerifyCeilingLight(String name, CallbackInfoReturnable<Block> cir) {
        Block registeredBlock = cir.getReturnValue();
        Identifier location = RegisterHelper.id(name);
        polymerify(location, registeredBlock);
    }

    @Unique
    private static void polymerify(Identifier location, Block block) {
        BlockStateModelManager.addBlock(location, block);
        PolymerBlock overlay = null;

        if (block instanceof NightLightFrogBlock || block instanceof NightLightMushroomBlock || block instanceof NightLightOctopusBlock) {
            overlay = BaseFactoryBlock.SAPLING;
        } else if (block instanceof CeilingLightBlock) {
            overlay = new DirectionalTrapdoorFactoryBlock();
        }

        if (overlay == null) {
            if (block.defaultBlockState().getCollisionShape(PolymerCommonUtils.getFakeWorld(), BlockPos.ZERO).isEmpty()) {
                overlay = BaseFactoryBlock.SAPLING;
            } else {
                overlay = BaseFactoryBlock.BARRIER;
            }
        }

        PolymerBlock.registerOverlay(block, overlay);
        if (overlay instanceof BlockWithElementHolder blockWithElementHolder) {
            BlockWithElementHolder.registerOverlay(block, blockWithElementHolder);
        }
    }
}