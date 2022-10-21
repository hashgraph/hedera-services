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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.ContractGetBytecodeQuery;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.fee.SmartContractFeeBuilder;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetBytecodeResourceUsageTest {
    private static final byte[] bytecode = "A Supermarket in California".getBytes();
    private static final ContractID target = asContract("0.0.123");

    private StateView view;
    private AliasManager aliasManager;
    private SmartContractFeeBuilder usageEstimator;

    private GetBytecodeResourceUsage subject;

    @BeforeEach
    void setup() {
        aliasManager = mock(AliasManager.class);
        usageEstimator = mock(SmartContractFeeBuilder.class);
        view = mock(StateView.class);

        subject = new GetBytecodeResourceUsage(aliasManager, usageEstimator);
    }

    @Test
    void recognizesApplicableQuery() {
        final var applicable = bytecodeQuery(target, COST_ANSWER);
        final var inapplicable = Query.getDefaultInstance();

        assertTrue(subject.applicableTo(applicable));
        assertFalse(subject.applicableTo(inapplicable));
    }

    @Test
    void invokesEstimatorAsExpectedForType() {
        final var costAnswerUsage = mock(FeeData.class);
        final var answerOnlyUsage = mock(FeeData.class);
        final var size = bytecode.length;
        final var answerOnlyQuery = bytecodeQuery(target, ANSWER_ONLY);
        final var costAnswerQuery = bytecodeQuery(target, COST_ANSWER);
        given(view.bytecodeOf(EntityNum.fromContractId(target))).willReturn(Optional.of(bytecode));
        given(usageEstimator.getContractByteCodeQueryFeeMatrices(size, COST_ANSWER))
                .willReturn(costAnswerUsage);
        given(usageEstimator.getContractByteCodeQueryFeeMatrices(size, ANSWER_ONLY))
                .willReturn(answerOnlyUsage);

        final var costAnswerEstimate = subject.usageGiven(costAnswerQuery, view);
        final var answerOnlyEstimate = subject.usageGiven(answerOnlyQuery, view);

        assertSame(costAnswerEstimate, costAnswerUsage);
        assertSame(answerOnlyEstimate, answerOnlyUsage);
    }

    private static final Query bytecodeQuery(final ContractID id, final ResponseType type) {
        final var op =
                ContractGetBytecodeQuery.newBuilder()
                        .setContractID(id)
                        .setHeader(QueryHeader.newBuilder().setResponseType(type));
        return Query.newBuilder().setContractGetBytecode(op).build();
    }
}
