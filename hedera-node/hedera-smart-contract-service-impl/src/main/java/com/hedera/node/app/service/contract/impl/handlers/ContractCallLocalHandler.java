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

package com.hedera.node.app.service.contract.impl.handlers;

import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CALL_LOCAL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_NEGATIVE_GAS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.isLongZeroAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.numberOfLongZero;
import static com.hedera.node.app.spi.validation.Validations.mustExist;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.QueryHeader;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.contract.ContractCallLocalQuery;
import com.hedera.hapi.node.contract.ContractCallLocalResponse;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.node.app.hapi.utils.CommonPbjConverters;
import com.hedera.node.app.hapi.utils.fee.SmartContractFeeBuilder;
import com.hedera.node.app.service.contract.impl.exec.QueryComponent;
import com.hedera.node.app.service.contract.impl.exec.QueryComponent.Factory;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.PaidQueryHandler;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.InstantSource;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#CONTRACT_CALL_LOCAL}.
 */
@Singleton
public class ContractCallLocalHandler extends PaidQueryHandler {
    private final Provider<QueryComponent.Factory> provider;
    private final GasCalculator gasCalculator;
    private final InstantSource instantSource;

    /**
     *  Constructs a {@link ContractCreateHandler} with the given {@link Provider}, {@link GasCalculator} and {@link InstantSource}.
     *
     * @param provider the provider to be used
     * @param gasCalculator the gas calculator to be used
     * @param instantSource the source of the current instant
     */
    @Inject
    public ContractCallLocalHandler(
            @NonNull final Provider<Factory> provider,
            @NonNull final GasCalculator gasCalculator,
            @NonNull final InstantSource instantSource) {
        this.provider = requireNonNull(provider);
        this.gasCalculator = requireNonNull(gasCalculator);
        this.instantSource = requireNonNull(instantSource);
    }

    @Override
    public QueryHeader extractHeader(@NonNull final Query query) {
        requireNonNull(query);
        return query.contractCallLocalOrThrow().header();
    }

    @Override
    public Response createEmptyResponse(@NonNull final ResponseHeader header) {
        requireNonNull(header);
        final var response = ContractCallLocalResponse.newBuilder().header(header);
        return Response.newBuilder().contractCallLocal(response).build();
    }

    @Override
    public void validate(@NonNull final QueryContext context) throws PreCheckException {
        requireNonNull(context);
        final var query = context.query();
        final ContractCallLocalQuery op = query.contractCallLocalOrThrow();
        final var requestedGas = op.gas();
        validateTruePreCheck(requestedGas >= 0, CONTRACT_NEGATIVE_GAS);
        final var maxGasLimit =
                context.configuration().getConfigData(ContractsConfig.class).maxGasPerSec();
        validateTruePreCheck(requestedGas <= maxGasLimit, MAX_GAS_LIMIT_EXCEEDED);
        final var intrinsicGas = gasCalculator.transactionIntrinsicGasCost(
                org.apache.tuweni.bytes.Bytes.wrap(op.functionParameters().toByteArray()), false);
        validateTruePreCheck(op.gas() >= intrinsicGas, INSUFFICIENT_GAS);

        final var contractID = op.contractID();
        mustExist(contractID, INVALID_CONTRACT_ID);
        // A contract or token contract corresponding to that contract ID must exist in state (otherwise we have
        // nothing
        // to call)
        final var contract = context.createStore(ReadableAccountStore.class).getContractById(contractID);
        if (contract == null) {
            var tokenNum = contractID.contractNumOrElse(0L);
            // For convenience also translate a long-zero address to a token ID
            if (contractID.hasEvmAddress()) {
                final var evmAddress = contractID.evmAddressOrThrow().toByteArray();
                if (isLongZeroAddress(evmAddress)) {
                    tokenNum = numberOfLongZero(evmAddress);
                }
            }
            final var tokenID = TokenID.newBuilder().tokenNum(tokenNum).build();
            final var tokenContract =
                    context.createStore(ReadableTokenStore.class).get(tokenID);
            mustExist(tokenContract, INVALID_CONTRACT_ID);
        }
    }

    @Override
    public Response findResponse(@NonNull final QueryContext context, @NonNull final ResponseHeader header) {
        requireNonNull(context);
        requireNonNull(header);

        final var component = provider.get().create(context, instantSource.instant(), CONTRACT_CALL_LOCAL);
        final var outcome = component.contextQueryProcessor().call();

        final var responseHeader = outcome.isSuccess()
                ? header
                : header.copyBuilder()
                        .nodeTransactionPrecheckCode(outcome.status())
                        .build();
        var response = ContractCallLocalResponse.newBuilder();
        response.header(responseHeader);
        response.functionResult(outcome.result());

        return Response.newBuilder().contractCallLocal(response).build();
    }

    @NonNull
    @Override
    public Fees computeFees(@NonNull final QueryContext context) {
        requireNonNull(context);
        final var op = context.query().contractCallLocalOrThrow();
        final var contractsConfig = context.configuration().getConfigData(ContractsConfig.class);
        return context.feeCalculator().legacyCalculate(sigValueObj -> {
            final var contractFnResult = ContractFunctionResult.newBuilder()
                    .setContractID(CommonPbjConverters.fromPbj(op.contractIDOrElse(ContractID.DEFAULT)))
                    .setContractCallResult(
                            CommonPbjConverters.fromPbj(Bytes.wrap(new byte[contractsConfig.localCallEstRetBytes()])))
                    .build();
            final var builder = new SmartContractFeeBuilder();
            final var feeData = builder.getContractCallLocalFeeMatrices(
                    (int) op.functionParameters().length(),
                    contractFnResult,
                    CommonPbjConverters.fromPbjResponseType(
                            op.headerOrElse(QueryHeader.DEFAULT).responseType()));
            return feeData.toBuilder()
                    .setNodedata(feeData.getNodedata().toBuilder().setGas(op.gas()))
                    .build();
        });
    }
}
