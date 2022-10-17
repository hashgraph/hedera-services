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
package com.hedera.node.app.service.token.impl;

import static com.hedera.node.app.spi.state.StateKey.ACCOUNT_STORE;
import static com.hedera.node.app.spi.state.StateKey.ALIASES_STORE;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asAliasAccount;
import static com.hedera.test.utils.TxnUtils.buildTransactionFrom;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.ByteString;
import com.hedera.node.app.spi.state.States;
import com.hedera.node.app.spi.state.impl.InMemoryStateImpl;
import com.hedera.node.app.spi.state.impl.RebuiltStateImpl;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.KeyUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
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
class AccountStoreTest {
    private Key key = KeyUtils.A_COMPLEX_KEY;
    private Key payerKey = KeyUtils.A_COMPLEX_KEY;
    private Timestamp consensusTimestamp = Timestamp.newBuilder().setSeconds(1_234_567L).build();
    private AccountID payerAlias = asAliasAccount(ByteString.copyFromUtf8("testAlias"));
    private AccountID payer = asAccount("0.0.3");
    private EntityNum payerNum = EntityNum.fromInt(3);

    @Mock private RebuiltStateImpl aliases;
    @Mock private InMemoryStateImpl accounts;
    @Mock private MerkleAccount account;
    @Mock private States states;
    private AccountStore subject;

    @BeforeEach
    public void setUp() {
        given(states.get(ACCOUNT_STORE)).willReturn(accounts);
        given(states.get(ALIASES_STORE)).willReturn(aliases);
        subject = new AccountStore(states);
    }

    @Test
    void createAccountSigningMetaChecksAccounts() throws DecoderException {
        final var jkey = JKey.mapKey(key);
        final var payerJkey = JKey.mapKey(payerKey);
        given(accounts.get(payerNum)).willReturn(Optional.of(account));
        given(account.getAccountKey()).willReturn(payerJkey);

        final var txn = createAccountTransaction(payer);
        final var meta = subject.createAccountSigningMetadata(txn, Optional.of(jkey), true, payer);

        assertFalse(meta.failed());
        assertEquals(txn, meta.getTxn());
        assertEquals(List.of(payerJkey, jkey), meta.getReqKeys());
    }

    @Test
    void createAccountSigningMetaChecksAlias() throws DecoderException {
        final var jkey = JKey.mapKey(key);
        final var payerJkey = JKey.mapKey(payerKey);
        given(aliases.get(payerAlias.getAlias())).willReturn(Optional.of(payerNum));
        given(accounts.get(payerNum)).willReturn(Optional.of(account));
        given(account.getAccountKey()).willReturn(payerJkey);

        final var txn = createAccountTransaction(payerAlias);
        final var meta =
                subject.createAccountSigningMetadata(txn, Optional.of(jkey), true, payerAlias);

        assertFalse(meta.failed());
        assertEquals(txn, meta.getTxn());
        assertEquals(List.of(payerJkey, jkey), meta.getReqKeys());
    }

    @Test
    void fetchingNonExistingLeafFails() throws DecoderException {
        final var jkey = JKey.mapKey(key);
        given(accounts.get(payerNum)).willReturn(Optional.empty());
        final var txn = createAccountTransaction(payer);
        var meta = subject.createAccountSigningMetadata(txn, Optional.of(jkey), true, payer);

        assertTrue(meta.failed());
        assertEquals(ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID, meta.failureStatus());

        given(aliases.get(payerAlias.getAlias())).willReturn(Optional.empty());
        final var aliasedTxn = createAccountTransaction(payerAlias);

        meta = subject.createAccountSigningMetadata(aliasedTxn, Optional.of(jkey), true, payerAlias);
        assertTrue(meta.failed());
        assertEquals(ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID, meta.failureStatus());
    }

    private Transaction createAccountTransaction(final AccountID payer) {
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
