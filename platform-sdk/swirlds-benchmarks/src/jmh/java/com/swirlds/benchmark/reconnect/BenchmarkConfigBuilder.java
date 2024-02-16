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

package com.swirlds.benchmark.reconnect;

import com.swirlds.common.config.ConfigUtils;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.threading.locks.AutoClosableLock;
import com.swirlds.common.threading.locks.Locks;
import com.swirlds.common.threading.locks.locked.Locked;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * Helper for use the config in benchmarks.
 */
public class BenchmarkConfigBuilder {

    private static final String SWIRLDS_PACKAGE = "com.swirlds";
    private static final String HEDERA_PACKAGE = "com.hedera";

    private final AutoClosableLock configLock = Locks.createAutoLock();

    private Configuration configuration = null;

    private final ConfigurationBuilder builder;

    /**
     * Creates a new instance and automatically registers all config data records (see {@link ConfigData}) on classpath
     * / modulepath that are part of the packages {@code com.swirlds} and {@code com.hedera} (see {@link ConfigUtils}).
     */
    public BenchmarkConfigBuilder() {
        this.builder = ConfigUtils.scanAndRegisterAllConfigTypes(
                ConfigurationBuilder.create(), Set.of(SWIRLDS_PACKAGE, HEDERA_PACKAGE));
    }

    /**
     * This method returns the {@link Configuration} instance. If the method is called for the first time the
     * {@link Configuration} instance will be created. The config will support all
     * config data record types (see {@link ConfigData}) that are on the classpath.
     *
     * @return the created configuration
     */
    @NonNull
    public Configuration getOrCreateConfig() {
        try (final Locked ignore = configLock.lock()) {
            if (configuration == null) {
                configuration = builder.build();
                ConfigurationHolder.getInstance().setConfiguration(configuration);
            }
            return configuration;
        }
    }
}
