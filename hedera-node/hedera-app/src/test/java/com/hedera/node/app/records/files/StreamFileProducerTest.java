/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.records.files;

import static com.hedera.node.app.records.files.RecordStreamV6Verifier.validateRecordStreamFiles;
import static com.hedera.node.app.records.files.RecordTestData.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.node.app.records.BlockRecordStreamConfig;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.spi.records.SingleTransactionRecord;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfiguration;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("DataFlowIssue")
@ExtendWith(MockitoExtension.class)
public class StreamFileProducerTest {
    private @Mock ConfigProvider configProvider;
    private @Mock VersionedConfiguration versionedConfiguration;
    private @Mock NodeInfo nodeInfo;

    /** Temporary in memory file system used for testing */
    private FileSystem fs;

    void setUpEach(int sidecarMaxSizeMb) throws Exception {
        // create in memory temp dir
        fs = Jimfs.newFileSystem(Configuration.unix());
        Path tempDir = fs.getPath("/temp");
        Files.createDirectory(tempDir);

        // setup config
        final BlockRecordStreamConfig recordStreamConfig =
                new BlockRecordStreamConfig(true, "", "sidecar", 2, 5000, false, 256, 6, 6, true, true, 256);
        // setup mocks
        when(versionedConfiguration.getConfigData(BlockRecordStreamConfig.class))
                .thenReturn(recordStreamConfig);
        when(configProvider.getConfiguration()).thenReturn(versionedConfiguration);
        when(nodeInfo.accountMemo()).thenReturn("test-node");
        when(nodeInfo.hapiVersion()).thenReturn(VERSION);
    }

    @AfterEach
    public void shutdown() throws Exception {
        fs.close();
    }

    /**
     * BlockRecordManager mostly writes records one at a time so simulate that here
     */
    @ParameterizedTest
    @CsvSource({
        "StreamFileProducerSingleThreaded, 256",
        "StreamFileProducerConcurrent,256",
        "StreamFileProducerConcurrent,1"
    })
    public void oneTransactionAtATime(final String streamFileProducerClassName, int sidecarMaxSizeMb) throws Exception {
        setUpEach(sidecarMaxSizeMb);
        doTestCommon(
                streamFileProducerClassName, (streamFileProducer, blockData, block, blockFirstTransactionTimestamp) -> {
                    for (var record : blockData) {
                        streamFileProducer.writeRecordStreamItems(
                                block, blockFirstTransactionTimestamp, Stream.of(record));
                    }
                });
    }

    /**
     * It is also interesting to test as larger batches because in theory 1 user transaction could be 100 transactions
     */
    @ParameterizedTest
    @CsvSource({
        "StreamFileProducerSingleThreaded, 256",
        "StreamFileProducerConcurrent,256",
        "StreamFileProducerConcurrent,1"
    })
    public void batchOfTransactions(final String streamFileProducerClassName, int sidecarMaxSizeMb) throws Exception {
        setUpEach(sidecarMaxSizeMb);
        final Random random = new Random(82792874);
        doTestCommon(
                streamFileProducerClassName, (streamFileProducer, blockData, block, blockFirstTransactionTimestamp) -> {
                    int i = 0;
                    while (i < blockData.size()) {
                        // write batch == simulated user transaction
                        final int batchSize = Math.min(random.nextInt(100) + 1, blockData.size() - i);
                        streamFileProducer.writeRecordStreamItems(
                                block, blockFirstTransactionTimestamp, blockData.subList(i, i + batchSize).stream());
                        i += batchSize;
                    }
                });
    }

