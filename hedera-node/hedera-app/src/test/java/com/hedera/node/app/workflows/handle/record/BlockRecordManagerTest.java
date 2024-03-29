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

import static com.hedera.node.app.records.BlockRecordService.BLOCK_INFO_STATE_KEY;
import static com.hedera.node.app.records.BlockRecordService.EPOCH;
import static com.hedera.node.app.records.BlockRecordService.NAME;
import static com.hedera.node.app.records.BlockRecordService.RUNNING_HASHES_STATE_KEY;
import static com.hedera.node.app.records.RecordTestData.BLOCK_NUM;
import static com.hedera.node.app.records.RecordTestData.ENDING_RUNNING_HASH;
import static com.hedera.node.app.records.RecordTestData.SIGNER;
import static com.hedera.node.app.records.RecordTestData.STARTING_RUNNING_HASH_OBJ;
import static com.hedera.node.app.records.RecordTestData.TEST_BLOCKS;
import static com.hedera.node.app.records.RecordTestData.USER_PUBLIC_KEY;
import static com.hedera.node.app.records.impl.producers.formats.v6.RecordStreamV6Verifier.validateRecordStreamFiles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.node.app.fixtures.AppTestBase;
import com.hedera.node.app.records.impl.BlockRecordManagerImpl;
import com.hedera.node.app.records.impl.BlockRecordStreamProducer;
import com.hedera.node.app.records.impl.producers.BlockRecordFormat;
import com.hedera.node.app.records.impl.producers.BlockRecordWriterFactory;
import com.hedera.node.app.records.impl.producers.StreamFileProducerConcurrent;
import com.hedera.node.app.records.impl.producers.StreamFileProducerSingleThreaded;
import com.hedera.node.app.records.impl.producers.formats.BlockRecordWriterFactoryImpl;
import com.hedera.node.app.records.impl.producers.formats.v6.BlockRecordFormatV6;
import com.hedera.node.app.spi.fixtures.state.MapReadableStates;
import com.hedera.node.app.spi.state.ReadableSingletonStateBase;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.config.data.BlockRecordStreamConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.state.PlatformState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"DataFlowIssue"})
final class BlockRecordManagerTest extends AppTestBase {
    private static final Timestamp CONSENSUS_TIME =
            Timestamp.newBuilder().seconds(1_234_567L).nanos(13579).build();
    /** Make it small enough to trigger roll over code with the number of test blocks we have */
    private static final int NUM_BLOCK_HASHES_TO_KEEP = 4;

    private static final Timestamp FIRST_CONS_TIME_OF_LAST_BLOCK = new Timestamp(1682899224, 38693760);
    private static final Instant FORCED_BLOCK_SWITCH_TIME = Instant.ofEpochSecond(1682899224L, 38693760);

    /** Temporary in memory file system used for testing */
    private FileSystem fs;

    private App app;

    private BlockRecordFormat blockRecordFormat;
    private BlockRecordWriterFactory blockRecordWriterFactory;

    @BeforeEach
    void setUpEach() throws Exception {
        // create in memory temp dir
        fs = Jimfs.newFileSystem(Configuration.unix());
        final var tempDir = fs.getPath("/temp");
        Files.createDirectory(tempDir);

        // This test is for V6 files at this time.
        blockRecordFormat = BlockRecordFormatV6.INSTANCE;

        // Configure the application configuration and state we want to test with
        app = appBuilder()
                .withConfigValue("hedera.recordStream.enabled", true)
                .withConfigValue("hedera.recordStream.logDir", tempDir.toString())
                .withConfigValue("hedera.recordStream.sidecarDir", "sidecar")
                .withConfigValue("hedera.recordStream.recordFileVersion", 6)
                .withConfigValue("hedera.recordStream.signatureFileVersion", 6)
                .withConfigValue("hedera.recordStream.compressFilesOnCreation", true)
                .withConfigValue("hedera.recordStream.sidecarMaxSizeMb", 256)
                .withService(new BlockRecordService())
                .build();

        // Preload the specific state we want to test with
        app.stateMutator(BlockRecordService.NAME)
                .withSingletonState(
                        RUNNING_HASHES_STATE_KEY, new RunningHashes(STARTING_RUNNING_HASH_OBJ.hash(), null, null, null))
                .withSingletonState(
                        BLOCK_INFO_STATE_KEY,
                        new BlockInfo(-1, EPOCH, STARTING_RUNNING_HASH_OBJ.hash(), null, false, EPOCH))
                .commit();

        blockRecordWriterFactory = new BlockRecordWriterFactoryImpl(
                app.configProvider(), app.networkInfo().selfNodeInfo(), SIGNER, fs);
    }

