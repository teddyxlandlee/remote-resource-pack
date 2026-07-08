/*
 * Remote Resource Pack: load resource packs to which contents are downloaded from the internet.
 * Copyright (c) 2026 teddyxlandlee & contributors.
 * SPDX-License-Identifier: MIT
 */
package xland.mcmod.rrp.v3;

import org.jetbrains.annotations.Contract;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public record PackRepoItem(Path repo, RemotePackConfig config) {
    @Contract(pure = true)
    public Path zipCache() {
        return this.pathWithSuffix(".zip");
    }

    @Contract(pure = true)
    public Path zipTimestamp() {
        return this.pathWithSuffix(".timestamp");
    }

    @Contract(pure = true)
    public Path zipConfigCache() {
        return this.pathWithSuffix(".zipconfig.cache");
    }

    @Contract(pure = true)
    public Path zipConfigEtag() {
        return this.pathWithSuffix(".zipconfig.etag");
    }

    private Path pathWithSuffix(String suffix) {
        return repo.resolve(config.getSlicedHash().append(suffix).toString());
    }

    public boolean exists() {
        return Files.exists(zipCache());
    }

    public boolean isOutdated() {
        if (!exists()) return true;
        if (config.isAlwaysUpToDate()) return false;

        final Path timestamp = zipTimestamp();
        if (Files.notExists(timestamp)) return true;
        try (DataInputStream input = new DataInputStream(Files.newInputStream(timestamp))) {
            Instant instant = RemotePackConfig.readInstant(input);
            return instant.plus(config.autoUpdate()).isBefore(Instant.now());
        } catch (Exception e) {
            RemoteResourcePack.LOGGER.error("Can't read timestamp file {}", timestamp, e);
            return true;
        }
    }

    public void generate() throws IOException, CompletionException {
        if (!isOutdated()) return;
        final Path zipCache = zipCache();
        Files.createDirectories(zipCache.getParent());
        ZipConfigDownload.generateZip(this);

        dumpTimestamp();
    }

    Optional<String> getZipConfigEtag() {
        final Path zipConfigEtag = zipConfigEtag();
        if (!Files.exists(zipConfigEtag)) return Optional.empty();
        try {
            return Optional.of(Files.readString(zipConfigEtag));
        } catch (IOException e) {
            RemoteResourcePack.LOGGER.warn("Failed to get etag for {}", this, e);
            return Optional.empty();
        }
    }

    void dumpZipConfigETag(String etag) {
        try {
            Files.writeString(zipConfigEtag(), etag);
        } catch (IOException e) {
            RemoteResourcePack.LOGGER.warn("Failed to dump etag for {}", this, e);
            // silent ignore
        }
    }

    CachedZipConfig loadZipConfig() throws IOException {
        try (var input = new ObjectInputStream(new GZIPInputStream(Files.newInputStream(this.zipConfigCache())))) {
            return CachedZipConfig.readFrom(input);
        }
    }

    void dumpZipConfig(CachedZipConfig cachedZipConfig) {
        try (var output = new ObjectOutputStream(new GZIPOutputStream(Files.newOutputStream(this.zipConfigCache())))) {
            cachedZipConfig.writeTo(output);
        } catch (IOException e) {
            RemoteResourcePack.LOGGER.warn("Failed to dump zip config cache", e);
        }
    }

    private void dumpTimestamp() throws IOException {
        final Path timestamp = zipTimestamp();
        try (var output = new DataOutputStream(Files.newOutputStream(timestamp))) {
            RemotePackConfig.writeInstant(output, Instant.now());
        }
    }
}
