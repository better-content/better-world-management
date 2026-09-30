package com.bettercontent.betterworldmanagement;

import com.bettercontent.betterworldmanagement.api.event.WorldCondenserAccessedEvent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import org.joml.Vector3f;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

public final class WorldCondenserInterfaceBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final VoxelShape SHAPE = Shapes.or(box(1, 9, 1, 15, 16, 15), box(5, 0, 5, 11, 9, 11));

    public WorldCondenserInterfaceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (!WorldCondenserAssembly.valid(context.getLevel(), context.getClickedPos())) return null;
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                         net.minecraft.world.phys.shapes.CollisionContext context) { return SHAPE; }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WorldCondenserBlockEntity(pos, state);
    }

    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof WorldCondenserBlockEntity condenser) || !condenser.isPouring()) return;
        level.addParticle(new DustParticleOptions(new Vector3f(0.38f, 0.72f, 0.58f), 0.85f),
                pos.getX() + 0.5, pos.getY() - random.nextDouble() * 1.5, pos.getZ() + 0.5,
                0, -0.045, 0);
        if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.PORTAL,
                pos.getX() + 0.5, pos.getY() - 1.4, pos.getZ() + 0.5, 0, 0.02, 0);
    }

    static boolean hasOperatorPermission(int permissionLevel) { return permissionLevel >= 4; }

    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                           InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof WorldCondenserBlockEntity condenser)) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!PrestigeService.supportsPrestigeReset(serverPlayer.server)) {
                serverPlayer.displayClientMessage(Component.translatable(
                        "message.better_world_management.condenser_dedicated_only"), true);
                return InteractionResult.CONSUME;
            }
            if (!WorldCondenserAssembly.valid(level, pos)) {
                serverPlayer.displayClientMessage(Component.literal("Place the Condenser two blocks above an unbound Font, with open space between."), true);
                return InteractionResult.CONSUME;
            }
            String episodeId=java.util.UUID.nameUUIDFromBytes((serverPlayer.getUUID()+":"+level.dimension().location()+":"+pos.asLong()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new WorldCondenserAccessedEvent(
                    serverPlayer, level.dimension(), pos, episodeId));
            if (!PrestigeNetwork.allowPhysicalOpen(serverPlayer)) return InteractionResult.CONSUME;
            try {
                NetworkHooks.openScreen(serverPlayer, condenser, buffer -> {
                    buffer.writeBlockPos(pos);
                    buffer.writeBoolean(false);
                    buffer.writeVarInt(0);
                });
                PrestigeMod.LOGGER.info("World Condenser opened for {} at {}", serverPlayer.getScoreboardName(), pos);
            } catch (RuntimeException error) {
                PrestigeMod.LOGGER.error("World Condenser menu failed to open for {} at {}", serverPlayer.getScoreboardName(), pos, error);
                serverPlayer.displayClientMessage(Component.literal("World Condenser failed to open; check the server log."), false);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
