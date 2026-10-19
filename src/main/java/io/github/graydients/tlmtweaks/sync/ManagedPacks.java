// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.sync;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;

public final class ManagedPacks {
    private ManagedPacks() {}

    public static boolean install(PackEntry pack, String serverId, ClientJournal journal,
                                  Path folder, BooleanSupplier current, PackFiles.Fetcher fetcher)
            throws IOException, InterruptedException {
        if (!current.getAsBoolean()) throw new InterruptedException("Pack sync disabled or connection changed");
        PackEntry previous = journal.owned(serverId).stream().filter(p -> p.id().equals(pack.id())).findFirst().orElse(null);
        Path target = PackFiles.target(folder, pack.fileName());
        if (previous != null && !previous.fileName().equals(pack.fileName())) {
            Path old = PackFiles.target(folder, previous.fileName());
            if (Files.exists(old) && !PackFiles.hash(old).equals(previous.sha256()))
                throw new IOException("Previous local pack was manually changed; left untouched");
        }
        boolean exact = Files.exists(target) && PackFiles.hash(target).equals(pack.sha256());
        if (exact && previous == null) {
            if (journal.downloaded(pack) && current.getAsBoolean()) journal.own(serverId, pack);
            return false;
        }
        if (exact && pack.equals(previous)) return false;
        String replacement = previous != null && previous.fileName().equals(pack.fileName()) ? previous.sha256() : null;
        // Persist the new ownership before retiring an old filename.
        PackFiles.download(pack, folder, replacement, current, fetcher, approved -> journal.own(serverId, approved));
        if (previous != null && !previous.fileName().equals(pack.fileName())) PackFiles.removeOwned(previous, folder);
        return true;
    }
}
