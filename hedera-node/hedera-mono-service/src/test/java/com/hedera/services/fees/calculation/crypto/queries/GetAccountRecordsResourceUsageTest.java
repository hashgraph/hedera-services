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
package com.hedera.services.fees.calculation.crypto.queries;

import static com.hedera.services.queries.meta.GetTxnRecordAnswer.PAYER_RECORDS_CTX_KEY;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.TxnUtils.recordOne;
import static com.hedera.test.utils.TxnUtils.recordTwo;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.queries.answering.AnswerFunctions;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hederahashgraph.api.proto.java.CryptoGetAccountRecordsQuery;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.fee.CryptoFeeBuilder;
import com.swirlds.merkle.map.MerkleMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetAccountRecordsResourceUsageTest {
    private final String a = "0.0.1234";
    private final List<TransactionRecord> someRecords =
            ExpirableTxnRecord.allToGrpc(List.of(recordOne(), recordTwo()));

    private StateView view;
    private MerkleAccount aValue;

    @Mock private CryptoFeeBuilder usageEstimator;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private MerkleMap<EntityNum, MerkleAccount> accounts;

    private GetAccountRecordsResourceUsage subject;

    @BeforeEach
    void setup() {
        aValue = MerkleAccountFactory.newAccount().get();
        aValue.records().offer(recordOne());
        aValue.records().offer(recordTwo());
        final MutableStateChildren children = new MutableStateChildren();
        children.setAccounts(accounts);
        view = new StateView(null, children, null);

        subject =
                new GetAccountRecordsResourceUsage(
                        new AnswerFunctions(dynamicProperties), usageEstimator);
    }

    @Test
    void returnsEmptyFeeDataWhenAccountMissing() {
        final var query = accountRecordsQuery(a, ANSWER_ONLY);

        assertSame(FeeData.getDefaultInstance(), subject.usageGiven(query, view));
    }

    @Test
    void invokesEstimatorAsExpectedForType() {
        // setup:
        final var costAnswerUsage = mock(FeeData.class);
        final var answerOnlyUsage = mock(FeeData.class);
        final var key = EntityNum.fromAccountId(asAccount(a));
        final Map<String, Object> queryCtx = new HashMap<>();
        given(dynamicProperties.maxNumQueryableRecords()).willReturn(180);

        // given:
        final var answerOnlyQuery = accountRecordsQuery(a, ANSWER_ONLY);
        final var costAnswerQuery = accountRecordsQuery(a, COST_ANSWER);
        given(accounts.get(key)).willReturn(aValue);
        given(accounts.containsKey(key)).willReturn(true);
        given(usageEstimator.getCryptoAccountRecordsQueryFeeMatrices(someRecords, COST_ANSWER))
                .willReturn(costAnswerUsage);
        given(usageEstimator.getCryptoAccountRecordsQueryFeeMatrices(someRecords, ANSWER_ONLY))
                .willReturn(answerOnlyUsage);

        final var costAnswerEstimate = subject.usageGiven(costAnswerQuery, view, queryCtx);
        final var answerOnlyEstimate = subject.usageGiven(answerOnlyQuery, view, queryCtx);

        assertSame(costAnswerUsage, costAnswerEstimate);
        assertSame(answerOnlyUsage, answerOnlyEstimate);
        assertTrue(queryCtx.containsKey(PAYER_RECORDS_CTX_KEY));
    }

    @Test
    void recognizesApplicableQuery() {
        final var accountRecordsQuery = accountRecordsQuery(a, COST_ANSWER);
        final var nonAccountRecordsQuery = nonAccountRecordsQuery();

        assertTrue(subject.applicableTo(accountRecordsQuery));
        assertFalse(subject.applicableTo(nonAccountRecordsQuery));
    }

    private Query accountRecordsQuery(final String target, final ResponseType type) {
        final var id = asAccount(target);
        final var op =
                CryptoGetAccountRecordsQuery.newBuilder()
                        .setAccountID(id)
                        .setHeader(QueryHeader.newBuilder().setResponseType(type));
        return Query.newBuilder().setCryptoGetAccountRecords(op).build();
    }

    private Query nonAccountRecordsQuery() {
        return Query.newBuilder().build();
    }
}
