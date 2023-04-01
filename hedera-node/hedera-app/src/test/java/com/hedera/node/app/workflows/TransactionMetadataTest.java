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

package com.hedera.node.app.workflows;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.swirlds.common.crypto.TransactionSignature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionMetadataTest {
    @Mock
    private HederaKey payerKey;

    @Mock
    private HederaKey otherKey;

    private final TransactionBody txBody = TransactionBody.newBuilder().build();
    private final AccountID payer = AccountID.newBuilder().accountNum(42L).build();

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
        when(context.getRequiredNonPayerKeys()).thenReturn(List.of(otherKey));
        final var signatureMap = SignatureMap.newBuilder().build();
        final var innerMetadata = new PreHandleResult(null, null, null, OK, null, null, List.of(), null);
        final var expectedSigs = List.of(payerSignature, otherSignature);

        // when
        final var metadata = new PreHandleResult(context, signatureMap, expectedSigs, innerMetadata);

        // then
        assertThat(metadata.txnBody()).isEqualTo(txBody);
        assertThat(metadata.payer()).isEqualTo(payer);
        assertThat(metadata.signatureMap()).isEqualTo(signatureMap);
        assertThat(metadata.status()).isEqualTo(OK);
        assertThat(metadata.payerKey()).isEqualTo(payerKey);
        assertThat(metadata.cryptoSignatures()).isEqualTo(expectedSigs);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testPreHandleContextConstructorWithIllegalArguments(@Mock PreHandleContext context) {
        // given
        when(context.getTxn()).thenReturn(txBody);
        when(context.getPayer()).thenReturn(payer);
        when(context.getStatus()).thenReturn(OK);
        final var signatureMap = SignatureMap.newBuilder().build();
        final List<TransactionSignature> signatures = List.of();

        // then
        assertThatCode(() -> new PreHandleResult(context, signatureMap, signatures, null))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new PreHandleResult(null, signatureMap, signatures, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PreHandleResult(context, null, signatures, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PreHandleResult(context, signatureMap, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testErrorConstructor() {
        // when
        final var metadata = new PreHandleResult(INVALID_ACCOUNT_ID);

        // then
        assertThat(metadata.txnBody()).isNull();
        assertThat(metadata.payer()).isNull();
        assertThat(metadata.signatureMap()).isNull();
        assertThat(metadata.status()).isEqualTo(INVALID_ACCOUNT_ID);
        assertThat(metadata.payerKey()).isNull();
        assertThat(metadata.cryptoSignatures()).isEmpty();
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testErrorConstructorWithInvalidArguments() {
        assertThatThrownBy(() -> new PreHandleResult(null)).isInstanceOf(NullPointerException.class);
    }
}
