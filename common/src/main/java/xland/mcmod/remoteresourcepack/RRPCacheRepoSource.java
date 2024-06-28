package xland.mcmod.remoteresourcepack;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.DetectedVersion;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

public class RRPCacheRepoSource implements RepositorySource {
    // Description: `%s (Remote cache)`
    private static final PackSource PACK_SOURCE = PackSource.create(component2 ->
            Component.translatable("pack.nameAndSource",
                    component2,
                    Component.translatable("pack.source.mod.remoteresourcepack")
            ).withStyle(ChatFormatting.GRAY), /*loadedOnStart=*/true);

    private static final Gson GSON = new Gson();    // for autogen pack.mcmeta

    private final Map<String, Path> knownCaches;

    public RRPCacheRepoSource(Map<String, Path> knownCaches) {
        this.knownCaches = Collections.unmodifiableMap(knownCaches);
    }

    @Override
    public void loadPacks(@NotNull Consumer<Pack> consumer) {
        for (Map.Entry<String, Path> entry : knownCaches.entrySet()) {
            String packId = "RemoteResourcePack/" + entry.getKey();
            Path zipFile = entry.getValue();
            Pack.ResourcesSupplier resourcesSupplier = new WrapperSupplier(getZipFile(zipFile));
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

    private static final class WrapperSupplier implements Pack.ResourcesSupplier {
        private final FilePackResources.FileResourcesSupplier supplierWrapped;

        private WrapperSupplier(File file) {
            this.supplierWrapped = new FilePackResources.FileResourcesSupplier(file);
        }

        @Override
        public @NotNull PackResources openPrimary(PackLocationInfo packLocationInfo) {
            return new MetaFixedPackResourcesWrapper(supplierWrapped.openPrimary(packLocationInfo));
        }

        @Override
        public @NotNull PackResources openFull(PackLocationInfo packLocationInfo, Pack.Metadata metadata) {
            return new MetaFixedPackResourcesWrapper(supplierWrapped.openFull(packLocationInfo, metadata));
        }
    }

    private static final class MetaFixedPackResourcesWrapper extends AbstractPackResources {
        private final PackResources wrapped;

        private static final JsonObject STUB_OBJ = new JsonObject();
        private byte[] packMcmetaModified;
        private static final String[] packMcmeta = {"pack.mcmeta"};

        private MetaFixedPackResourcesWrapper(PackResources wrapped) {
            super(wrapped.location());
            this.wrapped = wrapped;
        }

        @Nullable
        @Override
        public <T> T getMetadataSection(MetadataSectionSerializer<T> metadataSectionSerializer) throws IOException {
            final IoSupplier<InputStream> rootResource = this.getRootResource(packMcmeta);
            if (rootResource == null) {
                throw new FileNotFoundException(location().id() + "/pack.mcmeta");
            }

            if (packMcmetaModified == null) {
                JsonObject obj = GsonHelper.parse(new InputStreamReader(rootResource.get()));
                final int packVersion = DetectedVersion.BUILT_IN.getPackVersion(PackType.CLIENT_RESOURCES);

                final JsonObject pack1 = GsonHelper.getAsJsonObject(obj, "pack", STUB_OBJ);
                if (GsonHelper.getAsInt(pack1, "pack_format", -1) != packVersion)
                    RemoteResourcePack.LOGGER.warn("Remote pack {} has invalid pack_format", location().id());
                // overwrite pack_format in case marked as incompatible and refused to enable
                pack1.addProperty("pack_format", packVersion);

                packMcmetaModified = GSON.toJson(obj).getBytes(StandardCharsets.UTF_8);
            }
            var bis = new ByteArrayInputStream(packMcmetaModified);
            return getMetadataFromStream(metadataSectionSerializer, bis);
        }

        @Nullable
        @Override
        public IoSupplier<InputStream> getRootResource(String... strings) {
            return wrapped.getRootResource(strings);
        }

        @Nullable
        @Override
        public IoSupplier<InputStream> getResource(PackType packType, ResourceLocation resourceLocation) {
            return wrapped.getResource(packType, resourceLocation);
        }

        @Override
        public void listResources(PackType packType, String string, String string2, ResourceOutput resourceOutput) {
            wrapped.listResources(packType, string, string2, resourceOutput);
        }

        @Override
        public @NotNull Set<String> getNamespaces(PackType packType) {
            return wrapped.getNamespaces(packType);
        }

        @Override
        public void close() {
            wrapped.close();
        }
    }
}
