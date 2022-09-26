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
package com.hedera.services.queries.contract;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ContractGetRecordsQuery;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Transaction;
import org.junit.jupiter.api.Test;

class GetContractRecordsAnswerTest {
    private static final ByteString address =
            ByteString.copyFrom(unhex("0000000000000000000000009abcdefabcdefbbb"));
    private final ContractGetRecordsQuery.Builder queryBuilder =
            ContractGetRecordsQuery.newBuilder()
                    .setContractID(ContractID.newBuilder().setEvmAddress(address).build());

    private final GetContractRecordsAnswer subject = new GetContractRecordsAnswer();

    @Test
    void assertHeadersWhenResponseTypeIsCostAnswer() {
        var header = QueryHeader.newBuilder().setResponseType(COST_ANSWER).build();
        var op = queryBuilder.setHeader(header).build();
        Query query = Query.newBuilder().setContractGetRecords(op).build();
        var result = subject.responseGiven(query, null, null, 0);

        assertEquals(
                NOT_SUPPORTED,
                result.getContractGetRecordsResponse()
                        .getHeader()
                        .getNodeTransactionPrecheckCode());
        assertEquals(
                COST_ANSWER, result.getContractGetRecordsResponse().getHeader().getResponseType());
        assertEquals(0L, result.getContractGetRecordsResponse().getHeader().getCost());
    }

    @Test
    void assertHeadersWhenResponseTypeIsMissing() {
        Query query = Query.newBuilder().setContractGetRecords(queryBuilder.build()).build();
        var result = subject.responseGiven(query, null, null, 0);

        assertEquals(
                NOT_SUPPORTED,
                result.getContractGetRecordsResponse()
                        .getHeader()
                        .getNodeTransactionPrecheckCode());
    }

    @Test
    void canExtractPayment() {
        final var query =
                Query.newBuilder()
                        .setContractGetRecords(
                                queryBuilder
                                        .setHeader(
                                                QueryHeader.newBuilder()
                                                        .setPayment(
                                                                Transaction.getDefaultInstance()))
                                        .build())
                        .build();
        assertTrue(subject.extractPaymentFrom(query).isPresent());
    }
}
