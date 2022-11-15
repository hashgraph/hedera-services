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
package com.hedera.node.app;

import static com.hedera.node.app.Utils.asHederaKey;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.token.impl.AccountStore;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.state.States;
import com.hedera.node.app.state.impl.InMemoryStateImpl;
import com.hedera.node.app.state.impl.RebuiltStateImpl;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.KeyUtils;
import com.hederahashgraph.api.proto.java.*;
import java.util.List;
import java.util.Optional;
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
    private HederaKey payerKey = asHederaKey(key).get();

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
    void gettersWorkAsExpectedWhenOnlyPayerKeyExist() {
        final var txn = createAccountTransaction();
        given(accounts.get(payerNum)).willReturn(Optional.of(account));
        given(account.getAccountKey()).willReturn((JKey) payerKey);

        subject = new SigTransactionMetadata(store, txn, payer, List.of());

        assertFalse(subject.failed());
        assertEquals(txn, subject.getTxn());
        assertEquals(List.of(payerKey), subject.getReqKeys());
    }

    @Test
    void gettersWorkAsExpectedWhenOtherSigsExist() {
        final var txn = createAccountTransaction();
        given(accounts.get(payerNum)).willReturn(Optional.of(account));
        given(account.getAccountKey()).willReturn((JKey) payerKey);

        subject = new SigTransactionMetadata(store, txn, payer, List.of(payerKey));

        assertFalse(subject.failed());
        assertEquals(txn, subject.getTxn());
        assertEquals(List.of(payerKey, payerKey), subject.getReqKeys());
    }

    @Test
    void failsWhenPayerKeyDoesntExist() {
        final var txn = createAccountTransaction();
        given(accounts.get(payerNum)).willReturn(Optional.empty());

        subject = new SigTransactionMetadata(store, txn, payer, List.of(payerKey));

        assertTrue(subject.failed());
        assertEquals(INVALID_PAYER_ACCOUNT_ID, subject.status());

        assertEquals(txn, subject.getTxn());
        assertEquals(List.of(payerKey), subject.getReqKeys());
    }

    @Test
    void addsToReqKeysCorrectly() {
        given(accounts.get(payerNum)).willReturn(Optional.of(account));
        given(account.getAccountKey()).willReturn((JKey) payerKey);

        final var anotherKey = asHederaKey(KeyUtils.A_COMPLEX_KEY).get();

        subject = new SigTransactionMetadata(store, createAccountTransaction(), payer, List.of());

        assertEquals(1, subject.getReqKeys().size());
        assertTrue(subject.getReqKeys().contains(payerKey));

        subject.addToReqKeys(anotherKey);
        assertEquals(2, subject.getReqKeys().size());
        assertTrue(subject.getReqKeys().contains(anotherKey));
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
