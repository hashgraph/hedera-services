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

package com.swirlds.platform.builder;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.platform.builder.internal.StaticPlatformBuilder.getMetricsProvider;
import static com.swirlds.platform.builder.internal.StaticPlatformBuilder.setupGlobalMetrics;

import com.swirlds.base.time.Time;
import com.swirlds.common.concurrent.ExecutorFactory;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.context.DefaultPlatformContext;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyFactory;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.io.filesystem.FileSystemManager;
import com.swirlds.common.io.filesystem.FileSystemManagerFactory;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.crypto.MerkleCryptography;
import com.swirlds.common.merkle.crypto.MerkleCryptographyFactory;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Configures and builds a {@link PlatformContext}.
 */
public class PlatformContextBuilder {

    private static final Logger logger = LogManager.getLogger(PlatformContextBuilder.class);

    private final NodeId selfId;

    private ConfigurationBuilder configurationBuilder;
    private Metrics metrics;
    private Cryptography cryptography;
    private Time time;
    private FileSystemManager fileSystemManager;
    private ExecutorFactory executorFactory;

    private boolean used = false;

    /**
     * Constructor.
     *
     * @param selfId the ID of the node
     */
    private PlatformContextBuilder(@NonNull final NodeId selfId) {
        this.selfId = Objects.requireNonNull(selfId);
    }

    /**
     * Create a new builder.
     *
     * @param selfId the ID of the node
     * @return the builder
     */
    @NonNull
    public static PlatformContextBuilder create(@NonNull final NodeId selfId) {
        return new PlatformContextBuilder(selfId);
    }

    /**
     * Build the platform context.
     *
     * @return the platform context
     */
    @NonNull
    public PlatformContext build() {
        used = true;

        if (configurationBuilder == null) {
            configurationBuilder = ConfigurationBuilder.create();
        }
        final Configuration configuration = buildConfiguration(configurationBuilder);

        if (cryptography == null) {
            cryptography = buildCryptography(configuration);
        }

        if (metrics == null) {
            metrics = buildMetrics(configuration, selfId);
        }

        if (time == null) {
            time = Time.getCurrent();
        }

        if (fileSystemManager == null) {
            fileSystemManager = FileSystemManagerFactory.getInstance().createFileSystemManager(configuration, metrics);
        }

        if (executorFactory == null) {
            executorFactory = buildExecutorFactory();
        }

        return new DefaultPlatformContext(
                configuration, metrics, cryptography, time, executorFactory, fileSystemManager);
    }

    /**
     * Provide a configuration builder. The platform will register any needed platform configuration properties prior to
     * building {@link Configuration}.
     *
     * @param configurationBuilder the configuration builder
     * @return this
     */
    @NonNull
    public PlatformContextBuilder withConfigurationBuilder(@NonNull final ConfigurationBuilder configurationBuilder) {
        throwIfUsed();
        this.configurationBuilder = Objects.requireNonNull(configurationBuilder);
        return this;
    }

    /**
     * Provide a metrics instance.
     *
     * @param metrics the metrics
     * @return this
     */
    @NonNull
    public PlatformContextBuilder withMetrics(@NonNull final Metrics metrics) {
        throwIfUsed();
        this.metrics = Objects.requireNonNull(metrics);
        return this;
    }

    /**
     * Provide a cryptography instance.
     *
     * @param cryptography the cryptography
     * @return this
     */
    @NonNull
    public PlatformContextBuilder withCryptography(@NonNull final Cryptography cryptography) {
        throwIfUsed();
        this.cryptography = Objects.requireNonNull(cryptography);
        return this;
    }

    /**
     * Provide a time instance.
     *
     * @param time the time
     * @return this
     */
    @NonNull
    public PlatformContextBuilder withTime(@NonNull final Time time) {
        throwIfUsed();
        this.time = Objects.requireNonNull(time);
        return this;
    }

    /**
     * Provide a file system manager instance.
     *
     * @param fileSystemManager the file system manager
     * @return this
     */
    @NonNull
    public PlatformContextBuilder withFileSystemManager(@NonNull final FileSystemManager fileSystemManager) {
        throwIfUsed();
        this.fileSystemManager = Objects.requireNonNull(fileSystemManager);
        return this;
    }

    /**
     * Provide an executor factory instance.
     *
     * @param executorFactory the executor factory
     * @return this
     */
    @NonNull
    public PlatformContextBuilder withExecutorFactory(@NonNull final ExecutorFactory executorFactory) {
        throwIfUsed();
        this.executorFactory = Objects.requireNonNull(executorFactory);
        return this;
    }

    /**
     * Throws an exception if this builder has already been used to create a context.
     */
    private void throwIfUsed() {
        if (used) {
            throw new IllegalStateException("This builder has already been used.");
        }
    }

    /**
     * Build the configuration for the node.
     *
     * @param configurationBuilder used to build configuration
     * @return the configuration
     */
    @NonNull
    private static Configuration buildConfiguration(@NonNull final ConfigurationBuilder configurationBuilder) {
        Objects.requireNonNull(configurationBuilder);
        // FUTURE WORK: don't use auto-discovery (requires design discussion)
        configurationBuilder.autoDiscoverExtensions();
        final Configuration configuration = configurationBuilder.build();
        ConfigurationHolder.getInstance().setConfiguration(configuration);
        return configuration;
    }

    /**
     * Build the cryptography for the node.
     *
     * @param configuration the configuration
     * @return the cryptography
     */
    @NonNull
    private static Cryptography buildCryptography(@NonNull final Configuration configuration) {
        final Cryptography cryptography = CryptographyFactory.create(configuration);
        CryptographyHolder.set(cryptography);
        final MerkleCryptography merkleCryptography = MerkleCryptographyFactory.create(configuration, cryptography);
        MerkleCryptoFactory.set(merkleCryptography);
        return cryptography;
    }

    /**
     * Build metrics.
     *
     * @param configuration the configuration
     * @param selfId        the ID of the node
     */
    @NonNull
    private static Metrics buildMetrics(@NonNull final Configuration configuration, @NonNull final NodeId selfId) {
        setupGlobalMetrics(configuration);
        return getMetricsProvider().createPlatformMetrics(selfId);
    }

    /**
     * Build the uncaught exception handler.
     *
     * @return the uncaught exception handler
     */
    @NonNull
    private static UncaughtExceptionHandler buildUncaughtExceptionHandler() {
        return (t, e) -> {
            logger.error(EXCEPTION.getMarker(), "Uncaught exception in thread: " + t.getName(), e);
            throw new RuntimeException("Uncaught exception", e);
        };
    }

    /**
     * Build the executor factory.
     *
     * @return the executor factory
     */
    @NonNull
    private static ExecutorFactory buildExecutorFactory() {
        final ExecutorFactory executorFactory =
                ExecutorFactory.create("platform", null, buildUncaughtExceptionHandler());
        return executorFactory;
    }
}
