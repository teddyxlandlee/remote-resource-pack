package xland.mcmod.remoteresourcepack.fabric;

import net.fabricmc.loader.api.FabricLoader;
import xland.mcmod.remoteresourcepack.RemoteResourcePack;

import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

public final class RemoteResourcePackImpl {
    public static void init() {
        RemoteResourcePack.init();
    }

    public static Path getGameDir() {
        return FabricLoader.getInstance()
                .getGameDir()
                .toAbsolutePath()
                .normalize();
    }

    public static Path getConfigDir() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .toAbsolutePath()
                .normalize();
    }

    public static Map<String, Path> getModsBuiltinConfigs() {
        return FabricLoader.getInstance().getAllMods().stream()
                .flatMap(c -> c.findPath("RemoteResourcePack.json").stream()
                        .map(p -> Map.entry(c.getMetadata().getId(), p))
                )
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static String getModVersion(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(modContainer -> modContainer.getMetadata().getVersion().getFriendlyString())
                .orElseThrow(() -> new RuntimeException("Can't find " + modId + " mod?!"));
    }

    public static String modVersion() {
        return getModVersion(RemoteResourcePack.MOD_ID);
    }

    public static String minecraftVersion() {
        return getModVersion("minecraft");
    }
}
