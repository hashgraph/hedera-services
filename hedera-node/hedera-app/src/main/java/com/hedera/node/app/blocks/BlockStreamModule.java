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

package com.hedera.node.app.blocks;

import com.hedera.node.app.blocks.impl.BlockStreamManagerImpl;
import com.hedera.node.app.blocks.impl.FileBlockItemWriter;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.BlockStreamConfig;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.state.spi.info.NodeInfo;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.FileSystem;
import java.util.function.Supplier;
import javax.inject.Singleton;

@Module
public interface BlockStreamModule {
    @Binds
    @Singleton
    BlockStreamManager bindBlockStreamManager(BlockStreamManagerImpl blockStreamManager);

    @Provides
    @Singleton
    static Supplier<BlockItemWriter> bindBlockItemWriterSupplier(
            @NonNull final ConfigProvider configProvider,
            @NonNull final NodeInfo selfNodeInfo,
            @NonNull final FileSystem fileSystem) {
        final var config = configProvider.getConfiguration();
        final var blockStreamConfig = config.getConfigData(BlockStreamConfig.class);
        return switch (blockStreamConfig.writerMode()) {
            case FILE -> () -> new BlockItemWriter() {
                @Override
                public void openBlock(long blockNumber) {
                }

                @Override
                public BlockItemWriter writeItem(@NonNull byte[] bytes) {
                    return this;
                }

                @Override
                public BlockItemWriter writeItems(@NonNull BufferedData data) {
                    return this;
                }

                @Override
                public void closeBlock() {

                }
            };
            case GRPC -> throw new IllegalArgumentException("gRPC block writer not yet implemented");
        };
    }

}
