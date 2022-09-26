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
package com.hedera.services.state.submerkle;

import static com.hedera.services.state.submerkle.ExchangeRates.MERKLE_VERSION;
import static com.hedera.services.state.submerkle.ExchangeRates.RUNTIME_CONSTRUCTABLE_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;

import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExchangeRatesTest {
    private static final int expCurrentHbarEquiv = 25;
    private static final int expCurrentCentEquiv = 1;
    private static final long expCurrentExpiry = Instant.now().getEpochSecond() + 1_234L;

    private static final int expNextHbarEquiv = 45;
    private static final int expNextCentEquiv = 2;
    private static final long expNextExpiry = Instant.now().getEpochSecond() + 5_678L;

    private static final ExchangeRateSet grpc =
            ExchangeRateSet.newBuilder()
                    .setCurrentRate(
                            ExchangeRate.newBuilder()
                                    .setHbarEquiv(expCurrentHbarEquiv)
                                    .setCentEquiv(expCurrentCentEquiv)
                                    .setExpirationTime(
                                            TimestampSeconds.newBuilder()
                                                    .setSeconds(expCurrentExpiry)))
                    .setNextRate(
                            ExchangeRate.newBuilder()
                                    .setHbarEquiv(expNextHbarEquiv)
                                    .setCentEquiv(expNextCentEquiv)
                                    .setExpirationTime(
                                            TimestampSeconds.newBuilder()
                                                    .setSeconds(expNextExpiry)))
                    .build();

    private ExchangeRates subject;

    @BeforeEach
    void setup() {
        subject =
                new ExchangeRates(
                        expCurrentHbarEquiv,
                        expCurrentCentEquiv,
                        expCurrentExpiry,
                        expNextHbarEquiv,
                        expNextCentEquiv,
                        expNextExpiry);
    }

    @Test
    void notAutoInitialized() {
        subject = new ExchangeRates();

        assertFalse(subject.isInitialized());
    }

    @Test
    void hashCodeWorks() {
        int expectedResult = Integer.hashCode(MERKLE_VERSION);
        expectedResult = expectedResult * 31 + Long.hashCode(RUNTIME_CONSTRUCTABLE_ID);
        expectedResult = expectedResult * 31 + Integer.hashCode(expCurrentHbarEquiv);
        expectedResult = expectedResult * 31 + Integer.hashCode(expCurrentCentEquiv);
        expectedResult = expectedResult * 31 + Long.hashCode(expCurrentExpiry);
        expectedResult = expectedResult * 31 + Integer.hashCode(expNextHbarEquiv);
        expectedResult = expectedResult * 31 + Integer.hashCode(expNextCentEquiv);
        expectedResult = expectedResult * 31 + Long.hashCode(expNextExpiry);

        assertEquals(expectedResult, subject.hashCode());
    }

    @Test
    void readableReprWorks() {
        var expected =
                new StringBuilder()
                        .append(expCurrentHbarEquiv)
                        .append("ℏ <-> ")
                        .append(expCurrentCentEquiv)
                        .append("¢ til ")
                        .append(expCurrentExpiry)
                        .append(" | ")
                        .append(expNextHbarEquiv)
                        .append("ℏ <-> ")
                        .append(expNextCentEquiv)
                        .append("¢ til ")
                        .append(expNextExpiry)
                        .toString();

        assertEquals(expected, subject.readableRepr());
    }

    @Test
    void copyWorks() {
        final var sameButDifferent = subject;
        final var subjectCopy = subject.copy();

        assertEquals(subject, sameButDifferent);
        assertNotEquals(null, subject);
        assertEquals(expCurrentHbarEquiv, subjectCopy.getCurrHbarEquiv());
        assertEquals(expCurrentCentEquiv, subjectCopy.getCurrCentEquiv());
        assertEquals(expCurrentExpiry, subjectCopy.getCurrExpiry());
        assertEquals(expNextHbarEquiv, subjectCopy.getNextHbarEquiv());
        assertEquals(expNextCentEquiv, subjectCopy.getNextCentEquiv());
        assertEquals(expNextExpiry, subjectCopy.getNextExpiry());
        assertEquals(subject, subjectCopy);
        assertTrue(subjectCopy.isInitialized());
    }

    @Test
    void serializesAsExpected() throws IOException {
        final var out = mock(SerializableDataOutputStream.class);
        final var inOrder = inOrder(out);

        subject.serialize(out);

        inOrder.verify(out).writeInt(expCurrentHbarEquiv);
        inOrder.verify(out).writeInt(expCurrentCentEquiv);
        inOrder.verify(out).writeLong(expCurrentExpiry);
        inOrder.verify(out).writeInt(expNextHbarEquiv);
        inOrder.verify(out).writeInt(expNextCentEquiv);
        inOrder.verify(out).writeLong(expNextExpiry);
    }

    @Test
    void deserializesAsExpected() throws IOException {
        final var in = mock(SerializableDataInputStream.class);
        subject = new ExchangeRates();
        given(in.readLong()).willReturn(expCurrentExpiry).willReturn(expNextExpiry);
        given(in.readInt())
                .willReturn(expCurrentHbarEquiv)
                .willReturn(expCurrentCentEquiv)
                .willReturn(expNextHbarEquiv)
                .willReturn(expNextCentEquiv);

        subject.deserialize(in, MERKLE_VERSION);

        assertEquals(expCurrentHbarEquiv, subject.getCurrHbarEquiv());
        assertEquals(expCurrentCentEquiv, subject.getCurrCentEquiv());
        assertEquals(expCurrentExpiry, subject.getCurrExpiry());
        assertEquals(expNextHbarEquiv, subject.getNextHbarEquiv());
        assertEquals(expNextCentEquiv, subject.getNextCentEquiv());
        assertEquals(expNextExpiry, subject.getNextExpiry());
        assertTrue(subject.isInitialized());
    }

    @Test
    void sanityChecks() {
        assertEquals(expCurrentHbarEquiv, subject.getCurrHbarEquiv());
        assertEquals(expCurrentCentEquiv, subject.getCurrCentEquiv());
        assertEquals(expCurrentExpiry, subject.getCurrExpiry());
        assertEquals(expNextHbarEquiv, subject.getNextHbarEquiv());
        assertEquals(expNextCentEquiv, subject.getNextCentEquiv());
        assertEquals(expNextExpiry, subject.getNextExpiry());
        assertTrue(subject.isInitialized());
    }

    @Test
    void replaces() {
        final var newRates =
                ExchangeRateSet.newBuilder()
                        .setCurrentRate(
                                ExchangeRate.newBuilder()
                                        .setHbarEquiv(expCurrentHbarEquiv)
                                        .setCentEquiv(expCurrentCentEquiv)
                                        .setExpirationTime(
                                                TimestampSeconds.newBuilder()
                                                        .setSeconds(expCurrentExpiry)))
                        .setNextRate(
                                ExchangeRate.newBuilder()
                                        .setHbarEquiv(expNextHbarEquiv)
                                        .setCentEquiv(expNextCentEquiv)
                                        .setExpirationTime(
                                                TimestampSeconds.newBuilder()
                                                        .setSeconds(expNextExpiry)))
                        .build();
        subject = new ExchangeRates();

        subject.replaceWith(newRates);

        assertEquals(expCurrentHbarEquiv, subject.getCurrHbarEquiv());
        assertEquals(expCurrentCentEquiv, subject.getCurrCentEquiv());
        assertEquals(expCurrentExpiry, subject.getCurrExpiry());
        assertEquals(expNextHbarEquiv, subject.getNextHbarEquiv());
        assertEquals(expNextCentEquiv, subject.getNextCentEquiv());
        assertEquals(expNextExpiry, subject.getNextExpiry());
        assertTrue(subject.isInitialized());
    }

    @Test
    void toStringWorks() {
        assertEquals(
                "ExchangeRates{currHbarEquiv="
                        + expCurrentHbarEquiv
                        + ", currCentEquiv="
                        + expCurrentCentEquiv
                        + ", currExpiry="
                        + expCurrentExpiry
                        + ", nextHbarEquiv="
                        + expNextHbarEquiv
                        + ", nextCentEquiv="
                        + expNextCentEquiv
                        + ", nextExpiry="
                        + expNextExpiry
                        + "}",
                subject.toString());
    }

    @Test
    void viewWorks() {
        assertEquals(grpc, subject.toGrpc());
    }

    @Test
    void factoryWorks() {
        assertEquals(subject, ExchangeRates.fromGrpc(grpc));
    }

    @Test
    void serializableDetWorks() {
        assertEquals(MERKLE_VERSION, subject.getVersion());
        assertEquals(RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
    }
}
