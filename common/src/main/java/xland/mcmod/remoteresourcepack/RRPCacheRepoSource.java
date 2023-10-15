package xland.mcmod.remoteresourcepack;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.bridge.game.PackType;
import net.minecraft.ChatFormatting;
import net.minecraft.DetectedVersion;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
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
    public void loadPacks(Consumer<Pack> consumer) {
        for (Map.Entry<String, Path> entry : knownCaches.entrySet()) {
            String packId = "RemoteResourcePack/" + entry.getKey();
            final FileSystem zipFS = readZip(entry.getValue());
            Pack pack = Pack.readMetaAndCreate(
                    packId,
                    Component.translatable("pack.source.mod.remoteresourcepack")
                            .append(" #")
                            .append(packId.substring(19 /*prefix len*/, Math.min(packId.length(), 27))),
                    /*required=*/false,
                    (String packName) -> new PathPackResources(packName, zipFS.getPath("/"), false) {
                        private static final JsonObject STUB_OBJ = new JsonObject();
                        private byte[] packMcmetaModified;
                        private static final String[] packMcmeta = {"pack.mcmeta"};

                        @Nullable
                        @Override
                        public IoSupplier<InputStream> getRootResource(String... strings) {
                            return Arrays.equals(packMcmeta, strings) ? getPackMeta() : super.getRootResource(strings);
                        }

                        @NotNull
                        private IoSupplier<InputStream> getPackMeta() {
                            // here throws FNF if pack.mcmeta not found
                            final IoSupplier<InputStream> rootResource = super.getRootResource("pack.mcmeta");
                            if (rootResource == null)
                                return () -> {
                                    throw new FileNotFoundException(entry.getKey() + "/pack.mcmeta");
                                };
                            return () -> {
                                if (packMcmetaModified == null) {
                                    JsonObject obj = GsonHelper.parse(new InputStreamReader(rootResource.get()));
                                    final int packVersion = DetectedVersion.BUILT_IN.getPackVersion(PackType.RESOURCE);

                                    final JsonObject pack1 = GsonHelper.getAsJsonObject(obj, "pack", STUB_OBJ);
                                    if (GsonHelper.getAsInt(pack1, "pack_format", -1) != packVersion)
                                        RemoteResourcePack.LOGGER.warn("Remote pack {} has invalid pack_format", entry.getKey());
                                    // overwrite pack_format in case marked as incompatible and refused to enable
                                    pack1.addProperty("pack_format", packVersion);

                                    packMcmetaModified = GSON.toJson(obj).getBytes(StandardCharsets.UTF_8);
                                }
                                return new ByteArrayInputStream(packMcmetaModified);
                            };
                        }

                        @Override
                        public void close() {
                            super.close();
//                            IOUtils.closeQuietly(zipFS);
                        }
                    },
                    net.minecraft.server.packs.PackType.CLIENT_RESOURCES,
                    Pack.Position.TOP,
                    PACK_SOURCE
            );
            consumer.accept(pack);
        }
    }

    private static FileSystem readZip(Path zipFile) {
        try {
            return FileSystems.newFileSystem(zipFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
