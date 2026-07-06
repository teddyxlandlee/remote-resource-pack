package xland.mcmod.remoteresourcepack.forge;

//? if forge {
/*import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.commons.io.function.IOSupplier;
import xland.mcmod.remoteresourcepack.RemoteResourcePack;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class RemoteResourcePackForge extends RemoteResourcePack {
    private static final RemoteResourcePackForge INSTANCE = new RemoteResourcePackForge();

    public static RemoteResourcePackForge getInstance() {
        return INSTANCE;
    }

    @Override
    protected Path getGameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    protected Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    protected Map<String, IOSupplier<BufferedReader>> getModsBuiltinConfigs() {
        return ModList.get().applyForEachModFile(modFile -> Map.entry(
                modFile.getModInfos().get(0).getModId(),
                Optional.ofNullable(modFile.findResource("RemoteReesourcePack.json"))
        ))
                .flatMap(e -> e.getValue().map(
                        v -> Map.entry(e.getKey(), (IOSupplier<BufferedReader>) () -> Files.newBufferedReader(v))
                ).stream())
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    protected String modVersion() {
        return Objects.requireNonNull(RemoteResourcePackForgeEntrypoint.MOD_VERSION.get(), "modVersion uninitialized");
    }

    @Override
    protected String minecraftVersion() {
        return FMLLoader.versionInfo().mcVersion();
    }
}
*///?}
