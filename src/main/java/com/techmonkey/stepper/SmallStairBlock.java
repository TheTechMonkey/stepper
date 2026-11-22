package com.techmonkey.stepper;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A small stair made from some other full block. It reuses vanilla's stair placement,
 * corner and waterlogging logic, and only swaps the shape for the smaller two-thirds one.
 *
 * <p>Mining speed, tool requirements and the name all come from the base block, so a small
 * stone stair mines like stone and needs a pickaxe, without needing per-block tags.
 */
public class SmallStairBlock extends StairBlock {
    private final Block baseBlock;

    public SmallStairBlock(Block baseBlock, Properties properties) {
        super(baseBlock.defaultBlockState(), properties);
        this.baseBlock = baseBlock;
    }

    public Block getBaseBlock() {
        return baseBlock;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SmallStairShapes.shape(state.getValue(FACING), state.getValue(HALF), state.getValue(SHAPE));
    }

    @Override
    public MutableComponent getName() {
        return Component.translatable("block.stepper.small_stairs", baseBlock.getName());
    }

    /** Mine at the base block's speed, using the base block's tool tags. */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return baseBlock.defaultBlockState().getDestroyProgress(player, level, pos);
    }

    /**
     * Always drop exactly one small stair, but only if the tool would have been allowed to
     * harvest the base block. (The block itself never requires a tool, because tool checks
     * use block tags and these generated blocks aren't in any.)
     */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        Float explosionRadius = params.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS);
        if (explosionRadius != null) {
            // Same rule as vanilla's survives_explosion condition.
            boolean survives = explosionRadius <= 0 || params.getLevel().getRandom().nextFloat() <= 1.0F / explosionRadius;
            return survives ? List.of(new ItemStack(this)) : List.of();
        }

        BlockState baseState = baseBlock.defaultBlockState();
        if (baseState.requiresCorrectToolForDrops()
                && params.getOptionalParameter(LootContextParams.THIS_ENTITY) instanceof Player player
                && !player.hasCorrectToolForDrops(baseState)) {
            return List.of();
        }
        return List.of(new ItemStack(this));
    }
}
