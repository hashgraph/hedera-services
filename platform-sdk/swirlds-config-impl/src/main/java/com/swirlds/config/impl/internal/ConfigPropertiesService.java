/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.config.impl.internal;

import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.source.ConfigSource;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Service that provides all loaded config properties
 */
class ConfigPropertiesService implements ConfigLifecycle {

    /**
     * stores all config properties
     */
    private final Map<String, String> internalProperties;

    /**
     * The services that is used to load all properties in the correct order
     */
    private final ConfigSourceService configSourceService;

    /**
     * Defines if this service is initialized
     */
    private boolean initialized = false;

    ConfigPropertiesService(final ConfigSourceService configSourceService) {
        this.configSourceService = CommonUtils.throwArgNull(configSourceService, "configSourceService");
        internalProperties = new HashMap<>();
    }

    @Override
    public void init() {
        throwIfInitialized();
        configSourceService
                .getSources()
                .map(ConfigSource::getProperties)
                .flatMap(map -> map.entrySet().stream())
                .forEach(entry -> addProperty(entry.getKey(), entry.getValue()));
        initialized = true;
    }

    @Override
    public void dispose() {
        internalProperties.clear();
        initialized = false;
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    Stream<String> getPropertyNames() {
        throwIfNotInitialized();
        return internalProperties.keySet().stream();
    }

    boolean containsKey(final String propertyName) {
        throwIfNotInitialized();
        CommonUtils.throwArgBlank(propertyName, "propertyName");
        return internalProperties.containsKey(propertyName);
    }

    String getProperty(final String propertyName) {
        throwIfNotInitialized();
        CommonUtils.throwArgBlank(propertyName, "propertyName");
        return internalProperties.get(propertyName);
    }

    private void addProperty(final String propertyName, final String propertyValue) {
        CommonUtils.throwArgBlank(propertyName, "propertyName");
        internalProperties.put(propertyName, propertyValue);
    }
}
