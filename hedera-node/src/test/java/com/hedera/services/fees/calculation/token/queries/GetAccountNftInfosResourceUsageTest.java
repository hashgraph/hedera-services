package com.hedera.services.fees.calculation.token.queries;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.protobuf.ByteString;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.usage.token.TokenGetAccountNftInfosUsage;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TokenGetAccountNftInfosQuery;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static com.hedera.services.queries.token.GetAccountNftInfosAnswer.ACCOUNT_NFT_INFO_CTX_KEY;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GetAccountNftInfosResourceUsageTest {
    ByteString m1 = ByteString.copyFromUtf8("metadata1"), m2 = ByteString.copyFromUtf8("metadata2");
    List<ByteString> metadata = List.of(m1, m2);
    TokenGetAccountNftInfosUsage estimator;
    Function<Query, TokenGetAccountNftInfosUsage> factory;
    FeeData expected;
    AccountID target = IdUtils.asAccount("0.0.123");
    int start = 0, end = 1;

    StateView view;
    List<TokenNftInfo> info = List.of(TokenNftInfo.newBuilder()
                    .setMetadata(m1)
                    .build(),
            TokenNftInfo.newBuilder()
                    .setMetadata(m2)
                    .build());

    Query satisfiableAnswerOnly = tokenGetAccountNftInfosQuery(target, start, end, ANSWER_ONLY);

    GetAccountNftInfosResourceUsage subject;

    @BeforeEach
    private void setup() {
        expected = mock(FeeData.class);
        view = mock(StateView.class);
        estimator = mock(TokenGetAccountNftInfosUsage.class);
        factory = mock(Function.class);
        given(factory.apply(any())).willReturn(estimator);

        GetAccountNftInfosResourceUsage.factory = factory;

        given(estimator.givenMetadata(any())).willReturn(estimator);
        given(estimator.get()).willReturn(expected);

        given(view.infoForAccountNfts(target, start, end)).willReturn(Optional.of(info));

        subject = new GetAccountNftInfosResourceUsage();
    }

    @Test
     void recognizesApplicableQuery() {
        // given:
        var applicable = tokenGetAccountNftInfosQuery(target, start, end, COST_ANSWER);
        var inapplicable = Query.getDefaultInstance();

        // expect:
        assertTrue(subject.applicableTo(applicable));
        assertFalse(subject.applicableTo(inapplicable));
    }

    @Test
     void setsInfoInQueryCxtIfPresent() {
        // setup:
        var queryCtx = new HashMap<String, Object>();

        // when:
        var usage = subject.usageGiven(satisfiableAnswerOnly, view, queryCtx);

        // then:
        assertSame(info, queryCtx.get(ACCOUNT_NFT_INFO_CTX_KEY));
        assertSame(expected, usage);
        // and:
        verify(estimator).givenMetadata(metadata);
    }

    @Test
     void onlySetsTokenInfoInQueryCxtIfFound() {
        // setup:
        var queryCtx = new HashMap<String, Object>();

        given(view.infoForAccountNfts(target, start, end)).willReturn(Optional.empty());

        // when:
        var usage = subject.usageGiven(satisfiableAnswerOnly, view, queryCtx);

        // then:
        assertFalse(queryCtx.containsKey(ACCOUNT_NFT_INFO_CTX_KEY));
        // and:
        assertSame(FeeData.getDefaultInstance(), usage);
    }

    @Test
    void doesntSetTokenInfoInQueryCxtNotFound() {
        // setup:
        var queryCtx = new HashMap<String, Object>();

        given(view.infoForAccountNfts(target, start, end)).willReturn(Optional.of(info));

        // when:
        var usage = subject.usageGiven(satisfiableAnswerOnly, view);

        // then:
        assertFalse(queryCtx.containsKey(ACCOUNT_NFT_INFO_CTX_KEY));
        verify(estimator).givenMetadata(metadata);
        assertNotSame(FeeData.getDefaultInstance(), usage);
    }

    @Test
    void doesntSetTokenInfoForAnswerOnlyType() {
        // setup:
        var queryCtx = new HashMap<String, Object>();

        given(view.infoForAccountNfts(target, start, end)).willReturn(Optional.of(info));

        // when:
        var usage = subject.usageGivenType(satisfiableAnswerOnly, view, ANSWER_ONLY);

        // then:
        assertFalse(queryCtx.containsKey(ACCOUNT_NFT_INFO_CTX_KEY));
        assertNotSame(FeeData.getDefaultInstance(), usage);
    }

    private Query tokenGetAccountNftInfosQuery(AccountID id, long start, long end, ResponseType type) {
        TokenGetAccountNftInfosQuery.Builder op = TokenGetAccountNftInfosQuery.newBuilder()
                .setAccountID(id)
                .setStart(start)
                .setEnd(end)
                .setHeader(QueryHeader.newBuilder().setResponseType(type));
        return Query.newBuilder()
                .setTokenGetAccountNftInfos(op)
                .build();
    }
}
