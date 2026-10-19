// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.permissions;

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
public final class PermissionProtocol {
    private PermissionProtocol() {}

    public record Set(PermissionSettings settings) implements CustomPacketPayload {
        public static final Type<Set> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MaidTweaks.ID, "set_player_permissions"));
        public static final StreamCodec<ByteBuf, Set> CODEC = PermissionSettings.CODEC.map(Set::new, Set::settings);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record State(PermissionSettings settings) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MaidTweaks.ID, "player_permissions"));
        public static final StreamCodec<ByteBuf, State> CODEC = PermissionSettings.CODEC.map(State::new, State::settings);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static boolean apply(Set packet, ServerPlayer player) {
        return (player.hasPermissions(2) || player.getServer().isSingleplayerOwner(player.getGameProfile()))
                && PermissionConfig.update(player.getServer(), packet.settings());
    }

    public static void handle(Set packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            boolean canEdit = player.hasPermissions(2) || player.getServer().isSingleplayerOwner(player.getGameProfile());
            if (canEdit && !apply(packet, player))
                player.sendSystemMessage(Component.translatable("message." + MaidTweaks.ID + ".permissions.save_failed"));
            PacketDistributor.sendToPlayer(player, new State(PermissionConfig.settings()));
        });
    }

    public static void broadcast(MinecraftServer server) {
        if (server.getPlayerList() == null) return;
        var state = new State(PermissionConfig.settings());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) PacketDistributor.sendToPlayer(player, state);
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            PacketDistributor.sendToPlayer(player, new State(PermissionConfig.settings()));
    }
}
