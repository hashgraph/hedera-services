/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.config.singleton;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The old Settings API used several static holder classes to support to change the configuration for tests. Next to
 * this the internal serialization API needs default constructors but uses the static Settings in serializable classes.
 * Based on this points we can not simply introduce the new -non-static- config API without refactoring all the
 * mentioned points. By using the given class for static access to the config we can refactor all places and introduce
 * the new config API. This class must be seen as a workaround that will be removed once we do not need static access
 * anymore! The class must only be used when we can not avoid static access without having a big refactoring. This class
 * is not thread safe!
 * <p>
 * The class is defined as a singleton that provides the current configuration. By default the configuration has no
 * config sources. Therefore the default values will be used for all values of config data records (see {@link
 * ConfigData}). A new config can be set by calling the {@link #setConfiguration(Configuration)} method. That should be
 * done once in the browser or for unit tests and nowhere else!
 *
 * @deprecated Will be removed once we have the Platform Context in place or all mentioned problems have been refactored
 */
@Deprecated(forRemoval = true)
public final class ConfigurationHolder implements Supplier<Configuration> {

    private static final ConfigurationHolder INSTANCE = new ConfigurationHolder();

    private Configuration configuration;

    private ConfigurationHolder() {
        reset();
    }

    public void reset() {
        this.configuration =
                ConfigurationBuilder.create().autoDiscoverExtensions().build();
    }

    /**
     * Sets the config. This method should only be called in the browser at startup or at unit tests
     *
     * @param configuration the new configuration
     * @throws NullPointerException in case {@code configuration} parameter is {@code null}
     */
    public void setConfiguration(final Configuration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
    }

    /**
     * Returns the configuration
     *
     * @return the configuration
     */
    @Override
    public Configuration get() {
        return configuration;
    }

    /**
     * Returns the singleton instance
     *
     * @return the singleton instance
     */
    public static ConfigurationHolder getInstance() {
        return INSTANCE;
    }

    /**
     * Convenience method for {@link Configuration#getConfigData(Class)}.
     *
     * @param type the data type
     * @param <T>  the type
     * @return the data instance
     */
    public static <T extends Record> T getConfigData(final Class<T> type) {
        return getInstance().get().getConfigData(type);
    }
}
