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

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.ledger.TransactionalLedger.activeLedgerWrapping;
import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_ACCOUNT_ID;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.records.TxnAwareRecordsHistorian.DEFAULT_SOURCE_ID;
import static com.hedera.services.state.EntityCreator.NO_CUSTOM_FEES;
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.interceptors.AccountsCommitInterceptor;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.tuweni.bytes.Bytes;

@Singleton
public class RecordedStorageFeeCharging implements StorageFeeCharging {
    public static final String MEMO = "Contract storage fees";

    private static final List<TransactionSidecarRecord.Builder> NO_SIDECARS =
            Collections.emptyList();

    // Used to created the synthetic record if itemizing is enabled
    private final EntityCreator creator;
    // Used to get the current exchange rate
    private final HbarCentExchange exchange;
    // Used to track the storage fee payments in a succeeding child record
    private final RecordsHistorian recordsHistorian;
    // Used to create the synthetic CryptoTransfer for storage fee payments
    private final SyntheticTxnFactory syntheticTxnFactory;
    // Used to get the current consensus time
    private final TransactionContext txnCtx;
    // Used to get the storage slot lifetime and pricing tiers
    private final GlobalDynamicProperties dynamicProperties;

    @Inject
    public RecordedStorageFeeCharging(
            final EntityCreator creator,
            final HbarCentExchange exchange,
            final RecordsHistorian recordsHistorian,
            final TransactionContext txnCtx,
            final SyntheticTxnFactory syntheticTxnFactory,
            final GlobalDynamicProperties dynamicProperties) {
        this.txnCtx = txnCtx;
        this.creator = creator;
        this.exchange = exchange;
        this.recordsHistorian = recordsHistorian;
        this.dynamicProperties = dynamicProperties;
        this.syntheticTxnFactory = syntheticTxnFactory;
    }

    @Override
    public void chargeStorageFees(
            final long numKvPairs,
            final Map<AccountID, Bytes> newBytecodes,
            final Map<AccountID, Integer> newUsageDeltas,
            final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accounts) {
        if (newBytecodes.isEmpty() && newUsageDeltas.isEmpty()) {
            return;
        }
        if (!dynamicProperties.shouldItemizeStorageFees()) {
            chargeStorageFeesInternal(numKvPairs, newBytecodes, newUsageDeltas, accounts);
        } else {
            final var wrappedAccounts = activeLedgerWrapping(accounts);
            final var sideEffects = new SideEffectsTracker();
            final var accountsCommitInterceptor = new AccountsCommitInterceptor(sideEffects);
            wrappedAccounts.setCommitInterceptor(accountsCommitInterceptor);
            chargeStorageFeesInternal(numKvPairs, newBytecodes, newUsageDeltas, wrappedAccounts);
            wrappedAccounts.commit();

            final var charges = sideEffects.getNetTrackedHbarChanges();
            final var synthBody = syntheticTxnFactory.synthCryptoTransfer(charges);
            final var synthRecord =
                    creator.createSuccessfulSyntheticRecord(NO_CUSTOM_FEES, sideEffects, MEMO);
            recordsHistorian.trackFollowingChildRecord(
                    DEFAULT_SOURCE_ID, synthBody, synthRecord, NO_SIDECARS);
        }
    }

    void chargeStorageFeesInternal(
            final long numKvPairs,
            final Map<AccountID, Bytes> newBytecodes,
            final Map<AccountID, Integer> newUsageDeltas,
            final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accounts) {
        final var now = txnCtx.consensusTime();
        final var rate = exchange.activeRate(now);
        final var thisSecond = now.getEpochSecond();
        final var slotLifetime = dynamicProperties.storageSlotLifetime();
        final var storagePriceTiers = dynamicProperties.storagePriceTiers();

        if (!newBytecodes.isEmpty()) {
            newBytecodes.forEach(
                    (id, code) -> {
                        final var lifetime = (long) accounts.get(id, EXPIRY) - thisSecond;
                        final var fee =
                                storagePriceTiers.codePrice(
                                        rate, slotLifetime, numKvPairs, code, lifetime);
                        pay(id, fee, accounts);
                    });
        }

        if (!newUsageDeltas.isEmpty()) {
            newUsageDeltas.forEach(
                    (id, delta) -> {
                        if (delta > 0) {
                            final var lifetime = (long) accounts.get(id, EXPIRY) - thisSecond;
                            final var fee =
                                    storagePriceTiers.slotPrice(
                                            rate, slotLifetime, numKvPairs, delta, lifetime);
                            pay(id, fee, accounts);
                        }
                    });
        }
    }

    private void pay(
            final AccountID id,
            final long fee,
            final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accounts) {
        var leftToPay = fee;
        final var autoRenewId = (EntityId) accounts.get(id, AUTO_RENEW_ACCOUNT_ID);
        if (autoRenewId != null && !MISSING_ENTITY_ID.equals(autoRenewId)) {
            final var grpcId = autoRenewId.toGrpcAccountId();
            if (accounts.contains(grpcId) && !(boolean) accounts.get(grpcId, IS_DELETED)) {
                final var debited =
                        charge(autoRenewId.toGrpcAccountId(), leftToPay, false, accounts);
                leftToPay -= debited;
            }
        }
        if (leftToPay > 0) {
            charge(id, leftToPay, true, accounts);
        }
    }

    private long charge(
            final AccountID id,
            final long amount,
            final boolean isLastResort,
            final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accounts) {
        long paid;
        final var balance = (long) accounts.get(id, BALANCE);
        if (amount > balance) {
            validateFalse(isLastResort, INSUFFICIENT_ACCOUNT_BALANCE);
            accounts.set(id, BALANCE, 0L);
            paid = balance;
        } else {
            accounts.set(id, BALANCE, balance - amount);
            paid = amount;
        }
        final var fundingId = dynamicProperties.fundingAccount();
        final var fundingBalance = (long) accounts.get(fundingId, BALANCE);
        accounts.set(fundingId, BALANCE, fundingBalance + paid);
        return paid;
    }
}
