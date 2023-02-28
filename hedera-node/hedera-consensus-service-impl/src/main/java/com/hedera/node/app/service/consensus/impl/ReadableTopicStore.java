/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.consensus.impl;

import static com.hedera.node.app.service.consensus.impl.ReadableTopicStore.TopicMetaOrLookupFailureReason.withFailureReason;
import static com.hedera.node.app.service.consensus.impl.ReadableTopicStore.TopicMetaOrLookupFailureReason.withTopicMeta;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TopicID;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;

/**
 * Provides read-only methods for interacting with the underlying data storage mechanisms for
 * working with Topics.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail.
 */
public class ReadableTopicStore extends TopicStore {
    /** The underlying data storage class that holds the topic data. */
    private final ReadableKVState<Long, MerkleTopic> topicState;

    /**
     * Create a new {@link ReadableTopicStore} instance.
     *
     * @param states The state to use.
     */
    public ReadableTopicStore(@NonNull final ReadableStates states) {
        requireNonNull(states);

        this.topicState = states.get("TOPICS");
    }

    /**
     * Returns the topic metadata needed. If the topic doesn't exist returns failureReason. If the
     * topic exists , the failure reason will be null.
     *
     * @param id topic id being looked up
     * @return topic's metadata
     */
    // TODO : Change to return Topic instead of TopicMetadata
    public TopicMetaOrLookupFailureReason getTopicMetadata(@NonNull final TopicID id) {
        requireNonNull(id);

        final var topic = getTopicLeaf(id);

        if (topic.isEmpty()) {
            return withFailureReason(INVALID_TOPIC_ID);
        }
        return withTopicMeta(topicMetaFrom(topic.get()));
    }

    private Optional<MerkleTopic> getTopicLeaf(TopicID id) {
        final var topic = topicState.get(id.getTopicNum());
        return Optional.ofNullable(topic);
    }

    /**
     * Returns the topics metadata if the topic exists. If the topic doesn't exist returns failure reason.
     * @param metadata topic's metadata
     * @param failureReason failure reason if the topic doesn't exist
     */
    public record TopicMetaOrLookupFailureReason(TopicMetadata metadata, ResponseCodeEnum failureReason) {
        public boolean failed() {
            return failureReason != null;
        }

        public static TopicMetaOrLookupFailureReason withFailureReason(final ResponseCodeEnum response) {
            return new TopicMetaOrLookupFailureReason(null, response);
        }

        public static TopicMetaOrLookupFailureReason withTopicMeta(final TopicMetadata meta) {
            return new TopicMetaOrLookupFailureReason(meta, null);
        }
    }
}
