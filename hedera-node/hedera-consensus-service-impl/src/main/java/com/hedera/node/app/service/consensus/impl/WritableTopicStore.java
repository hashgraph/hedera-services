/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl.TOPICS_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.config.data.TopicsConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;
import java.util.Set;

/**
 * Provides write methods for modifying underlying data storage mechanisms for
 * working with Topics.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail.
 * This class is not complete, it will be extended with other methods like remove, update etc.,
 */
public class WritableTopicStore {
    /** The underlying data storage class that holds the topic data. */
    private final WritableKVState<TopicID, Topic> topicState;

    /**
     * Create a new {@link WritableTopicStore} instance.
     *
     * @param states The state to use.
     * @param configuration The configuration used to read the maximum capacity.
     * @param metrics The metrics-API used to report utilization.
     */
    public WritableTopicStore(
            @NonNull final WritableStates states,
            @NonNull final Configuration configuration,
            @NonNull final Metrics metrics) {
        requireNonNull(states);
        requireNonNull(metrics);

        this.topicState = states.get(TOPICS_KEY);

        final long maxCapacity = configuration.getConfigData(TopicsConfig.class).maxNumber();
        topicState.setupMetrics(metrics, "topics", maxCapacity);
    }

    /**
     * Persists a new {@link Topic} into the state, as well as exporting its ID to the transaction
     * receipt.
     *
     * @param topic - the topic to be mapped onto a new {@link MerkleTopic} and persisted.
     */
    public void put(@NonNull final Topic topic) {
        topicState.put(requireNonNull(topic.topicId()), requireNonNull(topic));
    }

    /**
     * Returns the {@link Topic} with the given number. If no such topic exists, returns {@code Optional.empty()}
     * @param topicID - the id of the topic to be retrieved.
     */
    public Optional<Topic> get(@NonNull final TopicID topicID) {
        final var topic = topicState.get(topicID);
        return Optional.ofNullable(topic);
    }

    /**
     * Returns the {@link Topic} with the given number using {@link WritableKVState#getForModify}.
     * If no such topic exists, returns {@code Optional.empty()}
     * @param topicID - the id of the topic to be retrieved.
     */
    public Optional<Topic> getForModify(@NonNull final TopicID topicID) {
        requireNonNull(topicID);
        final var topic = topicState.getForModify(topicID);
        return Optional.ofNullable(topic);
    }

    /**
     * Returns the number of topics in the state.
     * @return the number of topics in the state.
     */
    public long sizeOfState() {
        return topicState.size();
    }

    /**
     * Returns the set of topics modified in existing state.
     * @return the set of topics modified in existing state
     */
    public Set<TopicID> modifiedTopics() {
        return topicState.modifiedKeys();
    }
}
