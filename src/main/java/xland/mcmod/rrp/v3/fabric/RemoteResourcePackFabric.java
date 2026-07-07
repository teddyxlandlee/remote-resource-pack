package xland.mcmod.rrp.v3.fabric;

//? if fabric {
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.packs.repository.PackRepository;
import org.apache.commons.io.function.IOSupplier;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import xland.mcmod.rrp.v3.RRPCacheRepoSource;
import xland.mcmod.rrp.v3.RemoteResourcePack;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

@NotNullByDefault
public final class RemoteResourcePackFabric extends RemoteResourcePack {
    private static final RemoteResourcePackFabric INSTANCE = new RemoteResourcePackFabric();

    public static RemoteResourcePackFabric getInstance() {
        return INSTANCE;
    }

    public static void init() {
        RemoteResourcePack.init();
    }

    protected Path getGameDir() {
        return FabricLoader.getInstance()
                .getGameDir()
                .toAbsolutePath()
                .normalize();
    }

    protected Path getConfigDir() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .toAbsolutePath()
                .normalize();
    }

    protected Map<String, IOSupplier<BufferedReader>> getModsBuiltinConfigs() {
        return FabricLoader.getInstance().getAllMods().stream()
                .flatMap(c -> c.findPath("RemoteResourcePack.json").stream().map(
                        p -> Map.entry(c.getMetadata().getId(), (IOSupplier<BufferedReader>) () -> Files.newBufferedReader(p)))
                )
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static String getModVersion(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(modContainer -> modContainer.getMetadata().getVersion().getFriendlyString())
                .orElseThrow(() -> new RuntimeException("Can't find " + modId + " mod?!"));
    }

    protected String modVersion() {
        return getModVersion(RemoteResourcePack.MOD_ID);
    }

    @ApiStatus.Internal
    public static void addPackSource(PackRepository packRepository) {
        ((MutablePackRepository) packRepository).remoteResourcePack$addRepoSource(RRPCacheRepoSource.ofCached());
    }

    protected String minecraftVersion() {
        return getModVersion("minecraft");
    }
}
//?}