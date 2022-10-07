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
package com.hedera.services.fees.calculation.token.queries;

import static com.hedera.services.queries.token.GetTokenInfoAnswer.TOKEN_INFO_CTX_KEY;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.queries.token.GetTokenInfoAnswer;
import com.hedera.services.usage.token.TokenGetInfoUsage;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TokenGetInfoQuery;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenInfo;
import com.hederahashgraph.api.proto.java.TokenPauseStatus;
import java.util.HashMap;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class GetTokenInfoResourceUsageTest {
    private static final String memo = "22 a million";
    private static final String symbol = "HEYMAOK";
    private static final String name = "IsItReallyOk";
    private static final TokenID target = IdUtils.asToken("0.0.123");
    private static final TokenInfo info =
            TokenInfo.newBuilder()
                    .setAdminKey(TxnHandlingScenario.TOKEN_ADMIN_KT.asKey())
                    .setFreezeKey(TxnHandlingScenario.TOKEN_FREEZE_KT.asKey())
                    .setWipeKey(TxnHandlingScenario.TOKEN_WIPE_KT.asKey())
                    .setSupplyKey(TxnHandlingScenario.TOKEN_SUPPLY_KT.asKey())
                    .setKycKey(TxnHandlingScenario.TOKEN_KYC_KT.asKey())
                    .setPauseKey(TxnHandlingScenario.TOKEN_PAUSE_KT.asKey())
                    .setSymbol(symbol)
                    .setName(name)
                    .setMemo(memo)
                    .setPauseStatus(TokenPauseStatus.Paused)
                    .setAutoRenewAccount(IdUtils.asAccount("1.2.3"))
                    .build();
    private static final Query satisfiableAnswerOnly = tokenInfoQuery(target, ANSWER_ONLY);

    private FeeData expected;
    private TokenGetInfoUsage estimator;
    private MockedStatic<TokenGetInfoUsage> mockedStatic;
    private StateView view;

    private GetTokenInfoResourceUsage subject;

    @BeforeEach
    void setup() {
        expected = mock(FeeData.class);
        view = mock(StateView.class);
        estimator = mock(TokenGetInfoUsage.class);
        mockedStatic = mockStatic(TokenGetInfoUsage.class);
        mockedStatic
                .when(() -> TokenGetInfoUsage.newEstimate(satisfiableAnswerOnly))
                .thenReturn(estimator);

        given(estimator.givenCurrentAdminKey(any())).willReturn(estimator);
        given(estimator.givenCurrentWipeKey(any())).willReturn(estimator);
        given(estimator.givenCurrentKycKey(any())).willReturn(estimator);
        given(estimator.givenCurrentSupplyKey(any())).willReturn(estimator);
        given(estimator.givenCurrentFreezeKey(any())).willReturn(estimator);
        given(estimator.givenCurrentSymbol(any())).willReturn(estimator);
        given(estimator.givenCurrentName(any())).willReturn(estimator);
        given(estimator.givenCurrentMemo(any())).willReturn(estimator);
        given(estimator.givenCurrentPauseKey(any())).willReturn(estimator);
        given(estimator.givenCurrentlyUsingAutoRenewAccount()).willReturn(estimator);
        given(estimator.get()).willReturn(expected);

        given(view.infoForToken(target)).willReturn(Optional.of(info));

        subject = new GetTokenInfoResourceUsage();
    }

    @AfterEach
    void tearDown() {
        mockedStatic.close();
    }

    @Test
    void recognizesApplicableQuery() {
        final var applicable = tokenInfoQuery(target, COST_ANSWER);
        final var inapplicable = Query.getDefaultInstance();

        assertTrue(subject.applicableTo(applicable));
        assertFalse(subject.applicableTo(inapplicable));
    }

    @Test
    void setsInfoInQueryCxtIfPresent() {
        final var queryCtx = new HashMap<String, Object>();

        final var usage = subject.usageGiven(satisfiableAnswerOnly, view, queryCtx);

        assertSame(info, queryCtx.get(TOKEN_INFO_CTX_KEY));
        assertSame(expected, usage);
        verifyCommonCalls();
        verify(estimator)
                .givenCurrentAdminKey(Optional.of(TxnHandlingScenario.TOKEN_ADMIN_KT.asKey()));
        verify(estimator)
                .givenCurrentPauseKey(Optional.of(TxnHandlingScenario.TOKEN_PAUSE_KT.asKey()));
        verify(estimator).givenCurrentlyUsingAutoRenewAccount();
    }

    @Test
    void onlySetsTokenInfoInQueryCxtIfFound() {
        final var queryCtx = new HashMap<String, Object>();
        given(view.infoForToken(target)).willReturn(Optional.empty());

        final var usage = subject.usageGiven(satisfiableAnswerOnly, view, queryCtx);

        assertFalse(queryCtx.containsKey(GetTokenInfoAnswer.TOKEN_INFO_CTX_KEY));
        assertSame(FeeData.getDefaultInstance(), usage);
    }

    @Test
    void estimatesWithIncompleteInfo() {
        final var incompleteInfo = info.toBuilder().clearAdminKey().clearAutoRenewAccount().build();
        given(view.infoForToken(target)).willReturn(Optional.of(incompleteInfo));

        final var usage = subject.usageGiven(satisfiableAnswerOnly, view);

        assertSame(expected, usage);
        verifyCommonCalls();
        verify(estimator).givenCurrentAdminKey(Optional.empty());
        verify(estimator, never()).givenCurrentlyUsingAutoRenewAccount();
    }

    private void verifyCommonCalls() {
        verify(estimator)
                .givenCurrentWipeKey(Optional.of(TxnHandlingScenario.TOKEN_WIPE_KT.asKey()));
        verify(estimator).givenCurrentKycKey(Optional.of(TxnHandlingScenario.TOKEN_KYC_KT.asKey()));
        verify(estimator)
                .givenCurrentSupplyKey(Optional.of(TxnHandlingScenario.TOKEN_SUPPLY_KT.asKey()));
        verify(estimator)
                .givenCurrentFreezeKey(Optional.of(TxnHandlingScenario.TOKEN_FREEZE_KT.asKey()));
        verify(estimator).givenCurrentSymbol(symbol);
        verify(estimator).givenCurrentName(name);
        verify(estimator).givenCurrentMemo(memo);
    }

    private static final Query tokenInfoQuery(final TokenID id, final ResponseType type) {
        final var op =
                TokenGetInfoQuery.newBuilder()
                        .setToken(id)
                        .setHeader(QueryHeader.newBuilder().setResponseType(type));
        return Query.newBuilder().setTokenGetInfo(op).build();
    }
}
