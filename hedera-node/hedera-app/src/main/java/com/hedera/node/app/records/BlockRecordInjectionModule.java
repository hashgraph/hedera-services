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
import com.hedera.node.app.records.impl.BlockStreamManagerImpl;
import com.hedera.node.app.records.impl.BlockStreamProducer;
import com.hedera.node.app.records.impl.producers.BlockRecordFormat;
import com.hedera.node.app.records.impl.producers.BlockRecordWriterFactory;
import com.hedera.node.app.records.impl.producers.BlockStreamFormat;
import com.hedera.node.app.records.impl.producers.BlockStreamProducerSingleThreaded;
import com.hedera.node.app.records.impl.producers.BlockStreamWriterFactory;
import com.hedera.node.app.records.impl.producers.StreamFileProducerConcurrent;
import com.hedera.node.app.records.impl.producers.StreamFileProducerSingleThreaded;
import com.hedera.node.app.records.impl.producers.formats.BlockRecordWriterFactoryImpl;
import com.hedera.node.app.records.impl.producers.formats.v1.BlockStreamFormatV1;
import com.hedera.node.app.records.impl.producers.formats.v6.BlockRecordFormatV6;
import com.hedera.node.app.records.impl.producers.formats.v7.BlockRecordFormatV7;
import com.hedera.node.app.records.impl.producers.formats.v8.BlockStreamWriterFactoryImpl;
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
import java.util.concurrent.ExecutorService;
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
        final var recordStreamConfig = configProvider.getConfiguration().getConfigData(BlockRecordStreamConfig.class);
        final var producerType = recordStreamConfig.streamFileProducer().toUpperCase();
        return switch (producerType) {
            case "CONCURRENT" -> concurrent;
            case "SERIAL" -> serial;
            default -> throw new IllegalArgumentException("Unknown stream file producer type: " + producerType);
        };
    }

    /** Provides an implementation of the {@link BlockRecordFormat}.
     * @param configProvider the configuration provider
     * @return the block record format
     */
    @Provides
    @Singleton
    public static BlockRecordFormat provideBlockRecordFormat(@NonNull final ConfigProvider configProvider) {
        final var recordStreamConfig = configProvider.getConfiguration().getConfigData(BlockRecordStreamConfig.class);
        final var recordFileVersion = recordStreamConfig.recordFileVersion();
        return switch (recordFileVersion) {
            case BlockRecordFormatV6.VERSION_6 -> BlockRecordFormatV6.INSTANCE;
            case BlockRecordFormatV7.VERSION_7, BlockStreamFormatV1.VERSION_8 -> BlockRecordFormatV7.INSTANCE;
            default -> throw new IllegalArgumentException("Unknown block record version: " + recordFileVersion);
        };
    }

    /** Provides an implementation of the {@link com.hedera.node.app.records.BlockRecordManager}. */
    @Provides
    @Singleton
    @NonNull
    public static BlockRecordManager provideBlockRecordManager(
            @NonNull final ExecutorService executor,
            @NonNull final ConfigProvider configProvider,
            @NonNull final WorkingStateAccessor state,
            @NonNull final BlockRecordStreamProducer streamFileProducer,
            @NonNull final BlockStreamProducer blockStreamProducer) {
        final var hederaState = state.getHederaState();
        if (hederaState == null) {
            throw new IllegalStateException("Hedera state is null");
        }

        final var recordStreamConfig = configProvider.getConfiguration().getConfigData(BlockRecordStreamConfig.class);
        final var recordFileVersion = recordStreamConfig.recordFileVersion();
        return switch (recordFileVersion) {
            case BlockRecordFormatV6.VERSION_6 -> new BlockRecordManagerImpl(
                    configProvider, hederaState, streamFileProducer);
            case BlockStreamFormatV1.VERSION_8 -> provideBlockStreamManager(
                    executor, configProvider, state, blockStreamProducer);
            default -> {
                throw new IllegalArgumentException("Unknown block record version: " + recordFileVersion);
            }
        };
    }

    @Binds
    @Singleton
    public abstract BlockRecordWriterFactory provideBlockRecordWriterFactory(
            @NonNull BlockRecordWriterFactoryImpl impl);

    @Provides
    @Singleton
    @NonNull
    public static BlockStreamFormat provideBlockStreamFormat(@NonNull final ConfigProvider configProvider) {
        final var blockVersion = validateBlockStreamVersion(configProvider);
        return switch (8) {
            case BlockStreamFormatV1.VERSION_8 -> BlockStreamFormatV1.INSTANCE;
            default -> {
                throw new IllegalArgumentException("Unknown block stream version: " + 8);
            }
        };
    }

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
            @NonNull final ExecutorService executor,
            @NonNull final ConfigProvider configProvider,
            @NonNull final BlockStreamProducerSingleThreaded serial) {
        final var recordStreamConfig = configProvider.getConfiguration().getConfigData(BlockStreamConfig.class);
        final var producerType = recordStreamConfig.streamFileProducer().toUpperCase();
        return switch (producerType) {
            case "SERIAL" -> serial;
            default -> {
                logger.fatal("Unknown stream file producer type: {}", producerType);
                throw new IllegalArgumentException("Unknown stream file producer type: " + producerType);
            }
        };
    }

    @Binds
    @Singleton
    @NonNull
    public abstract BlockStreamWriterFactory provideBlockStreamWriterFactory(
            @NonNull BlockStreamWriterFactoryImpl impl);

    // @Provides
    @Singleton
    public static BlockRecordManager provideBlockStreamManager(
            @NonNull final ExecutorService executor,
            @NonNull final ConfigProvider configProvider,
            @NonNull final WorkingStateAccessor state,
            @NonNull final BlockStreamProducer blockStreamProducer) {
        final var hederaState = state.getHederaState();
        if (hederaState == null) {
            throw new IllegalStateException("Hedera state is null");
        }
        return new BlockStreamManagerImpl(executor, configProvider, hederaState, blockStreamProducer);
    }

    private static int validateBlockStreamVersion(@NonNull final ConfigProvider configProvider) {
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
        if (blockVersion < BlockStreamFormatV1.VERSION_8) {
            // This is only a hint as block streams started at version 8.
            logger.fatal("Block stream version must be >= 8. See BlockRecordInjectionModule instead.");
            throw new IllegalArgumentException(
                    "Block stream version must be >= 8. See BlockRecordInjectionModule instead.");
        }
        return blockVersion;
    }
}
