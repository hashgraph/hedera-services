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
package com.hedera.node.app.service.mono.token.impl;

import com.hedera.node.app.service.mono.SigTransactionMetadata;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.impl.InMemoryStateImpl;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hedera.node.app.spi.numbers.HederaFileNumbers;
import com.hedera.node.app.spi.state.States;
import com.hederahashgraph.api.proto.java.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.node.app.service.mono.utils.KeyUtils.A_COMPLEX_KEY;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class TokenPreTransactionHandlerImplTest {

    private static final String ACCOUNTS = "ACCOUNTS";
    @Mock private InMemoryStateImpl accounts;
    @Mock private States states;
    @Mock private HederaAccountNumbers accountNumbers;
    @Mock private MerkleAccount payerAccount;
    @Mock private HederaFileNumbers fileNumbers;
    private PreHandleContext context;
    private AccountStore store;
    private TokenPreTransactionHandlerImpl subject;
    private Key key = A_COMPLEX_KEY;
    private HederaKey payerKey = asHederaKey(A_COMPLEX_KEY).get();
    private AccountID payer = asAccount("0.0.3");
    private Timestamp consensusTimestamp = Timestamp.newBuilder().setSeconds(1_234_567L).build();
    private Long payerNum = payer.getAccountNum();

    @BeforeEach
    public void setUp() {
        given(states.get(ACCOUNTS)).willReturn(accounts);

        store = new AccountStore(states);

        context = new PreHandleContext(accountNumbers, fileNumbers);

        subject = new TokenPreTransactionHandlerImpl(store, context);
    }

    @Test
    void preHandleCreateToken() {
        final var txn = createAccountTransaction(true);

        final var meta = subject.preHandleCreateToken(txn);

        assertEquals(txn, meta.getTxn());
        basicMetaAssertions(meta, 1, false, OK);
        assertTrue(meta.getReqKeys().contains(payerKey));
    }

    @Test
    void noReceiverSigRequiredPreHandleCreateToken() {
        final var txn = createAccountTransaction(false);
        final var expectedMeta = new SigTransactionMetadata(store, txn, payer);

        final var meta = subject.preHandleCreateToken(txn);

        assertEquals(expectedMeta.getTxn(), meta.getTxn());
        assertTrue(meta.getReqKeys().contains(payerKey));
        basicMetaAssertions(meta, 1, expectedMeta.failed(), OK);
        assertIterableEquals(List.of(payerKey), meta.getReqKeys());
    }

    private void basicMetaAssertions(
            final TransactionMetadata meta,
            final int keysSize,
            final boolean failed,
            final ResponseCodeEnum failureStatus) {
        assertEquals(keysSize, meta.getReqKeys().size());
        assertTrue(failed ? meta.failed() : !meta.failed());
        assertEquals(failureStatus, meta.status());
    }

    private TransactionBody createAccountTransaction(final boolean receiverSigReq) {
        setUpPayer();

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

    final void setUpPayer() {
        given(accounts.get(payerNum)).willReturn(Optional.of(payerAccount));
        given(payerAccount.getAccountKey()).willReturn((JKey) payerKey);
    }
}
