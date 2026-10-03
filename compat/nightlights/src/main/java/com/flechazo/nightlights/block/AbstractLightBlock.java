package com.flechazo.nightlights.block;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;

public abstract class AbstractLightBlock extends Block {
    public static final IntegerProperty BRIGHTNESS = IntegerProperty.create("brightness", 0, 15);
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    protected AbstractLightBlock(Properties properties) {
        super(properties.lightLevel(state ->
                state.getValue(LIT) ? state.getValue(BRIGHTNESS) : 0
        ));
    }

    @Override
    @NonNull
    protected InteractionResult useWithoutItem(@NonNull BlockState state, Level level, @NonNull BlockPos pos, @NonNull Player player, @NonNull BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            if (isAdjustable()) {
                handleLightInteraction(state, level, pos);
            } else {
                handleFixedLightInteraction(state, level, pos);
            }
        }
        return InteractionResult.SUCCESS;
    }

    protected void handleLightInteraction(BlockState state, Level level, BlockPos pos) {
        boolean isLit = state.getValue(LIT);
        int currentBrightness = state.getValue(BRIGHTNESS);
        int[] levels = getBrightnessLevels();

        if (!isLit || currentBrightness == 0) {
            BlockState newState = state.setValue(LIT, true).setValue(BRIGHTNESS, levels[1]);
            level.setBlock(pos, newState, 3);
            playLightSound(level, pos, true, levels[1]);
        } else {
            int currentIndex = -1;
            for (int i = 0; i < levels.length; i++) {
                if (levels[i] == currentBrightness) {
                    currentIndex = i;
                    break;
                }
            }

            int nextIndex = (currentIndex + 1) % levels.length;
            int nextBrightness = levels[nextIndex];

            if (nextBrightness == 0) {
                BlockState newState = state.setValue(LIT, false).setValue(BRIGHTNESS, getDefaultBrightness());
                level.setBlock(pos, newState, 3);
                playLightSound(level, pos, false, 0);
            } else {
                BlockState newState = state.setValue(BRIGHTNESS, nextBrightness);
                level.setBlock(pos, newState, 3);
                playLightSound(level, pos, true, nextBrightness);
            }
        }
    }

    protected void handleFixedLightInteraction(BlockState state, Level level, BlockPos pos) {
        boolean isLit = state.getValue(LIT);

        if (isLit) {
            BlockState newState = state.setValue(LIT, false);
            level.setBlock(pos, newState, 3);
            playLightSound(level, pos, false, 0);
        } else {
            BlockState newState = state.setValue(LIT, true);
            level.setBlock(pos, newState, 3);
            playLightSound(level, pos, true, getDefaultBrightness());
        }
    }

    protected void playLightSound(Level level, BlockPos pos, boolean turnOn, int brightness) {
        if (turnOn) {
            float pitch = 0.5F + (brightness * 0.03F);
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.6F, pitch);
        } else {
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.6F, 0.5F);
        }
    }

    protected abstract int getDefaultBrightness();

    protected boolean isAdjustable() {
        return false;
    }

    protected int[] getBrightnessLevels() {
        return new int[]{0, getDefaultBrightness()};
    }
}