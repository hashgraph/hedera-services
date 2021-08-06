package com.hedera.services.legacy.unit;

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

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.TestHelper;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FreezeTransactionBody;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.builder.RequestBuilder;

import java.time.Instant;
import java.util.Calendar;
import java.util.TimeZone;

class FreezeTestHelper {
	private static AccountID nodeAccountId = IdUtils.asAccount("0.0.3");

	static Transaction createFreezeTransaction(final boolean paidBy58, final boolean valid, final FileID fileID) {
		return createFreezeTransaction(paidBy58, valid, fileID, null, null);
	}

	static Transaction createFreezeTransaction(
			final boolean paidBy58,
			final boolean valid,
			final FileID fileID,
			final byte[] fileHash
	) {
		return createFreezeTransaction(paidBy58, valid, fileID, fileHash, null);
	}

	static Transaction createFreezeTransaction(
			final boolean paidBy58,
			final boolean valid,
			final FileID fileID,
			final Instant freezeStartTime
	) {
		return createFreezeTransaction(paidBy58, valid, fileID, null, freezeStartTime);
	}

	static Transaction createFreezeTransaction(
			final boolean paidBy58,
			final boolean valid,
			final FileID fileID,
			final byte[] fileHash,
			final Instant timeStamp
	) {
		final var startHourMin = getUTCHourMinFromMillis(System.currentTimeMillis() + 60000);
		final var endHourMin = getUTCHourMinFromMillis(System.currentTimeMillis() + 120000);
		return createFreezeTransaction(paidBy58, valid, fileID, fileHash, startHourMin, endHourMin, timeStamp);
	}

	static Transaction createFreezeTransaction(
			final boolean paidBy58,
			final boolean valid,
			final FileID fileID,
			final byte[] fileHash,
			final int[] startHourMin,
			final int[] endHourMin,
			final Instant timeStamp
	) {
		FreezeTransactionBody freezeBody;
		if (valid) {
			final var builder = (timeStamp == null)
					? getFreezeTranBuilder(startHourMin[0], startHourMin[1], endHourMin[0], endHourMin[1])
					: getFreezeTranBuilder(timeStamp);
			if (fileID != null) {
				builder.setUpdateFile(fileID);
				builder.setFileHash(ByteString.copyFrom(fileHash));
			}
			freezeBody = builder.build();
		} else {
			freezeBody = getFreezeTranBuilder(25, 1, 3, 4).build();
		}

		final var timestamp = TestHelper.getDefaultCurrentTimestampUTC();
		final var transactionDuration = RequestBuilder.getDuration(30);

		final long transactionFee = 100000000000l;
		final String memo = "Freeze Test";

		final var payerAccountId = (paidBy58)
				? RequestBuilder.getAccountIdBuild(58l, 0l, 0l)
				: RequestBuilder.getAccountIdBuild(100l, 0l, 0l);

		final var body = RequestBuilder.getTxBodyBuilder(transactionFee,
				timestamp, transactionDuration, true, memo,
				payerAccountId, nodeAccountId);
		body.setFreeze(freezeBody);
		final var bodyBytesArr = body.build().toByteArray();
		final var bodyBytes = ByteString.copyFrom(bodyBytesArr);
		return Transaction.newBuilder().setBodyBytes(bodyBytes).build();
	}

	private static FreezeTransactionBody.Builder getFreezeTranBuilder(int startHour, int startMin, int endHour,
			int endMin) {
		return FreezeTransactionBody.newBuilder()
				.setStartHour(startHour).setStartMin(startMin)
				.setEndHour(endHour).setEndMin(endMin);
	}

	private static FreezeTransactionBody.Builder getFreezeTranBuilder(final Instant freezeStartTime) {
		return FreezeTransactionBody.newBuilder()
				.setStartTime(Timestamp.newBuilder()
						.setSeconds(freezeStartTime.getEpochSecond())
						.setNanos(freezeStartTime.getNano()));
	}

	private static int[] getUTCHourMinFromMillis(final long utcMillis) {
		int[] hourMin = new int[2];
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		cal.setTimeInMillis(utcMillis);
		hourMin[0] = cal.get(Calendar.HOUR_OF_DAY);
		hourMin[1] = cal.get(Calendar.MINUTE);
		return hourMin;
	}
}
