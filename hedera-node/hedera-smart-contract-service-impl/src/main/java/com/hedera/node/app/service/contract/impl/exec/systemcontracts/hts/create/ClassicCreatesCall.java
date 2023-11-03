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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MISSING_TOKEN_SYMBOL;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HtsSystemContract.HTS_PRECOMPILE_ADDRESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asEvmContractId;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asHeadlongAddress;
import static com.hedera.node.app.service.contract.impl.utils.SystemContractUtils.contractFunctionResultFailedFor;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.token.TokenCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.utils.SystemContractUtils.ResultStatus;
import com.hedera.node.app.service.token.records.CryptoCreateRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import org.hyperledger.besu.datatypes.Address;

public class ClassicCreatesCall extends AbstractHtsCall {
    /**
     * The mono-service stipulated minimum gas requirement for a token creation.
     */
    private static final long MINIMUM_TINYBAR_PRICE = 100_000L;

    @NonNull
    final TransactionBody syntheticCreate;

    private final AddressIdConverter addressIdConverter;
    private final VerificationStrategy verificationStrategy;
    private final org.hyperledger.besu.datatypes.Address spender;

    public ClassicCreatesCall(
            @NonNull final SystemContractGasCalculator systemContractGasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final TransactionBody syntheticCreate,
            @NonNull final VerificationStrategy verificationStrategy,
            @NonNull final org.hyperledger.besu.datatypes.Address spender,
            @NonNull final AddressIdConverter addressIdConverter) {
        super(systemContractGasCalculator, enhancement);
        this.syntheticCreate = requireNonNull(syntheticCreate);
        this.verificationStrategy = requireNonNull(verificationStrategy);
        this.spender = requireNonNull(spender);
        this.addressIdConverter = requireNonNull(addressIdConverter);
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

        final var spenderId = addressIdConverter.convert(asHeadlongAddress(spender.toArrayUnsafe()));
        final var recordBuilder = systemContractOperations()
                .dispatch(syntheticCreate, verificationStrategy, spenderId, CryptoCreateRecordBuilder.class);
        final var customFees =
                ((TokenCreateTransactionBody) syntheticCreate.data().value()).customFees();
        final var tokenType =
                ((TokenCreateTransactionBody) syntheticCreate.data().value()).tokenType();
        if (recordBuilder.status() != ResponseCodeEnum.SUCCESS) {
            return gasOnly(revertResult(recordBuilder.status(), MINIMUM_TINYBAR_PRICE));
        } else {
            final var isFungible = tokenType == TokenType.FUNGIBLE_COMMON;
            ByteBuffer encodedOutput;

            if (isFungible && customFees.size() == 0) {
                encodedOutput = CreateTranslator.CREATE_FUNGIBLE_TOKEN_V1
                        .getOutputs()
                        .encodeElements(BigInteger.valueOf(ResponseCodeEnum.SUCCESS.protoOrdinal()));
            } else if (isFungible && customFees.size() > 0) {
                encodedOutput = CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V1
                        .getOutputs()
                        .encodeElements(BigInteger.valueOf(ResponseCodeEnum.SUCCESS.protoOrdinal()));
            } else if (customFees.size() == 0) {
                encodedOutput = CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V1
                        .getOutputs()
                        .encodeElements(BigInteger.valueOf(ResponseCodeEnum.SUCCESS.protoOrdinal()));
            } else {
                encodedOutput = CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1
                        .getOutputs()
                        .encodeElements(BigInteger.valueOf(ResponseCodeEnum.SUCCESS.protoOrdinal()));
            }
            final long gasRequirement = gasCalculator.gasRequirement(syntheticCreate, spenderId, MINIMUM_TINYBAR_PRICE);
            return gasOnly(successResult(encodedOutput, gasRequirement));
        }
    }

    private PricedResult externalizeUnsuccessfulResult(ResponseCodeEnum responseCode, long gasRequirement) {
        final var result = gasOnly(revertResult(responseCode, gasRequirement));
        final var contractID = asEvmContractId(Address.fromHexString(HTS_PRECOMPILE_ADDRESS));

        enhancement
                .systemOperations()
                .externalizeResult(
                        contractFunctionResultFailedFor(MINIMUM_TINYBAR_PRICE, responseCode.toString(), contractID),
                        ResultStatus.IS_ERROR,
                        responseCode);
        return result;
    }
}
