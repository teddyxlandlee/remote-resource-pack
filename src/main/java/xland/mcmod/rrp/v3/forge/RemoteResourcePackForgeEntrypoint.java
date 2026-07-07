package xland.mcmod.rrp.v3.forge;

//? if forge {
/*import net.minecraft.server.packs.PackType;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import xland.mcmod.rrp.v3.RRPCacheRepoSource;
import xland.mcmod.rrp.v3.RemoteResourcePack;

import java.util.concurrent.atomic.AtomicReference;

@Mod(RemoteResourcePack.MOD_ID)
public class RemoteResourcePackForgeEntrypoint {
    //? if >=1.21.1 {
    public RemoteResourcePackForgeEntrypoint(final FMLJavaModLoadingContext ctx) {
    //?} else {
    /^public RemoteResourcePackForgeEntrypoint() {
        @SuppressWarnings("removal")
        final FMLJavaModLoadingContext ctx = FMLJavaModLoadingContext.get();
        ^///?}
        if (!FMLEnvironment.dist.isClient()) {
            throw new IllegalStateException("Mod " + RemoteResourcePack.MOD_ID + " is client-only!");
        }
        if (!MOD_VERSION.compareAndSet(null, ctx.getContainer().getModInfo().getVersion().toString())) {
            throw new IllegalStateException(RemoteResourcePack.MOD_ID + " mod is initialized twice");
        }

        RemoteResourcePack.init();
        //? if <=1.21.5 {
        /^ctx.getModEventBus().addListener(RemoteResourcePackForgeEntrypoint::addPackFinder);
        ^///?} else {
        AddPackFindersEvent.BUS.addListener(RemoteResourcePackForgeEntrypoint::addPackFinder);
        //?}
    }

    private static void addPackFinder(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        event.addRepositorySource(RRPCacheRepoSource.ofCached());
    }

    static final AtomicReference<String> MOD_VERSION = new AtomicReference<>();
}
*///?}