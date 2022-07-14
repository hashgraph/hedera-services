package com.hedera.services.contracts.gascalculator;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static com.hedera.services.contracts.execution.CreateEvmTxProcessor.EXPIRY_ORACLE_CONTEXT_KEY;
import static com.hedera.services.contracts.execution.CreateEvmTxProcessor.SBH_CONTEXT_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.services.txns.contract.helpers.StorageExpiry;
import java.util.Deque;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StorageGasCalculatorTest {
    @Mock private MessageFrame frame;
    @Mock private MessageFrame lastStackFrame;
    @Mock private GasCalculator gasCalculator;
    @Mock private BlockValues blockValues;
    @Mock private Deque<MessageFrame> stack;
    @Mock private StorageExpiry.Oracle oracle;

    private StorageGasCalculator subject = new StorageGasCalculator();

    @Test
    void computesHappyPathAsExpected() {
        setupFrame();
        given(gasCalculator.memoryExpansionGasCost(frame, 10L, 20L)).willReturn(memExpansionCost);

        final var expected = (storageCostTinybars / gasPrice.toLong()) + memExpansionCost;
        final var actual = subject.creationGasCost(frame, gasCalculator);

        assertEquals(expected, actual);
    }

    @Test
    void neverChargesNegativeGas() {
        setupFrame();
        given(gasCalculator.memoryExpansionGasCost(frame, 10L, 20L)).willReturn(memExpansionCost);
        given(oracle.storageExpiryIn(frame)).willReturn(now - 1);

        final var expected = memExpansionCost;
        final var actual = subject.creationGasCost(frame, gasCalculator);

        assertEquals(expected, actual);
    }

    private void setupFrame() {
        given(blockValues.getTimestamp()).willReturn(now);
        given(oracle.storageExpiryIn(frame)).willReturn(prophesiedExpiry);

        given(frame.getGasPrice()).willReturn(gasPrice);
        given(frame.getStackItem(1)).willReturn(oneOffsetStackItem);
        given(frame.getStackItem(2)).willReturn(twoOffsetStackItem);
        given(frame.getBlockValues()).willReturn(blockValues);
        given(frame.getMessageFrameStack()).willReturn(stack);

        given(stack.getLast()).willReturn(lastStackFrame);
        given(lastStackFrame.getContextVariable(SBH_CONTEXT_KEY)).willReturn(sbh);
        given(lastStackFrame.getContextVariable(EXPIRY_ORACLE_CONTEXT_KEY)).willReturn(oracle);
    }

    private static final Wei gasPrice = Wei.of(42);
    private static final long memExpansionCost = 1000L;
    private static final long sbh = 12;
    private static final long now = 1_234_567L;
    private static final long lifetimeSecs = 7776000L;
    private static final long prophesiedExpiry = now + lifetimeSecs;
    private static final long storageCostTinybars = lifetimeSecs * sbh / 3600;
    private static final Bytes oneOffsetStackItem = Bytes.of(10);
    private static final Bytes twoOffsetStackItem = Bytes.of(20);
}
