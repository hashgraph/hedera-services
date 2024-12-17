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

package com.swirlds.common.test.fixtures.platform;

import static com.swirlds.common.io.utility.FileUtils.rethrowIO;
import static org.junit.jupiter.api.Assertions.fail;

import com.swirlds.base.time.Time;
import com.swirlds.common.concurrent.ExecutorFactory;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.io.filesystem.FileSystemManager;
import com.swirlds.common.io.utility.NoOpRecycleBin;
import com.swirlds.common.io.utility.RecycleBin;
import com.swirlds.common.merkle.crypto.MerkleCryptography;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.common.test.fixtures.TestFileSystemManager;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.lang.Thread.UncaughtExceptionHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A simple builder to create a {@link PlatformContext} for unit tests.
 */
public final class TestPlatformContextBuilder {

    private static final Metrics defaultMetrics = new NoOpMetrics();
    private static final Configuration defaultConfig =
            ConfigurationBuilder.create().autoDiscoverExtensions().build();
    private static final Cryptography defaultCryptography = CryptographyHolder.get();
    private Configuration configuration;
    private Metrics metrics;
    private Cryptography cryptography;
    private Time time = Time.getCurrent();
    private FileSystemManager fileSystemManager;
    private RecycleBin recycleBin;
    private MerkleCryptography merkleCryptography;

    private TestPlatformContextBuilder() {}

    /**
     * Creates a new builder instance
     *
     * @return a new instance
     */
    @NonNull
    public static TestPlatformContextBuilder create() {
        return new TestPlatformContextBuilder();
    }

    /**
     * Set the {@link Configuration} to use. If null or not set, uses a default configuration.
     *
     * @param configuration the configuration to use
     * @return the builder instance
     */
    @NonNull
    public TestPlatformContextBuilder withConfiguration(@Nullable final Configuration configuration) {
        this.configuration = configuration;
        return this;
    }

    /**
     * Set the {@link Metrics} to use. If null or not set, uses a default metrics instance.
     *
     * @param metrics the metrics to use
     */
    @NonNull
    public TestPlatformContextBuilder withMetrics(@Nullable final Metrics metrics) {
        this.metrics = metrics;
        return this;
    }

    /**
     * Set the {@link Cryptography} to use. If null or not set, uses a default cryptography instance.
     *
     * @param cryptography the cryptography to use
     */
    @NonNull
    public TestPlatformContextBuilder withCryptography(@Nullable final Cryptography cryptography) {
        this.cryptography = cryptography;
        return this;
    }

    /**
     * Set the {@link Time} to use.
     *
     * @param time the time to use
     */
    @NonNull
    public TestPlatformContextBuilder withTime(@NonNull final Time time) {
        this.time = Objects.requireNonNull(time);
        return this;
    }

    @NonNull
    public TestPlatformContextBuilder withRecycleBin(@Nullable final RecycleBin recycleBin) {
        this.recycleBin = recycleBin;
        return this;
    }

    @NonNull
    public TestPlatformContextBuilder withFileSystemManager(@NonNull final FileSystemManager fileSystemManager) {
        this.fileSystemManager = fileSystemManager;
        return this;
    }

    @NonNull
    public TestPlatformContextBuilder withTestFileSystemManagerUnder(@NonNull final Path rootPath) {
        this.fileSystemManager = new TestFileSystemManager(rootPath);
        return this;
    }

    @NonNull
    public TestPlatformContextBuilder withMerkleCryptography(@NonNull final MerkleCryptography merkleCryptography) {
        this.merkleCryptography = merkleCryptography;
        return this;
    }

    /**
     * Returns a new {@link PlatformContext} based on this builder
     *
     * @return a new {@link PlatformContext}
     */
    public PlatformContext build() {
        if (configuration == null) {
            this.configuration = defaultConfig;
        }
        if (metrics == null) {
            this.metrics = defaultMetrics; // FUTURE WORK: replace this with NoOp Metrics
        }
        if (this.cryptography == null) {
            this.cryptography = defaultCryptography;
        }

        if (recycleBin == null) {
            this.recycleBin = new NoOpRecycleBin();
        }
        if (this.fileSystemManager == null) {
            this.fileSystemManager = getTestFileSystemManager();
        }

        final ExecutorFactory executorFactory = ExecutorFactory.create("test", new UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                fail("Uncaught exception in thread " + t.getName(), e);
            }
        });

        return new PlatformContext() {
            @NonNull
            @Override
            public Configuration getConfiguration() {
                return configuration;
            }

            @NonNull
            @Override
            public Cryptography getCryptography() {
                return cryptography;
            }

            @NonNull
            @Override
            public Metrics getMetrics() {
                return metrics;
            }

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

            @NonNull
            @Override
            public ExecutorFactory getExecutorFactory() {
                return executorFactory;
            }

            @NonNull
            @Override
            public RecycleBin getRecycleBin() {
                return recycleBin;
            }

            @NonNull
            @Override
            public MerkleCryptography getMerkleCryptography() {
                return merkleCryptography;
            }
        };
    }

    private static TestFileSystemManager getTestFileSystemManager() {
        Path defaultRootLocation = rethrowIO(() -> Files.createTempDirectory("testRootDir"));
        return new TestFileSystemManager(defaultRootLocation);
    }
}
