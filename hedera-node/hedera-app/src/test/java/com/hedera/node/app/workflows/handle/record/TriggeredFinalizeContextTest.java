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

package com.hedera.node.app.workflows.handle.record;

import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_TRANSFER;
import static com.hedera.hapi.node.base.HederaFunctionality.TOKEN_MINT;
import static com.hedera.node.app.fixtures.AppTestBase.DEFAULT_CONFIG;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.CHILD;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.SCHEDULED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.node.app.service.token.records.ChildRecordBuilder;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.store.WritableStoreFactory;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TriggeredFinalizeContextTest {
    private static final Instant NOW = Instant.ofEpochSecond(1_234_567L);

    @Mock
    private RecordListBuilder recordListBuilder;

    @Mock
    private ReadableStoreFactory readableStoreFactory;

    @Mock
    private WritableStoreFactory writableStoreFactory;

    @Mock
    private SingleTransactionRecordBuilderImpl recordBuilder;

    @Mock
    private SingleTransactionRecordBuilderImpl childNoAdjustments;

    @Mock
    private SingleTransactionRecordBuilderImpl childWithAdjustments;

    @Mock
    private Consumer<ChildRecordBuilder> consumer;

    private TriggeredFinalizeContext subject;

    @Test
    void nonScheduledChildrenHaveNoSelfChildren() {
        subject = subjectWith(CHILD, CRYPTO_TRANSFER);
        subject.forEachChildRecord(ChildRecordBuilder.class, consumer);

        assertThat(subject.hasChildOrPrecedingRecords()).isFalse();
        verifyNoInteractions(recordListBuilder);
        verifyNoInteractions(consumer);
    }

    @Test
    void scheduledMintChildrenHaveNoSelfChildren() {
        subject = subjectWith(SCHEDULED, TOKEN_MINT);
        subject.forEachChildRecord(ChildRecordBuilder.class, consumer);

        assertThat(subject.hasChildOrPrecedingRecords()).isFalse();
        verifyNoInteractions(recordListBuilder);
        verifyNoInteractions(consumer);
    }

    @Test
    void scheduledCryptoTransferHasChildrenIfBalancesChange() {
        given(childNoAdjustments.transferList()).willReturn(TransferList.DEFAULT);
        given(childWithAdjustments.transferList())
                .willReturn(new TransferList(List.of(AccountAmount.DEFAULT, AccountAmount.DEFAULT)));
        given(recordListBuilder.precedingRecordBuilders())
                .willReturn(List.of(childNoAdjustments, childWithAdjustments));
        subject = subjectWith(SCHEDULED, CRYPTO_TRANSFER);
        subject.forEachChildRecord(ChildRecordBuilder.class, consumer);

        assertThat(subject.hasChildOrPrecedingRecords()).isTrue();
        verify(consumer).accept(childWithAdjustments);
        verify(consumer, never()).accept(childNoAdjustments);
    }

    private TriggeredFinalizeContext subjectWith(
            @NonNull final HandleContext.TransactionCategory category,
            @NonNull final HederaFunctionality functionality) {
        return new TriggeredFinalizeContext(
                readableStoreFactory,
                writableStoreFactory,
                recordBuilder,
                NOW,
                DEFAULT_CONFIG,
                functionality,
                category,
                recordListBuilder);
    }
}
