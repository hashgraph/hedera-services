package com.hedera.services.txns.validation;

import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransferList;
import org.junit.jupiter.api.Test;

import static com.hedera.services.txns.validation.TransferListChecks.hasRepeatedAccount;
import static com.hedera.test.utils.TxnUtils.withAdjustments;
import static org.junit.jupiter.api.Assertions.*;

class TransferListChecksTest {
	private final AccountID a = IdUtils.asAccount("0.0.2");
	private final long A = 1_000L;
	private final AccountID b = IdUtils.asAccount("0.0.4");
	private final long B = 1_001L;
	private final AccountID c = IdUtils.asAccount("0.0.6");
	private final long C = 1_002L;

	@Test
	void acceptsDegenerateCases() {
		// expect:
		assertFalse(hasRepeatedAccount(TransferList.getDefaultInstance()));
		assertFalse(hasRepeatedAccount(TransferList.newBuilder()
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(a).setAmount(A).build())
				.build()));
	}

	@Test
	void distinguishesRepeated() {
		// expect:
		assertFalse(hasRepeatedAccount(withAdjustments(a, -4L, b, +2L, c, +2L)));
		assertTrue(hasRepeatedAccount(withAdjustments(a, -4L, b, +2L, a, +2L)));
		assertTrue(hasRepeatedAccount(withAdjustments(a, -4L, b, +2L, b, +2L)));
		assertTrue(hasRepeatedAccount(withAdjustments(a, -4L, a, +2L, b, +2L)));
	}
}