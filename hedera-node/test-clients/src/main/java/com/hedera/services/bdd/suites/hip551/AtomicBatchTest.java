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

import static com.hedera.services.bdd.junit.ContextRequirement.THROTTLE_OVERRIDES;
import static com.hedera.services.bdd.junit.RepeatableReason.NEEDS_VIRTUAL_TIME_FOR_FAST_EXECUTION;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiTest;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.PREDEFINED_SHAPE;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.TrieSigMapGenerator.uniqueWithFullPrefixesFor;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getReceipt;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.atomicBatch;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumCryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromToWithAlias;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingThrottles;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.usableTxnIdNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsd;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.FIVE_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.MAX_CALL_DATA_SIZE;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SHAPE;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SOURCE_KEY;
import static com.hedera.services.bdd.suites.HapiSuite.THROTTLE_DEFS;
import static com.hedera.services.bdd.suites.HapiSuite.flattened;
import static com.hedera.services.bdd.suites.crypto.AutoCreateUtils.createHollowAccountFrom;
import static com.hedera.services.bdd.suites.crypto.AutoCreateUtils.updateSpecFor;
import static com.hedera.services.bdd.suites.utils.sysfiles.serdes.ThrottleDefsLoader.protoDefsFromResource;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INNER_TRANSACTION_FAILED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_EXPIRED;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hedera.services.bdd.junit.RepeatableHapiTest;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;

@HapiTestLifecycle
public class AtomicBatchTest {

    @HapiTest
    public Stream<DynamicTest> missingInnerTxnPayerSignatureFails() {
        final var batchOperator = "batchOperator";
        final var innerTnxPayer = "innerPayer";
        final var innerTxnId = "innerId";
        // crete inner txn with innerTnxPayer, but sign only with DEFAULT_PAYER
        final var innerTxn = cryptoCreate("foo")
                .balance(ONE_HBAR)
                .txnId(innerTxnId)
                .batchKey(batchOperator)
                .payingWith(innerTnxPayer)
                .signedBy(DEFAULT_PAYER);

        return hapiTest(
                cryptoCreate(batchOperator).balance(ONE_HBAR),
                cryptoCreate(innerTnxPayer).balance(ONE_HBAR),
                usableTxnIdNamed(innerTxnId).payerId(innerTnxPayer),
                // Since the inner txn is signed by DEFAULT_PAYER, it should fail
                atomicBatch(innerTxn).payingWith(batchOperator).hasKnownStatus(INNER_TRANSACTION_FAILED));
    }

    @HapiTest
    public Stream<DynamicTest> missingBatchKeyFails() {
        final var batchOperator = "batchOperator";
        final var innerTnxPayer = "innerPayer";
        final var innerTxnId = "innerId";
        // crete inner txn with innerTnxPayer and without batchKey
        final var innerTxn =
                cryptoCreate("foo").balance(ONE_HBAR).txnId(innerTxnId).payingWith(innerTnxPayer);

        return hapiTest(
                cryptoCreate(batchOperator).balance(ONE_HBAR),
                cryptoCreate(innerTnxPayer).balance(ONE_HBAR),
                usableTxnIdNamed(innerTxnId).payerId(innerTnxPayer),
                // Since the inner txn doesn't have batchKey, it should fail
                atomicBatch(innerTxn).payingWith(batchOperator).hasKnownStatus(INNER_TRANSACTION_FAILED));
    }

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

