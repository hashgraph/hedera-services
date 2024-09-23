package com.swirlds.virtualmap.constructable.constructors;

import com.swirlds.virtualmap.config.VirtualMapConfig;
import com.swirlds.virtualmap.internal.cache.VirtualNodeCache;

@FunctionalInterface
public interface VirtualNodeCacheConstructor {
    VirtualNodeCache create(final VirtualMapConfig vmConfig);
}

