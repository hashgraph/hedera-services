/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.blocks.schemas.V0600BlockStreamSchema.BLOCK_STREAM_INFO_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.blockstream.BlockStreamInfo;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class V0600BlockStreamSchemaTest {
    @Mock
    private MigrationContext migrationContext;

    @Mock
    private WritableStates writableStates;

    @Mock
    private WritableSingletonState<BlockStreamInfo> state;

    private V0600BlockStreamSchema subject;

    @BeforeEach
    void setUp() {
        subject = new V0600BlockStreamSchema();
    }

    @Test
    void versionIsV0600() {
        assertEquals(new SemanticVersion(0, 60, 0, "", ""), subject.getVersion());
    }

    @Test
    void doesNotModifyStateAtGenesis() {
        given(migrationContext.isGenesis()).willReturn(true);

        subject.migrate(migrationContext);

        verifyNoInteractions(writableStates);
    }

    @Test
    void setsGenesisWorkDoneToTrueForNonGenesisMigration() {
        given(migrationContext.isGenesis()).willReturn(false);
        given(migrationContext.newStates()).willReturn(writableStates);
        given(writableStates.<BlockStreamInfo>getSingleton(BLOCK_STREAM_INFO_KEY))
                .willReturn(state);
        given(state.get()).willReturn(BlockStreamInfo.DEFAULT);

        subject.migrate(migrationContext);

        verify(state)
                .put(BlockStreamInfo.DEFAULT.copyBuilder().genesisWorkDone(true).build());
    }
}
