// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.sleep;

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
public final class SleepProtocol {
    private SleepProtocol() {}

    public record Set(SleepSettings settings) implements CustomPacketPayload {
        public static final Type<Set> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MaidTweaks.ID, "set_sleep_rules"));
        public static final StreamCodec<ByteBuf, Set> CODEC = SleepSettings.CODEC.map(Set::new, Set::settings);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record State(SleepSettings settings) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MaidTweaks.ID, "sleep_rules"));
        public static final StreamCodec<ByteBuf, State> CODEC = SleepSettings.CODEC.map(State::new, State::settings);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Status(SleepStatus status) implements CustomPacketPayload {
        public static final Type<Status> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MaidTweaks.ID, "sleep_status"));
        public static final StreamCodec<ByteBuf, Status> CODEC = SleepStatus.CODEC.map(Status::new, Status::status);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static boolean canEdit(ServerPlayer player) {
        return player.hasPermissions(2) || player.getServer().isSingleplayerOwner(player.getGameProfile());
    }

    public static void handle(Set packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!packet.settings().valid() || !canEdit(player)) {
                PacketDistributor.sendToPlayer(player, new State(SleepConfig.settings()));
                return;
            }
            if (!SleepConfig.update(player.getServer(), packet.settings())) {
                player.sendSystemMessage(Component.translatable("message." + MaidTweaks.ID + ".sleep.save_failed"));
                PacketDistributor.sendToPlayer(player, new State(SleepConfig.settings()));
            }
        });
    }

    public static void broadcast(MinecraftServer server) {
        if (server.getPlayerList() == null) return; // SERVER config also loads before the player list exists.
        State snapshot = new State(SleepConfig.settings());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) PacketDistributor.sendToPlayer(player, snapshot);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new State(SleepConfig.settings()));
            MaidSleep.invalidate(player.serverLevel());
        }
    }

    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) MaidSleep.invalidate(player.serverLevel());
    }
}
