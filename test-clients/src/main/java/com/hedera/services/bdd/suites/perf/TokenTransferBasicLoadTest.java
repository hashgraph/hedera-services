package com.hedera.services.bdd.suites.perf;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.utilops.LoadTest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.tokenOpsEnablement;

public class TokenTransferBasicLoadTest extends LoadTest  {
	private static final org.apache.logging.log4j.Logger log = LogManager.getLogger(TokenTransferBasicLoadTest.class);

	private static final Random r = new Random();

	public static void main(String... args) {
		parseArgs(args);

		TokenTransferBasicLoadTest suite = new TokenTransferBasicLoadTest();
		suite.setReportStats(true);
		suite.runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(runTokenTransferLoadTest());
	}

	@Override
	public boolean hasInterestingStats() {
		return true;
	}

	private HapiApiSpec runTokenTransferLoadTest() {
		PerfTestLoadSettings settings = new PerfTestLoadSettings();

		Supplier<HapiSpecOperation[]> tokenTransferBurst = () -> new HapiSpecOperation[] {
				opSupplier(settings).get()
		};

		return defaultHapiSpec("TokenTransferBasicLoadTest")
				.given(
						tokenOpsEnablement()
				).when(
						withOpContext((spec, ignore) -> {
							settings.setFrom(spec.setup().ciPropertiesMap());
							int totalTestTokens = settings.getTotalTokens();
							int totalTestTokenAccounts = settings.getTotalTestTokenAccounts();
							int testTreasureStartAccount = settings.getTestTreasureStartAccount();

							List<String> tokens = new ArrayList<>(settings.getTotalTokens());
							for(int i = 0; i < totalTestTokens; i++) {
								tokens.add("token" + i);
							}

							List<HapiSpecOperation> ops = new ArrayList<>();

							for(int i = 0; i < totalTestTokens; i++) {
								ops.add(
										tokenCreate(tokens.get(i))
												.payingWith(String.format("0.0.%d", testTreasureStartAccount + i ))
												.initialSupply(100_000_000_000L)
												.treasury(String.format("0.0.%d", testTreasureStartAccount + i ))
												.fee(10_000_000_000L)
												.signedBy(GENESIS)
								);
							}
							CustomSpecAssert.allRunFor(spec, ops );

							ops.clear();

							for(int i = 0; i < totalTestTokenAccounts; ) {
								if(log.isDebugEnabled()) {
									log.debug("Build token association for {} accounts starting at 0.0.{}",
											Math.min(100, totalTestTokenAccounts), testTreasureStartAccount + i);
								}
								for(int j = 0; j < Math.min(100,totalTestTokenAccounts); j++) {
									ops.add(
											tokenAssociate(String.format("0.0.%d",testTreasureStartAccount + i + j), tokens)
													.fee(10_000_000_000L)
													.signedBy(GENESIS)
													.noLogging()
													.hasKnownStatusFrom(SUCCESS, TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT)
									);
								}
								CustomSpecAssert.allRunFor(spec, ops );
								i += Math.min(100,testTreasureStartAccount);
								ops.clear();
							}
						}),
						sleepFor(1000)
				).then(
						defaultLoadTest(tokenTransferBurst, settings)
				);
	}

	private static Supplier<HapiSpecOperation> opSupplier(PerfTestLoadSettings settings) {
		int tokenNum = r.nextInt(settings.getTotalTokens());
		int sender =  r.nextInt(settings.getTotalTestTokenAccounts());
		int receiver =  r.nextInt(settings.getTotalTestTokenAccounts());
		while (receiver == sender) {
			receiver =  r.nextInt(settings.getTotalTestTokenAccounts());
		}
		String senderAcct = String.format("0.0.%d", settings.getTestTreasureStartAccount() + sender);
		String receiverAcct  = String.format("0.0.%d", settings.getTestTreasureStartAccount()  + receiver);

		if(log.isDebugEnabled()) {
			log.debug("Account 0.0.{} will send  1 token of testToken{} to account 0.0.{}", senderAcct, tokenNum, receiverAcct );
		}
		var op =
				cryptoTransfer(
						moving(1, "token" + tokenNum).between(senderAcct, receiverAcct)
				).payingWith(senderAcct)
				.signedBy(GENESIS)
				.fee(10_000_000_000L)
				.noLogging()
				.suppressStats(true)
				.hasPrecheckFrom(OK, ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS,
						TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN, EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS, INSUFFICIENT_PAYER_BALANCE)
				.hasRetryPrecheckFrom(BUSY,PLATFORM_TRANSACTION_NOT_CREATED)
				.hasKnownStatusFrom(SUCCESS, OK, INSUFFICIENT_TOKEN_BALANCE,TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)
				.deferStatusResolution();
		return () -> op;
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
