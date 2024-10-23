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

package com.hedera.node.app.workflows.handle.record;

import static com.hedera.node.app.records.BlockRecordService.EPOCH;
import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.BLOCK_INFO_STATE_KEY;
import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.RUNNING_HASHES_STATE_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.records.schemas.V0490BlockRecordSchema;
import com.hedera.node.app.records.schemas.V0560BlockRecordSchema;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.MigrationContext;
import com.swirlds.state.spi.Schema;
import com.swirlds.state.spi.SchemaRegistry;
import com.swirlds.state.spi.StateDefinition;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings({"rawtypes", "unchecked"})
@ExtendWith(MockitoExtension.class)
final class BlockRecordServiceTest {
    private static final Bytes GENESIS_HASH = Bytes.wrap(new byte[48]);
    private @Mock SchemaRegistry schemaRegistry;
    private @Mock MigrationContext migrationContext;
    private @Mock WritableSingletonState runningHashesState;
    private @Mock WritableSingletonState blockInfoState;
    private @Mock WritableStates writableStates;

    @Test
    void testGetServiceName() {
        BlockRecordService blockRecordService = new BlockRecordService();
        assertEquals(BlockRecordService.NAME, blockRecordService.getServiceName());
    }

    @Test
    void testRegisterSchemas() {
        when(schemaRegistry.register(any())).then(invocation -> {
            Object[] args = invocation.getArguments();
            assertEquals(1, args.length);
            Schema schema = (Schema) args[0];
            if (schema instanceof V0490BlockRecordSchema) {
                Set<StateDefinition> states = schema.statesToCreate();
                assertEquals(2, states.size());
                assertTrue(states.contains(StateDefinition.singleton("RUNNING_HASHES", RunningHashes.PROTOBUF)));
                assertTrue(states.contains(StateDefinition.singleton("BLOCKS", BlockInfo.PROTOBUF)));

                when(migrationContext.newStates()).thenReturn(writableStates);
                when(migrationContext.previousVersion()).thenReturn(null);
                when(writableStates.getSingleton(BLOCK_INFO_STATE_KEY)).thenReturn(blockInfoState);
                when(writableStates.getSingleton(RUNNING_HASHES_STATE_KEY)).thenReturn(runningHashesState);

                ArgumentCaptor<RunningHashes> runningHashesCapture = ArgumentCaptor.forClass(RunningHashes.class);
                doNothing().when(runningHashesState).put(runningHashesCapture.capture());
                ArgumentCaptor<BlockInfo> blockInfoCapture = ArgumentCaptor.forClass(BlockInfo.class);
                doNothing().when(blockInfoState).put(blockInfoCapture.capture());

                schema.migrate(migrationContext);
                assertEquals(
                        new RunningHashes(GENESIS_HASH, Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY),
                        runningHashesCapture.getValue());
                assertEquals(new BlockInfo(-1, EPOCH, Bytes.EMPTY, EPOCH, true, EPOCH), blockInfoCapture.getValue());
            } else {
                assertThat(schema).isInstanceOf(V0560BlockRecordSchema.class);
            }
            return null;
        });
        BlockRecordService blockRecordService = new BlockRecordService();
        blockRecordService.registerSchemas(schemaRegistry);
    }
}
