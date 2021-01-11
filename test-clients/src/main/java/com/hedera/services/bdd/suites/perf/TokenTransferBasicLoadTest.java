package com.hedera.services.bdd.suites.perf;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.assertions.AccountInfoAsserts;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.utilops.LoadTest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.NOISY_RETRY_PRECHECKS;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.tokenOpsEnablement;

public class TokenTransferBasicLoadTest extends LoadTest {
	private static final org.apache.logging.log4j.Logger log = LogManager.getLogger(TokenTransferBasicLoadTest.class);

	private static final Random r = new Random();

	public static void main(String... args) {
		parseArgs(args);

		TokenTransferBasicLoadTest suite = new TokenTransferBasicLoadTest();

		suite.runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				runTokenTransferLoadTest(),
//				miscChecks(),
		});
	}

	@Override
	public boolean hasInterestingStats() {
		return false;
	}

	private static String tokenRegistryName(int id) {
		return "TestToken" + id;
	}

	private Function<HapiApiSpec, OpProvider> tokenCreatesFactory(PerfTestLoadSettings settings) {
		AtomicInteger remaining = new AtomicInteger(settings.getTotalTokens() - 1);

		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				return Collections.emptyList();
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				int next;
				if ((next = remaining.getAndDecrement()) < 0) {
					return Optional.empty();
				}
				var payingTreasury = String.format("0.0.%d", settings.getTestTreasureStartAccount() + next);
				var op = tokenCreate(tokenRegistryName(next))
						.payingWith(payingTreasury)
						.initialSupply(100_000_000_000L)
						.treasury(payingTreasury)
						.fee(10_000_000_000L)
						.signedBy(GENESIS);
				return Optional.of(op);
			}
		};
	}

	private Function<HapiApiSpec, OpProvider> tokenAssociatesFactory(PerfTestLoadSettings settings) {
		int numTokens = settings.getTotalTokens();
		int numAccounts = settings.getTotalTestTokenAccounts();
		AtomicInteger remainingAssociations = new AtomicInteger(numTokens * numAccounts - 1);

		int startAccountId = settings.getTestTreasureStartAccount();
		/* Given n accounts, the association between the i-th token and the j-th account has id
		     assocId = i * numAccounts + j
		   Where:
		    - i is in the range [0, numTokens)
		    - j is in the range [0, numAccounts)
		*/

		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				return Collections.emptyList();
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				int nextAssocId;
				if ((nextAssocId = remainingAssociations.getAndDecrement()) < 0) {
					return Optional.empty();
				}
				int curToken = nextAssocId / numAccounts;
				int curAccount = nextAssocId % numAccounts;
				var accountId = "0.0." + (startAccountId + curAccount);
				var op = tokenAssociate(accountId, tokenRegistryName(curToken))
						.hasKnownStatusFrom(SUCCESS, TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT)
						.fee(A_HUNDRED_HBARS)
						.signedBy(GENESIS)
						.payingWith(GENESIS)
						.hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS)
						.deferStatusResolution()
						.noLogging()
						.suppressStats(true);
				return Optional.of(op);
			}
		};
	}

	private HapiApiSpec miscChecks() {
		return defaultHapiSpec("MiscChecks")
				.given().when().then(
						inParallel(IntStream.range(0, 500).mapToObj(i ->
								getAccountInfo("0.0." + (1001 + i))
										.has(accountWith().totalAssociatedTokens(100)))
								.toArray(HapiSpecOperation[]::new))

				);
	}

	private HapiApiSpec runTokenTransferLoadTest() {
		PerfTestLoadSettings settings = new PerfTestLoadSettings();

		Supplier<HapiSpecOperation[]> tokenTransferBurst = () -> new HapiSpecOperation[] {
				opSupplier(settings).get()
		};

		return defaultHapiSpec("TokenTransferBasicLoadTest")
				.given(
						tokenOpsEnablement(),
						withOpContext((spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap()))
				).when(
						sourcing(() -> runWithProvider(tokenCreatesFactory(settings))
//								.lasting(() -> 1, () -> TimeUnit.MINUTES)
								.lasting(() -> 5, () -> TimeUnit.SECONDS)
								.maxOpsPerSec(() -> 500)),
						sourcing(() -> runWithProvider(tokenAssociatesFactory(settings))
//								.lasting(() -> 5, () -> TimeUnit.MINUTES)
								.lasting(() -> 10, () -> TimeUnit.SECONDS)
								.maxOpsPerSec(() -> 500))
				).then(
						defaultLoadTest(tokenTransferBurst, settings)
				);
	}

	private static Supplier<HapiSpecOperation> opSupplier(PerfTestLoadSettings settings) {
		int tokenNum = r.nextInt(settings.getTotalTokens());
		int sender = r.nextInt(settings.getTotalTestTokenAccounts());
		int receiver = r.nextInt(settings.getTotalTestTokenAccounts());
		while (receiver == sender) {
			receiver = r.nextInt(settings.getTotalTestTokenAccounts());
		}
		String senderAcct = String.format("0.0.%d", settings.getTestTreasureStartAccount() + sender);
		String receiverAcct = String.format("0.0.%d", settings.getTestTreasureStartAccount() + receiver);

		if (log.isDebugEnabled()) {
			log.debug("Account 0.0.{} will send  1 token of testToken{} to account 0.0.{}", senderAcct, tokenNum,
					receiverAcct);
		}
		var op = cryptoTransfer(
				moving(1, tokenRegistryName(tokenNum)).between(senderAcct, receiverAcct)
		).payingWith(senderAcct)
				.signedBy(GENESIS)
				.fee(10_000_000_000L)
				.noLogging()
				.suppressStats(true)
				.hasPrecheckFrom(OK, INSUFFICIENT_PAYER_BALANCE)
				.hasRetryPrecheckFrom(BUSY, PLATFORM_TRANSACTION_NOT_CREATED)
				.hasKnownStatusFrom(SUCCESS, OK, INSUFFICIENT_TOKEN_BALANCE, TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)
				.deferStatusResolution();
		return () -> op;
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
