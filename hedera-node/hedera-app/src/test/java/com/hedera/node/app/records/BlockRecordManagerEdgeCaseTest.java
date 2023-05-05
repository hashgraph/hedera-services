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

import static com.hedera.node.app.records.files.RecordTestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.spi.state.WritableSingletonState;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.state.WorkingStateAccessor;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfiguration;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings({"Convert2MethodRef", "unchecked"})
@ExtendWith(MockitoExtension.class)
public class BlockRecordManagerEdgeCaseTest {

    private @Mock ConfigProvider configProvider;
    private @Mock NodeInfo nodeInfo;
    private @Mock VersionedConfiguration versionedConfiguration;

    /** Temporary in memory file system used for testing */
    private FileSystem fs;

    @Test
    public void testEdgeCases() throws Exception {
        // create in memory temp dir
        fs = Jimfs.newFileSystem(Configuration.unix());
        Path tempDir = fs.getPath("/temp");
        Files.createDirectory(tempDir);
        // setup config
        final BlockRecordStreamConfig recordStreamConfig = new BlockRecordStreamConfig(
                true, tempDir.toString(), "sidecar", 2, 5000, true, 256, 6, 6, true, true, 4);
        // setup mocks
        when(versionedConfiguration.getConfigData(BlockRecordStreamConfig.class))
                .thenReturn(recordStreamConfig);
        when(configProvider.getConfiguration()).thenReturn(versionedConfiguration);
        when(nodeInfo.accountMemo()).thenReturn("test-node");
        when(nodeInfo.hapiVersion()).thenReturn(new SemanticVersion(1, 2, 3, "", ""));

        final WritableStates blockManagerWritableStates = mock(WritableStates.class);
        final HederaState hederaState = mock(HederaState.class);
        final WorkingStateAccessor workingStateAccessor = mock(WorkingStateAccessor.class);
        // create a BlockInfo state
        WritableSingletonState<BlockInfo> blockInfoWritableSingletonState =
                new BlockRecordManagerTest.BlockInfoWritableSingletonState();
        // create a RunningHashes state
        WritableSingletonState<RunningHashes> runningHashesWritableSingletonState =
                new BlockRecordManagerTest.RunningHashesWritableSingletonState();
        // setup initial block info, pretend that previous block was 2 seconds before first test transaction
        when(blockManagerWritableStates.getSingleton(BlockRecordService.BLOCK_INFO_STATE_KEY))
                .thenReturn((WritableSingletonState) blockInfoWritableSingletonState);
        // setup initial running hashes
        //        runningHashesWritableSingletonState.put(new RunningHashes(STARTING_RUNNING_HASH_OBJ.hash(), null,
        // null, null));
        when(blockManagerWritableStates.getSingleton(BlockRecordService.RUNNING_HASHES_STATE_KEY))
                .thenReturn((WritableSingletonState) runningHashesWritableSingletonState);
        // setup hedera state to get blockManagerWritableStates
        when(hederaState.createWritableStates(BlockRecordService.NAME)).thenReturn(blockManagerWritableStates);
        when(hederaState.createReadableStates(BlockRecordService.NAME)).thenReturn(blockManagerWritableStates);

        try (BlockRecordManager blockRecordManager =
                new BlockRecordManagerImpl(configProvider, nodeInfo, SIGNER, fs, workingStateAccessor)) {

            // before we set hedera state check exception is thrown when it is null
            assertThrowsExactly(RuntimeException.class, () -> blockRecordManager.firstConsTimeOfLastBlock());
            assertThrowsExactly(RuntimeException.class, () -> blockRecordManager.lastBlockHash());
            assertThrowsExactly(RuntimeException.class, () -> blockRecordManager.lastBlockNo());
            assertThrowsExactly(RuntimeException.class, () -> blockRecordManager.blockHashByBlockNumber(0));

            // setup workingStateAccessor to get hedera state
            when(workingStateAccessor.getHederaState()).thenReturn(hederaState);

            // check block info is null cases
            assertNull(blockRecordManager.lastBlockHash());
            assertNull(blockRecordManager.firstConsTimeOfLastBlock());
            assertEquals(0, blockRecordManager.lastBlockNo());
            assertThrowsExactly(RuntimeException.class, () -> blockRecordManager.blockHashByBlockNumber(0));

            // now add block info to handle edge cases
            blockInfoWritableSingletonState.put(new BlockInfo(1, null, STARTING_RUNNING_HASH_OBJ.hash()));
            assertNull(blockRecordManager.blockHashByBlockNumber(0));
            assertNull(blockRecordManager.blockHashByBlockNumber(2));
            assertNull(blockRecordManager.blockHashByBlockNumber(-1));

            blockRecordManager.endRound(hederaState);
            assertNull(runningHashesWritableSingletonState.get().runningHash());
            assertNull(runningHashesWritableSingletonState.get().nMinus1RunningHash());
            assertNull(runningHashesWritableSingletonState.get().nMinus2RunningHash());
            assertNull(runningHashesWritableSingletonState.get().nMinus3RunningHash());
        }
    }
}
