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

import static com.swirlds.platform.builder.internal.StaticPlatformBuilder.getMetricsProvider;
import static com.swirlds.platform.builder.internal.StaticPlatformBuilder.setupGlobalMetrics;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.DefaultPlatformContext;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyFactory;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.io.filesystem.FileSystemManager;
import com.swirlds.common.io.filesystem.FileSystemManagerFactory;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Configures and builds a {@link PlatformContext}.
 */
public class PlatformContextBuilder {

    private final NodeId selfId;

    private ConfigurationBuilder configurationBuilder;
    private Metrics metrics;
    private Cryptography cryptography;
    private Time time;
    private FileSystemManager fileSystemManager;

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

        return new DefaultPlatformContext(configuration, metrics, cryptography, time, fileSystemManager);
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
        return configurationBuilder.build();
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
}
