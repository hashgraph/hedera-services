package com.swirlds.virtualmap.constructable.constructors;

import com.swirlds.config.api.Configuration;
import com.swirlds.virtualmap.VirtualMap;

@FunctionalInterface
public interface VirtualMapConstructor {
    VirtualMap create(final Configuration configuration);
}
