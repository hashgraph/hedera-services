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
package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.PRNG;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.execution.LivePricesSource;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.AbstractLedgerWorldUpdater;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.contracts.precompile.utils.PrecompileUtils;
import com.hedera.services.txns.util.PrngLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.UtilPrngTransactionBody;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.AbstractPrecompiledContract;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

/**
 * System contract to generate random numbers. This will generate 256-bit pseudorandom number when
 * no range is provided using n-3 record's running hash. If 32-bit integer "range" and 256-bit
 * "seed" is provided returns a pseudorandom 32-bit integer X belonging to [0, range).
 */
@Singleton
public class PrngSystemPrecompiledContract extends AbstractPrecompiledContract {
    private static final Logger log = LogManager.getLogger(PrngSystemPrecompiledContract.class);
    private static final String PRECOMPILE_NAME = "PRNG";
    // random256BitGenerator(uint256)
    static final int PSEUDORANDOM_SEED_GENERATOR_SELECTOR = 0xd83bf9a1;
    public static final String PRNG_PRECOMPILE_ADDRESS = "0x169";
    private final PrngLogic prngLogic;
    private final EntityCreator creator;
    private HederaStackedWorldStateUpdater updater;
    private final RecordsHistorian recordsHistorian;
    private final PrecompilePricingUtils pricingUtils;
    private final LivePricesSource livePricesSource;
    private final GlobalDynamicProperties properties;
    private long gasRequirement;

    @Inject
    public PrngSystemPrecompiledContract(
            final GasCalculator gasCalculator,
            final PrngLogic prngLogic,
            final ExpiringCreations creator,
            final RecordsHistorian recordsHistorian,
            final PrecompilePricingUtils pricingUtils,
            final LivePricesSource livePricesSource,
            final GlobalDynamicProperties properties) {
        super(PRECOMPILE_NAME, gasCalculator);
        this.prngLogic = prngLogic;
        this.creator = creator;
        this.recordsHistorian = recordsHistorian;
        this.pricingUtils = pricingUtils;
        this.livePricesSource = livePricesSource;
        this.properties = properties;
    }

