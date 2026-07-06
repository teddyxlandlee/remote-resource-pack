package xland.mcmod.remoteresourcepack;

import com.google.gson.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
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

    public static RRPCacheRepoSource ofCached() {
        return new RRPCacheRepoSource(RemoteResourcePack.getCacheFiles());
    }

    private static final Gson GSON = new Gson();
    private static final String FORCE_COMPATIBLE = "remoteresourcepack:force_compatible";

    static byte[] modifyPackMcmeta(final byte @NotNull[] b) {
        Objects.requireNonNull(b, "input bytes shall be non-null");
        try {
            return modifyPackMcmetaImpl(b);
        } catch (Exception e) {
            RemoteResourcePack.LOGGER.warn("Exception while trying to modify a pack.mcmeta. Remaining unchanged.", e);
            return b;
        }
    }

    private static byte[] modifyPackMcmetaImpl(final byte[] b) throws RuntimeException {
        String s = new String(b, StandardCharsets.UTF_8);
        JsonObject rootObj = GSON.fromJson(s, JsonObject.class);
        if (!GsonHelper.getAsBoolean(rootObj, FORCE_COMPATIBLE, false)) {
            // no need to modify
            return b;
        }
        JsonObject packObj = GsonHelper.getAsJsonObject(rootObj, "pack");

        //? if >= 1.21.9 {
        packObj.addProperty("min_format", 65);  // the version that defines min/max_format
        packObj.addProperty("max_format", Integer.MAX_VALUE);
        packObj.remove("supported_formats");
        packObj.remove("pack_format");
        //?} elif >= 1.20.2 {
        /*var supportedFormats = new JsonArray(); {
            supportedFormats.add(16);   // the version that defines supported_formats
            supportedFormats.add(64);   // the next version defines min_format
        }
        packObj.add("supported_formats", supportedFormats);
        packObj.addProperty("pack_format", 16);
        packObj.remove("min_format");
        packObj.remove("max_format");
        *///?} else {
        /*// Legacy Minecraft was a noob
        packObj.addProperty("pack_format", net.minecraft.DetectedVersion.BUILT_IN.getPackVersion(PackType.CLIENT_RESOURCES));
        packObj.remove("min_format");
        packObj.remove("max_format");
        packObj.remove("supported_formats");
        *///?}
        return rootObj.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void loadPacks(@NotNull Consumer<Pack> consumer) {
        for (Map.Entry<String, Path> entry : knownCaches.entrySet()) {
            final String packId = RemoteResourcePack.packName(entry.getKey());
            final Path zipFile = entry.getValue();
            // Now we don't support pack.mcmeta force-modification
            final Component packDescription = Component.translatable("pack.source.mod.remoteresourcepack")
                    .append(" #")
                    .append(packId.substring(19 /*prefix len*/, Math.min(packId.length(), 27)));
            //? if > 1.20.1 {
            Pack.ResourcesSupplier resourcesSupplier = new FilePackResources.FileResourcesSupplier(getZipFile(zipFile));
            final Pack pack = Pack.readMetaAndCreate(
                    new PackLocationInfo(
                            packId,
                            packDescription,
                            PACK_SOURCE,
                            Optional.empty()
                    ),
                    resourcesSupplier,
                    PackType.CLIENT_RESOURCES,
                    new PackSelectionConfig(/*required=*/false, Pack.Position.TOP, /*fixedPosition=*/false)
            );
            //?} else {
            /*final Pack pack = Pack.readMetaAndCreate(
                    packId,
                    packDescription,
                    /^required=^/false,
                    (final String packName) -> new FilePackResources(packName, getZipFile(zipFile), /^isBuiltin=^/false),
                    PackType.CLIENT_RESOURCES,
                    Pack.Position.TOP,
                    PACK_SOURCE
            );
            *///?}
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
