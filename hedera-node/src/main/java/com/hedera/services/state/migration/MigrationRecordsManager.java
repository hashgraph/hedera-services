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
package com.hedera.services.state.migration;

import static com.hedera.services.legacy.core.jproto.TxnReceipt.SUCCESS_LITERAL;
import static com.hedera.services.records.TxnAwareRecordsHistorian.DEFAULT_SOURCE_ID;
import static com.hedera.services.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.services.state.EntityCreator.NO_CUSTOM_FEES;
import static com.hedera.services.state.initialization.BackedSystemAccountsCreator.FUNDING_ACCOUNT_EXPIRY;
import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import com.hedera.services.config.AccountNumbers;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.legacy.proto.utils.ByteStringUtils;
import com.hedera.services.records.ConsensusTimeTracker;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.initialization.TreasuryCloner;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.IterableContractValue;
import com.hedera.services.store.contracts.EntityAccess;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.stream.proto.ContractStateChange;
import com.hedera.services.stream.proto.ContractStateChanges;
import com.hedera.services.stream.proto.StorageChange;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.SidecarUtils;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Responsible for externalizing any state changes that happened during migration via child records,
 * and then marking the work done via {@link MerkleNetworkContext#markMigrationRecordsStreamed()}.
 *
 * <p>For example, in release v0.24.1 we created two new accounts {@code 0.0.800} and {@code
 * 0.0.801} to receive staking reward funds. Without synthetic {@code CryptoCreate} records in the
 * record stream, mirror nodes wouldn't know about these new staking accounts. (Note on a network
 * reset, we will <i>also</i> stream these two synthetic creations for mirror node consumption.)
 */
@Singleton
public class MigrationRecordsManager {
    static final String AUTO_RENEW_MEMO_TPL =
            "Contract {} was renewed during 0.26.0 upgrade; new expiry is {}";
    private static final Logger log = LogManager.getLogger(MigrationRecordsManager.class);
    private static final Key immutableKey =
            Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build();
    private static final String STAKING_MEMO = "Release 0.24.1 migration record";
    private static final String TREASURY_CLONE_MEMO = "Synthetic zero-balance treasury clone";
    private static boolean expiryJustEnabled = false;
    private final EntityCreator creator;
    private final TreasuryCloner treasuryCloner;
    private final SigImpactHistorian sigImpactHistorian;
    private final RecordsHistorian recordsHistorian;
    private final Supplier<MerkleNetworkContext> networkCtx;
    private final ConsensusTimeTracker consensusTimeTracker;
    private final SyntheticTxnFactory syntheticTxnFactory;
    private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;
    private final AccountNumbers accountNumbers;
    private final TransactionContext transactionContext;
    private final GlobalDynamicProperties globalDynamicProperties;
    private final Supplier<VirtualMap<ContractKey, IterableContractValue>> contractStorage;
    private final EntityAccess entityAccess;
    private Supplier<SideEffectsTracker> sideEffectsFactory = SideEffectsTracker::new;
    // helper flag in the highly unlikely case when the traceability migration
    // cannot be executed alongside the rest of the migrations (ContractCall/Create as first txn)
    private boolean areAllMigrationsSansTraceabilityFinished;

    @Inject
    public MigrationRecordsManager(
            final EntityCreator creator,
            final TreasuryCloner treasuryCloner,
            final SigImpactHistorian sigImpactHistorian,
            final RecordsHistorian recordsHistorian,
            final Supplier<MerkleNetworkContext> networkCtx,
            final ConsensusTimeTracker consensusTimeTracker,
            final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
            final SyntheticTxnFactory syntheticTxnFactory,
            final AccountNumbers accountNumbers,
            final TransactionContext transactionContext,
            final GlobalDynamicProperties globalDynamicProperties,
            final Supplier<VirtualMap<ContractKey, IterableContractValue>> contractStorage,
            final EntityAccess entityAccess) {
        this.treasuryCloner = treasuryCloner;
        this.sigImpactHistorian = sigImpactHistorian;
        this.recordsHistorian = recordsHistorian;
        this.networkCtx = networkCtx;
        this.consensusTimeTracker = consensusTimeTracker;
        this.creator = creator;
        this.accounts = accounts;
        this.syntheticTxnFactory = syntheticTxnFactory;
        this.accountNumbers = accountNumbers;
        this.transactionContext = transactionContext;
        this.globalDynamicProperties = globalDynamicProperties;
        this.contractStorage = contractStorage;
        this.entityAccess = entityAccess;
    }

    @VisibleForTesting
    static void setExpiryJustEnabled(boolean expiryJustEnabled) {
        MigrationRecordsManager.expiryJustEnabled = expiryJustEnabled;
    }

