/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.virtualmap.constructable;

import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.config.api.Configuration;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.config.VirtualMapConfig;
import com.swirlds.virtualmap.internal.cache.VirtualNodeCache;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;

/**
 * Utility class for registering constructable objects from the {@code com.swirlds.virtualmap} package
 * in the {@link ConstructableRegistry}.
 */
public final class ConstructableUtils {
    private ConstructableUtils() {}

    /**
     * Add Virtual Map classes to the constructable registry which need the configuration.
     * @param configuration configuration
     */
    public static void registerVirtualMapConstructables(Configuration configuration)
            throws ConstructableRegistryException {
        ConstructableRegistry.getInstance()
                .registerConstructable(new ClassConstructorPair(VirtualMap.class, () -> new VirtualMap(configuration)));
        ConstructableRegistry.getInstance()
                .registerConstructable(new ClassConstructorPair(
                        VirtualNodeCache.class,
                        () -> new VirtualNodeCache(configuration.getConfigData(VirtualMapConfig.class))));
        ConstructableRegistry.getInstance()
                .registerConstructable(new ClassConstructorPair(
                        VirtualRootNode.class,
                        () -> new VirtualRootNode(configuration.getConfigData(VirtualMapConfig.class))));
    }
}
