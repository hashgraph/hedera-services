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

import static com.hedera.node.app.Utils.asHederaKey;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.SigTransactionMetadata;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.state.States;
import com.hedera.node.app.state.impl.InMemoryStateImpl;
import com.hedera.node.app.state.impl.RebuiltStateImpl;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.KeyUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CryptoPreTransactionHandlerImplTest {
    private Key key = KeyUtils.A_COMPLEX_KEY;
    private HederaKey hederaKey = asHederaKey(key).get();
    private Timestamp consensusTimestamp = Timestamp.newBuilder().setSeconds(1_234_567L).build();
    private AccountID payer = asAccount("0.0.3");
    private AccountID deleteAccountId = asAccount("0.0.3213");
    private AccountID transferAccountId = asAccount("0.0.32134");
    private Long payerNum = payer.getAccountNum();
    private Long deleteAccountNum = deleteAccountId.getAccountNum();
    private Long transferAccountNum = transferAccountId.getAccountNum();
    private static final String ACCOUNTS = "ACCOUNTS";
    private static final String ALIASES = "ALIASES";

    @Mock private RebuiltStateImpl aliases;
    @Mock private InMemoryStateImpl accounts;
    @Mock private States states;
    @Mock private MerkleAccount account;
    @Mock private MerkleAccount deleteAccount;
    @Mock private MerkleAccount transferAccount;
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
    void preHandlesCryptoCreate() {
        given(accounts.get(payerNum)).willReturn(Optional.of(account));
        given(account.getAccountKey()).willReturn((JKey) hederaKey);

        final var txn = createAccountTransaction(true);

        final var meta = subject.preHandleCryptoCreate(txn);

        assertEquals(txn, meta.getTxn());
        assertEquals(2, meta.getReqKeys().size());
        assertTrue(meta.getReqKeys().contains(hederaKey));
        assertFalse(meta.failed());
        assertEquals(OK, meta.status());
    }

    @Test
    void noReceiverSigRequiredPreHandleCryptoCreate() {
        given(accounts.get(payerNum)).willReturn(Optional.of(account));
        given(account.getAccountKey()).willReturn((JKey) hederaKey);

        final var txn = createAccountTransaction(false);
        final var expectedMeta = new SigTransactionMetadata(store, txn, payer);

        final var meta = subject.preHandleCryptoCreate(txn);

        assertEquals(expectedMeta.getTxn(), meta.getTxn());
        assertTrue(meta.getReqKeys().contains(hederaKey));
        assertEquals(expectedMeta.failed(), meta.failed());
    }

    @Test
    void preHandlesCryptoDeleteIfNoReceiverSigRequired() {
        final var keyUsed = (JKey) hederaKey;

        given(accounts.get(payerNum)).willReturn(Optional.of(account));
        given(account.getAccountKey()).willReturn(keyUsed);
        given(accounts.get(deleteAccountNum)).willReturn(Optional.of(deleteAccount));
        given(accounts.get(transferAccountNum)).willReturn(Optional.of(transferAccount));
        given(deleteAccount.getAccountKey()).willReturn(keyUsed);
        given(transferAccount.isReceiverSigRequired()).willReturn(false);

        final var txn = deleteAccountTransaction(deleteAccountId, transferAccountId);

        final var meta = subject.preHandleCryptoDelete(txn);

        assertEquals(txn, meta.getTxn());
        assertEquals(2, meta.getReqKeys().size());
        assertTrue(meta.getReqKeys().contains(keyUsed));
        assertFalse(meta.failed());
        assertEquals(OK, meta.status());
    }

    @Test
    void preHandlesCryptoDeleteIfReceiverSigRequired() {
        final var keyUsed = (JKey) hederaKey;

        given(accounts.get(payerNum)).willReturn(Optional.of(account));
        given(account.getAccountKey()).willReturn(keyUsed);
        given(accounts.get(deleteAccountNum)).willReturn(Optional.of(deleteAccount));
        given(accounts.get(transferAccountNum)).willReturn(Optional.of(transferAccount));
        given(deleteAccount.getAccountKey()).willReturn(keyUsed);
        given(transferAccount.getAccountKey()).willReturn(keyUsed);
        given(transferAccount.isReceiverSigRequired()).willReturn(true);

        final var txn = deleteAccountTransaction(deleteAccountId, transferAccountId);

        final var meta = subject.preHandleCryptoDelete(txn);

        assertEquals(txn, meta.getTxn());
        assertEquals(3, meta.getReqKeys().size());
        assertTrue(meta.getReqKeys().contains(keyUsed));
        assertFalse(meta.failed());
        assertEquals(OK, meta.status());
    }

    @Test
    void doesntAddBothKeysAccountsSameAsPayerForCryptoDelete() {
        final var keyUsed = (JKey) hederaKey;

        given(accounts.get(payerNum)).willReturn(Optional.of(account));
        given(account.getAccountKey()).willReturn(keyUsed);

        final var txn = deleteAccountTransaction(payer, payer);

        final var meta = subject.preHandleCryptoDelete(txn);

        assertEquals(txn, meta.getTxn());
        assertEquals(1, meta.getReqKeys().size());
        assertTrue(meta.getReqKeys().contains(keyUsed));
        assertFalse(meta.failed());
        assertEquals(OK, meta.status());
    }

    @Test
    void doesntAddTransferKeyIfAccountSameAsPayerForCryptoDelete() {
        final var keyUsed = (JKey) hederaKey;

        given(accounts.get(payerNum)).willReturn(Optional.of(account));
        given(account.getAccountKey()).willReturn(keyUsed);
        given(accounts.get(deleteAccountNum)).willReturn(Optional.of(deleteAccount));
        given(deleteAccount.getAccountKey()).willReturn(keyUsed);

        final var txn = deleteAccountTransaction(deleteAccountId, payer);

        final var meta = subject.preHandleCryptoDelete(txn);

        assertEquals(txn, meta.getTxn());
        assertEquals(2, meta.getReqKeys().size());
        assertTrue(meta.getReqKeys().contains(keyUsed));
        assertFalse(meta.failed());
        assertEquals(OK, meta.status());
    }

    @Test
    void doesntAddDeleteKeyIfAccountSameAsPayerForCryptoDelete() {
        final var keyUsed = (JKey) hederaKey;

        given(accounts.get(payerNum)).willReturn(Optional.of(account));
        given(account.getAccountKey()).willReturn(keyUsed);
        given(accounts.get(transferAccountNum)).willReturn(Optional.of(transferAccount));
        given(transferAccount.getAccountKey()).willReturn(keyUsed);
        given(transferAccount.isReceiverSigRequired()).willReturn(true);

        final var txn = deleteAccountTransaction(payer, transferAccountId);

        final var meta = subject.preHandleCryptoDelete(txn);

        assertEquals(txn, meta.getTxn());
        assertEquals(2, meta.getReqKeys().size());
        assertTrue(meta.getReqKeys().contains(keyUsed));
        assertFalse(meta.failed());
        assertEquals(OK, meta.status());
    }

    @Test
    void failsWithResponseCodeIfAnyAccountMissingForCryptoDelete() {
        final var keyUsed = (JKey) hederaKey;

        /* ------ payerAccount missing, so deleteAccount and transferAccount will not be added  ------ */
        given(accounts.get(payerNum)).willReturn(Optional.empty());
        given(accounts.get(deleteAccountNum)).willReturn(Optional.of(deleteAccount));
        given(accounts.get(transferAccountNum)).willReturn(Optional.of(transferAccount));
        given(deleteAccount.getAccountKey()).willReturn(keyUsed);
        var txn = deleteAccountTransaction(deleteAccountId, transferAccountId);

        var meta = subject.preHandleCryptoDelete(txn);
        assertEquals(0, meta.getReqKeys().size());
        assertTrue(meta.failed());
        assertEquals(INVALID_PAYER_ACCOUNT_ID, meta.status());

        /* ------ deleteAccount missing, so transferAccount will not be added ------ */
        given(accounts.get(payerNum)).willReturn(Optional.of(account));
        given(account.getAccountKey()).willReturn(keyUsed);
        given(accounts.get(deleteAccountNum)).willReturn(Optional.empty());
        given(accounts.get(transferAccountNum)).willReturn(Optional.of(transferAccount));

        meta = subject.preHandleCryptoDelete(txn);

        assertEquals(1, meta.getReqKeys().size());
        assertTrue(meta.failed());
        assertEquals(INVALID_ACCOUNT_ID, meta.status());

        /* ------ transferAccount missing ------ */
        given(accounts.get(deleteAccountNum)).willReturn(Optional.of(deleteAccount));
        given(deleteAccount.getAccountKey()).willReturn(keyUsed);
        given(accounts.get(transferAccountNum)).willReturn(Optional.empty());

        meta = subject.preHandleCryptoDelete(txn);

        assertEquals(2, meta.getReqKeys().size());
        assertTrue(meta.failed());
        assertEquals(INVALID_ACCOUNT_ID, meta.status());
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

    private TransactionBody deleteAccountTransaction(
            final AccountID deleteAccountId, final AccountID transferAccountId) {
        final var transactionID =
                TransactionID.newBuilder()
                        .setAccountID(payer)
                        .setTransactionValidStart(consensusTimestamp);
        final var deleteTxBody =
                CryptoDeleteTransactionBody.newBuilder()
                        .setDeleteAccountID(deleteAccountId)
                        .setTransferAccountID(transferAccountId);

        return TransactionBody.newBuilder()
                .setTransactionID(transactionID)
                .setCryptoDelete(deleteTxBody)
                .build();
    }
}
