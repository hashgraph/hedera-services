package com.hedera.services.legacy.unit;

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

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FreezeTransactionBody;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.builder.RequestBuilder;
import com.hedera.services.legacy.TestHelper;

public class FreezeTestHelper {

	private static AccountID payerAccountId;
	private static AccountID nodeAccountId =
			RequestBuilder.getAccountIdBuild(3l, 0l, 0l);

	public static Transaction createFreezeTransaction(boolean paidBy58, boolean valid) {
		FreezeTransactionBody freezeBody;
		if (valid) {
			int[] startHourMin = CommonUtilsTest.getUTCHourMinFromMillis(System.currentTimeMillis() + 60000);
			int[] endHourMin = CommonUtilsTest.getUTCHourMinFromMillis(System.currentTimeMillis() + 120000);
			freezeBody = FreezeTransactionBody.newBuilder()
					.setStartHour(startHourMin[0]).setStartMin(startHourMin[1])
					.setEndHour(endHourMin[0]).setEndMin(endHourMin[1]).build();
		} else {
			freezeBody = FreezeTransactionBody.newBuilder()
					.setStartHour(25).setStartMin(1)
					.setEndHour(3).setEndMin(4).build();
		}


		Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
		Duration transactionDuration = RequestBuilder.getDuration(30);

		long transactionFee = 100000000000l;
		String memo = "Freeze Test";

		if (paidBy58) {
			payerAccountId = RequestBuilder.getAccountIdBuild(58l, 0l, 0l);
		} else {
			payerAccountId = RequestBuilder.getAccountIdBuild(100l, 0l, 0l);
		}

		TransactionBody.Builder body = RequestBuilder.getTxBodyBuilder(transactionFee,
				timestamp, transactionDuration, true, memo,
				payerAccountId, nodeAccountId);
		body.setFreeze(freezeBody);
		byte[] bodyBytesArr = body.build().toByteArray();
		ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
		return Transaction.newBuilder().setBodyBytes(bodyBytes).build();
	}
}
