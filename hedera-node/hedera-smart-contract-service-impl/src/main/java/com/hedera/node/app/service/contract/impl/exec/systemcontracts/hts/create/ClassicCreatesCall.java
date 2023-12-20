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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MISSING_TOKEN_SYMBOL;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HtsSystemContract.HTS_EVM_ADDRESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.configOf;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asEvmContractId;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asHeadlongAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.headlongAddressOf;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.tuweniToPbjBytes;
import static com.hedera.node.app.service.contract.impl.utils.SystemContractUtils.contractFunctionResultFailedFor;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.token.TokenCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.ActiveContractVerificationStrategy.UseTopLevelSigs;
import com.hedera.node.app.service.contract.impl.exec.scope.CustomContractVerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import com.hedera.node.app.service.token.records.TokenCreateRecordBuilder;
import com.hedera.node.config.data.ContractsConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.ByteBuffer;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class ClassicCreatesCall extends AbstractHtsCall {
    /**
     * The mono-service stipulated minimum gas requirement for a token creation.
     */
    private static final long MINIMUM_TINYBAR_PRICE = 100_000L;

    @NonNull
    final TransactionBody syntheticCreate;

    private final AddressIdConverter addressIdConverter;
    private final VerificationStrategy verificationStrategy;
    private final AccountID spenderId;
    private final Bytes spender;
    private final long gasRequirement;
    private boolean onlyDelegatableContractKeysActive;
    private TokenCreateRecordBuilder recordBuilder;

    public ClassicCreatesCall(
            @NonNull final SystemContractGasCalculator systemContractGasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final TransactionBody syntheticCreate,
            @NonNull final VerificationStrategy verificationStrategy,
            @NonNull final org.hyperledger.besu.datatypes.Address spender,
            @NonNull final AddressIdConverter addressIdConverter,
            final boolean onlyDelegatableContractKeysActive) {
        super(systemContractGasCalculator, enhancement, false);
        this.syntheticCreate = requireNonNull(syntheticCreate);
        this.verificationStrategy = requireNonNull(verificationStrategy);
        this.addressIdConverter = requireNonNull(addressIdConverter);
        this.onlyDelegatableContractKeysActive = onlyDelegatableContractKeysActive;

        this.spender = spender;
        this.spenderId = addressIdConverter.convert(asHeadlongAddress(spender.toArrayUnsafe()));
        this.gasRequirement = gasCalculator.gasRequirement(syntheticCreate, spenderId, MINIMUM_TINYBAR_PRICE);
    }

    @Override
    public @NonNull PricedResult execute() {
        final var token = ((TokenCreateTransactionBody) syntheticCreate.data().value());
        if (token.symbol().isEmpty()) {
            return externalizeUnsuccessfulResult(MISSING_TOKEN_SYMBOL, gasCalculator.viewGasRequirement());
        }

        final var treasuryAccount =
                nativeOperations().getAccount(token.treasury().accountNum());
        if (treasuryAccount == null) {
            return externalizeUnsuccessfulResult(INVALID_ACCOUNT_ID, gasCalculator.viewGasRequirement());
        }
        if (token.autoRenewAccount() == null) {
            return externalizeUnsuccessfulResult(INVALID_EXPIRATION_TIME, gasCalculator.viewGasRequirement());
        }

        if (recordBuilder == null) {
            recordBuilder = systemContractOperations()
                    .dispatch(syntheticCreate, verificationStrategy, spenderId, TokenCreateRecordBuilder.class);
        }

        final var customFees =
                ((TokenCreateTransactionBody) syntheticCreate.data().value()).customFees();
        final var tokenType =
                ((TokenCreateTransactionBody) syntheticCreate.data().value()).tokenType();
        final var status = recordBuilder.status();
        if (status != ResponseCodeEnum.SUCCESS) {
            return gasOnly(revertResult(status, MINIMUM_TINYBAR_PRICE), status, false);
        } else {
            final var isFungible = tokenType == TokenType.FUNGIBLE_COMMON;
            ByteBuffer encodedOutput;

            if (isFungible && customFees.size() == 0) {
                encodedOutput = CreateTranslator.CREATE_FUNGIBLE_TOKEN_V1
                        .getOutputs()
                        .encodeElements(
                                (long) ResponseCodeEnum.SUCCESS.protoOrdinal(),
                                headlongAddressOf(recordBuilder.tokenID()));
            } else if (isFungible && customFees.size() > 0) {
                encodedOutput = CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V1
                        .getOutputs()
                        .encodeElements(
                                (long) ResponseCodeEnum.SUCCESS.protoOrdinal(),
                                headlongAddressOf(recordBuilder.tokenID()));
            } else if (customFees.size() == 0) {
                encodedOutput = CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V1
                        .getOutputs()
                        .encodeElements(
                                (long) ResponseCodeEnum.SUCCESS.protoOrdinal(),
                                headlongAddressOf(recordBuilder.tokenID()));
            } else {
                encodedOutput = CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1
                        .getOutputs()
                        .encodeElements(
                                (long) ResponseCodeEnum.SUCCESS.protoOrdinal(),
                                headlongAddressOf(recordBuilder.tokenID()));
            }
            return gasOnly(successResult(encodedOutput, gasRequirement), status, false);
        }
    }

    @Override
    public @NonNull PricedResult execute(final MessageFrame frame) {
        final var property =
                configOf(frame).getConfigData(ContractsConfig.class).keysLegacyActivations();
        // @TODO converter to parse the data in more consumable way
        final var propertyContractId =
                Long.parseLong(property.substring(property.indexOf("[") + 1, property.indexOf("]")));

        final var parentNumber = ((ProxyWorldUpdater)
                        frame.getWorldUpdater().parentUpdater().get())
                .getPendingCreation()
                .parentNumber();

        // @TODO won't enter here unless parentNumber is 1062784
        if (parentNumber == propertyContractId) {
            final var customStrategy = new CustomContractVerificationStrategy(
                    parentNumber,
                    propertyContractId,
                    tuweniToPbjBytes(spender),
                    onlyDelegatableContractKeysActive,
                    UseTopLevelSigs.NO);
            recordBuilder = systemContractOperations()
                    .dispatch(syntheticCreate, customStrategy, spenderId, TokenCreateRecordBuilder.class);
        }
        if (!frame.getValue().greaterOrEqualThan(Wei.of(gasRequirement))) {
            return externalizeUnsuccessfulResult(INSUFFICIENT_TX_FEE, gasCalculator.viewGasRequirement());
        }
        return execute();
    }

    // @TODO extract externalizeResult() calls into a single location on a higher level
    private PricedResult externalizeUnsuccessfulResult(ResponseCodeEnum responseCode, long gasRequirement) {
        final var result = gasOnly(FullResult.revertResult(responseCode, gasRequirement), responseCode, false);
        final var contractID = asEvmContractId(Address.fromHexString(HTS_EVM_ADDRESS));

        enhancement
                .systemOperations()
                .externalizeResult(
                        contractFunctionResultFailedFor(MINIMUM_TINYBAR_PRICE, responseCode.toString(), contractID),
                        responseCode);
        return result;
    }
}
