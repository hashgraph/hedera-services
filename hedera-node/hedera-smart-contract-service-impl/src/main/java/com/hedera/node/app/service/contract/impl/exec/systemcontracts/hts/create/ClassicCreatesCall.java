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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MISSING_TOKEN_SYMBOL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.hapi.node.base.TokenType.FUNGIBLE_COMMON;
import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.ERROR_DECODING_PRECOMPILE_INPUT;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.haltResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall.PricedResult.gasPlus;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.RC_AND_ADDRESS_ENCODER;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.ZERO_ADDRESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.standardized;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.configOf;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.contractsConfigOf;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.stackIncludesActiveAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asEvmAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asHeadlongAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.headlongAddressOf;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjToBesuAddress;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.ActiveContractVerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.scope.ActiveContractVerificationStrategy.UseTopLevelSigs;
import com.hedera.node.app.service.contract.impl.exec.scope.EitherOrVerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.records.ContractCallRecordBuilder;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.ByteBuffer;
import java.util.Collections;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class ClassicCreatesCall extends AbstractHtsCall {
    /**
     * The mono-service stipulated gas cost for a token creation (remaining fee is collected by sent value)
     */
    private static final long FIXED_GAS_COST = 100_000L;

    @Nullable
    final TransactionBody syntheticCreate;

    private final VerificationStrategy verificationStrategy;
    private final AccountID spenderId;
    private long nonGasCost;

    public ClassicCreatesCall(
            @NonNull final SystemContractGasCalculator systemContractGasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @Nullable final TransactionBody syntheticCreate,
            @NonNull final VerificationStrategy verificationStrategy,
            @NonNull final Address spender,
            @NonNull final AddressIdConverter addressIdConverter) {
        super(systemContractGasCalculator, enhancement, false);
        this.verificationStrategy = requireNonNull(verificationStrategy);
        this.spenderId = addressIdConverter.convert(asHeadlongAddress(spender.toArrayUnsafe()));
        this.syntheticCreate = syntheticCreate;
    }

    private record LegacyActivation(long contractNum, Bytes pbjAddress, Address besuAddress) {}

    @Override
    public @NonNull PricedResult execute(@NonNull final MessageFrame frame) {
        if (syntheticCreate == null) {
            return gasOnly(
                    haltResult(
                            ERROR_DECODING_PRECOMPILE_INPUT,
                            contractsConfigOf(frame).precompileHtsDefaultGasCost()),
                    INVALID_TRANSACTION_BODY,
                    false);
        }
        final var timestampSeconds = frame.getBlockValues().getTimestamp();
        final var timestamp = Timestamp.newBuilder().seconds(timestampSeconds).build();
        final var syntheticCreateWithId = syntheticCreate
                .copyBuilder()
                .transactionID(TransactionID.newBuilder()
                        .accountID(AccountID.DEFAULT)
                        .transactionValidStart(timestamp)
                        .build())
                .build();
        final var baseCost = gasCalculator.canonicalPriceInTinybars(syntheticCreateWithId, spenderId);
        // The non-gas cost is a 20% surcharge on the HAPI TokenCreate price, minus the fee taken as gas
        this.nonGasCost = baseCost + (baseCost / 5) - gasCalculator.gasCostInTinybars(FIXED_GAS_COST);
        if (frame.getValue().lessThan(Wei.of(nonGasCost))) {
            return completionWith(
                    FIXED_GAS_COST,
                    systemContractOperations().externalizePreemptedDispatch(syntheticCreate, INSUFFICIENT_TX_FEE),
                    RC_AND_ADDRESS_ENCODER.encodeElements((long) INSUFFICIENT_TX_FEE.protoOrdinal(), ZERO_ADDRESS));
        } else {
            operations().collectFee(spenderId, nonGasCost);
            // (future) remove after differential testing
            nonGasCost = frame.getValue().toLong();
        }

        final var validity = validityOfSynthOp();
        if (validity != OK) {
            return gasOnly(revertResult(validity, FIXED_GAS_COST), validity, true);
        }

        // Choose a dispatch verification strategy based on whether the legacy activation address is active
        final var dispatchVerificationStrategy = verificationStrategyFor(frame);
        final var recordBuilder = systemContractOperations()
                .dispatch(syntheticCreate, dispatchVerificationStrategy, spenderId, ContractCallRecordBuilder.class);
        recordBuilder.status(standardized(recordBuilder.status()));

        final var status = recordBuilder.status();
        if (status != SUCCESS) {
            return gasPlus(revertResult(recordBuilder, FIXED_GAS_COST), status, false, nonGasCost);
        } else {
            ByteBuffer encodedOutput;
            final var op = syntheticCreate.tokenCreationOrThrow();
            final var customFees = op.customFeesOrElse(Collections.emptyList());
            if (op.tokenType() == FUNGIBLE_COMMON) {
                if (customFees.isEmpty()) {
                    encodedOutput = CreateTranslator.CREATE_FUNGIBLE_TOKEN_V1
                            .getOutputs()
                            .encodeElements((long) SUCCESS.protoOrdinal(), headlongAddressOf(recordBuilder.tokenID()));
                } else {
                    encodedOutput = CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V1
                            .getOutputs()
                            .encodeElements((long) SUCCESS.protoOrdinal(), headlongAddressOf(recordBuilder.tokenID()));
                }
            } else {
                if (customFees.isEmpty()) {
                    encodedOutput = CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V1
                            .getOutputs()
                            .encodeElements((long) SUCCESS.protoOrdinal(), headlongAddressOf(recordBuilder.tokenID()));
                } else {
                    encodedOutput = CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1
                            .getOutputs()
                            .encodeElements((long) SUCCESS.protoOrdinal(), headlongAddressOf(recordBuilder.tokenID()));
                }
            }
            return gasPlus(successResult(encodedOutput, FIXED_GAS_COST, recordBuilder), status, false, nonGasCost);
        }
    }

    private ResponseCodeEnum validityOfSynthOp() {
        final var op = syntheticCreate.tokenCreationOrThrow();
        if (op.symbol().isEmpty()) {
            return MISSING_TOKEN_SYMBOL;
        }
        final var treasuryAccount = nativeOperations().getAccount(op.treasuryOrThrow());
        if (treasuryAccount == null) {
            return INVALID_ACCOUNT_ID;
        }
        if (op.autoRenewAccount() == null) {
            return INVALID_EXPIRATION_TIME;
        }
        return OK;
    }

    private VerificationStrategy verificationStrategyFor(@NonNull final MessageFrame frame) {
        final var legacyActivation = legacyActivationIn(frame);

        // Choose a dispatch verification strategy based on whether the legacy
        // activation address is active (somewhere on the stack)
        return stackIncludesActiveAddress(frame, legacyActivation.besuAddress())
                ? new EitherOrVerificationStrategy(
                        verificationStrategy,
                        new ActiveContractVerificationStrategy(
                                ContractID.newBuilder()
                                        .contractNum(legacyActivation.contractNum())
                                        .build(),
                                legacyActivation.pbjAddress(),
                                false,
                                UseTopLevelSigs.NO))
                : verificationStrategy;
    }

    private LegacyActivation legacyActivationIn(@NonNull final MessageFrame frame) {
        final var literal = configOf(frame).getConfigData(ContractsConfig.class).keysLegacyActivations();
        final var contractNum = Long.parseLong(literal.substring(literal.indexOf("[") + 1, literal.indexOf("]")));
        final var pbjAddress = com.hedera.pbj.runtime.io.buffer.Bytes.wrap(asEvmAddress(contractNum));
        return new LegacyActivation(contractNum, pbjAddress, pbjToBesuAddress(pbjAddress));
    }
}
