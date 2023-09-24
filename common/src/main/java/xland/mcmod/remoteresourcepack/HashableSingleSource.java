package xland.mcmod.remoteresourcepack;

import com.google.common.hash.Hashing;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.util.GsonHelper;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.StringCharacterIterator;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public final class HashableSingleSource {
    final URL baseUrl;
    final URL zipConfigUrl;
    final Duration autoUpdate;
    final Map<String, String> args;
    private static final byte schemaVersion = 1;
    private transient final String hash;

    HashableSingleSource(URL baseUrl, URL zipConfigUrl, Duration autoUpdate, Map<String, String> args) {
        this.baseUrl = baseUrl;
        this.zipConfigUrl = zipConfigUrl;
        this.autoUpdate = autoUpdate;
        this.hash = internalCalcSha256();
        this.args = args;
    }

    public boolean exists(Path repo) {
        return Files.exists(getStoreCacheFile(repo));
    }

    public boolean isOutdated(Path repo) {
        if (!exists(repo)) return true;
        if (isAlwaysUpToDate(autoUpdate)) return false;

        final Path timestamp = getStoreCacheTimestampFile(repo);
        if (Files.notExists(timestamp)) return true;
        try (DataInputStream input = new DataInputStream(Files.newInputStream(timestamp))) {
            Instant instant = readInstant(input);
            return instant.plus(autoUpdate).isBefore(Instant.now());
        } catch (Exception e) {
            RemoteResourcePack.LOGGER.error("Can't read timestamp file {}", timestamp, e);
            return true;
        }
    }

    public Path generate(Path repo) throws IOException {
        final Path file = getStoreCacheFile(repo);
        if (!isOutdated(repo)) return file;
        Files.createDirectories(file.getParent());
        ZipConfigUtil.generateZip(readZipConfig(), baseUrl, args, file);

        final Path timestamp = getStoreCacheTimestampFile(repo);
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(timestamp))) {
            writeInstant(output, Instant.now());
        }
        return file;
    }

    private JsonObject readZipConfig() throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(zipConfigUrl.openStream()))) {
            return GsonHelper.parse(reader);
        }
    }

    private static boolean isAlwaysUpToDate(Duration duration) {
        return duration.isNegative();
    }

    public String getHash() {
        return hash;
    }

    private StringBuilder getSlicedHash() {
        StringBuilder sb = new StringBuilder();
        sb.append(hash, 0, 2).append('/');
        sb.append(hash, 2, 32).append('/');
        sb.append(hash, 33, 64);
        return sb;
    }

    public Path getStoreCacheFile(Path repo) {
        return repo.resolve(getSlicedHash().append(".zip").toString());
    }

    public Path getStoreCacheTimestampFile(Path repo) {
        return repo.resolve(getSlicedHash().append(".timestamp").toString());
    }

    public static HashableSingleSource of(URL baseUrl, URL zipConfigUrl, Duration autoUpdate, Map<String, String> args) {
        autoUpdate = canonicalizeDuration(autoUpdate);
        return new HashableSingleSource(baseUrl, zipConfigUrl, autoUpdate, args);
    }

    public static HashableSingleSource readFromJson(JsonObject obj) throws JsonParseException {
        if (GsonHelper.getAsByte(obj, "schema", (byte)0) != schemaVersion)
            throw new JsonParseException(schemaMismatch(obj.get("schema").getAsByte()));
        final URL baseUrl, zipConfigUrl;
        try {
            baseUrl = new URL(GsonHelper.getAsString(obj, "base"));
            zipConfigUrl = new URL(GsonHelper.getAsString(obj, "zipconfig"));
        } catch (MalformedURLException e) {
            throw new JsonParseException("Unresolvable URL", e);
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

        return of(baseUrl, zipConfigUrl, autoUpdate, args);
    }

    /*public static HashableSingleSource readFromBinary(DataInput input) throws IOException {
        byte b;
        if ((b = input.readByte()) != schemaVersion)
            throw schemaMismatch(b);

        final URL baseUrl = new URL(input.readUTF());
        final URL zipConfigUrl = new URL(input.readUTF());

        return new HashableSingleSource(baseUrl, zipConfigUrl, readDuration(input));
    }*/

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

    /*private static Duration canonicalizeDuration(long sec, int nano) {
        if (sec < 0) return Duration.ofSeconds(-1L);
        return Duration.ofSeconds(sec, nano);
    }*/

    private static void writeDuration(DataOutput output, Duration duration) throws IOException {
        output.writeLong(duration.getSeconds());
        output.writeInt(duration.getNano());
    }

    /*private static Duration readDuration(DataInput input) throws IOException {
        final long sec = input.readLong();
        final int nanos = input.readInt();
        return canonicalizeDuration(sec, nanos);
    }*/

    private static Instant readInstant(DataInput input) throws IOException {
        final long sec = input.readLong();
        final int nanos = input.readInt();
        return Instant.ofEpochSecond(sec, nanos);
    }

    private static void writeInstant(DataOutput output, Instant instant) throws IOException {
        output.writeLong(instant.getEpochSecond());
        output.writeInt(instant.getNano());
    }

    public void dumpsToBinary(DataOutput output) throws IOException {
        output.writeByte(schemaVersion);
        output.writeUTF(baseUrl.toString());
        output.writeUTF(zipConfigUrl.toString());
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

    @SuppressWarnings("all")
    private String internalCalcSha256() {
        return Hashing.sha256().hashBytes(this.toBytes()).toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        HashableSingleSource that = (HashableSingleSource) o;

        return new EqualsBuilder()
                .append(baseUrl, that.baseUrl)
                .append(zipConfigUrl, that.zipConfigUrl)
                .append(autoUpdate, that.autoUpdate)
                .append(args, that.args)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(baseUrl)
                .append(zipConfigUrl)
                .append(autoUpdate)
                .append(args)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("baseUrl", baseUrl)
                .append("zipConfigUrl", zipConfigUrl)
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

        return Duration.ofMillis(
                list.stream().mapToLong(e -> Objects.requireNonNull(DURATION_UNITS.get(e.getKey()), e::getKey) * e.getValue())
                        .sum()
        );
    }
}
