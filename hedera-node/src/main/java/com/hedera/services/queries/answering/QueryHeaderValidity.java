package com.hedera.services.queries.answering;

import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;

import java.util.EnumSet;

import static com.hedera.services.utils.MiscUtils.activeHeaderFrom;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_QUERY_HEADER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_STATE_PROOF;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER_STATE_PROOF;

public class QueryHeaderValidity {
	private EnumSet<ResponseType> UNSUPPORTED_RESPONSE_TYPES = EnumSet.of(ANSWER_STATE_PROOF, COST_ANSWER_STATE_PROOF);

	public ResponseCodeEnum checkHeader(Query query) {
		final var bestGuessHeader = activeHeaderFrom(query);
		if (bestGuessHeader.isEmpty()) {
			return MISSING_QUERY_HEADER;
		} else {
			final var type = bestGuessHeader.get().getResponseType();
			return UNSUPPORTED_RESPONSE_TYPES.contains(type) ? NOT_SUPPORTED : OK;
		}
	}
}
