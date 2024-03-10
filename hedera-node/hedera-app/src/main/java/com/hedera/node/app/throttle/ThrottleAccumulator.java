/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.throttle;

import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CALL;
import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CALL_LOCAL;
import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CREATE;
import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_CREATE;
import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_TRANSFER;
import static com.hedera.hapi.node.base.HederaFunctionality.ETHEREUM_TRANSACTION;
import static com.hedera.node.app.hapi.utils.ethereum.EthTxData.populateEthTxData;
import static com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ScaleFactor.ONE_TO_ONE;
import static com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases.isMirror;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.isOfEvmAddressSize;
import static com.hedera.node.app.service.schedule.impl.handlers.HandlerUtility.childAsOrdinary;
import static com.hedera.node.app.service.token.AliasUtils.isAlias;
import static com.hedera.node.app.service.token.AliasUtils.isSerializedProtoKey;
import static com.hedera.node.app.spi.HapiUtils.functionOf;
import static com.hedera.node.app.throttle.ThrottleAccumulator.ThrottleType.FRONTEND_THROTTLE;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.contract.ContractCallLocalQuery;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.token.TokenMintTransactionBody;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.ThrottleDefinitions;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ThrottleBucket;
import com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ThrottleGroup;
import com.hedera.node.app.hapi.utils.throttles.DeterministicThrottle;
import com.hedera.node.app.hapi.utils.throttles.GasLimitDeterministicThrottle;
import com.hedera.node.app.service.mono.throttling.ThrottleReqsManager;
import com.hedera.node.app.service.schedule.ReadableScheduleStore;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.UnknownHederaFunctionality;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.node.config.data.AutoCreationConfig;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.node.config.data.LazyCreationConfig;
import com.hedera.node.config.data.SchedulingConfig;
import com.hedera.node.config.data.TokensConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntSupplier;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Keeps track of the amount of usage of different TPS throttle categories and gas, and returns whether a given
 * transaction or query should be throttled based on that.
 * Meant to be used in single-threaded context only as part of the {@link com.hedera.node.app.workflows.handle.HandleWorkflow}.
 */
public class ThrottleAccumulator {

    private static final Logger log = LogManager.getLogger(ThrottleAccumulator.class);
    private static final Set<HederaFunctionality> GAS_THROTTLED_FUNCTIONS =
            EnumSet.of(CONTRACT_CALL_LOCAL, CONTRACT_CALL, CONTRACT_CREATE, ETHEREUM_TRANSACTION);
    private static final int UNKNOWN_NUM_IMPLICIT_CREATIONS = -1;

    private EnumMap<HederaFunctionality, ThrottleReqsManager> functionReqs = new EnumMap<>(HederaFunctionality.class);
    private boolean lastTxnWasGasThrottled;
    private GasLimitDeterministicThrottle gasThrottle;
    private List<DeterministicThrottle> activeThrottles = emptyList();

    private final ConfigProvider configProvider;
    private final IntSupplier capacitySplitSource;
    private final ThrottleType throttleType;

    public ThrottleAccumulator(
            @NonNull final IntSupplier capacitySplitSource,
            @NonNull final ConfigProvider configProvider,
            @NonNull final ThrottleType throttleType) {
        this.configProvider = requireNonNull(configProvider, "configProvider must not be null");
        this.capacitySplitSource = requireNonNull(capacitySplitSource, "capacitySplitSource must not be null");
        this.throttleType = requireNonNull(throttleType, "throttleType must not be null");
    }

    // For testing purposes, in practice the gas throttle is
    // lazy-initialized based on the configuration before handling
    // any transactions
    public ThrottleAccumulator(
            @NonNull final IntSupplier capacitySplitSource,
            @NonNull final ConfigProvider configProvider,
            @NonNull final ThrottleType throttleType,
            @NonNull final GasLimitDeterministicThrottle gasThrottle) {
        this.configProvider = requireNonNull(configProvider, "configProvider must not be null");
        this.capacitySplitSource = requireNonNull(capacitySplitSource, "capacitySplitSource must not be null");
        this.throttleType = requireNonNull(throttleType, "throttleType must not be null");
        this.gasThrottle = requireNonNull(gasThrottle, "gasThrottle must not be null");
    }

