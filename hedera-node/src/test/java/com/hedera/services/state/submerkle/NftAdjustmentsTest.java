/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NftAdjustmentsTest {
    private final AccountID sender = AccountID.newBuilder().setAccountNum(1).build();
    private final AccountID recipient = AccountID.newBuilder().setAccountNum(3).build();

    private NftAdjustments subject;

    @BeforeEach
    void setUp() {
        subject = new NftAdjustments();
    }

    @Test
    void getClassId() {
        assertEquals(0xd7a02bf45e103466L, subject.getClassId());
    }

    @Test
    void getVersion() {
        assertEquals(1, subject.getVersion());
    }

    @Test
    void deserialize() throws IOException {
        SerializableDataInputStream stream = mock(SerializableDataInputStream.class);
        given(
                        stream.readSerializableList(
                                eq(NftAdjustments.MAX_NUM_ADJUSTMENTS), anyBoolean(), any()))
                .willReturn(Collections.emptyList());
        given(stream.readLongArray(NftAdjustments.MAX_NUM_ADJUSTMENTS))
                .willReturn(new long[] {1, 2, 3});

        subject.deserialize(stream, 1);
        verify(stream).readLongArray(NftAdjustments.MAX_NUM_ADJUSTMENTS);
        verify(stream, times(2)).readSerializableList(eq(1024), eq(true), any());
    }

    @Test
    void serialize() throws IOException {
        givenCanonicalSubject();
        SerializableDataOutputStream stream = mock(SerializableDataOutputStream.class);
        subject.serialize(stream);
        verify(stream).writeLongArray(new long[] {1});
        verify(stream, times(1))
                .writeSerializableList(List.of(EntityId.fromGrpcAccountId(sender)), true, true);
        verify(stream, times(1))
                .writeSerializableList(List.of(EntityId.fromGrpcAccountId(recipient)), true, true);
    }

    @Test
    void testEquals() {
        givenCanonicalSubject();
        final var same = subject;
        assertEquals(subject, same);
        assertEquals(subject, canonicalOwnershipChange());
        assertNotEquals(null, subject);
        assertNotEquals(subject, new Object());
    }

    @Test
    void testHashCode() {
        assertEquals(new NftAdjustments().hashCode(), subject.hashCode());
    }

    @Test
    void toStringWorks() {
        givenCanonicalSubject();
        var str = "NftAdjustments{readable=[1 0.0.1 0.0.3]}";
        assertEquals(str, subject.toString());
    }

    @Test
    void toGrpc() {
        assertNotNull(subject.toGrpc());
        givenCanonicalSubject();
        var grpc = subject.toGrpc();
        var transferList = grpc.getNftTransfersList();
        assertEquals(1, transferList.get(0).getSerialNumber());
        assertEquals(sender, transferList.get(0).getSenderAccountID());
        assertEquals(recipient, transferList.get(0).getReceiverAccountID());
    }

    @Test
    void canAddToBuilder() {
        givenCanonicalSubject();
        final var builder = TokenTransferList.newBuilder();
        subject.addToGrpc(builder);
        final var result = builder.build();
        assertEquals(subject.toGrpc(), result);
    }

    @Test
    void canAppend() {
        givenCanonicalSubject();
        final var senderNum = EntityNum.fromLong(1234L);
        final var receiverNum = EntityNum.fromLong(5678L);
        subject.appendAdjust(senderNum.toEntityId(), receiverNum.toEntityId(), 666L);

        var grpc = subject.toGrpc();
        var exchanges = grpc.getNftTransfersList();
        assertEquals(1, exchanges.get(0).getSerialNumber());
        assertEquals(sender, exchanges.get(0).getSenderAccountID());
        assertEquals(recipient, exchanges.get(0).getReceiverAccountID());
        assertEquals(666, exchanges.get(1).getSerialNumber());
        assertEquals(senderNum.toGrpcAccountId(), exchanges.get(1).getSenderAccountID());
        assertEquals(receiverNum.toGrpcAccountId(), exchanges.get(1).getReceiverAccountID());
    }

    @Test
    void fromGrpc() {
        givenCanonicalSubject();
        var grpc =
                List.of(
                        NftTransfer.newBuilder()
                                .setSerialNumber(1)
                                .setReceiverAccountID(recipient)
                                .setSenderAccountID(sender)
                                .build());

        assertEquals(subject, NftAdjustments.fromGrpc(grpc));
    }

    private void givenCanonicalSubject() {
        subject = canonicalOwnershipChange();
    }

    private NftAdjustments canonicalOwnershipChange() {
        return NftAdjustments.fromGrpc(
                List.of(
                        NftTransfer.newBuilder()
                                .setSerialNumber(1)
                                .setSenderAccountID(sender)
                                .setReceiverAccountID(recipient)
                                .build()));
    }
}
