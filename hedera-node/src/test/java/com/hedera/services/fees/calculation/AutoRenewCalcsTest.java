package com.hedera.services.fees.calculation;

import com.hedera.services.fees.bootstrap.JsonToProtoSerde;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.usage.crypto.CryptoOpsUsage;
import com.hedera.services.usage.crypto.ExtantCryptoContext;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.hedera.test.utils.IdUtils.asToken;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoAccountAutoRenew;
import static com.hederahashgraph.fee.FeeBuilder.HRS_DIVISOR;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AutoRenewCalcsTest {
	private final Instant preCutoff = Instant.ofEpochSecond(1_234_566L);
	private final Instant cutoff = Instant.ofEpochSecond(1_234_567L);
	private final Instant afterCutoff = Instant.ofEpochSecond(1_234_568L);

	private Triple<FeeData, Instant, FeeData> cryptoPrices;
	private MerkleAccount expiredAccount;
	private final CryptoOpsUsage cryptoOpsUsage = new CryptoOpsUsage();

	private AutoRenewCalcs subject;

	@BeforeEach
	void setUp() throws Exception {
		cryptoPrices = frozenPricesFrom("fees/feeSchedules.json", CryptoAccountAutoRenew);
		subject = new AutoRenewCalcs(cryptoOpsUsage);
		subject.setCryptoAutoRenewPriceSeq(cryptoPrices);
	}

	@Test
	void throwsIseIfUsedWithoutInitializedPrices() {
		// given:
		subject = new AutoRenewCalcs(cryptoOpsUsage);

		// expect:
		Assertions.assertThrows(IllegalStateException.class, () ->
				subject.maxRenewalAndFeeFor(null, 0L, preCutoff));
	}

	@Test
	void returnsZeroZeroIfBalanceIsZero() {
		setupAccountWith(0L);

		// when:
		var maxRenewalAndFee = subject.maxRenewalAndFeeFor(expiredAccount, 7776000L, preCutoff);

		// then:
		assertEquals(0, maxRenewalAndFee.getLeft());
		assertEquals(0, maxRenewalAndFee.getRight());
	}

	@Test
	void usesPostPricesWhenApropos() {
		setupAccountWith(1L);
		// and:
		long serviceRbhPrice = cryptoPrices.getRight().getServicedata().getRbh();
		long constantFees = cryptoPrices.getRight().getNetworkdata().getConstant() +
				cryptoPrices.getRight().getNodedata().getConstant() +
				cryptoPrices.getRight().getServicedata().getConstant();
		long rbUsage = subject.rbUsedBy(expiredAccount);
		long requested = 1231200L;
		long requestedHrs = requested / HRS_DIVISOR;
		long testBalance = constantFees + ((rbUsage * serviceRbhPrice) * requestedHrs);
		// and:
		setupAccountWith(testBalance);

		// when:
		var maxRenewalAndFee = subject.maxRenewalAndFeeFor(expiredAccount, requested, afterCutoff);

		// then:
		assertEquals(requested, maxRenewalAndFee.getLeft());
		// and:
		assertEquals(constantFees + serviceRbhPrice * requestedHrs, maxRenewalAndFee.getRight());
	}

	@Test
	void usesHelperMethodsAsExpected() {
		setupAccountWith(1L);
		// and:
		long serviceRbhPrice = cryptoPrices.getLeft().getServicedata().getRbh();
		long constantFees = cryptoPrices.getLeft().getNetworkdata().getConstant() +
				cryptoPrices.getLeft().getNodedata().getConstant() +
				cryptoPrices.getLeft().getServicedata().getConstant();
		long rbUsage = subject.rbUsedBy(expiredAccount);
		long requested = 1231200L;
		long requestedHrs = requested / HRS_DIVISOR;
		long testBalance = constantFees + ((rbUsage * serviceRbhPrice) * requestedHrs);
		// and:
		setupAccountWith(testBalance);

		// when:
		var maxRenewalAndFee = subject.maxRenewalAndFeeFor(expiredAccount, requested, preCutoff);

		// then:
		assertEquals(requested, maxRenewalAndFee.getLeft());
		// and:
		assertEquals(constantFees + serviceRbhPrice * requestedHrs, maxRenewalAndFee.getRight());
	}

	@Test
	void roundsUpToOneHourAndChargesRemainingBalanceIfOnlyRoundingErrorFundsLeft() {
		setupAccountWith(1L);
		// and:
		long requested = 7776000L;
		long testBalance = 1L;
		// and:
		setupAccountWith(testBalance);

		// when:
		var maxRenewalAndFee = subject.maxRenewalAndFeeFor(expiredAccount, requested, preCutoff);

		// then:
		assertEquals(HRS_DIVISOR, maxRenewalAndFee.getLeft());
		// and:
		assertEquals(testBalance, maxRenewalAndFee.getRight());
	}

	@Test
	void roundsUpToOneHourAndChargesOneHourFeeIfTinyRenewalRequested() {
		setupAccountWith(1L);
		// and:
		long serviceRbhPrice = cryptoPrices.getLeft().getServicedata().getRbh();
		long constantFees = cryptoPrices.getLeft().getNetworkdata().getConstant() +
				cryptoPrices.getLeft().getNodedata().getConstant() +
				cryptoPrices.getLeft().getServicedata().getConstant();
		long requested = 1800;
		long testBalance = 1_234_567_890_123L;
		// and:
		setupAccountWith(testBalance);

		// when:
		var maxRenewalAndFee = subject.maxRenewalAndFeeFor(expiredAccount, requested, preCutoff);

		// then:
		assertEquals(HRS_DIVISOR, maxRenewalAndFee.getLeft());
		// and:
		assertEquals(constantFees + serviceRbhPrice, maxRenewalAndFee.getRight());
	}

	@Test
	void recognizesUnaffordableRequestedPeriod() throws Exception {
		// setup:
		var cryptoPrices = frozenPricesFrom("fees/feeSchedules.json", CryptoAccountAutoRenew);
		long serviceRbhPrice = cryptoPrices.getLeft().getServicedata().getRbh();
		long constantFees = cryptoPrices.getLeft().getNetworkdata().getConstant() +
				cryptoPrices.getLeft().getNodedata().getConstant() +
				cryptoPrices.getLeft().getServicedata().getConstant();
		long rbUsage = 100L;
		long requestedRenewalHours = 1234L;
		long testBalance = constantFees + ((rbUsage * serviceRbhPrice) * requestedRenewalHours);

		// when:
		var actualMaxUsageHrs = subject.maxRenewableRbhGiven(
				rbUsage,
				2 * requestedRenewalHours * HRS_DIVISOR,
				testBalance);

		// then:
		assertEquals(requestedRenewalHours, actualMaxUsageHrs);
	}

	@Test
	void neverGoesBeyondMax() throws Exception {
		// setup:
		var cryptoPrices = frozenPricesFrom("fees/feeSchedules.json", CryptoAccountAutoRenew);
		long serviceRbhPrice = cryptoPrices.getLeft().getServicedata().getRbh();
		long constantFees = cryptoPrices.getLeft().getNetworkdata().getConstant() +
				cryptoPrices.getLeft().getNodedata().getConstant() +
				cryptoPrices.getLeft().getServicedata().getConstant();
		long rbUsage = 100L;
		long requestedRenewalHours = 1234L;
		long testBalance = constantFees + ((rbUsage * serviceRbhPrice) * requestedRenewalHours);

		// when:
		var actualMaxUsageHrs = subject.maxRenewableRbhGiven(
				rbUsage,
				requestedRenewalHours * HRS_DIVISOR / 2,
				testBalance);

		// then:
		assertEquals(requestedRenewalHours / 2, actualMaxUsageHrs);
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
		assertEquals(cryptoOpsUsage.cryptoAutoRenewRb(expectedCtx), subject.rbUsedBy(expiredAccount));
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
}