package com.hedera.services.ledger.properties;

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

import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import org.junit.jupiter.api.Test;

import static com.hedera.services.ledger.properties.TokenRelProperty.IS_AUTOMATIC_ASSOCIATION;
import static com.hedera.services.ledger.properties.TokenRelProperty.IS_FROZEN;
import static com.hedera.services.ledger.properties.TokenRelProperty.IS_KYC_GRANTED;
import static com.hedera.services.ledger.properties.TokenRelProperty.TOKEN_BALANCE;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenRelPropertyTest {
	long balance = 123, newBalance = 321;
	boolean frozen = true;
	boolean kycGranted = false;
	boolean automaticAssociation = false;

	MerkleTokenRelStatus target = new MerkleTokenRelStatus(balance, frozen, kycGranted, automaticAssociation);

	@Test
	void gettersWork() {
		// expect:
		assertEquals(balance, TOKEN_BALANCE.getter().apply(target));
		assertEquals(frozen, IS_FROZEN.getter().apply(target));
		assertEquals(kycGranted, IS_KYC_GRANTED.getter().apply(target));
		assertEquals(automaticAssociation, IS_AUTOMATIC_ASSOCIATION.getter().apply(target));
	}

	@Test
	void settersWork() {
		// when:
		TOKEN_BALANCE.setter().accept(target, newBalance);
		IS_FROZEN.setter().accept(target, !frozen);
		IS_KYC_GRANTED.setter().accept(target, !kycGranted);
		IS_AUTOMATIC_ASSOCIATION.setter().accept(target, !automaticAssociation);

		// expect:
		assertEquals(newBalance, TOKEN_BALANCE.getter().apply(target));
		assertEquals(!frozen, IS_FROZEN.getter().apply(target));
		assertEquals(!kycGranted, IS_KYC_GRANTED.getter().apply(target));
		assertEquals(!automaticAssociation, IS_AUTOMATIC_ASSOCIATION.getter().apply(target));
	}
}
