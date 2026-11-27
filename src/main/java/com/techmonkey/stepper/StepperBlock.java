package com.techmonkey.stepper;

import java.util.List;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** A Stepper block cut from some other block (small stairs and vertical small stairs). */
public interface StepperBlock {
    Block getBaseBlock();

    /**
     * Always drop exactly one of {@code self}, but only if the tool would have been allowed to
     * harvest the base block. (The block itself never requires a tool, because tool checks
     * use block tags and these generated blocks aren't in any.)
     */
    static List<ItemStack> drops(Block self, Block baseBlock, LootParams.Builder params) {
        Float explosionRadius = params.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS);
        if (explosionRadius != null) {
            // Same rule as vanilla's survives_explosion condition.
            boolean survives = explosionRadius <= 0 || params.getLevel().getRandom().nextFloat() <= 1.0F / explosionRadius;
            return survives ? List.of(new ItemStack(self)) : List.of();
        }

        BlockState baseState = baseBlock.defaultBlockState();
        if (baseState.requiresCorrectToolForDrops()
                && params.getOptionalParameter(LootContextParams.THIS_ENTITY) instanceof Player player
                && !player.hasCorrectToolForDrops(baseState)) {
            return List.of();
        }
        return List.of(new ItemStack(self));
    }
}
