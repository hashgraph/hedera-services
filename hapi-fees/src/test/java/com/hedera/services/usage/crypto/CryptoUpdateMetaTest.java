package com.hedera.services.usage.crypto;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CryptoUpdateMetaTest {
	private final long keyBytes = 123;
	private final long msgBytes = 1_234;
	private final long memoSize = 20;
	private final long now = 1_234_567;
	private final long expiry = 2_234_567;
	private final boolean hasProxy = true;
	private final boolean hasMaxAutoAssociations = true;
	private final int maxAutoAssociations = 12;

	@Test
	void allGettersAndToStringWork() {
		var expected = "CryptoUpdateMeta{keyBytesUsed=123, msgBytesUsed=1234, memoSize=20, effectiveNow=1234567, " +
				"expiry=2234567, hasProxy=true, maxAutomaticAssociations=12, hasMaxAutomaticAssociations=true}";

		final var subject = new CryptoUpdateMeta.Builder()
				.keyBytesUsed(keyBytes)
				.msgBytesUsed(msgBytes)
				.memoSize(memoSize)
				.effectiveNow(now)
				.expiry(expiry)
				.hasProxy(hasProxy)
				.maxAutomaticAssociations(maxAutoAssociations)
				.hasMaxAutomaticAssociations(hasMaxAutoAssociations)
				.build();

		assertEquals(keyBytes, subject.getKeyBytesUsed());
		assertEquals(msgBytes, subject.getMsgBytesUsed());
		assertEquals(memoSize, subject.getMemoSize());
		assertEquals(now, subject.getEffectiveNow());
		assertEquals(expiry, subject.getExpiry());
		assertTrue(subject.hasProxy());
		assertTrue(subject.hasMaxAutomaticAssociations());
		assertEquals(maxAutoAssociations, subject.getMaxAutomaticAssociations());
		assertEquals(expected, subject.toString());
	}

	@Test
	void hashCodeAndEqualsWork() {
		final var subject1 = new CryptoUpdateMeta.Builder()
				.keyBytesUsed(keyBytes)
				.msgBytesUsed(msgBytes)
				.memoSize(memoSize)
				.effectiveNow(now)
				.expiry(expiry)
				.hasProxy(hasProxy)
				.maxAutomaticAssociations(maxAutoAssociations)
				.hasMaxAutomaticAssociations(hasMaxAutoAssociations)
				.build();

		final var subject2 = new CryptoUpdateMeta.Builder()
				.keyBytesUsed(keyBytes)
				.msgBytesUsed(msgBytes)
				.memoSize(memoSize)
				.effectiveNow(now)
				.expiry(expiry)
				.hasProxy(hasProxy)
				.maxAutomaticAssociations(maxAutoAssociations)
				.hasMaxAutomaticAssociations(hasMaxAutoAssociations)
				.build();

		assertEquals(subject1, subject2);
		assertEquals(subject1.hashCode(), subject2.hashCode());
	}

	@Test
	void calculatesSizesAsExpected() {
		final var accountID = AccountID.newBuilder().setAccountNum(1_234L).build();
		final var canonicalTxn = TransactionBody.newBuilder()
				.setCryptoUpdateAccount(
						CryptoUpdateTransactionBody.newBuilder()
						.setAccountIDToUpdate(accountID)
						.setExpirationTime(Timestamp.newBuilder().setSeconds(expiry))
				).build();

		final var subject = new CryptoUpdateMeta(canonicalTxn);

		assertEquals(0, subject.getKeyBytesUsed());
		assertEquals(32, subject.getMsgBytesUsed());
		assertEquals(0, subject.getMemoSize());
		assertEquals(expiry, subject.getExpiry());
		assertFalse(subject.hasProxy());
		assertFalse(subject.hasMaxAutomaticAssociations());
		assertEquals(0, subject.getMaxAutomaticAssociations());
	}
}
