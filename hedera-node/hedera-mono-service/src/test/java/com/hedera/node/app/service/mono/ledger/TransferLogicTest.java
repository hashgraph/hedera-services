/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.ledger;

import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.node.app.service.mono.ledger.properties.NftProperty.SPENDER;
import static com.hedera.node.app.service.mono.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.test.mocks.TestContextValidator.TEST_VALIDATOR;
import static com.hedera.test.utils.TxnUtils.aaOf;
import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.mono.config.MockGlobalDynamicProps;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.fees.charging.FeeDistribution;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.ledger.backing.HashMapBackingAccounts;
import com.hedera.node.app.service.mono.ledger.interceptors.AccountsCommitInterceptor;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.ledger.properties.ChangeSummaryManager;
import com.hedera.node.app.service.mono.ledger.properties.NftProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenRelProperty;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.migration.HederaTokenRel;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenAdapter;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.FcTokenAllowanceId;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.hedera.node.app.service.mono.store.tokens.TokenStore;
import com.hedera.node.app.service.mono.txns.crypto.AutoCreationLogic;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hedera.test.mocks.MockAccountNumbers;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.IntConsumer;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferLogicTest {
    private TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger;
    private GlobalDynamicProperties dynamicProperties = new MockGlobalDynamicProps();
    private HederaAccountNumbers accountNums = new MockAccountNumbers();
    private final long initialBalance = 1_000_000L;
    private final long initialAllowance = 100L;
    private final AccountID revokedSpender =
            AccountID.newBuilder().setAccountNum(12346L).build();
    private final AccountID payer = AccountID.newBuilder().setAccountNum(12345L).build();
    private final AccountID owner = AccountID.newBuilder().setAccountNum(12347L).build();
    private final EntityNum payerNum = EntityNum.fromAccountId(payer);
    private final TokenID fungibleTokenID =
            TokenID.newBuilder().setTokenNum(1234L).build();
    private final TokenID anotherFungibleTokenID =
            TokenID.newBuilder().setTokenNum(12345L).build();
    private final TokenID nonFungibleTokenID =
            TokenID.newBuilder().setTokenNum(1235L).build();
    private final long initialPayerBalance = 10000L;
    private final FcTokenAllowanceId fungibleAllowanceId =
            FcTokenAllowanceId.from(EntityNum.fromTokenId(fungibleTokenID), payerNum);
    private final Id funding = new Id(0, 0, 98);
    private TreeMap<EntityNum, Long> cryptoAllowances = new TreeMap<>() {
        {
            put(payerNum, initialAllowance);
        }
    };
    private TreeMap<FcTokenAllowanceId, Long> fungibleAllowances = new TreeMap<>() {
        {
            put(fungibleAllowanceId, initialAllowance);
        }
    };
    private TreeSet<FcTokenAllowanceId> nftAllowances = new TreeSet<>() {
        {
            add(fungibleAllowanceId);
        }
    };

    @Mock
    private TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nftsLedger;

    @Mock
    private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, HederaTokenRel> tokenRelsLedger;

    @Mock
    private SideEffectsTracker sideEffectsTracker;

    @Mock
    private TokenStore tokenStore;

    @Mock
    private AutoCreationLogic autoCreationLogic;

    @Mock
    private RecordsHistorian recordsHistorian;

    @Mock
    private AccountsCommitInterceptor accountsCommitInterceptor;

    @Mock
    private TransactionContext txnCtx;

    @Mock
    private AliasManager aliasManager;

    @Mock
    private IntConsumer cryptoCreateThrottleReclaimer;

    private final FeeDistribution feeDistribution = new FeeDistribution(accountNums, dynamicProperties);

    private TransferLogic subject;

    @BeforeEach
    void setUp() {
        final var backingAccounts = new HashMapBackingAccounts();
        accountsLedger = new TransactionalLedger<>(
                AccountProperty.class, MerkleAccount::new, backingAccounts, new ChangeSummaryManager<>());
        subject = new TransferLogic(
                accountsLedger,
                nftsLedger,
                tokenRelsLedger,
                tokenStore,
                sideEffectsTracker,
                TEST_VALIDATOR,
                autoCreationLogic,
                recordsHistorian,
                txnCtx,
                aliasManager,
                feeDistribution);
    }

    @Test
    void throwsIseOnNonEmptyAliasWithNullAutoCreationLogic() {
        final var firstAmount = 1_000L;
        final var firstAlias = ByteString.copyFromUtf8("fake");
        final var inappropriateTrigger = BalanceChange.changingHbar(aliasedAa(firstAlias, firstAmount), payer);
        given(aliasManager.lookupIdBy(firstAlias)).willReturn(EntityNum.MISSING_NUM);
        given(txnCtx.activePayer()).willReturn(payer);

        accountsLedger.begin();
        accountsLedger.create(payer);

        subject = new TransferLogic(
                accountsLedger,
                nftsLedger,
                tokenRelsLedger,
                tokenStore,
                sideEffectsTracker,
                TEST_VALIDATOR,
                null,
                recordsHistorian,
                txnCtx,
                aliasManager,
                feeDistribution);

        final var triggerList = List.of(inappropriateTrigger);
        assertThrows(IllegalStateException.class, () -> subject.doZeroSum(triggerList));
    }

    @Test
    void cleansUpOnFailedAutoCreation() {
        final var mockCreation = IdUtils.asAccount("0.0.1234");
        final var firstAmount = 1_000L;
        final var firstAlias = ByteString.copyFromUtf8("fake");
        final var failingTrigger = BalanceChange.changingHbar(aliasedAa(firstAlias, firstAmount), payer);
        final var changes = List.of(failingTrigger);
        given(aliasManager.lookupIdBy(firstAlias)).willReturn(EntityNum.MISSING_NUM);

        given(autoCreationLogic.create(failingTrigger, accountsLedger, changes))
                .willReturn(Pair.of(INSUFFICIENT_ACCOUNT_BALANCE, 0L));
        accountsLedger.begin();
        accountsLedger.create(mockCreation);
        given(autoCreationLogic.reclaimPendingAliases()).willReturn(true);
        given(recordsHistorian.canTrackPrecedingChildRecords(anyInt())).willReturn(true);

        assertFailsWith(() -> subject.doZeroSum(changes), INSUFFICIENT_ACCOUNT_BALANCE);

        verify(autoCreationLogic).reclaimPendingAliases();
        assertTrue(accountsLedger.getCreatedKeys().isEmpty());
    }

    @Test
    void behavesAsExpectedOnAutoCreationWithInsufficientChildRecords() {
        final var mockCreation = IdUtils.asAccount("0.0.1234");
        final var firstAmount = 1_000L;
        final var firstAlias = ByteString.copyFromUtf8("fake");
        final var failingTrigger = BalanceChange.changingHbar(aliasedAa(firstAlias, firstAmount), payer);
        final var changes = List.of(failingTrigger);
        given(aliasManager.lookupIdBy(firstAlias)).willReturn(EntityNum.MISSING_NUM);

        accountsLedger.begin();
        accountsLedger.create(mockCreation);
        given(autoCreationLogic.reclaimPendingAliases()).willReturn(true);

        assertFailsWith(() -> subject.doZeroSum(changes), MAX_CHILD_RECORDS_EXCEEDED);

        verify(autoCreationLogic).reclaimPendingAliases();
        assertTrue(accountsLedger.getCreatedKeys().isEmpty());
        verify(recordsHistorian, never()).trackPrecedingChildRecord(anyInt(), any(), any());
    }

    @Test
    void autoCreatesWithNftTransferToAlias() {
        final var mockCreation = IdUtils.asAccount("0.0.1234");
        final var firstAlias = ByteString.copyFromUtf8("fake");
        final var transfer = NftTransfer.newBuilder()
                .setSenderAccountID(payer)
                .setReceiverAccountID(
                        AccountID.newBuilder().setAlias(firstAlias).build())
                .setSerialNumber(20L)
                .build();
        final var nftTransfer = BalanceChange.changingNftOwnership(
                Id.fromGrpcToken(nonFungibleTokenID), nonFungibleTokenID, transfer, payer);
        final var changes = List.of(nftTransfer);

        given(autoCreationLogic.create(nftTransfer, accountsLedger, changes)).willReturn(Pair.of(OK, 100L));
        accountsLedger.begin();
        accountsLedger.create(mockCreation);
        accountsLedger.create(funding.asGrpcAccount());
        accountsLedger.create(payer);
        accountsLedger.set(payer, BALANCE, initialPayerBalance);

        given(tokenStore.tryTokenChange(any())).willReturn(OK);
        given(txnCtx.activePayer()).willReturn(payer);
        given(aliasManager.lookupIdBy(firstAlias)).willReturn(EntityNum.MISSING_NUM);
        given(recordsHistorian.canTrackPrecedingChildRecords(anyInt())).willReturn(true);

        subject.doZeroSum(changes);

        verify(autoCreationLogic).create(nftTransfer, accountsLedger, changes);
        assertFalse(accountsLedger.getCreatedKeys().isEmpty());
    }

    @Test
    void autoCreatesWithFungibleTokenTransferToAlias() {
        final var mockCreation = IdUtils.asAccount("0.0.1234");
        final var firstAlias = ByteString.copyFromUtf8("fake");
        final var fungibleTransfer = BalanceChange.changingFtUnits(
                Id.fromGrpcToken(fungibleTokenID), fungibleTokenID, aliasedAa(firstAlias, 10L), payer);
        final var anotherFungibleTransfer = BalanceChange.changingFtUnits(
                Id.fromGrpcToken(anotherFungibleTokenID), anotherFungibleTokenID, aliasedAa(firstAlias, 10L), payer);

        final var changes = List.of(fungibleTransfer, anotherFungibleTransfer);

        given(autoCreationLogic.create(fungibleTransfer, accountsLedger, changes))
                .willReturn(Pair.of(OK, 100L));
        given(autoCreationLogic.create(anotherFungibleTransfer, accountsLedger, changes))
                .willReturn(Pair.of(OK, 100L));
        accountsLedger.begin();
        accountsLedger.create(mockCreation);
        accountsLedger.create(funding.asGrpcAccount());
        accountsLedger.create(payer);
        accountsLedger.set(payer, BALANCE, initialPayerBalance);

        given(tokenStore.tryTokenChange(any())).willReturn(OK);
        given(txnCtx.activePayer()).willReturn(payer);
        given(aliasManager.lookupIdBy(firstAlias)).willReturn(EntityNum.MISSING_NUM);
        given(recordsHistorian.canTrackPrecedingChildRecords(anyInt())).willReturn(true);

        subject.doZeroSum(changes);

        verify(autoCreationLogic).create(fungibleTransfer, accountsLedger, changes);
        verify(autoCreationLogic).create(anotherFungibleTransfer, accountsLedger, changes);
        assertFalse(accountsLedger.getCreatedKeys().isEmpty());
    }

    @Test
    void replacesExistingAliasesInChanges() {
        final var mockCreation = IdUtils.asAccount("0.0.1234");
        final var firstAlias = ByteString.copyFromUtf8("fake");
        final var fungibleTransfer = BalanceChange.changingFtUnits(
                Id.fromGrpcToken(fungibleTokenID), fungibleTokenID, aliasedAa(firstAlias, 10L), payer);
        final var transfer = NftTransfer.newBuilder()
                .setSenderAccountID(payer)
                .setReceiverAccountID(
                        AccountID.newBuilder().setAlias(firstAlias).build())
                .setSerialNumber(20L)
                .build();
        final var nftTransfer = BalanceChange.changingNftOwnership(
                Id.fromGrpcToken(nonFungibleTokenID), nonFungibleTokenID, transfer, payer);
        final var changes = List.of(fungibleTransfer, nftTransfer);

        given(aliasManager.lookupIdBy(firstAlias)).willReturn(payerNum);

        accountsLedger.begin();
        accountsLedger.create(mockCreation);
        accountsLedger.create(funding.asGrpcAccount());
        accountsLedger.create(payer);

        given(tokenStore.tryTokenChange(any())).willReturn(OK);
        subject.doZeroSum(changes);

        verify(autoCreationLogic, never()).create(fungibleTransfer, accountsLedger, changes);
        verify(autoCreationLogic, never()).create(nftTransfer, accountsLedger, changes);
        assertFalse(accountsLedger.getCreatedKeys().isEmpty());
    }

    @Test
    void createsAccountsAsExpected() {
        final var autoFee = 500L;
        final var firstAmount = 1_000L;
        final var secondAmount = 2_000;

        final var firstAlias = ByteString.copyFromUtf8("fake");
        final var secondAlias = ByteString.copyFromUtf8("mock");
        final var firstNewAccount = IdUtils.asAccount("0.0.1234");
        final var secondNewAccount = IdUtils.asAccount("0.0.1235");
        final var firstNewAccountNum = EntityNum.fromAccountId(firstNewAccount);
        final var secondNewAccountNum = EntityNum.fromAccountId(secondNewAccount);

        final var firstTrigger = BalanceChange.changingHbar(aliasedAa(firstAlias, firstAmount), payer);
        final var secondTrigger = BalanceChange.changingHbar(aliasedAa(secondAlias, secondAmount), payer);
        final var changes = List.of(firstTrigger, secondTrigger);

        given(autoCreationLogic.create(firstTrigger, accountsLedger, changes)).willAnswer(invocationOnMock -> {
            accountsLedger.create(firstNewAccount);
            final var change = (BalanceChange) invocationOnMock.getArgument(0);
            change.replaceNonEmptyAliasWith(firstNewAccountNum);
            change.setNewBalance(change.getAggregatedUnits());
            return Pair.of(OK, autoFee);
        });
        given(autoCreationLogic.create(secondTrigger, accountsLedger, changes)).willAnswer(invocationOnMock -> {
            accountsLedger.create(secondNewAccount);
            final var change = (BalanceChange) invocationOnMock.getArgument(0);
            change.replaceNonEmptyAliasWith(secondNewAccountNum);
            change.setNewBalance(change.getAggregatedUnits());
            return Pair.of(OK, autoFee);
        });

        final var funding = IdUtils.asAccount("0.0.98");
        accountsLedger.begin();
        accountsLedger.create(funding);
        accountsLedger.create(payer);
        accountsLedger.set(payer, BALANCE, initialPayerBalance);

        given(txnCtx.activePayer()).willReturn(payer);
        given(aliasManager.lookupIdBy(firstAlias)).willReturn(EntityNum.MISSING_NUM);
        given(aliasManager.lookupIdBy(secondAlias)).willReturn(EntityNum.MISSING_NUM);
        given(recordsHistorian.canTrackPrecedingChildRecords(anyInt())).willReturn(true);
        subject.doZeroSum(changes);

        assertEquals(2 * autoFee, (long) accountsLedger.get(funding, AccountProperty.BALANCE));
        assertEquals(initialPayerBalance - 2 * autoFee, (long) accountsLedger.get(payer, AccountProperty.BALANCE));
        assertEquals(firstAmount, (long) accountsLedger.get(firstNewAccount, AccountProperty.BALANCE));
        assertEquals(secondAmount, (long) accountsLedger.get(secondNewAccount, AccountProperty.BALANCE));
        verify(autoCreationLogic).submitRecordsTo(recordsHistorian);
    }

    @Test
    void failsIfPayerDoesntHaveEnoughBalance() {
        final var autoFee = 500L;
        final var firstAmount = 1_000L;
        final var secondAmount = 2_000;
        final var firstAlias = ByteString.copyFromUtf8("fake");
        final var secondAlias = ByteString.copyFromUtf8("mock");
        final var firstNewAccount = IdUtils.asAccount("0.0.1234");
        final var secondNewAccount = IdUtils.asAccount("0.0.1235");
        final var firstNewAccountNum = EntityNum.fromAccountId(firstNewAccount);
        final var secondNewAccountNum = EntityNum.fromAccountId(secondNewAccount);

        final var firstTrigger = BalanceChange.changingHbar(aliasedAa(firstAlias, firstAmount), payer);
        final var secondTrigger = BalanceChange.changingHbar(aliasedAa(secondAlias, secondAmount), payer);
        final var changes = List.of(firstTrigger, secondTrigger);

        given(autoCreationLogic.create(firstTrigger, accountsLedger, changes)).willAnswer(invocationOnMock -> {
            accountsLedger.create(firstNewAccount);
            final var change = (BalanceChange) invocationOnMock.getArgument(0);
            change.replaceNonEmptyAliasWith(firstNewAccountNum);
            change.setNewBalance(change.getAggregatedUnits());
            return Pair.of(OK, autoFee);
        });
        given(autoCreationLogic.create(secondTrigger, accountsLedger, changes)).willAnswer(invocationOnMock -> {
            accountsLedger.create(secondNewAccount);
            final var change = (BalanceChange) invocationOnMock.getArgument(0);
            change.replaceNonEmptyAliasWith(secondNewAccountNum);
            change.setNewBalance(change.getAggregatedUnits());
            return Pair.of(OK, autoFee);
        });

        final var funding = IdUtils.asAccount("0.0.98");
        accountsLedger.begin();
        accountsLedger.create(funding);
        accountsLedger.create(payer);

        given(txnCtx.activePayer()).willReturn(payer);
        given(aliasManager.lookupIdBy(firstAlias)).willReturn(EntityNum.MISSING_NUM);
        given(aliasManager.lookupIdBy(secondAlias)).willReturn(EntityNum.MISSING_NUM);
        given(recordsHistorian.canTrackPrecedingChildRecords(anyInt())).willReturn(true);
        final var ex = assertThrows(InvalidTransactionException.class, () -> subject.doZeroSum(changes));
        assertEquals(INSUFFICIENT_PAYER_BALANCE, ex.getResponseCode());

        assertEquals(0L, (long) accountsLedger.get(funding, AccountProperty.BALANCE));
        assertEquals(0L, (long) accountsLedger.get(payer, AccountProperty.BALANCE));
        verify(autoCreationLogic, never()).submitRecordsTo(recordsHistorian);
    }

    @Test
    void failsIfPayerDoesntHaveEnoughBalanceAfterTransfersFromHisAccount() {
        final var autoFee = 500L;
        final var payerInitBalance = 1_000L;
        final var firstAlias = ByteString.copyFromUtf8("fake");
        final var firstNewAccount = IdUtils.asAccount("0.0.1234");
        final var firstNewAccountNum = EntityNum.fromAccountId(firstNewAccount);
        final var firstTrigger = BalanceChange.changingHbar(aaOf(payer, -payerInitBalance), payer);
        final var secondTrigger = BalanceChange.changingHbar(aliasedAa(firstAlias, payerInitBalance), payer);
        final var changes = List.of(firstTrigger, secondTrigger);
        given(autoCreationLogic.create(secondTrigger, accountsLedger, changes)).willAnswer(invocationOnMock -> {
            accountsLedger.create(firstNewAccount);
            final var change = (BalanceChange) invocationOnMock.getArgument(0);
            change.replaceNonEmptyAliasWith(firstNewAccountNum);
            change.setNewBalance(change.getAggregatedUnits());
            return Pair.of(OK, autoFee);
        });
        final var funding = IdUtils.asAccount("0.0.98");
        accountsLedger.begin();
        accountsLedger.create(funding);
        accountsLedger.create(payer);
        accountsLedger.set(payer, BALANCE, payerInitBalance);
        accountsLedger.commit();
        accountsLedger.begin();

        given(txnCtx.activePayer()).willReturn(payer);
        given(aliasManager.lookupIdBy(firstAlias)).willReturn(EntityNum.MISSING_NUM);
        given(autoCreationLogic.reclaimPendingAliases()).willReturn(true);
        given(recordsHistorian.canTrackPrecedingChildRecords(anyInt())).willReturn(true);

        final var ex = assertThrows(InvalidTransactionException.class, () -> subject.doZeroSum(changes));

        assertEquals(INSUFFICIENT_PAYER_BALANCE, ex.getResponseCode());
        assertEquals(0L, (long) accountsLedger.get(funding, AccountProperty.BALANCE));
        assertEquals(payerInitBalance, (long) accountsLedger.get(payer, AccountProperty.BALANCE));
        verify(autoCreationLogic, never()).submitRecordsTo(recordsHistorian);
        verify(autoCreationLogic).reclaimPendingAliases();
    }

    @Test
    void happyPathHbarAllowance() {
        setUpAccountWithAllowances();
        final var change = BalanceChange.changingHbar(allowanceAA(owner, -50L), payer);
        given(txnCtx.activePayer()).willReturn(payer);

        accountsLedger.begin();
        subject.doZeroSum(List.of(change));

        updateAllowanceMaps();
        assertEquals(initialBalance - 50L, accountsLedger.get(owner, AccountProperty.BALANCE));
        assertEquals(initialAllowance - 50L, cryptoAllowances.get(payerNum));
    }

    @Test
    void happyPathFungibleAllowance() {
        setUpAccountWithAllowances();
        final var change = BalanceChange.changingFtUnits(
                Id.fromGrpcToken(fungibleTokenID), fungibleTokenID, allowanceAA(owner, -50L), payer);
        given(tokenStore.tryTokenChange(change)).willReturn(OK);

        accountsLedger.begin();
        assertDoesNotThrow(() -> subject.doZeroSum(List.of(change)));

        updateAllowanceMaps();
        assertEquals(initialAllowance - 50L, fungibleAllowances.get(fungibleAllowanceId));
    }

    @Test
    void happyPathNFTAllowance() {
        setUpAccountWithAllowances();
        final var nftId1 = NftId.withDefaultShardRealm(nonFungibleTokenID.getTokenNum(), 1L);
        final var nftId2 = NftId.withDefaultShardRealm(nonFungibleTokenID.getTokenNum(), 2L);
        final var change1 = BalanceChange.changingNftOwnership(
                Id.fromGrpcToken(nonFungibleTokenID),
                nonFungibleTokenID,
                allowanceNftTransfer(owner, revokedSpender, 1L),
                payer);
        final var change2 = BalanceChange.changingNftOwnership(
                Id.fromGrpcToken(fungibleTokenID),
                fungibleTokenID,
                allowanceNftTransfer(owner, revokedSpender, 123L),
                payer);
        final var change3 = BalanceChange.changingNftOwnership(
                Id.fromGrpcToken(nonFungibleTokenID),
                nonFungibleTokenID,
                nftTransfer(owner, revokedSpender, 2L),
                payer);

        given(tokenStore.tryTokenChange(change1)).willReturn(OK);
        given(tokenStore.tryTokenChange(change2)).willReturn(OK);
        given(tokenStore.tryTokenChange(change3)).willReturn(OK);
        given(nftsLedger.get(nftId1, SPENDER)).willReturn(EntityId.fromGrpcAccountId(payer));

        accountsLedger.begin();
        assertDoesNotThrow(() -> subject.doZeroSum(List.of(change1, change2, change3)));

        updateAllowanceMaps();
        assertTrue(nftAllowances.contains(fungibleAllowanceId));
        verify(nftsLedger).set(nftId1, SPENDER, MISSING_ENTITY_ID);
        verify(nftsLedger).set(nftId2, SPENDER, MISSING_ENTITY_ID);
    }

    private AccountAmount aliasedAa(final ByteString alias, final long amount) {
        return AccountAmount.newBuilder()
                .setAccountID(AccountID.newBuilder().setAlias(alias))
                .setAmount(amount)
                .build();
    }

    private AccountAmount allowanceAA(final AccountID accountID, final long amount) {
        return AccountAmount.newBuilder()
                .setAccountID(accountID)
                .setAmount(amount)
                .setIsApproval(true)
                .build();
    }

    private NftTransfer allowanceNftTransfer(final AccountID sender, final AccountID receiver, final long serialNum) {
        return NftTransfer.newBuilder()
                .setIsApproval(true)
                .setSenderAccountID(sender)
                .setReceiverAccountID(receiver)
                .setSerialNumber(serialNum)
                .build();
    }

    private NftTransfer nftTransfer(final AccountID sender, final AccountID receiver, final long serialNum) {
        return NftTransfer.newBuilder()
                .setIsApproval(false)
                .setSenderAccountID(sender)
                .setReceiverAccountID(receiver)
                .setSerialNumber(serialNum)
                .build();
    }

    private void setUpAccountWithAllowances() {
        accountsLedger.setCommitInterceptor(accountsCommitInterceptor);
        accountsLedger.begin();
        accountsLedger.create(owner);
        accountsLedger.set(owner, AccountProperty.CRYPTO_ALLOWANCES, cryptoAllowances);
        accountsLedger.set(owner, AccountProperty.FUNGIBLE_TOKEN_ALLOWANCES, fungibleAllowances);
        accountsLedger.set(owner, AccountProperty.APPROVE_FOR_ALL_NFTS_ALLOWANCES, nftAllowances);
        accountsLedger.set(owner, AccountProperty.BALANCE, initialBalance);
        accountsLedger.commit();
    }

    private void updateAllowanceMaps() {
        cryptoAllowances =
                new TreeMap<>((Map<EntityNum, Long>) accountsLedger.get(owner, AccountProperty.CRYPTO_ALLOWANCES));
        fungibleAllowances = new TreeMap<>(
                (Map<FcTokenAllowanceId, Long>) accountsLedger.get(owner, AccountProperty.FUNGIBLE_TOKEN_ALLOWANCES));
        nftAllowances = new TreeSet<>(
                (Set<FcTokenAllowanceId>) accountsLedger.get(owner, AccountProperty.APPROVE_FOR_ALL_NFTS_ALLOWANCES));
    }
}
