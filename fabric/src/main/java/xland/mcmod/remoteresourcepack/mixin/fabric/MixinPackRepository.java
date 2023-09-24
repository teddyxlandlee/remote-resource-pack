package xland.mcmod.remoteresourcepack.mixin.fabric;

import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import xland.mcmod.remoteresourcepack.fabric.MutablePackRepository;

import java.util.HashSet;
import java.util.Set;

@Mixin(PackRepository.class)    // client only
abstract class MixinPackRepository implements MutablePackRepository {
    @Accessor("sources")
    abstract Set<RepositorySource> remoteResourcePack$getSources();

    @Mutable
    @Accessor("sources")
    abstract void remoteResourcePack$setSources(Set<RepositorySource> set);

    @Override
    public void remoteResourcePack$addRepoSource(RepositorySource repositorySource) {
        Set<RepositorySource> set = remoteResourcePack$getSources();
        try {
            set.add(repositorySource);
        } catch (UnsupportedOperationException e) {
            set = new HashSet<>(set);
            set.add(repositorySource);
            remoteResourcePack$setSources(set);
        }
    }
}
