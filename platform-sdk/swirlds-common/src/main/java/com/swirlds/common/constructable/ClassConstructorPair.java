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

import java.util.function.Supplier;

/**
 * @deprecated see {@link ConstructableRegistry#registerConstructable(ClassConstructorPair)}
 */
@Deprecated(forRemoval = true)
public class ClassConstructorPair {
    private final Class<? extends RuntimeConstructable> aClass;
    private final Supplier<RuntimeConstructable> constructor;

    public ClassConstructorPair(
            final Class<? extends RuntimeConstructable> aClass, final Supplier<RuntimeConstructable> constructor) {
        this.aClass = aClass;
        this.constructor = constructor;
    }

    public Class<? extends RuntimeConstructable> getConstructableClass() {
        return aClass;
    }

    public Supplier<RuntimeConstructable> getConstructor() {
        return constructor;
    }

    public boolean classEquals(ClassConstructorPair pair) {
        return this.aClass.equals(pair.getConstructableClass());
    }
}
