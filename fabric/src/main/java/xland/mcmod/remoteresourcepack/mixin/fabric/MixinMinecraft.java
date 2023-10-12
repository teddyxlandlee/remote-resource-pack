package xland.mcmod.remoteresourcepack.mixin.fabric;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import xland.mcmod.remoteresourcepack.fabric.asm.TransformTarget;

@Mixin(Minecraft.class)
@TransformTarget
public abstract class MixinMinecraft {
//    @Invoker("getResourcePackRepository")
//    public abstract PackRepository remoteResourcePack$getResourcePackRepository();
//
//    @Inject(method = "<init>", at = @At(
//            value = "INVOKE", target = "Lnet/minecraft/server/packs/repository/PackRepository;reload()V"
//    ))
//    private void injectResourcePacks(GameConfig gameConfig, CallbackInfo ci) {
//        @SuppressWarnings("all")
//        final PackRepository packRepository = this.remoteResourcePack$getResourcePackRepository();
////        ((MutablePackRepository) packRepository).remoteResourcePack$addRepoSource(
////                new RRPCacheRepoSource(RemoteResourcePack.getCacheFiles()));
//        MutablePackRepository.hookAddRepoSource(packRepository);
////        RemoteResourcePack.insertEnabledPacks(packRepository);
//    }
//
//    @Inject(method = "<init>", at = @At(
//            value = "INVOKE_ASSIGN",
//            target = "Lnet/minecraft/server/packs/repository/PackRepository;reload()V"
//    ))
//    private void insertEnabledPacks(GameConfig gameConfig, CallbackInfo ci) {
//        RemoteResourcePack.insertEnabledPacks(remoteResourcePack$getResourcePackRepository());
//    }
}
