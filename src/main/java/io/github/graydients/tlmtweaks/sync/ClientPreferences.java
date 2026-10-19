// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.sync;

import io.github.graydients.tlmtweaks.MaidTweaks;
import java.io.IOException;
import java.net.IDN;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class ClientPreferences {
    public record Consent(boolean enabled, boolean customPacks) {
        public static final Consent OFF = new Consent(false, false);
    }
    private record Saved(int format, Map<String, Consent> servers) {}
    private final Map<String, Consent> servers = new LinkedHashMap<>();

    public static String key(String host, int port) {
        if (host == null || host.isBlank() || port < 1 || port > 65535) throw new IllegalArgumentException("Invalid server address");
        String normalized = host.strip().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) normalized = normalized.substring(1, normalized.length() - 1);
        if (normalized.indexOf(':') >= 0) return "[" + normalized + "]:" + port;
        normalized = IDN.toASCII(normalized);
        if (normalized.endsWith(".")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized + ":" + port;
    }

    public static String worldKey(Path worldRoot) {
        try {
            byte[] path = worldRoot.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8);
            return "local-world:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(path));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public synchronized Consent get(String serverKey) { return servers.getOrDefault(serverKey, Consent.OFF); }
    public synchronized void set(String serverKey, Consent consent) { servers.put(serverKey, consent); }

    public synchronized void load(Path file) throws IOException {
        if (!Files.exists(file)) return;
        try {
            Saved saved = MaidTweaks.JSON.fromJson(Files.readString(file), Saved.class);
            if (saved == null || saved.format != 1 || saved.servers == null || saved.servers.containsValue(null))
                throw new IllegalArgumentException("Invalid per-server preferences");
            servers.clear();
            servers.putAll(saved.servers);
        } catch (RuntimeException invalid) { throw new IOException("Invalid per-server maid sync preferences", invalid); }
    }

    public void save(Path file) throws IOException {
        Saved snapshot;
        synchronized (this) { snapshot = new Saved(1, new LinkedHashMap<>(servers)); }
        PackFiles.atomicJson(file, snapshot);
    }
}
