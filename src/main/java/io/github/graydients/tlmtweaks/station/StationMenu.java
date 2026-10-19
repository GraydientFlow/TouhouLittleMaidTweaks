// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.station;

import net.minecraft.core.BlockPos;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import io.github.graydients.tlmtweaks.jobs.JobConfig;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Readable to nearby players; mutations require this valid menu and build permission. */
public final class StationMenu extends AbstractContainerMenu {
    public final BlockPos pos;
    private final Level level;
    private final StationBlockEntity boundStation;
    private final ContainerData data;
    private final Player viewer;
    private List<StationAnimals.Row> animals = List.of(), sentAnimals;
    private long lastAnimals = Long.MIN_VALUE;
    public StationMenu(int id, Inventory inventory, BlockPos pos) {
        super(StationContent.MENU.get(), id); this.pos = pos; level = inventory.player.level(); viewer = inventory.player;
        boundStation = level.getBlockEntity(pos) instanceof StationBlockEntity station ? station : null;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof StationBlockEntity station) {
            data = new ContainerData() {
                public int get(int index) {
                    var s = station.settings(); int density=(int)Math.round(s.density()*1000000);
                    return switch(index) { case 0 -> s.method(); case 1 -> density & 65535; case 2 -> density >>> 16;
                        case 3 -> s.keepCount(); case 4 -> s.limitBreeding()?1:0; case 5 -> s.protectPair()?1:0;
                        case 6 -> station.status(); case 7 -> station.area(); case 8 -> station.population(); case 9 -> (int)station.revision(); case 10 -> station.detectionMode(); case 11 -> station.selectedSide(); default -> 0; };
                }
                public void set(int index, int value) {}
                public int getCount() { return 12; }
            };
        } else data = new SimpleContainerData(12);
        addDataSlots(data);
    }
    public StationSettings settings() { return new StationSettings(data.get(0), ((data.get(1)&65535) | ((data.get(2)&65535)<<16))/1000000.0, data.get(3)&65535, data.get(4)!=0, data.get(5)!=0); }
    public List<StationAnimals.Row> animals() { return animals; }
    public void acceptAnimals(List<StationAnimals.Row> value) { animals = List.copyOf(value); }
    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (!(level instanceof ServerLevel server) || !(viewer instanceof ServerPlayer player)
                || boundStation == null || player.containerMenu != this) return;
        long now = level.getGameTime();
        if (sentAnimals != null && now - lastAnimals < 20) return;
        lastAnimals = now; animals = StationManager.get(server).animals(boundStation);
        if (animals.equals(sentAnimals)) return;
        sentAnimals = animals;
        PacketDistributor.sendToPlayer(player, new StationProtocol.Animals(containerId, animals));
    }
    public int status() { return data.get(6); }
    public int area() { return data.get(7)&65535; }
    public int population() { return data.get(8)&65535; }
    public int detectionMode() { return data.get(10); }
    public int selectedSide() { return data.get(11); }
    @Override public boolean clickMenuButton(Player player, int id) {
        if (!JobConfig.stationsEnabled() || id < 0 || id > 5 || !player.mayBuild() || !stillValid(player) || player.containerMenu != this
                || !(level instanceof net.minecraft.server.level.ServerLevel server)) return false;
        StationManager.get(server).detect((StationBlockEntity) level.getBlockEntity(pos), id);
        return true;
    }
    @Override public boolean stillValid(Player player) {
        return JobConfig.stationsEnabled() && boundStation != null && !boundStation.isRemoved()
                && level.getBlockEntity(pos) == boundStation && player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;
    }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
