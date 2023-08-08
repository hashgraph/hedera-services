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

package com.hedera.node.app.fees;

import static com.hedera.hapi.node.transaction.ExchangeRateSet.PROTOBUF;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.base.TimestampSeconds;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class ExchangeRateManagerTest {
    static final int hbarEquiv = 30_000;
    static final int centEquiv = 120_000;
    TimestampSeconds expirationTime =
            TimestampSeconds.newBuilder().seconds(150_000L).build();
    ExchangeRate.Builder someRate =
            ExchangeRate.newBuilder().hbarEquiv(hbarEquiv).centEquiv(centEquiv).expirationTime(expirationTime);
    ExchangeRateSet validRatesObj = ExchangeRateSet.newBuilder()
            .currentRate(someRate)
            .nextRate(someRate)
            .build();

    Bytes validRateBytes = ExchangeRateSet.PROTOBUF.toBytes(validRatesObj);
    ExchangeRateManager subject = new ExchangeRateManager();

    @Test
    void hasExpectedFields() throws IOException {
        // when
        subject.update(validRateBytes);

        // expect
        final var curr = subject.exchangeRates().currentRateOrThrow();
        final var next = subject.exchangeRates().nextRateOrThrow();
        assertEquals(hbarEquiv, curr.hbarEquiv());
        assertEquals(hbarEquiv, next.hbarEquiv());
        assertEquals(centEquiv, curr.centEquiv());
        assertEquals(centEquiv, next.centEquiv());
        assertEquals(expirationTime.seconds(), curr.expirationTimeOrThrow().seconds());
        assertEquals(expirationTime.seconds(), next.expirationTimeOrThrow().seconds());
        assertEquals(PROTOBUF.parse(validRateBytes.toReadableSequentialData()), subject.exchangeRates());
    }

    @Test
    void onlyCurrentRates() throws IOException {
        // given
        final var onlyCurrentRates =
                ExchangeRateSet.newBuilder().currentRate(someRate).build();
        subject = new ExchangeRateManager();

        // when
        subject.update(ExchangeRateSet.PROTOBUF.toBytes(onlyCurrentRates));

        // expect
        final var curr = subject.exchangeRates().currentRateOrElse(ExchangeRate.DEFAULT);
        final var next = subject.exchangeRates().nextRateOrElse(ExchangeRate.DEFAULT);
        assertEquals(hbarEquiv, curr.hbarEquiv());
        assertEquals(0, next.hbarEquiv());
        assertEquals(centEquiv, curr.centEquiv());
        assertEquals(0, next.centEquiv());
        assertEquals(expirationTime.seconds(), curr.expirationTimeOrThrow().seconds());
        assertEquals(0, next.expirationTimeOrElse(TimestampSeconds.DEFAULT).seconds());
    }

    @Test
    void onlyNextRates() throws IOException {
        // given
        final var onlyNextRates =
                ExchangeRateSet.newBuilder().nextRate(someRate).build();
        subject = new ExchangeRateManager();

        // when
        subject.update(ExchangeRateSet.PROTOBUF.toBytes(onlyNextRates));

        // expect
        final var curr = subject.exchangeRates().currentRateOrElse(ExchangeRate.DEFAULT);
        final var next = subject.exchangeRates().nextRateOrElse(ExchangeRate.DEFAULT);
        assertEquals(0, curr.hbarEquiv());
        assertEquals(hbarEquiv, next.hbarEquiv());
        assertEquals(0, curr.centEquiv());
        assertEquals(centEquiv, next.centEquiv());
        assertEquals(0, curr.expirationTimeOrElse(TimestampSeconds.DEFAULT).seconds());
        assertEquals(expirationTime.seconds(), next.expirationTimeOrThrow().seconds());
    }
}
