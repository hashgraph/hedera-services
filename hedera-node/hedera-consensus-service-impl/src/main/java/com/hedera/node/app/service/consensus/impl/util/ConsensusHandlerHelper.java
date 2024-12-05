/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.consensus.impl.util;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.node.app.service.consensus.ReadableTopicStore;
import com.hedera.node.app.spi.workflows.HandleException;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Class for retrieving objects in a certain context. For example, during a {@code handler.handle(...)} call.
 * This allows compartmentalizing common validation logic without requiring store implementations to
 * throw inappropriately-contextual exceptions, and also abstracts duplicated business logic out of
 * multiple handlers.
 */
public class ConsensusHandlerHelper {

    private ConsensusHandlerHelper() {
        throw new UnsupportedOperationException("Utility class only");
    }

    /**
     * Returns the topic if it exists and is usable. A {@link HandleException} is thrown if the topic is invalid.
     *
     * @param topicId the ID of the topic to get
     * @param topicStore the {@link ReadableTopicStore} to use for topic retrieval
     * @return the topic if it exists and is usable
     * @throws HandleException if any of the topic conditions are not met
     */
    @NonNull
    public static Topic getIfUsable(@NonNull final TopicID topicId, @NonNull final ReadableTopicStore topicStore) {
        requireNonNull(topicId);
        requireNonNull(topicStore);

        final var topic = topicStore.getTopic(topicId);
        validateTrue(topic != null, INVALID_TOPIC_ID);
        // todo check if we need TOPIC_DELETED
        validateFalse(topic.deleted(), INVALID_TOPIC_ID);
        return topic;
    }
}