    /*
     * Updates the throttle requirements for the given transaction and returns whether the transaction should be throttled.
     *
     * @param txnInfo the transaction to update the throttle requirements for
     * @param now the instant of time the transaction throttling should be checked for
     * @param state the current state of the node
     * @return whether the transaction should be throttled
     */
    public boolean shouldThrottle(
            @NonNull final TransactionInfo txnInfo, @NonNull final Instant now, @NonNull final HederaState state) {
        resetLastAllowedUse();
        lastTxnWasGasThrottled = false;
        if (shouldThrottleTxn(false, txnInfo, now, state)) {
            reclaimLastAllowedUse();
            return true;
        }

        return false;
    }

    /*
     * Updates the throttle requirements for the given query and returns whether the query should be throttled.
     *
     * @param queryFunction the functionality of the query
     * @param now the time at which the query is being processed
     * @param query the query to update the throttle requirements for
     * @param queryPayerId the payer id of the query
     * @return whether the query should be throttled
     */
    public boolean shouldThrottle(
            @NonNull final HederaFunctionality queryFunction,
            @NonNull final Instant now,
            @NonNull final Query query,
            @Nullable final AccountID queryPayerId) {
        final var configuration = configProvider.getConfiguration();
        if (throttleExempt(queryPayerId, configuration)) {
            return false;
        }
        if (isGasThrottled(queryFunction)) {
            final var enforceGasThrottle =
                    configuration.getConfigData(ContractsConfig.class).throttleThrottleByGas();
            return enforceGasThrottle
                    && !gasThrottle.allow(
                            now,
                            query.contractCallLocalOrElse(ContractCallLocalQuery.DEFAULT)
                                    .gas());
        }
        resetLastAllowedUse();
        final var manager = functionReqs.get(queryFunction);
        if (manager == null) {
            return true;
        }
        if (!manager.allReqsMetAt(now)) {
            reclaimLastAllowedUse();
            return true;
        }
        return false;
    }

    /*
     * Updates the throttle requirements for given number of transactions of same functionality and returns whether they should be throttled.
     *
     * @param n the number of transactions to consider
     * @param function the functionality type of the transactions
     * @param consensusTime the consensus time of the transaction
     * @return whether the transaction should be throttled
     */
    public boolean shouldThrottleNOfUnscaled(
            final int n, @NonNull final HederaFunctionality function, @NonNull final Instant consensusTime) {
        resetLastAllowedUse();
        final var manager = functionReqs.get(function);
        if (manager == null) {
            return true;
        }
        if (!manager.allReqsMetAt(consensusTime, n, ONE_TO_ONE)) {
            reclaimLastAllowedUse();
            return true;
        }

        return false;
    }

    /*
     * Undoes the claimed capacity for a number of transactions of the same functionality.
     *
     * @param n the number of transactions to consider
     * @param function the functionality type of the transactions
     */
    public void leakCapacityForNOfUnscaled(final int n, @NonNull final HederaFunctionality function) {
        final var manager = Objects.requireNonNull(functionReqs.get(function));
        manager.undoClaimedReqsFor(n);
    }

    /*
     * Leaks the gas amount previously reserved for the given transaction.
     *
     * @param txnInfo the transaction to leak the gas for
     * @param value the amount of gas to leak
     *
     */
    public void leakUnusedGasPreviouslyReserved(@NonNull final TransactionInfo txnInfo, final long value) {
        final var configuration = configProvider.getConfiguration();
        if (throttleExempt(txnInfo.payerID(), configuration)) {
            return;
        }

        gasThrottle.leakUnusedGasPreviouslyReserved(value);
    }

    /*
     * Gets the current list of active throttles.
     *
     * @return the current list of active throttles
     */
    @NonNull
    public List<DeterministicThrottle> allActiveThrottles() {
        return activeThrottles;
    }

    /*
     * Gets the current list of active throttles for the given functionality.
     *
     * @param function the functionality to get the active throttles for
     * @return the current list of active throttles for the given functionality
     */
    @NonNull
    public List<DeterministicThrottle> activeThrottlesFor(@NonNull final HederaFunctionality function) {
        final var manager = functionReqs.get(function);
        if (manager == null) {
            return emptyList();
        } else {
            return manager.managedThrottles();
        }
    }

    /*
     * Indicates whether the last transaction was throttled by gas.
     *
     * @return whether the last transaction was throttled by gas
     */
    public boolean wasLastTxnGasThrottled() {
        return lastTxnWasGasThrottled;
    }

