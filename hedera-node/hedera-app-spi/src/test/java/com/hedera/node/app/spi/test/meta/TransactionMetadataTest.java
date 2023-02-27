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

package com.hedera.node.app.spi.test.meta;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.TransactionSignature;
import java.util.Map;
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
        final Map<HederaKey, TransactionSignature> signatures = Map.of();

        assertThatCode(() -> new TransactionMetadata(null, null, null, OK, null, null, signatures, null))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new TransactionMetadata(
                        txBody, payer, null, null, payerKey, null, signatures, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () -> new TransactionMetadata(txBody, payer, null, OK, payerKey, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testPreHandleContextConstructor(
            @Mock PreHandleContext context,
            @Mock TransactionSignature payerSignature,
            @Mock TransactionSignature otherSignature) {
        // given
        when(context.getTxn()).thenReturn(txBody);
        when(context.getPayer()).thenReturn(payer);
        when(context.getStatus()).thenReturn(OK);
        when(context.getPayerKey()).thenReturn(payerKey);
        final var signatureMap = SignatureMap.newBuilder().build();
        final var innerMetadata = new TransactionMetadata(null, null, null, OK, null, null, Map.of(), null);

        // when
        final var metadata =
                new TransactionMetadata(context, signatureMap, payerSignature, Map.of(otherKey, otherSignature), innerMetadata);

        // then
        assertThat(metadata.txnBody()).isEqualTo(txBody);
        assertThat(metadata.payer()).isEqualTo(payer);
        assertThat(metadata.signatureMap()).isEqualTo(signatureMap);
        assertThat(metadata.status()).isEqualTo(OK);
        assertThat(metadata.payerKey()).isEqualTo(payerKey);
        assertThat(metadata.payerSignature()).isEqualTo(payerSignature);
        assertThat(metadata.otherSignatures()).containsExactly(Map.entry(otherKey, otherSignature));
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testPreHandleContextConstructorWithIllegalArguments(@Mock PreHandleContext context) {
        // given
        when(context.getTxn()).thenReturn(txBody);
        when(context.getPayer()).thenReturn(payer);
        when(context.getStatus()).thenReturn(OK);
        final var signatureMap = SignatureMap.newBuilder().build();
        final Map<HederaKey, TransactionSignature> signatures = Map.of();

        // then
        assertThatCode(() -> new TransactionMetadata(context, signatureMap, null, signatures, null))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new TransactionMetadata(null, signatureMap, null, signatures, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TransactionMetadata(context, null, null, signatures, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TransactionMetadata(context, signatureMap, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testErrorConstructor() {
        // when
        final var metadata = new TransactionMetadata(INVALID_ACCOUNT_ID);

        // then
        assertThat(metadata.txnBody()).isNull();
        assertThat(metadata.payer()).isNull();
        assertThat(metadata.signatureMap()).isNull();
        assertThat(metadata.status()).isEqualTo(INVALID_ACCOUNT_ID);
        assertThat(metadata.payerKey()).isNull();
        assertThat(metadata.payerSignature()).isNull();
        assertThat(metadata.otherSignatures()).isEmpty();
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testErrorConstructorWithInvalidArguments() {
        assertThatThrownBy(() -> new TransactionMetadata(null)).isInstanceOf(NullPointerException.class);
    }
}
