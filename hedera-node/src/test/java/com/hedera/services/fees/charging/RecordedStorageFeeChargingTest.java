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
package com.hedera.services.fees.charging;

import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_ACCOUNT_ID;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.records.TxnAwareRecordsHistorian.DEFAULT_SOURCE_ID;
import static com.hedera.test.utils.TxnUtils.assertExhaustsResourceLimit;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_BALANCES_FOR_STORAGE_RENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.services.config.AccountNumbers;
import com.hedera.services.config.MockAccountNumbers;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.ledger.backing.HashMapBackingAccounts;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.KvUsageInfo;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordedStorageFeeChargingTest {
    private static final int FREE_TIER_LIMIT = 100;
    private static final long REFERENCE_LIFETIME = 2592000L;
    private static final long MAX_TOTAL_SLOTS = 500_000_000L;
    private static final ContractStoragePriceTiers STORAGE_PRICE_TIERS =
            ContractStoragePriceTiers.from(
                    "10til50M,50til100M,100til150M,200til200M,500til250M,700til300M,1000til350M,2000til400M,5000til450M,10000til500M",
                    FREE_TIER_LIMIT,
                    MAX_TOTAL_SLOTS,
                    REFERENCE_LIFETIME);

    @Mock private EntityCreator creator;
    @Mock private HbarCentExchange exchange;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private TransactionContext txnCtx;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;

    private RecordedStorageFeeCharging subject;

    private final AccountNumbers numbers = new MockAccountNumbers();

    @BeforeEach
    void setUp() {
        final var syntheticTxnFactory = new SyntheticTxnFactory(dynamicProperties);
        final var feeDistribution = new FeeDistribution(numbers, dynamicProperties);
        subject =
                new RecordedStorageFeeCharging(
                        creator,
                        feeDistribution,
                        exchange,
                        recordsHistorian,
                        txnCtx,
                        syntheticTxnFactory,
                        dynamicProperties);
    }

    @Test
    void createsNoRecordWithNothingToDo() {
        subject.chargeStorageRent(NUM_SLOTS_USED, Collections.emptyMap(), accountsLedger);
        verifyNoInteractions(accountsLedger);
    }

    @Test
    void doesNothingIfWithinPromotionalUsage() {
        final var tierWithPromotionalOffer =
                ContractStoragePriceTiers.from(
                        "0til50M,50til100M", FREE_TIER_LIMIT, 50_000_000, REFERENCE_LIFETIME);
        given(dynamicProperties.storagePriceTiers()).willReturn(tierWithPromotionalOffer);
        final Map<Long, KvUsageInfo> usageInfos = new LinkedHashMap<>();
        usageInfos.put(aContract.getAccountNum(), nonFreeUsageFor(+2));
        usageInfos.put(bContract.getAccountNum(), nonFreeUsageFor(-1));
        usageInfos.put(cContract.getAccountNum(), nonFreeUsageFor(+4));

        subject.chargeStorageRent(50_000_000L, usageInfos, accountsLedger);
        verifyNoInteractions(accountsLedger);
    }

    @Test
    void chargesOnlyPositiveDeltasWithAutoRenewAccountPriority() {
        givenStandardSetup();
        final Map<Long, KvUsageInfo> usageInfos = new LinkedHashMap<>();
        usageInfos.put(aContract.getAccountNum(), nonFreeUsageFor(+2));
        usageInfos.put(bContract.getAccountNum(), nonFreeUsageFor(-1));
        usageInfos.put(cContract.getAccountNum(), nonFreeUsageFor(+4));
        final var expectedACharge =
                STORAGE_PRICE_TIERS.priceOfPendingUsage(
                        someRate,
                        NUM_SLOTS_USED,
                        REFERENCE_LIFETIME,
                        usageInfos.get(aContract.getAccountNum()));
        final var expectedCCharge =
                STORAGE_PRICE_TIERS.priceOfPendingUsage(
                        someRate,
                        NUM_SLOTS_USED,
                        cExpiry,
                        usageInfos.get(cContract.getAccountNum()));
        given(accountsLedger.get(funding, BALANCE)).willReturn(0L).willReturn(expectedACharge);

        givenChargeableContract(aContract, -1, REFERENCE_LIFETIME, anAutoRenew);
        givenAutoRenew(anAutoRenew, expectedACharge + 1);
        givenChargeableContract(cContract, expectedCCharge + 2, cExpiry, null);

        subject.chargeStorageRent(NUM_SLOTS_USED, usageInfos, accountsLedger);

        verify(accountsLedger).set(anAutoRenew, BALANCE, 1L);
        verify(accountsLedger, never()).set(eq(aContract), eq(BALANCE), anyLong());
        verify(accountsLedger).set(cContract, BALANCE, 2L);
        verify(accountsLedger).set(funding, BALANCE, expectedACharge);
        verify(accountsLedger).set(funding, BALANCE, expectedACharge + expectedCCharge);
    }

    @Test
    void fallsBackToContractIfAutoRenewCannotCover() {
        givenStandardInternalSetup();
        final Map<Long, KvUsageInfo> usageInfos =
                Map.of(aContract.getAccountNum(), nonFreeUsageFor(+181));
        final var expectedACharge =
                STORAGE_PRICE_TIERS.priceOfPendingUsage(
                        someRate,
                        NUM_SLOTS_USED,
                        REFERENCE_LIFETIME,
                        usageInfos.get(aContract.getAccountNum()));
        final var autoRenewBalance = expectedACharge / 2;
        final var contractBalance = expectedACharge / 2 + 1;
        given(accountsLedger.get(funding, BALANCE)).willReturn(0L).willReturn(autoRenewBalance);

        givenChargeableContract(aContract, contractBalance, REFERENCE_LIFETIME, anAutoRenew);
        givenAutoRenew(anAutoRenew, autoRenewBalance);

        subject.chargeStorageFeesInternal(
                NUM_SLOTS_USED, usageInfos, STORAGE_PRICE_TIERS, accountsLedger);

        verify(accountsLedger).set(anAutoRenew, BALANCE, 0L);
        verify(accountsLedger).set(aContract, BALANCE, 1L);
        verify(accountsLedger).set(funding, BALANCE, autoRenewBalance);
        verify(accountsLedger).set(funding, BALANCE, expectedACharge);
    }

    @Test
    void fallsBackToContractIfAutoRenewMissing() {
        givenStandardInternalSetup();
        final Map<Long, KvUsageInfo> usageInfos =
                Map.of(aContract.getAccountNum(), nonFreeUsageFor(+181));
        final var expectedACharge =
                STORAGE_PRICE_TIERS.priceOfPendingUsage(
                        someRate,
                        NUM_SLOTS_USED,
                        REFERENCE_LIFETIME,
                        usageInfos.get(aContract.getAccountNum()));
        final var contractBalance = expectedACharge + 1;
        given(accountsLedger.get(funding, BALANCE)).willReturn(0L);

        givenChargeableContract(aContract, contractBalance, REFERENCE_LIFETIME, anAutoRenew);
        given(accountsLedger.contains(anAutoRenew)).willReturn(false);

        subject.chargeStorageFeesInternal(
                NUM_SLOTS_USED, usageInfos, STORAGE_PRICE_TIERS, accountsLedger);

        verify(accountsLedger).set(aContract, BALANCE, 1L);
        verify(accountsLedger).set(funding, BALANCE, expectedACharge);
    }

    @Test
    void fallsBackToContractIfAutoRenewDeleted() {
        givenStandardInternalSetup();
        final Map<Long, KvUsageInfo> usageInfos =
                Map.of(aContract.getAccountNum(), nonFreeUsageFor(+181));
        final var expectedACharge =
                STORAGE_PRICE_TIERS.priceOfPendingUsage(
                        someRate,
                        NUM_SLOTS_USED,
                        REFERENCE_LIFETIME,
                        usageInfos.get(aContract.getAccountNum()));
        final var contractBalance = expectedACharge + 1;
        given(accountsLedger.get(funding, BALANCE)).willReturn(0L);

        givenChargeableContract(aContract, contractBalance, REFERENCE_LIFETIME, anAutoRenew);
        given(accountsLedger.contains(anAutoRenew)).willReturn(true);
        given(accountsLedger.get(anAutoRenew, IS_DELETED)).willReturn(true);

        subject.chargeStorageFeesInternal(
                NUM_SLOTS_USED, usageInfos, STORAGE_PRICE_TIERS, accountsLedger);

        verify(accountsLedger).set(aContract, BALANCE, 1L);
        verify(accountsLedger).set(funding, BALANCE, expectedACharge);
    }

    @Test
    void failsIfFeesCannotBePaid() {
        givenStandardInternalSetup();
        final Map<Long, KvUsageInfo> usageInfos =
                Map.of(aContract.getAccountNum(), nonFreeUsageFor(+181));
        final var expectedACharge =
                STORAGE_PRICE_TIERS.priceOfPendingUsage(
                        someRate,
                        NUM_SLOTS_USED,
                        REFERENCE_LIFETIME,
                        usageInfos.get(aContract.getAccountNum()));
        final var autoRenewBalance = expectedACharge / 2;
        final var contractBalance = expectedACharge / 2 - 1;
        given(accountsLedger.get(funding, BALANCE)).willReturn(0L).willReturn(autoRenewBalance);

        givenChargeableContract(aContract, contractBalance, REFERENCE_LIFETIME, anAutoRenew);
        givenAutoRenew(anAutoRenew, autoRenewBalance);

        assertExhaustsResourceLimit(
                () ->
                        subject.chargeStorageFeesInternal(
                                NUM_SLOTS_USED, usageInfos, STORAGE_PRICE_TIERS, accountsLedger),
                INSUFFICIENT_BALANCES_FOR_STORAGE_RENT);
    }

    @Test
    void managesRecordAsExpected() {
        givenStandardSetup();
        final ArgumentCaptor<TransactionBody.Builder> bodyCaptor =
                forClass(TransactionBody.Builder.class);
        final Map<Long, KvUsageInfo> usageInfos =
                Map.of(
                        aContract.getAccountNum(),
                        nonFreeUsageFor(+181),
                        bContract.getAccountNum(),
                        freeUsageFor(12));
        final var expectedACharge =
                STORAGE_PRICE_TIERS.priceOfPendingUsage(
                        someRate,
                        NUM_SLOTS_USED,
                        REFERENCE_LIFETIME,
                        usageInfos.get(aContract.getAccountNum()));
        // setup:
        final BackingStore<AccountID, MerkleAccount> backingAccounts = new HashMapBackingAccounts();
        final var a =
                MerkleAccountFactory.newContract()
                        .balance(1_000_000_000)
                        .expirationTime(REFERENCE_LIFETIME + now.getEpochSecond())
                        .get();
        final var b =
                MerkleAccountFactory.newContract()
                        .balance(1_000_000_000)
                        .expirationTime(REFERENCE_LIFETIME + now.getEpochSecond())
                        .get();
        final var f = MerkleAccountFactory.newAccount().balance(0).get();
        backingAccounts.put(aContract, a);
        backingAccounts.put(bContract, b);
        backingAccounts.put(funding, f);
        final var liveLedger =
                new TransactionalLedger<>(
                        AccountProperty.class,
                        MerkleAccount::new,
                        backingAccounts,
                        new ChangeSummaryManager<>());
        // and:
        final var mockRecord = ExpirableTxnRecord.newBuilder();
        // and:
        given(dynamicProperties.shouldItemizeStorageFees()).willReturn(true);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                eq(Collections.EMPTY_LIST),
                                any(SideEffectsTracker.class),
                                eq(RecordedStorageFeeCharging.MEMO)))
                .willReturn(mockRecord);

        liveLedger.begin();
        subject.chargeStorageRent(NUM_SLOTS_USED, usageInfos, liveLedger);
        liveLedger.commit();

        verify(recordsHistorian)
                .trackFollowingChildRecord(
                        eq(DEFAULT_SOURCE_ID), bodyCaptor.capture(), eq(mockRecord), eq(List.of()));
        final var body = bodyCaptor.getValue().build();
        final var op = body.getCryptoTransfer();
        final var transfers = op.getTransfers().getAccountAmountsList();
        assertEquals(
                List.of(aaWith(funding, expectedACharge), aaWith(aContract, -expectedACharge)),
                transfers);
        assertEquals(expectedACharge, f.getBalance());
    }

    @Test
    void managesAsExpectedWithStakingEnabledAndFullyDistributedFees() {
        givenStandardSetup();
        final ArgumentCaptor<TransactionBody.Builder> bodyCaptor =
                forClass(TransactionBody.Builder.class);
        final Map<Long, KvUsageInfo> usageInfos =
                Map.of(
                        aContract.getAccountNum(),
                        nonFreeUsageFor(+181),
                        bContract.getAccountNum(),
                        freeUsageFor(12));
        final var expectedACharge =
                STORAGE_PRICE_TIERS.priceOfPendingUsage(
                        someRate,
                        NUM_SLOTS_USED,
                        REFERENCE_LIFETIME,
                        usageInfos.get(aContract.getAccountNum()));
        given(dynamicProperties.isStakingEnabled()).willReturn(true);
        given(dynamicProperties.getNodeRewardPercent()).willReturn(33);
        given(dynamicProperties.getStakingRewardPercent()).willReturn(33);
        // setup:
        final BackingStore<AccountID, MerkleAccount> backingAccounts = new HashMapBackingAccounts();
        final var a =
                MerkleAccountFactory.newContract()
                        .balance(1_000_000_000)
                        .expirationTime(REFERENCE_LIFETIME + now.getEpochSecond())
                        .get();
        final var b =
                MerkleAccountFactory.newContract()
                        .balance(1_000_000_000)
                        .expirationTime(REFERENCE_LIFETIME + now.getEpochSecond())
                        .get();
        final var f = MerkleAccountFactory.newAccount().balance(0).get();
        final var s = MerkleAccountFactory.newAccount().balance(0).get();
        final var n = MerkleAccountFactory.newAccount().balance(0).get();
        backingAccounts.put(aContract, a);
        backingAccounts.put(bContract, b);
        backingAccounts.put(funding, f);
        backingAccounts.put(stakingReward, s);
        backingAccounts.put(nodeReward, n);
        final var liveLedger =
                new TransactionalLedger<>(
                        AccountProperty.class,
                        MerkleAccount::new,
                        backingAccounts,
                        new ChangeSummaryManager<>());
        // and:
        final var mockRecord = ExpirableTxnRecord.newBuilder();
        // and:
        given(dynamicProperties.shouldItemizeStorageFees()).willReturn(true);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                eq(Collections.EMPTY_LIST),
                                any(SideEffectsTracker.class),
                                eq(RecordedStorageFeeCharging.MEMO)))
                .willReturn(mockRecord);

        liveLedger.begin();
        subject.chargeStorageRent(NUM_SLOTS_USED, usageInfos, liveLedger);
        liveLedger.commit();

        verify(recordsHistorian)
                .trackFollowingChildRecord(
                        eq(DEFAULT_SOURCE_ID), bodyCaptor.capture(), eq(mockRecord), eq(List.of()));
        final var body = bodyCaptor.getValue().build();
        final var op = body.getCryptoTransfer();
        final var transfers = op.getTransfers().getAccountAmountsList();
        final var thirtyThreePercent = expectedACharge * 33 / 100;
        final var fundingReward = expectedACharge - 2 * thirtyThreePercent;
        assertEquals(
                List.of(
                        aaWith(funding, fundingReward),
                        aaWith(stakingReward, thirtyThreePercent),
                        aaWith(nodeReward, thirtyThreePercent),
                        aaWith(aContract, -expectedACharge)),
                transfers);
        assertEquals(fundingReward, f.getBalance());
        assertEquals(thirtyThreePercent, s.getBalance());
        assertEquals(thirtyThreePercent, n.getBalance());
    }

    @Test
    void managesAsExpectedWithStakingEnabledAndStakingConcentratedFees() {
        givenStandardSetup();
        final ArgumentCaptor<TransactionBody.Builder> bodyCaptor =
                forClass(TransactionBody.Builder.class);
        final Map<Long, KvUsageInfo> usageInfos =
                Map.of(
                        aContract.getAccountNum(),
                        nonFreeUsageFor(+181),
                        bContract.getAccountNum(),
                        freeUsageFor(12));
        final var expectedACharge =
                STORAGE_PRICE_TIERS.priceOfPendingUsage(
                        someRate,
                        NUM_SLOTS_USED,
                        REFERENCE_LIFETIME,
                        usageInfos.get(aContract.getAccountNum()));
        given(dynamicProperties.isStakingEnabled()).willReturn(true);
        given(dynamicProperties.getStakingRewardPercent()).willReturn(100);
        // setup:
        final BackingStore<AccountID, MerkleAccount> backingAccounts = new HashMapBackingAccounts();
        final var a =
                MerkleAccountFactory.newContract()
                        .balance(1_000_000_000)
                        .expirationTime(REFERENCE_LIFETIME + now.getEpochSecond())
                        .get();
        final var b =
                MerkleAccountFactory.newContract()
                        .balance(1_000_000_000)
                        .expirationTime(REFERENCE_LIFETIME + now.getEpochSecond())
                        .get();
        final var f = MerkleAccountFactory.newAccount().balance(0).get();
        final var s = MerkleAccountFactory.newAccount().balance(0).get();
        final var n = MerkleAccountFactory.newAccount().balance(0).get();
        backingAccounts.put(aContract, a);
        backingAccounts.put(bContract, b);
        backingAccounts.put(funding, f);
        backingAccounts.put(stakingReward, s);
        backingAccounts.put(nodeReward, n);
        final var liveLedger =
                new TransactionalLedger<>(
                        AccountProperty.class,
                        MerkleAccount::new,
                        backingAccounts,
                        new ChangeSummaryManager<>());
        // and:
        final var mockRecord = ExpirableTxnRecord.newBuilder();
        // and:
        given(dynamicProperties.shouldItemizeStorageFees()).willReturn(true);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                eq(Collections.EMPTY_LIST),
                                any(SideEffectsTracker.class),
                                eq(RecordedStorageFeeCharging.MEMO)))
                .willReturn(mockRecord);

        liveLedger.begin();
        subject.chargeStorageRent(NUM_SLOTS_USED, usageInfos, liveLedger);
        liveLedger.commit();

        verify(recordsHistorian)
                .trackFollowingChildRecord(
                        eq(DEFAULT_SOURCE_ID), bodyCaptor.capture(), eq(mockRecord), eq(List.of()));
        final var body = bodyCaptor.getValue().build();
        final var op = body.getCryptoTransfer();
        final var transfers = op.getTransfers().getAccountAmountsList();
        assertEquals(
                List.of(
                        aaWith(stakingReward, expectedACharge),
                        aaWith(aContract, -expectedACharge)),
                transfers);
        assertEquals(expectedACharge, s.getBalance());
    }

    @Test
    void managesAsExpectedWithStakingEnabledAndFundingFees() {
        givenStandardSetup();
        final ArgumentCaptor<TransactionBody.Builder> bodyCaptor =
                forClass(TransactionBody.Builder.class);
        final Map<Long, KvUsageInfo> usageInfos =
                Map.of(
                        aContract.getAccountNum(),
                        nonFreeUsageFor(+181),
                        bContract.getAccountNum(),
                        freeUsageFor(12));
        final var expectedACharge =
                STORAGE_PRICE_TIERS.priceOfPendingUsage(
                        someRate,
                        NUM_SLOTS_USED,
                        REFERENCE_LIFETIME,
                        usageInfos.get(aContract.getAccountNum()));
        given(dynamicProperties.isStakingEnabled()).willReturn(true);
        // setup:
        final BackingStore<AccountID, MerkleAccount> backingAccounts = new HashMapBackingAccounts();
        final var a =
                MerkleAccountFactory.newContract()
                        .balance(1_000_000_000)
                        .expirationTime(REFERENCE_LIFETIME + now.getEpochSecond())
                        .get();
        final var b =
                MerkleAccountFactory.newContract()
                        .balance(1_000_000_000)
                        .expirationTime(REFERENCE_LIFETIME + now.getEpochSecond())
                        .get();
        final var f = MerkleAccountFactory.newAccount().balance(0).get();
        final var s = MerkleAccountFactory.newAccount().balance(0).get();
        final var n = MerkleAccountFactory.newAccount().balance(0).get();
        backingAccounts.put(aContract, a);
        backingAccounts.put(bContract, b);
        backingAccounts.put(funding, f);
        backingAccounts.put(stakingReward, s);
        backingAccounts.put(nodeReward, n);
        final var liveLedger =
                new TransactionalLedger<>(
                        AccountProperty.class,
                        MerkleAccount::new,
                        backingAccounts,
                        new ChangeSummaryManager<>());
        // and:
        final var mockRecord = ExpirableTxnRecord.newBuilder();
        // and:
        given(dynamicProperties.shouldItemizeStorageFees()).willReturn(true);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                eq(Collections.EMPTY_LIST),
                                any(SideEffectsTracker.class),
                                eq(RecordedStorageFeeCharging.MEMO)))
                .willReturn(mockRecord);

        liveLedger.begin();
        subject.chargeStorageRent(NUM_SLOTS_USED, usageInfos, liveLedger);
        liveLedger.commit();

        verify(recordsHistorian)
                .trackFollowingChildRecord(
                        eq(DEFAULT_SOURCE_ID), bodyCaptor.capture(), eq(mockRecord), eq(List.of()));
        final var body = bodyCaptor.getValue().build();
        final var op = body.getCryptoTransfer();
        final var transfers = op.getTransfers().getAccountAmountsList();
        assertEquals(
                List.of(aaWith(funding, expectedACharge), aaWith(aContract, -expectedACharge)),
                transfers);
        assertEquals(expectedACharge, f.getBalance());
    }

    @Test
    void doesntCreateRecordIfNoFeesCharged() {
        given(txnCtx.consensusTime()).willReturn(now);
        given(exchange.activeRate(now)).willReturn(someRate);
        given(dynamicProperties.storagePriceTiers()).willReturn(STORAGE_PRICE_TIERS);
        final Map<Long, KvUsageInfo> usageInfos =
                Map.of(
                        aContract.getAccountNum(),
                        freeUsageFor(+1),
                        bContract.getAccountNum(),
                        freeUsageFor(12));
        // setup:
        final BackingStore<AccountID, MerkleAccount> backingAccounts = new HashMapBackingAccounts();
        final var a =
                MerkleAccountFactory.newContract()
                        .balance(1_000_000_000)
                        .expirationTime(REFERENCE_LIFETIME + now.getEpochSecond())
                        .get();
        final var b =
                MerkleAccountFactory.newContract()
                        .balance(1_000_000_000)
                        .expirationTime(REFERENCE_LIFETIME + now.getEpochSecond())
                        .get();
        final var f = MerkleAccountFactory.newAccount().balance(0).get();
        backingAccounts.put(aContract, a);
        backingAccounts.put(bContract, b);
        backingAccounts.put(funding, f);
        final var liveLedger =
                new TransactionalLedger<>(
                        AccountProperty.class,
                        MerkleAccount::new,
                        backingAccounts,
                        new ChangeSummaryManager<>());
        given(dynamicProperties.shouldItemizeStorageFees()).willReturn(true);

        liveLedger.begin();
        subject.chargeStorageRent(NUM_SLOTS_USED, usageInfos, liveLedger);
        liveLedger.commit();

        verifyNoInteractions(recordsHistorian);
    }

    private void givenStandardSetup() {
        given(dynamicProperties.storagePriceTiers()).willReturn(STORAGE_PRICE_TIERS);
        givenStandardInternalSetup();
    }

    private void givenStandardInternalSetup() {
        given(txnCtx.consensusTime()).willReturn(now);
        given(exchange.activeRate(now)).willReturn(someRate);
        given(dynamicProperties.fundingAccount()).willReturn(funding);
    }

    private void givenAutoRenew(final AccountID id, final long amount) {
        given(accountsLedger.contains(id)).willReturn(true);
        given(accountsLedger.get(id, IS_DELETED)).willReturn(false);
        given(accountsLedger.get(id, BALANCE)).willReturn(amount);
    }

    private void givenChargeableContract(
            final AccountID id,
            final long amount,
            final long expiry,
            @Nullable AccountID autoRenewId) {
        if (amount > -1) {
            given(accountsLedger.get(id, BALANCE)).willReturn(amount);
        }
        given(accountsLedger.get(id, EXPIRY)).willReturn(now.getEpochSecond() + expiry);
        if (autoRenewId != null) {
            given(accountsLedger.get(id, AUTO_RENEW_ACCOUNT_ID))
                    .willReturn(EntityId.fromGrpcAccountId(autoRenewId));
        } else {
            given(accountsLedger.get(id, AUTO_RENEW_ACCOUNT_ID))
                    .willReturn(EntityId.MISSING_ENTITY_ID);
        }
    }

    private static final AccountID aContract = IdUtils.asAccount("0.0.1234");
    private static final AccountID anAutoRenew = IdUtils.asAccount("0.0.2345");
    private static final AccountID bContract = IdUtils.asAccount("0.0.3456");
    private static final AccountID cContract = IdUtils.asAccount("0.0.4567");
    private static final AccountID funding = IdUtils.asAccount("0.0.98");
    private static final AccountID stakingReward = IdUtils.asAccount("0.0.800");
    private static final AccountID nodeReward = IdUtils.asAccount("0.0.801");
    private static final Instant now = Instant.ofEpochSecond(1_234_567, 890);
    private static final long cExpiry = 3 * REFERENCE_LIFETIME / 2;
    private static final long NUM_SLOTS_USED = 100_000_000;

    private static final ExchangeRate someRate =
            ExchangeRate.newBuilder().setHbarEquiv(12).setCentEquiv(123).build();

    private AccountAmount aaWith(final AccountID account, final long amount) {
        return AccountAmount.newBuilder().setAccountID(account).setAmount(amount).build();
    }

    private KvUsageInfo nonFreeUsageFor(final int delta) {
        final var info = new KvUsageInfo(FREE_TIER_LIMIT + 1);
        info.updatePendingBy(delta);
        return info;
    }

    private KvUsageInfo freeUsageFor(final int delta) {
        final var info = new KvUsageInfo(delta);
        info.updatePendingBy(delta);
        return info;
    }
}
