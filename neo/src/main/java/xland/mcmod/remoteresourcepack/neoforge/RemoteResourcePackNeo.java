package xland.mcmod.remoteresourcepack.neoforge;

import net.minecraft.server.packs.PackType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import xland.mcmod.remoteresourcepack.RRPCacheRepoSource;
import xland.mcmod.remoteresourcepack.RemoteResourcePack;

@Mod(RemoteResourcePack.MOD_ID)
public class RemoteResourcePackNeo {
    public RemoteResourcePackNeo(Dist dist, ModContainer modContainer, IEventBus bus) {
        if (!dist.isClient())
            throw new IllegalStateException("Mod " + RemoteResourcePack.MOD_ID + " is client-only!");
        modVersion = modContainer.getModInfo().getVersion().toString();

        RemoteResourcePack.init();
        bus.addListener(AddPackFindersEvent.class, event -> {
            if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
            event.addRepositorySource(new RRPCacheRepoSource(RemoteResourcePack.getCacheFiles()));
        });
    }

    static String modVersion;
}
