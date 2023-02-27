/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.constructable.internal;

import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.constructable.ConstructorRegistry;
import com.swirlds.common.constructable.NoArgsConstructor;
import com.swirlds.common.constructable.RuntimeConstructable;
import com.swirlds.common.constructable.URLClassLoaderWithLookup;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class DefaultConstructableRegistry implements ConstructableRegistry {
    private final Map<Class<?>, GenericConstructorRegistry<?>> allRegistries = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> ConstructorRegistry<T> getRegistry(final Class<T> constructorType) {
        return (ConstructorRegistry<T>) allRegistries.get(constructorType);
    }

    @Override
    public Supplier<RuntimeConstructable> getConstructor(final long classId) {
        return getOrCreate(NoArgsConstructor.class).getConstructor(classId);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends RuntimeConstructable> T createObject(final long classId) {
        final Supplier<RuntimeConstructable> c = getConstructor(classId);
        if (c == null) {
            return null;
        }
        return (T) c.get();
    }

    @Override
    public void registerConstructables(final String packagePrefix, final URLClassLoaderWithLookup additionalClassloader)
            throws ConstructableRegistryException {
        final Collection<ConstructableClasses<?>> scanResults =
                ConstructableScanner.getConstructableClasses(packagePrefix, additionalClassloader);
        for (final ConstructableClasses<?> constructableClasses : scanResults) {
            getOrCreate(constructableClasses.getConstructorType())
                    .registerConstructables(constructableClasses, additionalClassloader);
        }
    }

    @Override
    public void registerConstructables(final String packagePrefix) throws ConstructableRegistryException {
        registerConstructables(packagePrefix, null);
    }

    @Override
    public void registerConstructable(final ClassConstructorPair pair) throws ConstructableRegistryException {
        getOrCreate(NoArgsConstructor.class)
                .registerConstructable(pair.getConstructableClass(), pair.getConstructor()::get);
    }

    @Override
    public void reset() {
        allRegistries.clear();
    }

    @SuppressWarnings("unchecked")
    private <T> GenericConstructorRegistry<T> getOrCreate(final Class<T> constructor) {
        return (GenericConstructorRegistry<T>)
                allRegistries.computeIfAbsent(constructor, GenericConstructorRegistry::new);
    }
}
