package xland.mcmod.remoteresourcepack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.random.RandomGenerator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static net.minecraft.util.GsonHelper.*;

final class ZipConfigUtil {
    private ZipConfigUtil() {}
    private static final Base64.Decoder B64DECODER = Base64.getDecoder();
    private static final ThreadLocal<RandomGenerator> RANDOM = ThreadLocal.withInitial(RandomGenerator::getDefault);

    private static void addFileToZip(ZipOutputStream zos, String filename,
                                     JsonObject data, URL baseUrl)
            throws IOException {
        zos.putNextEntry(new ZipEntry(filename));
        if (!filename.endsWith("/")) {  // otherwise is directory
            if (isStringValue(data, "fetch")) {
                final String s = getAsString(data, "fetch");
                try (var is = new URL(baseUrl, s).openStream()) {
                    is.transferTo(zos);
                }
            } else if (isStringValue(data, "base64")) {
                final byte[] b = B64DECODER.decode(getAsString(data, "base64"));
                zos.write(b);
            } else if (isStringValue(data, "raw")) {
                zos.write(getAsString(data, "raw").getBytes(StandardCharsets.UTF_8));
            }   // else: put an empty entry
        }
        zos.closeEntry();
    }

    static void generateZip(JsonObject zipConfig, URL baseUrl,
                            Map<String, String> args, Path dest)
            throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(dest))) {
            final JsonObject staticFiles = getAsJsonObject(zipConfig, "static");
            for (Map.Entry<String, JsonElement> entry : staticFiles.entrySet()) {
                addFileToZip(zos, entry, baseUrl);
            }

            final JsonObject dynamicFiles = getAsJsonObject(zipConfig, "dynamic");
            for (Map.Entry<String, JsonElement> dynArgEntry : dynamicFiles.entrySet()) {
                final JsonObject dynamicData = convertToJsonObject(dynArgEntry.getValue(), dynArgEntry.getKey());
                // paramValue
                int paramValue;
                String paramString = args.get(dynArgEntry.getKey());

                if ("random".equals(paramString))
                    paramValue = -1;
                else try {
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

                if (paramValue < 0) {   // is random
                    final JsonArray items = getAsJsonArray(dynamicData, "items");
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
                                addFileToZip(zos, fileEntry, baseUrl);
                            }
                            break;
                        }
                    }
                } else {
                    final JsonArray items = convertToJsonArray(dynamicData, "items");
                    if (paramValue < items.size()) {    // index in bounds
                        final JsonObject files = getAsJsonObject(convertToJsonObject(items.get(paramValue),
                                "dynamic." + dynArgEntry.getKey() + ".items." + paramValue), "files");
                        for (Map.Entry<String, JsonElement> fileEntry : files.entrySet()) {
                            addFileToZip(zos, fileEntry, baseUrl);
                        }
                    }
                }
            }
        }
    }

    private static void addFileToZip(ZipOutputStream zos, Map.Entry<String, ? extends JsonElement> fileEntry,
                                     URL baseUrl) throws IOException {
        addFileToZip(zos, fileEntry.getKey(), convertToJsonObject(fileEntry.getValue(), fileEntry.getKey()), baseUrl);
    }
}
