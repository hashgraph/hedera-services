/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.fees;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BasicHbarCentExchangeTest {
    private static final long crossoverTime = 1_234_567L;
    private static final ExchangeRateSet rates =
            ExchangeRateSet.newBuilder()
                    .setCurrentRate(
                            ExchangeRate.newBuilder()
                                    .setHbarEquiv(1)
                                    .setCentEquiv(12)
                                    .setExpirationTime(
                                            TimestampSeconds.newBuilder()
                                                    .setSeconds(crossoverTime)))
                    .setNextRate(
                            ExchangeRate.newBuilder()
                                    .setExpirationTime(
                                            TimestampSeconds.newBuilder()
                                                    .setSeconds(crossoverTime * 2))
                                    .setHbarEquiv(1)
                                    .setCentEquiv(24))
                    .build();

    private BasicHbarCentExchange subject;

    @BeforeEach
    void setUp() {
        subject = new BasicHbarCentExchange();
    }

    @Test
    void updatesWorkWithCurrentRate() {
        subject.updateRates(rates);

        assertEquals(rates, subject.activeRates());
        assertEquals(rates.getCurrentRate(), subject.activeRate(beforeCrossInstant));
        assertEquals(rates.getCurrentRate(), subject.rate(beforeCrossTime));
        assertEquals(rates, subject.fcActiveRates().toGrpc());
    }

    @Test
    void updatesWorkWithNextRate() {
        subject.updateRates(rates);

        assertEquals(rates.getNextRate(), subject.activeRate(afterCrossInstant));
        assertEquals(rates.getNextRate(), subject.rate(afterCrossTime));
        assertEquals(rates, subject.fcActiveRates().toGrpc());
    }

    private static final Timestamp beforeCrossTime =
            Timestamp.newBuilder().setSeconds(crossoverTime - 1).build();
    private static final Timestamp afterCrossTime =
            Timestamp.newBuilder().setSeconds(crossoverTime).build();
    private static final Instant afterCrossInstant = Instant.ofEpochSecond(crossoverTime);
    private static final Instant beforeCrossInstant = Instant.ofEpochSecond(crossoverTime - 1);
}
