package xland.mcmod.remoteresourcepack.forge;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.versions.mcp.MCPVersion;
import org.apache.commons.io.function.IOSupplier;
import xland.mcmod.remoteresourcepack.RemoteResourcePack;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

public class RemoteResourcePackImpl extends RemoteResourcePack {
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
                modFile.getModInfos().getFirst().getModId(),
                Optional.of(modFile.findResource("RemoteResourcePack.json")).filter(Files::exists)
        ))
                .flatMap(e -> e.getValue().map(
                        v -> Map.entry(e.getKey(), (IOSupplier<BufferedReader>) () -> Files.newBufferedReader(v))
                ).stream())
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    protected String modVersion() {
        return ModList.get().getModContainerById(MOD_ID)
                .orElseThrow(() -> new NoSuchElementException(MOD_ID))
                .getModInfo()
                .getVersion()
                .toString();
    }

    @Override
    protected String minecraftVersion() {
        return MCPVersion.getMCVersion();
    }

    private static final RemoteResourcePackImpl INSTANCE = new RemoteResourcePackImpl();
    public static RemoteResourcePack platform() { return INSTANCE; }
}
