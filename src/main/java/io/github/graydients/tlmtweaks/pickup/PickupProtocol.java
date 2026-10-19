// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.pickup;

import io.github.graydients.tlmtweaks.MaidTweaks;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = MaidTweaks.ID)
public final class PickupProtocol {
    private PickupProtocol() {}

    public record Set(PickupSettings settings) implements CustomPacketPayload {
        public static final Type<Set> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MaidTweaks.ID, "set_pickup_rules"));
        public static final StreamCodec<ByteBuf, Set> CODEC = PickupSettings.CODEC.map(Set::new, Set::settings);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record State(PickupSettings settings) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MaidTweaks.ID, "pickup_rules"));
        public static final StreamCodec<ByteBuf, State> CODEC = PickupSettings.CODEC.map(State::new, State::settings);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void handle(Set packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            boolean canEdit = player.hasPermissions(2) || player.getServer().isSingleplayerOwner(player.getGameProfile());
            if (!packet.settings().valid() || !canEdit) {
                PacketDistributor.sendToPlayer(player, new State(PickupConfig.settings()));
                return;
            }
            if (!PickupConfig.update(player.getServer(), packet.settings())) {
                player.sendSystemMessage(Component.translatable("message." + MaidTweaks.ID + ".pickup.save_failed"));
                PacketDistributor.sendToPlayer(player, new State(PickupConfig.settings()));
            }
        });
    }

    public static void broadcast(MinecraftServer server) {
        if (server.getPlayerList() == null) return;
        State snapshot = new State(PickupConfig.settings());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) PacketDistributor.sendToPlayer(player, snapshot);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PacketDistributor.sendToPlayer(player, new State(PickupConfig.settings()));
    }
}
