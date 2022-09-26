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

import static com.hedera.services.state.submerkle.RichInstant.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.inOrder;

import com.hederahashgraph.api.proto.java.Timestamp;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RichInstantTest {
    private static final long seconds = 1_234_567L;
    private static final int nanos = 890;

    private RichInstant subject;

    @BeforeEach
    void setup() {
        subject = new RichInstant(seconds, nanos);
    }

    @Test
    void serializeWorks() throws IOException {
        final var out = mock(SerializableDataOutputStream.class);
        final var inOrder = inOrder(out);

        subject.serialize(out);

        inOrder.verify(out).writeLong(seconds);
        inOrder.verify(out).writeInt(nanos);
    }

    @Test
    void factoryWorks() throws IOException {
        final var in = mock(SerializableDataInputStream.class);
        given(in.readLong()).willReturn(seconds);
        given(in.readInt()).willReturn(nanos);

        final var readSubject = from(in);

        assertEquals(subject, readSubject);
    }

    @Test
    void beanWorks() {
        assertEquals(subject, new RichInstant(subject.getSeconds(), subject.getNanos()));
    }

    @Test
    void viewWorks() {
        final var grpc = Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();

        assertEquals(grpc, subject.toGrpc());
    }

    @Test
    void knowsIfMissing() {
        assertFalse(subject.isMissing());
        assertTrue(MISSING_INSTANT.isMissing());
    }

    @Test
    void toStringWorks() {
        assertEquals(
                "RichInstant{seconds=" + seconds + ", nanos=" + nanos + "}", subject.toString());
    }

    @Test
    void factoryWorksForMissing() {
        assertEquals(MISSING_INSTANT, fromGrpc(Timestamp.getDefaultInstance()));
        assertEquals(subject, fromGrpc(subject.toGrpc()));
    }

    @Test
    void objectContractWorks() {
        final var one = subject;
        final var two = new RichInstant(seconds - 1, nanos - 1);
        final var three = new RichInstant(subject.getSeconds(), subject.getNanos());

        assertNotEquals(null, one);
        assertNotEquals(new Object(), one);
        assertNotEquals(one, two);
        assertEquals(one, three);

        assertEquals(one.hashCode(), three.hashCode());
        assertNotEquals(one.hashCode(), two.hashCode());
    }

    @Test
    void orderingWorks() {
        assertTrue(subject.isAfter(new RichInstant(seconds - 1, nanos)));
        assertTrue(subject.isAfter(new RichInstant(seconds, nanos - 1)));
        assertFalse(subject.isAfter(new RichInstant(seconds, nanos + 1)));
    }

    @Test
    void javaFactoryWorks() {
        assertEquals(
                subject, fromJava(Instant.ofEpochSecond(subject.getSeconds(), subject.getNanos())));
    }

    @Test
    void javaViewWorks() {
        assertEquals(
                Instant.ofEpochSecond(subject.getSeconds(), subject.getNanos()), subject.toJava());
    }

    @Test
    void nullEqualsWorks() {
        assertNotEquals(null, subject);
    }

    @Test
    void emptyConstructor() {
        RichInstant anotherSubject = new RichInstant();
        assertEquals(0, anotherSubject.getNanos());
        assertEquals(0, anotherSubject.getSeconds());
        assertEquals(MISSING_INSTANT, anotherSubject);
        assertEquals(MISSING_INSTANT, fromGrpc(Timestamp.getDefaultInstance()));
        assertEquals(Timestamp.getDefaultInstance(), anotherSubject.toGrpc());
    }

    @Test
    void compareToWorks() {
        assertEquals(0, new RichInstant(2, 2).compareTo(new RichInstant(2, 2)));

        assertTrue(new RichInstant(2, 3).compareTo(new RichInstant(2, 2)) > 0);
        assertTrue(new RichInstant(2, 3).compareTo(new RichInstant(2, 4)) < 0);

        assertTrue(new RichInstant(3, 2).compareTo(new RichInstant(2, 2)) > 0);
        assertTrue(new RichInstant(3, 2).compareTo(new RichInstant(4, 2)) < 0);

        assertTrue(new RichInstant(3, 1).compareTo(new RichInstant(2, 2)) > 0);
        assertTrue(new RichInstant(3, 2).compareTo(new RichInstant(4, 1)) < 0);
    }
}
