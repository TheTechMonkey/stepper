package com.techmonkey.stepper.client;

import com.mojang.serialization.MapCodec;
import org.joml.Matrix4fc;

import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

import com.techmonkey.stepper.SmallStairShapes;

/**
 * Item model definition for a small stair or vertical small stair, wrapping the base block's
 * own item model. Small stairs use the orientation vanilla stair items show (facing east,
 * bottom, straight). Vertical ones stand in the far (south-west) corner, so the inventory icon
 * shows both steps facing you.
 */
public record SmallStairItemUnbakedModel(ItemModel.Unbaked baseModel, boolean vertical) implements ItemModel.Unbaked {
    @Override
    public MapCodec<SmallStairItemUnbakedModel> type() {
        // Only built in code, never read from or written to JSON.
        return MapCodec.unit(this);
    }

    @Override
    public void resolveDependencies(Resolver resolver) {
        baseModel.resolveDependencies(resolver);
    }

    @Override
    public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transformation) {
        boolean[][][] voxels = vertical
                ? SmallStairShapes.verticalVoxels(Direction.SOUTH)
                : SmallStairShapes.voxels(Direction.EAST, Half.BOTTOM, StairsShape.STRAIGHT);
        return new SmallStairItemModel(baseModel.bake(context, transformation), voxels);
    }
}
