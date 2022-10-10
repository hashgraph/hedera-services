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
package com.hedera.services.fees.calculation.meta.queries;

import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_QUERY_HEADER;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_QUERY_RES_HEADER;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_TX_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.FEE_MATRICES_CONST;
import static com.hederahashgraph.fee.FeeBuilder.LONG_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.NetworkGetExecutionTimeQuery;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetExecTimeResourceUsageTest {
    private static final Query execTimeQuery =
            Query.newBuilder()
                    .setNetworkGetExecutionTime(
                            NetworkGetExecutionTimeQuery.newBuilder()
                                    .addAllTransactionIds(
                                            List.of(
                                                    TransactionID.getDefaultInstance(),
                                                    TransactionID.getDefaultInstance())))
                    .build();
    private static final Query nonExecTimeQuery = Query.getDefaultInstance();

    private GetExecTimeResourceUsage subject;

    @BeforeEach
    void setup() {
        subject = new GetExecTimeResourceUsage();
    }

    @Test
    void recognizesApplicability() {
        assertTrue(subject.applicableTo(execTimeQuery));
        assertFalse(subject.applicableTo(nonExecTimeQuery));
    }

    @Test
    void getsExpectedUsage() {
        final var expectedNodeUsage =
                FeeComponents.newBuilder()
                        .setConstant(FEE_MATRICES_CONST)
                        .setBpt(BASIC_QUERY_HEADER + 2 * BASIC_TX_ID_SIZE)
                        .setBpr(BASIC_QUERY_RES_HEADER + 2 * LONG_SIZE)
                        .build();
        final var expected = FeeData.newBuilder().setNodedata(expectedNodeUsage).build();

        assertEquals(expected, subject.usageGiven(execTimeQuery, null));
        assertEquals(expected, subject.usageGivenType(execTimeQuery, null, ANSWER_ONLY));
    }
}
