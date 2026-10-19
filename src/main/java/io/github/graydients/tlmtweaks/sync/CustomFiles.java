// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.sync;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.zip.ZipFile;

/** Archive inspection is data-only: animation scripts are never evaluated here. */
public final class CustomFiles {
    private CustomFiles(){}
    public static void validate(Path path,CustomEntry entry)throws IOException {
        entry.validated();if(Files.size(path)!=entry.bytes() || !PackFiles.hash(path).equals(entry.sha256()))throw new IOException("Custom pack size/hash mismatch");
        long expanded=0;int count=0;boolean metadata=false;var names=new HashSet<String>();
        byte[] buffer=new byte[8192];
        try(var zip=new ZipFile(path.toFile())) {
            var entries=zip.entries();
            while(entries.hasMoreElements()) {
                var e=entries.nextElement();String name=e.getName();
                if(++count>10000 || !names.add(name) || name.startsWith("/") || name.contains("\\") || name.contains(":") || java.util.Arrays.asList(name.split("/")).contains(".."))throw new IOException("Unsafe custom archive");
                if(e.isDirectory())continue;
                boolean maid=name.matches("assets/[^/]+/(maid_model|chair_model|maid_sound)\\.json");
                java.io.ByteArrayOutputStream json=maid?new java.io.ByteArrayOutputStream():null;
                try(InputStream in=zip.getInputStream(e)) {
                    int size;while((size=in.read(buffer))!=-1) {expanded+=size;if(expanded>512L*1024*1024)throw new IOException("Expanded custom pack exceeds limit");
                        if(json!=null){if(json.size()+size>1024*1024)throw new IOException("Maid metadata exceeds limit");json.write(buffer,0,size);}}
                }
                if(json!=null) {
                    try {
                        var parsed=JsonParser.parseString(json.toString(StandardCharsets.UTF_8));
                        if(!parsed.isJsonObject())throw new IOException("Invalid maid metadata");
                        var object=parsed.getAsJsonObject();
                        if(!object.has("pack_name") || !object.get("pack_name").isJsonPrimitive() || !object.getAsJsonPrimitive("pack_name").isString())throw new IOException("Missing maid pack name");
                        if(!name.endsWith("maid_sound.json") && (!object.has("model_list") || !object.get("model_list").isJsonArray()))throw new IOException("Missing maid model list");
                        metadata=true;
                    }
                    catch(RuntimeException invalid){throw new IOException("Invalid maid metadata",invalid);}
                }
            }
        }
        if(!metadata)throw new IOException("ZIP is not a Touhou maid resource pack");
    }
    public static byte[] chunk(Path path,int offset,long length)throws IOException {
        if(offset<0 || offset>=length || Files.isSymbolicLink(path) || Files.size(path)!=length)throw new IOException("Invalid chunk request");
        int size = (int) Math.min(CustomEntry.CHUNK, length - offset);
        var bytes = java.nio.ByteBuffer.allocate(size);
        try (var channel = Files.newByteChannel(path)) {
            channel.position(offset);
            while (bytes.hasRemaining()) if (channel.read(bytes) < 0) throw new IOException("Pack changed during chunk read");
        }
        return bytes.array();
    }
    public static void move(Path from,Path to)throws IOException {
        try{Files.move(from,to,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException noAtomic){Files.move(from,to,StandardCopyOption.REPLACE_EXISTING);}
    }
    @FunctionalInterface public interface Persist {void save()throws IOException;}
    /** Replace only owned bytes; roll back our write if the ownership journal cannot commit. */
    public static void install(CustomEntry entry,CustomEntry previous,Path source,Path folder,java.util.function.BooleanSupplier allowed,Persist persist)throws IOException {
        Path target=PackFiles.target(folder,entry.fileName());boolean existed=Files.exists(target);String before=existed?PackFiles.hash(target):null;
        if(existed && (previous==null || !previous.sha256().equals(before)))throw new IOException("Local custom pack was preserved: ownership/hash conflict");
        Path replacement=Files.createTempFile(folder,".custom-replace-",".tmp"),backup=existed?Files.createTempFile(folder,".custom-backup-",".tmp"):null;
        boolean moved=false;
        try {
            Files.copy(source,replacement,StandardCopyOption.REPLACE_EXISTING);if(backup!=null)Files.copy(target,backup,StandardCopyOption.REPLACE_EXISTING);
            if(!allowed.getAsBoolean())throw new IOException("Custom sync session ended");
            if(existed?!PackFiles.hash(target).equals(before):Files.exists(target))throw new IOException("Local custom pack changed during transfer");
            move(replacement,target);moved=true;if(!allowed.getAsBoolean())throw new IOException("Custom sync session ended");persist.save();
        }catch(IOException error){
            if(moved && Files.exists(target) && PackFiles.hash(target).equals(entry.sha256())) {if(backup!=null)move(backup,target);else Files.delete(target);}
            throw error;
        }finally{Files.deleteIfExists(replacement);if(backup!=null)Files.deleteIfExists(backup);}
    }
    public static final class Incoming implements AutoCloseable {
        public final CustomEntry entry;
        public final Path file;
        public final long generation;
        public int offset;
        public long touched=System.currentTimeMillis();
        public Incoming(CustomEntry entry,Path folder,long generation)throws IOException {
            this.entry=entry.validated();this.generation=generation;Files.createDirectories(folder);file=Files.createTempFile(folder,"incoming-",".tmp");
        }
        public boolean append(CustomProtocol.Chunk packet)throws IOException {
            if(packet.offset()!=offset || packet.bytes().length==0 || packet.bytes().length>CustomEntry.CHUNK || (long)offset+packet.bytes().length>entry.bytes())throw new IOException("Invalid transfer offset/length");
            Files.write(file,packet.bytes(),java.nio.file.StandardOpenOption.APPEND);offset+=packet.bytes().length;touched=System.currentTimeMillis();return offset==entry.bytes();
        }
        public void close()throws IOException {Files.deleteIfExists(file);}
    }
}
