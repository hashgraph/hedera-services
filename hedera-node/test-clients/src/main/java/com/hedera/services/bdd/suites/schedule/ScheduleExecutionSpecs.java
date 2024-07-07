/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asId;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUppercase;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.deleteTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.invalidBurnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.invalidMintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.revokeTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.systemFileDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.updateTopic;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromToWithInvalidAmounts;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.doSeveralWithStartupConfig;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.recordFeeAmount;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.restoreDefault;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.usableTxnIdNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.ADDRESS_BOOK_CONTROL;
import static com.hedera.services.bdd.suites.HapiSuite.APP_PROPERTIES;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.FREEZE_ADMIN;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.SOFTWARE_UPDATE_ADMIN;
import static com.hedera.services.bdd.suites.HapiSuite.SYSTEM_DELETE_ADMIN;
import static com.hedera.services.bdd.suites.HapiSuite.THREE_MONTHS_IN_SECONDS;
import static com.hedera.services.bdd.suites.freeze.UpgradeSuite.standardUpdateFile;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.ADMIN;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.A_SCHEDULE;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.A_TOKEN;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.BASIC_XFER;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.CREATE_TXN;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.CREATION;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.DEFAULT_MAX_BATCH_SIZE_MINT;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.FAILED_XFER;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.FAILING_TXN;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.LUCKY_RECEIVER;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.ORIG_FILE;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.PAYER;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.PAYING_ACCOUNT;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.PAYING_ACCOUNT_2;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.RANDOM_MSG;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.RECEIVER;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.SCHEDULED_TRANSACTION_MUST_NOT_SUCCEED;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.SCHEDULED_TRANSACTION_MUST_SUCCEED;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.SCHEDULE_CREATE_FEE;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.SCHEDULE_PAYER;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.SCHEDULING_WHITELIST;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.SENDER;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.SENDER_1;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.SENDER_2;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.SENDER_3;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.SIGN_TX;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.SIGN_TXN;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.SUCCESS_TXN;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.SUPPLY_KEY;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.TOKENS_NFTS_MAX_BATCH_SIZE_MINT;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.TRANSACTION_NOT_SCHEDULED;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.TREASURY;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.WEIRDLY_POPULAR_KEY;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.WHITELIST_DEFAULT;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.WHITELIST_MINIMUM;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.WRONG_CONSENSUS_TIMESTAMP;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.WRONG_RECORD_ACCOUNT_ID;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.WRONG_SCHEDULE_ID;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.WRONG_TRANSACTION_VALID_START;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.WRONG_TRANSFER_LIST;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.scheduledVersionOf;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.transferListCheck;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTHORIZATION_FAILED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BATCH_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CHUNK_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CHUNK_TRANSACTION_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_METADATA;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MESSAGE_SIZE_TOO_LARGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.METADATA_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_NEW_VALID_SIGNATURES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_ALREADY_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SOME_SIGNATURES_WERE_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS;
import static java.lang.Integer.parseInt;

