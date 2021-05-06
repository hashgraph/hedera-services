package com.hedera.services.state.expiry.renewal;

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

import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class RenewalFeeHelperTest {
	private final long startBalance = 1_234L;
	private final MerkleEntityId fundingKey = new MerkleEntityId(0, 0, 98);

	private final MerkleAccount fundingAccountPreCycle = MerkleAccountFactory.newAccount()
			.balance(startBalance)
			.get();
	private final MerkleAccount fundingAccountPostCycle = MerkleAccountFactory.newAccount()
			.balance(startBalance + 55L)
			.get();

	@Mock
	private FCMap<MerkleEntityId, MerkleAccount> accounts;

	private GlobalDynamicProperties properties = new MockGlobalDynamicProps();

	private RenewalFeeHelper subject;

	@BeforeEach
	void setUp() {
		subject = new RenewalFeeHelper(properties, () -> accounts);
	}

	@Test
	void abortsIfNoFeesToCharge() {
		// when:
		subject.endChargingCycle();

		// then:
		verifyNoInteractions(accounts);
	}

	@Test
	void chargesAsExpectedInCycle() {
		long expected = 0;
		setupFundingAccount();

		// given:
		subject.beginChargingCycle();
		// and:
		for (long i = 1; i <= 10; i++) {
			subject.recordCharged(i);
			expected += i;
			assertEquals(expected, subject.getTotalFeesCharged());
		}
		// and:
		subject.endChargingCycle();
		assertEquals(0L, subject.getTotalFeesCharged());

		// then:
		verify(accounts).getForModify(fundingKey);
		verify(accounts).replace(fundingKey, fundingAccountPostCycle);
	}

	private void setupFundingAccount() {
		given(accounts.getForModify(fundingKey)).willReturn(fundingAccountPreCycle);
	}
}
