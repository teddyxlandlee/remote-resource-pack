/*
 * Remote Resource Pack: load resource packs to which contents are downloaded from the internet.
 * Copyright (c) 2023-2026 teddyxlandlee & contributors.
 * SPDX-License-Identifier: MIT
 */
package xland.mcmod.rrp.v3.mixin.fabric;

//? if fabric {
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import xland.mcmod.rrp.v3.fabric.MutablePackRepository;

import java.util.HashSet;
import java.util.Set;

@Mixin(PackRepository.class)    // client only
abstract public class MixinPackRepository implements MutablePackRepository {
    @Accessor("sources")
    abstract Set<RepositorySource> remoteResourcePack$getSources();

    @Mutable
    @Accessor("sources")
    abstract void remoteResourcePack$setSources(Set<RepositorySource> set);

    @Override
    public void remoteResourcePack$addRepoSource(RepositorySource repositorySource) {
        Set<RepositorySource> set = remoteResourcePack$getSources();
        if (set.getClass() != HashSet.class) {
            set = new HashSet<>(set);
            remoteResourcePack$setSources(set);
        }
        set.add(repositorySource);
    }
}
//?}
