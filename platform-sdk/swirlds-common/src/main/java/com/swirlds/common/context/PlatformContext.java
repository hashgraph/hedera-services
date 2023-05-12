/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.context;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.config.api.Configuration;

/**
 * Public interface of the platform context that provides access to all basic services and resources. By using the
 * {@link PlatformContext} a developer does not need to take care of the lifecycle of any basic service or resource.
 * <p>
 * The basic architecture approach of the {@link PlatformContext} defines the context as a single instance per
 * {@link com.swirlds.common.system.Platform}. When a platform is created the context will be passed to the platform and
 * can be used internally in the platform to access all basic services.
 */
public interface PlatformContext {

    /**
     * Returns the {@link Configuration} instance for the platform
     *
     * @return the {@link Configuration} instance
     */
    Configuration getConfiguration();

    /**
     * Returns the {@link Cryptography} instance for the platform
     *
     * @return the {@link Cryptography} instance
     */
    Cryptography getCryptography();

    /**
     * Returns the {@link Metrics} instance for the platform
     *
     * @return the {@link Metrics} instance
     */
    Metrics getMetrics();
}
