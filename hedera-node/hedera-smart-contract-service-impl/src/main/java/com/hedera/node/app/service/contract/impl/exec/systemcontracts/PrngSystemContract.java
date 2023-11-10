/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts;

import static com.hedera.hapi.node.base.ResponseCodeEnum.FAIL_INVALID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.scope.HandleHederaOperations.ZERO_ENTROPY;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asEvmContractId;
import static com.hedera.node.app.service.contract.impl.utils.SystemContractUtils.contractFunctionResultFailedFor;
import static com.hedera.node.app.service.contract.impl.utils.SystemContractUtils.contractFunctionResultSuccessFor;
import static java.util.Objects.requireNonNull;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INVALID_OPERATION;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import com.hedera.node.app.service.contract.impl.utils.SystemContractUtils.ResultStatus;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

/**
 * System contract to generate random numbers. This will generate 256-bit pseudorandom number when
 * no range is provided using n-3 record's running hash. If 32-bit integer "range" and 256-bit
 * "seed" is provided returns a pseudorandom 32-bit integer X belonging to [0, range).
 */
@Singleton
public class PrngSystemContract extends AbstractFullContract implements HederaSystemContract {
    private static final Logger log = LogManager.getLogger(PrngSystemContract.class);
    private static final String PRECOMPILE_NAME = "PRNG";
    // random256BitGenerator(uint256)
    static final int PSEUDORANDOM_SEED_GENERATOR_SELECTOR = 0xd83bf9a1;
    public static final String PRNG_PRECOMPILE_ADDRESS = "0x169";
    private long gasRequirement;

    @Inject
    public PrngSystemContract(@NonNull final GasCalculator gasCalculator) {
        super(PRECOMPILE_NAME, gasCalculator);
    }

    @Override
    public FullResult computeFully(@NonNull final Bytes input, @NonNull final MessageFrame frame) {
        requireNonNull(input);
        requireNonNull(frame);

        // compute the gas requirement
        gasRequirement =
                calculateGas(Instant.ofEpochSecond(frame.getBlockValues().getTimestamp()));

        // get the contract ID
        final ContractID contractID = asEvmContractId(Address.fromHexString(PRNG_PRECOMPILE_ADDRESS));

        try {
            // compute the pseudorandom number
            final var randomNum = generatePseudoRandomData(input, frame);
            requireNonNull(randomNum);
            final var result = PrecompiledContract.PrecompileContractResult.success(randomNum);

            // create a child record
            createSuccessfulRecord(frame, randomNum, contractID);

            return new FullResult(result, gasRequirement);
        } catch (InvalidTransactionException e) {
            // This error is caused by the user sending in the wrong selector
            createFailedRecord(frame, FAIL_INVALID.toString(), contractID);
            return new FullResult(
                    PrecompiledContract.PrecompileContractResult.halt(Bytes.EMPTY, Optional.of(INVALID_OPERATION)),
                    gasRequirement);
        } catch (NullPointerException e) {
            // Log a warning as this error will be caused by insufficient entropy
            log.warn("Internal precompile failure", e);
            createFailedRecord(frame, FAIL_INVALID.toString(), contractID);
            return new FullResult(
                    PrecompiledContract.PrecompileContractResult.halt(Bytes.EMPTY, Optional.of(INVALID_OPERATION)),
                    gasRequirement);
        }
    }

    void createSuccessfulRecord(
            @NonNull MessageFrame frame, @NonNull final Bytes randomNum, @NonNull final ContractID contractID) {
        if (!frame.isStatic()) {
            requireNonNull(frame);
            requireNonNull(randomNum);
            requireNonNull(contractID);
            var updater = (ProxyWorldUpdater) frame.getWorldUpdater();
            updater.externalizeSystemContractResults(
                    contractFunctionResultSuccessFor(gasRequirement, randomNum, contractID),
                    ResultStatus.IS_SUCCESS,
                    SUCCESS);
        }
    }

    void createFailedRecord(
            @NonNull MessageFrame frame, @NonNull final String errorMsg, @NonNull final ContractID contractID) {
        if (!frame.isStatic()) {
            requireNonNull(frame);
            requireNonNull(contractID);
            contractFunctionResultFailedFor(gasRequirement, errorMsg, contractID);
            var updater = (ProxyWorldUpdater) frame.getWorldUpdater();
            updater.externalizeSystemContractResults(
                    contractFunctionResultFailedFor(gasRequirement, errorMsg, contractID),
                    ResultStatus.IS_ERROR,
                    FAIL_INVALID);
        }
    }

    Bytes generatePseudoRandomData(@NonNull final Bytes input, @NonNull final MessageFrame frame) {
        final var selector = input.getInt(0);
        if (selector == PSEUDORANDOM_SEED_GENERATOR_SELECTOR) {
            return random256BitGenerator(frame);
        }
        throw new InvalidTransactionException(
                "Invalid selector for PRNG precompile", ResponseCodeEnum.INVALID_TRANSACTION);
    }

    Bytes random256BitGenerator(final MessageFrame frame) {
        final var entropy = ((ProxyWorldUpdater) frame.getWorldUpdater()).entropy();
        if (entropy.equals(Bytes.wrap(ZERO_ENTROPY.toByteArray()))) {
            return null;
        }
        return entropy;
    }

    long calculateGas(@NonNull final Instant now) {
        // @future('8094') Update gas calculations once the fee calculator classes are available
        // final var feesInTinyCents = pricingUtils.getCanonicalPriceInTinyCents(PRNG);
        // final var currentGasPriceInTinyCents = livePricesSource.currentGasPriceInTinycents(now, ContractCall);
        // return feesInTinyCents / currentGasPriceInTinyCents;
        return 0;
    }
}
