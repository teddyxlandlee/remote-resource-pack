/*
 * Remote Resource Pack: load resource packs to which contents are downloaded from the internet.
 * Copyright (c) 2026 teddyxlandlee & contributors.
 * SPDX-License-Identifier: MIT
 */
package xland.mcmod.rrp.v3;

import com.google.common.hash.Hashing;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;
import org.jetbrains.annotations.UnmodifiableView;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class ResourceCacheManager implements ResourceCacheAccess {
    private final Path repo;
    private final Map<String, String> etags;
    private boolean isCacheDisabled;

    public ResourceCacheManager(Path repo) {
        this.repo = repo;
        this.etags = readEtags();
    }

    @Contract(pure = true)
    public Path etagsFile() {
        return repo.resolve("etags.bin");
    }

    private static final int ETAGS_FILE_MAGIC = 0x7498d655;

    private Map<String, String> readEtags()  {
        final var ret = new HashMap<String, String>();
        if (!Files.exists(this.etagsFile())) return ret;

        // if any I/O error occurs
        try (var input = new DataInputStream(new BufferedInputStream(Files.newInputStream(this.etagsFile())))) {
            if (input.readInt() != ETAGS_FILE_MAGIC) {
                // File broken
                input.close();
                RemoteResourcePack.LOGGER.error("Etags cache is broken");
                return ret;
            }
            while (true) {
                final String key, value;
                try {
                    key = input.readUTF();
                    value = input.readUTF();
                } catch (EOFException e) {
                    break;
                }
                ret.put(key, value);
            }
        } catch (IOException e) {
            isCacheDisabled = true;
            RemoteResourcePack.LOGGER.error("Error while reading etags cache. Force disabled cache.");
            return new HashMap<>();
        }
        return ret;
    }

    void writeEtags() throws IOException {
        if (isCacheDisabled()) return;
        final Path etagsTemp = this.etagsFile().resolveSibling("etags.tmp");
        Files.createDirectories(etagsTemp.getParent());

        try (var output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(etagsTemp)))) {
            output.writeInt(ETAGS_FILE_MAGIC);
            for (Map.Entry<String, String> entry : this.etags.entrySet()) {
                output.writeUTF(entry.getKey());
                output.writeUTF(entry.getValue());
            }
        }

        Files.move(etagsTemp, this.etagsFile(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public @UnmodifiableView Map<String, String> getEtags() {
        return Collections.unmodifiableMap(etags);
    }

    public @UnknownNullability String putEtag(String uri, String etag) {
        return etags.put(uri, etag);
    }

    // Whether etag caching is completely disabled
    public boolean isCacheDisabled() {
        return isCacheDisabled;
    }

    private Path cachePath(URI uri, String etag) {
        return cachePath(uri.toASCIIString(), etag);
    }

    private Path cachePath(String uri, String etag) {
        final String cacheKey = uri + '\0' + etag;
        final String hash = Hashing.sha256().hashString(cacheKey, StandardCharsets.UTF_8).toString();
        return repo.resolve(RemotePackConfig.slicedSha256(hash).toString()).resolve("cache.bin");
    }

    @Override
    public BufferedInputStream readCache(URI uri, String etag) throws IOException {
        if (this.isCacheDisabled()) throw new FileNotFoundException(uri.toString());
        final Path cachePath = this.cachePath(uri, etag);
        return new BufferedInputStream(Files.newInputStream(cachePath));
    }

    void writeCache(String uri, String etag, InputStream inputStream) throws IOException {
        if (this.isCacheDisabled()) return;

        final Path cachePath = this.cachePath(uri, etag);
        final Path cacheTemp = cachePath.resolveSibling("cache.tmp");
        Files.createDirectories(cacheTemp.getParent());
        Files.copy(inputStream, cacheTemp, StandardCopyOption.REPLACE_EXISTING);
        Files.move(cacheTemp, cachePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private final List<Map.Entry<Path, Iterable<? extends PendingCache>>> pendingCachesPerZipFile = new CopyOnWriteArrayList<>();

    @Override
    public void pushPending(Path zipFile, Iterable<? extends PendingCache> pendingCache) {
        if (isCacheDisabled()) return;
        pendingCachesPerZipFile.add(Map.entry(zipFile, pendingCache));
    }

    List<Map.Entry<Path, Iterable<? extends PendingCache>>> getPendingCachesPerZipFile() {
        return pendingCachesPerZipFile;
    }

    CompletableFuture<Void> writeCachesAsync(Executor executor) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (Map.Entry<Path, Iterable<? extends PendingCache>> entry : getPendingCachesPerZipFile()) {
            final Path zipFilePath = entry.getKey();
            final Iterable<? extends PendingCache> pendingCaches = entry.getValue();
            futures.add(CompletableFuture.runAsync(() -> {
                final var oldEtags = new ArrayList<Path>(64);
                try {
                    try (var zipFile = new ZipFile(RRPCacheRepoSource.getZipFile(zipFilePath))) {
                        for (PendingCache cache : pendingCaches) {
                            if (Thread.currentThread().isInterrupted()) break;

                            final @Nullable ZipEntry zipEntry = zipFile.getEntry(cache.filename());
                            if (zipEntry == null) continue;
                            try (InputStream inputStream = zipFile.getInputStream(zipEntry)) {
                                this.writeCache(cache.uri(), cache.etag(), inputStream);
                                var oldEtag = this.putEtag(cache.uri(), cache.etag());
                                if (oldEtag != null) {
                                    oldEtags.add(this.cachePath(cache.uri(), oldEtag));
                                }
                            }
                        }
                    }
                    for (final Path oldEtag : oldEtags) {
                        Files.deleteIfExists(oldEtag);
                    }
                } catch (IOException e) {
                    RemoteResourcePack.LOGGER.error("Failed to write cache for {}", zipFilePath, e);
                }
            }, executor));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenComposeAsync(ignore -> {
            try {
                this.writeEtags();
                return CompletableFuture.completedStage(null);
            } catch (IOException e) {
                return CompletableFuture.failedStage(e);
            }
        }, executor);
    }
}
