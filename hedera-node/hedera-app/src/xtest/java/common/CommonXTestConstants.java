/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
