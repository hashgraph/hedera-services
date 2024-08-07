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

import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.hapi.block.stream.output.UtilPrngOutput;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.bdd.junit.support.translators.SingleTransactionBlockItems;
import com.hedera.services.bdd.junit.support.translators.UtilPrngTranslator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UtilPrngTranslatorTest {

    @Mock
    private SingleTransactionBlockItems mockTransactionBlockItems;

    @Mock
    private TransactionOutput mockTransactionOutput;

    @Mock
    private Transaction mockTransaction;

    @Mock
    private StateChanges mockStateChanges;

    @Mock
    private UtilPrngOutput mockUtilPrngOutput;

    private UtilPrngTranslator translator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        translator = new UtilPrngTranslator();
    }

    @Test
    void testTranslateWithPrngBytes() {
        // Arrange
        when(mockTransactionBlockItems.txn()).thenReturn(mockTransaction);
        when(mockTransactionBlockItems.output()).thenReturn(mockTransactionOutput);
        when(mockTransactionBlockItems.output().hasUtilPrng()).thenReturn(true);
        when(mockTransactionBlockItems.output().utilPrng()).thenReturn(mockUtilPrngOutput);
        final Bytes bytes = Bytes.fromHex("badcadfaddad2bedfedbeef959feedbeadcafecadecedebeed4acedecada5ada");
        when(mockUtilPrngOutput.entropy()).thenReturn(new OneOf<>(UtilPrngOutput.EntropyOneOfType.PRNG_BYTES, bytes));
        final Timestamp timestamp = Timestamp.newBuilder().seconds(123456L).build();
        when(mockStateChanges.consensusTimestamp()).thenReturn(timestamp);

        // Act
        SingleTransactionRecord result = translator.translate(mockTransactionBlockItems, mockStateChanges);

        // Assert
        TransactionRecord expectedRecord = TransactionRecord.newBuilder()
                .prngBytes(bytes)
                .consensusTimestamp(timestamp)
                .build();
        assertEquals(expectedRecord, result.transactionRecord());
    }

    @Test
    void testTranslateWithPrngNumber() {
        // Arrange
        when(mockTransactionBlockItems.txn()).thenReturn(mockTransaction);
        when(mockTransactionBlockItems.output()).thenReturn(mockTransactionOutput);
        when(mockTransactionBlockItems.output().hasUtilPrng()).thenReturn(true);
        when(mockTransactionBlockItems.output().utilPrng()).thenReturn(mockUtilPrngOutput);
        final int number = 42;
        when(mockUtilPrngOutput.entropy()).thenReturn(new OneOf<>(UtilPrngOutput.EntropyOneOfType.PRNG_NUMBER, number));
        final Timestamp timestamp = Timestamp.newBuilder().seconds(123456L).build();
        when(mockStateChanges.consensusTimestamp()).thenReturn(timestamp);

        // Act
        SingleTransactionRecord result = translator.translate(mockTransactionBlockItems, mockStateChanges);

        // Assert
        TransactionRecord expectedRecord = TransactionRecord.newBuilder()
                .prngNumber(number)
                .consensusTimestamp(timestamp)
                .build();
        assertEquals(expectedRecord, result.transactionRecord());
    }

    @Test
    void testTranslateNoPrng() {
        // Arrange
        when(mockTransactionBlockItems.txn()).thenReturn(mockTransaction);
        when(mockTransactionBlockItems.output()).thenReturn(mockTransactionOutput);
        when(mockTransactionBlockItems.output().hasUtilPrng()).thenReturn(false);
        final Timestamp timestamp = Timestamp.newBuilder().seconds(123456L).build();
        when(mockStateChanges.consensusTimestamp()).thenReturn(timestamp);

        // Act
        SingleTransactionRecord result = translator.translate(mockTransactionBlockItems, mockStateChanges);

        // Assert
        TransactionRecord expectedRecord =
                TransactionRecord.newBuilder().consensusTimestamp(timestamp).build();
        assertEquals(expectedRecord, result.transactionRecord());
    }
}
