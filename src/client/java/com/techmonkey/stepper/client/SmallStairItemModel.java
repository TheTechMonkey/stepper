package com.techmonkey.stepper.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.techmonkey.stepper.SmallStairShapes;
import com.techmonkey.stepper.client.mixin.ItemStackRenderStateAccessor;
import com.techmonkey.stepper.client.mixin.LayerRenderStateAccessor;

/**
 * Item model for a small stair: lets the base block's own item model do all the work
 * (display transforms, tints, enchantment glint), then swaps each layer's quads for the same
 * faces cut down to the small stair shape. So the icon uses the base block's textures and
 * looks like a small stair in inventories, the creative menu, your hand and item frames.
 */
public final class SmallStairItemModel implements ItemModel {
    private static final float EPSILON = 1.0E-4F;
    private static final int GRID = SmallStairShapes.GRID;

    private record Clipped(ItemQuads quads, Vector3fc[] extents) {
    }

    private final ItemModel baseModel;
    private final boolean[][][] voxels;
    private final Map<ItemQuads, Clipped> cache = Collections.synchronizedMap(new IdentityHashMap<>());

    public SmallStairItemModel(ItemModel baseModel, boolean[][][] voxels) {
        this.baseModel = baseModel;
        this.voxels = voxels;
    }

    @Override
    public void update(ItemStackRenderState state, ItemStack stack, ItemModelResolver resolver, ItemDisplayContext displayContext,
            @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        ItemStackRenderStateAccessor stateAccess = (ItemStackRenderStateAccessor) state;
        int firstNewLayer = stateAccess.stepper$getActiveLayerCount();
        baseModel.update(state, stack, resolver, displayContext, level, owner, seed);
        int layerCount = stateAccess.stepper$getActiveLayerCount();
        ItemStackRenderState.LayerRenderState[] layers = stateAccess.stepper$getLayers();
        for (int i = firstNewLayer; i < layerCount; i++) {
            ItemStackRenderState.LayerRenderState layer = layers[i];
            ItemQuads source = ((LayerRenderStateAccessor) layer).stepper$getQuads();
            if (source == null || source.isEmpty()) {
                continue;
            }
            Clipped clipped = cache.computeIfAbsent(source, this::clip);
            layer.setQuads(clipped.quads());
            Vector3fc[] extents = clipped.extents();
            Supplier<Vector3fc[]> extentsSupplier = () -> extents;
            layer.setExtents(extentsSupplier);
        }
    }

    private Clipped clip(ItemQuads source) {
        List<BakedQuad> result = new ArrayList<>();
        for (BakedQuad quad : source.all()) {
            clipQuad(quad, result);
        }
        return new Clipped(ItemQuads.split(result), CuboidItemModelWrapper.computeExtents(result));
    }

