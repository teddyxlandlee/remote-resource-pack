/*
 * Remote Resource Pack: load resource packs to which contents are downloaded from the internet.
 * Copyright (c) 2023-2026 teddyxlandlee & contributors.
 * SPDX-License-Identifier: MIT
 */
package xland.mcmod.rrp.v3.neoforge;

//? if neoforge {
/*import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import org.apache.commons.io.function.IOSupplier;
import org.jetbrains.annotations.NotNullByDefault;
import xland.mcmod.rrp.v3.RemoteResourcePack;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@NotNullByDefault
public final class RemoteResourcePackNeo extends RemoteResourcePack {
    private static final RemoteResourcePackNeo INSTANCE = new RemoteResourcePackNeo();

    public static RemoteResourcePackNeo getInstance() {
        return INSTANCE;
    }

    public Path getGameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    public Map<String, IOSupplier<BufferedReader>> getModsBuiltinConfigs() {
        return ModList.get().applyForEachModFile(modFile -> {
            final IOSupplier<BufferedReader> supplier;
            //? if <1.21.11 {
            /^final var path = modFile.findResource("RemoteResourcePack.json");   // java.nio.file.Path
            if (Files.notExists(path)) return null;
            supplier = () -> Files.newBufferedReader(path);
            ^///?} else {
            final var resource = modFile.getContents().get("RemoteResourcePack.json");
            if (resource == null) return null;  // non-exist
            supplier = resource::bufferedReader;
            //?}
            return Map.entry(modFile.getModInfos().getFirst().getModId(), supplier);
        }).filter(Objects::nonNull).collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public String modVersion() {
        return Objects.requireNonNull(RemoteResourcePackNeoEntrypoint.modVersion, "modVersion uninitialized");
    }

    public String minecraftVersion() {
        return Objects.requireNonNull(ModList.get().getModFileById("minecraft"), "Minecraft not found?!").versionString();
    }
}
*///?}