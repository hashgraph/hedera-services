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

package com.hedera.services.bdd.suites.fees;

import static com.hedera.services.bdd.spec.HapiSpec.CostSnapshotMode;
import static com.hedera.services.bdd.spec.HapiSpec.CostSnapshotMode.TAKE;
import static com.hedera.services.bdd.spec.HapiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountRecords;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static java.util.stream.Collectors.toList;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.TransferList;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CostOfEverythingSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(CostOfEverythingSuite.class);

    CostSnapshotMode costSnapshotMode = TAKE;
    //	CostSnapshotMode costSnapshotMode = COMPARE;

    public static void main(String... args) {
        new CostOfEverythingSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return Stream.of(
                        //				cryptoCreatePaths(),
                        //				cryptoTransferPaths(),
                        //				cryptoGetAccountInfoPaths(),
                        //				cryptoGetAccountRecordsPaths(),
                        //				transactionGetRecordPaths(),
                        miscContractCreatesAndCalls()
                        //				canonicalScheduleOpsHaveExpectedUsdFees()
                        )
                .map(Stream::of)
                .reduce(Stream.empty(), Stream::concat)
                .collect(toList());
    }

    HapiSpec[] transactionGetRecordPaths() {
        return new HapiSpec[] {
            txnGetCreateRecord(), txnGetSmallTransferRecord(), txnGetLargeTransferRecord(),
        };
    }

    HapiSpec canonicalScheduleOpsHaveExpectedUsdFees() {
        return customHapiSpec("CanonicalScheduleOps")
                .withProperties(Map.of(
                        "nodes", "35.231.208.148",
                        "default.payer.pemKeyLoc", "previewtestnet-account2.pem",
                        "default.payer.pemKeyPassphrase", "<secret>"))
                .given(
                        cryptoCreate("payingSender").balance(ONE_HUNDRED_HBARS),
                        cryptoCreate("receiver").balance(0L).receiverSigRequired(true))
                .when(
                        scheduleCreate(
                                        "canonical",
                                        cryptoTransfer(tinyBarsFromTo("payingSender", "receiver", 1L))
                                                .blankMemo()
                                                .fee(ONE_HBAR))
                                .via("canonicalCreation")
                                .payingWith("payingSender")
                                .adminKey("payingSender"),
                        getScheduleInfo("canonical").payingWith("payingSender"),
                        scheduleSign("canonical")
                                .via("canonicalSigning")
                                .payingWith("payingSender")
                                .alsoSigningWith("receiver"),
                        scheduleCreate(
                                        "tbd",
                                        cryptoTransfer(tinyBarsFromTo("payingSender", "receiver", 1L))
                                                .memo("")
                                                .fee(ONE_HBAR)
                                                .blankMemo()
                                                .signedBy("payingSender"))
                                .payingWith("payingSender")
                                .adminKey("payingSender"),
                        scheduleDelete("tbd").via("canonicalDeletion").payingWith("payingSender"))
                .then(
                        validateChargedUsdWithin("canonicalCreation", 0.01, 3.0),
                        validateChargedUsdWithin("canonicalSigning", 0.001, 3.0),
                        validateChargedUsdWithin("canonicalDeletion", 0.001, 3.0));
    }

    HapiSpec miscContractCreatesAndCalls() {
        Object[] donationArgs = new Object[] {2, "Hey, Ma!"};
        final var multipurposeContract = "Multipurpose";
        final var lookupContract = "BalanceLookup";

        return customHapiSpec("MiscContractCreatesAndCalls")
                .withProperties(Map.of("cost.snapshot.mode", costSnapshotMode.toString()))
                .given(
                        cryptoCreate("civilian").balance(ONE_HUNDRED_HBARS),
                        uploadInitCode(multipurposeContract, lookupContract))
                .when(
                        contractCreate(multipurposeContract)
                                .payingWith("civilian")
                                .balance(652),
                        contractCreate(lookupContract).payingWith("civilian").balance(256))
                .then(
                        contractCall(multipurposeContract, "believeIn", 256).payingWith("civilian"),
                        contractCallLocal(multipurposeContract, "pick")
                                .payingWith("civilian")
                                .logged()
                                .has(resultWith()
                                        .resultThruAbi(
                                                getABIFor(FUNCTION, "pick", multipurposeContract),
                                                isLiteralResult(new Object[] {BigInteger.valueOf(256)}))),
                        contractCall(multipurposeContract, "donate", donationArgs)
                                .payingWith("civilian"),
                        contractCallLocal(lookupContract, "lookup", spec -> new Object[] {
                                    spec.registry().getAccountID("civilian").getAccountNum()
                                })
                                .payingWith("civilian")
                                .logged());
    }

    HapiSpec txnGetCreateRecord() {
        return customHapiSpec("TxnGetCreateRecord")
                .withProperties(Map.of("cost.snapshot.mode", costSnapshotMode.toString()))
                .given(cryptoCreate("hairTriggerPayer").balance(99_999_999_999L).sendThreshold(1L))
                .when(cryptoCreate("somebodyElse")
                        .payingWith("hairTriggerPayer")
                        .via("txn"))
                .then(getTxnRecord("txn").logged());
    }

    HapiSpec txnGetSmallTransferRecord() {
        return customHapiSpec("TxnGetSmalTransferRecord")
                .withProperties(Map.of("cost.snapshot.mode", costSnapshotMode.toString()))
                .given(cryptoCreate("hairTriggerPayer").sendThreshold(1L))
                .when(cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L))
                        .payingWith("hairTriggerPayer")
                        .via("txn"))
                .then(getTxnRecord("txn").logged());
    }

    HapiSpec txnGetLargeTransferRecord() {
        return customHapiSpec("TxnGetLargeTransferRecord")
                .withProperties(Map.of("cost.snapshot.mode", costSnapshotMode.toString()))
                .given(
                        cryptoCreate("hairTriggerPayer").sendThreshold(1L),
                        cryptoCreate("a"),
                        cryptoCreate("b"),
                        cryptoCreate("c"),
                        cryptoCreate("d"))
                .when(cryptoTransfer(spec -> TransferList.newBuilder()
                                .addAccountAmounts(aa(spec, GENESIS, -4L))
                                .addAccountAmounts(aa(spec, "a", 1L))
                                .addAccountAmounts(aa(spec, "b", 1L))
                                .addAccountAmounts(aa(spec, "c", 1L))
                                .addAccountAmounts(aa(spec, "d", 1L))
                                .build())
                        .payingWith("hairTriggerPayer")
                        .via("txn"))
                .then(getTxnRecord("txn").logged());
    }

    private AccountAmount aa(HapiSpec spec, String id, long amount) {
        return AccountAmount.newBuilder()
                .setAmount(amount)
                .setAccountID(spec.registry().getAccountID(id))
                .build();
    }

    HapiSpec[] cryptoGetAccountRecordsPaths() {
        return new HapiSpec[] {
            cryptoGetRecordsHappyPathS(), cryptoGetRecordsHappyPathM(), cryptoGetRecordsHappyPathL(),
        };
    }

    HapiSpec cryptoGetRecordsHappyPathS() {
        return customHapiSpec("CryptoGetRecordsHappyPathS")
                .withProperties(Map.of("cost.snapshot.mode", costSnapshotMode.toString()))
                .given(cryptoCreate("hairTriggerPayer").sendThreshold(1L))
                .when(cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)).payingWith("hairTriggerPayer"))
                .then(getAccountRecords("hairTriggerPayer").has(inOrder(recordWith())));
    }

    HapiSpec cryptoGetRecordsHappyPathM() {
        return customHapiSpec("CryptoGetRecordsHappyPathM")
                .withProperties(Map.of("cost.snapshot.mode", costSnapshotMode.toString()))
                .given(cryptoCreate("hairTriggerPayer").sendThreshold(1L))
                .when(
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)).payingWith("hairTriggerPayer"),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)).payingWith("hairTriggerPayer"))
                .then(getAccountRecords("hairTriggerPayer").has(inOrder(recordWith(), recordWith())));
    }

    HapiSpec cryptoGetRecordsHappyPathL() {
        return customHapiSpec("CryptoGetRecordsHappyPathL")
                .withProperties(Map.of("cost.snapshot.mode", costSnapshotMode.toString()))
                .given(cryptoCreate("hairTriggerPayer").sendThreshold(1L))
                .when(
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)).payingWith("hairTriggerPayer"),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)).payingWith("hairTriggerPayer"),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)).payingWith("hairTriggerPayer"),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)).payingWith("hairTriggerPayer"),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)).payingWith("hairTriggerPayer"))
                .then(getAccountRecords("hairTriggerPayer")
                        .has(inOrder(recordWith(), recordWith(), recordWith(), recordWith(), recordWith())));
    }

    HapiSpec[] cryptoGetAccountInfoPaths() {
        return new HapiSpec[] {cryptoGetAccountInfoHappyPath()};
    }

    HapiSpec cryptoGetAccountInfoHappyPath() {
        KeyShape smallKey = threshOf(1, 3);
        KeyShape midsizeKey = listOf(SIMPLE, listOf(2), threshOf(1, 2));
        KeyShape hugeKey = threshOf(4, SIMPLE, SIMPLE, listOf(4), listOf(3), listOf(2));

        return customHapiSpec("CryptoGetAccountInfoHappyPath")
                .withProperties(Map.of("cost.snapshot.mode", costSnapshotMode.toString()))
                .given(
                        newKeyNamed("smallKey").shape(smallKey),
                        newKeyNamed("midsizeKey").shape(midsizeKey),
                        newKeyNamed("hugeKey").shape(hugeKey))
                .when(
                        cryptoCreate("small").key("smallKey"),
                        cryptoCreate("midsize").key("midsizeKey"),
                        cryptoCreate("huge").key("hugeKey"))
                .then(getAccountInfo("small"), getAccountInfo("midsize"), getAccountInfo("huge"));
    }

    HapiSpec[] cryptoCreatePaths() {
        return new HapiSpec[] {
            cryptoCreateSimpleKey(),
        };
    }

    HapiSpec cryptoCreateSimpleKey() {
        KeyShape shape = SIMPLE;

        return customHapiSpec("SuccessfulCryptoCreate")
                .withProperties(Map.of("cost.snapshot.mode", costSnapshotMode.toString()))
                .given(newKeyNamed("key").shape(shape))
                .when()
                .then(cryptoCreate("a").key("key"));
    }

    HapiSpec[] cryptoTransferPaths() {
        return new HapiSpec[] {
            cryptoTransferGenesisToFunding(),
        };
    }

    HapiSpec cryptoTransferGenesisToFunding() {
        return customHapiSpec("CryptoTransferGenesisToFunding")
                .withProperties(Map.of("cost.snapshot.mode", costSnapshotMode.toString()))
                .given()
                .when()
                .then(cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1_000L)));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
