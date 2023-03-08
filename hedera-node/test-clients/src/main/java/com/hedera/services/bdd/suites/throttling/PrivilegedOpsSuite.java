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

package com.hedera.services.bdd.suites.throttling;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileAppend;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.suites.utils.sysfiles.serdes.ThrottleDefsLoader.protoDefsFromResource;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTHORIZATION_FAILED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PrivilegedOpsSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(PrivilegedOpsSuite.class);

    private static final byte[] totalLimits =
            protoDefsFromResource("testSystemFiles/only-mint-allowed.json").toByteArray();
    private static final byte[] defaultThrottles =
            protoDefsFromResource("testSystemFiles/throttles-dev.json").toByteArray();
    private static final String ACCOUNT_88 = "0.0.88";
    private static final String ACCOUNT_2 = "0.0.2";
    private static final String CIVILIAN = "civilian";
    private static final String NEW_88 = "new88";
    private static final int BURST_SIZE = 10;

    public static void main(String... args) {
        new PrivilegedOpsSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(new HapiSpec[]{
                superusersAreNeverThrottledOnTransfers(),
                superusersAreNeverThrottledOnMiscTxns(),
                superusersAreNeverThrottledOnHcsTxns(),
                superusersAreNeverThrottledOnMiscQueries(),
                superusersAreNeverThrottledOnHcsQueries(),
                systemAccountUpdatePrivilegesAsExpected(),
                freezeAdminPrivilegesAsExpected(),
        });
    }

    Function<String, HapiSpecOperation[]> transferBurstFn = payer -> IntStream.range(0, BURST_SIZE)
            .mapToObj(ignore -> cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L))
                    .payingWith(payer)
                    .fee(ONE_HUNDRED_HBARS)
                    .deferStatusResolution())
            .toArray(n -> new HapiSpecOperation[n]);
    Function<String, HapiSpecOperation[]> miscTxnBurstFn = payer -> IntStream.range(0, BURST_SIZE)
            .mapToObj(i -> cryptoCreate(String.format("Account%d", i))
                    .payingWith(payer)
                    .deferStatusResolution())
            .toArray(n -> new HapiSpecOperation[n]);
    Function<String, HapiSpecOperation[]> hcsTxnBurstFn = payer -> IntStream.range(0, BURST_SIZE)
            .mapToObj(i ->
                    createTopic(String.format("Topic%d", i)).payingWith(payer).deferStatusResolution())
            .toArray(n -> new HapiSpecOperation[n]);
    Function<String, HapiSpecOperation[]> miscQueryBurstFn = payer -> IntStream.range(0, BURST_SIZE)
            .mapToObj(
                    i -> getAccountInfo(ADDRESS_BOOK_CONTROL).nodePayment(100L).payingWith(payer))
            .toArray(n -> new HapiSpecOperation[n]);
    Function<String, HapiSpecOperation[]> hcsQueryBurstFn = payer -> IntStream.range(0, BURST_SIZE)
            .mapToObj(i -> getTopicInfo("misc").nodePayment(100L).payingWith(payer))
            .toArray(n -> new HapiSpecOperation[n]);

    private HapiSpec freezeAdminPrivilegesAsExpected() {
        return defaultHapiSpec("FreezeAdminPrivilegesAsExpected")
                .given(
                        cryptoCreate(CIVILIAN),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, EXCHANGE_RATE_CONTROL, ONE_MILLION_HBARS)))
                .when(
                        fileUpdate(UPDATE_ZIP_FILE)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .contents("Nope")
                                .hasPrecheck(AUTHORIZATION_FAILED),
                        fileUpdate(UPDATE_ZIP_FILE)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .contents("Nope")
                                .hasPrecheck(AUTHORIZATION_FAILED),
                        fileUpdate(UPDATE_ZIP_FILE)
                                .payingWith(FEE_SCHEDULE_CONTROL)
                                .contents("Nope")
                                .hasPrecheck(AUTHORIZATION_FAILED),
                        fileUpdate(UPDATE_ZIP_FILE)
                                .payingWith(CIVILIAN)
                                .contents("Nope")
                                .hasPrecheck(AUTHORIZATION_FAILED))
                .then(
                        fileUpdate(UPDATE_ZIP_FILE)
                                .fee(0L)
                                .via("updateTxn")
                                .payingWith(FREEZE_ADMIN)
                                .contents("Yuu"),
                        getTxnRecord("updateTxn").showsNoTransfers(),
                        fileAppend(UPDATE_ZIP_FILE)
                                .fee(0L)
                                .via("appendTxn")
                                .payingWith(FREEZE_ADMIN)
                                .content("upp"),
                        getTxnRecord("appendTxn").showsNoTransfers(),
                        fileUpdate(UPDATE_ZIP_FILE)
                                .fee(0L)
                                .payingWith(SYSTEM_ADMIN)
                                .contents("Yuuupp"),
                        fileAppend(UPDATE_ZIP_FILE).fee(0L).payingWith(GENESIS).content(new byte[0]));
    }

    private HapiSpec systemAccountUpdatePrivilegesAsExpected() {
        final var tmpTreasury = "tmpTreasury";
        return defaultHapiSpec("SystemAccountUpdatePrivilegesAsExpected")
                .given(newKeyNamed(tmpTreasury), newKeyNamed(NEW_88), cryptoCreate(CIVILIAN))
                .when(cryptoUpdate(ACCOUNT_88)
                        .payingWith(GENESIS)
                        .signedBy(GENESIS, NEW_88)
                        .key(NEW_88))
                .then(
                        cryptoUpdate(ACCOUNT_2)
                                .receiverSigRequired(true)
                                .payingWith(CIVILIAN)
                                .signedBy(CIVILIAN, GENESIS)
                                .hasPrecheck(AUTHORIZATION_FAILED),
                        cryptoUpdate(ACCOUNT_2)
                                .receiverSigRequired(true)
                                .payingWith(SYSTEM_ADMIN)
                                .signedBy(SYSTEM_ADMIN, GENESIS)
                                .hasPrecheck(AUTHORIZATION_FAILED),
                        cryptoUpdate(ACCOUNT_2)
                                .receiverSigRequired(false)
                                .payingWith(GENESIS)
                                .signedBy(GENESIS),
                        cryptoUpdate(ACCOUNT_2)
                                .key(tmpTreasury)
                                .payingWith(GENESIS)
                                .signedBy(GENESIS)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        cryptoUpdate(ACCOUNT_2)
                                .key(tmpTreasury)
                                .payingWith(GENESIS)
                                .signedBy(GENESIS, tmpTreasury)
                                .notUpdatingRegistryWithNewKey(),
                        cryptoUpdate(ACCOUNT_2)
                                .key(GENESIS)
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(GENESIS)
                                .signedBy(GENESIS, tmpTreasury)
                                .notUpdatingRegistryWithNewKey(),
                        cryptoUpdate(ACCOUNT_88)
                                .key(GENESIS)
                                .payingWith(CIVILIAN)
                                .signedBy(CIVILIAN, NEW_88, GENESIS),
                        cryptoUpdate(ACCOUNT_88)
                                .payingWith(GENESIS)
                                .signedBy(GENESIS, NEW_88)
                                .key(NEW_88),
                        cryptoUpdate(ACCOUNT_88)
                                .key(GENESIS)
                                .payingWith(SYSTEM_ADMIN)
                                .signedBy(SYSTEM_ADMIN, GENESIS));
    }

    private HapiSpec superusersAreNeverThrottledOnTransfers() {
        return defaultHapiSpec("SuperusersAreNeverThrottledOnTransfers")
                .given(
                        cryptoTransfer(tinyBarsFromTo(GENESIS, ADDRESS_BOOK_CONTROL, 1_000_000_000_000L))
                                .fee(ONE_HUNDRED_HBARS),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, SYSTEM_ADMIN, 1_000_000_000_000L))
                                .fee(ONE_HUNDRED_HBARS))
                .when(fileUpdate(THROTTLE_DEFS)
                        .payingWith(EXCHANGE_RATE_CONTROL)
                        .contents(totalLimits)
                        .hasKnownStatus(SUCCESS_BUT_MISSING_EXPECTED_OPERATION))
                .then(flattened(
                        transferBurstFn.apply(SYSTEM_ADMIN),
                        transferBurstFn.apply(ADDRESS_BOOK_CONTROL),
                        sleepFor(5_000L),
                        fileUpdate(THROTTLE_DEFS)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .contents(defaultThrottles)));
    }

    private HapiSpec superusersAreNeverThrottledOnMiscTxns() {
        return defaultHapiSpec("MasterIsNeverThrottledOnMiscTxns")
                .given(
                        cryptoTransfer(tinyBarsFromTo(GENESIS, ADDRESS_BOOK_CONTROL, 1_000_000_000_000L))
                                .fee(ONE_HUNDRED_HBARS),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, SYSTEM_ADMIN, 1_000_000_000_000L))
                                .fee(ONE_HUNDRED_HBARS))
                .when(fileUpdate(THROTTLE_DEFS)
                        .payingWith(EXCHANGE_RATE_CONTROL)
                        .contents(totalLimits)
                        .hasKnownStatus(SUCCESS_BUT_MISSING_EXPECTED_OPERATION))
                .then(flattened(
                        miscTxnBurstFn.apply(SYSTEM_ADMIN),
                        miscTxnBurstFn.apply(ADDRESS_BOOK_CONTROL),
                        sleepFor(5_000L),
                        fileUpdate(THROTTLE_DEFS)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .contents(defaultThrottles)));
    }

    private HapiSpec superusersAreNeverThrottledOnHcsTxns() {
        return defaultHapiSpec("MasterIsNeverThrottledOnHcsTxns")
                .given(
                        cryptoTransfer(tinyBarsFromTo(GENESIS, ADDRESS_BOOK_CONTROL, 1_000_000_000_000L)),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, SYSTEM_ADMIN, 1_000_000_000_000L)))
                .when(fileUpdate(THROTTLE_DEFS)
                        .payingWith(EXCHANGE_RATE_CONTROL)
                        .contents(totalLimits)
                        .hasKnownStatus(SUCCESS_BUT_MISSING_EXPECTED_OPERATION))
                .then(flattened(
                        hcsTxnBurstFn.apply(SYSTEM_ADMIN),
                        hcsTxnBurstFn.apply(ADDRESS_BOOK_CONTROL),
                        fileUpdate(THROTTLE_DEFS)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .contents(defaultThrottles)));
    }

    private HapiSpec superusersAreNeverThrottledOnMiscQueries() {
        return defaultHapiSpec("MasterIsNeverThrottledOnMiscQueries")
                .given(
                        cryptoTransfer(tinyBarsFromTo(GENESIS, ADDRESS_BOOK_CONTROL, 1_000_000_000_000L)),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, SYSTEM_ADMIN, 1_000_000_000_000L)))
                .when(fileUpdate(THROTTLE_DEFS)
                        .payingWith(EXCHANGE_RATE_CONTROL)
                        .contents(totalLimits)
                        .hasKnownStatus(SUCCESS_BUT_MISSING_EXPECTED_OPERATION))
                .then(flattened(
                        inParallel(miscQueryBurstFn.apply(SYSTEM_ADMIN)),
                        inParallel(miscQueryBurstFn.apply(ADDRESS_BOOK_CONTROL)),
                        fileUpdate(THROTTLE_DEFS)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .contents(defaultThrottles)));
    }

    private HapiSpec superusersAreNeverThrottledOnHcsQueries() {
        return defaultHapiSpec("MasterIsNeverThrottledOnHcsQueries")
                .given(
                        cryptoTransfer(tinyBarsFromTo(GENESIS, ADDRESS_BOOK_CONTROL, 1_000_000_000_000L)),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, SYSTEM_ADMIN, 1_000_000_000_000L)),
                        createTopic("misc"))
                .when(fileUpdate(THROTTLE_DEFS)
                        .payingWith(EXCHANGE_RATE_CONTROL)
                        .contents(totalLimits)
                        .hasKnownStatus(SUCCESS_BUT_MISSING_EXPECTED_OPERATION))
                .then(flattened(
                        inParallel(hcsQueryBurstFn.apply(SYSTEM_ADMIN)),
                        inParallel(hcsQueryBurstFn.apply(ADDRESS_BOOK_CONTROL)),
                        fileUpdate(THROTTLE_DEFS)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .contents(defaultThrottles)));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
