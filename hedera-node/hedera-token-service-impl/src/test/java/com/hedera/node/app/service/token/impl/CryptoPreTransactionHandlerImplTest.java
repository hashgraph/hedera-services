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

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_DELEGATING_SPENDER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import com.google.protobuf.BoolValue;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.impl.InMemoryStateImpl;
import com.hedera.node.app.service.mono.state.impl.RebuiltStateImpl;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.SigTransactionMetadataBuilder;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hedera.node.app.spi.numbers.HederaFileNumbers;
import com.hedera.node.app.spi.state.States;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CryptoPreTransactionHandlerImplTest {
    private final Key key = A_COMPLEX_KEY;
    private final HederaKey hederaKey = asHederaKey(key).get();
    private final HederaKey payerKey = asHederaKey(A_COMPLEX_KEY).get();
    private final HederaKey ownerKey = asHederaKey(A_COMPLEX_KEY).get();
    private final HederaKey updateAccountKey = asHederaKey(A_COMPLEX_KEY).get();
    private final Timestamp consensusTimestamp =
            Timestamp.newBuilder().setSeconds(1_234_567L).build();
    private final AccountID payer = asAccount("0.0.3");
    private final AccountID deleteAccountId = asAccount("0.0.3213");
    private final AccountID transferAccountId = asAccount("0.0.32134");
    private final AccountID updateAccountId = asAccount("0.0.32132");
    private final AccountID spender = asAccount("0.0.12345");
    private final AccountID delegatingSpender = asAccount("0.0.1234567");
    private final AccountID owner = asAccount("0.0.123456");
    private final TokenID token = asToken("0.0.6789");
    private final TokenID nft = asToken("0.0.56789");
    private final Long payerNum = payer.getAccountNum();
    private final Long deleteAccountNum = deleteAccountId.getAccountNum();
    private final Long transferAccountNum = transferAccountId.getAccountNum();

    private final CryptoAllowance cryptoAllowance =
            CryptoAllowance.newBuilder().setSpender(spender).setOwner(owner).setAmount(10L).build();
    private final TokenAllowance tokenAllowance =
            TokenAllowance.newBuilder()
                    .setSpender(spender)
                    .setAmount(10L)
                    .setTokenId(token)
                    .setOwner(owner)
                    .build();
    private final NftAllowance nftAllowance =
            NftAllowance.newBuilder()
                    .setSpender(spender)
                    .setOwner(owner)
                    .setTokenId(nft)
                    .setApprovedForAll(BoolValue.of(true))
                    .addAllSerialNumbers(List.of(1L, 2L))
                    .build();
    private final NftAllowance nftAllowanceWithDelegatingSpender =
            NftAllowance.newBuilder()
                    .setSpender(spender)
                    .setOwner(owner)
                    .setTokenId(nft)
                    .setApprovedForAll(BoolValue.of(false))
                    .addAllSerialNumbers(List.of(1L, 2L))
                    .setDelegatingSpender(delegatingSpender)
                    .build();
    private static final String ACCOUNTS = "ACCOUNTS";
    private static final String ALIASES = "ALIASES";

    @Mock private RebuiltStateImpl aliases;
    @Mock private InMemoryStateImpl accounts;
    @Mock private States states;
    @Mock private MerkleAccount payerAccount;
    @Mock private MerkleAccount deleteAccount;
    @Mock private MerkleAccount transferAccount;
    @Mock private MerkleAccount updateAccount;
    @Mock private MerkleAccount ownerAccount;
    @Mock private HederaAccountNumbers accountNumbers;
    @Mock private HederaFileNumbers fileNumbers;
    @Mock private CryptoSignatureWaiversImpl waivers;
    private PreHandleContext context;
    private ReadableAccountStore store;
    private CryptoPreTransactionHandlerImpl subject;

    @BeforeEach
    public void setUp() {
        given(states.get(ACCOUNTS)).willReturn(accounts);
        given(states.get(ALIASES)).willReturn(aliases);

        store = new ReadableAccountStore(states);

        context = new PreHandleContext(accountNumbers, fileNumbers, store);

        subject = new CryptoPreTransactionHandlerImpl(store, context);

        subject.setWaivers(waivers);
    }

    @Test
    void preHandleCryptoCreateVanilla() {
        final var txn = createAccountTransaction(true);

        final var meta = subject.preHandleCryptoCreate(txn, payer);

        assertEquals(txn, meta.txnBody());
        basicMetaAssertions(meta, 1, false, OK);
        assertEquals(payerKey, meta.payerKey());
    }

    @Test
    void noReceiverSigRequiredPreHandleCryptoCreate() {
        final var txn = createAccountTransaction(false);
        final var expectedMeta =
                new SigTransactionMetadataBuilder(store).payerKeyFor(payer).txnBody(txn).build();

        final var meta = subject.preHandleCryptoCreate(txn, payer);

        assertEquals(expectedMeta.txnBody(), meta.txnBody());
        assertFalse(meta.requiredNonPayerKeys().contains(payerKey));
        basicMetaAssertions(meta, 0, expectedMeta.failed(), OK);
        assertIterableEquals(List.of(), meta.requiredNonPayerKeys());
        assertEquals(payerKey, meta.payerKey());
    }

    @Test
    void preHandlesCryptoDeleteIfNoReceiverSigRequired() {
        final var keyUsed = (JKey) payerKey;

        given(accounts.get(deleteAccountNum)).willReturn(Optional.of(deleteAccount));
        given(accounts.get(transferAccountNum)).willReturn(Optional.of(transferAccount));
        given(deleteAccount.getAccountKey()).willReturn(keyUsed);
        given(transferAccount.isReceiverSigRequired()).willReturn(false);

        final var txn = deleteAccountTransaction(deleteAccountId, transferAccountId);

        final var meta = subject.preHandleCryptoDelete(txn, payer);

        assertEquals(txn, meta.txnBody());
        assertEquals(payerKey, meta.payerKey());
        basicMetaAssertions(meta, 1, false, OK);
        assertIterableEquals(List.of(keyUsed), meta.requiredNonPayerKeys());
    }

    @Test
    void preHandlesCryptoDeleteIfReceiverSigRequiredVanilla() {
        final var keyUsed = (JKey) payerKey;

        given(accounts.get(deleteAccountNum)).willReturn(Optional.of(deleteAccount));
        given(accounts.get(transferAccountNum)).willReturn(Optional.of(transferAccount));
        given(deleteAccount.getAccountKey()).willReturn(keyUsed);
        given(transferAccount.getAccountKey()).willReturn(keyUsed);
        given(transferAccount.isReceiverSigRequired()).willReturn(true);

        final var txn = deleteAccountTransaction(deleteAccountId, transferAccountId);

        final var meta = subject.preHandleCryptoDelete(txn, payer);

        assertEquals(txn, meta.txnBody());
        basicMetaAssertions(meta, 2, false, OK);
        assertEquals(payerKey, meta.payerKey());
        assertIterableEquals(List.of(keyUsed, keyUsed), meta.requiredNonPayerKeys());
    }

    @Test
    void doesntAddBothKeysAccountsSameAsPayerForCryptoDelete() {
        final var txn = deleteAccountTransaction(payer, payer);

        final var meta = subject.preHandleCryptoDelete(txn, payer);

        assertEquals(txn, meta.txnBody());
        basicMetaAssertions(meta, 0, false, OK);
        assertEquals(payerKey, meta.payerKey());
        assertIterableEquals(List.of(), meta.requiredNonPayerKeys());
    }

    @Test
    void doesntAddTransferKeyIfAccountSameAsPayerForCryptoDelete() {
        final var keyUsed = (JKey) payerKey;

        given(accounts.get(deleteAccountNum)).willReturn(Optional.of(deleteAccount));
        given(deleteAccount.getAccountKey()).willReturn(keyUsed);

        final var txn = deleteAccountTransaction(deleteAccountId, payer);

        final var meta = subject.preHandleCryptoDelete(txn, payer);

        assertEquals(txn, meta.txnBody());
        assertEquals(payerKey, meta.payerKey());
        basicMetaAssertions(meta, 1, false, OK);
        assertIterableEquals(List.of(keyUsed), meta.requiredNonPayerKeys());
    }

    @Test
    void doesntExecuteIfAccountIdIsDefaultInstance() {
        final var keyUsed = (JKey) payerKey;

        given(accounts.get(deleteAccountNum)).willReturn(Optional.of(deleteAccount));
        given(deleteAccount.getAccountKey()).willReturn(keyUsed);

        final var txn = deleteAccountTransaction(deleteAccountId, AccountID.getDefaultInstance());

        final var meta = subject.preHandleCryptoDelete(txn, payer);

        assertEquals(txn, meta.txnBody());
        basicMetaAssertions(meta, 1, false, OK);
        assertEquals(payerKey, meta.payerKey());
        assertIterableEquals(List.of(keyUsed), meta.requiredNonPayerKeys());
    }

    @Test
    void doesntAddDeleteKeyIfAccountSameAsPayerForCryptoDelete() {
        final var keyUsed = (JKey) payerKey;

        given(accounts.get(transferAccountNum)).willReturn(Optional.of(transferAccount));
        given(transferAccount.getAccountKey()).willReturn(keyUsed);
        given(transferAccount.isReceiverSigRequired()).willReturn(true);

        final var txn = deleteAccountTransaction(payer, transferAccountId);

        final var meta = subject.preHandleCryptoDelete(txn, payer);

        assertEquals(txn, meta.txnBody());
        basicMetaAssertions(meta, 1, false, OK);
        assertEquals(payerKey, meta.payerKey());
        assertIterableEquals(List.of(keyUsed), meta.requiredNonPayerKeys());
    }

    @Test
    void failsWithResponseCodeIfAnyAccountMissingForCryptoDelete() {
        final var keyUsed = (JKey) payerKey;

        /* ------ payerAccount missing, so deleteAccount and transferAccount will not be added  ------ */
        final var txn = deleteAccountTransaction(deleteAccountId, transferAccountId);
        given(accounts.get(payerNum)).willReturn(Optional.empty());
        given(accounts.get(deleteAccountNum)).willReturn(Optional.of(deleteAccount));
        given(accounts.get(transferAccountNum)).willReturn(Optional.of(transferAccount));
        given(deleteAccount.getAccountKey()).willReturn(keyUsed);

        var meta = subject.preHandleCryptoDelete(txn, payer);
        basicMetaAssertions(meta, 0, true, INVALID_PAYER_ACCOUNT_ID);
        assertNull(meta.payerKey());
        assertIterableEquals(List.of(), meta.requiredNonPayerKeys());

        /* ------ deleteAccount missing, so transferAccount will not be added ------ */
        given(accounts.get(payerNum)).willReturn(Optional.of(payerAccount));
        given(payerAccount.getAccountKey()).willReturn(keyUsed);
        given(accounts.get(deleteAccountNum)).willReturn(Optional.empty());
        given(accounts.get(transferAccountNum)).willReturn(Optional.of(transferAccount));

        meta = subject.preHandleCryptoDelete(txn, payer);

        basicMetaAssertions(meta, 0, true, INVALID_ACCOUNT_ID);
        assertEquals(payerKey, meta.payerKey());
        assertIterableEquals(List.of(), meta.requiredNonPayerKeys());

        /* ------ transferAccount missing ------ */
        given(accounts.get(deleteAccountNum)).willReturn(Optional.of(deleteAccount));
        given(deleteAccount.getAccountKey()).willReturn(keyUsed);
        given(accounts.get(transferAccountNum)).willReturn(Optional.empty());

        meta = subject.preHandleCryptoDelete(txn, payer);

        basicMetaAssertions(meta, 1, true, INVALID_TRANSFER_ACCOUNT_ID);
        assertEquals(payerKey, meta.payerKey());
        assertIterableEquals(List.of(keyUsed), meta.requiredNonPayerKeys());
    }

    @Test
    void cryptoApproveAllowanceVanilla() {
        given(accounts.get(owner.getAccountNum())).willReturn(Optional.of(ownerAccount));
        given(ownerAccount.getAccountKey()).willReturn((JKey) ownerKey);

        final var txn = cryptoApproveAllowanceTransaction(payer, false);
        final var meta = subject.preHandleApproveAllowances(txn, payer);
        basicMetaAssertions(meta, 3, false, OK);
        assertEquals(payerKey, meta.payerKey());
        assertIterableEquals(List.of(ownerKey, ownerKey, ownerKey), meta.requiredNonPayerKeys());
    }

    @Test
    void cryptoApproveAllowanceFailsWithInvalidOwner() {
        given(accounts.get(owner.getAccountNum())).willReturn(Optional.empty());

        final var txn = cryptoApproveAllowanceTransaction(payer, false);
        final var meta = subject.preHandleApproveAllowances(txn, payer);
        basicMetaAssertions(meta, 0, true, INVALID_ALLOWANCE_OWNER_ID);
        assertEquals(payerKey, meta.payerKey());
        assertIterableEquals(List.of(), meta.requiredNonPayerKeys());
    }

    @Test
    void cryptoApproveAllowanceDoesntAddIfOwnerSameAsPayer() {
        given(accounts.get(owner.getAccountNum())).willReturn(Optional.of(ownerAccount));
        given(ownerAccount.getAccountKey()).willReturn((JKey) ownerKey);

        final var txn = cryptoApproveAllowanceTransaction(owner, false);
        final var meta = subject.preHandleApproveAllowances(txn, owner);
        basicMetaAssertions(meta, 0, false, OK);
        assertEquals(ownerKey, meta.payerKey());
        assertIterableEquals(List.of(), meta.requiredNonPayerKeys());
    }

    @Test
    void cryptoApproveAllowanceAddsDelegatingSpender() {
        given(accounts.get(owner.getAccountNum())).willReturn(Optional.of(ownerAccount));
        given(ownerAccount.getAccountKey()).willReturn((JKey) ownerKey);
        given(accounts.get(delegatingSpender.getAccountNum()))
                .willReturn(Optional.of(payerAccount));

        final var txn = cryptoApproveAllowanceTransaction(payer, true);
        final var meta = subject.preHandleApproveAllowances(txn, payer);
        basicMetaAssertions(meta, 3, false, OK);
        assertEquals(payerKey, meta.payerKey());
        assertIterableEquals(List.of(ownerKey, ownerKey, payerKey), meta.requiredNonPayerKeys());
    }

    @Test
    void cryptoApproveAllowanceFailsIfDelegatingSpenderMissing() {
        given(accounts.get(owner.getAccountNum())).willReturn(Optional.of(ownerAccount));
        given(ownerAccount.getAccountKey()).willReturn((JKey) ownerKey);
        given(accounts.get(delegatingSpender.getAccountNum())).willReturn(Optional.empty());

        final var txn = cryptoApproveAllowanceTransaction(payer, true);
        final var meta = subject.preHandleApproveAllowances(txn, payer);
        assertEquals(payerKey, meta.payerKey());
        basicMetaAssertions(meta, 2, true, INVALID_DELEGATING_SPENDER);
        assertIterableEquals(List.of(ownerKey, ownerKey), meta.requiredNonPayerKeys());
    }

    @Test
    void cryptoDeleteAllowanceVanilla() {
        given(accounts.get(owner.getAccountNum())).willReturn(Optional.of(ownerAccount));
        given(ownerAccount.getAccountKey()).willReturn((JKey) ownerKey);

        final var txn = cryptoDeleteAllowanceTransaction(payer);
        final var meta = subject.preHandleDeleteAllowances(txn, payer);
        basicMetaAssertions(meta, 1, false, OK);
        assertEquals(payerKey, meta.payerKey());
        assertIterableEquals(List.of(ownerKey), meta.requiredNonPayerKeys());
    }

    @Test
    void cryptoDeleteAllowanceDoesntAddIfOwnerSameAsPayer() {
        given(accounts.get(owner.getAccountNum())).willReturn(Optional.of(ownerAccount));
        given(ownerAccount.getAccountKey()).willReturn((JKey) ownerKey);

        final var txn = cryptoDeleteAllowanceTransaction(owner);
        final var meta = subject.preHandleDeleteAllowances(txn, owner);
        basicMetaAssertions(meta, 0, false, OK);
        assertEquals(ownerKey, meta.payerKey());
        assertIterableEquals(List.of(), meta.requiredNonPayerKeys());
    }

    @Test
    void cryptoDeleteAllowanceFailsIfPayerOrOwnerNotExist() {
        var txn = cryptoDeleteAllowanceTransaction(owner);
        given(accounts.get(owner.getAccountNum())).willReturn(Optional.empty());

        var meta = subject.preHandleDeleteAllowances(txn, owner);
        basicMetaAssertions(meta, 0, true, INVALID_PAYER_ACCOUNT_ID);
        assertNull(meta.payerKey());
        assertIterableEquals(List.of(), meta.requiredNonPayerKeys());

        txn = cryptoDeleteAllowanceTransaction(payer);
        meta = subject.preHandleDeleteAllowances(txn, payer);
        basicMetaAssertions(meta, 0, true, INVALID_ALLOWANCE_OWNER_ID);
        assertEquals(payerKey, meta.payerKey());
        assertIterableEquals(List.of(), meta.requiredNonPayerKeys());
    }

    @Test
    void cryptoUpdateVanilla() {
        final var txn = cryptoUpdateTransaction(payer, updateAccountId);
        given(accounts.get(updateAccountId.getAccountNum())).willReturn(Optional.of(updateAccount));
        given(updateAccount.getAccountKey()).willReturn((JKey) updateAccountKey);
        given(waivers.isNewKeySignatureWaived(txn, payer)).willReturn(false);
        given(waivers.isTargetAccountSignatureWaived(txn, payer)).willReturn(false);

        final var meta = subject.preHandleUpdateAccount(txn, payer);
        basicMetaAssertions(meta, 2, false, OK);
        assertEquals(payerKey, meta.payerKey());
        assertTrue(meta.requiredNonPayerKeys().contains(updateAccountKey));
    }

    @Test
    void cryptoUpdateNewSignatureKeyWaivedVanilla() {
        final var txn = cryptoUpdateTransaction(payer, updateAccountId);
        given(accounts.get(updateAccountId.getAccountNum())).willReturn(Optional.of(updateAccount));
        given(updateAccount.getAccountKey()).willReturn((JKey) updateAccountKey);
        given(waivers.isNewKeySignatureWaived(txn, payer)).willReturn(true);
        given(waivers.isTargetAccountSignatureWaived(txn, payer)).willReturn(false);

        final var meta = subject.preHandleUpdateAccount(txn, payer);
        basicMetaAssertions(meta, 1, false, OK);
        assertEquals(payerKey, meta.payerKey());
        assertIterableEquals(List.of(updateAccountKey), meta.requiredNonPayerKeys());
    }

    @Test
    void cryptoUpdateTargetSignatureKeyWaivedVanilla() {
        final var txn = cryptoUpdateTransaction(payer, updateAccountId);
        given(waivers.isNewKeySignatureWaived(txn, payer)).willReturn(false);
        given(waivers.isTargetAccountSignatureWaived(txn, payer)).willReturn(true);

        final var meta = subject.preHandleUpdateAccount(txn, payer);
        basicMetaAssertions(meta, 1, false, OK);
        assertEquals(payerKey, meta.payerKey());
        assertFalse(meta.requiredNonPayerKeys().contains(updateAccountKey));
    }

    @Test
    void cryptoUpdatePayerMissingFails() {
        final var txn = cryptoUpdateTransaction(updateAccountId, updateAccountId);
        given(accounts.get(updateAccountId.getAccountNum())).willReturn(Optional.empty());

        given(waivers.isNewKeySignatureWaived(txn, updateAccountId)).willReturn(false);
        given(waivers.isTargetAccountSignatureWaived(txn, updateAccountId)).willReturn(true);

        final var meta = subject.preHandleUpdateAccount(txn, updateAccountId);
        basicMetaAssertions(meta, 0, true, INVALID_PAYER_ACCOUNT_ID);
        assertNull(meta.payerKey());
    }

    @Test
    void cryptoUpdatePayerMissingFailsWhenNoOtherSigsRequired() {
        final var txn = cryptoUpdateTransaction(updateAccountId, updateAccountId);
        given(accounts.get(updateAccountId.getAccountNum())).willReturn(Optional.empty());

        given(waivers.isNewKeySignatureWaived(txn, updateAccountId)).willReturn(true);
        given(waivers.isTargetAccountSignatureWaived(txn, updateAccountId)).willReturn(true);

        final var meta = subject.preHandleUpdateAccount(txn, updateAccountId);
        basicMetaAssertions(meta, 0, true, INVALID_PAYER_ACCOUNT_ID);
        assertNull(meta.payerKey());
    }

    @Test
    void cryptoUpdateUpdateAccountMissingFails() {
        final var txn = cryptoUpdateTransaction(payer, updateAccountId);
        given(accounts.get(updateAccountId.getAccountNum())).willReturn(Optional.empty());

        given(waivers.isNewKeySignatureWaived(txn, payer)).willReturn(true);
        given(waivers.isTargetAccountSignatureWaived(txn, payer)).willReturn(false);

        final var meta = subject.preHandleUpdateAccount(txn, payer);
        basicMetaAssertions(meta, 0, true, INVALID_ACCOUNT_ID);
        assertEquals(payerKey, meta.payerKey());
    }

    private void basicMetaAssertions(
            final TransactionMetadata meta,
            final int keysSize,
            final boolean failed,
            final ResponseCodeEnum failureStatus) {
        assertEquals(keysSize, meta.requiredNonPayerKeys().size());
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

    private TransactionBody deleteAccountTransaction(
            final AccountID deleteAccountId, final AccountID transferAccountId) {
        setUpPayer();

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

    private TransactionBody cryptoApproveAllowanceTransaction(
            final AccountID id, final boolean isWithDelegatingSpender) {
        if (id.equals(payer)) {
            setUpPayer();
        }

        final var transactionID =
                TransactionID.newBuilder()
                        .setAccountID(id)
                        .setTransactionValidStart(consensusTimestamp);
        final var allowanceTxnBody =
                CryptoApproveAllowanceTransactionBody.newBuilder()
                        .addCryptoAllowances(cryptoAllowance)
                        .addTokenAllowances(tokenAllowance)
                        .addNftAllowances(
                                isWithDelegatingSpender
                                        ? nftAllowanceWithDelegatingSpender
                                        : nftAllowance)
                        .build();
        return TransactionBody.newBuilder()
                .setTransactionID(transactionID)
                .setCryptoApproveAllowance(allowanceTxnBody)
                .build();
    }

    private TransactionBody cryptoUpdateTransaction(
            final AccountID payerId, final AccountID accountToUpdate) {
        if (payerId.equals(payer)) {
            setUpPayer();
        }
        final var transactionID =
                TransactionID.newBuilder()
                        .setAccountID(payerId)
                        .setTransactionValidStart(consensusTimestamp);
        final var updateTxnBody =
                CryptoUpdateTransactionBody.newBuilder()
                        .setAccountIDToUpdate(accountToUpdate)
                        .setKey(key)
                        .build();
        return TransactionBody.newBuilder()
                .setTransactionID(transactionID)
                .setCryptoUpdateAccount(updateTxnBody)
                .build();
    }

    private TransactionBody cryptoDeleteAllowanceTransaction(final AccountID id) {
        if (id.equals(payer)) {
            setUpPayer();
        }
        final var transactionID =
                TransactionID.newBuilder()
                        .setAccountID(id)
                        .setTransactionValidStart(consensusTimestamp);
        final var allowanceTxnBody =
                CryptoDeleteAllowanceTransactionBody.newBuilder()
                        .addNftAllowances(
                                NftRemoveAllowance.newBuilder()
                                        .setOwner(owner)
                                        .setTokenId(nft)
                                        .addAllSerialNumbers(List.of(1L, 2L, 3L))
                                        .build())
                        .build();
        return TransactionBody.newBuilder()
                .setTransactionID(transactionID)
                .setCryptoDeleteAllowance(allowanceTxnBody)
                .build();
    }

    final void setUpPayer() {
        lenient().when(accounts.get(payerNum)).thenReturn(Optional.of(payerAccount));
        lenient().when(payerAccount.getAccountKey()).thenReturn((JKey) payerKey);
    }
}
