package xland.mcmod.rrp.v3.forge;

//? if forge {
/*import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
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
        return ModList/^? if <26 {^//^.get()^//^?}^/.applyForEachModFile(modFile -> {
            final var path = modFile.findResource("RemoteResourcePack.json");
            if (Files.notExists(path)) return null;

            final IOSupplier<BufferedReader> supplier = () -> Files.newBufferedReader(path);
            return Map.entry(modFile.getModInfos().get(0).getModId(), supplier);
        }).filter(Objects::nonNull).collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
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
