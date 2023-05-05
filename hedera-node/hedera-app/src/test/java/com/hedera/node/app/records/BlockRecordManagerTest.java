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

package com.hedera.node.app.records;

import static com.hedera.node.app.records.files.RecordStreamV6Verifier.validateRecordStreamFiles;
import static com.hedera.node.app.records.files.RecordTestData.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.node.app.records.files.RecordFileReaderV6;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.spi.state.WritableSingletonState;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.state.WorkingStateAccessor;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfiguration;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"rawtypes", "unchecked", "DataFlowIssue"})
public class BlockRecordManagerTest {
    /** Make it small enough to trigger roll over code with num of test blocks we have */
    private static final int NUM_BLOCK_HASHES_TO_KEEP = 4;

    private @Mock ConfigProvider configProvider;
    private @Mock VersionedConfiguration versionedConfiguration;
    private @Mock NodeInfo nodeInfo;
    private @Mock WritableStates blockManagerWritableStates;
    private @Mock HederaState hederaState;
    private @Mock WorkingStateAccessor workingStateAccessor;

    /** Temporary in memory file system used for testing */
    private FileSystem fs;

    @BeforeEach
    void setUpEach() throws Exception {
        // create in memory temp dir
        fs = Jimfs.newFileSystem(Configuration.unix());
        Path tempDir = fs.getPath("/temp");
        Files.createDirectory(tempDir);

        // setup config
        final BlockRecordStreamConfig recordStreamConfig = new BlockRecordStreamConfig(
                true, tempDir.toString(), "sidecar", 2, 5000, true, 256, 6, 6, true, true, NUM_BLOCK_HASHES_TO_KEEP);
        // setup mocks
        when(versionedConfiguration.getConfigData(BlockRecordStreamConfig.class))
                .thenReturn(recordStreamConfig);
        when(configProvider.getConfiguration()).thenReturn(versionedConfiguration);
        when(nodeInfo.accountMemo()).thenReturn("test-node");
        when(nodeInfo.hapiVersion()).thenReturn(new SemanticVersion(1, 2, 3, "", ""));
        // create a BlockInfo state
        WritableSingletonState<BlockInfo> blockInfoWritableSingletonState = new BlockInfoWritableSingletonState();
        // create a RunningHashes state
        WritableSingletonState<RunningHashes> runningHashesWritableSingletonState =
                new RunningHashesWritableSingletonState();
        // setup initial block info
        when(blockManagerWritableStates.getSingleton(BlockRecordService.BLOCK_INFO_STATE_KEY))
                .thenReturn((WritableSingletonState) blockInfoWritableSingletonState);
        // setup initial running hashes
        runningHashesWritableSingletonState.put(new RunningHashes(STARTING_RUNNING_HASH_OBJ.hash(), null, null, null));
        when(blockManagerWritableStates.getSingleton(BlockRecordService.RUNNING_HASHES_STATE_KEY))
                .thenReturn((WritableSingletonState) runningHashesWritableSingletonState);
        // setup hedera state to get blockManagerWritableStates
        when(hederaState.createWritableStates(BlockRecordService.NAME)).thenReturn(blockManagerWritableStates);
        when(hederaState.createReadableStates(BlockRecordService.NAME)).thenReturn(blockManagerWritableStates);
        // setup workingStateAccessor to get hedera state
        when(workingStateAccessor.getHederaState()).thenReturn(hederaState);
    }

    @AfterEach
    public void shutdown() throws Exception {
        fs.close();
    }