    /**
     * Common test implementation for streamFileProducer and batchOfTransactions
     *
     * @param streamFileProducerClassName the class name for the StreamFileProducer
     * @param blockWriter the block writer to use
     */
    private void doTestCommon(final String streamFileProducerClassName, BlockWriter blockWriter) throws Exception {
        Bytes finalRunningHash;
        try (StreamFileProducerBase streamFileProducer =
                switch (streamFileProducerClassName) {
                    case "StreamFileProducerSingleThreaded" -> new StreamFileProducerSingleThreaded(
                            configProvider, nodeInfo, SIGNER, fs);
                    case "StreamFileProducerConcurrent" -> new StreamFileProducerConcurrent(
                            configProvider, nodeInfo, SIGNER, fs, ForkJoinPool.commonPool());
                    default -> throw new IllegalArgumentException(
                            "Unknown streamFileProducerClassName: " + streamFileProducerClassName);
                }) {
            streamFileProducer.setRunningHash(STARTING_RUNNING_HASH_OBJ.hash());
            long block = BLOCK_NUM - 1;
            // write a blocks & record files
            for (var blockData : TEST_BLOCKS) {
                block++;
                final Instant blockFirstTransactionTimestamp =
                        fromTimestamp(blockData.get(0).record().consensusTimestamp());
                streamFileProducer.switchBlocks(block - 1, block, blockFirstTransactionTimestamp);
                blockWriter.write(streamFileProducer, blockData, block, blockFirstTransactionTimestamp);
            }
            // collect final running hash
            finalRunningHash = streamFileProducer.getRunningHash();
        }

        // check running hash
        assertEquals(
                computeRunningHash(
                                STARTING_RUNNING_HASH_OBJ.hash(),
                                TEST_BLOCKS.stream().flatMap(List::stream).toList())
                        .toHex(),
                finalRunningHash.toHex());
        // print out all files
        try (var pathStream = Files.walk(fs.getPath("/temp"))) {
            pathStream.filter(Files::isRegularFile).forEach(file -> {
                try {
                    System.out.println(file.toAbsolutePath() + " - (" + Files.size(file) + ")");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        // check record files
        final var recordStreamConfig = versionedConfiguration.getConfigData(BlockRecordStreamConfig.class);
        validateRecordStreamFiles(
                fs.getPath(recordStreamConfig.logDir()).resolve("record" + nodeInfo.accountMemo()),
                recordStreamConfig,
                USER_PUBLIC_KEY,
                TEST_BLOCKS,
                BLOCK_NUM);
    }

    /**
     * It is important to test the get running hash query methods return the right hashes at the right point in time.
     * We have to simulate user transactions with more than one transaction because hashes only update once per user
     * transaction if it is a single transaction or 10 transactions.
     */
    @ParameterizedTest
    @CsvSource({"StreamFileProducerSingleThreaded", "StreamFileProducerConcurrent"})
    public void testRunningHashes(final String streamFileProducerClassName) throws Exception {
        setUpEach(256);
        final Random random = new Random(82792874);
        try (StreamFileProducerBase streamFileProducer =
                switch (streamFileProducerClassName) {
                    case "StreamFileProducerSingleThreaded" -> new StreamFileProducerSingleThreaded(
                            configProvider, nodeInfo, SIGNER, fs);
                    case "StreamFileProducerConcurrent" -> new StreamFileProducerConcurrent(
                            configProvider, nodeInfo, SIGNER, fs, ForkJoinPool.commonPool());
                    default -> throw new IllegalArgumentException(
                            "Unknown streamFileProducerClassName: " + streamFileProducerClassName);
                }) {
            streamFileProducer.setRunningHash(STARTING_RUNNING_HASH_OBJ.hash());
            long block = BLOCK_NUM - 1;
            Bytes runningHash = STARTING_RUNNING_HASH_OBJ.hash();
            Bytes runningHashNMinus1 = null;
            Bytes runningHashNMinus2 = null;
            Bytes runningHashNMinus3;
            // write a blocks & record files
            for (var blockData : TEST_BLOCKS) {
                block++;
                final Instant blockFirstTransactionTimestamp =
                        fromTimestamp(blockData.get(0).record().consensusTimestamp());
                streamFileProducer.switchBlocks(block - 1, block, blockFirstTransactionTimestamp);
                // write transactions in random batches to simulate user transactions of different sizes
                int i = 0;
                while (i < blockData.size()) {
                    // write batch == simulated user transaction
                    final int batchSize = Math.min(random.nextInt(2) + 1, blockData.size() - i);
                    streamFileProducer.writeRecordStreamItems(
                            block, blockFirstTransactionTimestamp, blockData.subList(i, i + batchSize).stream());
                    i += batchSize;
                    // collect hashes
                    runningHashNMinus3 = runningHashNMinus2;
                    runningHashNMinus2 = runningHashNMinus1;
                    runningHashNMinus1 = runningHash;
                    runningHash = streamFileProducer.getRunningHash();
                    // check running hash N - 3
                    if (runningHashNMinus3 != null) {
                        assertEquals(
                                runningHashNMinus3.toHex(),
                                streamFileProducer.getNMinus3RunningHash().toHex());
                    } else {
                        // check nulls as well
                        assertNull(streamFileProducer.getNMinus3RunningHash());
                    }
                }
            }
            // check running hash
            assertEquals(
                    computeRunningHash(
                                    STARTING_RUNNING_HASH_OBJ.hash(),
                                    TEST_BLOCKS.stream().flatMap(List::stream).toList())
                            .toHex(),
                    runningHash.toHex());
        }
    }

    /** Given a list of items and a starting hash calculate the running hash at the end */
    private Bytes computeRunningHash(
            final Bytes startingHash, final List<SingleTransactionRecord> transactionRecordList) {
        return RecordFileFormatV6.INSTANCE.computeNewRunningHash(
                startingHash,
                transactionRecordList.stream()
                        .map(str -> RecordFileFormatV6.INSTANCE.serialize(str, BLOCK_NUM, VERSION))
                        .toList());
    }

    private static Instant fromTimestamp(final Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.seconds(), timestamp.nanos());
    }

    /** Interface for BlockWriter used for lambdas in tests */
    private interface BlockWriter {
        void write(
                @NonNull final StreamFileProducerBase streamFileProducer,
                @NonNull final List<SingleTransactionRecord> blockData,
                final long block,
                @Nullable final Instant blockFirstTransactionTimestamp);
    }
}
