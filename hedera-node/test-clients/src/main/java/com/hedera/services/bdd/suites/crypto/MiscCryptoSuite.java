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

package com.hedera.services.bdd.suites.crypto;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.reduceFeeFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MiscCryptoSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(MiscCryptoSuite.class);

    public static void main(String... args) {
        new MiscCryptoSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return allOf(
                positiveTests()
                //				negativeTests()
                );
    }

    private List<HapiSpec> positiveTests() {
        return Arrays.asList(
                //				transferChangesBalance()
                //				getsGenesisBalance()
                //				reduceTransferFee(),
                sysAccountKeyUpdateBySpecialWontNeedNewKeyTxnSign());
    }

    private List<HapiSpec> negativeTests() {
        return List.of(updateWithOutOfDateKeyFails());
    }

    private HapiSpec sysAccountKeyUpdateBySpecialWontNeedNewKeyTxnSign() {
        String sysAccount = "0.0.977";
        String randomAccountA = "randomAccountA";
        String randomAccountB = "randomAccountB";
        String firstKey = "firstKey";
        String secondKey = "secondKey";

        return defaultHapiSpec("sysAccountKeyUpdateBySpecialWontNeedNewKeyTxnSign")
                .given(
                        withOpContext((spec, opLog) -> {
                            spec.registry().saveKey(sysAccount, spec.registry().getKey(GENESIS));
                        }),
                        newKeyNamed(firstKey).shape(SIMPLE),
                        newKeyNamed(secondKey).shape(SIMPLE))
                .when(
                        cryptoCreate(randomAccountA).key(firstKey),
                        cryptoCreate(randomAccountB).key(firstKey).balance(ONE_HUNDRED_HBARS))
                .then(
                        cryptoUpdate(sysAccount)
                                .key(secondKey)
                                .payingWith(GENESIS)
                                .hasKnownStatus(SUCCESS)
                                .logged(),
                        cryptoUpdate(randomAccountA)
                                .key(secondKey)
                                .signedBy(firstKey)
                                .payingWith(randomAccountB)
                                .hasKnownStatus(INVALID_SIGNATURE));
    }

    private HapiSpec reduceTransferFee() {
        final long REDUCED_NODE_FEE = 2L;
        final long REDUCED_NETWORK_FEE = 3L;
        final long REDUCED_SERVICE_FEE = 3L;
        final long REDUCED_TOTAL_FEE = REDUCED_NODE_FEE + REDUCED_NETWORK_FEE + REDUCED_SERVICE_FEE;
        return defaultHapiSpec("ReduceTransferFee")
                .given(
                        cryptoCreate("sender").balance(ONE_HUNDRED_HBARS),
                        cryptoCreate("receiver").balance(0L),
                        cryptoTransfer(tinyBarsFromTo("sender", "receiver", ONE_HBAR))
                                .payingWith("sender")
                                .fee(REDUCED_TOTAL_FEE)
                                .hasPrecheck(INSUFFICIENT_TX_FEE))
                .when(reduceFeeFor(CryptoTransfer, REDUCED_NODE_FEE, REDUCED_NETWORK_FEE, REDUCED_SERVICE_FEE))
                .then(
                        cryptoTransfer(tinyBarsFromTo("sender", "receiver", ONE_HBAR))
                                .payingWith("sender")
                                .fee(ONE_HBAR)
                                .hasPrecheck(OK),
                        getAccountBalance("sender")
                                .hasTinyBars(ONE_HUNDRED_HBARS - ONE_HBAR - REDUCED_TOTAL_FEE)
                                .logged());
    }

    public static HapiSpec getsGenesisBalance() {
        return defaultHapiSpec("GetsGenesisBalance")
                .given()
                .when()
                .then(getAccountBalance(GENESIS).logged());
    }

    public static HapiSpec transferChangesBalance() {
        return defaultHapiSpec("TransferChangesBalance")
                .given(cryptoCreate("newPayee").balance(0L))
                .when(cryptoTransfer(tinyBarsFromTo(GENESIS, "newPayee", 1_000_000_000L)))
                .then(getAccountBalance("newPayee").hasTinyBars(1_000_000_000L).logged());
    }

    private HapiSpec updateWithOutOfDateKeyFails() {
        return defaultHapiSpec("UpdateWithOutOfDateKeyFails")
                .given(
                        newKeyNamed("originalKey"),
                        newKeyNamed("updateKey"),
                        cryptoCreate("targetAccount").key("originalKey"))
                .when(
                        cryptoUpdate("targetAccount").key("updateKey").deferStatusResolution(),
                        cryptoUpdate("targetAccount")
                                .receiverSigRequired(true)
                                .signedBy(GENESIS, "originalKey")
                                .via("invalidKeyUpdateTxn")
                                .deferStatusResolution()
                                .hasAnyKnownStatus(),
                        sleepFor(1_000L))
                .then(getTxnRecord("invalidKeyUpdateTxn")
                        .hasPriority(recordWith().status(INVALID_SIGNATURE)));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
