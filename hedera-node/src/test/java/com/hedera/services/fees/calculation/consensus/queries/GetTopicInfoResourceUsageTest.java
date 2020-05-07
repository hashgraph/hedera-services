package com.hedera.services.fees.calculation.consensus.queries;

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

import com.hedera.services.context.domain.topic.Topic;
import com.hedera.services.context.primitives.StateView;
import com.hedera.test.utils.JAccountIDConverter;
import com.hedera.test.utils.JEd25519KeyConverter;
import com.hederahashgraph.api.proto.java.*;
import com.hedera.services.legacy.core.MapKey;
import com.hedera.services.legacy.core.jproto.JAccountID;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JTimestamp;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hedera.test.utils.IdUtils.asTopic;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class GetTopicInfoResourceUsageTest {
	StateView view;
	FCMap<MapKey, Topic> topics;
	TopicID topicId = asTopic("0.0.1234");
	GetTopicInfoResourceUsage subject;

	@BeforeEach
	private void setup() throws Throwable {
		topics = mock(FCMap.class);
		view = new StateView(topics, StateView.EMPTY_ACCOUNTS);

		subject = new GetTopicInfoResourceUsage();
	}

	@Test
	public void recognizesApplicableQuery() {
		// given:
		Query topicInfoQuery = topicInfoQuery(topicId, COST_ANSWER);
		Query nonTopicInfoQuery = nonTopicInfoQuery();

		// expect:
		assertTrue(subject.applicableTo(topicInfoQuery));
		assertFalse(subject.applicableTo(nonTopicInfoQuery));
	}

	@Test
	public void throwsIaeWhenTopicDoesNotExist() {
		// given:
		Query query = topicInfoQuery(topicId, ANSWER_ONLY);

		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.usageGiven(query, view));
	}

	@ParameterizedTest
	@CsvSource({
			", , , , 236, 112",
			"abcdefgh, , , , 236, 120", // bpr += memo size(8)
			"abcdefgh, 0000000000000000000000000000000000000000000000000000000000000000, , , 236, 152", // bpr += 32 for admin key
			"abcdefgh, 0000000000000000000000000000000000000000000000000000000000000000, 1111111111111111111111111111111111111111111111111111111111111111, , 236, 184", // bpr += 32 for submit key
			"abcdefgh, 0000000000000000000000000000000000000000000000000000000000000000, 1111111111111111111111111111111111111111111111111111111111111111, 0.1.2, 236, 208" // bpr += 24 for auto renew account
	})
	public void feeDataAsExpected(
			String memo,
			@ConvertWith(JEd25519KeyConverter.class) JEd25519Key adminKey,
			@ConvertWith(JEd25519KeyConverter.class) JEd25519Key submitKey,
			@ConvertWith(JAccountIDConverter.class) JAccountID autoRenewAccountId,
			int expectedBpt,  // query header + topic id size
			int expectedBpr  // query response header + topic id size + topic info size
	) {
	    // setup:
		Topic topic = new Topic(memo, adminKey, submitKey, 0, autoRenewAccountId, new JTimestamp(1, 0));
		FeeData expectedFeeData = FeeData.newBuilder()
				.setNodedata(FeeComponents.newBuilder().setConstant(1).setBpt(expectedBpt).setBpr(expectedBpr).build())
				.setNetworkdata(FeeComponents.getDefaultInstance())
				.setServicedata(FeeComponents.getDefaultInstance())
				.build();

		// given:
		given(topics.get(MapKey.getMapKey(topicId))).willReturn(topic);

		// when:
		FeeData costAnswerEstimate = subject.usageGiven(topicInfoQuery(topicId, COST_ANSWER), view);
		FeeData answerOnlyEstimate = subject.usageGiven(topicInfoQuery(topicId, ANSWER_ONLY), view);

		// expect:
		assertEquals(expectedFeeData, costAnswerEstimate);
		assertEquals(expectedFeeData, answerOnlyEstimate);
	}

	private Query topicInfoQuery(TopicID topicId, ResponseType type) {
		ConsensusGetTopicInfoQuery.Builder op = ConsensusGetTopicInfoQuery.newBuilder()
				.setTopicID(topicId)
				.setHeader(QueryHeader.newBuilder().setResponseType(type));
		return Query.newBuilder()
				.setConsensusGetTopicInfo(op)
				.build();
	}

	private Query nonTopicInfoQuery() {
		return Query.newBuilder().build();
	}
}
