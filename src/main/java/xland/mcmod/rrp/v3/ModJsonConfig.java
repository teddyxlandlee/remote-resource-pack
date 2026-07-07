package xland.mcmod.rrp.v3;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.util.GsonHelper;
import org.apache.commons.io.function.IOSupplier;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

record ModJsonConfig(JsonObject configData, int version) {
    static int getConfigVersion(JsonObject obj) {
        JsonElement configVersionElement = obj.get("configVersion");
        if (configVersionElement == null || !configVersionElement.isJsonPrimitive() || !configVersionElement.getAsJsonPrimitive().isNumber()) {
            return -1;
        } else {
            return configVersionElement.getAsJsonPrimitive().getAsInt();
        }
    }

    static Map<String, ModJsonConfig> load(Map<String, IOSupplier<BufferedReader>> source) throws IOException {
        final Map<String, ModJsonConfig> configs = new LinkedHashMap<>();

        final Map<String, String> path2modCache = new LinkedHashMap<>();
        for (var confFileEntry : source.entrySet()) {
            final String modId = confFileEntry.getKey();
            final JsonObject conf;
            try (BufferedReader reader = confFileEntry.getValue().get()) {
                conf = GsonHelper.parse(reader);
            }

            for (Map.Entry<String, JsonElement> e : conf.entrySet()) {
                final String fileKey = e.getKey();
                if (!e.getValue().isJsonObject()) {
                    throw new JsonParseException(String.format(
                            "Expect %s (from mod %s) to be object, got %s",
                            fileKey, modId, e.getValue()
                    ));
                }
                path2modCache.merge(fileKey, modId, (mod1, mod2) -> {
                    throw new JsonParseException(String.format(
                            "Duplicate definition of %s (from mod %s and %s)",
                            fileKey, mod1, mod2
                    ));
                });
                final JsonObject obj = e.getValue().getAsJsonObject();
                // Check version
                final int configVersion = getConfigVersion(obj);
                configs.put(fileKey, new ModJsonConfig(obj, configVersion));
            }
        }
        return configs;
    }

    static void ensurePathInConfigDir(Path configFile, Path dest, String fileKey) throws AccessDeniedException {
        boolean isSub = false;
        for (Path dynPath = configFile; dynPath != null; dynPath = dynPath.getParent()) {
            if (dynPath.equals(dest)) {
                isSub = true;
                break;
            }
        }
        if (!isSub) {
            throw new AccessDeniedException(fileKey + " escapes out of config dir");
        }
    }
}
