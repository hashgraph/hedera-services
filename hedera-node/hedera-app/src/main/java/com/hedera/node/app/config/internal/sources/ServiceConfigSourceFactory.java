/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.config.internal.sources;

import com.swirlds.common.config.sources.PropertyFileConfigSource;
import com.swirlds.common.config.sources.SimpleConfigSource;
import com.swirlds.config.api.source.ConfigSource;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Class that provides factory methods for all the config sources that are used by the service. Based on documentation
 * at https://github.com/hashgraph/hedera-services/blob/develop/hedera-node/docs/services-configuration.md
 */
public class ServiceConfigSourceFactory {

    private static final String CONFIG_FOLDER_NAME = "data/config/";

    private static final String BOOTSTRAP_PROPERTIES_FILENAME = "bootstrap.properties";

    private static final String APPLICATION_PROPERTIES_FILENAME = "application.properties";

    private static final String NODE_PROPERTIES_FILENAME = "node.properties";

    private static final String API_PERMISSION_PROPERTIES_FILENAME = "api-permission.properties";

    private static final int DEFAULT_BOOTSTRAP_PROPERTIES_ORDINAL = 210;

    private static final Path BOOTSTRAP_PROPERTIES_PATH = Path.of(CONFIG_FOLDER_NAME, BOOTSTRAP_PROPERTIES_FILENAME);

    private static final int BOOTSTRAP_PROPERTIES_ORDINAL = 211;

    private static final int DEFAULT_APPLICATION_PROPERTIES_ORDINAL = 220;

    private static final Path APPLICATION_PROPERTIES_PATH =
            Path.of(CONFIG_FOLDER_NAME, APPLICATION_PROPERTIES_FILENAME);

    private static final int APPLICATION_PROPERTIES_ORDINAL = 221;

    private static final int DEFAULT_NODE_PROPERTIES_ORDINAL = 230;

    private static final Path NODE_PROPERTIES_PATH = Path.of(CONFIG_FOLDER_NAME, NODE_PROPERTIES_FILENAME);

    private static final int NODE_PROPERTIES_ORDINAL = 231;

    private static final int DEFAULT_API_PERMISSION_PROPERTIES_ORDINAL = 240;

    private static final Path API_PERMISSION_PROPERTIES_PATH =
            Path.of(CONFIG_FOLDER_NAME, API_PERMISSION_PROPERTIES_FILENAME);

    private static final int API_PERMISSION_PROPERTIES_ORDINAL = 241;

    private static final ConfigSource EMPTY_SOURCE = new SimpleConfigSource();

    private ServiceConfigSourceFactory() {}

    /**
     * Creates all the config sources that are used by the service.
     *
     * @return a collection of all the config sources that are used by the service.
     */
    @NonNull
    public static Collection<ConfigSource> createAll() {
        return Set.of(
                createForDefaultBootstrapProperties(),
                createForBootstrapProperties(),
                createForDefaultApplicationProperties(),
                createForApplicationProperties(),
                createForDefaultNodeProperties(),
                createForNodeProperties(),
                createForDefaultApiPermissionProperties(),
                createForApiPermissionProperties());
    }

    /**
     * Returns an optional that contains the URL of the resource with the given name if it can be loaded by the
     * classloader. Otherwise the optional contains null.
     *
     * @param resourceName the filename
     * @return the optional.
     */
    @NonNull
    private static Optional<URL> getInternalResource(@NonNull final String resourceName) {
        Objects.requireNonNull(resourceName, "fileName must not be null");
        return Optional.ofNullable(
                ServiceConfigSourceFactory.class.getClassLoader().getResource(resourceName));
    }

    /**
     * Returns an {@link Optional} that contains a config source for the given filename if it can be loaded by the
     * classloader.
     *
     * @param resourceName the filename
     * @param ordinal      the ordinal of the config source
     * @return the Optional that contains the created config source or null.
     */
    @NonNull
    private static Optional<ConfigSource> createForResource(@NonNull final String resourceName, final int ordinal) {
        return getInternalResource(resourceName).map(url -> {
            try {
                return new PropertyFileConfigSource(Path.of(url.toURI()), ordinal);
            } catch (final IOException | URISyntaxException e) {
                throw new IllegalStateException("Can not read property source from " + url, e);
            }
        });
    }

    /**
     * Creates a config source for the application.properties file.
     *
     * @param propertyFilePath the path to the application.properties file.
     * @param ordinal          the ordinal of the config source.
     * @return the config source.
     */
    @NonNull
    private static Optional<ConfigSource> createForPath(@NonNull final Path propertyFilePath, final int ordinal) {
        Objects.requireNonNull(propertyFilePath, "propertyFilePath must not be null");
        if (propertyFilePath.toFile().exists()) {
            try {
                return Optional.of(new PropertyFileConfigSource(propertyFilePath, ordinal));
            } catch (final IOException e) {
                throw new IllegalStateException("Can not read property source from " + propertyFilePath, e);
            }
        } else {
            return Optional.empty();
        }
    }

    @NonNull
    public static ConfigSource createForDefaultBootstrapProperties() {
        return createForResource(BOOTSTRAP_PROPERTIES_FILENAME, DEFAULT_BOOTSTRAP_PROPERTIES_ORDINAL)
                .orElse(EMPTY_SOURCE);
    }

    @NonNull
    public static ConfigSource createForDefaultApplicationProperties() {
        return createForResource(APPLICATION_PROPERTIES_FILENAME, DEFAULT_APPLICATION_PROPERTIES_ORDINAL)
                .orElse(EMPTY_SOURCE);
    }

    @NonNull
    public static ConfigSource createForDefaultNodeProperties() {
        return createForResource(NODE_PROPERTIES_FILENAME, DEFAULT_NODE_PROPERTIES_ORDINAL)
                .orElse(EMPTY_SOURCE);
    }

    @NonNull
    public static ConfigSource createForDefaultApiPermissionProperties() {
        return createForResource(APPLICATION_PROPERTIES_FILENAME, DEFAULT_API_PERMISSION_PROPERTIES_ORDINAL)
                .orElse(EMPTY_SOURCE);
    }

    @NonNull
    public static ConfigSource createForBootstrapProperties() {
        return createForPath(BOOTSTRAP_PROPERTIES_PATH, BOOTSTRAP_PROPERTIES_ORDINAL)
                .orElse(EMPTY_SOURCE);
    }

    @NonNull
    public static ConfigSource createForApplicationProperties() {
        return createForPath(APPLICATION_PROPERTIES_PATH, APPLICATION_PROPERTIES_ORDINAL)
                .orElse(EMPTY_SOURCE);
    }

    @NonNull
    public static ConfigSource createForNodeProperties() {
        return createForPath(NODE_PROPERTIES_PATH, NODE_PROPERTIES_ORDINAL).orElse(EMPTY_SOURCE);
    }

    @NonNull
    public static ConfigSource createForApiPermissionProperties() {
        return createForPath(API_PERMISSION_PROPERTIES_PATH, API_PERMISSION_PROPERTIES_ORDINAL)
                .orElse(EMPTY_SOURCE);
    }
}
