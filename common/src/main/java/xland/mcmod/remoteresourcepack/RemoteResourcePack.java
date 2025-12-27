package xland.mcmod.remoteresourcepack;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.util.GsonHelper;
import org.apache.commons.io.function.IOSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class RemoteResourcePack {
    public static final String MOD_ID = "remoteresourcepack";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static final Logger LOGGER = LogManager.getLogger();
    private static final Marker MARKER = MarkerManager.getMarker("RemoteResourcePack");

    private static volatile Map<String, Path> cacheFiles;

    public static String packName(String key) {
        return "RemoteResourcePack/" + key;
    }

    public static RemoteResourcePack platform() {
        class Holder {
            static final RemoteResourcePack PLATFORM = Platform.findImplementation(RemoteResourcePack.class);
        }
        return Holder.PLATFORM;
    }

    // invoked by Fabric entrypoint
    public static void init() {
        final Path repo = platform().getGameDir().resolve("RemoteResourcePack");
        LOGGER.info(MARKER, "Scanning builtin mod config");
        final Map<String, IOSupplier<BufferedReader>> modsBuiltinConfigs = platform().getModsBuiltinConfigs();
        try {
            cacheFiles = Collections.unmodifiableMap(cache(modsBuiltinConfigs, repo));
        } catch (IOException e) {
            LOGGER.error("Failed to download/generate remote resource pack(s)", e);
        }
    }

    public static Map<String, Path> getCacheFiles() {
        final Map<String, Path> map = cacheFiles;
        if (map == null)
            throw new IllegalStateException("cacheFiles not initialized yet");
        return map;
    }

    protected abstract Path getGameDir();

    private static int getConfigVersion(JsonObject obj) {
        JsonElement configVersionElement = obj.get("configVersion");
        if (configVersionElement == null || !configVersionElement.isJsonPrimitive() || !configVersionElement.getAsJsonPrimitive().isNumber()) {
            return -1;
        } else {
            return configVersionElement.getAsJsonPrimitive().getAsInt();
        }
    }

    private static void extractModConfig(Map<String, IOSupplier<BufferedReader>> source, Path dest) throws IOException {
        final Map<String, JsonObject> toBeWritten = new LinkedHashMap<>();
        final Map<String, Integer> configVersions = new HashMap<>();
        {
            final Map<String, String> path2modCache = new LinkedHashMap<>();
            for (Map.Entry<String, IOSupplier<BufferedReader>> confFileEntry : source.entrySet()) {
                final JsonObject conf;
                try (BufferedReader reader = confFileEntry.getValue().get()) {
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
                    JsonObject obj = e.getValue().getAsJsonObject();
                    // Check version
                    {
                        final int configVersion = getConfigVersion(obj);
                        configVersions.put(e.getKey(), configVersion);
                    }
                    toBeWritten.put(e.getKey(), obj);
                }
            }
        }
        // dump configs to modConfigDir
        LOGGER.info(MARKER, "Dumping builtin configs");
        for (Map.Entry<String, JsonObject> filename2json : toBeWritten.entrySet()) {
            final Path configFile = dest.resolve(filename2json.getKey()).toAbsolutePath().normalize();
            if (Files.exists(configFile)) {
                // Check version
                try (BufferedReader reader = Files.newBufferedReader(configFile)) {
                    JsonObject obj = GSON.fromJson(reader, JsonObject.class);
                    final int localConfigVersion = getConfigVersion(obj);
                    final int givenConfigVersion = configVersions.getOrDefault(filename2json.getKey(), -1);
                    if (givenConfigVersion <= localConfigVersion) {
                        // No need to update, skip
                        continue;
                    }
                } catch (Exception e) {
                    LOGGER.warn("Can't read config at {}. Force override.", configFile);
                }
            }
            // security check: file should be INSIDE modConfigDir
            {
                boolean isSub = false;
                for (Path dynPath = configFile; dynPath != null; dynPath = dynPath.getParent()) {
                    if (dynPath.equals(dest)) {
                        isSub = true;
                        break;
                    }
                }
                if (!isSub)
                    throw new AccessDeniedException(filename2json.getKey() + " escapes out of config dir");
            }

            Files.createDirectories(configFile.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(configFile)) {
                GSON.toJson(filename2json.getValue(), writer);
            }
        }
    }

    static Map<String, Path> cache(Map<String, IOSupplier<BufferedReader>> modConfigs, Path repo)
            throws IOException, JsonParseException {
        // load configs from mods
        LOGGER.info(MARKER, "Loading config");
        final Path modConfigDir = getModConfigDir().toAbsolutePath().normalize();
        Files.createDirectories(modConfigDir);

        extractModConfig(modConfigs, modConfigDir);

        // download + generate zip files
        LOGGER.info("Downloading + generating files");

        final ConcurrentMap<String, Path> cacheFilesPerHash = new ConcurrentHashMap<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CopyOnWriteArrayList<CompletableFuture<?>> futures = new CopyOnWriteArrayList<>();
            AtomicInteger cc = new AtomicInteger();

            try (var stream = Files.walk(modConfigDir)) {
                stream.forEach(path -> {
                    if (!Files.isRegularFile(path) || !path.toString().endsWith(".json")) return;

                    futures.add(CompletableFuture.runAsync(() -> {
                        LOGGER.info("(#{}) Generating pack from {}", cc.incrementAndGet(), path);

                        final JsonObject singleConfig;
                        try (BufferedReader reader = Files.newBufferedReader(path)) {
                            singleConfig = GsonHelper.parse(reader);
                        } catch (IOException e) {
                            LOGGER.error("Failed to parse config from {}", path);
                            return;
                        }

                        try {
                            final HashableSingleSource source = HashableSingleSource.readFromJson(singleConfig);
                            cacheFilesPerHash.put(source.getHash(), source.generate(repo));
                            LOGGER.info("Generated pack {} from {}", source.getHash(), path);
                        } catch (Exception e) {
                            LOGGER.error("Failed to parse config or generate pack from {}", path, e);
                        }
                    }, executor));
                });
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }

        return cacheFilesPerHash;
    }

    static Path getModConfigDir() {
        return platform().getConfigDir().resolve("RemoteResourcePack");
    }

    protected abstract Path getConfigDir();

    // <mod.jar>/RemoteResourcePack.json
    protected abstract Map<String, IOSupplier<BufferedReader>> getModsBuiltinConfigs();

    protected abstract String modVersion();

    protected abstract String minecraftVersion();

    // invoked by Mixins
    public static void insertEnabledPacks(PackRepository packRepository) {
        final Set<String> set = new LinkedHashSet<>();
        // proven that elements are unique: mapped from keySet
        final List<String> remotePackNames = getCacheFiles().keySet().stream().map(RemoteResourcePack::packName).toList();
        if (remotePackNames.isEmpty()) return;

        set.addAll(packRepository.getSelectedIds());
        set.addAll(remotePackNames);
        packRepository.setSelected(set);

        final List<String> optionsResourcePacks = Minecraft.getInstance().options.resourcePacks;
        final Set<String> existingPackNames = new HashSet<>(optionsResourcePacks);
        remotePackNames.forEach(s -> {
            if (!existingPackNames.contains(s))
                optionsResourcePacks.add(s);
        });
    }
}
