package xland.mcmod.remoteresourcepack.fabric;

import net.minecraft.server.packs.repository.RepositorySource;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface MutablePackRepository {
    void remoteResourcePack$addRepoSource(RepositorySource repositorySource);
}
