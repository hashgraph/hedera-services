/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.logging.api.extensions.provider;

import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * An abstract log provider. This class provides some basic functionality that is used by all log providers.
 */
public abstract class AbstractLogProvider implements LogProvider {

    private static final String PROPERTY_PROVIDER_ENABLED = "logging.provider.%s.enabled";

    /**
     * The configuration key of the log provider. This is used to create configuration keys for the log provider.
     */
    private final String configKey;

    /**
     * The configuration.
     */
    private final Configuration configuration;

    /**
     * Creates a new log provider.
     *
     * @param configKey     the configuration key
     * @param configuration the configuration
     */
    public AbstractLogProvider(@NonNull final String configKey, @NonNull final Configuration configuration) {
        this.configKey = Objects.requireNonNull(configKey, "configKey must not be null");
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
    }

    @Override
    public boolean isActive() {
        return Boolean.TRUE.equals(
                configuration.getValue(PROPERTY_PROVIDER_ENABLED.formatted(configKey), Boolean.class, false));
    }
}
