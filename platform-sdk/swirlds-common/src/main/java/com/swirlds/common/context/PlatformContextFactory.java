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

package com.swirlds.common.context;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.internal.DefaultPlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.config.api.Configuration;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Factory for creating a new instance of the platform context.
 */
public interface PlatformContextFactory {

    /**
     * Creates a new instance of the platform context. The instance uses a {@link NoOpMetrics} implementation for
     * metrics. The instance uses the static {@link CryptographyHolder#get()} call to get the cryptography. The instance
     * uses the static {@link Time#getCurrent()} call to get the time.
     *
     * @param configuration the configuration
     * @return the platform context
     * @deprecated since we need to remove the static {@link CryptographyHolder#get()} call in future.
     */
    @Deprecated
    @NonNull
    static PlatformContext create(@NonNull final Configuration configuration) {
        final Metrics metrics = new NoOpMetrics();
        final Cryptography cryptography = CryptographyHolder.get();
        return create(configuration, metrics, cryptography);
    }

    /**
     * Creates a new instance of the platform context.
     * <p>
     * The instance uses the static {@link Time#getCurrent()} call to get the time.
     *
     * @param configuration the configuration
     * @param metrics       the metrics
     * @param cryptography  the cryptography
     * @return the platform context
     */
    @NonNull
    static PlatformContext create(
            @NonNull final Configuration configuration,
            @NonNull final Metrics metrics,
            @NonNull final Cryptography cryptography) {
        return new DefaultPlatformContext(configuration, metrics, cryptography);
    }
}
