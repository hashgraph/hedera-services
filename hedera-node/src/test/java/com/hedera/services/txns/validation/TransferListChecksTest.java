package com.hedera.services.txns.validation;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransferList;
import org.junit.jupiter.api.Test;

import static com.hedera.services.txns.validation.TransferListChecks.hasRepeatedAccount;
import static com.hedera.test.utils.TxnUtils.withAdjustments;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferListChecksTest {
	private static final AccountID a = IdUtils.asAccount("0.0.2");
	private static final long A = 1_000L;
	private static final AccountID b = IdUtils.asAccount("0.0.4");
	private static final AccountID c = IdUtils.asAccount("0.0.6");

	@Test
	void acceptsDegenerateCases() {
		assertFalse(hasRepeatedAccount(TransferList.getDefaultInstance()));
		assertFalse(hasRepeatedAccount(TransferList.newBuilder()
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(a).setAmount(A).build())
				.build()));
	}

	@Test
	void distinguishesRepeated() {
		assertFalse(hasRepeatedAccount(withAdjustments(a, -4L, b, +2L, c, +2L)));
		assertTrue(hasRepeatedAccount(withAdjustments(a, -4L, b, +2L, a, +2L)));
		assertTrue(hasRepeatedAccount(withAdjustments(a, -4L, b, +2L, b, +2L)));
		assertTrue(hasRepeatedAccount(withAdjustments(a, -4L, a, +2L, b, +2L)));
	}
}
