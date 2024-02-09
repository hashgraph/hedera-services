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
import com.hedera.node.app.records.streams.impl.producers.BlockStreamProducerConcurrent;
import com.hedera.node.app.records.streams.impl.producers.BlockStreamProducerSingleThreaded;
import com.hedera.node.app.records.streams.impl.producers.BlockStreamWriterFactory;
import com.hedera.node.app.records.streams.impl.producers.formats.BlockStreamWriterFactoryImpl;
import com.hedera.node.app.records.streams.impl.producers.formats.v1.BlockStreamFormatV1;
import com.hedera.node.app.records.streams.state.BlockObserverSingleton;
import com.hedera.node.app.state.WorkingStateAccessor;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.BlockRecordStreamConfig;
import com.hedera.node.config.data.BlockStreamConfig;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.util.concurrent.Executors;
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

    /** Provides the normal, default {@link java.nio.file.FileSystem}.*/
    @Provides
    @Singleton
    static FileSystem provideFileSystem() {
        System.out.println("Called provideFileSystem");
        return FileSystems.getDefault();
    }

    /**
     * Provides a {@link BlockRecordStreamProducer} based on the configuration. It is possible to use a concurrent producer,
     * or a single-threaded producer, based on configuration.
     */
    @Provides
    @Singleton
    public static BlockRecordStreamProducer provideStreamFileProducer(
            @NonNull final ConfigProvider configProvider,
            @NonNull final StreamFileProducerConcurrent concurrent,
            @NonNull final StreamFileProducerSingleThreaded serial) {
        System.out.println("Called provideStreamFileProducer");
        final var recordStreamConfig = configProvider.getConfiguration().getConfigData(BlockRecordStreamConfig.class);
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
    public static FunctionalBlockRecordManager provideBlockRecordManager(
            @NonNull final ConfigProvider configProvider,
            @NonNull final WorkingStateAccessor state,
            @NonNull final BlockRecordStreamProducer streamFileProducer,
            @NonNull final BlockStreamProducer blockStreamProducer) {
        System.out.println("Called provideBlockRecordManager");

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
            case BlockStreamFormatV1.VERSION_7 -> provideBlockStreamManager(configProvider, state, blockStreamProducer);
            default -> {
                logger.fatal("Unknown block record version: {}", recordFileVersion);
                throw new IllegalArgumentException("Unknown block record version: " + recordFileVersion);
            }
        };
    }

    @Provides
    @Singleton
    public static BlockRecordFormat provideBlockRecordFormat(@NonNull final ConfigProvider configProvider) {
        System.out.println("Called provideBlockRecordFormat");
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
    public abstract BlockRecordWriterFactory provideBlockRecordWriterFactory(
            @NonNull BlockRecordWriterFactoryImpl impl);

    // ==================== BLOCK STREAM ====================
    /**
     * Provides a {@link BlockStreamProducer} based on the configuration. It is possible to use a concurrent producer,
     * or a single-threaded producer, based on configuration.
     */
    @Provides
    @Singleton
    public static BlockStreamProducer provideBlockStreamFileProducer(
            @NonNull final ConfigProvider configProvider,
            //            @NonNull final BlockStreamProducerConcurrentV1 concurrent,
            @NonNull final BlockStreamProducerSingleThreaded serial) {
        System.out.println("Called provideBlockStreamFileProducer");
        final var recordStreamConfig = configProvider.getConfiguration().getConfigData(BlockStreamConfig.class);
        final var producerType = recordStreamConfig.streamFileProducer().toUpperCase();
        return switch (producerType) {
                // TODO(nickpoorman): Maybe we grab an executor from dagger and pass it in here?
                // We want to make sure we are using an async ForkJoinPool, not the common pool (which is not async).
            case "CONCURRENT" -> new BlockStreamProducerConcurrent(serial, Executors.newWorkStealingPool());
            case "SERIAL" -> serial;
            default -> {
                logger.fatal("Unknown stream file producer type: {}", producerType);
                throw new IllegalArgumentException("Unknown stream file producer type: " + producerType);
            }
        };
    }

    /** Provides an implementation of the {@link BlockRecordManager}. */
    //    @Provides // FUTURE: Uncomment this once we deprecate records.
    @Singleton
    public static FunctionalBlockRecordManager provideBlockStreamManager(
            @NonNull final ConfigProvider configProvider,
            @NonNull final WorkingStateAccessor state,
            @NonNull final BlockStreamProducer blockStreamProducer) {
        System.out.println("Called provideBlockStreamManager");
        validateBlockStreamVersion(configProvider);

        // We also want to create a BlockObserver instance at this point.
        BlockObserverSingleton.initInstance(configProvider);

        final var hederaState = state.getHederaState();
        if (hederaState == null) {
            logger.fatal("Hedera state is null");
            throw new IllegalStateException("Hedera state is null");
        }

        return new BlockStreamManagerImpl(configProvider, hederaState, blockStreamProducer);
    }

    @Provides
    @Singleton
    public static BlockStreamFormat provideBlockStreamFormat(@NonNull final ConfigProvider configProvider) {
        System.out.println("Called provideBlockStreamFormat");
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
    public abstract BlockStreamWriterFactory provideBlockStreamWriterFactory(
            @NonNull BlockStreamWriterFactoryImpl impl);

    private static int validateBlockStreamVersion(@NonNull final ConfigProvider configProvider) {
        System.out.println("Called validateBlockStreamVersion");
        // This is the only time BlockRecordStreamConfig should be referenced in this package.
        final var recordStreamConfig = configProvider.getConfiguration().getConfigData(BlockRecordStreamConfig.class);
        final var recordFileVersion = recordStreamConfig.recordFileVersion();

        final var blockStreamConfig = configProvider.getConfiguration().getConfigData(BlockStreamConfig.class);
        final var blockVersion = blockStreamConfig.blockVersion();
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
