// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.jobs;

import io.github.graydients.tlmtweaks.MaidTweaks;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = MaidTweaks.ID)
public final class JobProtocol {
    private JobProtocol() {}
    public record SetAnimal(int maidId, AnimalSettings settings) implements CustomPacketPayload {
        public static final Type<SetAnimal> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MaidTweaks.ID, "set_animal_job"));
        public static final StreamCodec<ByteBuf, SetAnimal> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SetAnimal::maidId,
                ByteBufCodecs.fromCodec(AnimalSettings.CODEC), SetAnimal::settings, SetAnimal::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static void apply(SetAnimal packet, Player sender) {
        if (!packet.settings().valid() || !(sender.containerMenu instanceof AnimalConfigMenu menu)
                || menu.getMaid() == null || menu.getMaid().getId() != packet.maidId() || !menu.stillValid(sender)
                || !io.github.graydients.tlmtweaks.permissions.PlayerPermissions.canChangeSettings(sender, menu.getMaid())
                || !AnimalWork.active(menu.getMaid())) return;
        menu.getMaid().setAndSyncData(AnimalSettings.KEY, packet.settings());
        AnimalWork.invalidate(menu.getMaid());
    }
    public static void handle(SetAnimal packet, IPayloadContext context) {
        context.enqueueWork(() -> apply(packet, context.player()));
    }
    public record SetWork(WorkSettings settings) implements CustomPacketPayload {
        public static final Type<SetWork> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MaidTweaks.ID, "set_work_rules"));
        public static final StreamCodec<ByteBuf, SetWork> CODEC = WorkSettings.CODEC.map(SetWork::new, SetWork::settings);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record WorkState(WorkSettings settings) implements CustomPacketPayload {
        public static final Type<WorkState> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MaidTweaks.ID, "work_rules"));
        public static final StreamCodec<ByteBuf, WorkState> CODEC = WorkSettings.CODEC.map(WorkState::new, WorkState::settings);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static boolean canEditWork(ServerPlayer player) {
        return player.hasPermissions(2) || player.getServer().isSingleplayerOwner(player.getGameProfile());
    }
    public static boolean applyWork(SetWork packet, ServerPlayer player) {
        return packet.settings().valid() && canEditWork(player) && JobConfig.update(player.getServer(), packet.settings());
    }
    public static void handle(SetWork packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (packet.settings().valid() && canEditWork(player)) {
                boolean saved = applyWork(packet, player);
                player.sendSystemMessage(Component.translatable("message." + MaidTweaks.ID + ".work." + (saved ? "saved" : "save_failed")));
            }
            PacketDistributor.sendToPlayer(player, new WorkState(JobConfig.settings()));
        });
    }
    public static void broadcast(MinecraftServer server) {
        if (server.getPlayerList() == null) return;
        var state = new WorkState(JobConfig.settings());
        for (var player : server.getPlayerList().getPlayers()) PacketDistributor.sendToPlayer(player, state);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PacketDistributor.sendToPlayer(player, new WorkState(JobConfig.settings()));
    }
}
