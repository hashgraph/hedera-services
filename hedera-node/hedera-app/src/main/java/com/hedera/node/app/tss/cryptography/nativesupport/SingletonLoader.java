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

package com.hedera.node.app.tss.cryptography.nativesupport;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A convenience class to load a singleton instance of a native library. It does not load the library until the first
 * call to {@link #getInstance()} is made. It ensures that the library is loaded only once. It is thread-safe.
 *
 * @param <T> the type of the singleton instance
 */
public class SingletonLoader<T> {
    private final NativeLibrary nativeLibrary;
    private final T instance;
    private final AtomicBoolean libraryLoaded = new AtomicBoolean(false);

    /**
     * Creates a new instance of the loader.
     *
     * @param libraryName the name of the library to load
     * @param instance    the singleton instance to use
     */
    public SingletonLoader(@NonNull final String libraryName, @NonNull final T instance) {
        this.nativeLibrary = NativeLibrary.withName(Objects.requireNonNull(libraryName));
        this.instance = Objects.requireNonNull(instance);
    }

    /**
     * Returns the singleton instance of the library. On the first call, it loads the native library.
     *
     * @return the singleton
     */
    public @NonNull T getInstance() {
        if (!libraryLoaded.get()) {
            synchronized (this) {
                if (libraryLoaded.get()) {
                    return instance;
                }
                nativeLibrary.install(instance.getClass());
                libraryLoaded.set(true);
            }
        }
        return instance;
    }

    /**
     * Returns the package name of the native library is located. This is useful if you need to open the package
     * programmatically.
     *
     * @return the package name
     */
    public String getNativeLibraryPackageName() {
        return nativeLibrary.packageNameOfResource();
    }
}