    @AfterEach
    void shutdown() throws Exception {
        fs.close();
    }

    /**
     * Test general record stream production without calling all the block info getter methods as they can change the
     * way the code runs and are tested in other tests. The normal case is they are not called often.
     */
    @ParameterizedTest
    @CsvSource({"GENESIS, false", "NON_GENESIS, false", "GENESIS, true", "NON_GENESIS, true"})
    void testRecordStreamProduction(final String startMode, final boolean concurrent) throws Exception {
        // setup initial block info
        final long STARTING_BLOCK;
        if (startMode.equals("GENESIS")) {
            STARTING_BLOCK = 0;
        } else {
            // pretend that previous block was 2 seconds before first test transaction
            STARTING_BLOCK = BLOCK_NUM;
            app.stateMutator(NAME)
                    .withSingletonState(
                            BLOCK_INFO_STATE_KEY,
                            new BlockInfo(
                                    STARTING_BLOCK - 1,
                                    new Timestamp(
                                            TEST_BLOCKS
                                                            .get(0)
                                                            .get(0)
                                                            .transactionRecord()
                                                            .consensusTimestamp()
                                                            .seconds()
                                                    - 2,
                                            0),
                                    STARTING_RUNNING_HASH_OBJ.hash(),
                                    CONSENSUS_TIME,
                                    true,
                                    FIRST_CONS_TIME_OF_LAST_BLOCK))
                    .commit();
        }

        final var hederaState = app.workingStateAccessor().getHederaState();
        final var producer = concurrent
                ? new StreamFileProducerConcurrent(
                        app.networkInfo().selfNodeInfo(),
                        blockRecordFormat,
                        blockRecordWriterFactory,
                        ForkJoinPool.commonPool())
                : new StreamFileProducerSingleThreaded(
                        app.networkInfo().selfNodeInfo(), blockRecordFormat, blockRecordWriterFactory);
        Bytes finalRunningHash;
        try (final var blockRecordManager = new BlockRecordManagerImpl(
                app.configProvider(), app.workingStateAccessor().getHederaState(), producer)) {
            if (!startMode.equals("GENESIS")) {
                blockRecordManager.switchBlocksAt(FORCED_BLOCK_SWITCH_TIME);
            }
            assertThat(blockRecordManager.currentBlockTimestamp()).isNotNull();
            assertThat(blockRecordManager.blockNo()).isEqualTo(blockRecordManager.lastBlockNo() + 1);
            // write a blocks & record files
            int transactionCount = 0;
            final List<Bytes> endOfBlockHashes = new ArrayList<>();
            for (int i = 0; i < TEST_BLOCKS.size(); i++) {
                final var blockData = TEST_BLOCKS.get(i);
                final var block = STARTING_BLOCK + i;
                for (var record : blockData) {
                    blockRecordManager.startUserTransaction(
                            fromTimestamp(record.transactionRecord().consensusTimestamp()),
                            hederaState,
                            mock(PlatformState.class));
                    // check start hash if first transaction
                    if (transactionCount == 0) {
                        // check starting hash, we need to be using the correct starting hash for the tests to work
                        assertThat(blockRecordManager.getRunningHash().toHex())
                                .isEqualTo(STARTING_RUNNING_HASH_OBJ.hash().toHex());
                    }
                    blockRecordManager.endUserTransaction(Stream.of(record), hederaState);
                    transactionCount++;
                    // pretend rounds happen every 20 transactions
                    if (transactionCount % 20 == 0) {
                        blockRecordManager.endRound(hederaState);
                    }
                }
                assertThat(block - 1).isEqualTo(blockRecordManager.lastBlockNo());
                // check block hashes
                if (endOfBlockHashes.size() > 1) {
                    assertThat(endOfBlockHashes.get(endOfBlockHashes.size() - 1).toHex())
                            .isEqualTo(blockRecordManager.lastBlockHash().toHex());
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
        assertThat(ENDING_RUNNING_HASH.toHex()).isEqualTo(finalRunningHash.toHex());
        // check record files
        final var recordStreamConfig =
                app.configProvider().getConfiguration().getConfigData(BlockRecordStreamConfig.class);
        validateRecordStreamFiles(
                fs.getPath(recordStreamConfig.logDir())
                        .resolve("record" + app.networkInfo().selfNodeInfo().memo()),
                recordStreamConfig,
                USER_PUBLIC_KEY,
                TEST_BLOCKS,
                STARTING_BLOCK);
    }

    @Test
    void testBlockInfoMethods() throws Exception {
        // setup initial block info, pretend that previous block was 2 seconds before first test transaction
        app.stateMutator(NAME)
                .withSingletonState(
                        BLOCK_INFO_STATE_KEY,
                        new BlockInfo(
                                BLOCK_NUM - 1,
                                new Timestamp(
                                        TEST_BLOCKS
                                                        .get(0)
                                                        .get(0)
                                                        .transactionRecord()
                                                        .consensusTimestamp()
                                                        .seconds()
                                                - 2,
                                        0),
                                STARTING_RUNNING_HASH_OBJ.hash(),
                                CONSENSUS_TIME,
                                true,
                                FIRST_CONS_TIME_OF_LAST_BLOCK))
                .commit();

        final Random random = new Random(82792874);
        final var hederaState = app.workingStateAccessor().getHederaState();
        final var producer = new StreamFileProducerSingleThreaded(
                app.networkInfo().selfNodeInfo(), blockRecordFormat, blockRecordWriterFactory);
        Bytes finalRunningHash;
        try (final var blockRecordManager = new BlockRecordManagerImpl(
                app.configProvider(), app.workingStateAccessor().getHederaState(), producer)) {
            blockRecordManager.switchBlocksAt(FORCED_BLOCK_SWITCH_TIME);
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
                                fromTimestamp(record.transactionRecord().consensusTimestamp()),
                                hederaState,
                                mock(PlatformState.class));
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
                            assertThat(runningHashNMinus3.toHex())
                                    .isEqualTo(blockRecordManager
                                            .getNMinus3RunningHash()
                                            .toHex());
                        } else {
                            // check empty as well
                            assertThat(blockRecordManager.getNMinus3RunningHash())
                                    .isEqualTo(Bytes.EMPTY);
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
                assertThat(block - 1).isEqualTo(blockRecordManager.lastBlockNo());
                // check last block first transaction timestamp
                if (lastBlockFirstTransactionTimestamp != null) {
                    assertThat(lastBlockFirstTransactionTimestamp)
                            .isEqualTo(blockRecordManager.firstConsTimeOfLastBlock());
                }
                lastBlockFirstTransactionTimestamp =
                        fromTimestamp(blockData.get(0).transactionRecord().consensusTimestamp());
                // check block hashes we have in history
                if (endOfBlockHashes.size() > 0) {
                    // trim endOfBlockHashes to NUM_BLOCK_HASHES_TO_KEEP
                    while (endOfBlockHashes.size() > NUM_BLOCK_HASHES_TO_KEEP) {
                        endOfBlockHashes.remove(0);
                    }
                    assertThat(endOfBlockHashes.get(endOfBlockHashes.size() - 1).toHex())
                            .isEqualTo(blockRecordManager.lastBlockHash().toHex());
                    assertThat(endOfBlockHashes.get(endOfBlockHashes.size() - 1).toHex())
                            .isEqualTo(blockRecordManager
                                    .blockHashByBlockNumber(block - 1)
                                    .toHex());
                    final int numBlockHashesToCheck = Math.min(NUM_BLOCK_HASHES_TO_KEEP, endOfBlockHashes.size());
                    for (int k = (numBlockHashesToCheck - 1); k >= 0; k--) {
                        var blockNumToCheck = block - (numBlockHashesToCheck - k);
                        assertThat(endOfBlockHashes.get(k).toHex())
                                .isEqualTo(blockRecordManager
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
        assertThat(ENDING_RUNNING_HASH.toHex()).isEqualTo(finalRunningHash.toHex());
        // check record files
        final var recordStreamConfig =
                app.configProvider().getConfiguration().getConfigData(BlockRecordStreamConfig.class);
        validateRecordStreamFiles(
                fs.getPath(recordStreamConfig.logDir())
                        .resolve("record" + app.networkInfo().selfNodeInfo().memo()),
                recordStreamConfig,
                USER_PUBLIC_KEY,
                TEST_BLOCKS,
                BLOCK_NUM);
    }

    @Test
    void isDefaultConsTimeForNullParam() {
        @SuppressWarnings("ConstantValue")
        final var result = BlockRecordManagerImpl.isDefaultConsTimeOfLastHandledTxn(null);
        //noinspection ConstantValue
        Assertions.assertThat(result).isTrue();
    }

    @Test
    void isDefaultConsTimeForNullConsensusTimeOfLastHandledTxn() {
        final var result = BlockRecordManagerImpl.isDefaultConsTimeOfLastHandledTxn(
                new BlockInfo(0, CONSENSUS_TIME, Bytes.EMPTY, null, false, CONSENSUS_TIME));
        Assertions.assertThat(result).isTrue();
    }

    @Test
    void isDefaultConsTimeForTimestampAfterEpoch() {
        final var timestampAfterEpoch = Timestamp.newBuilder()
                .seconds(EPOCH.seconds())
                .nanos(EPOCH.nanos() + 1)
                .build();
        final var result = BlockRecordManagerImpl.isDefaultConsTimeOfLastHandledTxn(
                new BlockInfo(0, CONSENSUS_TIME, Bytes.EMPTY, timestampAfterEpoch, false, CONSENSUS_TIME));
        Assertions.assertThat(result).isFalse();
    }

    @Test
    void isDefaultConsTimeForTimestampAtEpoch() {
        final var result = BlockRecordManagerImpl.isDefaultConsTimeOfLastHandledTxn(
                new BlockInfo(0, CONSENSUS_TIME, Bytes.EMPTY, EPOCH, false, CONSENSUS_TIME));
        Assertions.assertThat(result).isTrue();
    }

    @Test
    void isDefaultConsTimeForTimestampBeforeEpoch() {
        final var timestampBeforeEpoch = Timestamp.newBuilder()
                .seconds(EPOCH.seconds())
                .nanos(EPOCH.nanos() - 1)
                .build();
        final var result = BlockRecordManagerImpl.isDefaultConsTimeOfLastHandledTxn(
                new BlockInfo(0, CONSENSUS_TIME, Bytes.EMPTY, timestampBeforeEpoch, false, CONSENSUS_TIME));
        Assertions.assertThat(result).isTrue();
    }

    @Test
    void consTimeOfLastHandledTxnIsSet() {
        final var blockInfo = new BlockInfo(0, EPOCH, Bytes.EMPTY, CONSENSUS_TIME, false, EPOCH);
        final var state = simpleBlockInfoState(blockInfo);
        final var subject =
                new BlockRecordManagerImpl(app.configProvider(), state, mock(BlockRecordStreamProducer.class));

        final var result = subject.consTimeOfLastHandledTxn();
        Assertions.assertThat(result).isEqualTo(fromTimestamp(CONSENSUS_TIME));
    }

    @Test
    void consTimeOfLastHandledTxnIsNotSet() {
        final var blockInfo = new BlockInfo(0, EPOCH, Bytes.EMPTY, null, false, EPOCH);
        final var state = simpleBlockInfoState(blockInfo);
        final var subject =
                new BlockRecordManagerImpl(app.configProvider(), state, mock(BlockRecordStreamProducer.class));

        final var result = subject.consTimeOfLastHandledTxn();
        Assertions.assertThat(result).isEqualTo(fromTimestamp(EPOCH));
    }

    private static HederaState simpleBlockInfoState(final BlockInfo blockInfo) {
        return new HederaState() {
            @NonNull
            @Override
            public ReadableStates getReadableStates(@NonNull final String serviceName) {
                return new MapReadableStates(Map.of(
                        BlockRecordService.BLOCK_INFO_STATE_KEY,
                        new ReadableSingletonStateBase<>(BlockRecordService.BLOCK_INFO_STATE_KEY, () -> blockInfo),
                        RUNNING_HASHES_STATE_KEY,
                        new ReadableSingletonStateBase<>(RUNNING_HASHES_STATE_KEY, () -> RunningHashes.DEFAULT)));
            }

            @NonNull
            @Override
            public WritableStates getWritableStates(@NonNull String serviceName) {
                throw new UnsupportedOperationException("Shouldn't be needed for this test");
            }
        };
    }

    private static Instant fromTimestamp(final Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.seconds(), timestamp.nanos());
    }
}
