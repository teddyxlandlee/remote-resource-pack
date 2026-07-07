package xland.mcmod.rrp.v3.neoforge;

//? if neoforge {
/*import net.minecraft.server.packs.PackType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import xland.mcmod.rrp.v3.RRPCacheRepoSource;
import xland.mcmod.rrp.v3.RemoteResourcePack;

@Mod(RemoteResourcePack.MOD_ID)
public class RemoteResourcePackNeoEntrypoint {
    public RemoteResourcePackNeoEntrypoint(Dist dist, ModContainer modContainer, IEventBus bus) {
        if (!dist.isClient())
            throw new IllegalStateException("Mod " + RemoteResourcePack.MOD_ID + " is client-only!");
        modVersion = modContainer.getModInfo().getVersion().toString();

        RemoteResourcePack.init();
        bus.addListener(AddPackFindersEvent.class, event -> {
            if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
            event.addRepositorySource(RRPCacheRepoSource.ofCached());
        });
    }

    static volatile String modVersion;
}
*///?}