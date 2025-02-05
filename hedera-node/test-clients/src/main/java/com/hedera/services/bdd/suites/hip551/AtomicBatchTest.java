/*
 * Copyright (C) 2022-2025 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.hip551;

import static com.hedera.services.bdd.junit.RepeatableReason.NEEDS_VIRTUAL_TIME_FOR_FAST_EXECUTION;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.atomicBatch;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumCryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.usableTxnIdNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsd;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.FIVE_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SHAPE;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SOURCE_KEY;
import static com.hedera.services.bdd.suites.crypto.AutoCreateUtils.updateSpecFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INNER_TRANSACTION_FAILED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_EXPIRED;

import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.RepeatableHapiTest;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;

@HapiTestLifecycle
public class AtomicBatchTest {

    @HapiTest
    public Stream<DynamicTest> simpleBatchTest() {
        final var batchOperator = "batchOperator";
        final var innerTnxPayer = "innerPayer";
        final var innerTxnId = "innerId";

        // create inner txn with:
        // - custom txn id -> for getting the record
        // - batch key -> for batch operator to sign
        // - payer -> for paying the fee
        final var innerTxn = cryptoCreate("foo")
                .balance(ONE_HBAR)
                .txnId(innerTxnId)
                .batchKey(batchOperator)
                .payingWith(innerTnxPayer);

        return hapiTest(
                // create batch operator
                cryptoCreate(batchOperator).balance(ONE_HBAR),
                // create another payer for the inner txn
                cryptoCreate(innerTnxPayer).balance(ONE_HBAR),
                // use custom txn id so we can get the record
                usableTxnIdNamed(innerTxnId).payerId(innerTnxPayer),
                // create a batch txn
                atomicBatch(innerTxn).payingWith(batchOperator).via("batchTxn"),
                // get and log inner txn record
                getTxnRecord(innerTxnId).assertingNothingAboutHashes().logged(),
                // validate the batch txn result
                getAccountBalance("foo").hasTinyBars(ONE_HBAR),
                validateChargedUsd("batchTxn", 0.001));
    }

    @HapiTest
    public Stream<DynamicTest> multiBatchSuccess() {
        final var batchOperator = "batchOperator";
        final var innerTnxPayer = "innerPayer";
        final var innerTxnId1 = "innerId1";
        final var innerTxnId2 = "innerId2";
        final var account1 = "foo1";
        final var account2 = "foo2";
        final var atomicTxn = "atomicTxn";

        final var innerTxn1 = cryptoCreate(account1)
                .balance(ONE_HBAR)
                .txnId(innerTxnId1)
                .batchKey(batchOperator)
                .payingWith(innerTnxPayer);
        final var innerTxn2 = cryptoCreate(account2)
                .balance(ONE_HBAR)
                .txnId(innerTxnId2)
                .batchKey(batchOperator)
                .payingWith(innerTnxPayer);

        return hapiTest(
                cryptoCreate(batchOperator).balance(ONE_HBAR),
                cryptoCreate(innerTnxPayer).balance(ONE_HUNDRED_HBARS),
                usableTxnIdNamed(innerTxnId1).payerId(innerTnxPayer),
                usableTxnIdNamed(innerTxnId2).payerId(innerTnxPayer),
                atomicBatch(innerTxn1, innerTxn2).via(atomicTxn),
                getTxnRecord(atomicTxn).logged(),
                getTxnRecord(innerTxnId1).assertingNothingAboutHashes().logged(),
                getTxnRecord(innerTxnId2).assertingNothingAboutHashes().logged(),
                getAccountBalance(account1).hasTinyBars(ONE_HBAR),
                getAccountBalance(account2).hasTinyBars(ONE_HBAR));
    }

    @HapiTest
    public Stream<DynamicTest> multiBatchFail() {
        final var batchOperator = "batchOperator";
        final var innerTnxPayer = "innerPayer";
        final var innerTxnId1 = "innerId1";
        final var innerTxnId2 = "innerId2";
        final var account1 = "foo1";
        final var account2 = "foo2";
        final var atomicTxn = "atomicTxn";

        final var innerTxn1 = cryptoCreate(account1)
                .balance(ONE_HBAR)
                .txnId(innerTxnId1)
                .batchKey(batchOperator)
                .payingWith(innerTnxPayer);
        final var innerTxn2 = cryptoCreate(account2)
                .balance(ONE_HBAR)
                .txnId(innerTxnId2)
                .batchKey(batchOperator)
                .payingWith(innerTnxPayer);

        return hapiTest(
                cryptoCreate(batchOperator).balance(ONE_HBAR),
                cryptoCreate(innerTnxPayer).balance(ONE_HBAR),
                usableTxnIdNamed(innerTxnId1).payerId(innerTnxPayer),
                usableTxnIdNamed(innerTxnId2).payerId(innerTnxPayer),
                atomicBatch(innerTxn1, innerTxn2).via(atomicTxn).hasKnownStatus(INNER_TRANSACTION_FAILED),
                getTxnRecord(atomicTxn).logged(),
                getTxnRecord(innerTxnId1).assertingNothingAboutHashes().logged(),
                getTxnRecord(innerTxnId2).assertingNothingAboutHashes().logged());
    }

    @Nested
    @DisplayName("Batch Order And Execution - POSITIVE")
    class BatchOrderExecutionPositive {

        @HapiTest
        @DisplayName("Validate order of execution with successful schedule")
        // BATCH_10
        final Stream<DynamicTest> executionWithSchedule() {
            final var sender = "sender";
            final var receiver = "receiver";
            final var schedule = "scheduledXfer";
            final var scheduleCreateTxnId = "scheduledCreateTxnId";
            final var scheduledTxnId = "scheduledTxnId";
            final var signTxnId = "signTxnId";
            final var secondInnerTxnId = "secondInnerTxnId";
            final var batchOperator = "batchOperator";

            return hapiTest(
                    cryptoCreate(batchOperator),
                    cryptoCreate(sender).balance(ONE_HBAR),
                    cryptoCreate(receiver).balance(0L).receiverSigRequired(true),
                    // store txn ids in spec registry for later order validation
                    usableTxnIdNamed(scheduleCreateTxnId).payerId(sender),
                    usableTxnIdNamed(scheduledTxnId)
                            .asScheduled(scheduleCreateTxnId)
                            .payerId(sender),
                    usableTxnIdNamed(signTxnId).payerId(sender),
                    usableTxnIdNamed(secondInnerTxnId).payerId(sender),
                    // create a schedule
                    scheduleCreate(schedule, cryptoTransfer(tinyBarsFromTo(sender, receiver, 1)))
                            .waitForExpiry(false)
                            .txnId(scheduleCreateTxnId)
                            .payingWith(sender),
                    // execute batch with schedule sign
                    atomicBatch(
                                    scheduleSign(schedule)
                                            .batchKey(batchOperator)
                                            .txnId(signTxnId)
                                            .alsoSigningWith(receiver)
                                            .payingWith(sender),
                                    cryptoCreate("foo")
                                            .batchKey(batchOperator)
                                            .txnId(secondInnerTxnId)
                                            .balance(1L)
                                            .payingWith(sender))
                            .signedByPayerAnd(batchOperator)
                            // validate order of execution
                            .validateTxnOrder(
                                    signTxnId,
                                    scheduledTxnId, // scheduled txn is executed right after a sign txn
                                    secondInnerTxnId));
        }

        @HapiTest
        @DisplayName("Validate order of execution with failing schedule")
        // BATCH_11
        final Stream<DynamicTest> executionWithFailingSchedule() {
            final var sender = "sender";
            final var receiver = "receiver";
            final var schedule = "scheduledXfer";
            final var signTxnId = "signTxnId";
            final var secondInnerTxnId = "secondInnerTxnId";
            final var batchOperator = "batchOperator";

            return hapiTest(
                    cryptoCreate(batchOperator),
                    cryptoCreate(sender).balance(ONE_HBAR),
                    cryptoCreate(receiver).receiverSigRequired(true),
                    // store txn ids in spec registry for later order validation
                    usableTxnIdNamed(signTxnId).payerId(sender),
                    usableTxnIdNamed(secondInnerTxnId).payerId(sender),
                    // create a failing scheduled transaction (transfer more than the balance)
                    scheduleCreate(schedule, cryptoTransfer(tinyBarsFromTo(sender, receiver, ONE_HUNDRED_HBARS))),
                    // execute batch with schedule sign
                    atomicBatch(
                                    scheduleSign(schedule)
                                            .batchKey(batchOperator)
                                            .txnId(signTxnId)
                                            .alsoSigningWith(receiver)
                                            .payingWith(sender),
                                    cryptoCreate("foo")
                                            .batchKey(batchOperator)
                                            .txnId(secondInnerTxnId)
                                            .balance(1L)
                                            .payingWith(sender))
                            .signedByPayerAnd(batchOperator)
                            // validate order of execution
                            .validateTxnOrder(signTxnId, secondInnerTxnId),
                    // validate the result of the inner txn
                    getAccountBalance("foo").hasTinyBars(1L));
        }

        // TODO: repeatable tests should run as integration tests - move it there when the logic is ready
        @RepeatableHapiTest(NEEDS_VIRTUAL_TIME_FOR_FAST_EXECUTION)
        @DisplayName("Failing batch will trigger schedule")
        // BATCH_14
        final Stream<DynamicTest> failingBatchWillTriggerSchedule() {
            final var sender = "sender";
            final var receiver = "receiver";
            final var schedule = "scheduledXfer";
            final var batchOperator = "batchOperator";
            return hapiTest(
                    cryptoCreate(batchOperator),
                    cryptoCreate(sender).balance(ONE_HBAR).via("createSender"),
                    cryptoCreate(receiver).balance(0L),
                    // create a failing scheduled transaction (transfer more than the balance)
                    scheduleCreate(schedule, cryptoTransfer(tinyBarsFromTo(sender, receiver, 10L)))
                            .waitForExpiry(true)
                            .withRelativeExpiry("createSender", 4)
                            .signedByPayerAnd(sender)
                            .via("scheduleCreate"),
                    sleepFor(8_000),
                    // trigger with failing batch
                    atomicBatch(cryptoTransfer(tinyBarsFromTo(sender, receiver, ONE_HUNDRED_HBARS))
                                    .batchKey(batchOperator))
                            .signedByPayerAnd(batchOperator)
                            .hasKnownStatus(INNER_TRANSACTION_FAILED),
                    // validate the result of the schedule
                    getAccountBalance(receiver).hasTinyBars(10L));
        }

        @HapiTest
        @DisplayName("Validate batch valid start")
        // BATCH_15
        final Stream<DynamicTest> validateBatchValidStart() {
            final var payer = "payer";
            final var batchTxnId = "batchTxnId";
            final var innerTxnId = "innerTxnId";
            final var beforeHour = -3_600L; // 1 hour in the past
            final var batchOperator = "batchOperator";

            return hapiTest(
                    cryptoCreate(batchOperator),
                    cryptoCreate(payer).balance(ONE_HUNDRED_HBARS),
                    usableTxnIdNamed(batchTxnId).modifyValidStart(beforeHour).payerId(payer),
                    usableTxnIdNamed(innerTxnId).payerId(payer),
                    atomicBatch(cryptoCreate("foo")
                                    .txnId(innerTxnId)
                                    .payingWith(payer)
                                    .batchKey(batchOperator))
                            .signedByPayerAnd(batchOperator)
                            .txnId(batchTxnId)
                            .payingWith(payer)
                            .hasPrecheck(TRANSACTION_EXPIRED),
                    atomicBatch(cryptoCreate("foo").txnId(innerTxnId).payingWith(payer)));
        }

        @HapiTest
        @Disabled // TODO: enable when the inner txn validity checks are ready
        @DisplayName("Validate inner txn valid start")
        // BATCH_16
        final Stream<DynamicTest> validateInnerTxnValidStart() {
            final var alice = "alice";
            final var bob = "bob";
            final var dave = "dave";
            final var carl = "carl";

            final var bobInnerTxnId = "bobInnerTxnId";
            final var bobExpiredTxnId = "bobExpiredTxnId";
            final var daveInnerTxnId = "daveInnerTxnId";
            final var carlInnerTxnId = "carlInnerTxnId";

            final var beforeHour = -3_600L; // 1 hour in the past

            return hapiTest(
                    cryptoCreate(alice).balance(ONE_HUNDRED_HBARS),
                    cryptoCreate(bob).balance(ONE_HBAR),
                    cryptoCreate(dave).balance(ONE_HBAR),
                    cryptoCreate(carl).balance(ONE_HBAR),
                    usableTxnIdNamed(bobExpiredTxnId)
                            .modifyValidStart(beforeHour)
                            .payerId(bob),
                    usableTxnIdNamed(bobInnerTxnId).payerId(bob),
                    usableTxnIdNamed(daveInnerTxnId).payerId(dave),
                    usableTxnIdNamed(carlInnerTxnId).payerId(carl),
                    atomicBatch(
                                    // Bob's txn is expired, so no inner txns should be executed
                                    cryptoCreate("foo")
                                            .batchKey(alice)
                                            .balance(1L)
                                            .txnId(bobExpiredTxnId)
                                            .payingWith(bob),
                                    cryptoCreate("bar")
                                            .batchKey(alice)
                                            .balance(1L)
                                            .txnId(daveInnerTxnId)
                                            .payingWith(dave),
                                    cryptoCreate("baz")
                                            .batchKey(alice)
                                            .balance(1L)
                                            .txnId(carlInnerTxnId)
                                            .payingWith(carl))
                            .signedByPayerAnd(alice)
                            .hasPrecheck(INNER_TRANSACTION_FAILED),
                    atomicBatch(
                                    cryptoCreate("foo")
                                            .batchKey(alice)
                                            .balance(1L)
                                            .txnId(bobInnerTxnId)
                                            .payingWith(bob),
                                    cryptoCreate("bar")
                                            .batchKey(alice)
                                            .balance(1L)
                                            .txnId(daveInnerTxnId)
                                            .payingWith(dave),
                                    cryptoCreate("baz")
                                            .batchKey(alice)
                                            .balance(1L)
                                            .txnId(carlInnerTxnId)
                                            .payingWith(carl))
                            .signedByPayerAnd(alice),

                    // validate inner transactions were successfully executed
                    getAccountBalance("foo").hasTinyBars(1L),
                    getAccountBalance("bar").hasTinyBars(1L),
                    getAccountBalance("baz").hasTinyBars(1L));
        }

        @HapiTest
        @DisplayName("Submit batch containng Hapi and Ethereum txns")
        // BATCH_17
        final Stream<DynamicTest> submitBatchWithEthereumTxn() {
            final var receiver = "receiver";
            final var batchOperator = "batchOperator";
            return hapiTest(
                    cryptoCreate(batchOperator),
                    newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                    cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS)),
                    withOpContext((spec, opLog) -> updateSpecFor(spec, SECP_256K1_SOURCE_KEY)),
                    cryptoCreate(receiver).balance(0L),
                    // submit a batch with Hapi and Ethereum txns
                    atomicBatch(
                                    cryptoTransfer(tinyBarsFromTo(GENESIS, receiver, FIVE_HBARS))
                                            .batchKey(batchOperator),
                                    ethereumCryptoTransfer(receiver, FIVE_HBARS)
                                            .type(EthTxData.EthTransactionType.EIP2930)
                                            .payingWith(SECP_256K1_SOURCE_KEY)
                                            .nonce(0)
                                            .gasPrice(0L)
                                            .gasLimit(2_000_000L)
                                            .batchKey(batchOperator))
                            .signedByPayerAnd(batchOperator),
                    getAccountBalance(receiver).hasTinyBars(FIVE_HBARS * 2));
        }
    }
}
