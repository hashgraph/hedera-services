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
package com.hedera.services.fees.calculation.contract.queries;

import static com.hedera.test.utils.IdUtils.asContract;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.accounts.staking.RewardCalculator;
import com.hedera.services.queries.contract.GetContractInfoAnswer;
import com.hedera.services.usage.contract.ContractGetInfoUsage;
import com.hederahashgraph.api.proto.java.ContractGetInfoQuery;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TokenRelationship;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class GetContractInfoResourceUsageTest {
    private static final int maxTokensPerContractInfo = 10;
    private static final String memo = "Stay cold...";
    private static final ContractID target = asContract("0.0.123");
    private static final ByteString ledgerId = ByteString.copyFromUtf8("0xff");
    private static final Key aKey =
            Key.newBuilder().setEd25519(ByteString.copyFrom("NONSENSE".getBytes())).build();
    private static final ContractGetInfoResponse.ContractInfo info =
            ContractGetInfoResponse.ContractInfo.newBuilder()
                    .setLedgerId(ledgerId)
                    .setAdminKey(aKey)
                    .addAllTokenRelationships(
                            List.of(
                                    TokenRelationship.getDefaultInstance(),
                                    TokenRelationship.getDefaultInstance(),
                                    TokenRelationship.getDefaultInstance()))
                    .setMemo(memo)
                    .build();
    private static final Query satisfiableAnswerOnly = contractInfoQuery(target, ANSWER_ONLY);

    private StateView view;
    private ContractGetInfoUsage estimator;
    private MockedStatic<ContractGetInfoUsage> mockedStatic;
    private FeeData expected;
    private AliasManager aliasManager;
    private GlobalDynamicProperties dynamicProperties;
    private RewardCalculator rewardCalculator;

    private GetContractInfoResourceUsage subject;

    @BeforeEach
    void setup() {
        expected = mock(FeeData.class);
        aliasManager = mock(AliasManager.class);
        dynamicProperties = mock(GlobalDynamicProperties.class);
        rewardCalculator = mock(RewardCalculator.class);

        view = mock(StateView.class);
        given(dynamicProperties.maxTokensRelsPerInfoQuery()).willReturn(maxTokensPerContractInfo);
        given(
                        view.infoForContract(
                                target, aliasManager, maxTokensPerContractInfo, rewardCalculator))
                .willReturn(Optional.of(info));

        estimator = mock(ContractGetInfoUsage.class);
        mockedStatic = mockStatic(ContractGetInfoUsage.class);
        mockedStatic
                .when(() -> ContractGetInfoUsage.newEstimate(satisfiableAnswerOnly))
                .thenReturn(estimator);

        given(estimator.givenCurrentKey(aKey)).willReturn(estimator);
        given(estimator.givenCurrentMemo(memo)).willReturn(estimator);
        given(estimator.givenCurrentTokenAssocs(3)).willReturn(estimator);
        given(estimator.get()).willReturn(expected);

        subject =
                new GetContractInfoResourceUsage(aliasManager, dynamicProperties, rewardCalculator);
    }

    @AfterEach
    void tearDown() {
        mockedStatic.close();
    }

    @Test
    void recognizesApplicableQuery() {
        final var applicable = contractInfoQuery(target, COST_ANSWER);
        final var inapplicable = Query.getDefaultInstance();

        assertTrue(subject.applicableTo(applicable));
        assertFalse(subject.applicableTo(inapplicable));
    }

    @Test
    void usesEstimator() {
        final var usage = subject.usageGiven(contractInfoQuery(target, ANSWER_ONLY), view);

        assertEquals(expected, usage);
        verify(estimator).givenCurrentKey(aKey);
        verify(estimator).givenCurrentMemo(memo);
        verify(estimator).givenCurrentTokenAssocs(3);
    }

    @Test
    void setsInfoInQueryCxtIfPresent() {
        final var queryCtx = new HashMap<String, Object>();

        subject.usageGiven(satisfiableAnswerOnly, view, queryCtx);

        assertSame(info, queryCtx.get(GetContractInfoAnswer.CONTRACT_INFO_CTX_KEY));
    }

    @Test
    void onlySetsContractInfoInQueryCxtIfFound() {
        final var queryCtx = new HashMap<String, Object>();
        given(
                        view.infoForContract(
                                target, aliasManager, maxTokensPerContractInfo, rewardCalculator))
                .willReturn(Optional.empty());

        final var actual = subject.usageGiven(satisfiableAnswerOnly, view, queryCtx);

        assertFalse(queryCtx.containsKey(GetContractInfoAnswer.CONTRACT_INFO_CTX_KEY));
        assertSame(FeeData.getDefaultInstance(), actual);
    }

    private static final Query contractInfoQuery(final ContractID id, final ResponseType type) {
        final var op =
                ContractGetInfoQuery.newBuilder()
                        .setContractID(id)
                        .setHeader(QueryHeader.newBuilder().setResponseType(type));
        return Query.newBuilder().setContractGetInfo(op).build();
    }
}
