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
import static org.mockito.Mockito.times;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FcTokenAllowanceTest {
    private boolean approvedForAll = true;
    private List<Long> serialNums = new ArrayList<>();
    private FcTokenAllowance subject;

    @BeforeEach
    void setup() {
        serialNums.add(1L);
        serialNums.add(2L);
        subject = FcTokenAllowance.from(approvedForAll, serialNums);
    }

    @Test
    void objectContractWorks() {
        final var one = subject;
        final var two = FcTokenAllowance.from(false, new ArrayList<>());
        final var three = FcTokenAllowance.from(true, serialNums);

        assertNotEquals(null, one);
        assertNotEquals(new Object(), one);
        assertNotEquals(two, one);
        assertEquals(one, three);

        assertEquals(one.hashCode(), three.hashCode());
        assertNotEquals(one.hashCode(), two.hashCode());
    }

    @Test
    void toStringWorks() {
        assertEquals(
                "FcTokenAllowance{approvedForAll="
                        + approvedForAll
                        + ", serialNumbers="
                        + serialNums
                        + "}",
                subject.toString());
    }

    @Test
    void gettersWork() {
        assertEquals(serialNums, subject.getSerialNumbers());
        assertEquals(true, subject.isApprovedForAll());
    }

    @Test
    void deserializeWorksForBothSet() throws IOException {
        final var in = mock(SerializableDataInputStream.class);
        final var newSubject = new FcTokenAllowance();
        given(in.readBoolean()).willReturn(approvedForAll);
        given(in.readLongList(Integer.MAX_VALUE)).willReturn(serialNums);

        newSubject.deserialize(in, FcTokenAllowance.CURRENT_VERSION);

        assertEquals(subject, newSubject);
    }

    @Test
    void serializeWorks() throws IOException {
        final var out = mock(SerializableDataOutputStream.class);
        final var inOrder = inOrder(out);

        subject.serialize(out);

        inOrder.verify(out, times(1)).writeBoolean(true);
        inOrder.verify(out).writeLongList(serialNums);
    }

    @Test
    void serializeWorksWithApprovalSet() throws IOException {
        final var subject = FcTokenAllowance.from(true);
        final var out = mock(SerializableDataOutputStream.class);
        final var inOrder = inOrder(out);

        subject.serialize(out);

        inOrder.verify(out).writeBoolean(true);
        inOrder.verify(out).writeLongList(Collections.emptyList());
    }

    @Test
    void deserializeWorksWithApprovalSet() throws IOException {
        final var subject = FcTokenAllowance.from(true);
        final var in = mock(SerializableDataInputStream.class);
        final var newSubject = new FcTokenAllowance();
        given(in.readBoolean()).willReturn(true);
        given(in.readLongList(Integer.MAX_VALUE)).willReturn(Collections.emptyList());

        newSubject.deserialize(in, FcTokenAllowance.CURRENT_VERSION);

        assertEquals(subject, newSubject);
    }

    @Test
    void serializeWorksWithSerialNumsSet() throws IOException {
        final var subject = FcTokenAllowance.from(serialNums);
        final var out = mock(SerializableDataOutputStream.class);
        final var inOrder = inOrder(out);

        subject.serialize(out);

        inOrder.verify(out).writeBoolean(false);
        inOrder.verify(out).writeLongList(serialNums);
    }

    @Test
    void deserializeWorksWithSerialNumsSet() throws IOException {
        final var subject = FcTokenAllowance.from(serialNums);
        final var in = mock(SerializableDataInputStream.class);
        final var newSubject = new FcTokenAllowance();
        given(in.readBoolean()).willReturn(false);
        given(in.readLongList(Integer.MAX_VALUE)).willReturn(serialNums);

        newSubject.deserialize(in, FcTokenAllowance.CURRENT_VERSION);

        assertEquals(subject, newSubject);
    }

    @Test
    void constructorWorks() {
        final var withApprovalForAll = FcTokenAllowance.from(true);
        final var withSerials = FcTokenAllowance.from(List.of(1L, 2L));

        assertEquals(true, withApprovalForAll.isApprovedForAll());
        assertEquals(Collections.emptyList(), withApprovalForAll.getSerialNumbers());

        assertEquals(false, withSerials.isApprovedForAll());
        assertEquals(List.of(1L, 2L), withSerials.getSerialNumbers());
    }

    @Test
    void serializableDetWorks() {
        assertEquals(FcTokenAllowance.RELEASE_023X_VERSION, subject.getVersion());
        assertEquals(FcTokenAllowance.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
    }

    @Test
    void keepsSortedSerialNumsList() {
        serialNums = new ArrayList();
        serialNums.add(100L);
        serialNums.add(2L);
        serialNums.add(1L);
        serialNums.add(20L);
        var subject = FcTokenAllowance.from(serialNums);
        assertEquals(1L, subject.getSerialNumbers().get(0));
        assertEquals(2L, subject.getSerialNumbers().get(1));
        assertEquals(20L, subject.getSerialNumbers().get(2));
        assertEquals(100L, subject.getSerialNumbers().get(3));

        subject = FcTokenAllowance.from(true, serialNums);
        assertEquals(1L, subject.getSerialNumbers().get(0));
        assertEquals(2L, subject.getSerialNumbers().get(1));
        assertEquals(20L, subject.getSerialNumbers().get(2));
        assertEquals(100L, subject.getSerialNumbers().get(3));
    }
}
