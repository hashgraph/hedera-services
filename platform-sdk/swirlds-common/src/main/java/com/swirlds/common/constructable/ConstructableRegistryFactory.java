/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.constructable;

import com.swirlds.common.constructable.internal.DefaultConstructableRegistry;
import com.swirlds.common.constructable.internal.GenericConstructorRegistry;

/**
 * Creates instances of {@link ConstructableRegistry} and {@link ConstructorRegistry}
 */
public final class ConstructableRegistryFactory {
    private ConstructableRegistryFactory() {}

    /**
     * @return a new instance of {@link ConstructableRegistry}
     */
    public static ConstructableRegistry createConstructableRegistry() {
        return new DefaultConstructableRegistry();
    }

    /**
     * @param constructorType
     * 		a class that represents the constructor type
     * @param <T>
     * 		the type of constructor used
     * @return a new instance of {@link ConstructorRegistry}
     */
    public static <T> ConstructorRegistry<T> createConstructorRegistry(final Class<T> constructorType) {
        return new GenericConstructorRegistry<>(constructorType);
    }
}
