package com.hedera.services.store.schedule;

/*
 * ‌
 * Hedera Services Node
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
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.Key;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;

import static com.hedera.services.state.submerkle.EntityId.fromGrpcAccount;
import static com.hedera.services.store.schedule.ContentAddressableSchedule.fromMerkleSchedule;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.SCHEDULE_ADMIN_KT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ContentAddressableScheduleTest {
	byte[] txn = TxnUtils.randomUtf8Bytes(100);
	byte[] diffTxn = TxnUtils.randomUtf8Bytes(100);

	int transactionBodyHashCode;

	Key adminKey = SCHEDULE_ADMIN_KT.asKey();
	Key diffAdminKey = MISC_ACCOUNT_KT.asKey();

	EntityId payerId = fromGrpcAccount(IdUtils.asAccount("1.2.456"));
	EntityId diffPayerId = fromGrpcAccount(IdUtils.asAccount("1.2.654"));

	String entityMemo = "Some memo here";
	String diffEntityMemo = "Some other memo here";

	ContentAddressableSchedule subject;

	@BeforeEach
	public void setup() {
		transactionBodyHashCode = Arrays.hashCode(txn);

		subject = new ContentAddressableSchedule(adminKey, entityMemo, payerId, txn);
	}

	@Test
	public void objectContractMet() {
		// setup:
		var subjectExceptKey =
				new ContentAddressableSchedule(diffAdminKey, entityMemo, payerId, txn);
		var subjectExceptMemo =
				new ContentAddressableSchedule(adminKey, diffEntityMemo, payerId, txn);
		var subjectExceptPayer =
				new ContentAddressableSchedule(adminKey, entityMemo, diffPayerId, txn);
		var subjectExceptTxn =
				new ContentAddressableSchedule(adminKey, entityMemo, payerId, diffTxn);
		// and:
		var identicalSubject =
				new ContentAddressableSchedule(adminKey, entityMemo, payerId, txn);

		assertEquals(subject, subject);
		assertEquals(subject, identicalSubject);
		assertNotEquals(subject, new Object());
		assertNotEquals(subject, subjectExceptKey);
		assertNotEquals(subject, subjectExceptMemo);
		assertNotEquals(subject, subjectExceptPayer);
		assertNotEquals(subject, subjectExceptTxn);
		// and:
		assertNotEquals(subject.hashCode(), subjectExceptKey.hashCode());
		assertNotEquals(subject.hashCode(), subjectExceptMemo.hashCode());
		assertNotEquals(subject.hashCode(), subjectExceptPayer.hashCode());
		assertNotEquals(subject.hashCode(), subjectExceptTxn.hashCode());
	}

	@Test
	public void factoryWorksWithJustTxn() {
		// setup:
		MerkleSchedule schedule = new MerkleSchedule(txn, payerId, RichInstant.fromJava(Instant.now()));
		schedule.setPayer(payerId);

		// given:
		subject = fromMerkleSchedule(schedule);
		// expect:
		assertSame(ContentAddressableSchedule.UNUSED_KEY, subject.adminKey);
		assertSame(ContentAddressableSchedule.EMPTY_MEMO, subject.entityMemo);
		assertEquals(ByteString.copyFrom(txn), subject.transactionBytes);
		assertEquals(payerId, subject.payer);
	}

	@Test
	public void factoryWorksWithGivenMemoAndAdminKey() {
		// setup:
		MerkleSchedule schedule = new MerkleSchedule(txn, payerId, RichInstant.fromJava(Instant.now()));
		schedule.setPayer(payerId);
		schedule.setMemo(entityMemo);
		schedule.setAdminKey(SCHEDULE_ADMIN_KT.asJKeyUnchecked());

		// given:
		subject = fromMerkleSchedule(schedule);
		// expect:
		assertSame(entityMemo, subject.entityMemo);
		assertEquals(ByteString.copyFrom(txn), subject.transactionBytes);
		assertEquals(payerId, subject.payer);
		assertEquals(adminKey, subject.adminKey);
	}
}
