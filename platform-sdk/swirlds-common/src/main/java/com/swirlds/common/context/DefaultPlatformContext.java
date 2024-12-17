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
import com.swirlds.common.concurrent.ExecutorFactory;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.io.filesystem.FileSystemManager;
import com.swirlds.common.io.utility.RecycleBin;
import com.swirlds.common.merkle.crypto.MerkleCryptography;
import com.swirlds.config.api.Configuration;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
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
    private final FileSystemManager fileSystemManager;
    private final RecycleBin recycleBin;
    private final MerkleCryptography merkleCryptography;

    /**
     * Constructor.
     *
     * @param configuration     the configuration
     * @param metrics           the metrics
     * @param cryptography      the cryptography
     * @param time              the time
     * @param executorFactory   the executor factory
     * @param fileSystemManager the fileSystemManager
     * @param recycleBin        the recycleBin
     */
    public DefaultPlatformContext(
            @NonNull final Configuration configuration,
            @NonNull final Metrics metrics,
            @NonNull final Cryptography cryptography,
            @NonNull final Time time,
            @NonNull final ExecutorFactory executorFactory,
            @NonNull final FileSystemManager fileSystemManager,
            @NonNull final RecycleBin recycleBin,
            @NonNull final MerkleCryptography merkleCryptography) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.cryptography = Objects.requireNonNull(cryptography, "cryptography must not be null");
        this.time = Objects.requireNonNull(time, "time must not be null");
        this.executorFactory = Objects.requireNonNull(executorFactory, "executorFactory must not be null");
        this.fileSystemManager = Objects.requireNonNull(fileSystemManager, "fileSystemManager must not be null");
        this.recycleBin = Objects.requireNonNull(recycleBin, "recycleBin must not be null");
        this.merkleCryptography = Objects.requireNonNull(merkleCryptography, "merkleCryptography must not be null");
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

    @NonNull
    @Override
    public FileSystemManager getFileSystemManager() {
        return fileSystemManager;
    }

    @Override
    @NonNull
    public ExecutorFactory getExecutorFactory() {
        return executorFactory;
    }

    @NonNull
    @Override
    public RecycleBin getRecycleBin() {
        return recycleBin;
    }

    public MerkleCryptography getMerkleCryptography() {
        return merkleCryptography;
    }
}
