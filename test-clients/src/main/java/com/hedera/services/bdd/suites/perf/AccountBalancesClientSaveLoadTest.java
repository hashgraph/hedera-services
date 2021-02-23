package com.hedera.services.bdd.suites.perf;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.utilops.LoadTest;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.NOISY_RETRY_PRECHECKS;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;

import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.exportAccountBalances;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freeze;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.tokenOpsEnablement;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_EXPIRED;


public class AccountBalancesClientSaveLoadTest extends LoadTest  {

	private static final org.apache.logging.log4j.Logger log = LogManager.getLogger(AccountBalancesClientSaveLoadTest.class);
	final static int EXPECTED_MAX_OPS_PER_SEC = 5_000;
	final static int MAX_PENDING_OPS_FOR_SETUP = 10_000;
	final static int ESTIMATED_TOKEN_CREATION_RATE = 50;
	final static long MIN_ACCOUNT_BALANCE = 1_000_000_000L;
	final static long MAX_ACCOUNT_BALANCE = 1_000_000_000L;
	final static int MIN_TOKEN_SUPPLY = 1000;
	final static int MAX_TOKEN_SUPPLY = 1_000_000;
	final static int MAX_TOKEN_TRANSFER = 100;
	final static int SECOND = 1000;
	private static final Random r = new Random();

	private final static int TOTAL_TEST_TOKENS = 500;
	private final static String TREASURE_ACCT_NAME_PREFIX = "treasure-acct-";
	private final static String TOKEN_NAME_PREFIX = "token-";

	private final static String NORMAL_ACCT_NAME_PREFIX = "acct-";
	private static int normal_acct_created = 0;

	private final static String ACCOUNT_FILE_EXPORT_DIR = "src/main/resource/accountBalancesClient.pb";

	// For simplification purpose, each treasure account has one token and they have same ordinal number
	private int totalTestTokenNum = TOTAL_TEST_TOKENS;
	List<Pair<Integer, Integer>> tokenAcctAssociations = new ArrayList<>();

	private final ResponseCodeEnum[] permissiblePrechecks = new ResponseCodeEnum[] {
			OK, BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED
	};

