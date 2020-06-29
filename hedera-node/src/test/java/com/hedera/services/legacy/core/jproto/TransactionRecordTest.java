package com.hedera.services.legacy.core.jproto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransactionRecordTest {
	ExpirableTxnRecord subject;

	@BeforeEach
	public void setup() {
		subject = new ExpirableTxnRecord();
	}

	@Test
	public void serializableDetWorks() {
		// expect;
		assertEquals(ExpirableTxnRecord.MERKLE_VERSION, subject.getVersion());
		assertEquals(ExpirableTxnRecord.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
	}
}