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

import static com.hedera.hapi.node.base.ResponseCodeEnum.*;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.common.UniqueTokenId;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.token.CryptoAllowance;
import com.hedera.hapi.node.token.CryptoApproveAllowanceTransactionBody;
import com.hedera.hapi.node.token.NftAllowance;
import com.hedera.hapi.node.token.TokenAllowance;
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
import com.hedera.node.app.spi.workflows.HandleException;
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

    @Mock(strictness = Strictness.LENIENT)
    private ConfigProvider configProvider;

    @Mock(strictness = Strictness.LENIENT)
    private HandleContext handleContext;

    private Configuration configuration;

    private CryptoApproveAllowanceHandler subject;

    @BeforeEach
    public void setUp() {
        super.setUp();
        refreshWritableStores();
        validator = new ApproveAllowanceValidator(configProvider);
        configuration = new HederaTestConfigBuilder().getOrCreateConfig();
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(configuration, 1));
        given(handleContext.configuration()).willReturn(configuration);
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
        final var txn = cryptoApproveAllowanceTransaction(
                id, false, List.of(cryptoAllowance), List.of(tokenAllowance), List.of(nftAllowance));
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

        final var txn = cryptoApproveAllowanceTransaction(
                id, false, List.of(cryptoAllowance), List.of(tokenAllowance), List.of(nftAllowance));
        final var context = new FakePreHandleContext(readableAccountStore, txn);
        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_ALLOWANCE_OWNER_ID);
    }

    @Test
    void cryptoApproveAllowanceDoesntAddIfOwnerSameAsPayer() throws PreCheckException {
        final var txn = cryptoApproveAllowanceTransaction(
                ownerId, false, List.of(cryptoAllowance), List.of(tokenAllowance), List.of(nftAllowance));
        final var context = new FakePreHandleContext(readableAccountStore, txn);
        subject.preHandle(context);
        basicMetaAssertions(context, 0);
        assertEquals(ownerKey, context.payerKey());
        assertThat(context.requiredNonPayerKeys()).isEmpty();
    }

    @Test
    void cryptoApproveAllowanceAddsDelegatingSpender() throws PreCheckException {
        final var txn = cryptoApproveAllowanceTransaction(
                id, true, List.of(cryptoAllowance), List.of(tokenAllowance), List.of(nftAllowance));
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

        final var txn = cryptoApproveAllowanceTransaction(
                id, true, List.of(cryptoAllowance), List.of(tokenAllowance), List.of(nftAllowance));
        final var context = new FakePreHandleContext(readableAccountStore, txn);
        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_DELEGATING_SPENDER);
    }

    @Test
    void happyPathAddsAllowances() {
        writableAccountStore.put(ownerAccount
                .copyBuilder()
                .cryptoAllowances(List.of())
                .tokenAllowances(List.of())
                .approveForAllNftAllowances(List.of())
                .build());
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);

        final var txn = cryptoApproveAllowanceTransaction(
                id, false, List.of(cryptoAllowance), List.of(tokenAllowance), List.of(nftAllowanceWithApproveForALl));
        given(handleContext.body()).willReturn(txn);
        final var existingOwner = writableAccountStore.getAccountById(ownerId);

        assertThat(existingOwner.cryptoAllowances().size()).isEqualTo(0);
        assertThat(existingOwner.tokenAllowances().size()).isEqualTo(0);
        assertThat(existingOwner.approveForAllNftAllowances().size()).isEqualTo(0);

        assertThat(writableNftStore.get(uniqueTokenIdSl1)).isNotNull();
        assertThat(writableNftStore.get(uniqueTokenIdSl2)).isNotNull();
        assertThat(writableNftStore.get(uniqueTokenIdSl1).ownerNumber()).isEqualTo(ownerId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl2).ownerNumber()).isEqualTo(ownerId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl1).spenderNumber()).isEqualTo(0);
        assertThat(writableNftStore.get(uniqueTokenIdSl2).spenderNumber()).isEqualTo(0);

        subject.handle(handleContext);

        final var modifiedOwner = writableAccountStore.getAccountById(ownerId);
        assertThat(modifiedOwner.cryptoAllowances().size()).isEqualTo(1);
        assertThat(modifiedOwner.tokenAllowances().size()).isEqualTo(1);
        assertThat(modifiedOwner.cryptoAllowances().get(0).spenderNum()).isEqualTo(spenderId.accountNum());
        assertThat(modifiedOwner.cryptoAllowances().get(0).amount()).isEqualTo(10);
        assertThat(modifiedOwner.tokenAllowances().get(0).spenderNum()).isEqualTo(spenderId.accountNum());
        assertThat(modifiedOwner.tokenAllowances().get(0).amount()).isEqualTo(10);
        assertThat(modifiedOwner.tokenAllowances().get(0).tokenNum()).isEqualTo(fungibleTokenId.tokenNum());
        assertThat(modifiedOwner.approveForAllNftAllowances().size()).isEqualTo(1);
        assertThat(modifiedOwner.approveForAllNftAllowances().get(0).spenderNum())
                .isEqualTo(spenderId.accountNum());
        assertThat(modifiedOwner.approveForAllNftAllowances().get(0).tokenNum())
                .isEqualTo(nonFungibleTokenId.tokenNum());

        assertThat(writableNftStore.get(uniqueTokenIdSl1)).isNotNull();
        assertThat(writableNftStore.get(uniqueTokenIdSl2)).isNotNull();
        assertThat(writableNftStore.get(uniqueTokenIdSl1).ownerNumber()).isEqualTo(ownerId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl2).ownerNumber()).isEqualTo(ownerId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl1).spenderNumber()).isEqualTo(spenderId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl2).spenderNumber()).isEqualTo(spenderId.accountNum());
    }

    @Test
    void happyPathForUpdatingAllowances() {
        final var txn = cryptoApproveAllowanceTransaction(
                id, false, List.of(cryptoAllowance), List.of(tokenAllowance), List.of(nftAllowance));
        given(handleContext.body()).willReturn(txn);
        final var existingOwner = writableAccountStore.getAccountById(ownerId);

        assertThat(existingOwner.cryptoAllowances().size()).isEqualTo(1);
        assertThat(existingOwner.tokenAllowances().size()).isEqualTo(1);
        assertThat(existingOwner.cryptoAllowances().get(0).spenderNum()).isEqualTo(spenderId.accountNum());
        assertThat(existingOwner.cryptoAllowances().get(0).amount()).isEqualTo(100);
        assertThat(existingOwner.tokenAllowances().get(0).spenderNum()).isEqualTo(spenderId.accountNum());
        assertThat(existingOwner.tokenAllowances().get(0).amount()).isEqualTo(100);
        assertThat(existingOwner.tokenAllowances().get(0).tokenNum()).isEqualTo(fungibleTokenId.tokenNum());
        assertThat(existingOwner.approveForAllNftAllowances().size()).isEqualTo(1);
        assertThat(existingOwner.approveForAllNftAllowances().get(0).spenderNum())
                .isEqualTo(spenderId.accountNum());
        assertThat(existingOwner.approveForAllNftAllowances().get(0).tokenNum())
                .isEqualTo(nonFungibleTokenId.tokenNum());

        assertThat(writableNftStore.get(uniqueTokenIdSl1)).isNotNull();
        assertThat(writableNftStore.get(uniqueTokenIdSl2)).isNotNull();
        assertThat(writableNftStore.get(uniqueTokenIdSl1).ownerNumber()).isEqualTo(ownerId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl2).ownerNumber()).isEqualTo(ownerId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl1).spenderNumber()).isEqualTo(0);
        assertThat(writableNftStore.get(uniqueTokenIdSl2).spenderNumber()).isEqualTo(0);

        subject.handle(handleContext);

        final var modifiedOwner = writableAccountStore.getAccountById(ownerId);
        assertThat(modifiedOwner.cryptoAllowances().size()).isEqualTo(1);
        assertThat(modifiedOwner.tokenAllowances().size()).isEqualTo(1);
        assertThat(modifiedOwner.cryptoAllowances().get(0).spenderNum()).isEqualTo(spenderId.accountNum());
        assertThat(modifiedOwner.cryptoAllowances().get(0).amount()).isEqualTo(10);
        assertThat(modifiedOwner.tokenAllowances().get(0).spenderNum()).isEqualTo(spenderId.accountNum());
        assertThat(modifiedOwner.tokenAllowances().get(0).amount()).isEqualTo(10);
        assertThat(modifiedOwner.tokenAllowances().get(0).tokenNum()).isEqualTo(fungibleTokenId.tokenNum());
        assertThat(modifiedOwner.approveForAllNftAllowances().size()).isEqualTo(1);
        assertThat(modifiedOwner.approveForAllNftAllowances().get(0).spenderNum())
                .isEqualTo(spenderId.accountNum());
        assertThat(modifiedOwner.approveForAllNftAllowances().get(0).tokenNum())
                .isEqualTo(nonFungibleTokenId.tokenNum());

        assertThat(writableNftStore.get(uniqueTokenIdSl1)).isNotNull();
        assertThat(writableNftStore.get(uniqueTokenIdSl2)).isNotNull();
        assertThat(writableNftStore.get(uniqueTokenIdSl1).ownerNumber()).isEqualTo(ownerId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl2).ownerNumber()).isEqualTo(ownerId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl1).spenderNumber()).isEqualTo(spenderId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl2).spenderNumber()).isEqualTo(spenderId.accountNum());
    }

    @Test
    void settingApproveForAllToFalseRemovesAllowance() {
        final var txn = cryptoApproveAllowanceTransaction(
                id,
                false,
                List.of(),
                List.of(),
                List.of(nftAllowance.copyBuilder().approvedForAll(Boolean.FALSE).build()));
        given(handleContext.body()).willReturn(txn);
        final var existingOwner = writableAccountStore.getAccountById(ownerId);

        assertThat(existingOwner.approveForAllNftAllowances().size()).isEqualTo(1);
        assertThat(writableNftStore.get(uniqueTokenIdSl1).ownerNumber()).isEqualTo(ownerId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl2).ownerNumber()).isEqualTo(ownerId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl1).spenderNumber()).isEqualTo(0);
        assertThat(writableNftStore.get(uniqueTokenIdSl2).spenderNumber()).isEqualTo(0);

        subject.handle(handleContext);

        final var modifiedOwner = writableAccountStore.getAccountById(ownerId);

        assertThat(modifiedOwner.approveForAllNftAllowances().size()).isEqualTo(0);
        assertThat(writableNftStore.get(uniqueTokenIdSl1).ownerNumber()).isEqualTo(ownerId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl2).ownerNumber()).isEqualTo(ownerId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl1).spenderNumber()).isEqualTo(spenderId.accountNum());
        assertThat(writableNftStore.get(uniqueTokenIdSl2).spenderNumber()).isEqualTo(spenderId.accountNum());
    }

    @Test
    void failsIfSenderDoesntOwnNFTSerial() {
        final var txn = cryptoApproveAllowanceTransaction(
                id,
                false,
                List.of(cryptoAllowance.copyBuilder().owner(AccountID.DEFAULT).build()),
                List.of(tokenAllowance.copyBuilder().owner(AccountID.DEFAULT).build()),
                List.of(nftAllowance.copyBuilder().owner(AccountID.DEFAULT).build()));

        given(handleContext.body()).willReturn(txn);
        final var payer = writableAccountStore.getAccountById(id);

        assertEquals(0, payer.cryptoAllowances().size());
        assertEquals(0, payer.tokenAllowances().size());
        assertEquals(0, payer.approveForAllNftAllowances().size());

        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO));

        final var modifiedPayer = writableAccountStore.getAccountById(id);

        assertEquals(0, modifiedPayer.cryptoAllowances().size());
        assertEquals(0, modifiedPayer.tokenAllowances().size());
        assertEquals(0, modifiedPayer.approveForAllNftAllowances().size());
    }

    @Test
    void treatsPayerAsOwnerIfOwnerNotSet() {
        // change the state to have the payer as owner for NFTs for a passing test.
        // If not it fails since those NFTs are not owned by the payer.
        writableNftState = emptyWritableNftStateBuilder()
                .value(
                        uniqueTokenIdSl1,
                        nftSl1.copyBuilder().ownerNumber(accountNum).build())
                .value(
                        uniqueTokenIdSl2,
                        nftSl2.copyBuilder().ownerNumber(accountNum).build())
                .build();
        given(writableStates.<UniqueTokenId, Nft>get(NFTS)).willReturn(writableNftState);
        writableNftStore = new WritableUniqueTokenStore(writableStates);

        final var txn = cryptoApproveAllowanceTransaction(
                id,
                false,
                List.of(cryptoAllowance.copyBuilder().owner(AccountID.DEFAULT).build()),
                List.of(tokenAllowance.copyBuilder().owner(AccountID.DEFAULT).build()),
                List.of(nftAllowance.copyBuilder().owner(AccountID.DEFAULT).build()));

        given(handleContext.body()).willReturn(txn);
        final var payer = writableAccountStore.getAccountById(id);

        assertThat(payer.cryptoAllowances()).hasSize(1);
        assertThat(payer.tokenAllowances()).hasSize(1);
        assertThat(payer.approveForAllNftAllowances()).hasSize(1);

        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO));

        final var modifiedPayer = writableAccountStore.getAccountById(id);

        final var newCryptoAllowances = modifiedPayer.cryptoAllowances();
        final var newTokenAllowances = modifiedPayer.tokenAllowances();
        final var newNftAllowances = modifiedPayer.approveForAllNftAllowances();

        assertThat(newCryptoAllowances).hasSize(1);
        assertThat(newTokenAllowances).hasSize(1);
        assertThat(newNftAllowances).hasSize(1);

        assertThat(newCryptoAllowances.get(0).spenderNum()).isEqualTo(spenderId.accountNum());
        assertThat(newTokenAllowances.get(0).spenderNum()).isEqualTo(spenderId.accountNum());
        assertThat(newNftAllowances.get(0).spenderNum()).isEqualTo(spenderId.accountNum());
    }

    @Test
    void checksIfAllowancesExceedLimit() {
        configuration = new HederaTestConfigBuilder()
                .withValue("hedera.allowances.maxAccountLimit", 2)
                .getOrCreateConfig();
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(configuration, 1));
        given(handleContext.configuration()).willReturn(configuration);

        final var txn = cryptoApproveAllowanceTransaction(
                id, false, List.of(cryptoAllowance), List.of(tokenAllowance), List.of(nftAllowance));
        given(handleContext.body()).willReturn(txn);

        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(MAX_ALLOWANCES_EXCEEDED));
    }

    @Test
    void emptyAllowanceListInTransactionFails() {
        final var txn = cryptoApproveAllowanceTransaction(id, false, List.of(), List.of(), List.of());
        given(handleContext.body()).willReturn(txn);
        // Two know accounts we are using for these tests. Initial allowances
        final var existingPayer = writableAccountStore.getAccountById(id);
        final var existingOwner = writableAccountStore.getAccountById(ownerId);
        assertThat(existingPayer.cryptoAllowances()).hasSize(0);
        assertThat(existingPayer.tokenAllowances()).hasSize(0);
        assertThat(existingPayer.approveForAllNftAllowances()).hasSize(0);
        assertThat(existingOwner.cryptoAllowances()).hasSize(1);
        assertThat(existingOwner.tokenAllowances()).hasSize(1);
        assertThat(existingOwner.approveForAllNftAllowances()).hasSize(1);

        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(EMPTY_ALLOWANCES));

        // After handle allowances are not modified
        final var afterHandlePayer = writableAccountStore.getAccountById(id);
        final var afterHandleOwner = writableAccountStore.getAccountById(ownerId);
        assertThat(afterHandlePayer.cryptoAllowances()).hasSize(0);
        assertThat(afterHandlePayer.tokenAllowances()).hasSize(0);
        assertThat(afterHandlePayer.approveForAllNftAllowances()).hasSize(0);
        assertThat(afterHandleOwner.cryptoAllowances()).hasSize(1);
        assertThat(afterHandleOwner.tokenAllowances()).hasSize(1);
        assertThat(afterHandleOwner.approveForAllNftAllowances()).hasSize(1);
    }

    @Test
    void newAllowancesWithAmountZeroAreNotAdded() {
        writableAccountStore.put(ownerAccount
                .copyBuilder()
                .cryptoAllowances(List.of())
                .tokenAllowances(List.of())
                .approveForAllNftAllowances(List.of())
                .build());
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);

        final var txn = cryptoApproveAllowanceTransaction(
                id,
                false,
                List.of(cryptoAllowance.copyBuilder().amount(0).build()),
                List.of(tokenAllowance.copyBuilder().amount(0).build()),
                List.of());
        given(handleContext.body()).willReturn(txn);
        final var existingOwner = writableAccountStore.getAccountById(ownerId);

        assertThat(existingOwner.cryptoAllowances().size()).isEqualTo(0);
        assertThat(existingOwner.tokenAllowances().size()).isEqualTo(0);
        assertThat(existingOwner.approveForAllNftAllowances().size()).isEqualTo(0);

        subject.handle(handleContext);

        final var modifiedOwner = writableAccountStore.getAccountById(ownerId);
        assertThat(modifiedOwner.cryptoAllowances().size()).isEqualTo(0);
        assertThat(modifiedOwner.tokenAllowances().size()).isEqualTo(0);
        assertThat(existingOwner.approveForAllNftAllowances().size()).isEqualTo(0);
    }

    @Test
    void existingAllowancesDeletedWithAmountZero() {
        final var txn = cryptoApproveAllowanceTransaction(
                id,
                false,
                List.of(cryptoAllowance.copyBuilder().amount(0).build()),
                List.of(tokenAllowance.copyBuilder().amount(0).build()),
                List.of());
        given(handleContext.body()).willReturn(txn);
        final var existingOwner = writableAccountStore.getAccountById(ownerId);

        assertThat(existingOwner.cryptoAllowances().size()).isEqualTo(1);
        assertThat(existingOwner.tokenAllowances().size()).isEqualTo(1);

        subject.handle(handleContext);

        final var modifiedOwner = writableAccountStore.getAccountById(ownerId);
        assertThat(modifiedOwner.cryptoAllowances().size()).isEqualTo(0);
        assertThat(modifiedOwner.tokenAllowances().size()).isEqualTo(0);
    }

    private TransactionBody cryptoApproveAllowanceTransaction(
            final AccountID id,
            final boolean isWithDelegatingSpender,
            final List<CryptoAllowance> cryptoAllowance,
            final List<TokenAllowance> tokenAllowance,
            final List<NftAllowance> nftAllowance) {
        final var transactionID = TransactionID.newBuilder().accountID(id).transactionValidStart(consensusTimestamp);
        final var allowanceTxnBody = CryptoApproveAllowanceTransactionBody.newBuilder()
                .cryptoAllowances(cryptoAllowance)
                .tokenAllowances(tokenAllowance)
                .nftAllowances(isWithDelegatingSpender ? List.of(nftAllowanceWithDelegatingSpender) : nftAllowance)
                .build();
        return TransactionBody.newBuilder()
                .transactionID(transactionID)
                .cryptoApproveAllowance(allowanceTxnBody)
                .build();
    }
}
