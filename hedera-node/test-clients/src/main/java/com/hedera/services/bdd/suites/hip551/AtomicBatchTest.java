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
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getReceipt;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.atomicBatch;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromToWithAlias;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingThrottles;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.usableTxnIdNamed;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.MAX_CALL_DATA_SIZE;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.THROTTLE_DEFS;
import static com.hedera.services.bdd.suites.utils.sysfiles.serdes.ThrottleDefsLoader.protoDefsFromResource;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INNER_TRANSACTION_FAILED;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;

@HapiTestLifecycle
public class AtomicBatchTest {

    @HapiTest
    // just test that the batch is submitted
    // disabled for now because there is no handler logic and streamValidation is failing in CI
    public Stream<DynamicTest> simpleBatchTest() {
        return hapiTest(
                cryptoCreate("dummy").balance(ONE_HBAR),
                usableTxnIdNamed("inner").payerId("dummy"),
                atomicBatch(cryptoCreate("PAYER")
                                // .setNode("0.0.3")
                                .txnId("inner")
                                .balance(ONE_HBAR)
                                .payingWith("dummy")
                                .batchKey("dummy"))
                        //                        .payingWith("dummy")
                        .via("batchTxn"),
                getTxnRecord("batchTxn").andAllChildRecords().hasChildRecordCount(0),
                getTxnRecord("inner").assertingNothingAboutHashes().logged(),
                getAccountBalance("PAYER").logged());
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
            return propertyPreservingHapiTest(
                    // set the maxInnerTxn to 2
                    List.of("atomicBatch.maxInnerTxn"),
                    overriding("atomicBatch.maxInnerTxn", "2"),
                    cryptoCreate("payer").balance(ONE_HUNDRED_HBARS),
                    newKeyNamed("bar"),
                    usableTxnIdNamed(transferTxnId).payerId(payer),
                    // create a batch with the maximum number of inner transactions
                    // even if we have 1 child transaction, the batch should succeed
                    atomicBatch(
                                    cryptoCreate("foo").balance(ONE_HBAR),
                                    cryptoTransfer(tinyBarsFromToWithAlias(payer, "bar", 10))
                                            .txnId(transferTxnId)
                                            .payingWith(payer))
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
            final var contract = "CalldataSize";
            final var function = "callme";
            final var payload = new byte[100];
            final var payer = "payer";
            return hapiTest(
                    cryptoCreate(payer).balance(ONE_HBAR),
                    uploadInitCode(contract),
                    contractCreate(contract),
                    overridingThrottles("testSystemFiles/artificial-limits.json"),
                    // create batch with 6 contract calls
                    atomicBatch(
                                    contractCall(contract, function, payload).payingWith(payer),
                                    contractCall(contract, function, payload).payingWith(payer),
                                    contractCall(contract, function, payload).payingWith(payer),
                                    contractCall(contract, function, payload).payingWith(payer),
                                    contractCall(contract, function, payload).payingWith(payer),
                                    contractCall(contract, function, payload).payingWith(payer))
                            .payingWith(payer));
        }

        @LeakyHapiTest
        @DisplayName("Bach contract call with the gas limit")
        //  BATCH_03
        public Stream<DynamicTest> contractCallGasLimit() {
            final var contract = "CalldataSize";
            final var function = "callme";
            final var payload = new byte[100];
            return propertyPreservingHapiTest(
                    List.of("contracts.maxGasPerSec"),
                    overriding("contracts.maxGasPerSec", "2000000"),
                    uploadInitCode(contract),
                    contractCreate(contract),
                    atomicBatch(contractCall(contract, function, payload).gas(2000000)));
        }

        @HapiTest
        @DisplayName("Bach contract call with 6kb payload")
        //  BATCH_04
        public Stream<DynamicTest> contractCallTxnSizeLimit() {
            final var contract = "CalldataSize";
            final var function = "callme";
            // Adjust the payload size with 512 bytes, so the total size is just under 6kb
            final var payload = new byte[MAX_CALL_DATA_SIZE - 512];
            return hapiTest(
                    uploadInitCode(contract),
                    contractCreate(contract),
                    atomicBatch(contractCall(contract, function, payload)));
        }

        @HapiTest
        @DisplayName("Following batch with same inner txn")
        @Disabled // TODO: enable this test when we have deduplication logic for inner txn.
        //  BATCH_06
        public Stream<DynamicTest> followingBatchWithSameButNonExecutedTxn() {
            final var payer = "payer";
            final var firstTxnId = "firstTxnId";
            final var secondTxnId = "secondTxnId";
            return hapiTest(
                    cryptoCreate(payer).balance(ONE_HUNDRED_HBARS),
                    usableTxnIdNamed(firstTxnId).payerId(payer),
                    usableTxnIdNamed(secondTxnId).payerId(payer),
                    // execute first transaction
                    cryptoCreate("foo").txnId(firstTxnId).payingWith(payer),
                    // create a failing batch, containing duplicated transaction
                    atomicBatch(
                                    cryptoCreate("foo").txnId(firstTxnId).payingWith(payer),
                                    // second inner txn will not be executed
                                    cryptoCreate("bar").txnId(secondTxnId).payingWith(payer))
                            .hasKnownStatus(INNER_TRANSACTION_FAILED),
                    // create a successful batch, containing the second (non-executed) transaction
                    atomicBatch(cryptoCreate("bar").txnId(secondTxnId).payingWith(payer)));
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
            return hapiTest(
                    cryptoCreate(payer).balance(ONE_HBAR),
                    uploadInitCode(contract),
                    contractCreate(contract),
                    // seth contract call to 6 TPS
                    overridingThrottles("testSystemFiles/artificial-limits.json"),
                    // create batch with 6 contract calls
                    atomicBatch(
                                    fileUpdate(THROTTLE_DEFS)
                                            .noLogging()
                                            .payingWith(GENESIS)
                                            .contents(protoDefsFromResource("testSystemFiles/mainnet-throttles.json")
                                                    .toByteArray()),
                                    // call more than 6 times
                                    contractCall(contract, function, payload).payingWith(payer),
                                    contractCall(contract, function, payload).payingWith(payer),
                                    contractCall(contract, function, payload).payingWith(payer),
                                    contractCall(contract, function, payload).payingWith(payer),
                                    contractCall(contract, function, payload).payingWith(payer),
                                    contractCall(contract, function, payload).payingWith(payer),
                                    contractCall(contract, function, payload).payingWith(payer),
                                    contractCall(contract, function, payload).payingWith(payer),
                                    contractCall(contract, function, payload).payingWith(payer),
                                    contractCall(contract, function, payload).payingWith(payer),
                                    contractCall(contract, function, payload).payingWith(payer),
                                    fileUpdate(THROTTLE_DEFS)
                                            .noLogging()
                                            .payingWith(GENESIS)
                                            .contents(protoDefsFromResource("testSystemFiles/artificial-limits.json")
                                                    .toByteArray()))
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
}
