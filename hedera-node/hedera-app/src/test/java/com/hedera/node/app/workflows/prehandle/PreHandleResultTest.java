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

package com.hedera.node.app.workflows.prehandle;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNKNOWN;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.signature.hapi.SignatureVerificationResults;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.workflows.TransactionInfo;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PreHandleResultTest {
    @SuppressWarnings("ConstantConditions")
    @Test
    void statusMustNotBeNull(
            @Mock AccountID payer,
            @Mock TransactionInfo txInfo,
            @Mock Future<SignatureVerificationResults> sigResults,
            @Mock PreHandleResult innerResult) {
        assertThatThrownBy(() -> new PreHandleResult(payer, null, txInfo, sigResults, innerResult))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Unknown failures only set the status to UNKNOWN")
    void unknownFailure() {
        final var result = PreHandleResult.unknownFailure();

        assertThat(result.status()).isEqualTo(UNKNOWN);
        assertThat(result.failed()).isTrue();
        assertThat(result.innerResult()).isNull();
        assertThat(result.payer()).isNull();
        assertThat(result.txInfo()).isNull();
        assertThat(result.signatureResults()).isNull();
    }

    @Test
    @DisplayName("Node Diligence Failures only set the status and the payer to be the node and the tx info")
    void nodeDiligenceFailure(@Mock TransactionInfo txInfo) {
        final var nodeAccountId = AccountID.newBuilder().accountNum(3).build();
        final var status = INVALID_PAYER_ACCOUNT_ID;
        final var result = PreHandleResult.nodeDueDiligenceFailure(nodeAccountId, status, txInfo);

        assertThat(result.status()).isEqualTo(status);
        assertThat(result.failed()).isTrue();
        assertThat(result.innerResult()).isNull();
        assertThat(result.payer()).isEqualTo(nodeAccountId);
        assertThat(result.txInfo()).isSameAs(txInfo);
        assertThat(result.signatureResults()).isNull();
    }

    @Test
    @DisplayName("Pre-Handle Failures set the payer, status, txInfo, and payer verification future")
    void preHandleFailure(@Mock TransactionInfo txInfo, @Mock SignatureVerification payerVerification) {
        final var payer = AccountID.newBuilder().accountNum(1001).build();
        final var status = INVALID_PAYER_ACCOUNT_ID;
        when(payerVerification.passed()).thenReturn(true);
        final var payerFuture = completedFuture(payerVerification);
        final var result = PreHandleResult.preHandleFailure(payer, status, txInfo, payerFuture);

        assertThat(result.status()).isEqualTo(status);
        assertThat(result.failed()).isTrue();
        assertThat(result.innerResult()).isNull();
        assertThat(result.payer()).isEqualTo(payer);
        assertThat(result.txInfo()).isSameAs(txInfo);
        assertThat(result.signatureResults()).isNotNull();
        assertThat(result.signatureResults())
                .succeedsWithin(1, TimeUnit.MILLISECONDS)
                .extracting(SignatureVerificationResults::getPayerSignatureVerification)
                .isSameAs(payerVerification);
    }

    @Test
    @DisplayName("Not Failed if OK")
    void notFailedIfOk(
            @Mock AccountID payer,
            @Mock TransactionInfo txInfo,
            @Mock PreHandleResult innerResult,
            @Mock Future<SignatureVerificationResults> sigResults) {
        final var result = new PreHandleResult(payer, OK, txInfo, sigResults, innerResult);
        assertThat(result.failed()).isFalse();
    }
}
