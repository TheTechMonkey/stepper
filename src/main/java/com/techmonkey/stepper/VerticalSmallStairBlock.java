package com.techmonkey.stepper;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A small stair turned on its side: the same two 1/3 steps, standing upright in one corner of
 * the block, full height.
 *
 * <p>{@link #FACING} names one wall of the corner; the other wall is FACING turned clockwise.
 * So NORTH sits in the north-east corner, EAST in the south-east, SOUTH in the south-west and
 * WEST in the north-west.
 *
 * <p>Placement is a plain right-click, no sneaking: it goes in the corner of the space nearest
 * to the spot you clicked. Click the left or right half of a wall and it hugs that wall on that
 * side; click the top of a block and it goes in the quarter you clicked.
 */
public class VerticalSmallStairBlock extends Block implements StepperBlock, SimpleWaterloggedBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private final Block baseBlock;

    public VerticalSmallStairBlock(Block baseBlock, Properties properties) {
        super(properties);
        this.baseBlock = baseBlock;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
    }

    @Override
    public Block getBaseBlock() {
        return baseBlock;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Vec3 hit = context.getClickLocation();
        boolean east = hit.x - pos.getX() >= 0.5;
        boolean south = hit.z - pos.getZ() >= 0.5;
        boolean water = context.getLevel().getFluidState(pos).getType() == Fluids.WATER;
        return defaultBlockState().setValue(FACING, cornerFacing(east, south)).setValue(WATERLOGGED, water);
    }

    /** The FACING whose corner (FACING and FACING clockwise) is the given one. */
    static Direction cornerFacing(boolean east, boolean south) {
        if (south) {
            return east ? Direction.EAST : Direction.SOUTH;
        }
        return east ? Direction.NORTH : Direction.WEST;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SmallStairShapes.verticalShape(state.getValue(FACING));
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        Direction facing = state.getValue(FACING);
        Direction wallA = mirror.mirror(facing);
        Direction wallB = mirror.mirror(facing.getClockWise());
        // After mirroring, one of the two walls is the new FACING and the other is its clockwise.
        return state.setValue(FACING, wallA.getClockWise() == wallB ? wallA : wallB);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess scheduledTickAccess, BlockPos pos,
            Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (state.getValue(WATERLOGGED)) {
            scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    public MutableComponent getName() {
        return Component.translatable("block.stepper.vertical_small_stairs", baseBlock.getName());
    }

    /** Mine at the base block's speed, using the base block's tool tags. */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return baseBlock.defaultBlockState().getDestroyProgress(player, level, pos);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return StepperBlock.drops(this, baseBlock, params);
    }
}
