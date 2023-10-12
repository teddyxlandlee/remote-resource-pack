package xland.mcmod.remoteresourcepack.fabric;

import net.minecraft.obfuscate.DontObfuscate;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.jetbrains.annotations.ApiStatus;
import xland.mcmod.remoteresourcepack.RRPCacheRepoSource;
import xland.mcmod.remoteresourcepack.RemoteResourcePack;

@ApiStatus.Internal
public interface MutablePackRepository {
    @DontObfuscate
    void remoteResourcePack$addRepoSource(RepositorySource repositorySource);

    @DontObfuscate
    @SuppressWarnings("unused")
    static void hookAddRepoSource(PackRepository repository) {
        ((MutablePackRepository) repository).remoteResourcePack$addRepoSource(
                new RRPCacheRepoSource(RemoteResourcePack.getCacheFiles())
        );
    }
}
