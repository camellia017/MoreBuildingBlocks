package com.camelie.morebuildingblocks.block;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * One eighth of a block: the cell it sits in, cut into the eight cubes an eighth of a block on a
 * side, each of them under its own switch - see {@link MBEighthProperties}.
 *
 * <p>A cell holds eighths of one material only, and they are all one block, so a cell that already
 * has a piece takes another one through {@link #canBeReplaced}: the click is read as "this cell" and
 * the piece named by it is switched on. A click that names a cell instead - any click on a face of
 * the cell rather than on one of the seams inside it - goes to the cell beyond that face, which is
 * the block vanilla builds in, and is the reason a cell that is full simply passes clicks on to its
 * neighbours.
 *
 * <p>The piece a click names is read off where the click landed: the face the player pointed at is
 * cut into four quarters, each of which names two of the three halves of the piece, and the third
 * half is the one lying against that face. On a seam inside the cell - a plane one of the pieces
 * shows to a neighbour in the same cell - the third half is the one on the far side of the seam
 * instead, which is what makes a wall of eighths grow piece by piece out of a cell.
 *
 * <p>The stair and the slab cut from the same block never share a cell with these pieces, in either
 * order: the eighth refuses a stair or slab aimed at a seam of its cell, and {@link
 * #getStateForPlacement} refuses an eighth aimed at a seam of its own stair or slab. Both refusals
 * leave the world, and the held item, exactly as they were.
 */
public class MBEighthBlock extends Block {
	/** How far off the middle of a cell a click may land and still count as a click on a seam. */
	private static final double SEAM = 1.0E-6;

	/** The slab of the same material, or {@code null} for a material that has no slab. */
	private final @Nullable Block slab;

	/** The stairs of the same material, or {@code null} for a material that has no stairs. */
	private final @Nullable Block stairs;

	/**
	 * @param slab the slab of the same material, or {@code null}; only the material's own forms are
	 *             ever named by a piece, which is why they travel with the block instead of being
	 *             looked up.
	 * @param stairs the stairs of the same material, or {@code null}.
	 */
	public MBEighthBlock(final @Nullable Block slab, final @Nullable Block stairs, final Properties properties) {
		super(properties);
		this.slab = slab;
		this.stairs = stairs;
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(MBEighthProperties.BITS);
		builder.add(MBEighthProperties.FACING);
	}

	@Override
	protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
		return MBEighthProperties.shape(state);
	}

	/** The collision shape follows the outline by itself - see {@code BlockBehaviour.getCollisionShape}. */
	@Override
	protected boolean useShapeForLightOcclusion(final BlockState state) {
		return !MBEighthProperties.isFull(state);
	}

	/**
	 * Every piece in the cell comes back at once.
	 *
	 * <p>A cell is mined as one block, so which eighths were in it is read off the block state
	 * rather than left to a loot table: one eighth item for every piece that was still there.
	 */
	@Override
	protected List<ItemStack> getDrops(final BlockState state, final LootParams.Builder params) {
		List<ItemStack> drops = new ArrayList<>();

		for (BooleanProperty piece : MBEighthProperties.BITS) {
			if (state.getValue(piece)) {
				drops.add(new ItemStack(this.asItem()));
			}
		}

		return drops;
	}

	@Override
	protected boolean canBeReplaced(final BlockState state, final BlockPlaceContext context) {
		if (state.is(this)) {
			if (!context.getItemInHand().is(this.asItem()) || MBEighthProperties.isFull(state)) {
				return false;
			}

			// Two questions are asked of this method. The first comes while the context is being
			// built, which is before the flag below has been written and while the state is still the
			// block the click landed on: only a click on one of the seams inside the cell means the
			// click stays in it, and every click on a face of the cell means the next cell along.
			// The second comes from canPlace(), for the cell the placement is going into, where the
			// only thing left to ask is whether there is room in it.
			return context.replacingClickedOnBlock() ? isSeam(context) : true;
		}

		// A cell of another material's eighths keeps its own material: the click goes to the cell
		// beyond the face, so no second material can ever take over a cell that is in use.
		if (state.getBlock() instanceof MBEighthBlock) {
			return false;
		}

		// Any other block: a cell of eighths is the only cell this item can join, so vanilla's own
		// answer stands. Air and grass may be built over; a stair, a slab or a plain block may not,
		// and the click goes to the cell beyond the face as it does for any other block item.
		return super.canBeReplaced(state, context);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(final BlockPlaceContext context) {
		BlockPos cell = context.getClickedPos();
		BlockState existing = context.getLevel().getBlockState(cell);
		BooleanProperty bit = MBEighthProperties.bit(pieceAt(context));

		if (existing.is(this)) {
			// One more piece for a cell that has room, under the texture direction its first piece
			// settled. A piece can only be added where there is none: the click reached an empty
			// eighth through a face of the cell, or a neighbour's face through a seam of this one.
			return existing.getValue(bit) ? null : existing.setValue(bit, true);
		}

		if (!existing.canBeReplaced(context)) {
			return null;
		}

		if (isOwnForm(context) && isSeam(context)) {
			return null;
		}

		BlockState state = this.defaultBlockState();

		for (BooleanProperty other : MBEighthProperties.BITS) {
			state = state.setValue(other, other == bit);
		}

		return state.setValue(MBEighthProperties.FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected InteractionResult useItemOn(
		final ItemStack stack,
		final BlockState state,
		final Level level,
		final BlockPos pos,
		final Player player,
		final InteractionHand hand,
		final BlockHitResult hitResult
	) {
		if (isSeam(hitResult, pos) && isRefused(stack)) {
			// A click on a seam of this cell means "add to this cell", and a cell never takes two
			// materials. The click is spent and nothing else happens - in particular the item is not
			// used up, and the cell beyond the seam is not built in either.
			return InteractionResult.SUCCESS;
		}

		return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
	}

	@Override
	protected BlockState rotate(final BlockState state, final Rotation rotation) {
		return MBEighthProperties.turned(state, switch (rotation) {
			case CLOCKWISE_90 -> 1;
			case CLOCKWISE_180 -> 2;
			case COUNTERCLOCKWISE_90 -> 3;
			case NONE -> 0;
		});
	}

	@Override
	protected BlockState mirror(final BlockState state, final Mirror mirror) {
		if (mirror == Mirror.NONE) {
			return state;
		}

		// A mirror flips one horizontal axis of the cell, and which one it is shows in where the
		// mirror sends the two horizontal directions. The pieces move with the axis; the texture
		// direction is only a quarter turn either way, so a mirrored pattern is turned to face the
		// mirrored side, as it is for a glazed stair or slab.
		boolean flipEast = mirror.mirror(Direction.EAST) == Direction.WEST;
		boolean flipSouth = mirror.mirror(Direction.NORTH) == Direction.SOUTH;
		int index = 0;

		for (int piece = 0; piece < MBEighthProperties.BITS.length; piece++) {
			if (!state.getValue(MBEighthProperties.BITS[piece])) {
				continue;
			}

			index |= 1 << MBEighthProperties.index(
				MBEighthProperties.isEast(piece) != flipEast,
				MBEighthProperties.isUpper(piece),
				MBEighthProperties.isSouth(piece) != flipSouth);
		}

		BlockState mirrored = state;

		for (int piece = 0; piece < MBEighthProperties.BITS.length; piece++) {
			mirrored = mirrored.setValue(MBEighthProperties.BITS[piece], (index & (1 << piece)) != 0);
		}

		return mirrored.setValue(MBEighthProperties.FACING, mirror.mirror(state.getValue(MBEighthProperties.FACING)));
	}

	/**
	 * @return the eighth a click names, as an index into {@link MBEighthProperties#BITS}, read in the
	 *         coordinates of the cell being built in.
	 */
	private static int pieceAt(final BlockPlaceContext context) {
		BlockPos cell = context.getClickedPos();
		double clickX = context.getClickLocation().x - cell.getX();
		double clickY = context.getClickLocation().y - cell.getY();
		double clickZ = context.getClickLocation().z - cell.getZ();
		boolean east = clickX >= 0.5;
		boolean upper = clickY >= 0.5;
		boolean south = clickZ >= 0.5;

		if (context.replacingClickedOnBlock() && isSeam(context)) {
			// The click landed on a seam of this cell: the two axes across the seam come from the
			// quarter of it that was hit, and the axis along the seam is the side the face looks at,
			// which is the piece the player is holding the new one against.
			boolean positive = context.getClickedFace().getAxisDirection() == Direction.AxisDirection.POSITIVE;

			switch (context.getClickedFace().getAxis()) {
				case X -> east = positive;
				case Y -> upper = positive;
				case Z -> south = positive;
			}
		}

		return MBEighthProperties.index(east, upper, south);
	}

	/** @return {@code true} when the click landed on a middle plane of the cell it was made against. */
	private static boolean isSeam(final BlockPlaceContext context) {
		return isSeam(context.getClickLocation(), context.getClickedFace(), hitCell(context));
	}

	/** @return {@code true} when the click landed on a middle plane of {@code cell}. */
	private static boolean isSeam(final BlockHitResult hitResult, final BlockPos cell) {
		return isSeam(hitResult.getLocation(), hitResult.getDirection(), cell);
	}

	private static boolean isSeam(final Vec3 location, final Direction face, final BlockPos cell) {
		double local = switch (face.getAxis()) {
			case X -> location.x - cell.getX();
			case Y -> location.y - cell.getY();
			case Z -> location.z - cell.getZ();
		};

		return Math.abs(local - 0.5) < SEAM;
	}

	/**
	 * @return the cell the click landed on, which is the one a click on a seam of it is read against.
	 *         Vanilla only hands out the cell being built in, and the two differ by a whole block
	 *         whenever the click is being read as a click on the next cell along - the top of a
	 *         bottom slab is read as the cell above it, and the boundary of a cell as the cell past
	 *         it - so the cell is taken from the click itself.
	 */
	private static BlockPos hitCell(final BlockPlaceContext context) {
		if (context instanceof PlacementClick click) {
			return click.morebuildingblocks$hitPos();
		}

		Vec3 location = context.getClickLocation();

		return BlockPos.containing(location.x, location.y, location.z);
	}

	/** @return {@code true} when the click landed on the slab or the stair of this material. */
	private boolean isOwnForm(final BlockPlaceContext context) {
		Block clicked = context.getLevel().getBlockState(hitCell(context)).getBlock();

		return clicked == this.slab || clicked == this.stairs;
	}

	/**
	 * @return {@code true} when the held item may not be added to a cell of eighths: the stair or the
	 *         slab of this material, which would share the cell with its own pieces, or one of the
	 *         other eighth blocks, which would share it with another material.
	 */
	private boolean isRefused(final ItemStack stack) {
		if (!(stack.getItem() instanceof BlockItem item)) {
			return false;
		}

		Block held = item.getBlock();

		return held == this.slab || held == this.stairs || held instanceof MBEighthBlock && held != this;
	}
}
