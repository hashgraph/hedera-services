/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.fees;

import static com.hedera.node.app.service.consensus.impl.handlers.PbjKeyConverter.fromPbjKey;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.toPbjTopicId;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.node.app.service.consensus.impl.ReadableTopicStore;
import com.hedera.node.app.service.mono.fees.calculation.consensus.queries.GetTopicInfoResourceUsage;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.workflows.query.QueryWorkflow;
import com.hederahashgraph.api.proto.java.ConsensusGetTopicInfoQuery;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;

import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A thin adapter for {@link GetTopicInfoResourceUsage} to be used in {@link QueryWorkflow}.
 * It simply looks up the requested topic in the given {@link ReadableTopicStore}, converts
 * it to a {@link MerkleTopic}, and delegates to the {@link GetTopicInfoResourceUsage} to
 * compute the resource usage.
 */
@Singleton
public class MonoGetTopicInfoUsage {
    private final GetTopicInfoResourceUsage delegate;

    @Inject
    public MonoGetTopicInfoUsage(final GetTopicInfoResourceUsage delegate) {
        this.delegate = delegate;
    }

    /**
     * Computes the resource usage for a the {@link ConsensusGetTopicInfoQuery} in the
     * given top-level {@link Query}, based on the contents of the given
     * {@link ReadableTopicStore} and the requested response type in the query header.
     *
     * @param query the top-level query
     * @param topicStore the topic store
     * @return the resource usage of the contained topic info query
     */
    public FeeData computeUsage(final Query query, final ReadableTopicStore topicStore) {
        final var topicInfoQuery = query.getConsensusGetTopicInfo();
        final var topicId = topicInfoQuery.getTopicID();
        final var responseType = topicInfoQuery.getHeader().getResponseType();
        final var maybeTopic = topicStore.getTopicLeaf(toPbjTopicId(topicId));
        return delegate.usageGivenTypeAndTopic(
                maybeTopic.map(MonoGetTopicInfoUsage::monoTopicFrom).orElse(null), responseType);
    }

    /**
     * Converts a PBJ {@link Topic} to a {@link MerkleTopic} for use with the
     * {@link GetTopicInfoResourceUsage} delegate.
     *
     * @param topic the PBJ topic
     * @return the Merkle topic
     */
    public static MerkleTopic monoTopicFrom(@NonNull final Topic topic) {
        final MerkleTopic monoTopic = new MerkleTopic(
                topic.memo(),
                (JKey) fromPbjKey(topic.adminKeyOrElse(Key.DEFAULT)).orElse(null),
                (JKey) fromPbjKey(topic.submitKeyOrElse(Key.DEFAULT)).orElse(null),
                topic.autoRenewPeriod(),
                new EntityId(0, 0, topic.autoRenewAccountNumber()),
                new RichInstant(topic.expiry(), 0));
        monoTopic.setRunningHash(PbjConverter.asBytes(topic.runningHash()));
        monoTopic.setSequenceNumber(topic.sequenceNumber());
        monoTopic.setDeleted(topic.deleted());
        return monoTopic;
    }
}
