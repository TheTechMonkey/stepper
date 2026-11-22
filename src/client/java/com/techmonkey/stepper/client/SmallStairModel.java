package com.techmonkey.stepper.client;

import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;

import com.techmonkey.stepper.SmallStairShapes;

/**
 * Renders a small stair by taking the base block's own model (whatever mod it comes from)
 * and cutting each of its outer faces down to the faces of the small stair shape.
 *
 * <p>Each face of the small stair is filled with the matching part of the base block's face
 * texture: the riser of a step facing north uses the base block's north texture, and so on.
 * Because the base model is read every time the chunk is built, resource packs, random
 * texture variants, overlays (like grass sides) and tinting all carry over automatically.
 */
public final class SmallStairModel implements BlockStateModel {
    private static final float EPSILON = 1.0E-4F;
    private static final int GRID = SmallStairShapes.GRID;
    private static final ThreadLocal<MutableMesh> CAPTURE = ThreadLocal.withInitial(() -> Renderer.get().mutableMesh());

    private final BlockState baseState;
    private final boolean[][][] voxels;

    public SmallStairModel(BlockState baseState, boolean[][][] voxels) {
        this.baseState = baseState;
        this.voxels = voxels;
    }

    private BlockStateModel baseModel() {
        return Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(baseState);
    }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest) {
        MutableMesh capture = CAPTURE.get();
        capture.clear();
        // Capture every face of the base block, unculled. We decide culling per piece below.
        baseModel().emitQuads(capture.emitter(), level, pos, baseState, random, face -> false);
        capture.forEach(quad -> emitClipped(quad, emitter, cullTest));
        capture.clear();
    }

    /** Copies one base-model face onto every exposed face of the small stair that points the same way. */
    private void emitClipped(QuadView quad, QuadEmitter emitter, Predicate<@Nullable Direction> cullTest) {
        Direction face = quad.lightFace();
        int axis = face.getAxis().ordinal();
        boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        float plane = positive ? 1.0F : 0.0F;

        // Only faces lying on the outside of the block can be cut up this way.
        for (int vertex = 0; vertex < 4; vertex++) {
            if (Math.abs(quad.posByIndex(vertex, axis) - plane) > EPSILON) {
                return;
            }
        }

        // The two axes running across the face.
        int axisA = axis == 0 ? 2 : 0;
        int axisB = axis == 1 ? 2 : 1;

        float minA = Float.MAX_VALUE;
        float maxA = -Float.MAX_VALUE;
        float minB = Float.MAX_VALUE;
        float maxB = -Float.MAX_VALUE;
        for (int vertex = 0; vertex < 4; vertex++) {
            float a = quad.posByIndex(vertex, axisA);
            float b = quad.posByIndex(vertex, axisB);
            minA = Math.min(minA, a);
            maxA = Math.max(maxA, a);
            minB = Math.min(minB, b);
            maxB = Math.max(maxB, b);
        }
        if (maxA - minA < EPSILON || maxB - minB < EPSILON) {
            return;
        }

        // Texture coordinates at the four corners of the source face, indexed [highA][highB].
        float midA = (minA + maxA) * 0.5F;
        float midB = (minB + maxB) * 0.5F;
        float[][] cornerU = new float[2][2];
        float[][] cornerV = new float[2][2];
        boolean[][] cornerSeen = new boolean[2][2];
        for (int vertex = 0; vertex < 4; vertex++) {
            int highA = quad.posByIndex(vertex, axisA) > midA ? 1 : 0;
            int highB = quad.posByIndex(vertex, axisB) > midB ? 1 : 0;
            cornerU[highA][highB] = quad.u(vertex);
            cornerV[highA][highB] = quad.v(vertex);
            cornerSeen[highA][highB] = true;
        }
        if (!(cornerSeen[0][0] && cornerSeen[0][1] && cornerSeen[1][0] && cornerSeen[1][1])) {
            return; // Not a rectangle.
        }

        int[] index = new int[3];
        int[] neighbor = new int[3];
        float[] position = new float[3];
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
                        continue; // Hidden against the next third of the stair.
                    }

                    // This third's face, clipped to the area the source face covers.
                    float pieceMinA = Math.max(minA, index[axisA] / (float) GRID);
                    float pieceMaxA = Math.min(maxA, (index[axisA] + 1) / (float) GRID);
                    float pieceMinB = Math.max(minB, index[axisB] / (float) GRID);
                    float pieceMaxB = Math.min(maxB, (index[axisB] + 1) / (float) GRID);
                    if (pieceMaxA - pieceMinA < EPSILON || pieceMaxB - pieceMinB < EPSILON) {
                        continue;
                    }

                    // Only faces on the outside of the block can be hidden by a neighboring block.
                    Direction cullFace = neighborInside ? null : face;
                    if (cullFace != null && cullTest.test(cullFace)) {
                        continue;
                    }

                    float planeHere = (index[axis] + (positive ? 1 : 0)) / (float) GRID;
                    emitter.copyFrom(quad);
                    for (int vertex = 0; vertex < 4; vertex++) {
                        boolean highA = quad.posByIndex(vertex, axisA) > midA;
                        boolean highB = quad.posByIndex(vertex, axisB) > midB;
                        float a = highA ? pieceMaxA : pieceMinA;
                        float b = highB ? pieceMaxB : pieceMinB;
                        position[axis] = planeHere;
                        position[axisA] = a;
                        position[axisB] = b;
                        emitter.pos(vertex, position[0], position[1], position[2]);

                        float fractionA = (a - minA) / (maxA - minA);
                        float fractionB = (b - minB) / (maxB - minB);
                        emitter.uv(vertex,
                                bilinear(cornerU, fractionA, fractionB),
                                bilinear(cornerV, fractionA, fractionB));
                    }
                    emitter.cullFace(cullFace);
                    emitter.emit();
                }
            }
        }
    }

    private static float bilinear(float[][] corner, float fractionA, float fractionB) {
        float low = corner[0][0] + (corner[1][0] - corner[0][0]) * fractionA;
        float high = corner[0][1] + (corner[1][1] - corner[0][1]) * fractionA;
        return low + (high - low) * fractionB;
    }

    @Override
    public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
        // Everything is emitted through emitQuads (Fabric Renderer API).
    }

    @Override
    public Material.Baked particleMaterial() {
        return baseModel().particleMaterial();
    }

    @Override
    public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        return baseModel().particleMaterial(level, pos, baseState);
    }

    @Override
    public @BakedQuad.MaterialFlags int materialFlags() {
        return baseModel().materialFlags();
    }

    @Override
    public @BakedQuad.MaterialFlags int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        return baseModel().materialFlags(level, pos, baseState, random);
    }
}
