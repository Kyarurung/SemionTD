package com.flechazo.nightlights.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

public class NightLightFrogBlock extends AbstractNightLightBlock {
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 9, 13);

    public NightLightFrogBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShapeForType() {
        return SHAPE;
    }
}