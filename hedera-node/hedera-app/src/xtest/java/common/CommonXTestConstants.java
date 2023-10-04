package common;

import com.hedera.hapi.node.base.TimestampSeconds;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.ExchangeRateSet;

import java.time.Instant;

public class CommonXTestConstants {
    /**
     * The exchange rate long used in dev environments to run HAPI spec; expected to be in effect for
     * some x-tests to pass.
     */
    public static final ExchangeRate TRADITIONAL_HAPI_SPEC_RATE = ExchangeRate.newBuilder()
            .hbarEquiv(1)
            .centEquiv(12)
            .expirationTime(TimestampSeconds.newBuilder()
                    .seconds(Instant.MAX.getEpochSecond())
                    .build())
            .build();
    public static final ExchangeRateSet SET_OF_TRADITIONAL_RATES = ExchangeRateSet.newBuilder()
            .currentRate(TRADITIONAL_HAPI_SPEC_RATE)
            .nextRate(TRADITIONAL_HAPI_SPEC_RATE)
            .build();
}
