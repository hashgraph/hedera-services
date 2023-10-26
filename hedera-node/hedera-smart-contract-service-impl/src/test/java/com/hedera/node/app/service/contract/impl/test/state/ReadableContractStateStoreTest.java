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

import static com.hedera.node.app.service.contract.impl.state.ContractSchema.BYTECODE_KEY;
import static com.hedera.node.app.service.contract.impl.state.ContractSchema.STORAGE_KEY;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.BYTECODE;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.CALLED_CONTRACT_ENTITY_NUMBER;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.node.app.service.contract.impl.state.ReadableContractStateStore;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReadableContractStateStoreTest {

    private static final SlotKey SLOT_KEY = new SlotKey(1L, Bytes.EMPTY);
    private static final SlotValue SLOT_VALUE = new SlotValue(Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY);

    @Mock
    private ReadableKVState<SlotKey, SlotValue> storage;

    @Mock
    private ReadableKVState<EntityNumber, Bytecode> bytecode;

    @Mock
    private ReadableStates states;

    private ReadableContractStateStore subject;

    @BeforeEach
    void setUp() {
        given(states.<SlotKey, SlotValue>get(STORAGE_KEY)).willReturn(storage);
        given(states.<EntityNumber, Bytecode>get(BYTECODE_KEY)).willReturn(bytecode);

        subject = new ReadableContractStateStore(states);
    }

    @Test
    void allMutationsAreUnsupported() {
        assertThrows(UnsupportedOperationException.class, () -> subject.removeSlot(SLOT_KEY));
        assertThrows(UnsupportedOperationException.class, () -> subject.putSlot(SLOT_KEY, SLOT_VALUE));
        assertThrows(
                UnsupportedOperationException.class,
                () -> subject.putBytecode(CALLED_CONTRACT_ENTITY_NUMBER, BYTECODE));
    }

    @Test
    void getsBytecodeAsExpected() {
        given(bytecode.get(CALLED_CONTRACT_ENTITY_NUMBER)).willReturn(BYTECODE);

        assertSame(BYTECODE, subject.getBytecode(CALLED_CONTRACT_ENTITY_NUMBER));
    }

    @Test
    void getsSlotAsExpected() {
        given(storage.get(SLOT_KEY)).willReturn(SLOT_VALUE);

        assertSame(SLOT_VALUE, subject.getSlotValue(SLOT_KEY));
    }

    @Test
    void getsOriginalSlotAsExpected() {
        given(storage.get(SLOT_KEY)).willReturn(SLOT_VALUE);

        assertSame(SLOT_VALUE, subject.getOriginalSlotValue(SLOT_KEY));
    }

    @Test
    void getsModifiedSlotKeysAsExpected() {
        assertSame(Collections.emptySet(), subject.getModifiedSlotKeys());
    }

    @Test
    void getsSizeAsExpected() {
        given(storage.size()).willReturn(1L);

        assertSame(1L, subject.getNumSlots());
    }

    @Test
    void getsNumBytecodesAsExpected() {
        given(bytecode.size()).willReturn(123L);

        assertSame(123L, subject.getNumBytecodes());
    }
}
