package me.drex.fafpatch.impl.entity.holder;

import com.faboslav.friendsandfoes.common.entity.MoobloomEntity;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import me.drex.fafpatch.impl.entity.SimpleElementHolder;
import me.drex.fafpatch.impl.entity.model.EntityModelHelper;
import me.drex.fafpatch.impl.entity.model.EntityModels;
import me.drex.fafpatch.impl.entity.model.entity.CowModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.joml.Matrix4fStack;

public class MoobloomElementHolder extends SimpleElementHolder<MoobloomEntity, CowModel<MoobloomEntity>> {

    private final BlockDisplayElement[] flowerModels = new BlockDisplayElement[4];

    public MoobloomElementHolder(MoobloomEntity entity) {
        super(entity);
        for (int i = 0; i < 4; i++) {
            BlockDisplayElement element = EntityModelHelper.createBlockDisplay();
            addElement(element);
            flowerModels[i] = element;
        }
        addConditionalLayer(RenderState::new, MAIN_LAYER, EntityModels.MOOBLOOM::get);
    }

    @Override
    protected void renderSpecialLayers(Matrix4fStack stack) {
        super.renderSpecialLayers(stack);
        renderFlowers(stack);
    }

    private void renderFlowers(Matrix4fStack stack) {
        if (!entity.isBaby() && !entity.isInvisible()) {
            var flower = entity.getVariant().getFlower();
            BlockState blockState = entity.getVariant().getFlower().defaultBlockState();

            if (flower instanceof DoublePlantBlock) {
                blockState = entity.getVariant().getFlower().defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
            }

            float scaleFactor = 0.8F;
            float yOffset = -0.5F;

            if (flower instanceof DoublePlantBlock) {
                scaleFactor = 0.6F;
                yOffset = -0.666F;
            }

            // Head
            stack.pushMatrix();
            getMainModel().model().getHead().translateAndRotate(stack);
            stack.translate(0.09f, -0.6f, -0.185f);
            stack.scale(-scaleFactor, -scaleFactor, scaleFactor);
            stack.translate(-0.5f, yOffset, -0.5f);
            this.renderFlower(stack, blockState, 0);
            stack.popMatrix();

            // Body
            stack.pushMatrix();
            stack.translate(0.22f, -0.28f, -0.06f);
            stack.scale(-scaleFactor, -scaleFactor, scaleFactor);
            stack.translate(-0.5f, yOffset, -0.5f);
            this.renderFlower(stack, blockState, 1);
            stack.popMatrix();

            stack.pushMatrix();
            stack.translate(-0.2f, -0.22f, 0.01f);
            stack.scale(-scaleFactor, -scaleFactor, scaleFactor);
            stack.translate(-0.5f, yOffset, -0.5f);
            this.renderFlower(stack, blockState, 2);
            stack.popMatrix();

            stack.pushMatrix();
            stack.translate(0.03f, -0.28f, 0.47f);
            stack.scale(-scaleFactor, -scaleFactor, scaleFactor);
            stack.translate(-0.5f, yOffset, -0.5f);
            this.renderFlower(stack, blockState, 3);
            stack.popMatrix();
        }
    }

    private void renderFlower(Matrix4fStack stack, BlockState flowerState, int index) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(flowerState.getBlock());
        if (!id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
            // rendering modded blocks is annoying
            flowerState = Blocks.DANDELION.defaultBlockState();
        }

        flowerModels[index].setBlockState(flowerState);
        flowerModels[index].setTransformation(stack);
        EntityModelHelper.updateDisplayElement(flowerModels[index], this.entity);
    }

    public record RenderState(String variant, boolean baby) {
        public RenderState(MoobloomEntity moobloomEntity) {
            this(moobloomEntity.getVariant().getName(), moobloomEntity.isBaby());
        }
    }
}