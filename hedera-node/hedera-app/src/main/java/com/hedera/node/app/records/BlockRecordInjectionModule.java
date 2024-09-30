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
import com.hedera.node.app.records.impl.producers.formats.v7.BlockRecordFormatV7;
import com.hedera.node.app.state.HederaRecordCache;
import com.hedera.node.app.state.WorkingStateAccessor;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.BlockRecordStreamConfig;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import javax.inject.Singleton;

/** A Dagger module for facilities in the {@link com.hedera.node.app.records} package. */
@Module
public abstract class BlockRecordInjectionModule {

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

    /** Provides an implementation of the {@link com.hedera.node.app.records.BlockRecordManager}. */
    @Provides
    @Singleton
    public static BlockRecordManager provideBlockRecordManager(
            @NonNull final ConfigProvider configProvider,
            @NonNull final WorkingStateAccessor state,
            @NonNull final BlockRecordStreamProducer streamFileProducer,
            @NonNull final HederaRecordCache recordCache) {
        final var merkleState = state.getState();
        if (merkleState == null) {
            throw new IllegalStateException("Merkle state is null");
        }
        return new BlockRecordManagerImpl(configProvider, merkleState, streamFileProducer);
    }

    @Provides
    @Singleton
    public static BlockRecordFormat provideBlockRecordFormat(@NonNull final ConfigProvider configProvider) {
        final var recordStreamConfig = configProvider.getConfiguration().getConfigData(BlockRecordStreamConfig.class);
        final var recordFileVersion = recordStreamConfig.recordFileVersion();
        return switch (recordFileVersion) {
            case BlockRecordFormatV6.VERSION_6 -> BlockRecordFormatV6.INSTANCE;
            case BlockRecordFormatV7.VERSION_7 -> BlockRecordFormatV7.INSTANCE;
            default -> throw new IllegalArgumentException("Unknown block record version: " + recordFileVersion);
        };
    }

    @Binds
    @Singleton
    public abstract BlockRecordWriterFactory provideBlockRecordWriterFactory(
            @NonNull BlockRecordWriterFactoryImpl impl);
}
