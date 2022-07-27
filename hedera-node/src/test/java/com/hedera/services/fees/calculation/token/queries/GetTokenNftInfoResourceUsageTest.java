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
package com.hedera.services.fees.calculation.token.queries;

import static com.hedera.services.queries.token.GetTokenNftInfoAnswer.NFT_INFO_CTX_KEY;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.usage.token.TokenGetNftInfoUsage;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TokenGetNftInfoQuery;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import java.util.HashMap;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class GetTokenNftInfoResourceUsageTest {
    private static final NftID target =
            NftID.newBuilder().setTokenID(IdUtils.asToken("0.0.123")).setSerialNumber(1).build();
    private static final ByteString metadata = ByteString.copyFromUtf8("LMAO");
    private static final AccountID owner = IdUtils.asAccount("0.0.321321");
    private static final TokenNftInfo info =
            TokenNftInfo.newBuilder()
                    .setAccountID(owner)
                    .setMetadata(metadata)
                    .setNftID(target)
                    .build();
    private static final Query satisfiableAnswerOnly = TokenNftInfoQuery(target, ANSWER_ONLY);

    private FeeData expected;
    private TokenGetNftInfoUsage estimator;
    private MockedStatic<TokenGetNftInfoUsage> mockedStatic;
    private StateView view;

    private GetTokenNftInfoResourceUsage subject;

    @BeforeEach
    void setup() {
        expected = mock(FeeData.class);
        view = mock(StateView.class);
        estimator = mock(TokenGetNftInfoUsage.class);
        mockedStatic = mockStatic(TokenGetNftInfoUsage.class);
        mockedStatic
                .when(() -> TokenGetNftInfoUsage.newEstimate(satisfiableAnswerOnly))
                .thenReturn(estimator);

        given(estimator.givenMetadata(metadata.toString())).willReturn(estimator);
        given(estimator.get()).willReturn(expected);

        given(view.infoForNft(target)).willReturn(Optional.of(info));

        subject = new GetTokenNftInfoResourceUsage();
    }

    @AfterEach
    void tearDown() {
        mockedStatic.close();
    }

    @Test
    void recognizesApplicableQuery() {
        final var applicable = TokenNftInfoQuery(target, COST_ANSWER);
        final var inapplicable = Query.getDefaultInstance();

        assertTrue(subject.applicableTo(applicable));
        assertFalse(subject.applicableTo(inapplicable));
    }

    @Test
    void setsInfoInQueryCxtIfPresent() {
        final var queryCtx = new HashMap<String, Object>();

        final var usage = subject.usageGiven(satisfiableAnswerOnly, view, queryCtx);

        assertSame(info, queryCtx.get(NFT_INFO_CTX_KEY));
        assertSame(expected, usage);
        verify(estimator).givenMetadata(metadata.toString());
    }

    @Test
    void onlySetsTokenNftInfoInQueryCxtIfFound() {
        final var queryCtx = new HashMap<String, Object>();
        given(view.infoForNft(target)).willReturn(Optional.empty());

        final var usage = subject.usageGiven(satisfiableAnswerOnly, view, queryCtx);

        assertFalse(queryCtx.containsKey(NFT_INFO_CTX_KEY));
        assertSame(FeeData.getDefaultInstance(), usage);
    }

    @Test
    void worksWithoutQueryContext() {
        given(view.infoForNft(target)).willReturn(Optional.empty());

        final var usage = subject.usageGiven(satisfiableAnswerOnly, view);

        assertSame(FeeData.getDefaultInstance(), usage);
    }

    @Test
    void worksWithNoQueryContext() {
        given(view.infoForNft(target)).willReturn(Optional.empty());

        final var usage = subject.usageGivenType(satisfiableAnswerOnly, view, ANSWER_ONLY);

        assertSame(FeeData.getDefaultInstance(), usage);
    }

    private static final Query TokenNftInfoQuery(final NftID id, final ResponseType type) {
        final var op =
                TokenGetNftInfoQuery.newBuilder()
                        .setNftID(id)
                        .setHeader(QueryHeader.newBuilder().setResponseType(type));
        return Query.newBuilder().setTokenGetNftInfo(op).build();
    }
}
