package com.hedera.services.fees.calculation;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hedera.test.factories.keys.KeyTree;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.fee.FeeBuilder;
import com.hederahashgraph.fee.FeeObject;
import com.hederahashgraph.fee.SigValueObj;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.exception.NoFeeScheduleExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;

import java.util.List;
import java.util.function.Function;

import static com.hedera.services.fees.calculation.AwareFcfsUsagePrices.DEFAULT_USAGE_PRICES;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.fee.FeeBuilder.getTinybarsFromTinyCents;
import static com.hederahashgraph.fee.FeeBuilder.getTransactionRecordFeeInTinyCents;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.*;
import static com.hedera.test.factories.txns.CryptoCreateFactory.newSignedCryptoCreate;
import static com.hedera.test.utils.IdUtils.*;

@RunWith(JUnitPlatform.class)
class UsageBasedFeeCalculatorTest {
	FeeComponents mockFees = FeeComponents.newBuilder()
			.setMax(1_234_567L)
			.setBpr(1_000_000L)
			.setBpt(2_000_000L)
			.setRbh(3_000_000L)
			.setSbh(4_000_000L).build();
	FeeData mockFeeData = FeeData.newBuilder()
			.setNetworkdata(mockFees).setNodedata(mockFees).setServicedata(mockFees).build();
	FeeData currentPrices = mockFeeData;
	FeeData resourceUsage = mockFeeData;
	ExchangeRate currentRate = ExchangeRate.newBuilder().setCentEquiv(22).setHbarEquiv(1).build();
	Query query;
	StateView view;
	Timestamp at = Timestamp.newBuilder().setSeconds(1_234_567L).build();
	HbarCentExchange exchange;
	UsagePricesProvider usagePrices;
	TxnResourceUsageEstimator correctOpEstimator;
	TxnResourceUsageEstimator incorrectOpEstimator;
	QueryResourceUsageEstimator correctQueryEstimator;
	QueryResourceUsageEstimator incorrectQueryEstimator;
	TransactionRecord record = TransactionRecord.newBuilder()
			.setTransferList(TxnUtils.withAdjustments(
					asAccount("1.2.3"), 100,
					asAccount("2.3.4"), -50,
					asAccount("3.4.5"), -50))
			.build();
	Function<HederaFunctionality, List<TxnResourceUsageEstimator>> txnUsageEstimators;

	PropertySource properties;
	UsageBasedFeeCalculator subject;
	/* Has nine simple keys. */
	KeyTree complexKey = TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
	JKey payerKey;
	Transaction signedTxn;
	SignedTxnAccessor accessor;

	@BeforeEach
	private void setup() throws Throwable {
		view = mock(StateView.class);
		query = mock(Query.class);
		payerKey = complexKey.asJKey();
		exchange = mock(HbarCentExchange.class);
		signedTxn = newSignedCryptoCreate()
				.payerKt(complexKey)
				.txnValidStart(at)
				.get();
		accessor = new SignedTxnAccessor(signedTxn);
		usagePrices = mock(UsagePricesProvider.class);
		given(usagePrices.activePrices()).willReturn(currentPrices);
		correctOpEstimator = mock(TxnResourceUsageEstimator.class);
		incorrectOpEstimator = mock(TxnResourceUsageEstimator.class);
		correctQueryEstimator = mock(QueryResourceUsageEstimator.class);
		incorrectQueryEstimator = mock(QueryResourceUsageEstimator.class);
		properties = mock(PropertySource.class);

		txnUsageEstimators = (Function<HederaFunctionality, List<TxnResourceUsageEstimator>>)mock(Function.class);

		subject = new UsageBasedFeeCalculator(
				properties,
				exchange,
				usagePrices,
				List.of(incorrectQueryEstimator, correctQueryEstimator),
				txnUsageEstimators);
	}

	@Test
	public void loadPriceSchedulesOnInit() throws Exception {
		// when:
		subject.init();

		// expect:
		verify(usagePrices).loadPriceSchedules();
	}

	@Test
	public void throwsIseOnBadScheduleInFcfs() throws Exception {
		willThrow(NoFeeScheduleExistsException.class).given(usagePrices).loadPriceSchedules();

		// expect:
		assertThrows(IllegalStateException.class, () -> subject.init());
	}

