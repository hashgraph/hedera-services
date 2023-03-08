/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.schedule;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUppercase;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.schedule.ScheduleLongTermExecutionSpecs.withAndWithoutLongTermEnabled;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

public class ScheduleExecutionSpecStateful extends HapiSuite {
    private static final Logger log = LogManager.getLogger(ScheduleExecutionSpecStateful.class);

    private static final int TMP_MAX_TRANSFER_LENGTH = 2;
    private static final int TMP_MAX_TOKEN_TRANSFER_LENGTH = 2;

    private static final String defaultTxExpiry =
            HapiSpecSetup.getDefaultNodeProps().get("ledger.schedule.txExpiryTimeSecs");
    private static final String LEDGER_TRANSFERS_MAX_LEN = "ledger.transfers.maxLen";
    private static final String defaultMaxTransferLen =
            HapiSpecSetup.getDefaultNodeProps().get(LEDGER_TRANSFERS_MAX_LEN);
    private static final String LEDGER_TOKEN_TRANSFERS_MAX_LEN = "ledger.tokenTransfers.maxLen";
    private static final String defaultMaxTokenTransferLen =
            HapiSpecSetup.getDefaultNodeProps().get(LEDGER_TOKEN_TRANSFERS_MAX_LEN);
    private static final String defaultWhitelist =
            HapiSpecSetup.getDefaultNodeProps().get("scheduling.whitelist");

    private static final String A_TOKEN = "token";
    private static final String VALID_SCHEDULE = "validSchedule";
    private static final String SCHEDULE_PAYER = "schedulePayer";
    private static final String TOKENS_NFTS_ARE_ENABLED = "tokens.nfts.areEnabled";
    private static final String TREASURY = "treasury";
    private static final String SUPPLY_KEY = "supplyKey";
    private static final String SENDER = "sender";
    private static final String RECEIVER_A = "receiverA";
    private static final String RECEIVER_B = "receiverB";
    private static final String RECEIVER_C = "receiverC";
    private static final String PAYING_ACCOUNT = "payingAccount";
    String failingTxn = "failingTxn";

    public static void main(String... args) {
        new ScheduleExecutionSpecStateful().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return withAndWithoutLongTermEnabled(() -> List.of(
                /* Stateful specs from ScheduleExecutionSpecs */
                scheduledUniqueMintFailsWithNftsDisabled(),
                scheduledUniqueBurnFailsWithNftsDisabled(),
                scheduledBurnWithInvalidTokenThrowsUnresolvableSigners(),
                executionWithTransferListWrongSizedFails(),
                executionWithTokenTransferListSizeExceedFails(),
                suiteCleanup()));
    }

    private HapiSpec scheduledBurnWithInvalidTokenThrowsUnresolvableSigners() {
        return defaultHapiSpec("ScheduledBurnWithInvalidTokenThrowsUnresolvableSigners")
                .given(cryptoCreate(SCHEDULE_PAYER))
                .when(scheduleCreate(VALID_SCHEDULE, burnToken("0.0.123231", List.of(1L, 2L)))
                        .designatingPayer(SCHEDULE_PAYER)
                        .hasKnownStatus(UNRESOLVABLE_REQUIRED_SIGNERS))
                .then();
    }

