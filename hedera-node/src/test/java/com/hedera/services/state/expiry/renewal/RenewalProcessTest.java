package com.hedera.services.state.expiry.renewal;

import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.config.MockHederaNumbers;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.ACCOUNT_EXPIRED_ZERO_BALANCE;
import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.OTHER;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class RenewalProcessTest {
	private final long now = 1_234_567L;
	private final long renewalPeriod = 3600L;
	private final long nonZeroBalance = 1L;
	private final Instant instantNow = Instant.ofEpochSecond(now);

	private final MerkleAccount nonExpiredAccount = MerkleAccountFactory.newAccount()
			.balance(0).expirationTime(now + 1)
			.get();
	private final MerkleAccount expiredAccountZeroBalance = MerkleAccountFactory.newAccount()
			.balance(0).expirationTime(now - 1)
			.get();
	private final MerkleAccount expiredAccountNonZeroBalance = MerkleAccountFactory.newAccount()
			.balance(nonZeroBalance).expirationTime(now - 1)
			.get();
	private final long nonExpiredAccountNum = 1002L, brokeExpiredAccountNum = 1003L, fundedExpiredAccountNum = 1004L;

	@Mock
	private FeeCalculator fees;
	@Mock
	private RenewalHelper helper;
	@Mock
	private RenewalFeeHelper feeHelper;
	@Mock
	private RenewalRecordsHelper recordsHelper;

	private MockGlobalDynamicProps dynamicProps = new MockGlobalDynamicProps();

	private RenewalProcess subject;

	@BeforeEach
	void setUp() {
		subject = new RenewalProcess(fees, new MockHederaNumbers(), helper, feeHelper, recordsHelper, dynamicProps);
	}

	@Test
	void throwsIfNotInCycle() {
		// expect:
		assertThrows(IllegalStateException.class, () -> subject.process(2));
	}

	@Test
	void doesNothingOnNonExpiredAccount() {
		given(helper.classify(nonExpiredAccountNum, now)).willReturn(OTHER);

		// when:
		subject.beginRenewalCycle(instantNow);
		// and:
		var wasTouched = subject.process(nonExpiredAccountNum);

		// then:
		assertFalse(wasTouched);
		verifyNoMoreInteractions(helper);
	}

	@Test
	void removesExpiredBrokeAccountIfNoGracePeriod() {
		// setup:
		dynamicProps.endGracePeriod();

		given(helper.classify(brokeExpiredAccountNum, now)).willReturn(ACCOUNT_EXPIRED_ZERO_BALANCE);

		// when:
		subject.beginRenewalCycle(instantNow);
		// and:
		var wasTouched = subject.process(brokeExpiredAccountNum);

		// then:
		assertTrue(wasTouched);
		verify(helper).removeLastClassifiedEntity();
		verify(recordsHelper).streamCryptoRemoval(new MerkleEntityId(0, 0, brokeExpiredAccountNum));
	}

	@Test
	void renewsNoFeesDuringGracePeriod() {
		// setup:
		long gracePeriod = dynamicProps.autoRenewGracePeriod();
		var key = new MerkleEntityId(0, 0, brokeExpiredAccountNum);

		given(helper.classify(brokeExpiredAccountNum, now)).willReturn(ACCOUNT_EXPIRED_ZERO_BALANCE);

		// when:
		subject.beginRenewalCycle(instantNow);
		// and:
		var wasTouched = subject.process(brokeExpiredAccountNum);

		// then:
		assertTrue(wasTouched);
		verify(helper).renewLastClassifiedWith(0L, gracePeriod);
		verify(recordsHelper).streamCryptoRenewal(key, 0L, now + gracePeriod);
	}
}