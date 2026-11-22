package com.techmonkey.stepper.client;

import com.mojang.serialization.MapCodec;
import org.joml.Matrix4fc;

import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

import com.techmonkey.stepper.SmallStairShapes;

/**
 * Item model definition for a small stair, wrapping the base block's own item model.
 * Uses the same orientation vanilla stair items show (facing east, bottom, straight).
 */
public record SmallStairItemUnbakedModel(ItemModel.Unbaked baseModel) implements ItemModel.Unbaked {
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
        return new SmallStairItemModel(baseModel.bake(context, transformation),
                SmallStairShapes.voxels(Direction.EAST, Half.BOTTOM, StairsShape.STRAIGHT));
    }
}
