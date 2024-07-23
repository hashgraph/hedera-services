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

package com.hedera.nativesupport;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.Objects;

/**
 *
 */
public interface LibraryLoader {

    /**
     * A No-op Library Loader to return in case the expected {@link Architecture} or {@link OperatingSystem} doesn't match
     */
    LibraryLoader NO_OP_LOADER = (libraryName, libExtensions) -> {
        Objects.requireNonNull(libraryName, "libraryName must not be null");
        Objects.requireNonNull(libExtensions, "libExtensions must not be null");
    };

    /**
     * Default extensions for binary libraries per OS
     */
    Map<OperatingSystem, String> DEFAULT_LIB_EXTENSIONS =
            Map.of(OperatingSystem.WINDOWS, "dll", OperatingSystem.LINUX, "so", OperatingSystem.DARWIN, "dylib");

    /**
     * installs a library assuming the default lib extensions
     *
     * @param libraryName the library to load.
     * @implNote This method expects the executable to be present at the following location in the JAR file:
     * {@code /software/<os>/<arch>/libraryName}.
     */
    default void install(@NonNull String libraryName) {
        install(libraryName, DEFAULT_LIB_EXTENSIONS);
    }

    /**
     * installs a library
     *
     * @param libraryName the library to load.
     * @param libExtensions defaults extensions for each os to use to load the library
     * @implNote This method expects the executable to be present at the following location in the JAR file:
     * {@code /software/<os>/<arch>/libraryName}.
     */
    void install(@NonNull String libraryName, @NonNull Map<OperatingSystem, String> libExtensions);

    /**
     * Returns an instance of a LibraryLoader for the current combination of Architecture and OS
     * @param caller caller class located in the same jar that the library wanted to be loaded
     * @return an instance of {@link LibraryLoader}
     */
    @NonNull
    static LibraryLoader create(@NonNull final Class<?> caller) {
        return new LibraryLoaderImpl(new ResourceLoader(Objects.requireNonNull(caller, "caller must not be null")));
    }

    /**
     * Returns an instance of a LibraryLoader for the requested combination of Architecture and OS.
     * If the system does not match the requested combination a no-op version of the libraryLoader is returned.
     * @param caller caller class located in the same jar that the library wanted to be loaded
     * @return an instance of {@link LibraryLoader}
     */
    @NonNull
    static LibraryLoader create(
            @NonNull final Class<?> caller,
            @NonNull final Architecture architecture,
            @NonNull final OperatingSystem operatingSystem) {
        if (Architecture.current() != Objects.requireNonNull(architecture, "architecture must not be null")
                || OperatingSystem.current()
                        != Objects.requireNonNull(operatingSystem, "operatingSystem must not be null")) {
            return NO_OP_LOADER;
        } else
            return new LibraryLoaderImpl(new ResourceLoader(Objects.requireNonNull(caller, "caller must not be null")));
    }
}
