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

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.config.api.Configuration;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A simple builder to create a {@link PlatformContext} for tests.
 */
public final class BenchmarkPlatformContextBuilder {

    private static final Metrics defaultMetrics = new NoOpMetrics();
    private static final Cryptography defaultCryptography = CryptographyHolder.get();

    private Metrics metrics;
    private Cryptography cryptography;
    private Time time = Time.getCurrent();

    private BenchmarkPlatformContextBuilder() {}

    /**
     * Creates a new builder instance
     *
     * @return a new instance
     */
    @NonNull
    public static BenchmarkPlatformContextBuilder create() {
        return new BenchmarkPlatformContextBuilder();
    }

    /**
     * Returns a new {@link PlatformContext} based on this builder
     *
     * @return a new {@link PlatformContext}
     */
    public PlatformContext build(final Configuration configuration) {
        if (metrics == null) {
            metrics = defaultMetrics; // FUTURE WORK: replace this with NoOp Metrics
        }
        if (this.cryptography == null) {
            this.cryptography = defaultCryptography;
        }

        return new PlatformContext() {
            @Override
            public Configuration getConfiguration() {
                return configuration;
            }

            @Override
            public Cryptography getCryptography() {
                return cryptography;
            }

            @Override
            public Metrics getMetrics() {
                return metrics;
            }

            @NonNull
            @Override
            public Time getTime() {
                return time;
            }
        };
    }
}
