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

package com.hedera.services.bdd.spec.utilops.streams.assertions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.block.stream.Block;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A {@link BlockStreamAssertion} used to verify the presence of some number {@code n} of expected indirect proofs
 * in the block stream. When constructed, it assumes proof construction is paused, and fails if any block
 * is written in this stage.
 * <p>
 * After {@link #startExpectingBlocks()} is called, the assertion will verify that the next {@code n} proofs are
 * indirect proofs with the correct number of sibling hashes; and are followed by a direct proof, at which point
 * it passes.
 */
public class IndirectProofsAssertion implements BlockStreamAssertion {
    private boolean proofsArePaused;
    private int remainingIndirectProofs;

    public IndirectProofsAssertion(final int remainingIndirectProofs) {
        this.proofsArePaused = true;
        this.remainingIndirectProofs = remainingIndirectProofs;
    }

    /**
     * Signals that the assertion should now expect proofs to be created, hence blocks to be written.
     */
    public void startExpectingBlocks() {
        proofsArePaused = false;
    }

    @Override
    public boolean test(@NonNull final Block block) throws AssertionError {
        if (proofsArePaused) {
            throw new AssertionError("No blocks should be written when proofs are unavailable");
        } else {
            final var items = block.items();
            final var proofItem = items.getLast();
            assertTrue(proofItem.hasBlockProof(), "Block proof is expected as the last item");
            final var proof = proofItem.blockProofOrThrow();
            if (remainingIndirectProofs == 0) {
                assertTrue(proof.siblingHashes().isEmpty(), "No sibling hashes should be present on a direct proof");
                return true;
            } else {
                assertEquals(
                        // Two sibling hashes per indirection level
                        2 * remainingIndirectProofs,
                        proof.siblingHashes().size(),
                        "Wrong number of sibling hashes for indirect proof");
            }
            remainingIndirectProofs--;
            return false;
        }
    }
}
