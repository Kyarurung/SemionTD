package com.flechazo.nightlights.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public abstract class AbstractNightLightBlock extends AbstractLightBlock {
    public static final EnumProperty<Direction> FACING = EnumProperty.create("facing", Direction.class, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST);
    private static final int[] BRIGHTNESS_LEVELS = {0, 8, 13};

    public AbstractNightLightBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(LIT, true)
                .setValue(FACING, Direction.NORTH)
                .setValue(BRIGHTNESS, getDefaultBrightness()));
    }

    @Override
    protected boolean canSurvive(@NonNull BlockState state, @NonNull LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return canSupportRigidBlock(level, below) || canSupportCenter(level, below, Direction.UP);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(BRIGHTNESS, getDefaultBrightness())
                .setValue(LIT, true);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, FACING, BRIGHTNESS);
    }

    @Override
    @NotNull
    protected VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level, @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return getShapeForType();
    }

    @Override
    protected int getDefaultBrightness() {
        return 8;
    }

    @Override
    protected boolean isAdjustable() {
        return true;
    }

    @Override
    protected int[] getBrightnessLevels() {
        return BRIGHTNESS_LEVELS;
    }

    protected abstract VoxelShape getShapeForType();
}