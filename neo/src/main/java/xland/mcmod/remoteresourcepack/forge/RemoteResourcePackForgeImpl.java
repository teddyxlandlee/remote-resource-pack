package xland.mcmod.remoteresourcepack.forge;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.commons.io.function.IOSupplier;
import xland.mcmod.remoteresourcepack.Platform;
import xland.mcmod.remoteresourcepack.RemoteResourcePack;

import java.io.BufferedReader;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Platform.Implementation(Platform.FORGE)
public class RemoteResourcePackForgeImpl extends RemoteResourcePack {
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
        return Objects.requireNonNull(RemoteResourcePackForgeEntrypoint.modVersion, "modVersion uninitialized");
    }

    @Override
    protected String minecraftVersion() {
        var lookup = MethodHandles.lookup();
        try {
            var C_MCPVersion = lookup.findClass("net.minecraftforge.versions.mcp.MCPVersion");
            return (String) lookup.findStatic(C_MCPVersion, "getMCVersion", MethodType.methodType(String.class)).invokeExact();
        } catch (Throwable t) {
            throw new RuntimeException("Cannot get Minecraft version", t);
        }
    }
}
