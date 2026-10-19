// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.sync;

import io.github.graydients.tlmtweaks.MaidTweaks;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** History includes unavailable packs; only verified files enter the published manifest. */
public final class ServerJournal {
    private final Path file;
    private final String serverId;
    private Map<String, PackEntry> history = new LinkedHashMap<>();
    private final Map<String, PackEntry> verified = new LinkedHashMap<>();

    public ServerJournal(Path configRoot, Path folder) throws IOException {
        file = configRoot.resolve(MaidTweaks.ID).resolve("shared-packs.json");
        boolean exists = Files.exists(file);
        PackEntry.Registry registry = exists
                ? MaidTweaks.JSON.fromJson(Files.readString(file), PackEntry.Registry.class).validated()
                : new PackEntry.Registry(1, UUID.randomUUID().toString(), List.of());
        serverId = registry.serverId();
        for (PackEntry pack : registry.packs()) {
            history.put(pack.id(), pack);
            Path target = PackFiles.target(folder, pack.fileName());
            if (Files.exists(target) && PackFiles.hash(target).equals(pack.sha256())) verified.put(pack.id(), pack);
        }
        if (!exists) PackFiles.atomicJson(file, registry);
    }

    public PackEntry previous(String id) { return history.get(id); }
    public int size() { return history.size(); }
    public boolean verified(PackEntry pack) { return pack.equals(verified.get(pack.id())); }
    public PackEntry.Registry manifest() { return new PackEntry.Registry(1, serverId, List.copyOf(verified.values())).validated(); }

    public void publish(PackEntry pack) throws IOException {
        Map<String, PackEntry> next = new LinkedHashMap<>(history);
        next.put(pack.id(), pack);
        PackEntry.Registry registry = new PackEntry.Registry(1, serverId, new ArrayList<>(next.values())).validated();
        PackFiles.atomicJson(file, registry);
        history = next;
        verified.put(pack.id(), pack);
    }
}
