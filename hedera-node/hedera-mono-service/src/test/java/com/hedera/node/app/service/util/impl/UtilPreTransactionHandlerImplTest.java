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
package com.hedera.node.app.service.util.impl;

import static com.hedera.node.app.Utils.asHederaKey;
import static com.hedera.node.app.service.TestUtils.basicMetaAssertions;
import static com.hedera.services.utils.KeyUtils.A_COMPLEX_KEY;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.SigTransactionMetadata;
import com.hedera.node.app.service.token.impl.AccountStore;
import com.hedera.node.app.service.util.UtilPreTransactionHandler;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.state.States;
import com.hedera.node.app.state.impl.InMemoryStateImpl;
import com.hedera.node.app.state.impl.RebuiltStateImpl;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hederahashgraph.api.proto.java.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class UtilPreTransactionHandlerImplTest {
    @Mock private RebuiltStateImpl aliases;
    @Mock private InMemoryStateImpl accounts;
    @Mock private States states;
    @Mock private MerkleAccount payerAccount;

    private AccountID payer = asAccount("0.0.3");
    private Timestamp consensusTimestamp = Timestamp.newBuilder().setSeconds(1_234_567L).build();
    private static final String ACCOUNTS = "ACCOUNTS";
    private static final String ALIASES = "ALIASES";
    private HederaKey payerKey = asHederaKey(A_COMPLEX_KEY).get();
    private UtilPreTransactionHandler subject;
    private AccountStore store;

    @BeforeEach
    public void setUp() {
        given(states.get(ACCOUNTS)).willReturn(accounts);
        given(states.get(ALIASES)).willReturn(aliases);

        store = new AccountStore(states);
        subject = new UtilPreTransactionHandlerImpl();
    }

    @Test
    void preHandlesUtilPrng() {
        given(accounts.get(payer.getAccountNum())).willReturn(Optional.of(payerAccount));
        given(payerAccount.getAccountKey()).willReturn((JKey) payerKey);

        final var txn = utilPrngTransaction();
        final var expectedMeta = new SigTransactionMetadata(store, txn, payer);

        final var meta = subject.preHandlePrng(txn);

        assertEquals(expectedMeta.getTxn(), meta.getTxn());
        basicMetaAssertions(meta, 1, expectedMeta.failed(), OK);
        assertIterableEquals(List.of(payerKey), meta.getReqKeys());
    }

    @Test
    void failsPreHandlesIfPayerMissing() {
        given(accounts.get(payer.getAccountNum())).willReturn(Optional.empty());

        final var txn = utilPrngTransaction();
        final var expectedMeta = new SigTransactionMetadata(store, txn, payer);

        final var meta = subject.preHandlePrng(txn);

        assertEquals(expectedMeta.getTxn(), meta.getTxn());
        basicMetaAssertions(meta, 0, expectedMeta.failed(), INVALID_PAYER_ACCOUNT_ID);
        assertIterableEquals(List.of(), meta.getReqKeys());
    }

    private TransactionBody utilPrngTransaction() {
        final var transactionID =
                TransactionID.newBuilder()
                        .setAccountID(payer)
                        .setTransactionValidStart(consensusTimestamp);
        final var prngTxnBody = UtilPrngTransactionBody.newBuilder().setRange(10).build();

        return TransactionBody.newBuilder()
                .setTransactionID(transactionID)
                .setUtilPrng(prngTxnBody)
                .build();
    }
}
