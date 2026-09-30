package com.bettercontent.betterworldmanagement;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class WorldCondenserBlockEntity extends BlockEntity implements MenuProvider {
    private boolean pouring;
    public WorldCondenserBlockEntity(BlockPos pos, BlockState state) {
        super(PrestigeRegistry.WORLD_CONDENSER_BLOCK_ENTITY.get(), pos, state);
    }

    public boolean isPouring() { return pouring; }

    void setPouring(boolean value) {
        if (pouring == value) return;
        pouring = value;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("Pouring", pouring);
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        pouring = tag.getBoolean("Pouring");
    }

    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }

    @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override public Component getDisplayName() {
        return Component.translatable("screen.better_world_management.world_condenser");
    }

    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new WorldCondenserMenu(id, inventory, worldPosition);
    }
}