	@Test
	public void calculatesExpectedThresholdRecordFee() {
		// setup:
		int ttl = 10_000;

		given(exchange.activeRate()).willReturn(currentRate);
		given(usagePrices.activePrices()).willReturn(mockFeeData);
		given(properties.getIntProperty("ledger.records.ttl")).willReturn(ttl);
		// and:
		long shouldBe = expectedPriceForStorage(record, ttl);

		// when:
		long actual = subject.computeStorageFee(record);

		// then:
		assertEquals(shouldBe, actual);
	}

	@Test
	public void calculatesExpectedCachingFee() {
		// setup:
		int ttl = 100;

		given(exchange.activeRate()).willReturn(currentRate);
		given(usagePrices.activePrices()).willReturn(mockFeeData);
		given(properties.getIntProperty("cache.records.ttl")).willReturn(ttl);
		// and:
		long shouldBe = expectedPriceForStorage(record, ttl);

		// when:
		long actual = subject.computeCachingFee(record);

		// then:
		assertEquals(shouldBe, actual);
	}

	private long expectedPriceForStorage(TransactionRecord record, int ttl) {
		long rbhPrice = mockFeeData.getServicedata().getRbh();
		long feeInTinyCents = getTransactionRecordFeeInTinyCents(record, rbhPrice, ttl);
		return getTinybarsFromTinyCents(exchange.activeRate(), feeInTinyCents);
	}

