// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.sync;

import io.github.graydients.tlmtweaks.MaidTweaks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class Protocol {
    private Protocol() {}

    public record Hello(boolean enabled, boolean customConsent) implements CustomPacketPayload {
        public static final Type<Hello> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MaidTweaks.ID, "hello"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Hello> CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeBoolean(value.enabled); buf.writeBoolean(value.customConsent); },
                buf -> new Hello(buf.readBoolean(), buf.readBoolean()));
        @Override public Type<Hello> type() { return TYPE; }
    }

    public record Policy(boolean enabled, boolean customTransfer) implements CustomPacketPayload {
        public static final Type<Policy> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MaidTweaks.ID, "policy"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Policy> CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeBoolean(value.enabled); buf.writeBoolean(value.customTransfer); },
                buf -> new Policy(buf.readBoolean(), buf.readBoolean()));
        @Override public Type<Policy> type() { return TYPE; }
    }

    public record Offer(String fileName, long checksum) implements CustomPacketPayload {
        public static final Type<Offer> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MaidTweaks.ID, "offer"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Offer> CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeUtf(value.fileName, 180); buf.writeLong(value.checksum); },
                buf -> new Offer(buf.readUtf(180), buf.readLong()));
        @Override public Type<Offer> type() { return TYPE; }
    }

    public record Manifest(String json) implements CustomPacketPayload {
        public static final Type<Manifest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MaidTweaks.ID, "manifest"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Manifest> CODEC = StreamCodec.of(
                (buf, value) -> buf.writeUtf(value.json, 200_000),
                buf -> new Manifest(buf.readUtf(200_000)));
        @Override public Type<Manifest> type() { return TYPE; }
    }
}
