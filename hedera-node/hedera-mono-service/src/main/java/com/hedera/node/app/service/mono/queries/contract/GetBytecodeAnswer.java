/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.queries.contract;

import static com.hedera.node.app.service.mono.utils.EntityIdUtils.unaliased;
import static com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor.uncheckedFrom;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetBytecode;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.queries.AnswerService;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.ContractGetBytecodeResponse;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.Optional;
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
    public boolean needsAnswerOnlyCost(final Query query) {
        return COST_ANSWER == query.getContractGetBytecode().getHeader().getResponseType();
    }

    @Override
    public boolean requiresNodePayment(final Query query) {
        return typicallyRequiresNodePayment(
                query.getContractGetBytecode().getHeader().getResponseType());
    }

    @Override
    public Response responseGiven(
            final Query query,
            @Nullable final StateView view,
            final ResponseCodeEnum validity,
            final long cost) {
        final var op = query.getContractGetBytecode();
        final var target = EntityIdUtils.unaliased(op.getContractID(), aliasManager);

        final var response = ContractGetBytecodeResponse.newBuilder();
        final var type = op.getHeader().getResponseType();
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
    public ResponseCodeEnum checkValidity(final Query query, final StateView view) {
        final var id = unaliased(query.getContractGetBytecode().getContractID(), aliasManager);

        return validator.queryableContractStatus(id, view.contracts());
    }

    @Override
    public HederaFunctionality canonicalFunction() {
        return ContractGetBytecode;
    }

    @Override
    public ResponseCodeEnum extractValidityFrom(final Response response) {
        return response.getContractGetBytecodeResponse()
                .getHeader()
                .getNodeTransactionPrecheckCode();
    }

    @Override
    public Optional<SignedTxnAccessor> extractPaymentFrom(final Query query) {
        final var paymentTxn = query.getContractGetBytecode().getHeader().getPayment();
        return Optional.ofNullable(uncheckedFrom(paymentTxn));
    }
}