    /*
     * Checks if the given functionality should be throttled by gas.
     *
     * @param function the functionality to check
     * @return whether the given functionality should be throttled by gas
     */
    public static boolean isGasThrottled(@NonNull final HederaFunctionality function) {
        return GAS_THROTTLED_FUNCTIONS.contains(function);
    }

    /*
     * Resets the usage for all underlying throttles.
     */
    public void resetUsage() {
        lastTxnWasGasThrottled = false;
        activeThrottles.forEach(DeterministicThrottle::resetUsage);
        gasThrottle.resetUsage();
    }

    /*
     * Resets the usage for all snapshots.
     */
    public void resetUsageThrottlesTo(final List<DeterministicThrottle.UsageSnapshot> snapshots) {
        for (int i = 0, n = activeThrottles.size(); i < n; i++) {
            activeThrottles.get(i).resetUsageTo(snapshots.get(i));
        }
    }

    private boolean shouldThrottleTxn(
            final boolean isScheduled,
            @NonNull final TransactionInfo txnInfo,
            @NonNull final Instant now,
            @NonNull final HederaState state) {
        final var function = txnInfo.functionality();
        final var configuration = configProvider.getConfiguration();

        // Note that by payer exempt from throttling we mean just that those transactions will not be throttled,
        // such payer accounts neither impact the throttles nor are they impacted by them
        // In the current mono-service implementation we have the same behavior, additionally it is
        // possible that transaction can also be exempt from affecting congestion levels separate from throttle
        // exemption
        // but this is only possible for the case of triggered transactions which is not yet implemented (see
        // MonoMultiplierSources.java)
        final boolean isPayerThrottleExempt = throttleExempt(txnInfo.payerID(), configuration);
        if (isPayerThrottleExempt) {
            return false;
        }

        if (isGasExhausted(txnInfo, now, configuration)) {
            lastTxnWasGasThrottled = true;
            return true;
        }

        final var manager = functionReqs.get(function);
        if (manager == null) {
            return true;
        }

        return switch (function) {
            case SCHEDULE_CREATE -> {
                if (isScheduled) {
                    throw new IllegalStateException("ScheduleCreate cannot be a child!");
                }

                yield shouldThrottleScheduleCreate(manager, txnInfo, now, state);
            }
            case SCHEDULE_SIGN -> {
                if (isScheduled) {
                    throw new IllegalStateException("ScheduleSign cannot be a child!");
                }

                yield shouldThrottleScheduleSign(manager, txnInfo, now, state);
            }
            case TOKEN_MINT -> shouldThrottleMint(manager, txnInfo.txBody().tokenMint(), now, configuration);
            case CRYPTO_TRANSFER -> {
                final var accountStore = new ReadableStoreFactory(state).getStore(ReadableAccountStore.class);
                yield shouldThrottleCryptoTransfer(
                        manager, now, configuration, getImplicitCreationsCount(txnInfo.txBody(), accountStore));
            }
            case ETHEREUM_TRANSACTION -> {
                final var accountStore = new ReadableStoreFactory(state).getStore(ReadableAccountStore.class);
                yield shouldThrottleEthTxn(
                        manager, now, configuration, getImplicitCreationsCount(txnInfo.txBody(), accountStore));
            }
            default -> !manager.allReqsMetAt(now);
        };
    }