	@Test
	public void failsWithIaeSansApplicableUsageCalculator() {
		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.computeFee(accessor, payerKey, view));
		assertThrows(IllegalArgumentException.class, () -> subject.computePayment(query, currentPrices, view, at));
	}

	@Test
	public void invokesQueryDelegateAsExpected() {
		// setup:
		FeeObject expectedFees = FeeBuilder.getFeeObject(currentPrices, resourceUsage, currentRate);

		given(correctQueryEstimator.applicableTo(query)).willReturn(true);
		given(incorrectQueryEstimator.applicableTo(query)).willReturn(false);
		given(correctQueryEstimator.usageGiven(query, view)).willReturn(resourceUsage);
		given(incorrectQueryEstimator.usageGiven(any(), any())).willThrow(RuntimeException.class);
		given(exchange.rate(at)).willReturn(currentRate);

		// when:
		FeeObject fees = subject.computePayment(query, currentPrices, view, at);

		// then:
		assertEquals(fees.getNodeFee(), expectedFees.getNodeFee());
		assertEquals(fees.getNetworkFee(), expectedFees.getNetworkFee());
		assertEquals(fees.getServiceFee(), expectedFees.getServiceFee());
	}

	@Test
	public void invokesQueryDelegateByTypeAsExpected() {
		// setup:
		FeeObject expectedFees = FeeBuilder.getFeeObject(currentPrices, resourceUsage, currentRate);

		given(correctQueryEstimator.applicableTo(query)).willReturn(true);
		given(incorrectQueryEstimator.applicableTo(query)).willReturn(false);
		given(correctQueryEstimator.usageGivenType(query, view, ANSWER_ONLY)).willReturn(resourceUsage);
		given(incorrectQueryEstimator.usageGivenType(any(), any(), any())).willThrow(RuntimeException.class);
		given(exchange.rate(at)).willReturn(currentRate);

		// when:
		FeeObject fees = subject.estimatePayment(query, currentPrices, view, at, ANSWER_ONLY);

		// then:
		assertEquals(fees.getNodeFee(), expectedFees.getNodeFee());
		assertEquals(fees.getNetworkFee(), expectedFees.getNetworkFee());
		assertEquals(fees.getServiceFee(), expectedFees.getServiceFee());
	}

	@Test
	public void invokesOpDelegateAsExpectedWithOneOption() throws Exception {
		// setup:
		SigValueObj expectedSigUsage = new SigValueObj(
				FeeBuilder.getSignatureCount(signedTxn),
				9,
				FeeBuilder.getSignatureSize(signedTxn));
		FeeObject expectedFees = FeeBuilder.getFeeObject(currentPrices, resourceUsage, currentRate);

		given(correctOpEstimator.applicableTo(accessor.getTxn())).willReturn(true);
		given(txnUsageEstimators.apply(CryptoCreate)).willReturn(List.of(correctOpEstimator));
		given(correctOpEstimator.usageGiven(
				argThat(accessor.getTxn()::equals),
				argThat(factory.apply(expectedSigUsage)),
				argThat(view::equals))).willReturn(resourceUsage);
		given(exchange.activeRate()).willReturn(currentRate);

		// when:
		FeeObject fees = subject.computeFee(accessor, payerKey, view);

		// then:
		assertEquals(fees.getNodeFee(), expectedFees.getNodeFee());
		assertEquals(fees.getNetworkFee(), expectedFees.getNetworkFee());
		assertEquals(fees.getServiceFee(), expectedFees.getServiceFee());
	}

	@Test
	public void invokesOpDelegateAsExpectedWithTwoOptions() throws Exception {
		// setup:
		SigValueObj expectedSigUsage = new SigValueObj(
				FeeBuilder.getSignatureCount(signedTxn),
				9,
				FeeBuilder.getSignatureSize(signedTxn));
		FeeObject expectedFees = FeeBuilder.getFeeObject(currentPrices, resourceUsage, currentRate);

		given(correctOpEstimator.applicableTo(accessor.getTxn())).willReturn(true);
		given(incorrectOpEstimator.applicableTo(accessor.getTxn())).willReturn(false);
		given(txnUsageEstimators.apply(CryptoCreate)).willReturn(List.of(incorrectOpEstimator, correctOpEstimator));
		given(correctOpEstimator.usageGiven(
				argThat(accessor.getTxn()::equals),
				argThat(factory.apply(expectedSigUsage)),
				argThat(view::equals))).willReturn(resourceUsage);
		given(incorrectOpEstimator.usageGiven(any(), any(), any())).willThrow(RuntimeException.class);
		given(exchange.rate(at)).willReturn(currentRate);
		given(usagePrices.activePrices()).willThrow(RuntimeException.class);
		given(usagePrices.pricesGiven(CryptoCreate, at)).willReturn(currentPrices);

		// when:
		FeeObject fees = subject.estimateFee(accessor, payerKey, view, at);

		// then:
		assertEquals(fees.getNodeFee(), expectedFees.getNodeFee());
		assertEquals(fees.getNetworkFee(), expectedFees.getNetworkFee());
		assertEquals(fees.getServiceFee(), expectedFees.getServiceFee());
	}


	@Test
	public void invokesOpDelegateAsExpectedForEstimateOfUnrecognizable() throws Exception {
		// setup:
		SigValueObj expectedSigUsage = new SigValueObj(
				FeeBuilder.getSignatureCount(signedTxn),
				9,
				FeeBuilder.getSignatureSize(signedTxn));
		FeeObject expectedFees = FeeBuilder.getFeeObject(DEFAULT_USAGE_PRICES, resourceUsage, currentRate);

		given(txnUsageEstimators.apply(CryptoCreate)).willReturn(List.of(correctOpEstimator));
		given(correctOpEstimator.applicableTo(accessor.getTxn())).willReturn(true);
		given(correctOpEstimator.usageGiven(
				argThat(accessor.getTxn()::equals),
				argThat(factory.apply(expectedSigUsage)),
				argThat(view::equals))).willReturn(resourceUsage);
		given(exchange.rate(at)).willReturn(currentRate);
		given(usagePrices.pricesGiven(CryptoCreate, at)).willThrow(RuntimeException.class);

		// when:
		FeeObject fees = subject.estimateFee(accessor, payerKey, view, at);

		// then:
		assertEquals(fees.getNodeFee(), expectedFees.getNodeFee());
		assertEquals(fees.getNetworkFee(), expectedFees.getNetworkFee());
		assertEquals(fees.getServiceFee(), expectedFees.getServiceFee());
	}

	private Function<SigValueObj, ArgumentMatcher<SigValueObj>> factory = expectedSigUsage -> sigUsage ->
			expectedSigUsage.getSignatureSize() == sigUsage.getSignatureSize()
					&& expectedSigUsage.getPayerAcctSigCount() == sigUsage.getPayerAcctSigCount()
					&& expectedSigUsage.getSignatureSize() == sigUsage.getSignatureSize();
}
