// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.sync;

import io.github.graydients.tlmtweaks.MaidTweaks;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import java.util.zip.ZipFile;

public final class PackFiles {
    private static final class Http {
        private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }
    private PackFiles() {}

    public static ExecutorService worker(String name) {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(256), work -> {
            Thread thread = new Thread(work, name);
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }

    public static byte[] get(URI uri, int limit) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(90))
                .header("User-Agent", "Graydients-Touhou-Little-Maid-Tweaks/" + MaidTweaks.VERSION).GET().build();
        // The future completes after the entire bounded body, so the deadline also
        // covers a peer that sends headers and then stops transmitting bytes.
        var pending = Http.CLIENT.sendAsync(request, info -> new LimitedBody(limit));
        try {
            HttpResponse<byte[]> response = pending.get(90, TimeUnit.SECONDS);
            if (response.statusCode() != 200) throw new IOException("Official pack host returned " + response.statusCode());
            return response.body();
        } catch (java.util.concurrent.TimeoutException timeout) {
            throw new java.net.http.HttpTimeoutException("Official response body exceeded its deadline");
        } catch (java.util.concurrent.ExecutionException failed) {
            if (failed.getCause() instanceof IOException io) throw io;
            throw new IOException("Official response failed", failed.getCause());
        } finally {
            if (!pending.isDone()) pending.cancel(true);
        }
    }

    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public static String hash(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) throw new IOException("Custom pack is a symbolic link");
        if (Files.size(path) > PackEntry.MAX_BYTES) throw new IOException("Custom pack exceeds the size limit");
        final MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        readBounded(path, (bytes, length) -> digest.update(bytes, 0, length));
        return HexFormat.of().formatHex(digest.digest());
    }

    public static long checksum(byte[] bytes) { CRC32 crc = new CRC32(); crc.update(bytes); return crc.getValue(); }

    public static long checksum(Path path) throws IOException {
        CRC32 crc = new CRC32();
        readBounded(path, (bytes, length) -> crc.update(bytes, 0, length));
        return crc.getValue();
    }

    private static void readBounded(Path path, java.util.function.ObjIntConsumer<byte[]> consume) throws IOException {
        if (Files.isSymbolicLink(path)) throw new IOException("Custom pack is a symbolic link");
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        try (InputStream input = Files.newInputStream(path)) {
            int length;
            while ((length = input.read(buffer)) != -1) {
                total += length;
                if (total > PackEntry.MAX_BYTES) throw new IOException("Custom pack exceeds the size limit");
                consume.accept(buffer, length);
            }
        }
    }

    public static Path target(Path folder, String fileName) throws IOException {
        if (!PackEntry.safeFileName(fileName)) throw new IOException("Invalid pack file name");
        Path root = folder.toAbsolutePath().normalize();
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root)) throw new IOException("Custom pack directory is a symbolic link");
        Path target = root.resolve(fileName).normalize();
        if (!target.getParent().equals(root) || Files.isSymbolicLink(target)) throw new IOException("Unsafe custom pack path");
        return target;
    }

    @FunctionalInterface
    public interface Fetcher { byte[] get(URI uri, int limit) throws IOException, InterruptedException; }

    @FunctionalInterface
    public interface Persist { void save(PackEntry pack) throws IOException; }

    public static PackEntry download(PackEntry pack, Path folder, String replaceHash,
                                     java.util.function.BooleanSupplier canCommit) throws IOException, InterruptedException {
        return download(pack, folder, replaceHash, canCommit, PackFiles::get);
    }

    public static PackEntry download(PackEntry pack, Path folder, String replaceHash,
                                     java.util.function.BooleanSupplier canCommit, Fetcher fetcher) throws IOException, InterruptedException {
        return download(pack, folder, replaceHash, canCommit, fetcher, approved -> {});
    }

    /** Commit ownership with the replacement; failed persistence restores the prior bytes. */
    public static PackEntry download(PackEntry pack, Path folder, String replaceHash,
                                     java.util.function.BooleanSupplier canCommit, Fetcher fetcher, Persist persist) throws IOException, InterruptedException {
        pack.validated(false);
        if (Thread.currentThread().isInterrupted() || !canCommit.getAsBoolean()) throw new InterruptedException("Pack sync disabled");
        Path target = target(folder, pack.fileName());
        String beforeHash = null;
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            beforeHash = hash(target);
            if (pack.sha256() != null && beforeHash.equals(pack.sha256())) {
                pack.validated(true);
                if (!canCommit.getAsBoolean()) throw new InterruptedException("Pack sync session ended");
                persist.save(pack);
                return pack;
            }
            if (replaceHash == null || !beforeHash.equals(replaceHash)) throw new IOException("A local custom pack uses " + pack.fileName() + "; left untouched");
        }
        byte[] bytes = fetcher.get(pack.downloadUri(), (int)PackEntry.MAX_BYTES);
        if (bytes.length != pack.bytes() || checksum(bytes) != pack.checksum()) throw new IOException("Official pack checksum or size mismatch");
        String hash = sha256(bytes);
        if (pack.sha256() != null && !pack.sha256().equals(hash)) throw new IOException("Official pack SHA256 differs from the shared version");
        Path temporary = Files.createTempFile(target.getParent(), ".maid-pack-download-", ".tmp");
        Path backup = null;
        boolean moved = false;
        boolean keepBackup = false;
        PackEntry approved = pack.withHash(hash).validated(true);
        try {
            Files.write(temporary, bytes);
            validateZip(temporary);
            if (beforeHash != null) {
                backup = Files.createTempFile(target.getParent(), ".maid-pack-backup-", ".tmp");
                Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
                if (!hash(backup).equals(beforeHash)) throw new IOException("Local pack changed before backup");
            }
            if (Thread.currentThread().isInterrupted() || !canCommit.getAsBoolean()) throw new InterruptedException("Pack sync session ended");
            // Recheck immediately before committing so a manual file change stays protected.
            if (beforeHash == null ? Files.exists(target, LinkOption.NOFOLLOW_LINKS) : !hash(target).equals(beforeHash)) {
                throw new IOException("The local pack changed during download; left untouched");
            }
            if (!canCommit.getAsBoolean()) throw new InterruptedException("Pack sync disabled");
            move(temporary, target);
            moved = true;
            if (!canCommit.getAsBoolean()) throw new InterruptedException("Pack sync session ended");
            persist.save(approved);
        } catch (IOException | InterruptedException | RuntimeException error) {
            try {
                if (moved && Files.exists(target) && hash(target).equals(hash)) {
                    if (backup != null) move(backup, target);
                    else Files.delete(target);
                }
            } catch (IOException rollback) { keepBackup = true; error.addSuppressed(rollback); }
            throw error;
        } finally {
            Files.deleteIfExists(temporary);
            if (backup != null && !keepBackup) Files.deleteIfExists(backup);
        }
        return approved;
    }

    public static void validateZip(Path path) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            boolean metadata = false;
            long uncompressed = 0;
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith("/") || name.contains("\\") || name.contains(":")
                        || java.util.Arrays.asList(name.split("/")).contains("..")) throw new IOException("Unsafe ZIP entry");
                if (entry.getSize() < 0) throw new IOException("Unknown ZIP entry size");
                uncompressed += entry.getSize();
                if (uncompressed > 512L * 1024 * 1024) throw new IOException("Expanded pack exceeds the size limit");
                if (name.matches("assets/[^/]+/(maid_model|chair_model|maid_sound)\\.json")) metadata = true;
            }
            if (!metadata) throw new IOException("ZIP has no Touhou maid pack metadata");
        }
    }

    public static void atomicJson(Path file, Object value) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        if (Files.isSymbolicLink(file) || Files.isSymbolicLink(parent)) throw new IOException("Unsafe registry path");
        Path tmp = Files.createTempFile(parent, ".maid-registry-", ".tmp");
        try { Files.writeString(tmp, MaidTweaks.JSON.toJson(value) + "\n", StandardCharsets.UTF_8); move(tmp, file); }
        finally { Files.deleteIfExists(tmp); }
    }

    private static void move(Path from, Path to) throws IOException {
        try { Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING); }
    }

    public static void removeOwned(PackEntry previous, Path folder) throws IOException {
        Path path = target(folder, previous.fileName());
        if (Files.exists(path) && hash(path).equals(previous.sha256())) Files.delete(path);
    }
}
