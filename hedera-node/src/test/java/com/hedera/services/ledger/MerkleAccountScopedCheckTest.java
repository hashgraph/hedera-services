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
import java.util.function.Function;

import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_PERIOD;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.ledger.properties.AccountProperty.IS_SMART_CONTRACT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerkleAccountScopedCheckTest {
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private OptionValidator validator;
	@Mock
	private BalanceChange balanceChange;
	@Mock
	private MerkleAccount account;
	@Mock
	private Map<AccountProperty, Object> changeSet;
	@Mock
	private Function<AccountProperty, Object> extantProps;

	private MerkleAccountScopedCheck subject;

	@BeforeEach
	void setUp() {
		subject = new MerkleAccountScopedCheck(dynamicProperties, validator);
		subject.setBalanceChange(balanceChange);
	}

	@Test
	void failsAsExpectedForDeletedAccount() {
		when(account.isDeleted()).thenReturn(true);
		assertEquals(ACCOUNT_DELETED, subject.checkUsing(account, changeSet));

		given(extantProps.apply(IS_DELETED)).willReturn(true);
		assertEquals(ACCOUNT_DELETED, subject.checkUsing(extantProps, changeSet));
	}

	@Test
	void failAsExpectedForDeletedAccountInChangeSet() {
		Map<AccountProperty, Object> changes = new HashMap<>();
		changes.put(IS_DELETED, true);

		assertEquals(ACCOUNT_DELETED, subject.checkUsing(account, changes));
	}

	@Test
	void failsAsExpectedForExpiredAccount() {
		final var expiry = 1234L;

		when(account.isDeleted()).thenReturn(false);
		when(dynamicProperties.autoRenewEnabled()).thenReturn(true);
		when(account.getBalance()).thenReturn(0L);
		when(account.getExpiry()).thenReturn(expiry);
		when(validator.isAfterConsensusSecond(expiry)).thenReturn(false);
		assertEquals(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL, subject.checkUsing(account, changeSet));

		given(extantProps.apply(IS_DELETED)).willReturn(false);
		given(extantProps.apply(BALANCE)).willReturn(0L);
		given(extantProps.apply(EXPIRY)).willReturn(expiry);
		assertEquals(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL, subject.checkUsing(extantProps, changeSet));
	}

	@Test
	void failsAsExpectedWhenInsufficientBalance() {
		when(account.isDeleted()).thenReturn(false);
		when(dynamicProperties.autoRenewEnabled()).thenReturn(false);

		when(account.getBalance()).thenReturn(5L);
		when(balanceChange.units()).thenReturn(-6L);
		when(balanceChange.codeForInsufficientBalance()).thenReturn(INSUFFICIENT_ACCOUNT_BALANCE);

		assertEquals(INSUFFICIENT_ACCOUNT_BALANCE, subject.checkUsing(account, changeSet));
	}

	@Test
	void happyPath() {
		when(account.isDeleted()).thenReturn(false);
		when(dynamicProperties.autoRenewEnabled()).thenReturn(false);
		when(account.getBalance()).thenReturn(0L);
		when(balanceChange.units()).thenReturn(5L);

		assertEquals(OK, subject.checkUsing(account, changeSet));
	}

	@Test
	void throwsAsExpected() {
		var iae = assertThrows(IllegalArgumentException.class,
				() -> subject.getEffective(AUTO_RENEW_PERIOD, account, null, changeSet));
		assertEquals("Property "+ AUTO_RENEW_PERIOD + " cannot be validated in scoped check", iae.getMessage());
	}
}
