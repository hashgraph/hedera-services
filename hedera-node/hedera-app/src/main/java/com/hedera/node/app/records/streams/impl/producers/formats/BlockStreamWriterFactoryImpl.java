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

package com.hedera.node.app.records.streams.impl.producers.formats;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.records.BlockRecordInjectionModule.AsyncWorkStealingExecutor;
import com.hedera.node.app.records.streams.impl.producers.BlockStreamWriter;
import com.hedera.node.app.records.streams.impl.producers.BlockStreamWriterFactory;
import com.hedera.node.app.records.streams.impl.producers.ConcurrentBlockStreamWriter;
import com.hedera.node.app.records.streams.impl.producers.formats.v1.BlockStreamFileWriterV1;
import com.hedera.node.app.records.streams.impl.producers.formats.v1.BlockStreamGrpcWriterV1;
import com.amh.config.ConfigProvider;
import com.swirlds.common.stream.Signer;
import com.swirlds.platform.state.merkle.disk.BlockStreamConfig;
import com.swirlds.state.spi.info.SelfNodeInfo;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.FileSystem;
import java.util.concurrent.ExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class BlockStreamWriterFactoryImpl implements BlockStreamWriterFactory {
    private final ConfigProvider configProvider;
    private final Signer signer;
    private final SelfNodeInfo nodeInfo;
    private final FileSystem fileSystem;
    private final ExecutorService executor;

    /**
     * A factory to create writers for the block stream. We specify the annotation @AsyncWorkStealingExecutor to ensure
     * dagger provides the correct executor.
     *
     * @param executor the executor to use for writing the block stream, should be a work stealing executor
     * @param configProvider the configuration provider
     * @param nodeInfo the node info
     * @param fileSystem the file system to use, also used in testing to be able to use a non-standard file system
     */
    @Inject
    public BlockStreamWriterFactoryImpl(
            @NonNull @AsyncWorkStealingExecutor final ExecutorService executor,
            @NonNull final ConfigProvider configProvider,
            @NonNull final SelfNodeInfo nodeInfo,
            @NonNull final Signer signer,
            @NonNull final FileSystem fileSystem) {
        this.executor = requireNonNull(executor);
        this.configProvider = requireNonNull(configProvider);
        this.nodeInfo = requireNonNull(nodeInfo);
        this.fileSystem = requireNonNull(fileSystem);
        this.signer = requireNonNull(signer);
    }

    @Override
    @NonNull
    public BlockStreamWriter create() throws RuntimeException {
        // read configuration
        final var config = configProvider.getConfiguration();
        final var recordStreamConfig = config.getConfigData(BlockStreamConfig.class);
        final var recordFileVersion = recordStreamConfig.blockVersion();

        // pick a record file format
        return switch (recordFileVersion) {
            case 6 -> throw new IllegalArgumentException(
                    "Record file version 6 is not supported by BlockRecordWriterFactoryImpl");
            case 7 -> createBlockStreamWriter(executor, recordStreamConfig.writer());
            default -> throw new IllegalArgumentException("Unknown record file version: " + recordFileVersion);
        };
    }

    @NonNull
    private BlockStreamWriter createBlockStreamWriter(
            @NonNull final ExecutorService executor, @NonNull final String writer) {
        return switch (writer) {
            case "file" -> new ConcurrentBlockStreamWriter(
                    executor,
                    new BlockStreamFileWriterV1(
                            configProvider.getConfiguration().getConfigData(BlockStreamConfig.class),
                            nodeInfo,
                            fileSystem,
                            nodeInfo.hapiVersion(),
                            signer));
            case "grpc" -> new BlockStreamGrpcWriterV1(
                    configProvider.getConfiguration().getConfigData(BlockStreamConfig.class), nodeInfo);
            default -> throw new IllegalArgumentException("Unknown writer type: " + writer);
        };
    }
}
