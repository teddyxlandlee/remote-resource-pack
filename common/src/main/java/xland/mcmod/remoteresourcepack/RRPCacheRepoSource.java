package xland.mcmod.remoteresourcepack;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

@SuppressWarnings("ClassCanBeRecord")
public class RRPCacheRepoSource implements RepositorySource {
    // Description: `%s (Remote cache)`
    private static final PackSource PACK_SOURCE = PackSource.create(
            packName -> Component.translatable("pack.nameAndSource",
                    packName,
                    Component.translatable("pack.source.mod.remoteresourcepack")
            ).withStyle(ChatFormatting.GRAY),
            /*loadedOnStart=*/true
    );

    private final Map<String, Path> knownCaches;

    public RRPCacheRepoSource(Map<String, Path> knownCaches) {
        this.knownCaches = Collections.unmodifiableMap(knownCaches);
    }

    @Override
    public void loadPacks(@NotNull Consumer<Pack> consumer) {
        for (Map.Entry<String, Path> entry : knownCaches.entrySet()) {
            String packId = "RemoteResourcePack/" + entry.getKey();
            Path zipFile = entry.getValue();
            // Now we don't support pack.mcmeta force-modification
            Pack.ResourcesSupplier resourcesSupplier = new FilePackResources.FileResourcesSupplier(getZipFile(zipFile));
            Pack pack = Pack.readMetaAndCreate(
                    new PackLocationInfo(
                            packId,
                            Component.translatable("pack.source.mod.remoteresourcepack")
                            .append(" #")
                            .append(packId.substring(19 /*prefix len*/, Math.min(packId.length(), 27))),
                            PACK_SOURCE,
                            Optional.empty()
                    ),
                    resourcesSupplier,
                    PackType.CLIENT_RESOURCES,
                    new PackSelectionConfig(false, Pack.Position.TOP, false)
            );
            Objects.requireNonNull(pack, () -> "Missing pack meta for " + packId);
            consumer.accept(pack);
        }
    }

    private static File getZipFile(Path zipFile) {
        try {
            return zipFile.toFile();
        } catch (UnsupportedOperationException e) {
            try {
                var file = File.createTempFile("RRPCache", ".zip");
                file.deleteOnExit();
                Files.copy(zipFile, file.toPath());
                return file;
            } catch (IOException ex) {
                throw new UncheckedIOException(
                        "File " + zipFile + " (in filesystem " + zipFile.getFileSystem() +
                                "), failed to copy to temp file",
                        ex
                );
            }
        }
    }
}
