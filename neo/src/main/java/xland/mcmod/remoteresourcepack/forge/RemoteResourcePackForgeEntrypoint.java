package xland.mcmod.remoteresourcepack.forge;

import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.bus.EventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;
import xland.mcmod.remoteresourcepack.RRPCacheRepoSource;
import xland.mcmod.remoteresourcepack.RemoteResourcePack;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

@Mod(RemoteResourcePack.MOD_ID)
public class RemoteResourcePackForgeEntrypoint {
    // 1.21.9+
    public RemoteResourcePackForgeEntrypoint(FMLJavaModLoadingContext context) {
        if (FMLLoader.getDist() == Dist.DEDICATED_SERVER) {
            throw new IllegalStateException(RemoteResourcePack.MOD_ID + " is client-only!");
        }
        modVersion = context.getContainer().getModInfo().getVersion().toString();

        RemoteResourcePack.init();

        var lookup = MethodHandles.lookup();
        final Class<?> C_AddPackFindersEvent;
        final EventBus<?> bus;
        final MethodHandle M_getPackType;
        final MethodHandle M_addRepositorySource;

        try {
            C_AddPackFindersEvent = lookup.findClass("net.minecraftforge.event.AddPackFindersEvent");
            var busHandle = lookup.findStaticVarHandle(C_AddPackFindersEvent, "BUS", EventBus.class);
            bus = (EventBus<?>) busHandle.get();

            M_getPackType = lookup.findVirtual(
                    C_AddPackFindersEvent, "getPackType",
                    MethodType.methodType(PackType.class)
            );
            M_addRepositorySource = lookup.findVirtual(
                    C_AddPackFindersEvent, "addRepositorySource",
                    MethodType.methodType(void.class, RepositorySource.class)
            );
        } catch (ClassNotFoundException | IllegalAccessException | NoSuchFieldException | NoSuchMethodException e) {
            throw new IllegalStateException("Corrupted Forge environment");
        }

        bus.addListener(event -> {
            try {
                if (M_getPackType.bindTo(event).invoke() != PackType.CLIENT_RESOURCES) return;
                M_addRepositorySource.bindTo(event).invoke(new RRPCacheRepoSource(RemoteResourcePack.getCacheFiles()));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger(RemoteResourcePackForgeEntrypoint.class).warn(
                        "Cannot register RRPCacheRepoSource under this Forge environment. "
                        + "Forge environment will be unsupported by RemoteResourcePack soon.", t
                );
            }
        });
    }

    static volatile String modVersion;
}
