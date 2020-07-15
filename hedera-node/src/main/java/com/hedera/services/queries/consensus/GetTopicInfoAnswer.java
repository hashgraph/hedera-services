package com.hedera.services.queries.consensus;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.protobuf.ByteString;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.queries.AnswerService;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.ConsensusGetTopicInfoQuery;
import com.hederahashgraph.api.proto.java.ConsensusGetTopicInfoResponse;
import com.hederahashgraph.api.proto.java.ConsensusTopicInfo;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.swirlds.fcmap.FCMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hedera.services.utils.EntityIdUtils.asAccount;
import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;

import java.util.Optional;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusGetTopicInfo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

public class GetTopicInfoAnswer implements AnswerService {
	private static final Logger log = LogManager.getLogger(GetTopicInfoAnswer.class);

	private final OptionValidator optionValidator;

	public GetTopicInfoAnswer(OptionValidator optionValidator) {
		this.optionValidator = optionValidator;
	}

	@Override
	public ResponseCodeEnum checkValidity(Query query, StateView view) {
		FCMap<MerkleEntityId, MerkleTopic> topics = view.topics();
		ConsensusGetTopicInfoQuery op = query.getConsensusGetTopicInfo();
		return validityOf(op, topics);
	}

	private ResponseCodeEnum validityOf(
			ConsensusGetTopicInfoQuery op,
			FCMap<MerkleEntityId, MerkleTopic> topics
	) {
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
		return typicallyRequiresNodePayment(query.getConsensusGetTopicInfo().getHeader().getResponseType());
	}

	@Override
	public boolean needsAnswerOnlyCost(Query query) {
		return COST_ANSWER == query.getConsensusGetTopicInfo().getHeader().getResponseType();
	}

	@Override
	public Response responseGiven(Query query, StateView view, ResponseCodeEnum validity, long cost) {
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
				ConsensusTopicInfo.Builder info = infoBuilder(op, view);
				response.setHeader(answerOnlyHeader(OK));
				response.setTopicInfo(info);
			}
		}

		return Response.newBuilder().setConsensusGetTopicInfo(response).build();
	}

	private static ConsensusTopicInfo.Builder infoBuilder(ConsensusGetTopicInfoQuery op, StateView view) {

		TopicID id = op.getTopicID();
		MerkleTopic merkleTopic = view.topics().get(MerkleEntityId.fromPojoTopicId(id));
		ConsensusTopicInfo.Builder info = ConsensusTopicInfo.newBuilder();
		if (merkleTopic.hasMemo()) {
			info.setMemo(merkleTopic.getMemo());
		}
		if (merkleTopic.hasAdminKey()) {
			info.setAdminKey(asKeyUnchecked(merkleTopic.getAdminKey()));
		}
		if (merkleTopic.hasSubmitKey()) {
			info.setSubmitKey(asKeyUnchecked(merkleTopic.getSubmitKey()));
		}
		info.setAutoRenewPeriod(Duration.newBuilder().setSeconds(merkleTopic.getAutoRenewDurationSeconds()));
		if (merkleTopic.hasAutoRenewAccountId()) {
			info.setAutoRenewAccount(asAccount(merkleTopic.getAutoRenewAccountId()));
		}
		info.setExpirationTime(merkleTopic.getExpirationTimestamp().toGrpc());
		info.setSequenceNumber(merkleTopic.getSequenceNumber());
		info.setRunningHash(ByteString.copyFrom(merkleTopic.getRunningHash()));

		return info;
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