    private boolean shouldThrottleScheduleCreate(
            final ThrottleReqsManager manager,
            final TransactionInfo txnInfo,
            final Instant now,
            final HederaState state) {
        final var txnBody = txnInfo.txBody();
        final var scheduleCreate = txnBody.scheduleCreateOrThrow();
        final var scheduled = scheduleCreate.scheduledTransactionBodyOrThrow();
        final var schedule = Schedule.newBuilder()
                .originalCreateTransaction(txnBody)
                .payerAccountId(txnInfo.payerID())
                .scheduledTransaction(scheduled)
                .build();

        TransactionBody innerTxn;
        HederaFunctionality scheduledFunction;
        try {
            innerTxn = childAsOrdinary(schedule);
            scheduledFunction = functionOf(innerTxn);
        } catch (HandleException | UnknownHederaFunctionality ex) {
            log.debug("ScheduleCreate was associated with an invalid txn.", ex);
            return true;
        }

        // maintain legacy behaviour
        final var configuration = configProvider.getConfiguration();
        final boolean areLongTermSchedulesEnabled =
                configuration.getConfigData(SchedulingConfig.class).longTermEnabled();
        if (!areLongTermSchedulesEnabled) {
            final boolean isAutoCreationEnabled =
                    configuration.getConfigData(AutoCreationConfig.class).enabled();
            final boolean isLazyCreationEnabled =
                    configuration.getConfigData(LazyCreationConfig.class).enabled();

            // we check for CryptoTransfer because implicit creations (i.e. auto- or lazy-creation) may happen in it,
            // and we need to throttle those separately
            if ((isAutoCreationEnabled || isLazyCreationEnabled) && scheduledFunction == CRYPTO_TRANSFER) {
                final var transfer = scheduled.cryptoTransfer();
                if (usesAliases(transfer)) {
                    final var accountStore = new ReadableStoreFactory(state).getStore(ReadableAccountStore.class);
                    final var transferTxnBody = TransactionBody.newBuilder()
                            .cryptoTransfer(transfer)
                            .build();
                    final int implicitCreationsCount = getImplicitCreationsCount(transferTxnBody, accountStore);
                    if (implicitCreationsCount > 0) {
                        return shouldThrottleImplicitCreations(implicitCreationsCount, now);
                    }
                }
            }
            return !manager.allReqsMetAt(now);
        } else {
            log.warn("Long term scheduling is enabled, but throttling of long term schedules is not yet implemented.");
            if (!manager.allReqsMetAt(now)) {
                return true;
            }

            // only check deeply if the schedule could immediately execute
            if ((!scheduleCreate.waitForExpiry()) && (throttleType == FRONTEND_THROTTLE)) {
                var effectivePayer = scheduleCreate.hasPayerAccountID()
                        ? scheduleCreate.payerAccountID()
                        : txnBody.transactionID().accountID();

                final var innerTxnInfo = new TransactionInfo(
                        Transaction.DEFAULT,
                        innerTxn,
                        TransactionID.DEFAULT,
                        effectivePayer,
                        SignatureMap.DEFAULT,
                        Bytes.EMPTY,
                        scheduledFunction);

                return shouldThrottleTxn(true, innerTxnInfo, now, state);
            }

            return false;
        }
    }

    private boolean shouldThrottleScheduleSign(
            ThrottleReqsManager manager, TransactionInfo txnInfo, Instant now, HederaState state) {
        final var txnBody = txnInfo.txBody();
        if (!manager.allReqsMetAt(now)) {
            return true;
        }

        // maintain legacy behaviour
        final var configuration = configProvider.getConfiguration();
        final boolean areLongTermSchedulesEnabled =
                configuration.getConfigData(SchedulingConfig.class).longTermEnabled();
        if (!areLongTermSchedulesEnabled) {
            return false;
        } else {
            log.warn("Long term scheduling is enabled, but throttling of long term schedules is not yet implemented.");
            // deeply check throttle only in the frontend throttle
            if (throttleType != FRONTEND_THROTTLE) {
                return false;
            }

            final var scheduledId = txnBody.scheduleSign().scheduleID();
            final var scheduleStore = new ReadableStoreFactory(state).getStore(ReadableScheduleStore.class);
            final var schedule = scheduleStore.get(scheduledId);
            if (schedule == null) {
                log.error(
                        "Tried to throttle in the frontend throttle a ScheduleSign that does not exist! We should not get here.");
                return true;
            }

            // only check deeply if the schedule could immediately execute
            if (schedule.waitForExpiry()) {
                return false;
            }

            TransactionBody innerTxn;
            HederaFunctionality scheduledFunction;
            try {
                innerTxn = childAsOrdinary(schedule);
                scheduledFunction = functionOf(innerTxn);
            } catch (HandleException | UnknownHederaFunctionality ex) {
                log.error("ScheduleSign was associated with an invalid txn.", ex);
                return true;
            }

            final var effectivePayer =
                    schedule.hasPayerAccountId() ? schedule.payerAccountId() : schedule.schedulerAccountId();

            final var innerTxnInfo = new TransactionInfo(
                    Transaction.DEFAULT,
                    innerTxn,
                    TransactionID.DEFAULT,
                    effectivePayer,
                    SignatureMap.DEFAULT,
                    Bytes.EMPTY,
                    scheduledFunction);

            return shouldThrottleTxn(true, innerTxnInfo, now, state);
        }
    }

