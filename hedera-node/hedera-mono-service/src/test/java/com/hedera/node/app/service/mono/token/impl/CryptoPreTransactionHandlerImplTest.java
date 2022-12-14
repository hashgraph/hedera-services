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

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.node.app.service.mono.utils.KeyUtils.A_COMPLEX_KEY;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asAliasAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_DELEGATING_SPENDER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.JRSA_3072Key;
import com.hedera.node.app.service.mono.state.impl.InMemoryStateImpl;
import com.hedera.node.app.service.mono.state.impl.RebuiltStateImpl;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.SigTransactionMetadataBuilder;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hedera.node.app.spi.numbers.HederaFileNumbers;
import com.hedera.node.app.spi.state.States;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransferList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CryptoPreTransactionHandlerImplTest {
//    private final Key key = A_COMPLEX_KEY;
//    private final HederaKey emptyKey = new JRSA_3072Key(null);
//    private final HederaKey payerKey = asHederaKey(A_COMPLEX_KEY).get();
//    private final HederaKey ownerKey = asHederaKey(A_COMPLEX_KEY).get();
//    private final HederaKey updateAccountKey = asHederaKey(A_COMPLEX_KEY).get();
//    private final HederaKey randomKey = asHederaKey(A_COMPLEX_KEY).get();
//    private final HederaKey genericKey = asHederaKey(A_COMPLEX_KEY).get();
//    private final Timestamp consensusTimestamp =
//            Timestamp.newBuilder().setSeconds(1_234_567L).build();
//    private final AccountID payer = asAccount("0.0.3");
//    private final AccountID deleteAccountId = asAccount("0.0.3213");
//    private final AccountID transferAccountId = asAccount("0.0.32134");
//    private final AccountID updateAccountId = asAccount("0.0.32132");
//    private final AccountID spender = asAccount("0.0.12345");
//    private final AccountID delegatingSpender = asAccount("0.0.1234567");
//    private final AccountID nftSenderAccountId = asAccount("0.0.11");
//    private final AccountID nftReceiverAccountId = asAccount("0.0.22");
//    private final AccountID cryptoTransferSenderAccountId = asAccount("0.0.33");
//    private final AccountID randomAccountId = asAccount("0.0.44");
//    private final AccountID aliasedAccountId = asAliasAccount(ByteString.copyFromUtf8("test"));
//    private final AccountID owner = asAccount("0.0.123456");
//    private final TokenID token = asToken("0.0.6789");
//    private final TokenID nft = asToken("0.0.56789");
//    private final Long payerNum = payer.getAccountNum();
//    private final Long deleteAccountNum = deleteAccountId.getAccountNum();
//    private final Long transferAccountNum = transferAccountId.getAccountNum();
//
//    private final CryptoAllowance cryptoAllowance =
//            CryptoAllowance.newBuilder().setSpender(spender).setOwner(owner).setAmount(10L).build();
//    private final TokenAllowance tokenAllowance =
//            TokenAllowance.newBuilder()
//                    .setSpender(spender)
//                    .setAmount(10L)
//                    .setTokenId(token)
//                    .setOwner(owner)
//                    .build();
//    private final NftAllowance nftAllowance =
//            NftAllowance.newBuilder()
//                    .setSpender(spender)
//                    .setOwner(owner)
//                    .setTokenId(nft)
//                    .setApprovedForAll(BoolValue.of(true))
//                    .addAllSerialNumbers(List.of(1L, 2L))
//                    .build();
//    private final NftAllowance nftAllowanceWithDelegatingSpender =
//            NftAllowance.newBuilder()
//                    .setSpender(spender)
//                    .setOwner(owner)
//                    .setTokenId(nft)
//                    .setApprovedForAll(BoolValue.of(false))
//                    .addAllSerialNumbers(List.of(1L, 2L))
//                    .setDelegatingSpender(delegatingSpender)
//                    .build();
//    private static final String ACCOUNTS = "ACCOUNTS";
//    private static final String ALIASES = "ALIASES";
//    private static final String TOKENS = "TOKENS";
//
//    @Mock private RebuiltStateImpl aliases;
//    @Mock private InMemoryStateImpl accounts;
//    @Mock private States states;
//    @Mock private MerkleAccount payerAccount;
//    @Mock private MerkleAccount deleteAccount;
//    @Mock private MerkleAccount transferAccount;
//    @Mock private MerkleAccount updateAccount;
//    @Mock private MerkleAccount ownerAccount;
//    @Mock private MerkleAccount nftSenderAccount;
//    @Mock private MerkleAccount nftReceiverAccount;
//    @Mock private MerkleAccount cryptoTransferSenderAccount;
//    @Mock private MerkleAccount randomAccount;
//    @Mock private HederaAccountNumbers accountNumbers;
//    @Mock private HederaFileNumbers fileNumbers;
//    @Mock private CryptoSignatureWaiversImpl waivers;
//    @Mock private TokenStore tokenStore;
//    private PreHandleContext context;
//    private AccountStore accountStore;
//    private CryptoPreTransactionHandlerImpl subject;
//
//    @BeforeEach
//    public void setUp() {
//        given(states.get(ACCOUNTS)).willReturn(accounts);
//        given(states.get(ALIASES)).willReturn(aliases);
//
//        store = new AccountStore(states);
//
//        context = new PreHandleContext(accountNumbers, fileNumbers, store);
//
//        subject = new CryptoPreTransactionHandlerImpl(accountStore, tokenStore, context);
//
//        subject.setWaivers(waivers);
//    }
//
//    @Test
//    void preHandleCryptoCreateVanilla() {
//        final var txn = createAccountTransaction(true);
//
//        final var meta = subject.preHandleCryptoCreate(txn, payer);
//
//        assertEquals(txn, meta.txnBody());
//        basicMetadataAssertions(meta, 1, false, OK);
//        assertEquals(payerKey, meta.payerKey());
//    }
//
//    @Test
//    void noReceiverSigRequiredPreHandleCryptoCreate() {
//        final var txn = createAccountTransaction(false);
//        final var expectedMeta =
//                new SigTransactionMetadataBuilder(store).payerKeyFor(payer).txnBody(txn).build();
//
//        final var meta = subject.preHandleCryptoCreate(txn, payer);
//
//        assertEquals(expectedMeta.txnBody(), meta.txnBody());
//        assertFalse(meta.requiredNonPayerKeys().contains(payerKey));
//        basicMetadataAssertions(meta, 0, expectedMeta.failed(), OK);
//        assertIterableEquals(List.of(), meta.requiredNonPayerKeys());
//        assertEquals(payerKey, meta.payerKey());
//    }
//
//    @Test
//    void preHandlesCryptoDeleteIfNoReceiverSigRequired() {
//        final var keyUsed = (JKey) payerKey;
//
//        given(accounts.get(deleteAccountNum)).willReturn(Optional.of(deleteAccount));
//        given(accounts.get(transferAccountNum)).willReturn(Optional.of(transferAccount));
//        given(transferAccount.getAccountKey()).willReturn(keyUsed);
//        given(deleteAccount.getAccountKey()).willReturn(keyUsed);
//        given(transferAccount.isReceiverSigRequired()).willReturn(false);
//
//        final var txn = deleteAccountTransaction(deleteAccountId, transferAccountId);
//
//        final var meta = subject.preHandleCryptoDelete(txn, payer);
//
//        assertEquals(txn, meta.txnBody());
//        assertEquals(payerKey, meta.payerKey());
//        basicMetadataAssertions(meta, 1, false, OK);
//        assertIterableEquals(List.of(keyUsed), meta.requiredNonPayerKeys());
//    }
//
//    @Test
//    void preHandlesCryptoDeleteIfReceiverSigRequiredVanilla() {
//        final var keyUsed = (JKey) payerKey;
//
//        given(accounts.get(deleteAccountNum)).willReturn(Optional.of(deleteAccount));
//        given(accounts.get(transferAccountNum)).willReturn(Optional.of(transferAccount));
//        given(deleteAccount.getAccountKey()).willReturn(keyUsed);
//        given(transferAccount.getAccountKey()).willReturn(keyUsed);
//        given(transferAccount.isReceiverSigRequired()).willReturn(true);
//
//        final var txn = deleteAccountTransaction(deleteAccountId, transferAccountId);
//
//        final var meta = subject.preHandleCryptoDelete(txn, payer);
//
//        assertEquals(txn, meta.txnBody());
//        basicMetadataAssertions(meta, 2, false, OK);
//        assertEquals(payerKey, meta.payerKey());
//        assertIterableEquals(List.of(keyUsed, keyUsed), meta.requiredNonPayerKeys());
//    }
//
//    @Test
//    void doesntAddBothKeysAccountsSameAsPayerForCryptoDelete() {
//        final var txn = deleteAccountTransaction(payer, payer);
//
//        final var meta = subject.preHandleCryptoDelete(txn, payer);
//
//        assertEquals(txn, meta.txnBody());
//        basicMetadataAssertions(meta, 0, false, OK);
//        assertEquals(payerKey, meta.payerKey());
//        assertIterableEquals(List.of(), meta.requiredNonPayerKeys());
//    }
//
//    @Test
//    void doesntAddTransferKeyIfAccountSameAsPayerForCryptoDelete() {
//        final var keyUsed = (JKey) payerKey;
//
//        given(accounts.get(deleteAccountNum)).willReturn(Optional.of(deleteAccount));
//        given(deleteAccount.getAccountKey()).willReturn(keyUsed);
//
//        final var txn = deleteAccountTransaction(deleteAccountId, payer);
//
//        final var meta = subject.preHandleCryptoDelete(txn, payer);
//
//        assertEquals(txn, meta.txnBody());
//        assertEquals(payerKey, meta.payerKey());
//        basicMetadataAssertions(meta, 1, false, OK);
//        assertIterableEquals(List.of(keyUsed), meta.requiredNonPayerKeys());
//    }
//
//    @Test
//    void doesntExecuteIfAccountIdIsDefaultInstance() {
//        final var keyUsed = (JKey) payerKey;
//
//        given(accounts.get(deleteAccountNum)).willReturn(Optional.of(deleteAccount));
//        given(deleteAccount.getAccountKey()).willReturn(keyUsed);
//
//        final var txn = deleteAccountTransaction(deleteAccountId, AccountID.getDefaultInstance());
//
//        final var meta = subject.preHandleCryptoDelete(txn, payer);
//
//        assertEquals(txn, meta.txnBody());
//        basicMetadataAssertions(meta, 1, false, OK);
//        assertEquals(payerKey, meta.payerKey());
//        assertIterableEquals(List.of(keyUsed), meta.requiredNonPayerKeys());
//    }
//
//    @Test
//    void doesntAddDeleteKeyIfAccountSameAsPayerForCryptoDelete() {
//        final var keyUsed = (JKey) payerKey;
//
//        given(accounts.get(transferAccountNum)).willReturn(Optional.of(transferAccount));
//        given(transferAccount.getAccountKey()).willReturn(keyUsed);
//        given(transferAccount.isReceiverSigRequired()).willReturn(true);
//
//        final var txn = deleteAccountTransaction(payer, transferAccountId);
//
//        final var meta = subject.preHandleCryptoDelete(txn, payer);
//
//        assertEquals(txn, meta.txnBody());
//        basicMetadataAssertions(meta, 1, false, OK);
//        assertEquals(payerKey, meta.payerKey());
//        assertIterableEquals(List.of(keyUsed), meta.requiredNonPayerKeys());
//    }
//
//    @Test
//    void failsWithResponseCodeIfAnyAccountMissingForCryptoDelete() {
//        final var keyUsed = (JKey) payerKey;
//
//        /* ------ payerAccount missing, so deleteAccount and transferAccount will not be added  ------ */
//        var txn = deleteAccountTransaction(deleteAccountId, transferAccountId);
//        given(accounts.get(payerNum)).willReturn(Optional.empty());
//        given(accounts.get(deleteAccountNum)).willReturn(Optional.of(deleteAccount));
//        given(accounts.get(transferAccountNum)).willReturn(Optional.of(transferAccount));
//        given(deleteAccount.getAccountKey()).willReturn(keyUsed);
//
//        var meta = subject.preHandleCryptoDelete(txn, payer);
//        basicMetadataAssertions(meta, 0, true, INVALID_PAYER_ACCOUNT_ID);
//        assertNull(meta.payerKey());
//        assertIterableEquals(List.of(), meta.requiredNonPayerKeys());
//
//        /* ------ deleteAccount missing, so transferAccount will not be added ------ */
//        given(accounts.get(payerNum)).willReturn(Optional.of(payerAccount));
//        given(payerAccount.getAccountKey()).willReturn(keyUsed);
//        given(accounts.get(deleteAccountNum)).willReturn(Optional.empty());
//        given(accounts.get(transferAccountNum)).willReturn(Optional.of(transferAccount));
//
//        meta = subject.preHandleCryptoDelete(txn, payer);
//
//        basicMetadataAssertions(meta, 0, true, INVALID_ACCOUNT_ID);
//        assertEquals(payerKey, meta.payerKey());
//        assertIterableEquals(List.of(), meta.requiredNonPayerKeys());
//
//        /* ------ transferAccount missing ------ */
//        given(accounts.get(deleteAccountNum)).willReturn(Optional.of(deleteAccount));
//        given(deleteAccount.getAccountKey()).willReturn(keyUsed);
//        given(accounts.get(transferAccountNum)).willReturn(Optional.empty());
//
//        meta = subject.preHandleCryptoDelete(txn, payer);
//
//        basicMetadataAssertions(meta, 1, true, INVALID_TRANSFER_ACCOUNT_ID);
//        assertEquals(payerKey, meta.payerKey());
//        assertIterableEquals(List.of(keyUsed), meta.requiredNonPayerKeys());
//    }
//
//    @Test
//    void cryptoApproveAllowanceVanilla() {
//        given(accounts.get(owner.getAccountNum())).willReturn(Optional.of(ownerAccount));
//        given(ownerAccount.getAccountKey()).willReturn((JKey) ownerKey);
//
//        final var txn = cryptoApproveAllowanceTransaction(payer, false);
//        final var meta = subject.preHandleApproveAllowances(txn, payer);
//        basicMetadataAssertions(meta, 3, false, OK);
//        assertEquals(payerKey, meta.payerKey());
//        assertIterableEquals(List.of(ownerKey, ownerKey, ownerKey), meta.requiredNonPayerKeys());
//    }
//
//    @Test
//    void cryptoApproveAllowanceFailsWithInvalidOwner() {
//        given(accounts.get(owner.getAccountNum())).willReturn(Optional.empty());
//
//        final var txn = cryptoApproveAllowanceTransaction(payer, false);
//        final var meta = subject.preHandleApproveAllowances(txn, payer);
//        basicMetadataAssertions(meta, 0, true, INVALID_ALLOWANCE_OWNER_ID);
//        assertEquals(payerKey, meta.payerKey());
//        assertIterableEquals(List.of(), meta.requiredNonPayerKeys());
//    }
//
//    @Test
//    void cryptoApproveAllowanceDoesntAddIfOwnerSameAsPayer() {
//        given(accounts.get(owner.getAccountNum())).willReturn(Optional.of(ownerAccount));
//        given(ownerAccount.getAccountKey()).willReturn((JKey) ownerKey);
//
//        final var txn = cryptoApproveAllowanceTransaction(owner, false);
//        final var meta = subject.preHandleApproveAllowances(txn, owner);
//        basicMetadataAssertions(meta, 0, false, OK);
//        assertEquals(ownerKey, meta.payerKey());
//        assertIterableEquals(List.of(), meta.requiredNonPayerKeys());
//    }
//
//    @Test
//    void cryptoApproveAllowanceAddsDelegatingSpender() {
//        given(accounts.get(owner.getAccountNum())).willReturn(Optional.of(ownerAccount));
//        given(ownerAccount.getAccountKey()).willReturn((JKey) ownerKey);
//        given(accounts.get(delegatingSpender.getAccountNum()))
//                .willReturn(Optional.of(payerAccount));
//
//        final var txn = cryptoApproveAllowanceTransaction(payer, true);
//        final var meta = subject.preHandleApproveAllowances(txn, payer);
//        basicMetadataAssertions(meta, 3, false, OK);
//        assertEquals(payerKey, meta.payerKey());
//        assertIterableEquals(List.of(ownerKey, ownerKey, payerKey), meta.requiredNonPayerKeys());
//    }
//
//    @Test
//    void cryptoApproveAllowanceFailsIfDelegatingSpenderMissing() {
//        given(accounts.get(owner.getAccountNum())).willReturn(Optional.of(ownerAccount));
//        given(ownerAccount.getAccountKey()).willReturn((JKey) ownerKey);
//        given(accounts.get(delegatingSpender.getAccountNum())).willReturn(Optional.empty());
//
//        final var txn = cryptoApproveAllowanceTransaction(payer, true);
//        final var meta = subject.preHandleApproveAllowances(txn, payer);
//        assertEquals(payerKey, meta.payerKey());
//        basicMetadataAssertions(meta, 2, true, INVALID_DELEGATING_SPENDER);
//        assertIterableEquals(List.of(ownerKey, ownerKey), meta.requiredNonPayerKeys());
//    }
//
//    @Test
//    void cryptoDeleteAllowanceVanilla() {
//        given(accounts.get(owner.getAccountNum())).willReturn(Optional.of(ownerAccount));
//        given(ownerAccount.getAccountKey()).willReturn((JKey) ownerKey);
//
//        final var txn = cryptoDeleteAllowanceTransaction(payer);
//        final var meta = subject.preHandleDeleteAllowances(txn, payer);
//        basicMetadataAssertions(meta, 1, false, OK);
//        assertEquals(payerKey, meta.payerKey());
//        assertIterableEquals(List.of(ownerKey), meta.requiredNonPayerKeys());
//    }
//
//    @Test
//    void cryptoDeleteAllowanceDoesntAddIfOwnerSameAsPayer() {
//        given(accounts.get(owner.getAccountNum())).willReturn(Optional.of(ownerAccount));
//        given(ownerAccount.getAccountKey()).willReturn((JKey) ownerKey);
//
//        final var txn = cryptoDeleteAllowanceTransaction(owner);
//        final var meta = subject.preHandleDeleteAllowances(txn, owner);
//        basicMetadataAssertions(meta, 0, false, OK);
//        assertEquals(ownerKey, meta.payerKey());
//        assertIterableEquals(List.of(), meta.requiredNonPayerKeys());
//    }
//
//    @Test
//    void cryptoDeleteAllowanceFailsIfPayerOrOwnerNotExist() {
//        var txn = cryptoDeleteAllowanceTransaction(owner);
//        given(accounts.get(owner.getAccountNum())).willReturn(Optional.empty());
//
//        var meta = subject.preHandleDeleteAllowances(txn);
//        basicMetadataAssertions(meta, 0, true, INVALID_PAYER_ACCOUNT_ID);
//        assertIterableEquals(List.of(), meta.getReqKeys());
//
//        txn = cryptoDeleteAllowanceTransaction(payer);
//        meta = subject.preHandleDeleteAllowances(txn, payer);
//        basicMetadataAssertions(meta, 0, true, INVALID_ALLOWANCE_OWNER_ID);
//        assertEquals(payerKey, meta.payerKey());
//        assertIterableEquals(List.of(), meta.requiredNonPayerKeys());
//    }
//
//    @Test
//    void cryptoTransferVanilla() {
//        final var basicCryptoTransfer =
//                List.of(
//                        AccountAmount.newBuilder()
//                                .setAccountID(cryptoTransferSenderAccountId)
//                                .setAmount(123)
//                                .build());
//        final var basicNftTransfer =
//                List.of(
//                        NftTransfer.newBuilder()
//                                .setSenderAccountID(nftSenderAccountId)
//                                .setReceiverAccountID(nftReceiverAccountId)
//                                .build());
//        final var txn =
//                cryptoTransferTransaction(payer, basicCryptoTransfer, basicNftTransfer, null);
//
//        given(accounts.get(nftSenderAccountId.getAccountNum()))
//                .willReturn(Optional.of(nftSenderAccount));
//        given(accounts.get(nftReceiverAccountId.getAccountNum()))
//                .willReturn(Optional.of(nftReceiverAccount));
//        given(accounts.get(cryptoTransferSenderAccountId.getAccountNum()))
//                .willReturn(Optional.of(cryptoTransferSenderAccount));
//        given(nftSenderAccount.getAccountKey()).willReturn((JKey) ownerKey);
//        given(cryptoTransferSenderAccount.getAccountKey()).willReturn((JKey) randomKey);
//        given(nftReceiverAccount.getAccountKey()).willReturn((JKey) genericKey);
//        given(nftReceiverAccount.isReceiverSigRequired()).willReturn(true);
//        given(cryptoTransferSenderAccount.isReceiverSigRequired()).willReturn(true);
//        given(tokenStore.getTokenMeta(any()))
//                .willReturn(
//                        new TokenStore.TokenMetaOrLookupFailureReason(
//                                new TokenStore.TokenMetadata(
//                                        null, null, null, null, null, null, null, false, null),
//                                null));
//
//        final var meta = subject.preHandleCryptoTransfer(txn);
//
//        assertEquals(txn, meta.getTxn());
//        basicMetadataAssertions(meta, 4, false, OK);
//        assertTrue(meta.getReqKeys().contains(payerKey));
//        assertTrue(meta.getReqKeys().contains(ownerKey));
//        assertTrue(meta.getReqKeys().contains(randomKey));
//    }
//
//    @Test
//    void cryptoTransferUnapprovedDebitShouldAddKey() {
//        final var unapprovedDebit =
//                AccountAmount.newBuilder()
//                        .setAccountID(cryptoTransferSenderAccountId)
//                        .setAmount(-54)
//                        .build();
//        final var approvedDebit =
//                AccountAmount.newBuilder()
//                        .setAccountID(randomAccountId)
//                        .setAmount(-54)
//                        .setIsApproval(true)
//                        .build();
//        final var txn =
//                cryptoTransferTransaction(
//                        payer, List.of(unapprovedDebit, approvedDebit), null, null);
//
//        given(accounts.get(cryptoTransferSenderAccountId.getAccountNum()))
//                .willReturn(Optional.of(cryptoTransferSenderAccount));
//        given(accounts.get(randomAccountId.getAccountNum())).willReturn(Optional.of(randomAccount));
//        given(cryptoTransferSenderAccount.getAccountKey()).willReturn((JKey) ownerKey);
//        given(randomAccount.getAccountKey()).willReturn((JKey) randomKey);
//        given(tokenStore.getTokenMeta(any()))
//                .willReturn(
//                        new TokenStore.TokenMetaOrLookupFailureReason(
//                                new TokenStore.TokenMetadata(
//                                        null, null, null, null, null, null, null, false, null),
//                                null));
//
//        final var meta = subject.preHandleCryptoTransfer(txn);
//
//        assertEquals(txn, meta.getTxn());
//        basicMetadataAssertions(meta, 2, false, OK);
//        assertTrue(meta.getReqKeys().contains(payerKey));
//        assertTrue(meta.getReqKeys().contains(ownerKey));
//        assertFalse(meta.getReqKeys().contains(randomKey));
//    }
//
//    @Test
//    void cryptoTransferMissingAccountStatusOk() {
//        final var basicTransfer =
//                AccountAmount.newBuilder().setAccountID(aliasedAccountId).setAmount(343).build();
//        final var basicNftTransfer =
//                NftTransfer.newBuilder()
//                        .setSenderAccountID(nftSenderAccountId)
//                        .setReceiverAccountID(aliasedAccountId)
//                        .build();
//        final var txn =
//                cryptoTransferTransaction(
//                        payer, List.of(basicTransfer), List.of(basicNftTransfer), null);
//
//        given(accounts.get(nftSenderAccountId.getAccountNum()))
//                .willReturn(Optional.of(nftSenderAccount));
//        given(nftSenderAccount.getAccountKey()).willReturn((JKey) randomKey);
//        given(tokenStore.getTokenMeta(any()))
//                .willReturn(
//                        new TokenStore.TokenMetaOrLookupFailureReason(
//                                new TokenStore.TokenMetadata(
//                                        null, null, null, null, null, null, null, false, null),
//                                null));
//
//        final var meta = subject.preHandleCryptoTransfer(txn);
//
//        assertEquals(txn, meta.getTxn());
//        basicMetadataAssertions(meta, 2, false, OK);
//        assertTrue(meta.getReqKeys().contains(payerKey));
//        assertTrue(meta.getReqKeys().contains(randomKey));
//    }
//
//    @Test
//    void cryptoTransferMissingAccountFails() {
//        final var aliasDebit =
//                AccountAmount.newBuilder().setAccountID(aliasedAccountId).setAmount(-343).build();
//        final var missingReceiver =
//                NftTransfer.newBuilder()
//                        .setSenderAccountID(nftSenderAccountId)
//                        .setReceiverAccountID(nftReceiverAccountId)
//                        .build();
//        final var fungibleTransfer =
//                cryptoTransferTransaction(payer, List.of(aliasDebit), null, null);
//        final var nftTransfer =
//                cryptoTransferTransaction(payer, null, List.of(missingReceiver), null);
//
//        given(accounts.get(nftSenderAccountId.getAccountNum()))
//                .willReturn(Optional.of(nftSenderAccount));
//        given(nftSenderAccount.getAccountKey()).willReturn((JKey) ownerKey);
//        given(tokenStore.getTokenMeta(any()))
//                .willReturn(
//                        new TokenStore.TokenMetaOrLookupFailureReason(
//                                new TokenStore.TokenMetadata(
//                                        null, null, null, null, null, null, null, false, null),
//                                null));
//
//        final var fungibleMeta = subject.preHandleCryptoTransfer(fungibleTransfer);
//        final var nftMeta = subject.preHandleCryptoTransfer(nftTransfer);
//
//        assertEquals(fungibleTransfer, fungibleMeta.getTxn());
//        assertEquals(nftTransfer, nftMeta.getTxn());
//        basicMetadataAssertions(fungibleMeta, 1, true, INVALID_ACCOUNT_ID);
//        basicMetadataAssertions(nftMeta, 2, true, INVALID_ACCOUNT_ID);
//        assertTrue(fungibleMeta.getReqKeys().contains(payerKey));
//        assertTrue(nftMeta.getReqKeys().contains(payerKey));
//        assertTrue(nftMeta.getReqKeys().contains(ownerKey));
//    }
//
//    @Test
//    void cryptoTransferAddsKeyIfValidTreasury() {
//        final var treasury = EntityId.fromGrpcAccountId(randomAccountId);
//        final var basicNftTransfer =
//                NftTransfer.newBuilder()
//                        .setSenderAccountID(nftSenderAccountId)
//                        .setReceiverAccountID(nftReceiverAccountId)
//                        .build();
//        final var txn = cryptoTransferTransaction(payer, null, List.of(basicNftTransfer), null);
//
//        given(accounts.get(nftSenderAccountId.getAccountNum()))
//                .willReturn(Optional.of(nftSenderAccount));
//        given(accounts.get(nftReceiverAccountId.getAccountNum()))
//                .willReturn(Optional.of(nftReceiverAccount));
//        given(nftSenderAccount.getAccountKey()).willReturn((JKey) ownerKey);
//        given(nftReceiverAccount.getAccountKey()).willReturn((JKey) randomKey);
//        given(nftReceiverAccount.isReceiverSigRequired()).willReturn(false);
//        given(tokenStore.getTokenMeta(any()))
//                .willReturn(
//                        new TokenStore.TokenMetaOrLookupFailureReason(
//                                new TokenStore.TokenMetadata(
//                                        null, null, null, null, null, null, null, true, treasury),
//                                null));
//
//        final var meta = subject.preHandleCryptoTransfer(txn);
//
//        assertEquals(txn, meta.getTxn());
//        basicMetadataAssertions(meta, 3, false, OK);
//        assertTrue(meta.getReqKeys().contains(payerKey));
//        assertTrue(meta.getReqKeys().contains(ownerKey));
//        assertTrue(meta.getReqKeys().contains(randomKey));
//    }
//
//    @Test
//    void cryptoTransferFailsIfNoNftSenderOrReceiver() {
//        final var missingSender = NftTransfer.newBuilder().build();
//        final var missingReceiver =
//                NftTransfer.newBuilder().setSenderAccountID(nftSenderAccountId).build();
//        final var missingSenderTx =
//                cryptoTransferTransaction(payer, null, List.of(missingSender), null);
//        final var missingReceiverTx =
//                cryptoTransferTransaction(payer, null, List.of(missingReceiver), null);
//
//        given(accounts.get(nftSenderAccountId.getAccountNum()))
//                .willReturn(Optional.of(nftSenderAccount));
//        given(nftSenderAccount.getAccountKey()).willReturn((JKey) ownerKey);
//        given(tokenStore.getTokenMeta(any()))
//                .willReturn(
//                        new TokenStore.TokenMetaOrLookupFailureReason(
//                                new TokenStore.TokenMetadata(
//                                        null, null, null, null, null, null, null, false, null),
//                                null));
//
//        final var missingSenderMeta = subject.preHandleCryptoTransfer(missingSenderTx);
//        final var missingReceiverMeta = subject.preHandleCryptoTransfer(missingReceiverTx);
//
//        assertEquals(missingSenderTx, missingSenderMeta.getTxn());
//        assertEquals(missingReceiverTx, missingReceiverMeta.getTxn());
//        basicMetadataAssertions(missingSenderMeta, 1, true, INVALID_ACCOUNT_ID);
//        basicMetadataAssertions(missingReceiverMeta, 2, true, INVALID_ACCOUNT_ID);
//        assertTrue(missingSenderMeta.getReqKeys().contains(payerKey));
//        assertTrue(missingReceiverMeta.getReqKeys().contains(payerKey));
//        assertTrue(missingReceiverMeta.getReqKeys().contains(ownerKey));
//    }
//
//    @Test
//    void cryptoTransferDoesntReachTreasuryIfFungible() {
//        final var cryptoTransfer =
//                AccountAmount.newBuilder().setAccountID(nftSenderAccountId).setAmount(134).build();
//        final var negativeHbarTransfer =
//                AccountAmount.newBuilder()
//                        .setAccountID(cryptoTransferSenderAccountId)
//                        .setAmount(-256)
//                        .build();
//        final var hbarTransfer =
//                AccountAmount.newBuilder().setAccountID(nftSenderAccountId).setAmount(134).build();
//        final var nftTransfer =
//                NftTransfer.newBuilder()
//                        .setSenderAccountID(nftSenderAccountId)
//                        .setReceiverAccountID(nftReceiverAccountId)
//                        .build();
//
//        final var positiveCryptoTransaction =
//                cryptoTransferTransaction(payer, null, List.of(nftTransfer), List.of(hbarTransfer));
//        final var positiveHbarTransaction =
//                cryptoTransferTransaction(
//                        payer,
//                        List.of(cryptoTransfer),
//                        List.of(nftTransfer),
//                        List.of(negativeHbarTransfer));
//
//        given(accounts.get(cryptoTransferSenderAccountId.getAccountNum()))
//                .willReturn(Optional.of(cryptoTransferSenderAccount));
//        given(accounts.get(nftSenderAccountId.getAccountNum()))
//                .willReturn(Optional.of(nftSenderAccount));
//        given(accounts.get(nftReceiverAccountId.getAccountNum()))
//                .willReturn(Optional.of(nftReceiverAccount));
//        given(cryptoTransferSenderAccount.getAccountKey()).willReturn((JKey) genericKey);
//        given(nftReceiverAccount.getAccountKey()).willReturn((JKey) randomKey);
//        given(nftSenderAccount.getAccountKey()).willReturn((JKey) ownerKey);
//        given(nftReceiverAccount.isReceiverSigRequired()).willReturn(false);
//        given(tokenStore.getTokenMeta(any()))
//                .willReturn(
//                        new TokenStore.TokenMetaOrLookupFailureReason(
//                                new TokenStore.TokenMetadata(
//                                        null, null, null, null, null, null, null, true, null),
//                                null));
//
//        final var positiveCryptoMeta = subject.preHandleCryptoTransfer(positiveCryptoTransaction);
//        final var positiveHbarMeta = subject.preHandleCryptoTransfer(positiveHbarTransaction);
//
//        assertEquals(positiveCryptoTransaction, positiveCryptoMeta.getTxn());
//        assertEquals(positiveHbarTransaction, positiveHbarMeta.getTxn());
//        basicMetadataAssertions(positiveCryptoMeta, 2, false, OK);
//        basicMetadataAssertions(positiveHbarMeta, 3, false, OK);
//        assertTrue(positiveCryptoMeta.getReqKeys().contains(payerKey));
//        assertTrue(positiveCryptoMeta.getReqKeys().contains(ownerKey));
//        assertTrue(positiveHbarMeta.getReqKeys().contains(payerKey));
//        assertTrue(positiveHbarMeta.getReqKeys().contains(ownerKey));
//        assertTrue(positiveHbarMeta.getReqKeys().contains(genericKey));
//    }
//
//    @Test
//    void cryptoTransferAddsKeyIfUnapprovedDebit() {
//        final var unapprovedDebit =
//                AccountAmount.newBuilder()
//                        .setAccountID(cryptoTransferSenderAccountId)
//                        .setAmount(-54)
//                        .build();
//        final var approvedDebit =
//                AccountAmount.newBuilder()
//                        .setAccountID(randomAccountId)
//                        .setAmount(-54)
//                        .setIsApproval(true)
//                        .build();
//        final var txn =
//                cryptoTransferTransaction(
//                        payer, null, null, List.of(unapprovedDebit, approvedDebit));
//
//        given(accounts.get(cryptoTransferSenderAccountId.getAccountNum()))
//                .willReturn(Optional.of(cryptoTransferSenderAccount));
//        given(accounts.get(randomAccountId.getAccountNum())).willReturn(Optional.of(randomAccount));
//        given(cryptoTransferSenderAccount.getAccountKey()).willReturn((JKey) ownerKey);
//        given(randomAccount.getAccountKey()).willReturn((JKey) randomKey);
//        given(randomAccount.isReceiverSigRequired()).willReturn(false);
//        given(tokenStore.getTokenMeta(any()))
//                .willReturn(
//                        new TokenStore.TokenMetaOrLookupFailureReason(
//                                new TokenStore.TokenMetadata(
//                                        null, null, null, null, null, null, null, true, null),
//                                null));
//
//        final var meta = subject.preHandleCryptoTransfer(txn);
//
//        assertEquals(txn, meta.getTxn());
//        basicMetadataAssertions(meta, 2, false, OK);
//        assertTrue(meta.getReqKeys().contains(payerKey));
//        assertTrue(meta.getReqKeys().contains(ownerKey));
//        assertFalse(meta.getReqKeys().contains(randomKey));
//    }
//
//    @Test
//    void cryptoTransferMissingAliasedAndImmutableAccStatusOk() {
//        final var immutableAccount =
//                AccountAmount.newBuilder()
//                        .setAccountID(cryptoTransferSenderAccountId)
//                        .setAmount(54)
//                        .build();
//        final var missingAliasedAccount =
//                AccountAmount.newBuilder().setAccountID(aliasedAccountId).setAmount(54).build();
//        final var txn =
//                cryptoTransferTransaction(
//                        payer, null, null, List.of(immutableAccount, missingAliasedAccount));
//
//        given(accounts.get(cryptoTransferSenderAccountId.getAccountNum()))
//                .willReturn(Optional.of(cryptoTransferSenderAccount));
//        given(cryptoTransferSenderAccount.getAccountKey()).willReturn((JKey) emptyKey);
//
//        given(tokenStore.getTokenMeta(any()))
//                .willReturn(
//                        new TokenStore.TokenMetaOrLookupFailureReason(
//                                new TokenStore.TokenMetadata(
//                                        null, null, null, null, null, null, null, true, null),
//                                null));
//
//        final var meta = subject.preHandleCryptoTransfer(txn);
//
//        assertEquals(txn, meta.getTxn());
//        basicMetadataAssertions(meta, 1, false, OK);
//        assertTrue(meta.getReqKeys().contains(payerKey));
//    }
//
//    @Test
//    void cryptoTransferMissingAliasedAndImmutableAccDebitFails() {
//        final var immutableDebit =
//                AccountAmount.newBuilder()
//                        .setAccountID(cryptoTransferSenderAccountId)
//                        .setAmount(-256)
//                        .build();
//        final var missingAliasedAccount =
//                AccountAmount.newBuilder().setAccountID(aliasedAccountId).setAmount(-256).build();
//        final var immutableTx =
//                cryptoTransferTransaction(payer, null, null, List.of(immutableDebit));
//        final var missingTx =
//                cryptoTransferTransaction(payer, null, null, List.of(missingAliasedAccount));
//
//        given(accounts.get(cryptoTransferSenderAccountId.getAccountNum()))
//                .willReturn(Optional.of(cryptoTransferSenderAccount));
//        given(cryptoTransferSenderAccount.getAccountKey()).willReturn((JKey) emptyKey);
//
//        given(tokenStore.getTokenMeta(any()))
//                .willReturn(
//                        new TokenStore.TokenMetaOrLookupFailureReason(
//                                new TokenStore.TokenMetadata(
//                                        null, null, null, null, null, null, null, true, null),
//                                null));
//
//        final var immutableMeta = subject.preHandleCryptoTransfer(immutableTx);
//        final var missingMeta = subject.preHandleCryptoTransfer(missingTx);
//
//        assertEquals(immutableTx, immutableMeta.getTxn());
//        assertEquals(missingTx, missingMeta.getTxn());
//        basicMetadataAssertions(immutableMeta, 1, true, ACCOUNT_IS_IMMUTABLE);
//        basicMetadataAssertions(missingMeta, 1, true, INVALID_ACCOUNT_ID);
//        assertTrue(immutableMeta.getReqKeys().contains(payerKey));
//        assertTrue(missingMeta.getReqKeys().contains(payerKey));
//    }
//
//    @Test
//    void cryptoTransferTransfersMissingAccountFails() {
//        final var missingAliasedAccount =
//                AccountAmount.newBuilder().setAccountID(randomAccountId).setAmount(54).build();
//        final var txn =
//                cryptoTransferTransaction(payer, null, null, List.of(missingAliasedAccount));
//
//        given(tokenStore.getTokenMeta(any()))
//                .willReturn(
//                        new TokenStore.TokenMetaOrLookupFailureReason(
//                                new TokenStore.TokenMetadata(
//                                        null, null, null, null, null, null, null, true, null),
//                                null));
//
//        final var meta = subject.preHandleCryptoTransfer(txn);
//
//        assertEquals(txn, meta.getTxn());
//        basicMetadataAssertions(meta, 1, true, INVALID_ACCOUNT_ID);
//        assertTrue(meta.getReqKeys().contains(payerKey));
//    }
//
//    @Test
//    void cryptoTransferFailsIfNoPayer() {
//        final var txn = cryptoTransferTransaction(owner, null, null, null);
//
//        given(tokenStore.getTokenMeta(any()))
//                .willReturn(
//                        new TokenStore.TokenMetaOrLookupFailureReason(
//                                new TokenStore.TokenMetadata(
//                                        null, null, null, null, null, null, null, true, null),
//                                null));
//
//        final var meta = subject.preHandleCryptoTransfer(txn);
//
//        assertEquals(txn, meta.getTxn());
//        basicMetadataAssertions(meta, 0, true, INVALID_PAYER_ACCOUNT_ID);
//        assertFalse(meta.getReqKeys().contains(payerKey));
//    }
//
//    @Test
//    void cryptoTransferFailsIfNoTokenMeta() {
//        final var cryptoTransfer =
//                AccountAmount.newBuilder()
//                        .setAccountID(nftReceiverAccountId)
//                        .setAmount(134)
//                        .build();
//        final var txn = cryptoTransferTransaction(payer, List.of(cryptoTransfer), null, null);
//
//        given(tokenStore.getTokenMeta(any()))
//                .willReturn(
//                        new TokenStore.TokenMetaOrLookupFailureReason(
//                                new TokenStore.TokenMetadata(
//                                        null, null, null, null, null, null, null, true, null),
//                                INVALID_TOKEN_ID));
//
//        final var meta = subject.preHandleCryptoTransfer(txn);
//
//        assertEquals(txn, meta.txnBody());
//        basicMetadataAssertions(meta, 1, true, INVALID_TOKEN_ID);
//        assertTrue(meta.requiredNonPayerKeys().contains(payerKey));
//    }
//
//    @Test
//    void cryptoUpdateVanilla() {
//        var txn = cryptoUpdateTransaction(payer, updateAccountId);
//        given(accounts.get(updateAccountId.getAccountNum())).willReturn(Optional.of(updateAccount));
//        given(updateAccount.getAccountKey()).willReturn((JKey) updateAccountKey);
//        given(waivers.isNewKeySignatureWaived(txn, payer)).willReturn(false);
//        given(waivers.isTargetAccountSignatureWaived(txn, payer)).willReturn(false);
//
//        var meta = subject.preHandleUpdateAccount(txn, payer);
//        basicMetadataAssertions(meta, 2, false, OK);
//        assertEquals(payerKey, meta.payerKey());
//        assertTrue(meta.requiredNonPayerKeys().contains(updateAccountKey));
//    }
//
//    @Test
//    void cryptoUpdateNewSignatureKeyWaivedVanilla() {
//        var txn = cryptoUpdateTransaction(payer, updateAccountId);
//        given(accounts.get(updateAccountId.getAccountNum())).willReturn(Optional.of(updateAccount));
//        given(updateAccount.getAccountKey()).willReturn((JKey) updateAccountKey);
//        given(waivers.isNewKeySignatureWaived(txn, payer)).willReturn(true);
//        given(waivers.isTargetAccountSignatureWaived(txn, payer)).willReturn(false);
//
//        var meta = subject.preHandleUpdateAccount(txn, payer);
//        basicMetadataAssertions(meta, 1, false, OK);
//        assertEquals(payerKey, meta.payerKey());
//        assertIterableEquals(List.of(updateAccountKey), meta.requiredNonPayerKeys());
//    }
//
//    @Test
//    void cryptoUpdateTargetSignatureKeyWaivedVanilla() {
//        var txn = cryptoUpdateTransaction(payer, updateAccountId);
//        given(waivers.isNewKeySignatureWaived(txn, payer)).willReturn(false);
//        given(waivers.isTargetAccountSignatureWaived(txn, payer)).willReturn(true);
//
//        var meta = subject.preHandleUpdateAccount(txn, payer);
//        basicMetadataAssertions(meta, 1, false, OK);
//        assertEquals(payerKey, meta.payerKey());
//        assertFalse(meta.requiredNonPayerKeys().contains(updateAccountKey));
//    }
//
//    @Test
//    void cryptoUpdatePayerMissingFails() {
//        var txn = cryptoUpdateTransaction(updateAccountId, updateAccountId);
//        given(accounts.get(updateAccountId.getAccountNum())).willReturn(Optional.empty());
//
//        given(waivers.isNewKeySignatureWaived(txn, updateAccountId)).willReturn(false);
//        given(waivers.isTargetAccountSignatureWaived(txn, updateAccountId)).willReturn(true);
//
//        var meta = subject.preHandleUpdateAccount(txn, updateAccountId);
//        basicMetadataAssertions(meta, 0, true, INVALID_PAYER_ACCOUNT_ID);
//        assertNull(meta.payerKey());
//    }
//
//    @Test
//    void cryptoUpdatePayerMissingFailsWhenNoOtherSigsRequired() {
//        var txn = cryptoUpdateTransaction(updateAccountId, updateAccountId);
//        given(accounts.get(updateAccountId.getAccountNum())).willReturn(Optional.empty());
//
//        given(waivers.isNewKeySignatureWaived(txn, updateAccountId)).willReturn(true);
//        given(waivers.isTargetAccountSignatureWaived(txn, updateAccountId)).willReturn(true);
//
//        var meta = subject.preHandleUpdateAccount(txn, updateAccountId);
//        basicMetadataAssertions(meta, 0, true, INVALID_PAYER_ACCOUNT_ID);
//        assertNull(meta.payerKey());
//    }
//
//    @Test
//    void cryptoUpdateUpdateAccountMissingFails() {
//        var txn = cryptoUpdateTransaction(payer, updateAccountId);
//        given(accounts.get(updateAccountId.getAccountNum())).willReturn(Optional.empty());
//
//        given(waivers.isNewKeySignatureWaived(txn, payer)).willReturn(true);
//        given(waivers.isTargetAccountSignatureWaived(txn, payer)).willReturn(false);
//
//        var meta = subject.preHandleUpdateAccount(txn, payer);
//        basicMetadataAssertions(meta, 0, true, INVALID_ACCOUNT_ID);
//        assertEquals(payerKey, meta.payerKey());
//    }
//
//    private void basicMetadataAssertions(
//            final TransactionMetadata meta,
//            final int keysSize,
//            final boolean failed,
//            final ResponseCodeEnum failureStatus) {
//        assertEquals(keysSize, meta.requiredNonPayerKeys().size());
//        assertTrue(failed ? meta.failed() : !meta.failed());
//        assertEquals(failureStatus, meta.status());
//    }
//
//    private TransactionBody createAccountTransaction(final boolean receiverSigReq) {
//        setUpPayer();
//
//        final var transactionID =
//                TransactionID.newBuilder()
//                        .setAccountID(payer)
//                        .setTransactionValidStart(consensusTimestamp);
//        final var createTxnBody =
//                CryptoCreateTransactionBody.newBuilder()
//                        .setKey(key)
//                        .setReceiverSigRequired(receiverSigReq)
//                        .setMemo("Create Account")
//                        .build();
//
//        return TransactionBody.newBuilder()
//                .setTransactionID(transactionID)
//                .setCryptoCreateAccount(createTxnBody)
//                .build();
//    }
//
//    private TransactionBody deleteAccountTransaction(
//            final AccountID deleteAccountId, final AccountID transferAccountId) {
//        setUpPayer();
//
//        final var transactionID =
//                TransactionID.newBuilder()
//                        .setAccountID(payer)
//                        .setTransactionValidStart(consensusTimestamp);
//        final var deleteTxBody =
//                CryptoDeleteTransactionBody.newBuilder()
//                        .setDeleteAccountID(deleteAccountId)
//                        .setTransferAccountID(transferAccountId);
//
//        return TransactionBody.newBuilder()
//                .setTransactionID(transactionID)
//                .setCryptoDelete(deleteTxBody)
//                .build();
//    }
//
//    private TransactionBody cryptoApproveAllowanceTransaction(
//            final AccountID id, final boolean isWithDelegatingSpender) {
//        if (id.equals(payer)) {
//            setUpPayer();
//        }
//
//        final var transactionID =
//                TransactionID.newBuilder()
//                        .setAccountID(id)
//                        .setTransactionValidStart(consensusTimestamp);
//        final var allowanceTxnBody =
//                CryptoApproveAllowanceTransactionBody.newBuilder()
//                        .addCryptoAllowances(cryptoAllowance)
//                        .addTokenAllowances(tokenAllowance)
//                        .addNftAllowances(
//                                isWithDelegatingSpender
//                                        ? nftAllowanceWithDelegatingSpender
//                                        : nftAllowance)
//                        .build();
//        return TransactionBody.newBuilder()
//                .setTransactionID(transactionID)
//                .setCryptoApproveAllowance(allowanceTxnBody)
//                .build();
//    }
//
//    private TransactionBody cryptoUpdateTransaction(
//            final AccountID payerId, final AccountID accountToUpdate) {
//        if (payerId.equals(payer)) {
//            setUpPayer();
//        }
//        final var transactionID =
//                TransactionID.newBuilder()
//                        .setAccountID(payerId)
//                        .setTransactionValidStart(consensusTimestamp);
//        final var updateTxnBody =
//                CryptoUpdateTransactionBody.newBuilder()
//                        .setAccountIDToUpdate(accountToUpdate)
//                        .setKey(key)
//                        .build();
//        return TransactionBody.newBuilder()
//                .setTransactionID(transactionID)
//                .setCryptoUpdateAccount(updateTxnBody)
//                .build();
//    }
//
//    private TransactionBody cryptoDeleteAllowanceTransaction(final AccountID id) {
//        if (id.equals(payer)) {
//            setUpPayer();
//        }
//        final var transactionID =
//                TransactionID.newBuilder()
//                        .setAccountID(id)
//                        .setTransactionValidStart(consensusTimestamp);
//        final var allowanceTxnBody =
//                CryptoDeleteAllowanceTransactionBody.newBuilder()
//                        .addNftAllowances(
//                                NftRemoveAllowance.newBuilder()
//                                        .setOwner(owner)
//                                        .setTokenId(nft)
//                                        .addAllSerialNumbers(List.of(1L, 2L, 3L))
//                                        .build())
//                        .build();
//        return TransactionBody.newBuilder()
//                .setTransactionID(transactionID)
//                .setCryptoDeleteAllowance(allowanceTxnBody)
//                .build();
//    }
//
//    private TransactionBody cryptoTransferTransaction(
//            final AccountID id,
//            List<AccountAmount> cryptoTransfers,
//            List<NftTransfer> nftTransfers,
//            List<AccountAmount> transfers) {
//        cryptoTransfers = cryptoTransfers == null ? new ArrayList<>() : cryptoTransfers;
//        nftTransfers = nftTransfers == null ? new ArrayList<>() : nftTransfers;
//        transfers = transfers == null ? new ArrayList<>() : transfers;
//
//        if (id.equals(payer)) {
//            setUpPayer();
//        }
//        final var transactionID =
//                TransactionID.newBuilder()
//                        .setAccountID(id)
//                        .setTransactionValidStart(consensusTimestamp);
//        final var transferTxnBody =
//                CryptoTransferTransactionBody.newBuilder()
//                        .setTransfers(
//                                TransferList.newBuilder().addAllAccountAmounts(transfers).build())
//                        .addTokenTransfers(
//                                TokenTransferList.newBuilder()
//                                        .addAllTransfers(cryptoTransfers)
//                                        .addAllNftTransfers(nftTransfers)
//                                        .setToken(
//                                                TokenID.newBuilder()
//                                                        .setTokenNum(666)
//                                                        .setRealmNum(0)
//                                                        .setShardNum(0)
//                                                        .build()))
//                        .build();
//        return TransactionBody.newBuilder()
//                .setTransactionID(transactionID)
//                .setCryptoTransfer(transferTxnBody)
//                .build();
//    }
//
//    final void setUpPayer() {
//        lenient().when(accounts.get(payerNum)).thenReturn(Optional.of(payerAccount));
//        lenient().when(payerAccount.getAccountKey()).thenReturn((JKey) payerKey);
//    }
}
