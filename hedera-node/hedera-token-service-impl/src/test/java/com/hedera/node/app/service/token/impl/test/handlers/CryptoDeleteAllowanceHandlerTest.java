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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.*;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.token.CryptoDeleteAllowanceTransactionBody;
import com.hedera.hapi.node.token.NftRemoveAllowance;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.config.VersionedConfigImpl;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableNftStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.*;
import com.hedera.node.app.service.token.impl.handlers.CryptoDeleteAllowanceHandler;
import com.hedera.node.app.service.token.impl.test.handlers.util.CryptoTokenHandlerTestBase;
import com.hedera.node.app.service.token.impl.validators.DeleteAllowanceValidator;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.config.ConfigProvider;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CryptoDeleteAllowanceHandlerTest extends CryptoTokenHandlerTestBase {
    @Mock
    private ConfigProvider configProvider;

    @Mock(strictness = LENIENT)
    private HandleContext handleContext;

    private CryptoDeleteAllowanceHandler subject;

    @BeforeEach
    public void setUp() {
        super.setUp();
        final var deleteAllowanceValidator = new DeleteAllowanceValidator(configProvider);
        subject = new CryptoDeleteAllowanceHandler(deleteAllowanceValidator);
        refreshWritableStores();
        givenStores();

        given(handleContext.configuration()).willReturn(configuration);
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(configuration, 1));
    }

    @Test
    void cryptoDeleteAllowanceVanilla() throws PreCheckException {
        final var txn = cryptoDeleteAllowanceTransaction(id);
        final var context = new FakePreHandleContext(readableAccountStore, txn);
        subject.preHandle(context);
        basicMetaAssertions(context, 1);
        assertEquals(key, context.payerKey());
        assertThat(context.requiredNonPayerKeys()).containsExactlyInAnyOrder(ownerKey);
    }

    @Test
    void cryptoDeleteAllowanceDoesntAddIfOwnerSameAsPayer() throws PreCheckException {
        final var txn = cryptoDeleteAllowanceTransaction(ownerId);
        final var context = new FakePreHandleContext(readableAccountStore, txn);
        subject.preHandle(context);
        basicMetaAssertions(context, 0);
        assertEquals(ownerKey, context.payerKey());
        assertIterableEquals(List.of(), context.requiredNonPayerKeys());
    }

    @Test
    void happyPathDeletesAllowances() {
        writableNftStore.put(
                nftSl1.copyBuilder().spenderNumber(spenderId.accountNum()).build());
        writableNftStore.put(
                nftSl2.copyBuilder().spenderNumber(spenderId.accountNum()).build());

        final var txn = cryptoDeleteAllowanceTransaction(id);
        given(handleContext.body()).willReturn(txn);

        assertThat(ownerAccount.approveForAllNftAllowances()).hasSize(1);
        assertThat(writableNftStore.get(uniqueTokenIdSl1).ownerNumber()).isEqualTo(ownerId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl2).ownerNumber()).isEqualTo(ownerId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl1).spenderNumber()).isEqualTo(spenderId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl2).spenderNumber()).isEqualTo(spenderId.accountNum());

        subject.handle(handleContext);

        assertThat(ownerAccount.approveForAllNftAllowances()).hasSize(1);
        assertThat(writableNftStore.get(uniqueTokenIdSl1).ownerNumber()).isEqualTo(ownerId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl2).ownerNumber()).isEqualTo(ownerId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl1).spenderNumber()).isEqualTo(0);
        assertThat(writableNftStore.get(uniqueTokenIdSl2).spenderNumber()).isEqualTo(0);
    }

    @Test
    void canDeleteAllowancesOnTreasury() {
        writableNftStore.put(
                nftSl1.copyBuilder().spenderNumber(spenderId.accountNum()).build());
        writableNftStore.put(
                nftSl2.copyBuilder().spenderNumber(spenderId.accountNum()).build());

        final var txn = cryptoDeleteAllowanceTransaction(id);
        given(handleContext.body()).willReturn(txn);

        assertThat(ownerAccount.approveForAllNftAllowances()).hasSize(1);
        assertThat(writableNftStore.get(uniqueTokenIdSl1).ownerNumber()).isEqualTo(ownerId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl2).ownerNumber()).isEqualTo(ownerId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl1).spenderNumber()).isEqualTo(spenderId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl2).spenderNumber()).isEqualTo(spenderId.accountNum());

        subject.handle(handleContext);

        assertThat(ownerAccount.approveForAllNftAllowances()).hasSize(1);
        assertThat(writableNftStore.get(uniqueTokenIdSl1).ownerNumber()).isEqualTo(ownerId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl2).ownerNumber()).isEqualTo(ownerId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl1).spenderNumber()).isEqualTo(0);
        assertThat(writableNftStore.get(uniqueTokenIdSl2).spenderNumber()).isEqualTo(0);
    }
    //
    //    @Test
    //    void failsDeleteAllowancesOnInvalidTreasury() {
    //        givenValidTxnCtx();
    //
    //        given(accountStore.loadAccount(Id.fromGrpcAccount(payerId))).willReturn(payerAccount);
    //        given(accountStore.loadAccountOrFailWith(Id.fromGrpcAccount(ownerId), INVALID_ALLOWANCE_OWNER_ID))
    //                .willReturn(ownerAccount);
    //        token2Model.setTreasury(payerAccount);
    //        given(tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(token2))).willReturn(token2Model);
    //        given(tokenStore.loadUniqueToken(Id.fromGrpcToken(token2), 12L)).willReturn(uniqueToken2);
    //        uniqueToken1.setOwner(Id.MISSING_ID);
    //        uniqueToken2.setOwner(Id.MISSING_ID);
    //
    //        Executable deleteAllowance = () -> subject.deleteAllowance(op.getNftAllowancesList(), payerId);
    //
    //        assertThrows(InvalidTransactionException.class, deleteAllowance);
    //    }
    //
    //    @Test
    //    void doesntThrowIfAllowancesDoesNotExist() {
    //        final com.hederahashgraph.api.proto.java.NftRemoveAllowance nftRemoveAllowance =
    //                com.hederahashgraph.api.proto.java.NftRemoveAllowance.newBuilder().setOwner(ownerId).build();
    //
    //        cryptoDeleteAllowanceTxn = com.hederahashgraph.api.proto.java.TransactionBody.newBuilder()
    //                .setTransactionID(ourTxnId())
    //                .setCryptoDeleteAllowance(
    //
    // com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody.newBuilder().addNftAllowances(nftRemoveAllowance))
    //                .build();
    //
    //        op = cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance();
    //
    //        given(accountStore.loadAccount(Id.fromGrpcAccount(payerId))).willReturn(payerAccount);
    //        given(accountStore.loadAccountOrFailWith(Id.fromGrpcAccount(ownerId), INVALID_ALLOWANCE_OWNER_ID))
    //                .willReturn(ownerAccount);
    //
    //        subject.deleteAllowance(op.getNftAllowancesList(), payerId);
    //
    //        verify(tokenStore, never()).persistNft(any());
    //    }
    //
    //    @Test
    //    void clearsPayerIfOwnerNotSpecified() {
    //        givenValidTxnCtxWithNoOwner();
    //        addExistingAllowances(payerAccount);
    //
    //        given(accountStore.loadAccount(payerAccount.getId())).willReturn(payerAccount);
    //        given(tokenStore.loadUniqueToken(token2Model.getId(), 12L)).willReturn(uniqueToken1);
    //        given(tokenStore.loadUniqueToken(token2Model.getId(), 10L)).willReturn(uniqueToken2);
    //        uniqueToken1.setOwner(payerAccount.getId());
    //        uniqueToken2.setOwner(payerAccount.getId());
    //
    //        assertEquals(1, payerAccount.getApprovedForAllNftsAllowances().size());
    //
    //        subject.deleteAllowance(op.getNftAllowancesList(), payerId);
    //
    //        assertEquals(1, payerAccount.getApprovedForAllNftsAllowances().size());
    //
    //        verify(tokenStore, times(2)).persistNft(any());
    //    }
    //
    //    @Test
    //    void emptyAllowancesInStateTransitionWorks() {
    //        cryptoDeleteAllowanceTxn = com.hederahashgraph.api.proto.java.TransactionBody.newBuilder()
    //                .setTransactionID(ourTxnId())
    //
    // .setCryptoDeleteAllowance(com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody.newBuilder())
    //                .build();
    //
    //        op = cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance();
    //
    //        given(accountStore.loadAccount(Id.fromGrpcAccount(payerId))).willReturn(payerAccount);
    //
    //        subject.deleteAllowance(op.getNftAllowancesList(), payerId);
    //
    //        assertEquals(0, ownerAccount.getCryptoAllowances().size());
    //        assertEquals(0, ownerAccount.getFungibleTokenAllowances().size());
    //        assertEquals(0, ownerAccount.getApprovedForAllNftsAllowances().size());
    //        verify(accountStore, never()).commitAccount(ownerAccount);
    //    }
    //
    private TransactionBody cryptoDeleteAllowanceTransaction(final AccountID id) {
        final var transactionID = TransactionID.newBuilder().accountID(id).transactionValidStart(consensusTimestamp);
        final var allowanceTxnBody = CryptoDeleteAllowanceTransactionBody.newBuilder()
                .nftAllowances(NftRemoveAllowance.newBuilder()
                        .owner(ownerId)
                        .tokenId(nonFungibleTokenId)
                        .serialNumbers(List.of(1L, 2L))
                        .build())
                .build();
        return TransactionBody.newBuilder()
                .transactionID(transactionID)
                .cryptoDeleteAllowance(allowanceTxnBody)
                .build();
    }

    private void givenStores() {
        given(handleContext.writableStore(WritableNftStore.class)).willReturn(writableNftStore);
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        given(handleContext.writableStore(WritableTokenStore.class)).willReturn(writableTokenStore);
        given(handleContext.writableStore(WritableTokenRelationStore.class)).willReturn(writableTokenRelStore);

        given(handleContext.readableStore(ReadableTokenStore.class)).willReturn(readableTokenStore);
        given(handleContext.readableStore(ReadableTokenRelationStore.class)).willReturn(readableTokenRelStore);
        given(handleContext.readableStore(ReadableAccountStore.class)).willReturn(readableAccountStore);
        given(handleContext.readableStore(ReadableNftStore.class)).willReturn(readableNftStore);
    }
}
