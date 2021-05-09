package com.hedera.services.bdd.suites.autorenew;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.usage.crypto.ExtantCryptoContext;
import com.hedera.services.usage.crypto.entities.CryptoEntitySizes;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import junit.framework.Assert;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.google.protobuf.ByteString.copyFromUtf8;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUppercase;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.suites.autorenew.AutoRenewConfigChoices.disablingAutoRenewWithDefaults;
import static com.hedera.services.bdd.suites.autorenew.AutoRenewConfigChoices.enablingAutoRenewWith;
import static com.hedera.services.usage.crypto.entities.CryptoEntitySizes.CRYPTO_ENTITY_SIZES;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoAccountAutoRenew;
import static com.hederahashgraph.fee.FeeBuilder.FEE_DIVISOR_FACTOR;
import static com.hederahashgraph.fee.FeeBuilder.getTinybarsFromTinyCents;
import static java.util.concurrent.TimeUnit.SECONDS;

public class MacroFeesChargedSanityCheckSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(MacroFeesChargedSanityCheckSuite.class);

	public static void main(String... args) {
		new MacroFeesChargedSanityCheckSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						feesChargedMatchNumberOfRenewals(),

						macroFeesChargedSanityCheckSuiteCleanup(),
				}
		);
	}

	private HapiApiSpec feesChargedMatchNumberOfRenewals() {
		final long reqAutoRenew = 2L;
		final long startBalance = ONE_HUNDRED_HBARS;
		final var crazyShortAutoRenew = "czar";
		final String crazyMemo = "Calmer than you are!";
		final AtomicLong initialExpiry = new AtomicLong();
		final AtomicLong finalExpiry = new AtomicLong();
		final AtomicLong finalBalance = new AtomicLong();
		final AtomicLong duration = new AtomicLong(30);
		final AtomicReference<TimeUnit> unit = new AtomicReference<>(SECONDS);
		final AtomicInteger maxOpsPerSec = new AtomicInteger(100);

		final ExtantCryptoContext crazyCtx = ExtantCryptoContext.newBuilder()
				.setCurrentExpiry(0L)
				.setCurrentKey(Key.newBuilder().setEd25519(copyFromUtf8(randomUppercase(32))).build())
				.setCurrentlyHasProxy(true)
				.setCurrentMemo(crazyMemo)
				.setCurrentNumTokenRels(0)
				.build();

		return defaultHapiSpec("FeesChargedMatchNumberOfRenewals")
				.given(
						fileUpdate(APP_PROPERTIES)
								.payingWith(GENESIS)
								.overridingProps(enablingAutoRenewWith(1, 1234L)),
						cryptoCreate(crazyShortAutoRenew)
								.entityMemo(crazyMemo)
								.balance(startBalance)
								.autoRenewSecs(reqAutoRenew),
						getAccountInfo(crazyShortAutoRenew)
								.exposingExpiry(initialExpiry::set)
				).when(
						sleepFor(reqAutoRenew * 1_000L + 500L),
						runWithProvider(getAnyOldXfers())
								.lasting(duration::get, unit::get)
								.maxOpsPerSec(maxOpsPerSec::get)
				).then(
						/* The account with the crazy short auto-renew will have expired (and
						been auto-renewed) multiple times during the 30-second burst of
						CryptoTransfers. We want to confirm its balance changed as expected
						with the number of renewals. */
						assertionsHold((spec, opLog) -> {
							final var prices = spec.ratesProvider().currentSchedule().getTransactionFeeScheduleList()
									.stream()
									.filter(tfs -> tfs.getHederaFunctionality() == CryptoAccountAutoRenew)
									.findFirst()
									.get()
									.getFeeData();
							final var constantPrice = prices.getNodedata().getConstant() +
											prices.getNetworkdata().getConstant() +
											prices.getServicedata().getConstant();
							final var rbUsage = CRYPTO_ENTITY_SIZES.fixedBytesInAccountRepr() +
									crazyCtx.currentNonBaseRb();
							final var variablePrice = prices.getServicedata().getRbh() * rbUsage;
							final var rates = spec.ratesProvider().rates();
							final var tbFee = inTinybars(constantPrice, rates) + inTinybars(variablePrice, rates);
							opLog.info("Expected fee in tinybars: {}", tbFee);

							var infoOp = getAccountInfo(crazyShortAutoRenew)
									.exposingBalance(finalBalance::set)
									.exposingExpiry(finalExpiry::set);
							allRunFor(spec, infoOp);
							final long balanceChange = startBalance - finalBalance.get();
							final long expiryChange = finalExpiry.get() - initialExpiry.get();
							final int numRenewals = (int) (expiryChange / reqAutoRenew);
							opLog.info("{} renewals happened, extending expiry by {} and reducing balance by {}",
									numRenewals, expiryChange, balanceChange);
							Assert.assertEquals(balanceChange, numRenewals * tbFee);
						})
				);
	}

	private long inTinybars(long nominalFee, ExchangeRate rate) {
		return getTinybarsFromTinyCents(rate, nominalFee / FEE_DIVISOR_FACTOR);
	}

	private Function<HapiApiSpec, OpProvider> getAnyOldXfers() {
		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				return Collections.emptyList();
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				return Optional.of(cryptoTransfer(
						tinyBarsFromTo(GENESIS, FUNDING, 1L))
						.noLogging()
						.deferStatusResolution());
			}
		};
	}


	private HapiApiSpec macroFeesChargedSanityCheckSuiteCleanup() {
		return defaultHapiSpec("MacroFeesChargedSanityCheckSuiteCleanup")
				.given().when().then(
						fileUpdate(APP_PROPERTIES)
								.payingWith(GENESIS)
								.overridingProps(disablingAutoRenewWithDefaults())
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
