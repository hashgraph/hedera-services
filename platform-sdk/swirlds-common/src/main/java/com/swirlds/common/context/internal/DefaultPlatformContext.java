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

package com.swirlds.common.context.internal;

import com.swirlds.base.time.Time;
import com.swirlds.common.concurrent.ExecutorFactory;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.config.api.Configuration;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Objects;

/**
 * Default implementation of the platform context. Warning: this class is private API and will be changed in the future.
 * The {@link PlatformContext} interface should be used all the time to interact with basic services.
 */
public final class DefaultPlatformContext implements PlatformContext {

    private final Configuration configuration;
    private final Metrics metrics;
    private final Cryptography cryptography;
    private final Time time;

    private final ExecutorFactory executorFactory;

    /**
     * Constructor.
     *
     * @param configuration the configuration
     * @param metrics       the metrics
     * @param cryptography  the cryptography
     * @param time          the time
     * @param executorFactory the executor factory
     */
    public DefaultPlatformContext(
            @NonNull final Configuration configuration,
            @NonNull final Metrics metrics,
            @NonNull final Cryptography cryptography,
            @NonNull final Time time,
            @NonNull final ExecutorFactory executorFactory) {
        this.configuration = Objects.requireNonNull(configuration);
        this.metrics = Objects.requireNonNull(metrics);
        this.cryptography = Objects.requireNonNull(cryptography);
        this.time = Objects.requireNonNull(time);
        this.executorFactory = Objects.requireNonNull(executorFactory);
    }

    /**
     * Creates a new instance of the platform context. The instance uses a {@link NoOpMetrics} implementation for
     * metrics. The instance uses the static {@link CryptographyHolder#get()} call to get the cryptography. The instance
     * uses the static {@link Time#getCurrent()} call to get the time.
     *
     * @param configuration the configuration
     * @return the platform context
     * @deprecated since we need to remove the static {@link CryptographyHolder#get()} call in future.
     */
    @Deprecated(forRemoval = true)
    @NonNull
    public static PlatformContext create(@NonNull final Configuration configuration) {
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
    public static PlatformContext create(
            @NonNull final Configuration configuration,
            @NonNull final Metrics metrics,
            @NonNull final Cryptography cryptography) {
        final Time time = Time.getCurrent();
        final UncaughtExceptionHandler handler = new PlatformUncaughtExceptionHandler();
        final ExecutorFactory executorFactory = ExecutorFactory.create("platform", null, handler);
        return new DefaultPlatformContext(configuration, metrics, cryptography, time, executorFactory);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Cryptography getCryptography() {
        return cryptography;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Metrics getMetrics() {
        return metrics;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Time getTime() {
        return time;
    }

    @Override
    public ExecutorFactory getExecutorFactory() {
        return executorFactory;
    }
}
