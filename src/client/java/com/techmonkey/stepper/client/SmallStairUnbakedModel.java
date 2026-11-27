package com.techmonkey.stepper.client;

import java.util.List;

import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.techmonkey.stepper.SmallStairShapes;

/**
 * Model "loader" for one small stair or vertical small stair block: there's no JSON, the
 * model is built in code.
 */
public record SmallStairUnbakedModel(Block baseBlock) implements BlockStateModel.UnbakedRoot {
    @Override
    public BlockStateModel bake(BlockState state, ModelBaker baker) {
        return new SmallStairModel(baseBlock.defaultBlockState(), SmallStairShapes.voxels(state));
    }

    /** Waterlogged and dry versions of the same shape look identical. */
    @Override
    public Object visualEqualityGroup(BlockState state) {
        // The voxel arrays are shared per shape, so the same array means the same look.
        return List.of(baseBlock, SmallStairShapes.voxels(state));
    }

    @Override
    public void resolveDependencies(Resolver resolver) {
        // No other models needed: the base block's model is looked up when rendering.
    }
}
