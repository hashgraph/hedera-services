package com.hedera.services.stream;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(JUnitPlatform.class)
public class RecordStreamObjectTest {
	private static final TransactionRecord record = mock(TransactionRecord.class);
	private static final TransactionID transactionID = mock(TransactionID.class);
	private static final Transaction transaction = mock(Transaction.class);
	private static final Instant consensusTimestamp = mock(Instant.class);
	private static final Hash runningHashSoFar = mock(Hash.class);
	private static final RecordStreamObject recordStreamObject = new RecordStreamObject(record, transaction, consensusTimestamp);

	private static final RecordStreamObject realObject = getRecordStreamObject();

	@BeforeAll
	public static void setUp() {
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
	public void initTest() {
		assertEquals(recordStreamObject.getTimestamp(), consensusTimestamp);
	}

	@Test
	public void setHashTest() {
		// the Hash in its runningHash should not be set after initialization;
		assertNull(recordStreamObject.getRunningHash().getHash());
		// set Hash
		recordStreamObject.getRunningHash().setHash(runningHashSoFar);
		assertEquals(runningHashSoFar, recordStreamObject.getRunningHash().getHash());
	}

	@Test
	public void toStringTest() {
		final String expectedString = "RecordStreamObject[TransactionRecord=mock record,Transaction=mock transaction,ConsensusTimestamp=mock consensusTimestamp]";
		assertEquals(expectedString, recordStreamObject.toString());
	}

	@Test
	public void toShortStringTest() {
		final String expectedString = "RecordStreamObject[TransactionRecord=[TransactionID=mock transactionID],ConsensusTimestamp=mock consensusTimestamp]";
		assertEquals(expectedString, recordStreamObject.toShortString());
	}

	@Test
	public void toShortStringRecordTest() {
		final String expectedString = "[TransactionID=mock transactionID]";
		assertEquals(expectedString, RecordStreamObject.toShortStringRecord(record));
	}

	@Test
	public void equalsTest() {
		assertEquals(recordStreamObject, new RecordStreamObject(record, transaction, consensusTimestamp));

		assertNotEquals(recordStreamObject, realObject);
		assertNotEquals(recordStreamObject, new RecordStreamObject(record, transaction, realObject.getTimestamp()));
		assertNotEquals(recordStreamObject, new RecordStreamObject(record, realObject.getTransaction(), consensusTimestamp));
		assertNotEquals(recordStreamObject, new RecordStreamObject(realObject.getTransactionRecord(), transaction, consensusTimestamp));
	}

	@Test
	public void serializationDeserializationTest() throws IOException {
		try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
			 SerializableDataOutputStream out = new SerializableDataOutputStream(byteArrayOutputStream)) {
			realObject.serialize(out);
			byteArrayOutputStream.flush();
			byte[] bytes = byteArrayOutputStream.toByteArray();
			try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
				 SerializableDataInputStream input = new SerializableDataInputStream(byteArrayInputStream)) {
				RecordStreamObject deserialized = new RecordStreamObject();
				deserialized.deserialize(input, RecordStreamObject.CLASS_VERSION);
				Assert.assertEquals(realObject, deserialized);
				Assert.assertEquals(realObject.getTimestamp(), deserialized.getTimestamp());
			}
		}
	}

	private static RecordStreamObject getRecordStreamObject() {
		final Instant consensusTimestamp = Instant.now();
		final AccountID.Builder accountID = AccountID.newBuilder().setAccountNum(3);
		final TransactionID.Builder transactionID = TransactionID.newBuilder().setAccountID(accountID);
		final TransactionBody.Builder transactionBody = TransactionBody.newBuilder().setTransactionID(transactionID);
		final SignedTransaction.Builder signedTransaction = SignedTransaction.newBuilder().setBodyBytes(transactionBody.build().toByteString());
		final Transaction transaction = Transaction.newBuilder().setSignedTransactionBytes(signedTransaction.getBodyBytes()).build();
		final TransactionRecord record = TransactionRecord.newBuilder().setConsensusTimestamp(MiscUtils.asTimestamp(consensusTimestamp)).setTransactionID(transactionID).build();
		return new RecordStreamObject(record, transaction, consensusTimestamp);
	}
}
