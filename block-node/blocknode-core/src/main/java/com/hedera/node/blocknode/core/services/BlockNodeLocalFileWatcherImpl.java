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

package com.hedera.node.blocknode.core.services;

import static java.util.Objects.requireNonNull;

import com.hedera.services.stream.v7.proto.Block;
import com.hedera.services.stream.v7.proto.BlockItem;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BlockNodeLocalFileWatcherImpl {
    private static final Logger logger = LogManager.getLogger(BlockNodeLocalFileWatcherImpl.class);
    private static final String FILE_EXTENSION = ".blk.gz";

    private static final Path blocksLocPath = Path.of(
            "/home/nikolay/Desktop/hedera-services-nick/hedera-node/hedera-app/build/node/hedera-node/data/block-streams/block0.0.3/");
    private static final Path blocksOutputPath =
            Path.of("/home/nikolay/Desktop/hedera-services/block-node/blocknode-core/build/blocks/");

    private byte[] readCompressedFileBytes(final Path filepath) throws IOException {
        return (new GZIPInputStream(new FileInputStream(filepath.toString()))).readAllBytes();
    }

    private String extractBlockFileNameFromBlock(Block block) {
        Long blockNumber = null;
        for (BlockItem item : block.getItemsList()) {
            if (item.hasHeader()) {
                blockNumber = item.getHeader().getNumber();
                break;
            }
        }

        requireNonNull(blockNumber, "Block number can not be extracted.");

        return String.format("%036d", blockNumber) + FILE_EXTENSION;
    }

    private void writeBlockAsCompressedBytes(final Block block, final Path outputPath) {
        try {
            OutputStream out = Files.newOutputStream(outputPath.resolve(extractBlockFileNameFromBlock(block)));
            block.writeTo(new GZIPOutputStream(out, 1024 * 256));
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private FileAlterationListenerAdaptor buildFileListener() {
        return new FileAlterationListenerAdaptor() {
            @Override
            public void onFileCreate(File file) {
                final Path newFilePath = file.toPath();
                try {
                    byte[] content = readCompressedFileBytes(newFilePath);
                    Block block = Block.parseFrom(content);
                    writeBlockAsCompressedBytes(block, blocksOutputPath);
                    block.getItemsList().stream().toList().forEach(logger::info);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                logger.info("--- file create: " + newFilePath);
            }

            @Override
            public void onFileDelete(File file) {
                // no-op
            }

            @Override
            public void onFileChange(File file) {
                // no-op
            }
        };
    }

    public BlockNodeLocalFileWatcherImpl(@NonNull final ConfigProvider configProvider) {
        if (!blocksOutputPath.toFile().exists()) {
            blocksOutputPath.toFile().mkdirs();
        }

        final FileAlterationObserver observer = new FileAlterationObserver(blocksLocPath.toFile());
        observer.addListener(buildFileListener());

        final FileAlterationMonitor monitor = new FileAlterationMonitor(500L);
        monitor.addObserver(observer);
        try {
            monitor.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
