package com.techmonkey.stepper;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Block item for small stairs and vertical small stairs. Its name is built from the base
 * block, e.g. "Small Stone Stairs".
 */
public class SmallStairItem extends BlockItem {
    public SmallStairItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return getBlock().getName();
    }
}
