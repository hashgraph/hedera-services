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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.mint;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.mintReturnType;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asEvmAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asHeadlongAddress;
import static com.hedera.node.app.service.evm.store.contracts.utils.DescriptorUtils.isTokenProxyRedirect;
import static com.hedera.node.app.service.evm.store.contracts.utils.DescriptorUtils.isViewFunction;
import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.token.TokenMintTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchType;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.token.records.TokenMintRecordBuilder;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.math.BigInteger;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class FungibleMintCall extends AbstractHtsCall implements MintCall {
    private final long amount;

    @Nullable
    private final TokenID tokenId;

    private final VerificationStrategy verificationStrategy;
    private final org.hyperledger.besu.datatypes.Address spender;
    private final AddressIdConverter addressIdConverter;
    private final TransactionBody syntheticTransfer;

    public FungibleMintCall(
            @NonNull final SystemContractGasCalculator gasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            final long amount,
            @Nullable final TokenID tokenId,
            @NonNull final VerificationStrategy verificationStrategy,
            @NonNull final org.hyperledger.besu.datatypes.Address spender,
            @NonNull final AddressIdConverter addressIdConverter,
            @NonNull final TransactionBody syntheticTransfer) {
        super(gasCalculator, enhancement);
        this.amount = amount;
        this.tokenId = tokenId;
        this.verificationStrategy = requireNonNull(verificationStrategy);
        this.spender = requireNonNull(spender);
        this.addressIdConverter = requireNonNull(addressIdConverter);
        this.syntheticTransfer = requireNonNull(syntheticTransfer);
    }

    @Override
    public @NonNull PricedResult execute() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public @NonNull PricedResult execute(final MessageFrame frame) {
        if (tokenId == null) {
            return reversionWith(INVALID_TOKEN_ID, 0L);
        }
        final var spenderId = addressIdConverter.convert(asHeadlongAddress(spender.toArrayUnsafe()));

        TokenMintRecordBuilder recordBuilder;

        if (frame.isStatic() && !isTokenProxyRedirect(frame.getInputData()) && !isViewFunction(frame.getInputData())) {
            recordBuilder = systemContractOperations()
                    .dispatchRemovable(
                            syntheticMintUnits(tokenId, amount),
                            verificationStrategy,
                            spenderId,
                            TokenMintRecordBuilder.class,
                            ExternalizedRecordCustomizer.NOOP_EXTERNALIZED_RECORD_CUSTOMIZER);
        } else {
            recordBuilder = systemContractOperations()
                    .dispatch(
                            syntheticMintUnits(tokenId, amount),
                            verificationStrategy,
                            spenderId,
                            TokenMintRecordBuilder.class);
        }

        var contractCallResultTuple = Tuple.of(
                recordBuilder.status().protoOrdinal(),
                BigInteger.valueOf(recordBuilder.getNewTotalSupply()),
                recordBuilder.getSerialNumbers().stream()
                        .mapToLong(Long::longValue)
                        .toArray());
        var contractCallResult =
                Bytes.wrap(mintReturnType.encode(contractCallResultTuple).array());

        var functionParamsTuple = Tuple.of(
                asHeadlongAddress(asEvmAddress(tokenId.tokenNum())), BigInteger.valueOf(amount), new byte[][] {});
        var functionParams =
                Bytes.wrap(MintTranslator.MINT.encodeCall(functionParamsTuple).array());

        recordBuilder.contractCallResult(ContractFunctionResult.newBuilder()
                .contractCallResult(contractCallResult)
                .gasUsed(gasCalculator.gasRequirement(syntheticTransfer, DispatchType.MINT_FUNGIBLE, spenderId))
                .gas(frame.getRemainingGas())
                .functionParameters(functionParams)
                .build());

        final var encodedOutput = MintTranslator.MINT
                .getOutputs()
                .encodeElements(
                        BigInteger.valueOf(recordBuilder.status().protoOrdinal())
                                .longValue(),
                        BigInteger.valueOf(recordBuilder.getNewTotalSupply()),
                        new long[0]);

        // should here be status with failureCustomizer ?

        return gasOnly(successResult(encodedOutput, gasCalculator.viewGasRequirement()));
    }

    private TransactionBody syntheticMintUnits(@NonNull final TokenID tokenId, final long amount) {
        return TransactionBody.newBuilder()
                .tokenMint(TokenMintTransactionBody.newBuilder()
                        .token(tokenId)
                        .amount(amount)
                        .build())
                .build();
    }
}
