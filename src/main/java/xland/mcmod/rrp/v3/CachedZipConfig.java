/*
 * Remote Resource Pack: load resource packs to which contents are downloaded from the internet.
 * Copyright (c) 2026 teddyxlandlee & contributors.
 * SPDX-License-Identifier: MIT
 */
package xland.mcmod.rrp.v3;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

public record CachedZipConfig(FileMap staticFiles, Map<String, DynamicArg> dynamic) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public record FileMap(Map<String, FileEntry> map) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        public void forEach(BiConsumer<? super String, ? super FileEntry> consumer) {
            map.forEach(consumer);
        }

        public static FileMap fromJson(JsonObject obj) {
            final var map = new LinkedHashMap<String, FileEntry>();
            obj.asMap().forEach((key, value) -> {
                final JsonObject fileEntryJson = value.getAsJsonObject();
                if (shouldSkip(fileEntryJson)) return;  // do not parse/serialize this

                final FileEntry fileEntry = FileEntry.fromJson(fileEntryJson);
                map.put(key, fileEntry);
            });
            return new FileMap(map);
        }

        private static boolean shouldSkip(JsonObject data) {
            JsonElement e = data.get("skip_on");
            if (e == null) return false;    // non-exist
            if (e.isJsonPrimitive()) {
                return ZipConfigDownload.SKIP_KEY.equalsIgnoreCase(e.getAsString());
            }
            if (e.isJsonArray()) {
                for (JsonElement arrayElement : e.getAsJsonArray()) {
                    if (!arrayElement.isJsonPrimitive()) continue;
                    if (ZipConfigDownload.SKIP_KEY.equalsIgnoreCase(arrayElement.getAsString())) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    public record DynamicArg(int defaultIndex, List<DynamicItem> items) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private static boolean isRandom(int index) {
            return index < 0;
        }

        public void select(@Nullable Integer providedIndex, RandomGenerator random, Consumer<? super FileMap> consumer) {
            int index = providedIndex != null ? providedIndex : defaultIndex;
            if (isRandom(index)) index = randomIndex(random, items);

            if (index >= 0 && index < items.size()) {
                consumer.accept(items.get(index).files());
            }
            // else: do nothing (parity with JS/TS)
        }

        private static int randomIndex(RandomGenerator random, List<? extends DynamicItem> items) {
            if (items.isEmpty()) {
                return -1;
            }
            long totalWeight = 0;
            for (var item : items) {
                totalWeight += item.weight();
            }
            double remaining = random.nextDouble() * totalWeight;
            for (int i = 0; i < items.size(); i++) {
                remaining -= items.get(i).weight();
                if (remaining < 0) {
                    return i;
                }
            }
            return items.size() - 1;
        }

        public static DynamicArg fromJson(JsonObject obj) {
            checkExistence(obj, "default");
            checkExistence(obj, "items");

            final JsonPrimitive defaultPrimitive = obj.get("default").getAsJsonPrimitive();
            final int defaultIndex = "random".equals(defaultPrimitive.getAsString()) ? -1 : defaultPrimitive.getAsInt();
            // make it mutable deliberately
            final List<DynamicItem> items = obj.get("items").getAsJsonArray()
                    .asList()
                    .stream()
                    .map(e -> DynamicItem.fromJson(e.getAsJsonObject()))
                    .collect(Collectors.toList());
            return new DynamicArg(defaultIndex, items);
        }
    }

    public record DynamicItem(int weight, FileMap files) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        public static final int DEFAULT_WEIGHT = 100;

        public static DynamicItem fromJson(JsonObject obj) {
            checkExistence(obj, "files");

            int weight = DEFAULT_WEIGHT;
            if (obj.has("weight")) {
                weight = obj.getAsJsonPrimitive("weight").getAsInt();
            }

            FileMap fileMap = FileMap.fromJson(obj.get("files").getAsJsonObject());
            return new DynamicItem(weight, fileMap);
        }
    }

    public static CachedZipConfig fromJson(JsonObject obj) {
        checkExistence(obj, "static");
        checkExistence(obj, "dynamic");

        final FileMap staticFiles = FileMap.fromJson(obj.get("static").getAsJsonObject());
        final Map<String, DynamicArg> dynamic = new LinkedHashMap<>();
        obj.get("dynamic").getAsJsonObject().asMap().forEach((key, argValue) -> {
            final DynamicArg dynamicArg = DynamicArg.fromJson(argValue.getAsJsonObject());
            dynamic.put(key, dynamicArg);
        });
        return new CachedZipConfig(staticFiles, dynamic);
    }

    private static void checkExistence(JsonObject obj, String key) {
        if (!obj.has(key)) {
            throw new IllegalArgumentException("'" + key + "' not present in " + obj);
        }
    }

    public void writeTo(ObjectOutput output) throws IOException {
        output.writeObject(this);
    }

    public static CachedZipConfig readFrom(ObjectInput input) throws IOException {
        try {
            return (CachedZipConfig) input.readObject();
        } catch (ClassNotFoundException | ClassCastException e) {
            throw new IOException("Malformed data", e);
        }
    }

    public sealed interface FileEntry extends Serializable {
        CompletableFuture<byte[]> fetch(FetchContext context);

        static FileEntry raw(String utf8) {
            return new LocalFileEntry(utf8.getBytes(StandardCharsets.UTF_8));
        }

        static FileEntry base64(String base64) throws IllegalArgumentException {
            return new LocalFileEntry(LocalFileEntry.BASE64_DECODER.decode(base64));
        }

        static FileEntry fetch(String uri) throws IllegalArgumentException {
            return new RemoteFileEntry(URI.create(uri));
        }

        static FileEntry empty() {
            return new LocalFileEntry(new byte[0]);
        }

        static FileEntry fromJson(JsonObject obj) {
            if (obj.get("fetch") instanceof JsonPrimitive f) {
                return fetch(f.getAsString());
            } else if (obj.get("base64") instanceof JsonPrimitive f) {
                return base64(f.getAsString());
            } else if (obj.get("raw") instanceof JsonPrimitive f) {
                return raw(f.getAsString());
            } else {
                return empty();
            }
        }
    }

    private record LocalFileEntry(byte[] bytes) implements FileEntry {
        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        @Override
        public CompletableFuture<byte[]> fetch(FetchContext context) {
            return CompletableFuture.completedFuture(this.bytes());
        }

        private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();
    }

    private record RemoteFileEntry(URI uri) implements FileEntry {
        @Override
        public CompletableFuture<byte[]> fetch(FetchContext context) {
            // TODO: component cache based on etag
            //noinspection resource
            return context.httpClient().sendAsync(
                    HttpRequest.newBuilder(context.baseUri().resolve(uri)).GET().header("User-Agent", context.userAgent()).build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            ).thenCompose(httpResponse -> {
                if (ZipConfigDownload.isStatusOk(httpResponse.statusCode()))
                    return CompletableFuture.completedStage(httpResponse.body());
                return CompletableFuture.failedStage(new IOException(
                        "Response " + uri + " responds " + httpResponse.statusCode()
                ));
            });
        }
    }

    @ApiStatus.Experimental
    public interface FetchContext {
        HttpClient httpClient();
        URI baseUri();
        String userAgent();
    }

    record FetchContextImpl(HttpClient httpClient, URI baseUri, Supplier<String> userAgentSupplier) implements FetchContext {
        @Override
        public String userAgent() {
            return this.userAgentSupplier().get();
        }
    }
}
