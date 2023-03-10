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

package com.hedera.node.app.config;

import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.spi.config.GlobalConfig;
import com.hedera.node.app.spi.config.NodeConfig;
import com.hedera.node.app.spi.config.Profile;
import com.hedera.node.app.spi.config.PropertyNames;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Adaptor for the configuration functionality. This class will be removed in future once the full
 * services layer is refactored to use the "real" config api from the platform. In that case the
 * {@link Configuration} implementation from the platform will be used.
 *
 * <p>This implementation is backed by a {@link PropertySource} instance and all calls will be
 * forwarded to that instance.
 */
@Deprecated(forRemoval = true)
public class ConfigurationAdaptor implements Configuration {

    private final PropertySource propertySource;

    private final NodeConfig nodeConfig;

    private final GlobalConfig globalConfig;

    public ConfigurationAdaptor(@NonNull final PropertySource propertySource) {
        this.propertySource = Objects.requireNonNull(propertySource);
        nodeConfig = createNodeConfig();
        globalConfig = createGlobalConfig();
    }

    @Override
    public Stream<String> getPropertyNames() {
        return propertySource.allPropertyNames().stream();
    }

    @Override
    public boolean exists(final String name) {
        return propertySource.containsProperty(name);
    }

    @Override
    public String getValue(final String name) throws NoSuchElementException {
        if (exists(name)) {
            return propertySource.getRawValue(name);
        }
        throw new NoSuchElementException("Config property with name '" + name + "' does not exist!");
    }

    @Override
    public String getValue(final String name, final String defaultValue) {
        if (exists(name)) {
            return getValue(name);
        }
        return defaultValue;
    }

    @Override
    public <T> T getValue(final String name, final Class<T> type)
            throws NoSuchElementException, IllegalArgumentException {
        if (exists(name)) {
            return propertySource.getTypedProperty(type, name);
        }
        throw new NoSuchElementException("Config property with name '" + name + "' does not exist!");
    }

    @Override
    public <T> T getValue(final String name, final Class<T> type, final T defaultValue)
            throws IllegalArgumentException {
        if (exists(name)) {
            return getValue(name, type);
        }
        return defaultValue;
    }

    @Override
    public List<String> getValues(final String name) {
        if (exists(name)) {
            return propertySource.getTypedProperty(List.class, name);
        }
        throw new NoSuchElementException("Config property with name '" + name + "' does not exist!");
    }

    @Override
    public List<String> getValues(final String name, final List<String> defaultValues) {
        if (exists(name)) {
            return getValues(name);
        }
        return defaultValues;
    }

    @Override
    public <T> List<T> getValues(final String name, final Class<T> type)
            throws NoSuchElementException, IllegalArgumentException {
        if (exists(name)) {
            return propertySource.getTypedProperty(List.class, name);
        }
        throw new NoSuchElementException("Config property with name '" + name + "' does not exist!");
    }

    @Override
    public <T> List<T> getValues(final String name, final Class<T> type, final List<T> defaultValues)
            throws IllegalArgumentException {
        if (exists(name)) {
            return getValues(name, type);
        }
        return defaultValues;
    }

    /**
     * This implementation only supports {@link GlobalConfig} and {@link NodeConfig} as config data
     * records.
     *
     * @param type {@link GlobalConfig} or {@link NodeConfig}, otherwise a {@link
     *     IllegalArgumentException} will be thrown
     * @param <T> type, must be {@link GlobalConfig} or {@link NodeConfig}
     * @return the {@link GlobalConfig} or {@link NodeConfig} instance
     * @see Configuration#getConfigData(Class)
     */
    @Override
    public <T extends Record> T getConfigData(final Class<T> type) {
        if (Objects.equals(type, GlobalConfig.class)) {
            return (T) globalConfig;
        }
        if (Objects.equals(type, NodeConfig.class)) {
            return (T) nodeConfig;
        }
        throw new IllegalArgumentException("Config data type '" + type.getName() + "' not defined");
    }

    @Override
    public Collection<Class<? extends Record>> getConfigDataTypes() {
        return Set.of(GlobalConfig.class, NodeConfig.class);
    }

