/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static com.hedera.hapi.node.base.HederaFunctionality.UTIL_PRNG;
import static com.hedera.node.app.hapi.utils.CommonPbjConverters.toPbj;
import static com.hedera.node.app.hapi.utils.ValidationUtils.validateTrue;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.systemContractGasCalculatorOf;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asEvmContractId;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.tuweniToPbjBytes;
import static com.hedera.node.app.service.contract.impl.utils.SystemContractUtils.successResultOfZeroValueTraceable;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static java.util.Objects.requireNonNull;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INVALID_OPERATION;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.util.UtilPrngTransactionBody;
import com.hedera.node.app.hapi.utils.InvalidTransactionException;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchType;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy.Decision;
import com.hedera.node.app.service.contract.impl.records.ContractCallStreamBuilder;
import com.hedera.node.app.service.contract.impl.state.AbstractProxyEvmAccount;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import edu.umd.cs.findbugs.annotations.NonNull;
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
        gasRequirement = calculateGas(frame);

        // get the contract ID
        final ContractID contractID = asEvmContractId(Address.fromHexString(PRNG_PRECOMPILE_ADDRESS));

        try {
            validateTrue(input.size() >= 4, INVALID_TRANSACTION_BODY);
            // compute the pseudorandom number
            final var randomNum = generatePseudoRandomData(input, frame);
            requireNonNull(randomNum);
            final var result = PrecompiledContract.PrecompileContractResult.success(randomNum);

            // create a child record
            createSuccessfulRecord(frame, randomNum, contractID);

            return new FullResult(result, gasRequirement, null);
        } catch (InvalidTransactionException e) {
            // This error is caused by the user sending in the wrong selector
            createFailedRecord(frame, e.getResponseCode(), contractID);
            return new FullResult(
                    PrecompiledContract.PrecompileContractResult.halt(Bytes.EMPTY, Optional.of(INVALID_OPERATION)),
                    gasRequirement,
                    null);
        } catch (Exception e) {
            // Log a warning as this error will be caused by insufficient entropy
            log.warn("Internal precompile failure", e);
            createFailedRecord(frame, FAIL_INVALID, contractID);
            return new FullResult(
                    PrecompiledContract.PrecompileContractResult.halt(Bytes.EMPTY, Optional.of(INVALID_OPERATION)),
                    gasRequirement,
                    null);
        }
    }

    void createSuccessfulRecord(
            @NonNull MessageFrame frame, @NonNull final Bytes randomNum, @NonNull final ContractID contractID) {
        if (!frame.isStatic()) {
            requireNonNull(frame);
            requireNonNull(randomNum);
            requireNonNull(contractID);
            var updater = (ProxyWorldUpdater) frame.getWorldUpdater();
            final var senderId = ((AbstractProxyEvmAccount) updater.getAccount(frame.getSenderAddress())).hederaId();

            var data = successResultOfZeroValueTraceable(
                    gasRequirement, randomNum, frame.getRemainingGas(), frame.getInputData(), senderId);

            updater.enhancement()
                    .systemOperations()
                    .dispatch(synthBody(), key -> Decision.INVALID, senderId, ContractCallStreamBuilder.class)
                    .contractCallResult(data)
                    .entropyBytes(tuweniToPbjBytes(randomNum));
        }
    }

    void createFailedRecord(
            @NonNull MessageFrame frame,
            @NonNull final ResponseCodeEnum responseCode,
            @NonNull final ContractID contractID) {
        if (!frame.isStatic()) {
            requireNonNull(frame);
            requireNonNull(contractID);
            var updater = (ProxyWorldUpdater) frame.getWorldUpdater();

            final var senderId = ((AbstractProxyEvmAccount) updater.getAccount(frame.getSenderAddress())).hederaId();

            final var contractResult = ContractFunctionResult.newBuilder()
                    .gasUsed(gasRequirement)
                    .functionParameters(tuweniToPbjBytes(frame.getInputData()))
                    .errorMessage(null)
                    .contractID(contractID)
                    .senderId(senderId)
                    .gas(frame.getRemainingGas())
                    .build();

            updater.enhancement()
                    .systemOperations()
                    .externalizePreemptedDispatch(synthBody(), toPbj(responseCode), UTIL_PRNG)
                    .contractCallResult(contractResult);
        }
    }

    private TransactionBody synthBody() {
        return TransactionBody.newBuilder()
                .utilPrng(UtilPrngTransactionBody.DEFAULT)
                .build();
    }

    Bytes generatePseudoRandomData(@NonNull final Bytes input, @NonNull final MessageFrame frame) {
        final var selector = input.getInt(0);
        if (selector == PSEUDORANDOM_SEED_GENERATOR_SELECTOR) {
            return random256BitGenerator(frame);
        }
        throw new InvalidTransactionException(
                "Invalid selector for PRNG precompile", ResponseCodeEnum.REVERTED_SUCCESS);
    }

    Bytes random256BitGenerator(final MessageFrame frame) {
        final var entropy = ((ProxyWorldUpdater) frame.getWorldUpdater()).entropy();
        return entropy.slice(0, 32);
    }

    long calculateGas(@NonNull final MessageFrame frame) {
        final var gasCalculator = systemContractGasCalculatorOf(frame);
        if (frame.isStatic()) {
            return gasCalculator.viewGasRequirement();
        }

        return Math.max(
                gasCalculator.canonicalGasRequirement(DispatchType.UTIL_PRNG), gasCalculator.viewGasRequirement());
    }
}
