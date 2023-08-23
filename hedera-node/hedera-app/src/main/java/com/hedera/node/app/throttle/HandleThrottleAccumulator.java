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

import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_CREATE;
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
import com.hedera.hapi.node.transaction.ThrottleDefinitions;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ThrottleBucket;
import com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ThrottleGroup;
import com.hedera.node.app.hapi.utils.throttles.DeterministicThrottle;
import com.hedera.node.app.service.mono.throttling.ThrottleReqsManager;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.AutoCreationConfig;
import com.hedera.node.config.data.LazyCreationConfig;
import com.hedera.node.config.data.TokensConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class HandleThrottleAccumulator {
    private static final Logger log = LogManager.getLogger(HandleThrottleAccumulator.class);
    private final ConfigProvider configProvider;
    private EnumMap<HederaFunctionality, ThrottleReqsManager> functionReqs = new EnumMap<>(HederaFunctionality.class);
    private static final int UNKNOWN_NUM_IMPLICIT_CREATIONS = -1;

    @Inject
    public HandleThrottleAccumulator(@NonNull final ConfigProvider configProvider) {
        this.configProvider = requireNonNull(configProvider, "configProvider must not be null");
    }

    public boolean shouldThrottle(@NonNull TransactionInfo txnInfo, Instant t, HederaState state) {
        // TODO: additional implementation to throttle by gas
        //        resetLastAllowedUse();
        //        lastTxnWasGasThrottled = false;
        if (shouldThrottleTxn(txnInfo, t, state)) {
            //  reclaimLastAllowedUse();
            return true;
        }

        return false;
    }

    private boolean shouldThrottleTxn(
            @NonNull final TransactionInfo txnInfo, final Instant now, final HederaState state) {
        final var function = txnInfo.functionality();
        final var configuration = configProvider.getConfiguration();

        // TODO: check if transaction is exempt from throttling
        //        if (txnAccessor.throttleExempt()) {
        //            return false;
        //        }

        // TODO: check if gas is exhausted
        //        final var tmp = txnAccessor.getGasLimitForContractTx();
        //        if (isGasExhausted(function, now, tmp)) {
        //            lastTxnWasGasThrottled = true;
        //            return true;
        //        }

        ThrottleReqsManager manager;
        if ((manager = functionReqs.get(function)) == null) {
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

    //    private boolean isGasExhausted(
    //            final com.hederahashgraph.api.proto.java.HederaFunctionality function, final Instant now, long
    // txGasLimit) {
    //        return false;
    //                return dynamicProperties.shouldThrottleByGas()
    //                        && isGasThrottled(function)
    //                        && (gasThrottle == null || !gasThrottle.allow(now, txGasLimit));
    //    }

    private boolean shouldThrottleMint(
            ThrottleReqsManager manager,
            @NonNull TokenMintTransactionBody op,
            Instant now,
            Configuration configuration) {
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
            ThrottleReqsManager manager,
            Instant now,
            @NonNull Configuration configuration,
            int implicitCreationsCount) {
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
            ThrottleReqsManager manager,
            Instant now,
            @NonNull Configuration configuration,
            int implicitCreationsCount) {
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

    private int getImplicitCreationsCount(@NonNull TransactionBody txnBody, ReadableAccountStore accountStore) {
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
            implicitCreationsCount += hbarAdjustsImplicitCreationsCount(accountStore, cryptoTransferBody);
            implicitCreationsCount += tokenAdjustsImplicitCreationsCount(accountStore, cryptoTransferBody);
        }

        return implicitCreationsCount;
    }

    private int hbarAdjustsImplicitCreationsCount(
            final ReadableAccountStore accountStore, @NonNull CryptoTransferTransactionBody cryptoTransferBody) {
        var implicitCreationsCount = 0;
        for (var adjust : cryptoTransferBody.transfers().accountAmounts()) {
            if (!isKnownAlias(adjust.accountID(), accountStore) && containsImplicitCreations(adjust)) {
                implicitCreationsCount++;
            }
        }

        return implicitCreationsCount;
    }

    private int tokenAdjustsImplicitCreationsCount(
            final ReadableAccountStore accountStore, @NonNull CryptoTransferTransactionBody cryptoTransferBody) {
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

    private boolean isKnownAlias(final AccountID idOrAlias, final ReadableAccountStore accountStore) {
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

    private boolean containsImplicitCreations(@NonNull AccountAmount adjust) {
        return containsImplicitCreations(adjust.amount(), adjust.accountID().alias());
    }

    private boolean containsImplicitCreations(@NonNull final NftTransfer change) {
        return containsImplicitCreations(
                change.serialNumber(), change.receiverAccountID().alias());
    }

    private boolean containsImplicitCreations(final long assetChange, final Bytes alias) {
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
            final ThrottleReqsManager manager, final int implicitCreationsCount, final Instant now) {
        return (implicitCreationsCount == 0)
                ? !manager.allReqsMetAt(now)
                : shouldThrottleImplicitCreations(implicitCreationsCount, now);
    }

    private boolean shouldThrottleImplicitCreations(final int n, final Instant now) {
        final var manager = functionReqs.get(CRYPTO_CREATE);
        return manager == null || !manager.allReqsMetAt(now, n, ONE_TO_ONE);
    }

    // TODO: implement the logic for parsing the throttle defs to be used
    public void rebuildFor(@NonNull ThrottleDefinitions defs) {
        final var capacitySplit = 1;
        //        calculateThrottles(defs, capacitySplitSource.getAsInt());
        //        List<DeterministicThrottle> newActiveThrottles = new ArrayList<>();
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
                var mapping = utilThrottleBucket.asThrottleMapping(capacitySplit);
                var throttle = mapping.getLeft();
                var reqs = mapping.getRight();
                for (var req : reqs) {
                    reqLists.computeIfAbsent(req.getLeft(), ignore -> new ArrayList<>())
                            .add(Pair.of(throttle, req.getRight()));
                }
                //                newActiveThrottles.add(throttle);
            } catch (IllegalStateException badBucket) {
                log.error("When constructing bucket '{}' from state: {}", bucket.name(), badBucket.getMessage());
            }
        }
        EnumMap<HederaFunctionality, ThrottleReqsManager> newFunctionReqs = new EnumMap<>(HederaFunctionality.class);
        reqLists.forEach((function, reqs) -> newFunctionReqs.put(function, new ThrottleReqsManager(reqs)));

        functionReqs = newFunctionReqs;
        //        activeThrottles = newActiveThrottles;
        //        logResolvedDefinitions();
    }

    private ThrottleGroup<HederaFunctionality> hapiGroupFromPbj(
            com.hedera.hapi.node.transaction.ThrottleGroup pbjThrottleGroup) {
        return new ThrottleGroup<>(pbjThrottleGroup.milliOpsPerSec(), pbjThrottleGroup.operations());
    }

    //    @Override
    //    public boolean shouldThrottleQuery(@NonNull HederaFunctionality functionality, @NonNull Query query) {
    //        return false;
    //    }
}
