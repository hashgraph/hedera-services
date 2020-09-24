package com.hedera.services.state.validation;

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

import com.hedera.services.config.HederaNumbers;
import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.legacy.exception.NegativeAccountBalanceException;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

@RunWith(JUnitPlatform.class)
class BasedLedgerValidatorTest {
	private long shard = 1;
	private long realm = 2;

	FCMap<MerkleEntityId, MerkleAccount> accounts = new FCMap<>();

	HederaNumbers hederaNums;
	PropertySource properties;
	GlobalDynamicProperties dynamicProperties = new MockGlobalDynamicProps();

	BasedLedgerValidator subject;

	@BeforeEach
	private void setup() {
		hederaNums = mock(HederaNumbers.class);
		given(hederaNums.realm()).willReturn(realm);
		given(hederaNums.shard()).willReturn(shard);

		properties = mock(PropertySource.class);
		given(properties.getLongProperty("ledger.totalTinyBarFloat")).willReturn(100L);

		subject = new BasedLedgerValidator(hederaNums, properties, dynamicProperties);
	}

	@Test
	public void recognizesRightFloat() throws NegativeAccountBalanceException {
		// given:
		accounts.put(new MerkleEntityId(shard, realm, 1L), expectedWith(50L));
		accounts.put(new MerkleEntityId(shard, realm, 2L), expectedWith(50L));

		// expect:
		assertTrue(subject.hasExpectedTotalBalance(accounts));
	}

	@Test
	public void recognizesWrongFloat() throws NegativeAccountBalanceException {
		// given:
		accounts.put(new MerkleEntityId(shard, realm, 1L), expectedWith(50L));
		accounts.put(new MerkleEntityId(shard, realm, 2L), expectedWith(51L));

		// expect:
		assertFalse(subject.hasExpectedTotalBalance(accounts));
	}

	@Test
	public void doesntThrowWithValidIds() throws NegativeAccountBalanceException {
		// given:
		accounts.put(new MerkleEntityId(shard, realm, 3L), expectedWith(100L));

		// expect:
		assertDoesNotThrow(() -> subject.assertIdsAreValid(accounts));
	}

	@Test
	public void throwsOnIdWithInvalidShard() throws NegativeAccountBalanceException {
		// given:
		accounts.put(
				new MerkleEntityId(shard - 1, realm, 3L),
				expectedWith(dynamicProperties.maxAccountNum()));

		// expect:
		assertThrows(IllegalStateException.class, () -> subject.assertIdsAreValid(accounts));
	}

	@Test
	public void throwsOnIdWithNumTooSmall() throws NegativeAccountBalanceException {
		// given:
		accounts.put(new MerkleEntityId(shard, realm, 0L), expectedWith(100L));

		// expect:
		assertThrows(IllegalStateException.class, () -> subject.assertIdsAreValid(accounts));
	}

	@Test
	public void throwsOnIdWithNumTooLarge() throws NegativeAccountBalanceException {
		// given:
		accounts.put(
				new MerkleEntityId(shard, realm, dynamicProperties.maxAccountNum() + 1),
				expectedWith(100L));

		// expect:
		assertThrows(IllegalStateException.class, () -> subject.assertIdsAreValid(accounts));
	}

	@Test
	public void throwsOnIdWithInvalidRealm() throws NegativeAccountBalanceException {
		// given:
		accounts.put(new MerkleEntityId(shard, realm - 1, 3L), expectedWith(100L));

		// expect:
		assertThrows(IllegalStateException.class, () -> subject.assertIdsAreValid(accounts));
	}

	private MerkleAccount expectedWith(long balance) throws NegativeAccountBalanceException {
		MerkleAccount hAccount = new HederaAccountCustomizer()
				.fundsSentRecordThreshold(123)
				.fundsReceivedRecordThreshold(123)
				.isReceiverSigRequired(false)
				.proxy(MISSING_ENTITY_ID)
				.isAccountDeleted(false)
				.expiry(1_234_567L)
				.memo("")
				.isSmartContract(false)
				.customizing(new MerkleAccount());
		hAccount.setBalance(balance);
		return hAccount;
	}
}
