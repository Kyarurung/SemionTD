package me.drex.fafpatch.impl.entity.holder;

import com.faboslav.friendsandfoes.common.entity.TuffGolemEntity;
import com.mojang.math.Axis;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import me.drex.fafpatch.impl.FriendsAndFoesPatch;
import me.drex.fafpatch.impl.entity.SimpleElementHolder;
import me.drex.fafpatch.impl.entity.model.EntityModelHelper;
import me.drex.fafpatch.impl.entity.model.EntityModels;
import me.drex.fafpatch.impl.entity.model.entity.TuffGolemEntityModel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4fStack;

public class TuffGolemElementHolder extends SimpleElementHolder<TuffGolemEntity, TuffGolemEntityModel> {

    private final ItemDisplayElement itemElement;
    public static final Identifier CLOTH_LAYER = FriendsAndFoesPatch.id("cloth_layer");
    public static final Identifier CLOSED_EYES_LAYER = FriendsAndFoesPatch.id("closed_eyes_layer");

    public TuffGolemElementHolder(TuffGolemEntity entity) {
        super(entity);
        itemElement = EntityModelHelper.createItemDisplay(ItemStack.EMPTY);
        addElement(itemElement);
        addConditionalLayer(TuffGolemEntity::getColor, CLOTH_LAYER, EntityModels.TUFF_GOLEM_CLOTH::get);
        addConditionalLayer(TuffGolemEntity::isInSleepingPose, CLOSED_EYES_LAYER, isSleeping -> {
                if (isSleeping) {
                    return EntityModels.TUFF_GOLEM_CLOSED_EYES;
                } else {
                    return null;
                }
            }
        );
    }

    @Override
    protected void renderSpecialLayers(Matrix4fStack stack) {
        super.renderSpecialLayers(stack);
        renderItem(stack);
    }

    // Copied from TuffGolemHeldItemFeatureRenderer.render
    private void renderItem(Matrix4fStack stack) {
        var animationProgress = entity.tickCount;

        if (
            entity.isDeadOrDying()
                || !entity.isHoldingItem()
        ) {
            itemElement.setItem(ItemStack.EMPTY);
            return;
        }
        ItemStack itemStack = entity.getItemBySlot(EquipmentSlot.MAINHAND);

        float yItemOffset = 0.4F;
        float levitationOffset = Mth.sin(((float) entity.tickCount) / 10.0F + 3.1415927F) * 0.05F + 0.05F;
        float yOffset = levitationOffset + (1.0F - yItemOffset * 0.7F);
        float rotationAngle = (float) Math.toDegrees((animationProgress * 0.05F) % (2.0F * (float) Math.PI));
        stack.pushMatrix();
        stack.translate(0.0f, yOffset, -0.575f);
        stack.rotate(Axis.XP.rotationDegrees(180.0F));
        stack.rotate(Axis.YP.rotationDegrees(rotationAngle));

        itemElement.setItem(itemStack);
        itemElement.setItemDisplayContext(ItemDisplayContext.GROUND);
        itemElement.setTransformation(stack);
        EntityModelHelper.updateDisplayElement(itemElement, this.entity);
        stack.popMatrix();
    }

}
