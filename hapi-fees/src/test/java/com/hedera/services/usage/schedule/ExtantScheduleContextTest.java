package com.hedera.services.usage.schedule;

import com.hedera.services.test.KeyUtils;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static com.hedera.services.usage.schedule.ExtantScheduleContextTest.SettableField.ADMIN_KEY;
import static com.hedera.services.usage.schedule.ExtantScheduleContextTest.SettableField.NO_ADMIN_KEY;
import static com.hedera.services.usage.schedule.ExtantScheduleContextTest.SettableField.NUM_SIGNERS;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_RICH_INSTANT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.KEY_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.getAccountKeyStorageSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtantScheduleContextTest {
	int numSigners = 2;
	boolean resolved = true;
	Key adminKey = KeyUtils.A_THRESHOLD_KEY;
	String memo = "Not since life began";
	SchedulableTransactionBody scheduledTxn = SchedulableTransactionBody.newBuilder().setTransactionFee(123).build();

	enum SettableField { NUM_SIGNERS, NO_ADMIN_KEY, ADMIN_KEY, MEMO, SCHEDULED_TXN, IS_RESOLVED }

	@Test
	void buildsAsExpectedWithAllPresent() {
		// given:
		var ctx = builderWith(EnumSet.allOf(SettableField.class)).build();
		// and:
		long expectedNonBaseRb = ExtantScheduleContext.METADATA_SIZE
				+ BASIC_RICH_INSTANT_SIZE
				+ memo.getBytes().length
				+ getAccountKeyStorageSize(adminKey)
				+ scheduledTxn.getSerializedSize()
				+ numSigners * KEY_SIZE;

		// then:
		assertTrue(ctx.isResolved());
		assertSame(memo, ctx.memo());
		assertSame(scheduledTxn, ctx.scheduledTxn());
		assertSame(adminKey, ctx.adminKey());
		assertEquals(numSigners, ctx.numSigners());
		// and:
		assertEquals(expectedNonBaseRb, ctx.nonBaseRb());
	}

	@Test
	void buildsAsExpected() {
		// given:
		var ctx = builderWith(EnumSet.complementOf(EnumSet.of(ADMIN_KEY))).build();
		// and:
		long expectedNonBaseRb = ExtantScheduleContext.METADATA_SIZE
				+ BASIC_RICH_INSTANT_SIZE
				+ memo.getBytes().length
				+ scheduledTxn.getSerializedSize()
				+ ctx.numSigners() * KEY_SIZE;

		// then:
		assertTrue(ctx.isResolved());
		assertNull(ctx.adminKey());
		assertEquals(memo, ctx.memo());
		assertSame(scheduledTxn, ctx.scheduledTxn());
		assertEquals(numSigners, ctx.numSigners());
		// and:
		assertEquals(expectedNonBaseRb, ctx.nonBaseRb());
	}

	@Test
	void requiresAllFieldsSet() {
		// expect:
		Assertions.assertThrows(IllegalStateException.class,
				() -> builderWith(EnumSet.complementOf(EnumSet.of(NUM_SIGNERS))).build());
		Assertions.assertThrows(IllegalStateException.class,
				() -> builderWith(EnumSet.complementOf(
						EnumSet.of(SettableField.ADMIN_KEY, NO_ADMIN_KEY))).build());
		Assertions.assertThrows(IllegalStateException.class,
				() -> builderWith(EnumSet.of(SettableField.MEMO)).build());
		Assertions.assertThrows(IllegalStateException.class,
				() -> builderWith(EnumSet.complementOf(EnumSet.of(SettableField.SCHEDULED_TXN))).build());
		Assertions.assertThrows(IllegalStateException.class,
				() -> builderWith(EnumSet.complementOf(EnumSet.of(SettableField.IS_RESOLVED))).build());

	}

	private ExtantScheduleContext.Builder builderWith(EnumSet<SettableField> fieldsSet) {
		var builder = ExtantScheduleContext.newBuilder();

		for (SettableField field : fieldsSet) {
			switch (field) {
				case NUM_SIGNERS:
					builder.setNumSigners(numSigners);
					break;
				case NO_ADMIN_KEY:
					builder.setNoAdminKey();
					break;
				case ADMIN_KEY:
					builder.setAdminKey(adminKey);
					break;
				case MEMO:
					builder.setMemo(memo);
					break;
				case SCHEDULED_TXN:
					builder.setScheduledTxn(scheduledTxn);
					break;
				case IS_RESOLVED:
					builder.setResolved(resolved);
					break;
			}
		}

		return builder;
	}
}