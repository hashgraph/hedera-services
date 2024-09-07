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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.block.stream.Block;
import com.hedera.node.app.blocks.BlockStreamManager;
import com.hedera.services.bdd.junit.RepeatableHapiTest;
import com.hedera.services.bdd.junit.hedera.embedded.fakes.FakeTssBaseService;
import com.hedera.services.bdd.spec.utilops.streams.assertions.BlockStreamAssertion;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
        final AtomicBoolean expectBlocks = new AtomicBoolean(false);
        // Once blocks are produced again, the first two will need indirect proofs
        final AtomicInteger remainingIndirectionLevels = new AtomicInteger(2);
        final var streamAssertion = new BlockStreamAssertion() {
            @Override
            public boolean test(@NonNull final Block block) throws AssertionError {
                if (!expectBlocks.get()) {
                    throw new AssertionError("No blocks should be written when TSS is ignoring signature requests");
                } else {
                    final var items = block.items();
                    final var proofItem = items.getLast();
                    assertTrue(proofItem.hasBlockProof(), "Block proof is expected as the last item");
                    final var proof = proofItem.blockProofOrThrow();
                    if (remainingIndirectionLevels.get() == 0) {
                        assertTrue(
                                proof.siblingHashes().isEmpty(),
                                "No sibling hashes should be present on a direct proof");
                        return true;
                    } else {
                        assertEquals(
                                // Two sibling hashes per indirection level
                                2 * remainingIndirectionLevels.get(),
                                proof.siblingHashes().size(),
                                "Wrong number of sibling hashes for indirect proof");
                    }
                    remainingIndirectionLevels.decrementAndGet();
                    return false;
                }
            }
        };
        return hapiTest(
                startIgnoringTssSignatureRequests(),
                blockStreamMustIncludePassFrom(spec -> streamAssertion),
                cryptoCreate("firstIndirectProof"),
                cryptoCreate("secondIndirectProof"),
                stopIgnoringTssSignatureRequests(),
                doAdhoc(() -> expectBlocks.set(true)),
                cryptoCreate("directProof"));
    }
}
