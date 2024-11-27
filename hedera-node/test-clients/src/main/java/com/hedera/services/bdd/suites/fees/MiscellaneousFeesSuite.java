package com.hedera.services.bdd.suites.fees;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.hapiPrng;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingAllOf;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsd;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;

import com.hedera.services.bdd.junit.HapiTest;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

public class MiscellaneousFeesSuite {
    private static final String PRNG_IS_ENABLED = "utilPrng.isEnabled";
    public static final String BOB = "bob";

    @HapiTest
    final Stream<DynamicTest> usdFeeAsExpected() {
        double baseFee = 0.001;
        double plusRangeFee = 0.0010010316;

        final var baseTxn = "prng";
        final var plusRangeTxn = "prngWithRange";

        return defaultHapiSpec("usdFeeAsExpected")
                .given(
                        overridingAllOf(Map.of(PRNG_IS_ENABLED, "true")),
                        cryptoCreate(BOB).balance(ONE_HUNDRED_HBARS),
                        hapiPrng().payingWith(BOB).via(baseTxn).blankMemo().logged(),
                        getTxnRecord(baseTxn).hasOnlyPseudoRandomBytes().logged(),
                        validateChargedUsd(baseTxn, baseFee))
                .when(
                        hapiPrng(10)
                                .payingWith(BOB)
                                .via(plusRangeTxn)
                                .blankMemo()
                                .logged(),
                        getTxnRecord(plusRangeTxn)
                                .hasOnlyPseudoRandomNumberInRange(10)
                                .logged(),
                        validateChargedUsdWithin(plusRangeTxn, plusRangeFee, 0.5))
                .then();
    }
}
