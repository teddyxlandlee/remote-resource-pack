/*
 * Remote Resource Pack: load resource packs to which contents are downloaded from the internet.
 * Copyright (c) 2023-2026 teddyxlandlee & contributors.
 * SPDX-License-Identifier: MIT
 */
package xland.mcmod.rrp.v3;

import com.google.common.hash.Hashing;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URI;
import java.nio.file.Path;
import java.text.StringCharacterIterator;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletionException;

public final class RemotePackConfig implements java.io.Serializable {
    private final URI baseUri;
    private final URI zipConfigUri;
    private final Duration autoUpdate;
    final Map<String, String> args;
    private static final byte schemaVersion = 1;
    private transient final String hash;

    private RemotePackConfig(URI baseUri, URI zipConfigUri, Duration autoUpdate, Map<String, String> args) {
        this.baseUri = baseUri;
        this.zipConfigUri = zipConfigUri;
        this.autoUpdate = autoUpdate;
        this.args = args;

        this.hash = internalCalcSha256();
    }

    public Path generate(Path repo, ResourceCacheAccess cacheManager) throws IOException, CompletionException {
        PackRepoItem item = new PackRepoItem(repo, this);
        item.generate(cacheManager);
        return item.zipCache();
    }

    public boolean isAlwaysUpToDate() {
        return isAlwaysUpToDate(this.autoUpdate);
    }

    private static boolean isAlwaysUpToDate(Duration duration) {
        return duration.isNegative();
    }

    public String getHash() {
        return hash;
    }

    StringBuilder getSlicedHash() {
        return slicedSha256(this.hash);
    }

    static StringBuilder slicedSha256(String hash) {
        StringBuilder sb = new StringBuilder();
        sb.append(hash, 0, 2).append('/');
        sb.append(hash, 2, 32).append('/');
        sb.append(hash, 33, 64);
        return sb;
    }

    private static RemotePackConfig ofInternal(URI baseUri, URI zipConfigUri, Duration autoUpdate, Map<String, String> args) {
        autoUpdate = canonicalizeDuration(autoUpdate);
        return new RemotePackConfig(baseUri, zipConfigUri, autoUpdate, args);
    }

    @SuppressWarnings("unused")
    public static RemotePackConfig of(URI baseUri, URI zipConfigUri, Duration autoUpdate, Map<String, String> args) {
        return ofInternal(baseUri, zipConfigUri, autoUpdate, Map.copyOf(args));
    }

    public static RemotePackConfig readFromJson(JsonObject obj) throws JsonParseException {
        if (!(obj.get("schema") instanceof JsonPrimitive primitive) || !primitive.isNumber() || primitive.getAsLong() != schemaVersion) {
            // Includes the condition where 'schema' is absent
            throw new JsonParseException(schemaMismatch(obj.get("schema")));
        }

        final URI baseUri, zipConfigUri;
        try {
            @Nullable JsonElement baseUriElement = obj.get("base"), zipConfigElement = obj.get("zipconfig");
            if (!(baseUriElement instanceof JsonPrimitive p1) || !(zipConfigElement instanceof JsonPrimitive p2) || !p1.isString() || !p2.isString()) {
                throw new JsonParseException("'base' and 'zipconfig' must be string");
            }
            baseUri = URI.create(baseUriElement.getAsString());
            zipConfigUri = URI.create(zipConfigElement.getAsString());
        } catch (IllegalArgumentException e) {
            throw new JsonParseException("Unresolvable URI", e);
        }
        @Nullable JsonElement autoUpdateElement = obj.get("autoUpdate");
        final String autoUpdateExpr;
        if (autoUpdateElement == null) {
            autoUpdateExpr = "2d";
        } else if (!(autoUpdateElement.isJsonPrimitive())) {
            throw new JsonParseException("'autoUpdate' must be string");
        } else {
            autoUpdateExpr = autoUpdateElement.getAsString();
        }
        Duration autoUpdate = switch (autoUpdateExpr) {
            case "always", "0" -> Duration.ZERO;
            case "never", "-1" -> Duration.ofSeconds(-1);
            default -> durationFromString(autoUpdateExpr);
        };
        @Nullable JsonElement argsElement = obj.get("args");
        final JsonObject argsObj;
        if (argsElement == null) {
            argsObj = new JsonObject();
        } else if (argsElement.isJsonObject()) {
            argsObj = argsElement.getAsJsonObject();
        } else {
            throw new JsonParseException("'args' must be an object");
        }

        final Map<String, String> args = new LinkedHashMap<>();
        argsObj.entrySet().forEach(e -> {
            if (!e.getValue().isJsonPrimitive()) {
                throw new JsonParseException(String.format(
                        "Expect argument %s to be primitive, got %s",
                        e.getKey(), e.getValue()));
            }
            args.put(e.getKey(), e.getValue().getAsString());
        });

        return ofInternal(baseUri, zipConfigUri, autoUpdate, args);
    }

