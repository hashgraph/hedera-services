/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.consensus.impl.codecs;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.node.app.service.consensus.ReadableTopicStore;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.WritableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.BiConsumer;

/**
 * Provides a static method to migrate the state of the consensus service from the merkle state to the pbj state
 * and vice versa.
 */
public class ConsensusServiceStateTranslator {

    private ConsensusServiceStateTranslator() {
        // Utility class
    }

    /**
     * Migrates the state of the consensus service from the merkle state to the pbj state.
     */
    @NonNull
    public static void migrateFromMerkleToPbj(
            com.swirlds.merkle.map.MerkleMap<
                            com.hedera.node.app.service.mono.utils.EntityNum,
                            com.hedera.node.app.service.mono.state.merkle.MerkleTopic>
                    monoTopics,
            WritableKVState<TopicID, Topic> appTopics) {
        com.hedera.node.app.service.mono.state.adapters.MerkleMapLike.from(monoTopics)
                .forEachNode(new PutConvertedTopic(appTopics));
    }

    /**
     * Migrates the state of the consensus service from the pbj state to the merkle state.
     */
    @NonNull
    public static Topic stateToPbj(@NonNull com.hedera.node.app.service.mono.state.merkle.MerkleTopic monoTopic) {
        requireNonNull(monoTopic);
        final var topicBuilder = new Topic.Builder();
        topicBuilder.topicId(
                TopicID.newBuilder().topicNum(monoTopic.getKey().longValue()).build());
        topicBuilder.memo(monoTopic.getMemo());
        if (monoTopic.hasAdminKey()) topicBuilder.adminKey(PbjConverter.asPbjKey(monoTopic.getAdminKey()));
        if (monoTopic.hasSubmitKey()) topicBuilder.submitKey(PbjConverter.asPbjKey(monoTopic.getSubmitKey()));
        topicBuilder.autoRenewPeriod(monoTopic.getAutoRenewDurationSeconds());
        final var autoRenewAccountId = monoTopic.getAutoRenewAccountId();
        topicBuilder.autoRenewAccountId(autoRenewAccountId.toPbjAccountId());
        topicBuilder.expirationSecond(monoTopic.getExpirationTimestamp().getSeconds());
        topicBuilder.runningHash(Bytes.wrap(monoTopic.getRunningHash()));
        topicBuilder.sequenceNumber(monoTopic.getSequenceNumber());
        topicBuilder.deleted(monoTopic.isDeleted());

        return topicBuilder.build();
    }

    /**
     * Migrates the state of the consensus service from the pbj state to the merkle state.
     */
    @NonNull
    public static com.hedera.node.app.service.mono.state.merkle.MerkleTopic pbjToState(
            @NonNull TopicID topicID, @NonNull ReadableTopicStore readableTopicStore) {
        requireNonNull(topicID);
        requireNonNull(readableTopicStore);
        final var topic = readableTopicStore.getTopic(topicID);
        return pbjToState(topic);
    }

    /**
     * Migrates the state of the consensus service from the pbj state to the merkle state.
     */
    @NonNull
    public static com.hedera.node.app.service.mono.state.merkle.MerkleTopic pbjToState(@NonNull Topic topic) {
        requireNonNull(topic);
        final com.hedera.node.app.service.mono.state.merkle.MerkleTopic monoTopic =
                new com.hedera.node.app.service.mono.state.merkle.MerkleTopic(
                        topic.memo(),
                        (JKey) com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbjKey(
                                        topic.adminKeyOrElse(Key.DEFAULT))
                                .orElse(null),
                        (JKey) com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbjKey(
                                        topic.submitKeyOrElse(Key.DEFAULT))
                                .orElse(null),
                        topic.autoRenewPeriod(),
                        com.hedera.node.app.service.mono.state.submerkle.EntityId.fromPbjAccountId(
                                topic.autoRenewAccountId()),
                        new com.hedera.node.app.service.mono.state.submerkle.RichInstant(topic.expirationSecond(), 0));
        monoTopic.setKey(EntityNum.fromTopicId(topic.topicId()));
        monoTopic.setRunningHash(PbjConverter.asBytes(topic.runningHash()));
        monoTopic.setSequenceNumber(topic.sequenceNumber());
        monoTopic.setDeleted(topic.deleted());
        return monoTopic;
    }

    private static class PutConvertedTopic
            implements BiConsumer<EntityNum, com.hedera.node.app.service.mono.state.merkle.MerkleTopic> {
        private final WritableKVState<TopicID, Topic> appTopics;

        public PutConvertedTopic(WritableKVState<TopicID, Topic> appTopics) {
            this.appTopics = appTopics;
        }

        @Override
        public void accept(EntityNum entityNum, com.hedera.node.app.service.mono.state.merkle.MerkleTopic merkleTopic) {
            final var pbjTopic = stateToPbj(merkleTopic);
            appTopics.put(pbjTopic.topicId(), pbjTopic);
        }
    }
}
