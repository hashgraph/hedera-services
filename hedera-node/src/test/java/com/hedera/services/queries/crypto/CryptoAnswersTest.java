package com.hedera.services.queries.crypto;

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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.BDDMockito.*;

class CryptoAnswersTest {
	GetLiveHashAnswer claim;
	GetStakersAnswer stakers;
	GetAccountInfoAnswer accountInfo;
	GetAccountBalanceAnswer accountBalance;
	GetAccountRecordsAnswer accountRecords;

	@BeforeEach
	private void setup() {
		claim = mock(GetLiveHashAnswer.class);
		stakers = mock(GetStakersAnswer.class);
		accountInfo = mock(GetAccountInfoAnswer.class);
		accountBalance = mock(GetAccountBalanceAnswer.class);
		accountRecords = mock(GetAccountRecordsAnswer.class);
	}

	@Test
	void getsQueryBalance() {
		// given:
		CryptoAnswers subject = new CryptoAnswers(
				claim,
				stakers,
				accountInfo,
				accountBalance,
				accountRecords);

		// expect:
		assertTrue(subject.getAccountInfo() == accountInfo);
		assertTrue(subject.getAccountBalance() == accountBalance);
		assertTrue(subject.getAccountRecords() == accountRecords);
		assertTrue(subject.getLiveHash() == claim);
		assertTrue(subject.getStakers() == stakers);
	}
}