    private static boolean throttleExempt(
            @Nullable final AccountID accountID, @NonNull final Configuration configuration) {
        final long maxThrottleExemptNum =
                configuration.getConfigData(AccountsConfig.class).lastThrottleExempt();
        if (accountID != null) {
            final var accountNum = accountID.accountNumOrElse(0L);
            return 1L <= accountNum && accountNum <= maxThrottleExemptNum;
        }
        return false;
    }

    private void reclaimLastAllowedUse() {
        activeThrottles.forEach(DeterministicThrottle::reclaimLastAllowedUse);
        gasThrottle.reclaimLastAllowedUse();
    }

    private void resetLastAllowedUse() {
        activeThrottles.forEach(DeterministicThrottle::resetLastAllowedUse);
        gasThrottle.resetLastAllowedUse();
    }

    private long getGasLimitForContractTx(
            @NonNull final TransactionBody txn, @NonNull final HederaFunctionality function) {
        return switch (function) {
            case CONTRACT_CREATE -> txn.contractCreateInstance().gas();
            case CONTRACT_CALL -> txn.contractCall().gas();
            case ETHEREUM_TRANSACTION -> Optional.of(
                            txn.ethereumTransactionOrThrow().ethereumData().toByteArray())
                    .map(EthTxData::populateEthTxData)
                    .map(EthTxData::gasLimit)
                    .orElse(0L);
            default -> 0L;
        };
    }

    private boolean isGasExhausted(
            @NonNull final TransactionInfo txnInfo,
            @NonNull final Instant now,
            @NonNull final Configuration configuration) {
        final boolean shouldThrottleByGas =
                configuration.getConfigData(ContractsConfig.class).throttleThrottleByGas();
        return shouldThrottleByGas
                && isGasThrottled(txnInfo.functionality())
                && !gasThrottle.allow(now, getGasLimitForContractTx(txnInfo.txBody(), txnInfo.functionality()));
    }

    private boolean shouldThrottleMint(
            @NonNull final ThrottleReqsManager manager,
            @NonNull final TokenMintTransactionBody op,
            @NonNull final Instant now,
            @NonNull final Configuration configuration) {
        final int numNfts = op.metadata().size();
        if (numNfts == 0) {
            return !manager.allReqsMetAt(now);
        } else {
            final var nftsMintThrottleScaleFactor =
                    configuration.getConfigData(TokensConfig.class).nftsMintThrottleScaleFactor();
            return !manager.allReqsMetAt(now, numNfts, nftsMintThrottleScaleFactor);
        }
    }

    private boolean shouldThrottleCryptoTransfer(
            @NonNull final ThrottleReqsManager manager,
            @NonNull final Instant now,
            @NonNull final Configuration configuration,
            final int implicitCreationsCount) {
        final boolean isAutoCreationEnabled =
                configuration.getConfigData(AutoCreationConfig.class).enabled();
        final boolean isLazyCreationEnabled =
                configuration.getConfigData(LazyCreationConfig.class).enabled();
        if (isAutoCreationEnabled || isLazyCreationEnabled) {
            return shouldThrottleBasedOnImplicitCreations(manager, implicitCreationsCount, now);
        } else {
            return !manager.allReqsMetAt(now);
        }
    }

    private boolean shouldThrottleEthTxn(
            @NonNull final ThrottleReqsManager manager,
            @NonNull final Instant now,
            @NonNull final Configuration configuration,
            final int implicitCreationsCount) {
        final boolean isAutoCreationEnabled =
                configuration.getConfigData(AutoCreationConfig.class).enabled();
        final boolean isLazyCreationEnabled =
                configuration.getConfigData(LazyCreationConfig.class).enabled();
        if (isAutoCreationEnabled && isLazyCreationEnabled) {
            return shouldThrottleBasedOnImplicitCreations(manager, implicitCreationsCount, now);
        } else {
            return !manager.allReqsMetAt(now);
        }
    }

    private int getImplicitCreationsCount(
            @NonNull final TransactionBody txnBody, @NonNull final ReadableAccountStore accountStore) {
        int implicitCreationsCount = 0;
        if (txnBody.hasEthereumTransaction()) {
            final var ethTxData = populateEthTxData(
                    txnBody.ethereumTransaction().ethereumData().toByteArray());
            if (ethTxData == null) {
                return UNKNOWN_NUM_IMPLICIT_CREATIONS;
            }

            final boolean doesNotExist = !accountStore.containsAlias(Bytes.wrap(ethTxData.to()));
            if (doesNotExist && ethTxData.value().compareTo(BigInteger.ZERO) > 0) {
                implicitCreationsCount++;
            }
        } else {
            final var cryptoTransferBody = txnBody.cryptoTransfer();
            if (cryptoTransferBody == null) {
                return 0;
            }

            implicitCreationsCount += hbarAdjustsImplicitCreationsCount(accountStore, cryptoTransferBody);
            implicitCreationsCount += tokenAdjustsImplicitCreationsCount(accountStore, cryptoTransferBody);
        }

        return implicitCreationsCount;
    }

