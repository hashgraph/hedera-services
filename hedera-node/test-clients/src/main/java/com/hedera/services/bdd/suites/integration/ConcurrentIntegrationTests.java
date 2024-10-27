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

import static com.hedera.hapi.node.base.HederaFunctionality.NODE_STAKE_UPDATE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.BUSY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.FAIL_INVALID;
import static com.hedera.node.app.blocks.schemas.V0560BlockStreamSchema.BLOCK_STREAM_INFO_KEY;
import static com.hedera.services.bdd.junit.EmbeddedReason.MANIPULATES_EVENT_VERSION;
import static com.hedera.services.bdd.junit.TestTags.INTEGRATION;
import static com.hedera.services.bdd.junit.hedera.embedded.EmbeddedMode.CONCURRENT;
import static com.hedera.services.bdd.junit.hedera.embedded.SyntheticVersion.PAST;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.keys.TrieSigMapGenerator.uniqueWithFullPrefixesFor;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.EmbeddedVerbs.mutateScheduleExpiries;
import static com.hedera.services.bdd.spec.utilops.EmbeddedVerbs.mutateToken;
import static com.hedera.services.bdd.spec.utilops.EmbeddedVerbs.simulatePostUpgradeTransaction;
import static com.hedera.services.bdd.spec.utilops.EmbeddedVerbs.viewSingleton;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockStreamMustIncludePassFrom;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.createHollow;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.usingVersion;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.FUNDING;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RECORD_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.state.blockstream.BlockStreamInfo;
import com.hedera.hapi.node.state.primitives.ProtoLong;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.schedule.ScheduleList;
import com.hedera.node.app.blocks.BlockStreamService;
import com.hedera.services.bdd.junit.EmbeddedHapiTest;
import com.hedera.services.bdd.junit.GenesisHapiTest;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.TargetEmbeddedMode;
import com.hedera.services.bdd.junit.hedera.embedded.SyntheticVersion;
import com.hedera.services.bdd.junit.support.translators.inputs.TransactionParts;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.spec.utilops.streams.assertions.BlockStreamAssertion;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(INTEGRATION)
@TargetEmbeddedMode(CONCURRENT)
public class ConcurrentIntegrationTests {
    @HapiTest
    @DisplayName("hollow account completion happens even with unsuccessful txn")
    final Stream<DynamicTest> hollowAccountCompletionHappensEvenWithUnsuccessfulTxn() {
        return hapiTest(
                tokenCreate("token").treasury(DEFAULT_PAYER).initialSupply(123L),
                cryptoCreate("unassociated"),
                createHollow(
                        1,
                        i -> "hollowAccount",
                        evmAddress -> cryptoTransfer(tinyBarsFromTo(GENESIS, evmAddress, ONE_HUNDRED_HBARS))),
                cryptoTransfer(TokenMovement.moving(1, "token").between(DEFAULT_PAYER, "unassociated"))
                        .payingWith("hollowAccount")
                        .sigMapPrefixes(uniqueWithFullPrefixesFor("hollowAccount"))
                        .hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
                getAccountInfo("hollowAccount").isNotHollow());
    }

    @EmbeddedHapiTest(MANIPULATES_EVENT_VERSION)
    @DisplayName("skips pre-upgrade event and streams result with BUSY status")
    final Stream<DynamicTest> skipsStaleEventWithBusyStatus() {
        return hapiTest(
                blockStreamMustIncludePassFrom(spec -> blockWithResultOf(BUSY)),
                cryptoCreate("somebody").balance(0L),
                cryptoTransfer(tinyBarsFromTo(GENESIS, "somebody", ONE_HBAR))
                        .setNode("0.0.4")
                        .withSubmissionStrategy(usingVersion(PAST))
                        .hasKnownStatus(com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY),
                getAccountBalance("somebody").hasTinyBars(0L));
    }

