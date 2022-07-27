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
package com.hedera.services.sigs.metadata.lookups;

import static com.hedera.services.sigs.order.KeyOrderingFailure.INVALID_TOPIC;
import static com.hedera.services.utils.EntityNum.fromTopicId;

import com.hedera.services.sigs.metadata.SafeLookupResult;
import com.hedera.services.sigs.metadata.TopicSigningMetadata;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.TopicID;
import com.swirlds.merkle.map.MerkleMap;
import java.util.function.Supplier;

public class DefaultTopicLookup implements TopicSigMetaLookup {
    private final Supplier<MerkleMap<EntityNum, MerkleTopic>> topics;

    public DefaultTopicLookup(Supplier<MerkleMap<EntityNum, MerkleTopic>> topics) {
        this.topics = topics;
    }

    @Override
    public SafeLookupResult<TopicSigningMetadata> safeLookup(TopicID id) {
        var topic = topics.get().get(fromTopicId(id));
        if (topic == null || topic.isDeleted()) {
            return SafeLookupResult.failure(INVALID_TOPIC);
        } else {
            final var effAdminKey = topic.hasAdminKey() ? topic.getAdminKey() : null;
            final var effSubmitKey = topic.hasSubmitKey() ? topic.getSubmitKey() : null;
            return new SafeLookupResult<>(new TopicSigningMetadata(effAdminKey, effSubmitKey));
        }
    }
}