    private int hbarAdjustsImplicitCreationsCount(
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final CryptoTransferTransactionBody cryptoTransferBody) {
        if (cryptoTransferBody.transfers() == null
                || cryptoTransferBody.transfers().accountAmounts() == null) {
            return 0;
        }

        int implicitCreationsCount = 0;
        for (var adjust : cryptoTransferBody.transfers().accountAmounts()) {
            if (referencesAliasNotInUse(adjust.accountIDOrElse(AccountID.DEFAULT), accountStore)
                    && isPlausibleAutoCreate(adjust)) {
                implicitCreationsCount++;
            }
        }

        return implicitCreationsCount;
    }

    private int tokenAdjustsImplicitCreationsCount(
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final CryptoTransferTransactionBody cryptoTransferBody) {
        if (cryptoTransferBody.tokenTransfers() == null) {
            return 0;
        }

        int implicitCreationsCount = 0;
        for (var tokenAdjust : cryptoTransferBody.tokenTransfers()) {
            for (final var adjust : tokenAdjust.transfers()) {
                if (referencesAliasNotInUse(adjust.accountID(), accountStore) && isPlausibleAutoCreate(adjust)) {
                    implicitCreationsCount++;
                }
            }

            for (final var change : tokenAdjust.nftTransfers()) {
                if (referencesAliasNotInUse(change.receiverAccountID(), accountStore)
                        && isPlausibleAutoCreate(change)) {
                    implicitCreationsCount++;
                }
            }
        }

        return implicitCreationsCount;
    }

