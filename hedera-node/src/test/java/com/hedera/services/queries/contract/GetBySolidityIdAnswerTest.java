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
package com.hedera.services.queries.contract;

import static org.junit.jupiter.api.Assertions.*;

import com.hederahashgraph.api.proto.java.GetBySolidityIDQuery;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import org.junit.jupiter.api.Test;

class GetBySolidityIdAnswerTest {
    private final GetBySolidityIdAnswer solidityIdAnswer = new GetBySolidityIdAnswer();
    private final GetBySolidityIDQuery.Builder getBySolidityIDQueryBuilder =
            GetBySolidityIDQuery.newBuilder().setSolidityID("1234");

    @Test
    void assertSolidityIDHeadersWhenResponseTypeIsCostAnswer() {
        var header = QueryHeader.newBuilder().setResponseType(ResponseType.COST_ANSWER).build();
        var getSolidityIdQuery = getBySolidityIDQueryBuilder.setHeader(header).build();
        Query query = Query.newBuilder().setGetBySolidityID(getSolidityIdQuery).build();
        var result = solidityIdAnswer.responseGiven(query, null, null, 0);

        assertEquals(
                ResponseCodeEnum.NOT_SUPPORTED,
                result.getGetBySolidityID().getHeader().getNodeTransactionPrecheckCode());
        assertEquals(
                ResponseType.COST_ANSWER,
                result.getGetBySolidityID().getHeader().getResponseType());
        assertEquals(0L, result.getGetBySolidityID().getHeader().getCost());
    }

    @Test
    void assertSolidityIDHeadersWhenResponseTypeIsMissing() {
        Query query =
                Query.newBuilder().setGetBySolidityID(getBySolidityIDQueryBuilder.build()).build();
        var result = solidityIdAnswer.responseGiven(query, null, null, 0);

        assertEquals(
                ResponseCodeEnum.NOT_SUPPORTED,
                result.getGetBySolidityID().getHeader().getNodeTransactionPrecheckCode());
    }
}
