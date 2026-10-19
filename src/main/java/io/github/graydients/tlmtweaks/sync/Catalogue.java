// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.sync;

import io.github.graydients.tlmtweaks.MaidTweaks;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Catalogue {
    private Map<String, PackEntry> entries = Map.of();
    private long fetchedAt;
    private long attemptedAt;
    private final PackFiles.Fetcher fetcher;

    public Catalogue() { this(PackFiles::get); }
    public Catalogue(PackFiles.Fetcher fetcher) { this.fetcher = fetcher; }

    // Called only by the server's single IO worker.
    public PackEntry find(String fileName, long checksum) throws IOException, InterruptedException {
        long now = System.currentTimeMillis();
        if (now - fetchedAt > 60 * 60 * 1000L || !entries.containsKey(fileName)) {
            if (now - attemptedAt > 30_000) refresh();
        }
        PackEntry result = entries.get(fileName);
        return result != null && result.checksum() == checksum ? result : null;
    }

    private void refresh() throws IOException, InterruptedException {
        attemptedAt = System.currentTimeMillis();
        byte[] data = fetcher.get(PackEntry.ORIGIN.resolve("info.json"), 1024 * 1024);
        var array = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonArray();
        if (array.size() > 2048) throw new IOException("Official catalogue contains too many entries");
        Map<String, PackEntry> next = new LinkedHashMap<>();
        for (var element : array) {
            try {
                var entry = element.getAsJsonObject();
                PackEntry pack = new PackEntry(entry.get("name").getAsString(), entry.get("file_name").getAsString(),
                        entry.get("version").getAsString(), entry.get("url").getAsString(),
                        entry.get("file_size").getAsLong(), entry.get("checksum").getAsLong(), null).validated(false);
                next.put(pack.fileName(), pack);
            } catch (RuntimeException ignored) { /* Unsupported catalogue entry is never accepted. */ }
        }
        if (next.isEmpty()) throw new IOException("Official catalogue is empty or invalid");
        entries = next;
        fetchedAt = System.currentTimeMillis();
    }
}
