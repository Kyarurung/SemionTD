package me.drex.fafpatch.impl.entity.holder;

import com.faboslav.friendsandfoes.common.entity.IllusionerEntity;
import com.mojang.math.Axis;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import me.drex.fafpatch.impl.entity.SimpleElementHolder;
import me.drex.fafpatch.impl.entity.model.EntityModelHelper;
import me.drex.fafpatch.impl.entity.model.entity.IllagerModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4fStack;

import java.util.HashMap;
import java.util.Map;


public class IllusionerElementHolder extends SimpleElementHolder<IllusionerEntity, IllagerModel<IllusionerEntity>> {

    private final Map<HumanoidArm, ItemDisplayElement> handItems = new HashMap<>();

    public IllusionerElementHolder(IllusionerEntity entity) {
        super(entity);
        setupHandItem(HumanoidArm.LEFT, ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
        setupHandItem(HumanoidArm.RIGHT, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
    }

    private void setupHandItem(HumanoidArm humanoidArm, ItemDisplayContext context) {

        var itemElement = EntityModelHelper.createItemDisplay(ItemStack.EMPTY);
        itemElement.setDisplaySize(this.entity.getBbWidth() * 2, this.entity.getBbHeight() * 2);
        itemElement.setItemDisplayContext(context);
        addElement(itemElement);
        handItems.put(humanoidArm, itemElement);
    }

    @Override
    protected void renderSpecialLayers(Matrix4fStack stack) {
        super.renderSpecialLayers(stack);
        renderHandItem(HumanoidArm.LEFT, stack);
        renderHandItem(HumanoidArm.RIGHT, stack);
    }

    // Based on ItemInHandLayer
    private void renderHandItem(HumanoidArm humanoidArm, Matrix4fStack stack) {
        ItemStack itemHeldByArm = entity.getItemHeldByArm(humanoidArm);
        ItemDisplayElement handItemDisplay = handItems.get(humanoidArm);
        if (!itemHeldByArm.isEmpty()) {
            stack.pushMatrix();
            IllagerModel<IllusionerEntity> entityModel = getMainModel().model();

            // this.getParentModel().translateToHand(humanoidArm, poseStack);
            entityModel.translateToHand(humanoidArm, stack);

            stack.rotate(Axis.XP.rotationDegrees(-90.0F));
            stack.rotate(Axis.YP.rotationDegrees(180.0F));
            boolean bl = humanoidArm == HumanoidArm.LEFT;
            stack.translate((bl ? -1 : 1) / 16.0F, 0.125F, -0.625F);

            handItemDisplay.setTransformation(stack);
            handItemDisplay.setItem(itemHeldByArm);
            stack.popMatrix();
        } else {
            handItemDisplay.setItem(ItemStack.EMPTY);
        }
    }
}
