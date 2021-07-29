package com.hedera.services.ledger;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.txns.validation.OptionValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Function;

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
	Function<AccountProperty, Object> getter;

	long expiry = 1234L;
	MerkleAccountScopedCheck subject;

	@BeforeEach
	void setUp() {
		subject = new MerkleAccountScopedCheck(dynamicProperties, validator, balanceChange);
	}

	@Test
	void failsAsExpectedForSmartContacts() {
		when(getter.apply(AccountProperty.IS_SMART_CONTRACT)).thenReturn(true);

		assertEquals(INVALID_ACCOUNT_ID, subject.checkUsing(getter));
	}

	@Test
	void failsAsExpectedForDeletedAccount() {
		when(getter.apply(AccountProperty.IS_SMART_CONTRACT)).thenReturn(false);
		when(getter.apply(AccountProperty.IS_DELETED)).thenReturn(true);

		assertEquals(ACCOUNT_DELETED, subject.checkUsing(getter));
	}

	@Test
	void failsAsExpectedForExpiredAccount() {
		when(getter.apply(AccountProperty.IS_SMART_CONTRACT)).thenReturn(false);
		when(getter.apply(AccountProperty.IS_DELETED)).thenReturn(false);
		when(dynamicProperties.autoRenewEnabled()).thenReturn(true);
		when(getter.apply(AccountProperty.BALANCE)).thenReturn(0L);
		when(getter.apply(AccountProperty.EXPIRY)).thenReturn(expiry);
		when(validator.isAfterConsensusSecond(expiry)).thenReturn(false);

		assertEquals(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL, subject.checkUsing(getter));
	}

	@Test
	void failsAsExpectedWhenInsufficientBalance() {
		when(getter.apply(AccountProperty.IS_SMART_CONTRACT)).thenReturn(false);
		when(getter.apply(AccountProperty.IS_DELETED)).thenReturn(false);
		when(dynamicProperties.autoRenewEnabled()).thenReturn(false);

		when(getter.apply(AccountProperty.BALANCE)).thenReturn(5L);
		when(balanceChange.units()).thenReturn(-6L);
		when(balanceChange.codeForInsufficientBalance()).thenReturn(INSUFFICIENT_ACCOUNT_BALANCE);

		assertEquals(INSUFFICIENT_ACCOUNT_BALANCE, subject.checkUsing(getter));
	}

	@Test
	void hapyPath() {
		when(getter.apply(AccountProperty.IS_SMART_CONTRACT)).thenReturn(false);
		when(getter.apply(AccountProperty.IS_DELETED)).thenReturn(false);
		when(dynamicProperties.autoRenewEnabled()).thenReturn(false);
		when(getter.apply(AccountProperty.BALANCE)).thenReturn(0L);
		when(balanceChange.units()).thenReturn(5L);

		assertEquals(OK, subject.checkUsing(getter));
	}

}
