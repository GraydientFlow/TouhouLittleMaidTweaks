// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.station;

import net.minecraft.core.BlockPos;
import io.github.graydients.tlmtweaks.jobs.JobConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/** Geometry is persisted for exclusion, then revalidated before it can advertise work. */
public final class StationBlockEntity extends BlockEntity implements MenuProvider {
    StationSettings settings = StationSettings.defaults();
    long[] savedCells = new long[0], savedBoundary = new long[0];
    long revision, policyOrder;
    StationManager.Pen pen;
    BlockPos selectedSeed;
    int detectionMode, selectedSide;
    private BlockState originalFence = Blocks.AIR.defaultBlockState();
    public static final ModelProperty<BlockState> FENCE_MODEL = new ModelProperty<>();
    private ModelData fenceModel = ModelData.EMPTY;
    public StationBlockEntity(BlockPos pos, BlockState state) { super(StationContent.ENTITY.get(), pos, state); }
    @Override public void onLoad() { super.onLoad(); if (level instanceof ServerLevel server) StationManager.get(server).add(this); }
    @Override public void setRemoved() {
        if (level instanceof ServerLevel server) {
            var manager=StationManager.get(server);
            if (server.hasChunkAt(worldPosition) && !StationContent.isStation(server.getBlockState(worldPosition))) manager.remove(this);
            else manager.unload(this);
        }
        super.setRemoved();
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (selectedSeed != null) tag.putLong("selected_seed", selectedSeed.asLong());
        tag.putInt("detection_mode", detectionMode); tag.putInt("selected_side", selectedSide);
        if (originalFence.getBlock() instanceof FenceBlock) tag.put("original_fence", NbtUtils.writeBlockState(originalFence));
        tag.put("settings", settings.save()); tag.putLongArray("floor_cells", savedCells); tag.putLongArray("boundary", savedBoundary); tag.putLong("revision", revision); tag.putLong("policy_order", policyOrder);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        selectedSeed = tag.contains("selected_seed") ? BlockPos.of(tag.getLong("selected_seed")) : null;
        detectionMode = Math.clamp(tag.getInt("detection_mode"), 0, 5);
        selectedSide = Math.clamp(tag.getInt("selected_side"), 0, 5);
        if (tag.contains("original_fence")) originalFence = NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("original_fence"));
        updateFenceModel();
        if (tag.contains("settings")) settings = StationSettings.load(tag.getCompound("settings"));
        savedCells = tag.getLongArray("floor_cells"); savedBoundary = tag.getLongArray("boundary"); revision = tag.getLong("revision"); policyOrder = tag.getLong("policy_order");
    }
    public BlockState originalFence() { return originalFence; }
    @Override public ModelData getModelData() { return fenceModel; }
    private void updateFenceModel() {
        fenceModel = ModelData.of(FENCE_MODEL, originalFence);
        if (level != null && level.isClientSide()) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }
    public void setOriginalFence(BlockState state) {
        originalFence = state; updateFenceModel(); setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (originalFence.getBlock() instanceof FenceBlock) tag.put("original_fence", NbtUtils.writeBlockState(originalFence));
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    public int detectionMode() { return detectionMode; }
    public int selectedSide() { return selectedSide; }
    public BlockPos selectedSeed() { return selectedSeed; }
    public StationSettings settings() { return settings; }
    public int area() { return pen == null ? savedCells.length : pen.cells.size(); }
    public int status() { return !JobConfig.stationsEnabled() ? 6 : pen == null ? 0 : pen.status; }
    public long revision() { return revision; }
    public int population() { return pen == null || pen.population == null ? 0 : pen.population.animals().size(); }
    public void configure(StationSettings value) {
        if (!(level instanceof ServerLevel server) || !value.valid()) return;
        var manager=StationManager.get(server);
        if (pen==null) manager.add(this);
        manager.configure(this, value);
    }
    @Override public Component getDisplayName() { return Component.translatable("block.touhou_little_maid_tweaks.animal_management_station"); }
    @Override public StationMenu createMenu(int id, Inventory inventory, Player player) { return new StationMenu(id, inventory, getBlockPos()); }
}
