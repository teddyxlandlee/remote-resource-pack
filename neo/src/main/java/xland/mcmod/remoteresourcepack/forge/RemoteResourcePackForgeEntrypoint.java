package xland.mcmod.remoteresourcepack.forge;

import net.minecraft.server.packs.PackType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;
import xland.mcmod.remoteresourcepack.RRPCacheRepoSource;
import xland.mcmod.remoteresourcepack.RemoteResourcePack;

@Mod(RemoteResourcePack.MOD_ID)
public class RemoteResourcePackForgeEntrypoint {
    // 1.21.9+
    public RemoteResourcePackForgeEntrypoint(FMLJavaModLoadingContext context) {
        if (FMLLoader.getDist() == Dist.DEDICATED_SERVER) {
            throw new IllegalStateException(RemoteResourcePack.MOD_ID + " is client-only!");
        }
        modVersion = context.getContainer().getModInfo().getVersion().toString();

        RemoteResourcePack.init();
        AddPackFindersEvent.BUS.addListener(event -> {
            if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
            event.addRepositorySource(new RRPCacheRepoSource(RemoteResourcePack.getCacheFiles()));
        });
    }

    static volatile String modVersion;
}
