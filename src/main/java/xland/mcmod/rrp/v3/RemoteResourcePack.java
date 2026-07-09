/*
 * Remote Resource Pack: load resource packs to which contents are downloaded from the internet.
 * Copyright (c) 2023-2026 teddyxlandlee & contributors.
 * SPDX-License-Identifier: MIT
 */
package xland.mcmod.rrp.v3;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import org.apache.commons.io.function.IOSupplier;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.UnknownNullability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
//? if fabric
import xland.mcmod.rrp.v3.fabric.RemoteResourcePackFabric;
//? if neoforge
//import xland.mcmod.rrp.v3.neoforge.RemoteResourcePackNeo;
//? if forge
//import xland.mcmod.rrp.v3.forge.RemoteResourcePackForge;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class RemoteResourcePack {
    public static final String MOD_ID = "remoteresourcepack";
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static final Logger LOGGER = LoggerFactory.getLogger(RemoteResourcePack.class);
    private static final Marker MARKER = MarkerFactory.getMarker("RemoteResourcePack");

    // late-init
    private static volatile @UnknownNullability Map<String, Path> cacheFiles;

    public static String packName(String key) {
        return "RemoteResourcePack/" + key;
    }

    public static RemoteResourcePack platform() {
        //? if fabric {
        return RemoteResourcePackFabric.getInstance();
        //?} elif neoforge {
        /*return RemoteResourcePackNeo.getInstance();
        *///?} elif forge {
        /*return RemoteResourcePackForge.getInstance();
        *///?} else {
        /*throw new IllegalStateException("Unimplemented platform")*/
        //?}
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

    @ApiStatus.Obsolete
    public static Map<String, Path> getCacheFiles() {
        final Map<String, Path> map = cacheFiles;
        if (map == null)
            throw new IllegalStateException("cacheFiles not initialized yet");
        return map;
    }

    protected abstract Path getGameDir();

    protected abstract Path getConfigDir();

    // <mod.jar>/RemoteResourcePack.json
    protected abstract Map<String, IOSupplier<BufferedReader>> getModsBuiltinConfigs();

    protected abstract String modVersion();

    protected abstract String minecraftVersion();

    static Path getModConfigDir() {
        return platform().getConfigDir().resolve("RemoteResourcePack");
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
        return download(repo, modConfigDir);
    }

    private static void extractModConfig(final Map<String, IOSupplier<BufferedReader>> source, final Path dest) throws IOException {
        final Map<String, ModJsonConfig> configs = ModJsonConfig.load(source);
//        final Map<String, JsonObject> toBeWritten = new LinkedHashMap<>();
//        final Map<String, Integer> configVersions = new HashMap<>();

        // dump configs to modConfigDir
        LOGGER.info(MARKER, "Dumping builtin configs");
        for (Map.Entry<String, ModJsonConfig> config : configs.entrySet()) {
            final String fileKey = config.getKey();

            final Path configFile = dest.resolve(fileKey).toAbsolutePath().normalize();
            if (Files.exists(configFile)) {
                // Check version
                try (BufferedReader reader = Files.newBufferedReader(configFile)) {
                    final JsonObject localObj = GSON.fromJson(reader, JsonObject.class);
                    final int localConfigVersion = ModJsonConfig.getConfigVersion(localObj);
                    final int givenConfigVersion = config.getValue().version();
                    if (givenConfigVersion <= localConfigVersion) {
                        // No need to update, skip
                        continue;
                    }
                } catch (Exception e) {
                    LOGGER.warn("Can't read config at {}. Force override.", configFile);
                }
            }
            // security check: file should be INSIDE modConfigDir
            ModJsonConfig.ensurePathInConfigDir(configFile, dest, fileKey);

            Files.createDirectories(configFile.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(configFile)) {
                GSON.toJson(config.getValue().configData(), writer);
            }
        }
    }

    private static ConcurrentMap<String, Path> download(Path repo, Path modConfigDir) throws IOException {
        final ConcurrentMap<String, Path> cacheFilesPerHash = new ConcurrentHashMap<>();
        //? if java: >= 21 {
        try (final var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            //?} else {
            /*try (final var executor = LegacyExecutorCloser.cachedThreadPool()) {
             *///?}
            CopyOnWriteArrayList<CompletableFuture<?>> futures = new CopyOnWriteArrayList<>();
            AtomicInteger cc = new AtomicInteger();

            try (var stream = Files.walk(modConfigDir)) {
                stream.forEach(path -> {
                    if (!Files.isRegularFile(path) || !path.toString().endsWith(".json")) return;

                    futures.add(CompletableFuture.runAsync(() -> {
                        LOGGER.info("(#{}) Generating pack from {}", cc.incrementAndGet(), path);

                        final JsonObject singleConfig;
                        try (BufferedReader reader = Files.newBufferedReader(path)) {
                            singleConfig = GSON.fromJson(reader, JsonObject.class);
                        } catch (IOException | JsonParseException e) {
                            LOGGER.error("Failed to parse config from {}", path);
                            return;
                        }

                        try {
                            final RemotePackConfig source = RemotePackConfig.readFromJson(singleConfig);
                            cacheFilesPerHash.put(source.getHash(), source.generate(repo));
                            LOGGER.info("Generated pack {} from {}", source.getHash(), path);
                        } catch (Exception e) {
                            LOGGER.error("Failed to parse config or generate pack from {}", path, e);
                        }
                    }, executor));
                });
            }
            ZipConfigDownload.joinAllFutures(futures);
        }
        return cacheFilesPerHash;
    }

    // invoked by Mixins
    @ApiStatus.Internal
    public static Collection<String> insertEnabledPacks(final Collection<String> oldPacks) {
//        final Set<String> set = new LinkedHashSet<>();
        // proven that elements are unique: mapped from keySet
        final List<String> remotePackNames = getCacheFiles().keySet()
                .stream()
                .map(RemoteResourcePack::packName)
                .toList();
        if (remotePackNames.isEmpty()) return oldPacks;

        final Collection<String> set = oldPacks.getClass() == LinkedHashSet.class ? oldPacks : new LinkedHashSet<>(oldPacks);
        set.addAll(remotePackNames);

        final List<String> optionsResourcePacks = Minecraft.getInstance().options.resourcePacks;
        final Set<String> existingPackNames = new HashSet<>(optionsResourcePacks);
        remotePackNames.forEach(s -> {
            if (!existingPackNames.contains(s)) optionsResourcePacks.add(s);
        });

        return set;
    }
}
