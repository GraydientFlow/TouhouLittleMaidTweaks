// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.sync;

import io.github.graydients.tlmtweaks.MaidTweaks;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Pull/ack transfers have one chunk in flight per pack and fit the C2S payload limit. */
public final class CustomProtocol {
    private CustomProtocol(){}
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path){return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MaidTweaks.ID,path));}
    private static void entry(RegistryFriendlyByteBuf b,CustomEntry p){b.writeUtf(p.id(),36);b.writeUtf(p.name(),180);b.writeUtf(p.sha256(),64);b.writeVarLong(p.bytes());}
    private static CustomEntry entry(RegistryFriendlyByteBuf b){return new CustomEntry(b.readUtf(36),b.readUtf(180),b.readUtf(64),b.readVarLong());}
    public record Offer(String name,String hash,long bytes) implements CustomPacketPayload {
        public static final Type<Offer> TYPE=CustomProtocol.type("custom_offer");
        public static final StreamCodec<RegistryFriendlyByteBuf,Offer> CODEC=StreamCodec.of((b,p)->{b.writeUtf(p.name,180);b.writeUtf(p.hash,64);b.writeVarLong(p.bytes);},b->new Offer(b.readUtf(180),b.readUtf(64),b.readVarLong()));
        public Type<Offer> type(){return TYPE;}
    }
    public record Manifest(String json) implements CustomPacketPayload {
        public static final Type<Manifest> TYPE=CustomProtocol.type("custom_manifest");
        public static final StreamCodec<RegistryFriendlyByteBuf,Manifest> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,200000),b->new Manifest(b.readUtf(200000)));
        public Type<Manifest> type(){return TYPE;}
    }
    public record Pull(UUID ticket,CustomEntry pack,int offset) implements CustomPacketPayload {
        public static final Type<Pull> TYPE=CustomProtocol.type("custom_pull");
        public static final StreamCodec<RegistryFriendlyByteBuf,Pull> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.ticket);entry(b,p.pack);b.writeVarInt(p.offset);},b->new Pull(b.readUUID(),entry(b),b.readVarInt()));
        public Type<Pull> type(){return TYPE;}
    }
    public record Chunk(UUID ticket,int offset,byte[] bytes) implements CustomPacketPayload {
        public static final Type<Chunk> TYPE=CustomProtocol.type("custom_chunk");
        public static final StreamCodec<RegistryFriendlyByteBuf,Chunk> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.ticket);b.writeVarInt(p.offset);if(p.bytes.length>CustomEntry.CHUNK)throw new IllegalArgumentException("Chunk too large");b.writeByteArray(p.bytes);},b->new Chunk(b.readUUID(),b.readVarInt(),b.readByteArray(CustomEntry.CHUNK)));
        public Type<Chunk> type(){return TYPE;}
    }
    public record Abort(UUID ticket) implements CustomPacketPayload {
        public static final Type<Abort> TYPE=CustomProtocol.type("custom_abort");
        public static final StreamCodec<RegistryFriendlyByteBuf,Abort> CODEC=StreamCodec.of((b,p)->b.writeUUID(p.ticket),b->new Abort(b.readUUID()));
        public Type<Abort> type(){return TYPE;}
    }
}
