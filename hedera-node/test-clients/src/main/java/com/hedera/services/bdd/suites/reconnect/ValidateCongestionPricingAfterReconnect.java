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

package com.hedera.services.bdd.suites.reconnect;

import static com.hedera.services.bdd.spec.HapiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uncheckedSubmit;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockingOrder;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.usableTxnIdNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withLiveNode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.reconnect.AutoRenewEntitiesForReconnect.runTransfersBeforeReconnect;
import static com.hedera.services.bdd.suites.reconnect.ValidateTokensStateAfterReconnect.reconnectingNode;
import static com.hedera.services.bdd.suites.utils.sysfiles.serdes.ThrottleDefsLoader.protoDefsFromResource;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

/**
 * A reconnect test in which a congestion pricing multiplier is updated and triggered while the node 0.0.8 is
 * disconnected from the network. Once the node is reconnected validate that the congestion pricing is in affect on
 * reconnected node
 */
public class ValidateCongestionPricingAfterReconnect extends HapiSuite {
    private static final Logger log = LogManager.getLogger(ValidateCongestionPricingAfterReconnect.class);
    private static final String FEES_PERCENT_CONGESTION_MULTIPLIERS = "fees.percentCongestionMultipliers";
    private static final String defaultCongestionMultipliers =
            HapiSpecSetup.getDefaultNodeProps().get(FEES_PERCENT_CONGESTION_MULTIPLIERS);
    private static final String FEES_MIN_CONGESTION_PERIOD = "fees.minCongestionPeriod";
    private static final String defaultMinCongestionPeriod =
            HapiSpecSetup.getDefaultNodeProps().get(FEES_MIN_CONGESTION_PERIOD);

    public static void main(String... args) {
        new ValidateAppPropertiesStateAfterReconnect().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(new HapiSpec[] {
            runTransfersBeforeReconnect(), validateCongestionPricing(),
        });
    }

    private HapiSpec validateCongestionPricing() {
        var artificialLimits = protoDefsFromResource("testSystemFiles/artificial-limits-6N.json");
        var defaultThrottles = protoDefsFromResource("testSystemFiles/throttles-dev.json");
        String tmpMinCongestionPeriodInSecs = "5";
        String civilianAccount = "civilian";
        String oneContract = "Multipurpose";

        AtomicLong normalPrice = new AtomicLong();
        AtomicLong tenXPrice = new AtomicLong();

        return customHapiSpec("ValidateCongestionPricing")
                .withProperties(Map.of("txn.start.offset.secs", "-5"))
                .given(
                        cryptoCreate(civilianAccount).payingWith(GENESIS).balance(ONE_MILLION_HBARS),
                        uploadInitCode(oneContract),
                        contractCreate(oneContract).payingWith(GENESIS).logging(),
                        contractCall(oneContract)
                                .payingWith(civilianAccount)
                                .fee(ONE_HUNDRED_HBARS)
                                .sending(ONE_HBAR)
                                .via("cheapCallBeforeCongestionPricing"),
                        getTxnRecord("cheapCallBeforeCongestionPricing").providingFeeTo(normalPrice::set),
                        sleepFor(30000),
                        getAccountBalance(GENESIS).setNode(reconnectingNode).unavailableNode())
                .when(
                        /* update the multiplier to 10x with a 1% congestion for tmpMinCongestionPeriodInSecs */
                        fileUpdate(APP_PROPERTIES)
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .overridingProps(Map.of(
                                        FEES_PERCENT_CONGESTION_MULTIPLIERS,
                                        "1,10x",
                                        FEES_MIN_CONGESTION_PERIOD,
                                        tmpMinCongestionPeriodInSecs)),
                        fileUpdate(THROTTLE_DEFS)
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .contents(artificialLimits.toByteArray()),
                        blockingOrder(IntStream.range(0, 110)
                                .mapToObj(i -> new HapiSpecOperation[] {
                                    usableTxnIdNamed("uncheckedTxn1" + i).payerId(civilianAccount),
                                    uncheckedSubmit(contractCall(oneContract)
                                                    .signedBy(civilianAccount)
                                                    .fee(ONE_HUNDRED_HBARS)
                                                    .sending(ONE_HBAR)
                                                    .txnId("uncheckedTxn1" + i))
                                            .payingWith(GENESIS),
                                    sleepFor(50)
                                })
                                .flatMap(Arrays::stream)
                                .toArray(HapiSpecOperation[]::new)))
                .then(
                        withLiveNode(reconnectingNode)
                                .within(5 * 60, TimeUnit.SECONDS)
                                .loggingAvailabilityEvery(30)
                                .sleepingBetweenRetriesFor(10),
                        // So although currently disconnected node is in ACTIVE mode, it might
                        // immediately enter BEHIND mode and start a reconnection session.
                        // We need to wait for the reconnection session to finish and the platform
                        // becomes ACTIVE again,
                        // then we can send more transactions. Otherwise, transactions may be
                        // pending for too long
                        // and we will get UNKNOWN status
                        sleepFor(80000),
                        blockingOrder(IntStream.range(0, 110)
                                .mapToObj(i -> new HapiSpecOperation[] {
                                    usableTxnIdNamed("uncheckedTxn2" + i).payerId(civilianAccount),
                                    uncheckedSubmit(contractCall(oneContract)
                                                    .signedBy(civilianAccount)
                                                    .fee(ONE_HUNDRED_HBARS)
                                                    .sending(ONE_HBAR)
                                                    .txnId("uncheckedTxn2" + i))
                                            .payingWith(GENESIS)
                                            .setNode(reconnectingNode),
                                    sleepFor(50)
                                })
                                .flatMap(Arrays::stream)
                                .toArray(HapiSpecOperation[]::new)),
                        contractCall(oneContract)
                                .payingWith(civilianAccount)
                                .fee(ONE_HUNDRED_HBARS)
                                .sending(ONE_HBAR)
                                .via("pricyCallAfterReconnect")
                                .setNode(reconnectingNode),
                        getTxnRecord("pricyCallAfterReconnect")
                                .payingWith(GENESIS)
                                .providingFeeTo(tenXPrice::set)
                                .setNode(reconnectingNode),

                        /* check if the multiplier took effect in the contract call operation */
                        withOpContext((spec, opLog) -> Assertions.assertEquals(
                                10.0,
                                (1.0 * tenXPrice.get()) / normalPrice.get(),
                                0.1,
                                "~10x multiplier should be in affect!")),

                        /* revert the multiplier before test ends */
                        fileUpdate(THROTTLE_DEFS)
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .contents(defaultThrottles.toByteArray())
                                .setNode(reconnectingNode),
                        fileUpdate(APP_PROPERTIES)
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .overridingProps(Map.of(
                                        FEES_PERCENT_CONGESTION_MULTIPLIERS, defaultCongestionMultipliers,
                                        FEES_MIN_CONGESTION_PERIOD, defaultMinCongestionPeriod)),
                        cryptoTransfer(HapiCryptoTransfer.tinyBarsFromTo(GENESIS, FUNDING, 1))
                                .payingWith(GENESIS));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
