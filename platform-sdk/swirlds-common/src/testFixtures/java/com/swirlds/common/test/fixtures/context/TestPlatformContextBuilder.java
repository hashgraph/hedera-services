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

package com.swirlds.common.test.fixtures.context;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.test.fixtures.config.TestConfigBuilder;
import com.swirlds.config.api.Configuration;

/**
 * A simple builder to create a {@link PlatformContext} for unit tests.
 */
public final class TestPlatformContextBuilder {

    private TestConfigBuilder testConfigBuilder;
    private Metrics metrics;

    private TestPlatformContextBuilder() {
        this.testConfigBuilder = new TestConfigBuilder();
    }

    /**
     * Creates a new builder instance
     *
     * @return a new instance
     */
    public static TestPlatformContextBuilder create() {
        return new TestPlatformContextBuilder();
    }

    /**
     * Adds a {@link Configuration} builder
     *
     * @param testConfigBuilder the config builder
     * @return the builder instance
     */
    public TestPlatformContextBuilder withConfigBuilder(final TestConfigBuilder testConfigBuilder) {
        this.testConfigBuilder = testConfigBuilder;
        return this;
    }

    public TestPlatformContextBuilder withMetrics(final Metrics metrics) {
        this.metrics = metrics;
        return this;
    }

    /**
     * Returns a new {@link PlatformContext} based on this builder
     *
     * @return a new {@link PlatformContext}
     */
    public PlatformContext build() {
        final Configuration orCreateConfig = testConfigBuilder.getOrCreateConfig();
        final Cryptography cryptography = CryptographyHolder.get();
        return new PlatformContext() {
            @Override
            public Configuration getConfiguration() {
                return orCreateConfig;
            }

            @Override
            public Cryptography getCryptography() {
                return cryptography;
            }

            @Override
            public Metrics getMetrics() {
                return metrics;
            }
        };
    }
}
