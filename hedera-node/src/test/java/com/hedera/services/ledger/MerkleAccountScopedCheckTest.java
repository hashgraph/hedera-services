package com.hedera.services.ledger;

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

import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.Assert.assertEquals;
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
	void hapyPath() {
		when(account.isSmartContract()).thenReturn(false);
		when(account.isDeleted()).thenReturn(false);
		when(dynamicProperties.autoRenewEnabled()).thenReturn(false);
		when(account.getBalance()).thenReturn(0L);
		when(balanceChange.units()).thenReturn(5L);

		assertEquals(OK, subject.checkUsing(account, changeSet));
	}

}