    private boolean usesAliases(final CryptoTransferTransactionBody transferBody) {
        for (var adjust : transferBody.transfersOrElse(TransferList.DEFAULT).accountAmountsOrElse(emptyList())) {
            if (isAlias(adjust.accountIDOrElse(AccountID.DEFAULT))) {
                return true;
            }
        }

        for (var tokenAdjusts : transferBody.tokenTransfersOrElse(emptyList())) {
            for (var ownershipChange : tokenAdjusts.nftTransfersOrElse(emptyList())) {
                if (isAlias(ownershipChange.senderAccountIDOrElse(AccountID.DEFAULT))
                        || isAlias(ownershipChange.receiverAccountIDOrElse(AccountID.DEFAULT))) {
                    return true;
                }
            }
            for (var tokenAdjust : tokenAdjusts.transfersOrElse(emptyList())) {
                if (isAlias(tokenAdjust.accountIDOrElse(AccountID.DEFAULT))) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean referencesAliasNotInUse(
            @NonNull final AccountID idOrAlias, @NonNull final ReadableAccountStore accountStore) {
        if (isAlias(idOrAlias)) {
            final var alias = idOrAlias.aliasOrElse(Bytes.EMPTY);
            if (isOfEvmAddressSize(alias)) {
                final var evmAddress = alias.toByteArray();
                if (isMirror(evmAddress)) {
                    return false;
                }
            }
            return accountStore.getAccountIDByAlias(alias) == null;
        }
        return false;
    }

    private boolean isPlausibleAutoCreate(@NonNull final AccountAmount adjust) {
        return isPlausibleAutoCreate(
                adjust.amount(), adjust.accountIDOrElse(AccountID.DEFAULT).aliasOrElse(Bytes.EMPTY));
    }

    private boolean isPlausibleAutoCreate(@NonNull final NftTransfer change) {
        return isPlausibleAutoCreate(
                change.serialNumber(),
                change.receiverAccountIDOrElse(AccountID.DEFAULT).aliasOrElse(Bytes.EMPTY));
    }

    private boolean isPlausibleAutoCreate(final long assetChange, @NonNull final Bytes alias) {
        if (assetChange > 0) {
            if (isSerializedProtoKey(alias)) {
                return true;
            } else {
                return isOfEvmAddressSize(alias);
            }
        }

        return false;
    }

    private boolean shouldThrottleBasedOnImplicitCreations(
            @NonNull final ThrottleReqsManager manager, final int implicitCreationsCount, @NonNull final Instant now) {
        return (implicitCreationsCount == 0)
                ? !manager.allReqsMetAt(now)
                : shouldThrottleImplicitCreations(implicitCreationsCount, now);
    }

    private boolean shouldThrottleImplicitCreations(final int n, @NonNull final Instant now) {
        final var manager = functionReqs.get(CRYPTO_CREATE);
        return manager == null || !manager.allReqsMetAt(now, n, ONE_TO_ONE);
    }

    /*
     * Rebuilds the throttle requirements based on the given throttle definitions.
     *
     * @param defs the throttle definitions to rebuild the throttle requirements based on
     */
    public void rebuildFor(@NonNull final ThrottleDefinitions defs) {
        List<DeterministicThrottle> newActiveThrottles = new ArrayList<>();
        EnumMap<HederaFunctionality, List<Pair<DeterministicThrottle, Integer>>> reqLists =
                new EnumMap<>(HederaFunctionality.class);

        for (var bucket : defs.throttleBucketsOrElse(emptyList())) {
            try {
                final var utilThrottleBucket = new ThrottleBucket<>(
                        bucket.burstPeriodMs(),
                        bucket.name(),
                        bucket.throttleGroups().stream()
                                .map(this::hapiGroupFromPbj)
                                .toList());
                var mapping = utilThrottleBucket.asThrottleMapping(capacitySplitSource.getAsInt());
                var throttle = mapping.getLeft();
                var reqs = mapping.getRight();
                for (var req : reqs) {
                    reqLists.computeIfAbsent(req.getLeft(), ignore -> new ArrayList<>())
                            .add(Pair.of(throttle, req.getRight()));
                }
                newActiveThrottles.add(throttle);
            } catch (IllegalStateException badBucket) {
                log.error("When constructing bucket '{}' from state: {}", bucket.name(), badBucket.getMessage());
            }
        }
        EnumMap<HederaFunctionality, ThrottleReqsManager> newFunctionReqs = new EnumMap<>(HederaFunctionality.class);
        reqLists.forEach((function, reqs) -> newFunctionReqs.put(function, new ThrottleReqsManager(reqs)));

        functionReqs = newFunctionReqs;
        activeThrottles = newActiveThrottles;

        logResolvedDefinitions(capacitySplitSource.getAsInt());
    }

    /*
     * Rebuilds the gas throttle based on the current configuration.
     */
    public void applyGasConfig() {
        final var configuration = configProvider.getConfiguration();
        final var contractsConfig = configuration.getConfigData(ContractsConfig.class);
        if (contractsConfig.throttleThrottleByGas() && contractsConfig.maxGasPerSec() == 0) {
            log.warn("{} gas throttling enabled, but limited to 0 gas/sec", throttleType.name());
        }
        gasThrottle = new GasLimitDeterministicThrottle(contractsConfig.maxGasPerSec());
        log.info(
                "Resolved {} gas throttle -\n {} gas/sec (throttling {})",
                throttleType.name(),
                gasThrottle.capacity(),
                (contractsConfig.throttleThrottleByGas() ? "ON" : "OFF"));
    }

    @NonNull
    private ThrottleGroup<HederaFunctionality> hapiGroupFromPbj(
            @NonNull final com.hedera.hapi.node.transaction.ThrottleGroup pbjThrottleGroup) {
        return new ThrottleGroup<>(pbjThrottleGroup.milliOpsPerSec(), pbjThrottleGroup.operations());
    }

    private void logResolvedDefinitions(final int capacitySplit) {
        var sb = new StringBuilder("Resolved ")
                .append(throttleType.name())
                .append(" ")
                .append("(after splitting capacity ")
                .append(capacitySplit)
                .append(" ways) - \n");
        functionReqs.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> {
                    var function = entry.getKey();
                    var manager = entry.getValue();
                    sb.append("  ")
                            .append(function)
                            .append(": ")
                            .append(manager.asReadableRequirements())
                            .append("\n");
                });
        log.info("{}", () -> sb.toString().trim());
    }

    /*
     * Gets the gas throttle.
     */
    public @NonNull GasLimitDeterministicThrottle gasLimitThrottle() {
        return requireNonNull(gasThrottle, "");
    }

    public enum ThrottleType {
        FRONTEND_THROTTLE,
        BACKEND_THROTTLE
    }
}
