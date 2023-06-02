/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_DELEGATING_SPENDER;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoApproveAllowanceTransactionBody;
import com.hedera.hapi.node.token.NftAllowance;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.config.VersionedConfigImpl;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.ReadableUniqueTokenStore;
import com.hedera.node.app.service.token.impl.ReadableAccountStoreImpl;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.WritableUniqueTokenStore;
import com.hedera.node.app.service.token.impl.handlers.CryptoApproveAllowanceHandler;
import com.hedera.node.app.service.token.impl.test.handlers.util.CryptoTokenHandlerTestBase;
import com.hedera.node.app.service.token.impl.validators.ApproveAllowanceValidator;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CryptoApproveAllowanceHandlerTest extends CryptoTokenHandlerTestBase {
    private ApproveAllowanceValidator validator;

    @Mock
    private ConfigProvider configProvider;

    @Mock(strictness = Strictness.LENIENT)
    private HandleContext handleContext;

    private Configuration configuration;

    private final NftAllowance nftAllowance = NftAllowance.newBuilder()
            .spender(spenderId)
            .owner(ownerId)
            .tokenId(nonFungibleTokenId)
            .approvedForAll(Boolean.TRUE)
            .serialNumbers(List.of(1L, 2L))
            .build();
    private final NftAllowance nftAllowanceWithDelegatingSpender = NftAllowance.newBuilder()
            .spender(spenderId)
            .owner(ownerId)
            .tokenId(nonFungibleTokenId)
            .approvedForAll(Boolean.FALSE)
            .serialNumbers(List.of(1L, 2L))
            .delegatingSpender(delegatingSpenderId)
            .build();

    private CryptoApproveAllowanceHandler subject;

    @BeforeEach
    public void setUp() {
        super.setUp();
        refreshWritableStores();
        validator = new ApproveAllowanceValidator(configProvider);
        configuration = new HederaTestConfigBuilder().getOrCreateConfig();
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(configuration, 1));
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        given(handleContext.readableStore(ReadableAccountStore.class)).willReturn(readableAccountStore);

        given(handleContext.writableStore(WritableTokenStore.class)).willReturn(writableTokenStore);
        given(handleContext.readableStore(ReadableTokenStore.class)).willReturn(readableTokenStore);

        given(handleContext.readableStore(ReadableTokenRelationStore.class)).willReturn(readableTokenRelStore);
        given(handleContext.writableStore(WritableTokenRelationStore.class)).willReturn(writableTokenRelStore);

        given(handleContext.readableStore(ReadableUniqueTokenStore.class)).willReturn(readableNftStore);
        given(handleContext.writableStore(WritableUniqueTokenStore.class)).willReturn(writableNftStore);
        subject = new CryptoApproveAllowanceHandler(validator);
    }

    @Test
    void cryptoApproveAllowanceVanilla() throws PreCheckException {
        final var txn = cryptoApproveAllowanceTransaction(id, false);
        final var context = new FakePreHandleContext(readableAccountStore, txn);
        subject.preHandle(context);
        basicMetaAssertions(context, 1);
        assertEquals(key, context.payerKey());
        assertThat(context.requiredNonPayerKeys()).containsExactlyInAnyOrder(ownerKey);
    }

    @Test
    void cryptoApproveAllowanceFailsWithInvalidOwner() throws PreCheckException {
        readableAccounts = emptyReadableAccountStateBuilder()
                .value(EntityNumVirtualKey.fromLong(accountNum), account)
                .build();
        given(readableStates.<EntityNumVirtualKey, Account>get(ACCOUNTS)).willReturn(readableAccounts);
        readableAccountStore = new ReadableAccountStoreImpl(readableStates);

        final var txn = cryptoApproveAllowanceTransaction(id, false);
        final var context = new FakePreHandleContext(readableAccountStore, txn);
        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_ALLOWANCE_OWNER_ID);
    }

    @Test
    void cryptoApproveAllowanceDoesntAddIfOwnerSameAsPayer() throws PreCheckException {
        final var txn = cryptoApproveAllowanceTransaction(ownerId, false);
        final var context = new FakePreHandleContext(readableAccountStore, txn);
        subject.preHandle(context);
        basicMetaAssertions(context, 0);
        assertEquals(ownerKey, context.payerKey());
        assertThat(context.requiredNonPayerKeys()).isEmpty();
    }

    @Test
    void cryptoApproveAllowanceAddsDelegatingSpender() throws PreCheckException {
        final var txn = cryptoApproveAllowanceTransaction(id, true);
        final var context = new FakePreHandleContext(readableAccountStore, txn);
        subject.preHandle(context);
        basicMetaAssertions(context, 1);
        assertEquals(key, context.payerKey());
        assertThat(context.requiredNonPayerKeys()).containsExactlyInAnyOrder(ownerKey);
    }

    @Test
    void cryptoApproveAllowanceFailsIfDelegatingSpenderMissing() throws PreCheckException {
        readableAccounts = emptyReadableAccountStateBuilder()
                .value(EntityNumVirtualKey.fromLong(accountNum), account)
                .value(EntityNumVirtualKey.fromLong(ownerId.accountNum()), ownerAccount)
                .build();
        given(readableStates.<EntityNumVirtualKey, Account>get(ACCOUNTS)).willReturn(readableAccounts);
        readableAccountStore = new ReadableAccountStoreImpl(readableStates);
        given(ownerAccount.key()).willReturn(ownerKey);

        final var txn = cryptoApproveAllowanceTransaction(id, true);
        final var context = new FakePreHandleContext(readableAccountStore, txn);
        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_DELEGATING_SPENDER);
    }

    @Test
    void happyPathAddsAllowances() {
        final var txn = cryptoApproveAllowanceTransaction(id, false);
        given(handleContext.body()).willReturn(txn);

        subject.handle(handleContext);

        assertEquals(1, ownerAccount.cryptoAllowances().size());
        assertEquals(1, ownerAccount.tokenAllowances().size());
        assertEquals(1, ownerAccount.approveForAllNftAllowances().size());
    }

    //    @Test
    //    void considersPayerAsOwnerIfNotMentioned() {
    //        givenValidTxnCtxWithOwnerAsPayer();
    //        nft1.setOwner(Id.fromGrpcAccount(payerId));
    //        nft2.setOwner(Id.fromGrpcAccount(payerId));
    //
    //        given(accountStore.loadAccount(payerAccount.getId())).willReturn(payerAccount);
    //        given(dynamicProperties.maxAllowanceLimitPerAccount()).willReturn(100);
    //        given(tokenStore.loadUniqueToken(tokenId2, serial1)).willReturn(nft1);
    //        given(tokenStore.loadUniqueToken(tokenId2, serial2)).willReturn(nft2);
    //
    //        assertEquals(0, payerAccount.getCryptoAllowances().size());
    //        assertEquals(0, payerAccount.getFungibleTokenAllowances().size());
    //        assertEquals(0, payerAccount.getApprovedForAllNftsAllowances().size());
    //
    //        subject.approveAllowance(
    //                op.getCryptoAllowancesList(),
    //                op.getTokenAllowancesList(),
    //                op.getNftAllowancesList(),
    //                fromGrpcAccount(payerId).asGrpcAccount());
    //
    //        assertEquals(1, payerAccount.getCryptoAllowances().size());
    //        assertEquals(1, payerAccount.getFungibleTokenAllowances().size());
    //        assertEquals(1, payerAccount.getApprovedForAllNftsAllowances().size());
    //        assertEquals(spenderId1, nft1.getSpender());
    //        assertEquals(spenderId1, nft2.getSpender());
    //
    //        verify(accountStore).commitAccount(payerAccount);
    //    }
    //
    //    @Test
    //    void wipesSerialsWhenApprovedForAll() {
    //        givenValidTxnCtx();
    //
    //        given(accountStore.loadAccount(payerAccount.getId())).willReturn(payerAccount);
    //        given(accountStore.loadAccountOrFailWith(ownerAccount.getId(),
    // ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID))
    //                .willReturn(ownerAccount);
    //        given(dynamicProperties.maxAllowanceLimitPerAccount()).willReturn(100);
    //        given(tokenStore.loadUniqueToken(tokenId2, serial1)).willReturn(nft1);
    //        given(tokenStore.loadUniqueToken(tokenId2, serial2)).willReturn(nft2);
    //
    //        subject.approveAllowance(
    //                op.getCryptoAllowancesList(),
    //                op.getTokenAllowancesList(),
    //                op.getNftAllowancesList(),
    //                fromGrpcAccount(payerId).asGrpcAccount());
    //
    //        assertEquals(1, ownerAccount.getCryptoAllowances().size());
    //        assertEquals(1, ownerAccount.getFungibleTokenAllowances().size());
    //        assertEquals(1, ownerAccount.getApprovedForAllNftsAllowances().size());
    //
    //        verify(accountStore).commitAccount(ownerAccount);
    //    }
    //
    //    @Test
    //    void checksIfAllowancesExceedLimit() {
    //        com.hedera.node.app.service.mono.store.models.Account owner = mock(
    //                com.hedera.node.app.service.mono.store.models.Account.class);
    //
    //        given(accountStore.loadAccount(payerAccount.getId())).willReturn(payerAccount);
    //        given(accountStore.loadAccountOrFailWith(ownerAccount.getId(),
    // ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID))
    //                .willReturn(owner);
    //        given(owner.getTotalAllowances()).willReturn(101);
    //
    //        givenValidTxnCtx();
    //
    //        Executable approveAllowance = () -> subject.approveAllowance(
    //                op.getCryptoAllowancesList(),
    //                op.getTokenAllowancesList(),
    //                op.getNftAllowancesList(),
    //                fromGrpcAccount(payerId).asGrpcAccount());
    //
    //        var exception = assertThrows(InvalidTransactionException.class, approveAllowance);
    //        assertEquals(MAX_ALLOWANCES_EXCEEDED, exception.getResponseCode());
    //        assertEquals(0, ownerAccount.getCryptoAllowances().size());
    //        assertEquals(0, ownerAccount.getFungibleTokenAllowances().size());
    //        assertEquals(0, ownerAccount.getApprovedForAllNftsAllowances().size());
    //
    //        verify(accountStore, never()).commitAccount(ownerAccount);
    //    }
    //
    //    @Test
    //    void emptyAllowancesInStateTransitionWorks() {
    //        cryptoApproveAllowanceTxn = com.hederahashgraph.api.proto.java.TransactionBody.newBuilder()
    //                .setTransactionID(ourTxnId())
    //
    // .setCryptoApproveAllowance(com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody.newBuilder())
    //                .build();
    //
    //        given(accountStore.loadAccount(payerAccount.getId())).willReturn(payerAccount);
    //        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();
    //
    //        subject.approveAllowance(
    //                op.getCryptoAllowancesList(),
    //                op.getTokenAllowancesList(),
    //                op.getNftAllowancesList(),
    //                fromGrpcAccount(payerId).asGrpcAccount());
    //
    //        assertEquals(0, ownerAccount.getCryptoAllowances().size());
    //        assertEquals(0, ownerAccount.getFungibleTokenAllowances().size());
    //        assertEquals(0, ownerAccount.getApprovedForAllNftsAllowances().size());
    //    }
    //
    //    @Test
    //    void doesntAddAllowancesWhenAmountIsZero() {
    //        givenTxnCtxWithZeroAmount();
    //
    //        given(dynamicProperties.maxAllowanceLimitPerAccount()).willReturn(100);
    //
    //        given(accountStore.loadAccountOrFailWith(ownerAccount.getId(),
    // ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID))
    //                .willReturn(ownerAccount);
    //        given(accountStore.loadAccount(payerAccount.getId())).willReturn(payerAccount);
    //        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();
    //        given(tokenStore.loadUniqueToken(tokenId2, serial1)).willReturn(nft1);
    //        given(tokenStore.loadUniqueToken(tokenId2, serial2)).willReturn(nft2);
    //
    //        subject.approveAllowance(
    //                op.getCryptoAllowancesList(),
    //                op.getTokenAllowancesList(),
    //                op.getNftAllowancesList(),
    //                fromGrpcAccount(payerId).asGrpcAccount());
    //
    //        assertEquals(0, ownerAccount.getCryptoAllowances().size());
    //        assertEquals(0, ownerAccount.getFungibleTokenAllowances().size());
    //        assertEquals(0, ownerAccount.getApprovedForAllNftsAllowances().size());
    //
    //        verify(accountStore).commitAccount(ownerAccount);
    //    }
    //
    //    @Test
    //    void skipsTxnWhenKeyExistsAndAmountGreaterThanZero() {
    //        var ownerAccount = new com.hedera.node.app.service.mono.store.models.Account(Id.fromGrpcAccount(ownerId));
    //        setUpOwnerWithExistingKeys(ownerAccount);
    //
    //        assertEquals(1, ownerAccount.getCryptoAllowances().size());
    //        assertEquals(1, ownerAccount.getFungibleTokenAllowances().size());
    //        assertEquals(1, ownerAccount.getApprovedForAllNftsAllowances().size());
    //
    //        givenValidTxnCtx();
    //
    //        given(accountStore.loadAccount(payerAccount.getId())).willReturn(payerAccount);
    //        given(accountStore.loadAccountOrFailWith(ownerAccount.getId(),
    // ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID))
    //                .willReturn(ownerAccount);
    //        given(dynamicProperties.maxAllowanceLimitPerAccount()).willReturn(100);
    //        given(tokenStore.loadUniqueToken(tokenId2, serial1)).willReturn(nft1);
    //        given(tokenStore.loadUniqueToken(tokenId2, serial2)).willReturn(nft2);
    //
    //        subject.approveAllowance(
    //                op.getCryptoAllowancesList(),
    //                op.getTokenAllowancesList(),
    //                op.getNftAllowancesList(),
    //                fromGrpcAccount(payerId).asGrpcAccount());
    //
    //        assertEquals(1, ownerAccount.getCryptoAllowances().size());
    //        assertEquals(1, ownerAccount.getFungibleTokenAllowances().size());
    //        assertEquals(1, ownerAccount.getApprovedForAllNftsAllowances().size());
    //    }
    //
    //    @Test
    //    void checkIfApproveForAllIsSet() {
    //        final com.hederahashgraph.api.proto.java.NftAllowance nftAllowance =
    // com.hederahashgraph.api.proto.java.NftAllowance.newBuilder()
    //                .setSpender(spender1)
    //                .setOwner(ownerId)
    //                .setTokenId(token2)
    //                .addAllSerialNumbers(List.of(serial1))
    //                .build();
    //        final com.hederahashgraph.api.proto.java.NftAllowance nftAllowance1 =
    // com.hederahashgraph.api.proto.java.NftAllowance.newBuilder()
    //                .setSpender(spender1)
    //                .setOwner(ownerId)
    //                .setTokenId(token2)
    //                .setApprovedForAll(BoolValue.of(false))
    //                .addAllSerialNumbers(List.of(serial1))
    //                .build();
    //        nftAllowances.add(nftAllowance);
    //        nftAllowances.add(nftAllowance1);
    //
    //        var ownerAcccount = new
    // com.hedera.node.app.service.mono.store.models.Account(Id.fromGrpcAccount(ownerId));
    //
    //        givenValidTxnCtx();
    //
    //        given(accountStore.loadAccountOrFailWith(spenderId1, INVALID_ALLOWANCE_SPENDER_ID))
    //                .willReturn(payerAccount);
    //        ownerAcccount.setCryptoAllowances(new TreeMap<>());
    //        ownerAcccount.setFungibleTokenAllowances(new TreeMap<>());
    //        ownerAcccount.setApproveForAllNfts(new TreeSet<>());
    //        given(dynamicProperties.maxAllowanceLimitPerAccount()).willReturn(100);
    //        given(tokenStore.loadUniqueToken(tokenId2, serial1)).willReturn(nft1);
    //        given(tokenStore.loadUniqueToken(tokenId2, serial2)).willReturn(nft2);
    //
    //        subject.applyNftAllowances(nftAllowances, ownerAcccount);
    //
    //        assertEquals(1, ownerAcccount.getApprovedForAllNftsAllowances().size());
    //    }
    //
    //    @Test
    //    void overridesExistingAllowances() {
    //        givenValidTxnCtxForOverwritingAllowances();
    //        addExistingAllowances();
    //
    //        assertEquals(1, ownerAccount.getCryptoAllowances().size());
    //        assertEquals(1, ownerAccount.getFungibleTokenAllowances().size());
    //        assertEquals(2, ownerAccount.getApprovedForAllNftsAllowances().size());
    //        assertEquals(
    //                20,
    //                ownerAccount
    //                        .getCryptoAllowances()
    //                        .get(EntityNum.fromAccountId(spender1))
    //                        .intValue());
    //        assertEquals(
    //                20,
    //                ownerAccount
    //                        .getFungibleTokenAllowances()
    //                        .get(FcTokenAllowanceId.from(EntityNum.fromTokenId(token1),
    // EntityNum.fromAccountId(spender1)))
    //                        .intValue());
    //        assertTrue(ownerAccount
    //                .getApprovedForAllNftsAllowances()
    //                .contains(FcTokenAllowanceId.from(EntityNum.fromTokenId(token1),
    // EntityNum.fromAccountId(spender1))));
    //        assertTrue(ownerAccount
    //                .getApprovedForAllNftsAllowances()
    //                .contains(FcTokenAllowanceId.from(EntityNum.fromTokenId(token1),
    // EntityNum.fromAccountId(spender1))));
    //
    //        given(accountStore.loadAccount(payerAccount.getId())).willReturn(payerAccount);
    //        given(accountStore.loadAccountOrFailWith(ownerAccount.getId(),
    // ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID))
    //                .willReturn(ownerAccount);
    //        given(dynamicProperties.maxAllowanceLimitPerAccount()).willReturn(100);
    //        given(tokenStore.loadUniqueToken(tokenId2, serial1)).willReturn(nft1);
    //        given(tokenStore.loadUniqueToken(tokenId2, serial2)).willReturn(nft2);
    //
    //        subject.approveAllowance(
    //                op.getCryptoAllowancesList(),
    //                op.getTokenAllowancesList(),
    //                op.getNftAllowancesList(),
    //                fromGrpcAccount(payerId).asGrpcAccount());
    //
    //        assertEquals(1, ownerAccount.getCryptoAllowances().size());
    //        assertEquals(1, ownerAccount.getFungibleTokenAllowances().size());
    //        assertEquals(1, ownerAccount.getApprovedForAllNftsAllowances().size());
    //        assertEquals(
    //                10,
    //                ownerAccount
    //                        .getCryptoAllowances()
    //                        .get(EntityNum.fromAccountId(spender1))
    //                        .intValue());
    //        assertEquals(
    //                10,
    //                ownerAccount
    //                        .getFungibleTokenAllowances()
    //                        .get(FcTokenAllowanceId.from(EntityNum.fromTokenId(token1),
    // EntityNum.fromAccountId(spender1)))
    //                        .intValue());
    //        assertTrue(ownerAccount
    //                .getApprovedForAllNftsAllowances()
    //                .contains(FcTokenAllowanceId.from(EntityNum.fromTokenId(token1),
    // EntityNum.fromAccountId(spender1))));
    //        assertFalse(ownerAccount
    //                .getApprovedForAllNftsAllowances()
    //                .contains(FcTokenAllowanceId.from(EntityNum.fromTokenId(token2),
    // EntityNum.fromAccountId(spender1))));
    //
    //        verify(accountStore).commitAccount(ownerAccount);
    //    }

    private TransactionBody cryptoApproveAllowanceTransaction(
            final AccountID id, final boolean isWithDelegatingSpender) {
        final var transactionID = TransactionID.newBuilder().accountID(id).transactionValidStart(consensusTimestamp);
        final var allowanceTxnBody = CryptoApproveAllowanceTransactionBody.newBuilder()
                .cryptoAllowances(cryptoAllowance)
                .tokenAllowances(tokenAllowance)
                .nftAllowances(isWithDelegatingSpender ? nftAllowanceWithDelegatingSpender : nftAllowance)
                .build();
        return TransactionBody.newBuilder()
                .transactionID(transactionID)
                .cryptoApproveAllowance(allowanceTxnBody)
                .build();
    }
}
