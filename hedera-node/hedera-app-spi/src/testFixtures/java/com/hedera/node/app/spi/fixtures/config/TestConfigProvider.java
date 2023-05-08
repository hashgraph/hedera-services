/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.spi.fixtures.config;

import com.hedera.node.app.spi.config.ConfigProvider;
import com.hedera.node.app.spi.config.VersionedConfiguration;
import com.swirlds.common.config.ConfigUtils;
import com.swirlds.common.config.sources.SimpleConfigSource;
import com.swirlds.common.threading.locks.AutoClosableLock;
import com.swirlds.common.threading.locks.Locks;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * First version of a {@link ConfigProvider} that can be used in tests.
 */
public class TestConfigProvider implements ConfigProvider {

    private final ConfigurationBuilder configurationBuilder;

    private VersionedConfiguration versionedConfiguration;

    private final AutoClosableLock creationLock = Locks.createAutoLock();

    public TestConfigProvider() {
        this(ConfigurationBuilder.create());
    }

    public TestConfigProvider(@NonNull ConfigurationBuilder configurationBuilder) {
        this.configurationBuilder = Objects.requireNonNull(configurationBuilder);
    }

    /**
     * Adds all config records on class path / module path to the config
     *
     * @return the provider for fluent API useage
     */
    public TestConfigProvider addAllDataTypes() {
        ConfigUtils.scanAndRegisterAllConfigTypes(configurationBuilder);
        return this;
    }

    /**
     * Adds the given config property to the config
     *
     * @param propertyName the name of the config property
     * @param value        the value of the config property
     * @return the provider for fluent API useage
     */
    public TestConfigProvider withValue(String propertyName, String value) {
        configurationBuilder.withSource(new SimpleConfigSource(propertyName, value));
        return this;
    }

    /**
     * Adds the given config property to the config
     *
     * @param propertyName the name of the config property
     * @param value        the value of the config property
     * @return the provider for fluent API useage
     */
    public TestConfigProvider withValue(String propertyName, int value) {
        configurationBuilder.withSource(new SimpleConfigSource(propertyName, value));
        return this;
    }

    /**
     * Adds the given config property to the config
     *
     * @param propertyName the name of the config property
     * @param value        the value of the config property
     * @return the provider for fluent API useage
     */
    public TestConfigProvider withValue(String propertyName, double value) {
        configurationBuilder.withSource(new SimpleConfigSource(propertyName, value));
        return this;
    }

    /**
     * Adds the given config property to the config
     *
     * @param propertyName the name of the config property
     * @param value        the value of the config property
     * @return the provider for fluent API useage
     */
    public TestConfigProvider withValue(String propertyName, long value) {
        configurationBuilder.withSource(new SimpleConfigSource(propertyName, value));
        return this;
    }

    /**
     * Adds the given config property to the config
     *
     * @param propertyName the name of the config property
     * @param value        the value of the config property
     * @return the provider for fluent API useage
     */
    public TestConfigProvider withValue(String propertyName, boolean value) {
        configurationBuilder.withSource(new SimpleConfigSource(propertyName, value));
        return this;
    }

    /**
     * Adds the given config property to the config
     *
     * @param propertyName the name of the config property
     * @param value        the value of the config property
     * @return the provider for fluent API useage
     */
    public TestConfigProvider withValue(String propertyName, Object value) {
        CommonUtils.throwArgNull(value, "value");
        configurationBuilder.withSource(new SimpleConfigSource(propertyName, value.toString()));
        return this;
    }

    @Override
    public VersionedConfiguration getConfiguration() {
        try (var ignore = creationLock.lock();) {
            if (versionedConfiguration != null) {
                final Configuration configuration = configurationBuilder.build();
                versionedConfiguration = new VersionedConfiguration() {
                    @Override
                    public long getVersion() {
                        return 0;
                    }

                    @NonNull
                    @Override
                    public Stream<String> getPropertyNames() {
                        return configuration.getPropertyNames();
                    }

                    @Override
                    public boolean exists(@NonNull String s) {
                        return configuration.exists(s);
                    }

                    @Nullable
                    @Override
                    public String getValue(@NonNull String s) throws NoSuchElementException {
                        return configuration.getValue(s);
                    }

                    @Nullable
                    @Override
                    public String getValue(@NonNull String s, @Nullable String s1) {
                        return configuration.getValue(s, s1);
                    }

                    @Nullable
                    @Override
                    public <T> T getValue(@NonNull String s, @NonNull Class<T> aClass)
                            throws NoSuchElementException, IllegalArgumentException {
                        return configuration.getValue(s, aClass);
                    }

                    @Nullable
                    @Override
                    public <T> T getValue(@NonNull String s, @NonNull Class<T> aClass, @Nullable T t)
                            throws IllegalArgumentException {
                        return configuration.getValue(s, aClass, t);
                    }

                    @Nullable
                    @Override
                    public List<String> getValues(@NonNull String s) {
                        return configuration.getValues(s);
                    }

                    @Nullable
                    @Override
                    public List<String> getValues(@NonNull String s, @Nullable List<String> list) {
                        return configuration.getValues(s, list);
                    }

                    @Nullable
                    @Override
                    public <T> List<T> getValues(@NonNull String s, @NonNull Class<T> aClass)
                            throws NoSuchElementException, IllegalArgumentException {
                        return configuration.getValues(s, aClass);
                    }

                    @Nullable
                    @Override
                    public <T> List<T> getValues(@NonNull String s, @NonNull Class<T> aClass, @Nullable List<T> list)
                            throws IllegalArgumentException {
                        return configuration.getValues(s, aClass, list);
                    }

                    @NonNull
                    @Override
                    public <T extends Record> T getConfigData(@NonNull Class<T> aClass) {
                        return configuration.getConfigData(aClass);
                    }

                    @NonNull
                    @Override
                    public Collection<Class<? extends Record>> getConfigDataTypes() {
                        return configuration.getConfigDataTypes();
                    }
                };
            }
            return versionedConfiguration;
        }
    }
}
