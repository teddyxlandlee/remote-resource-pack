package xland.mcmod.remoteresourcepack.forge;

import net.minecraft.server.packs.PackType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;
import xland.mcmod.remoteresourcepack.RRPCacheRepoSource;
import xland.mcmod.remoteresourcepack.RemoteResourcePack;

@Mod(RemoteResourcePack.MOD_ID)
public class RemoteResourcePackForge {
    public RemoteResourcePackForge() {
        if (FMLLoader.getDist() == Dist.DEDICATED_SERVER) {
            throw new IllegalStateException(RemoteResourcePack.MOD_ID + " is client-only!");
        }

        RemoteResourcePack.init();
    }

    @Mod.EventBusSubscriber(modid = RemoteResourcePack.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Listener {
        @SubscribeEvent
        public static void addPackFinder(AddPackFindersEvent event) {
            if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
            event.addRepositorySource(new RRPCacheRepoSource(RemoteResourcePack.getCacheFiles()));
        }
    }
}
