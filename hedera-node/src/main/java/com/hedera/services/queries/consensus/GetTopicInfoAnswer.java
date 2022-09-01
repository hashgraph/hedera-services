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
package com.hedera.services.queries.consensus;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusGetTopicInfo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.queries.AnswerService;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.ConsensusGetTopicInfoQuery;
import com.hederahashgraph.api.proto.java.ConsensusGetTopicInfoResponse;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.swirlds.merkle.map.MerkleMap;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GetTopicInfoAnswer implements AnswerService {
    private final OptionValidator optionValidator;

    @Inject
    public GetTopicInfoAnswer(OptionValidator optionValidator) {
        this.optionValidator = optionValidator;
    }

    @Override
    public ResponseCodeEnum checkValidity(Query query, StateView view) {
        MerkleMap<EntityNum, MerkleTopic> topics = view.topics();
        ConsensusGetTopicInfoQuery op = query.getConsensusGetTopicInfo();
        return validityOf(op, topics);
    }

    private ResponseCodeEnum validityOf(
            ConsensusGetTopicInfoQuery op, MerkleMap<EntityNum, MerkleTopic> topics) {
        if (op.hasTopicID()) {
            return optionValidator.queryableTopicStatus(op.getTopicID(), topics);
        } else {
            return INVALID_TOPIC_ID;
        }
    }

    @Override
    public Optional<SignedTxnAccessor> extractPaymentFrom(Query query) {
        Transaction paymentTxn = query.getConsensusGetTopicInfo().getHeader().getPayment();
        return Optional.ofNullable(SignedTxnAccessor.uncheckedFrom(paymentTxn));
    }

    @Override
    public boolean requiresNodePayment(Query query) {
        return typicallyRequiresNodePayment(
                query.getConsensusGetTopicInfo().getHeader().getResponseType());
    }

    @Override
    public boolean needsAnswerOnlyCost(Query query) {
        return COST_ANSWER == query.getConsensusGetTopicInfo().getHeader().getResponseType();
    }

    @Override
    public Response responseGiven(
            Query query, @Nullable StateView view, ResponseCodeEnum validity, long cost) {
        ConsensusGetTopicInfoQuery op = query.getConsensusGetTopicInfo();
        ConsensusGetTopicInfoResponse.Builder response = ConsensusGetTopicInfoResponse.newBuilder();
        response.setTopicID(op.getTopicID());

        ResponseType type = op.getHeader().getResponseType();
        if (validity != OK) {
            response.setHeader(header(validity, type, cost));
        } else {
            if (type == COST_ANSWER) {
                response.setHeader(costAnswerHeader(OK, cost));
            } else {
                var optionalInfo = Objects.requireNonNull(view).infoForTopic(op.getTopicID());
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
    public ResponseCodeEnum extractValidityFrom(Response response) {
        return response.getConsensusGetTopicInfo().getHeader().getNodeTransactionPrecheckCode();
    }

    @Override
    public HederaFunctionality canonicalFunction() {
        return ConsensusGetTopicInfo;
    }
}
