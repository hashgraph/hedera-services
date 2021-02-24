package com.hedera.services.bdd.suites.perf;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.utils.sysfiles.serdes.FeesJsonToGrpcBytes;
import com.hedera.services.bdd.suites.utils.sysfiles.serdes.SysFileSerde;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.HapiPropertySource.asAccount;
import static com.hedera.services.bdd.spec.transactions.TxnFactory.bannerWith;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.NOISY_ALLOWED_STATUSES;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.NOISY_RETRY_PRECHECKS;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freeze;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.uploadDefaultFeeSchedules;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.stdMgmtOf;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.tokenOpsEnablement;
import static java.util.Map.entry;
import static java.util.concurrent.TimeUnit.MINUTES;

public class TokenTransfersLoadProvider extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TokenTransfersLoadProvider.class);

	private AtomicLong duration = new AtomicLong(Long.MAX_VALUE);
	private AtomicReference<TimeUnit> unit = new AtomicReference<>(MINUTES);
	private AtomicInteger maxOpsPerSec = new AtomicInteger(500);

	public static void main(String... args) {
		new TokenTransfersLoadProvider().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						runTokenTransfers(),
				}
		);
	}

	private HapiApiSpec runTokenTransfers() {
		return HapiApiSpec.defaultHapiSpec("RunTokenTransfers")
				.given(
						stdMgmtOf(duration, unit, maxOpsPerSec),
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of("balances.exportPeriodSecs", "300",
										"balances.exportDir.path", "data/accountBalances/")
								)
				).when(	runWithProvider(tokenTransfersFactory())
						.lasting(duration::get, unit::get)
						.maxOpsPerSec(maxOpsPerSec::get)
				).then(
						// The freeze and long wait after freeze means to keep the server in MAINTAENANCE state till test
						// end to prevent it from making new export files that may cause account balances validator to
						// be inconsistent. The freeze shouldn't cause normal perf test any issue.
						freeze().payingWith(GENESIS)
								.startingIn(10).seconds()
								.andLasting(10).minutes(),
						sleepFor(60_000)
				);
	}

	private Function<HapiApiSpec, OpProvider> tokenTransfersFactory() {
		var firstDir = new AtomicBoolean(Boolean.TRUE);
		var balanceInit = new AtomicLong();
		var tokensPerTxn = new AtomicInteger();
		var sendingAccountsPerToken = new AtomicInteger();
		var receivingAccountsPerToken = new AtomicInteger();
		List<String> treasuries = new ArrayList<>();
		Map<String, List<String>> senders = new HashMap<>();
		Map<String, List<String>> receivers = new HashMap<>();

		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				var ciProps = spec.setup().ciPropertiesMap();
				balanceInit.set(ciProps.getLong("balanceInit"));
				tokensPerTxn.set(ciProps.getInteger("tokensPerTxn"));
				sendingAccountsPerToken.set(ciProps.getInteger("sendingAccountsPerToken"));
				receivingAccountsPerToken.set(ciProps.getInteger("receivingAccountsPerToken"));

				var initialSupply =
						(sendingAccountsPerToken.get() + receivingAccountsPerToken.get()) * balanceInit.get();
				List<HapiSpecOperation> initializers = new ArrayList<>();
				initializers.add(tokenOpsEnablement());
				/* Temporary, can be removed after the public testnet state used in
				   restart tests includes a fee schedule with HTS resource prices. */
				if (spec.setup().defaultNode().equals(asAccount("0.0.3"))) {
					initializers.add(uploadDefaultFeeSchedules(GENESIS));
					initializers.add(
							fileUpdate(APP_PROPERTIES)
									.fee(9_999_999_999L)
									.payingWith(GENESIS)
									.overridingProps(Map.ofEntries(
											entry("hapi.throttling.buckets.fastOpBucket.capacity", "4000")
									))
					);
				} else {
					initializers.add(withOpContext((spec, opLog) -> {
						log.info("\n\n" + bannerWith("Waiting for a fee schedule with token ops!"));
						boolean hasKnownHtsFeeSchedules = false;
						SysFileSerde<String> serde = new FeesJsonToGrpcBytes();
						while (!hasKnownHtsFeeSchedules) {
							var query = QueryVerbs.getFileContents(FEE_SCHEDULE);
							try {
								allRunFor(spec, query);
								var contents = query.getResponse().getFileGetContents().getFileContents().getContents();
								var schedules = serde.fromRawFile(contents.toByteArray());
								hasKnownHtsFeeSchedules = schedules.contains("TokenCreate");
							} catch (Exception e) {
								var msg = e.toString();
								msg = msg.substring(msg.indexOf(":") + 2);
								log.info( "Couldn't check for HTS fee schedules---'{}'", msg);
							}
							TimeUnit.SECONDS.sleep(3);
						}
						log.info("\n\n" + bannerWith("A fee schedule with token ops now available!"));
						spec.tryReinitializingFees();
					}));
				}
				for (int i = 0; i < tokensPerTxn.get(); i++) {
					var token = "token" + i;
					var treasury = "treasury" + i;
					initializers.add(cryptoCreate(treasury));
					initializers.add(tokenCreate(token).treasury(treasury).initialSupply(initialSupply));
					treasuries.add(treasury);
					for (int j = 0; j < sendingAccountsPerToken.get(); j++) {
						var sender = token + "sender" + j;
						senders.computeIfAbsent(token, ignore -> new ArrayList<>()).add(sender);
						initializers.add(cryptoCreate(sender));
						initializers.add(tokenAssociate(sender, token));
						initializers.add(cryptoTransfer(
								moving(balanceInit.get(), token)
										.between(treasury, sender)));
					}
					for (int j = 0; j < receivingAccountsPerToken.get(); j++) {
						var receiver = token + "receiver" + j;
						receivers.computeIfAbsent(token, ignore -> new ArrayList<>()).add(receiver);
						initializers.add(cryptoCreate(receiver));
						initializers.add(tokenAssociate(receiver, token));
						initializers.add(cryptoTransfer(
								moving(balanceInit.get(), token)
										.between(treasury, receiver)));
					}
				}

				for (HapiSpecOperation op : initializers) {
					if (op instanceof HapiTxnOp) {
						((HapiTxnOp) op).hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS);
					}
				}

				return initializers;
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				HapiSpecOperation op;
				var numTokens = tokensPerTxn.get();
				var numSenders = sendingAccountsPerToken.get();
				var numReceivers = receivingAccountsPerToken.get();
				if (firstDir.get()) {
					var xfers = new TokenMovement[numTokens * numSenders];
					for (int i = 0; i < numTokens; i++) {
						var token = "token" + i;
						for (int j = 0; j < numSenders; j++) {
							var receivers = new String[numReceivers];
							for (int k = 0; k < numReceivers; k++) {
								receivers[k] = token + "receiver" + k;
							}
							xfers[i * numSenders + j] = moving(numReceivers, token)
									.distributing(token + "sender" + j, receivers);
						}
					}
					op = cryptoTransfer(xfers)
							.hasKnownStatusFrom(NOISY_ALLOWED_STATUSES)
							.hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS)
							.noLogging()
							.deferStatusResolution();
					firstDir.set(Boolean.FALSE);
				} else {
					var xfers = new TokenMovement[numTokens * numReceivers];
					for (int i = 0; i < numTokens; i++) {
						var token = "token" + i;
						for (int j = 0; j < numReceivers; j++) {
							var senders = new String[numSenders];
							for (int k = 0; k < numSenders; k++) {
								senders[k] = token + "sender" + k;
							}
							xfers[i * numReceivers + j] = moving(numSenders, token)
									.distributing(token + "receiver" + j, senders);
						}
					}
					op = cryptoTransfer(xfers)
							.hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS)
							.hasKnownStatusFrom(NOISY_ALLOWED_STATUSES)
							.noLogging()
							.deferStatusResolution();
					firstDir.set(Boolean.TRUE);
				}
				return Optional.of(op);
			}
		};
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
