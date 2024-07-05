package xland.mcmod.remoteresourcepack.forge;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

public final class RemoteResourcePackImpl {
    public static Path getGameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    public static Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    public static Map<String, Path> getModsBuiltinConfigs() {
        return ModList.get().applyForEachModFile(modFile -> Map.entry(modFile.getModInfos().get(0).getModId(),
                        modFile.findResource("RemoteResourcePack.json")))
                .filter(e -> Files.exists(e.getValue()))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
