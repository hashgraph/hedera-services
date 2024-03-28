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

package com.swirlds.config.extensions.sources;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * A {@link com.swirlds.config.api.source.ConfigSource} abstract implementation that can be used to provide values from a
 * property file.
 * Subclasses should define how to {@link AbstractFileConfigSource#getReader} out of the {@code filePath}
 */
public abstract class AbstractFileConfigSource extends AbstractConfigSource {
    protected @NonNull final Map<String, String> internalProperties;
    protected @NonNull final Path filePath;
    protected final int ordinal;

    /**
     * Creates a new instance based on a path with a default ordinal
     *
     * @param filePath the properties file
     * @throws IOException if the file can not be loaded or parsed
     */
    protected AbstractFileConfigSource(@NonNull final Path filePath) throws IOException {
        this(filePath, ConfigSourceOrdinalConstants.PROPERTY_FILE_ORDINAL);
    }

    /**
     * Creates a new instance based on a file
     *
     * @param filePath the properties file
     * @param ordinal  the ordinal of the config source
     * @throws IOException if the file can not be loaded or parsed
     */
    protected AbstractFileConfigSource(@NonNull final Path filePath, final int ordinal) throws IOException {
        this.internalProperties = new HashMap<>();
        this.filePath = Objects.requireNonNull(filePath, "filePath can not be null");
        this.ordinal = ordinal;
        try (BufferedReader reader = getReader()) {
            final Properties loadedProperties = new Properties();
            loadedProperties.load(reader);
            loadedProperties
                    .stringPropertyNames()
                    .forEach(name -> internalProperties.put(name, loadedProperties.getProperty(name)));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull String getName() {
        return "Property file config source for " + filePath;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected @NonNull Map<String, String> getInternalProperties() {
        return Collections.unmodifiableMap(internalProperties);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getOrdinal() {
        return ordinal;
    }

    /**
     * @return a bufferReader with the {@code filePath}
     * @throws IOException in case of error while creating the reader
     */
    protected abstract @NonNull BufferedReader getReader() throws IOException;
}
