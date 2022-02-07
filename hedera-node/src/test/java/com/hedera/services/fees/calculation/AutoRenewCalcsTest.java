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

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.FcTokenAllowance;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.sysfiles.serdes.FeesJsonToProtoSerde;
import com.hedera.services.usage.crypto.CryptoOpsUsage;
import com.hedera.services.usage.crypto.ExtantCryptoContext;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import org.apache.commons.lang3.tuple.Triple;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.hedera.services.fees.calculation.AutoRenewCalcs.countSerials;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoAccountAutoRenew;
import static com.hederahashgraph.fee.FeeBuilder.FEE_DIVISOR_FACTOR;
import static com.hederahashgraph.fee.FeeBuilder.getTinybarsFromTinyCents;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(LogCaptureExtension.class)
class AutoRenewCalcsTest {
	private final Instant preCutoff = Instant.ofEpochSecond(1_234_566L);
	private final Instant cutoff = Instant.ofEpochSecond(1_234_567L);

	private Triple<Map<SubType, FeeData>, Instant, Map<SubType, FeeData>> cryptoPrices;
	private MerkleAccount expiredAccount;
	private final CryptoOpsUsage cryptoOpsUsage = new CryptoOpsUsage();
	private final ExchangeRate activeRates = ExchangeRate.newBuilder()
			.setHbarEquiv(1)
			.setCentEquiv(10)
			.build();

	@LoggingTarget
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
		var maxRenewalAndFee = subject.maxRenewalAndFeeFor(expiredAccount, threeMonthsInSeconds, preCutoff,
				activeRates);
		// and:
		var percentageOfExpected = (1.0 * maxRenewalAndFee.fee()) / expectedFeeInTinybars * 100.0;

		// then:
		assertEquals(threeMonthsInSeconds, maxRenewalAndFee.renewalPeriod());
		// and:

		assertEquals(100.0, percentageOfExpected, 5.0);
	}

	@Test
	void computesExpectedUsdPriceForSlightlyMoreThanThreeMonthRenewal() {
		// setup:
		long expectedFeeInTinycents = 2200000L;
		long expectedFeeInTinybars = getTinybarsFromTinyCents(activeRates, expectedFeeInTinycents);
		long threeMonthsInSeconds = 7776001L;
		// and:
		setupSuperStandardAccountWith(Long.MAX_VALUE);

		// when:
		var maxRenewalAndFee = subject.maxRenewalAndFeeFor(expiredAccount, threeMonthsInSeconds, preCutoff,
				activeRates);
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
				.setCurrentKey(MiscUtils.asKeyUnchecked(expiredAccount.getAccountKey()))
				.setCurrentlyHasProxy(true)
				.setCurrentMemo(expiredAccount.getMemo())
				.setCurrentNumTokenRels(expiredAccount.tokens().numAssociations())
				.setCurrentMaxAutomaticAssociations(expiredAccount.getMaxAutomaticAssociations())
				.setCurrentCryptoAllowanceCount(expiredAccount.getCryptoAllowances().size())
				.setCurrentTokenAllowanceCount(expiredAccount.getFungibleTokenAllowances().size())
				.setCurrentNftAllowanceCount(expiredAccount.getNftAllowances().size())
				.setCurrentNftSerialsCount(countSerials(expiredAccount.getNftAllowances()))
				.build();

		// expect:
		assertEquals(cryptoOpsUsage.cryptoAutoRenewRb(expectedCtx), subject.rbUsedBy(expiredAccount));
	}

	@Test
	void countsSerialsCorrectly() {
		final var spender1 = asAccount("0.0.1000");
		final var token1 = asToken("0.0.1000");
		final var token2 = asToken("0.0.1000");
		Map<FcTokenAllowanceId, FcTokenAllowance> nftAllowances = new TreeMap<>();
		final var id = FcTokenAllowanceId.from(EntityNum.fromTokenId(token1),
				EntityNum.fromAccountId(spender1));
		final var Nftid = FcTokenAllowanceId.from(EntityNum.fromTokenId(token2),
				EntityNum.fromAccountId(spender1));
		final var val = FcTokenAllowance.from(false, List.of(1L, 100L));
		nftAllowances.put(Nftid, val);
		assertEquals(2, countSerials(nftAllowances));
	}

	private Triple<Map<SubType, FeeData>, Instant, Map<SubType, FeeData>> frozenPricesFrom(
			String resource,
			HederaFunctionality autoRenewFunction
	) throws Exception {
		var schedules = FeesJsonToProtoSerde.loadFeeScheduleFromJson(resource);
		var prePrices = schedules.getCurrentFeeSchedule().getTransactionFeeScheduleList().stream()
				.filter(transactionFeeSchedule -> transactionFeeSchedule.getHederaFunctionality() == autoRenewFunction)
				.findFirst()
				.get()
				.getFeesList();
		var prePricesMap = toSubTypeMap(prePrices);

		var postPricesMap = toPostPrices(prePricesMap);
		return Triple.of(prePricesMap, cutoff, postPricesMap);
	}

	private Map<SubType, FeeData> toSubTypeMap(List<FeeData> feesList) {
		Map<SubType, FeeData> result = new HashMap<>();
		for (FeeData feeData : feesList) {
			result.put(feeData.getSubType(), feeData);
		}
		return result;
	}

	private Map<SubType, FeeData> toPostPrices(Map<SubType, FeeData> feeDataMap) {
		var changeableMap = new HashMap<>(feeDataMap);
		for (FeeData feeData : feeDataMap.values()) {
			var postPrices = feeData.toBuilder()
					.setServicedata(feeData.getServicedata().toBuilder().setRbh(2 * feeData.getServicedata().getRbh()))
					.build();
			changeableMap.put(postPrices.getSubType(), postPrices);
		}
		return changeableMap;
	}

	private void setupAccountWith(long balance) {
		expiredAccount = MerkleAccountFactory.newAccount()
				.accountKeys(TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asJKeyUnchecked())
				.balance(balance)
				.tokens(asToken("1.2.3"), asToken("2.3.4"), asToken("3.4.5"))
				.proxy(asAccount("0.0.12345"))
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
