package com.hedera.services.usage.util;

/*-
 * ‌
 * Hedera Services API Fees
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.hederahashgraph.api.proto.java.RandomGenerateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RandomGenerateMetaTest {
	private final long msgBytes = 1_234;

	@Test
	void allGettersAndToStringWork() {
		var expected = "RandomGenerateMeta{msgBytesUsed=1234}";

		final var subject = RandomGenerateMeta.newBuilder()
				.msgBytesUsed(msgBytes)
				.build();
		assertEquals(msgBytes, subject.getMsgBytesUsed());
		assertEquals(expected, subject.toString());
	}

	@Test
	void hashCodeAndEqualsWork() {
		final var subject1 = RandomGenerateMeta.newBuilder()
				.msgBytesUsed(msgBytes)
				.build();

		final var subject2 = RandomGenerateMeta.newBuilder()
				.msgBytesUsed(msgBytes)
				.build();

		assertEquals(subject1, subject2);
		assertEquals(subject1.hashCode(), subject2.hashCode());
	}

	@Test
	void calculatesSizesAsExpected() {
		var canonicalTxn = TransactionBody.newBuilder()
				.setRandomGenerate(
						RandomGenerateTransactionBody.newBuilder()
								.setRange(10)
				).build();

		var subject = new RandomGenerateMeta(canonicalTxn.getRandomGenerate());
		assertEquals(4, subject.getMsgBytesUsed());

		// without range
		canonicalTxn = TransactionBody.newBuilder()
				.setRandomGenerate(
						RandomGenerateTransactionBody.newBuilder()
				).build();

		subject = new RandomGenerateMeta(canonicalTxn.getRandomGenerate());
		assertEquals(0, subject.getMsgBytesUsed());
	}
}
