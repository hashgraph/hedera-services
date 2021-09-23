package com.hedera.services.usage.token.meta;

/*-
 * ‌
 * Hedera Services API Fees
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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExtantTokenContextTest {
	@Test
	void allGettersAndToStringWork() {
		final var expected = "ExtantTokenContext{existingNameLen=12, existingSymLen=5, " +
				"existingMemoLen=56, existingKeysLen=132, existingExpiry=2012167, hasAutoRenewAccount=true}";

		final var subject = ExtantTokenContext.newBuilder()
				.setExistingKeysLen(132)
				.setExistingSymLen(5)
				.setExistingNameLen(12)
				.setExistingMemoLen(56)
				.setExistingExpiry(1_234_567L + 777_600L)
				.setHasAutoRenewalAccount(true)
				.build();

		assertEquals(229, subject.getExistingRbSize());
		assertEquals(1_234_567L + 777_600, subject.getExistingExpiry());
		assertEquals(5, subject.getExistingSymLen());
		assertEquals(12, subject.getExistingNameLen());
		assertEquals(56, subject.getExistingMemoLen());
		assertEquals(true, subject.getHashasAutoRenewAccount());
		assertEquals(expected, subject.toString());
	}
	@Test
	void allSettersWorkForNegativeNumbers() {

		final var subject = ExtantTokenContext.newBuilder()
				.setExistingKeysLen(-132)
				.setExistingSymLen(-5)
				.setExistingNameLen(-12)
				.setExistingMemoLen(-56)
				.setExistingExpiry(-1_234_567L)
				.build();

		assertEquals(0, subject.getExistingRbSize());
		assertEquals(0, subject.getExistingExpiry());
		assertEquals(0, subject.getExistingSymLen());
		assertEquals(0, subject.getExistingNameLen());
		assertEquals(0, subject.getExistingMemoLen());
	}

	@Test
	void hashCodeAndEqualsWork() {
		final var ctx1 = ExtantTokenContext.newBuilder()
				.setExistingKeysLen(132)
				.setExistingSymLen(5)
				.setExistingNameLen(12)
				.setExistingMemoLen(56)
				.setExistingExpiry(1_234_567L + 777_600L)
				.setHasAutoRenewalAccount(true)
				.build();
		final var ctx2 = ExtantTokenContext.newBuilder()
				.setExistingKeysLen(132)
				.setExistingSymLen(5)
				.setExistingNameLen(12)
				.setExistingMemoLen(56)
				.setExistingExpiry(1_234_567L + 777_600L)
				.setHasAutoRenewalAccount(true)
				.build();

		assertEquals(ctx1, ctx2);
		assertEquals(ctx1.hashCode(), ctx2.hashCode());
	}
}
