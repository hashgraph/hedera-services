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

import static com.hedera.hapi.node.base.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.token.CryptoDeleteAllowanceTransactionBody;
import com.hedera.hapi.node.token.NftRemoveAllowance;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.impl.handlers.CryptoDeleteAllowanceHandler;
import com.hedera.node.app.service.token.impl.test.handlers.util.CryptoTokenHandlerTestBase;
import com.hedera.node.app.service.token.impl.validators.DeleteAllowanceValidator;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
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
    @Mock(strictness = LENIENT)
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
        givenStoresAndConfig(configProvider, handleContext);

        given(handleContext.configuration()).willReturn(configuration);
        given(configProvider.getConfiguration()).willReturn(versionedConfig);
    }

    @Test
    void cryptoDeleteAllowanceVanilla() throws PreCheckException {
        final var txn = cryptoDeleteAllowanceTransaction(payerId);
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

        final var txn = cryptoDeleteAllowanceTransaction(payerId);
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
        assertThat(writableNftStore.get(uniqueTokenIdSl1).spenderNumber()).isZero();
        assertThat(writableNftStore.get(uniqueTokenIdSl2).spenderNumber()).isZero();
    }

    @Test
    void canDeleteAllowancesOnTreasury() {
        writableNftStore.put(
                nftSl1.copyBuilder().spenderNumber(spenderId.accountNum()).build());
        writableNftStore.put(
                nftSl2.copyBuilder().spenderNumber(spenderId.accountNum()).build());

        final var txn = cryptoDeleteAllowanceTransaction(payerId);
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
        assertThat(writableNftStore.get(uniqueTokenIdSl1).spenderNumber()).isZero();
        assertThat(writableNftStore.get(uniqueTokenIdSl2).spenderNumber()).isZero();
    }

    @Test
    void validateIfSerialsEmpty() {
        final var nftAllowance = NftRemoveAllowance.newBuilder()
                .owner(payerId)
                .tokenId(nonFungibleTokenId)
                .serialNumbers(List.of())
                .build();
        final var txn = allowancesTxn(payerId, List.of(nftAllowance));
        assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(EMPTY_ALLOWANCES));
    }

    @Test
    void checksEmptyAllowancesInTxn() {
        final var txn = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(payerId).build())
                .cryptoDeleteAllowance(CryptoDeleteAllowanceTransactionBody.newBuilder())
                .build();
        assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(EMPTY_ALLOWANCES));
    }

    @Test
    void failsDeleteAllowancesOnInvalidTreasury() {
        writableTokenStore.put(
                nonFungibleToken.copyBuilder().treasuryAccountNumber(200L).build());
        writableNftStore.put(
                nftSl1.copyBuilder().spenderNumber(spenderId.accountNum()).build());
        writableNftStore.put(
                nftSl2.copyBuilder().spenderNumber(spenderId.accountNum()).build());

        final var txn = cryptoDeleteAllowanceTransaction(payerId);
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
        assertThat(writableNftStore.get(uniqueTokenIdSl1).spenderNumber()).isZero();
        assertThat(writableNftStore.get(uniqueTokenIdSl2).spenderNumber()).isZero();
    }

    @Test
    void doesntThrowIfAllowanceToBeDeletedDoesNotExist() {
        final var nftAllowance = NftRemoveAllowance.newBuilder()
                .tokenId(nonFungibleTokenId)
                .owner(ownerId)
                .serialNumbers(List.of(1L, 2L))
                .build();

        writableNftStore.put(
                nftSl1.copyBuilder().ownerNumber(ownerId.accountNum()).build());
        writableNftStore.put(
                nftSl2.copyBuilder().ownerNumber(ownerId.accountNum()).build());

        final var txn = txnWithAllowance(payerId, nftAllowance);
        given(handleContext.body()).willReturn(txn);

        assertThat(ownerAccount.approveForAllNftAllowances()).hasSize(1);
        assertThat(writableNftStore.get(uniqueTokenIdSl1).ownerNumber()).isEqualTo(ownerId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl2).ownerNumber()).isEqualTo(ownerId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl1).spenderNumber()).isEqualTo(0);
        assertThat(writableNftStore.get(uniqueTokenIdSl2).spenderNumber()).isEqualTo(0);

        subject.handle(handleContext);
        // No error thrown and no changes to state
        assertThat(ownerAccount.approveForAllNftAllowances()).hasSize(1);
        assertThat(writableNftStore.get(uniqueTokenIdSl1).ownerNumber()).isEqualTo(ownerId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl2).ownerNumber()).isEqualTo(ownerId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl1).spenderNumber()).isEqualTo(0);
        assertThat(writableNftStore.get(uniqueTokenIdSl2).spenderNumber()).isEqualTo(0);
    }

    @Test
    void considersPayerIfOwnerNotSpecifiedAndFailIfDoesntOwn() {
        final var nftAllowance = NftRemoveAllowance.newBuilder()
                .tokenId(nonFungibleTokenId)
                .serialNumbers(List.of(1L, 2L))
                .build();

        writableNftStore.put(
                nftSl1.copyBuilder().spenderNumber(spenderId.accountNum()).build());
        writableNftStore.put(
                nftSl2.copyBuilder().spenderNumber(spenderId.accountNum()).build());

        final var txn = txnWithAllowance(payerId, nftAllowance);
        given(handleContext.body()).willReturn(txn);

        assertThat(ownerAccount.approveForAllNftAllowances()).hasSize(1);
        assertThat(writableNftStore.get(uniqueTokenIdSl1).ownerNumber()).isEqualTo(ownerId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl2).ownerNumber()).isEqualTo(ownerId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl1).spenderNumber()).isEqualTo(spenderId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl2).spenderNumber()).isEqualTo(spenderId.accountNum());

        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO));
    }

    @Test
    void considersPayerIfOwnerNotSpecified() {
        final var nftAllowance = NftRemoveAllowance.newBuilder()
                .tokenId(nonFungibleTokenId)
                .serialNumbers(List.of(1L, 2L))
                .build();

        writableNftStore.put(nftSl1.copyBuilder()
                .ownerNumber(payerId.accountNum())
                .spenderNumber(spenderId.accountNum())
                .build());
        writableNftStore.put(nftSl2.copyBuilder()
                .ownerNumber(payerId.accountNum())
                .spenderNumber(spenderId.accountNum())
                .build());

        final var txn = txnWithAllowance(payerId, nftAllowance);
        given(handleContext.body()).willReturn(txn);

        assertThat(ownerAccount.approveForAllNftAllowances()).hasSize(1);
        assertThat(writableNftStore.get(uniqueTokenIdSl1).ownerNumber()).isEqualTo(payerId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl2).ownerNumber()).isEqualTo(payerId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl1).spenderNumber()).isEqualTo(spenderId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl2).spenderNumber()).isEqualTo(spenderId.accountNum());

        subject.handle(handleContext);

        assertThat(ownerAccount.approveForAllNftAllowances()).hasSize(1);
        assertThat(writableNftStore.get(uniqueTokenIdSl1).ownerNumber()).isEqualTo(payerId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl2).ownerNumber()).isEqualTo(payerId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl1).spenderNumber()).isZero();
        assertThat(writableNftStore.get(uniqueTokenIdSl2).spenderNumber()).isZero();
    }

    private TransactionBody cryptoDeleteAllowanceTransaction(final AccountID txnPayer) {
        final var nftAllowance = NftRemoveAllowance.newBuilder()
                .owner(ownerId)
                .tokenId(nonFungibleTokenId)
                .serialNumbers(List.of(1L, 2L))
                .build();
        return allowancesTxn(txnPayer, List.of(nftAllowance));
    }

    private TransactionBody txnWithAllowance(final AccountID id, NftRemoveAllowance allowance) {
        return allowancesTxn(id, List.of(allowance));
    }

    private TransactionBody allowancesTxn(final AccountID id, List<NftRemoveAllowance> nftAllowances) {
        final var transactionID = TransactionID.newBuilder().accountID(id).transactionValidStart(consensusTimestamp);
        final var allowanceTxnBody = CryptoDeleteAllowanceTransactionBody.newBuilder()
                .nftAllowances(nftAllowances)
                .build();
        return TransactionBody.newBuilder()
                .transactionID(transactionID)
                .cryptoDeleteAllowance(allowanceTxnBody)
                .build();
    }
}
