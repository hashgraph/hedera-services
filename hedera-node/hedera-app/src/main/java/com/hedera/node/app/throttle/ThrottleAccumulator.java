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

package com.hedera.node.app.throttle;

import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CALL;
import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CALL_LOCAL;
import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CREATE;
import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_CREATE;
import static com.hedera.hapi.node.base.HederaFunctionality.ETHEREUM_TRANSACTION;
import static com.hedera.node.app.hapi.utils.ethereum.EthTxData.populateEthTxData;
import static com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ScaleFactor.ONE_TO_ONE;
import static com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases.isMirror;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.isOfEvmAddressSize;
import static com.hedera.node.app.service.token.impl.handlers.transfer.AliasUtils.isAlias;
import static com.hedera.node.app.service.token.impl.handlers.transfer.AliasUtils.isSerializedProtoKey;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.NftTransfer;
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
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.throttle.HandleThrottleParser;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.node.config.data.AutoCreationConfig;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.node.config.data.LazyCreationConfig;
import com.hedera.node.config.data.TokensConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Keeps track of the amount of usage of different TPS throttle categories and gas, and returns whether a given
 * transaction or query should be throttled based on that.
 * Meant to be used in single-threaded context only as part of the {@link com.hedera.node.app.workflows.handle.HandleWorkflow}.
 */
@Singleton
public class ThrottleAccumulator implements HandleThrottleParser {

    private static final Logger log = LogManager.getLogger(ThrottleAccumulator.class);
    private static final Set<HederaFunctionality> GAS_THROTTLED_FUNCTIONS =
            EnumSet.of(CONTRACT_CALL_LOCAL, CONTRACT_CALL, CONTRACT_CREATE, ETHEREUM_TRANSACTION);
    private final IntSupplier capacitySplitSource;
    private final ConfigProvider configProvider;
    private EnumMap<HederaFunctionality, ThrottleReqsManager> functionReqs = new EnumMap<>(HederaFunctionality.class);
    private static final int UNKNOWN_NUM_IMPLICIT_CREATIONS = -1;
    private boolean lastTxnWasGasThrottled;
    private GasLimitDeterministicThrottle gasThrottle;
    private List<DeterministicThrottle> activeThrottles = Collections.emptyList();

    public ThrottleAccumulator(
            @NonNull final IntSupplier capacitySplitSource, @NonNull final ConfigProvider configProvider) {
        this.configProvider = requireNonNull(configProvider, "configProvider must not be null");
        this.capacitySplitSource = capacitySplitSource;
    }

