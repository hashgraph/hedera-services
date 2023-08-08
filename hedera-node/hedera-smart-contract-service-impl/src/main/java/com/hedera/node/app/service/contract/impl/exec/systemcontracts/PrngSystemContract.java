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
import com.hedera.node.app.service.contract.impl.exec.scope.HandleSystemContractOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaOperations;
import com.hedera.node.app.service.contract.impl.records.ContractCallRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Optional;
import javax.inject.Singleton;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
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
public class PrngSystemContract extends AbstractPrecompiledContract {
    private static final String PRECOMPILE_NAME = "PRNG";
    // random256BitGenerator(uint256)
    static final int PSEUDORANDOM_SEED_GENERATOR_SELECTOR = 0xd83bf9a1;
    public static final String PRNG_PRECOMPILE_ADDRESS = "0x169";
    private final HederaOperations hederaOperations;
    private final HandleSystemContractOperations handleSystemContractOperations;
    private long gasRequirement;

    public PrngSystemContract(
            @NonNull final GasCalculator gasCalculator,
            @NonNull final HederaOperations hederaOperations,
            @NonNull final HandleSystemContractOperations handleSystemContractOperations) {
        super(PRECOMPILE_NAME, gasCalculator);
        this.hederaOperations = hederaOperations;
        this.handleSystemContractOperations = handleSystemContractOperations;
    }

    @Override
    public long gasRequirement(final Bytes bytes) {
        return gasRequirement;
    }

    @Override
    public @NonNull PrecompileContractResult computePrecompile(final Bytes input, @NonNull final MessageFrame frame) {
        requireNonNull(input);
        requireNonNull(frame);

        // compute the gas requirement
        gasRequirement =
                calculateGas(Instant.ofEpochSecond(frame.getBlockValues().getTimestamp()));

        // get the contract ID
        final ContractID contractID = asEvmContractId(Address.fromHexString(PRNG_PRECOMPILE_ADDRESS));

        try {
            // compute the pseudorandom number
            final var randomNum = generatePseudoRandomData(input);
            requireNonNull(randomNum);
            final var result = PrecompiledContract.PrecompileContractResult.success(randomNum);

            // create a child record if we are not in a static call
            if(!frame.isStatic()) {
                createSuccessfulRecord(randomNum, contractID);
            }

            return result;
        } catch (Exception e) {
            // create a failed record and returned a halt result
            createFailedRecord(FAIL_INVALID.toString(), contractID);
            return PrecompiledContract.PrecompileContractResult.halt(Bytes.EMPTY, Optional.of(INVALID_OPERATION));
        }
    }

    void createSuccessfulRecord(@NonNull final Bytes randomNum, @NonNull final ContractID contractID) {
        requireNonNull(randomNum);
        requireNonNull(contractID);
        final var childRecord = handleSystemContractOperations.createChildRecord(ContractCallRecordBuilder.class);
        childRecord
                .contractID(contractID)
                .status(SUCCESS)
                .contractCallResult(contractFunctionResultSuccessFor(gasRequirement, randomNum, contractID));
    }

    void createFailedRecord(@NonNull final String errorMsg, @NonNull final ContractID contractID) {
        requireNonNull(errorMsg);
        requireNonNull(contractID);
        final var childRecord = handleSystemContractOperations.createChildRecord(ContractCallRecordBuilder.class);
        childRecord
                .contractID(contractID)
                .status(FAIL_INVALID)
                .contractCallResult(contractFunctionResultFailedFor(gasRequirement, errorMsg, contractID));
    }

    Bytes generatePseudoRandomData(@NonNull final Bytes input) {
        final var selector = input.getInt(0);
        if(selector == PSEUDORANDOM_SEED_GENERATOR_SELECTOR) {
            return random256BitGenerator();
        }
        return null;
    }

    Bytes random256BitGenerator() {
        final var entropy = hederaOperations.entropy();
        if (entropy.equals(ZERO_ENTROPY)) {
            return null;
        }
        return Bytes.wrap(entropy.toByteArray(), 0, 32);
    }

    long calculateGas(@NonNull final Instant now) {
        // TODO: Update gas calculations once the fee calculator classes are available
        // final var feesInTinyCents = pricingUtils.getCanonicalPriceInTinyCents(PRNG);
        // final var currentGasPriceInTinyCents = livePricesSource.currentGasPriceInTinycents(now, ContractCall);
        // return feesInTinyCents / currentGasPriceInTinyCents;
        return 0;
    }
}
