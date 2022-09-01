/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.queries.contract;

import static com.hedera.services.utils.EntityIdUtils.unaliased;
import static com.hedera.services.utils.accessors.SignedTxnAccessor.uncheckedFrom;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetBytecode;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

import com.google.protobuf.ByteString;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.queries.AnswerService;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.ContractGetBytecodeResponse;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GetBytecodeAnswer implements AnswerService {
    private static final byte[] EMPTY_BYTECODE = new byte[0];

    private final OptionValidator validator;
    private final AliasManager aliasManager;

    @Inject
    public GetBytecodeAnswer(final AliasManager aliasManager, final OptionValidator validator) {
        this.aliasManager = aliasManager;
        this.validator = validator;
    }

    @Override
    public boolean needsAnswerOnlyCost(Query query) {
        return COST_ANSWER == query.getContractGetBytecode().getHeader().getResponseType();
    }

    @Override
    public boolean requiresNodePayment(Query query) {
        return typicallyRequiresNodePayment(
                query.getContractGetBytecode().getHeader().getResponseType());
    }

    @Override
    public Response responseGiven(
            Query query, @Nullable StateView view, ResponseCodeEnum validity, long cost) {
        var op = query.getContractGetBytecode();
        final var target = EntityIdUtils.unaliased(op.getContractID(), aliasManager);

        var response = ContractGetBytecodeResponse.newBuilder();
        var type = op.getHeader().getResponseType();
        if (validity != OK) {
            response.setHeader(header(validity, type, cost));
            response.setBytecode(ByteString.copyFrom(EMPTY_BYTECODE));
        } else {
            if (type == COST_ANSWER) {
                response.setHeader(costAnswerHeader(OK, cost));
                response.setBytecode(ByteString.copyFrom(EMPTY_BYTECODE));
            } else {
                /* Include cost here to satisfy legacy regression tests. */
                response.setHeader(answerOnlyHeader(OK, cost));
                response.setBytecode(
                        ByteString.copyFrom(
                                Objects.requireNonNull(view)
                                        .bytecodeOf(target)
                                        .orElse(EMPTY_BYTECODE)));
            }
        }
        return Response.newBuilder().setContractGetBytecodeResponse(response).build();
    }

    @Override
    public ResponseCodeEnum checkValidity(Query query, StateView view) {
        final var id = unaliased(query.getContractGetBytecode().getContractID(), aliasManager);

        return validator.queryableContractStatus(id, view.contracts());
    }

    @Override
    public HederaFunctionality canonicalFunction() {
        return ContractGetBytecode;
    }

    @Override
    public ResponseCodeEnum extractValidityFrom(Response response) {
        return response.getContractGetBytecodeResponse()
                .getHeader()
                .getNodeTransactionPrecheckCode();
    }

    @Override
    public Optional<SignedTxnAccessor> extractPaymentFrom(Query query) {
        var paymentTxn = query.getContractGetBytecode().getHeader().getPayment();
        return Optional.ofNullable(uncheckedFrom(paymentTxn));
    }
}
