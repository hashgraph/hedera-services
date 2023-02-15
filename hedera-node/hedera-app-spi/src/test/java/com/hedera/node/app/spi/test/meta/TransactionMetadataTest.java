/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi.test.meta;

import static com.hedera.node.app.spi.fixtures.meta.TransactionMetadataAssert.assertThat;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.PreHandleContext;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionMetadataTest {

    @Mock
    private HederaKey payerKey;

    @Mock
    private HederaKey otherKey;

    private final TransactionBody txBody = TransactionBody.newBuilder().build();
    private final AccountID payer = AccountID.newBuilder().setAccountNum(42L).build();

    @Test
    void testDefaultConstructorWithInvalidArguments() {
        // given
        final List<HederaKey> hederaKeys = List.of(otherKey);
        final List<TransactionMetadata.ReadKeys> readKeys = List.of();

        assertThatCode(() -> new TransactionMetadata(null, null, OK, null, hederaKeys, null, readKeys))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new TransactionMetadata(txBody, payer, null, payerKey, hederaKeys, null, readKeys))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TransactionMetadata(txBody, payer, OK, payerKey, null, null, readKeys))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TransactionMetadata(txBody, payer, OK, payerKey, hederaKeys, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testPreHandleContextConstructor(@Mock PreHandleContext context) {
        // given
        final var meta = new Object();
        when(context.getTxn()).thenReturn(txBody);
        when(context.getPayer()).thenReturn(payer);
        when(context.getStatus()).thenReturn(OK);
        when(context.getPayerKey()).thenReturn(payerKey);
        when(context.getRequiredNonPayerKeys()).thenReturn(List.of(otherKey));
        when(context.getHandlerMetadata()).thenReturn(meta);
        final TransactionMetadata.ReadKeys readKeys =
                new TransactionMetadata.ReadKeys("myStatesKey", "myStateKey", Set.of("myReadKey"));

        // when
        final var metadata = new TransactionMetadata(context, List.of(readKeys));

        // then
        assertThat(metadata)
                .hasTxnBody(txBody)
                .hasPayer(payer)
                .hasStatus(OK)
                .hasPayerKey(payerKey)
                .hasRequiredNonPayerKeys(List.of(otherKey))
                .hasHandlerMetadata(meta)
                .hasReadKeys(List.of(readKeys));
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testPreHandleContextConstructorWithIllegalArguments(@Mock PreHandleContext context) {
        // given
        final List<TransactionMetadata.ReadKeys> readKeys = List.of();

        // then
        assertThatThrownBy(() -> new TransactionMetadata(null, readKeys)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TransactionMetadata(context, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testErrorConstructor() {
        // when
        final var metadata = new TransactionMetadata(txBody, payer, INVALID_ACCOUNT_ID);

        // then
        assertThat(metadata)
                .hasTxnBody(txBody)
                .hasPayer(payer)
                .hasStatus(INVALID_ACCOUNT_ID)
                .hasPayerKey(null)
                .hasRequiredNonPayerKeys(List.of())
                .hasHandlerMetadata(null)
                .hasReadKeys(List.of());
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testErrorConstructorWithInvalidArguments() {
        assertThatCode(() -> new TransactionMetadata(null, null, OK)).doesNotThrowAnyException();
        assertThatThrownBy(() -> new TransactionMetadata(txBody, payer, null)).isInstanceOf(NullPointerException.class);
    }
}
