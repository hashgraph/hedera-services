package com.hedera.services.ledger;

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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.txns.validation.OptionValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_PERIOD;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerkleAccountScopedCheckTest {
	@Mock
	GlobalDynamicProperties dynamicProperties;

	@Mock
	OptionValidator validator;

	@Mock
	BalanceChange balanceChange;

	@Mock
	MerkleAccount account;

	@Mock
	Map<AccountProperty, Object> changeSet;

	long expiry = 1234L;
	MerkleAccountScopedCheck subject;

	@BeforeEach
	void setUp() {
		subject = new MerkleAccountScopedCheck(dynamicProperties, validator);
		subject.setBalanceChange(balanceChange);
	}

	@Test
	void failsAsExpectedForSmartContacts() {
		when(account.isSmartContract()).thenReturn(true);

		assertEquals(INVALID_ACCOUNT_ID, subject.checkUsing(account, changeSet));
	}

	@Test
	void failsAsExpectedForDeletedAccount() {
		when(account.isSmartContract()).thenReturn(false);
		when(account.isDeleted()).thenReturn(true);

		assertEquals(ACCOUNT_DELETED, subject.checkUsing(account, changeSet));
	}

	@Test
	void failAsExpectedForDeletedAccountInChangeSet() {
		when(account.isSmartContract()).thenReturn(false);
		Map<AccountProperty, Object> changes = new HashMap<>();
		changes.put(IS_DELETED, true);

		assertEquals(ACCOUNT_DELETED, subject.checkUsing(account, changes));
	}

	@Test
	void failsAsExpectedForExpiredAccount() {
		when(account.isSmartContract()).thenReturn(false);
		when(account.isDeleted()).thenReturn(false);
		when(dynamicProperties.autoRenewEnabled()).thenReturn(true);
		when(account.getBalance()).thenReturn(0L);
		when(account.getExpiry()).thenReturn(expiry);
		when(validator.isAfterConsensusSecond(expiry)).thenReturn(false);

		assertEquals(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL, subject.checkUsing(account, changeSet));
	}

	@Test
	void failsAsExpectedWhenInsufficientBalance() {
		when(account.isSmartContract()).thenReturn(false);
		when(account.isDeleted()).thenReturn(false);
		when(dynamicProperties.autoRenewEnabled()).thenReturn(false);

		when(account.getBalance()).thenReturn(5L);
		when(balanceChange.units()).thenReturn(-6L);
		when(balanceChange.codeForInsufficientBalance()).thenReturn(INSUFFICIENT_ACCOUNT_BALANCE);

		assertEquals(INSUFFICIENT_ACCOUNT_BALANCE, subject.checkUsing(account, changeSet));
	}

	@Test
	void happyPath() {
		when(account.isSmartContract()).thenReturn(false);
		when(account.isDeleted()).thenReturn(false);
		when(dynamicProperties.autoRenewEnabled()).thenReturn(false);
		when(account.getBalance()).thenReturn(0L);
		when(balanceChange.units()).thenReturn(5L);

		assertEquals(OK, subject.checkUsing(account, changeSet));
	}

	@Test
	void throwsAsExpected() {
		var iae = assertThrows(IllegalArgumentException.class,
				() -> subject.getEffective(AUTO_RENEW_PERIOD, account, changeSet));
		assertEquals("Invalid Property " + AUTO_RENEW_PERIOD + " cannot be validated in scoped check",
				iae.getMessage());
	}
}
