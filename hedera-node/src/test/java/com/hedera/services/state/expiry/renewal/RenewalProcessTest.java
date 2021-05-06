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
import com.hedera.services.config.MockHederaNumbers;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.calculation.AutoRenewCalcs;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.ACCOUNT_EXPIRED_NONZERO_BALANCE;
import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.ACCOUNT_EXPIRED_ZERO_BALANCE;
import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.OTHER;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class RenewalProcessTest {
	private final long now = 1_234_567L;
	private final long requestedRenewalPeriod = 3601L;
	private final long nonZeroBalance = 2L;
	private final long fee = 1L;
	private final long actualRenewalPeriod = 3600L;
	private final Instant instantNow = Instant.ofEpochSecond(now);

	private final MerkleAccount expiredAccountNonZeroBalance = MerkleAccountFactory.newAccount()
			.autoRenewPeriod(requestedRenewalPeriod)
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
	void startsHelperRenewalCycles() {
		// when:
		subject.beginRenewalCycle(instantNow);

		// then:
		verify(feeHelper).beginChargingCycle();
		verify(recordsHelper).beginRenewalCycle(instantNow);
	}

	@Test
	void throwsIfEndingButNotStarted() {
		// expect:
		Assertions.assertThrows(IllegalStateException.class, subject::endRenewalCycle);
	}

	@Test
	void endsAsExpectedIfStarted() {
		// given:
		subject.beginRenewalCycle(instantNow);

		// when:
		subject.endRenewalCycle();

		// then:
		verify(feeHelper).endChargingCycle();
		verify(recordsHelper).endRenewalCycle();
		assertNull(subject.getCycleTime());
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

		// and when:
		subject.endRenewalCycle();

		// then:
		assertNull(subject.getCycleTime());
	}

	@Test
	void renewsAtExpectedFee() {
		// setup:
		long gracePeriod = dynamicProps.autoRenewGracePeriod();
		var key = new MerkleEntityId(0, 0, fundedExpiredAccountNum);

		given(helper.classify(fundedExpiredAccountNum, now)).willReturn(ACCOUNT_EXPIRED_NONZERO_BALANCE);
		given(helper.getLastClassifiedAccount()).willReturn(expiredAccountNonZeroBalance);
		given(fees.assessCryptoAutoRenewal(expiredAccountNonZeroBalance, requestedRenewalPeriod, instantNow))
				.willReturn(new AutoRenewCalcs.RenewAssessment(fee, actualRenewalPeriod));

		// when:
		subject.beginRenewalCycle(instantNow);
		// and:
		var wasTouched = subject.process(fundedExpiredAccountNum);

		// then:
		assertTrue(wasTouched);
		verify(helper).renewLastClassifiedWith(fee, actualRenewalPeriod);
		verify(recordsHelper).streamCryptoRenewal(key, fee, now + actualRenewalPeriod);
	}
}
