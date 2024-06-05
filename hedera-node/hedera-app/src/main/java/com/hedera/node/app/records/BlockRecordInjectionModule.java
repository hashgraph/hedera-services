/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.records;

import com.hedera.node.app.records.impl.BlockRecordManagerImpl;
import com.hedera.node.app.records.impl.BlockRecordStreamProducer;
import com.hedera.node.app.records.impl.producers.BlockRecordFormat;
import com.hedera.node.app.records.impl.producers.BlockRecordWriterFactory;
import com.hedera.node.app.records.impl.producers.StreamFileProducerConcurrent;
import com.hedera.node.app.records.impl.producers.StreamFileProducerSingleThreaded;
import com.hedera.node.app.records.impl.producers.formats.BlockRecordWriterFactoryImpl;
import com.hedera.node.app.records.impl.producers.formats.v6.BlockRecordFormatV6;
import com.hedera.node.app.records.streams.impl.BlockStreamManagerImpl;
import com.hedera.node.app.records.streams.impl.BlockStreamProducer;
import com.hedera.node.app.records.streams.impl.producers.BlockStreamFormat;
import com.hedera.node.app.records.streams.impl.producers.BlockStreamProducerSingleThreaded;
import com.hedera.node.app.records.streams.impl.producers.BlockStreamWriterFactory;
import com.hedera.node.app.records.streams.impl.producers.ConcurrentBlockStreamProducer;
import com.hedera.node.app.records.streams.impl.producers.formats.BlockStreamWriterFactoryImpl;
import com.hedera.node.app.records.streams.impl.producers.formats.v1.BlockStreamFormatV1;
import com.swirlds.platform.state.merkle.disk.BlockObserverSingleton;
import com.hedera.node.app.state.WorkingStateAccessor;
import com.amh.config.ConfigProvider;
import com.swirlds.platform.state.merkle.disk.BlockRecordStreamConfig;
import com.swirlds.platform.state.merkle.disk.BlockStreamConfig;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Qualifier;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** A Dagger module for facilities in the {@link com.hedera.node.app.records} package. */
@Module
public abstract class BlockRecordInjectionModule {

    private static final Logger logger = LogManager.getLogger(BlockRecordInjectionModule.class);

    protected BlockRecordInjectionModule() {
        /* Nothing to do */
    }

    @Qualifier // Create a new qualifier annotation, that can be used to provide a specific executor.
    @Retention(RetentionPolicy.RUNTIME) // Ensure the @AsyncWorkStealingExecutor annotation is available at runtime.
    public @interface AsyncWorkStealingExecutor {}

    @Provides
    @Singleton
    @AsyncWorkStealingExecutor
    static ExecutorService provideExecutorService() {
        // Customize the parallelism level if needed, or leave it to default
        return Executors.newWorkStealingPool();
    }

    /** Provides the normal, default {@link java.nio.file.FileSystem}.*/
    @Provides
    @Singleton
    @NonNull
    static FileSystem provideFileSystem() {
        return FileSystems.getDefault();
    }

    /**
     * Provides a {@link BlockRecordStreamProducer} based on the configuration. It is possible to use a concurrent producer,
     * or a single-threaded producer, based on configuration.
     */
    @Provides
    @Singleton
    @NonNull
    public static BlockRecordStreamProducer provideStreamFileProducer(
            @NonNull final ConfigProvider configProvider,
            @NonNull final StreamFileProducerConcurrent concurrent,
            @NonNull final StreamFileProducerSingleThreaded serial) {
        final var recordStreamConfig = configProvider.getConfiguration().getConfigData(
                BlockRecordStreamConfig.class);
        final var producerType = recordStreamConfig.streamFileProducer().toUpperCase();
        return switch (producerType) {
            case "CONCURRENT" -> concurrent;
            case "SERIAL" -> serial;
            default -> {
                logger.fatal("Unknown stream file producer type: {}", producerType);
                throw new IllegalArgumentException("Unknown stream file producer type: " + producerType);
            }
        };
    }

    /** Provides an implementation of the {@link com.hedera.node.app.records.BlockRecordManager}. */
    @Provides
    @Singleton
    @NonNull
    public static FunctionalBlockRecordManager provideBlockRecordManager(
            @NonNull @AsyncWorkStealingExecutor final ExecutorService executor,
            @NonNull final ConfigProvider configProvider,
            @NonNull final WorkingStateAccessor state,
            @NonNull final BlockRecordStreamProducer streamFileProducer,
            @NonNull final BlockStreamProducer blockStreamProducer) {

        // We also want to create a BlockObserver instance at this point.
        BlockObserverSingleton.initInstance(configProvider);

        final var hederaState = state.getHederaState();
        if (hederaState == null) {
            logger.fatal("Hedera state is null");
            throw new IllegalStateException("Hedera state is null");
        }

        final var recordStreamConfig = configProvider.getConfiguration().getConfigData(BlockRecordStreamConfig.class);
        final var recordFileVersion = recordStreamConfig.recordFileVersion();
        return switch (recordFileVersion) {
            case BlockRecordFormatV6.VERSION_6 -> new BlockRecordManagerImpl(
                    configProvider, hederaState, streamFileProducer);
            case BlockStreamFormatV1.VERSION_7 -> provideBlockStreamManager(
                    executor, configProvider, state, blockStreamProducer);
            default -> {
                logger.fatal("Unknown block record version: {}", recordFileVersion);
                throw new IllegalArgumentException("Unknown block record version: " + recordFileVersion);
            }
        };
    }

