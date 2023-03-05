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

package com.swirlds.test.framework.config;

import com.swirlds.common.config.ConfigUtils;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.config.sources.SimpleConfigSource;
import com.swirlds.common.threading.locks.AutoClosableLock;
import com.swirlds.common.threading.locks.Locks;
import com.swirlds.common.threading.locks.locked.Locked;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.api.source.ConfigSource;

/**
 * Helper for use the config in test and change the config for specific tests. Instance can be used per class or per
 * test.
 */
public class TestConfigBuilder {

    private final AutoClosableLock configLock = Locks.createAutoLock();

    private Configuration configuration = null;

    private final ConfigurationBuilder builder;

    /**
     * Creates a new instance and automatically registers all config data records on classpath (see {@link ConfigData})
     */
    public TestConfigBuilder() {
        this(true);
    }

    /**
     * Creates a new instance and add all records on classpath that are annotated with {@link ConfigData} as config data
     * types if the
     * {@code registerAllTypes} param is true.
     *
     * @param registerAllTypes
     * 		if true all config data records on classpath will automatically be registered
     */
    public TestConfigBuilder(final boolean registerAllTypes) {
        if (registerAllTypes) {
            this.builder = ConfigUtils.addAllConfigDataOnClasspath(ConfigurationBuilder.create());
        } else {
            this.builder = ConfigurationBuilder.create();
        }
    }

    /**
     * Sets the value for the config.
     *
     * @param propertyName
     * 		name of the property
     * @param value
     * 		the value
     * @return the {@link TestConfigBuilder} instance (for fluent API)
     */
    public TestConfigBuilder withValue(final String propertyName, final String value) {
        return withSource(new SimpleConfigSource(propertyName, value));
    }

    /**
     * Sets the value for the config.
     *
     * @param propertyName
     * 		name of the property
     * @param value
     * 		the value
     * @return the {@link TestConfigBuilder} instance (for fluent API)
     */
    public TestConfigBuilder withValue(final String propertyName, final int value) {
        return withSource(new SimpleConfigSource(propertyName, value));
    }

    /**
     * Sets the value for the config.
     *
     * @param propertyName
     * 		name of the property
     * @param value
     * 		the value
     * @return the {@link TestConfigBuilder} instance (for fluent API)
     */
    public TestConfigBuilder withValue(final String propertyName, final double value) {
        return withSource(new SimpleConfigSource(propertyName, value));
    }

    /**
     * Sets the value for the config.
     *
     * @param propertyName
     * 		name of the property
     * @param value
     * 		the value
     * @return the {@link TestConfigBuilder} instance (for fluent API)
     */
    public TestConfigBuilder withValue(final String propertyName, final long value) {
        return withSource(new SimpleConfigSource(propertyName, value));
    }

    /**
     * Sets the value for the config.
     *
     * @param propertyName
     * 		name of the property
     * @param value
     * 		the value
     * @return the {@link TestConfigBuilder} instance (for fluent API)
     */
    public TestConfigBuilder withValue(final String propertyName, final boolean value) {
        return withSource(new SimpleConfigSource(propertyName, value));
    }

    /**
     * This method returns the {@link Configuration} instance. If the method is called for the first time the
     * {@link Configuration} instance will be created. All values that have been set (see
     * {@link #withValue(String, int)}) methods will be part of the config. Next to this the config will support
     * all config data record types (see {@link ConfigData}) that are on the classpath.
     *
     * @return the created configuration
     */
    public Configuration getOrCreateConfig() {
        try (Locked ignore = configLock.lock()) {
            if (configuration == null) {
                configuration = builder.build();
                ConfigurationHolder.getInstance().setConfiguration(configuration);
            }
            return configuration;
        }
    }

    private void checkConfigState() {
        try (Locked ignore = configLock.lock()) {
            if (configuration != null) {
                throw new IllegalStateException("Configuration already created!");
            }
        }
    }

    /**
     * Adds the given config source to the builder
     *
     * @param configSource
     * 		the config source that will be added
     * @return the {@link TestConfigBuilder} instance (for fluent API)
     */
    public TestConfigBuilder withSource(final ConfigSource configSource) {
        checkConfigState();
        builder.withSource(configSource);
        return this;
    }
}
