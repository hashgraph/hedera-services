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

package com.hedera.node.app.service.contract.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseType.ANSWER_ONLY;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateFalsePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.QueryHeader;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.contract.ContractGetBytecodeResponse;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.node.app.service.contract.impl.state.ContractStateStore;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.PaidQueryHandler;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#CONTRACT_GET_BYTECODE}.
 */
@Singleton
public class ContractGetBytecodeHandler extends PaidQueryHandler {
    @Inject
    public ContractGetBytecodeHandler() {
        // Exists for injection
    }

    @Override
    public QueryHeader extractHeader(@NonNull final Query query) {
        requireNonNull(query);
        return query.contractGetBytecodeOrThrow().header();
    }

    @Override
    public Response createEmptyResponse(@NonNull final ResponseHeader header) {
        requireNonNull(header);
        final var response = ContractGetBytecodeResponse.newBuilder().header(header);
        return Response.newBuilder().contractGetBytecodeResponse(response).build();
    }

    @Override
    public void validate(@NonNull final QueryContext context) throws PreCheckException {
        requireNonNull(context);
        validateFalsePreCheck(contractFrom(context) == null, INVALID_CONTRACT_ID);
        validateFalsePreCheck(contractFrom(context).deleted(), CONTRACT_DELETED);
    }

    @Override
    public Response findResponse(@NonNull final QueryContext context, @NonNull final ResponseHeader header) {
        requireNonNull(context);
        requireNonNull(header);
        final var contractGetBytecode = ContractGetBytecodeResponse.newBuilder().header(header);

        // although ResponseType enum includes an unsupported field ResponseType#ANSWER_STATE_PROOF,
        // the response returned ONLY when both
        // the ResponseHeader#nodeTransactionPrecheckCode is OK and the requested response type is
        // ResponseType#ANSWER_ONLY
        if (header.nodeTransactionPrecheckCode() == OK && header.responseType() == ANSWER_ONLY) {
            final var contract = requireNonNull(contractFrom(context));
            contractGetBytecode.bytecode(bytecodeFrom(context, contract));
        }
        return Response.newBuilder()
                .contractGetBytecodeResponse(contractGetBytecode)
                .build();
    }

    private @Nullable Account contractFrom(@NonNull final QueryContext context) {
        final var accountsStore = context.createStore(ReadableAccountStore.class);
        final var contractId = context.query().contractGetBytecodeOrThrow().contractIDOrElse(ContractID.DEFAULT);
        final var contract = accountsStore.getContractById(contractId);
        return (contract == null || !contract.smartContract()) ? null : contract;
    }

    private Bytes bytecodeFrom(@NonNull final QueryContext context, @NonNull Account contract) {
        final var store = context.createStore(ContractStateStore.class);
        var contractNumber = contract.accountId().accountNum();
        var contractEntityNumber =
                EntityNumber.newBuilder().number(contractNumber).build();
        final var bytecode = store.getBytecode(contractEntityNumber);
        return bytecode.code();
    }

    @NonNull
    @Override
    public Fees computeFees(@NonNull final QueryContext context) {
        return context.feeCalculator().calculate();
    }
}
