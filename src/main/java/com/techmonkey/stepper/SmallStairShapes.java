package com.techmonkey.stepper;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Geometry of a small stair, shared by the collision shape (server and client) and the
 * client model, so what you see always matches what you walk on.
 *
 * <p>A small stair lives on a 3x3x3 grid of thirds. It is two steps, each 1/3 of a block
 * tall, so the whole piece is 2/3 of a block tall: the lower step is 2/3 deep and the
 * upper step is 1/3 deep, against the back (the side the stair faces). Corners follow the
 * same left/right/inner/outer rules as vanilla stairs, and the top half is the bottom half
 * flipped upside down.
 */
public final class SmallStairShapes {
    /** Grid size: the block is split into thirds on every axis. */
    public static final int GRID = 3;

    private static final Map<Direction, Map<Half, Map<StairsShape, boolean[][][]>>> VOXELS = new EnumMap<>(Direction.class);
    private static final Map<Direction, Map<Half, Map<StairsShape, VoxelShape>>> SHAPES = new EnumMap<>(Direction.class);
    private static final Map<Direction, boolean[][][]> VERTICAL_VOXELS = new EnumMap<>(Direction.class);
    private static final Map<Direction, VoxelShape> VERTICAL_SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Map<Half, Map<StairsShape, boolean[][][]>> voxelsByHalf = new EnumMap<>(Half.class);
            Map<Half, Map<StairsShape, VoxelShape>> shapesByHalf = new EnumMap<>(Half.class);
            for (Half half : Half.values()) {
                Map<StairsShape, boolean[][][]> voxelsByShape = new EnumMap<>(StairsShape.class);
                Map<StairsShape, VoxelShape> shapesByShape = new EnumMap<>(StairsShape.class);
                for (StairsShape stairsShape : StairsShape.values()) {
                    boolean[][][] voxels = buildVoxels(facing, half, stairsShape);
                    voxelsByShape.put(stairsShape, voxels);
                    shapesByShape.put(stairsShape, buildShape(voxels));
                }
                voxelsByHalf.put(half, voxelsByShape);
                shapesByHalf.put(half, shapesByShape);
            }
            VOXELS.put(facing, voxelsByHalf);
            SHAPES.put(facing, shapesByHalf);

            boolean[][][] verticalVoxels = buildVerticalVoxels(facing);
            VERTICAL_VOXELS.put(facing, verticalVoxels);
            VERTICAL_SHAPES.put(facing, buildShape(verticalVoxels));
        }
    }

    private SmallStairShapes() {
    }

    /** Occupied thirds, indexed [x][y][z]. Shared array: do not modify. */
    public static boolean[][][] voxels(Direction facing, Half half, StairsShape stairsShape) {
        return VOXELS.get(facing).get(half).get(stairsShape);
    }

    public static VoxelShape shape(Direction facing, Half half, StairsShape stairsShape) {
        return SHAPES.get(facing).get(half).get(stairsShape);
    }

    /** Occupied thirds of a vertical small stair, indexed [x][y][z]. Shared array: do not modify. */
    public static boolean[][][] verticalVoxels(Direction facing) {
        return VERTICAL_VOXELS.get(facing);
    }

    public static VoxelShape verticalShape(Direction facing) {
        return VERTICAL_SHAPES.get(facing);
    }

    /** Occupied thirds for any small stair or vertical small stair state. */
    public static boolean[][][] voxels(BlockState state) {
        if (state.getBlock() instanceof VerticalSmallStairBlock) {
            return verticalVoxels(state.getValue(VerticalSmallStairBlock.FACING));
        }
        return voxels(state.getValue(StairBlock.FACING), state.getValue(StairBlock.HALF), state.getValue(StairBlock.SHAPE));
    }

    /**
     * The small stair's side profile (two steps, 2/3 and 1/3 deep) laid flat and stood up full
     * height: in the corner between FACING and FACING clockwise, a third is filled when its
     * distances from the two walls add up to 1 or less.
     */
    private static boolean[][][] buildVerticalVoxels(Direction facing) {
        boolean[][][] voxels = new boolean[GRID][GRID][GRID];
        for (int x = 0; x < GRID; x++) {
            for (int z = 0; z < GRID; z++) {
                if (depthFromBack(facing, x, z) + depthFromBack(facing.getClockWise(), x, z) <= 1) {
                    for (int y = 0; y < GRID; y++) {
                        voxels[x][y][z] = true;
                    }
                }
            }
        }
        return voxels;
    }

    private static boolean[][][] buildVoxels(Direction facing, Half half, StairsShape stairsShape) {
        boolean[][][] voxels = new boolean[GRID][GRID][GRID];
        for (int x = 0; x < GRID; x++) {
            for (int z = 0; z < GRID; z++) {
                int columnHeight = columnHeight(facing, stairsShape, x, z);
                for (int step = 0; step < columnHeight; step++) {
                    int y = half == Half.BOTTOM ? step : GRID - 1 - step;
                    voxels[x][y][z] = true;
                }
            }
        }
        return voxels;
    }

    /**
     * How many thirds tall the column at (x, z) is. The back row is 2 tall, the middle row 1,
     * the front row empty. Outer corners only keep the corner of the back rows (the deeper of
     * the two depths wins), inner corners keep both back rows (the shallower depth wins).
     */
    private static int columnHeight(Direction facing, StairsShape stairsShape, int x, int z) {
        int depth = depthFromBack(facing, x, z);
        int depthOfSide = switch (stairsShape) {
            case OUTER_RIGHT, INNER_RIGHT -> depthFromBack(facing.getClockWise(), x, z);
            case OUTER_LEFT, INNER_LEFT -> depthFromBack(facing.getCounterClockWise(), x, z);
            case STRAIGHT -> depth;
        };
        int effectiveDepth = switch (stairsShape) {
            case OUTER_RIGHT, OUTER_LEFT -> Math.max(depth, depthOfSide);
            case INNER_RIGHT, INNER_LEFT -> Math.min(depth, depthOfSide);
            case STRAIGHT -> depth;
        };
        return Math.max(0, 2 - effectiveDepth);
    }

    /** Distance in thirds from the edge of the block on the given side (0 = touching that side). */
    private static int depthFromBack(Direction side, int x, int z) {
        return switch (side) {
            case NORTH -> z;
            case SOUTH -> GRID - 1 - z;
            case WEST -> x;
            case EAST -> GRID - 1 - x;
            default -> throw new IllegalArgumentException("Not a horizontal direction: " + side);
        };
    }

    private static VoxelShape buildShape(boolean[][][] voxels) {
        VoxelShape shape = Shapes.empty();
        double third = 1.0 / GRID;
        for (int x = 0; x < GRID; x++) {
            for (int y = 0; y < GRID; y++) {
                for (int z = 0; z < GRID; z++) {
                    if (voxels[x][y][z]) {
                        shape = Shapes.or(shape, Shapes.box(
                                x * third, y * third, z * third,
                                (x + 1) * third, (y + 1) * third, (z + 1) * third));
                    }
                }
            }
        }
        return shape.optimize();
    }
}
