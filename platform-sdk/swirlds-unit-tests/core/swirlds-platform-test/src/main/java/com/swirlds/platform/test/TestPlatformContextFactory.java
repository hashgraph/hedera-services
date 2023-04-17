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

package com.swirlds.platform.test;

import static org.mockito.Mockito.mock;

import com.swirlds.common.context.DefaultPlatformContext;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.config.api.ConfigurationBuilder;

/**
 * A utility class for creating a {@link PlatformContext} for testing.
 */
public final class TestPlatformContextFactory {

    private TestPlatformContextFactory() {}

    /**
     * Create a new {@link PlatformContext} for testing.
     *
     * @return a new {@link PlatformContext} for testing
     */
    public static PlatformContext build() {
        return new DefaultPlatformContext(
                ConfigurationBuilder.create().build(),
                mock(Metrics.class), // TODO this should be NoOpMetrics!
                CryptographyHolder.get());
    }
}
