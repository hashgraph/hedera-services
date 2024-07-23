/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.common.nativesupport;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Utility class that helps to extract a library contained in the JAR file into a temporary directory.
 */
final class LibraryLoaderImpl implements LibraryLoader {

    /**
     * The root resources folder where the software is located.
     */
    private static final String SOFTWARE_FOLDER_NAME = "software";

    /**
     * The path delimiter used in the JAR file.
     */
    private static final String RESOURCE_PATH_DELIMITER = File.separator;

    /**
     * The {@link ResourceLoader} used to load the library.
     */
    private final ResourceLoader resourceLoader;

    /**
     * Creates an instance of the LibraryLoader with a ResourceLoader.
     * The {@link ResourceLoader} needs to be configured from a class located in the same jar where the library is located
     * @param resourceLoader the resourceLoader that needs to be configured from a class located in the same jar where the library is located
     */
    LibraryLoaderImpl(@NonNull final ResourceLoader resourceLoader) {
        this.resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader must not be null");
    }

    /**
     * Unpacks a library in the JAR file into a temporary directory and loads it using {@link System#load(String)}
     *
     * @param libraryName the library to load.
     * @param libExtensions defaults extensions for each os to use to load the library
     * @implNote This method expects the executable to be present at the following location in the JAR file:
     * {@code /software/<os>/<arch>/libraryName}.
     */
    @Override
    public void install(@NonNull final String libraryName, @NonNull final Map<OperatingSystem, String> libExtensions) {
        Objects.requireNonNull(libraryName, "libraryName must not be null");
        Objects.requireNonNull(libExtensions, "libExtensions must not be null");
        try {
            final OperatingSystem os = OperatingSystem.current();
            final Architecture arch = Architecture.current();

            String libExtension = libExtensions.get(os);
            if (!libExtensions.isEmpty()) {
                libExtension = "." + libExtension;
            }
            Path libraryPath = resourceLoader.load(SOFTWARE_FOLDER_NAME
                    + RESOURCE_PATH_DELIMITER
                    + os.name().toLowerCase(Locale.US)
                    + RESOURCE_PATH_DELIMITER
                    + arch.name().toLowerCase(Locale.US)
                    + RESOURCE_PATH_DELIMITER
                    + libraryName
                    + libExtension);
            System.load(libraryPath.toString());
        } catch (IOException | SecurityException | IllegalStateException e) {
            throw new IllegalStateException("Could not install requested library " + libraryName, e);
        }
    }
}
