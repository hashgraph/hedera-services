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
package com.hedera.services.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.stream.StreamAligned;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RecordStreamObjectTest {
    private static final TransactionRecord record = mock(TransactionRecord.class);
    private static final TransactionID transactionID = mock(TransactionID.class);
    private static final Transaction transaction = mock(Transaction.class);
    private static final Instant consensusTimestamp = mock(Instant.class);
    private static final Hash runningHashSoFar = mock(Hash.class);
    private static final RecordStreamObject recordStreamObject =
            new RecordStreamObject(record, transaction, consensusTimestamp);

    private static final RecordStreamObject realObject = getRecordStreamObject();

    @BeforeAll
    static void setUp() {
        when(record.toString()).thenReturn("mock record");
        when(transaction.toString()).thenReturn("mock transaction");
        when(consensusTimestamp.toString()).thenReturn("mock consensusTimestamp");

        when(record.getTransactionID()).thenReturn(transactionID);
        when(transactionID.toString()).thenReturn("mock transactionID");

        SettingsCommon.maxTransactionCountPerEvent = 245760;
        SettingsCommon.maxTransactionBytesPerEvent = 245760;
        SettingsCommon.transactionMaxBytes = 6144;
    }

    @Test
    void defaultAlignmentIsNewAlignment() {
        final var subject = new RecordStreamObject();

        assertEquals(StreamAligned.NO_ALIGNMENT, subject.getStreamAlignment());
    }

    @Test
    void alignmentIsBlockNumberIfSet() {
        final var blockNo = 666L;
        final var subject = new RecordStreamObject().withBlockNumber(blockNo);

        assertEquals(blockNo, subject.getStreamAlignment());
    }

    @Test
    void initTest() {
        assertEquals(consensusTimestamp, recordStreamObject.getTimestamp());
    }

    @Test
    void setHashTest() {
        // the Hash in its runningHash should not be set after initialization;
        assertNull(recordStreamObject.getRunningHash().getHash());
        // set Hash
        recordStreamObject.getRunningHash().setHash(runningHashSoFar);
        assertEquals(runningHashSoFar, recordStreamObject.getRunningHash().getHash());
    }

    @Test
    void toStringTest() {
        final var expectedString =
                "RecordStreamObject[TransactionRecord=mock record,Transaction=mock"
                    + " transaction,ConsensusTimestamp=mock consensusTimestamp,Sidecars=<null>]";
        assertEquals(expectedString, recordStreamObject.toString());
    }

    @Test
    void toShortStringTest() {
        final String expectedString =
                "RecordStreamObject[TransactionRecord=[TransactionID=mock transactionID],"
                        + "ConsensusTimestamp=mock consensusTimestamp]";
        assertEquals(expectedString, recordStreamObject.toShortString());
    }

    @Test
    void toShortStringRecordTest() {
        final String expectedString = "[TransactionID=mock transactionID]";
        assertEquals(expectedString, RecordStreamObject.toShortStringRecord(record));
    }

    @Test
    void equalsTest() {
        final var sameButDifferent = recordStreamObject;
        assertNotEquals(recordStreamObject, new Object());
        assertEquals(recordStreamObject, sameButDifferent);

        assertEquals(
                recordStreamObject,
                new RecordStreamObject(record, transaction, consensusTimestamp));

        assertNotEquals(recordStreamObject, realObject);
        assertNotEquals(
                recordStreamObject,
                new RecordStreamObject(record, transaction, realObject.getTimestamp()));
        assertNotEquals(
                recordStreamObject,
                new RecordStreamObject(record, realObject.getTransaction(), consensusTimestamp));
        assertNotEquals(
                recordStreamObject,
                new RecordStreamObject(
                        realObject.getTransactionRecord(), transaction, consensusTimestamp));
    }

    @Test
    void serializationDeserializationTest() throws IOException {
        try (final var byteArrayOutputStream = new ByteArrayOutputStream();
                final var out = new SerializableDataOutputStream(byteArrayOutputStream)) {
            realObject.serialize(out);
            byteArrayOutputStream.flush();
            final var bytes = byteArrayOutputStream.toByteArray();
            try (final var byteArrayInputStream = new ByteArrayInputStream(bytes);
                    final var input = new SerializableDataInputStream(byteArrayInputStream)) {
                final var deserialized = new RecordStreamObject();
                deserialized.deserialize(input, RecordStreamObject.CLASS_VERSION);
                assertEquals(realObject, deserialized);
                assertEquals(realObject.getTimestamp(), deserialized.getTimestamp());
            }
        }
    }

    private static RecordStreamObject getRecordStreamObject() {
        final var consensusTimestamp = Instant.now();
        final var accountID = AccountID.newBuilder().setAccountNum(3);
        final var transactionID = TransactionID.newBuilder().setAccountID(accountID);
        final var transactionBody = TransactionBody.newBuilder().setTransactionID(transactionID);
        final var signedTransaction =
                SignedTransaction.newBuilder().setBodyBytes(transactionBody.build().toByteString());
        final var transaction =
                Transaction.newBuilder()
                        .setSignedTransactionBytes(signedTransaction.getBodyBytes())
                        .build();
        final var record =
                TransactionRecord.newBuilder()
                        .setConsensusTimestamp(MiscUtils.asTimestamp(consensusTimestamp))
                        .setTransactionID(transactionID)
                        .build();
        return new RecordStreamObject(record, transaction, consensusTimestamp);
    }
}