    @HapiTest
    public Stream<DynamicTest> duplicatedInnerTransactionsFail() {
        final var batchOperator = "batchOperator";
        final var innerTnxPayer = "innerPayer";
        final var innerTxnId1 = "innerId1";
        final var innerTxnId2 = "innerId2";
        final var account1 = "foo1";
        final var account2 = "foo2";

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
                cryptoCreate(innerTnxPayer).balance(ONE_HUNDRED_HBARS),
                usableTxnIdNamed(innerTxnId1).payerId(innerTnxPayer),
                usableTxnIdNamed(innerTxnId2).payerId(innerTnxPayer),
                cryptoCreate(batchOperator)
                        .txnId(innerTxnId1)
                        .payingWith(innerTnxPayer)
                        .balance(ONE_HBAR),
                atomicBatch(innerTxn1, innerTxn2).hasKnownStatus(INNER_TRANSACTION_FAILED),
                atomicBatch(innerTxn2));
        // PreHandleWorkflowImpl.preHandleTransaction() added inner transaction id to deduplicationCache, uncommented
        // below after neeha's pr 17763
        // atomicBatch(innerTxn2).hasKnownStatus(INNER_TRANSACTION_FAILED));
    }

    @HapiTest
    public Stream<DynamicTest> invalidTransactionStartFailed() {
        final var batchOperator = "batchOperator";
        final var innerTnxPayer = "innerPayer";
        final var innerTxnId1 = "innerId1";
        final var account1 = "foo1";

        final var innerTxn1 = cryptoCreate(account1)
                .balance(ONE_HBAR)
                .txnId(innerTxnId1)
                .batchKey(batchOperator)
                .payingWith(innerTnxPayer);
        final var validStart = Timestamp.newBuilder()
                .setSeconds(Instant.now().getEpochSecond() + 1000)
                .setNanos(1)
                .build();

        return hapiTest(
                cryptoCreate(innerTnxPayer).balance(ONE_HUNDRED_HBARS),
                usableTxnIdNamed(innerTxnId1).payerId(innerTnxPayer).validStart(validStart),
                cryptoCreate(batchOperator).balance(ONE_HBAR),
                atomicBatch(innerTxn1).hasKnownStatus(INNER_TRANSACTION_FAILED));
    }

    @HapiTest
    public Stream<DynamicTest> invalidTransactionDurationFailed() {
        final var batchOperator = "batchOperator";
        final var innerTnxPayer = "innerPayer";
        final var innerTxnId1 = "innerId1";
        final var account1 = "foo1";

        final var innerTxn1 = cryptoCreate(account1)
                .balance(ONE_HBAR)
                .txnId(innerTxnId1)
                .batchKey(batchOperator)
                .payingWith(innerTnxPayer)
                .validDurationSecs(200);

        return hapiTest(
                cryptoCreate(innerTnxPayer).balance(ONE_HUNDRED_HBARS),
                usableTxnIdNamed(innerTxnId1).payerId(innerTnxPayer),
                cryptoCreate(batchOperator).balance(ONE_HBAR),
                atomicBatch(innerTxn1).hasKnownStatus(INNER_TRANSACTION_FAILED));
    }

    @Nested
    @DisplayName("Batch Constraints - POSITIVE")
    class BatchConstraintsPositive {

        @LeakyHapiTest
        @Disabled // TODO: enable this test when we have the maxInnerTxn property
        @DisplayName("Bach with max number of inner transaction")
        // BATCH_01
        public Stream<DynamicTest> maxInnerTxn() {
            final var payer = "payer";
            final var transferTxnId = "transferTxnId";
            final var batchOperator = "batchOperator";

            return propertyPreservingHapiTest(
                    // set the maxInnerTxn to 2
                    List.of("atomicBatch.maxInnerTxn"),
                    overriding("atomicBatch.maxInnerTxn", "2"),
                    cryptoCreate(batchOperator),
                    cryptoCreate("payer").balance(ONE_HUNDRED_HBARS),
                    newKeyNamed("bar"),
                    usableTxnIdNamed(transferTxnId).payerId(payer),
                    // create a batch with the maximum number of inner transactions
                    // even if we have 1 child transaction, the batch should succeed
                    atomicBatch(
                                    cryptoCreate("foo").balance(ONE_HBAR).batchKey(batchOperator),
                                    cryptoTransfer(tinyBarsFromToWithAlias(payer, "bar", 10))
                                            .batchKey(batchOperator)
                                            .txnId(transferTxnId)
                                            .payingWith(payer))
                            .signedByPayerAnd(batchOperator)
                            .via("batchTxnId"),

                    // TODO: auto account creation should be child of the cryptoTransfer txn, not the batch!!!
                    // we need to fix this when we dispatch childs of the inner txns
                    //
                    // getReceipt(transferTxnId).andAnyChildReceipts().hasChildAutoAccountCreations(1),
                    getReceipt("batchTxnId").andAnyChildReceipts().hasChildAutoAccountCreations(1));
        }

        @LeakyHapiTest(requirement = {THROTTLE_OVERRIDES})
        @DisplayName("Bach contract call with the TPS limit")
        //  BATCH_02
        public Stream<DynamicTest> contractCallTPSLimit() {
            final var batchOperator = "batchOperator";
            final var contract = "CalldataSize";
            final var function = "callme";
            final var payload = new byte[100];
            final var payer = "payer";
            return hapiTest(
                    cryptoCreate(batchOperator),
                    cryptoCreate(payer).balance(ONE_HBAR),
                    uploadInitCode(contract),
                    contractCreate(contract),
                    overridingThrottles("testSystemFiles/artificial-limits.json"),
                    // create batch with 6 contract calls
                    atomicBatch(
                                    contractCall(contract, function, payload)
                                            .payingWith(payer)
                                            .batchKey(batchOperator),
                                    contractCall(contract, function, payload)
                                            .payingWith(payer)
                                            .batchKey(batchOperator),
                                    contractCall(contract, function, payload)
                                            .payingWith(payer)
                                            .batchKey(batchOperator),
                                    contractCall(contract, function, payload)
                                            .payingWith(payer)
                                            .batchKey(batchOperator),
                                    contractCall(contract, function, payload)
                                            .payingWith(payer)
                                            .batchKey(batchOperator),
                                    contractCall(contract, function, payload)
                                            .payingWith(payer)
                                            .batchKey(batchOperator))
                            .signedByPayerAnd(batchOperator)
                            .payingWith(payer));
        }

        @LeakyHapiTest
        @DisplayName("Bach contract call with the gas limit")
        //  BATCH_03
        public Stream<DynamicTest> contractCallGasLimit() {
            final var contract = "CalldataSize";
            final var function = "callme";
            final var payload = new byte[100];
            final var batchOperator = "batchOperator";
            return propertyPreservingHapiTest(
                    List.of("contracts.maxGasPerSec"),
                    overriding("contracts.maxGasPerSec", "2000000"),
                    cryptoCreate(batchOperator),
                    uploadInitCode(contract),
                    contractCreate(contract),
                    atomicBatch(contractCall(contract, function, payload)
                                    .gas(2000000)
                                    .batchKey(batchOperator))
                            .signedByPayerAnd(batchOperator));
        }

        @HapiTest
        @DisplayName("Bach contract call with 6kb payload")
        //  BATCH_04
        public Stream<DynamicTest> contractCallTxnSizeLimit() {
            final var contract = "CalldataSize";
            final var function = "callme";
            // Adjust the payload size with 512 bytes, so the total size is just under 6kb
            final var payload = new byte[MAX_CALL_DATA_SIZE - 512];
            final var batchOperator = "batchOperator";
            return hapiTest(
                    cryptoCreate(batchOperator),
                    uploadInitCode(contract),
                    contractCreate(contract),
                    atomicBatch(contractCall(contract, function, payload).batchKey(batchOperator))
                            .signedByPayerAnd(batchOperator));
        }

        @HapiTest
        @DisplayName("Following batch with same inner txn")
        //  BATCH_06
        public Stream<DynamicTest> followingBatchWithSameButNonExecutedTxn() {
            final var payer = "payer";
            final var firstTxnId = "firstTxnId";
            final var secondTxnId = "secondTxnId";
            final var batchOperator = "batchOperator";
            return hapiTest(
                    cryptoCreate(batchOperator),
                    cryptoCreate(payer).balance(ONE_HUNDRED_HBARS),
                    usableTxnIdNamed(firstTxnId).payerId(payer),
                    usableTxnIdNamed(secondTxnId).payerId(payer),
                    // execute first transaction
                    cryptoCreate("foo").txnId(firstTxnId).payingWith(payer),
                    // create a failing batch, containing duplicated transaction
                    atomicBatch(
                                    cryptoCreate("foo")
                                            .txnId(firstTxnId)
                                            .payingWith(payer)
                                            .batchKey(batchOperator),
                                    // second inner txn will not be executed
                                    cryptoCreate("bar")
                                            .txnId(secondTxnId)
                                            .payingWith(payer)
                                            .batchKey(batchOperator))
                            .signedByPayerAnd(batchOperator)
                            .hasKnownStatus(INNER_TRANSACTION_FAILED),
                    // create a successful batch, containing the second (non-executed) transaction
                    atomicBatch(cryptoCreate("bar")
                            .txnId(secondTxnId)
                            .payingWith(payer)
                            .batchKey(batchOperator)));
        }

        @HapiTest
        @DisplayName("Deleted account key as batch key")
        //  BATCH_07
        public Stream<DynamicTest> deletedAccountKeyAsBatchKey() {
            final var payer = "payer";
            final var aliceKey = "aliceKey";
            final var alice = "alice";
            return hapiTest(
                    cryptoCreate(payer).balance(ONE_HUNDRED_HBARS),
                    newKeyNamed(aliceKey),
                    cryptoCreate(alice).key(aliceKey),
                    cryptoDelete(alice),
                    atomicBatch(cryptoCreate("foo").batchKey(aliceKey))
                            .payingWith(payer)
                            .signedBy(payer, aliceKey));
        }

        @LeakyHapiTest(requirement = {THROTTLE_OVERRIDES})
        @DisplayName("Update throttles should take effect to following inner txns")
        //  BATCH_08
        public Stream<DynamicTest> throttlesShouldTakeEffect() {
            final var contract = "CalldataSize";
            final var function = "callme";
            final var payload = new byte[100];
            final var payer = "payer";
            final var batchOperator = "batchOperator";
            return hapiTest(
                    cryptoCreate(batchOperator),
                    cryptoCreate(payer).balance(ONE_HBAR),
                    uploadInitCode(contract),
                    contractCreate(contract),
                    // seth contract call to 6 TPS
                    overridingThrottles("testSystemFiles/artificial-limits.json"),
                    // create batch with 6 contract calls
                    atomicBatch(
                                    fileUpdate(THROTTLE_DEFS)
                                            .batchKey(batchOperator)
                                            .noLogging()
                                            .payingWith(GENESIS)
                                            .contents(protoDefsFromResource("testSystemFiles/mainnet-throttles.json")
                                                    .toByteArray()),
                                    // call more than 6 times
                                    contractCall(contract, function, payload)
                                            .payingWith(payer)
                                            .batchKey(batchOperator),
                                    contractCall(contract, function, payload)
                                            .payingWith(payer)
                                            .batchKey(batchOperator),
                                    contractCall(contract, function, payload)
                                            .payingWith(payer)
                                            .batchKey(batchOperator),
                                    contractCall(contract, function, payload)
                                            .payingWith(payer)
                                            .batchKey(batchOperator),
                                    contractCall(contract, function, payload)
                                            .payingWith(payer)
                                            .batchKey(batchOperator),
                                    contractCall(contract, function, payload)
                                            .payingWith(payer)
                                            .batchKey(batchOperator),
                                    contractCall(contract, function, payload)
                                            .payingWith(payer)
                                            .batchKey(batchOperator),
                                    contractCall(contract, function, payload)
                                            .payingWith(payer)
                                            .batchKey(batchOperator),
                                    contractCall(contract, function, payload)
                                            .payingWith(payer)
                                            .batchKey(batchOperator),
                                    contractCall(contract, function, payload)
                                            .payingWith(payer)
                                            .batchKey(batchOperator),
                                    contractCall(contract, function, payload)
                                            .payingWith(payer)
                                            .batchKey(batchOperator),
                                    fileUpdate(THROTTLE_DEFS)
                                            .batchKey(batchOperator)
                                            .noLogging()
                                            .payingWith(GENESIS)
                                            .contents(protoDefsFromResource("testSystemFiles/artificial-limits.json")
                                                    .toByteArray()))
                            .signedByPayerAnd(batchOperator)
                            .payingWith(payer));
        }

        @HapiTest
        @DisplayName("Sign batch with additional keys")
        //  BATCH_09
        public Stream<DynamicTest> signBatchWithAdditionalKeys() {
            final var payer = "payer";
            final var alice = "alice";
            return hapiTest(
                    cryptoCreate(payer).balance(ONE_HUNDRED_HBARS),
                    cryptoCreate(alice),
                    atomicBatch(cryptoCreate("foo").batchKey(alice))
                            .payingWith(payer)
                            .signedBy(payer, alice));
        }
    }

    @Nested()
    @DisplayName("Signatures - positive")
    class AtomicBatchSignaturesPositive {

        @HapiTest
        // BATCH_18  BATCH_19
        @DisplayName("Batch should finalize hollow account")
        final Stream<DynamicTest> batchFinalizeHollowAccount() {
            final var alias = "alias";
            final var batchOperator = "batchOperator";
            return hapiTest(flattened(
                    cryptoCreate(batchOperator),
                    newKeyNamed(alias).shape(SECP_256K1_SHAPE),
                    createHollowAccountFrom(alias),
                    getAliasedAccountInfo(alias).isHollow(),
                    atomicBatch(cryptoCreate("foo")
                                    .payingWith(alias)
                                    .sigMapPrefixes(uniqueWithFullPrefixesFor(alias))
                                    .batchKey(batchOperator))
                            .payingWith(alias)
                            .batchKey(batchOperator)
                            .sigMapPrefixes(uniqueWithFullPrefixesFor(alias))
                            .signedBy(alias, batchOperator),
                    getAliasedAccountInfo(alias)
                            .has(accountWith().hasNonEmptyKey())
                            .logged()));
        }

        @HapiTest
        // BATCH_20
        @DisplayName("Failing batch should finalize hollow account")
        final Stream<DynamicTest> failingBatchShouldFinalizeHollowAccount() {
            final var alias = "alias";
            final var batchOperator = "batchOperator";
            return hapiTest(flattened(
                    cryptoCreate(batchOperator),
                    newKeyNamed(alias).shape(SECP_256K1_SHAPE),
                    createHollowAccountFrom(alias),
                    getAliasedAccountInfo(alias).isHollow(),
                    atomicBatch(
                                    cryptoCreate("foo")
                                            .payingWith(alias)
                                            .batchKey(batchOperator)
                                            .sigMapPrefixes(uniqueWithFullPrefixesFor(alias)),
                                    cryptoCreate("bar")
                                            .alias(ByteString.EMPTY)
                                            .payingWith(alias)
                                            .batchKey(batchOperator))
                            .payingWith(alias)
                            .sigMapPrefixes(uniqueWithFullPrefixesFor(alias))
                            .signedBy(alias, batchOperator)
                            .hasKnownStatus(INNER_TRANSACTION_FAILED),
                    getAliasedAccountInfo(alias).isNotHollow()));
        }

        @HapiTest
        // BATCH_23
        @DisplayName("Threshold batch key should work")
        final Stream<DynamicTest> thresholdBatchKeyShouldWork() {
            final KeyShape threshKeyShape = KeyShape.threshOf(1, PREDEFINED_SHAPE, PREDEFINED_SHAPE);
            final var threshBatchKey = "threshBatchKey";
            final var alis = "alis";
            final var bob = "bob";

            return hapiTest(
                    cryptoCreate(alis).balance(FIVE_HBARS),
                    cryptoCreate(bob),
                    newKeyNamed(threshBatchKey).shape(threshKeyShape.signedWith(sigs(alis, bob))),
                    atomicBatch(
                                    cryptoCreate("foo").batchKey(threshBatchKey),
                                    cryptoCreate("bar").batchKey(threshBatchKey))
                            .signedByPayerAnd(alis));
        }

        @HapiTest
        // BATCH_25 BATCH_28 BATCH_29 BATCH_30
        // This cases all are very similar and can be combined into one
        @DisplayName("Payer is different from batch operator")
        final Stream<DynamicTest> payWithDifferentAccount() {
            final var alis = "alis";
            final var bob = "bob";

            return hapiTest(
                    cryptoCreate(alis).balance(FIVE_HBARS),
                    cryptoCreate(bob),
                    atomicBatch(
                                    cryptoCreate("foo").batchKey(bob),
                                    cryptoCreate("bar").batchKey(bob))
                            .payingWith(alis)
                            .signedBy(alis, bob));
        }
    }

    @Nested
    @DisplayName("Privileged Transactions - POSITIVE")
    class PrivilegedTransactionsPositive {

        @LeakyHapiTest(requirement = {THROTTLE_OVERRIDES})
        @DisplayName("Batch containing only privileged transactions")
        public Stream<DynamicTest> batchContainingOnlyPrivilegedTxn() {
            final var batchOperator = "batchOperator";

            return hapiTest(
                    cryptoCreate(batchOperator).balance(FIVE_HBARS),
                    atomicBatch(fileUpdate(THROTTLE_DEFS)
                                    .batchKey(batchOperator)
                                    .noLogging()
                                    .payingWith(GENESIS)
                                    .contents(protoDefsFromResource("testSystemFiles/mainnet-throttles.json")
                                            .toByteArray()))
                            .payingWith(batchOperator));
        }
    }

    @Nested
    @DisplayName("Fees - POSITIVE")
    class FeesPositive {

        @HapiTest
        @Disabled // TODO: enable this, when the fee calculation of inner transactions is implemented
        @DisplayName("Payer was charged for all transactions")
        public Stream<DynamicTest> payerWasCharged() {
            final var batchOperator = "batchOperator";
            return hapiTest(
                    cryptoCreate(batchOperator).balance(ONE_HUNDRED_HBARS),
                    usableTxnIdNamed("innerTxn1").payerId(batchOperator),
                    usableTxnIdNamed("innerTxn2").payerId(batchOperator),
                    atomicBatch(
                                    cryptoCreate("foo")
                                            .txnId("innerTxn1")
                                            .batchKey(batchOperator)
                                            .payingWith(batchOperator),
                                    cryptoCreate("bar")
                                            .txnId("innerTxn1")
                                            .batchKey(batchOperator)
                                            .payingWith(batchOperator))
                            .payingWith(batchOperator)
                            .via("batchTxn"),
                    // validate the fee charged for the batch txn and the inner txns
                    validateChargedUsd("batchTxn", 0.001),
                    validateChargedUsd("innerTxn1", 0.05),
                    validateChargedUsd("innerTxn2", 0.05));
        }
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

                    // modify batch valid start to 1 hour in the past
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

                    // submit new batch with valid start and the same inner transaction
                    atomicBatch(cryptoCreate("foo")
                                    .txnId(innerTxnId)
                                    .payingWith(payer)
                                    .batchKey(batchOperator))
                            .signedByPayerAnd(batchOperator));
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
