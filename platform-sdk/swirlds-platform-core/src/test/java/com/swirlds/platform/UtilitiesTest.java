/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.platform.internal.Deserializer;
import com.swirlds.platform.internal.Serializer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import javax.net.ssl.SSLException;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

class UtilitiesTest {

    @Test
    void writeReadList() throws IOException {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        SerializableDataOutputStream fcOut = new SerializableDataOutputStream(byteOut);

        List<Pair<Long, Long>> w1 = null;
        List<Pair<Long, Long>> w2 = new ArrayList<>();
        List<Pair<Long, Long>> w3 = new LinkedList<>();
        w3.add(Pair.of(1L, 2L));
        w3.add(Pair.of(3L, 4L));

        Serializer<Pair<Long, Long>> ser = (pair, stream) -> {
            stream.writeLong(pair.getKey());
            stream.writeLong(pair.getValue());
        };

        Utilities.writeList(w1, fcOut, ser);
        Utilities.writeList(w2, fcOut, ser);
        Utilities.writeList(w3, fcOut, ser);

        SerializableDataInputStream fcIn =
                new SerializableDataInputStream(new ByteArrayInputStream(byteOut.toByteArray()));

        Deserializer<Pair<Long, Long>> des = (stream) -> {
            long key = stream.readLong();
            long value = stream.readLong();
            return Pair.of(key, value);
        };

        List<Pair<Long, Long>> r1 = Utilities.readList(fcIn, LinkedList::new, des);
        List<Pair<Long, Long>> r2 = Utilities.readList(fcIn, LinkedList::new, des);
        List<Pair<Long, Long>> r3 = Utilities.readList(fcIn, LinkedList::new, des);

        assertIterableEquals(w1, r1);
        assertIterableEquals(w2, r2);
        assertIterableEquals(w3, r3);
    }

    @Test
    void isSupermajorityTest() {
        // Test behavior near boundary region
        assertFalse(Utilities.isSuperMajority(5, 9));
        assertFalse(Utilities.isSuperMajority(6, 9));
        assertTrue(Utilities.isSuperMajority(7, 9));
        assertFalse(Utilities.isSuperMajority(5, 10));
        assertFalse(Utilities.isSuperMajority(6, 10));
        assertTrue(Utilities.isSuperMajority(7, 10));

        // Test behavior with large numbers
        long totalStake = 50L * 1_000_000_000L * 100L * 1_000_000L;
        long quarterStake = totalStake / 4;
        assertTrue(Utilities.isSuperMajority(3 * quarterStake, totalStake));
        assertFalse(Utilities.isSuperMajority(2 * quarterStake, totalStake));
    }

    @Test
    void isStrongMinorityTest() {
        // Test behavior near boundary region
        assertFalse(Utilities.isStrongMinority(2, 9));
        assertTrue(Utilities.isStrongMinority(3, 9));
        assertTrue(Utilities.isStrongMinority(4, 9));
        assertFalse(Utilities.isStrongMinority(2, 10));
        assertFalse(Utilities.isStrongMinority(3, 10));
        assertTrue(Utilities.isStrongMinority(4, 10));

        // Test behavior with large numbers
        long totalStake = 50L * 1_000_000_000L * 100L * 1_000_000L;
        long quarterStake = totalStake / 4;
        assertTrue(Utilities.isStrongMinority(2 * quarterStake, totalStake));
        assertFalse(Utilities.isStrongMinority(1 * quarterStake, totalStake));
    }

