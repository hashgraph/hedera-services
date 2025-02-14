// SPDX-License-Identifier: Apache-2.0
package com.hedera.services.bdd.suites.file;

import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
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
        return hapiTest(
                resetRatesOp,
                cryptoTransfer(tinyBarsFromTo(GENESIS, EXCHANGE_RATE_CONTROL, ADEQUATE_FUNDS))
                        .fee(ONE_HUNDRED_HBARS),
                fileUpdate(EXCHANGE_RATES)
                        .contents(spec -> {
                            ByteString newRates =
                                    spec.ratesProvider().rateSetWith(10, 121).toByteString();
                            spec.registry().saveBytes("newRates", newRates);
                            return newRates;
                        })
                        .payingWith(EXCHANGE_RATE_CONTROL),
                getFileContents(EXCHANGE_RATES)
                        .hasContents(spec -> spec.registry().getBytes("newRates")),
                resetRatesOp);
    }

    @HapiTest
    final Stream<DynamicTest> midnightRateChangesWhenAcct50UpdatesFile112() {
        return hapiTest(
                resetRatesOp,
                cryptoTransfer(tinyBarsFromTo(GENESIS, EXCHANGE_RATE_CONTROL, ADEQUATE_FUNDS))
                        .fee(ONE_HUNDRED_HBARS),
                cryptoTransfer(tinyBarsFromTo(GENESIS, SYSTEM_ADMIN, ADEQUATE_FUNDS))
                        .fee(ONE_HUNDRED_HBARS),
                fileUpdate(EXCHANGE_RATES)
                        .contents(spec -> {
                            ByteString newRates =
                                    spec.ratesProvider().rateSetWith(10, 254).toByteString();
                            spec.registry().saveBytes("newRates", newRates);
                            return newRates;
                        })
                        .payingWith(EXCHANGE_RATE_CONTROL)
                        .fee(1_000_000_000)
                        .hasKnownStatus(EXCHANGE_RATE_CHANGE_LIMIT_EXCEEDED),
                fileUpdate(EXCHANGE_RATES)
                        .contents(spec -> {
                            ByteString newRates =
                                    spec.ratesProvider().rateSetWith(1, 25).toByteString();
                            spec.registry().saveBytes("newRates", newRates);
                            return newRates;
                        })
                        .payingWith(SYSTEM_ADMIN)
                        .fee(1_000_000_000),
                fileUpdate(EXCHANGE_RATES)
                        .contents(spec -> {
                            ByteString newRates =
                                    spec.ratesProvider().rateSetWith(10, 254).toByteString();
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
        return hapiTest(
                resetRatesOp,
                cryptoCreate("randomAccount"),
                fileUpdate(EXCHANGE_RATES)
                        .contents("Should be impossible!")
                        .payingWith("randomAccount")
                        .hasPrecheck(AUTHORIZATION_FAILED));
    }

    @HapiTest
    final Stream<DynamicTest> acct57CantMakeLargeChanges() {
        return hapiTest(
                resetRatesOp,
                cryptoTransfer(tinyBarsFromTo(GENESIS, EXCHANGE_RATE_CONTROL, ADEQUATE_FUNDS))
                        .fee(ONE_HUNDRED_HBARS),
                fileUpdate(EXCHANGE_RATES)
                        .contents(
                                spec -> spec.ratesProvider().rateSetWith(1, 25).toByteString())
                        .payingWith(EXCHANGE_RATE_CONTROL)
                        .hasKnownStatus(EXCHANGE_RATE_CHANGE_LIMIT_EXCEEDED));
    }
}
