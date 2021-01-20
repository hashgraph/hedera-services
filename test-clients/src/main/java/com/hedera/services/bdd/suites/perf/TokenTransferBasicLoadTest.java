package com.hedera.services.bdd.suites.perf;

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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.utilops.LoadTest;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Collections;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.function.Function;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.NOISY_RETRY_PRECHECKS;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.tokenOpsEnablement;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_EXPIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;

public class TokenTransferBasicLoadTest extends LoadTest {
	private static final org.apache.logging.log4j.Logger log = LogManager.getLogger(TokenTransferBasicLoadTest.class);
	final static int EXPECTED_MAX_OPS_PER_SEC = 5_000;
	final static int MAX_PENDING_OPS_FOR_SETUP = 10_000;
	final static int ESTIMATED_TOKEN_CREATION_RATE = 50;
	private static final Random r = new Random();

	public static void main(String... args) {
		parseArgs(args);
		TokenTransferBasicLoadTest suite = new TokenTransferBasicLoadTest();
		suite.runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {	runTokenTransferBasicLoadTest() });
	}

	@Override
	public boolean hasInterestingStats() {
		return false;
	}

	private static String tokenRegistryName(int id) {
		return "TestToken" + id;
	}

	private Function<HapiApiSpec, OpProvider> tokenCreatesFactory(PerfTestLoadSettings settings) {
		int numTotalTokens = settings.getTotalTokens();
		int totalClients = settings.getTotalClients();
		int numActiveTokens = (totalClients >= 1) ? numTotalTokens / totalClients : numTotalTokens;
		AtomicInteger remaining = new AtomicInteger(numActiveTokens - 1);

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
						.payingWith(GENESIS)
						.signedBy(GENESIS)
						.fee(A_HUNDRED_HBARS)
						.initialSupply(100_000_000_000L)
						.treasury(payingTreasury)
						.hasRetryPrecheckFrom(BUSY, PLATFORM_TRANSACTION_NOT_CREATED, DUPLICATE_TRANSACTION,INSUFFICIENT_PAYER_BALANCE)
						.hasPrecheckFrom(DUPLICATE_TRANSACTION, OK)
						.hasKnownStatusFrom(SUCCESS, TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT,TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED, FAIL_INVALID)
						.suppressStats(true)
						.noLogging();
				return Optional.of(op);
			}
		};
	}

	private Function<HapiApiSpec, OpProvider> activeTokenAssociatesFactory(PerfTestLoadSettings settings) {
		int numTotalTokens = settings.getTotalTokens();
		int numActiveTokenAccounts = settings.getTotalTestTokenAccounts();
		int totalClients = settings.getTotalClients();
		int numActiveTokens = (totalClients >= 1) ? numTotalTokens / totalClients : numTotalTokens;
		AtomicLong remainingAssociations = new AtomicLong(numActiveTokens * numActiveTokenAccounts - 1);

		if(log.isDebugEnabled()) {
			log.debug("Total active token accounts {}, total test tokens {}, my portion of tokens {}",
					numActiveTokenAccounts, numTotalTokens, numActiveTokens);
		}

		long startAccountId = settings.getTestTreasureStartAccount();
		/* We are useing first portion, 10K, 100K or any other number of total existing accounts to test
		   token performance. Thus the total account number (currently 1M+) won't impact the algorithm
		   here to build the associations of active testing accounts and newly created active tokens.
		   Given n accounts, the association between the i-th token and the j-th account has id
		        assocId = i * numActiveTokenAccounts + j
		   Where:
		    - i is in the range [0, numActiveTokens)
		    - j is in the range [0, numActiveTokenAccounts)
		*/

		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				return Collections.emptyList();
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				long nextAssocId;
				if ((nextAssocId = remainingAssociations.getAndDecrement()) < 0) {
					return Optional.empty();
				}
				int curToken = (int)nextAssocId / numActiveTokenAccounts;
				long curAccount = nextAssocId % numActiveTokenAccounts;
				var accountId = "0.0." + (startAccountId + curAccount);
				var op = tokenAssociate(accountId, tokenRegistryName(curToken))
						.payingWith(GENESIS)
						.signedBy(GENESIS)
						.hasRetryPrecheckFrom(BUSY, PLATFORM_TRANSACTION_NOT_CREATED, DUPLICATE_TRANSACTION,INSUFFICIENT_PAYER_BALANCE)
						.hasPrecheckFrom(DUPLICATE_TRANSACTION, OK)
						.hasKnownStatusFrom(SUCCESS, TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT,
								TRANSACTION_EXPIRED, TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED,FAIL_INVALID)
						.fee(A_HUNDRED_HBARS)
						.noLogging()
						.suppressStats(true)
						.deferStatusResolution();
				return Optional.of(op);
			}
		};
	}

	private HapiApiSpec runTokenTransferBasicLoadTest() {
		PerfTestLoadSettings settings = new PerfTestLoadSettings();
		Supplier<HapiSpecOperation[]> tokenTransferBurst = () -> new HapiSpecOperation[] {
				opSupplier(settings).get()
		};
		return defaultHapiSpec("TokenTransferBasicLoadTest" )
				.given(
						tokenOpsEnablement(), // remove this line when token services enabled by default
						withOpContext((spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap()))
				).when(
						sourcing(() -> runWithProvider(tokenCreatesFactory(settings))
								.lasting(() -> settings.getTotalTokens() / ESTIMATED_TOKEN_CREATION_RATE + 10, // 10s as buffering time
										() -> TimeUnit.SECONDS)
								.totalOpsToSumbit(() ->
										(int)Math.ceil((double)(settings.getTotalTokens()) / settings.getTotalClients()))
								.maxOpsPerSec(() -> (EXPECTED_MAX_OPS_PER_SEC / settings.getTotalClients()))
								.maxPendingOps(() -> MAX_PENDING_OPS_FOR_SETUP)),
						sourcing(() -> runWithProvider(activeTokenAssociatesFactory(settings))
								.lasting(() -> (settings.getTotalTokens() * settings.getTotalTestTokenAccounts()
												/ EXPECTED_MAX_OPS_PER_SEC) + 30, // 30s as buffering time
										() -> TimeUnit.SECONDS)
								.totalOpsToSumbit(() ->
										(int)Math.ceil((double)(settings.getTotalTokens()) / settings.getTotalClients())
												* settings.getTotalTestTokenAccounts())
								.maxOpsPerSec(() ->  (EXPECTED_MAX_OPS_PER_SEC / settings.getTotalClients()))
								.maxPendingOps( () -> MAX_PENDING_OPS_FOR_SETUP)
						),
						sleepFor(2000)
				).then(
						defaultLoadTest(tokenTransferBurst, settings)
				);
	}

	private static Supplier<HapiSpecOperation> opSupplier(PerfTestLoadSettings settings) {
		int tokenNum = r.nextInt(settings.getTotalTokens() / settings.getTotalClients());
		int sender = r.nextInt(settings.getTotalTestTokenAccounts());
		int receiver = r.nextInt(settings.getTotalTestTokenAccounts());
		while (receiver == sender) {
			receiver = r.nextInt(settings.getTotalTestTokenAccounts());
		}
		String senderAcct = String.format("0.0.%d", settings.getTestTreasureStartAccount() + sender);
		String receiverAcct = String.format("0.0.%d", settings.getTestTreasureStartAccount() + receiver);

		if (log.isDebugEnabled()) {
			log.debug("Account 0.0.{} will send  1 token of testToken{} to account 0.0.{}",
					senderAcct, tokenNum, receiverAcct);
		}

		var op = cryptoTransfer (
				moving(1, tokenRegistryName(tokenNum)).between(senderAcct, receiverAcct))
				.payingWith(senderAcct)
				.signedBy(GENESIS)
				.fee(A_HUNDRED_HBARS)
				.noLogging()
				.suppressStats(true)
				.hasPrecheckFrom(OK, INSUFFICIENT_PAYER_BALANCE,EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS
						,DUPLICATE_TRANSACTION)
				.hasRetryPrecheckFrom(BUSY, PLATFORM_TRANSACTION_NOT_CREATED)
				.hasKnownStatusFrom(SUCCESS, OK, INSUFFICIENT_TOKEN_BALANCE,TRANSACTION_EXPIRED,
						UNKNOWN, TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)
				.deferStatusResolution();
		return () -> op;
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
