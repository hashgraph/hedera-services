/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.queries.answering;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_QUERY_HEADER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hederahashgraph.api.proto.java.ConsensusGetTopicInfoQuery;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import org.junit.jupiter.api.Test;

class QueryHeaderValidityTest {
    final QueryHeaderValidity subject = new QueryHeaderValidity();

    @Test
    void recognizesMissingHeader() {
        // given:
        final var missingHeader = Query.getDefaultInstance();

        // expect:
        assertEquals(MISSING_QUERY_HEADER, subject.checkHeader(missingHeader));
    }

    @Test
    void rejectsCostAnswerStateProof() {
        // given:
        final var costAnswerStateProof =
                Query.newBuilder()
                        .setConsensusGetTopicInfo(
                                ConsensusGetTopicInfoQuery.newBuilder()
                                        .setHeader(
                                                QueryHeader.newBuilder()
                                                        .setResponseType(
                                                                ResponseType
                                                                        .COST_ANSWER_STATE_PROOF)))
                        .build();

        // expect:
        assertEquals(NOT_SUPPORTED, subject.checkHeader(costAnswerStateProof));
    }

    @Test
    void rejectsAnswerOnlyStateProof() {
        // given:
        final var answerStateProof =
                Query.newBuilder()
                        .setConsensusGetTopicInfo(
                                ConsensusGetTopicInfoQuery.newBuilder()
                                        .setHeader(
                                                QueryHeader.newBuilder()
                                                        .setResponseType(
                                                                ResponseType.ANSWER_STATE_PROOF)))
                        .build();

        // expect:
        assertEquals(NOT_SUPPORTED, subject.checkHeader(answerStateProof));
    }

    @Test
    void acceptsSupportedResponseType() {
        // given:
        final var answerStateProof =
                Query.newBuilder()
                        .setConsensusGetTopicInfo(
                                ConsensusGetTopicInfoQuery.newBuilder()
                                        .setHeader(
                                                QueryHeader.newBuilder()
                                                        .setResponseType(ResponseType.ANSWER_ONLY)))
                        .build();

        // expect:
        assertEquals(OK, subject.checkHeader(answerStateProof));
    }
}
