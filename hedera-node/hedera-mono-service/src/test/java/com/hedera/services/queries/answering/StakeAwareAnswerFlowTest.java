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
package com.hedera.services.queries.answering;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;

import com.hedera.services.context.NodeInfo;
import com.hedera.services.queries.AnswerService;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StakeAwareAnswerFlowTest {
    private static final Query mockQuery = Query.getDefaultInstance();
    private static final Response mockResponse = Response.getDefaultInstance();

    @Mock private NodeInfo nodeInfo;
    @Mock private AnswerService service;
    @Mock private StakedAnswerFlow stakedAnswerFlow;
    @Mock private ZeroStakeAnswerFlow zeroStakeAnswerFlow;

    private StakeAwareAnswerFlow subject;

    @BeforeEach
    void setUp() {
        subject = new StakeAwareAnswerFlow(nodeInfo, stakedAnswerFlow, zeroStakeAnswerFlow);
    }

    @Test
    void delegatesToZeroStakeAsExpected() {
        given(nodeInfo.isSelfZeroStake()).willReturn(true);
        given(zeroStakeAnswerFlow.satisfyUsing(service, mockQuery)).willReturn(mockResponse);

        assertSame(mockResponse, subject.satisfyUsing(service, mockQuery));
    }

    @Test
    void delegatesToStakedAsExpected() {
        given(stakedAnswerFlow.satisfyUsing(service, mockQuery)).willReturn(mockResponse);

        assertSame(mockResponse, subject.satisfyUsing(service, mockQuery));
    }
}
