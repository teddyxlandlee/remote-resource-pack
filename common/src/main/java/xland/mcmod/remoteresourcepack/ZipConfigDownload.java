package xland.mcmod.remoteresourcepack;

import com.google.common.base.Suppliers;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.client.ClientBrandRetriever;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static net.minecraft.util.GsonHelper.*;

final class ZipConfigDownload implements Closeable {
    private static final Base64.Decoder B64DECODER = Base64.getDecoder();
    private static final ThreadLocal<RandomGenerator> RANDOM = ThreadLocal.withInitial(java.util.Random::new);
    private static final String SKIP_KEY = "mod";

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private ZipConfigDownload(ZipOutputStream zos, URI baseUri) {
        this.zos = zos;
        this.baseUri = baseUri;

        this.executor = Executors.newSingleThreadExecutor();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
        this.futures = new CopyOnWriteArrayList<>();
    }

    @Override
    public void close() throws IOException {
        this.executor.close();
        this.httpClient.close();
        this.futures.clear();
        this.zos.close();
    }

    private final ZipOutputStream zos;
    private final ExecutorService executor;
    private final HttpClient httpClient;
    private final URI baseUri;
    private final List<CompletableFuture<?>> futures;

    private static final Supplier<String> USER_AGENT = Suppliers.memoize(ZipConfigDownload::userAgent0);

    private static String userAgent0() {
        return "RemoteResourcePack/" + RemoteResourcePack.modVersion()
                + " MC/" + RemoteResourcePack.minecraftVersion()
                + " (Platform:" + ClientBrandRetriever.getClientModName() + ")";
    }

    private static boolean isStatusOk(int statusCode) {
        return statusCode >= 200 && statusCode <= 299;
    }

    private void addFileToZip(String filename, JsonObject data) throws IllegalArgumentException {
        if (shouldSkip(data)) return;
        final ZipEntry zipEntry = new ZipEntry(filename);

        CompletableFuture<byte[]> completableFuture = null;
        final CompletableFuture<Void> finalFuture;

        if (!filename.endsWith("/")) {  // otherwise is directory
            if (isStringValue(data, "fetch")) {
                final String s = getAsString(data, "fetch");
                URI uri = baseUri.resolve(s);

                completableFuture = httpClient.sendAsync(
                        HttpRequest.newBuilder(uri).GET().header("User-Agent", USER_AGENT.get()).build(),
                        HttpResponse.BodyHandlers.ofByteArray()
                ).thenCompose(httpResponse -> {
                    if (isStatusOk(httpResponse.statusCode()))
                        return CompletableFuture.completedStage(httpResponse.body());
                    return CompletableFuture.failedStage(new IOException(
                            "Failed to GET " + uri + ": status code returned " + httpResponse.statusCode()
                    ));
                });
            } else if (isStringValue(data, "base64")) {
                final byte[] b = B64DECODER.decode(getAsString(data, "base64"));
                completableFuture = CompletableFuture.completedFuture(b);
            } else if (isStringValue(data, "raw")) {
                byte[] b = getAsString(data, "raw").getBytes(StandardCharsets.UTF_8);
                completableFuture = CompletableFuture.completedFuture(b);
            }   // else: put an empty entry
        }

        if (completableFuture != null) {
            finalFuture = completableFuture.thenComposeAsync(bytes -> {
                try {
                    zos.putNextEntry(zipEntry);
                    zos.write(bytes);
                    zos.closeEntry();
                    return CompletableFuture.completedStage(null);
                } catch (IOException e) {
                    return CompletableFuture.failedStage(e);
                }
            }, executor);
        } else {    // a directory or an empty entry
            finalFuture = new CompletableFuture<Void>().thenComposeAsync(v -> {
                try {
                    zos.putNextEntry(zipEntry);
                    zos.closeEntry();
                    return CompletableFuture.completedStage(null);
                } catch (IOException e) {
                    return CompletableFuture.failedStage(e);
                }
            }, executor);
        }
        this.futures.add(finalFuture);
    }

