package com.hedera.services.usage.schedule;

/*
 * ‌
 * Hedera Services API Fees
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.google.protobuf.ByteString;
import com.hedera.services.test.IdUtils;
import com.hedera.services.test.KeyUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleGetInfoQuery;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.FeeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.hedera.services.usage.schedule.entities.ScheduleEntitySizes.SCHEDULE_ENTITY_SIZES;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_QUERY_RES_HEADER;
import static com.hederahashgraph.fee.FeeBuilder.BOOL_SIZE;
import static org.junit.Assert.assertEquals;

public class ScheduleGetInfoUsageTest {
	TransactionID scheduledTxnId = TransactionID.newBuilder()
			.setScheduled(true)
			.setAccountID(IdUtils.asAccount("0.0.2"))
			.setNonce(ByteString.copyFromUtf8("Something something something"))
			.build();
	Optional<Key> adminKey = Optional.of(KeyUtils.A_COMPLEX_KEY);
	Optional<KeyList> signers = Optional.of(KeyUtils.DUMMY_KEY_LIST);
	ScheduleID id = IdUtils.asSchedule("0.0.1");
	ScheduleGetInfoUsage subject;
	ByteString memo = ByteString.copyFromUtf8("This is just a memo?");
	AccountID payer = IdUtils.asAccount("0.0.2");
	SchedulableTransactionBody scheduledTxn = SchedulableTransactionBody.newBuilder()
			.setTransactionFee(1_234_567L)
			.setCryptoDelete(CryptoDeleteTransactionBody.newBuilder()
					.setDeleteAccountID(payer))
			.build();

	@BeforeEach
	public void setup() {
		subject = ScheduleGetInfoUsage.newEstimate(scheduleQuery());
	}

	@Test
	public void assessesRequiredBytes() {
		// given:
		subject.givenCurrentAdminKey(adminKey)
				.givenSignatories(signers)
				.givenScheduledTxn(scheduledTxn)
				.givenMemo(memo)
				.givenDeleted(true)
				.givenExecuted(true)
				.givenScheduledTxnId(scheduledTxnId);

		// and:
		var expectedAdminBytes = FeeBuilder.getAccountKeyStorageSize(adminKey.get());
		var signersBytes = signers.get().toByteArray().length;
		var expectedBytes = BASIC_QUERY_RES_HEADER
				+ scheduledTxnId.toByteArray().length
				+ expectedAdminBytes
				+ signersBytes
				+ 2 * BOOL_SIZE
				+ SCHEDULE_ENTITY_SIZES.bytesInReprGiven(scheduledTxn, memo.size());

		// when:
		var usage = subject.get();

		// then:
		var node = usage.getNodedata();
		assertEquals(FeeBuilder.BASIC_QUERY_HEADER + BASIC_ENTITY_ID_SIZE, node.getBpt());
		assertEquals(expectedBytes, node.getBpr());

	}

	private Query scheduleQuery() {
		var op = ScheduleGetInfoQuery.newBuilder()
				.setScheduleID(id)
				.build();
		return Query.newBuilder().setScheduleGetInfo(op).build();
	}
}
