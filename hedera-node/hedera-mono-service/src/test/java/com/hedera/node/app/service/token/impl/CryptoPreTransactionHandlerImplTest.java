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

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

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
import java.util.Optional;
import org.apache.commons.codec.DecoderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CryptoPreTransactionHandlerImplTest {
    private Key key = KeyUtils.A_COMPLEX_KEY;
    private Timestamp consensusTimestamp = Timestamp.newBuilder().setSeconds(1_234_567L).build();
    private AccountID payer = asAccount("0.0.3");
    private Long payerNum = 3L;
    private static final String ACCOUNTS = "ACCOUNTS";
    private static final String ALIASES = "ALIASES";

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

        final var meta = subject.preHandleCryptoCreate(txn);

        assertEquals(txn, meta.getTxn());
        assertEquals(2, meta.getReqKeys().size());
        assertTrue(meta.getReqKeys().contains(jkey));
        assertFalse(meta.failed());
        assertEquals(OK, meta.status());
    }

    @Test
    void noReceiverSigRequiredPreHandleCreate() throws DecoderException {
        final var jkey = JKey.mapKey(key);
        given(accounts.get(payerNum)).willReturn(Optional.of(account));
        given(account.getAccountKey()).willReturn(jkey);

        final var txn = createAccountTransaction(false);
        final var expectedMeta = new SigTransactionMetadata(store, txn, payer);

        final var meta = subject.preHandleCryptoCreate(txn);

        assertEquals(expectedMeta.getTxn(), meta.getTxn());
        assertTrue(meta.getReqKeys().contains(jkey));
        assertEquals(expectedMeta.failed(), meta.failed());
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
