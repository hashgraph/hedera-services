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

package com.hedera.services.bdd.utils.test.translators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.hedera.hapi.block.stream.output.CallContractOutput;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.hapi.streams.TransactionSidecarRecord;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.services.bdd.junit.support.translators.SingleTransactionBlockItems;
import com.hedera.services.bdd.junit.support.translators.impl.ContractCallTranslator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractCallTranslatorTest {

    @Mock
    private SingleTransactionBlockItems mockTransactionBlockItems;

    @Mock
    private TransactionOutput mockTransactionOutput;

    @Mock
    private Transaction mockTransaction;

    @Mock
    private StateChanges mockStateChanges;

    @Mock
    private CallContractOutput mockContractCallOutput;

    @Mock
    private ContractFunctionResult mockContractFunctionResult;

    private ContractCallTranslator translator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        translator = new ContractCallTranslator();
    }

    @Test
    void testTranslateContractCall() {
        final List<TransactionSidecarRecord> sidecars = List.of(TransactionSidecarRecord.DEFAULT);
        // When
        when(mockTransactionBlockItems.txn()).thenReturn(mockTransaction);
        when(mockTransactionBlockItems.output()).thenReturn(mockTransactionOutput);
        when(mockTransactionBlockItems.output().hasContractCall()).thenReturn(true);
        when(mockTransactionBlockItems.output().contractCall()).thenReturn(mockContractCallOutput);
        when(mockContractCallOutput.contractCallResult()).thenReturn(mockContractFunctionResult);
        when(mockContractCallOutput.sidecars()).thenReturn(sidecars);

        SingleTransactionRecord result = translator.translate(mockTransactionBlockItems, mockStateChanges);

        // Then
        TransactionRecord expectedRecord = TransactionRecord.newBuilder()
                .contractCallResult(mockContractFunctionResult)
                .build();

        assertEquals(mockTransaction, result.transaction());
        assertEquals(expectedRecord, result.transactionRecord());
        assertEquals(result.transactionSidecarRecords(), sidecars);
        assertEquals(result.transactionOutputs(), new SingleTransactionRecord.TransactionOutputs(null));
    }

    @Test
    void testTranslateContractCallDefaultSidecars() {
        // When
        when(mockTransactionBlockItems.txn()).thenReturn(mockTransaction);
        when(mockTransactionBlockItems.output()).thenReturn(mockTransactionOutput);
        when(mockTransactionBlockItems.output().hasContractCall()).thenReturn(true);
        when(mockTransactionBlockItems.output().contractCall()).thenReturn(mockContractCallOutput);
        when(mockContractCallOutput.contractCallResult()).thenReturn(mockContractFunctionResult);

        SingleTransactionRecord result = translator.translate(mockTransactionBlockItems, mockStateChanges);

        // Then
        TransactionRecord expectedRecord = TransactionRecord.newBuilder()
                .contractCallResult(mockContractFunctionResult)
                .build();

        assertEquals(mockTransaction, result.transaction());
        assertEquals(expectedRecord, result.transactionRecord());
        assertEquals(result.transactionSidecarRecords(), List.of());
        assertEquals(result.transactionOutputs(), new SingleTransactionRecord.TransactionOutputs(null));
    }
}
