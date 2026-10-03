package com.phoen1x.nightlightspatch.impl.block;

import com.flechazo.nightlights.block.CeilingLightBlock;
import eu.pb4.factorytools.api.block.FactoryBlock;
import eu.pb4.factorytools.api.block.model.generic.BlockStateModel;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;

public class DirectionalTrapdoorFactoryBlock implements FactoryBlock, PolymerTexturedBlock {

    @Override
    public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
        Direction facing = state.getValue(CeilingLightBlock.FACING);

        return switch (facing) {
            case SOUTH -> PolymerBlockResourceUtils.requestEmpty(BlockModelType.TRAPDOOR_SOUTH);
            case EAST  -> PolymerBlockResourceUtils.requestEmpty(BlockModelType.TRAPDOOR_EAST);
            case WEST  -> PolymerBlockResourceUtils.requestEmpty(BlockModelType.TRAPDOOR_WEST);

            default -> PolymerBlockResourceUtils.requestEmpty(BlockModelType.TRAPDOOR_NORTH);
        };
    }

    @Override
    public @Nullable ElementHolder createElementHolder(ServerLevel world, BlockPos pos, BlockState state) {
        return BlockStateModel.longRange(state, pos);
    }

    @Override
    public boolean tickElementHolder(ServerLevel world, BlockPos pos, BlockState state) {
        return false;
    }
}
