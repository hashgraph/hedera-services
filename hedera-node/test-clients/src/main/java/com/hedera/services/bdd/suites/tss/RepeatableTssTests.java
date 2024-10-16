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

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.config.types.StreamMode.RECORDS;
import static com.hedera.services.bdd.junit.RepeatableReason.NEEDS_TSS_CONTROL;
import static com.hedera.services.bdd.junit.RepeatableReason.NEEDS_VIRTUAL_TIME_FOR_FAST_EXECUTION;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.TssVerbs.startIgnoringTssSignatureRequests;
import static com.hedera.services.bdd.spec.utilops.TssVerbs.stopIgnoringTssSignatureRequests;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockStreamMustIncludePassFrom;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.doAdhoc;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.doWithStartupConfig;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitUntilStartOfNextStakingPeriod;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static java.lang.Long.parseLong;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.node.app.blocks.BlockStreamManager;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.services.bdd.junit.LeakyRepeatableHapiTest;
import com.hedera.services.bdd.junit.RepeatableHapiTest;
import com.hedera.services.bdd.junit.hedera.embedded.fakes.FakeTssBaseService;
import com.hedera.services.bdd.spec.utilops.streams.assertions.BlockStreamAssertion;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicTest;

/**
 * TSS tests that require repeatable mode to run.
 */
public class RepeatableTssTests {
    /**
     * Validates behavior of the {@link BlockStreamManager} under specific conditions related to signature requests
     * and block creation.
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
        return hapiTest(withOpContext((spec, opLog) -> {
            if (spec.startupProperties().getStreamMode("blockStream.streamMode") != RECORDS) {
                allRunFor(
                        spec,
                        startIgnoringTssSignatureRequests(),
                        blockStreamMustIncludePassFrom(ignore -> indirectProofsAssertion),
                        // Each transaction is placed into its own round and hence block with default config
                        cryptoCreate("firstIndirectProof"),
                        cryptoCreate("secondIndirectProof"),
                        stopIgnoringTssSignatureRequests(),
                        doAdhoc(indirectProofsAssertion::startExpectingBlocks),
                        cryptoCreate("directProof"));
            }
        }));
    }

    /**
     * Validates that after the first transaction in a staking period, the embedded node generates a
     * {@link TssMessageTransactionBody} which then triggers a successful {@link TssVoteTransactionBody}.
     * This is a trivial placeholder for he real TSS rekeying process that begins on the first transaction
     * in a staking period.
     * <p>
     * <b>TODO:</b> Continue the rekeying happy path after the successful TSS message.
     * <ol>
     *     <li>(TSS-FUTURE) Initialize the roster such that the embedded node has more than one share;
     *     verify it creates a successful {@link TssMessageTransactionBody} for each of its shares.</li>
     *     <Li>(TSS-FUTURE) Submit valid TSS messages from other nodes in the embedded "network".</Li>
     *     <Li>(TSS-FUTURE) Confirm the embedded node votes yes for the the first {@code t} successful
     *     messages, where {@code t} suffices to meet the recovery threshold.</Li>
     *     <Li>(TSS-FUTURE) Confirm the embedded node's recovered ledger id in its
     *     {@link TssVoteTransactionBody} matches the id returned by the fake TSS library.</Li>
     * </ol>
     */
    @LeakyRepeatableHapiTest(
            value = {NEEDS_TSS_CONTROL, NEEDS_VIRTUAL_TIME_FOR_FAST_EXECUTION},
            overrides = {"tss.keyCandidateRoster"})
    Stream<DynamicTest> tssMessageSubmittedForRekeyingIsSuccessful() {
        return hapiTest(
                blockStreamMustIncludePassFrom(spec -> successfulTssMessageThenVote()),
                // Current TSS default is not to try to key the candidate
                overriding("tss.keyCandidateRoster", "true"),
                doWithStartupConfig(
                        "staking.periodMins",
                        stakePeriodMins -> waitUntilStartOfNextStakingPeriod(parseLong(stakePeriodMins))),
                // This transaction is now first in a new staking period and should trigger the TSS rekeying process,
                // in particular a successful TssMessage from the embedded node (and then a TssVote since this is our
                // placeholder implementation of TssMessageHandler)
                cryptoCreate("rekeyingTransaction"));
    }

    /**
     * Returns an assertion that only passes when it has seen a successful TSS message follows by a successful
     * TSS vote in the block stream.
     *
     * @return the assertion
     */
    private static BlockStreamAssertion successfulTssMessageThenVote() {
        final var sawTssMessage = new AtomicBoolean(false);
        return block -> {
            final var items = block.items();
            final IntConsumer assertSuccessResultAt = i -> {
                assertTrue(i < items.size(), "Missing transaction result");
                final var resultItem = items.get(i);
                assertTrue(resultItem.hasTransactionResult(), "Misplaced transaction result");
                final var result = resultItem.transactionResultOrThrow();
                assertEquals(SUCCESS, result.status());
            };
            for (int i = 0, n = items.size(); i < n; i++) {
                final var item = items.get(i);
                if (item.hasEventTransaction()) {
                    try {
                        final var wrapper = Transaction.PROTOBUF.parse(
                                item.eventTransactionOrThrow().applicationTransactionOrThrow());
                        final var signedTxn = SignedTransaction.PROTOBUF.parse(wrapper.signedTransactionBytes());
                        final var txn = TransactionBody.PROTOBUF.parse(signedTxn.bodyBytes());
                        if (txn.hasTssMessage()) {
                            assertSuccessResultAt.accept(i + 1);
                            sawTssMessage.set(true);
                        } else if (txn.hasTssVote()) {
                            assertTrue(sawTssMessage.get(), "Vote seen before message");
                            assertSuccessResultAt.accept(i + 1);
                            return true;
                        }
                    } catch (ParseException e) {
                        Assertions.fail(e.getMessage());
                    }
                }
            }
            return false;
        };
    }

    /**
     * A {@link BlockStreamAssertion} used to verify the presence of some number {@code n} of expected indirect proofs
     * in the block stream. When constructed, it assumes proof construction is paused, and fails if any block
     * is written in this stage.
     * <p>
     * After {@link #startExpectingBlocks()} is called, the assertion will verify that the next {@code n} proofs are
     * indirect proofs with the correct number of sibling hashes; and are followed by a direct proof, at which point
     * it passes.
     */
    private static class IndirectProofsAssertion implements BlockStreamAssertion {
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
                    assertTrue(
                            proof.siblingHashes().isEmpty(), "No sibling hashes should be present on a direct proof");
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
}
