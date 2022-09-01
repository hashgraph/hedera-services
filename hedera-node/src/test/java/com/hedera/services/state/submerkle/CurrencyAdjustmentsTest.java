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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.booleanThat;
import static org.mockito.ArgumentMatchers.intThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;

import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CurrencyAdjustmentsTest {
    private static final AccountID a = IdUtils.asAccount("0.0.13257");
    private static final AccountID b = IdUtils.asAccount("0.0.13258");
    private static final AccountID c = IdUtils.asAccount("0.0.13259");

    private static final long aAmount = 1L;
    private static final long bAmount = 2L;
    private static final long cAmount = -3L;

    private static final CurrencyAdjustments grpcAdjustments =
            CurrencyAdjustments.fromChanges(
                    new long[] {aAmount, bAmount, cAmount},
                    new long[] {a.getAccountNum(), b.getAccountNum(), c.getAccountNum()});
    private static final CurrencyAdjustments otherGrpcAdjustments =
            CurrencyAdjustments.fromChanges(
                    new long[] {aAmount * 2, bAmount * 2, cAmount * 2},
                    new long[] {a.getAccountNum(), b.getAccountNum(), c.getAccountNum()});

    private CurrencyAdjustments subject;

    @BeforeEach
    void setup() {
        subject = new CurrencyAdjustments();
        subject.accountNums = new long[] {a.getAccountNum(), b.getAccountNum(), c.getAccountNum()};
        subject.hbars = new long[] {aAmount, bAmount, cAmount};
    }

    @Test
    void equalsWork() {
        var expectedAmounts = new long[] {1, 2, 3};
        var expectedParties = new long[] {1L};

        final var sameButDifferent = subject;
        final var anotherSubject = new CurrencyAdjustments(expectedAmounts, expectedParties);
        assertNotEquals(subject, anotherSubject);
        assertEquals(subject, sameButDifferent);
        assertNotEquals(null, subject);
    }

    @Test
    void isEmptyWorks() {
        assertFalse(subject.isEmpty());
        assertTrue(new CurrencyAdjustments().isEmpty());
    }

    @Test
    void toStringWorks() {
        assertEquals(
                "CurrencyAdjustments{readable="
                        + "[0.0.13257 <- +1, 0.0.13258 <- +2, 0.0.13259 -> -3]"
                        + "}",
                subject.toString());
    }

    @Test
    void objectContractWorks() {
        final var one = subject;
        final var two = otherGrpcAdjustments;
        final var three = grpcAdjustments;

        assertNotEquals(null, one);
        assertNotEquals(new Object(), one);
        assertEquals(one, three);
        assertNotEquals(one, two);

        assertNotEquals(one.hashCode(), two.hashCode());
        assertEquals(one.hashCode(), three.hashCode());
    }

    @Test
    void viewWorks() {
        final TransferList grpcAdjustments =
                TxnUtils.withAdjustments(a, aAmount, b, bAmount, c, cAmount);
        assertEquals(grpcAdjustments, subject.toGrpc());
    }

    @Test
    void canAddToGrpc() {
        final var builder = TokenTransferList.newBuilder();
        subject.addToGrpc(builder);

        final TransferList grpcAdjustments =
                TxnUtils.withAdjustments(a, aAmount, b, bAmount, c, cAmount);
        final var expected = grpcAdjustments.getAccountAmountsList();
        final var actual = builder.build().getTransfersList();

        assertEquals(expected, actual);
    }

    @Test
    void factoryWorks() {
        assertEquals(grpcAdjustments, subject);
    }

    @Test
    void serializableDetWorks() {
        assertEquals(CurrencyAdjustments.CURRENT_VERSION, subject.getVersion());
        assertEquals(CurrencyAdjustments.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
    }

    @Test
    void deserializeWorksForPre0240Version() throws IOException {
        final var in = mock(SerializableDataInputStream.class);
        given(
                        in.readSerializableList(
                                intThat(i -> i == CurrencyAdjustments.MAX_NUM_ADJUSTMENTS),
                                booleanThat(Boolean.TRUE::equals),
                                (Supplier<EntityId>) any()))
                .willReturn(
                        Arrays.stream(subject.accountNums)
                                .mapToObj(a -> EntityId.fromIdentityCode((int) a))
                                .toList());
        given(in.readLongArray(CurrencyAdjustments.MAX_NUM_ADJUSTMENTS)).willReturn(subject.hbars);

        final var readSubject = new CurrencyAdjustments();
        readSubject.deserialize(in, CurrencyAdjustments.PRE_0240_VERSION);

        assertEquals(readSubject, subject);
    }

    @Test
    void deserializeWorks() throws IOException {
        final var in = mock(SerializableDataInputStream.class);
        given(in.readLongArray(CurrencyAdjustments.MAX_NUM_ADJUSTMENTS))
                .willReturn(subject.accountNums)
                .willReturn(subject.hbars);

        final var readSubject = new CurrencyAdjustments();
        readSubject.deserialize(in, CurrencyAdjustments.RELEASE_0240_VERSION);

        assertEquals(readSubject, subject);
    }

    @Test
    void serializeWorks() throws IOException {
        final var captor = ArgumentCaptor.forClass(long[].class);
        final var out = mock(SerializableDataOutputStream.class);
        final var inOrder = inOrder(out);

        subject.serialize(out);

        inOrder.verify(out, times(2)).writeLongArray(captor.capture());
        final var capturedValues = captor.getAllValues();

        assertArrayEquals(subject.accountNums, capturedValues.get(0));
        assertArrayEquals(subject.hbars, capturedValues.get(1));
    }
}
