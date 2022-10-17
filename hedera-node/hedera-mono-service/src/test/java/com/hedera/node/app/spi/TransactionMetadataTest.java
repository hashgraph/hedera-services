/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.spi;

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.TxnUtils.buildTransactionFrom;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.utils.KeyUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.Collections;
import java.util.List;
import org.apache.commons.codec.DecoderException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionMetadataTest {
    private Timestamp consensusTimestamp = Timestamp.newBuilder().setSeconds(1_234_567L).build();
    private Key key = KeyUtils.A_COMPLEX_KEY;
    private AccountID payer = asAccount("0.0.3");
    private TransactionMetadata subject;

    @Test
    void gettersWorkAsExpectedWhenOtherSigsDoesntExist() throws DecoderException {
        final var txn = createAccountTransaction();
        final var payerKey = JKey.mapKey(key);
        subject = new TransactionMetadata(txn, payerKey, Collections.emptyList());

        assertFalse(subject.failed());
        assertEquals(txn, subject.transaction());
        assertEquals(payerKey, subject.getPayerSig());
        assertEquals(Collections.emptyList(), subject.getOthersSigs());
    }

    @Test
    void gettersWorkAsExpectedWhenOtherSigsExist() throws DecoderException {
        final var txn = createAccountTransaction();
        final var payerKey = JKey.mapKey(key);
        subject = new TransactionMetadata(txn, payerKey, List.of(payerKey));

        assertFalse(subject.failed());
        assertEquals(txn, subject.transaction());
        assertEquals(payerKey, subject.getPayerSig());
        assertEquals(List.of(payerKey), subject.getOthersSigs());
    }

    private Transaction createAccountTransaction() {
        final var transactionID =
                TransactionID.newBuilder()
                        .setAccountID(payer)
                        .setTransactionValidStart(consensusTimestamp);
        final var createTxnBody =
                CryptoCreateTransactionBody.newBuilder()
                        .setKey(key)
                        .setReceiverSigRequired(true)
                        .setMemo("Create Account")
                        .build();
        final var transactionBody =
                TransactionBody.newBuilder()
                        .setTransactionID(transactionID)
                        .setCryptoCreateAccount(createTxnBody)
                        .build();
        return buildTransactionFrom(transactionBody);
    }
}
