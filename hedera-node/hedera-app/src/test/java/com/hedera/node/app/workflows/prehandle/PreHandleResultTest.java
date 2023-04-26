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
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.NODE_DUE_DILIGENCE_FAILURE;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.PRE_HANDLE_FAILURE;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.SO_FAR_SO_GOOD;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.UNKNOWN_FAILURE;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.workflows.TransactionInfo;
import java.util.Map;
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
    void statusMustNotBeNull(@Mock AccountID payer, @Mock TransactionInfo txInfo, @Mock PreHandleResult innerResult) {
        final Future<SignatureVerification> payerFuture = completedFuture(null);
        final Map<Key, Future<SignatureVerification>> nonPayers = Map.of();
        final Map<Long, Future<SignatureVerification>> hollows = Map.of();
        assertThatThrownBy(() ->
                        new PreHandleResult(payer, null, OK, txInfo, payerFuture, nonPayers, hollows, innerResult))
                .isInstanceOf(NullPointerException.class);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void responseCodeMustNotBeNull(
            @Mock AccountID payer, @Mock TransactionInfo txInfo, @Mock PreHandleResult innerResult) {
        final Future<SignatureVerification> payerFuture = completedFuture(null);
        final Map<Key, Future<SignatureVerification>> nonPayers = Map.of();
        final Map<Long, Future<SignatureVerification>> hollows = Map.of();
        assertThatThrownBy(() -> new PreHandleResult(
                        payer, SO_FAR_SO_GOOD, null, txInfo, payerFuture, nonPayers, hollows, innerResult))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Unknown failures only set the status to UNKNOWN")
    void unknownFailure() {
        final var result = PreHandleResult.unknownFailure();

        assertThat(result.status()).isEqualTo(UNKNOWN_FAILURE);
        assertThat(result.responseCode()).isEqualTo(UNKNOWN);
        assertThat(result.innerResult()).isNull();
        assertThat(result.payer()).isNull();
        assertThat(result.txInfo()).isNull();
        assertThat(result.payerVerification()).isNull();
        assertThat(result.nonPayerVerifications()).isNull();
        assertThat(result.nonPayerHollowVerifications()).isNull();
    }

    @Test
    @DisplayName("Node Diligence Failures only set the status and the payer to be the node and the tx info")
    void nodeDiligenceFailure(@Mock TransactionInfo txInfo) {
        final var nodeAccountId = AccountID.newBuilder().accountNum(3).build();
        final var status = INVALID_PAYER_ACCOUNT_ID;
        final var result = PreHandleResult.nodeDueDiligenceFailure(nodeAccountId, status, txInfo);

        assertThat(result.status()).isEqualTo(NODE_DUE_DILIGENCE_FAILURE);
        assertThat(result.responseCode()).isEqualTo(status);
        assertThat(result.innerResult()).isNull();
        assertThat(result.payer()).isEqualTo(nodeAccountId);
        assertThat(result.txInfo()).isSameAs(txInfo);
        assertThat(result.payerVerification()).isNull();
        assertThat(result.nonPayerVerifications()).isNull();
        assertThat(result.nonPayerHollowVerifications()).isNull();
    }

    @Test
    @DisplayName("Pre-Handle Failures set the payer, status, txInfo, and payer verification future")
    void preHandleFailure(@Mock TransactionInfo txInfo, @Mock SignatureVerification payerVerification) {
        final var payer = AccountID.newBuilder().accountNum(1001).build();
        final var status = INVALID_PAYER_ACCOUNT_ID;
        final var payerFuture = completedFuture(payerVerification);
        final var result = PreHandleResult.preHandleFailure(payer, status, txInfo, payerFuture);

        assertThat(result.status()).isEqualTo(PRE_HANDLE_FAILURE);
        assertThat(result.responseCode()).isEqualTo(status);
        assertThat(result.innerResult()).isNull();
        assertThat(result.payer()).isEqualTo(payer);
        assertThat(result.txInfo()).isSameAs(txInfo);
        assertThat(result.payerVerification()).isNotNull();
        assertThat(result.payerVerification())
                .succeedsWithin(1, TimeUnit.MILLISECONDS)
                .isSameAs(payerVerification);
        assertThat(result.nonPayerVerifications()).isNull();
        assertThat(result.nonPayerHollowVerifications()).isNull();
    }
}
