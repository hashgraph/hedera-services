package com.hedera.services.bdd.spec.infrastructure.providers.ops.consensus;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.EntityNameProvider;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TopicID;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FILE_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOPIC_EXPIRED;

public class RandomTopicInfo implements OpProvider {
	private final ResponseCodeEnum[] permissibleCostAnswerPrechecks = standardQueryPrechecksAnd(
			TOPIC_EXPIRED,
			INVALID_TOPIC_ID
	);

	private final ResponseCodeEnum[] permissibleAnswerOnlyPrechecks = standardQueryPrechecksAnd(
			TOPIC_EXPIRED,
			INVALID_TOPIC_ID
	);

	private final EntityNameProvider<TopicID> topics;

	public RandomTopicInfo(EntityNameProvider<TopicID> topics) {
		this.topics = topics;
	}

	@Override
	public List<HapiSpecOperation> suggestedInitializers() {
		return Collections.emptyList();
	}

	@Override
	public Optional<HapiSpecOperation> get() {
		final var target = topics.getQualifying();
		if (target.isEmpty()) {
			return Optional.empty();
		}

		var op = QueryVerbs.getTopicInfo(target.get())
				.hasCostAnswerPrecheckFrom(permissibleCostAnswerPrechecks)
				.hasAnswerOnlyPrecheckFrom(permissibleAnswerOnlyPrechecks);

		return Optional.of(op);
	}
}