import com.google.protobuf.ByteString;
import com.hedera.node.config.data.ConsensusConfig;
import com.hedera.services.bdd.SpecOperation;
import com.hedera.services.bdd.junit.HapiTest;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ScheduleExecutionSpecs {
    private static final Logger log = LogManager.getLogger(ScheduleExecutionSpecs.class);

    /**
     * This is matched to ConsensusTimeTracker.MAX_PRECEDING_RECORDS_REMAINING_TXN + 1.
     * It is not guaranteed to remain thus. If the configuration changes or there are any
     * following records generated by the txn before the scheduled transaction then
     * it could be different.
     */
    private final long normalTriggeredTxnTimestampOffset =
            getTestConfig(ConsensusConfig.class).handleMaxPrecedingRecords() + 1;

    @SuppressWarnings("java:S2245") // using java.util.Random in tests is fine
    private final Random r = new Random(882654L);

    @HapiTest
    @Order(18)
    final Stream<DynamicTest> scheduledBurnFailsWithInvalidTxBody() {
        return defaultHapiSpec("ScheduledBurnFailsWithInvalidTxBody")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(TREASURY),
                        cryptoCreate(SCHEDULE_PAYER),
                        newKeyNamed(SUPPLY_KEY),
                        tokenCreate(A_TOKEN)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TREASURY)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0))
                .when(scheduleCreate(A_SCHEDULE, invalidBurnToken(A_TOKEN, List.of(1L, 2L), 123))
                        .designatingPayer(SCHEDULE_PAYER)
                        .hasKnownStatus(INVALID_TRANSACTION_BODY))
                .then();
    }

    @HapiTest
    @Order(23)
    final Stream<DynamicTest> scheduledMintFailsWithInvalidTxBody() {
        return defaultHapiSpec("ScheduledMintFailsWithInvalidTxBody")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(TREASURY),
                        cryptoCreate(SCHEDULE_PAYER),
                        newKeyNamed(SUPPLY_KEY),
                        tokenCreate(A_TOKEN)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TREASURY)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0))
                .when()
                .then(
                        scheduleCreate(
                                        A_SCHEDULE,
                                        invalidMintToken(A_TOKEN, List.of(ByteString.copyFromUtf8("m1")), 123))
                                .hasKnownStatus(INVALID_TRANSACTION_BODY)
                                .designatingPayer(SCHEDULE_PAYER),
                        getTokenInfo(A_TOKEN).hasTotalSupply(0));
    }

    @HapiTest
    @Order(24)
    final Stream<DynamicTest> scheduledMintWithInvalidTokenThrowsUnresolvableSigners() {
        return defaultHapiSpec("ScheduledMintWithInvalidTokenThrowsUnresolvableSigners")
                .given(overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM), cryptoCreate(SCHEDULE_PAYER))
                .when(scheduleCreate(
                                A_SCHEDULE,
                                mintToken("0.0.123231", List.of(ByteString.copyFromUtf8("m1")))
                                        .fee(ONE_HBAR))
                        .designatingPayer(SCHEDULE_PAYER)
                        .hasKnownStatus(UNRESOLVABLE_REQUIRED_SIGNERS))
                .then();
    }

    @HapiTest
    @Order(35)
    final Stream<DynamicTest> scheduledUniqueBurnFailsWithInvalidBatchSize() {
        return defaultHapiSpec("ScheduledUniqueBurnFailsWithInvalidBatchSize")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(TREASURY),
                        cryptoCreate(SCHEDULE_PAYER),
                        newKeyNamed(SUPPLY_KEY),
                        tokenCreate(A_TOKEN)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TREASURY)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0),
                        mintToken(A_TOKEN, List.of(ByteString.copyFromUtf8("m1"))),
                        scheduleCreate(
                                        A_SCHEDULE,
                                        burnToken(
                                                A_TOKEN,
                                                List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L)))
                                .designatingPayer(SCHEDULE_PAYER)
                                .via(FAILING_TXN))
                .when(
                        getTokenInfo(A_TOKEN).hasTotalSupply(1),
                        scheduleSign(A_SCHEDULE)
                                .alsoSigningWith(SUPPLY_KEY, SCHEDULE_PAYER, TREASURY)
                                .hasKnownStatus(SUCCESS))
                .then(
                        getTxnRecord(FAILING_TXN)
                                .scheduled()
                                .hasPriority(recordWith().status(BATCH_SIZE_LIMIT_EXCEEDED)),
                        getTokenInfo(A_TOKEN).hasTotalSupply(1));
    }

    @HapiTest
    @Order(34)
    final Stream<DynamicTest> scheduledUniqueBurnExecutesProperly() {
        return defaultHapiSpec("ScheduledUniqueBurnExecutesProperly")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(TREASURY),
                        cryptoCreate(SCHEDULE_PAYER),
                        newKeyNamed(SUPPLY_KEY),
                        tokenCreate(A_TOKEN)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TREASURY)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0),
                        mintToken(A_TOKEN, List.of(ByteString.copyFromUtf8("metadata"))),
                        scheduleCreate(A_SCHEDULE, burnToken(A_TOKEN, List.of(1L)))
                                .designatingPayer(SCHEDULE_PAYER)
                                .via(SUCCESS_TXN))
                .when(
                        getTokenInfo(A_TOKEN).hasTotalSupply(1),
                        scheduleSign(A_SCHEDULE)
                                .alsoSigningWith(SUPPLY_KEY, SCHEDULE_PAYER, TREASURY)
                                .via(SIGN_TX)
                                .hasKnownStatus(SUCCESS))
                .then(
                        withOpContext((spec, opLog) -> {
                            var createTx = getTxnRecord(SUCCESS_TXN);
                            var signTx = getTxnRecord(SIGN_TX);
                            var triggeredTx = getTxnRecord(SUCCESS_TXN).scheduled();
                            allRunFor(spec, createTx, signTx, triggeredTx);

                            Assertions.assertEquals(
                                    signTx.getResponseRecord()
                                                    .getConsensusTimestamp()
                                                    .getNanos()
                                            + normalTriggeredTxnTimestampOffset,
                                    triggeredTx
                                            .getResponseRecord()
                                            .getConsensusTimestamp()
                                            .getNanos(),
                                    WRONG_CONSENSUS_TIMESTAMP);

                            Assertions.assertEquals(
                                    createTx.getResponseRecord()
                                            .getTransactionID()
                                            .getTransactionValidStart(),
                                    triggeredTx
                                            .getResponseRecord()
                                            .getTransactionID()
                                            .getTransactionValidStart(),
                                    WRONG_TRANSACTION_VALID_START);

                            Assertions.assertEquals(
                                    createTx.getResponseRecord()
                                            .getTransactionID()
                                            .getAccountID(),
                                    triggeredTx
                                            .getResponseRecord()
                                            .getTransactionID()
                                            .getAccountID(),
                                    WRONG_RECORD_ACCOUNT_ID);

                            Assertions.assertTrue(
                                    triggeredTx
                                            .getResponseRecord()
                                            .getTransactionID()
                                            .getScheduled(),
                                    TRANSACTION_NOT_SCHEDULED);

                            Assertions.assertEquals(
                                    createTx.getResponseRecord().getReceipt().getScheduleID(),
                                    triggeredTx.getResponseRecord().getScheduleRef(),
                                    WRONG_SCHEDULE_ID);
                        }),
                        getTokenInfo(A_TOKEN).hasTotalSupply(0));
    }

    @HapiTest
    @Order(39)
    final Stream<DynamicTest> scheduledUniqueMintFailsWithInvalidMetadata() {
        return defaultHapiSpec("ScheduledUniqueMintFailsWithInvalidMetadata")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate("payer"),
                        cryptoCreate(TREASURY),
                        cryptoCreate(SCHEDULE_PAYER),
                        newKeyNamed(SUPPLY_KEY),
                        tokenCreate(A_TOKEN)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TREASURY)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0),
                        scheduleCreate(A_SCHEDULE, mintToken(A_TOKEN, List.of(metadataOfLength(101))))
                                .designatingPayer(SCHEDULE_PAYER)
                                .payingWith("payer")
                                .via(FAILING_TXN))
                .when(scheduleSign(A_SCHEDULE)
                        .alsoSigningWith(SUPPLY_KEY, SCHEDULE_PAYER, TREASURY)
                        .hasKnownStatus(SUCCESS))
                .then(
                        getTxnRecord(FAILING_TXN)
                                .scheduled()
                                .hasPriority(recordWith().status(METADATA_TOO_LONG)),
                        getTokenInfo(A_TOKEN).hasTotalSupply(0));
    }

    @HapiTest
    @Order(36)
    final Stream<DynamicTest> scheduledUniqueBurnFailsWithInvalidNftId() {
        return defaultHapiSpec("ScheduledUniqueBurnFailsWithInvalidNftId")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(TREASURY),
                        cryptoCreate(SCHEDULE_PAYER),
                        newKeyNamed(SUPPLY_KEY),
                        tokenCreate(A_TOKEN)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TREASURY)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0),
                        scheduleCreate(A_SCHEDULE, burnToken(A_TOKEN, List.of(123L)))
                                .designatingPayer(SCHEDULE_PAYER)
                                .via(FAILING_TXN))
                .when(scheduleSign(A_SCHEDULE)
                        .alsoSigningWith(SUPPLY_KEY, SCHEDULE_PAYER, TREASURY)
                        .hasKnownStatus(SUCCESS))
                .then(getTxnRecord(FAILING_TXN)
                        .scheduled()
                        .hasPriority(recordWith().status(INVALID_NFT_ID)));
    }

    @HapiTest
    @Order(20)
    final Stream<DynamicTest> scheduledBurnForUniqueSucceedsWithExistingAmount() {
        return defaultHapiSpec("scheduledBurnForUniqueSucceedsWithExistingAmount")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(TREASURY),
                        cryptoCreate(SCHEDULE_PAYER),
                        newKeyNamed(SUPPLY_KEY),
                        tokenCreate(A_TOKEN)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TREASURY)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0),
                        scheduleCreate(A_SCHEDULE, burnToken(A_TOKEN, 123L))
                                .designatingPayer(SCHEDULE_PAYER)
                                .via(SUCCESS_TXN))
                .when(scheduleSign(A_SCHEDULE)
                        .alsoSigningWith(SUPPLY_KEY, SCHEDULE_PAYER, TREASURY)
                        .hasKnownStatus(SUCCESS))
                .then(
                        getTxnRecord(SUCCESS_TXN)
                                .scheduled()
                                .hasPriority(recordWith().status(INVALID_TOKEN_BURN_METADATA)),
                        getTokenInfo(A_TOKEN).hasTotalSupply(0));
    }

    @HapiTest
    @Order(19)
    final Stream<DynamicTest> scheduledBurnForUniqueFailsWithInvalidAmount() {
        return defaultHapiSpec("ScheduledBurnForUniqueFailsWithInvalidAmount")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(TREASURY),
                        cryptoCreate(SCHEDULE_PAYER),
                        newKeyNamed(SUPPLY_KEY),
                        tokenCreate(A_TOKEN)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TREASURY)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0))
                .when()
                .then(
                        scheduleCreate(A_SCHEDULE, burnToken(A_TOKEN, -123L))
                                .designatingPayer(SCHEDULE_PAYER)
                                .hasKnownStatus(INVALID_TOKEN_BURN_AMOUNT),
                        getTokenInfo(A_TOKEN).hasTotalSupply(0));
    }

    private ByteString metadataOfLength(int length) {
        return ByteString.copyFrom(genRandomBytes(length));
    }

    private byte[] genRandomBytes(int numBytes) {
        byte[] contents = new byte[numBytes];
        r.nextBytes(contents);
        return contents;
    }

    @HapiTest
    @Order(38)
    final Stream<DynamicTest> scheduledUniqueMintFailsWithInvalidBatchSize() {
        return defaultHapiSpec("ScheduledUniqueMintFailsWithInvalidBatchSize")
                .given(
                        overriding(TOKENS_NFTS_MAX_BATCH_SIZE_MINT, "5"),
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(TREASURY),
                        cryptoCreate(SCHEDULE_PAYER),
                        newKeyNamed(SUPPLY_KEY),
                        tokenCreate(A_TOKEN)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TREASURY)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0),
                        scheduleCreate(
                                        A_SCHEDULE,
                                        mintToken(
                                                A_TOKEN,
                                                List.of(
                                                        ByteString.copyFromUtf8("m1"),
                                                        ByteString.copyFromUtf8("m2"),
                                                        ByteString.copyFromUtf8("m3"),
                                                        ByteString.copyFromUtf8("m4"),
                                                        ByteString.copyFromUtf8("m5"),
                                                        ByteString.copyFromUtf8("m6"))))
                                .designatingPayer(SCHEDULE_PAYER)
                                .via(FAILING_TXN))
                .when(scheduleSign(A_SCHEDULE)
                        .alsoSigningWith(SUPPLY_KEY, SCHEDULE_PAYER, TREASURY)
                        .hasKnownStatus(SUCCESS))
                .then(
                        getTxnRecord(FAILING_TXN)
                                .scheduled()
                                .hasPriority(recordWith().status(BATCH_SIZE_LIMIT_EXCEEDED)),
                        getTokenInfo(A_TOKEN).hasTotalSupply(0),
                        overriding(TOKENS_NFTS_MAX_BATCH_SIZE_MINT, DEFAULT_MAX_BATCH_SIZE_MINT));
    }

    @HapiTest
    @Order(22)
    final Stream<DynamicTest> scheduledMintFailsWithInvalidAmount() {
        final var zeroAmountTxn = "zeroAmountTxn";
        return defaultHapiSpec("ScheduledMintFailsWithInvalidAmount")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(TREASURY),
                        cryptoCreate(SCHEDULE_PAYER),
                        newKeyNamed(SUPPLY_KEY),
                        tokenCreate(A_TOKEN)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TREASURY)
                                .initialSupply(101),
                        scheduleCreate(A_SCHEDULE, mintToken(A_TOKEN, 0))
                                .designatingPayer(SCHEDULE_PAYER)
                                .via(zeroAmountTxn))
                .when()
                .then(
                        scheduleCreate(A_SCHEDULE, mintToken(A_TOKEN, -1))
                                .designatingPayer(SCHEDULE_PAYER)
                                .hasKnownStatus(INVALID_TOKEN_MINT_AMOUNT),
                        getTokenInfo(A_TOKEN).hasTotalSupply(101));
    }

    @HapiTest
    @Order(37)
    final Stream<DynamicTest> scheduledUniqueMintExecutesProperly() {
        return defaultHapiSpec("ScheduledUniqueMintExecutesProperly")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(TREASURY),
                        cryptoCreate(SCHEDULE_PAYER),
                        newKeyNamed(SUPPLY_KEY),
                        tokenCreate(A_TOKEN)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TREASURY)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0),
                        scheduleCreate(
                                        A_SCHEDULE,
                                        mintToken(
                                                A_TOKEN,
                                                List.of(
                                                        ByteString.copyFromUtf8("somemetadata1"),
                                                        ByteString.copyFromUtf8("somemetadata2"))))
                                .designatingPayer(SCHEDULE_PAYER)
                                .via(SUCCESS_TXN))
                .when(scheduleSign(A_SCHEDULE)
                        .alsoSigningWith(SUPPLY_KEY, SCHEDULE_PAYER, TREASURY)
                        .via(SIGN_TX)
                        .hasKnownStatus(SUCCESS))
                .then(
                        withOpContext((spec, opLog) -> {
                            var createTx = getTxnRecord(SUCCESS_TXN);
                            var signTx = getTxnRecord(SIGN_TX);
                            var triggeredTx = getTxnRecord(SUCCESS_TXN).scheduled();
                            allRunFor(spec, createTx, signTx, triggeredTx);

                            Assertions.assertEquals(
                                    signTx.getResponseRecord()
                                                    .getConsensusTimestamp()
                                                    .getNanos()
                                            + normalTriggeredTxnTimestampOffset,
                                    triggeredTx
                                            .getResponseRecord()
                                            .getConsensusTimestamp()
                                            .getNanos(),
                                    WRONG_CONSENSUS_TIMESTAMP);

                            Assertions.assertEquals(
                                    createTx.getResponseRecord()
                                            .getTransactionID()
                                            .getTransactionValidStart(),
                                    triggeredTx
                                            .getResponseRecord()
                                            .getTransactionID()
                                            .getTransactionValidStart(),
                                    WRONG_TRANSACTION_VALID_START);

                            Assertions.assertEquals(
                                    createTx.getResponseRecord()
                                            .getTransactionID()
                                            .getAccountID(),
                                    triggeredTx
                                            .getResponseRecord()
                                            .getTransactionID()
                                            .getAccountID(),
                                    WRONG_RECORD_ACCOUNT_ID);

                            Assertions.assertTrue(
                                    triggeredTx
                                            .getResponseRecord()
                                            .getTransactionID()
                                            .getScheduled(),
                                    TRANSACTION_NOT_SCHEDULED);

                            Assertions.assertEquals(
                                    createTx.getResponseRecord().getReceipt().getScheduleID(),
                                    triggeredTx.getResponseRecord().getScheduleRef(),
                                    WRONG_SCHEDULE_ID);
                        }),
                        getTokenInfo(A_TOKEN).hasTotalSupply(2));
    }

    @HapiTest
    @Order(21)
    final Stream<DynamicTest> scheduledMintExecutesProperly() {
        return defaultHapiSpec("ScheduledMintExecutesProperly")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(TREASURY),
                        cryptoCreate(SCHEDULE_PAYER),
                        newKeyNamed(SUPPLY_KEY),
                        tokenCreate(A_TOKEN)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TREASURY)
                                .initialSupply(101),
                        scheduleCreate(A_SCHEDULE, mintToken(A_TOKEN, 10))
                                .designatingPayer(SCHEDULE_PAYER)
                                .via(SUCCESS_TXN))
                .when(scheduleSign(A_SCHEDULE)
                        .alsoSigningWith(SUPPLY_KEY, SCHEDULE_PAYER, TREASURY)
                        .via(SIGN_TX)
                        .hasKnownStatus(SUCCESS))
                .then(
                        withOpContext((spec, opLog) -> {
                            var createTx = getTxnRecord(SUCCESS_TXN);
                            var signTx = getTxnRecord(SIGN_TX);
                            var triggeredTx = getTxnRecord(SUCCESS_TXN).scheduled();
                            allRunFor(spec, createTx, signTx, triggeredTx);

                            Assertions.assertEquals(
                                    signTx.getResponseRecord()
                                                    .getConsensusTimestamp()
                                                    .getNanos()
                                            + normalTriggeredTxnTimestampOffset,
                                    triggeredTx
                                            .getResponseRecord()
                                            .getConsensusTimestamp()
                                            .getNanos(),
                                    WRONG_CONSENSUS_TIMESTAMP);

                            Assertions.assertEquals(
                                    createTx.getResponseRecord()
                                            .getTransactionID()
                                            .getTransactionValidStart(),
                                    triggeredTx
                                            .getResponseRecord()
                                            .getTransactionID()
                                            .getTransactionValidStart(),
                                    WRONG_TRANSACTION_VALID_START);

                            Assertions.assertEquals(
                                    createTx.getResponseRecord()
                                            .getTransactionID()
                                            .getAccountID(),
                                    triggeredTx
                                            .getResponseRecord()
                                            .getTransactionID()
                                            .getAccountID(),
                                    WRONG_RECORD_ACCOUNT_ID);

                            Assertions.assertTrue(
                                    triggeredTx
                                            .getResponseRecord()
                                            .getTransactionID()
                                            .getScheduled(),
                                    TRANSACTION_NOT_SCHEDULED);

                            Assertions.assertEquals(
                                    createTx.getResponseRecord().getReceipt().getScheduleID(),
                                    triggeredTx.getResponseRecord().getScheduleRef(),
                                    WRONG_SCHEDULE_ID);
                        }),
                        getTokenInfo(A_TOKEN).hasTotalSupply(111));
    }

    @HapiTest
    @Order(17)
    final Stream<DynamicTest> scheduledBurnExecutesProperly() {
        return defaultHapiSpec("ScheduledBurnExecutesProperly")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(TREASURY),
                        cryptoCreate(SCHEDULE_PAYER),
                        newKeyNamed(SUPPLY_KEY),
                        tokenCreate(A_TOKEN)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TREASURY)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(101),
                        scheduleCreate(A_SCHEDULE, burnToken(A_TOKEN, 10))
                                .designatingPayer(SCHEDULE_PAYER)
                                .via(SUCCESS_TXN))
                .when(scheduleSign(A_SCHEDULE)
                        .alsoSigningWith(SUPPLY_KEY, SCHEDULE_PAYER, TREASURY)
                        .via(SIGN_TX)
                        .hasKnownStatus(SUCCESS))
                .then(
                        withOpContext((spec, opLog) -> {
                            var createTx = getTxnRecord(SUCCESS_TXN);
                            var signTx = getTxnRecord(SIGN_TX);
                            var triggeredTx = getTxnRecord(SUCCESS_TXN).scheduled();
                            allRunFor(spec, createTx, signTx, triggeredTx);

                            Assertions.assertEquals(
                                    signTx.getResponseRecord()
                                                    .getConsensusTimestamp()
                                                    .getNanos()
                                            + normalTriggeredTxnTimestampOffset,
                                    triggeredTx
                                            .getResponseRecord()
                                            .getConsensusTimestamp()
                                            .getNanos(),
                                    WRONG_CONSENSUS_TIMESTAMP);

                            Assertions.assertEquals(
                                    createTx.getResponseRecord()
                                            .getTransactionID()
                                            .getTransactionValidStart(),
                                    triggeredTx
                                            .getResponseRecord()
                                            .getTransactionID()
                                            .getTransactionValidStart(),
                                    WRONG_TRANSACTION_VALID_START);

                            Assertions.assertEquals(
                                    createTx.getResponseRecord()
                                            .getTransactionID()
                                            .getAccountID(),
                                    triggeredTx
                                            .getResponseRecord()
                                            .getTransactionID()
                                            .getAccountID(),
                                    WRONG_RECORD_ACCOUNT_ID);

                            Assertions.assertTrue(
                                    triggeredTx
                                            .getResponseRecord()
                                            .getTransactionID()
                                            .getScheduled(),
                                    TRANSACTION_NOT_SCHEDULED);

                            Assertions.assertEquals(
                                    createTx.getResponseRecord().getReceipt().getScheduleID(),
                                    triggeredTx.getResponseRecord().getScheduleRef(),
                                    WRONG_SCHEDULE_ID);
                        }),
                        getTokenInfo(A_TOKEN).hasTotalSupply(91));
    }

    @HapiTest
    @Order(40)
    final Stream<DynamicTest> scheduledXferFailingWithDeletedAccountPaysServiceFeeButNoImpact() {
        final String xToken = "XXX";
        final String validSchedule = "withLiveAccount";
        final String invalidSchedule = "withDeletedAccount";
        final String schedulePayer = PAYER;
        final String xTreasury = "xt";
        final String xCivilian = "xc";
        final String deadXCivilian = "deadxc";
        final String successTx = "good";
        final String failedTx = "bad";
        final AtomicReference<Map<AccountID, Long>> successFeesObs = new AtomicReference<>();
        final AtomicReference<Map<AccountID, Long>> failureFeesObs = new AtomicReference<>();

        return defaultHapiSpec("ScheduledXferFailingWithDeletedTokenPaysServiceFeeButNoImpact")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(schedulePayer),
                        cryptoCreate(xTreasury),
                        cryptoCreate(xCivilian),
                        cryptoCreate(deadXCivilian),
                        tokenCreate(xToken).treasury(xTreasury).initialSupply(101),
                        tokenAssociate(xCivilian, xToken),
                        tokenAssociate(deadXCivilian, xToken))
                .when(
                        scheduleCreate(
                                        validSchedule,
                                        cryptoTransfer(moving(1, xToken).between(xTreasury, xCivilian)))
                                .via(successTx)
                                .alsoSigningWith(xTreasury, schedulePayer)
                                .designatingPayer(schedulePayer),
                        getAccountBalance(xTreasury).hasTokenBalance(xToken, 100),
                        getAccountBalance(xCivilian).hasTokenBalance(xToken, 1),
                        getTxnRecord(successTx).scheduled().logged().revealingDebitsTo(successFeesObs::set),
                        cryptoDelete(deadXCivilian),
                        scheduleCreate(
                                        invalidSchedule,
                                        cryptoTransfer(moving(1, xToken).between(xTreasury, deadXCivilian)))
                                .via(failedTx)
                                .alsoSigningWith(xTreasury, schedulePayer)
                                .designatingPayer(schedulePayer))
                .then(
                        getTxnRecord(failedTx)
                                .scheduled()
                                .hasPriority(recordWith().status(ACCOUNT_DELETED))
                                .revealingDebitsTo(failureFeesObs::set),
                        getAccountBalance(xTreasury).hasTokenBalance(xToken, 100),
                        assertionsHold((spec, opLog) ->
                                assertBasicallyIdentical(successFeesObs.get(), failureFeesObs.get(), 1.0)));
    }

    @HapiTest
    @Order(41)
    final Stream<DynamicTest> scheduledXferFailingWithDeletedTokenPaysServiceFeeButNoImpact() {
        String xToken = "XXX";
        String validSchedule = "withLiveToken";
        String invalidSchedule = "withDeletedToken";
        String schedulePayer = PAYER;
        String xTreasury = "xt";
        String xCivilian = "xc";
        String successTx = "good";
        String failedTx = "bad";
        AtomicReference<Map<AccountID, Long>> successFeesObs = new AtomicReference<>();
        AtomicReference<Map<AccountID, Long>> failureFeesObs = new AtomicReference<>();

        return defaultHapiSpec("ScheduledXferFailingWithDeletedTokenPaysServiceFeeButNoImpact")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        newKeyNamed(ADMIN),
                        cryptoCreate(schedulePayer),
                        cryptoCreate(xTreasury),
                        cryptoCreate(xCivilian),
                        tokenCreate(xToken)
                                .treasury(xTreasury)
                                .initialSupply(101)
                                .adminKey(ADMIN),
                        tokenAssociate(xCivilian, xToken))
                .when(
                        scheduleCreate(
                                        validSchedule,
                                        cryptoTransfer(moving(1, xToken).between(xTreasury, xCivilian))
                                                .memo(randomUppercase(100)))
                                .via(successTx)
                                .alsoSigningWith(xTreasury, schedulePayer)
                                .designatingPayer(schedulePayer),
                        getAccountBalance(xTreasury).hasTokenBalance(xToken, 100),
                        getAccountBalance(xCivilian).hasTokenBalance(xToken, 1),
                        getTxnRecord(successTx).scheduled().logged().revealingDebitsTo(successFeesObs::set),
                        tokenDelete(xToken),
                        scheduleCreate(
                                        invalidSchedule,
                                        cryptoTransfer(moving(1, xToken).between(xTreasury, xCivilian))
                                                .memo(randomUppercase(100)))
                                .via(failedTx)
                                .alsoSigningWith(xTreasury, schedulePayer)
                                .designatingPayer(schedulePayer))
                .then(
                        getTxnRecord(failedTx)
                                .scheduled()
                                .hasPriority(recordWith().status(TOKEN_WAS_DELETED))
                                .revealingDebitsTo(failureFeesObs::set),
                        getAccountBalance(xTreasury).hasTokenBalance(xToken, 100),
                        assertionsHold((spec, opLog) ->
                                assertBasicallyIdentical(successFeesObs.get(), failureFeesObs.get(), 1.0)));
    }

    @HapiTest
    @Order(43)
    final Stream<DynamicTest> scheduledXferFailingWithFrozenAccountTransferPaysServiceFeeButNoImpact() {
        String xToken = "XXX";
        String validSchedule = "withUnfrozenAccount";
        String invalidSchedule = "withFrozenAccount";
        String schedulePayer = PAYER;
        String xTreasury = "xt";
        String xCivilian = "xc";
        String successTx = "good";
        String failedTx = "bad";
        AtomicReference<Map<AccountID, Long>> successFeesObs = new AtomicReference<>();
        AtomicReference<Map<AccountID, Long>> failureFeesObs = new AtomicReference<>();

        return defaultHapiSpec("ScheduledXferFailingWithFrozenAccountTransferPaysServiceFeeButNoImpact")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        newKeyNamed("freeze"),
                        cryptoCreate(schedulePayer),
                        cryptoCreate(xTreasury),
                        cryptoCreate(xCivilian),
                        tokenCreate(xToken)
                                .treasury(xTreasury)
                                .initialSupply(101)
                                .freezeKey("freeze")
                                .freezeDefault(true),
                        tokenAssociate(xCivilian, xToken),
                        tokenUnfreeze(xToken, xCivilian))
                .when(
                        scheduleCreate(
                                        validSchedule,
                                        cryptoTransfer(moving(1, xToken).between(xTreasury, xCivilian))
                                                .memo(randomUppercase(100)))
                                .via(successTx)
                                .alsoSigningWith(xTreasury, schedulePayer)
                                .designatingPayer(schedulePayer),
                        getAccountBalance(xTreasury).hasTokenBalance(xToken, 100),
                        getAccountBalance(xCivilian).hasTokenBalance(xToken, 1),
                        getTxnRecord(successTx).scheduled().logged().revealingDebitsTo(successFeesObs::set),
                        tokenFreeze(xToken, xCivilian),
                        scheduleCreate(
                                        invalidSchedule,
                                        cryptoTransfer(moving(1, xToken).between(xTreasury, xCivilian))
                                                .memo(randomUppercase(100)))
                                .via(failedTx)
                                .alsoSigningWith(xTreasury, schedulePayer)
                                .designatingPayer(schedulePayer))
                .then(
                        getTxnRecord(failedTx)
                                .scheduled()
                                .hasPriority(recordWith().status(ACCOUNT_FROZEN_FOR_TOKEN))
                                .revealingDebitsTo(failureFeesObs::set),
                        getAccountBalance(xTreasury).hasTokenBalance(xToken, 100),
                        assertionsHold((spec, opLog) ->
                                assertBasicallyIdentical(successFeesObs.get(), failureFeesObs.get(), 1.0)));
    }

    @HapiTest
    @Order(44)
    final Stream<DynamicTest> scheduledXferFailingWithNonKycedAccountTransferPaysServiceFeeButNoImpact() {
        String xToken = "XXX";
        String validSchedule = "withKycedToken";
        String invalidSchedule = "withNonKycedToken";
        String schedulePayer = PAYER;
        String xTreasury = "xt";
        String xCivilian = "xc";
        String successTx = "good";
        String failedTx = "bad";
        AtomicReference<Map<AccountID, Long>> successFeesObs = new AtomicReference<>();
        AtomicReference<Map<AccountID, Long>> failureFeesObs = new AtomicReference<>();

        return defaultHapiSpec("ScheduledXferFailingWithNonKycedAccountTransferPaysServiceFeeButNoImpact")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        newKeyNamed("kyc"),
                        cryptoCreate(schedulePayer),
                        cryptoCreate(xTreasury),
                        cryptoCreate(xCivilian),
                        tokenCreate(xToken)
                                .treasury(xTreasury)
                                .initialSupply(101)
                                .kycKey("kyc"),
                        tokenAssociate(xCivilian, xToken),
                        grantTokenKyc(xToken, xCivilian))
                .when(
                        scheduleCreate(
                                        validSchedule,
                                        cryptoTransfer(moving(1, xToken).between(xTreasury, xCivilian))
                                                .memo(randomUppercase(100)))
                                .via(successTx)
                                .alsoSigningWith(xTreasury, schedulePayer)
                                .designatingPayer(schedulePayer),
                        getAccountBalance(xTreasury).hasTokenBalance(xToken, 100),
                        getAccountBalance(xCivilian).hasTokenBalance(xToken, 1),
                        getTxnRecord(successTx).scheduled().logged().revealingDebitsTo(successFeesObs::set),
                        revokeTokenKyc(xToken, xCivilian),
                        scheduleCreate(
                                        invalidSchedule,
                                        cryptoTransfer(moving(1, xToken).between(xTreasury, xCivilian))
                                                .memo(randomUppercase(100)))
                                .via(failedTx)
                                .alsoSigningWith(xTreasury, schedulePayer)
                                .designatingPayer(schedulePayer))
                .then(
                        getTxnRecord(failedTx)
                                .scheduled()
                                .hasPriority(recordWith().status(ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN))
                                .revealingDebitsTo(failureFeesObs::set),
                        getAccountBalance(xTreasury).hasTokenBalance(xToken, 100),
                        assertionsHold((spec, opLog) ->
                                assertBasicallyIdentical(successFeesObs.get(), failureFeesObs.get(), 1.0)));
    }

    @HapiTest
    @Order(47)
    final Stream<DynamicTest> scheduledXferFailingWithUnassociatedAccountTransferPaysServiceFeeButNoImpact() {
        String xToken = "XXX";
        String validSchedule = "withAssociatedToken";
        String invalidSchedule = "withUnassociatedToken";
        String schedulePayer = PAYER;
        String xTreasury = "xt";
        String xCivilian = "xc";
        String nonXCivilian = "nxc";
        String successTx = "good";
        String failedTx = "bad";
        AtomicReference<Map<AccountID, Long>> successFeesObs = new AtomicReference<>();
        AtomicReference<Map<AccountID, Long>> failureFeesObs = new AtomicReference<>();

        return defaultHapiSpec("ScheduledXferFailingWithUnassociatedAccountTransferPaysServiceFeeButNoImpact")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(schedulePayer),
                        cryptoCreate(xTreasury),
                        cryptoCreate(xCivilian),
                        cryptoCreate(nonXCivilian),
                        tokenCreate(xToken).treasury(xTreasury).initialSupply(101),
                        tokenAssociate(xCivilian, xToken))
                .when(
                        scheduleCreate(
                                        validSchedule,
                                        cryptoTransfer(moving(1, xToken).between(xTreasury, xCivilian)))
                                .via(successTx)
                                .alsoSigningWith(xTreasury, schedulePayer)
                                .designatingPayer(schedulePayer),
                        getAccountBalance(xTreasury).hasTokenBalance(xToken, 100),
                        getAccountBalance(xCivilian).hasTokenBalance(xToken, 1),
                        getTxnRecord(successTx).scheduled().logged().revealingDebitsTo(successFeesObs::set),
                        scheduleCreate(
                                        invalidSchedule,
                                        cryptoTransfer(moving(1, xToken).between(xTreasury, nonXCivilian)))
                                .via(failedTx)
                                .alsoSigningWith(xTreasury, schedulePayer)
                                .designatingPayer(schedulePayer))
                .then(
                        getTxnRecord(failedTx)
                                .scheduled()
                                .hasPriority(recordWith().status(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT))
                                .revealingDebitsTo(failureFeesObs::set),
                        getAccountBalance(xTreasury).hasTokenBalance(xToken, 100),
                        getAccountInfo(nonXCivilian).hasNoTokenRelationship(xToken),
                        assertionsHold((spec, opLog) ->
                                assertBasicallyIdentical(successFeesObs.get(), failureFeesObs.get(), 1.0)));
    }

    @HapiTest
    @Order(45)
    final Stream<DynamicTest> scheduledXferFailingWithNonNetZeroTokenTransferPaysServiceFeeButNoImpact() {
        String xToken = "XXX";
        String validSchedule = "withZeroNetTokenChange";
        String invalidSchedule = "withNonZeroNetTokenChange";
        String schedulePayer = PAYER;
        String xTreasury = "xt";
        String xCivilian = "xc";
        String successTx = "good";
        String failedTx = "bad";
        AtomicReference<Map<AccountID, Long>> successFeesObs = new AtomicReference<>();
        AtomicReference<Map<AccountID, Long>> failureFeesObs = new AtomicReference<>();

        return defaultHapiSpec("ScheduledXferFailingWithRepeatedTokenIdPaysServiceFeeButNoImpact")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(schedulePayer),
                        cryptoCreate(xTreasury),
                        cryptoCreate(xCivilian),
                        tokenCreate(xToken).treasury(xTreasury).initialSupply(101),
                        tokenAssociate(xCivilian, xToken))
                .when(
                        scheduleCreate(
                                        validSchedule,
                                        cryptoTransfer(moving(1, xToken).between(xTreasury, xCivilian)))
                                .via(successTx)
                                .alsoSigningWith(xTreasury, schedulePayer)
                                .designatingPayer(schedulePayer),
                        getAccountBalance(xTreasury).hasTokenBalance(xToken, 100),
                        getAccountBalance(xCivilian).hasTokenBalance(xToken, 1),
                        getTxnRecord(successTx).scheduled().logged().revealingDebitsTo(successFeesObs::set))
                .then(
                        scheduleCreate(
                                        invalidSchedule,
                                        cryptoTransfer(moving(1, xToken).between(xTreasury, xCivilian))
                                                .breakingNetZeroInvariant())
                                .alsoSigningWith(xTreasury, schedulePayer)
                                .designatingPayer(schedulePayer)
                                .hasKnownStatus(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN),
                        getAccountBalance(xTreasury).hasTokenBalance(xToken, 100),
                        getAccountBalance(xCivilian).hasTokenBalance(xToken, 1));
    }

    @HapiTest
    @Order(46)
    final Stream<DynamicTest> scheduledXferFailingWithRepeatedTokenIdPaysServiceFeeButNoImpact() {
        String xToken = "XXX";
        String yToken = "YYY";
        String validSchedule = "withNoRepeats";
        String invalidSchedule = "withRepeats";
        String schedulePayer = PAYER;
        String xTreasury = "xt";
        String yTreasury = "yt";
        String successTx = "good";
        String failedTx = "bad";
        AtomicReference<Map<AccountID, Long>> successFeesObs = new AtomicReference<>();
        AtomicReference<Map<AccountID, Long>> failureFeesObs = new AtomicReference<>();

        return defaultHapiSpec("ScheduledXferFailingWithRepeatedTokenIdPaysServiceFeeButNoImpact")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(schedulePayer),
                        cryptoCreate(xTreasury),
                        cryptoCreate(yTreasury),
                        tokenCreate(xToken).treasury(xTreasury).initialSupply(101),
                        tokenCreate(yToken).treasury(yTreasury).initialSupply(101),
                        tokenAssociate(xTreasury, yToken),
                        tokenAssociate(yTreasury, xToken))
                .when(
                        scheduleCreate(
                                        validSchedule,
                                        cryptoTransfer(
                                                moving(1, xToken).between(xTreasury, yTreasury),
                                                moving(1, yToken).between(yTreasury, xTreasury)))
                                .via(successTx)
                                .alsoSigningWith(xTreasury, yTreasury, schedulePayer)
                                .designatingPayer(schedulePayer),
                        getAccountBalance(xTreasury).hasTokenBalance(xToken, 100),
                        getAccountBalance(xTreasury).hasTokenBalance(yToken, 1),
                        getAccountBalance(yTreasury).hasTokenBalance(yToken, 100),
                        getAccountBalance(yTreasury).hasTokenBalance(xToken, 1),
                        getTxnRecord(successTx).scheduled().logged().revealingDebitsTo(successFeesObs::set))
                .then(
                        scheduleCreate(
                                        invalidSchedule,
                                        cryptoTransfer(moving(1, xToken).between(xTreasury, yTreasury))
                                                .appendingTokenFromTo(xToken, xTreasury, yTreasury, 1))
                                .alsoSigningWith(xTreasury, schedulePayer)
                                .designatingPayer(schedulePayer)
                                .hasKnownStatus(TOKEN_ID_REPEATED_IN_TOKEN_LIST),
                        getAccountBalance(xTreasury).hasTokenBalance(xToken, 100),
                        getAccountBalance(xTreasury).hasTokenBalance(yToken, 1),
                        getAccountBalance(yTreasury).hasTokenBalance(yToken, 100),
                        getAccountBalance(yTreasury).hasTokenBalance(xToken, 1));
    }

    @HapiTest
    @Order(42)
    final Stream<DynamicTest> scheduledXferFailingWithEmptyTokenTransferAccountAmountsPaysServiceFeeButNoImpact() {
        String xToken = "XXX";
        String yToken = "YYY";
        String validSchedule = "withNonEmptyTransfers";
        String invalidSchedule = "withEmptyTransfer";
        String schedulePayer = PAYER;
        String xTreasury = "xt";
        String yTreasury = "yt";
        String xyCivilian = "xyt";
        String successTx = "good";
        String failedTx = "bad";
        AtomicReference<Map<AccountID, Long>> successFeesObs = new AtomicReference<>();
        AtomicReference<Map<AccountID, Long>> failureFeesObs = new AtomicReference<>();

        return defaultHapiSpec("ScheduledXferFailingWithEmptyTokenTransferAccountAmountsPaysServiceFeeButNoImpact")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(schedulePayer),
                        cryptoCreate(xTreasury),
                        cryptoCreate(yTreasury),
                        cryptoCreate(xyCivilian),
                        tokenCreate(xToken).treasury(xTreasury).initialSupply(101),
                        tokenCreate(yToken).treasury(yTreasury).initialSupply(101),
                        tokenAssociate(xTreasury, yToken),
                        tokenAssociate(yTreasury, xToken))
                .when(
                        scheduleCreate(
                                        validSchedule,
                                        cryptoTransfer(
                                                moving(1, xToken).between(xTreasury, yTreasury),
                                                moving(1, yToken).between(yTreasury, xTreasury)))
                                .via(successTx)
                                .alsoSigningWith(xTreasury, yTreasury, schedulePayer)
                                .designatingPayer(schedulePayer),
                        getAccountBalance(xTreasury).hasTokenBalance(xToken, 100),
                        getAccountBalance(xTreasury).hasTokenBalance(yToken, 1),
                        getAccountBalance(yTreasury).hasTokenBalance(yToken, 100),
                        getAccountBalance(yTreasury).hasTokenBalance(xToken, 1),
                        getTxnRecord(successTx).scheduled().logged().revealingDebitsTo(successFeesObs::set))
                .then(
                        scheduleCreate(
                                        invalidSchedule,
                                        cryptoTransfer(moving(2, xToken).distributing(xTreasury, yTreasury, xyCivilian))
                                                .withEmptyTokenTransfers(yToken))
                                .alsoSigningWith(xTreasury, yTreasury, schedulePayer)
                                .designatingPayer(schedulePayer)
                                .hasKnownStatus(EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS),
                        getAccountBalance(xTreasury).hasTokenBalance(xToken, 100),
                        getAccountBalance(xTreasury).hasTokenBalance(yToken, 1),
                        getAccountBalance(yTreasury).hasTokenBalance(yToken, 100),
                        getAccountBalance(yTreasury).hasTokenBalance(xToken, 1));
    }

    @HapiTest
    @Order(29)
    final Stream<DynamicTest> scheduledSubmitFailedWithMsgSizeTooLargeStillPaysServiceFeeButHasNoImpact() {
        String immutableTopic = "XXX";
        String validSchedule = "withValidSize";
        String invalidSchedule = "withInvalidSize";
        String schedulePayer = PAYER;
        String successTx = "good";
        String failedTx = "bad";
        AtomicReference<Map<AccountID, Long>> successFeesObs = new AtomicReference<>();
        AtomicReference<Map<AccountID, Long>> failureFeesObs = new AtomicReference<>();

        return defaultHapiSpec("ScheduledSubmitFailedWithMsgSizeTooLargeStillPaysServiceFeeButHasNoImpact")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        createTopic(immutableTopic),
                        cryptoCreate(schedulePayer))
                .when(doSeveralWithStartupConfig("consensus.message.maxBytesAllowed", value -> {
                    final var maxValidLen = parseInt(value);
                    return new SpecOperation[] {
                        scheduleCreate(
                                        validSchedule,
                                        submitMessageTo(immutableTopic).message(randomUppercase(maxValidLen)))
                                .designatingPayer(schedulePayer)
                                .via(successTx)
                                .signedBy(DEFAULT_PAYER, schedulePayer),
                        getTopicInfo(immutableTopic).hasSeqNo(1L),
                        getTxnRecord(successTx).scheduled().logged().revealingDebitsTo(successFeesObs::set),
                        scheduleCreate(
                                        invalidSchedule,
                                        submitMessageTo(immutableTopic).message(randomUppercase(maxValidLen + 1)))
                                .designatingPayer(schedulePayer)
                                .via(failedTx)
                                .signedBy(DEFAULT_PAYER, schedulePayer)
                    };
                }))
                .then(
                        getTopicInfo(immutableTopic).hasSeqNo(1L),
                        getTxnRecord(failedTx)
                                .scheduled()
                                .hasPriority(recordWith().status(MESSAGE_SIZE_TOO_LARGE))
                                .revealingDebitsTo(failureFeesObs::set),
                        assertionsHold((spec, opLog) ->
                                assertBasicallyIdentical(successFeesObs.get(), failureFeesObs.get(), 1.0)));
    }

    @HapiTest
    @Order(28)
    final Stream<DynamicTest> scheduledSubmitFailedWithInvalidChunkTxnIdStillPaysServiceFeeButHasNoImpact() {
        String immutableTopic = "XXX";
        String validSchedule = "withValidChunkTxnId";
        String invalidSchedule = "withInvalidChunkTxnId";
        String schedulePayer = PAYER;
        String successTx = "good";
        String failedTx = "bad";
        AtomicReference<Map<AccountID, Long>> successFeesObs = new AtomicReference<>();
        AtomicReference<Map<AccountID, Long>> failureFeesObs = new AtomicReference<>();
        AtomicReference<TransactionID> initialTxnId = new AtomicReference<>();
        AtomicReference<TransactionID> irrelevantTxnId = new AtomicReference<>();

        return defaultHapiSpec("ScheduledSubmitFailedWithInvalidChunkTxnIdStillPaysServiceFeeButHasNoImpact")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        createTopic(immutableTopic),
                        cryptoCreate(schedulePayer))
                .when(
                        withOpContext((spec, opLog) -> {
                            var subOp = usableTxnIdNamed(successTx).payerId(schedulePayer);
                            var secondSubOp = usableTxnIdNamed("wontWork").payerId(schedulePayer);
                            allRunFor(spec, subOp, secondSubOp);
                            initialTxnId.set(spec.registry().getTxnId(successTx));
                            irrelevantTxnId.set(spec.registry().getTxnId("wontWork"));
                        }),
                        sourcing(() -> scheduleCreate(
                                        validSchedule,
                                        submitMessageTo(immutableTopic)
                                                .chunkInfo(3, 1, scheduledVersionOf(initialTxnId.get())))
                                .txnId(successTx)
                                .logged()
                                .signedBy(schedulePayer)),
                        getTopicInfo(immutableTopic).hasSeqNo(1L),
                        getTxnRecord(successTx).scheduled().logged().revealingDebitsTo(successFeesObs::set),
                        sourcing(() -> scheduleCreate(
                                        invalidSchedule,
                                        submitMessageTo(immutableTopic)
                                                .chunkInfo(3, 1, scheduledVersionOf(irrelevantTxnId.get())))
                                .designatingPayer(schedulePayer)
                                .via(failedTx)
                                .logged()
                                .signedBy(DEFAULT_PAYER, schedulePayer)))
                .then(
                        getTopicInfo(immutableTopic).hasSeqNo(1L),
                        getTxnRecord(failedTx)
                                .scheduled()
                                .hasPriority(recordWith().status(INVALID_CHUNK_TRANSACTION_ID))
                                .revealingDebitsTo(failureFeesObs::set),
                        assertionsHold(
                                (spec, opLog) -> Assertions.assertEquals(successFeesObs.get(), failureFeesObs.get())));
    }

    @HapiTest
    @Order(27)
    final Stream<DynamicTest> scheduledSubmitFailedWithInvalidChunkNumberStillPaysServiceFeeButHasNoImpact() {
        String immutableTopic = "XXX";
        String validSchedule = "withValidChunkNumber";
        String invalidSchedule = "withInvalidChunkNumber";
        String schedulePayer = PAYER;
        String successTx = "good";
        String failedTx = "bad";
        AtomicReference<Map<AccountID, Long>> successFeesObs = new AtomicReference<>();
        AtomicReference<Map<AccountID, Long>> failureFeesObs = new AtomicReference<>();
        AtomicReference<TransactionID> initialTxnId = new AtomicReference<>();

        return defaultHapiSpec("ScheduledSubmitFailedWithInvalidChunkNumberStillPaysServiceFeeButHasNoImpact")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        createTopic(immutableTopic),
                        cryptoCreate(schedulePayer))
                .when(
                        withOpContext((spec, opLog) -> {
                            var subOp = usableTxnIdNamed(successTx).payerId(schedulePayer);
                            allRunFor(spec, subOp);
                            initialTxnId.set(spec.registry().getTxnId(successTx));
                        }),
                        sourcing(() -> scheduleCreate(
                                        validSchedule,
                                        submitMessageTo(immutableTopic)
                                                .chunkInfo(3, 1, scheduledVersionOf(initialTxnId.get())))
                                .txnId(successTx)
                                .logged()
                                .signedBy(schedulePayer)),
                        getTopicInfo(immutableTopic).hasSeqNo(1L),
                        getTxnRecord(successTx).scheduled().logged().revealingDebitsTo(successFeesObs::set),
                        scheduleCreate(
                                        invalidSchedule,
                                        submitMessageTo(immutableTopic).chunkInfo(3, 111, schedulePayer))
                                .via(failedTx)
                                .logged()
                                .payingWith(schedulePayer))
                .then(
                        getTopicInfo(immutableTopic).hasSeqNo(1L),
                        getTxnRecord(failedTx)
                                .scheduled()
                                .hasPriority(recordWith().status(INVALID_CHUNK_NUMBER))
                                .revealingDebitsTo(failureFeesObs::set),
                        assertionsHold(
                                (spec, opLog) -> Assertions.assertEquals(successFeesObs.get(), failureFeesObs.get())));
    }

    @HapiTest
    @Order(30)
    final Stream<DynamicTest> scheduledSubmitThatWouldFailWithInvalidTopicIdCannotBeScheduled() {
        String civilianPayer = PAYER;
        AtomicReference<Map<AccountID, Long>> successFeesObs = new AtomicReference<>();
        AtomicReference<Map<AccountID, Long>> failureFeesObs = new AtomicReference<>();

        return defaultHapiSpec("ScheduledSubmitThatWouldFailWithInvalidTopicIdCannotBeScheduled")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(civilianPayer),
                        createTopic("fascinating"))
                .when(
                        scheduleCreate("yup", submitMessageTo("fascinating").message(RANDOM_MSG))
                                .payingWith(civilianPayer)
                                .via(CREATION),
                        scheduleCreate("nope", submitMessageTo("1.2.3").message(RANDOM_MSG))
                                .payingWith(civilianPayer)
                                .via("nothingShouldBeCreated")
                                .hasKnownStatus(UNRESOLVABLE_REQUIRED_SIGNERS))
                .then(
                        getTxnRecord(CREATION).revealingDebitsTo(successFeesObs::set),
                        getTxnRecord("nothingShouldBeCreated")
                                .revealingDebitsTo(failureFeesObs::set)
                                .logged(),
                        assertionsHold((spec, opLog) ->
                                assertBasicallyIdentical(successFeesObs.get(), failureFeesObs.get(), 1.0)));
    }

    private void assertBasicallyIdentical(
            Map<AccountID, Long> aFees, Map<AccountID, Long> bFees, double allowedPercentDeviation) {
        Assertions.assertEquals(aFees.keySet(), bFees.keySet());
        for (var id : aFees.keySet()) {
            long a = aFees.get(id);
            long b = bFees.get(id);
            Assertions.assertEquals(100.0, (1.0 * a) / b * 100.0, allowedPercentDeviation);
        }
    }

    @HapiTest
    @Order(31)
    final Stream<DynamicTest> scheduledSubmitThatWouldFailWithTopicDeletedCannotBeSigned() {
        String adminKey = ADMIN;
        String mutableTopic = "XXX";
        String postDeleteSchedule = "deferredTooLongSubmitMsg";
        String schedulePayer = PAYER;
        String failedTxn = "deleted";

        return defaultHapiSpec("ScheduledSubmitThatWouldFailWithTopicDeletedCannotBeSigned")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        newKeyNamed(adminKey),
                        createTopic(mutableTopic).adminKeyName(adminKey),
                        cryptoCreate(schedulePayer),
                        scheduleCreate(
                                        postDeleteSchedule,
                                        submitMessageTo(mutableTopic).message(RANDOM_MSG))
                                .designatingPayer(schedulePayer)
                                .payingWith(DEFAULT_PAYER)
                                .via(failedTxn))
                .when(deleteTopic(mutableTopic))
                .then(scheduleSign(postDeleteSchedule)
                        .alsoSigningWith(schedulePayer)
                        .hasKnownStatus(UNRESOLVABLE_REQUIRED_SIGNERS));
    }

    @HapiTest
    @Order(2)
    final Stream<DynamicTest> executionTriggersOnceTopicHasSatisfiedSubmitKey() {
        String adminKey = ADMIN;
        String submitKey = "submit";
        String mutableTopic = "XXX";
        String schedule = "deferredSubmitMsg";

        return defaultHapiSpec("ExecutionTriggersOnceTopicHasNoSubmitKey")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        newKeyNamed(adminKey),
                        newKeyNamed(submitKey),
                        createTopic(mutableTopic).adminKeyName(adminKey).submitKeyName(submitKey),
                        cryptoCreate(PAYER),
                        scheduleCreate(schedule, submitMessageTo(mutableTopic).message(RANDOM_MSG))
                                .designatingPayer(PAYER)
                                .payingWith(DEFAULT_PAYER)
                                .alsoSigningWith(PAYER)
                                .via(CREATION),
                        getTopicInfo(mutableTopic).hasSeqNo(0L))
                .when(
                        scheduleSign(schedule)
                                .alsoSigningWith(adminKey)
                                /* In the rare, but possible, case that the adminKey and submitKey keys overlap
                                 * in their first byte (and that byte is not shared by the DEFAULT_PAYER),
                                 * we will get SOME_SIGNATURES_WERE_INVALID instead of NO_NEW_VALID_SIGNATURES.
                                 *
                                 * So we need this to stabilize CI. But if just testing locally, you may
                                 * only use .hasKnownStatus(NO_NEW_VALID_SIGNATURES) and it will pass
                                 * >99.99% of the time. */
                                .hasKnownStatusFrom(NO_NEW_VALID_SIGNATURES, SOME_SIGNATURES_WERE_INVALID),
                        updateTopic(mutableTopic).submitKey(PAYER),
                        scheduleSign(schedule))
                .then(
                        getScheduleInfo(schedule).isExecuted(),
                        getTopicInfo(mutableTopic).hasSeqNo(1L));
    }

    @HapiTest
    @Order(3)
    final Stream<DynamicTest> executionTriggersWithWeirdlyRepeatedKey() {
        String schedule = "dupKeyXfer";

        return defaultHapiSpec("ExecutionTriggersWithWeirdlyRepeatedKey")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(WEIRDLY_POPULAR_KEY),
                        cryptoCreate(SENDER_1).key(WEIRDLY_POPULAR_KEY).balance(1L),
                        cryptoCreate(SENDER_2).key(WEIRDLY_POPULAR_KEY).balance(1L),
                        cryptoCreate(SENDER_3).key(WEIRDLY_POPULAR_KEY).balance(1L),
                        cryptoCreate(RECEIVER).balance(0L),
                        scheduleCreate(
                                        schedule,
                                        cryptoTransfer(
                                                tinyBarsFromTo(SENDER_1, RECEIVER, 1L),
                                                tinyBarsFromTo(SENDER_2, RECEIVER, 1L),
                                                tinyBarsFromTo(SENDER_3, RECEIVER, 1L)))
                                .payingWith(DEFAULT_PAYER)
                                .via(CREATION))
                .when(scheduleSign(schedule).alsoSigningWith(WEIRDLY_POPULAR_KEY))
                .then(
                        getScheduleInfo(schedule).isExecuted(),
                        getAccountBalance(SENDER_1).hasTinyBars(0L),
                        getAccountBalance(SENDER_2).hasTinyBars(0L),
                        getAccountBalance(SENDER_3).hasTinyBars(0L),
                        getAccountBalance(RECEIVER).hasTinyBars(3L),
                        scheduleSign(schedule)
                                .via("repeatedSigning")
                                .alsoSigningWith(WEIRDLY_POPULAR_KEY)
                                .hasKnownStatus(SCHEDULE_ALREADY_EXECUTED),
                        getTxnRecord("repeatedSigning").logged());
    }

    @HapiTest
    @Order(14)
    final Stream<DynamicTest> executionWithDefaultPayerWorks() {
        long transferAmount = 1;
        return defaultHapiSpec("ExecutionWithDefaultPayerWorks")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(SENDER),
                        cryptoCreate(RECEIVER),
                        cryptoCreate(PAYING_ACCOUNT),
                        scheduleCreate(BASIC_XFER, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, transferAmount)))
                                .payingWith(PAYING_ACCOUNT)
                                .via(CREATE_TXN))
                .when(scheduleSign(BASIC_XFER).alsoSigningWith(SENDER).via(SIGN_TXN))
                .then(withOpContext((spec, opLog) -> {
                    var createTx = getTxnRecord(CREATE_TXN);
                    var signTx = getTxnRecord(SIGN_TXN).logged();
                    var triggeredTx = getTxnRecord(CREATE_TXN).scheduled();
                    allRunFor(spec, createTx, signTx, triggeredTx);

                    Assertions.assertEquals(
                            signTx.getResponseRecord().getConsensusTimestamp().getNanos()
                                    + normalTriggeredTxnTimestampOffset,
                            triggeredTx
                                    .getResponseRecord()
                                    .getConsensusTimestamp()
                                    .getNanos(),
                            WRONG_CONSENSUS_TIMESTAMP);

                    Assertions.assertEquals(
                            createTx.getResponseRecord().getTransactionID().getTransactionValidStart(),
                            triggeredTx.getResponseRecord().getTransactionID().getTransactionValidStart(),
                            WRONG_TRANSACTION_VALID_START);

                    Assertions.assertEquals(
                            createTx.getResponseRecord().getTransactionID().getAccountID(),
                            triggeredTx.getResponseRecord().getTransactionID().getAccountID(),
                            WRONG_RECORD_ACCOUNT_ID);

                    Assertions.assertTrue(
                            triggeredTx.getResponseRecord().getTransactionID().getScheduled(),
                            TRANSACTION_NOT_SCHEDULED);

                    Assertions.assertEquals(
                            createTx.getResponseRecord().getReceipt().getScheduleID(),
                            triggeredTx.getResponseRecord().getScheduleRef(),
                            WRONG_SCHEDULE_ID);

                    Assertions.assertTrue(
                            transferListCheck(
                                    triggeredTx,
                                    asId(SENDER, spec),
                                    asId(RECEIVER, spec),
                                    asId(PAYING_ACCOUNT, spec),
                                    transferAmount),
                            WRONG_TRANSFER_LIST);
                }));
    }

    @HapiTest
    @Order(13)
    final Stream<DynamicTest> executionWithDefaultPayerButNoFundsFails() {
        long balance = 10_000_000L;
        long noBalance = 0L;
        long transferAmount = 1L;
        return defaultHapiSpec("ExecutionWithDefaultPayerButNoFundsFails")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(PAYING_ACCOUNT).balance(balance),
                        cryptoCreate(LUCKY_RECEIVER),
                        cryptoCreate(SENDER).balance(transferAmount),
                        cryptoCreate(RECEIVER).balance(noBalance),
                        scheduleCreate(BASIC_XFER, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, transferAmount)))
                                .payingWith(PAYING_ACCOUNT)
                                .via(CREATE_TXN),
                        recordFeeAmount(CREATE_TXN, SCHEDULE_CREATE_FEE))
                .when(
                        cryptoTransfer(tinyBarsFromTo(PAYING_ACCOUNT, LUCKY_RECEIVER, (spec -> {
                            long scheduleCreateFee = spec.registry().getAmount(SCHEDULE_CREATE_FEE);
                            return balance - scheduleCreateFee;
                        }))),
                        getAccountBalance(PAYING_ACCOUNT).hasTinyBars(noBalance),
                        scheduleSign(BASIC_XFER).alsoSigningWith(SENDER).hasKnownStatus(SUCCESS))
                .then(
                        getAccountBalance(SENDER).hasTinyBars(transferAmount),
                        getAccountBalance(RECEIVER).hasTinyBars(noBalance),
                        withOpContext((spec, opLog) -> {
                            var triggeredTx = getTxnRecord(CREATE_TXN).scheduled();

                            allRunFor(spec, triggeredTx);

                            Assertions.assertEquals(
                                    INSUFFICIENT_PAYER_BALANCE,
                                    triggeredTx.getResponseRecord().getReceipt().getStatus(),
                                    SCHEDULED_TRANSACTION_MUST_NOT_SUCCEED);
                        }));
    }

    @HapiTest
    @Order(11)
    final Stream<DynamicTest> executionWithCustomPayerWorksWithLastSigBeingCustomPayer() {
        long noBalance = 0L;
        long transferAmount = 1;
        return defaultHapiSpec("ExecutionWithCustomPayerWorksWithLastSigBeingCustomPayer")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(PAYING_ACCOUNT),
                        cryptoCreate(SENDER).balance(transferAmount),
                        cryptoCreate(RECEIVER).balance(noBalance),
                        scheduleCreate(BASIC_XFER, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, transferAmount)))
                                .designatingPayer(PAYING_ACCOUNT)
                                .via(CREATE_TXN))
                .when(scheduleSign(BASIC_XFER)
                        .alsoSigningWith(SENDER)
                        .via(SIGN_TXN)
                        .hasKnownStatus(SUCCESS))
                .then(
                        getAccountBalance(SENDER).hasTinyBars(transferAmount),
                        getAccountBalance(RECEIVER).hasTinyBars(noBalance),
                        scheduleSign(BASIC_XFER)
                                .alsoSigningWith(PAYING_ACCOUNT)
                                .via(SIGN_TXN)
                                .hasKnownStatus(SUCCESS),
                        withOpContext((spec, opLog) -> {
                            var triggeredTx = getTxnRecord(CREATE_TXN).scheduled();

                            allRunFor(spec, triggeredTx);

                            Assertions.assertEquals(
                                    SUCCESS,
                                    triggeredTx.getResponseRecord().getReceipt().getStatus(),
                                    SCHEDULED_TRANSACTION_MUST_NOT_SUCCEED);
                        }),
                        getAccountBalance(SENDER).hasTinyBars(noBalance),
                        getAccountBalance(RECEIVER).hasTinyBars(transferAmount));
    }

    @HapiTest
    @Order(8)
    final Stream<DynamicTest> executionWithCustomPayerButNoFundsFails() {
        long balance = 0L;
        long noBalance = 0L;
        long transferAmount = 1;
        return defaultHapiSpec("ExecutionWithCustomPayerButNoFundsFails")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(PAYING_ACCOUNT).balance(balance),
                        cryptoCreate(SENDER).balance(transferAmount),
                        cryptoCreate(RECEIVER).balance(noBalance),
                        scheduleCreate(BASIC_XFER, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, transferAmount)))
                                .designatingPayer(PAYING_ACCOUNT)
                                .via(CREATE_TXN))
                .when(scheduleSign(BASIC_XFER)
                        .alsoSigningWith(SENDER, PAYING_ACCOUNT)
                        .via(SIGN_TXN)
                        .hasKnownStatus(SUCCESS))
                .then(
                        getAccountBalance(SENDER).hasTinyBars(transferAmount),
                        getAccountBalance(RECEIVER).hasTinyBars(noBalance),
                        withOpContext((spec, opLog) -> {
                            var triggeredTx = getTxnRecord(CREATE_TXN).scheduled();

                            allRunFor(spec, triggeredTx);

                            Assertions.assertEquals(
                                    INSUFFICIENT_PAYER_BALANCE,
                                    triggeredTx.getResponseRecord().getReceipt().getStatus(),
                                    SCHEDULED_TRANSACTION_MUST_NOT_SUCCEED);
                        }));
    }

    @HapiTest
    @Order(12)
    final Stream<DynamicTest> executionWithDefaultPayerButAccountDeletedFails() {
        long balance = 10_000_000L;
        long noBalance = 0L;
        long transferAmount = 1L;
        return defaultHapiSpec("ExecutionWithDefaultPayerButAccountDeletedFails")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(PAYING_ACCOUNT).balance(balance),
                        cryptoCreate(LUCKY_RECEIVER),
                        cryptoCreate(SENDER).balance(transferAmount),
                        cryptoCreate(RECEIVER).balance(noBalance),
                        scheduleCreate(BASIC_XFER, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, transferAmount)))
                                .payingWith(PAYING_ACCOUNT)
                                .via(CREATE_TXN),
                        recordFeeAmount(CREATE_TXN, SCHEDULE_CREATE_FEE))
                .when(
                        cryptoDelete(PAYING_ACCOUNT),
                        scheduleSign(BASIC_XFER).alsoSigningWith(SENDER).hasKnownStatus(SUCCESS))
                .then(
                        getScheduleInfo(BASIC_XFER).isExecuted(),
                        getTxnRecord(CREATE_TXN)
                                .scheduled()
                                .hasPriority(
                                        recordWith().statusFrom(INSUFFICIENT_PAYER_BALANCE, PAYER_ACCOUNT_DELETED)));
    }

    @Order(7)
    @HapiTest
    final Stream<DynamicTest> executionWithCustomPayerButAccountDeletedFails() {
        long balance = 10_000_000L;
        long noBalance = 0L;
        long transferAmount = 1;
        return defaultHapiSpec("ExecutionWithCustomPayerButAccountDeletedFails")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(PAYING_ACCOUNT).balance(balance),
                        cryptoCreate(SENDER).balance(transferAmount),
                        cryptoCreate(RECEIVER).balance(noBalance),
                        scheduleCreate(BASIC_XFER, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, transferAmount)))
                                .designatingPayer(PAYING_ACCOUNT)
                                .alsoSigningWith(PAYING_ACCOUNT)
                                .via(CREATE_TXN))
                .when(
                        cryptoDelete(PAYING_ACCOUNT),
                        scheduleSign(BASIC_XFER)
                                .alsoSigningWith(SENDER)
                                .via(SIGN_TXN)
                                .hasKnownStatus(SUCCESS))
                .then(
                        getAccountBalance(SENDER).hasTinyBars(transferAmount),
                        getAccountBalance(RECEIVER).hasTinyBars(noBalance),
                        getScheduleInfo(BASIC_XFER).isExecuted(),
                        withOpContext((spec, opLog) -> {
                            var triggeredTx = getTxnRecord(CREATE_TXN).scheduled();

                            allRunFor(spec, triggeredTx);

                            final var failureReasons = EnumSet.of(INSUFFICIENT_PAYER_BALANCE, PAYER_ACCOUNT_DELETED);
                            Assertions.assertTrue(
                                    failureReasons.contains(triggeredTx
                                            .getResponseRecord()
                                            .getReceipt()
                                            .getStatus()),
                                    SCHEDULED_TRANSACTION_MUST_NOT_SUCCEED + " for one of reasons " + failureReasons);
                        }));
    }

    @HapiTest
    @Order(4)
    final Stream<DynamicTest> executionWithCryptoInsufficientAccountBalanceFails() {
        long noBalance = 0L;
        long senderBalance = 100L;
        long transferAmount = 101L;
        long payerBalance = 1_000_000L;
        return defaultHapiSpec("ExecutionWithCryptoInsufficientAccountBalanceFails")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(PAYING_ACCOUNT).balance(payerBalance),
                        cryptoCreate(SENDER).balance(senderBalance),
                        cryptoCreate(RECEIVER).balance(noBalance),
                        scheduleCreate(FAILED_XFER, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, transferAmount)))
                                .designatingPayer(PAYING_ACCOUNT)
                                .via(CREATE_TXN))
                .when(scheduleSign(FAILED_XFER)
                        .alsoSigningWith(SENDER, PAYING_ACCOUNT)
                        .via(SIGN_TXN)
                        .hasKnownStatus(SUCCESS))
                .then(
                        getAccountBalance(SENDER).hasTinyBars(senderBalance),
                        getAccountBalance(RECEIVER).hasTinyBars(noBalance),
                        withOpContext((spec, opLog) -> {
                            var triggeredTx = getTxnRecord(CREATE_TXN).scheduled();

                            allRunFor(spec, triggeredTx);

                            Assertions.assertEquals(
                                    INSUFFICIENT_ACCOUNT_BALANCE,
                                    triggeredTx.getResponseRecord().getReceipt().getStatus(),
                                    SCHEDULED_TRANSACTION_MUST_NOT_SUCCEED);
                        }));
    }

    @HapiTest
    @Order(5)
    final Stream<DynamicTest> executionWithCryptoSenderDeletedFails() {
        long noBalance = 0L;
        long senderBalance = 100L;
        long transferAmount = 101L;
        long payerBalance = 1_000_000L;
        return defaultHapiSpec("ExecutionWithCryptoSenderDeletedFails")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(PAYING_ACCOUNT).balance(payerBalance),
                        cryptoCreate(SENDER).balance(senderBalance),
                        cryptoCreate(RECEIVER).balance(noBalance),
                        scheduleCreate(FAILED_XFER, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, transferAmount)))
                                .designatingPayer(PAYING_ACCOUNT)
                                .via(CREATE_TXN))
                .when(
                        cryptoDelete(SENDER),
                        scheduleSign(FAILED_XFER)
                                .alsoSigningWith(SENDER, PAYING_ACCOUNT)
                                .via(SIGN_TXN)
                                .hasKnownStatus(SUCCESS))
                .then(
                        getAccountBalance(RECEIVER).hasTinyBars(noBalance),
                        getScheduleInfo(FAILED_XFER).isExecuted(),
                        withOpContext((spec, opLog) -> {
                            var triggeredTx = getTxnRecord(CREATE_TXN).scheduled();

                            allRunFor(spec, triggeredTx);

                            Assertions.assertEquals(
                                    ACCOUNT_DELETED,
                                    triggeredTx.getResponseRecord().getReceipt().getStatus(),
                                    SCHEDULED_TRANSACTION_MUST_NOT_SUCCEED);
                        }));
    }

    @HapiTest
    @Order(16)
    final Stream<DynamicTest> executionWithTokenInsufficientAccountBalanceFails() {
        String xToken = "XXX";
        String invalidSchedule = "withInsufficientTokenTransfer";
        String schedulePayer = PAYER;
        String xTreasury = "xt";
        String civilian = "xa";
        String failedTxn = "bad";
        return defaultHapiSpec("ExecutionWithTokenInsufficientAccountBalanceFails")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        newKeyNamed(ADMIN),
                        cryptoCreate(schedulePayer),
                        cryptoCreate(xTreasury),
                        cryptoCreate(civilian),
                        tokenCreate(xToken)
                                .treasury(xTreasury)
                                .initialSupply(100)
                                .adminKey(ADMIN),
                        tokenAssociate(civilian, xToken))
                .when(scheduleCreate(
                                invalidSchedule,
                                cryptoTransfer(moving(101, xToken).between(xTreasury, civilian))
                                        .memo(randomUppercase(100)))
                        .via(failedTxn)
                        .alsoSigningWith(xTreasury, schedulePayer)
                        .designatingPayer(schedulePayer))
                .then(
                        getTxnRecord(failedTxn)
                                .scheduled()
                                .hasPriority(recordWith().status(INSUFFICIENT_TOKEN_BALANCE)),
                        getAccountBalance(xTreasury).hasTokenBalance(xToken, 100));
    }

    @HapiTest
    @Order(15)
    final Stream<DynamicTest> executionWithInvalidAccountAmountsFails() {
        long transferAmount = 100;
        long senderBalance = 1000L;
        long payingAccountBalance = 1_000_000L;
        long noBalance = 0L;
        return defaultHapiSpec("ExecutionWithInvalidAccountAmountsFails")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(PAYING_ACCOUNT).balance(payingAccountBalance),
                        cryptoCreate(SENDER).balance(senderBalance),
                        cryptoCreate(RECEIVER).balance(noBalance))
                .when()
                .then(
                        scheduleCreate(
                                        FAILED_XFER,
                                        cryptoTransfer(
                                                tinyBarsFromToWithInvalidAmounts(SENDER, RECEIVER, transferAmount)))
                                .designatingPayer(PAYING_ACCOUNT)
                                .hasKnownStatus(INVALID_ACCOUNT_AMOUNTS),
                        getAccountBalance(SENDER).hasTinyBars(senderBalance),
                        getAccountBalance(RECEIVER).hasTinyBars(noBalance));
    }

    @HapiTest
    @Order(10)
    final Stream<DynamicTest> executionWithCustomPayerWorks() {
        long transferAmount = 1;
        return defaultHapiSpec("ExecutionWithCustomPayerWorks")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(PAYING_ACCOUNT),
                        cryptoCreate(SENDER),
                        cryptoCreate(RECEIVER),
                        scheduleCreate(BASIC_XFER, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, transferAmount)))
                                .designatingPayer(PAYING_ACCOUNT)
                                .via(CREATE_TXN))
                .when(scheduleSign(BASIC_XFER)
                        .alsoSigningWith(SENDER, PAYING_ACCOUNT)
                        .via(SIGN_TXN)
                        .hasKnownStatus(SUCCESS))
                .then(withOpContext((spec, opLog) -> {
                    var createTx = getTxnRecord(CREATE_TXN);
                    var signTx = getTxnRecord(SIGN_TXN);
                    var triggeredTx = getTxnRecord(CREATE_TXN).scheduled();
                    allRunFor(spec, createTx, signTx, triggeredTx);

                    Assertions.assertEquals(
                            SUCCESS,
                            triggeredTx.getResponseRecord().getReceipt().getStatus(),
                            SCHEDULED_TRANSACTION_MUST_SUCCEED);

                    Assertions.assertEquals(
                            signTx.getResponseRecord().getConsensusTimestamp().getNanos()
                                    + normalTriggeredTxnTimestampOffset,
                            triggeredTx
                                    .getResponseRecord()
                                    .getConsensusTimestamp()
                                    .getNanos(),
                            WRONG_CONSENSUS_TIMESTAMP);

                    Assertions.assertEquals(
                            createTx.getResponseRecord().getTransactionID().getTransactionValidStart(),
                            triggeredTx.getResponseRecord().getTransactionID().getTransactionValidStart(),
                            WRONG_TRANSACTION_VALID_START);

                    Assertions.assertEquals(
                            createTx.getResponseRecord().getTransactionID().getAccountID(),
                            triggeredTx.getResponseRecord().getTransactionID().getAccountID(),
                            WRONG_RECORD_ACCOUNT_ID);

                    Assertions.assertTrue(
                            triggeredTx.getResponseRecord().getTransactionID().getScheduled(),
                            TRANSACTION_NOT_SCHEDULED);

                    Assertions.assertEquals(
                            createTx.getResponseRecord().getReceipt().getScheduleID(),
                            triggeredTx.getResponseRecord().getScheduleRef(),
                            WRONG_SCHEDULE_ID);

                    Assertions.assertTrue(
                            transferListCheck(
                                    triggeredTx,
                                    asId(SENDER, spec),
                                    asId(RECEIVER, spec),
                                    asId(PAYING_ACCOUNT, spec),
                                    transferAmount),
                            WRONG_TRANSFER_LIST);
                }));
    }

    @HapiTest
    @Order(6)
    final Stream<DynamicTest> executionWithCustomPayerAndAdminKeyWorks() {
        long transferAmount = 1;
        return defaultHapiSpec("ExecutionWithCustomPayerAndAdminKeyWorks")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        newKeyNamed("adminKey"),
                        cryptoCreate(PAYING_ACCOUNT),
                        cryptoCreate(SENDER),
                        cryptoCreate(RECEIVER),
                        scheduleCreate(BASIC_XFER, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, transferAmount)))
                                .adminKey("adminKey")
                                .designatingPayer(PAYING_ACCOUNT)
                                .via(CREATE_TXN))
                .when(scheduleSign(BASIC_XFER)
                        .alsoSigningWith(SENDER, PAYING_ACCOUNT)
                        .via(SIGN_TXN)
                        .hasKnownStatus(SUCCESS))
                .then(withOpContext((spec, opLog) -> {
                    var createTx = getTxnRecord(CREATE_TXN);
                    var signTx = getTxnRecord(SIGN_TXN);
                    var triggeredTx = getTxnRecord(CREATE_TXN).scheduled();
                    allRunFor(spec, createTx, signTx, triggeredTx);

                    Assertions.assertEquals(
                            SUCCESS,
                            triggeredTx.getResponseRecord().getReceipt().getStatus(),
                            SCHEDULED_TRANSACTION_MUST_SUCCEED);

                    Assertions.assertEquals(
                            signTx.getResponseRecord().getConsensusTimestamp().getNanos()
                                    + normalTriggeredTxnTimestampOffset,
                            triggeredTx
                                    .getResponseRecord()
                                    .getConsensusTimestamp()
                                    .getNanos(),
                            WRONG_CONSENSUS_TIMESTAMP);

                    Assertions.assertEquals(
                            createTx.getResponseRecord().getTransactionID().getTransactionValidStart(),
                            triggeredTx.getResponseRecord().getTransactionID().getTransactionValidStart(),
                            WRONG_TRANSACTION_VALID_START);

                    Assertions.assertEquals(
                            createTx.getResponseRecord().getTransactionID().getAccountID(),
                            triggeredTx.getResponseRecord().getTransactionID().getAccountID(),
                            WRONG_RECORD_ACCOUNT_ID);

                    Assertions.assertTrue(
                            triggeredTx.getResponseRecord().getTransactionID().getScheduled(),
                            TRANSACTION_NOT_SCHEDULED);

                    Assertions.assertEquals(
                            createTx.getResponseRecord().getReceipt().getScheduleID(),
                            triggeredTx.getResponseRecord().getScheduleRef(),
                            WRONG_SCHEDULE_ID);

                    Assertions.assertTrue(
                            transferListCheck(
                                    triggeredTx,
                                    asId(SENDER, spec),
                                    asId(RECEIVER, spec),
                                    asId(PAYING_ACCOUNT, spec),
                                    transferAmount),
                            WRONG_TRANSFER_LIST);
                }));
    }

    @HapiTest
    @Order(9)
    final Stream<DynamicTest> executionWithCustomPayerWhoSignsAtCreationAsPayerWorks() {
        long transferAmount = 1;
        return defaultHapiSpec("ExecutionWithCustomPayerWhoSignsAtCreationAsPayerWorks")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(PAYING_ACCOUNT),
                        cryptoCreate(SENDER),
                        cryptoCreate(RECEIVER),
                        scheduleCreate(BASIC_XFER, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, transferAmount)))
                                .payingWith(PAYING_ACCOUNT)
                                .designatingPayer(PAYING_ACCOUNT)
                                .via(CREATE_TXN))
                .when(scheduleSign(BASIC_XFER)
                        .alsoSigningWith(SENDER)
                        .via(SIGN_TXN)
                        .hasKnownStatus(SUCCESS))
                .then(withOpContext((spec, opLog) -> {
                    var createTx = getTxnRecord(CREATE_TXN);
                    var signTx = getTxnRecord(SIGN_TXN);
                    var triggeredTx = getTxnRecord(CREATE_TXN).scheduled();
                    allRunFor(spec, createTx, signTx, triggeredTx);

                    Assertions.assertEquals(
                            SUCCESS,
                            triggeredTx.getResponseRecord().getReceipt().getStatus(),
                            SCHEDULED_TRANSACTION_MUST_SUCCEED);

                    Assertions.assertEquals(
                            signTx.getResponseRecord().getConsensusTimestamp().getNanos()
                                    + normalTriggeredTxnTimestampOffset,
                            triggeredTx
                                    .getResponseRecord()
                                    .getConsensusTimestamp()
                                    .getNanos(),
                            WRONG_CONSENSUS_TIMESTAMP);

                    Assertions.assertEquals(
                            createTx.getResponseRecord().getTransactionID().getTransactionValidStart(),
                            triggeredTx.getResponseRecord().getTransactionID().getTransactionValidStart(),
                            WRONG_TRANSACTION_VALID_START);

                    Assertions.assertEquals(
                            createTx.getResponseRecord().getTransactionID().getAccountID(),
                            triggeredTx.getResponseRecord().getTransactionID().getAccountID(),
                            WRONG_RECORD_ACCOUNT_ID);

                    Assertions.assertTrue(
                            triggeredTx.getResponseRecord().getTransactionID().getScheduled(),
                            TRANSACTION_NOT_SCHEDULED);

                    Assertions.assertEquals(
                            createTx.getResponseRecord().getReceipt().getScheduleID(),
                            triggeredTx.getResponseRecord().getScheduleRef(),
                            WRONG_SCHEDULE_ID);

                    Assertions.assertTrue(
                            transferListCheck(
                                    triggeredTx,
                                    asId(SENDER, spec),
                                    asId(RECEIVER, spec),
                                    asId(PAYING_ACCOUNT, spec),
                                    transferAmount),
                            WRONG_TRANSFER_LIST);
                }));
    }

    @HapiTest
    @Order(26)
    final Stream<DynamicTest> scheduledPermissionedFileUpdateWorksAsExpected() {
        return defaultHapiSpec("ScheduledPermissionedFileUpdateWorksAsExpected")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(PAYING_ACCOUNT),
                        scheduleCreate(
                                        A_SCHEDULE,
                                        fileUpdate(standardUpdateFile).contents("fooo!"))
                                .withEntityMemo(randomUppercase(100))
                                .designatingPayer(SOFTWARE_UPDATE_ADMIN)
                                .payingWith(PAYING_ACCOUNT)
                                .via(SUCCESS_TXN))
                .when(scheduleSign(A_SCHEDULE)
                        .alsoSigningWith(SOFTWARE_UPDATE_ADMIN)
                        .payingWith(PAYING_ACCOUNT)
                        .via(SIGN_TX)
                        .hasKnownStatus(SUCCESS))
                .then(
                        restoreDefault("scheduling.whitelist"),
                        getScheduleInfo(A_SCHEDULE).isExecuted(),
                        withOpContext((spec, opLog) -> {
                            var triggeredTx = getTxnRecord(SUCCESS_TXN).scheduled();
                            allRunFor(spec, triggeredTx);

                            Assertions.assertEquals(
                                    SUCCESS,
                                    triggeredTx.getResponseRecord().getReceipt().getStatus(),
                                    SCHEDULED_TRANSACTION_MUST_SUCCEED);
                        }));
    }

    @HapiTest
    @Order(25)
    final Stream<DynamicTest> scheduledPermissionedFileUpdateUnauthorizedPayerFails() {
        return defaultHapiSpec("ScheduledPermissionedFileUpdateUnauthorizedPayerFails")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(PAYING_ACCOUNT),
                        cryptoCreate(PAYING_ACCOUNT_2),
                        scheduleCreate(
                                        A_SCHEDULE,
                                        fileUpdate(standardUpdateFile).contents("fooo!"))
                                .withEntityMemo(randomUppercase(100))
                                .designatingPayer(PAYING_ACCOUNT_2)
                                .payingWith(PAYING_ACCOUNT)
                                .via(SUCCESS_TXN))
                .when(scheduleSign(A_SCHEDULE)
                        .alsoSigningWith(PAYING_ACCOUNT_2, FREEZE_ADMIN)
                        .payingWith(PAYING_ACCOUNT)
                        .via(SIGN_TX)
                        .hasKnownStatus(SUCCESS))
                .then(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_DEFAULT),
                        getScheduleInfo(A_SCHEDULE).isExecuted(),
                        withOpContext((spec, opLog) -> {
                            var triggeredTx = getTxnRecord(SUCCESS_TXN).scheduled();
                            allRunFor(spec, triggeredTx);

                            Assertions.assertEquals(
                                    AUTHORIZATION_FAILED,
                                    triggeredTx.getResponseRecord().getReceipt().getStatus(),
                                    "Scheduled transaction be AUTHORIZATION_FAILED!");
                        }));
    }

    @HapiTest
    @Order(33)
    final Stream<DynamicTest> scheduledSystemDeleteWorksAsExpected() {

        return defaultHapiSpec("ScheduledSystemDeleteWorksAsExpected")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(PAYING_ACCOUNT),
                        fileCreate("misc").lifetime(THREE_MONTHS_IN_SECONDS).contents(ORIG_FILE),
                        scheduleCreate(A_SCHEDULE, systemFileDelete("misc").updatingExpiry(1L))
                                .withEntityMemo(randomUppercase(100))
                                .designatingPayer(SYSTEM_DELETE_ADMIN)
                                .payingWith(PAYING_ACCOUNT)
                                .via(SUCCESS_TXN))
                .when(scheduleSign(A_SCHEDULE)
                        .alsoSigningWith(SYSTEM_DELETE_ADMIN)
                        .payingWith(PAYING_ACCOUNT)
                        .via(SIGN_TX)
                        .hasKnownStatus(SUCCESS))
                .then(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_DEFAULT),
                        getScheduleInfo(A_SCHEDULE).isExecuted(),
                        getFileInfo("misc").nodePayment(1_234L).hasAnswerOnlyPrecheck(INVALID_FILE_ID),
                        withOpContext((spec, opLog) -> {
                            var triggeredTx = getTxnRecord(SUCCESS_TXN).scheduled();
                            allRunFor(spec, triggeredTx);

                            Assertions.assertEquals(
                                    SUCCESS,
                                    triggeredTx.getResponseRecord().getReceipt().getStatus(),
                                    SCHEDULED_TRANSACTION_MUST_SUCCEED);
                        }));
    }

    @HapiTest
    @Order(32)
    final Stream<DynamicTest> hapiTestScheduledSystemDeleteUnauthorizedPayerFails() {
        return defaultHapiSpec("ScheduledSystemDeleteUnauthorizedPayerFails")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(PAYING_ACCOUNT),
                        cryptoCreate(PAYING_ACCOUNT_2),
                        fileCreate("misc").lifetime(THREE_MONTHS_IN_SECONDS).contents(ORIG_FILE),
                        overriding(SCHEDULING_WHITELIST, "SystemDelete"),
                        scheduleCreate(A_SCHEDULE, systemFileDelete("misc").updatingExpiry(1L))
                                .withEntityMemo(randomUppercase(100))
                                .designatingPayer(PAYING_ACCOUNT_2)
                                .payingWith(PAYING_ACCOUNT)
                                .via(SUCCESS_TXN))
                .when(scheduleSign(A_SCHEDULE)
                        .alsoSigningWith(PAYING_ACCOUNT_2)
                        .payingWith(PAYING_ACCOUNT)
                        .via(SIGN_TX)
                        .hasKnownStatus(SUCCESS))
                .then(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_DEFAULT),
                        getScheduleInfo(A_SCHEDULE).isExecuted(),
                        getFileInfo("misc").nodePayment(1_234L),
                        withOpContext((spec, opLog) -> {
                            var triggeredTx = getTxnRecord(SUCCESS_TXN).scheduled();
                            allRunFor(spec, triggeredTx);

                            Assertions.assertEquals(
                                    NOT_SUPPORTED,
                                    triggeredTx.getResponseRecord().getReceipt().getStatus(),
                                    "Scheduled transaction be NOT_SUPPORTED!");
                        }));
    }

    final Stream<DynamicTest> scheduledSystemDeleteUnauthorizedPayerFails(boolean isLongTermEnabled) {
        if (isLongTermEnabled) {

            return defaultHapiSpec("ScheduledSystemDeleteUnauthorizedPayerFails")
                    .given(
                            overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                            cryptoCreate(PAYING_ACCOUNT),
                            cryptoCreate(PAYING_ACCOUNT_2),
                            fileCreate("misc").lifetime(THREE_MONTHS_IN_SECONDS).contents(ORIG_FILE))
                    .when()
                    .then(
                            scheduleCreate(A_SCHEDULE, systemFileDelete("misc").updatingExpiry(1L))
                                    .withEntityMemo(randomUppercase(100))
                                    .designatingPayer(PAYING_ACCOUNT_2)
                                    .payingWith(PAYING_ACCOUNT)
                                    // we are always busy with long term enabled in this case
                                    // because there are no throttles for SystemDelete and we deeply
                                    // check with long term enabled
                                    .hasPrecheck(BUSY),
                            overriding(SCHEDULING_WHITELIST, WHITELIST_DEFAULT));
        }

        return defaultHapiSpec("ScheduledSystemDeleteUnauthorizedPayerFails")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(PAYING_ACCOUNT),
                        cryptoCreate(PAYING_ACCOUNT_2),
                        fileCreate("misc").lifetime(THREE_MONTHS_IN_SECONDS).contents(ORIG_FILE),
                        overriding(SCHEDULING_WHITELIST, "SystemDelete"),
                        scheduleCreate(A_SCHEDULE, systemFileDelete("misc").updatingExpiry(1L))
                                .withEntityMemo(randomUppercase(100))
                                .designatingPayer(PAYING_ACCOUNT_2)
                                .payingWith(PAYING_ACCOUNT)
                                .via(SUCCESS_TXN))
                .when(scheduleSign(A_SCHEDULE)
                        .alsoSigningWith(PAYING_ACCOUNT_2)
                        .payingWith(PAYING_ACCOUNT)
                        .via(SIGN_TX)
                        .hasKnownStatus(SUCCESS))
                .then(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_DEFAULT),
                        getScheduleInfo(A_SCHEDULE).isExecuted(),
                        getFileInfo("misc").nodePayment(1_234L),
                        withOpContext((spec, opLog) -> {
                            var triggeredTx = getTxnRecord(SUCCESS_TXN).scheduled();
                            allRunFor(spec, triggeredTx);

                            Assertions.assertEquals(
                                    NOT_SUPPORTED,
                                    triggeredTx.getResponseRecord().getReceipt().getStatus(),
                                    "Scheduled transaction be NOT_SUPPORTED!");
                        }));
    }

    @HapiTest
    @Order(48)
    final Stream<DynamicTest> suiteCleanup() {
        return defaultHapiSpec("suiteCleanup")
                .given()
                .when()
                .then(fileUpdate(APP_PROPERTIES)
                        .payingWith(ADDRESS_BOOK_CONTROL)
                        .overridingProps(Map.of(SCHEDULING_WHITELIST, WHITELIST_DEFAULT)));
    }

    private <T extends Record> T getTestConfig(Class<T> configClass) {
        final TestConfigBuilder builder = new TestConfigBuilder(configClass);
        return builder.getOrCreateConfig().getConfigData(configClass);
    }
}
