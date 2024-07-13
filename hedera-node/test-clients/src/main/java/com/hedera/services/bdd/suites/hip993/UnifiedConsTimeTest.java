/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.hip993;

import static com.hedera.services.bdd.junit.TestTags.NOT_EMBEDDED;
import static com.hedera.services.bdd.junit.TestTags.REPEATABLE;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.bdd.junit.HapiTest;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@DisplayName("given HIP-993 unified consensus times")
public class UnifiedConsTimeTest {
    /**
     * Tests that a user transaction gets the platform-assigned time. Requires a repeatable network
     * because we need virtual time to stand still between the point we submit the transaction and the
     * point we validate its consensus time is the platform-assigned time
     */
    @HapiTest
    @Tag(REPEATABLE)
    @Tag(NOT_EMBEDDED)
    @DisplayName("user transaction gets platform assigned time")
    final Stream<DynamicTest> userTxnGetsPlatformAssignedTime() {
        return hapiTest(cryptoCreate("somebody").via("txn"), withOpContext((spec, opLog) -> {
            final var op = getTxnRecord("txn");
            allRunFor(spec, op);
            assertEquals(
                    Timestamp.newBuilder()
                            .setSeconds(spec.consensusTime().getEpochSecond())
                            .setNanos(spec.consensusTime().getNano())
                            .build(),
                    op.getResponseRecord().getConsensusTimestamp(),
                    "User transaction should get platform-assigned time");
        }));
    }
}
