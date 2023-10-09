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

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asHeadlongAddress;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.token.TokenCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.token.records.CryptoCreateRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.nio.ByteBuffer;

public class ClassicCreatesCall extends AbstractHtsCall {

    @NonNull
    final TransactionBody syntheticCreate;

    private final AddressIdConverter addressIdConverter;
    private final VerificationStrategy verificationStrategy;
    private final org.hyperledger.besu.datatypes.Address spender;

    public ClassicCreatesCall(
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final TransactionBody syntheticCreate,
            @NonNull final VerificationStrategy verificationStrategy,
            @NonNull final org.hyperledger.besu.datatypes.Address spender,
            @NonNull final AddressIdConverter addressIdConverter) {
        super(enhancement);
        this.syntheticCreate = requireNonNull(syntheticCreate);
        this.verificationStrategy = requireNonNull(verificationStrategy);
        this.spender = requireNonNull(spender);
        this.addressIdConverter = requireNonNull(addressIdConverter);
    }

    @Override
    public @NonNull PricedResult execute() {
        final var spenderId = addressIdConverter.convert(asHeadlongAddress(spender.toArrayUnsafe()));
        final var recordBuilder = systemContractOperations()
                .dispatch(syntheticCreate, verificationStrategy, spenderId, CryptoCreateRecordBuilder.class);
        final var customFees =
                ((TokenCreateTransactionBody) syntheticCreate.data().value()).customFees();
        final var tokenType =
                ((TokenCreateTransactionBody) syntheticCreate.data().value()).tokenType();
        if (recordBuilder.status() != ResponseCodeEnum.SUCCESS) {
            return gasOnly(revertResult(recordBuilder.status(), 0L));
        } else {
            final var isFungible = tokenType == TokenType.FUNGIBLE_COMMON ? true : false;
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

            // @TODO zero should not be hardcoded
            return gasOnly(successResult(encodedOutput, 0L));
        }
    }
}
