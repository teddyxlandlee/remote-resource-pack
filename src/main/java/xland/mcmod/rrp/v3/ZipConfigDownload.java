/*
 * Remote Resource Pack: load resource packs to which contents are downloaded from the internet.
 * Copyright (c) 2023-2026 teddyxlandlee & contributors.
 * SPDX-License-Identifier: MIT
 */
package xland.mcmod.rrp.v3;

import com.google.common.base.Suppliers;
import com.google.gson.*;
import net.minecraft.client.ClientBrandRetriever;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class ZipConfigDownload implements Closeable {
    //? if java: >=25 {
    private static final ScopedValue<RandomGenerator> RANDOM = ScopedValue.newInstance();
    //?} else {
    /*private static final ThreadLocal<RandomGenerator> RANDOM = new ThreadLocal<>();
    *///?}
    static final String SKIP_KEY = "mod";
    private static final String PACK_MCMETA = "pack.mcmeta";

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Gson GSON = new Gson();    // for zipConfig parsing

    private ZipConfigDownload(ZipOutputStream zos, URI baseUri) {
        this.zos = zos;

        this.zipOutputWorker = Executors.newSingleThreadExecutor();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .executor(/*? if java: >= 21 {*/Executors.newVirtualThreadPerTaskExecutor()/*?} else {*//*LegacyExecutorCloser.cachedThreadPool()*//*?}*/)
                .build();
        this.futures = new CopyOnWriteArrayList<>();

        this.fetchContext = new CachedZipConfig.FetchContextImpl(httpClient, baseUri, USER_AGENT);
    }

    @Override
    public void close() throws IOException {
        //? if java: >= 21 {
        this.zipOutputWorker.close();
        this.httpClient.close();
        //?} else {
        /*LegacyExecutorCloser.close(this.zipOutputWorker);
        LegacyExecutorCloser.close(this.httpClient);
        *///?}
        this.futures.clear();
        this.zos.close();
    }

    private final ZipOutputStream zos;
    private final ExecutorService zipOutputWorker;
    private final HttpClient httpClient;
    private final List<CompletableFuture<?>> futures;

    private final transient CachedZipConfig.FetchContextImpl fetchContext;

    private static final Supplier<String> USER_AGENT = Suppliers.memoize(() ->
            "RemoteResourcePack/" + RemoteResourcePack.platform().modVersion()
                    + " MC/" + RemoteResourcePack.platform().minecraftVersion()
                    + " (Platform:" + ClientBrandRetriever.getClientModName() + ")"
    );

    static boolean isStatusOk(int statusCode) {
        return statusCode >= 200 && statusCode <= 299;
    }

    private void addFileToZip(String filename, CachedZipConfig.FileEntry fileEntry) {
        final ZipEntry zipEntry = new ZipEntry(filename);
        final CompletableFuture<Void> putEntryFuture;

        if (!zipEntry.isDirectory()) {
            CompletableFuture<byte[]> fetchBytesFuture = fileEntry.fetch(this.fetchContext);

            if (PACK_MCMETA.equals(filename)) {
                // Probably the pack version requires a fix
                fetchBytesFuture = fetchBytesFuture.thenApply(RRPCacheRepoSource::modifyPackMcmeta);
            }

            putEntryFuture = fetchBytesFuture.thenComposeAsync(bytes -> {
                try {
                    zos.putNextEntry(zipEntry);
                    zos.write(bytes);
                    zos.closeEntry();
                    return CompletableFuture.completedStage(null);
                } catch (IOException e) {
                    return CompletableFuture.failedStage(e);
                }
            }, zipOutputWorker);
        } else {
            putEntryFuture = CompletableFuture.completedFuture(null).thenComposeAsync(ignore -> {
                try {
                    zos.putNextEntry(zipEntry);
                    zos.closeEntry();
                    return CompletableFuture.completedStage(null);
                } catch (IOException e) {
                    return CompletableFuture.failedStage(e);
                }
            }, zipOutputWorker);
        }
        this.futures.add(putEntryFuture);
    }

    private void addFilesToZip(CachedZipConfig.FileMap files) {
        files.forEach(this::addFileToZip);
    }

    private void joinFutures() throws CompletionException {
        joinAllFutures(this.futures);
    }

    static void joinAllFutures(Collection<? extends CompletableFuture<?>> futures) throws CompletionException {
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    static void generateZip(PackRepoItem item)
            throws IOException, CompletionException {
        final RandomGenerator rng = new Random();
        //? if java: >= 25 {
        ScopedValue.where(RANDOM, rng).call(() -> {
            internalGenerateZip(item);
            return null;
        });
        //?} else {
        /*try {
            RANDOM.set(rng);
            internalGenerateZip(item);
        } finally {
            RANDOM.remove();    // gc
        }
        *///?}
    }

    private static void internalGenerateZip(PackRepoItem item)
            throws IOException, CompletionException {
        final ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(item.zipCache()));
        try (final ZipConfigDownload engine = new ZipConfigDownload(zos, item.config().baseUri())) {
            final CachedZipConfig zipConfig = engine.getZipConfig(item);
            final Map<String, String> args = item.config().args();

            engine.addFilesToZip(zipConfig.staticFiles());
            zipConfig.dynamic().forEach((paramKey, dynamicArg) -> {
                @Nullable String paramString = args.get(paramKey);
                @Nullable Integer paramInt;
                if (paramString == null) {
                    paramInt = null;
                } else if ("random".equals(paramString)) {
                    paramInt = -1;
                } else {
                    try {
                        paramInt = Integer.parseUnsignedInt(paramString);
                    } catch (NumberFormatException e) {
                        paramInt = null;    // equivalent to `isNaN(parseInt(x))` in JS/TS
                    }
                }
                dynamicArg.select(paramInt, RANDOM.get(), engine::addFilesToZip);
            });
            engine.joinFutures();
        }
    }

    private CachedZipConfig getZipConfig(PackRepoItem item) throws IOException {
        final URI uri = item.config().zipConfigUri();
        final HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                .GET()
                .header("User-Agent", USER_AGENT.get());
        item.getZipConfigEtag().ifPresent(etag -> {
            if (Files.exists(item.zipConfigCache())) requestBuilder.header("If-None-Match", etag);
        });
        HttpResponse<InputStream> response;
        try {
            response = this.httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interruption while fetching response from " + uri, e);
        }

        if (response.statusCode() == 304) {     // Not Modified
            RemoteResourcePack.LOGGER.debug("Etag matches. Loading serial cache.");
            response.body().close();
            return item.loadZipConfig();
        } else if (isStatusOk(response.statusCode())) {
            RemoteResourcePack.LOGGER.debug("Cache miss. Rebuilding cache.");
            // cache etag
            response.headers().firstValue("Etag").ifPresent(item::dumpZipConfigETag);

            // load body
            final JsonObject data;
            try (var reader = new BufferedReader(new InputStreamReader(response.body()))) {
                data = GSON.fromJson(reader, JsonObject.class);
            } catch (JsonParseException e) {
                throw new IOException("Malformed JSON", e);
            }
            final CachedZipConfig config = CachedZipConfig.fromJson(data);
            item.dumpZipConfig(config);
            return config;
        } else {
            response.body().close();
            throw new IOException("Resource " + uri + " responds " + response.statusCode());
        }
    }

}
