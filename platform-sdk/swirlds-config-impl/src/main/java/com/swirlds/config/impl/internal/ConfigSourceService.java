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

package com.swirlds.config.impl.internal;

import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.source.ConfigSource;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/**
 * Services that provides the config properties from sources (see {@link ConfigSource})
 */
class ConfigSourceService implements ConfigLifecycle {

    /**
     * List of all sources that will be used to load config properties
     */
    private final List<ConfigSource> sources;

    /**
     * Defines if this service is initialized
     */
    private boolean initialized = false;

    ConfigSourceService() {
        this.sources = new CopyOnWriteArrayList<>();
    }

    void addConfigSource(final ConfigSource configSource) {
        throwIfInitialized();
        CommonUtils.throwArgNull(configSource, "configSource");
        sources.add(configSource);
    }

    Stream<ConfigSource> getSources() {
        throwIfNotInitialized();
        return sources.stream();
    }

    @Override
    public void init() {
        throwIfInitialized();
        sources.sort(Comparator.comparingInt(ConfigSource::getOrdinal));
        initialized = true;
    }

    @Override
    public void dispose() {
        sources.clear();
        initialized = false;
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }
}
