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

import static com.hedera.node.app.spi.key.HederaKey.asHederaKey;
import static com.hedera.services.legacy.core.jproto.JKey.mapKey;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.token.impl.AccountStore;
import com.hedera.node.app.spi.meta.SigTransactionMetadata;
import com.hedera.node.app.spi.state.States;
import com.hedera.node.app.state.impl.InMemoryStateImpl;
import com.hedera.node.app.state.impl.RebuiltStateImpl;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.KeyUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.List;
import java.util.Optional;
import org.apache.commons.codec.DecoderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SigTransactionMetadataTest {
    private Timestamp consensusTimestamp = Timestamp.newBuilder().setSeconds(1_234_567L).build();
    private Key key = KeyUtils.A_COMPLEX_KEY;
    private AccountID payer = asAccount("0.0.3");
    private Long payerNum = 3L;

    private static final String ACCOUNTS = "ACCOUNTS";
    private static final String ALIASES = "ALIASES";

    @Mock private RebuiltStateImpl aliases;
    @Mock private InMemoryStateImpl accounts;
    @Mock private States states;
    @Mock private MerkleAccount account;

    private AccountStore store;
    private SigTransactionMetadata subject;

    @BeforeEach
    void setUp() {
        given(states.get(ACCOUNTS)).willReturn(accounts);
        given(states.get(ALIASES)).willReturn(aliases);
        store = new AccountStore(states);
    }

    @Test
    void gettersWorkAsExpectedWhenOnlyPayerKeyExist() throws DecoderException {
        final var txn = createAccountTransaction();
        final var payerKey = mapKey(key);
        given(accounts.get(payerNum)).willReturn(Optional.of(account));
        given(account.getAccountKey()).willReturn(payerKey);

        subject = new SigTransactionMetadata(store, txn, payer, List.of());

        assertFalse(subject.failed());
        assertEquals(txn, subject.getTxn());
        assertEquals(List.of(payerKey), subject.getReqKeys());
    }

    @Test
    void gettersWorkAsExpectedWhenOtherSigsExist() throws DecoderException {
        final var txn = createAccountTransaction();
        final var payerKey = mapKey(key);

        given(accounts.get(payerNum)).willReturn(Optional.of(account));
        given(account.getAccountKey()).willReturn(payerKey);

        subject = new SigTransactionMetadata(store, txn, payer, List.of(payerKey));

        assertFalse(subject.failed());
        assertEquals(txn, subject.getTxn());
        assertEquals(List.of(payerKey, payerKey), subject.getReqKeys());
    }

    @Test
    void failsWhenPayerKeyDoesntExist() {
        final var txn = createAccountTransaction();
        final var payerKey = (JKey) asHederaKey(key).get();

        given(accounts.get(payerNum)).willReturn(Optional.empty());

        subject = new SigTransactionMetadata(store, txn, payer, List.of(payerKey));

        assertTrue(subject.failed());
        assertEquals(INVALID_PAYER_ACCOUNT_ID, subject.status());

        assertEquals(txn, subject.getTxn());
        assertEquals(List.of(payerKey), subject.getReqKeys());
    }

    private TransactionBody createAccountTransaction() {
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
        return TransactionBody.newBuilder()
                .setTransactionID(transactionID)
                .setCryptoCreateAccount(createTxnBody)
                .build();
    }
}
