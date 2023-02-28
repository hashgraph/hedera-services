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

package com.hedera.node.app.service.mono.queries.consensus;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusGetTopicInfo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.queries.AnswerService;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.ConsensusGetTopicInfoQuery;
import com.hederahashgraph.api.proto.java.ConsensusGetTopicInfoResponse;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Transaction;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GetTopicInfoAnswer implements AnswerService {
    private final OptionValidator optionValidator;

    @Inject
    public GetTopicInfoAnswer(final OptionValidator optionValidator) {
        this.optionValidator = optionValidator;
    }

    @Override
    public ResponseCodeEnum checkValidity(final Query query, final StateView view) {
        final var topics = view.topics();
        final ConsensusGetTopicInfoQuery op = query.getConsensusGetTopicInfo();
        return validityOf(op, topics);
    }

    private ResponseCodeEnum validityOf(
            final ConsensusGetTopicInfoQuery op, final MerkleMapLike<EntityNum, MerkleTopic> topics) {
        if (op.hasTopicID()) {
            return optionValidator.queryableTopicStatus(op.getTopicID(), topics);
        } else {
            return INVALID_TOPIC_ID;
        }
    }

    @Override
    public Optional<SignedTxnAccessor> extractPaymentFrom(final Query query) {
        final Transaction paymentTxn =
                query.getConsensusGetTopicInfo().getHeader().getPayment();
        return Optional.ofNullable(SignedTxnAccessor.uncheckedFrom(paymentTxn));
    }

    @Override
    public boolean requiresNodePayment(final Query query) {
        return typicallyRequiresNodePayment(
                query.getConsensusGetTopicInfo().getHeader().getResponseType());
    }

    @Override
    public boolean needsAnswerOnlyCost(final Query query) {
        return COST_ANSWER == query.getConsensusGetTopicInfo().getHeader().getResponseType();
    }

    @Override
    public Response responseGiven(
            final Query query, @Nullable final StateView view, final ResponseCodeEnum validity, final long cost) {
        final ConsensusGetTopicInfoQuery op = query.getConsensusGetTopicInfo();
        final ConsensusGetTopicInfoResponse.Builder response = ConsensusGetTopicInfoResponse.newBuilder();
        response.setTopicID(op.getTopicID());

        final ResponseType type = op.getHeader().getResponseType();
        if (validity != OK) {
            response.setHeader(header(validity, type, cost));
        } else {
            if (type == COST_ANSWER) {
                response.setHeader(costAnswerHeader(OK, cost));
            } else {
                final var optionalInfo = Objects.requireNonNull(view).infoForTopic(op.getTopicID());
                if (optionalInfo.isPresent()) {
                    response.setHeader(answerOnlyHeader(OK));
                    response.setTopicInfo(optionalInfo.get());
                } else {
                    response.setHeader(answerOnlyHeader(INVALID_TOPIC_ID));
                }
            }
        }

        return Response.newBuilder().setConsensusGetTopicInfo(response).build();
    }

    @Override
    public ResponseCodeEnum extractValidityFrom(final Response response) {
        return response.getConsensusGetTopicInfo().getHeader().getNodeTransactionPrecheckCode();
    }

    @Override
    public HederaFunctionality canonicalFunction() {
        return ConsensusGetTopicInfo;
    }
}
