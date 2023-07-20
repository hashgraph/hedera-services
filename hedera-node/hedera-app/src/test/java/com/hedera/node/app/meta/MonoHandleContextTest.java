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

package com.hedera.node.app.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.ledger.ids.EntityIdSource;
import com.hedera.node.app.service.networkadmin.ReadableRunningHashLeafStore;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonoHandleContextTest {
    private static final Instant NOW = Instant.ofEpochSecond(1_234_567L, 890);

    @Mock
    private EntityIdSource ids;

    @Mock
    private ExpiryValidator expiryValidator;

    @Mock
    private AttributeValidator attributeValidator;

    @Mock
    private TransactionContext txnCtx;

    @Mock
    private ReadableStoreFactory readableStoreFactory;

    @Mock
    private SingleTransactionRecordBuilderImpl recordBuilder;

    private MonoHandleContext subject;

    @BeforeEach
    void setup() {
        subject = new MonoHandleContext(
                TransactionBody.DEFAULT,
                ids,
                expiryValidator,
                attributeValidator,
                txnCtx,
                readableStoreFactory,
                recordBuilder);
    }

    @Test
    void getsNowFromCtx() {
        given(txnCtx.consensusTime()).willReturn(NOW);

        assertThat(subject.consensusNow()).isEqualTo(NOW);
    }

    @Test
    void delegatesIdCreationToEntitySource() {
        final var expectedNum = 666L;
        given(ids.newAccountNumber()).willReturn(expectedNum);

        final var actualNum = subject.newEntityNum();

        assertThat(actualNum).isEqualTo(expectedNum);
    }

    @Test
    void returnsExpiryValidatorAsExpected() {
        assertThat(subject.expiryValidator()).isSameAs(expiryValidator);
    }

    @Test
    void returnsAttributeValidatorAsExpected() {
        assertThat(subject.attributeValidator()).isSameAs(attributeValidator);
    }

    @Test
    void createsStore() {
        subject.readableStore(ReadableRunningHashLeafStore.class);

        verify(readableStoreFactory).getStore(ReadableRunningHashLeafStore.class);
    }
}
