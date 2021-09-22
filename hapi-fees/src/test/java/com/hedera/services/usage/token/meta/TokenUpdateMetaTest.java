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

import java.time.Instant;

import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenUpdateMetaTest {
	@Test
	void allGettersAndToStringWork() {
		final var expected = "TokenUpdateMeta{newNameLen=12, newSymLen=5, newMemoLen=56, newKeysLen=132, " +
				"newExpiry=2012167, newAuroRenewPeriod=777600, removeAutoRenewAccount=false, " +
				"hasAutoRenewAccount=true, hasTreasure=false}";

		final var subject = TokenUpdateMeta.newBuilder()
				.setNewKeysLen(132)
				.setNewSymLen(5)
				.setNewNameLen(12)
				.setNewMemoLen(56)
				.setNewEffectiveTxnStartTime(1_234_567L)
				.setRemoveAutoRenewAccount(false)
				.setNewExpiry(1_234_567L + 777_600L)
				.setNewAutoRenewPeriod(777_600L)
				.setHasAutoRenewAccount(true)
				.build();

		assertEquals(132, subject.getNewKeysLen());
		assertEquals(1_234_567L, subject.getNewEffectiveTxnStartTime());
		assertEquals(1_234_567L + 777_600, subject.getNewExpiry());
		assertEquals(777_600, subject.getNewAutoRenewPeriod());
		assertEquals(5, subject.getNewSymLen());
		assertEquals(12, subject.getNewNameLen());
		assertEquals(56, subject.getNewMemoLen());
		assertEquals(true, subject.hasAutoRenewAccount());
		assertEquals(false, subject.getRemoveAutoRenewAccount());
		assertEquals(expected, subject.toString());
	}

	@Test
	void hashCodeAndEqualsWork() {
		final var meta1 = TokenUpdateMeta.newBuilder()
				.setNewKeysLen(132)
				.setNewSymLen(5)
				.setNewNameLen(12)
				.setNewEffectiveTxnStartTime(1_234_567L)
				.setRemoveAutoRenewAccount(true)
				.build();
		final var meta2 = TokenUpdateMeta.newBuilder()
				.setNewKeysLen(132)
				.setNewSymLen(5)
				.setNewNameLen(12)
				.setNewEffectiveTxnStartTime(1_234_567L)
				.setRemoveAutoRenewAccount(true)
				.build();
		assertEquals(meta1, meta2);
		assertEquals(meta1.hashCode(), meta2.hashCode());
	}
}
