package com.hedera.services.legacy.proto.utils;

/*-
 * ‌
 * Hedera Services API Utilities
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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CommonUtilsTest {
	@Test
	void testForReadableTransactionID() throws InvalidProtocolBufferException {
		final var transaction = Transaction.newBuilder().setBodyBytes(
				TransactionBody.newBuilder().setTransactionID(
						TransactionID.newBuilder()
								.setAccountID(AccountID.newBuilder()
									.setAccountNum(1L)
									.setRealmNum(0L)
									.setShardNum(0L)
									.build())
								.setTransactionValidStart(Timestamp.newBuilder()
										.setSeconds(500L)
										.setNanos(500)
										.build())
								.setScheduled(false)
								.build())
						.build().toByteString())
				.build();

		final var result = CommonUtils.toReadableTransactionID(transaction);
		Assertions.assertEquals("txID=transactionValidStart { seconds: 500 nanos: 500 } accountID { accountNum: 1 }", result);
	}
}