    @Test
    void isMajorityTest() {
        assertFalse(Utilities.isMajority(Long.MIN_VALUE, 10), "is not majority");
        assertFalse(Utilities.isMajority(-1, 10), "is not majority");
        assertFalse(Utilities.isMajority(0, 10), "is not majority");
        assertFalse(Utilities.isMajority(1, 10), "is not majority");
        assertFalse(Utilities.isMajority(2, 10), "is not majority");
        assertFalse(Utilities.isMajority(3, 10), "is not majority");
        assertFalse(Utilities.isMajority(4, 10), "is not majority");
        assertFalse(Utilities.isMajority(5, 10), "is not majority");
        assertTrue(Utilities.isMajority(6, 10), "is a majority");
        assertTrue(Utilities.isMajority(7, 10), "is a majority");
        assertTrue(Utilities.isMajority(8, 10), "is a majority");
        assertTrue(Utilities.isMajority(9, 10), "is a majority");
        assertTrue(Utilities.isMajority(10, 10), "is a majority");
        assertTrue(Utilities.isMajority(11, 10), "is a majority");
        assertTrue(Utilities.isMajority(Long.MAX_VALUE, 10), "is a majority");

        assertFalse(Utilities.isMajority(Long.MIN_VALUE, 11), "is not majority");
        assertFalse(Utilities.isMajority(-1, 11), "is not majority");
        assertFalse(Utilities.isMajority(0, 11), "is not majority");
        assertFalse(Utilities.isMajority(1, 11), "is not majority");
        assertFalse(Utilities.isMajority(2, 11), "is not majority");
        assertFalse(Utilities.isMajority(3, 11), "is not majority");
        assertFalse(Utilities.isMajority(4, 11), "is not majority");
        assertFalse(Utilities.isMajority(5, 11), "is not majority");
        assertTrue(Utilities.isMajority(6, 11), "is a majority");
        assertTrue(Utilities.isMajority(7, 11), "is a majority");
        assertTrue(Utilities.isMajority(8, 11), "is a majority");
        assertTrue(Utilities.isMajority(9, 11), "is a majority");
        assertTrue(Utilities.isMajority(10, 11), "is a majority");
        assertTrue(Utilities.isMajority(11, 11), "is a majority");
        assertTrue(Utilities.isMajority(Long.MAX_VALUE, 11), "is a majority");

        assertFalse(Utilities.isMajority(Long.MIN_VALUE, Long.MAX_VALUE), "is not majority");
        assertFalse(Utilities.isMajority(0, Long.MAX_VALUE), "is not majority");

        assertFalse(Utilities.isMajority(Long.MAX_VALUE / 2, Long.MAX_VALUE), "is not majority");
        assertTrue(Utilities.isMajority(Long.MAX_VALUE / 2 + 1, Long.MAX_VALUE), "is a majority");
        assertTrue(Utilities.isMajority(Long.MAX_VALUE, Long.MAX_VALUE), "is a majority");
    }

    @Test
    void isOrCausedBySocketExceptionTest() {
        assertFalse(Utilities.isOrCausedBySocketException(null));

        SSLException sslException = new SSLException("sslException");
        assertFalse(Utilities.isOrCausedBySocketException(sslException));

        SocketException socketException = new SocketException();
        assertTrue(Utilities.isOrCausedBySocketException(socketException));

        SSLException sslExCausedBySocketEx = new SSLException(socketException);
        assertTrue(Utilities.isOrCausedBySocketException(sslExCausedBySocketEx));

        SSLException sslExceptionMultiLayer = new SSLException(sslExCausedBySocketEx);
        assertTrue(Utilities.isOrCausedBySocketException(sslExceptionMultiLayer));
    }

    @Test
    void isRootCauseSuppliedTypeTest() {
        assertTrue(
                Utilities.isRootCauseSuppliedType(
                        new Exception(new IllegalArgumentException(new IOException())), IOException.class),
                "root cause should be IOException");

        assertFalse(
                Utilities.isRootCauseSuppliedType(
                        new Exception(new IllegalArgumentException(new NullPointerException())), IOException.class),
                "root cause should not be IOException");

        assertFalse(
                Utilities.isRootCauseSuppliedType(
                        new IOException(new IllegalArgumentException(new NullPointerException())), IOException.class),
                "root cause should not be IOException, even though is exists in the stack");
    }
}
