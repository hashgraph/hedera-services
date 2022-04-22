package com.hedera.services.state.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hedera.services.state.tasks.DissociateNftRemovals.RELEASE_0260_VERSION;
import static com.hedera.services.state.tasks.DissociateNftRemovals.RUNTIME_CONSTRUCTABLE_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DissociateNftRemovalsTest {
	private final long accountNum = 1001L;
	private final long tokenNum = 1002L;
	private final long serialsCount = 1003L;
	private final long headNftTokenNum = 1004L;
	private final long headSerialNum = 1005L;

	private DissociateNftRemovals subject;

	@BeforeEach
	void setUp() {
		subject = new DissociateNftRemovals(
				accountNum, tokenNum, serialsCount, headNftTokenNum, headSerialNum);
	}

	@Test
	void metaAsExpected() {
		assertEquals(RELEASE_0260_VERSION, subject.getVersion());
		assertEquals(RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
	}

	@Test
	void gettersAndSettersWork() {
		subject = new DissociateNftRemovals();

		subject.setAccountNum(accountNum);
		subject.setTargetTokenNum(tokenNum);
		subject.setSerialsCount(serialsCount);
		subject.setHeadNftTokenNum(headNftTokenNum);
		subject.setHeadSerialNum(headSerialNum);

		assertEquals(accountNum, subject.getAccountNum());
		assertEquals(tokenNum, subject.getTargetTokenNum());
		assertEquals(serialsCount, subject.getSerialsCount());
		assertEquals(headNftTokenNum, subject.getHeadNftTokenNum());
		assertEquals(headSerialNum, subject.getHeadSerialNum());
	}

	@Test
	void equalsWork() {
		final long otherAccountNum = 1002L;
		final long otherTokenNum = 1003L;
		final long otherSerialsCount = 1004L;
		final long otherHeadNftTokenNum = 1005L;
		final long otherHeadSerialNum = 1006L;

		final var subject2 = new DissociateNftRemovals(
				otherAccountNum, tokenNum, serialsCount, headNftTokenNum, headSerialNum);
		final var subject3 = new DissociateNftRemovals(
				accountNum, otherTokenNum, serialsCount, headNftTokenNum, headSerialNum);
		final var subject4 = new DissociateNftRemovals(
				accountNum, tokenNum, otherSerialsCount, headNftTokenNum, headSerialNum);
		final var subject5 = new DissociateNftRemovals(
				accountNum, tokenNum, serialsCount, otherHeadNftTokenNum, headSerialNum);
		final var subject6 = new DissociateNftRemovals(
				accountNum, tokenNum, serialsCount, headNftTokenNum, otherHeadSerialNum);
		final var identical = new DissociateNftRemovals(
				accountNum, tokenNum, serialsCount, headNftTokenNum, headSerialNum);

		assertEquals(subject, subject);
		assertEquals(subject, identical);
		assertNotEquals(subject, subject2);
		assertNotEquals(subject, subject3);
		assertNotEquals(subject, subject4);
		assertNotEquals(subject, subject5);
		assertNotEquals(subject, subject6);
	}
}
