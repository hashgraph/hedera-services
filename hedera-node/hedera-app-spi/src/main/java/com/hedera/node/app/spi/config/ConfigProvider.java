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

package com.hedera.node.app.spi.config;

import com.swirlds.config.api.Configuration;

/**
 * The ConfigProvider interface is used to provide the configuration. This interface can be seen as the "config
 * facility". Since some config properties can change at runtime the version of the configuration is also provided.
 * Whenever you want to access a configuration property that can change at runtime you should not store the {@link
 * Configuration} instance and always use this provider to get access to it or check the version by calling {@link
 * #getVersion()}. If the version has changed you should get a new instance of the {@link Configuration}.
 */
public interface ConfigProvider {

    /**
     * Returns the configuration.
     *
     * @return the configuration
     */
    Configuration getConfiguration();

    /**
     * Returns a version that always changes (counts up) when the configuration changes.
     *
     * @return the current version.
     */
    long getVersion();
}
