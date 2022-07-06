/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.test.utils;

import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.RuntimeConstructable;
import io.github.classgraph.ClassGraph;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public class ClassLoaderHelper {
    /**
     * Register all Hedera and Swirlds classpath dependencies into {@link ConstructableRegistry} so
     * that they can be deserialized properly.
     *
     * @param additionalPrefixes string containing package prefixes to whitelist (optional, can be
     *     omitted).
     */
    public static void loadClassPathDependencies(String... additionalPrefixes) {
        ClassGraph classGraph = new ClassGraph();
        URL[] urlList = classGraph.getClasspathURLs().toArray(new URL[] {});

        List<String> prefixes = new ArrayList<>();
        prefixes.add("com.hedera");
        prefixes.add("com.swirlds");
        prefixes.addAll(Arrays.stream(additionalPrefixes).toList());

        var result =
                classGraph
                        .enableClassInfo()
                        .enableMethodInfo()
                        .whitelistPackages(prefixes.toArray(new String[] {}))
                        .overrideClasspath(urlList)
                        .scan();

        for (var info : result.getAllClasses()) {
            if (!info.implementsInterface(RuntimeConstructable.class.getName())) {
                continue;
            }
            try {
                Constructor<?> constructor = info.loadClass().getConstructor();
                if (info == null) {
                    continue;
                }
                // Check if class id is already registered and avoid registering twice.
                RuntimeConstructable object = (RuntimeConstructable) constructor.newInstance();
                long classId = object.getClassId();
                if (ConstructableRegistry.getConstructor(classId) == null) {
                    ConstructableRegistry.registerConstructable(
                            new ClassConstructorPair(
                                    object.getClass(), tryOrNull(constructor::newInstance)));
                }
            } catch (Exception e) {
                // Skip class since not valid
                continue;
            }
        }
    }

    /** Returns a new object from a CheckedSupplier, or null if exception occurred. */
    private static <T> Supplier<T> tryOrNull(CheckedSupplier<?> supplierFn) {
        return () -> {
            try {
                return (T) supplierFn.get();
            } catch (Exception e) {
                return null;
            }
        };
    }

    /**
     * Supplier that can throw an exception.
     *
     * @param <T> the class type that is constructed.
     */
    interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
