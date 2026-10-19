// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.station;

import io.github.graydients.tlmtweaks.MaidTweaks;
import net.minecraft.core.BlockPos;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class StationProtocol {
    private StationProtocol() {}
    public record Configure(BlockPos pos, StationSettings settings) implements CustomPacketPayload {
        public static final Type<Configure> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MaidTweaks.ID,"configure_station"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Configure> CODEC=StreamCodec.of((buf,p)-> {
            buf.writeBlockPos(p.pos); buf.writeVarInt(p.settings.method()); buf.writeDouble(p.settings.density()); buf.writeVarInt(p.settings.keepCount());
            buf.writeBoolean(p.settings.limitBreeding()); buf.writeBoolean(p.settings.protectPair());
        },buf->new Configure(buf.readBlockPos(),new StationSettings(buf.readVarInt(),buf.readDouble(),buf.readVarInt(),buf.readBoolean(),buf.readBoolean())));
        @Override public Type<Configure> type() { return TYPE; }
    }
    public record Animals(int window, List<StationAnimals.Row> rows) implements CustomPacketPayload {
        public Animals { rows = List.copyOf(rows); }
        public static final Type<Animals> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MaidTweaks.ID,"station_animals"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Animals> CODEC = StreamCodec.of((buf, packet) -> {
            buf.writeVarInt(packet.window);
            buf.writeCollection(packet.rows, (b, row) -> {
                b.writeResourceLocation(row.type()); b.writeVarInt(row.adults()); b.writeVarInt(row.babies()); b.writeVarInt(row.ready());
                b.writeVarLong(row.volume()); b.writeVarLong(row.footprint()); b.writeVarLong(row.adultVolume()); b.writeVarLong(row.adultFootprint()); b.writeVarLong(row.babyVolume()); b.writeVarLong(row.babyFootprint());
            });
        }, buf -> new Animals(buf.readVarInt(), buf.readList(b -> new StationAnimals.Row(b.readResourceLocation(),
                b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarLong(), b.readVarLong(), b.readVarLong(), b.readVarLong(), b.readVarLong(), b.readVarLong()))));
        @Override public Type<Animals> type() { return TYPE; }
    }
    public static void receive(Animals packet, Player player) {
        if (player.containerMenu instanceof StationMenu menu && menu.containerId == packet.window) menu.acceptAnimals(packet.rows);
    }
    public static void apply(Configure packet, Player player) {
        if (!packet.settings.valid() || !player.mayBuild() || !(player.containerMenu instanceof StationMenu menu) || !menu.pos.equals(packet.pos) || !menu.stillValid(player)) return;
        if (player.level().getBlockEntity(packet.pos) instanceof StationBlockEntity station) station.configure(packet.settings);
    }
    public static void handle(Configure packet, IPayloadContext context) { context.enqueueWork(()->apply(packet,context.player())); }
}