    /** Same cutting rules as {@link SmallStairModel}, applied to a vanilla baked quad. */
    private void clipQuad(BakedQuad quad, List<BakedQuad> out) {
        Direction face = quad.direction();
        int axis = face.getAxis().ordinal();
        boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        float plane = positive ? 1.0F : 0.0F;

        for (int vertex = 0; vertex < 4; vertex++) {
            if (Math.abs(coord(quad.position(vertex), axis) - plane) > EPSILON) {
                return; // Not on the outside of the block (an overlay plane, a cross model, ...).
            }
        }

        int axisA = axis == 0 ? 2 : 0;
        int axisB = axis == 1 ? 2 : 1;

        float minA = Float.MAX_VALUE;
        float maxA = -Float.MAX_VALUE;
        float minB = Float.MAX_VALUE;
        float maxB = -Float.MAX_VALUE;
        for (int vertex = 0; vertex < 4; vertex++) {
            float a = coord(quad.position(vertex), axisA);
            float b = coord(quad.position(vertex), axisB);
            minA = Math.min(minA, a);
            maxA = Math.max(maxA, a);
            minB = Math.min(minB, b);
            maxB = Math.max(maxB, b);
        }
        if (maxA - minA < EPSILON || maxB - minB < EPSILON) {
            return;
        }

        float midA = (minA + maxA) * 0.5F;
        float midB = (minB + maxB) * 0.5F;
        float[][] cornerU = new float[2][2];
        float[][] cornerV = new float[2][2];
        boolean[][] cornerSeen = new boolean[2][2];
        boolean[] vertexHighA = new boolean[4];
        boolean[] vertexHighB = new boolean[4];
        for (int vertex = 0; vertex < 4; vertex++) {
            vertexHighA[vertex] = coord(quad.position(vertex), axisA) > midA;
            vertexHighB[vertex] = coord(quad.position(vertex), axisB) > midB;
            int highA = vertexHighA[vertex] ? 1 : 0;
            int highB = vertexHighB[vertex] ? 1 : 0;
            long packed = quad.packedUV(vertex);
            cornerU[highA][highB] = UVPair.unpackU(packed);
            cornerV[highA][highB] = UVPair.unpackV(packed);
            cornerSeen[highA][highB] = true;
        }
        if (!(cornerSeen[0][0] && cornerSeen[0][1] && cornerSeen[1][0] && cornerSeen[1][1])) {
            return;
        }

        int[] index = new int[3];
        int[] neighbor = new int[3];
        for (index[0] = 0; index[0] < GRID; index[0]++) {
            for (index[1] = 0; index[1] < GRID; index[1]++) {
                for (index[2] = 0; index[2] < GRID; index[2]++) {
                    if (!voxels[index[0]][index[1]][index[2]]) {
                        continue;
                    }
                    neighbor[0] = index[0];
                    neighbor[1] = index[1];
                    neighbor[2] = index[2];
                    neighbor[axis] += positive ? 1 : -1;
                    boolean neighborInside = neighbor[axis] >= 0 && neighbor[axis] < GRID;
                    if (neighborInside && voxels[neighbor[0]][neighbor[1]][neighbor[2]]) {
                        continue;
                    }

                    float pieceMinA = Math.max(minA, index[axisA] / (float) GRID);
                    float pieceMaxA = Math.min(maxA, (index[axisA] + 1) / (float) GRID);
                    float pieceMinB = Math.max(minB, index[axisB] / (float) GRID);
                    float pieceMaxB = Math.min(maxB, (index[axisB] + 1) / (float) GRID);
                    if (pieceMaxA - pieceMinA < EPSILON || pieceMaxB - pieceMinB < EPSILON) {
                        continue;
                    }

                    float planeHere = (index[axis] + (positive ? 1 : 0)) / (float) GRID;
                    Vector3f[] positions = new Vector3f[4];
                    long[] uvs = new long[4];
                    for (int vertex = 0; vertex < 4; vertex++) {
                        float a = vertexHighA[vertex] ? pieceMaxA : pieceMinA;
                        float b = vertexHighB[vertex] ? pieceMaxB : pieceMinB;
                        float[] position = new float[3];
                        position[axis] = planeHere;
                        position[axisA] = a;
                        position[axisB] = b;
                        positions[vertex] = new Vector3f(position[0], position[1], position[2]);

                        float fractionA = (a - minA) / (maxA - minA);
                        float fractionB = (b - minB) / (maxB - minB);
                        uvs[vertex] = UVPair.pack(
                                bilinear(cornerU, fractionA, fractionB),
                                bilinear(cornerV, fractionA, fractionB));
                    }
                    out.add(new BakedQuad(positions[0], positions[1], positions[2], positions[3],
                            uvs[0], uvs[1], uvs[2], uvs[3], face, quad.materialInfo()));
                }
            }
        }
    }

    private static float coord(Vector3fc position, int axis) {
        return switch (axis) {
            case 0 -> position.x();
            case 1 -> position.y();
            default -> position.z();
        };
    }

    private static float bilinear(float[][] corner, float fractionA, float fractionB) {
        float low = corner[0][0] + (corner[1][0] - corner[0][0]) * fractionA;
        float high = corner[0][1] + (corner[1][1] - corner[0][1]) * fractionA;
        return low + (high - low) * fractionB;
    }
}
