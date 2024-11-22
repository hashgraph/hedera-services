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

package com.hedera.services.bdd.suites.integration;

import static com.hedera.node.config.types.StreamMode.RECORDS;
import static com.hedera.services.bdd.junit.RepeatableReason.NEEDS_LAST_ASSIGNED_CONSENSUS_TIME;
import static com.hedera.services.bdd.junit.RepeatableReason.NEEDS_STATE_ACCESS;
import static com.hedera.services.bdd.junit.RepeatableReason.NEEDS_TSS_CONTROL;
import static com.hedera.services.bdd.junit.RepeatableReason.NEEDS_VIRTUAL_TIME_FOR_FAST_EXECUTION;
import static com.hedera.services.bdd.junit.RepeatableReason.THROTTLE_OVERRIDES;
import static com.hedera.services.bdd.junit.TestTags.INTEGRATION;
import static com.hedera.services.bdd.junit.hedera.embedded.EmbeddedMode.REPEATABLE;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.EmbeddedVerbs.exposeMaxSchedulable;
import static com.hedera.services.bdd.spec.utilops.TssVerbs.rekeyingScenario;
import static com.hedera.services.bdd.spec.utilops.TssVerbs.startIgnoringTssSignatureRequests;
import static com.hedera.services.bdd.spec.utilops.TssVerbs.stopIgnoringTssSignatureRequests;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockStreamMustIncludePassFrom;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockingOrder;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.doAdhoc;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.doWithStartupConfigNow;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.exposeSpecSecondTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingAllOf;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.tss.RekeyScenarioOp.BlockSigningType.SIGN_WITH_FAKE;
import static com.hedera.services.bdd.spec.utilops.tss.RekeyScenarioOp.BlockSigningType.SIGN_WITH_LEDGER_ID;
import static com.hedera.services.bdd.spec.utilops.tss.RekeyScenarioOp.DabEdits.NO_DAB_EDITS;
import static com.hedera.services.bdd.spec.utilops.tss.RekeyScenarioOp.NODE_ZERO_DOMINANT_STAKES;
import static com.hedera.services.bdd.spec.utilops.tss.RekeyScenarioOp.TSS_MESSAGE_SIMS;
import static com.hedera.services.bdd.spec.utilops.tss.RekeyScenarioOp.TssMessageSim.INVALID_MESSAGES;
import static com.hedera.services.bdd.spec.utilops.tss.RekeyScenarioOp.TssMessageSim.VALID_MESSAGES;
import static com.hedera.services.bdd.spec.utilops.tss.RekeyScenarioOp.UNEQUAL_NODE_STAKES;
import static com.hedera.services.bdd.suites.HapiSuite.CIVILIAN_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.FUNDING;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_MILLION_HBARS;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusCreateTopic;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_EXPIRY_IS_BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_EXPIRY_MUST_BE_FUTURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_EXPIRY_TOO_LONG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.block.stream.Block;
import com.hedera.node.app.blocks.BlockStreamManager;
import com.hedera.services.bdd.junit.LeakyRepeatableHapiTest;
import com.hedera.services.bdd.junit.RepeatableHapiTest;
import com.hedera.services.bdd.junit.TargetEmbeddedMode;
import com.hedera.services.bdd.junit.hedera.embedded.fakes.FakeTssBaseService;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.utilops.streams.assertions.BlockStreamAssertion;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(INTEGRATION)
@TargetEmbeddedMode(REPEATABLE)
public class RepeatableIntegrationTests {
    @LeakyRepeatableHapiTest(
            value = NEEDS_LAST_ASSIGNED_CONSENSUS_TIME,
            overrides = {"scheduling.longTermEnabled", "scheduling.maxTxnPerSec"})
    final Stream<DynamicTest> cannotScheduleTooManyTxnsInOneSecond() {
        final long lifetime = 123_456L;
        final AtomicLong expiry = new AtomicLong();
        return hapiTest(
                overridingTwo("scheduling.longTermEnabled", "true", "scheduling.maxTxnPerSec", "2"),
                cryptoCreate(CIVILIAN_PAYER).balance(10 * ONE_HUNDRED_HBARS),
                scheduleCreate("first", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 123L)))
                        .payingWith(CIVILIAN_PAYER)
                        .fee(ONE_HBAR)
                        .expiringIn(lifetime),
                // Consensus time advances exactly one second per transaction in repeatable mode
                exposeSpecSecondTo(now -> expiry.set(now + lifetime - 1)),
                sourcing(() -> scheduleCreate("second", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 456L)))
                        .payingWith(CIVILIAN_PAYER)
                        .fee(ONE_HBAR)
                        .expiringAt(expiry.get())),
                sourcing(() -> scheduleCreate("third", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 789)))
                        .payingWith(CIVILIAN_PAYER)
                        .fee(ONE_HBAR)
                        .expiringAt(expiry.get())
                        .hasPrecheck(BUSY)));
    }

    @LeakyRepeatableHapiTest(
            value = NEEDS_LAST_ASSIGNED_CONSENSUS_TIME,
            overrides = {"scheduling.longTermEnabled", "scheduling.maxExpirationFutureSeconds"})
    final Stream<DynamicTest> expiryMustBeValid() {
        final long maxLifetime = 123_456L;
        final AtomicLong lastSecond = new AtomicLong();
        return hapiTest(
                overridingTwo(
                        "scheduling.longTermEnabled",
                        "true",
                        "scheduling.maxExpirationFutureSeconds",
                        "" + maxLifetime),
                exposeSpecSecondTo(lastSecond::set),
                sourcing(() -> scheduleCreate("tooSoon", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 12L)))
                        .expiringAt(lastSecond.get())
                        .hasKnownStatus(SCHEDULE_EXPIRY_MUST_BE_FUTURE)),
                exposeSpecSecondTo(lastSecond::set),
                sourcing(() -> scheduleCreate("tooLate", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 34L)))
                        .expiringAt(lastSecond.get() + 1 + maxLifetime + 1)
                        .hasKnownStatus(SCHEDULE_EXPIRY_TOO_LONG)));
    }

    /**
     * Tests that the consensus {@link com.hedera.hapi.node.base.HederaFunctionality#SCHEDULE_CREATE} throttle is
     * enforced by overriding the dev throttles to the more restrictive mainnet throttles and scheduling one more
     * {@link com.hedera.hapi.node.base.HederaFunctionality#CONSENSUS_CREATE_TOPIC} that is allowed.
     */
    @LeakyRepeatableHapiTest(
            value = {
                NEEDS_LAST_ASSIGNED_CONSENSUS_TIME,
                NEEDS_VIRTUAL_TIME_FOR_FAST_EXECUTION,
                NEEDS_STATE_ACCESS,
                THROTTLE_OVERRIDES
            },
            overrides = {
                "scheduling.longTermEnabled",
                "scheduling.maxExecutionsPerUserTxn",
                "scheduling.whitelist",
            },
            throttles = "testSystemFiles/mainnet-throttles.json")
    final Stream<DynamicTest> throttlingAndExecutionAsExpected() {
        final var expirySecond = new AtomicLong();
        final var maxSchedulableTopicCreates = new AtomicInteger();
        return hapiTest(
                overridingAllOf(Map.of(
                        "scheduling.longTermEnabled", "true",
                        "scheduling.maxExecutionsPerUserTxn", "1",
                        "scheduling.whitelist", "ConsensusCreateTopic")),
                doWithStartupConfigNow(
                        "scheduling.maxExpirationFutureSeconds",
                        (maxLifetime, specTime) -> doAdhoc(
                                () -> expirySecond.set(specTime.getEpochSecond() + Long.parseLong(maxLifetime)))),
                cryptoCreate(CIVILIAN_PAYER).balance(ONE_MILLION_HBARS),
                exposeMaxSchedulable(ConsensusCreateTopic, maxSchedulableTopicCreates::set),
                // Schedule the maximum number of topic creations allowed
                sourcing(() -> blockingOrder(IntStream.range(0, maxSchedulableTopicCreates.get())
                        .mapToObj(i -> scheduleCreate(
                                        "topic" + i, createTopic("t" + i).topicMemo("m" + i))
                                .expiringAt(expirySecond.get())
                                .payingWith(CIVILIAN_PAYER)
                                .fee(ONE_HUNDRED_HBARS))
                        .toArray(SpecOperation[]::new))),
                // And confirm the next is throttled
                sourcing(() -> scheduleCreate(
                                "throttledTopicCreation", createTopic("NTB").topicMemo("NOPE"))
                        .expiringAt(expirySecond.get())
                        .payingWith(CIVILIAN_PAYER)
                        .fee(ONE_HUNDRED_HBARS)
                        .hasKnownStatus(SCHEDULE_EXPIRY_IS_BUSY)));
    }

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
     * Creates a rekeying scenario where the embedded node receives the threshold number of valid TSS messages.
     */
    @LeakyRepeatableHapiTest(
            value = {NEEDS_TSS_CONTROL, NEEDS_VIRTUAL_TIME_FOR_FAST_EXECUTION},
            overrides = {"tss.keyCandidateRoster"})
    Stream<DynamicTest> embeddedNodeVotesGivenThresholdValidMessages() {
        final var scenario = rekeyingScenario(
                // Changing stakes is enough to ensure the candidate roster is different from the active roster
                NO_DAB_EDITS,
                // Give unequal stake to all nodes (so they have different numbers of shares in the candidate roster)
                UNEQUAL_NODE_STAKES,
                // Submit invalid messages from node1, to verify the embedded node votes waits for the required
                // number of threshold valid messages
                TSS_MESSAGE_SIMS.apply(List.of(INVALID_MESSAGES, VALID_MESSAGES, VALID_MESSAGES)),
                SIGN_WITH_FAKE);
        return hapiTest(blockStreamMustIncludePassFrom(spec -> scenario), scenario);
    }

    /**
     * Creates a rekeying scenario where the embedded node receives the threshold number of valid TSS messages.
     */
    @LeakyRepeatableHapiTest(
            value = {NEEDS_TSS_CONTROL, NEEDS_VIRTUAL_TIME_FOR_FAST_EXECUTION},
            overrides = {"tss.keyCandidateRoster", "tss.signWithLedgerId"})
    Stream<DynamicTest> blockSigningHappyPath() {
        final var scenario = rekeyingScenario(
                // Changing stakes is enough to ensure the candidate roster is different from the active roster
                NO_DAB_EDITS,
                // Give unequal stake to all nodes (so they have different numbers of shares in the candidate roster)
                NODE_ZERO_DOMINANT_STAKES,
                // Submit invalid messages from node1, to verify the embedded node votes waits for the required
                // number of threshold valid messages
                TSS_MESSAGE_SIMS.apply(List.of(INVALID_MESSAGES, VALID_MESSAGES, VALID_MESSAGES)),
                SIGN_WITH_LEDGER_ID);
        return hapiTest(blockStreamMustIncludePassFrom(spec -> scenario), scenario);
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
