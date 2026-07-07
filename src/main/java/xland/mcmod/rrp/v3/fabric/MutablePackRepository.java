package xland.mcmod.rrp.v3.fabric;

//? if fabric {
import net.minecraft.server.packs.repository.RepositorySource;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface MutablePackRepository {
    void remoteResourcePack$addRepoSource(RepositorySource repositorySource);
}
//?}