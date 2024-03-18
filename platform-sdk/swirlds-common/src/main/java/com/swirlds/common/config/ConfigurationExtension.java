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

package com.swirlds.common.config;

import com.swirlds.config.api.ConfigurationBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A class that modifies a configuration builder (e.g. adding custom config types, converters, etc.). Can be used in
 * conjunction with automatic classpath scanning done by
 * {@link ConfigUtils#scanAndRegisterAllConfigExtensions(ConfigurationBuilder)} to automatically find and extend
 * configuration as needed.
 */
public interface ConfigurationExtension {

    /**
     * Extend the configuration builder with custom configuration types, converters, etc.
     *
     * @param builder the configuration builder to extend
     */
    void extendConfiguration(@NonNull final ConfigurationBuilder builder);
}
