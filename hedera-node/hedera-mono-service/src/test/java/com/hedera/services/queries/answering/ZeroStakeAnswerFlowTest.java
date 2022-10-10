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

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetStakers;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_NOT_GENESIS_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.queries.AnswerService;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ZeroStakeAnswerFlowTest {
    private final HederaFunctionality function = HederaFunctionality.ConsensusGetTopicInfo;

    @Mock private QueryHeaderValidity queryHeaderValidity;
    @Mock private StateView view;
    @Mock private FunctionalityThrottling throttles;
    @Mock private AnswerService service;

    private final Query query = Query.getDefaultInstance();
    private final Response response = Response.getDefaultInstance();

    private ZeroStakeAnswerFlow subject;

    @BeforeEach
    void setup() {
        subject = new ZeroStakeAnswerFlow(queryHeaderValidity, () -> view, throttles);
    }

    @Test
    void validatesMetaAsExpected() {
        given(queryHeaderValidity.checkHeader(query)).willReturn(ACCOUNT_IS_NOT_GENESIS_ACCOUNT);
        given(service.responseGiven(query, view, ACCOUNT_IS_NOT_GENESIS_ACCOUNT))
                .willReturn(response);

        // when:
        final var actual = subject.satisfyUsing(service, query);

        // then:
        assertEquals(response, actual);
    }

    @Test
    void validatesSpecificAfterMetaOk() {
        given(queryHeaderValidity.checkHeader(query)).willReturn(OK);
        given(service.checkValidity(query, view)).willReturn(INVALID_ACCOUNT_ID);
        given(service.responseGiven(query, view, INVALID_ACCOUNT_ID)).willReturn(response);

        // when:
        final var actual = subject.satisfyUsing(service, query);

        // then:
        assertEquals(response, actual);
    }

    @Test
    void throttlesIfAppropriate() {
        given(service.canonicalFunction()).willReturn(function);
        given(throttles.shouldThrottleQuery(eq(function), any())).willReturn(true);
        given(service.responseGiven(query, view, BUSY)).willReturn(response);

        // when:
        final var actual = subject.satisfyUsing(service, query);

        // then:
        assertEquals(response, actual);
        verify(throttles).shouldThrottleQuery(eq(function), any());
    }

    @Test
    void submitsIfShouldntThrottle() {
        given(service.canonicalFunction()).willReturn(CryptoGetStakers);
        given(queryHeaderValidity.checkHeader(query)).willReturn(OK);
        given(service.checkValidity(query, view)).willReturn(OK);
        given(throttles.shouldThrottleQuery(eq(CryptoGetStakers), any())).willReturn(false);
        // and:
        given(service.responseGiven(query, view, OK)).willReturn(response);

        // when:
        final var actual = subject.satisfyUsing(service, query);

        // then:
        assertEquals(response, actual);
    }
}
