package xland.mcmod.remoteresourcepack;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.bridge.game.PackType;
import net.minecraft.DetectedVersion;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

public class RRPCacheRepoSource implements RepositorySource {
    // Description: `%s (Remote cache)`
    private static final PackSource PACK_SOURCE = PackSource.decorating("pack.source.mod.remoteresourcepack");
    private static final Gson GSON = new Gson();    // for autogen pack.mcmeta

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
                    () -> new FilePackResources(file) {
                        private static final JsonObject STUB_OBJ = new JsonObject();
                        private byte[] packMcmetaModified;

                        @Override
                        protected @NotNull InputStream getResource(String path) throws IOException {
                            // here throws FNF if pack.mcmeta not found
                            final InputStream parentStream = super.getResource(path);
                            if (!"pack.mcmeta".equals(path)) return parentStream;

                            if (packMcmetaModified == null) {
                                JsonObject obj = GsonHelper.parse(new InputStreamReader(parentStream));
                                final int packVersion = DetectedVersion.BUILT_IN.getPackVersion(PackType.RESOURCE);

                                final JsonObject pack1 = GsonHelper.getAsJsonObject(obj, "pack", STUB_OBJ);
                                if (GsonHelper.getAsInt(pack1, "pack_format", -1) != packVersion)
                                    RemoteResourcePack.LOGGER.warn("Remote pack {} has invalid pack_format", entry.getKey());
                                // overwrite pack_format in case marked as incompatible and refused to enable
                                pack1.addProperty("pack_format", packVersion);

                                packMcmetaModified = GSON.toJson(obj).getBytes(StandardCharsets.UTF_8);
                            }
                            return new ByteArrayInputStream(packMcmetaModified);
                        }
                    },
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
