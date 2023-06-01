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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.contract.impl.state.DispatchingEvmFrameState;
import com.hedera.node.app.service.contract.impl.state.EvmFrameState;
import com.hedera.node.app.spi.meta.bni.Dispatch;
import com.hedera.node.app.spi.meta.bni.Scope;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableStates;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvmFrameStateTest {
    @Mock
    private Scope scope;

    @Mock
    private WritableStates writableStates;

    @Mock
    private Dispatch dispatch;

    @Mock
    private WritableKVState<SlotKey, SlotValue> storage;

    @Mock
    private WritableKVState<EntityNumber, Bytecode> bytecode;

    @Test
    void constructsDispatchingEvmFrameStateFromScope() {
        given(writableStates.<SlotKey, SlotValue>get(ContractServiceImpl.STORAGE_KEY))
                .willReturn(storage);
        given(writableStates.<EntityNumber, Bytecode>get(ContractServiceImpl.BYTECODE_KEY))
                .willReturn(bytecode);
        given(scope.writableContractState()).willReturn(writableStates);
        given(scope.dispatch()).willReturn(dispatch);

        final var frameState = EvmFrameState.from(scope);

        assertInstanceOf(DispatchingEvmFrameState.class, frameState);
    }
}
