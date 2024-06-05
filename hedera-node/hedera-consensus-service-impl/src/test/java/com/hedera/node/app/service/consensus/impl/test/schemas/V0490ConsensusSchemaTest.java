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

package com.hedera.node.app.service.consensus.impl.test.schemas;

import static com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl.TOPICS_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.consensus.impl.schemas.V0490ConsensusSchema;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.spi.fixtures.util.LogCaptor;
import com.hedera.node.app.spi.fixtures.util.LogCaptureExtension;
import com.hedera.node.app.spi.fixtures.util.LoggingSubject;
import com.hedera.node.app.spi.fixtures.util.LoggingTarget;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.state.spi.MigrationContext;
import com.swirlds.state.spi.StateDefinition;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableStates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
public class V0490ConsensusSchemaTest {

    @LoggingTarget
    private LogCaptor logCaptor;

    @Mock
    private MigrationContext migrationContext;

    @Mock
    private WritableStates writableStates;

    @Mock
    private MerkleMap<EntityNum, MerkleTopic> fs;

    @Mock
    private WritableKVState ctx;

    @LoggingSubject
    private V0490ConsensusSchema subject;

    @BeforeEach
    void setUp() {
        subject = new V0490ConsensusSchema();
    }

    @Test
    void registersExpectedSchema() {
        final var statesToCreate = subject.statesToCreate();
        assertThat(statesToCreate.size()).isEqualTo(1);
        final var iter =
                statesToCreate.stream().map(StateDefinition::stateKey).sorted().iterator();
        assertEquals(TOPICS_KEY, iter.next());
    }

    @Test
    void checkFSSetupCorrectly() {
        assertThatCode(() -> subject.setFromState(fs)).doesNotThrowAnyException();
    }

    @Test
    void setFSasExpectedAndHappyPathMigration() {
        subject.setFromState(fs);

        given(migrationContext.newStates()).willReturn(writableStates);
        given(writableStates.get(TOPICS_KEY)).willReturn(ctx);

        assertThatCode(() -> subject.migrate(migrationContext)).doesNotThrowAnyException();
        assertThat(logCaptor.infoLogs()).contains("BBM: running consensus migration...");
        assertThat(logCaptor.infoLogs()).contains("BBM: finished consensus service migration");
    }

    @Test
    void setEmptyFSasExpectedAndHappyPathMigration() {

        assertThatCode(() -> subject.migrate(migrationContext)).doesNotThrowAnyException();
        assertThat(logCaptor.warnLogs()).contains("BBM: no consensus 'from' state found");
    }
}