    @Provides
    @Singleton
    @NonNull
    public static BlockRecordFormat provideBlockRecordFormat(@NonNull final ConfigProvider configProvider) {
        final var recordStreamConfig = configProvider.getConfiguration().getConfigData(BlockRecordStreamConfig.class);
        var recordFileVersion = recordStreamConfig.recordFileVersion();
        if (recordFileVersion > BlockRecordFormatV6.VERSION_6) {
            // The max it can be is 6. So we'll use v6 and can continue on.
            //
            // FUTURE: We should stub out a NoOp v6 implementation and use that instead since we have to have it for
            // dagger.
            recordFileVersion = BlockRecordFormatV6.VERSION_6;
        }
        return switch (recordFileVersion) {
            case BlockRecordFormatV6.VERSION_6 -> BlockRecordFormatV6.INSTANCE;
            default -> {
                logger.fatal("Unknown block record version: {}", recordFileVersion);
                throw new IllegalArgumentException("Unknown block record version: " + recordFileVersion);
            }
        };
    }

    @Binds
    @Singleton
    @NonNull
    public abstract BlockRecordWriterFactory provideBlockRecordWriterFactory(
            @NonNull BlockRecordWriterFactoryImpl impl);

    // ==================== BLOCK STREAM ====================
    /**
     * Provides a {@link BlockStreamProducer} based on the configuration. It is possible to use a concurrent producer,
     * or a single-threaded producer, based on configuration.
     *
     * <p> We want to use an async ForkJoinPool as the executor, not the common pool. We create recursive tasks within
     *     the producer, and therefore want the LIFO semantics. This should help reduce latency for the block that is
     *     currently being constructed.
     */
    @Provides
    @Singleton
    @NonNull
    public static BlockStreamProducer provideBlockStreamFileProducer(
            @NonNull @AsyncWorkStealingExecutor final ExecutorService executor,
            @NonNull final ConfigProvider configProvider,
            @NonNull final BlockStreamProducerSingleThreaded serial) {
        final var recordStreamConfig = configProvider.getConfiguration().getConfigData(
                BlockStreamConfig.class);
        final var producerType = recordStreamConfig.streamFileProducer().toUpperCase();
        return switch (producerType) {
            case "CONCURRENT" -> new ConcurrentBlockStreamProducer(executor, serial);
            case "SERIAL" -> serial;
            default -> {
                logger.fatal("Unknown stream file producer type: {}", producerType);
                throw new IllegalArgumentException("Unknown stream file producer type: " + producerType);
            }
        };
    }

    /** Provides an implementation of the {@link BlockRecordManager}. */
    // FUTURE: Annotate ExecutorService with @AsyncWorkStealingExecutor and uncomment the @Provides once we deprecate
    // records so this is directly provided by dagger.
    // @Provides
    @Singleton
    @NonNull
    public static FunctionalBlockRecordManager provideBlockStreamManager(
            @NonNull final ExecutorService executor,
            @NonNull final ConfigProvider configProvider,
            @NonNull final WorkingStateAccessor state,
            @NonNull final BlockStreamProducer blockStreamProducer) {
        validateBlockStreamVersion(configProvider);

        // We also want to create a BlockObserver instance at this point.
        BlockObserverSingleton.initInstance(configProvider);

        final var hederaState = state.getHederaState();
        if (hederaState == null) {
            logger.fatal("Hedera state is null");
            throw new IllegalStateException("Hedera state is null");
        }

        return new BlockStreamManagerImpl(executor, configProvider, hederaState, blockStreamProducer);
    }

    @Provides
    @Singleton
    @NonNull
    public static BlockStreamFormat provideBlockStreamFormat(@NonNull final ConfigProvider configProvider) {
        final var blockVersion = validateBlockStreamVersion(configProvider);
        return switch (blockVersion) {
            case BlockStreamFormatV1.VERSION_7 -> BlockStreamFormatV1.INSTANCE;
            default -> {
                logger.fatal("Unknown block stream version: {}", blockVersion);
                throw new IllegalArgumentException("Unknown block stream version: " + blockVersion);
            }
        };
    }

    @Binds
    @Singleton
    @NonNull
    public abstract BlockStreamWriterFactory provideBlockStreamWriterFactory(
            @NonNull BlockStreamWriterFactoryImpl impl);

    private static int validateBlockStreamVersion(@NonNull final ConfigProvider configProvider) {
        // This is the only time BlockRecordStreamConfig should be referenced in this package.
        final var recordStreamConfig = configProvider.getConfiguration().getConfigData(BlockRecordStreamConfig.class);
        final var recordFileVersion = recordStreamConfig.recordFileVersion();

        final var blockStreamConfig = configProvider.getConfiguration().getConfigData(BlockStreamConfig.class);
        final var blockVersion = blockStreamConfig.blockVersion();

        if (!blockStreamConfig.enabled()) {
            // Not running block streams so no need to validate the version.
            return blockVersion;
        }

        // This is a sanity check until record streams are deprecated and we are fully switched to block streams.
        // FUTURE: Remove this check once we deprecate records.
        if (recordFileVersion != blockVersion) {
            logger.fatal("Record file version must match block stream version.");
            throw new IllegalArgumentException("Record file version must match block stream version.");
        }
        if (blockVersion < BlockStreamFormatV1.VERSION_7) {
            // This is only a hint as block streams started at version 7.
            logger.fatal("Block stream version must be >= 7. See BlockRecordInjectionModule instead.");
            throw new IllegalArgumentException(
                    "Block stream version must be >= 7. See BlockRecordInjectionModule instead.");
        }
        return blockVersion;
    }
}
