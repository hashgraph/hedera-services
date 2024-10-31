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
import static com.hedera.services.bdd.junit.RepeatableReason.NEEDS_TSS_CONTROL;
import static com.hedera.services.bdd.junit.RepeatableReason.NEEDS_VIRTUAL_TIME_FOR_FAST_EXECUTION;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockStreamMustIncludePassFrom;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.doWithStartupConfig;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitUntilStartOfNextStakingPeriod;
import static java.lang.Long.parseLong;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.services.bdd.junit.LeakyRepeatableHapiTest;
import com.hedera.services.bdd.spec.utilops.streams.assertions.BlockStreamAssertion;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;

/**
 * TSS tests that require repeatable mode to run.
 */
public class RepeatableTssTests {
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
    @Disabled
    // Need to fix by adding Roster entries to the state before running this test. Will do in next PR
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
    public static BlockStreamAssertion successfulTssMessageThenVote() {
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
}
