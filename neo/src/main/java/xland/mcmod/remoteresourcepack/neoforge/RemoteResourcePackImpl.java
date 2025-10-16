package xland.mcmod.remoteresourcepack.neoforge;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import org.apache.commons.io.function.IOSupplier;
import xland.mcmod.remoteresourcepack.RemoteResourcePack;

import java.io.BufferedReader;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public final class RemoteResourcePackImpl extends RemoteResourcePack {
    public Path getGameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    public Map<String, IOSupplier<BufferedReader>> getModsBuiltinConfigs() {
        Map<String, IOSupplier<BufferedReader>> map = new LinkedHashMap<>();
        return ModList.get().applyForEachModFile(modFile -> Map.entry(
                modFile.getModInfos().getFirst().getModId(),
                Optional.ofNullable(modFile.getContents().get("RemoteResourcePack.json"))
        ))
                .flatMap(e -> e.getValue().map(
                        v -> Map.entry(e.getKey(), (IOSupplier<BufferedReader>) v::bufferedReader)
                ).stream())
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public String modVersion() {
        return Objects.requireNonNull(RemoteResourcePackNeo.modVersion, "modVersion uninitialized");
    }

    public String minecraftVersion() {
        return Objects.requireNonNull(ModList.get().getModFileById("minecraft"), "Minecraft not found?!").versionString();
    }

    private static final RemoteResourcePackImpl INSTANCE = new RemoteResourcePackImpl();
    public static RemoteResourcePack platform() { return INSTANCE; }
}
