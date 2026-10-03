package me.drex.fafpatch.mixin.mod.registry;

import com.faboslav.friendsandfoes.common.block.CrabEggBlock;
import com.faboslav.friendsandfoes.common.init.FriendsAndFoesBlocks;
import com.teamresourceful.resourcefullib.common.registry.RegistryEntry;
import eu.pb4.factorytools.api.block.model.generic.BlockStateModelManager;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import me.drex.fafpatch.impl.FriendsAndFoesPatch;
import me.drex.fafpatch.impl.block.BaseFactoryBlock;
import me.drex.fafpatch.impl.block.StateCopyBlock;
import me.drex.fafpatch.impl.block.StateCopyFactoryBlock;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;
import java.util.function.Supplier;

@Mixin(FriendsAndFoesBlocks.class)
public abstract class FriendsAndFoesBlocksMixin {
    @Inject(
        method = "registerBlock",
        remap = false,
        at = @At("TAIL")
    )
    private static void polymerify(
        String id, Function<BlockBehaviour.Properties, Block> factory, Supplier<BlockBehaviour.Properties> getter,
        CallbackInfoReturnable<RegistryEntry<Block>> cir
    ) {
        RegistryEntry<Block> registryEntry = cir.getReturnValue();
        Block block = registryEntry.get();

        FriendsAndFoesPatch.LATE_INIT.add(() -> BlockStateModelManager.addBlock(registryEntry.getId(), block));

        PolymerBlock overlay = switch (block) {
            case BeehiveBlock ignored -> BaseFactoryBlock.BARRIER;
            case ButtonBlock ignored -> StateCopyFactoryBlock.BUTTON;
            case CrabEggBlock ignored -> StateCopyFactoryBlock.EGG;
            case FlowerBlock ignored -> BaseFactoryBlock.PLANT;
            case FlowerPotBlock ignored -> StateCopyFactoryBlock.POT;
            default -> {
                FriendsAndFoesPatch.LOGGER.warn("Missing overlay for block: {}", block.getClass().getName());
                yield BaseFactoryBlock.BARRIER;
            }
        };

        PolymerBlock.registerOverlay(block, overlay);
        if (overlay instanceof BlockWithElementHolder blockWithElementHolder) {
            BlockWithElementHolder.registerOverlay(block, blockWithElementHolder);
        }
    }

    @ModifyArg(
        method = "lambda$registerBlock$0",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/function/Function;apply(Ljava/lang/Object;)Ljava/lang/Object;"
        ),
        index = 0
    )
    private static Object noOcclussion(Object obj) {
        return ((BlockBehaviour.Properties) obj).noOcclusion();
    }
}
