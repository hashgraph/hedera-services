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

package com.hedera.services.bdd.suites.misc;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeOnly;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.tokenOpsEnablement;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.perf.PerfUtilOps;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MixedOpsTransactionsSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(MixedOpsTransactionsSuite.class);
    private static final String SENDER = "sender";

    public static void main(String... args) {
        new MixedOpsTransactionsSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(new HapiSpec[] {createStateWithMixedOps()
            //						triggerSavedScheduleTxn(),
        });
    }

    private HapiSpec triggerSavedScheduleTxn() {
        return HapiSpec.defaultHapiSpec("triggerSavedScheduleTxn")
                .given(getAccountBalance("0.0.1002").hasTinyBars(0L))
                .when(scheduleSign("0.0.1016").logged().alsoSigningWith(GENESIS))
                .then(getAccountBalance("0.0.1002").hasTinyBars(1L));
    }
    // Used to generate state with mixed operations
    private HapiSpec createStateWithMixedOps() {
        long ONE_YEAR_IN_SECS = 365 * 24 * 60 * 60;
        int numScheduledTxns = 10;
        return HapiSpec.defaultHapiSpec("createStateWithMixedOps")
                .given(
                        PerfUtilOps.scheduleOpsEnablement(),
                        tokenOpsEnablement(),
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(GENESIS)
                                .overridingProps(Map.of("ledger.schedule.txExpiryTimeSecs", "" + ONE_YEAR_IN_SECS)),
                        sleepFor(10000),
                        cryptoCreate(SENDER).advertisingCreation().balance(ONE_HBAR),
                        cryptoCreate("receiver")
                                .key(GENESIS)
                                .advertisingCreation()
                                .balance(0L)
                                .receiverSigRequired(true),
                        cryptoCreate("tokenTreasury")
                                .key(GENESIS)
                                .advertisingCreation()
                                .balance(ONE_HBAR),
                        tokenCreate("wellKnown").advertisingCreation().initialSupply(Long.MAX_VALUE),
                        cryptoCreate("tokenReceiver").advertisingCreation(),
                        tokenAssociate("tokenReceiver", "wellKnown"),
                        createTopic("wellKnownTopic").advertisingCreation())
                .when(IntStream.range(0, numScheduledTxns)
                        .mapToObj(i -> scheduleCreate(
                                        "schedule" + i, cryptoTransfer(tinyBarsFromTo(SENDER, "receiver", 1)))
                                .advertisingCreation()
                                .fee(ONE_HUNDRED_HBARS)
                                .signedBy(DEFAULT_PAYER)
                                .alsoSigningWith(SENDER)
                                .withEntityMemo("This is the " + i + "th scheduled txn."))
                        .toArray(HapiSpecOperation[]::new))
                .then(freezeOnly().payingWith(GENESIS).startingIn(60).seconds());
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
