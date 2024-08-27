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

package com.hedera.node.app.blocks;

import static com.hedera.node.app.blocks.schemas.V0540BlockStreamSchema.BLOCK_STREAM_INFO_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.state.blockstream.BlockStreamInfo;
import com.hedera.node.app.blocks.schemas.V0540BlockStreamSchema;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
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
final class BlockStreamServiceTest {
    @Mock(strictness = LENIENT)
    private SchemaRegistry schemaRegistry;

    @Mock(strictness = LENIENT)
    private MigrationContext migrationContext;

    @Mock(strictness = LENIENT)
    private WritableSingletonState blockStreamState;

    @Mock(strictness = LENIENT)
    private WritableStates writableStates;

    public static final Configuration DEFAULT_CONFIG = HederaTestConfigBuilder.createConfig();

    @Test
    void testGetServiceName() {
        BlockStreamService blockRecordService = new BlockStreamService(DEFAULT_CONFIG);
        assertEquals(BlockStreamService.NAME, blockRecordService.getServiceName());
    }

    @Test
    void testRegisterSchemas() {
        when(schemaRegistry.register(any())).then(invocation -> {
            Object[] args = invocation.getArguments();
            assertEquals(1, args.length);
            Schema schema = (Schema) args[0];
            assertThat(schema).isInstanceOf(V0540BlockStreamSchema.class);
            Set<StateDefinition> states = schema.statesToCreate();
            assertEquals(1, states.size());
            assertTrue(states.contains(StateDefinition.singleton(BLOCK_STREAM_INFO_KEY, BlockStreamInfo.PROTOBUF)));

            when(migrationContext.newStates()).thenReturn(writableStates);
            when(migrationContext.previousVersion()).thenReturn(null);
            when(writableStates.getSingleton(BLOCK_STREAM_INFO_KEY)).thenReturn(blockStreamState);

            // FINISH:
            ArgumentCaptor<BlockStreamInfo> blockInfoCapture = ArgumentCaptor.forClass(BlockStreamInfo.class);
            verify(blockStreamState).put(blockInfoCapture.capture());

            schema.migrate(migrationContext);
            assertEquals(new BlockStreamInfo(0, null, Bytes.EMPTY, null, Bytes.EMPTY), blockInfoCapture.getValue());
            return null;
        });
        BlockStreamService blockStreamService = new BlockStreamService(DEFAULT_CONFIG);
        blockStreamService.registerSchemas(schemaRegistry);
    }
}
