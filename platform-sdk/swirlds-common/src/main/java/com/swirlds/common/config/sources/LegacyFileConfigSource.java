/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.config.sources;

import com.swirlds.common.utility.CommonUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A {@link com.swirlds.config.api.source.ConfigSource} implementation that can be used to provide values from files
 * based on the old syntax of swirlds settings.txt files.
 *
 * @deprecated should be removed once the old fileformat is not used anymore
 */
@Deprecated(forRemoval = true)
public class LegacyFileConfigSource extends AbstractConfigSource {

    private final Map<String, String> internalProperties;

    private final Path filePath;

    private final int ordinal;

    /**
     * Creates an instance that provides the config properties from the legacy settings.txt file
     *
     * @param settingsPath the path to the settings.txt file
     * @return config source for the settings.txt file
     * @throws IOException if settings.txt can not be loaded
     */
    @NonNull
    public static LegacyFileConfigSource ofSettingsFile(@NonNull final Path settingsPath) throws IOException {
        return new LegacyFileConfigSource(
                settingsPath, ConfigSourceOrdinalConstants.LEGACY_PROPERTY_FILE_ORDINAL_FOR_SETTINGS);
    }

    /**
     * Creates a new instance based on a file
     *
     * @param filePath the file that contains the config properties
     * @throws IOException if the file can not be loaded or parsed
     */
    public LegacyFileConfigSource(final Path filePath) throws IOException {
        this(filePath, ConfigSourceOrdinalConstants.LEGACY_PROPERTY_FILE_ORDINAL);
    }

    /**
     * Creates a new instance based on a file
     *
     * @param ordinal  the ordinal of the source (see {@link #getOrdinal()})
     * @param filePath the file that contains the config properties
     * @throws IOException if the file can not be loaded or parsed
     */
    public LegacyFileConfigSource(final Path filePath, final int ordinal) throws IOException {
        this.filePath = Objects.requireNonNull(filePath, "filePath must not be null");

        this.ordinal = ordinal;
        this.internalProperties = Collections.unmodifiableMap(loadSettings(filePath.toFile()));
    }

    /**
     * Creates a new instance based on a file
     *
     * @param filePath the path of the file that contains the config properties
     * @throws IOException if the file can not be loaded or parsed
     */
    public LegacyFileConfigSource(final String filePath) throws IOException {
        this(Paths.get(CommonUtils.throwArgBlank(filePath, "filePath")));
    }

    private static Map<String, String> loadSettings(final File settingsFile) throws IOException {
        final Map<String, String> properties = new HashMap<>();
        if (!settingsFile.exists()) {
            return properties;
        }
        try (Stream<String> stream = Files.lines(settingsFile.toPath())) {
            stream.map(line -> {
                        final int pos = line.indexOf("#");
                        if (pos > -1) {
                            return line.substring(0, pos).trim();
                        }
                        return line.trim();
                    })
                    .filter(line -> !line.isEmpty())
                    .filter(line -> splitLine(line).length > 0) // ignore empty lines
                    .forEach(line -> {
                        final String[] pars = splitLine(line);
                        try {
                            if (pars.length > 1) {
                                StringBuilder stringBuilder = new StringBuilder();
                                for (int i = 1; i < pars.length; i++) {
                                    stringBuilder.append(pars[i].trim());
                                    if (i != pars.length - 1) {
                                        stringBuilder.append(",");
                                    }
                                }
                                properties.put(pars[0], stringBuilder.toString());
                            } else {
                                properties.put(pars[0], "");
                            }
                        } catch (final Exception e) {
                            throw new IllegalStateException("syntax error in settings file", e);
                        }
                    });
        }
        return properties;
    }

    private static String[] splitLine(final String line) {
        return Arrays.stream(line.split(",")).map(String::trim).toArray(String[]::new);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Map<String, String> getInternalProperties() {
        return Collections.unmodifiableMap(internalProperties);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "Swirlds Legacy Settings loader for " + filePath;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getOrdinal() {
        return ordinal;
    }
}
