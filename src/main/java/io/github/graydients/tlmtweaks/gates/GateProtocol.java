// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.gates;

import com.github.tartaricacid.touhoulittlemaid.inventory.container.config.MaidConfigContainer;
import io.github.graydients.tlmtweaks.MaidTweaks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class GateProtocol {
    private GateProtocol() {}
    public record SetMode(int maidId,int mode) implements CustomPacketPayload {
        public static final Type<SetMode> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(MaidTweaks.ID,"gate_mode"));
        public static final StreamCodec<RegistryFriendlyByteBuf,SetMode> CODEC=StreamCodec.of((b,p)->{b.writeVarInt(p.maidId);b.writeByte(p.mode);},b->new SetMode(b.readVarInt(),b.readUnsignedByte()));
        public Type<SetMode> type(){return TYPE;}
    }
    public record Flash(BlockPos pos,int maidId,int duration) implements CustomPacketPayload {
        public static final Type<Flash> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(MaidTweaks.ID,"ghost_gate_flash"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Flash> CODEC=StreamCodec.of((b,p)->{b.writeBlockPos(p.pos);b.writeVarInt(p.maidId);b.writeVarInt(p.duration);},b->new Flash(b.readBlockPos(),b.readVarInt(),b.readVarInt()));
        public Type<Flash> type(){return TYPE;}
    }
    public static void apply(SetMode packet,Player player) {
        if(packet.mode<0 || packet.mode>2 || !(player.containerMenu instanceof MaidConfigContainer menu) || menu.getMaid()==null
                || menu.getMaid().getId()!=packet.maidId || !menu.stillValid(player)
                || !io.github.graydients.tlmtweaks.permissions.PlayerPermissions.canChangeSettings(player, menu.getMaid())) return;
        GhostGates.setMode(menu.getMaid(),packet.mode);
    }
    public static void handle(SetMode packet,IPayloadContext context) {context.enqueueWork(()->apply(packet,context.player()));}
}
