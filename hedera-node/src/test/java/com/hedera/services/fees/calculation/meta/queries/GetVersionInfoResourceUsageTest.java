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
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.fees.calculation.meta.FixedUsageEstimates;
import com.hederahashgraph.api.proto.java.NetworkGetVersionInfoQuery;
import com.hederahashgraph.api.proto.java.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetVersionInfoResourceUsageTest {
    private static final Query versionInfoQuery =
            Query.newBuilder()
                    .setNetworkGetVersionInfo(NetworkGetVersionInfoQuery.getDefaultInstance())
                    .build();
    private GetVersionInfoResourceUsage subject;

    @BeforeEach
    void setup() {
        subject = new GetVersionInfoResourceUsage();
    }

    @Test
    void recognizesApplicability() {
        assertTrue(subject.applicableTo(versionInfoQuery));
        assertFalse(subject.applicableTo(Query.getDefaultInstance()));
    }

    @Test
    void getsExpectedUsage() {
        final var expected = FixedUsageEstimates.getVersionInfoUsage();

        assertEquals(expected, subject.usageGiven(versionInfoQuery, null));
        assertEquals(expected, subject.usageGivenType(versionInfoQuery, null, COST_ANSWER));
        assertEquals(expected, subject.usageGivenType(versionInfoQuery, null, ANSWER_ONLY));
    }
}
