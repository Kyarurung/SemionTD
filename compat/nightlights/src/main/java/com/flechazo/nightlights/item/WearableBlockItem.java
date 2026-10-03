package com.flechazo.nightlights.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Block;

public class WearableBlockItem extends BlockItem {

    public WearableBlockItem(Block block, Properties properties) {
        super(block, properties.component(DataComponents.EQUIPPABLE,
                Equippable.builder(EquipmentSlot.HEAD).build()));
    }
}