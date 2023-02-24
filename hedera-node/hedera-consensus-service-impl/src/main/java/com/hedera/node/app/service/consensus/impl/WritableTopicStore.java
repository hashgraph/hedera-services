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

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.consensus.entity.Topic;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableStates;
import com.hederahashgraph.api.proto.java.Timestamp;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Provides write methods for modifying underlying data storage mechanisms for
 * working with Topics.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail.
 */
public class WritableTopicStore {
    /** The underlying data storage class that holds the topic data. */
    private final WritableKVState<Long, MerkleTopic> topicState;

    /**
     * Create a new {@link WritableTopicStore} instance.
     *
     * @param states The state to use.
     */
    public WritableTopicStore(@NonNull final WritableStates states) {
        requireNonNull(states);

        this.topicState = states.get("TOPICS");
    }

    /**
     * Persists a new {@link Topic} into the state, as well as exporting its ID to the transaction
     * receipt.
     *
     * @param topic - the topic to be mapped onto a new {@link MerkleTopic} and persisted.
     */
    public void commit(@NonNull final Topic topic) {
        requireNonNull(topicState);
        requireNonNull(topic);
        topicState.put(topic.topicNumber(), asMerkleTopic(topic));
    }

    private MerkleTopic asMerkleTopic(@NonNull final Topic topic) {
        final var merkle = new MerkleTopic();
        topic.getAdminKey().ifPresent(key -> merkle.setAdminKey((JKey) key));
        topic.getSubmitKey().ifPresent(key -> merkle.setSubmitKey((JKey) key));
        merkle.setMemo(topic.memo());
        if (topic.autoRenewAccountNumber() != 0) {
            merkle.setAutoRenewAccountId(EntityId.fromNum(topic.autoRenewAccountNumber()));
        }
        merkle.setAutoRenewDurationSeconds(topic.autoRenewSecs());
        merkle.setExpirationTimestamp(RichInstant.fromGrpc(
                Timestamp.newBuilder().setSeconds(topic.expiry()).build()));
        merkle.setDeleted(topic.deleted());
        merkle.setSequenceNumber(topic.sequenceNumber());
        return merkle;
    }
}
