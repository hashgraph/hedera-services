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

package com.hedera.node.app.service.contract.impl.test.exec.operations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.contract.impl.exec.operations.DelegatingOperation;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DelegatingOperationTest {
    @Mock
    private EVM evm;

    @Mock
    private MessageFrame frame;

    @Mock
    private Operation delegate;

    private DelegatingOperation subject;

    @Test
    void delegatesEverything() {
        given(delegate.getOpcode()).willReturn(1);
        given(delegate.getName()).willReturn("name");
        given(delegate.getStackItemsConsumed()).willReturn(2);
        given(delegate.getStackItemsProduced()).willReturn(3);
        final var successResult = new Operation.OperationResult(1, null);
        given(delegate.execute(frame, evm)).willReturn(successResult);

        subject = new DelegatingOperation(delegate);

        assertEquals(1, subject.getOpcode());
        assertEquals("name", subject.getName());
        assertEquals(2, subject.getStackItemsConsumed());
        assertEquals(3, subject.getStackItemsProduced());
        assertEquals(successResult, subject.execute(frame, evm));
    }
}
