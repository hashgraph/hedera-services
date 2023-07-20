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

package com.hedera.node.app.service.contract.impl.test.state;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleExtFrameScope;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleExtWorldScope;
import com.hedera.node.app.service.contract.impl.state.ContractStateStore;
import com.hedera.node.app.service.contract.impl.state.InScopeFrameStateFactory;
import com.hedera.node.app.service.contract.impl.state.ScopedEvmFrameState;
import com.hedera.node.app.spi.state.WritableKVState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class InScopeFrameStateFactoryTest {
    @Mock
    private HandleExtWorldScope scope;

    @Mock
    private HandleExtFrameScope extFrameScope;

    @Mock
    private ContractStateStore writableContractsStore;

    @Mock
    private WritableKVState<SlotKey, SlotValue> storage;

    @Mock
    private WritableKVState<EntityNumber, Bytecode> bytecode;

    private InScopeFrameStateFactory subject;

    @BeforeEach
    void setUp() {
        subject = new InScopeFrameStateFactory(scope, extFrameScope);
    }

    @Test
    void createsScopedEvmFrameStates() {
        given(scope.getStore()).willReturn(writableContractsStore);
        given(writableContractsStore.storage()).willReturn(storage);
        given(writableContractsStore.bytecode()).willReturn(bytecode);

        final var nextFrame = subject.get();

        assertInstanceOf(ScopedEvmFrameState.class, nextFrame);
    }
}
