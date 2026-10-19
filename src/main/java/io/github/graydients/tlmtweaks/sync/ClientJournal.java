// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.sync;

import com.google.gson.reflect.TypeToken;
import io.github.graydients.tlmtweaks.MaidTweaks;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Only the client IO worker reads or mutates pack history and ownership. */
public final class ClientJournal {
    private final Path root;
    private volatile Map<String, Long> downloads = new LinkedHashMap<>();
    private volatile Map<String, List<PackEntry>> managed = new LinkedHashMap<>();

    public ClientJournal(Path configRoot) throws IOException {
        root = configRoot.resolve(MaidTweaks.ID);
        Path history = root.resolve("downloaded-via-ui.json");
        if (Files.exists(history)) {
            Map<String, Long> loaded = MaidTweaks.JSON.fromJson(Files.readString(history), new TypeToken<Map<String, Long>>(){}.getType());
            if (loaded == null) throw new IOException("Invalid download journal");
            for (var entry : loaded.entrySet())
                if (!PackEntry.safeFileName(entry.getKey()) || entry.getValue() == null || entry.getValue() < 0 || entry.getValue() > 0xffffffffL)
                    throw new IOException("Invalid download journal entry");
            downloads = new LinkedHashMap<>(loaded);
        }
        Path ownership = root.resolve("managed-packs.json");
        if (Files.exists(ownership)) {
            Map<String, List<PackEntry>> loaded = MaidTweaks.JSON.fromJson(Files.readString(ownership), new TypeToken<Map<String, List<PackEntry>>>(){}.getType());
            if (loaded == null) throw new IOException("Invalid ownership journal");
            for (var entry : loaded.entrySet()) new PackEntry.Registry(1, entry.getKey(), entry.getValue()).validated();
            managed = new LinkedHashMap<>(loaded);
        }
    }

    public boolean uploadExcluded(String name) { return downloads.containsKey(name) || managed.values().stream().flatMap(List::stream).anyMatch(p -> p.fileName().equals(name)); }
    public Map<String, Long> downloads() { return Map.copyOf(downloads); }
    public boolean downloaded(PackEntry pack) { return java.util.Objects.equals(downloads.get(pack.fileName()), pack.checksum()); }
    public List<PackEntry> owned(String serverId) { return List.copyOf(managed.getOrDefault(serverId, List.of())); }

    public void recordDownload(String name, long checksum) throws IOException {
        Map<String, Long> next = new LinkedHashMap<>(downloads);
        next.put(name, checksum);
        PackFiles.atomicJson(root.resolve("downloaded-via-ui.json"), next);
        downloads = next;
    }

    public void own(String serverId, PackEntry pack) throws IOException {
        Map<String, List<PackEntry>> next = new LinkedHashMap<>(managed);
        List<PackEntry> list = new ArrayList<>(owned(serverId));
        list.removeIf(previous -> previous.id().equals(pack.id()));
        list.add(pack);
        new PackEntry.Registry(1, serverId, list).validated();
        next.put(serverId, list);
        PackFiles.atomicJson(root.resolve("managed-packs.json"), next);
        managed = next;
    }
}
