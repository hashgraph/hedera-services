package com.swirlds.virtualmap.constructable.constructors;

import com.swirlds.config.api.Configuration;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;
import edu.umd.cs.findbugs.annotations.NonNull;

@FunctionalInterface
public interface VirtualRootNodeConstructor {
    VirtualRootNode create(final @NonNull Configuration configuration);
}