    /*
     * Updates the throttle requirements for the given transaction and returns whether the transaction should be throttled.
     *
     * @param txnInfo the transaction to update the throttle requirements for
     * @param consensusTime the consensus time of the transaction
     * @param state the current state of the node
     * @return whether the transaction should be throttled
     */
    public boolean shouldThrottle(
            @NonNull final TransactionInfo txnInfo,
            @NonNull final Instant consensusTime,
            @NonNull final HederaState state) {
        resetLastAllowedUse();
        lastTxnWasGasThrottled = false;
        if (shouldThrottleTxn(txnInfo, consensusTime, state)) {
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
     * @return whether the query should be throttled
     */
    public boolean shouldThrottle(
            @NonNull final HederaFunctionality queryFunction, @NonNull final Instant now, @NonNull final Query query) {
        final var configuration = configProvider.getConfiguration();
        final var shouldThrottleByGas =
                configuration.getConfigData(ContractsConfig.class).throttleThrottleByGas();

        resetLastAllowedUse();
        if (isGasThrottled(queryFunction)
                && shouldThrottleByGas
                && (gasThrottle == null
                        || !gasThrottle.allow(now, query.contractCallLocal().gas()))) {
            reclaimLastAllowedUse();
            return true;
        }
        final var manager = functionReqs.get(queryFunction);
        if (manager == null) {
            reclaimLastAllowedUse();
            return true;
        }
        if (!manager.allReqsMetAt(now)) {
            reclaimLastAllowedUse();
            return true;
        }
        return false;
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
    @Override
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
            return Collections.emptyList();
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

    private boolean shouldThrottleTxn(
            @NonNull final TransactionInfo txnInfo, @NonNull final Instant now, @NonNull final HederaState state) {
        final var function = txnInfo.functionality();
        final var configuration = configProvider.getConfiguration();

        // Note that by payer exempt from throttling we mean just that those transactions will not be throttled,
        // such payer accounts neither impact the throttles nor are they impacted by them
        // In the current mono-service implementation we have the same behavior, additionally it is
        // possible that transaction can also be exempt from affecting congestion levels separate from throttle
        // exemption
        // but this is only possible for the case of triggered transactions which is not yet implemented (see
        // MonoMultiplierSources.java)
        final var isPayerThrottleExempt = throttleExempt(txnInfo.payerID(), configuration);
        if (isPayerThrottleExempt) {
            return false;
        }

        // TODO: in mono we pass: () -> getSpanMapAccessor().getEthTxDataMeta(this) as 3rd param to
        // getGasLimitForContractTx as third param, should we do something similar here?
        final var txGasLimit = getGasLimitForContractTx(txnInfo.txBody(), txnInfo.functionality());
        if (isGasExhausted(function, now, txGasLimit, configuration)) {
            lastTxnWasGasThrottled = true;
            return true;
        }

        final var manager = functionReqs.get(function);
        if (manager == null) {
            return true;
        }

        return switch (function) {
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

    public static boolean throttleExempt(
            @NonNull final AccountID accountID, @NonNull final Configuration configuration) {
        final var maxThrottleExemptNum =
                configuration.getConfigData(AccountsConfig.class).lastThrottleExempt();
        final var accountNum = accountID.accountNum();
        return 1L <= accountNum && accountNum <= maxThrottleExemptNum;
    }

    private void reclaimLastAllowedUse() {
        activeThrottles.forEach(DeterministicThrottle::reclaimLastAllowedUse);
        if (gasThrottle != null) {
            gasThrottle.reclaimLastAllowedUse();
        }
    }

    private void resetLastAllowedUse() {
        activeThrottles.forEach(DeterministicThrottle::resetLastAllowedUse);
        if (gasThrottle != null) {
            gasThrottle.resetLastAllowedUse();
        }
    }

    private long getGasLimitForContractTx(
            @NonNull final TransactionBody txn, @NonNull final HederaFunctionality function) {
        return switch (function) {
            case CONTRACT_CREATE -> txn.contractCreateInstance().gas();
            case CONTRACT_CALL -> txn.contractCall().gas();
            case ETHEREUM_TRANSACTION -> EthTxData.populateEthTxData(
                            txn.ethereumTransaction().ethereumData().toByteArray())
                    .gasLimit();
            default -> 0L;
        };
    }

    private boolean isGasExhausted(
            @NonNull final HederaFunctionality function,
            @NonNull final Instant now,
            final long txGasLimit,
            @NonNull final Configuration configuration) {
        final var shouldThrottleByGas =
                configuration.getConfigData(ContractsConfig.class).throttleThrottleByGas();
        return shouldThrottleByGas
                && isGasThrottled(function)
                && (gasThrottle == null || !gasThrottle.allow(now, txGasLimit));
    }

    private boolean shouldThrottleMint(
            @NonNull final ThrottleReqsManager manager,
            @NonNull final TokenMintTransactionBody op,
            @NonNull final Instant now,
            @NonNull final Configuration configuration) {
        final var numNfts = op.metadata().size();
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
        final var isAutoCreationEnabled =
                configuration.getConfigData(AutoCreationConfig.class).enabled();
        final var isLazyCreationEnabled =
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
        final var isAutoCreationEnabled =
                configuration.getConfigData(AutoCreationConfig.class).enabled();
        final var isLazyCreationEnabled =
                configuration.getConfigData(LazyCreationConfig.class).enabled();
        if (isAutoCreationEnabled && isLazyCreationEnabled) {
            return shouldThrottleBasedOnImplicitCreations(manager, implicitCreationsCount, now);
        } else {
            return !manager.allReqsMetAt(now);
        }
    }

    private int getImplicitCreationsCount(
            @NonNull final TransactionBody txnBody, @NonNull final ReadableAccountStore accountStore) {
        var implicitCreationsCount = 0;
        if (txnBody.hasEthereumTransaction()) {
            final var ethTxData = populateEthTxData(
                    txnBody.ethereumTransaction().ethereumData().toByteArray());
            if (ethTxData == null) {
                return UNKNOWN_NUM_IMPLICIT_CREATIONS;
            }

            final var doesNotExist = accountStore.getAccountIDByAlias(Bytes.wrap(ethTxData.to())) == null;
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

        var implicitCreationsCount = 0;
        for (var adjust : cryptoTransferBody.transfers().accountAmounts()) {
            if (!isKnownAlias(adjust.accountID(), accountStore) && containsImplicitCreations(adjust)) {
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

        var implicitCreationsCount = 0;
        for (var tokenAdjust : cryptoTransferBody.tokenTransfers()) {
            for (final var adjust : tokenAdjust.transfers()) {
                if (!isKnownAlias(adjust.accountID(), accountStore) && containsImplicitCreations(adjust)) {
                    implicitCreationsCount++;
                }
            }

            for (final var change : tokenAdjust.nftTransfers()) {
                if (!isKnownAlias(change.receiverAccountID(), accountStore) && containsImplicitCreations(change)) {
                    implicitCreationsCount++;
                }
            }
        }

        return implicitCreationsCount;
    }

    private boolean isKnownAlias(@NonNull final AccountID idOrAlias, @NonNull final ReadableAccountStore accountStore) {
        if (isAlias(idOrAlias)) {
            final var alias = idOrAlias.alias();
            if (isOfEvmAddressSize(alias)) {
                final var evmAddress = alias.toByteArray();
                if (isMirror(evmAddress)) {
                    return true;
                }
            }

            return accountStore.getAccountIDByAlias(alias) != null;
        }

        return true;
    }

    private boolean containsImplicitCreations(@NonNull final AccountAmount adjust) {
        return containsImplicitCreations(adjust.amount(), adjust.accountID().alias());
    }

    private boolean containsImplicitCreations(@NonNull final NftTransfer change) {
        return containsImplicitCreations(
                change.serialNumber(), change.receiverAccountID().alias());
    }

    private boolean containsImplicitCreations(final long assetChange, @NonNull final Bytes alias) {
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
    @Override
    public void rebuildFor(@NonNull final ThrottleDefinitions defs) {
        List<DeterministicThrottle> newActiveThrottles = new ArrayList<>();
        EnumMap<HederaFunctionality, List<Pair<DeterministicThrottle, Integer>>> reqLists =
                new EnumMap<>(HederaFunctionality.class);

        for (var bucket : defs.throttleBuckets()) {
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
    @Override
    public void applyGasConfig() {
        final var configuration = configProvider.getConfiguration();
        final var contractsConfig = configuration.getConfigData(ContractsConfig.class);
        if (contractsConfig.throttleThrottleByGas() && contractsConfig.maxGasPerSec() == 0) {
            log.warn("Consensus gas throttling enabled, but limited to 0 gas/sec");
            return;
        }

        final long capacity = contractsConfig.maxGasPerSec();
        gasThrottle = new GasLimitDeterministicThrottle(capacity);

        log.info(
                "Resolved consensus gas throttle -\n {} gas/sec (throttling {})",
                gasThrottle.capacity(),
                (contractsConfig.throttleThrottleByGas() ? "ON" : "OFF"));
    }

    @NonNull
    private ThrottleGroup<HederaFunctionality> hapiGroupFromPbj(
            @NonNull final com.hedera.hapi.node.transaction.ThrottleGroup pbjThrottleGroup) {
        return new ThrottleGroup<>(pbjThrottleGroup.milliOpsPerSec(), pbjThrottleGroup.operations());
    }

    private void logResolvedDefinitions(final int capacitySplit) {
        var sb = new StringBuilder("Resolved handle throttles (after splitting capacity ")
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
                            .append(manager.currentUsage()) // use current usage instead of the package private
                            // asReadableRequirements(), otherwise we need to make it public as
                            // well
                            .append("\n");
                });
        log.info("{}", () -> sb.toString().trim());
    }

    /*
     * Gets the gas throttle.
     */
    @Nullable
    @Override
    public GasLimitDeterministicThrottle gasLimitThrottle() {
        return gasThrottle;
    }
}
