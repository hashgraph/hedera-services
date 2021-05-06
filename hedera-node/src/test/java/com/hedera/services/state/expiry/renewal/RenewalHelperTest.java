package com.hedera.services.state.expiry.renewal;

import com.hedera.services.config.HederaNumbers;
import com.hedera.services.config.MockHederaNumbers;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.ACCOUNT_EXPIRED_NONZERO_BALANCE;
import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.ACCOUNT_EXPIRED_ZERO_BALANCE;
import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.OTHER;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RenewalHelperTest {
	private final long now = 1_234_567L;
	private final long renewalPeriod = 3600L;
	private final long nonZeroBalance = 1L;

	private final MerkleAccount nonExpiredAccount = MerkleAccountFactory.newAccount()
			.balance(0).expirationTime(now + 1)
			.get();
	private final MerkleAccount expiredAccountZeroBalance = MerkleAccountFactory.newAccount()
			.balance(0).expirationTime(now - 1)
			.get();
	private final MerkleAccount expiredAccountNonZeroBalance = MerkleAccountFactory.newAccount()
			.balance(nonZeroBalance).expirationTime(now - 1)
			.get();
	private final MerkleAccount renewedExpiredAccount = MerkleAccountFactory.newAccount()
			.balance(0).expirationTime(now + renewalPeriod - 1)
			.get();
	private final long nonExpiredAccountNum = 1L, brokeExpiredAccountNum, fundedExpiredAccountNum = 3L;

	private final HederaNumbers nums = new MockHederaNumbers();

	@Mock
	private FCMap<MerkleEntityId, MerkleAccount> accounts;

	private RenewalHelper subject;

	RenewalHelperTest() {
		brokeExpiredAccountNum = 2L;
	}

	@BeforeEach
	void setUp() {
		subject = new RenewalHelper(nums, () -> accounts);
	}

	@Test
	void classifiesNonAccount() {
		// expect:
		assertEquals(OTHER, subject.classify(4L, now));
	}

	@Test
	void classifiesNonExpiredAccount() {
		givenPresent(nonExpiredAccountNum, nonExpiredAccount);

		// expect:
		assertEquals(OTHER, subject.classify(nonExpiredAccountNum, now));
	}

	@Test
	void classifiesExpiredAccountWithZeroBalance() {
		givenPresent(brokeExpiredAccountNum, expiredAccountZeroBalance);

		// expect:
		assertEquals(ACCOUNT_EXPIRED_ZERO_BALANCE, subject.classify(brokeExpiredAccountNum, now));
	}

	@Test
	void classifiesFundedExpiredAccount() {
		givenPresent(fundedExpiredAccountNum, expiredAccountNonZeroBalance);

		// expect:
		assertEquals(ACCOUNT_EXPIRED_NONZERO_BALANCE, subject.classify(fundedExpiredAccountNum, now));
		// and:
		assertEquals(expiredAccountNonZeroBalance, subject.getLastClassifiedAccount());
	}

	@Test
	void throwsOnRemovingIfLastClassifiedHadNonzeroBalance() {
		givenPresent(fundedExpiredAccountNum, expiredAccountNonZeroBalance);

		// when:
		subject.classify(fundedExpiredAccountNum, now);

		// expect:
		assertThrows(IllegalStateException.class, () -> subject.removeLastClassifiedEntity());
	}

	@Test
	void throwsOnRemovingIfNoLastClassified() {
		// expect:
		assertThrows(IllegalStateException.class, () -> subject.removeLastClassifiedEntity());
	}

	@Test
	void removesLastClassifiedIfAppropriate() {
		givenPresent(brokeExpiredAccountNum, expiredAccountZeroBalance);

		// when:
		subject.classify(brokeExpiredAccountNum, now);
		// and:
		subject.removeLastClassifiedEntity();

		// then:
		verify(accounts).remove(new MerkleEntityId(0, 0, brokeExpiredAccountNum));
	}

	@Test
	void renewsLastClassifiedAsRequested() {
		// setup:
		var key = new MerkleEntityId(0, 0, fundedExpiredAccountNum);

		givenPresent(fundedExpiredAccountNum, expiredAccountNonZeroBalance, true);

		// when:
		subject.classify(fundedExpiredAccountNum, now);
		// and:
		subject.renewLastClassifiedWith(nonZeroBalance, 3600L);

		// then:
		verify(accounts).getForModify(key);
		verify(accounts).replace(key, renewedExpiredAccount);
	}

	@Test
	void cannotRenewIfNoLastClassified() {
		// expect:
		assertThrows(IllegalStateException.class,
				() -> subject.renewLastClassifiedWith(nonZeroBalance, 3600L));
	}

	@Test
	void rejectsAsIseIfFeeIsUnaffordable() {
		givenPresent(brokeExpiredAccountNum, expiredAccountZeroBalance);

		// when:
		subject.classify(brokeExpiredAccountNum, now);
		// expect:
		assertThrows(IllegalStateException.class,
				() -> subject.renewLastClassifiedWith(nonZeroBalance, 3600L));
	}

	private void givenPresent(long num, MerkleAccount account) {
		givenPresent(num, account,false);
	}

	private void givenPresent(long num, MerkleAccount account, boolean modifiable) {
		var key = new MerkleEntityId(0, 0, num);
		given(accounts.containsKey(key)).willReturn(true);
		given(accounts.get(key)).willReturn(account);
		if (modifiable) {
			given(accounts.getForModify(key)).willReturn(account);
		}
	}
}