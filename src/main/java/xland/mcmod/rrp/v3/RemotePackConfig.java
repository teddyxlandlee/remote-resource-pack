/*
 * Remote Resource Pack: load resource packs to which contents are downloaded from the internet.
 * Copyright (c) 2023-2026 teddyxlandlee & contributors.
 * SPDX-License-Identifier: MIT
 */
package xland.mcmod.rrp.v3;

import com.google.common.hash.Hashing;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.util.GsonHelper;
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

    @Deprecated
    public boolean exists(Path repo) {
        return new PackRepoItem(repo, this).exists();
    }

    @Deprecated
    public boolean isOutdated(Path repo) {
        return new PackRepoItem(repo, this).isOutdated();
    }

    public Path generate(Path repo) throws IOException, CompletionException {
        PackRepoItem item = new PackRepoItem(repo, this);
        item.generate();
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
        StringBuilder sb = new StringBuilder();
        sb.append(hash, 0, 2).append('/');
        sb.append(hash, 2, 32).append('/');
        sb.append(hash, 33, 64);
        return sb;
    }

    @Deprecated
    public Path getStoreCacheFile(Path repo) {
        return new PackRepoItem(repo, this).zipCache();
    }

    @Deprecated
    public Path getStoreCacheTimestampFile(Path repo) {
        return new PackRepoItem(repo, this).zipTimestamp();
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
        if (GsonHelper.getAsByte(obj, "schema", (byte)0) != schemaVersion)
            throw new JsonParseException(schemaMismatch(obj.get("schema").getAsByte()));
        final URI baseUri, zipConfigUri;
        try {
            baseUri = URI.create(GsonHelper.getAsString(obj, "base"));
            zipConfigUri = URI.create(GsonHelper.getAsString(obj, "zipconfig"));
        } catch (IllegalArgumentException e) {
            throw new JsonParseException("Unresolvable URI", e);
        }
        final String autoUpdateExpr = GsonHelper.getAsString(obj, "autoUpdate", "2d");
        Duration autoUpdate = switch (autoUpdateExpr) {
            case "always", "0" -> Duration.ZERO;
            case "never", "-1" -> Duration.ofSeconds(-1);
            default -> durationFromString(autoUpdateExpr);
        };
        obj = GsonHelper.getAsJsonObject(obj, "args", new JsonObject());
        final Map<String, String> args = new LinkedHashMap<>();
        obj.entrySet().forEach(e -> {
            if (!e.getValue().isJsonPrimitive()) {
                throw new JsonParseException(String.format(
                        "Expect argument %s to be primitive, got %s",
                        e.getKey(), e.getValue()));
            }
            args.put(e.getKey(), e.getValue().getAsString());
        });

        return ofInternal(baseUri, zipConfigUri, autoUpdate, args);
    }

    private static IOException schemaMismatch(int b) {
        return new java.io.InvalidObjectException(String.format(
                "Invalid schema version: expected %d, got %d",
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