    /**
     * Test general record stream production without calling all the block info getter methods as they can change the
     * way the code runs and are tested in other tests. The normal case is they are not called often.
     */
    @ParameterizedTest
    @ValueSource(strings = {"GENESIS", "NON_GENESIS"})
    public void testRecordStreamProduction(final String startMode) throws Exception {
        // setup initial block info,
        final long STARTING_BLOCK;
        if (startMode.equals("GENESIS")) {
            // start with genesis block
            STARTING_BLOCK = 1;
            blockManagerWritableStates
                    .getSingleton(BlockRecordService.BLOCK_INFO_STATE_KEY)
                    .put(new BlockInfo(STARTING_BLOCK - 1, new Timestamp(0, 0), STARTING_RUNNING_HASH_OBJ.hash()));
        } else {
            // pretend that previous block was 2 seconds before first test transaction
            STARTING_BLOCK = BLOCK_NUM;
            blockManagerWritableStates
                    .getSingleton(BlockRecordService.BLOCK_INFO_STATE_KEY)
                    .put(new BlockInfo(
                            STARTING_BLOCK - 1,
                            new Timestamp(
                                    TEST_BLOCKS
                                                    .get(0)
                                                    .get(0)
                                                    .record()
                                                    .consensusTimestamp()
                                                    .seconds()
                                            - 2,
                                    0),
                            STARTING_RUNNING_HASH_OBJ.hash()));
        }

        Bytes finalRunningHash;
        try (BlockRecordManager blockRecordManager =
                new BlockRecordManagerImpl(configProvider, nodeInfo, SIGNER, fs, workingStateAccessor)) {

            // write a blocks & record files
            int transactionCount = 0;
            final List<Bytes> endOfBlockHashes = new ArrayList<>();
            for (int i = 0; i < TEST_BLOCKS.size(); i++) {
                final var blockData = TEST_BLOCKS.get(i);
                final var block = STARTING_BLOCK + i;
                for (var record : blockData) {
                    blockRecordManager.startUserTransaction(
                            fromTimestamp(record.record().consensusTimestamp()), hederaState);
                    // check start hash if first transaction
                    if (transactionCount == 0) {
                        // check starting hash, we need to be using the correct starting hash for the tests to work
                        assertEquals(
                                STARTING_RUNNING_HASH_OBJ.hash().toHex(),
                                blockRecordManager.getRunningHash().toHex());
                    }
                    blockRecordManager.endUserTransaction(Stream.of(record), hederaState);
                    transactionCount++;
                    // pretend rounds happen every 20 transactions
                    if (transactionCount % 20 == 0) {
                        blockRecordManager.endRound(hederaState);
                    }
                }
                assertEquals(block - 1, blockRecordManager.lastBlockNo());
                // check block hashes
                if (endOfBlockHashes.size() > 1) {
                    assertEquals(
                            endOfBlockHashes.get(endOfBlockHashes.size() - 1).toHex(),
                            blockRecordManager.lastBlockHash().toHex());
                }
                endOfBlockHashes.add(blockRecordManager.getRunningHash());
            }
            // end the last round
            blockRecordManager.endRound(hederaState);
            // collect info for later validation
            finalRunningHash = blockRecordManager.getRunningHash();
            // try with resources will close the blockRecordManager and result in waiting for background threads to
            // finish and close any open files. No collect block record manager info to be validated
        }
        // check running hash
        assertEquals(ENDING_RUNNING_HASH.toHex(), finalRunningHash.toHex());
        // print out all files
        try (var pathStream = Files.walk(fs.getPath("/temp"))) {
            pathStream.filter(Files::isRegularFile).forEach(file -> {
                try {
                    if (file.getFileName().toString().endsWith("Z.rcd.gz")) {
                        try {
                            int count = RecordFileReaderV6.read(file)
                                    .recordStreamItems()
                                    .size();
                            System.out.println(
                                    file.toAbsolutePath() + " - (" + Files.size(file) + ")   count = " + count);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    } else {
                        System.out.println(file.toAbsolutePath() + " - (" + Files.size(file) + ")");
                    }
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
                STARTING_BLOCK);
    }

    @Test
    public void testBlockInfoMethods() throws Exception {
        // setup initial block info, pretend that previous block was 2 seconds before first test transaction
        blockManagerWritableStates
                .getSingleton(BlockRecordService.BLOCK_INFO_STATE_KEY)
                .put(new BlockInfo(
                        BLOCK_NUM - 1,
                        new Timestamp(
                                TEST_BLOCKS
                                                .get(0)
                                                .get(0)
                                                .record()
                                                .consensusTimestamp()
                                                .seconds()
                                        - 2,
                                0),
                        STARTING_RUNNING_HASH_OBJ.hash()));

        final Random random = new Random(82792874);
        Bytes finalRunningHash;
        try (BlockRecordManager blockRecordManager =
                new BlockRecordManagerImpl(configProvider, nodeInfo, SIGNER, fs, workingStateAccessor)) {
            // write a blocks & record files
            int transactionCount = 0;
            Bytes runningHash = STARTING_RUNNING_HASH_OBJ.hash();
            Bytes runningHashNMinus1 = null;
            Bytes runningHashNMinus2 = null;
            Bytes runningHashNMinus3;
            final List<Bytes> endOfBlockHashes = new ArrayList<>();
            endOfBlockHashes.add(runningHash);
            Instant lastBlockFirstTransactionTimestamp = null;
            for (int i = 0; i < TEST_BLOCKS.size(); i++) {
                final var blockData = TEST_BLOCKS.get(i);
                final var block = BLOCK_NUM + i;
                // write this blocks transactions
                int j = 0;
                while (j < blockData.size()) {
                    // write batch == simulated user transaction
                    final int batchSize = Math.min(random.nextInt(100) + 1, blockData.size() - j);
                    final var userTransactions = blockData.subList(j, j + batchSize);
                    for (var record : userTransactions) {
                        blockRecordManager.startUserTransaction(
                                fromTimestamp(record.record().consensusTimestamp()), hederaState);
                        blockRecordManager.endUserTransaction(Stream.of(record), hederaState);
                        transactionCount++;
                        // collect hashes
                        runningHashNMinus3 = runningHashNMinus2;
                        runningHashNMinus2 = runningHashNMinus1;
                        runningHashNMinus1 = runningHash;
                        runningHash = blockRecordManager.getRunningHash();
                        // check running hash N - 3
                        if (runningHashNMinus3 != null) {
                            // check running hash N - 3
                            assertEquals(
                                    runningHashNMinus3.toHex(),
                                    blockRecordManager.getNMinus3RunningHash().toHex());
                        } else {
                            // check nulls as well
                            assertNull(
                                    blockRecordManager.getNMinus3RunningHash(), "Running Hash N - 3 should be null.");
                        }
                    }
                    j += batchSize;
                    // pretend rounds happen every 20 or so transactions
                    if (transactionCount % 20 == 0) {
                        blockRecordManager.endRound(hederaState);
                    }
                }
                // VALIDATE BLOCK INFO METHODS
                // check last block number
                assertEquals(block - 1, blockRecordManager.lastBlockNo());
                // check last block first transaction timestamp
                if (lastBlockFirstTransactionTimestamp != null) {
                    assertEquals(lastBlockFirstTransactionTimestamp, blockRecordManager.firstConsTimeOfLastBlock());
                }
                lastBlockFirstTransactionTimestamp =
                        fromTimestamp(blockData.get(0).record().consensusTimestamp());
                // check block hashes we have in history
                if (endOfBlockHashes.size() > 0) {
                    // trim endOfBlockHashes to NUM_BLOCK_HASHES_TO_KEEP
                    while (endOfBlockHashes.size() > NUM_BLOCK_HASHES_TO_KEEP) {
                        endOfBlockHashes.remove(0);
                    }
                    assertEquals(
                            endOfBlockHashes.get(endOfBlockHashes.size() - 1).toHex(),
                            blockRecordManager.lastBlockHash().toHex());
                    assertEquals(
                            endOfBlockHashes.get(endOfBlockHashes.size() - 1).toHex(),
                            blockRecordManager.blockHashByBlockNumber(block - 1).toHex());
                    final int numBlockHashesToCheck = Math.min(NUM_BLOCK_HASHES_TO_KEEP, endOfBlockHashes.size());
                    for (int k = (numBlockHashesToCheck - 1); k >= 0; k--) {
                        var blockNumToCheck = block - (numBlockHashesToCheck - k);
                        assertEquals(
                                endOfBlockHashes.get(k).toHex(),
                                blockRecordManager
                                        .blockHashByBlockNumber(blockNumToCheck)
                                        .toHex());
                    }
                }
                endOfBlockHashes.add(blockRecordManager.getRunningHash());
            }
            // end the last round
            blockRecordManager.endRound(hederaState);
            // collect info for later validation
            finalRunningHash = blockRecordManager.getRunningHash();
            // try with resources will close the blockRecordManager and result in waiting for background threads to
            // finish and close any open files. No collect block record manager info to be validated
        }
        // check running hash
        assertEquals(ENDING_RUNNING_HASH.toHex(), finalRunningHash.toHex());
        // check record files
        final var recordStreamConfig = versionedConfiguration.getConfigData(BlockRecordStreamConfig.class);
        validateRecordStreamFiles(
                fs.getPath(recordStreamConfig.logDir()).resolve("record" + nodeInfo.accountMemo()),
                recordStreamConfig,
                USER_PUBLIC_KEY,
                TEST_BLOCKS,
                BLOCK_NUM);
    }

    private static Instant fromTimestamp(final Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.seconds(), timestamp.nanos());
    }

    /**
     * Block Info Writeable Singleton State Implementation
     */
    static class BlockInfoWritableSingletonState implements WritableSingletonState<BlockInfo> {
        private BlockInfo blockInfo;

        @Override
        public void put(@Nullable BlockInfo value) {
            this.blockInfo = value;
        }

        @Override
        public boolean isModified() {
            return true;
        }

        @NotNull
        @Override
        public String getStateKey() {
            return BlockRecordService.BLOCK_INFO_STATE_KEY;
        }

        @Nullable
        @Override
        public BlockInfo get() {
            return blockInfo;
        }

        @Override
        public boolean isRead() {
            return true;
        }
    }

    /**
     * Running Hashes Writeable Singleton State Implementation
     */
    static class RunningHashesWritableSingletonState implements WritableSingletonState<RunningHashes> {
        private RunningHashes runningHashes;

        @Override
        public void put(@Nullable RunningHashes value) {
            this.runningHashes = value;
        }

        @Override
        public boolean isModified() {
            return true;
        }

        @NotNull
        @Override
        public String getStateKey() {
            return BlockRecordService.BLOCK_INFO_STATE_KEY;
        }

        @Nullable
        @Override
        public RunningHashes get() {
            return runningHashes;
        }

        @Override
        public boolean isRead() {
            return true;
        }
    }
    ;
}
