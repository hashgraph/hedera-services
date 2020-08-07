package com.hedera.services.bdd.spec.infrastructure.providers.ops.consensus;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.EntityNameProvider;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.transactions.consensus.HapiTopicUpdate;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoUpdate;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TopicID;

import java.util.List;
import java.util.Optional;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.updateTopic;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOPIC_EXPIRED;
import static java.util.Collections.EMPTY_LIST;

public class RandomTopicUpdate implements OpProvider {
	private final EntityNameProvider<TopicID> topics;

	private final ResponseCodeEnum[] permissibleOutcomes = standardOutcomesAnd(
			TOPIC_EXPIRED,
			INVALID_TOPIC_ID
	);

	public RandomTopicUpdate(
			EntityNameProvider<TopicID> topics) {
		this.topics = topics;
	}

	@Override
	public List<HapiSpecOperation> suggestedInitializers() {
		return EMPTY_LIST;
	}

	@Override
	public Optional<HapiSpecOperation> get() {
		final var target = topics.getQualifying();
		if (target.isEmpty()) {
			return Optional.empty();
		}

		HapiTopicUpdate op =  updateTopic(target.get())
				.hasKnownStatusFrom(permissibleOutcomes)
				.hasPrecheckFrom(STANDARD_PERMISSIBLE_PRECHECKS);
		return Optional.of(op);
	}
}
