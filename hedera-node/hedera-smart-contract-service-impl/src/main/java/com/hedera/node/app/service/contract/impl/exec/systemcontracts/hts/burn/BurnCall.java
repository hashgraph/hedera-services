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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.burn;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asHeadlongAddress;

import com.esaulpaugh.headlong.abi.TupleType;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchType;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.SystemContractOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall;
import com.hedera.node.app.service.token.records.TokenBurnRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.function.LongFunction;
import java.util.function.Supplier;

public interface BurnCall extends HtsCall {
    /**
     * Performs the {@link HtsCall#execute()} logic shared between fungible and non-fungible burns.
     *
     * @param tokenId the token ID, if found
     * @param bodySupplier the function to create the transaction body
     * @param dispatchType the dispatch type
     * @param addressIdConverter the address ID converter
     * @param verificationStrategy the verification strategy
     * @param gasCalculator the gas calculator
     * @param spender the spender
     * @param senderId the sender Hedera id
     * @param systemContractOperations the system contract operations
     * @param invalidTokenReversion the invalid token reversion
     * @return the result of the call
     */

    // too many parameters
    @SuppressWarnings("java:S107")
    default @NonNull PricedResult executeBurn(
            @Nullable final TokenID tokenId,
            @NonNull final AccountID senderId,
            @NonNull final TupleType outputs,
            @NonNull final DispatchType dispatchType,
            @NonNull final AddressIdConverter addressIdConverter,
            @NonNull final VerificationStrategy verificationStrategy,
            @NonNull final SystemContractGasCalculator gasCalculator,
            @NonNull final Supplier<TransactionBody> bodySupplier,
            @NonNull final org.hyperledger.besu.datatypes.Address spender,
            @NonNull final SystemContractOperations systemContractOperations,
            @NonNull final LongFunction<PricedResult> invalidTokenReversion) {
        if (tokenId == null) {
            return invalidTokenReversion.apply(gasCalculator.canonicalGasRequirement(dispatchType));
        }
        final var spenderId = addressIdConverter.convert(asHeadlongAddress(spender.toArrayUnsafe()));
        final var body = bodySupplier.get();
        final var gasRequirement = gasCalculator.gasRequirement(body, dispatchType, senderId);
        final var recordBuilder =
                systemContractOperations.dispatch(body, verificationStrategy, spenderId, TokenBurnRecordBuilder.class);
        if (recordBuilder.status() != ResponseCodeEnum.SUCCESS) {
            return gasOnly(revertResult(recordBuilder.status(), gasRequirement));
        }
        final var encodedOutput = outputs.encodeElements(
                (long) ResponseCodeEnum.SUCCESS.protoOrdinal(), recordBuilder.getNewTotalSupply());
        return gasOnly(successResult(encodedOutput, gasRequirement));
    }
}
