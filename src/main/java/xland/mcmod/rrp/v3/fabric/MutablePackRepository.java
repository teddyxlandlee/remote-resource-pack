/*
 * Remote Resource Pack: load resource packs to which contents are downloaded from the internet.
 * Copyright (c) 2023-2026 teddyxlandlee & contributors.
 * SPDX-License-Identifier: MIT
 */
package xland.mcmod.rrp.v3.fabric;

//? if fabric {
import net.minecraft.server.packs.repository.RepositorySource;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface MutablePackRepository {
    void remoteResourcePack$addRepoSource(RepositorySource repositorySource);
}
//?}