    private static boolean shouldSkip(JsonObject data) {
        JsonElement e = data.get("skip_on");
        if (e == null) return false;    // non-exist
        if (isStringValue(e)) {
            return SKIP_KEY.equalsIgnoreCase(e.getAsString());
        }
        if (e.isJsonArray()) {
            for (JsonElement arrayElement : e.getAsJsonArray()) {
                if (!isStringValue(arrayElement)) continue;
                if (SKIP_KEY.equalsIgnoreCase(arrayElement.getAsString()))
                    return true;
            }
        }
        return false;
    }

    static void generateZip(JsonObject zipConfig, URI baseUri,
                            Map<String, String> args, Path dest)
            throws IOException, CompletionException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(dest))) {
            try (ZipConfigDownload zipConfigDownload = new ZipConfigDownload(zos, baseUri)) {
                final JsonObject staticFiles = getAsJsonObject(zipConfig, "static");
                for (Map.Entry<String, JsonElement> entry : staticFiles.entrySet()) {
                    zipConfigDownload.addFileToZip(entry);
                }

                final JsonObject dynamicFiles = getAsJsonObject(zipConfig, "dynamic");
                for (Map.Entry<String, JsonElement> dynArgEntry : dynamicFiles.entrySet()) {
                    final JsonObject dynamicData = convertToJsonObject(dynArgEntry.getValue(), dynArgEntry.getKey());
                    // paramValue
                    int paramValue;
                    String paramString = args.get(dynArgEntry.getKey());

                    if ("random".equals(paramString)) {
                        paramValue = -1;
                    } else try {
                        paramValue = Integer.parseUnsignedInt(paramString);
                    } catch (NumberFormatException ex) {
                        final JsonElement e = dynamicData.get("default");
                        if (e != null && e.isJsonPrimitive() ) {
                            if ("random".equals(e.getAsString()))
                                paramValue = -1;
                                // may throw another NFE, here we assume it is provider's fault
                            else {
                                paramValue = e.getAsJsonPrimitive().getAsInt();
                                if (paramValue < 0)
                                    throw new JsonParseException(
                                            "dynamic default value of %s is %s while negative value is illegal".formatted(
                                                    dynArgEntry.getKey(), paramValue
                                            ));
                            }
                        } else {
                            throw new JsonParseException("Missing default value for " + dynArgEntry.getKey()
                                    + " or it is not primitive");
                        }
                    }

                    final JsonArray items = getAsJsonArray(dynamicData, "items");
                    if (paramValue < 0) {   // is random
                        final String errDesc = "dynamic." + dynArgEntry.getKey() + ".items";

                        int totalWeight = 0;
                        int index = 0;
                        int[] weights = new int[items.size()];

                        for (JsonElement item0 : items) {
                            final JsonObject item = convertToJsonObject(item0, errDesc + '.' + index);
                            int weight = getAsInt(item, "weight", 100);
                            if (weight == 0) weight = 100;
                            totalWeight += (weights[index++] = weight);
                        }

                        int randomNum = RANDOM.get().nextInt(totalWeight);
                        index = 0;
                        for (JsonElement item0 : items) {
                            randomNum -= weights[index++];
                            if (randomNum < 0) {
                                final JsonObject files = getAsJsonObject(item0.getAsJsonObject(), "files");
                                for (Map.Entry<String, JsonElement> fileEntry : files.entrySet()) {
                                    zipConfigDownload.addFileToZip(fileEntry);
                                }
                                break;
                            }
                        }
                    } else {
                        if (paramValue < items.size()) {    // index in bounds
                            final JsonObject files = getAsJsonObject(convertToJsonObject(items.get(paramValue),
                                    "dynamic." + dynArgEntry.getKey() + ".items." + paramValue), "files");
                            for (Map.Entry<String, JsonElement> fileEntry : files.entrySet()) {
                                zipConfigDownload.addFileToZip(fileEntry);
                            }
                        }
                    }
                }

                CompletableFuture.allOf(zipConfigDownload.futures.toArray(new CompletableFuture[0])).join();
            }
        }
    }

    private void addFileToZip(Map.Entry<String, ? extends JsonElement> fileEntry) {
        this.addFileToZip(fileEntry.getKey(), convertToJsonObject(fileEntry.getValue(), fileEntry.getKey()));
    }

}
