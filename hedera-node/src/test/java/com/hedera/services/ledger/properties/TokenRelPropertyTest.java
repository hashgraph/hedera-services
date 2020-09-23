package com.hedera.services.ledger.properties;

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

import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hedera.services.ledger.properties.TokenRelProperty.*;

@RunWith(JUnitPlatform.class)
class TokenRelPropertyTest {
	long balance = 123, newBalance = 321;
	boolean frozen = true;
	boolean kycGranted = false;

	MerkleTokenRelStatus target = new MerkleTokenRelStatus(balance, frozen, kycGranted);

	@Test
	public void gettersWork() {
		// expect:
		assertEquals(balance, TOKEN_BALANCE.getter().apply(target));
		assertEquals(frozen, IS_FROZEN.getter().apply(target));
		assertEquals(kycGranted, IS_KYC_GRANTED.getter().apply(target));
	}

	@Test
	public void settersWork() {
		// when:
		TOKEN_BALANCE.setter().accept(target, newBalance);
		IS_FROZEN.setter().accept(target, !frozen);
		IS_KYC_GRANTED.setter().accept(target, !kycGranted);

		// expect:
		assertEquals(newBalance, TOKEN_BALANCE.getter().apply(target));
		assertEquals(!frozen, IS_FROZEN.getter().apply(target));
		assertEquals(!kycGranted, IS_KYC_GRANTED.getter().apply(target));
	}
}