    private static IOException schemaMismatch(@Nullable Object b) {
        return new java.io.InvalidObjectException(String.format(
                "Invalid schema version: expected %d, got %s",
                schemaVersion, b
        ));
    }

    private static Duration canonicalizeDuration(Duration old) {
        if (old.isNegative()) return Duration.ofSeconds(-1L);
        return old;
    }

    private static void writeDuration(DataOutput output, Duration duration) throws IOException {
        output.writeLong(duration.getSeconds());
        output.writeInt(duration.getNano());
    }

    static Instant readInstant(DataInput input) throws IOException {
        final long sec = input.readLong();
        final int nanos = input.readInt();
        return Instant.ofEpochSecond(sec, nanos);
    }

    static void writeInstant(DataOutput output, Instant instant) throws IOException {
        output.writeLong(instant.getEpochSecond());
        output.writeInt(instant.getNano());
    }

    public void dumpsToBinary(DataOutput output) throws IOException {
        output.writeByte(schemaVersion);
        output.writeUTF(baseUri.toString());
        output.writeUTF(zipConfigUri.toString());
        writeDuration(output, autoUpdate);
        // write args
        writeMap(output, args);
    }

    private static void writeMap(DataOutput output, Map<String, String> args) throws IOException {
        output.writeInt(args.size());
        try {
            args.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEachOrdered(e -> {
                        try {
                            output.writeUTF(e.getKey());
                            output.writeUTF(e.getValue());
                        } catch (IOException ex) {
                            throw new UncheckedIOException(ex);
                        }
                    });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    public byte[] toBytes() {
        ByteArrayOutputStream os = new ByteArrayOutputStream(128);
        try {
            this.dumpsToBinary(new DataOutputStream(os));
        } catch (IOException e) {
            throw new AssertionError("Will not happen", e);
        }
        return os.toByteArray();
    }

    private String internalCalcSha256() {
        return Hashing.sha256().hashBytes(this.toBytes()).toString();
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        RemotePackConfig that = (RemotePackConfig) o;

        return new EqualsBuilder()
                .append(baseUri, that.baseUri)
                .append(zipConfigUri, that.zipConfigUri)
                .append(autoUpdate, that.autoUpdate)
                .append(args, that.args)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(baseUri)
                .append(zipConfigUri)
                .append(autoUpdate)
                .append(args)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("baseUri", baseUri)
                .append("zipConfigUri", zipConfigUri)
                .append("autoUpdate", autoUpdate)
                .append("args", args)
                .toString();
    }

    private static final Map<String, Long> DURATION_UNITS = Map.of(
            "ms", 1L,
            "s", 1000L,
            "sec", 1000L,
            "m", 1000L * 60,
            "mi", 1000L * 60,
            "min", 1000L * 60,
            "h", 1000L * 3600,
            "hr", 1000L * 3600,
            "d", 1000L * 86400
    );

    private static Duration durationFromString(String s) {
        StringCharacterIterator itr = new StringCharacterIterator(s);
        List<Map.Entry<String, Integer>> list = new ArrayList<>();

        char c;
        int start = 0;
        Integer integer = null;
        while (true) {
            c = itr.next();
            if ("0123456789".indexOf(c) >= 0) {
                if (integer != null) {  // !wasNumber
                    // stop suffixes
                    list.add(Map.entry(s.substring(start, (start = itr.getIndex())), integer));
                    integer = null;
                }
            } else if (c == StringCharacterIterator.DONE) {
                if (integer == null) {  // wasNumber
                    // treat the number as seconds
                    list.add(Map.entry("s", Integer.parseInt(s, start, itr.getEndIndex(), 10)));
                } else {
                    // treat as normal expressions
                    list.add(Map.entry(s.substring(start, itr.getEndIndex()), integer));
                }
                break;
            } else {
                if (integer == null) {  // wasNumber
                    // stop numbers
                    integer = Integer.parseInt(s, start, (start = itr.getIndex()), 10);
                }
            }
        }

        return Duration.ofMillis(list.stream().mapToLong(
                e -> Objects.requireNonNull(DURATION_UNITS.get(e.getKey()), e::getKey) * e.getValue()
        ).sum());
    }

    public URI zipConfigUri() {
        return zipConfigUri;
    }

    public URI baseUri() {
        return baseUri;
    }

    public Duration autoUpdate() {
        return autoUpdate;
    }

    public Map<String, String> args() {
        return args;
    }
}
