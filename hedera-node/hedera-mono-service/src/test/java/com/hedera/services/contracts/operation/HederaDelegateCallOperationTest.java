/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.contracts.operation;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import static com.hedera.services.contracts.operation.CommonCallSetup.commonSetup;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import java.util.Optional;
import java.util.function.BiPredicate;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaDelegateCallOperationTest {

    @Mock private GasCalculator calc;
    @Mock private MessageFrame evmMsgFrame;
    @Mock private EVM evm;
    @Mock private WorldUpdater worldUpdater;
    @Mock private Account acc;
    @Mock private BiPredicate<Address, MessageFrame> addressValidator;

    private final long cost = 100L;

    private HederaDelegateCallOperation subject;

    @BeforeEach
    void setup() {
        subject = new HederaDelegateCallOperation(calc, addressValidator);
        commonSetup(evmMsgFrame, worldUpdater, acc);
    }

    @Test
    void haltWithInvalidAddr() {
        given(worldUpdater.get(any())).willReturn(null);
        given(
                        calc.callOperationGasCost(
                                any(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), any(),
                                any(), any()))
                .willReturn(cost);
        given(evmMsgFrame.getStackItem(0)).willReturn(Bytes.EMPTY);
        given(evmMsgFrame.getStackItem(1)).willReturn(Bytes.EMPTY);
        given(evmMsgFrame.getStackItem(2)).willReturn(Bytes.EMPTY);
        given(evmMsgFrame.getStackItem(3)).willReturn(Bytes.EMPTY);
        given(evmMsgFrame.getStackItem(4)).willReturn(Bytes.EMPTY);
        given(evmMsgFrame.getStackItem(5)).willReturn(Bytes.EMPTY);
        given(addressValidator.test(any(), any())).willReturn(false);

        var opRes = subject.execute(evmMsgFrame, evm);

        assertEquals(
                opRes.getHaltReason(),
                Optional.of(HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS));
        assertTrue(opRes.getGasCost().isPresent());
        assertEquals(opRes.getGasCost().getAsLong(), cost);
    }

    @Test
    void executesAsExpected() {
        given(
                        calc.callOperationGasCost(
                                any(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), any(),
                                any(), any()))
                .willReturn(cost);
        for (int i = 0; i < 10; i++) {
            lenient().when(evmMsgFrame.getStackItem(i)).thenReturn(Bytes.ofUnsignedInt(10));
        }
        given(evmMsgFrame.stackSize()).willReturn(20);
        given(evmMsgFrame.getRemainingGas()).willReturn(cost);
        given(evmMsgFrame.getMessageStackDepth()).willReturn(1025);
        given(worldUpdater.get(any())).willReturn(acc);
        given(acc.getBalance()).willReturn(Wei.of(100));
        given(calc.gasAvailableForChildCall(any(), anyLong(), anyBoolean())).willReturn(10L);
        given(addressValidator.test(any(), any())).willReturn(true);

        var opRes = subject.execute(evmMsgFrame, evm);
        assertEquals(Optional.empty(), opRes.getHaltReason());
        assertTrue(opRes.getGasCost().isPresent());
        assertEquals(opRes.getGasCost().getAsLong(), cost);
    }
}
