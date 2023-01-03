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
package com.hedera.node.app.service.token.impl.test.handlers;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.node.app.service.token.impl.handlers.CryptoCreateHandler;
import com.hedera.node.app.spi.meta.SigTransactionMetadataBuilder;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.List;
import org.junit.jupiter.api.Test;

class CryptoCreateHandlerTest extends CryptoHandlerTestBase {
    private CryptoCreateHandler subject = new CryptoCreateHandler();

    @Test
    void preHandleCryptoCreateVanilla() {
        final var txn = createAccountTransaction(true);

        final var meta = subject.preHandle(txn, payer, store);

        assertEquals(txn, meta.txnBody());
        basicMetaAssertions(meta, 1, false, OK);
        assertEquals(payerKey, meta.payerKey());
    }

    @Test
    void noReceiverSigRequiredPreHandleCryptoCreate() {
        final var txn = createAccountTransaction(false);
        final var expectedMeta =
                new SigTransactionMetadataBuilder(store).payerKeyFor(payer).txnBody(txn).build();

        final var meta = subject.preHandle(txn, payer, store);

        assertEquals(expectedMeta.txnBody(), meta.txnBody());
        assertFalse(meta.requiredNonPayerKeys().contains(payerKey));
        basicMetaAssertions(meta, 0, expectedMeta.failed(), OK);
        assertIterableEquals(List.of(), meta.requiredNonPayerKeys());
        assertEquals(payerKey, meta.payerKey());
    }

    private TransactionBody createAccountTransaction(final boolean receiverSigReq) {
        final var transactionID =
                TransactionID.newBuilder()
                        .setAccountID(payer)
                        .setTransactionValidStart(consensusTimestamp);
        final var createTxnBody =
                CryptoCreateTransactionBody.newBuilder()
                        .setKey(key)
                        .setReceiverSigRequired(receiverSigReq)
                        .setMemo("Create Account")
                        .build();

        return TransactionBody.newBuilder()
                .setTransactionID(transactionID)
                .setCryptoCreateAccount(createTxnBody)
                .build();
    }
}