    /**
     * Since we do not depend on the real config implementation we need to create the config data
     * records "by hand".
     *
     * @return a new NodeConfig instance
     */
    private NodeConfig createNodeConfig() {
        return new NodeConfig(
                propertySource.getTypedProperty(Integer.class, PropertyNames.GRPC_PORT),
                propertySource.getTypedProperty(Integer.class, PropertyNames.GRPC_TLS_PORT),
                propertySource.getTypedProperty(
                        Long.class, PropertyNames.STATS_HAPI_OPS_SPEEDOMETER_UPDATE_INTERVAL_MS),
                propertySource.getTypedProperty(Long.class, PropertyNames.STATS_ENTITY_UTILS_GAUGE_UPDATE_INTERVAL_MS),
                propertySource.getTypedProperty(
                        Long.class, PropertyNames.STATS_THROTTLE_UTILS_GAUGE_UPDATE_INTERVAL_MS),
                propertySource.getTypedProperty(Profile.class, PropertyNames.HEDERA_PROFILES_ACTIVE),
                propertySource.getTypedProperty(Double.class, PropertyNames.STATS_SPEEDOMETER_HALF_LIFE_SECS),
                propertySource.getTypedProperty(Double.class, PropertyNames.STATS_RUNNING_AVG_HALF_LIFE_SECS),
                propertySource.getTypedProperty(String.class, PropertyNames.HEDERA_RECORD_STREAM_LOG_DIR),
                propertySource.getTypedProperty(Long.class, PropertyNames.HEDERA_RECORD_STREAM_LOG_PERIOD),
                propertySource.getTypedProperty(Boolean.class, PropertyNames.HEDERA_RECORD_STREAM_IS_ENABLED),
                propertySource.getTypedProperty(Integer.class, PropertyNames.HEDERA_RECORD_STREAM_QUEUE_CAPACITY),
                propertySource.getTypedProperty(Integer.class, PropertyNames.QUERIES_BLOB_LOOK_UP_RETRIES),
                propertySource.getTypedProperty(Long.class, PropertyNames.NETTY_PROD_KEEP_ALIVE_TIME),
                propertySource.getTypedProperty(String.class, PropertyNames.NETTY_TLS_CERT_PATH),
                propertySource.getTypedProperty(String.class, PropertyNames.NETTY_TLS_KEY_PATH),
                propertySource.getTypedProperty(Long.class, PropertyNames.NETTY_PROD_KEEP_ALIVE_TIMEOUT),
                propertySource.getTypedProperty(Long.class, PropertyNames.NETTY_PROD_MAX_CONNECTION_AGE),
                propertySource.getTypedProperty(Long.class, PropertyNames.NETTY_PROD_MAX_CONNECTION_AGE_GRACE),
                propertySource.getTypedProperty(Long.class, PropertyNames.NETTY_PROD_MAX_CONNECTION_IDLE),
                propertySource.getTypedProperty(Integer.class, PropertyNames.NETTY_PROD_MAX_CONCURRENT_CALLS),
                propertySource.getTypedProperty(Integer.class, PropertyNames.NETTY_PROD_FLOW_CONTROL_WINDOW),
                propertySource.getTypedProperty(String.class, PropertyNames.DEV_DEFAULT_LISTENING_NODE_ACCOUNT),
                propertySource.getTypedProperty(Boolean.class, PropertyNames.DEV_ONLY_DEFAULT_NODE_LISTENS),
                propertySource.getTypedProperty(String.class, PropertyNames.HEDERA_ACCOUNTS_EXPORT_PATH),
                propertySource.getTypedProperty(Boolean.class, PropertyNames.HEDERA_EXPORT_ACCOUNTS_ON_STARTUP),
                propertySource.getTypedProperty(Profile.class, PropertyNames.NETTY_MODE),
                propertySource.getTypedProperty(Integer.class, PropertyNames.NETTY_START_RETRIES),
                propertySource.getTypedProperty(Long.class, PropertyNames.NETTY_START_RETRY_INTERVAL_MS),
                propertySource.getTypedProperty(Integer.class, PropertyNames.STATS_EXECUTION_TIMES_TO_TRACK),
                propertySource.getTypedProperty(Integer.class, PropertyNames.ISS_RESET_PERIOD),
                propertySource.getTypedProperty(Integer.class, PropertyNames.ISS_ROUNDS_TO_LOG),
                propertySource.getTypedProperty(Integer.class, PropertyNames.HEDERA_PREFETCH_QUEUE_CAPACITY),
                propertySource.getTypedProperty(Integer.class, PropertyNames.HEDERA_PREFETCH_THREAD_POOL_SIZE),
                propertySource.getTypedProperty(Integer.class, PropertyNames.HEDERA_PREFETCH_CODE_CACHE_TTL_SECS),
                propertySource.getTypedProperty(List.class, PropertyNames.STATS_CONS_THROTTLES_TO_SAMPLE),
                propertySource.getTypedProperty(List.class, PropertyNames.STATS_HAPI_THROTTLES_TO_SAMPLE),
                propertySource.getTypedProperty(String.class, PropertyNames.HEDERA_RECORD_STREAM_SIDE_CAR_DIR),
                propertySource.getTypedProperty(Integer.class, PropertyNames.GRPC_WORKFLOWS_PORT),
                propertySource.getTypedProperty(Integer.class, PropertyNames.GRPC_WORKFLOWS_TLS_PORT));
    }

    /**
     * Since we do not depend on the real config implementation we need to create the config data *
     * records "by hand".
     *
     * @return a new GlobalConfig instance
     */
    private GlobalConfig createGlobalConfig() {
        return new GlobalConfig(propertySource.getTypedProperty(Set.class, PropertyNames.WORKFLOWS_ENABLED));
    }
}
