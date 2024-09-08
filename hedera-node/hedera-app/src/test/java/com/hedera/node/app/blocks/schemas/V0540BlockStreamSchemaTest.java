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

package com.hedera.node.app.blocks.schemas;

import static com.hedera.node.app.blocks.schemas.V0540BlockStreamSchema.BLOCK_STREAM_INFO_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.state.blockstream.BlockStreamInfo;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.spi.MigrationContext;
import com.swirlds.state.spi.StateDefinition;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class V0540BlockStreamSchemaTest {
    @Mock(strictness = LENIENT)
    private MigrationContext mockCtx;

    @Mock(strictness = LENIENT)
    private WritableSingletonState<Object> mockBlockStreamInfo;

    @Mock(strictness = LENIENT)
    private WritableStates mockWritableStates;

    @Mock
    private Consumer<Bytes> migratedBlockHashConsumer;

    public static final Configuration DEFAULT_CONFIG = HederaTestConfigBuilder.createConfig();

    private V0540BlockStreamSchema schema;

    @BeforeEach
    void setUp() {
        schema = new V0540BlockStreamSchema(migratedBlockHashConsumer);
        when(mockCtx.newStates()).thenReturn(mockWritableStates);
        when(mockWritableStates.getSingleton(BLOCK_STREAM_INFO_KEY)).thenReturn(mockBlockStreamInfo);
    }

    @Test
    void testVersion() {
        assertEquals(0, schema.getVersion().major());
        assertEquals(54, schema.getVersion().minor());
        assertEquals(0, schema.getVersion().patch());
    }

    @Test
    void testStatesToCreate() {
        Set<StateDefinition> statesToCreate = schema.statesToCreate(DEFAULT_CONFIG);
        assertNotNull(statesToCreate);
        assertEquals(1, statesToCreate.size());
        assertTrue(statesToCreate.stream().anyMatch(state -> state.stateKey().equals(BLOCK_STREAM_INFO_KEY)));
    }

    @Test
    void testMigration() {
        when(mockCtx.previousVersion()).thenReturn(null);

        schema.migrate(mockCtx);

        ArgumentCaptor<BlockStreamInfo> captor = ArgumentCaptor.forClass(BlockStreamInfo.class);
        verify(mockBlockStreamInfo).put(captor.capture());

        BlockStreamInfo blockInfoCapture = captor.getValue();
        assertEquals(BlockStreamInfo.DEFAULT, blockInfoCapture);
    }
}