	public static void main(String... args) {
		parseArgs(args);
		AccountBalancesClientSaveLoadTest suite = new AccountBalancesClientSaveLoadTest();
		suite.runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {	runAccountBalancesClientSaveLoadTest() });
	}

	@Override
	public boolean hasInterestingStats() {
		return false;
	}

	private Function<HapiApiSpec, OpProvider> treasureAcctCreate(PerfTestLoadSettings settings) {
		totalTestTokenNum = settings.getTotalTokens() > 1 ? settings.getTotalTokens()
				: TOTAL_TEST_TOKENS;

		// Create same amount of accounts for the tokens
		AtomicInteger remaining = new AtomicInteger(totalTestTokenNum);

		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				log.info("Now running treasureAcctCreation initializer");
				return Collections.emptyList();
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				int next;
				if ((next = remaining.getAndDecrement()) < 0) {
					return Optional.empty();
				}

				var op =  cryptoCreate(String.format("%s%s",TREASURE_ACCT_NAME_PREFIX , next))
						.balance((long)(r.nextInt((int)ONE_HBAR) * 1000 + MIN_ACCOUNT_BALANCE))
						.key(GENESIS)
						.fee(A_HUNDRED_HBARS)
						.withRecharging()
						.rechargeWindow(30)
						.persists()
						.noLogging()
						;
				if (next > 0) {
					op.deferStatusResolution();
				}
				return Optional.of(op);
			}
		};
	}

	private Function<HapiApiSpec, OpProvider> tokenCreates(PerfTestLoadSettings settings) {
		AtomicInteger remaining = new AtomicInteger(totalTestTokenNum);

		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				log.info("Now running tokenAcctCreation initializer");
				return Collections.emptyList();
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				int next;
				if ((next = remaining.getAndDecrement()) < 0) {
					return Optional.empty();
				}
				var payingTreasury = String.format(TREASURE_ACCT_NAME_PREFIX + next);
				var op = tokenCreate(TOKEN_NAME_PREFIX + next)
						.signedBy(GENESIS)
						.fee(A_HUNDRED_HBARS)
						.initialSupply(MIN_TOKEN_SUPPLY + r.nextInt(MAX_TOKEN_SUPPLY))
						.treasury(payingTreasury)
						.hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS)
						.hasPrecheckFrom(DUPLICATE_TRANSACTION, OK)
						.hasKnownStatusFrom(SUCCESS, TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT,
								TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED, FAIL_INVALID)
						.suppressStats(true)
						.noLogging();
				if (next > 0) {
					op.deferStatusResolution();
				}
				return Optional.of(op);
			}
		};
	}



	private HapiApiSpec runAccountBalancesClientSaveLoadTest() {
		PerfTestLoadSettings settings = new PerfTestLoadSettings();
		AtomicInteger accountCreated = new AtomicInteger(0);

		Supplier<HapiSpecOperation[]> cryptoCreateBurst = () -> new HapiSpecOperation[] {
				withOpContext( (spec, log) -> {
					List<HapiSpecOperation> ops = new ArrayList<>();

					String acctName = NORMAL_ACCT_NAME_PREFIX + accountCreated.getAndIncrement();
					normal_acct_created = accountCreated.get();

					var op = cryptoCreate(acctName)
							.balance((long)(r.nextInt( (int)ONE_HBAR ) * 1_000))
							.fee(A_HUNDRED_HBARS)
							.noLogging()
							.persists()
							.hasPrecheckFrom(permissiblePrechecks)
							.deferStatusResolution()
							;
					ops.add(op);
					allRunFor(spec,  ops);
				})
		};


		return defaultHapiSpec("AccountBalancesClientSaveLoadTest" )
				.given(
						tokenOpsEnablement(),
						withOpContext((spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())),
						newKeyNamed("adminKey"),
						newKeyNamed("supplyKey")

				).when(
						sourcing(() -> runWithProvider(treasureAcctCreate(settings))
								.lasting(() -> totalTestTokenNum / ESTIMATED_TOKEN_CREATION_RATE + 10,
										() -> TimeUnit.SECONDS)
								.totalOpsToSumbit(() ->	(int)Math.ceil(totalTestTokenNum))
								.maxOpsPerSec(() -> EXPECTED_MAX_OPS_PER_SEC )
								.maxPendingOps(() -> MAX_PENDING_OPS_FOR_SETUP)),

						sleepFor(15 * SECOND),

						sourcing(() -> runWithProvider(tokenCreates(settings))
								.lasting(() -> totalTestTokenNum / ESTIMATED_TOKEN_CREATION_RATE + 10,
										() -> TimeUnit.SECONDS)
								.totalOpsToSumbit(() -> (int)Math.ceil(totalTestTokenNum))
								.maxOpsPerSec(() -> (EXPECTED_MAX_OPS_PER_SEC ))
								.maxPendingOps(() -> MAX_PENDING_OPS_FOR_SETUP)),

						sleepFor(12 * SECOND),

						defaultLoadTest(cryptoCreateBurst, settings),

						sleepFor( 30 * SECOND),

						sourcing(() -> runWithProvider(randomTokenAssociates(settings))
								.lasting(() -> settings.getDurationCreateTokenAssociation(),() -> TimeUnit.SECONDS)
								.maxOpsPerSec(() -> (EXPECTED_MAX_OPS_PER_SEC))
								.maxPendingOps(() -> MAX_PENDING_OPS_FOR_SETUP)),

						sleepFor( 15 * SECOND),

						sourcing(() -> runWithProvider(randomTransfers(settings))
								.lasting(() -> settings.getDurationTokenTransfer(),	() -> TimeUnit.SECONDS)
								.maxOpsPerSec(() -> (EXPECTED_MAX_OPS_PER_SEC ))
								.maxPendingOps(() -> MAX_PENDING_OPS_FOR_SETUP))
				).then(
						sleepFor(10 * SECOND),
						withOpContext( (spec, log) -> {
							log.info("Now get all {} accounts created ", spec.getAccounts().size());
							for(AccountID accountID : spec.getAccounts()) {

								var op = getAccountBalance("0.0." + accountID.getAccountNum())
										.hasAnswerOnlyPrecheckFrom(permissiblePrechecks)
										.persists(true)
										.noLogging()
										;
								allRunFor(spec, op);
							}
						}),
						sleepFor(15 * SECOND),
						exportAccountBalances(() -> ACCOUNT_FILE_EXPORT_DIR ),

						freeze().payingWith(GENESIS)
								.startingIn(10).seconds()
								.andLasting(180).seconds()

						);
	}

	private Function<HapiApiSpec, OpProvider> randomTokenAssociates(PerfTestLoadSettings settings) {

		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				log.info("Now running tokenAssociatesFactory initializer");
				return Collections.emptyList();
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				int tokenNum = r.nextInt(totalTestTokenNum);
				int acctNum = r.nextInt(normal_acct_created);
				String tokenName = TOKEN_NAME_PREFIX + tokenNum;
				String accountName = NORMAL_ACCT_NAME_PREFIX + acctNum;
				tokenAcctAssociations.add(Pair.of(tokenNum, acctNum));

				var op = tokenAssociate(accountName, tokenName)
						.signedBy(GENESIS)
						.hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS)
						.hasPrecheckFrom(DUPLICATE_TRANSACTION, OK)
						.hasKnownStatusFrom(SUCCESS, TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT,INVALID_SIGNATURE,
								TRANSACTION_EXPIRED, TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED,FAIL_INVALID)
						.fee(A_HUNDRED_HBARS)
						.noLogging()
						.suppressStats(true)
						.deferStatusResolution()
						;

				return Optional.of(op);
			}
		};
	}


	private Function<HapiApiSpec, OpProvider> randomTransfers(PerfTestLoadSettings settings) {

		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				log.info("Now running tokenTransferFactory initializer");
				return Collections.emptyList();
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				int nextTransfer = r.nextInt(tokenAcctAssociations.size());
				int tokenAndSenderOrd = tokenAcctAssociations.get(nextTransfer).getLeft();
				int receiverOrd = tokenAcctAssociations.get(nextTransfer).getRight();
				String tokenName = TOKEN_NAME_PREFIX + tokenAndSenderOrd;
				String senderAcctName = NORMAL_ACCT_NAME_PREFIX + tokenAndSenderOrd;
				String receivedAcctName = NORMAL_ACCT_NAME_PREFIX + receiverOrd;
				var op = cryptoTransfer(
						moving(r.nextInt(MAX_TOKEN_TRANSFER) + 1, tokenName).between(senderAcctName, receivedAcctName))
						.hasKnownStatusFrom(OK, SUCCESS, DUPLICATE_TRANSACTION,INVALID_SIGNATURE)
						.hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS)
						.noLogging()
						.signedBy(GENESIS)
						.suppressStats(true)
						.fee(A_HUNDRED_HBARS)
						.deferStatusResolution()
						;

				return Optional.of(op);
			}
		};
	}


	@Override
	protected Logger getResultsLogger() {
		return log;
	}

}
