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
package com.hedera.services.fees.calculation.consensus.queries;

import static com.hedera.test.utils.IdUtils.asTopic;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.utils.EntityIdConverter;
import com.hedera.test.utils.JEd25519KeyConverter;
import com.hederahashgraph.api.proto.java.ConsensusGetTopicInfoQuery;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TopicID;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.CsvSource;

class GetMerkleTopicInfoResourceUsageTest {
    private StateView view;
    private MerkleMap<EntityNum, MerkleTopic> topics;
    private static final TopicID topicId = asTopic("0.0.1234");
    private GetTopicInfoResourceUsage subject;

    @BeforeEach
    void setup() {
        topics = mock(MerkleMap.class);
        final var children = new MutableStateChildren();
        children.setTopics(topics);
        view = new StateView(null, children, null);

        subject = new GetTopicInfoResourceUsage();
    }

    @Test
    void recognizesApplicableQuery() {
        final var topicInfoQuery = topicInfoQuery(topicId, COST_ANSWER);
        final var nonTopicInfoQuery = Query.getDefaultInstance();

        assertTrue(subject.applicableTo(topicInfoQuery));
        assertFalse(subject.applicableTo(nonTopicInfoQuery));
    }

    @Test
    void throwsIaeWhenTopicDoesNotExist() {
        final var query = topicInfoQuery(topicId, ANSWER_ONLY);

        assertSame(FeeData.getDefaultInstance(), subject.usageGiven(query, view));
    }

    @ParameterizedTest
    @CsvSource({
        ", , , , 236, 112",
        "abcdefgh, , , , 236, 120", // bpr += memo size(8)
        "abcdefgh, 0000000000000000000000000000000000000000000000000000000000000000,"
                + " , , 236, 152", // bpr += 32
        // for admin key
        "abcdefgh, 0000000000000000000000000000000000000000000000000000000000000000,"
                + " 1111111111111111111111111111111111111111111111111111111111111111,"
                + " , 236, 184", // bpr += 32 for
        // submit key
        "abcdefgh, 0000000000000000000000000000000000000000000000000000000000000000,"
                + " 1111111111111111111111111111111111111111111111111111111111111111,"
                + " 0.1.2, 236, 208" // bpr += 24
        // for auto renew account
    })
    void feeDataAsExpected(
            final String memo,
            @ConvertWith(JEd25519KeyConverter.class) final JEd25519Key adminKey,
            @ConvertWith(JEd25519KeyConverter.class) final JEd25519Key submitKey,
            @ConvertWith(EntityIdConverter.class) final EntityId autoRenewAccountId,
            final int expectedBpt, // query header + topic id size
            final int expectedBpr // query response header + topic id size + topic info size
            ) {
        final var merkleTopic =
                new MerkleTopic(
                        memo, adminKey, submitKey, 0, autoRenewAccountId, new RichInstant(1, 0));
        final var expectedFeeData =
                FeeData.newBuilder()
                        .setNodedata(
                                FeeComponents.newBuilder()
                                        .setConstant(1)
                                        .setBpt(expectedBpt)
                                        .setBpr(expectedBpr)
                                        .build())
                        .setNetworkdata(FeeComponents.getDefaultInstance())
                        .setServicedata(FeeComponents.getDefaultInstance())
                        .build();
        given(topics.get(EntityNum.fromTopicId(topicId))).willReturn(merkleTopic);

        final var costAnswerEstimate =
                subject.usageGiven(topicInfoQuery(topicId, COST_ANSWER), view);
        final var answerOnlyEstimate =
                subject.usageGiven(topicInfoQuery(topicId, ANSWER_ONLY), view);

        assertEquals(expectedFeeData, costAnswerEstimate);
        assertEquals(expectedFeeData, answerOnlyEstimate);
    }

    private Query topicInfoQuery(final TopicID topicId, final ResponseType type) {
        final var op =
                ConsensusGetTopicInfoQuery.newBuilder()
                        .setTopicID(topicId)
                        .setHeader(QueryHeader.newBuilder().setResponseType(type));
        return Query.newBuilder().setConsensusGetTopicInfo(op).build();
    }
}
