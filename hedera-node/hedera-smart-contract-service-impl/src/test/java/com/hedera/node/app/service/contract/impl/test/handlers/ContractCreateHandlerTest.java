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

package com.hedera.node.app.service.contract.impl.test.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.handlers.ContractCreateHandler;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContractCreateHandlerTest extends ContractHandlerTestBase {
    private final ContractCreateHandler subject = new ContractCreateHandler();

    @Test
    @DisplayName("Adds valid admin key")
    void validAdminKey() {
        final var txn = contractCreateTransaction(adminKey, null);
        final var context = new PreHandleContext(keyLookup, txn);
        subject.preHandle(context);

        basicMetaAssertions(context, 1, false, ResponseCodeEnum.OK);
        assertThat(context.getPayerKey()).isEqualTo(payerKey);
        //        FUTURE: uncomment this after JKey removal
        //        assertIterableEquals(List.of(adminHederaKey), meta.requiredNonPayerKeys());
    }

    @Test
    @DisplayName("Fails for invalid payer account")
    void invalidPayer() {
        final var txn = contractCreateTransaction(adminKey, null);
        given(keyLookup.getKey(payer)).willReturn(KeyOrLookupFailureReason.withFailureReason(ResponseCodeEnum.INVALID_ACCOUNT_ID));
        final var context = new PreHandleContext(keyLookup, txn);
        subject.preHandle(context);

        basicMetaAssertions(context, 0, true, ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID);
        assertThat(context.getPayerKey()).isNull();
    }

    @Test
    @DisplayName("admin key with contractID is not added")
    void adminKeyWithContractID() {
        final var txn = contractCreateTransaction(adminContractKey, null);
        final var context = new PreHandleContext(keyLookup, txn);
        subject.preHandle(context);

        basicMetaAssertions(context, 0, false, ResponseCodeEnum.OK);
        assertThat(context.getPayerKey()).isEqualTo(payerKey);
    }

    @Test
    @DisplayName("autoRenew account key is added")
    void autoRenewAccountIdAdded() {
        final var txn = contractCreateTransaction(adminContractKey, autoRenewAccountId);
        final var context = new PreHandleContext(keyLookup, txn);
        subject.preHandle(context);

        basicMetaAssertions(context, 1, false, ResponseCodeEnum.OK);
        assertThat(context.getPayerKey()).isEqualTo(payerKey);
        assertThat(context.getRequiredNonPayerKeys()).containsExactly(autoRenewHederaKey);
    }

    @Test
    @DisplayName("autoRenew account key is not added when it is sentinel value")
    void autoRenewAccountIdAsSentinelNotAdded() {
        final var txn = contractCreateTransaction(adminContractKey, asAccount("0.0.0"));
        final var context = new PreHandleContext(keyLookup, txn);
        subject.preHandle(context);

        basicMetaAssertions(context, 0, false, ResponseCodeEnum.OK);
        assertThat(context.getPayerKey()).isEqualTo(payerKey);
        assertThat(context.getRequiredNonPayerKeys()).isEmpty();
    }

    @Test
    @DisplayName("autoRenew account and adminKey both added")
    void autoRenewAccountIdAndAdminBothAdded() {
        final var txn = contractCreateTransaction(adminKey, autoRenewAccountId);
        final var context = new PreHandleContext(keyLookup, txn);
        subject.preHandle(context);

        basicMetaAssertions(context, 2, false, ResponseCodeEnum.OK);
        assertThat(context.getPayerKey()).isEqualTo(payerKey);
        //        FUTURE: uncomment this after JKey removal
        //        assertEquals(List.of(adminHederaKey, autoRenewHederaKey),
        // meta.requiredNonPayerKeys());
    }

    private TransactionBody contractCreateTransaction(final Key adminKey, final AccountID autoRenewId) {
        final var transactionID =
                new TransactionID.Builder().accountID(payer).transactionValidStart(consensusTimestamp);
        final var createTxnBody = new ContractCreateTransactionBody.Builder().memo("Create Contract");
        if (adminKey != null) {
            createTxnBody.adminKey(adminKey);
        }

        if (autoRenewId != null) {
            if (!autoRenewId.equals(asAccount("0.0.0"))) {
                given(keyLookup.getKey(autoRenewId)).willReturn(KeyOrLookupFailureReason.withKey(autoRenewHederaKey));
            }
            createTxnBody.autoRenewAccountId(autoRenewId);
        }

        return new TransactionBody.Builder()
                .transactionID(transactionID)
                .contractCreateInstance(createTxnBody)
                .build();
    }
}
