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

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.consensus.ConsensusGetTopicInfoQuery;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.pbj.runtime.io.Bytes;
import com.hedera.node.app.service.consensus.impl.ReadableTopicStore;
import com.hedera.node.app.service.mono.fees.calculation.consensus.queries.GetTopicInfoResourceUsage;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FeeData;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.QueryHeader;
import com.hedera.hapi.node.base.ResponseType;
import com.hedera.hapi.node.base.TopicID;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonoGetTopicInfoUsageTest {
    @Mock
    private GetTopicInfoResourceUsage delegate;

    @Mock
    private ReadableTopicStore topicStore;

    private final FeeData mockUsage = new FeeData.Builder().build();
    private final AccountID autoRenewId = PbjConverter.toPbj(asAccount("0.0.4"));
    private final byte[] runningHash = "runningHash".getBytes();

    private final Key key = PbjConverter.toPbj(A_COMPLEX_KEY);
    private final EntityNum topicEntityNum = EntityNum.fromLong(1L);
    private final TopicID topicId =
            new TopicID.Builder().topicNum(topicEntityNum.longValue()).build();
    private final String memo = "test memo";
    private final long expirationTime = 1_234_567L;
    private final long sequenceNumber = 1L;
    private final long autoRenewSecs = 100L;
    private final boolean deleted = true;

    private final Topic topic = new Topic(
            topicId.topicNum(),
            sequenceNumber,
            expirationTime,
            autoRenewSecs,
            autoRenewId.accountNum().get(),
            deleted,
            Bytes.wrap(runningHash),
            memo,
            key,
            key);

    private final MerkleTopic adapterTopic = new MerkleTopic(
            memo,
            (JKey) asHederaKey(key).get(),
            (JKey) asHederaKey(key).get(),
            autoRenewSecs,
            new EntityId(0, 0, autoRenewId.accountNum().get()),
            new RichInstant(expirationTime, 0));

    {
        adapterTopic.setRunningHash(runningHash);
        adapterTopic.setSequenceNumber(sequenceNumber);
        adapterTopic.setDeleted(deleted);
    }

    private MonoGetTopicInfoUsage subject;

    @BeforeEach
    void setUp() {
        subject = new MonoGetTopicInfoUsage(delegate);
    }

    @Test
    void usesDelegateWithAdaptedMerkleTopic() {
        final var query = new Query.Builder().consensusGetTopicInfo(
                new ConsensusGetTopicInfoQuery.Builder()
                        .header(new QueryHeader(null, ResponseType.ANSWER_STATE_PROOF))
                        .topicID(topicId).build())
                .build();
        given(topicStore.getTopicLeaf(topicId)).willReturn(Optional.of(topic));
        given(delegate.usageGivenTypeAndTopic(adapterTopic, PbjConverter.fromPbj(ResponseType.ANSWER_STATE_PROOF)))
                .willReturn(PbjConverter.fromPbj(mockUsage));

        final var usage = subject.computeUsage(query, topicStore);

        assertSame(mockUsage, usage);
    }
}
