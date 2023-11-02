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

package com.swirlds.logging.api.internal.configuration;

import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The Logging configuration that is used to configure the logging system. The config is based on a file that is by
 * default in of the application root folder and named {@code log.properties}. The system environment variable
 * {@code LOG_CONFIG_PATH} can be defined to overwrite the path to the configuration file. The class implements the
 * {@link Configuration} interface since it will later be replaced by using the "real" configuration system.
 */
public class LogConfiguration implements Configuration {

    /**
     * The configuration properties
     */
    private final Map<String, String> properties;

    /**
     * Creates a new instance of the logging configuration.
     */
    public LogConfiguration() {
        properties = new HashMap<>();
        final String logConfigPath = System.getenv("LOG_CONFIG_PATH");
        final URL configProperties = Optional.ofNullable(logConfigPath)
                .map(Path::of)
                .map(Path::toUri)
                .map(uri -> {
                    try {
                        return uri.toURL();
                    } catch (final Exception e) {
                        throw new RuntimeException("Can not convert path to URL!", e);
                    }
                })
                .orElseGet(() -> LogConfiguration.class.getClassLoader().getResource("log.properties"));
        if (configProperties != null) {
            try (final InputStream inputStream = configProperties.openStream()) {
                final Properties properties = new Properties();
                properties.load(inputStream);
                properties.forEach((key, value) -> this.properties.put((String) key, (String) value));
            } catch (final Exception e) {
                final System.Logger systemLogger = System.getLogger(LogConfiguration.class.getName());
                systemLogger.log(System.Logger.Level.ERROR, "Can not load logging configuration!", e);
            }
        }
    }

    @NonNull
    @Override
    public Stream<String> getPropertyNames() {
        return properties.keySet().stream();
    }

    @Override
    public boolean exists(@NonNull String property) {
        return properties.containsKey(property);
    }

    @Override
    public String getValue(@NonNull String property) throws NoSuchElementException {
        return properties.get(property);
    }

    @Override
    public String getValue(@NonNull String property, @Nullable String defaultValue) {
        return Optional.ofNullable(properties.get(property)).orElse(defaultValue);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getValue(@NonNull String property, @NonNull Class<T> type)
            throws NoSuchElementException, IllegalArgumentException {
        if (type == Boolean.class) {
            return (T) Boolean.valueOf(properties.get(property));
        }
        throw new IllegalStateException("Unsupported type: " + type.getName());
    }

    @Override
    public <T> T getValue(@NonNull String property, @NonNull Class<T> type, T defaultValue)
            throws IllegalArgumentException {
        return Optional.ofNullable(getValue(property, type)).orElse(defaultValue);
    }

    @Override
    public List<String> getValues(@NonNull String property) {
        throw new IllegalStateException("Collections not supported");
    }

    @Override
    public List<String> getValues(@NonNull String property, List<String> defaultValue) {
        throw new IllegalStateException("Collections not supported");
    }

    @Override
    public <T> List<T> getValues(@NonNull String property, @NonNull Class<T> type)
            throws NoSuchElementException, IllegalArgumentException {
        throw new IllegalStateException("Collections not supported");
    }

    @Override
    public <T> List<T> getValues(@NonNull String property, @NonNull Class<T> type, @Nullable List<T> defaultValue)
            throws IllegalArgumentException {
        throw new IllegalStateException("Collections not supported");
    }

    @Override
    public Set<String> getValueSet(@NonNull String property) {
        throw new IllegalStateException("Collections not supported");
    }

    @Override
    public Set<String> getValueSet(@NonNull String property, @Nullable Set<String> defaultValue) {
        throw new IllegalStateException("Collections not supported");
    }

    @Override
    public <T> Set<T> getValueSet(@NonNull String property, @NonNull Class<T> type)
            throws NoSuchElementException, IllegalArgumentException {
        throw new IllegalStateException("Collections not supported");
    }

    @Override
    public <T> Set<T> getValueSet(@NonNull String property, @NonNull Class<T> type, @Nullable Set<T> defaultValue)
            throws IllegalArgumentException {
        throw new IllegalStateException("Collections not supported");
    }

    @NonNull
    @Override
    public <T extends Record> T getConfigData(@NonNull Class<T> configType) {
        throw new IllegalStateException("Records not supported");
    }

    @NonNull
    @Override
    public Collection<Class<? extends Record>> getConfigDataTypes() {
        throw new IllegalStateException("Records not supported");
    }
}