    /**
     * If appropriate, publish the migration records for this upgrade. Only needs to be called once
     * per restart, but that call must be made from {@code handleTransaction} inside an active
     * {@link com.hedera.services.context.TransactionContext} (because the record running hash is in
     * state).
     */
    public void publishMigrationRecords(final Instant now) {
        final var curNetworkCtx = networkCtx.get();

        if (!consensusTimeTracker.unlimitedPreceding()
                || curNetworkCtx.areMigrationRecordsStreamed()) {
            return;
        }

        // We always publish creation records for 0.0.800, and 0.0.801 on a network reset
        if (curNetworkCtx.consensusTimeOfLastHandledTxn() == null) {
            final var implicitAutoRenewPeriod = FUNDING_ACCOUNT_EXPIRY - now.getEpochSecond();
            final var stakingFundAccounts =
                    List.of(
                            EntityNum.fromLong(accountNumbers.stakingRewardAccount()),
                            EntityNum.fromLong(accountNumbers.nodeRewardAccount()));
            stakingFundAccounts.forEach(
                    num -> publishSyntheticCreationForStakingFund(num, implicitAutoRenewPeriod));
        } else {
            // Publish free auto-renewal migration records if expiry is just being enabled
            if (expiryJustEnabled && !areAllMigrationsSansTraceabilityFinished) {
                publishContractFreeAutoRenewalRecords();
            }
        }

        // And we always publish records for any treasury clones that needed to be created
        if (!areAllMigrationsSansTraceabilityFinished) {
            treasuryCloner
                    .getClonesCreated()
                    .forEach(
                            account ->
                                    publishSyntheticCreation(
                                            account.getKey(),
                                            account.getExpiry() - now.getEpochSecond(),
                                            asKeyUnchecked(account.getAccountKey()),
                                            account.getMemo(),
                                            TREASURY_CLONE_MEMO,
                                            "treasury clone"));
        }

        if (globalDynamicProperties.isTraceabilityMigrationEnabled()) {
            // if we cannot perform the traceability migration with this txn
            // do not mark migration records as streamed in the context and indicate to {@link
            // ServicesTxnManager} to call this method until migration is finished
            if (isSidecarGeneratingFunction(transactionContext.accessor().getFunction())) {
                areAllMigrationsSansTraceabilityFinished = true;
                return;
            }
            publishTraceabilityMigrationRecords();
        }
        // if we reach this line, traceability migration was given opportunity to execute,
        // so we mark migration record streaming as completed
        areAllMigrationsSansTraceabilityFinished = false;
        curNetworkCtx.markMigrationRecordsStreamed();
    }

    private void publishSyntheticCreationForStakingFund(
            final EntityNum num, final long autoRenewPeriod) {
        publishSyntheticCreation(
                num, autoRenewPeriod, immutableKey, EMPTY_MEMO, STAKING_MEMO, "staking fund");
    }

    private void publishSyntheticCreation(
            final EntityNum num,
            final long autoRenewPeriod,
            final Key key,
            final String accountMemo,
            final String recordMemo,
            final String description) {
        final var tracker = sideEffectsFactory.get();
        tracker.trackAutoCreation(num.toGrpcAccountId(), ByteString.EMPTY);
        final var synthBody = synthCreation(autoRenewPeriod, key, accountMemo);
        final var synthRecord =
                creator.createSuccessfulSyntheticRecord(NO_CUSTOM_FEES, tracker, recordMemo);
        recordsHistorian.trackPrecedingChildRecord(DEFAULT_SOURCE_ID, synthBody, synthRecord);
        sigImpactHistorian.markEntityChanged(num.longValue());
        log.info(
                "Published synthetic CryptoCreate for {} account 0.0.{}",
                description,
                num.longValue());
    }

    private TransactionBody.Builder synthCreation(
            final long autoRenewPeriod, final Key key, final String memo) {
        final var txnBody =
                CryptoCreateTransactionBody.newBuilder()
                        .setKey(key)
                        .setMemo(memo)
                        .setInitialBalance(0)
                        .setAutoRenewPeriod(Duration.newBuilder().setSeconds(autoRenewPeriod))
                        .build();
        return TransactionBody.newBuilder().setCryptoCreateAccount(txnBody);
    }

