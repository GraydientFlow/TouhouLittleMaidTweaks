// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.sync;

import io.github.graydients.tlmtweaks.MaidTweaks;

import java.net.URI;
import java.util.List;
import java.util.UUID;

public record PackEntry(String id, String fileName, String version, String url,
                        long bytes, long checksum, String sha256) {
    public static final URI ORIGIN = URI.create("https://tlmdl.cfpa.team/");
    public static final long MAX_BYTES = 25L * 1024 * 1024;

    public PackEntry validated(boolean requireHash) {
        if (id == null || id.isBlank() || id.length() > 200 || version == null || version.length() > 80
                || !safeFileName(fileName) || bytes < 1 || bytes > MAX_BYTES
                || checksum < 0 || checksum > 0xffffffffL || url == null) {
            throw new IllegalArgumentException("Invalid catalogue pack metadata");
        }
        URI resolved = ORIGIN.resolve(url);
        if (!"https".equals(resolved.getScheme()) || !ORIGIN.getHost().equals(resolved.getHost())
                || resolved.getPort() != -1 || resolved.getUserInfo() != null
                || resolved.getQuery() != null || resolved.getFragment() != null
                || !("/file/" + fileName).equals(resolved.getPath())) {
            throw new IllegalArgumentException("Pack URL is outside the official catalogue host");
        }
        if (requireHash && (sha256 == null || !sha256.matches("[0-9a-f]{64}"))) {
            throw new IllegalArgumentException("Pack is missing its server-verified SHA256");
        }
        return this;
    }

    public URI downloadUri() { return ORIGIN.resolve(url); }
    public PackEntry withHash(String hash) { return new PackEntry(id, fileName, version, url, bytes, checksum, hash); }

    public static boolean safeFileName(String name) {
        return name != null && name.length() <= 180 && name.endsWith(".zip") && !name.startsWith(".")
                && !name.contains("..") && name.chars().noneMatch(c -> c < 32 || "/\\:".indexOf(c) >= 0);
    }

    public record Registry(int format, String serverId, List<PackEntry> packs) {
        public Registry validated() {
            if (format != 1 || serverId == null || packs == null || packs.size() > 256) {
                throw new IllegalArgumentException("Invalid shared pack registry");
            }
            UUID.fromString(serverId);
            java.util.Set<String> ids = new java.util.HashSet<>();
            java.util.Set<String> names = new java.util.HashSet<>();
            for (PackEntry pack : packs) {
                pack.validated(true);
                if (!ids.add(pack.id) || !names.add(pack.fileName)) throw new IllegalArgumentException("Duplicate shared pack");
            }
            return this;
        }
    }
}
