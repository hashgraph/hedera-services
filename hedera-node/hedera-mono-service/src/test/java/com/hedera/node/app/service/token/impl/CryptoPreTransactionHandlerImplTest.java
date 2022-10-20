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

import com.google.protobuf.ByteString;
import com.hedera.node.app.spi.meta.impl.InvalidTransactionMetadata;
import com.hedera.node.app.spi.meta.impl.SigTransactionMetadata;
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
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.apache.commons.codec.DecoderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.hedera.node.app.spi.state.StateKey.ACCOUNTS;
import static com.hedera.node.app.spi.state.StateKey.ALIASES;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.TxnUtils.buildTransactionFrom;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CryptoPreTransactionHandlerImplTest {
    private Key key = KeyUtils.A_COMPLEX_KEY;
    private Timestamp consensusTimestamp = Timestamp.newBuilder().setSeconds(1_234_567L).build();
    private AccountID payer = asAccount("0.0.3");
    private EntityNum payerNum = EntityNum.fromInt(3);

    @Mock private RebuiltStateImpl aliases;
    @Mock private InMemoryStateImpl accounts;
    @Mock private States states;
    @Mock private MerkleAccount account;
    private AccountStore store;
    private CryptoPreTransactionHandlerImpl subject;

    @BeforeEach
    public void setUp() {
        given(states.get(ACCOUNTS)).willReturn(accounts);
        given(states.get(ALIASES)).willReturn(aliases);

        store = new AccountStore(states);

        subject = new CryptoPreTransactionHandlerImpl(store);
    }

    @Test
    void preHandlesCryptoCreate() throws DecoderException {
        final var jkey = JKey.mapKey(key);

        given(accounts.get(payerNum)).willReturn(Optional.of(account));
        given(account.getAccountKey()).willReturn(jkey);

        final var txn = createAccountTransaction(true);

        final var meta = subject.cryptoCreate(txn);

        assertEquals(txn, meta.getTxn());
        assertEquals(2, meta.getReqKeys().size());
        assertTrue(meta.getReqKeys().contains(jkey));
        assertEquals(false, meta.failed());
        assertEquals(OK, meta.status());
    }

    @Test
    void preHandlesCryptoCreateFailure() {
        final var txn =
                Transaction.newBuilder()
                        .setSignedTransactionBytes(ByteString.copyFromUtf8("NONSENSE"))
                        .build();
        final var expectedMeta = new InvalidTransactionMetadata(txn, INVALID_TRANSACTION_BODY);
        final var meta = subject.cryptoCreate(txn);

        assertEquals(expectedMeta.getTxn(), meta.getTxn());
        assertEquals(0, meta.getReqKeys().size());
        assertEquals(expectedMeta.failed(), meta.failed());
    }

    @Test
    void noReceiverSigRequiredPreHandleCreate() throws DecoderException {
        final var jkey = JKey.mapKey(key);
        given(accounts.get(payerNum)).willReturn(Optional.of(account));
        given(account.getAccountKey()).willReturn(jkey);

        final var txn = createAccountTransaction(false);
        final var expectedMeta = new SigTransactionMetadata(store, txn, payer);

        final var meta = subject.cryptoCreate(txn);

        assertEquals(expectedMeta.getTxn(), meta.getTxn());
        assertTrue(meta.getReqKeys().contains(jkey));
        assertEquals(expectedMeta.failed(), meta.failed());
    }

    private Transaction createAccountTransaction(final boolean receiverSigReq) {
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
        final var transactionBody =
                TransactionBody.newBuilder()
                        .setTransactionID(transactionID)
                        .setCryptoCreateAccount(createTxnBody)
                        .build();
        return buildTransactionFrom(transactionBody);
    }
}
