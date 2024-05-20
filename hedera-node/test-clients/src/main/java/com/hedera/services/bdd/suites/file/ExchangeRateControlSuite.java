/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.file;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.suites.HapiSuite.ADEQUATE_FUNDS;
import static com.hedera.services.bdd.suites.HapiSuite.EXCHANGE_RATES;
import static com.hedera.services.bdd.suites.HapiSuite.EXCHANGE_RATE_CONTROL;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.SYSTEM_ADMIN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTHORIZATION_FAILED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXCHANGE_RATE_CHANGE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.OrderedInIsolation;
import com.hedera.services.bdd.spec.transactions.file.HapiFileUpdate;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

@OrderedInIsolation
public class ExchangeRateControlSuite {
    final HapiFileUpdate resetRatesOp = fileUpdate(EXCHANGE_RATES)
            .payingWith(EXCHANGE_RATE_CONTROL)
            .fee(ADEQUATE_FUNDS)
            .contents(spec -> spec.ratesProvider().rateSetWith(1, 12).toByteString());

    @HapiTest
    final Stream<DynamicTest> acct57CanMakeSmallChanges() {
        return defaultHapiSpec("Acct57CanMakeSmallChanges")
                .given(
                        resetRatesOp,
                        cryptoTransfer(tinyBarsFromTo(GENESIS, EXCHANGE_RATE_CONTROL, ADEQUATE_FUNDS))
                                .fee(ONE_HUNDRED_HBARS))
                .when(fileUpdate(EXCHANGE_RATES)
                        .contents(spec -> {
                            ByteString newRates =
                                    spec.ratesProvider().rateSetWith(10, 121).toByteString();
                            spec.registry().saveBytes("newRates", newRates);
                            return newRates;
                        })
                        .payingWith(EXCHANGE_RATE_CONTROL))
                .then(
                        getFileContents(EXCHANGE_RATES)
                                .hasContents(spec -> spec.registry().getBytes("newRates")),
                        resetRatesOp);
    }

    @HapiTest
    final Stream<DynamicTest> midnightRateChangesWhenAcct50UpdatesFile112() {
        return defaultHapiSpec("MidnightRateChangesWhenAcct50UpdatesFile112")
                .given(
                        resetRatesOp,
                        cryptoTransfer(tinyBarsFromTo(GENESIS, EXCHANGE_RATE_CONTROL, ADEQUATE_FUNDS))
                                .fee(ONE_HUNDRED_HBARS),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, SYSTEM_ADMIN, ADEQUATE_FUNDS))
                                .fee(ONE_HUNDRED_HBARS),
                        fileUpdate(EXCHANGE_RATES)
                                .contents(spec -> {
                                    ByteString newRates = spec.ratesProvider()
                                            .rateSetWith(10, 254)
                                            .toByteString();
                                    spec.registry().saveBytes("newRates", newRates);
                                    return newRates;
                                })
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .fee(1_000_000_000)
                                .hasKnownStatus(EXCHANGE_RATE_CHANGE_LIMIT_EXCEEDED))
                .when(fileUpdate(EXCHANGE_RATES)
                        .contents(spec -> {
                            ByteString newRates =
                                    spec.ratesProvider().rateSetWith(1, 25).toByteString();
                            spec.registry().saveBytes("newRates", newRates);
                            return newRates;
                        })
                        .payingWith(SYSTEM_ADMIN)
                        .fee(1_000_000_000))
                .then(
                        fileUpdate(EXCHANGE_RATES)
                                .contents(spec -> {
                                    ByteString newRates = spec.ratesProvider()
                                            .rateSetWith(10, 254)
                                            .toByteString();
                                    spec.registry().saveBytes("newRates", newRates);
                                    return newRates;
                                })
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .fee(1_000_000_000)
                                .hasKnownStatus(SUCCESS),
                        fileUpdate(EXCHANGE_RATES)
                                .contents(spec -> {
                                    ByteString newRates = spec.ratesProvider()
                                            .rateSetWith(1, 12, 1, 15)
                                            .toByteString();
                                    spec.registry().saveBytes("newRates", newRates);
                                    return newRates;
                                })
                                .payingWith(SYSTEM_ADMIN)
                                .fee(1_000_000_000)
                                .hasKnownStatus(SUCCESS));
    }

    @HapiTest
    final Stream<DynamicTest> anonCantUpdateRates() {
        return defaultHapiSpec("AnonCantUpdateRates")
                .given(resetRatesOp, cryptoCreate("randomAccount"))
                .when()
                .then(fileUpdate(EXCHANGE_RATES)
                        .contents("Should be impossible!")
                        .payingWith("randomAccount")
                        .hasPrecheck(AUTHORIZATION_FAILED));
    }

    @HapiTest
    final Stream<DynamicTest> acct57CantMakeLargeChanges() {
        return defaultHapiSpec("Acct57CantMakeLargeChanges")
                .given(
                        resetRatesOp,
                        cryptoTransfer(tinyBarsFromTo(GENESIS, EXCHANGE_RATE_CONTROL, ADEQUATE_FUNDS))
                                .fee(ONE_HUNDRED_HBARS))
                .when()
                .then(fileUpdate(EXCHANGE_RATES)
                        .contents(
                                spec -> spec.ratesProvider().rateSetWith(1, 25).toByteString())
                        .payingWith(EXCHANGE_RATE_CONTROL)
                        .hasKnownStatus(EXCHANGE_RATE_CHANGE_LIMIT_EXCEEDED));
    }
}
