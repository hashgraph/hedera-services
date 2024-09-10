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

package com.hedera.services.bdd.suites.tss;

import static com.hedera.services.bdd.junit.RepeatableReason.NEEDS_TSS_CONTROL;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.TssVerbs.startIgnoringTssSignatureRequests;
import static com.hedera.services.bdd.spec.utilops.TssVerbs.stopIgnoringTssSignatureRequests;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockStreamMustIncludePassFrom;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.doAdhoc;

import com.hedera.node.app.blocks.BlockStreamManager;
import com.hedera.services.bdd.junit.RepeatableHapiTest;
import com.hedera.services.bdd.junit.hedera.embedded.fakes.FakeTssBaseService;
import com.hedera.services.bdd.spec.utilops.streams.assertions.IndirectProofsAssertion;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

/**
 * TSS tests that require repeatable mode to run.
 */
public class RepeatableTssTests {
    /**
     * A test that simulates the behavior of the {@link FakeTssBaseService} under specific conditions
     * related to signature requests and block creation.
     *
     * <p>This test follows three main steps:</p>
     * <ul>
     *     <li>Instructs the {@link FakeTssBaseService} to start ignoring signature requests and
     *     produces several blocks. In this scenario, each transaction is placed into its own round
     *     since the service is operating in repeatable mode.</li>
     *     <li>Verifies that no blocks are written, as no block proofs are available, which is the
     *     expected behavior when the service is ignoring signature requests.</li>
     *     <li>Reactivates the {@link FakeTssBaseService}, creates another block, and verifies that
     *     the {@link BlockStreamManager} processes pending block proofs. It checks that the expected
     *     blocks are written within a brief period after the service resumes normal behavior.</li>
     * </ul>
     *
     * <p>The test ensures that block production halts when block proofs are unavailable and
     * verifies that the system can catch up on pending proofs when the service resumes.</p>
     */
    @RepeatableHapiTest(NEEDS_TSS_CONTROL)
    Stream<DynamicTest> blockStreamManagerCatchesUpWithIndirectProofs() {
        final var indirectProofsAssertion = new IndirectProofsAssertion(2);
        return hapiTest(
                startIgnoringTssSignatureRequests(),
                blockStreamMustIncludePassFrom(spec -> indirectProofsAssertion),
                // Each transaction is placed into its own round and hence block with default config
                cryptoCreate("firstIndirectProof"),
                cryptoCreate("secondIndirectProof"),
                stopIgnoringTssSignatureRequests(),
                doAdhoc(indirectProofsAssertion::startExpectingBlocks),
                cryptoCreate("directProof"));
    }
}
