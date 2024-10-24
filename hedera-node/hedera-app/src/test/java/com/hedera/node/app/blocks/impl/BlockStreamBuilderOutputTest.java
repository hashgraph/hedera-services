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

package com.hedera.node.app.blocks.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.output.CallContractOutput;
import com.hedera.hapi.block.stream.output.CryptoTransferOutput;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.transaction.AssessedCustomFee;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.hapi.platform.event.EventTransaction;
import com.hedera.node.app.blocks.BlockItemsTranslator;
import com.hedera.node.app.spi.records.RecordSource;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlockStreamBuilderOutputTest {
    private static final Timestamp CONSENSUS_TIME = new Timestamp(1_234_567, 890);
    private static final TransactionID TXN_ID = TransactionID.newBuilder()
            .accountID(AccountID.newBuilder().accountNum(2L).build())
            .build();
    private static final List<AssessedCustomFee> ASSESSED_CUSTOM_FEES = List.of(new AssessedCustomFee(
            1L,
            TokenID.newBuilder().tokenNum(123).build(),
            AccountID.newBuilder().accountNum(98L).build(),
            List.of(AccountID.newBuilder().accountNum(2L).build())));
    private static final ContractFunctionResult FUNCTION_RESULT =
            ContractFunctionResult.newBuilder().amount(666L).build();
    private static final BlockItem EVENT_TRANSACTION = BlockItem.newBuilder()
            .eventTransaction(EventTransaction.newBuilder()
                    .applicationTransaction(Bytes.wrap("MOCK"))
                    .build())
            .build();
    private static final BlockItem TRANSACTION_RESULT = BlockItem.newBuilder()
            .transactionResult(
                    TransactionResult.newBuilder().transactionFeeCharged(123L).build())
            .build();
    private static final BlockItem FIRST_OUTPUT = BlockItem.newBuilder()
            .transactionOutput(TransactionOutput.newBuilder()
                    .cryptoTransfer(new CryptoTransferOutput(ASSESSED_CUSTOM_FEES))
                    .build())
            .build();
    private static final BlockItem SECOND_OUTPUT = BlockItem.newBuilder()
            .transactionOutput(TransactionOutput.newBuilder()
                    .contractCall(new CallContractOutput(List.of(), FUNCTION_RESULT))
                    .build())
            .build();
    private static final BlockItem STATE_CHANGES = BlockItem.newBuilder()
            .stateChanges(new StateChanges(CONSENSUS_TIME, List.of()))
            .build();
    private static final List<BlockItem> ITEMS_NO_OUTPUTS =
            List.of(EVENT_TRANSACTION, TRANSACTION_RESULT, STATE_CHANGES);
    private static final List<BlockItem> ITEMS_WITH_OUTPUTS =
            List.of(EVENT_TRANSACTION, TRANSACTION_RESULT, FIRST_OUTPUT, SECOND_OUTPUT, STATE_CHANGES);

    @Mock
    private Consumer<BlockItem> action;

    @Mock
    private TranslationContext translationContext;

    @Mock
    private BlockItemsTranslator translator;

    @Test
    void traversesItemsAsExpected() {
        final var subject = new BlockStreamBuilder.Output(ITEMS_WITH_OUTPUTS, translationContext);

        subject.forEachItem(action);

        ITEMS_WITH_OUTPUTS.forEach(item -> verify(action).accept(item));
    }

    @Test
    void translatesNoOutputsToRecordAsExpected() {
        given(translator.translateRecord(translationContext, TRANSACTION_RESULT.transactionResultOrThrow()))
                .willReturn(TransactionRecord.DEFAULT);

        final var subject = new BlockStreamBuilder.Output(ITEMS_NO_OUTPUTS, translationContext);

        assertSame(TransactionRecord.DEFAULT, subject.toRecord(translator));
    }

    @Test
    void translatesOutputsToRecordAsExpected() {
        given(translator.translateRecord(
                        translationContext,
                        TRANSACTION_RESULT.transactionResultOrThrow(),
                        FIRST_OUTPUT.transactionOutputOrThrow(),
                        SECOND_OUTPUT.transactionOutputOrThrow()))
                .willReturn(TransactionRecord.DEFAULT);

        final var subject = new BlockStreamBuilder.Output(ITEMS_WITH_OUTPUTS, translationContext);

        assertSame(TransactionRecord.DEFAULT, subject.toRecord(translator));
    }

    @Test
    void translatesNoOutputsToReceiptAsExpected() {
        given(translationContext.txnId()).willReturn(TXN_ID);
        given(translator.translateReceipt(translationContext, TRANSACTION_RESULT.transactionResultOrThrow()))
                .willReturn(TransactionReceipt.DEFAULT);

        final var subject = new BlockStreamBuilder.Output(ITEMS_NO_OUTPUTS, translationContext);

        assertEquals(
                new RecordSource.IdentifiedReceipt(TXN_ID, TransactionReceipt.DEFAULT),
                subject.toIdentifiedReceipt(translator));
    }

    @Test
    void translatesOutputsToReceiptAsExpected() {
        given(translationContext.txnId()).willReturn(TXN_ID);
        given(translator.translateReceipt(
                        translationContext,
                        TRANSACTION_RESULT.transactionResultOrThrow(),
                        FIRST_OUTPUT.transactionOutputOrThrow(),
                        SECOND_OUTPUT.transactionOutputOrThrow()))
                .willReturn(TransactionReceipt.DEFAULT);

        final var subject = new BlockStreamBuilder.Output(ITEMS_WITH_OUTPUTS, translationContext);

        assertEquals(
                new RecordSource.IdentifiedReceipt(TXN_ID, TransactionReceipt.DEFAULT),
                subject.toIdentifiedReceipt(translator));
    }
}
