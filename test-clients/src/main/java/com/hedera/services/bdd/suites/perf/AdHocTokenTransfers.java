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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
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

import static com.hedera.services.bdd.spec.transactions.TxnUtils.NOISY_ALLOWED_STATUSES;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.NOISY_RETRY_PRECHECKS;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.stdMgmtOf;
import static java.util.concurrent.TimeUnit.MINUTES;

public class AdHocTokenTransfers extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(AdHocTokenTransfers.class);

	private AtomicLong duration = new AtomicLong(10L);
	private AtomicReference<TimeUnit> unit = new AtomicReference<>(MINUTES);
	private AtomicInteger maxOpsPerSec = new AtomicInteger(500);

	public static void main(String... args) {
		new AdHocTokenTransfers().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						runTokenTransfers(),
//						oneOff(),
				}
		);
	}

	private HapiApiSpec oneOff() {
		return HapiApiSpec.customHapiSpec("OneOff").withProperties(
				Map.of(
						"nodes", "34.74.82.254:0.0.3,35.245.216.12:0.0.4,146.148.65.62:0.0.5,34.83.73.160:0.0.6,34.94.104.178:0.0.7,35.203.2.132:0.0.8,34.77.1.188:0.0.9,35.246.68.126:0.0.10,35.234.74.241:0.0.11,35.204.223.190:0.0.12,35.194.125.44:0.0.13,34.97.26.91:0.0.14,35.187.159.128:0.0.15,34.90.203.246:0.0.16",
						"startupAccounts.literal", LITERAL_STARTUP_ACCOUNT,
						"ci.properties.map", Optional.ofNullable(System.getenv("CI_PROPS")).orElse(DEFAULT_CI_PROPS)
				)
		).given(
//				fileUpdate(APP_PROPERTIES)
//						.fee(9_999_999_999L)
//						.payingWith(GENESIS)
//						.overridingProps(Map.ofEntries(
//								entry("hapi.throttling.buckets.fastOpBucket.capacity", "4000")
//						))
		).when().then(
				QueryVerbs.getFileContents(APP_PROPERTIES).logged()
		);
	}

	private HapiApiSpec runTokenTransfers() {
		return HapiApiSpec.customHapiSpec("RunTokenTransfers").withProperties(
						Map.of(
								"nodes", "34.74.82.254:0.0.3,35.245.216.12:0.0.4,146.148.65.62:0.0.5,34.83.73.160:0.0.6,34.94.104.178:0.0.7,35.203.2.132:0.0.8,34.77.1.188:0.0.9,35.246.68.126:0.0.10,35.234.74.241:0.0.11,35.204.223.190:0.0.12,35.194.125.44:0.0.13,34.97.26.91:0.0.14,35.187.159.128:0.0.15,34.90.203.246:0.0.16",
								"startupAccounts.literal", LITERAL_STARTUP_ACCOUNT,
								"ci.properties.map", Optional.ofNullable(System.getenv("CI_PROPS")).orElse(DEFAULT_CI_PROPS)
						)
				).given(
						stdMgmtOf(duration, unit, maxOpsPerSec)
				).when().then(
						withOpContext((spec, opLog) -> {
							opLog.info("Targeting node " + targetNodeAccount());
						}),
						runWithProvider(tokenTransfersFactory())
								.lasting(duration::get, unit::get)
								.maxOpsPerSec(maxOpsPerSec::get)
				);
	}

	private String targetNodeAccount() {
		int targetNode = Integer.parseInt(Optional.ofNullable(System.getenv("TARGET")).orElse("3"));
		return String.format("0.0.%d", targetNode);
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
		String targetAccount = targetNodeAccount();

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
				for (int i = 0; i < tokensPerTxn.get(); i++) {
					var token = "token" + i;
					var treasury = "treasury" + i;
					initializers.add(cryptoCreate(treasury));
					initializers.add(tokenCreate(token).treasury(treasury).initialSupply(initialSupply));
					treasuries.add(treasury);
					for (int j = 0; j < sendingAccountsPerToken.get(); j++) {
						var sender = token + "sender" + j;
						senders.computeIfAbsent(token, ignore -> new ArrayList<>()).add(sender);
						initializers.add(
								cryptoCreate(sender)
										.balance(5_000_000_000_000L)
										.withRecharging()
										.rechargeWindow(3)
						);
						initializers.add(tokenAssociate(sender, token));
						initializers.add(cryptoTransfer(
								moving(balanceInit.get(), token)
										.between(treasury, sender)));
					}
					for (int j = 0; j < receivingAccountsPerToken.get(); j++) {
						var receiver = token + "receiver" + j;
						receivers.computeIfAbsent(token, ignore -> new ArrayList<>()).add(receiver);
						initializers.add(
								cryptoCreate(receiver)
										.balance(5_000_000_000_000L)
										.withRecharging()
										.rechargeWindow(3)
						);
						initializers.add(tokenAssociate(receiver, token));
						initializers.add(cryptoTransfer(
								moving(balanceInit.get(), token)
										.between(treasury, receiver)));
					}
				}

				for (HapiSpecOperation op : initializers) {
					((HapiTxnOp)op).hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS).setNode(targetAccount);
				}

				return initializers;
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				HapiSpecOperation op;
				var numTokens = tokensPerTxn.get();
				var numSenders = sendingAccountsPerToken.get();
				var numReceivers = receivingAccountsPerToken.get();
				String effPayer = null;
				var now = "" + Instant.now();
				if (firstDir.get()) {
					var xfers = new TokenMovement[numTokens * numSenders];
					for (int i = 0; i < numTokens; i++) {
						var token = "token" + i;
						for (int j = 0; j < numSenders; j++) {
							var receivers = new String[numReceivers];
							for (int k = 0; k < numReceivers; k++) {
								receivers[k] = token + "receiver" + k;
							}
							var source = token + "sender" + j;
							if (effPayer == null) {
								effPayer = source;
							}
							xfers[i * numSenders + j] = moving(numReceivers, token)
									.distributing(source, receivers);
						}
					}
					op = cryptoTransfer(xfers)
							.hasKnownStatusFrom(NOISY_ALLOWED_STATUSES)
							.hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS)
							.memo(now)
							.payingWith(effPayer)
							.setNode(targetAccount)
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
							var source = token + "receiver" + j;
							if (effPayer == null) {
								effPayer = source;
							}
							xfers[i * numReceivers + j] = moving(numSenders, token)
									.distributing(source, senders);
						}
					}
					op = cryptoTransfer(xfers)
							.hasKnownStatusFrom(NOISY_ALLOWED_STATUSES)
							.hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS)
							.memo(now)
							.payingWith(effPayer)
							.setNode(targetAccount)
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

	private static final String LITERAL_STARTUP_ACCOUNT = "rO0ABXNyABFqYXZhLnV0aWwuQ29sbFNlcleOq7Y6G6gRAwABSQADdGFneHAAAAADdwQAAAACdAANU1RBUlRfQUNDT1VOVHNxAH4AAAAAAAF3BAAAAAFzcgAxY29tLmhlZGVyYS5zZXJ2aWNlcy5sZWdhY3kuY29yZS5BY2NvdW50S2V5TGlzdE9iasKGpBBS1EebAgACTAAJYWNjb3VudElkdAAuTGNvbS9oZWRlcmFoYXNoZ3JhcGgvYXBpL3Byb3RvL2phdmEvQWNjb3VudElEO0wAC2tleVBhaXJMaXN0dAAQTGphdmEvdXRpbC9MaXN0O3hwc3IAN2NvbS5nb29nbGUucHJvdG9idWYuR2VuZXJhdGVkTWVzc2FnZUxpdGUkU2VyaWFsaXplZEZvcm0AAAAAAAAAAAIAA1sAB2FzQnl0ZXN0AAJbQkwADG1lc3NhZ2VDbGFzc3QAEUxqYXZhL2xhbmcvQ2xhc3M7TAAQbWVzc2FnZUNsYXNzTmFtZXQAEkxqYXZhL2xhbmcvU3RyaW5nO3hwdXIAAltCrPMX+AYIVOACAAB4cAAAAAIYAnZyACxjb20uaGVkZXJhaGFzaGdyYXBoLmFwaS5wcm90by5qYXZhLkFjY291bnRJRAAAAAAAAAAAAgAESgALYWNjb3VudE51bV9CABVtZW1vaXplZElzSW5pdGlhbGl6ZWRKAAlyZWFsbU51bV9KAAlzaGFyZE51bV94cgAmY29tLmdvb2dsZS5wcm90b2J1Zi5HZW5lcmF0ZWRNZXNzYWdlVjMAAAAAAAAAAQIAAUwADXVua25vd25GaWVsZHN0ACVMY29tL2dvb2dsZS9wcm90b2J1Zi9Vbmtub3duRmllbGRTZXQ7eHB0ACxjb20uaGVkZXJhaGFzaGdyYXBoLmFwaS5wcm90by5qYXZhLkFjY291bnRJRHNxAH4AAAAAAAF3BAAAAAFzcgAqY29tLmhlZGVyYS5zZXJ2aWNlcy5sZWdhY3kuY29yZS5LZXlQYWlyT2Jqfu50MIDYVscCAANMAAdwcml2S2V5dAAaTGphdmEvc2VjdXJpdHkvUHJpdmF0ZUtleTtMAApwcml2YXRlS2V5cQB+AAtMAAlwdWJsaWNLZXlxAH4AC3hwcHQAYDMwMmUwMjAxMDAzMDA1MDYwMzJiNjU3MDA0MjIwNDIwOTExMzIxNzhlNzIwNTdhMWQ3NTI4MDI1OTU2ZmUzOWIwYjg0N2YyMDBhYjU5YjJmZGQzNjcwMTdmMzA4NzEzN3QAWDMwMmEzMDA1MDYwMzJiNjU3MDAzMjEwMDBhYThlMjEwNjRjNjFlYWI4NmUyYTljMTY0NTY1YjRlN2E5YTQxNDYxMDZlMGE2Y2QwM2E4YzM5NWExMTBlOTJ4eHg=";

	private static final String DEFAULT_CI_PROPS = "duration=10,unit=MINUTES,maxOpsPerSec=705,tokensPerTxn=1,sendingAccountsPerToken=1,receivingAccountsPerToken=1,balanceInit=10000";
}
