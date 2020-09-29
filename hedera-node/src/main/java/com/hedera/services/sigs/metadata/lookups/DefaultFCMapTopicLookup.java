package com.hedera.services.sigs.metadata.lookups;

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

import com.hedera.services.sigs.metadata.TopicSigningMetadata;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hederahashgraph.api.proto.java.TopicID;
import com.swirlds.fcmap.FCMap;

import java.util.function.Supplier;

import static com.hedera.services.sigs.order.KeyOrderingFailure.INVALID_TOPIC;
import static com.hedera.services.state.merkle.MerkleEntityId.fromTopicId;

public class DefaultFCMapTopicLookup implements TopicSigMetaLookup {
	private final Supplier<FCMap<MerkleEntityId, MerkleTopic>> topics;

	public DefaultFCMapTopicLookup(Supplier<FCMap<MerkleEntityId, MerkleTopic>> topics) {
		this.topics = topics;
	}

	@Override
	public SafeLookupResult<TopicSigningMetadata> safeLookup(TopicID id) {
		var topic = topics.get().get(fromTopicId(id));
		return (topic == null || topic.isDeleted())
				? SafeLookupResult.failure(INVALID_TOPIC)
				: new SafeLookupResult<>(
				new TopicSigningMetadata(
						topic.hasAdminKey() ? topic.getAdminKey() : null,
						topic.hasSubmitKey() ? topic.getSubmitKey() : null));
	}
}
