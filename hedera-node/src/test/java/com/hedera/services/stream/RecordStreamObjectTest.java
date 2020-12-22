package com.hedera.services.stream;

import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.common.crypto.Hash;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RecordStreamObjectTest {
	private static final TransactionRecord record = mock(TransactionRecord.class);
	private static final TransactionID transactionID = mock(TransactionID.class);
	private static final Transaction transaction = mock(Transaction.class);
	private static final Instant consensusTimestamp = mock(Instant.class);
	private static final Hash runningHashSoFar = mock(Hash.class);
	private static final RecordStreamObject recordStreamObject = new RecordStreamObject(record, transaction, consensusTimestamp);

	@BeforeAll
	public static void setUp() {
		when(record.toString()).thenReturn("mock record");
		when(transaction.toString()).thenReturn("mock transaction");
		when(consensusTimestamp.toString()).thenReturn("mock consensusTimestamp");

		when(record.getTransactionID()).thenReturn(transactionID);
		when(transactionID.toString()).thenReturn("mock transactionID");
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
}