    private void publishContractFreeAutoRenewalRecords() {
        accounts.get()
                .forEach(
                        (id, account) -> {
                            if (account.isSmartContract()) {
                                final var contractNum = id.toEntityId();
                                final var newExpiry = account.getExpiry();

                                final var syntheticSuccessReceipt =
                                        TxnReceipt.newBuilder().setStatus(SUCCESS_LITERAL).build();
                                // for 0.26.0 migration we use the contract account's hbar since
                                // auto-renew accounts are not set
                                final var synthBody =
                                        syntheticTxnFactory.synthContractAutoRenew(
                                                contractNum.asNum(),
                                                newExpiry,
                                                contractNum.toGrpcAccountId());
                                final var memo =
                                        String.format(
                                                AUTO_RENEW_MEMO_TPL, contractNum.num(), newExpiry);
                                final var synthRecord =
                                        ExpirableTxnRecord.newBuilder()
                                                .setMemo(memo)
                                                .setReceipt(syntheticSuccessReceipt);

                                recordsHistorian.trackPrecedingChildRecord(
                                        DEFAULT_SOURCE_ID, synthBody, synthRecord);
                                log.debug(
                                        "Published synthetic ContractUpdate for contract 0.0.{}",
                                        contractNum.num());
                            }
                        });
    }

    private void publishTraceabilityMigrationRecords() {
        final var contractStorageMap = contractStorage.get();
        final var allContractsStateChangesBuilder = ContractStateChanges.newBuilder();
        accounts.get()
                .forEach(
                        (id, account) -> {
                            if (account.isSmartContract() && !account.isDeleted()) {
                                final var contractId = id.toGrpcContractID();
                                // create bytecode sidecar
                                final var runtimeCode =
                                        entityAccess.fetchCodeIfPresent(id.toGrpcAccountId());
                                final var bytecodeSidecar =
                                        SidecarUtils.createContractBytecodeSidecarFrom(
                                                contractId, runtimeCode.toArrayUnsafe());
                                bytecodeSidecar.setMigration(true);
                                transactionContext.addSidecarRecord(bytecodeSidecar);
                                log.debug(
                                        "Published synthetic bytecode sidecar for contract 0.0.{}",
                                        contractId.getContractNum());
                                // create state changes if contract has storage
                                var contractStorageKey = account.getFirstContractStorageKey();
                                if (contractStorageKey == null) {
                                    log.debug(
                                            "Contract 0.0.{} has no iterable storage - no state"
                                                    + " changes will be published.",
                                            contractId.getContractNum());
                                } else {
                                    final var contractStateChangeBuilder =
                                            ContractStateChange.newBuilder()
                                                    .setContractId(contractId);
                                    IterableContractValue iterableValue;
                                    while (contractStorageKey != null) {
                                        iterableValue = contractStorageMap.get(contractStorageKey);
                                        contractStateChangeBuilder.addStorageChanges(
                                                StorageChange.newBuilder()
                                                        .setSlot(
                                                                ByteStringUtils.wrapUnsafely(
                                                                        slotAsBytes(
                                                                                contractStorageKey)))
                                                        .setValueRead(
                                                                ByteStringUtils.wrapUnsafely(
                                                                        iterableValue.getValue()))
                                                        .build());
                                        contractStorageKey =
                                                iterableValue.getNextKeyScopedTo(
                                                        contractStorageKey.getContractId());
                                    }
                                    allContractsStateChangesBuilder.addContractStateChanges(
                                            contractStateChangeBuilder);
                                    log.debug(
                                            "Published synthetic state changes for contract 0.0.{}",
                                            contractId.getContractNum());
                                }
                            }
                        });
        if (allContractsStateChangesBuilder.getContractStateChangesCount() > 0) {
            transactionContext.addSidecarRecord(
                    TransactionSidecarRecord.newBuilder()
                            .setStateChanges(allContractsStateChangesBuilder)
                            .setMigration(true));
        }
    }

    private byte[] slotAsBytes(final ContractKey contractStorageKey) {
        final var contractKeyBytes = new byte[32];
        for (int i = contractStorageKey.getUint256KeyNonZeroBytes() - 1, j = 31 - i;
                i >= 0;
                i--, j++) {
            contractKeyBytes[j] = contractStorageKey.getUint256Byte(i);
        }
        return contractKeyBytes;
    }

    public boolean areAllMigrationsSansTraceabilityFinished() {
        return areAllMigrationsSansTraceabilityFinished;
    }

    private boolean isSidecarGeneratingFunction(final HederaFunctionality function) {
        return function == HederaFunctionality.ContractCall
                || function == HederaFunctionality.ContractCreate
                || function == HederaFunctionality.EthereumTransaction;
    }

    @VisibleForTesting
    void setSideEffectsFactory(Supplier<SideEffectsTracker> sideEffectsFactory) {
        this.sideEffectsFactory = sideEffectsFactory;
    }
}
