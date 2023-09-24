package xland.mcmod.remoteresourcepack;

import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

public class RRPCacheRepoSource implements RepositorySource {
    // Description: `%s (Remote cache)`
    private static final PackSource PACK_SOURCE = PackSource.decorating("pack.source.mod.remoteresourcepack");

    private final Map<String, Path> knownCaches;

    public RRPCacheRepoSource(Map<String, Path> knownCaches) {
        this.knownCaches = Collections.unmodifiableMap(knownCaches);
    }

    @Override
    public void loadPacks(Consumer<Pack> consumer, Pack.PackConstructor packConstructor) {
        for (Map.Entry<String, Path> entry : knownCaches.entrySet()) {
            File file = pathToFile(entry.getValue());
            Pack pack = Pack.create(
                    "RemoteResourcePack/" + entry.getKey(),
                    /*required=*/false,
                    () -> new FilePackResources(file),
                    packConstructor,
                    Pack.Position.TOP,
                    PACK_SOURCE
            );
            consumer.accept(pack);
        }
    }

    private static File pathToFile(Path path) {
        try {
            return path.toFile();
        } catch (UnsupportedOperationException e) {
            try {
                File file = File.createTempFile("RemoteResourcePack", ".zip");
                file.deleteOnExit();
                Files.copy(path, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return file;
            } catch (IOException ex) {
                throw new RuntimeException("Failed to copy " + path, ex);
            }
        }
    }
}
