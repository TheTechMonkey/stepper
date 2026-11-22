package com.techmonkey.stepper;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/** Block item whose name is built from the base block, e.g. "Small Stone Stairs". */
public class SmallStairItem extends BlockItem {
    public SmallStairItem(SmallStairBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return getBlock().getName();
    }
}
