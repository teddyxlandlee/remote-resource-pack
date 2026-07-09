/*
 * Remote Resource Pack: load resource packs to which contents are downloaded from the internet.
 * Copyright (c) 2026 teddyxlandlee & contributors.
 * SPDX-License-Identifier: MIT
 */
package xland.mcmod.rrp.v3;

import java.nio.file.Path;

public interface ResourceCacheAccess extends ResourceCacheProvider {
    void pushPending(Path zipFile, Iterable<? extends PendingCache> pendingCache);

    record PendingCache(String filename, String uri, String etag) implements java.io.Serializable {}
}
