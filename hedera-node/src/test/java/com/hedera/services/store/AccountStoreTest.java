package com.hedera.services.store;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.exceptions.NegativeAccountBalanceException;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AccountStoreTest {
	@Mock
	private OptionValidator validator;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private FCMap<MerkleEntityId, MerkleAccount> accounts;

	private AccountStore subject;

	@BeforeEach
	void setUp() {
		setupAccounts();

		subject = new AccountStore(validator, dynamicProperties, () -> accounts);
	}

	/* --- Account loading --- */
	@Test
	void failsLoadingMissingAccount() {
		assertMiscAccountLoadFailsWith(INVALID_ACCOUNT_ID);
	}

	@Test
	void failsLoadingDeleted() {
		givenAccount(miscMerkleId, miscMerkleAccount);
		miscMerkleAccount.setDeleted(true);

		assertMiscAccountLoadFailsWith(ACCOUNT_DELETED);
	}

	@Test
	void failsLoadingDetached() throws NegativeAccountBalanceException {
		givenAccount(miscMerkleId, miscMerkleAccount);
		given(validator.isAfterConsensusSecond(expiry)).willReturn(true);
		given(dynamicProperties.autoRenewEnabled()).willReturn(true);
		miscMerkleAccount.setBalance(0L);

		assertMiscAccountLoadFailsWith(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
	}

	@Test
	void canAlwaysLoadWithNonzeroBalance() {
		givenAccount(miscMerkleId, miscMerkleAccount);
		given(dynamicProperties.autoRenewEnabled()).willReturn(true);

		// when:
		final var actualAccount = subject.loadAccount(miscId);

		// then:
		assertEquals(actualAccount, miscAccount);
	}

	@Test
	void saveAccountNotYetImplemented() {
		assertThrows(NotImplementedException.class, () -> subject.saveAccount(miscAccount));
	}

	private void givenAccount(MerkleEntityId anId, MerkleAccount anAccount) {
		given(accounts.get(anId)).willReturn(anAccount);
	}

	private void assertMiscAccountLoadFailsWith(ResponseCodeEnum status) {
		var ex = assertThrows(InvalidTransactionException.class, () -> subject.loadAccount(miscId));
		assertEquals(status, ex.getResponseCode());
	}

	private void setupAccounts() {
		miscMerkleAccount = MerkleAccountFactory.newAccount().balance(balance).expirationTime(expiry).get();

		miscAccount.setExpiry(expiry);
		miscAccount.initBalance(balance);
		autoRenewAccount.setExpiry(expiry);
		autoRenewAccount.initBalance(balance);
	}

	private final long expiry = 1_234_567L;
	private final long balance = 1_000L;
	private final long miscAccountNum = 1_234L;
	private final long autoRenewAccountNum = 3_234L;
	private final Id miscId = new Id(0, 0, miscAccountNum);
	private final Id autoRenewId = new Id(0, 0, autoRenewAccountNum);
	private final MerkleEntityId miscMerkleId = new MerkleEntityId(0, 0, miscAccountNum);
	private final Account miscAccount = new Account(miscId);
	private final Account autoRenewAccount = new Account(autoRenewId);

	private MerkleAccount miscMerkleAccount;
}