package com.hedera.services.fees.calculation.consensus.queries;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.calculation.QueryResourceUsageEstimator;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.fee.ConsensusServiceFeeBuilder;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.legacy.core.jproto.JKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hedera.services.state.merkle.MerkleEntityId.fromTopicId;
import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;
import static com.hederahashgraph.fee.ConsensusServiceFeeBuilder.computeVariableSizedFieldsUsage;
import static com.hederahashgraph.fee.FeeBuilder.*;

public class GetTopicInfoResourceUsage implements QueryResourceUsageEstimator {
	private static final Logger log = LogManager.getLogger(GetTopicInfoResourceUsage.class);

	@Override
	public boolean applicableTo(Query query) {
		return query.hasConsensusGetTopicInfo();
	}

	@Override
	public FeeData usageGiven(Query query, StateView view) {
		return usageGivenType(query, view, query.getConsensusGetTopicInfo().getHeader().getResponseType());
	}

	@Override
	public FeeData usageGivenType(Query query, StateView view, ResponseType responseType) {
		MerkleTopic merkleTopic = view.topics().get(fromTopicId(query.getConsensusGetTopicInfo().getTopicID()));

		if (merkleTopic == null) {
			return FeeData.getDefaultInstance();
		}

		int bpr = BASIC_QUERY_RES_HEADER
				+ getStateProofSize(responseType)
				+ BASIC_ENTITY_ID_SIZE
				+ getTopicInfoSize(merkleTopic);
		var feeMatrices = FeeComponents.newBuilder()
				.setBpt(BASIC_QUERY_HEADER + BASIC_ENTITY_ID_SIZE)
				.setVpt(0)
				.setRbh(0)
				.setSbh(0)
				.setGas(0)
				.setTv(0)
				.setBpr(bpr)
				.setSbpr(0)
				.build();
		return getQueryFeeDataMatrices(feeMatrices);
	}

	private static int getTopicInfoSize(MerkleTopic merkleTopic) {
		/* Three longs in a topic representation: sequenceNumber, expirationTime, autoRenewPeriod */
		return TX_HASH_SIZE + 3 * LONG_SIZE + computeVariableSizedFieldsUsage(
						asKeyUnchecked(merkleTopic.getAdminKey()),
						asKeyUnchecked(merkleTopic.getSubmitKey()),
						merkleTopic.getMemo(),
						merkleTopic.hasAutoRenewAccountId());
	}
}