    @EmbeddedHapiTest(MANIPULATES_EVENT_VERSION)
    @DisplayName("only warns of missing creator if event version is current")
    final Stream<DynamicTest> completelySkipsTransactionFromUnknownNode() {
        return hapiTest(
                cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, ONE_HBAR))
                        .setNode("0.0.666")
                        .via("toBeSkipped")
                        .withSubmissionStrategy(usingVersion(SyntheticVersion.PRESENT))
                        .hasAnyStatusAtAll(),
                getTxnRecord("toBeSkipped").hasAnswerOnlyPrecheck(RECORD_NOT_FOUND));
    }

    @GenesisHapiTest
    @DisplayName("node stake update exported at upgrade boundary")
    final Stream<DynamicTest> nodeStakeUpdateExportedAtUpgradeBoundary() {
        // Currently both the genesis and the post-upgrade transaction export node stake updates
        final var expectedNodeStakeUpdates = 2;
        final var actualNodeStakeUpdates = new AtomicInteger(0);
        return hapiTest(
                blockStreamMustIncludePassFrom(
                        spec -> block -> actualNodeStakeUpdates.addAndGet((int) block.items().stream()
                                        .filter(BlockItem::hasEventTransaction)
                                        .map(item -> TransactionParts.from(item.eventTransactionOrThrow()
                                                        .applicationTransactionOrThrow())
                                                .function())
                                        .filter(NODE_STAKE_UPDATE::equals)
                                        .count())
                                == expectedNodeStakeUpdates),
                // This is the genesis transaction
                cryptoCreate("firstUser"),
                // And now simulate an upgrade boundary
                simulatePostUpgradeTransaction(),
                cryptoCreate("secondUser"));
    }

    @GenesisHapiTest
    @DisplayName("fail invalid during dispatch recharges fees")
    final Stream<DynamicTest> failInvalidDuringDispatchRechargesFees() {
        return hapiTest(
                blockStreamMustIncludePassFrom(spec -> blockWithResultOf(FAIL_INVALID)),
                cryptoCreate("treasury").balance(ONE_HUNDRED_HBARS),
                tokenCreate("token").supplyKey("treasury").treasury("treasury").initialSupply(1L),
                // Corrupt the state by removing the treasury account from the token
                mutateToken("token", token -> token.treasuryAccountId((AccountID) null)),
                burnToken("token", 1L)
                        .payingWith("treasury")
                        .hasKnownStatus(com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID),
                // Confirm the payer was still charged a non-zero fee
                getAccountBalance("treasury")
                        .hasTinyBars(spec -> amount ->
                                Optional.ofNullable(amount == ONE_HUNDRED_HBARS ? "Fee was not recharged" : null)));
    }

    @GenesisHapiTest
    @DisplayName("fail invalid outside dispatch does not attempt to charge fees")
    final Stream<DynamicTest> failInvalidOutsideDispatchDoesNotAttemptToChargeFees() {
        final AtomicReference<BlockStreamInfo> blockStreamInfo = new AtomicReference<>();
        return hapiTest(
                blockStreamMustIncludePassFrom(spec -> blockWithResultOf(FAIL_INVALID)),
                cryptoCreate("civilian").balance(ONE_HUNDRED_HBARS),
                // Ensure the block with the previous transaction is sealed
                sleepFor(100),
                // Get the last interval process time from state
                viewSingleton(BlockStreamService.NAME, BLOCK_STREAM_INFO_KEY, blockStreamInfo::set),
                // Ensure the next transaction is in a new second
                sleepFor(1000),
                // Corrupt the state by putting invalid expiring schedules into state
                sourcing(() -> mutateScheduleExpiries(state -> state.put(
                        new ProtoLong(blockStreamInfo
                                .get()
                                .lastIntervalProcessTimeOrThrow()
                                .seconds()),
                        new ScheduleList(List.of(Schedule.DEFAULT))))),
                cryptoTransfer(tinyBarsFromTo("civilian", FUNDING, 1))
                        .fee(ONE_HBAR)
                        .hasKnownStatus(com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID),
                // Confirm the payer was still charged a non-zero fee
                getAccountBalance("civilian")
                        .hasTinyBars(spec -> amount ->
                                Optional.ofNullable(amount != ONE_HUNDRED_HBARS ? "Fee still charged" : null)));
    }

    private static BlockStreamAssertion blockWithResultOf(@NonNull final ResponseCodeEnum status) {
        return block -> block.items().stream()
                .filter(BlockItem::hasTransactionResult)
                .map(BlockItem::transactionResultOrThrow)
                .map(TransactionResult::status)
                .anyMatch(status::equals);
    }
}
