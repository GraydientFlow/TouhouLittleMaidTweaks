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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CustomOwnership {
    private final Path file;
    private Map<String,List<CustomEntry>> entries=new LinkedHashMap<>();
    public CustomOwnership(Path root)throws IOException {
        file=root.resolve("managed-custom-packs.json");if(!Files.exists(file))return;
        var loaded=MaidTweaks.JSON.<Map<String,List<CustomEntry>>>fromJson(Files.readString(file),new TypeToken<Map<String,List<CustomEntry>>>(){}.getType());
        if(loaded==null)throw new IOException("Invalid custom ownership journal");
        for(var entry:loaded.entrySet())new CustomEntry.Manifest(entry.getKey(),entry.getValue()).validated();entries=new LinkedHashMap<>(loaded);
    }
    public CustomEntry previous(String server,String id){return entries.getOrDefault(server,List.of()).stream().filter(p->p.id().equals(id)).findFirst().orElse(null);}
    public boolean excluded(String name){return entries.values().stream().flatMap(List::stream).anyMatch(p->p.fileName().equals(name));}
    public void own(String server,CustomEntry pack)throws IOException {
        UUID.fromString(server);pack.validated();var next=new LinkedHashMap<>(entries);var list=new java.util.ArrayList<>(entries.getOrDefault(server,List.of()));
        list.removeIf(p->p.id().equals(pack.id()));list.add(pack);next.put(server,list);PackFiles.atomicJson(file,next);entries=next;
    }
}
