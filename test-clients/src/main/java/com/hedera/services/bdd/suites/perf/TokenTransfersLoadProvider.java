package com.hedera.services.bdd.suites.perf;

/*-
 * ‌
 * Hedera Services Test Clients
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
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.suites.HapiApiSuite;
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

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
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
						withOpContext((spec, opLog) -> {
							var ciProps = spec.setup().ciPropertiesMap();
							if (ciProps.has("duration")) {
								duration.set(ciProps.getLong("duration"));
							}
							if (ciProps.has("unit")) {
								unit.set(ciProps.getTimeUnit("unit"));
							}
							if (ciProps.has("maxOpsPerSec")) {
								maxOpsPerSec.set(ciProps.getInteger("maxOpsPerSec"));
							}
						})
				).when().then(
						runWithProvider(tokenTransfersFactory())
								.lasting(duration::get, unit::get)
								.maxOpsPerSec(maxOpsPerSec::get)
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
				initializers.add(
						fileUpdate(API_PERMISSIONS)
								.fee(9_999_999_999L)
								.payingWith(GENESIS)
								.overridingProps(Map.ofEntries(
										entry("tokenCreate", "0-*"),
										entry("tokenFreezeAccount", "0-*"),
										entry("tokenUnfreezeAccount", "0-*"),
										entry("tokenGrantKycToAccount", "0-*"),
										entry("tokenRevokeKycFromAccount", "0-*"),
										entry("tokenDelete", "0-*"),
										entry("tokenMint", "0-*"),
										entry("tokenBurn", "0-*"),
										entry("tokenAccountWipe", "0-*"),
										entry("tokenUpdate", "0-*"),
										entry("tokenGetInfo", "0-*"),
										entry("tokenAssociateToAccount", "0-*"),
										entry("tokenDissociateFromAccount", "0-*")
								))
				);
				initializers.add(
						fileUpdate(APP_PROPERTIES)
								.fee(9_999_999_999L)
								.payingWith(GENESIS)
								.overridingProps(Map.ofEntries(
										entry("hapi.throttling.buckets.fastOpBucket.capacity", "4000")
								))
				);
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
					((HapiTxnOp) op).hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED);
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
							.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED)
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
							.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED)
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
