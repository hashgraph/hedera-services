package com.hedera.services.store.schedule;

/*
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.state.submerkle.EntityId;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.hedera.test.factories.scenarios.TxnHandlingScenario.SCHEDULE_ADMIN_KT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class CompositeKeyTest {

	byte[] transactionBody;
	int transactionBodyHashCode;
	Key adminKey = SCHEDULE_ADMIN_KT.asKey();
	AccountID payerId = IdUtils.asAccount("1.2.456");
	String entityMemo = "Some memo here";

	@BeforeEach
	public void setup() {
		transactionBody = TxnUtils.randomUtf8Bytes(100);
		transactionBodyHashCode = Arrays.hashCode(transactionBody);
	}

	@Test
	public void sameInstanceEquals() {
		// given:
		var key = new CompositeKey(transactionBodyHashCode, EntityId.ofNullableAccountId(payerId), adminKey, entityMemo);

		assertEquals(key, key);
	}

	@Test
	public void equalsAndHashCodeAreSymmetric() {
		// given:
		var key = new CompositeKey(transactionBodyHashCode, EntityId.ofNullableAccountId(payerId), adminKey, entityMemo);
		var key2 = new CompositeKey(transactionBodyHashCode, EntityId.ofNullableAccountId(payerId), adminKey, entityMemo);

		assertEquals(key, key2);
		assertEquals(key.hashCode(), key2.hashCode());
	}

	@Test
	public void defaultKeyInstanceWorks() {
		// given:
		var key1 = new CompositeKey(transactionBodyHashCode, EntityId.ofNullableAccountId(payerId), adminKey, entityMemo);
		var key2 = new CompositeKey(transactionBodyHashCode, EntityId.ofNullableAccountId(payerId), Key.getDefaultInstance(), entityMemo);

		assertNotEquals(key1, key2);
	}

	@Test
	public void equalsWorksWithAnotherObj() {
		// given:
		var key = new CompositeKey(transactionBodyHashCode, EntityId.ofNullableAccountId(payerId), adminKey, entityMemo);

		assertNotEquals(key, new Object());
	}

}
