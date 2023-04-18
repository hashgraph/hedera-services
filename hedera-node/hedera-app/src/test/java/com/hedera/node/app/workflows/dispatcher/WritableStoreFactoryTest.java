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

package com.hedera.node.app.workflows.dispatcher;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.state.WorkingStateAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WritableStoreFactoryTest {
    private WritableStoreFactory subject;

    @Mock
    private HederaState state;

    private WorkingStateAccessor workingStateAccessor;

    @Mock
    WritableStates writableStates;

    @BeforeEach
    void setUp() {
        workingStateAccessor = new WorkingStateAccessor();
        workingStateAccessor.setHederaState(state);
        subject = new WritableStoreFactory(workingStateAccessor);
    }

    @Test
    void emptyConstructor() {
        assertDoesNotThrow(() -> new WritableStoreFactory(workingStateAccessor));
    }

    @Test
    void createsWritableStore() {
        given(state.createWritableStates(ConsensusService.NAME)).willReturn(writableStates);
        subject = new WritableStoreFactory(workingStateAccessor);
        assertNotNull(subject.createTopicStore());
    }

    @Test
    void returnsTopicStore() {
        workingStateAccessor.setHederaState(state);
        given(state.createWritableStates("ConsensusService")).willReturn(writableStates);
        final var store = subject.createTopicStore();
        assertNotNull(store);
    }

    @Test
    void returnsTokenStore() {
        workingStateAccessor.setHederaState(state);
        given(state.createWritableStates("TokenService")).willReturn(writableStates);
        final var store = subject.createTokenStore();
        assertNotNull(store);
    }

    @Test
    void returnsTokenRelStore() {
        workingStateAccessor.setHederaState(state);
        given(state.createWritableStates("TokenRelations")).willReturn(writableStates);
        final var store = subject.createTokenRelStore();
        assertNotNull(store);
    }
}