    private HapiSpec scheduledUniqueMintFailsWithNftsDisabled() {
        return defaultHapiSpec("ScheduledUniqueMintFailsWithNftsDisabled")
                .given(
                        cryptoCreate(TREASURY),
                        cryptoCreate(SCHEDULE_PAYER),
                        newKeyNamed(SUPPLY_KEY),
                        tokenCreate(A_TOKEN)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TREASURY)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0),
                        scheduleCreate(VALID_SCHEDULE, mintToken(A_TOKEN, List.of(ByteString.copyFromUtf8("m1"))))
                                .designatingPayer(SCHEDULE_PAYER)
                                .via(failingTxn),
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(Map.of(TOKENS_NFTS_ARE_ENABLED, "false")))
                .when(scheduleSign(VALID_SCHEDULE)
                        .alsoSigningWith(SUPPLY_KEY, SCHEDULE_PAYER, TREASURY)
                        .hasKnownStatus(SUCCESS))
                .then(
                        getTxnRecord(failingTxn)
                                .scheduled()
                                .hasPriority(recordWith().status(NOT_SUPPORTED)),
                        getTokenInfo(A_TOKEN).hasTotalSupply(0),
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(Map.of(TOKENS_NFTS_ARE_ENABLED, "true")));
    }

    private HapiSpec scheduledUniqueBurnFailsWithNftsDisabled() {
        return defaultHapiSpec("ScheduledUniqueBurnFailsWithNftsDisabled")
                .given(
                        cryptoCreate(TREASURY),
                        cryptoCreate(SCHEDULE_PAYER),
                        newKeyNamed(SUPPLY_KEY),
                        tokenCreate(A_TOKEN)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TREASURY)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0),
                        scheduleCreate(VALID_SCHEDULE, burnToken(A_TOKEN, List.of(1L, 2L)))
                                .designatingPayer(SCHEDULE_PAYER)
                                .via(failingTxn),
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(Map.of(TOKENS_NFTS_ARE_ENABLED, "false")))
                .when(scheduleSign(VALID_SCHEDULE)
                        .alsoSigningWith(SUPPLY_KEY, SCHEDULE_PAYER, TREASURY)
                        .hasKnownStatus(SUCCESS))
                .then(
                        getTxnRecord(failingTxn)
                                .scheduled()
                                .hasPriority(recordWith().status(NOT_SUPPORTED)),
                        getTokenInfo(A_TOKEN).hasTotalSupply(0),
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(Map.of(TOKENS_NFTS_ARE_ENABLED, "true")));
    }

    public HapiSpec executionWithTransferListWrongSizedFails() {
        long transferAmount = 1L;
        long senderBalance = 1000L;
        long payingAccountBalance = 1_000_000L;
        long noBalance = 0L;
        final var rejectedTxn = "rejectedTxn";

        return defaultHapiSpec("ExecutionWithTransferListWrongSizedFails")
                .given(
                        overriding(LEDGER_TRANSFERS_MAX_LEN, "" + TMP_MAX_TRANSFER_LENGTH),
                        cryptoCreate(PAYING_ACCOUNT).balance(payingAccountBalance),
                        cryptoCreate(SENDER).balance(senderBalance),
                        cryptoCreate(RECEIVER_A).balance(noBalance),
                        cryptoCreate(RECEIVER_B).balance(noBalance),
                        cryptoCreate(RECEIVER_C).balance(noBalance),
                        scheduleCreate(
                                rejectedTxn,
                                cryptoTransfer(
                                        tinyBarsFromTo(SENDER, RECEIVER_A, transferAmount),
                                        tinyBarsFromTo(SENDER, RECEIVER_B, transferAmount),
                                        tinyBarsFromTo(SENDER, RECEIVER_C, transferAmount))
                                        .memo(randomUppercase(100)))
                                .designatingPayer(PAYING_ACCOUNT)
                                .via("createTx"))
                .when(scheduleSign(rejectedTxn)
                        .alsoSigningWith(SENDER, PAYING_ACCOUNT)
                        .via("signTx")
                        .hasKnownStatus(SUCCESS))
                .then(
                        overriding(LEDGER_TRANSFERS_MAX_LEN, defaultMaxTransferLen),
                        getAccountBalance(SENDER).hasTinyBars(senderBalance),
                        getAccountBalance(RECEIVER_A).hasTinyBars(noBalance),
                        getAccountBalance(RECEIVER_B).hasTinyBars(noBalance),
                        getAccountBalance(RECEIVER_C).hasTinyBars(noBalance),
                        withOpContext((spec, opLog) -> {
                            var triggeredTx = getTxnRecord("createTx").scheduled();

                            allRunFor(spec, triggeredTx);

                            Assertions.assertEquals(
                                    TRANSFER_LIST_SIZE_LIMIT_EXCEEDED,
                                    triggeredTx.getResponseRecord().getReceipt().getStatus(),
                                    "Scheduled transaction should not be successful!");
                        }));
    }

    private HapiSpec executionWithTokenTransferListSizeExceedFails() {
        String xToken = "XXX";
        String invalidSchedule = "withMaxTokenTransfer";
        String schedulePayer = "somebody", xTreasury = "xt", civilianA = "xa", civilianB = "xb";
        String failedTxn = "bad";

        return defaultHapiSpec("ExecutionWithTokenTransferListSizeExceedFails")
                .given(
                        overriding(LEDGER_TOKEN_TRANSFERS_MAX_LEN, "" + TMP_MAX_TOKEN_TRANSFER_LENGTH),
                        newKeyNamed("admin"),
                        cryptoCreate(schedulePayer),
                        cryptoCreate(xTreasury),
                        cryptoCreate(civilianA),
                        cryptoCreate(civilianB),
                        tokenCreate(xToken)
                                .treasury(xTreasury)
                                .initialSupply(100)
                                .adminKey("admin"),
                        tokenAssociate(civilianA, xToken),
                        tokenAssociate(civilianB, xToken))
                .when(scheduleCreate(
                                invalidSchedule,
                                cryptoTransfer(moving(2, xToken).distributing(xTreasury, civilianA, civilianB))
                                        .memo(randomUppercase(100)))
                        .via(failedTxn)
                        .alsoSigningWith(xTreasury, schedulePayer)
                        .designatingPayer(schedulePayer))
                .then(
                        overriding(LEDGER_TOKEN_TRANSFERS_MAX_LEN, defaultMaxTokenTransferLen),
                        getTxnRecord(failedTxn)
                                .scheduled()
                                .hasPriority(recordWith().status(TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED)),
                        getAccountBalance(xTreasury).hasTokenBalance(xToken, 100));
    }

    private HapiSpec suiteCleanup() {
        return defaultHapiSpec("suiteCleanup")
                .given()
                .when()
                .then(
                        overriding("ledger.schedule.txExpiryTimeSecs", defaultTxExpiry),
                        overriding("scheduling.whitelist", defaultWhitelist),
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(Map.of(TOKENS_NFTS_ARE_ENABLED, "true")));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
