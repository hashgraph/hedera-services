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

import com.hedera.node.app.config.internal.ConfigurationAdaptor;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.spi.config.ConfigProvider;
import com.swirlds.common.threading.locks.AutoClosableLock;
import com.swirlds.common.threading.locks.Locks;
import com.swirlds.common.threading.locks.locked.Locked;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

public class ConfigProviderImpl implements ConfigProvider {

    private final PropertySource propertySource;

    private Configuration configuration;

    private long version;

    private final AutoClosableLock lock = Locks.createAutoLock();

    public ConfigProviderImpl(@NonNull final PropertySource propertySource) {
        this.propertySource = Objects.requireNonNull(propertySource, "propertySource");
        update();
    }

    public void update() {
        try (final Locked ignored = lock.lock()) {
            configuration = new ConfigurationAdaptor(propertySource);
            version = version + 1;
        }
    }

    @Override
    public Configuration getConfiguration() {
        try (final Locked ignored = lock.lock()) {
            return configuration;
        }
    }

    @Override
    public long getVersion() {
        try (final Locked ignored = lock.lock()) {
            return version;
        }
    }
}
