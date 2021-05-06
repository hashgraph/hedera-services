package com.hedera.services.fees.calculation;

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

import com.hedera.services.fees.bootstrap.JsonToProtoSerde;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.usage.crypto.CryptoOpsUsage;
import com.hedera.services.usage.crypto.ExtantCryptoContext;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.fee.FeeBuilder;
import org.apache.commons.lang3.tuple.Triple;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.inject.Inject;
import java.time.Instant;

import static com.hedera.test.utils.IdUtils.asToken;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoAccountAutoRenew;
import static com.hederahashgraph.fee.FeeBuilder.FEE_DIVISOR_FACTOR;
import static com.hederahashgraph.fee.FeeBuilder.getTinybarsFromTinyCents;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;

@ExtendWith(LogCaptureExtension.class)
class AutoRenewCalcsTest {
	private final Instant preCutoff = Instant.ofEpochSecond(1_234_566L);
	private final Instant cutoff = Instant.ofEpochSecond(1_234_567L);

	private Triple<FeeData, Instant, FeeData> cryptoPrices;
	private MerkleAccount expiredAccount;
	private final CryptoOpsUsage cryptoOpsUsage = new CryptoOpsUsage();
	private final ExchangeRate activeRates = ExchangeRate.newBuilder()
			.setHbarEquiv(1)
			.setCentEquiv(10)
			.build();

	@Inject
	private LogCaptor logCaptor;

	@LoggingSubject
	private AutoRenewCalcs subject;

	@BeforeEach
	void setUp() throws Exception {
		cryptoPrices = frozenPricesFrom("fees/feeSchedules.json", CryptoAccountAutoRenew);
		subject = new AutoRenewCalcs(cryptoOpsUsage);
		subject.setCryptoAutoRenewPriceSeq(cryptoPrices);
	}

	@Test
	void warnsOnMissingFeeData() {
		// when:
		subject.setCryptoAutoRenewPriceSeq(Triple.of(null, cutoff, null));

		// then:
		assertThat(
				logCaptor.warnLogs(),
				contains(Matchers.startsWith("No prices known for CryptoAccountAutoRenew, will charge zero fees!")));
	}

	@Test
	void computesTinybarFromNominal() {
		// given:
		final long nominalFee = 1_234_567_000_000L;
		final long tinybarFee = getTinybarsFromTinyCents(activeRates, nominalFee / FEE_DIVISOR_FACTOR);

		// expect:
		assertEquals(tinybarFee, subject.inTinybars(nominalFee, activeRates));
	}

	@Test
	void computesExpectedUsdPriceForThreeMonthRenewal() {
		// setup:
		long expectedFeeInTinycents = 2200000L;
		long expectedFeeInTinybars = getTinybarsFromTinyCents(activeRates, expectedFeeInTinycents);
		long threeMonthsInSeconds = 7776000L;
		// and:
		setupSuperStandardAccountWith(Long.MAX_VALUE);

		// when:
		var maxRenewalAndFee = subject.maxRenewalAndFeeFor(expiredAccount, threeMonthsInSeconds, preCutoff, activeRates);
		// and:
		var percentageOfExpected = (1.0 * maxRenewalAndFee.fee()) / expectedFeeInTinybars * 100.0;

		// then:
		assertEquals(threeMonthsInSeconds, maxRenewalAndFee.renewalPeriod());
		// and:

		assertEquals(100.0, percentageOfExpected, 5.0);
	}

	@Test
	void throwsIseIfUsedWithoutInitializedPrices() {
		// given:
		subject = new AutoRenewCalcs(cryptoOpsUsage);

		// expect:
		Assertions.assertThrows(IllegalStateException.class, () ->
				subject.maxRenewalAndFeeFor(null, 0L, preCutoff, activeRates));
	}

	@Test
	void returnsZeroZeroIfBalanceIsZero() {
		setupAccountWith(0L);

		// when:
		var maxRenewalAndFee = subject.maxRenewalAndFeeFor(expiredAccount, 7776000L, preCutoff, activeRates);

		// then:
		assertEquals(0, maxRenewalAndFee.renewalPeriod());
		assertEquals(0, maxRenewalAndFee.fee());
	}


	@Test
	void knowsHowToBuildCtx() {
		setupAccountWith(0L);

		// given:
		var expectedCtx = ExtantCryptoContext.newBuilder()
				.setCurrentExpiry(0L)
				.setCurrentKey(MiscUtils.asKeyUnchecked(expiredAccount.getKey()))
				.setCurrentlyHasProxy(true)
				.setCurrentMemo(expiredAccount.getMemo())
				.setCurrentNumTokenRels(expiredAccount.tokens().numAssociations())
				.build();

		// expect:
		assertEquals(FeeBuilder.BASIC_ACCOUNT_SIZE +
				cryptoOpsUsage.cryptoAutoRenewRb(expectedCtx), subject.rbUsedBy(expiredAccount));
	}

	private Triple<FeeData, Instant, FeeData> frozenPricesFrom(
			String resource,
			HederaFunctionality autoRenewFunction
	) throws Exception {
		var schedules = JsonToProtoSerde.loadFeeScheduleFromJson(resource);
		var prePrices = schedules.getCurrentFeeSchedule().getTransactionFeeScheduleList().stream()
				.filter(transactionFeeSchedule -> transactionFeeSchedule.getHederaFunctionality() == autoRenewFunction)
				.findFirst()
				.get()
				.getFeeData();
		var postPrices = prePrices.toBuilder()
				.setServicedata(prePrices.getServicedata().toBuilder().setRbh(2 * prePrices.getServicedata().getRbh()))
				.build();
		return Triple.of(prePrices, cutoff, postPrices);
	}

	private void setupAccountWith(long balance) {
		expiredAccount = MerkleAccountFactory.newAccount()
				.accountKeys(TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asJKeyUnchecked())
				.balance(balance)
				.tokens(asToken("1.2.3"), asToken("2.3.4"), asToken("3.4.5"))
				.proxy(IdUtils.asAccount("0.0.12345"))
				.memo("SHOCKED, I tell you!")
				.get();
	}

	private void setupSuperStandardAccountWith(long balance) {
		expiredAccount = MerkleAccountFactory.newAccount()
				.accountKeys(TxnHandlingScenario.SIMPLE_NEW_ADMIN_KT.asJKeyUnchecked())
				.balance(balance)
				.get();
	}
}
