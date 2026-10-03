package me.drex.fafpatch.impl.entity.model;

import eu.pb4.factorytools.api.virtualentity.ItemDisplayElementUtil;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class EntityModelHelper {
    public static final int TELEPORT_DURATION = 3;

    public static ItemDisplayElement createItemDisplay(ItemStack stack) {
        var itemDisplay = ItemDisplayElementUtil.createSimple(stack);
        itemDisplay.setInterpolationDuration(1);
        itemDisplay.setTeleportDuration(TELEPORT_DURATION);
        itemDisplay.setViewRange(2);
        return itemDisplay;
    }

    public static void updateDisplayElement(DisplayElement element, Entity entity) {
        EntityDimensions dimensions = entity.getDimensions(entity.getPose());
        element.setDisplaySize(dimensions.width() * 2, dimensions.height() * 2);
        // ensure the element doesn't clip into nearby blocks
        element.setOffset(new Vec3(0, dimensions.height() / 2, 0));
    }

    public static BlockDisplayElement createBlockDisplay() {
        var blockDisplay = new BlockDisplayElement();
        blockDisplay.setInterpolationDuration(1);
        blockDisplay.setTeleportDuration(3);
        blockDisplay.setViewRange(2);
        return blockDisplay;
    }
}
