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
package com.hedera.services.state.submerkle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.hedera.services.utils.EntityNum;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FcTokenAllowanceIdTest {
    private EntityNum tokenNum = EntityNum.fromLong(1L);
    private EntityNum spenderNum = EntityNum.fromLong(2L);

    private FcTokenAllowanceId subject;

    @BeforeEach
    void setup() {
        subject = FcTokenAllowanceId.from(tokenNum, spenderNum);
    }

    @Test
    void objectContractWorks() {
        final var one = subject;
        final var two = FcTokenAllowanceId.from(EntityNum.fromLong(3L), EntityNum.fromLong(4L));
        final var three = FcTokenAllowanceId.from(EntityNum.fromLong(1L), EntityNum.fromLong(2L));
        final var four =
                FcTokenAllowanceId.from(tokenNum.toGrpcTokenId(), spenderNum.toGrpcAccountId());

        assertNotEquals(null, one);
        assertNotEquals(new Object(), one);
        assertNotEquals(two, one);
        assertEquals(one, three);
        assertEquals(three, four);

        assertEquals(one.hashCode(), three.hashCode());
        assertNotEquals(one.hashCode(), two.hashCode());
    }

    @Test
    void toStringWorks() {
        assertEquals(
                "FcTokenAllowanceId{tokenNum="
                        + tokenNum.longValue()
                        + ", spenderNum="
                        + spenderNum.longValue()
                        + "}",
                subject.toString());
    }

    @Test
    void gettersWork() {
        assertEquals(1L, subject.getTokenNum().longValue());
        assertEquals(2L, subject.getSpenderNum().longValue());
    }

    @Test
    void deserializeWorks() throws IOException {
        final var in = mock(SerializableDataInputStream.class);
        final var newSubject = new FcTokenAllowanceId();
        given(in.readInt()).willReturn(tokenNum.intValue()).willReturn(spenderNum.intValue());

        newSubject.deserialize(in, FcTokenAllowanceId.CURRENT_VERSION);

        assertEquals(subject, newSubject);
    }

    @Test
    void serializeWorks() throws IOException {
        final var out = mock(SerializableDataOutputStream.class);
        final var inOrder = inOrder(out);

        subject.serialize(out);

        inOrder.verify(out).writeInt(tokenNum.intValue());
        inOrder.verify(out).writeInt(spenderNum.intValue());
    }

    @Test
    void serializableDetWorks() {
        assertEquals(FcTokenAllowanceId.RELEASE_023X_VERSION, subject.getVersion());
        assertEquals(FcTokenAllowanceId.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
    }

    @Test
    void orderingPrioritizesTokenNumThenSpender() {
        final var base = new FcTokenAllowanceId(tokenNum, spenderNum);
        final var sameButDiff = base;
        assertEquals(0, base.compareTo(sameButDiff));
        final var largerNum =
                new FcTokenAllowanceId(
                        EntityNum.fromInt(tokenNum.intValue() + 1),
                        EntityNum.fromInt(spenderNum.intValue() - 1));
        assertEquals(-1, base.compareTo(largerNum));
        final var smallerKey =
                new FcTokenAllowanceId(
                        EntityNum.fromInt(tokenNum.intValue() - 1),
                        EntityNum.fromInt(spenderNum.intValue() - 1));
        assertEquals(+1, base.compareTo(smallerKey));
    }
}
