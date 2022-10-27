/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.fees.congestion;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.services.throttling.annotations.HandleThrottle;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class GasPriceMultiplierSource extends DelegatingMultiplierSource {
    private static final Logger log = LogManager.getLogger(GasPriceMultiplierSource.class);

    @Inject
    public GasPriceMultiplierSource(
            final GlobalDynamicProperties properties,
            @HandleThrottle FunctionalityThrottling throttling) {
        super(
                new ThrottleMultiplierSource(
                        "EVM gas/sec",
                        "gas/sec",
                        "EVM utilization",
                        log,
                        properties::feesMinCongestionPeriod,
                        properties::congestionMultipliers,
                        () -> List.of(throttling.gasLimitThrottle())));
    }
}
