package xland.mcmod.remoteresourcepack;

import com.google.gson.*;
import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.util.GsonHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class RemoteResourcePack {
    public static final String MOD_ID = "remoteresourcepack";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static final Logger LOGGER = LogManager.getLogger();
    private static final Marker MARKER = MarkerManager.getMarker("RemoteResourcePack");

    private static volatile Map<String, Path> cacheFiles;

    public static void init() {
        final Path repo = getGameDir().resolve("RemoteResourcePack");
        LOGGER.info(MARKER, "Scanning builtin mod config");
        final Map<String, Path> modsBuiltinConfigs = getModsBuiltinConfigs();
        try {
            cacheFiles = Collections.unmodifiableMap(cache(modsBuiltinConfigs, repo));
        } catch (IOException e) {
            throw new RuntimeException("Failed to download/generate remote resource pack(s)", e);
        }
    }

    public static Map<String, Path> getCacheFiles() {
        final Map<String, Path> map = cacheFiles;
        if (map == null)
            throw new IllegalStateException("cacheFiles not initialized yet");
        return map;
    }

    @ExpectPlatform
    static Path getGameDir() { throw new AssertionError("ExpectPlatform"); }

    static Map<String, Path> cache(Map<String, Path> modConfigs, Path repo)
            throws IOException, JsonParseException {
        // load configs from mods
        LOGGER.info(MARKER, "Loading config");
        final Path modConfigDir = getModConfigDir().toAbsolutePath().normalize();
        final Map<String, JsonObject> toBeWritten = new LinkedHashMap<>();
        {
            final Map<String, String> path2modCache = new LinkedHashMap<>();
            for (Map.Entry<String, Path> confFileEntry : modConfigs.entrySet()) {
                final JsonObject conf;
                try (BufferedReader reader = Files.newBufferedReader(confFileEntry.getValue())) {
                    conf = GsonHelper.parse(reader);
                }

                for (Map.Entry<String, JsonElement> e : conf.entrySet()) {
                    if (!e.getValue().isJsonObject())
                        throw new JsonParseException(String.format(
                                "Expect %s (from mod %s) to be object, got %s",
                                e.getKey(), confFileEntry.getKey(), e.getValue()));
                    path2modCache.merge(e.getKey(), confFileEntry.getKey(), (mod1, mod2) -> {
                        throw new JsonParseException(String.format(
                                "Duplicate definition of %s (from mod %s and %s)",
                                e.getKey(), mod1, mod2));
                    });
                    toBeWritten.put(e.getKey(), e.getValue().getAsJsonObject());
                }
            }
        }
        // dump configs to modConfigDir
        LOGGER.info(MARKER, "Dumping builtin configs");
        for (Map.Entry<String, JsonObject> filename2json : toBeWritten.entrySet()) {
            final Path configFile = modConfigDir.resolve(filename2json.getKey()).toAbsolutePath().normalize();
            if (Files.exists(configFile)) continue;
            // security check: file should be INSIDE modConfigDir
            {
                Path dynPath = configFile;
                boolean isSub = false;
                while (true) {
                    if (dynPath == null) break;
                    if (dynPath.equals(modConfigDir)) {
                        isSub = true;
                        break;
                    }
                    dynPath = dynPath.getParent();
                }
                if (!isSub)
                    throw new AccessDeniedException(filename2json.getKey() + " escapes out of config dir");
            }

            Files.createDirectories(configFile.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(configFile)) {
                GSON.toJson(filename2json.getValue(), writer);
            }
        }
        // download + generate zip files
        LOGGER.info("Downloading + generating files");
        final Map<String, Path> cacheFilesPerHash = new LinkedHashMap<>();
        try (var stream = Files.walk(modConfigDir)) {
            stream.forEach(path -> {
                if (!Files.isRegularFile(path) || !path.toString().endsWith(".json")) return;

                final JsonObject singleConfig;
                try (BufferedReader reader = Files.newBufferedReader(path)) {
                    singleConfig = GsonHelper.parse(reader);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }

                try {
                    final HashableSingleSource source = HashableSingleSource.readFromJson(singleConfig);
                    cacheFilesPerHash.put(source.getHash(), source.generate(repo));
                    LOGGER.info("Generated pack {} from {}", source.getHash(), path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                } catch (Exception e) {
                    LOGGER.error("Failed to parse config or generate pack from {}", path, e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        return cacheFilesPerHash;
    }

    static Path getModConfigDir() {
        return getConfigDir().resolve("RemoteResourcePack");
    }

    @ExpectPlatform
    private static Path getConfigDir() { throw new AssertionError("ExpectPlatform"); }

    // <mod.jar>/RemoteResourcePack.json
    @ExpectPlatform
    static Map<String, Path> getModsBuiltinConfigs() { throw new AssertionError(); }

    public static void insertEnabledPacks(PackRepository packRepository, Collection<String> packs) {
        final Set<String> set = new LinkedHashSet<>();
        set.addAll(packRepository.getSelectedIds());
        set.addAll(packs);
        packRepository.setSelected(set);
    }
}
