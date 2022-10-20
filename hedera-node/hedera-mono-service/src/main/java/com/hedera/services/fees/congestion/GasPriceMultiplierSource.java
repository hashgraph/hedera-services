package com.hedera.services.fees.congestion;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.annotations.GasPriceMultiplier;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.services.throttling.annotations.HandleThrottle;
import com.hedera.services.utils.accessors.TxnAccessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.List;

@Singleton
public class GasPriceMultiplierSource extends DelegatingMultiplierSource {
    private static final Logger log = LogManager.getLogger(GasPriceMultiplierSource.class);

    @Inject
    public GasPriceMultiplierSource(
            final GlobalDynamicProperties properties,
            @HandleThrottle FunctionalityThrottling throttling) {
                super(new ThrottleMultiplierSource(
                        "EVM gas/sec",
                        "gas/sec",
                        "EVM utilization",
                        log,
                        properties::feesMinCongestionPeriod,
                        properties::congestionMultipliers,
                        () -> List.of(throttling.gasLimitThrottle())));
    }
}
