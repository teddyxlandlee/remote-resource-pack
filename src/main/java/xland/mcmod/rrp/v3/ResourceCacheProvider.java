/*
 * Remote Resource Pack: load resource packs to which contents are downloaded from the internet.
 * Copyright (c) 2026 teddyxlandlee & contributors.
 * SPDX-License-Identifier: MIT
 */
package xland.mcmod.rrp.v3;

import org.jetbrains.annotations.UnmodifiableView;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;

public interface ResourceCacheProvider {
    @UnmodifiableView   // (AsciiURI -> Etag)
    Map<String, String> getEtags();

    InputStream readCache(URI uri, String etag) throws IOException;  // contains FileNotFoundException
}
