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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.calculation.RenewAssessment;
import com.hedera.services.state.expiry.removal.AccountGC;
import com.hedera.services.state.expiry.removal.ContractGC;
import com.hedera.services.state.expiry.removal.TreasuryReturns;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;

import static com.hedera.services.state.expiry.EntityProcessResult.DONE;
import static com.hedera.services.state.expiry.EntityProcessResult.NOTHING_TO_DO;
import static com.hedera.services.state.expiry.EntityProcessResult.STILL_MORE_TO_DO;
import static com.hedera.services.state.expiry.renewal.RenewableEntityType.DETACHED_ACCOUNT;
import static com.hedera.services.state.expiry.renewal.RenewableEntityType.DETACHED_ACCOUNT_GRACE_PERIOD_OVER;
import static com.hedera.services.state.expiry.renewal.RenewableEntityType.DETACHED_CONTRACT_GRACE_PERIOD_OVER;
import static com.hedera.services.state.expiry.renewal.RenewableEntityType.DETACHED_TREASURY_GRACE_PERIOD_OVER_BEFORE_TOKEN;
import static com.hedera.services.state.expiry.renewal.RenewableEntityType.EXPIRED_ACCOUNT_READY_TO_RENEW;
import static com.hedera.services.state.expiry.renewal.RenewableEntityType.EXPIRED_CONTRACT_READY_TO_RENEW;
import static com.hedera.services.state.expiry.renewal.RenewableEntityType.OTHER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

	private final MerkleAccount mockAccount = MerkleAccountFactory.newAccount()
			.autoRenewPeriod(requestedRenewalPeriod)
			.balance(nonZeroBalance)
			.expirationTime(now - 1)
			.get();
	private final MerkleAccount mockContract = MerkleAccountFactory.newContract()
			.autoRenewPeriod(requestedRenewalPeriod)
			.balance(nonZeroBalance)
			.expirationTime(now - 1)
			.get();
	private final long nonExpiredAccountNum = 1002L;

	@Mock
	private FeeCalculator fees;
	@Mock
	private RenewableEntityClassifier helper;
	@Mock
	private AccountGC accountGC;
	@Mock
	private ContractGC contractGC;
	@Mock
	private RenewalRecordsHelper recordsHelper;
	@Mock
	private GlobalDynamicProperties dynamicProperties;

	private RenewalProcess subject;

	@BeforeEach
	void setUp() {
		subject = new RenewalProcess(
				accountGC, contractGC, fees, helper, dynamicProperties, recordsHelper);
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
		verify(recordsHelper).beginRenewalCycle();
	}

	@Test
	void throwsIfEndingButNotStarted() {
		// expect:
		Assertions.assertThrows(IllegalStateException.class, subject::endRenewalCycle);
	}

	@Test
	void throwsIfStartingButNotEnded() {
		// when:
		subject.beginRenewalCycle(instantNow);

		// expect:
		Assertions.assertThrows(IllegalStateException.class, () -> subject.beginRenewalCycle(instantNow));
	}

	@Test
	void endsAsExpectedIfStarted() {
		// given:
		subject.beginRenewalCycle(instantNow);

		// when:
		subject.endRenewalCycle();

		// then:
		verify(recordsHelper).endRenewalCycle();
		assertNull(subject.getCycleTime());
	}

	@Test
	void doesNothingOnNonExpiredAccount() {
		given(helper.classify(EntityNum.fromLong(nonExpiredAccountNum), now)).willReturn(OTHER);

		subject.beginRenewalCycle(instantNow);
		var result = subject.process(nonExpiredAccountNum);

		assertEquals(NOTHING_TO_DO, result);
		verifyNoMoreInteractions(helper);
	}

	@Test
	void doesNothingDuringGracePeriod() {
		given(helper.classify(EntityNum.fromLong(nonExpiredAccountNum), now)).willReturn(DETACHED_ACCOUNT);

		subject.beginRenewalCycle(instantNow);
		var result = subject.process(nonExpiredAccountNum);

		// then:
		assertEquals(NOTHING_TO_DO, result);
		verifyNoMoreInteractions(helper);
	}

	@Test
	void doesNothingForTreasuryWithTokenStillLive() {
		given(helper.classify(EntityNum.fromLong(nonExpiredAccountNum), now))
				.willReturn(DETACHED_TREASURY_GRACE_PERIOD_OVER_BEFORE_TOKEN);

		subject.beginRenewalCycle(instantNow);
		final var result = subject.process(nonExpiredAccountNum);

		assertEquals(NOTHING_TO_DO, result);
		verifyNoMoreInteractions(helper);
	}

	@Test
	void ignoresExpiredBrokeAccountIfNotTargetType() {
		long brokeExpiredAccountNum = 1003L;
		final var expiredNum = EntityNum.fromLong(brokeExpiredAccountNum);
		given(helper.classify(expiredNum, now)).willReturn(DETACHED_ACCOUNT_GRACE_PERIOD_OVER);

		subject.beginRenewalCycle(instantNow);
		final var result = subject.process(brokeExpiredAccountNum);

		assertEquals(NOTHING_TO_DO, result);
	}

	@Test
	void ignoresExpiredBrokeContractIfNotTargetType() {
		long brokeExpiredAccountNum = 1003L;
		final var expiredNum = EntityNum.fromLong(brokeExpiredAccountNum);
		given(helper.classify(expiredNum, now)).willReturn(EXPIRED_CONTRACT_READY_TO_RENEW);

		subject.beginRenewalCycle(instantNow);
		final var result = subject.process(brokeExpiredAccountNum);

		assertEquals(NOTHING_TO_DO, result);
	}

	@Test
	void removesExpiredBrokeAccount() {
		final var treasuryReturns = new TreasuryReturns(Collections.emptyList(), Collections.emptyList(), true);

		long brokeExpiredAccountNum = 1003L;
		final var expiredNum = EntityNum.fromLong(brokeExpiredAccountNum);
		given(helper.classify(expiredNum, now)).willReturn(DETACHED_ACCOUNT_GRACE_PERIOD_OVER);
		given(helper.getLastClassified()).willReturn(mockAccount);
		given(accountGC.expireBestEffort(expiredNum, mockAccount)).willReturn(treasuryReturns);
		given(dynamicProperties.shouldAutoRenewAccounts()).willReturn(true);

		subject.beginRenewalCycle(instantNow);
		final var result = subject.process(brokeExpiredAccountNum);

		assertEquals(DONE, result);
		verify(accountGC).expireBestEffort(expiredNum, mockAccount);
		verify(recordsHelper).streamCryptoRemoval(
				expiredNum,
				Collections.emptyList(),
				Collections.emptyList());
	}

	@Test
	void removesExpiredBrokeContractImmediatelyIfStoragePurged() {
		final var treasuryReturns = new TreasuryReturns(Collections.emptyList(), Collections.emptyList(), true);

		long brokeExpiredContractNum = 1003L;
		final var expiredNum = EntityNum.fromLong(brokeExpiredContractNum);
		given(helper.classify(expiredNum, now)).willReturn(DETACHED_CONTRACT_GRACE_PERIOD_OVER);
		given(helper.getLastClassified()).willReturn(mockContract);
		given(contractGC.expireBestEffort(expiredNum, mockContract)).willReturn(true);
		given(accountGC.expireBestEffort(expiredNum, mockContract)).willReturn(treasuryReturns);
		given(dynamicProperties.shouldAutoRenewContracts()).willReturn(true);

		subject.beginRenewalCycle(instantNow);
		final var result = subject.process(brokeExpiredContractNum);

		assertEquals(DONE, result);
		verify(accountGC).expireBestEffort(expiredNum, mockContract);
		verify(recordsHelper).streamCryptoRemoval(
				expiredNum,
				Collections.emptyList(),
				Collections.emptyList());
	}

	@Test
	void doesntExpireBrokeContractUntilStoragePurged() {
		long brokeExpiredContractNum = 1003L;
		final var expiredNum = EntityNum.fromLong(brokeExpiredContractNum);
		given(helper.classify(expiredNum, now)).willReturn(DETACHED_CONTRACT_GRACE_PERIOD_OVER);
		given(helper.getLastClassified()).willReturn(mockContract);
		given(dynamicProperties.shouldAutoRenewContracts()).willReturn(true);

		subject.beginRenewalCycle(instantNow);
		final var result = subject.process(brokeExpiredContractNum);

		assertEquals(STILL_MORE_TO_DO, result);
		verify(recordsHelper).beginRenewalCycle();
		verifyNoMoreInteractions(accountGC, recordsHelper);
	}

	@Test
	void alertsIfNotAllExpirationWorkCanBeDone() {
		final var treasuryReturns = new TreasuryReturns(Collections.emptyList(), Collections.emptyList(), false);

		long brokeExpiredAccountNum = 1003L;
		final var expiredNum = EntityNum.fromLong(brokeExpiredAccountNum);
		given(helper.classify(expiredNum, now)).willReturn(DETACHED_ACCOUNT_GRACE_PERIOD_OVER);
		given(helper.getLastClassified()).willReturn(mockAccount);
		given(accountGC.expireBestEffort(expiredNum, mockAccount)).willReturn(treasuryReturns);
		given(dynamicProperties.shouldAutoRenewAccounts()).willReturn(true);

		subject.beginRenewalCycle(instantNow);
		final var result = subject.process(brokeExpiredAccountNum);

		assertEquals(STILL_MORE_TO_DO, result);
		verify(accountGC).expireBestEffort(expiredNum, mockAccount);
		verify(recordsHelper).streamCryptoRemoval(
				expiredNum,
				Collections.emptyList(),
				Collections.emptyList());
	}

	@Test
	void renewsAccountAtExpectedFee() {
		// setup:
		long fundedExpiredAccountNum = 1004L;
		var key = EntityNum.fromLong(fundedExpiredAccountNum);
		mockAccount.setKey(key);

		given(helper.classify(EntityNum.fromLong(fundedExpiredAccountNum), now)).willReturn(
				EXPIRED_ACCOUNT_READY_TO_RENEW);
		given(helper.getLastClassified()).willReturn(mockAccount);
		given(fees.assessCryptoAutoRenewal(mockAccount, requestedRenewalPeriod, instantNow, mockAccount))
				.willReturn(new RenewAssessment(fee, actualRenewalPeriod));
		given(dynamicProperties.shouldAutoRenewAccounts()).willReturn(true);
		given(helper.resolvePayerForAutoRenew()).willReturn(mockAccount);

		subject.beginRenewalCycle(instantNow);
		final var result = subject.process(fundedExpiredAccountNum);

		assertEquals(DONE, result);
		verify(helper).renewLastClassifiedWith(fee, actualRenewalPeriod);
		verify(recordsHelper).streamCryptoRenewal(key, fee, now - 1 + actualRenewalPeriod, false, key);
	}

	@Test
	void renewsContractAtExpectedFee() {
		// setup:
		long fundedExpiredContractNum = 1004L;
		var key = EntityNum.fromLong(fundedExpiredContractNum);
		mockContract.setKey(key);

		given(helper.classify(EntityNum.fromLong(fundedExpiredContractNum), now)).willReturn(
				EXPIRED_CONTRACT_READY_TO_RENEW);
		given(helper.getLastClassified()).willReturn(mockContract);
		given(fees.assessCryptoAutoRenewal(mockContract, requestedRenewalPeriod, instantNow, mockContract))
				.willReturn(new RenewAssessment(fee, actualRenewalPeriod));
		given(dynamicProperties.shouldAutoRenewContracts()).willReturn(true);
		given(helper.resolvePayerForAutoRenew()).willReturn(mockContract);

		subject.beginRenewalCycle(instantNow);
		final var result = subject.process(fundedExpiredContractNum);

		assertEquals(DONE, result);
		verify(helper).renewLastClassifiedWith(fee, actualRenewalPeriod);
		verify(recordsHelper).streamCryptoRenewal(key, fee, now - 1 + actualRenewalPeriod, true, key);
	}

	@Test
	void skipsAccountRenewalIfNotTargeted() {
		// setup:
		long fundedExpiredAccountNum = 1004L;

		given(helper.classify(EntityNum.fromLong(fundedExpiredAccountNum), now)).willReturn(
				EXPIRED_ACCOUNT_READY_TO_RENEW);

		subject.beginRenewalCycle(instantNow);
		final var result = subject.process(fundedExpiredAccountNum);

		assertEquals(NOTHING_TO_DO, result);
	}
}