    @Override
    public long gasRequirement(final Bytes bytes) {
        return gasRequirement;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public PrecompileContractResult computePrecompile(final Bytes input, final MessageFrame frame) {
        final var gasNeeded =
                calculateGas(Instant.ofEpochSecond(frame.getBlockValues().getTimestamp()));
        final var result = computePrngResult(gasNeeded, input, frame);

        if (frame.isStatic()) {
            final var proxyUpdater = (HederaStackedWorldStateUpdater) frame.getWorldUpdater();
            if (!proxyUpdater.isInTransaction()) {
                // This thread is answering a ContractCallLocal query; don't create a record or
                // change
                // instance fields, just return the gas required and output for the given input
                return result.getLeft();
            }
        }
        final var randomNum = result.getLeft().getOutput();
        final var childRecord =
                result.getRight() == null
                        ? createSuccessfulChildRecord(randomNum, frame, input)
                        : createUnsuccessfulChildRecord(result.getRight(), frame);

        final var parentUpdater = updater.parentUpdater();
        if (parentUpdater.isPresent()) {
            final var parent = (AbstractLedgerWorldUpdater) parentUpdater.get();
            parent.manageInProgressRecord(recordsHistorian, childRecord, body(randomNum));
        } else {
            throw new InvalidTransactionException(
                    "PRNG precompile frame had no parent updater", FAIL_INVALID);
        }

        return result.getLeft();
    }

    Pair<PrecompileContractResult, ResponseCodeEnum> computePrngResult(
            final long gasNeeded, final Bytes input, final MessageFrame frame) {
        try {
            validateTrue(frame.getRemainingGas() >= gasNeeded, INSUFFICIENT_GAS);
            final var randomNum = generatePseudoRandomData(input);
            return Pair.of(PrecompiledContract.PrecompileContractResult.success(randomNum), null);
        } catch (InvalidTransactionException e) {
            return Pair.of(
                    PrecompiledContract.PrecompileContractResult.halt(
                            null, Optional.ofNullable(ExceptionalHaltReason.INVALID_OPERATION)),
                    e.getResponseCode());
        } catch (Exception e) {
            log.warn("Internal precompile failure", e);
            return Pair.of(
                    PrecompiledContract.PrecompileContractResult.halt(
                            null, Optional.ofNullable(ExceptionalHaltReason.INVALID_OPERATION)),
                    FAIL_INVALID);
        }
    }

    @VisibleForTesting
    Bytes generatePseudoRandomData(final Bytes input) {
        final var selector = input.getInt(0);
        return switch (selector) {
            case PSEUDORANDOM_SEED_GENERATOR_SELECTOR -> random256BitGenerator();
            default -> null;
        };
    }

    private Bytes random256BitGenerator() {
        final var hashBytes = prngLogic.getNMinus3RunningHashBytes();
        if (isEmptyOrNull(hashBytes)) {
            return null;
        }
        return Bytes.wrap(hashBytes, 0, 32);
    }

    private boolean isEmptyOrNull(final byte[] hashBytes) {
        return hashBytes == null || hashBytes.length == 0;
    }

    @VisibleForTesting
    long calculateGas(final Instant now) {
        final var feesInTinyCents = pricingUtils.getCanonicalPriceInTinyCents(PRNG);
        final var currentGasPriceInTinyCents =
                livePricesSource.currentGasPriceInTinycents(now, ContractCall);
        return feesInTinyCents / currentGasPriceInTinyCents;
    }

    @VisibleForTesting
    ExpirableTxnRecord.Builder createUnsuccessfulChildRecord(
            final ResponseCodeEnum status, final MessageFrame frame) {
        final var childRecord = creator.createUnsuccessfulSyntheticRecord(status);
        addContractCallResultToRecord(childRecord, null, Optional.of(status), frame);
        return childRecord;
    }

    @VisibleForTesting
    ExpirableTxnRecord.Builder createSuccessfulChildRecord(
            final Bytes randomNum, final MessageFrame frame, final Bytes input) {
        final var effectsTracker = new SideEffectsTracker();
        trackPrngOutput(effectsTracker, input, randomNum);
        final var childRecord =
                creator.createSuccessfulSyntheticRecord(
                        Collections.emptyList(), effectsTracker, EMPTY_MEMO);
        addContractCallResultToRecord(childRecord, randomNum, Optional.empty(), frame);
        return childRecord;
    }

    private void trackPrngOutput(
            final SideEffectsTracker effectsTracker, final Bytes input, final Bytes randomNum) {
        final var selector = input.getInt(0);
        if (randomNum == null) {
            return;
        }
        if (selector == PSEUDORANDOM_SEED_GENERATOR_SELECTOR) {
            effectsTracker.trackRandomBytes(randomNum.toArray());
        }
    }

    @VisibleForTesting
    TransactionBody.Builder body(final Bytes randomNum) {
        final var txnBody = TransactionBody.newBuilder();
        if (randomNum == null) {
            return txnBody;
        }
        final var body = UtilPrngTransactionBody.newBuilder();
        return txnBody.setUtilPrng(body.build());
    }

    private void addContractCallResultToRecord(
            final ExpirableTxnRecord.Builder childRecord,
            final Bytes result,
            final Optional<ResponseCodeEnum> errorStatus,
            final MessageFrame frame) {
        updater = (HederaStackedWorldStateUpdater) frame.getWorldUpdater();
        final var unaliasedSenderAddress =
                updater.permissivelyUnaliased(frame.getSenderAddress().toArray());
        final var senderAddress = Address.wrap(Bytes.of(unaliasedSenderAddress));
        PrecompileUtils.addContractCallResultToRecord(
                gasRequirement,
                childRecord,
                result,
                errorStatus,
                frame,
                properties.shouldExportPrecompileResults(),
                true,
                senderAddress);
    }

    @VisibleForTesting
    public void setGasRequirement(final long gasRequirement) {
        this.gasRequirement = gasRequirement;
    }
}
