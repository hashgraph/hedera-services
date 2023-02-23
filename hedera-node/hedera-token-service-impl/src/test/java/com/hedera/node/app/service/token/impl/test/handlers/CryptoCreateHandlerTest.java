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
import com.hedera.node.app.spi.meta.PreHandleContext;
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

        final var context = new PreHandleContext(store, txn, payer);
        subject.preHandle(context);

        assertEquals(txn, context.getTxn());
        basicMetaAssertions(context, 1, false, OK);
        assertEquals(payerKey, context.getPayerKey());
    }

    @Test
    void noReceiverSigRequiredPreHandleCryptoCreate() {
        final var txn = createAccountTransaction(false);
        final var expected = new PreHandleContext(store, txn, payer);

        final var context = new PreHandleContext(store, txn, payer);
        subject.preHandle(context);

        assertEquals(expected.getTxn(), context.getTxn());
        assertFalse(context.getRequiredNonPayerKeys().contains(payerKey));
        basicMetaAssertions(context, 0, expected.failed(), OK);
        assertIterableEquals(List.of(), context.getRequiredNonPayerKeys());
        assertEquals(payerKey, context.getPayerKey());
    }

    @Test
    void handleNotImplemented() {
        assertThrows(UnsupportedOperationException.class, () -> subject.handle(metaToHandle));
    }

    private TransactionBody createAccountTransaction(final boolean receiverSigReq) {
        final var transactionID =
                TransactionID.newBuilder().setAccountID(payer).setTransactionValidStart(consensusTimestamp);
        final var createTxnBody = CryptoCreateTransactionBody.newBuilder()
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
