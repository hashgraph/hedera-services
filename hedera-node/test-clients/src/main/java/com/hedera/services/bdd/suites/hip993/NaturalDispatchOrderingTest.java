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

import static com.hedera.services.bdd.junit.TestTags.REPEATABLE;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.streamMustIncludeNoFailuresFrom;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateVisibleItems;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.visibleNonSyntheticItems;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.FUNDING;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleCreate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.IDENTICAL_SCHEDULE_ALREADY_CREATED;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.hapi.utils.forensics.RecordStreamEntry;
import com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder.ReversingBehavior;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.streams.assertions.VisibleItemsAssertion;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

/**
 * Asserts the expected presence and order of all valid combinations of preceding and following stream items;
 * both when rolled back and directly committed. It particularly emphasizes the natural ordering of
 * {@link TransactionCategory#PRECEDING} stream items as defined in
 * [HIP-993](<a href="https://github.com/hashgraph/hedera-improvement-proposal/blob/a64bdb258d52ba4ce1ca26bede8e03871b9ade10/HIP/hip-993.md#natural-ordering-of-preceding-records">...</a>).
 * <p>
 * The only stream items created in a savepoint that are <b>not</b> expected to be present are those with reversing
 * behavior {@link ReversingBehavior#REMOVABLE}, and whose originating savepoint was rolled back.
 * <p>
 * All other stream items are expected to be present in the record stream once created; but if they are
 * {@link ReversingBehavior#REVERSIBLE}, their status may be changed from {@link ResponseCodeEnum#SUCCESS} to
 * {@link ResponseCodeEnum#REVERTED_SUCCESS} when their originating savepoint is rolled back.
 * <p>
 * Only {@link ReversingBehavior#IRREVERSIBLE} streams items appear unchanged in the record stream no matter whether
 * their originating savepoint is rolled back.
 */
public class NaturalDispatchOrderingTest {
    /**
     * Tests the {@link TransactionCategory#USER} + {@link ReversingBehavior#REVERSIBLE} combination via,
     * <ol>
     *     <Li>Successfully creating a scheduled transaction.</Li>
     *     <Li>Failing to create an identical schedule transaction, which leaves some information in the receipt
     *     despite rolling back the root savepoint stack.</Li>
     * </ol>
     * @return the test
     */
    @HapiTest
    @DisplayName("reversible user stream items are as expected")
    final Stream<DynamicTest> reversibleUserStreamItemsAsExpected() {
        final AtomicReference<VisibleItemsAssertion> assertion = new AtomicReference<>();
        return hapiTest(
                streamMustIncludeNoFailuresFrom(
                        visibleNonSyntheticItems(assertion, "firstCreation", "duplicateCreation")),
                scheduleCreate(
                                "scheduledTxn",
                                cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1))
                                        .fee(ONE_HBAR))
                        .via("firstCreation"),
                scheduleCreate(
                                "duplicateScheduledTxn",
                                cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1))
                                        .fee(ONE_HBAR))
                        .via("duplicateCreation")
                        .hasKnownStatus(IDENTICAL_SCHEDULE_ALREADY_CREATED),
                validateVisibleItems(assertion, reversibleUserValidator()));
    }

    @HapiTest
    @Tag(REPEATABLE)
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

    private static BiConsumer<HapiSpec, Map<String, List<RecordStreamEntry>>> reversibleUserValidator() {
        return (spec, records) -> {
            final var successItems = records.get("firstCreation");
            assertScheduledItemsMatch(successItems, 0, 1, ScheduleCreate, CryptoTransfer);
            final var duplicateItems = records.get("duplicateCreation");
            assertScheduledItemsMatch(duplicateItems, 0, -1, ScheduleCreate);
        };
    }

    private static void assertScheduledItemsMatch(
            @NonNull final List<RecordStreamEntry> items,
            final int userTxnIndex,
            final int triggeredTxnIndex,
            @NonNull final HederaFunctionality... functions) {
        assertEquals(
                functions.length, items.size(), "Expected " + functions.length + " items, but got " + items.size());
        assertArrayEquals(
                items.stream().map(RecordStreamEntry::function).toArray(HederaFunctionality[]::new), functions);
        assertParentChildStructure(items, userTxnIndex, triggeredTxnIndex);
    }

    private static void assertItemsMatch(
            @NonNull final List<RecordStreamEntry> items,
            final int userTxnIndex,
            @NonNull final HederaFunctionality... functions) {
        assertEquals(
                functions.length, items.size(), "Expected " + functions.length + " items, but got " + items.size());
        assertArrayEquals(
                items.stream().map(RecordStreamEntry::function).toArray(HederaFunctionality[]::new), functions);
        assertParentChildStructure(items, userTxnIndex, -1);
    }

    /**
     * Asserts the given stream items, which should all be for a single user transaction, have the expected:
     * <ol>
     *     <li>Consensus time offsets</li>
     *     <li>Nonces</li>
     *     <li>Following child parent consensus times</li>
     * </ol>
     * @param items the items for the user transaction
     * @param numPrecedingChildren the number of preceding children expected
     * @param triggeredTxnIndex if not -1, the index of the triggered transaction
     */
    private static void assertParentChildStructure(
            @NonNull final List<RecordStreamEntry> items, final int numPrecedingChildren, final int triggeredTxnIndex) {
        final var userConsensusTime = items.get(numPrecedingChildren).consensusTime();
        final var userTransactionID = items.get(numPrecedingChildren).txnId();
        for (int i = 0; i < numPrecedingChildren; i++) {
            final var preceding = items.get(i);
            assertEquals(userConsensusTime.minusNanos(i), preceding.consensusTime());
            assertEquals(withNonce(userTransactionID, numPrecedingChildren - i), preceding.txnId());
        }
        int postTriggeredOffset = 0;
        for (int i = numPrecedingChildren + 1; i < items.size(); i++) {
            final var following = items.get(i);
            assertEquals(userConsensusTime.plusNanos(i - numPrecedingChildren), following.consensusTime());
            if (i == triggeredTxnIndex) {
                assertEquals(withScheduled(userTransactionID), following.txnId());
                postTriggeredOffset++;
            } else {
                assertEquals(withNonce(userTransactionID, i - postTriggeredOffset), following.txnId());
            }
        }
    }

    private static TransactionID withNonce(@NonNull final TransactionID parentId, final int nonce) {
        return TransactionID.newBuilder()
                .setAccountID(parentId.getAccountID())
                .setTransactionValidStart(parentId.getTransactionValidStart())
                .setNonce(nonce)
                .build();
    }

    private static TransactionID withScheduled(@NonNull final TransactionID parentId) {
        return TransactionID.newBuilder()
                .setAccountID(parentId.getAccountID())
                .setTransactionValidStart(parentId.getTransactionValidStart())
                .setScheduled(true)
                .build();
    }
}
