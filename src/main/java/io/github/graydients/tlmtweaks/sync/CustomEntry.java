// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.sync;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Upload identity is the authenticated uploader plus original filename, never a display name. */
public record CustomEntry(String id,String name,String sha256,long bytes) {
    public static final int CHUNK=16*1024, MAX_ENTRIES=256;
    public CustomEntry validated() {
        UUID.fromString(id);
        if(!PackEntry.safeFileName(name) || sha256==null || !sha256.matches("[0-9a-f]{64}") || bytes<=0 || bytes>PackEntry.MAX_BYTES)throw new IllegalArgumentException("Invalid custom pack entry");
        return this;
    }
    public static String identity(UUID uploader,String name) {return UUID.nameUUIDFromBytes((uploader+"\0"+name).getBytes(StandardCharsets.UTF_8)).toString();}
    public String fileName(){return "graydients-tlm-custom-"+id+".zip";}
    public record Manifest(String serverId,List<CustomEntry> packs) {
        public Manifest validated() {UUID.fromString(serverId);if(packs==null || packs.size()>MAX_ENTRIES)throw new IllegalArgumentException("Invalid custom manifest");var ids=new java.util.HashSet<String>();for(var pack:packs){pack.validated();if(!ids.add(pack.id))throw new IllegalArgumentException("Duplicate custom identity");}return this;}
    }
}
