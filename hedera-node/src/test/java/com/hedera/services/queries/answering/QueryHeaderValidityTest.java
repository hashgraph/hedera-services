package com.hedera.services.queries.answering;

import com.hederahashgraph.api.proto.java.ConsensusGetTopicInfoQuery;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import org.junit.jupiter.api.Test;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_QUERY_HEADER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.*;

class QueryHeaderValidityTest {
	final QueryHeaderValidity subject = new QueryHeaderValidity();

	@Test
	void recognizesMissingHeader() {
		// given:
		final var missingHeader = Query.getDefaultInstance();

		// expect:
		assertEquals(MISSING_QUERY_HEADER, subject.checkHeader(missingHeader));
	}

	@Test
	void rejectsCostAnswerStateProof() {
		// given:
		final var costAnswerStateProof = Query.newBuilder()
				.setConsensusGetTopicInfo(ConsensusGetTopicInfoQuery.newBuilder()
						.setHeader(QueryHeader.newBuilder()
								.setResponseType(ResponseType.COST_ANSWER_STATE_PROOF)))
				.build();

		// expect:
		assertEquals(NOT_SUPPORTED, subject.checkHeader(costAnswerStateProof));
	}

	@Test
	void rejectsAnswerOnlyStateProof() {
		// given:
		final var answerStateProof = Query.newBuilder()
				.setConsensusGetTopicInfo(ConsensusGetTopicInfoQuery.newBuilder()
						.setHeader(QueryHeader.newBuilder()
								.setResponseType(ResponseType.ANSWER_STATE_PROOF)))
				.build();

		// expect:
		assertEquals(NOT_SUPPORTED, subject.checkHeader(answerStateProof));
	}

	@Test
	void acceptsSupportedResponseType() {
		// given:
		final var answerStateProof = Query.newBuilder()
				.setConsensusGetTopicInfo(ConsensusGetTopicInfoQuery.newBuilder()
						.setHeader(QueryHeader.newBuilder()
								.setResponseType(ResponseType.ANSWER_ONLY)))
				.build();

		// expect:
		assertEquals(OK, subject.checkHeader(answerStateProof));
	}
}